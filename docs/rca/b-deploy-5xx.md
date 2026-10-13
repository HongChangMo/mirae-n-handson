# RCA — b-deploy-5xx (2026-09-17 v1.4.2 배포 후 재배포 API 5xx)

> 작성 범위: 이번 판은 **1. 수집 범위 · 2. 타임라인 · 3. 가설(표)** 까지만 담는다. 반증 실행 · 코드 대조 · 판정(4절)과 리포트 본문(요약 · 원인 · 수정안 등)은 아직 작성하지 않았다.
> 모든 시각은 KST. IP · 이메일 · 학생 식별자 · 토큰은 마스킹 규칙에 따라 가렸다.

별칭(이 리포트 안에서 고정):

- `API-1` — 배포 대상 애플리케이션 서버(`deploy-history.md:3` 의 `api-1`)
- `API-2` — 대기 호스트, 평소 정지(`deploy-history.md:3` 의 `api-2`)
- `교사단말A` — 재배포 API 를 호출한 교사 client 중 5xx 를 받은 단말
- `교사단말B` — 재배포 API 를 호출한 또 다른 교사 client

---

## 1. 수집 범위

### 1.1 파일별 개요

| 파일 | 줄 수 | 기록 기간(KST) | 시각 형식 | 타임존 |
|---|---:|---|---|---|
| `app.log` | 2,938 (로그 이벤트 1,240 + 스택트레이스 줄) | 2026-09-17 09:00:04 ~ 14:39:57 | ISO-8601 `2026-09-17T09:00:04.878+09:00` | `+09:00` 명시 |
| `nginx-access.log` | 20,365 | 2026-09-17 09:00:00 ~ 14:39:58 | combined `[17/Sep/2026:09:00:00 +0900]` | `+0900` 명시 |
| `deploy-history.md` | 39 | v1.3.9(09-01) ~ v1.4.2(09-17) | `2026-09-17 13:40:05` | 본문에 "KST" 명시(`deploy-history.md:3`) |

- 세 파일 모두 타임존이 명시돼 있어 변환 없이 KST 로 쓴다. 교차 확인: `app.log:194`(09:52:17 ERROR, `/api/distributions/41/redistribute`) ↔ `nginx-access.log:3150`(09:52:17 POST 같은 경로 500) — 같은 요청이 초 단위로 일치한다.
- `app.log` 에는 호스트명이 없다. `deploy-history.md:3` 이 배포 대상을 단일 호스트 `API-1` 로 적고 있고, `API-2` 는 평소 정지라고 적혀 있다. 이 로그가 `API-1` 의 것인지는 로그만으로 확정할 수 없다.
- `nginx-access.log` 는 combined 형식이며 upstream 주소 · 응답 시간 필드가 없다. 어느 upstream 이 응답했는지와 지연 시간은 이 로그로 볼 수 없다.
- 로그 묶음에 DB 로그 · 배포 파이프라인 원본 로그 · nginx error.log 는 없다.

### 1.2 상태 · 레벨 집계

| 구분 | 건수 | 명령 |
|---|---:|---|
| nginx 200 / 400 / 404 / 409 | 19,278 / 29 / 969 / 35 | `awk '{print $9}' nginx-access.log \| sort \| uniq -c` |
| nginx **500** | **46** | 같은 명령 |
| nginx **502** | **8** | 같은 명령 |
| app ERROR | 46 | `grep -c ' ERROR ' app.log` |
| app WARN | 35 | `grep -c ' WARN ' app.log` |

- app ERROR 46건은 모두 같은 예외다: `JpaObjectRetrievalFailureException: Entity com.example.assignment.Assignment with identifier value 9 does not exist`(46/46, `grep -A1 ' ERROR ' app.log | grep -c 'identifier value .9.'`).
- app WARN 35건은 모두 `state conflict: 마감된 과제는 재배포할 수 없습니다`(409)이며 nginx 409 35건과 수가 같다.
- nginx 500 46건 = app ERROR 46건. nginx 502 8건에 대응하는 app 줄은 없다.

