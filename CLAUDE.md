# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

- 답변과 문서는 한국어로 쓴다.
- `legacy/` 는 분석 · 이관 대상이며 허락 없이 수정하지 않는다.

대상 코드: `modern/api`(Spring Boot 3.3 · Java 21 · Gradle) · `modern/web`(React 18 · TypeScript · Vite · Vitest).
도메인 용어: 문항 `item` · 단원 `unit` · 난이도 `level`(1~5) · 태그 `tag` / 학급 `class` · 과제 `assignment` · 배포 `distribution` · 제출 `submission` / 학생 식별자 `STU-<숫자>`.

## 1. 빌드 · 테스트 명령

```bash
cd modern/api && ./gradlew test                                  # H2(test 프로필), DB 컨테이너 없이 통과
cd modern/api && ./gradlew test --tests 'com.example.application.item.ItemServiceTest'
cd modern/api && ./gradlew build                                 # 테스트 포함 전체 빌드

cd modern/web && npm ci                                          # package-lock.json 을 바꾸지 않는 설치
cd modern/web && npm run lint && npm run typecheck && npm test
cd modern/web && npx vitest run src/components/ItemTable.test.tsx
cd modern/web && npm run build
```

- `modern/api` 를 바꿨으면 `./gradlew test`, `modern/web` 을 바꿨으면 `npm run lint && npm run typecheck && npm test` 를 실행하고 통과 · 실패 수를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 적는다.
- 하나라도 실패하면 답변에 "완료"라고 쓰지 않는다.

## 2. 코딩 컨벤션

### modern/api — 4계층 레이어드 아키텍처
- 최상위를 계층으로 나누고 그 아래에 도메인 패키지를 둔다: `com.example.<계층>.<도메인>`. 도메인에 걸치지 않는 코드만 `com.example.common` · `com.example.config` 에 둔다.

```
com/example/
├── interfaces/      item/ · assignment/
├── application/     item/ · assignment/
├── domain/          item/ · assignment/
└── infrastructure/  item/ · assignment/
```

| 계층 | 두는 것 | import 해도 되는 계층 |
|---|---|---|
| `interfaces` | `@RestController`, 요청 · 응답 record DTO | `application` |
| `application` | 유스케이스 서비스, `@Transactional` | `domain` |
| `domain` | 엔티티, 도메인 예외, 리포지토리 **인터페이스** | 없음 (다른 세 계층을 import 하지 않는다) |
| `infrastructure` | 리포지토리 구현체 `*RepositoryImpl`, Spring Data `*JpaRepository`, 외부 연동 클라이언트 | `domain` |

- DIP: `application` 은 `domain` 의 리포지토리 인터페이스에만 의존한다. 구현체는 `infrastructure` 에 두고 그 인터페이스를 `implements` 한다.
- 이름: 도메인 리포지토리 `<X>Repository`(인터페이스) · 구현체 `<X>RepositoryImpl` · Spring Data `<X>JpaRepository`. Spring Data 는 `<리포지토리명>Impl` 클래스를 커스텀 구현(fragment)으로 자동 연결하므로 Spring Data 인터페이스를 `<X>Repository` 로 이름 짓지 않는다.
- `interfaces` · `application` · `domain` 파일에 `com.example.infrastructure` import 가 없어야 한다. `domain` 파일에 `org.springframework.data` import 가 없어야 한다.
- `interfaces` 는 `application` 의 서비스만 주입받는다. 리포지토리를 주입받거나 SQL 문자열을 두지 않는다.
- 다른 도메인의 데이터는 `application.<그 도메인>` 의 서비스를 통해 읽는다. 다른 도메인의 리포지토리를 주입받지 않는다.
- 엔티티를 컨트롤러 반환 타입으로 쓰지 않는다. 응답은 record DTO 다.
- item · assignment 도메인은 4계층 이관을 마쳤다. 4계층이 아닌 도메인 코드를 고치는 작업에는 그 도메인의 파일을 네 계층 패키지 아래 `<도메인>/` 으로 옮기는 일이 포함된다. 옮기기 전에 이동 · 신규 파일 목록을 답변에 먼저 보여 주고 승인받는다.

