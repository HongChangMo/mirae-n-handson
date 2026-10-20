# 배치 · 데이터마트 점검 결과

**요약: 점검 기간 2026-09-08 ~ 2026-09-17(10일, job 5개), 이상 7건(4개 날짜: 09-09 · 09-12 · 09-15 · 09-17)**

- 대상: `pipeline-samples/batch-logs/*.log`, `pipeline-samples/datamart-counts.csv`
- 계산 기준
  - 적재 건수: 같은 job 의 직전 7일 평균 대비 편차, ±30% 초과 또는 0건이면 이상. 직전 데이터가 7일이 안 되는 날(09-09 ~ 09-15)은 있는 날만으로 평균을 냄. 09-08 은 직전 데이터가 없어 건수 비교를 하지 않음.
  - 평소 소요 시간: 그 job 의 전체 기간 SUCCESS 실행 소요 시간 중앙값, 그 2배를 넘으면 이상. 로그의 `elapsed=` 값을 쓰고, 값이 없는 `build_report_cache` 는 같은 run_id 의 START~SUCCESS 시각 차이로 계산함.
  - job 별 중앙값(2배 기준):
    - build_item_stats 213s(426s) — 9회, 09-09 는 종료 기록이 없어 제외
    - export_grades 319s(638s) — 10회
    - extract_submissions 680.5s(1361s) — 10회
    - load_datamart 923s(1846s) — 11회, 09-15 의 두 실행 포함
    - build_report_cache 195.5s(391s) — 10회, 시각 차이로 계산
  - 09-16 · 09-17 load_datamart 의 직전 7일 평균에는 09-15 의 2배 값(369064)이 들어가 편차가 −12.5% · −12.7% 로 나왔다. 기준 안이어서 이상으로 보지 않았다.
  - 모든 계산은 스크립트로 했다.
- 원인은 로그에 근거가 없으면 적지 않고 "다음 확인 사항"에 무엇을 볼지만 적었다. 로그에 적힌 메모는 "로그 메모"로 인용만 했다. "영향 추정"은 로그 · CSV 에서 보이는 범위의 추정이며 확인되지 않았다.
- 로그에 있는 운영자 이메일 · 학생 식별자는 옮기지 않았다.

## 이상 목록

