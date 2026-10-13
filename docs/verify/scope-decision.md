# 검증 범위 확정 — migration-review-1 · 2 후속

- 결정: 2026-10-06, 사람(HongChangMo)
- 대상 리포트: `docs/verify/migration-review-1.md`(HEAD `97db644`), `docs/verify/migration-review-2.md`(HEAD `33daf9e`)
- 이 문서는 리뷰 리포트가 아니라, 리포트가 사람에게 넘긴 결정과 지적에 대한 응답을 기록한다. 리포트 원본은 고치지 않았다.

## 확정한 범위

`git diff upstream/main...HEAD` **누적 전체를 요청 범위로 확정한다.** (2차 리포트 요약의 선택지 (b))

- 이 저장소는 `upstream`(tristan-kkim/mirae-n-handson)의 포크다. 누적 diff 에는 이미 `origin/main` 에 리뷰 · 머지된 PR #1(`429c08b`) · PR #2(`97db644`)가 들어 있다.
- 다음 검증은 이 범위를 요청 범위로 보고 판정한다.

## 이슈별 응답

| 리포트 · # | 심각도 | 분류 | 근거 |
|---|---|---|---|
| 1차 #1 · 2차 #1 (409 매핑) | 경고 | 범위 확정으로 해소, 동작 변경 지적은 반박 | 기존에 409 를 내던 `IllegalStateException` 은 재배포 마감 검사 한 곳이었고, `1cab966` 에서 `AssignmentClosedException`(`common.ConflictException` 상속)으로 바꿨다(`modern/api/src/main/java/com/example/application/assignment/DistributionService.java:56`). 이 경로는 지금도 409 다. 남은 `IllegalStateException` 은 `ReportService.java:110`(SHA-256 미지원 JVM)뿐이고 500 이 맞다. 기준은 `CLAUDE.md:58`, 검증은 `DistributionControllerTest.java:95-97` |
| 1차 #2 · 2차 #2 (assignment 이관) | 경고 | 범위 확정으로 해소 | PR #2(`97db644`)로 별도 리뷰 · 머지된 변경 |
| 1차 #3 · 2차 #3 (문서 · Skill 혼재) | 경고 | 범위 확정으로 해소 | 위 "확정한 범위" |
| 1차 #5 (레벨 `1 = 0` 분기 테스트) | 제안 | 반박 → 2차에서 해소 확인 | `modern/api/src/test/java/com/example/infrastructure/item/ItemRepositoryImplTest.java:94-99` |
| 1차 #4 · 2차 #5 (ORDER BY 결합) | 제안 | 수용, 별도 작업 | 값은 `ItemSortOrder` enum 고정 문자열(`domain/item/ItemSortOrder.java:16-21`). "enum 외 값 금지" 주석은 나중에 단다 |
| 2차 #4 (`ErrorResponse.java:9` 의 `Instant.now()`) | 제안 | 수용, 별도 작업 | `CLAUDE.md` 의 `Clock` 규칙 위반이지만 초기 커밋 `1260542` 부터 있던 diff 밖 코드다 |

## 확정 범위 기준 재판정: 승인

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `./gradlew cleanTest test` 105 통과(2차 리포트). 2026-10-06 사람이 8080 을 다시 띄운 뒤 `TARGET_BASE_URL=http://localhost:8080 npm test` → 29 통과 · 1 skipped · 0 실패, 스냅샷 변경 없음(2차 확인 필요 1 해소) |
| 치명 이슈 0건 | 충족 | 1 · 2차 리포트와 같음 |
| 요청 범위 이탈 없음 | 충족 | 누적 전체를 요청 범위로 확정 |
| 컨벤션 준수 | 충족 | 2차 리포트 "컨벤션 대조". 2차 #4 는 diff 밖 |

남은 항목은 제안 2건(ORDER BY 주석, `Clock` 주입)이며 별도 작업으로 다룬다.
