# 장애 원인 분석 — a-connection-pool

## 요약

- **무엇 · 언제**: 2026-09-16 **14:40:52 ~ 14:51:49 KST(약 11분)** item-bank-api 응답 실패 — 500 49건 · 502 82건 · 504 55건. 전조(커넥션 장시간 점유 경고)는 14:37:52 부터.
- **직접 원인**: 학급 리포트(`GET /api/classes/{id}/report`)가 요청 1건당 DB 커넥션을 15.5 ~ 21.5초 쥐었고, 리포트 동시 조회가 분당 0 → 10 ~ 19건으로 늘자 커넥션 풀 5개가 모두 리포트에 묶였다.
- **배경 원인**: 운영 빌드의 `ReportService.buildClassReport` 가 `@Transactional` 안에서 SHA-256 20만 회 서명과 배포별 제출 조회를 했다. 풀은 최대 5 · 대기 3초로 작고, 리포트 동시성 제한이 없었다. nginx 의 두 번째 upstream 은 연결을 거부해 장애를 넘겨받지 못했다.
- **확신 수준: 중간** — 로그 스택 · 코드 · 설정 값은 서로 맞는다. 운영 배포 버전, 점유 시간의 구성(서명 · CPU 경합 · 조회)은 확인하지 못했다.
- **조치 상태**: 같은 결함의 수정이 저장소 HEAD 에 있다(커밋 `1cab966`). 운영 반영 여부는 확인하지 못했다(6절).

대상 로그: `incident-logs/a-connection-pool/`

## 1. 수집 범위

> 이 절은 범위만 정한다. 원인 추정은 하지 않는다.
> 집계 방법: `wc -l`, `head`, `tail`, `grep -c`, `grep -n`, `awk`/`cut` 로 분 단위 집계했다. 모든 시각은 KST(+09:00) 기준이다.

### 1.1 파일별 기록 기간 · 형식

| 파일 | 줄 수 | 기록 기간 (원본 표기) | 기록 기간 (KST) | 시각 형식 | 타임존 |
|---|---:|---|---|---|---|
| `app.log` | 5,371 (로그 헤더 1,927 · 스택트레이스 등 연속 줄 3,444) | `2026-09-16T06:43:56.753+09:00` ~ `2026-09-16T15:29:51.760+09:00` | 09-16 06:43:56 ~ 15:29:51 | ISO-8601, 밀리초, 오프셋 포함 | KST (`+09:00` 명시) |
| `nginx-access.log` | 21,086 | `[16/Sep/2026:06:43:56 +0900]` ~ `[16/Sep/2026:15:29:59 +0900]` | 09-16 06:43:56 ~ 15:29:59 | Common/Combined Log Format, 초 단위 | KST (`+0900` 명시) |
| `nginx-error.log` | 168 | `2026/09/16 09:12:03` ~ `2026/09/16 14:51:47` | 09-16 09:12:03 ~ 14:51:47 | `YYYY/MM/DD HH:MM:SS`, 초 단위 | **표기 없음** → KST 로 판단 (아래 근거) |
| `mariadb-slow.log` | 21 (항목 3건) | `# Time: 260916  0:14:09` ~ `# Time: 260916  5:37:52` | 09-16 09:14:09 ~ 14:37:52 | `# Time: YYMMDD H:MM:SS` + `SET timestamp=<epoch>` | **UTC** (아래 근거) |

**타임존 판단 근거**

- `nginx-error.log`: 마지막 `[error]` 줄 `14:51:47`(client 10.20.3.17, `GET /api/classes/3/report`, upstream timed out)이 `nginx-access.log` 의 마지막 5xx 줄 `[16/Sep/2026:14:51:47 +0900] ... /api/classes/3/report ... 504`(같은 client)와 초 단위까지 일치한다. 따라서 KST 다.
- `mariadb-slow.log`: `SET timestamp=` 의 epoch 값을 변환하면 UTC 시각이 `# Time:` 과 같다.
  - `1789517647` → UTC 00:14:07 (`# Time` 0:14:09)
  - `1789524160` → UTC 02:02:40 (`# Time` 2:02:42)
  - `1789537062` → UTC 05:37:42 (`# Time` 5:37:52)
  - 차이(2초 · 2초 · 10초)는 각 항목의 `Query_time`(2.3초 · 2.0초 · 9.8초)과 맞는다. `SET timestamp` 는 쿼리 시작 시각, `# Time` 은 기록(종료) 시각이다.

**KST 변환 규칙**

| 파일 | 규칙 |
|---|---|
| `app.log`, `nginx-access.log` | 오프셋이 이미 +09:00 → 그대로 쓴다 |
| `nginx-error.log` | 표기 없음, KST 로 확인됨 → 그대로 쓴다 |
| `mariadb-slow.log` | `# Time` 에 **+9시간**. 날짜가 바뀔 수 있으니 UTC 15:00 이후 항목은 날짜를 하루 더한다(이번 파일에는 해당 없음). 쿼리 시작 시각이 필요하면 `SET timestamp` epoch 를 KST 로 변환한다 (`TZ=Asia/Seoul date -r <epoch>`) |

`mariadb-slow.log` 의 KST 기준 항목: 09:14:09, 11:02:42, 14:37:52 (기록 시각).

### 1.2 분 단위 이상 신호 집계

집계 대상

| 파일 | 센 줄 |
|---|---|
| `app.log` | 헤더 줄 중 레벨 `WARN` · `ERROR` (전체 WARN 203, ERROR 98, INFO 1,626). 별도로 `timeout`/`timed out` 포함 줄 |
| `nginx-access.log` | 상태 코드 5xx (500: 49, 502: 82, 504: 55 — 총 186) |
| `nginx-error.log` | `[warn]` · `[error]` 줄 (warn 28, error 137, info 3). 별도로 `timed out` 포함 줄 |
| `mariadb-slow.log` | 항목 3건뿐이라 분 단위 집계 대신 시각만 표시 |

**평소 수준 (06:43 ~ 14:36, 약 474분)**

- `app.log` WARN/ERROR: 분당 0 ~ 1건. 이 기간의 WARN 은 모두 `GlobalExceptionHandler` 의 `state conflict`(409, 마감 과제 재배포) 이며 하루 전체 20건으로 `nginx-access.log` 의 409 20건과 같다. ERROR 0건.
- `nginx-access.log` 5xx: 0건.
- `nginx-error.log` warn/error: 2건(09:12 · 13:05 `[warn]`, 프록시 임시파일 버퍼링). error 0건.
- 요청량: 분당 평균 약 40건(전체 527분). 장애 구간에도 34 ~ 58건으로 평소와 같은 범위다.
- 집계한 527분 중 빠진 분은 없다(06:43 ~ 15:29 연속).

**이상 신호가 있는 분 (14:30 ~ 14:55, 0이 아닌 분 + 경계)**

| 분 (KST) | 요청 수 | app WARN+ERROR | app timeout | nginx 5xx | nginx-error warn+error | nginx-error timed out | slow log |
|---|---:|---:|---:|---:|---:|---:|---|
| 14:33 | 52 | 1 | 0 | 0 | 0 | 0 | |
| 14:36 | 41 | 0 | 0 | 0 | 0 | 0 | |
| 14:37 | 50 | 1 | 0 | 0 | 0 | 0 | 14:37:52 기록 (시작 14:37:42) |
| 14:38 | 41 | 2 | 0 | 0 | 0 | 0 | |
| 14:39 | 42 | 3 | 0 | 0 | 0 | 0 | |
| 14:40 | 42 | 17 | 1 | 1 | 0 | 0 | |
| 14:41 | 42 | 31 | 6 | 9 | 3 | 3 | |
| 14:42 | 41 | 17 | 2 | 20 | 20 | 5 | |
| 14:43 | 58 | 44 | 10 | 17 | 8 | 5 | |
| 14:44 | 34 | 17 | 2 | 13 | 15 | 6 | |
| 14:45 | 39 | 33 | 8 | 27 | 22 | 5 | |
| 14:46 | 51 | 24 | 4 | 26 | 25 | 6 | |
| 14:47 | 38 | 24 | 5 | 14 | 10 | 3 | |
| 14:48 | 42 | 22 | 4 | 15 | 15 | 5 | |
| 14:49 | 42 | 33 | 7 | 23 | 20 | 8 | |
| 14:50 | 34 | 12 | 0 | 18 | 22 | 7 | |
| 14:51 | 46 | 4 | 0 | 3 | 3 | 2 | |
| 14:52 | 47 | 1 | 0 | 0 | 0 | 0 | |
| 14:53 ~ 15:29 | 32 ~ 68 | 0 | 0 | 0 | 0 | 0 | |

(14:33 · 14:52 의 1건은 평소와 같은 `state conflict` WARN 이다.)

**경계 시각 (첫 줄 · 마지막 줄)**

| 신호 | 첫 발생 | 마지막 발생 | 근거 |
|---|---|---|---|
| `app.log` Hikari `ProxyLeakTask` WARN (평소에 없던 종류) | 14:37:52.199 | 14:51:37.674 | `app.log:1243`, `app.log:5251` |
| `app.log` Hikari `ProxyLeakTask` INFO "was returned to the pool" | 14:38:01.902 | 14:51:49.148 | `app.log:1259`, `app.log:5267` |
| `app.log` ERROR | 14:40:52.319 | 14:49:51.553 | `app.log:1522`, `app.log:4981` |
| `nginx-access.log` 5xx | 14:40:52 (500) | 14:51:47 (504) | |
| `nginx-error.log` `[error]` | 14:41:06 | 14:51:47 | `nginx-error.log:6`, `nginx-error.log:168` |

**제안 — 이상 구간**

- **시작: 14:37:52 KST** — 평소에 없던 종류의 경고(`ProxyLeakTask`)가 처음 나온 시각. 사용자 영향(5xx · ERROR)은 **14:40:52** 부터다.
- **끝: 14:51:49 KST** — 이상 신호 마지막 줄(leak 커넥션 반환 INFO, `app.log:5267`). 마지막 leak WARN 은 14:51:37, 마지막 5xx · nginx `[error]` 는 14:51:47. 14:52 부터 모든 신호가 평소 수준으로 돌아갔고 15:29 까지 유지된다.
- 정리: 전조 14:37:52 ~ 14:40:51 (약 3분) / 영향 14:40:52 ~ 14:51:49 (약 11분).