### 1.3 분 단위 5xx (nginx)

| 시각(분) | 5xx | 비고 |
|---|---:|---|
| 09:52 | 3 | 500 |
| 10:31 | 1 | 500 |
| 11:17 · 11:18 | 1 · 2 | 500 |
| 13:41 | 8 | **502 8건** |
| 13:46 ~ 14:34 | 2 · 1 · 4 · 1 · 1 · 3 · 2 · 2 · 4 · 1 · 3 · 1 · 2 · 3 · 2 · 1 · 2 · 1 · 3 (13:46, 13:47, 13:50, 13:52, 13:53, 13:55, 13:58, 14:02, 14:03, 14:06, 14:11, 14:15, 14:16, 14:22, 14:27, 14:28, 14:31, 14:32, 14:34) | 500, 합계 39 |

명령: `awk '$9~/^5/{split($4,a,":"); print a[2]":"a[3]}' nginx-access.log | sort | uniq -c`

- 시간대별 전체 요청 수: 09시 3,625 · 10시 3,621 · 11시 3,762 · 12시 3,215 · 13시 3,678 · 14시(40분) 2,464. 13:30~13:55 분당 48~82건으로 큰 변화 없음. 10초 넘는 요청 공백 없음(최대 10초).

### 1.4 분석 구간

- **이상 구간**: 13:41 ~ 14:34 (5xx 가 끊기지 않고 이어지는 구간, 47건).
- **분석 구간**: 13:11 ~ 14:44 (앞 30분 · 뒤 10분). 단 로그가 14:39:58 에 끝나므로 실제 분석은 14:39:58 까지.
- 분석 구간 앞의 09:52 · 10:31 · 11:17~11:18 500 7건은 이상 구간과 같은 예외(Assignment 9)이므로 2.2 점검표의 "선행 사건"으로 함께 기록한다.

---

## 2. 타임라인

해석 없이 로그에 있는 사건만 적는다. `(선후 불확실)` 은 파일 간 시각 차가 몇 초 이내인 경우.

### 2.1 분석 구간 이전 — 참고(같은 예외의 첫 출현)

| 시각 | 사건 | 출처 |
|---|---|---|
| 09:52:17 | `POST /api/distributions/41/redistribute` 500 (교사단말A). 이후 09:52 에 3건 | `nginx-access.log:3150`, `app.log:194` |
| 09:52:17 | 스택: `DistributionService.loadOrThrow`(:65) ← `DistributionService.redistribute`(:51) ← `DistributionController.redistribute`(:38) | `app.log:205`, `app.log:206`, `app.log:214` |
| 10:31:05 | `GET /api/distributions/41` 500 (교사단말A). 스택 `loadOrThrow`(:65) ← `getDistribution`(:32) | `nginx-access.log:5563`, `app.log:451`, `app.log:462`, `app.log:463` |
| 11:17:40 | `POST /api/distributions/41/redistribute` 500. 이후 11:18 에 2건 | `nginx-access.log:8442`, `app.log:655` |
| 10:36 ~ 13:41 | 배포 42 · 43(`class 1`) 재배포 성공 로그가 반복됨(예: 배포 42 10:36:16, 배포 43 13:13:27) | `app.log:510`, `app.log:1157` |

### 2.2 분석 구간 13:11 ~ 14:39:58