### modern/api — 예외 · 로깅
- 예외를 삼키지 않는다. 빈 `catch` 블록, 그리고 로그만 남기고 다시 던지지 않는 `catch` 블록이 없어야 한다. 잡으면 도메인 예외로 바꿔 던진다(원인 예외를 생성자에 넘긴다).
- 예외 → HTTP 응답 변환은 `common/GlobalExceptionHandler` 에서만 한다. 컨트롤러 메서드에 `try`/`catch` 가 없어야 한다.
- 없는 리소스 404, 검증 실패 400, 상태 충돌 409(`common.ConflictException` 을 상속한 도메인 예외, 예: `AssignmentClosedException`), 그 밖의 예외 500. `IllegalStateException` 같은 범용 예외를 409 로 매핑하지 않는다.
- 로그는 SLF4J(`org.slf4j.Logger`)로만 남긴다. `System.out` · `System.err` · `printStackTrace()` 가 없어야 한다.
- 로그 인자에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 넣지 않는다.

### modern/api — 테스트 · 형식
- 새 public 서비스 메서드 · 새 엔드포인트마다 테스트 1개 이상. 동작을 바꾼 메서드는 그 동작을 검증하는 테스트가 추가되거나 수정되어야 한다.
- 컨트롤러는 `@WebMvcTest` + `@MockBean`, 서비스는 Mockito 단위 테스트, 리포지토리 쿼리는 `@DataJpaTest`. 모두 `@ActiveProfiles("test")`.
- 테스트 메서드 이름은 camelCase, `@DisplayName` 에 한국어 설명을 단다.
- 시각은 `Clock` 빈을 주입받아 쓴다. `LocalDateTime.now()` · `Instant.now()` 를 인자 없이 호출하지 않는다.
- 들여쓰기 4칸, 한 줄 120자 이내, 와일드카드 import 금지. 규칙 값(난이도 상한 등)은 `UPPER_SNAKE_CASE` 상수로 선언한다.

### modern/web
- `fetch` 는 `src/api/client.ts` 에서만 호출한다. 다른 파일에 `fetch(` 가 없어야 한다.
- 서버 조회 훅은 `src/hooks/` 에 두고 `useApiQuery` 로 만든다. 컴포넌트는 훅이 돌려준 `QueryState` 의 `status` 로 로딩 · 오류 · 성공을 분기한다.
- 응답 타입은 `src/api/types.ts` 에 두고 백엔드 JSON 필드명을 그대로 쓴다.
- 컴포넌트에서 HTTP 상태 코드를 비교하지 않는다. 오류 문구 변환은 `toErrorMessage` 한 곳에서 한다.
- 함수 컴포넌트만 쓴다. `React.FC` · 클래스 컴포넌트 · `defaultProps` 를 쓰지 않는다.
- `any` · `as unknown as` · `@ts-ignore` · `dangerouslySetInnerHTML` · `console.log` 가 없어야 한다.
- `eslint-disable` 주석은 같은 줄에 사유를 적는다.
- 테스트는 Testing Library, 조회는 `getByRole` · `getByLabelText` 우선. 네트워크는 `src/test/mockFetch.ts` 로 가로채고 실제 `localhost:8080` 을 호출하지 않는다. 스냅샷 테스트(`toMatchSnapshot`)를 만들지 않는다.

## 3. 금지 사항

- 의존성 추가 · 변경(`build.gradle` 의 `dependencies`, `package.json`)은 먼저 묻는다. 이유와 대안을 함께 적는다.
- DB 스키마(테이블 · 컬럼 · 인덱스) · 시드 데이터 변경과 마이그레이션 파일 추가는 먼저 묻는다.
- `modern/api/src/main/resources/application.yml` 의 DB 접속 정보 · 커넥션 풀 설정을 바꾸지 않는다.
- 비밀번호 · 토큰 · 키를 코드 · 설정 · 테스트 픽스처에 리터럴로 넣지 않는다. 실습용 더미(`app-pass`)는 예외다.
- 운영 DB 호스트(`prod-db` 등)에 접속하는 명령 · 설정을 만들지 않는다.
- `@Transactional` 메서드 안에서 외부 HTTP 호출이나 요청 건수에 비례하는 루프 안의 개별 쿼리를 두지 않는다.
- 테스트가 실제 MariaDB 에 붙게 만들지 않는다(테스트 설정은 `src/test/resources/application-test.yml`).
- 기존 테스트를 지우거나 `@Disabled` · `.skip` 으로 바꾸지 않는다.
- `package-lock.json` 을 손으로 고치거나 지우지 않는다. `.env*` 파일을 읽거나 만들지 않는다.
- 요청에 없는 파일을 고치지 않는다. 고쳤다면 답변에 파일명과 이유를 적는다. 포맷 · import 정리도 요청 범위 안의 파일에서만 한다.
- `characterization/` 의 테스트 · 스냅샷을 고쳐서 비교를 통과시키지 않는다. 비교가 깨지면 먼저 사람에게 알린다.