### 1.3 분석 구간 여유

| 구분 | 제안 | 시각 (KST) | 이유 |
|---|---|---|---|
| 앞쪽 여유 | **30분** | **14:07:00** 부터 | `modern/api` 설정의 `leak-detection-threshold: 10000`(10초) 때문에 leak 경고는 실제 커넥션 대여보다 최소 10초 늦게 찍힌다. 또 slow log 는 끝난 쿼리만 기록하므로 앞선 변화가 늦게 드러날 수 있다. 직전 30분은 신호가 평소 수준(WARN 0 ~ 1, 5xx 0)이라 비교 기준선으로도 쓴다 |
| 뒤쪽 여유 | **10분** | **15:02:00** 까지 | 회복 후 재발 없이 안정됐는지 확인하는 용도. 로그가 15:29 까지 있어 확보 가능 |
| **최종 분석 구간** | | **2026-09-16 14:07:00 ~ 15:02:00 KST** | (slow log 기준 UTC 05:07:00 ~ 06:02:00) |

참고: 구간 밖의 slow log 2건(KST 09:14:09 · 11:02:42)과 nginx-error `[warn]` 2건(09:12 · 13:05)은 평소 수준 비교용으로만 남겨 둔다.

## 2. 타임라인

> 대상 구간: **2026-09-16 14:07:00 ~ 15:02:00 KST** (1절에서 정한 구간 + 앞 30분 · 뒤 10분).
> 모든 시각은 KST. `mariadb-slow.log` 는 UTC 에 9시간을 더했다. 로그에 있는 사건만 적고 원인 해석은 하지 않는다.
> 학생 식별자 · 이메일이 들어 있는 줄(재배포 사유 등)은 발췌하지 않는다.

### 2.1 범례

- **출처**: `app` = `app.log`, `access` = `nginx-access.log`, `error` = `nginx-error.log`, `slow` = `mariadb-slow.log`.
- **(선후 불확실)**: 다른 파일의 사건과 시각 차이가 몇 초 이내라 순서를 단정할 수 없는 경우. `access` · `error` · `slow` 는 초 단위, `app` 은 밀리초 단위다.
- **반복 묶음**: "첫 발생 시각, 이후 N건/분" 형식. 분별 건수는 2.3 표에 있다.

### 2.2 시간순 사건

| 시각 (KST) | 출처 (파일:줄) | 사건 요약 | 원문 발췌 (80자 이내) |
|---|---|---|---|
| 14:07 ~ 14:37 | (전체) | 평소 수준. 요청 분당 32 ~ 56건, 5xx 0건, `app` WARN 은 409 상태 충돌만(14:11:38 · 14:14:03 · 14:33:45), `error` 0줄, `slow` 0건 | — |
| 14:10:05 | access:17539, app:1148 | 반 리포트 조회 1건(200). 14:18 까지 이런 조회가 모두 5건(14:10 · 14:13 · 14:15 · 14:17 · 14:18), 그 뒤 14:37 까지는 0건 | `GET /api/classes/3/report HTTP/1.1" 200` |
| 14:19:38 | access:17977, app:1182 | 재배포 1건(200), distribution 7. 이 날 08:24 부터 하루 29건 있는 종류의 사건 | `POST /api/distributions/7/redistribute` … `200` |
| 14:37:42 | slow:20 | 느린 쿼리 **시작** (`SET timestamp=1789537062`, UTC 05:37:42). `submission` 을 `distribution_id=5` 로 조회 | `SET timestamp=1789537062;` |
| 14:37:52 | slow:15, 18 | 느린 쿼리 **기록**. 9.8초, 1,284,310행 검사, 4행 반환. 이 날 slow log 의 세 항목 중 가장 길다(나머지 2.3초 · 2.0초) | `# Query_time: 9.812417 … Rows_sent: 4  Rows_examined: 1284310` |
| 14:37:52.199 | app:1243 | **처음 나온 커넥션 누수 의심 경고.** 스택: `ReportController.classReport` → `ReportService.buildClassReport` 에서 트랜잭션 시작 시 커넥션 획득. slow:15 와 같은 초 (선후 불확실) | `leak detection triggered for …@6e8f14d3 on thread http-nio-8080-exec-6` |
| 14:38:01 | access:18801 | 이 구간 첫 반 리포트 응답(200, client 10.20.3.12, class 3). 14:19 이후 첫 리포트 조회 | `"GET /api/classes/3/report HTTP/1.1" 200 266` |
| 14:38:01.900 | app:1258, 1259 | 리포트 생성 완료, 이어서 14:37:52 경고의 커넥션 반환 기록. access:18801 과 같은 초 (선후 불확실) | `Previously reported leaked connection …@6e8f14d3 … returned to the pool` |
| 14:38:20.016 | app:1261 | 누수 경고 반복 시작. **이후 14:38 2건, 14:39 3건, 14:40 14건, 14:41 ~ 14:50 분당 9 ~ 14건, 14:51 3건** (총 134건). 같은 구간에 "returned to the pool" INFO 도 총 134건 있다 | `leak detection triggered for …@6e8f14d3 on thread http-nio-8080-exec-3` |
| 14:38:01 ~ 14:39:55 | access:18801, 18815, 18839, 18862, 18882 | 같은 client(10.20.3.12)가 class 3 리포트를 약 26 ~ 30초 간격으로 조회(14:38:01 · :27 · :57 · 14:39:24 · :55) | `10.20.3.12 … "GET /api/classes/3/report HTTP/1.1" 200` |
| 14:38:29 | access:18817, app:1278 | 재배포 1건(200), distribution 5 (14:37:42 느린 쿼리의 `distribution_id` 와 같은 번호) | `POST /api/distributions/5/redistribute` … `200` |
| 14:39:50.132 | app:1331 | 처음으로 서로 다른 커넥션 2개가 동시에 누수 경고 상태(`@2ac19f07` 14:39:48, `@71d4b8e2` 14:39:50) | `…Connection@71d4b8e2 on thread http-nio-8080-exec-4` |
| 14:39:54 ~ 14:40:52 | access:18881 ~ 18919 | **트래픽 구성 변화.** 리포트 조회 client 가 1개(10.20.3.12)에서 4개(.11 · .12 · .17 · .23)로, class 1 · 2 · 3 으로 늘어남. 리포트 조회 분당 0 → 3 → 3 → 10 → 16건. 전체 요청 수는 분당 41 ~ 58건으로 평소 범위 | `10.20.3.23 … "GET /api/classes/1/report HTTP/1.1" 200` |
| 14:40:04 ~ 14:40:43 | app:1351 ~ 1500 | 누수 경고 대상 커넥션이 5개(`@6e8f14d3` · `@2ac19f07` · `@71d4b8e2` · `@3f6c2a91` · `@5b0e9e0c`)로 늘어남 | `…Connection@3f6c2a91 on thread http-nio-8080-exec-6` |
| 14:40:52.319 | app:1521, 1522, 1523 | **첫 커넥션 풀 획득 실패 ERROR**(가장 많이 나오는 ERROR 종류). `/api/units/M6-2/items` 요청. **이후 14:41 6건, 14:42 2건, 14:43 10건, 14:44 2건, 14:45 8건, 14:46 4건, 14:47 5건, 14:48 4건, 14:49 7건**(총 49건), 마지막 14:49:51.552(app:4980). 매번 `SQL Error: 0` WARN 1줄과 `unhandled exception` ERROR 1줄이 함께 찍힘 | `Connection is not available, … 3000ms (total=5, active=5, idle=0, waiting=4)` |
| 14:40:52 | access:18918 | **첫 5xx (500).** `/api/units/M6-2/items`. app:1522 와 같은 초 (선후 불확실). **이후 500 은 분당 app:1522 종류 ERROR 와 같은 건수**(총 49건), 마지막 14:49:51(access:19305) | `"GET /api/units/M6-2/items HTTP/1.1" 500 162` |
| 14:41:06 | access:18933, error:6 | **첫 504 · 첫 nginx `[error]`**: upstream 10.20.1.21 응답 헤더 대기 시간 초과. client 10.20.3.12, class 3 리포트. 두 파일이 같은 초 (선후 불확실). **이후 14:41 3건, 14:42 ~ 14:48 분당 3 ~ 6건, 14:49 8건, 14:50 7건, 14:51 2건**(총 55건, 두 파일 건수 같음) | `upstream timed out (110: Connection timed out) while reading response header` |
| 14:42:01 | error:10 | upstream **10.20.1.21** 일시 제외. **이후 분당 1 ~ 4건**(총 26건), 마지막 14:50:52(error:158) | `upstream server temporarily disabled while reading response header` |
| 14:42:01 | error:11 | 두 번째 upstream **10.20.1.22** 연결 거부. 이 파일에서 10.20.1.22 가 처음 나오는 줄. error:10 과 같은 초 (선후 불확실). **이후 분당 1 ~ 2건**(총 14건), 마지막 14:50:52(error:159) | `connect() failed (111: Connection refused) while connecting to upstream` |
| 14:42:01 | access:18970 | **첫 502.** error:10 · 11 과 같은 초 (선후 불확실). **이후 14:42 13건, 14:43 ~ 14:49 분당 2 ~ 16건, 14:50 11건, 14:51 1건**(총 82건), 마지막 14:51:02(access:19347) | `"GET /api/classes/2/report HTTP/1.1" 502 157` |
| 14:42:04 | error:12 | nginx 가 쓸 수 있는 upstream 이 없음(`item_bank_api` 그룹). **이후 14:42 11건, 14:45 12건, 14:46 14건 등**(총 68건), 마지막 14:51:02(error:166) | `no live upstreams while connecting to upstream, client: 10.20.6.87` |
| 14:44:19 | access:19078, app:2929 | 재배포 1건(200), distribution 8 | `POST /api/distributions/8/redistribute` … `200` |
| 14:49:51.552 | app:4980, 4981 / access:19305 | 마지막 커넥션 풀 획득 실패 ERROR · 마지막 500. 같은 초 (선후 불확실) | `Connection is not available, request timed out after 3000ms` |
| 14:50:52 | error:158, 159 | 마지막 upstream 일시 제외 · 마지막 연결 거부 | `upstream server temporarily disabled …` |
| 14:51:02 | error:166 / access:19347 | 마지막 `no live upstreams` · 마지막 502. 같은 초 (선후 불확실) | `no live upstreams while connecting to upstream` |
| 14:51:37.674 | app:5251 | 마지막 누수 경고 | `Connection leak detection triggered for …@6e8f14d3` |
| 14:51:47 | error:168 / access:19381 | 마지막 nginx `[error]`(timed out) · 마지막 504(client 10.20.3.17, class 3 리포트). 같은 초 (선후 불확실) | `"GET /api/classes/3/report HTTP/1.1" 504 167` |
| 14:51:49.148 | app:5266, 5267 | 마지막 리포트 생성 + 마지막 누수 커넥션 반환. **이상 신호 마지막 줄** | `Previously reported leaked connection … was returned to the pool (unleaked)` |
| 14:52 ~ 15:02 | (전체) | 평소 수준. 리포트 조회 분당 0건(14:52 ~ 14:58), 5xx 0건, `error` 0줄, `app` WARN 은 409 상태 충돌 1건(14:52:09, app:5269)뿐 | — |
| 14:59:15 · 14:59:52 · 15:01:20 | access:19701, 19724, 19781 / app:5283, 5284, 5287 | 리포트 조회 3건(200) · 리포트 생성 3건. 누수 경고 없음 | `"GET /api/classes/3/report HTTP/1.1" 200` |