| 시각 | 사건 | 출처 |
|---|---|---|
| 13:13:03 | WARN state conflict(409) — 이 구간에서 13:13 · 13:15 · 13:59 · 14:24 · 14:30 · 14:35 에 1건씩 | `app.log:1153`, `app.log:1166`, `app.log:2925` |
| 13:13:27 | `redistributed distribution 43 (assignment 6, class 1)` | `app.log:1157` |
| 13:38 (배포 이력 기준) | v1.4.2 승인 | `deploy-history.md:21` |
| 13:40:05 | v1.4.2 배포 시작. 변경: `DistributionController.redistribute` 응답에 학급 배포 이력(history) 포함 | `deploy-history.md:7`, `deploy-history.md:15`, `deploy-history.md:16` |
| 13:41:20 | `POST /api/distributions/43/redistribute` **200**, 응답 183바이트 (교사단말A) / app `redistributed distribution 43 (assignment 6, class 1)` | `nginx-access.log:16829`, `app.log:1238` |
| 13:41:48 | `Commencing graceful shutdown` | `app.log:1240` |
| 13:41:50 | `Graceful shutdown complete`, Hikari `Shutdown completed` | `app.log:1241`, `app.log:1244` |
| 13:41:51 ~ 13:41:59 | nginx **502** 8건(여러 경로: `/`, `/api/units`, `/api/items/5` 등) (선후 불확실: app 기동 로그와 수 초 이내) | `nginx-access.log:16865` ~ `nginx-access.log:16872` |
| 13:41:55 | `Starting ItemBankApplication v1.4.2` | `app.log:1245` |
| 13:41:55 | `No active profile set, falling back to 1 default profile: "default"` | `app.log:1246` |
| 13:41:58 | Hikari `Start completed` | `app.log:1260` |
| 13:42:00 | `Tomcat started on port 8080`, `Started ItemBankApplication in 4.921 seconds` | `app.log:1263`, `app.log:1264` |
| 13:42:01 | nginx 첫 200 응답(`GET /api/units`) | `nginx-access.log:16873` |
| 13:42:10 | `POST /api/distributions/7/redistribute` **200**, 802바이트 (교사단말B) | `nginx-access.log:16884` |
| 13:42:31 | v1.4.2 배포 완료 | `deploy-history.md:7` |
| 13:46:38.107 | `redistributed distribution 42 (assignment 4, class 1)` | `app.log:1274` |
| 13:46:38.113 | ERROR `unhandled exception on /api/distributions/42/redistribute` — Assignment 9 does not exist. 스택 `DistributionService.listByClass`(:38) ← `DistributionController.redistribute`(:40) | `app.log:1275`, `app.log:1276`, `app.log:1286`, `app.log:1294` |
| 13:46:38 | nginx `POST /api/distributions/42/redistribute` 500 (교사단말A). **이후 14:34 까지 배포 42 · 43 재배포 500 이 1~4건/분, 합계 39건** | `nginx-access.log:17155` |
| 13:50:00 | `POST /api/distributions/8/redistribute` 200, 908바이트 (교사단말A, `class 2`) | `nginx-access.log:17353`, `app.log:1393` |
| 13:56:43 | `POST /api/distributions/44/redistribute` 200, 640바이트 (교사단말A, `class 2`) | `nginx-access.log:17734`, `app.log:1754` |
| 14:21:02 | `POST /api/distributions/5/redistribute` 200, 910바이트 (교사단말B, `class 3`) | `nginx-access.log:19238`, `app.log:2420` |
| 14:34:45 | 마지막 500: `POST /api/distributions/43/redistribute` | `nginx-access.log:20084`, `app.log:2887` |
| 14:39:58 | 로그 끝 | `nginx-access.log:20365` |

- 이상 구간의 ERROR 39건은 모두 ERROR 바로 앞(같은 스레드, 약 6ms 앞)에 `redistributed distribution 42|43 (…, class 1)` INFO 줄이 있다(예: `app.log:1274` → `app.log:1275`, `app.log:1395` → `app.log:1396`).

### 2.3 이상 구간보다 앞선 "평소와 다른 줄" 점검표