## 4. 아키텍처 안내

### modern/api (포트 8080) — 현재 구조 (item · assignment 모두 4계층 이관 완료)
```
com/example/
├── interfaces/item/            ItemController, UnitController, ItemSearchController, *Response · ItemSearchRow · ItemSearchParams
├── interfaces/assignment/      DistributionController, ReportController, DistributionResponse · ClassReportResponse · RedistributeRequest
├── application/item/           ItemService, UnitService, ItemSearchService, ItemDetail · UnitSummary · ItemSearch{Command,Result}
├── application/assignment/     DistributionService, ReportService, DistributionDetail · ClassReport
├── domain/item/                Item, Unit, Tag, ItemStatus, *Repository(인터페이스), ItemSearchCondition · ItemSortOrder · PhpStrings
├── domain/assignment/          Assignment, ClassRoom, Distribution, Submission, *Repository(인터페이스)
├── infrastructure/item/        *JpaRepository(Spring Data), *RepositoryImpl(도메인 리포지토리 구현, 검색 JPQL)
├── infrastructure/assignment/  *JpaRepository(Spring Data), *RepositoryImpl(도메인 리포지토리 구현)
├── common/                     GlobalExceptionHandler, NotFoundException, ErrorResponse, ClockConfig, RootController
└── config/                     WebConfig — /api/** 에 http://localhost:5173 의 GET 만 CORS 허용
```

- 현재 엔드포인트: `GET /`, `GET /api/units`, `GET /api/units/{code}/items`, `GET /api/items/{id}`, `GET /api/items/search`(레거시 `search.php` 이관), `GET /api/distributions/{id}`, `POST /api/distributions/{id}/redistribute`, `GET /api/classes/{id}/report`. 새 엔드포인트는 `/api/<도메인 복수형>` 아래에 둔다.
- 공개 문항은 `status = 'A'`(`ItemStatus.ACTIVE`)로 판단한다. 삭제 플래그가 아니라 상태 코드다.
- 기본 프로필은 로컬 MariaDB(`itembank`), 테스트 프로필은 H2(MariaDB 모드, `ddl-auto: create-drop`).
- Hikari 풀은 운영 값과 같게 작다(최대 5, 대기 3초). 트랜잭션을 오래 쥐는 코드는 곧바로 풀 고갈로 이어진다.
- 레거시 규칙을 옮길 때는 근거를 `파일:줄번호` 로 적는다(예: `legacy/item-bank-php/search.php:214`).

### modern/web (포트 5173)
```
src/
├── api/          client.ts(getJson · ApiError · API_BASE) · items.ts(API 함수) · types.ts(응답 타입)
├── hooks/        useApiQuery(공통) · useUnits · useUnitItems · useItem
├── components/   화면 조각과 같은 폴더의 *.test.tsx
└── test/         mockFetch.ts · fixtures.ts
```

- 데이터 흐름: 컴포넌트 → `hooks/use*` → `useApiQuery` → `api/items.ts` → `api/client.ts` 의 `getJson` → `modern/api`.
- `useApiQuery(key, load)`: `key` 가 `null` 이면 조회하지 않는다. `key` 가 바뀌거나 언마운트되면 이전 요청을 `AbortSignal` 로 취소한다.
- API 주소는 `VITE_API_BASE`, 없으면 `http://localhost:8080`. 빈 문자열이면 상대 경로를 쓰고 `vite.config.ts` 의 `/api` 프록시가 8080 으로 넘긴다.

## 5. 완료 기준

- 이관 · 리팩토링 작업은 `characterization/` 의 `npm test` 가 전부 통과하기 전에는 완료라고 보고하지 않는다.
- 테스트가 실패하면 실패한 케이스와 차이를 그대로 보고한다. 요약해서 "거의 됐다"고 말하지 않는다.
- 테스트를 통과시키려고 `characterization/` 의 테스트 코드나 스냅샷 파일을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 멈추고 묻는다.
- 레거시 동작이 버그로 보여도 이관 중에는 고치지 않는다. "의심 동작" 목록으로 따로 보고한다.