### 2.3 분 단위 신호 (14:37 ~ 14:52)

| 분 | 전체 요청 | 리포트 조회 | 누수 WARN | 풀 획득 실패 ERROR | 500 | 502 | 504 | error: timed out | error: disabled(.21) | error: refused(.22) | error: no live |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 14:37 | 50 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:38 | 41 | 3 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:39 | 42 | 3 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:40 | 42 | 10 | 14 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:41 | 42 | 16 | 13 | 6 | 6 | 0 | 3 | 3 | 0 | 0 | 0 |
| 14:42 | 41 | 15 | 10 | 2 | 2 | 13 | 5 | 5 | 2 | 2 | 11 |
| 14:43 | 58 | 19 | 14 | 10 | 10 | 2 | 5 | 5 | 1 | 1 | 1 |
| 14:44 | 34 | 14 | 11 | 2 | 2 | 5 | 6 | 6 | 4 | 1 | 4 |
| 14:45 | 39 | 15 | 9 | 8 | 8 | 14 | 5 | 5 | 3 | 2 | 12 |
| 14:46 | 51 | 19 | 12 | 4 | 4 | 16 | 6 | 6 | 3 | 2 | 14 |
| 14:47 | 38 | 12 | 9 | 5 | 5 | 6 | 3 | 3 | 1 | 1 | 5 |
| 14:48 | 42 | 14 | 10 | 4 | 4 | 6 | 5 | 5 | 4 | 1 | 5 |
| 14:49 | 42 | 16 | 12 | 7 | 7 | 8 | 8 | 8 | 4 | 2 | 6 |
| 14:50 | 34 | 10 | 11 | 0 | 0 | 11 | 7 | 7 | 4 | 2 | 9 |
| 14:51 | 46 | 5 | 3 | 0 | 0 | 1 | 2 | 2 | 0 | 0 | 1 |
| 14:52 | 47 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| **합계** | | 179 | 134 | 49 | 49 | 82 | 55 | 55 | 26 | 14 | 68 |

### 2.4 가장 많은 오류보다 앞선 평소와 다른 줄 (점검표)

가장 많이 나오는 `app` ERROR 는 커넥션 풀 획득 실패(49건, 첫 14:40:52)다. nginx 쪽에서 가장 많은 것은 `no live upstreams`(68건, 첫 14:42:04)다. 그보다 앞선 시각의 줄을 종류별로 확인한 결과다.

| 종류 | 결과 |
|---|---|
| 느린 쿼리 | 있음 — 14:37:42 시작 · 14:37:52 기록, 9.8초 (slow:15 ~ 21). 구간 안의 유일한 slow log 항목 |
| 경고 | 있음 — 누수 경고 14:37:52 부터 14:40:52 전까지 16건 (app:1243 ~ 1500) |
| 설정 재적재 · 재시작 | **로그에 없음** — `app.log` · `nginx-error.log` 에서 reload · refresh · restart · start · shutdown · config · signal 을 grep 했고 0건 |
| 트래픽 변화 | 있음 — 전체 요청 수는 평소 범위. 리포트 조회만 14:19 ~ 14:37 0건 → 14:38 부터 분당 3 · 3 · 10 · 16건, client 1개 → 4개 |
| 그 밖 | 14:38:29 재배포(distribution 5)는 같은 종류가 이 날 08:24 부터 29건 있는 사건이라 평소 범위로 봤다. 목록에는 시각만 적었다 |

## 3. 가설과 검증

> 3.1 ~ 3.4 는 가설과 검증 방법이다. 수집 로그 · 저장소로 확인할 수 있는 것만 실행해 3.2 표에 "로그 확인 결과" 열로 적었고, 실행한 명령과 숫자는 3.5 에 있다. 운영 DB · nginx 설정 · 다른 인스턴스 로그가 필요한 확인은 아직 하지 않았다.
> 줄번호 표기: `rca:<줄>` = 이 문서(`docs/rca/a-connection-pool.md`)의 줄. `app:` · `access:` · `error:` · `slow:` 는 2.1 범례의 로그 파일 줄.
> 운영 DB · 운영 서버에서 실행할 확인은 직접 접속하지 않고 담당자(DBA · 인프라)에게 결과를 요청한다.

### 3.1 증상과 설명해야 할 관찰

**증상 (원인이라고 부르지 않는다)**

- 커넥션 풀 획득 실패 ERROR 49건 `Connection is not available … (total=5, active=5, idle=0, waiting=4)` (rca:147) — 풀 5개가 모두 대여 중이었다는 **결과**다. 질문은 "왜 5개가 동시에, 오래 대여되어 있었나"다.
- nginx `no live upstreams` 68건 · 502 82건 · 504 55건 (rca:149 ~ 145) — 같은 상태를 nginx 쪽에서 본 결과다.

**가설이 설명해야 할 관찰**

| # | 관찰 | 근거 |
|---|---|---|
| O1 | 커넥션 1개가 10초 이상 대여됐다. 첫 경고 14:37:52.199 → 반환 14:38:01.902, 경고 기준 10초 → 대여 시작은 14:37:42 이전 | rca:138, rca:140, rca:111 |
| O2 | 누수 경고 134건과 반환 INFO 134건이 같다 — 영구 누수가 아니라 장시간 점유 후 반환 | rca:141 |
| O3 | 전체 요청 수는 평소 범위, 리포트 조회만 0 → 16건/분, client 1 → 4개 | rca:62, rca:145, rca:195 |
| O4 | 같은 리포트라도 14:10 ~ 14:18(5건) · 14:59 ~ 15:01(3건)에는 누수 경고가 없다 | rca:133, rca:134, rca:162 |
| O5 | 두 번째 upstream 10.20.1.22 는 14:42:01 에 처음 나오고 처음부터 `Connection refused` | rca:151 |
| O6 | 로그에 재시작 · 설정 재적재 기록이 없다 | rca:194 |

### 3.2 가설 목록

| ID | 계층 | 가설 한 문장 | 로그 확인 결과 | 이유 (한 줄) | 판단 불가일 때 필요한 로그 |
|---|---|---|---|---|---|
| H1 | 애플리케이션 코드 | 운영에서 돈 `ReportService.buildClassReport` 가 `@Transactional` 안에서 오래 걸리는 작업(SHA-256 반복 서명 · 배포별 제출 조회)을 하며 커넥션을 쥐고 있어, 리포트 요청 몇 건만 겹쳐도 풀 5개가 찼다. | **유지** | 누수 경고 134건 모두 `ReportController.classReport` 의 트랜잭션 시작에서 빌린 커넥션이고, 장애 당시 코드(`fb2f0c4^`)는 `@Transactional` 안에서 배포별 조회 + 서명 20만 회를 돌며, 점유 시간은 15.5 ~ 21.5초다 (3.5 H1) | (유지지만 점유 시간 중 어느 단계가 길었는지는 로그로 못 가름) 점유 중 스레드 덤프, 단계별 소요 시간 로그 |
| H2 | DB · 쿼리 | 운영 `submission` 의 `distribution_id` 조건 조회가 인덱스를 타지 않아(128만 행 검사 → 4행 반환) 쿼리 자체가 약 10초 걸렸고, 리포트 요청마다 이 쿼리가 커넥션을 점유했다. | **기각** | 구간 안 리포트 179건(배포 3개씩 → 제출 조회 약 537회) 중 slow log 는 1건뿐이고, 기록 기준이 2.04초 이하라 나머지 조회는 각 2초 미만 → 요청당 DB 시간은 최대 약 10초로, 최소 점유 15.5초를 설명하지 못한다 (3.5 H2) | — (14:37:42 의 9.8초 전체 스캔 1건은 별도 사실로 남김. 인덱스 유무는 DBA 확인 필요) |
| H3 | 트래픽 | 14:38 이후 여러 교사(client 4개)의 리포트 동시 조회와 같은 client 의 반복 조회가 겹쳐 동시 리포트 처리 수가 풀 크기 5를 넘었다. 평소에는 리포트가 드물어 같은 처리 시간이 드러나지 않았다. | **유지** | 리포트 조회는 장애 구간 밖에서 분당 최대 2건(총 58건, 누수 경고 0건), 구간 안에서 분당 10 ~ 19건이고, 누수 경고 134건이 모두 리포트 요청이다 (3.5 H3) | — |
| H4 | 인프라 | upstream 그룹 `item_bank_api` 의 10.20.1.22 가 요청을 받지 못하는 상태여서 모든 요청이 10.20.1.21 한 대(풀 5개)에 몰렸다. 두 대였다면 같은 부하가 풀 10개에 나뉘었다. | **기각** | nginx 가 .22 에 연결을 시도한 14번은 모두 .21 일시 제외 직후 3초 이내(11번은 1초 이내)였고 그 전 하루 동안은 시도 0번 → .21 한 대가 평소에도 전부 처리하는 구성으로 보여, "한 대로 몰림"은 장애 중 생긴 변화가 아니다 (3.5 H4) | (설정 확인 전 잠정) nginx `upstream` 설정(`backup` 여부), 10.20.1.22 의 프로세스 · app 로그. 접근 로그에 `$upstream_addr` 가 있었다면 요청별 인스턴스로 바로 판단 가능 |
| H5 | 배포 · 설정 | 장애 당시 운영 빌드 · 설정(이관 전 버전 코드, Hikari 최대 5 · 대기 3초)이 리포트 처리 시간에 비해 작은 풀로 배포되어 있었다 — 최근 설정 축소나 리포트 경로 변경 배포가 있었다. | **판단 불가 (로그 부족)** | 수집 app.log 에 기동 · 버전 로그가 0줄이고, 저장소 이력은 09-22 첫 커밋부터라 장애(09-16) 이전의 설정 · 코드 변경을 볼 수 없다 (3.5 H5) | 09-01 ~ 09-16 배포 이력(커밋 · 이미지 태그 · 시각), 직전 기동 시점의 app.log(`Started …`, `itembank-pool - Start completed`, 빌드 버전), 운영 설정 저장소 · 환경 변수 변경 이력 |

