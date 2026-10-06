# 머지 전 검증 — migration-review-1

- 검증 대상: `git diff upstream/main...HEAD` (HEAD = `97db644`, 커밋 11개, 95개 파일, +5372 / -144)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 판정 기준: `templates/approval-checklist.md` (v0), 절차: `templates/verification-loop.md`
- 이 문서는 리뷰 세션의 보고서다. 코드는 수정하지 않았다.

## 판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew cleanTest test` → 105 통과 · 0 실패 · 0 오류 · 0 skipped. `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` → 29 통과 · 1 skipped · 0 실패 (skip 은 `example-units.test.js:21` 의 `it.skipIf(legacyOnly)`, 새 API 대상에서는 의도된 skip). 단, 8080 서버가 HEAD 빌드인지는 확인하지 못했다(확인 필요 1) |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. 리터럴 비밀값 · 문자열 이어 붙인 SQL · 새 삭제 로직 없음 (이슈 #5 는 경고) |
| 요청 범위 이탈 없음 | **미충족** | 요청은 "엔드포인트 1개 이관"인데 변경은 95개 파일. 이슈 #1 ~ #3 |
| 컨벤션 준수 | 충족 | `CLAUDE.md` 대조 결과 위반 없음. 컨트롤러에 `try`/`catch`·리포지토리 주입 없음, `application`·`domain`·`interfaces` 의 `infrastructure` import 없음, `System.out` 없음 |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/common/GlobalExceptionHandler.java:47-48` | 409 매핑 대상을 `IllegalStateException` → `ConflictException` 으로 바꿨다. 요청한 이관 대상(검색 엔드포인트)과 무관하고, "동작을 바꾸지 않고" 조건과 충돌한다. 이전에 `IllegalStateException` 을 던지던 경로는 이제 500 이 된다 | 별도 PR 로 분리한다. 이번 PR 에서 의도한 것이라면 요청 문장에 명시하고 사람이 수용 여부를 확정한다 |
| 2 | 경고 | `modern/api/src/main/java/com/example/application/assignment/ReportService.java` 전체, `modern/api/src/main/java/com/example/infrastructure/assignment/*RepositoryImpl.java` | assignment 도메인 4계층 이관 · 학급 리포트 N+1 / 트랜잭션 내 서명 계산 제거(`1cab966`)가 포함돼 있다. 이관 대상 엔드포인트와 무관하다 | assignment 변경을 별도 PR 로 나누고 검증도 따로 한다 |
| 3 | 경고 | `CLAUDE.md`, `.claude/skills/*`, `docs/**`, `characterization/**` (추가 파일 약 17개) | 문서 · Skill · 동작 보존 테스트 베이스라인이 같은 diff 에 섞여 있다. 요청 문장으로 설명되는 것은 `characterization/**` 뿐이다. `upstream/main` 이후 누적 변경을 한 번에 비교한 결과로 보이며, 실제로 리뷰할 단위가 아니다 | 검증 범위를 "검색 엔드포인트 이관 커밋(`6dd92da`)"으로 좁혀 다시 검증하거나, 범위를 사람이 "누적 전체"로 확정한다 |
| 4 | 제안 | `modern/api/src/main/java/com/example/infrastructure/item/ItemRepositoryImpl.java:65` | `"order by " + condition.sortOrder().jpql()` 은 문자열 결합이지만 값은 `ItemSortOrder` enum 의 고정 문자열이다(`domain/item/ItemSortOrder.java:23-31`). 사용자 입력이 직접 들어가지 않아 인젝션 위험은 없다 | 현 상태 유지. 나중에 정렬 키가 외부 입력으로 확장되지 않도록 enum 외 값을 받지 않는다는 주석을 남기면 된다 |
| 5 | 제안 | `modern/api/src/main/java/com/example/infrastructure/item/ItemRepositoryImpl.java:105-111` | 레벨 필터의 int 범위 밖 값에 `1 = 0` 을 넣는다. 레거시 동작을 보존하려는 의도로 보이며 `ItemRepositoryImplTest` 에 대응 케이스가 있는지 이 리뷰에서는 줄 단위로 확인하지 않았다 | 해당 분기 테스트가 있는지 만든 세션에서 확인한다 |

## 확인 필요

1. 8080 에서 응답한 서버(`GET /` 200)가 `HEAD` 로 빌드된 것인지 확인하지 못했다. characterization 29 통과는 그 서버 기준이므로, 사람이 `./gradlew bootRun` 을 HEAD 에서 다시 띄운 뒤 `TARGET_BASE_URL=http://localhost:8080 npm test` 를 재실행해 주기 바란다.
2. 레거시(8081)도 응답 중이지만, 이번에는 새 API 대상만 실행했다. 스냅샷은 수정되지 않았다(`git status` 로 확인).
3. 이슈 4 · 5 외의 검색 JPQL(`ItemRepositoryImpl.java:88-117`)의 레거시 `search.php` 와의 줄 단위 동등성은 characterization 16개 케이스가 통과한 범위까지만 확인했다. 그 밖의 입력 조합은 확인하지 못했다.
4. 이슈 0건 방지용으로 경고 · 제안까지 훑었으나, 외부 변경(문서 · Skill)의 내용 품질은 이 검증 범위가 아니다.

## 요약

테스트(api 105, characterization 29)는 통과했고 치명 이슈 · 컨벤션 위반은 없다. 그러나 요청이 "엔드포인트 1개 이관"인데 diff 에 assignment 이관, 409 처리 방식 변경, 문서 · Skill 이 함께 들어 있어 범위 기준이 미충족이다. 범위를 좁혀 재검증하거나, 사람이 누적 범위를 요청 범위로 확정한 뒤 다시 판정해야 한다.
