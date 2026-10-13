# 머지 전 검증 — migration-review-2

- 검증 대상: `git diff upstream/main...HEAD` (HEAD = `33daf9e`, 커밋 12개, 96개 파일, +5408 / -144)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 판정 기준: `templates/approval-checklist.md` (v0), 절차: `templates/verification-loop.md`
- 이전 리뷰: `docs/verify/migration-review-1.md` (HEAD `97db644`)
- 이 문서는 리뷰 세션의 보고서다. 코드는 수정하지 않았다. 새로 쓴 파일은 이 문서뿐이다.

## 1차 이후 달라진 점

`97db644` → `33daf9e` 사이 변경은 `docs/verify/migration-review-1.md` 추가(커밋 메시지 "검증루프 1차 리뷰 반영") 한 파일뿐이다. 1차 이슈 #1 ~ #3(범위)은 코드 · 구성 변경 없이 그대로이고, 1차 제안 #5(레벨 필터 `1 = 0` 분기 테스트 유무)는 이번에 테스트를 확인해 해소했다(이슈 목록 아래 참고).

## 판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew cleanTest test` → 105 통과 · 0 실패 · 0 오류 · 0 skipped (`build/test-results` 집계). `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` → 29 통과 · 1 skipped · 0 실패 (skip 은 `characterization/tests/example-units.test.js:21` 의 `it.skipIf(legacyOnly)`, 이번 diff 밖의 기존 코드이며 새 API 대상에서는 의도된 skip). 8080 서버의 빌드 일치 여부는 확인 필요 1. `@Disabled` · `.skip` 로 바꾼 기존 테스트 없음 |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. 리터럴 비밀값 · 파라미터 미바인딩 SQL · 새 삭제 로직 없음 |
| 요청 범위 이탈 없음 | **미충족** | 요청은 "엔드포인트 1개 이관"인데 변경은 96개 파일 중 검색 엔드포인트와 직접 관련 없는 파일이 다수. 이슈 #1 ~ #3 |
| 컨벤션 준수 | 충족 | 아래 "컨벤션 대조" 참고. 위반 없음 (제안 #4 는 이번 diff 밖 기존 코드) |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/common/GlobalExceptionHandler.java:47-48` | 409 매핑 대상을 `IllegalStateException` → `ConflictException` 으로 바꿨다. 이관 대상(검색)과 무관하고 "동작을 바꾸지 않고" 조건과 충돌한다. 이전에 `IllegalStateException` 을 던지던 경로는 이제 500 이 된다(현재 main 코드의 `IllegalStateException` 은 `application/assignment/ReportService.java:110` SHA-256 미지원 JVM 한 곳이며 이는 500 이 맞다). 1차에서 지적했으나 변경 없음 | 별도 PR 로 분리하거나, 의도한 변경이면 요청 문장에 명시하고 사람이 수용 여부를 확정한다 |
| 2 | 경고 | `modern/api/src/main/java/com/example/application/assignment/ReportService.java` 전체, `modern/api/src/main/java/com/example/infrastructure/assignment/*RepositoryImpl.java`, `modern/api/src/main/java/com/example/domain/assignment/*.java` | assignment 도메인 4계층 이관과 학급 리포트 N+1 · 트랜잭션 내 서명 계산 제거(`1cab966`)가 포함돼 있다. 이관 대상 엔드포인트와 무관하다. 1차에서 지적했으나 변경 없음 | assignment 변경을 별도 PR 로 나누고 검증도 따로 한다 |
| 3 | 경고 | `CLAUDE.md`, `.claude/skills/{convention-check,document-module,impact}/SKILL.md`, `docs/**`(`docs/verify/migration-review-1.md` 포함), `characterization/**` | 문서 · Skill · 동작 보존 베이스라인이 같은 diff 에 섞여 있다. 요청 문장으로 설명되는 것은 `characterization/**` 뿐이다. `upstream/main` 이후 누적 변경을 한 번에 비교한 것이라 리뷰 단위가 아니다. 1차에서 지적했으나 변경 없음 | 검증 범위를 검색 엔드포인트 이관 커밋(`6dd92da`)으로 좁혀 다시 검증하거나, 사람이 "누적 전체"를 요청 범위로 확정한다 |
| 4 | 제안 | `modern/api/src/main/java/com/example/common/ErrorResponse.java:9` | `Instant.now()` 인자 없는 호출. CLAUDE.md 는 시각을 `Clock` 빈으로 받으라고 한다. 이번 diff 에서 건드리지 않은 기존 코드라 이번 반려 사유는 아니다 | 별도 작업에서 `Clock` 주입으로 바꾼다. 이관 PR 에는 넣지 않는다 |
| 5 | 제안 | `modern/api/src/main/java/com/example/infrastructure/item/ItemRepositoryImpl.java:65` | `"order by " + condition.sortOrder().jpql()` 은 문자열 결합이지만 값은 `ItemSortOrder` enum 의 고정 문자열이다(`domain/item/ItemSortOrder.java:23-30`). 사용자 입력이 직접 들어가지 않아 인젝션 위험은 없다 | 현 상태 유지. 정렬 키가 외부 입력으로 확장되지 않도록 "enum 외 값 금지" 주석을 남기면 된다 |

### 1차 제안 #5 해소

`ItemRepositoryImpl.java:110` 의 `1 = 0`(레벨 범위 밖) 분기는 `modern/api/src/test/java/com/example/infrastructure/item/ItemRepositoryImplTest.java:94` ("범위 밖 난이도 · int 범위를 넘는 난이도는 0건, 오류 아님 (BR-12)")이 검증한다. 이슈로 올리지 않는다.

## 컨벤션 대조 (CLAUDE.md 기준, grep 로 확인)

- `interfaces` · `application` · `domain` 의 `com.example.infrastructure` import: 없음
- `domain` 의 `org.springframework.data` import: 없음
- 컨트롤러의 `catch` · 리포지토리 주입: 없음
- `System.out` · `System.err` · `printStackTrace` · `LocalDateTime.now()`: 없음. 와일드카드 import: 없음
- 로그는 SLF4J 만 사용(`GlobalExceptionHandler.java:22-55`, `DistributionService.java:19,59`). 로그 인자에 `STU-` · 이메일 · 토큰을 넣는 곳은 이번에 훑은 범위에서 발견하지 못했다
- 도메인 예외: `AssignmentClosedException` 이 `common.ConflictException` 을 상속(`domain/assignment/AssignmentClosedException.java:6`)
- 리포지토리 구현체 이름은 `*RepositoryImpl`(메모리 규칙과 일치)
- `modern/web` · `package.json` · `build.gradle` · `application.yml` · 스키마 · 시드 변경: 변경 파일 목록에 없음

## 확인 필요

1. 8080 에서 응답한 서버(`GET /` 200, PID 87100, 2026-10-06 12:44:03 기동)가 `HEAD` 소스로 빌드된 것인지 확인하지 못했다. 코드 변경이 마지막으로 있었던 시점보다 기동이 늦어 일치할 가능성이 높지만, 프로세스의 클래스패스는 보지 않았다. characterization 29 통과는 그 서버 기준이므로 사람이 `./gradlew bootRun` 을 HEAD 에서 다시 띄우고 `TARGET_BASE_URL=http://localhost:8080 npm test` 를 재실행해 주기 바란다.
2. 레거시(8081)도 응답 중이지만 이번에는 새 API 대상만 실행했다. 스냅샷 · characterization 테스트 파일은 이번 세션에서 수정하지 않았다.
3. 검색 JPQL(`ItemRepositoryImpl.java:88-117`)과 레거시 `search.php` 의 줄 단위 동등성은 characterization 케이스가 통과한 범위까지만 확인했다. 그 밖의 입력 조합은 확인하지 못했다.
4. 로그 인자의 학생 식별자 · 이메일 · 토큰 포함 여부는 `log.` 호출 전수 확인이 아니라 grep 으로 잡힌 호출(위 목록) 기준이다.
5. 문서 · Skill 의 내용 품질은 이 검증 범위가 아니다.

## 요약

테스트(api 105, characterization 29)는 통과했고 치명 이슈 · 컨벤션 위반은 없다. 그러나 1차 반려 이후 코드 변경 없이 리뷰 문서만 추가돼, 요청("엔드포인트 1개 이관")과 무관한 assignment 이관 · 409 처리 방식 변경 · 문서 · Skill 이 diff 에 그대로 남아 있다. 범위 기준이 여전히 미충족이므로 반려다. 다음 중 하나가 필요하다: (a) 검증 범위를 `6dd92da` 로 좁혀 재검증, (b) 사람이 "누적 전체"를 요청 범위로 확정, (c) 무관한 변경을 별도 PR 로 분리.