H1 · H2 는 "왜 요청 1건이 커넥션을 10초 가까이 쥐었나"(O1)를, H3 · H4 · H5 는 "왜 그 점유가 풀 고갈로 번졌나"를 설명하려는 가설이다. 서로 배타적이지 않으므로 둘 이상이 함께 맞을 수 있다.

### 3.3 가설별 근거 · 반증 · 확인

#### H1 — 애플리케이션 코드: 트랜잭션 안의 오래 걸리는 작업

| 항목 | 내용 |
|---|---|
| 가설 | 운영에서 돈 `buildClassReport` 가 `@Transactional` 안에서 SHA-256 반복 서명 · 배포별 제출 조회를 하며 커넥션을 쥐어, 리포트 요청 몇 건만 겹쳐도 풀 5개가 찼다. |
| 지지 근거 | rca:138 — 대여 스택이 `TransactionInterceptor` → `ReportService.buildClassReport` → `ReportController.classReport` (app:1243 ~ 1255). 트랜잭션 시작 시점에 커넥션을 빌렸다.<br>rca:140 — 반환이 `built class report` 로그 2ms 뒤(app:1258 → 1259). 리포트 처리가 끝날 때까지 커넥션을 쥐고 있었다.<br>rca:141 — 경고 134건 = 반환 134건(O2).<br>rca:146 — 경고 대상 커넥션이 5개(풀 크기)까지 늘어난 직후 rca:147 첫 획득 실패. |
| 반증 조건 | ① 장애 당시 배포된 `buildClassReport` 에 트랜잭션 경계가 없거나, 서명 계산 · 제출 조회가 트랜잭션 밖에 있다.<br>② 같은 데이터로 재현했을 때 대여 ~ 반환 시간 대부분이 DB 응답 대기(소켓 read)이고 서명 계산은 1초 미만이다 → 점유 시간은 H2 쪽 설명이 된다.<br>③ O4: 부하가 없을 때의 리포트(14:10 ~ 14:18, 14:59 ~ 15:01)는 10초 미만으로 끝났다. 처리 시간이 부하와 무관하게 10초라면 이때도 경고가 나와야 한다. "부하 시 CPU 경합으로 서명이 길어졌다"는 보조 설명이 확인되지 않으면 H1 단독으로는 성립하지 않는다. |
| 확인 방법 | ① 장애 당시 코드: 스택의 패키지가 `com.example.assignment.ReportService`(4계층 이관 전, app:1243). `git log --oneline -- 'modern/api/src/main/java/com/example/assignment/ReportService.java'` 로 이관 전 버전을 찾고 `git show fb2f0c4^:modern/api/src/main/java/com/example/assignment/ReportService.java` 에서 `@Transactional` · `sign(` · 배포별 조회 위치를 본다. 운영 빌드가 정확히 어느 커밋인지는 배포 이력(태그 · 이미지 다이제스트)으로 맞춘다.<br>② 점유 시간 분포: `grep -E 'leak detection triggered\|returned to the pool' incident-logs/a-connection-pool/app.log \| awk '{match($0,/Connection@[0-9a-f]+/); c=substr($0,RSTART,RLENGTH); if ($0 ~ /triggered/) w[c]=$1; else print c, w[c], $1}'` — 점유 시간 ≈ 10초 + (반환 − 경고).<br>③ 재현: 이관 전 버전을 로컬에 띄우고 `time curl -s localhost:8080/api/classes/3/report` 를 1건 · 동시 5건으로 실행하며 `jstack <pid>` 로 커넥션을 쥔 스레드가 `ReportService.sign` 에 있는지, DB read 에 있는지 본다. |

#### H2 — DB · 쿼리: `submission` 조회의 전체 스캔

| 항목 | 내용 |
|---|---|
| 가설 | 운영 `submission` 의 `distribution_id` 조건 조회가 인덱스를 타지 않아 쿼리 하나가 약 10초 걸렸고, 리포트 요청마다 이 쿼리가 커넥션을 점유했다. |
| 지지 근거 | rca:136 · rca:137 — 14:37:42 시작, 9.8초, `Rows_examined: 1284310` / `Rows_sent: 4` (slow:15 ~ 21). 4행을 얻으려고 128만 행을 읽었다.<br>rca:136 · rca:138 — 느린 쿼리 시작(14:37:42)과 첫 경고 커넥션의 대여 추정 시각(14:37:42 이전, O1)이 겹친다.<br>rca:143 — 같은 `distribution_id=5` 에 14:38:29 재배포가 있었다.<br>rca:192 — 분석 구간 안의 유일한 slow log 항목이며, 이 날 다른 두 항목(2.3초 · 2.0초)보다 길다. |
| 반증 조건 | ① 운영 `submission` 에 `distribution_id` 인덱스가 있고 `EXPLAIN` 이 그 인덱스를 쓴다.<br>② 장애 구간 리포트 조회 179건(rca:184) 중 slow log 항목은 1건뿐이다. `long_query_time` 이 10초보다 충분히 작은데도(이 날 2.0초 항목이 기록됨, rca:137) 다른 리포트의 제출 조회가 기록되지 않았다면, 요청마다 이 쿼리가 커넥션을 오래 쥐었다는 설명은 틀린다.<br>③ slow:17 의 `Thread_id 48211` 세션이 app 의 `@6e8f14d3` 커넥션(rca:138)과 다른 커넥션이다. |
| 확인 방법 | ① DBA 에게 요청: `SHOW INDEX FROM submission;` · `EXPLAIN SELECT … FROM submission WHERE distribution_id=5 ORDER BY student_id;` · `SELECT COUNT(*) FROM submission;` · `SHOW GLOBAL VARIABLES LIKE 'long_query_time';` 결과. 로컬 스키마 `db/mariadb/init/01-schema.sql:112` 에는 `idx_submission_distribution` 이 있으므로 운영과 다른지가 핵심이다.<br>② `grep -n -E '^# (Time\|Query_time)\|^SET timestamp\|^select' incident-logs/a-connection-pool/mariadb-slow.log` 로 구간 안 항목 수를 다시 센다.<br>③ DBA 에게 14:37 전후 `performance_schema` / `information_schema.PROCESSLIST` 이력(있다면)으로 48211 세션의 클라이언트 포트 · 시작 시각을 요청해 app 커넥션과 대조한다. |

#### H3 — 트래픽: 리포트 조회 동시성 증가

| 항목 | 내용 |
|---|---|
| 가설 | 14:38 이후 여러 교사의 리포트 동시 조회와 같은 client 의 반복 조회가 겹쳐 동시 리포트 처리 수가 풀 크기 5를 넘었다. |
| 지지 근거 | rca:134 — 평소 리포트는 14:10 ~ 14:18 사이 5건, 그 뒤 19분간 0건.<br>rca:142 — 10.20.3.12 가 class 3 리포트를 26 ~ 30초 간격으로 반복 조회.<br>rca:145 — 리포트 client 1 → 4개, 분당 0 → 3 → 3 → 10 → 16건. 전체 요청은 평소 범위(rca:62).<br>rca:144 · rca:146 — 경고 상태 커넥션 수가 1 → 2 → 5개로 리포트 증가와 같이 늘었다.<br>rca:161 · rca:182 — 리포트가 분당 0건이 된 14:52 부터 모든 신호가 사라졌다. |
| 반증 조건 | ① 이 날 다른 시간대(06:43 ~ 14:37)에 리포트 조회가 분당 10건 이상인 분이 있었는데 그때는 누수 경고 · 5xx 가 없었다.<br>② 누수 경고의 대여 스택 다수가 리포트가 아닌 다른 엔드포인트다(리포트가 아닌 요청이 풀을 쥐고 있었다).<br>③ 경고 상태 커넥션이 5개에 이른 시각이 리포트 조회 증가(14:39:54 ~)보다 앞선다. |
| 확인 방법 | ① `grep '/report HTTP' incident-logs/a-connection-pool/nginx-access.log \| awk '{print substr($4,14,5)}' \| sort \| uniq -c \| sort -rn \| head -20` — 하루 중 리포트 조회가 많았던 분과 그 분의 경고 유무를 대조.<br>② `grep -A12 'leak detection triggered' incident-logs/a-connection-pool/app.log \| grep -oE 'com\.example\.[A-Za-z.]+Controller\.[A-Za-z]+' \| sort \| uniq -c`<br>③ `grep -n 'leak detection triggered' incident-logs/a-connection-pool/app.log \| awk '{match($0,/Connection@[0-9a-f]+/); print $1, substr($0,RSTART,RLENGTH)}' \| head -30` 와 2.3 표의 리포트 조회 수를 분 단위로 맞춰 본다.<br>④ 반복 조회: referer 가 `/teacher/classes/3/report` 이므로 LMS 화면 담당에게 자동 새로고침 · 응답 지연 시 재시도 로직이 있는지 확인한다. |