| 점검 항목 | 실행한 grep | 결과 |
|---|---|---|
| 느린 쿼리 · 지연 | `grep -cE 'slow\|took [0-9]+ ?ms\|elapsed'` (app · nginx) | 0 · 0 — **로그에 없음** (nginx 에 응답 시간 필드 자체가 없음) |
| 커넥션 풀 경고 | `grep -cE 'HikariPool.*(timeout\|Connection is not available\|leak)' app.log` | 0 — **로그에 없음** |
| 재시작 | `grep -nE 'Starting ItemBankApplication\|GracefulShutdown\|Shutdown initiated' app.log` | 13:41:48 ~ 13:42:00 재기동 1회(`app.log:1240` ~ `app.log:1264`). 그 외 없음 |
| 설정 재적재 · 프로필 | `grep -nE 'Refresh\|reload\|ConfigData\|profile' app.log` | 1건: 기동 시 `No active profile set … "default"`(`app.log:1246`). 재기동 이전 기동 로그가 묶음에 없어 평소에도 같은지 비교 불가 |
| 데이터 삭제 흔적 | `grep -cE 'DELETE \|delete\|removed\|deleted'` (app · nginx), nginx 메서드 집계 | 0 · 0. nginx 메서드는 GET 20,213 · POST 152 뿐 — **로그에 없음** |
| 리소스 고갈 | `grep -cE 'OutOfMemory\|GC overhead\|Too many open'` | 0 — **로그에 없음** |
| 트래픽 변화 | 시간대별 · 분당 요청 수 집계(1.3) | 변화 없음 |
| 같은 예외의 선행 출현 | `grep -n 'identifier value .9.' app.log` | **있음**: 09:52:17 첫 출현(배포 41), 배포 전 7건(`app.log:194` ~ `app.log:733`) |

---

## 3. 가설과 검증

### 3.1 증상과 설명해야 할 관찰

**증상(결과)**

- S1. 13:46 ~ 14:34 `POST /api/distributions/{42,43}/redistribute` 가 500 (39건).
- S2. 13:41:51 ~ 13:41:59 여러 경로에 502 (8건).

**설명해야 할 관찰**

- O1. 500 의 예외는 모두 `Assignment 9 does not exist` 이고, 같은 예외가 배포 전 09:52 부터 배포 41 에서 7건 있었다(2.1).
- O2. 배포 전에는 오류 프레임이 `DistributionController.java:38`(재배포 자체), 배포 후에는 `DistributionController.java:40` → `DistributionService.listByClass`(:38) 로 바뀌었다(`app.log:214` vs `app.log:1294`).
- O3. 배포 42 · 43(`class 1`) 재배포는 배포 전엔 200(예: `nginx-access.log:16829`), 배포 후엔 39/39 가 500. 같은 시간대 `class 2` · `class 3` 재배포는 배포 후 200(`nginx-access.log:17353`, `17734`, `19238`).
- O4. 배포 후 성공 응답의 크기가 640~910바이트로, 배포 전(170~196바이트)보다 크다.
- O5. 500 39건마다 직전 수 ms 안에 `redistributed distribution …` INFO 가 있다(2.2 끝).
- O6. 500 을 받은 client 는 46건 모두 교사단말A 다. 교사단말B 의 재배포는 배포 전후 모두 200(23 · 4건).
- O7. 502 8건은 graceful shutdown 완료(13:41:50) ~ Tomcat 기동(13:42:00) 사이에 있다.

### 3.2 가설 표

