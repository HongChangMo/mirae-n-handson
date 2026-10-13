# 머지 전 검증 3차 — 누적 이관 변경

- 검증 일자: 2026-10-06
- 검증 대상: `git diff upstream/main...HEAD -- modern characterization` (HEAD `9c1d4a2`, 85개 파일, +3557 / −144)
- 요청 범위: "레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관." 사람이 이번 검증에서 다시 지시한 대로 **누적 diff 전체를 요청 범위로 보고 판정**했다(`docs/verify/scope-decision.md` 와 같은 기준).
- 판정 기준: `templates/approval-checklist.md` (v1 · 2회차). 절차: `templates/verification-loop.md`
- 수행: reviewer Sub-agent(체크리스트 대조) · tester Sub-agent(테스트 실행) → 메인 세션이 최종 판정

```
판정: 반려
```

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 미충족 | 실행 결과는 실패 0건이다(아래 "테스트 실행 결과"). 하지만 검증 대상 diff 안에 새 public 메서드 · 본문이 바뀐 public 메서드를 호출하는 테스트가 없는 곳이 있다. 체크리스트 §1 [반려] 항목 2건이 "아니오"다(이슈 #1, #2, #3). #3 은 두 보고가 엇갈렸던 항목인데, 사람이 수용해 확정했다 |
| 치명 이슈 0건 | 충족 | §2 A~D 전 항목이 "예"다. JPQL 결합은 고정 문자열과 enum 상수뿐이다(`ItemRepositoryImpl.java:57,65`, `ItemSortOrder.java:16-21`). 로그 인자는 id와 건수뿐이다. `ReportService` 는 `@Transactional` 을 제거했고 쿼리 3개로 끝난다 |
| 요청 범위 이탈 없음 | 충족 | 누적 전체를 요청 범위로 확정했다. 요청 엔드포인트 `GET /api/items/search` 가 들어 있다(`ItemSearchController.java:25-28`). 의존성 · 스키마 · 설정 · `legacy/` 변경은 없다 |
| 컨벤션 준수 | 충족 | §4 [반려] · [경고] 항목 위반은 0건이다. diff 밖에 원래 있던 위반 2건은 제안(#6, #7)으로 적었다 |

## 테스트 실행 결과 (tester)

| 명령 | 통과 | 실패 | skip |
|---|---|---|---|
| `cd modern/api && ./gradlew test` | 117 | 0 | 0 |
| `cd characterization && npm test` (레거시, `http://localhost:8081`) | 30 | 0 | 0 |
| `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` | 29 | 0 | 1 |

- 실패한 테스트: 없음
- skip 1건: `tests/example-units.test.js` "단원 목록 전체". 이 파일은 diff 밖이고, 원래부터 `it.skipIf(Boolean(process.env.TARGET_BASE_URL))` 로 표시되어 있다(`characterization/tests/example-units.test.js:21`). 이관 대상인 `tests/item-bank.test.js` 16건은 양쪽 대상에서 모두 통과했다.
- 스냅샷 변경: 0건(`git status --porcelain characterization`). `item-bank.test.js` · `.snap` 은 이관 전 베이스라인 커밋 `526eb05` 에서 추가됐다. `git diff 526eb05 HEAD -- characterization/tests` 의 차이도 0이다.
- `./gradlew test` 117건에는 tester 가 작업 트리에 **추가한 미커밋 테스트 13건**이 들어 있다(아래). 이 테스트를 빼면 원래 diff 의 테스트는 104건이다.

### tester 가 추가한 테스트 파일 (미커밋, 검증 대상 diff 밖)

- `modern/api/src/test/java/com/example/interfaces/item/UnitControllerTest.java`
- `modern/api/src/test/java/com/example/infrastructure/item/UnitRepositoryImplTest.java`
- `modern/api/src/test/java/com/example/infrastructure/item/TagRepositoryImplTest.java`
- `modern/api/src/test/java/com/example/infrastructure/assignment/ClassRoomRepositoryImplTest.java`
- `modern/api/src/test/java/com/example/infrastructure/assignment/DistributionRepositoryImplTest.java`

이 파일들은 이슈 #1 · #2 를 메우는 테스트다. 수용할지, 커밋할지는 만든 세션과 사람이 정한다. 리뷰 세션은 커밋하지 않았다.

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 주요 | `modern/api/src/main/java/com/example/infrastructure/assignment/ClassRoomRepositoryImpl.java:19`, `infrastructure/assignment/DistributionRepositoryImpl.java:20,25`, `infrastructure/item/UnitRepositoryImpl.java:20,25`, `infrastructure/item/TagRepositoryImpl.java:19` | 새 public 메서드인데 diff 안에 이를 호출하는 테스트가 없다. 서비스 테스트는 도메인 인터페이스를 mock 하고, `ItemJpaRepositoryTest` 는 Spring Data 쪽만 호출한다. 체크리스트 §1 "새 public 메서드(`infrastructure` 포함)마다 테스트 1개 이상" [반려]에 걸린다 | `SubmissionRepositoryImplTest` 처럼 Impl 을 `@Import` 한 `@DataJpaTest` 를 추가한다. tester 가 작업 트리에 추가한 4개 파일을 검토해 커밋해도 된다 |
| 2 | 주요 | `modern/api/src/main/java/com/example/interfaces/item/UnitController.java:22-26` | 본문이 바뀌었다(`UnitSummary` → `UnitResponse` 매핑). 이 경로를 부르는 테스트는 `WebConfigTest.java:35-40` 하나인데, 빈 목록에서 CORS 헤더만 확인하고 응답 본문은 assert 하지 않는다. 체크리스트 §1 "본문이 바뀐 public 메서드마다 같은 diff 안에 assert 테스트" [반려]에 걸린다 | `@WebMvcTest(UnitController.class)` 로 `/api/units` 의 JSON 필드를 assert 한다. tester 가 추가한 `UnitControllerTest.java` 를 검토해 커밋해도 된다 |
| 3 | 주요 | `modern/api/src/main/java/com/example/application/assignment/DistributionService.java:34,40` | 두 메서드는 반환 타입과 매핑(`DistributionDetail.from`)이 바뀌었다. 그런데 실제 메서드를 호출해 반환값을 assert 하는 테스트가 diff 에 없다. `DistributionServiceTest` 는 `redistribute` 만 호출하고, `DistributionControllerTest.java:40,56,67,97` 는 서비스를 mock 한다. 체크리스트 §1 "본문이 바뀐 public 메서드마다 같은 diff 안에 assert 테스트" [반려]에 걸린다. 처음에는 두 보고가 엇갈렸으나 사람이 수용했다(아래 "엇갈린 보고") | `DistributionServiceTest` 에 Mockito 테스트를 추가한다. 두 메서드의 반환값을 assert 하고, `getDistribution` 의 404(`NotFoundException`) 경로도 넣는다 |
| 4 | 제안 | `modern/api/src/main/java/com/example/domain/item/TagRepository.java:6`, `infrastructure/item/TagRepositoryImpl.java:19` | main 코드에서 주입하거나 호출하는 곳이 없다(`grep -rl TagRepository` 결과는 정의 파일 2개뿐). 태그 조건은 JPQL EXISTS 로 처리한다 | 쓰임이 생길 때 만들거나, 남긴다면 #1 의 테스트를 함께 둔다 |
| 5 | 제안 (의심 동작) | `modern/api/src/main/java/com/example/interfaces/item/ItemSearchParams.java:32-42` | 배열 키(`name[]`)가 항상 일반 키보다 우선한다. PHP `$_GET` 은 뒤에 나온 키가 덮어쓰므로 `?tag[]=a&tag=b` 의 결과가 PHP 는 `b`, modern 은 `a` 로 다르다(`legacy/item-bank-php/search.php:66-68`). characterization 에는 이 경우가 없다 | 이관 중에는 고치지 않는다(`CLAUDE.md` §5). 의심 동작 목록에 올리고, 레거시와 맞출지는 사람이 정한다. 맞추게 되면 characterization 케이스를 먼저 추가한다 |
| 6 | 제안 (diff 밖 기존 위반) | `modern/api/src/main/java/com/example/application/assignment/ReportService.java:109-110` | catch 에서 `IllegalStateException` 을 던진다. 체크리스트 §4 는 `domain` · `common` 예외를 요구한다. upstream 원본에 이미 있던 코드다 | 별도 작업으로 `common` 예외로 바꾼다 |
| 7 | 제안 (diff 밖 기존 위반) | `modern/api/src/main/java/com/example/common/ErrorResponse.java:9` | 인자 없는 `Instant.now()` 를 쓴다. `scope-decision.md` 에 별도 작업으로 기록되어 있다 | `Clock` 을 주입받아 쓴다 |

## 엇갈린 보고 — 사람 판단: 수용 (2026-10-06)

**이슈 #3: `DistributionService.getDistribution` · `listByClass` (`DistributionService.java:34,40`) 에 테스트가 있는가**

| 출처 | 주장 | 근거 |
|---|---|---|
| reviewer | 없음(주요). 두 메서드는 반환 타입과 매핑(`DistributionDetail.from`)이 바뀌었는데, 실제 메서드를 호출해 반환값을 assert 하는 테스트가 없다 | `DistributionServiceTest` 는 `redistribute` 만 호출한다. 컨트롤러 테스트는 서비스를 mock 한다 |
| tester | 있음. "`DistributionService` → `DistributionServiceTest`"로 보고했고, 테스트를 추가하지 않았다 | 메서드 단위가 아니라 파일 단위로 대응시킨 것으로 보인다 |

메인 세션에서 `grep` 으로 확인한 사실은 다음과 같다. `getDistribution` · `listByClass` 를 부르는 테스트는 `DistributionControllerTest.java:40,56,67,97` 뿐이고, 모두 `when(distributionService...)` 로 mock 을 설정한 곳이다. 실제 서비스 메서드를 호출하는 테스트는 찾지 못했다.

skill 규칙에 따라 리뷰 세션은 이 항목을 판정하지 않고 사람에게 넘겼다. 사람(HongChangMo)은 reviewer 의 지적을 **수용**했다. 그래서 이 항목은 이슈 #3(주요)로 확정했다. 테스트 기준은 원래 #1 · #2 로 미충족이었으므로 최종 판정(반려)은 그대로다.

## 확인 필요 (근거 줄번호를 댈 수 없는 지적)

- 잘못된 UTF-8 퍼센트 인코딩 입력에서 PHP 는 바이트를 그대로 쓰고, Java 는 U+FFFD 로 바꾼다. 그래서 `mb_strlen` 기준 길이와 자르는 위치가 달라질 수 있다. 재현 테스트가 없어 확인하지 못했다.
- `DistributionService` 의 재배포 로그는 자유 입력인 `reason` 을 그대로 남긴다(diff 밖 기존 코드). 이 입력에 학생 식별자(`STU-…`)가 들어올 수 있는지는 확인하지 못했다.

## 되돌리기 (만든 세션 · 사람에게)

1. 이슈 #1 · #2 를 해소한다. tester 가 추가한 미커밋 테스트 5개 파일을 검토해 커밋하면 된다.
2. 이슈 #3(수용됨)을 반영한다. `DistributionServiceTest` 에 `getDistribution` · `listByClass` 의 반환값 테스트와 404 경로 테스트를 추가한다. tester 가 추가한 파일에는 이 테스트가 없다.
3. 반영한 뒤 2단계부터 다시 검증한다.