#### H4 — 인프라: 두 번째 upstream 부재로 한 대에 부하 집중

| 항목 | 내용 |
|---|---|
| 가설 | upstream 10.20.1.22 가 요청을 받지 못하는 상태여서 모든 요청이 10.20.1.21 한 대(풀 5개)에 몰렸다. |
| 지지 근거 | rca:151 — 10.20.1.22 는 이 파일에서 14:42:01 에 처음 나오고 처음부터 `connect() failed (111: Connection refused)`. 프로세스가 그 포트에서 listen 하지 않았다는 뜻이다.<br>rca:150 · rca:153 — .21 일시 제외 직후 `no live upstreams`. 남은 정상 upstream 이 없었다.<br>rca:194 — 수집된 app.log 에는 재시작 기록이 없다(이 app.log 가 어느 인스턴스 것인지도 확인 대상).<br>`mariadb-slow.log:2` · `:9` · `:16` — DB 접속 클라이언트가 세 항목 모두 10.20.1.21. |
| 반증 조건 | ① nginx 설정에서 10.20.1.22 가 `backup` 이거나 평소 트래픽을 받지 않는 구성이다(원래 한 대 운영이었다).<br>② 10.20.1.22 가 14:42 직전까지 요청을 정상 처리했다(그 서버의 app.log 에 14:07 ~ 14:41 처리 기록이 있다). 이 경우 .22 의 거부는 장애의 결과이거나 별개 사건이다.<br>③ 장애 전 시간대에 nginx 가 .22 로 요청을 보냈고 성공했다(접속 거부가 장애 중에만 있었다). |
| 확인 방법 | ① nginx 호스트에서 `nginx -T 2>/dev/null \| grep -A8 'upstream item_bank_api'` — 서버 목록 · `backup` · `max_fails` · `fail_timeout` 확인(인프라 담당).<br>② `grep -n '10.20.1.22' incident-logs/a-connection-pool/nginx-error.log \| head -3` 와 `grep -c '10.20.1.22' incident-logs/a-connection-pool/nginx-access.log` — 현재 access 로그 형식에는 `$upstream_addr` 가 없어 요청별 인스턴스를 구분할 수 없다. 로그 형식 보강 필요 여부도 함께 기록한다.<br>③ 인프라 담당에게 10.20.1.22 의 09-16 컨테이너 · 프로세스 이력(`docker ps -a` · `docker inspect --format '{{.State.StartedAt}} {{.State.FinishedAt}}'` 또는 `journalctl -u <서비스> --since '2026-09-16 06:00'`)과 그 인스턴스의 app.log 를 요청한다. |

#### H5 — 배포 · 설정: 리포트 처리 시간에 비해 작은 풀

| 항목 | 내용 |
|---|---|
| 가설 | 장애 당시 운영 빌드 · 설정(이관 전 버전 코드, Hikari 최대 5 · 대기 3초)이 리포트 처리 시간에 비해 작은 풀로 배포되어 있었다 — 장애 전에 풀 축소나 리포트 경로 변경 배포가 있었다. |
| 지지 근거 | rca:147 — 런타임 풀 상태 `total=5`, 대기 `3000ms`. 대기 4건만으로 획득 실패가 났다.<br>rca:111 — `leak-detection-threshold: 10000` 이 운영에 켜져 있다(설정이 저장소 값과 같다는 간접 근거).<br>rca:138 — 운영 스택의 패키지 `com.example.assignment` 는 저장소 현재 구조(`com.example.application.assignment`)와 다르다. 운영 빌드가 저장소 HEAD 와 다른 버전이다. |
| 반증 조건 | ① 운영 Hikari 값(최대 5 · 대기 3초)과 리포트 처리 코드가 장애 전 수주 동안 바뀌지 않았고, 그 기간에도 비슷한 리포트 동시 조회가 장애 없이 처리됐다 → 설정은 조건일 뿐 변화 요인이 아니다.<br>② 장애 직전(예: 09-15 ~ 09-16) 배포 · 설정 변경 이력이 없다. O6 처럼 당일 재시작 기록도 없다. |
| 확인 방법 | ① `git log -p --since=2026-08-01 --until=2026-09-17 -- modern/api/src/main/resources/application.yml 'modern/api/src/main/java/com/example/assignment/'` — 장애 전 설정 · 리포트 코드 변경 이력.<br>② 배포 담당에게 09-01 ~ 09-16 배포 이력(커밋 · 이미지 태그 · 시각)과 운영 설정 저장소(환경 변수 `SPRING_DATASOURCE_HIKARI_*` 포함) 변경 이력을 요청한다.<br>③ 운영 app.log 의 직전 기동 로그에서 `itembank-pool - Start completed` 와 버전 정보를 찾는다(이번 수집분에는 기동 로그가 없어 이전 날짜 파일이 필요하다). |

### 3.4 확인 순서 제안

판정 전에 비용이 적고 여러 가설을 함께 가르는 것부터 확인한다.

1. 저장소 · 수집 로그만으로 되는 것: H1 ①② · H2 ② · H3 ①②③ · H5 ① — 로컬 명령으로 바로 실행 가능.
2. 담당자 요청이 필요한 것: H2 ①③(DBA) · H4 ①③(인프라) · H5 ②(배포).
3. 재현: H1 ③ — 1 · 2 의 결과로 H1 · H2 중 무엇이 O1(10초 점유)을 설명하는지 좁힌 뒤 실행한다.

### 3.5 로그 확인 실행 기록 (2026-10-06)

> 수집 로그(`incident-logs/a-connection-pool/`)와 저장소만 썼다. 명령은 저장소 루트에서 실행했고 로그 경로는 `L=incident-logs/a-connection-pool` 로 줄여 적는다. 표 안의 `\|` 는 `|` 이다.
> 점유 시간 계산 근거: Hikari 는 대여 후 `leak-detection-threshold`(10초)가 지나면 경고하므로 대여 시각 = 경고 시각 − 10초, 점유 시간 = 10초 + (반환 − 경고).

| 가설 | 명령 | 결과 (숫자 그대로) |
|---|---|---|
| H1 ② 점유 시간 | `grep -E 'leak detection triggered\|returned to the pool' $L/app.log \| awk '{match($0,/Connection@[0-9a-f]+/); c=substr($0,RSTART,RLENGTH); if ($0 ~ /triggered/) w[c]=$1; else print c, w[c], $1}'` → 점유 시간 계산 후 정렬 | 경고-반환 짝 134개. 점유 시간 min 15.538초 · p50 18.929초 · p90 21.002초 · max 21.508초. 첫 짝 `@6e8f14d3` 14:37:52.199 → 14:38:01.902 (점유 약 19.7초, 대여 약 14:37:42.2) |
| H1 ① 장애 당시 코드 | `git show fb2f0c4^:modern/api/src/main/java/com/example/assignment/ReportService.java \| grep -n -E '@Transactional\|sign\(\|findBy\|SIGNATURE_ROUNDS'` | 41행 `@Transactional` → 42행 `buildClassReport`. 54행 배포마다 `findByDistributionIdOrderByStudentIdAsc`, 71행 `sign(…)` 이 같은 메서드 안(트랜잭션 안). 23행 `SIGNATURE_ROUNDS = 200_000` |
| H1 ③ · O4 | `grep '/report HTTP' $L/nginx-access.log \| awk '{m=substr($4,14,5); if (m<"14:38"\|\|m>"14:51") n++} END{print n}'` / `grep -c 'leak detection triggered' $L/app.log` | 장애 구간 밖 리포트 조회 58건. 누수 경고 134건은 모두 14:37:52 ~ 14:51:37 안 → 구간 밖 58건은 점유 10초 미만 |
| H2 ② slow log 항목 | `grep -n -E '^# (Time\|Query_time)' $L/mariadb-slow.log` | 하루 3건: 2.318446초(41,822행) · 2.041903초(5행) · 9.812417초(1,284,310행). 장애 구간 안은 9.8초 1건 |
| H2 ② 리포트당 조회 수 | `grep 'built class report' $L/app.log \| grep -oE '[0-9]+ distributions' \| sort \| uniq -c` | `190 3 distributions` — 리포트 190건 모두 배포 3개 → 장애 당시 코드 기준 리포트당 쿼리 5개(학급 1 · 배포 목록 1 · 제출 3) |
| H3 ① 분당 리포트 | `grep '/report HTTP' $L/nginx-access.log \| awk '{print substr($4,14,5)}' \| sort \| uniq -c \| sort -rn` (구간 안 / 밖 나눠 실행) | 구간 안 상위: 14:43 · 14:46 19건, 14:41 · 14:49 16건, … 14:40 · 14:50 10건, 14:51 5건. 구간 밖 최대: 12:20 · 14:59 · 15:20 각 2건 |
| H3 ② 누수 스택의 컨트롤러 | `grep -A12 'leak detection triggered' $L/app.log \| grep -oE 'com\.example\.[A-Za-z.]+Controller\.[A-Za-z]+' \| sort \| uniq -c` | `134 com.example.assignment.ReportController.classReport` (다른 컨트롤러 0) |
| H3 ③ 순서 | 2.2 · 2.3 표 대조 | 리포트 client 증가 14:39:54 ~ → 경고 커넥션 5개 도달 14:40:43 (rca:145, rca:146). 증가가 먼저 |
| H4 ② .22 등장 | `grep -n '10.20.1.22' $L/nginx-error.log` / `grep -c '10.20.1.22' $L/nginx-access.log` | error 로그 14줄, 첫 14:42:01(11행) · 마지막 14:50:52(159행). access 로그 0줄(`$upstream_addr` 없음). 14:41 이전 error 로그는 `[info]` 3 · `[warn]` 2 뿐(upstream 오류 0) |
| H4 ② .21 제외와의 시각 관계 | `awk '{split($2,a,":"); s=a[1]*3600+a[2]*60+a[3]} /temporarily disabled/ {last=s} /10\.20\.1\.22/ {d=s-last; n++; if (d<=1) ok++; if (d>mx) mx=d} END{print n, ok, mx}' $L/nginx-error.log` | `refused=14 within_1s=11 max_gap=3` — .22 시도 14번 모두 직전 .21 제외로부터 3초 이내 |
| H5 ③ 기동 · 버전 로그 | `grep -c -E 'Started [A-Za-z]+ in\|Starting [A-Za-z]+ using\|Tomcat started' $L/app.log` | 0 |
| H5 ① 설정 · 코드 이력 | `git log --format='%h %ci %s' -- modern/api/src/main/resources/application.yml modern/api/src/main/java/com/example/assignment/` | 가장 이른 커밋 `1260542 2026-09-22`(저장소 초기 구성). 장애일 09-16 이전 이력 없음 |