| ID | 계층 | 가설 | 지지 근거 | 반증 조건 | 확인 방법(실행할 명령) |
|---|---|---|---|---|---|
| H1 | 배포/코드 | v1.4.2 가 재배포 응답에 `listByClass(classId)` 를 추가하면서, `class 1` 의 배포 목록에 존재하지 않는 Assignment 9 를 참조하는 배포가 섞여 있어 목록 조회 단계에서 예외가 난다. 재배포 자체는 성공하고 응답 조립에서 500 이 된다. | O2 · O3 · O4 · O5, `deploy-history.md:15`, `deploy-history.md:16` | (a) 배포 후 `class 1` 재배포가 한 건이라도 200, (b) 배포 후 500 의 스택에 `listByClass` 가 없는 건이 있음, (c) Assignment 9 를 참조하는 배포가 `class 1` 이 아님 | `awk '$7~/distributions\/4[23]\/redistribute/{split($4,a,":"); print (a[2]a[3]a[4]<"134148")?"pre":"post",$9}' nginx-access.log \| sort \| uniq -c` · `grep -A25 ' ERROR ' app.log \| grep -c 'listByClass'` · `git show <v1.4.2 커밋>:modern/api/src/main/java/com/example/assignment/DistributionController.java` (4절에서 수행) |
| H2 | DB(데이터) | 배포 41 이 존재하지 않는 Assignment 9 를 참조하는 고아 행이고(FK 부재 또는 과제 삭제), 이 데이터 상태가 배포 이전부터 있었다. | O1(09:52 부터 같은 예외, 배포 41 단건 조회도 500 — `app.log:451`) | (a) DB 에 Assignment 9 가 존재, (b) 배포 41 이 Assignment 9 를 참조하지 않음 | 담당자 요청: `SELECT id, assignment_id, class_room_id FROM distribution WHERE id = 41;` · `SELECT id FROM assignment WHERE id = 9;` · `SHOW CREATE TABLE distribution;`(FK 유무) — 운영 DB 직접 접속하지 않음 |
| H3 | 트래픽/클라이언트 | 교사단말A 의 반복 재시도(3건씩 묶음)가 오류를 키우거나 중복 처리로 상태를 꼬이게 했다. | O6, 500 이 1분 안에 3건씩 묶여 나타남(예: 13:50:12 · 13:50:13 · 13:50:19) | (a) 교사단말B 가 `class 1` 배포를 재배포하고도 200 을 받음 → 단말 문제가 아님, (b) 교사단말A 의 첫 요청부터 500 (재시도 전에 이미 실패), (c) 전체 요청량에 변화 없음 | `awk '$1=="<교사단말B>" && $7~/redistribute/{print $7,$9}' nginx-access.log \| sort \| uniq -c` · 각 묶음의 첫 요청 상태 확인 · 1.3 의 분당 요청 수 |
| H4 | 인프라/설정 | 재기동 때 프로필 미지정(`default`)으로 떠서 평소와 다른 DB · 스키마에 붙었다. | `app.log:1246`(프로필 미지정), 재기동 직후부터 500 | (a) 배포 후 다른 엔드포인트 · 다른 학급은 정상(O3), (b) 같은 Assignment 9 예외가 재기동 전에도 있음(O1), (c) 재기동 전 기동 로그도 `default` | `grep -c ' 200 ' nginx-access.log`(배포 후 구간) · 담당자 요청: 재기동 전 기동 로그 · 컨테이너 환경변수(`SPRING_PROFILES_ACTIVE`) · 접속 DB 호스트 확인 |
| H5 | 배포 방식 | 502 8건은 단일 호스트 재기동(무중단 아님) 동안 upstream 이 없어서 생겼다. S1 과는 별개 증상이다. | O7, `deploy-history.md:18`(단일 호스트 재기동, 예상 중단 10초 안팎) | (a) 502 가 재기동 창(13:41:50 ~ 13:42:00) 밖에도 있음, (b) nginx error.log 에 다른 원인(타임아웃 등) | `awk '$9==502{print $4}' nginx-access.log` · 담당자 요청: 같은 시각 nginx error.log(`connect() failed` · `upstream timed out` 구분) · upstream 설정에 `API-2` 포함 여부 |

> 4절(반증 실행 · 코드 대조 · 판정)과 5절(요약 · 원인 · 근거 · 수정안 · 재발 방지 · 모니터링 · 확인하지 못한 것)은 이번 범위에서 작성하지 않았다.