| 날짜 | job | 점검 항목 | 근거 | 영향 추정 | 다음 확인 사항 |
|---|---|---|---|---|---|
| 2026-09-09 | build_item_stats | 1. 실행 여부: 시작만 있고 종료 기록 없음 | `batch-logs/2026-09-09.log:2` START(pid=40655). `:4` RUNNING 25%(rows=3109)가 이 job 의 마지막 줄이고, SUCCESS · FAIL 기록이 없다. 다른 날은 모두 SUCCESS 가 있다(예: `batch-logs/2026-09-08.log:8`). CSV 에도 `2026-09-09,build_item_stats` 행이 없다(09-09 만 5개가 아닌 4개 행). | `dm.item_stats_daily` 의 09-09 데이터가 없거나 일부만 들어갔을 수 있다(확인되지 않은 추정). | 원인 확인 필요. 10.20.7.31 의 00:30:57 이후 시스템 로그(프로세스 종료 여부), 스케줄러 기록, 해당 테이블의 09-09 행 수 |
| 2026-09-12 | export_grades | 4. 지연 | `batch-logs/2026-09-12.log:16` elapsed=963s, 중앙값 319s 의 3.02배(기준 638s 초과). 같은 날 건수는 `datamart-counts.csv:22` 8257 로 평소 수준. | 내보내기 완료가 평소보다 약 11분 늦었다. 후속 영향은 로그에 없다(확인되지 않은 추정). | 원인 확인 필요. 로그에 지연 사유가 없다. 그 시각 원천 grade-db 부하, 내보내기 대상 쪽 지표 |
| 2026-09-15 | extract_submissions | 4. 지연 | `batch-logs/2026-09-15.log:20` elapsed=1668s, 중앙값 680.5s 의 2.45배(기준 1361s 초과). 로그 메모 `:19` WARN `note="source read slow: grade-db … replica lag 412s"` | 01:57:50 에 끝나 후속 load_datamart(02:00 시작) 직전까지 갔다(확인되지 않은 추정). | 원인 확인 필요. grade-db 복제 지연 지표로 로그 메모와 실제 지연의 관계 확인 |
| 2026-09-15 | load_datamart | 4. 지연 | `batch-logs/2026-09-15.log:40` elapsed=4357s(cron 실행분), 중앙값 923s 의 4.72배(기준 1846s 초과). 로그 메모 `:27` WARN "elapsed 40m exceeds p95 17m", `:28` sla-monitor NOT_FINISHED(sla=02:45). 수동 재실행분 `:42` elapsed=1008s 는 기준 안. | SLA 를 넘겼고, 이 지연 뒤에 수동 재실행이 요청됐다(`:30`). | 원인 확인 필요. 02:00~02:58 구간(재실행과 겹치기 전) 대상 DB 부하 · 락 대기 |
| 2026-09-15 | load_datamart | 2. 중복 실행 | SUCCESS 2회: `batch-logs/2026-09-15.log:40`(cron, rows=184532), `:42`(수동, rows=184532). `:30` force 재실행 요청(로그 메모 "SLA 알림 — 멈춘 것으로 보고 재실행"), `:31` lock 을 `--force` 로 무시, `:32` lock=bypassed 로 START. `:41` 첫 실행이 lock 해제, `:43` 재실행은 `release_skipped` | 같은 날짜 데이터가 두 번 들어갔을 수 있다(아래 건수 행과 함께 볼 것). lock 파일 상태가 불명확하다(확인되지 않은 추정). | `dm.daily_submission_fact` 의 2026-09-15 행을 run_id 별로 셈. `/var/lock/batch/load_datamart.lock` 현재 상태. 다음 날 실행은 lock 을 정상으로 얻었다(`batch-logs/2026-09-16.log:20` lock=acquired) |
| 2026-09-15 | load_datamart | 3. 적재 건수 | `datamart-counts.csv:39` `2026-09-15,load_datamart,369064`. 직전 7일 평균 184617.4 대비 +99.9%. 두 실행 rows 184532×2 와 같다. `batch-logs/2026-09-15.log:33` 재실행이 `mode=append` 로 `dm.daily_submission_fact` 대상. `:45` build_report_cache input_rows=369064 경고. `:47` dq-check deviation=+99% | 이 테이블을 읽는 집계가 2배로 부풀었을 수 있다. build_report_cache(09-15) 입력이 2배였다(결과 `datamart-counts.csv:40` 1271건은 평소 수준). 확인되지 않은 추정. | 중복 적재 여부 확정(위 행). build_report_cache 09-15 결과를 다시 만들어야 하는지. 이 테이블을 쓰는 다른 하위 집계 · 리포트 목록 |
| 2026-09-17 | build_item_stats | 3. 적재 건수 | `datamart-counts.csv:46` `2026-09-17,build_item_stats,7284`. 직전 7일 평균 12453.1 대비 −41.5%. 로그 `batch-logs/2026-09-17.log:8` SUCCESS rows=7284, elapsed=202s(평소 수준) | 09-17 문항 통계가 평소보다 약 41% 적게 집계됐을 수 있다(확인되지 않은 추정). | 원인 확인 필요. 배치는 정상 종료여서 로그에 단서가 없다. 원천 `itembank.item_response` 의 09-17 응답 수, 그날 서비스 이용량 · 원천 적재 지연 여부 |

## 이상 없음으로 판정한 날짜

- 2026-09-08
- 2026-09-10
- 2026-09-11
- 2026-09-13
- 2026-09-14
- 2026-09-16