**판정 후 남은 것 (판정을 바꾸지는 않지만 기록)**

- H1 · H3 유지는 서로 맞물린다: 같은 리포트가 구간 밖에서는 10초 안에 끝났고(58건, 경고 0), 동시 조회가 많을 때만 15 ~ 21초로 늘었다. 늘어난 이유(서명 계산의 CPU 경합인지, 커넥션 대기 중인 다른 리포트 때문인지)는 로그로 가를 수 없다 — 스레드 덤프나 단계별 소요 시간 로그가 필요하다.
- H2 기각은 "리포트 요청마다 느린 쿼리가 커넥션을 쥐었다"는 설명을 기각한 것이다. 14:37:42 의 `distribution_id=5` 9.8초 전체 스캔(128만 행) 1건은 사실로 남는다. 왜 그 1건만 느렸는지(같은 번호의 14:38:29 재배포, rca:143)와 운영 인덱스 유무는 DBA 확인이 필요하다.
- H4 기각은 "평소 두 대 → 장애 중 한 대"라는 변화를 기각한 것이다. .21 이 제외될 때 넘겨받아야 할 .22 가 연결을 거부했다는 사실(장애 완화 실패)은 영향 확대 요인으로 따로 남긴다. nginx 설정을 보기 전까지는 잠정이다.

### 3.6 판정

> 3.5 에서 유지된 H1 · H3 을 코드 · 설정과 대조했다. 벤치마크 · 테스트는 실행하지 않았고, 설정값은 파일에서 읽은 값만 쓴다.
> **장애 당시 코드 위치**: 로그 스택의 클래스는 `com.example.assignment.ReportService` · `com.example.assignment.ReportController` 다(app:1253 ~ 1254). 이 패키지는 현재 HEAD 에 없고 4계층 이관 커밋 `fb2f0c4` 직전까지 있었으므로 `fb2f0c4^` 의 파일을 읽었다. 아래 `fb2f0c4^:<경로>:<줄>` 은 그 커밋의 파일 줄이다(`git show fb2f0c4^:<경로> | cat -n` 으로 열 수 있다).
> 경로 줄임: `A = modern/api/src/main/java/com/example/assignment`, `Y = modern/api/src/main/resources/application.yml`.

#### 3.6.1 단서 → 코드 · 설정

| 로그 단서 | 찾은 코드 · 설정 |
|---|---|
| `ReportController.classReport(ReportController.java:22)` (app:1254) | `fb2f0c4^:A/ReportController.java:20 ~ 22` — `GET /{id}/report` 가 `reportService.buildClassReport(id)` 를 그대로 호출 |
| `ReportService$$SpringCGLIB$$0.buildClassReport` ← `TransactionInterceptor` (app:1250 ~ 1253) | `fb2f0c4^:A/ReportService.java:41 ~ 42` — `@Transactional public ClassReport buildClassReport(Integer classId)` |
| `built class report for class 3: 3 distributions, 10 submissions` (app:1258) | `fb2f0c4^:A/ReportService.java:73 ~ 74` — 서명 계산(71행) **뒤**, 메서드 끝에서 찍는 로그 |
| slow:21 `… from submission s1_0 where s1_0.distribution_id=5 order by s1_0.student_id` | `fb2f0c4^:A/SubmissionRepository.java:8` `findByDistributionIdOrderByStudentIdAsc` — 호출부 `fb2f0c4^:A/ReportService.java:52 ~ 54` (배포마다 1회) |
| `itembank-pool … after 3000ms (total=5, active=5, idle=0, waiting=4)` (app:1521 ~ 1523) | `Y:10` `pool-name: itembank-pool`, `Y:14` `maximum-pool-size: 5`, `Y:15` `connection-timeout: 3000` |
| `ProxyLeakTask` 경고(경고 = 대여 + 10초) | `Y:18` `leak-detection-threshold: 10000` |

설정 파일은 `fb2f0c4^` 와 HEAD 사이에 차이가 없다(`git diff fb2f0c4^ HEAD -- Y` 출력 없음). `Y:11 ~ 12` 주석은 "커넥션 풀 — 운영 값과 같게 맞춘다. 풀이 작고 대기 시간이 짧아, 트랜잭션을 오래 쥐는 코드가 있으면 금방 고갈된다." 이다. 로그의 런타임 값(`total=5`, `3000ms`, 10초 경고)이 파일 값과 같다.

#### 3.6.2 가설별 대조

| 가설 | 판정 | 근거 (파일:줄) |
|---|---|---|
| H1 앱 코드 — 트랜잭션 안의 오래 걸리는 작업이 커넥션을 쥠 | **코드 · 설정이 가설을 지지한다** | ① `fb2f0c4^:A/ReportService.java:41` `@Transactional` — 메서드 전체가 하나의 트랜잭션이라 첫 조회(43행)에서 빌린 커넥션을 메서드가 끝날 때까지 쥔다. 로그의 대여 지점(`JpaTransactionManager.doBegin`, app:1250)과 반환 시점(`built class report` 2ms 뒤, app:1258 → 1259)이 이 경계와 맞는다.<br>② `fb2f0c4^:A/ReportService.java:69 ~ 71` — 주석 원문 "TODO: 트랜잭션 밖으로 — 외부 집계 시스템 호출을 흉내내는 긴 루프. DB 작업은 위에서 끝났는데 커넥션(트랜잭션)을 쥔 채로 돈다." 바로 아래에서 `sign(payload)` 호출.<br>③ `fb2f0c4^:A/ReportService.java:23`, `:85 ~ 92` — `SIGNATURE_ROUNDS = 200_000`, SHA-256 을 20만 번 반복하는 CPU 작업. DB 와 무관한 시간이 트랜잭션 안에 들어 있다.<br>④ `fb2f0c4^:A/ReportService.java:52 ~ 54` — 배포마다 제출 조회 1회(N+1). 로그상 리포트는 모두 배포 3개(3.5)라 리포트당 쿼리 5개. 3.5 H2 에서 이 쿼리들이 각 2.04초 미만임을 확인했으므로 점유 시간(15.5 ~ 21.5초)의 나머지는 서명 계산과 그 대기가 차지한다.<br>⑤ 비교: 현재 HEAD `modern/api/src/main/java/com/example/application/assignment/ReportService.java:47 ~ 51` 은 `@Transactional` 을 지우고 "오래 걸리는 서명 계산은 커넥션을 쥐지 않은 채로 돈다" 고 적었다(커밋 `1cab966`). 같은 지점을 결함으로 보고 고친 이력이다. |
| H3 트래픽 — 리포트 동시 조회가 풀 크기를 넘음 | **코드 · 설정이 가설을 지지한다** (반복 조회의 출처는 **관련 코드를 찾지 못했다**) | ① `Y:14` `maximum-pool-size: 5`, `Y:15` `connection-timeout: 3000` — 동시 점유 5개 이상이면 다음 요청은 3초 뒤 실패. 로그 `active=5, waiting=4`(app:1523)와 맞는다.<br>② 로그 숫자 × 설정값 대조(계산만, 별도 실행 없음): 구간 안 리포트 분당 10 ~ 19건 × 점유 중앙값 18.9초 ÷ 60 ≈ 동시 3.2 ~ 6.0개 → 풀 5개에 닿는다. 구간 밖 분당 최대 2건 × 10초 미만 ÷ 60 < 0.4개 → 닿지 않는다.<br>③ 리포트 동시성을 줄이는 장치가 없다: `fb2f0c4^` 의 `modern/api/src/main` 에서 `Cacheable · EnableCaching · RateLimit · Bulkhead · Semaphore · @Async · tomcat · threads` 를 찾았고 0건, `fb2f0c4^:A/ReportController.java:20 ~ 22` 도 매 요청 새로 계산한다. 이 결과는 "그런 장치를 찾지 못했다"는 뜻이고, 설정에 없는 기본값은 추측하지 않는다.<br>④ 같은 client 의 26 ~ 30초 반복 조회(rca:142)는 referer 가 `https://lms.example.com/teacher/classes/3/report` 인 외부 LMS 화면이다. `modern/web/src` 에서 `report · setInterval · refetchInterval · retry` 0건 → **관련 코드를 찾지 못했다**(이 저장소 밖). |

#### 3.6.3 최종 판정

**채택: H1 — 학급 리포트 생성(`buildClassReport`)이 `@Transactional` 안에서 SHA-256 20만 회 서명과 배포별 조회를 하며 커넥션을 처리 끝까지 쥐었다.** 리포트 동시 조회가 늘자(H3, 촉발 요인) 커넥션 5개가 모두 리포트에 묶였고, 다른 API 의 커넥션 획득 실패(500)와 nginx 의 504 · 502 가 뒤따랐다.

H3 은 기각하지 않았다. 다만 "리포트 조회가 분당 10 ~ 19건"은 결함이 아니라 부하 조건이다. 같은 부하라도 커넥션을 쿼리 동안만 쥐는 코드였다면(현재 HEAD 의 구조, `…/application/assignment/ReportService.java:47 ~ 51`) 점유 시간이 풀 고갈로 이어지는 길이가 되지 않는다. 그래서 근본 원인이 아니라 **촉발 요인**으로 분류했다.

| 채택하지 않은 가설 | 상태 | 사유 |
|---|---|---|
| H2 DB · 쿼리 | 기각 | 구간 안 리포트 179건(제출 조회 약 537회) 중 slow log 는 1건, 나머지는 각 2.04초 미만이라 최소 점유 15.5초를 설명하지 못한다(3.5). 코드상 그 쿼리는 리포트의 배포별 조회(`fb2f0c4^:A/SubmissionRepository.java:8`)와 SQL 형태가 같아 H1 의 일부일 뿐이다. 9.8초 전체 스캔 1건은 별도 확인 대상(DBA)으로 남긴다. |
| H4 인프라 | 기각 (잠정) | 10.20.1.22 연결 시도 14번이 모두 .21 제외 직후 3초 이내이고 그 전에는 0번이라, "두 대 → 한 대" 변화가 장애 중에 없었다(3.5). .22 가 연결을 거부해 장애를 넘겨받지 못한 것은 영향 확대 요인으로 남긴다. nginx 설정 확인 전이라 잠정이다. |
| H5 배포 · 설정 | 판단 불가 → 채택 안 함 | 기동 · 배포 로그가 없고 저장소 이력이 09-22 부터라 장애 전 변경을 볼 수 없다(3.5). 파일상 설정(`Y:14 ~ 18`)은 로그의 런타임 값과 같고 `fb2f0c4^` ~ HEAD 사이에 바뀌지 않아, 이 설정은 "변경"이 아니라 H1 이 고갈로 이어지는 **조건**이다. |

**확신 수준: 중간**

- 높게 볼 근거
  - 커넥션을 리포트 트랜잭션 시작부터 리포트 완료까지 쥐었다는 점은 로그 스택(app:1250 ~ 1254)과 반환 시각(app:1258 → 1259)이 직접 보여 준다.
  - 그 경계를 만드는 `@Transactional` 과 그 안의 서명 루프가 코드에 있고, 코드 주석도 같은 문제를 적어 두었다(`fb2f0c4^:A/ReportService.java:41`, `:69 ~ 71`).
  - 설정값이 로그의 런타임 값과 일치한다.
- 낮추는 이유
  - 운영 배포물이 `fb2f0c4^` 와 같다는 것은 패키지명으로만 맞췄다. 저장소 이력이 장애 이후(09-22)부터라 정확한 배포 버전 대조가 안 된다.
  - 15.5 ~ 21.5초 점유 중 서명 계산 · 동시 실행 시 CPU 경합 · 조회 시간이 각각 얼마인지 스레드 덤프나 단계별 소요 시간 로그가 없어 나누지 못했다.
  - 부하 없는 리포트가 10초 미만이었다는 것(3.5)만 알고 실제 소요 시간은 모른다.
- 높음으로 올리려면: 운영 배포 버전 확인, 이관 전 코드로 리포트 동시 실행 재현(스레드 덤프 포함). 이번 요청 범위에서는 하지 않았다.

## 4. 원인

> 3.6.3 의 채택 판정(H1 원인 · H3 촉발 요인)을 인과 순서로 다시 적는다. 문장 번호(B · D)는 5절의 근거와 짝을 이룬다.
> 경로 줄임은 3.6 과 같다: `A = modern/api/src/main/java/com/example/assignment`(장애 당시 코드, `fb2f0c4^`), `Y = modern/api/src/main/resources/application.yml`.

### 4.1 배경 원인 — 장애가 날 수 있는 조건

- **B1** 운영 빌드의 `buildClassReport` 는 메서드 전체가 `@Transactional` 이라 첫 조회에서 빌린 커넥션을 메서드가 끝날 때까지 쥔다.
- **B2** 그 트랜잭션 안에 DB 와 무관한 SHA-256 20만 회 서명 루프와 배포별 제출 조회(N+1)가 있다. 코드 주석도 "커넥션을 쥔 채로 돈다"고 적어 두었다.
- **B3** 커넥션 풀은 최대 5개, 획득 대기 3초다. 5건이 동시에 오래 쥐면 다른 요청은 3초 뒤 바로 실패한다.
- **B4** 리포트 요청에는 캐시 · 동시성 제한이 없어, 리포트 요청 수만큼 커넥션을 쥔다.
- **B5** (영향 확대, 잠정) nginx 의 두 번째 upstream 10.20.1.22 는 연결을 거부해, 10.20.1.21 이 제외됐을 때 요청을 넘겨받지 못했다.

### 4.2 직접 원인과 진행

- **D1** 14:37:42 경 리포트 1건이 커넥션을 빌려 19.7초 쥐었다 — 장애의 첫 장시간 점유다.
- **D2** 14:38 부터 리포트 조회가 분당 0 → 3 → 3 → 10 → 16건으로, 조회 client 가 1개 → 4개로 늘었다. 전체 요청 수는 평소 범위였다(촉발 요인).
- **D3** 이 구간의 리포트는 건당 커넥션을 15.5 ~ 21.5초(중앙값 18.9초) 쥐었다. 평소 리포트 58건은 10초 미만이었다.
- **D4** 10초 넘게 쥔 커넥션만 세어도 14:40:11 에 4개, 14:40:25 에 5개(풀 전체)가 동시에 점유됐다. — **직접 원인: 리포트 트랜잭션의 장시간 점유 × 동시 조회로 풀 5개 고갈.**
- **D5** 14:40:52 리포트와 무관한 `/api/units/M6-2/items` 가 3초 대기 후 커넥션을 얻지 못해 500 을 냈다. 이후 같은 실패 49건.
- **D6** 커넥션을 기다리던 리포트 요청은 nginx 응답 대기 시간을 넘겨 14:41:06 부터 504 를 받았다.
- **D7** 14:42:01 nginx 가 10.20.1.21 을 일시 제외하자 10.20.1.22 는 연결을 거부했고, 남은 upstream 이 없어 502(`no live upstreams`)가 났다.
- **D8** 리포트 조회가 분당 5건 → 0건으로 줄자 점유 커넥션이 반환되고, 14:51:49 마지막 반환 뒤 14:52 부터 모든 신호가 평소 수준으로 돌아왔다.

## 5. 근거 로그 줄

> 학생 식별자가 들어 있는 줄은 발췌하지 않았다. 로그 파일 줄이 아닌 집계 결과는 그 결과가 있는 이 문서의 절(3.5 등)을 적었다.

| 문장 | 파일:줄 | 발췌 |
|---|---|---|
| B1 | `fb2f0c4^:A/ReportService.java:41 ~ 42` | `@Transactional` / `public ClassReport buildClassReport(Integer classId) {` |
| B1 | `app.log:1250`, `:1253 ~ 1254` | `JpaTransactionManager.doBegin(…)` · `ReportService$$SpringCGLIB$$0.buildClassReport` · `ReportController.classReport(ReportController.java:22)` |
| B1 | `app.log:1258 ~ 1259` | `14:38:01.900 … built class report for class 3` → `14:38:01.902 … Previously reported leaked connection …@6e8f14d3 … was returned` |
| B2 | `fb2f0c4^:A/ReportService.java:69 ~ 71` | `// TODO: 트랜잭션 밖으로 — … 커넥션(트랜잭션)을 쥔 채로 돈다.` / `String signature = sign(payload.toString());` |
| B2 | `fb2f0c4^:A/ReportService.java:23`, `:52 ~ 54` | `SIGNATURE_ROUNDS = 200_000` / `submissionRepository.findByDistributionIdOrderByStudentIdAsc(distribution.getId())` |
| B3 | `Y:14 ~ 15`, `app.log:1522` | `maximum-pool-size: 5` · `connection-timeout: 3000` / `… after 3000ms (total=5, active=5, idle=0, waiting=…` |
| B4 | 3.6.2 H3 ③ (검색 결과) | `Cacheable · RateLimit · Bulkhead · Semaphore …` 0건 |
| B5 | `nginx-error.log:11` | `14:42:01 [error] … connect() failed (111: Connection refused) while connecting to upstream` |
| D1 | `app.log:1243` | `14:37:52.199 WARN … ProxyLeakTask : Connection leak detection triggered for …@6e8f14d3 on thread http-nio-8080-exec-6` |
| D1 | `mariadb-slow.log:15`, `:20` | `# Query_time: 9.812417 … Rows_examined: 1284310` / `SET timestamp=1789537062;` (14:37:42 KST 시작) |
| D2 | `nginx-access.log:18801`, `:18881` | `10.20.3.12 … 14:38:01 … "GET /api/classes/3/report …" 200` / `10.20.3.11 … 14:39:54 … "GET /api/classes/1/report …" 200` |
| D3 | 3.5 H1 ② · H1 ③ (명령 결과) | `점유 시간 min 15.538초 · p50 18.929초 · max 21.508초` / `장애 구간 밖 리포트 조회 58건`, 누수 경고 0 |
| D3 | 3.5 H3 ② | `134 com.example.assignment.ReportController.classReport` (다른 컨트롤러 0) |
| D4 | `app.log:1331`, `:1351`, `:1500` | `14:39:50.132 … @71d4b8e2` · `14:40:04.730 … @3f6c2a91` · `14:40:43.534 … @3f6c2a91 on thread …exec-5` |
| D4 | 경고−10초 ~ 반환 구간 겹침 계산 (이번 작성 시 awk 집계) | 동시 점유 최대 5, ≥4 첫 시각 14:40:11.4, =5 첫 시각 14:40:25.8 (10초 미만 점유는 빠진 하한값) |
| D5 | `app.log:1522 ~ 1523`, `nginx-access.log:18918` | `itembank-pool - Connection is not available, request timed out after 3000ms` / `unhandled exception on /api/units/M6-2/items` / `"GET /api/units/M6-2/items …" 500 162` |
| D6 | `nginx-error.log:6`, `nginx-access.log:18933` | `14:41:06 [error] … upstream timed out (110: Connection timed out) while reading response header` / `… /api/classes/3/report …" 504 167` |
| D7 | `nginx-error.log:10 ~ 12`, `nginx-access.log:18970` | `upstream server temporarily disabled` · `connect() failed (111: Connection refused)` · `no live upstreams` / `… /api/classes/2/report …" 502 157` |
| D8 | `app.log:5267`, 2.3 표 14:51 · 14:52 행 | `14:51:49.148 … Previously reported leaked connection …@6e8f14d3 … was returned` / 리포트 5 → 0, 5xx 3 → 0 |

## 6. 수정안 — 지금 당장 적용할 것

> 코드는 이번에 고치지 않았다. 아래는 제안이다.

| # | 대상 파일 · 위치 | 무엇을 | 어떻게 |
|---|---|---|---|
| F1 | 운영 빌드의 `A/ReportService.java:41` | `@Transactional` | 제거한다. 조회는 리포지토리 호출마다 짧은 읽기 트랜잭션으로 끝나게 한다. |
| F2 | 같은 파일 `:52 ~ 54` | 배포마다 하는 제출 조회(N+1) | 배포 id 목록으로 한 번에 읽는 `findByDistributionIds` 로 바꿔 리포트당 쿼리를 3개로 고정한다. |
| F3 | 같은 파일 `:71` | 서명 계산 | 조회가 모두 끝난 뒤, 커넥션을 쥐지 않은 상태에서 호출한다(서명 입력 순서는 유지). |

- F1 ~ F3 은 저장소 HEAD 에 이미 같은 형태로 있다: `modern/api/src/main/java/com/example/application/assignment/ReportService.java:45 ~ 82`(커밋 `1cab966`, 커밋 메시지상 `gradlew test` 105 통과 · characterization 29 통과 — 이번에 다시 실행하지 않음). 운영에는 **HEAD 빌드를 배포**하는 것을 1안, 이관 전 브랜치 핫픽스를 2안으로 한다.
- 배포 후 확인: 리포트 조회가 있는 시간대에 `ProxyLeakTask` 경고 0건, 리포트 5건 동시 호출 시 다른 API 의 획득 실패 0건.
- **설정(`Y:14 ~ 18`)은 바꾸지 않는다.** 풀을 늘려도 리포트 1건의 점유 시간(15 ~ 21초)은 그대로라 동시 리포트 수가 커지면 같은 방식으로 고갈된다. 이 값은 운영과 맞춘 값이고 저장소 규칙상 변경 금지 대상이다.
- 인프라(담당자 요청, 잠정): 10.20.1.22 가 `item_bank_api` 의 정식 멤버라면 프로세스를 기동하고 8080 listen 을 확인한다. `backup` 구성이라면 대기 상태가 실제로 기동되어 있는지 확인한다.

## 7. 재발 방지 — 같은 유형을 구조적으로 막는 장치

1. **규칙**: CLAUDE.md 금지 사항의 "`@Transactional` 안 외부 HTTP 호출 · 루프 안 개별 쿼리" 에 "**CPU 를 오래 쓰는 계산(해시 반복 · 서명 · 대량 직렬화)**" 을 추가하고, `convention-check` · `verify` 스킬 체크리스트에 "`@Transactional` 메서드 본문에 DB 와 무관한 긴 작업이 있는가" 항목을 넣는다.
2. **동시성 회귀 테스트**: H2 테스트 프로필에서 풀 크기를 운영과 같게(5) 두고 리포트 6건 이상을 동시에 호출하는 동안 다른 엔드포인트가 3초 안에 응답하는지 검증하는 테스트를 추가한다. 이번 결함처럼 단건으로는 드러나지 않는 점유 문제를 잡는다.
3. **쿼리 수 고정 테스트**: 리포트 같은 집계 경로는 배포 수를 늘려도 쿼리 수가 같다는 것을 검증한다(현재 `ReportServiceTest` 는 `findByDistributionIds` 1회 호출만 Mockito 로 본다). N+1 재도입을 막는다.
4. **무거운 엔드포인트 격리**: 리포트처럼 오래 걸리는 엔드포인트는 동시 실행 수를 풀 크기보다 작게 제한(세마포어)하거나, 결과를 학급 단위로 캐시 · 사전 계산해 한 종류 요청이 풀 전체를 잡지 못하게 한다. 라이브러리(resilience4j 등)를 쓰려면 의존성 추가 승인이 필요하다.
5. **nginx 이중화 점검**: upstream 각 대상에 헬스 체크를 두고, 배포 · 기동 체크리스트에 "모든 upstream 이 listen 중" 확인을 넣는다. 한 대가 빠지면 넘겨받을 대상이 실제로 살아 있어야 한다.
6. **TODO 배포 차단**: 이번 결함은 `TODO: 트랜잭션 밖으로` 주석이 달린 채 배포됐다. 리뷰 게이트에서 `@Transactional` 메서드 안의 `TODO` 를 반려 사유로 둔다.

## 8. 모니터링 항목

> "이번 경보 시각" 은 앞 절 로그 · 집계로 계산한 값이다. 지표 수집(Hikari · HTTP 메트릭 노출)에는 Spring Boot Actuator · Micrometer 의존성 추가가 필요하다(현재 `build.gradle` 에 없음, 추가 시 승인 필요).

| # | 지표 | 임계값 (제안) | 이번 경보 시각 (KST) | 첫 5xx(14:40:52) 대비 | 계산 근거 |
|---|---|---|---|---|---|
| M1 | Hikari 누수 경고 로그(`ProxyLeakTask` WARN) | 5분 안에 1건 이상 | **14:37:52** | 3분 앞 | `app.log:1243`. 평소 0건(1.2) |
| M2 | 커넥션 점유 시간 `hikaricp_connections_usage` max | 5초 초과 | **14:38:01** | 약 2분 50초 앞 | 사용 시간은 반환 시 기록 → 첫 장시간 점유 반환 `app.log:1259`(19.7초) |
| M3 | 활성 커넥션 `hikaricp_connections_active` | 최대의 80%(4개) 이상 30초 지속 | **14:40:41 경** (하한 추정) | 약 11초 앞 | 10초 넘게 쥔 커넥션만으로 14:40:11.4 에 4개 도달(5절 D4). 짧은 점유는 빠져 실제는 더 이를 수 있음 |
| M4 | 대기 스레드 `hikaricp_connections_pending` · 획득 실패 `hikaricp_connections_timeout_total` | pending > 0 30초 지속 / timeout 1건 이상 | **14:40:52** | 동시 | `app.log:1522` `waiting=4` 첫 기록. pending 시계열은 로그에 없음(9절) |
| M5 | API 5xx 비율 (nginx, 1분) | 5% 초과 | **14:41 분** | 약 1분 뒤 | 14:40 분 1/42 = 2.4%(미달), 14:41 분 9/42 = 21%(2.3 표) |
| M6 | nginx `no live upstreams` · upstream 일시 제외 | 1건 이상 즉시 | **14:42:01** | 약 1분 9초 뒤 | `nginx-error.log:10`(제외) · `:12`(no live 14:42:04) |
| M7 | upstream 대상별 헬스(10.20.1.22 listen) | 1분 이상 실패 | **판단 불가** | — | 14:42 전에는 nginx 가 .22 에 시도한 기록이 없어(3.5 H4) 언제부터 내려가 있었는지 모름 |

- 1차 경보는 **M1 · M2**(원인 신호, 사용자 영향 약 3분 전), 2차는 **M4 · M5**(영향 확인), M6 · M7 은 이중화 상태 확인용이다.
- M3 의 지속 조건은 리포트 1건 점유(15초 이상)보다 짧게 잡아야 의미가 있다.

## 9. 확인하지 못한 것

| 판단하지 못한 것 | 없던 로그 · 정보 | 다음을 위해 남길 것 |
|---|---|---|
| 운영 배포물이 `fb2f0c4^` 코드와 같은지 | 기동 · 버전 로그 0줄(3.5 H5 ③), 09-22 이전 저장소 이력 없음 | 기동 시 빌드 버전 · 커밋 해시 INFO 1줄, 배포 이력(시각 · 커밋 · 이미지 태그) 보관 |
| 점유 15.5 ~ 21.5초 중 서명 · CPU 경합 · 조회 · 커넥션 대기 각각의 몫 | 단계별 소요 시간, 스레드 덤프 | 리포트 단계별(조회 · 집계 · 서명) 소요 ms 를 DEBUG/INFO 로, 누수 경고 시 스레드 덤프 자동 수집 |
| 평소 리포트의 실제 응답 시간(10초 미만이라는 것만 앎) | access 로그에 `$request_time` · `$upstream_response_time` 없음 | nginx `log_format` 에 `$request_time $upstream_addr $upstream_response_time $upstream_status` 추가 |
| 요청이 어느 인스턴스로 갔는지, 10.20.1.22 가 언제부터 거부했는지 | `$upstream_addr` 없음, .22 의 app 로그 · 프로세스 이력 없음 | 위 `log_format` + 인스턴스별 app 로그 수집, upstream 헬스 체크 결과 로그 |
| nginx upstream 구성(`backup` · `max_fails` · 응답 대기 시간) | nginx 설정 미수집 | 장애 수집 대상에 `nginx -T` 결과 포함 |
| 풀 상태 시계열(active · pending) — 획득 실패 순간 외에는 모름 | Hikari 상태는 ERROR 메시지에만 나옴 | Hikari 메트릭 수집(8절) 또는 housekeeper 풀 상태 주기 로그 |
| 14:37:42 의 `distribution_id=5` 9.8초 전체 스캔 원인, 운영 인덱스 유무 | `EXPLAIN` · 인덱스 정보, 세션 대조 정보 | DBA 확인 결과(3.3 H2 확인 방법 ①③). slow log 는 KST 로 남기거나 타임존 명시 |
| 같은 client 의 26 ~ 30초 반복 조회 출처(자동 새로고침 · 재시도?) | 외부 LMS 화면 코드는 이 저장소 밖 | LMS 화면 담당 확인, 요청에 화면 · 재시도 여부를 나타내는 헤더 |
