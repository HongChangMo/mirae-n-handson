# 단원별 · 난이도별 문항 수 조회 API — 작업 계획 (CLAUDE.md 적용)

> 상태: 계획 (미구현).
> 이 문서는 루트 `CLAUDE.md` 가 있을 때 같은 요청에 대해 세운 계획이다.
> `CLAUDE.md` 가 없을 때의 계획은 `docs/unit-level-counts-api-plan.md` 를 본다.

## 두 계획이 다른 이유

`docs/unit-level-counts-api-plan.md` 는 기존 `com.example.item` 패키지에 파일을 더하고, 패키지 구조 개편을 "하지 않을 일"로 둔다.
`CLAUDE.md` 는 "기존 도메인 코드를 고치는 작업에는 그 도메인의 파일을 네 계층 패키지 아래로 옮기는 일이 포함된다"고 정한다.
또 옮기기 전에 이동 · 신규 파일 목록을 보여 주고 승인을 받아야 한다.
그래서 이 계획은 **item 도메인을 4계층으로 옮기는 일과 새 API 추가를 함께** 다룬다.

## 0. 설계 가정

- **엔드포인트:** `GET /api/items/level-counts`. `/api/<도메인 복수형>` 규칙을 따른다. 기존 `/api/items/{id}` 와 겹치지 않는지 테스트로 확인한다.
- **집계 대상:** 공개 문항(`status = 'A'`, `ItemStatus.ACTIVE`)만 센다.
- **단원 순서:** `/api/units` 와 같은 학년 → 코드 순.
- **빈 칸 채우기:** 문항이 없는 단원이나 난이도도 0으로 채운다.
- **난이도 범위:** 1~5. 근거는 `db/mariadb/init/01-schema.sql:25`.
  - DB에 CHECK 제약이 없어 범위 밖 값이 들어올 수 있다. 이런 값은 집계에서 빼고, `total` 은 난이도별 개수의 합으로 둔다. **(확인 필요)**
- **쿼리 수:** 요청 한 번에 두 번. 단원 목록 조회 1번, `GROUP BY unit_id, level` 집계 1번. 단원마다 따로 세는 N+1 방식은 쓰지 않는다.

응답 예시:

```json
[
  {
    "unitCode": "M5-1",
    "unitName": "분수의 덧셈과 뺄셈",
    "grade": 5,
    "counts": { "1": 0, "2": 1, "3": 0, "4": 1, "5": 1 },
    "total": 3
  }
]
```

## 1. 새로 만들거나 고칠 파일 (승인이 필요한 목록)

기준 경로는 `modern/api/src/main/java/com/example/` 이다.

### A. 옮길 파일 (item 도메인 → 4계층)

| 기존 위치 | 옮길 위치 | 바뀌는 점 |
|---|---|---|
| `item/Item.java` | `domain/item/Item.java` | `MIN_LEVEL = 1`, `MAX_LEVEL = 5` 상수 추가 |
| `item/Unit.java` · `Tag.java` · `ItemStatus.java` | `domain/item/` | 패키지 선언만 바뀜 |
| `item/ItemRepository.java` | `domain/item/ItemRepository.java` | 순수 인터페이스로 바꿈(Spring Data import 없음), 집계 메서드 추가 |
| `item/UnitRepository.java` · `TagRepository.java` | `domain/item/` | 순수 인터페이스로 바꿈 |
| `item/ItemService.java` · `UnitService.java` | `application/item/` | 반환 타입을 application 결과 record로 바꿈, 집계 메서드 추가 |
| `item/ItemController.java` · `UnitController.java` | `interfaces/item/` | 결과 record를 응답 DTO로 바꿈, 새 엔드포인트 추가 |
| `item/ItemResponse.java` · `UnitResponse.java` | `interfaces/item/` | `from(...)` 의 입력이 결과 record로 바뀜 |

### B. 새로 만들 파일

| 경로 | 계층 | 내용 |
|---|---|---|
| `domain/item/UnitLevelCount.java` | domain | 집계 한 행을 담는 record(`unitId`, `level`, `count`) |
| `application/item/ItemInfo.java` · `UnitInfo.java` | application | 서비스가 돌려주는 결과 record |
| `application/item/UnitLevelCountInfo.java` | application | 단원 하나의 집계 결과(0 채우기 완료, `total` 포함) |
| `interfaces/item/UnitLevelCountResponse.java` | interfaces | 응답 record |
| `infrastructure/item/ItemJpaRepository.java` 외 2개(Unit · Tag) | infrastructure | Spring Data 리포지토리. `@EntityGraph` 와 집계 JPQL을 여기에 둠 |
| `infrastructure/item/ItemRepositoryImpl.java` 외 2개(Unit · Tag) | infrastructure | domain 리포지토리 인터페이스를 `implements` 하는 구현체 |

결과 record를 따로 두는 이유:
- `application` 은 `interfaces` 의 `*Response` 를 import 할 수 없다.
- OSIV가 꺼져 있어 엔티티를 트랜잭션 밖으로 넘길 수 없다.

### C. 범위 밖이지만 import만 바꿔야 하는 파일

- `assignment/Assignment.java:3` — `com.example.item.Unit` 을 `com.example.domain.item.Unit` 으로 바꾼다.
  - 이 한 줄 외에는 assignment 코드를 고치지 않는다.
  - assignment가 item 엔티티를 직접 참조하는 문제는 그대로 둔다.

### D. 옮기고 고칠 테스트 파일 (삭제 없음)

기준 경로는 `modern/api/src/test/java/com/example/` 이다.

- `item/ItemControllerTest.java` → `interfaces/item/`: mock 반환 타입을 결과 record로 바꾸고, 새 테스트를 추가한다.
- `item/ItemServiceTest.java` · `UnitServiceTest.java` → `application/item/`: 기대값 타입을 바꾸고, 새 테스트를 추가한다.
- `item/ItemRepositoryTest.java` → `infrastructure/item/`: 기존 테스트 3개를 유지하고 집계 테스트를 추가한다.
- `item/ItemFixtures.java`: 여러 패키지에서 쓰도록 public으로 바꾼다.
- `assignment/DistributionServiceTest.java`, `assignment/ReportServiceTest.java`, `config/WebConfigTest.java`, `ItemBankApplicationTests.java`: import만 바꾼다.

## 2. 계층과 호출 방향

```
interfaces.item.ItemController ──▶ application.item.ItemService (@Transactional(readOnly = true))
                                     ├─▶ domain.item.UnitRepository.findAllByOrderByGradeAscCodeAsc()
                                     └─▶ domain.item.ItemRepository.countActiveByUnitAndLevel()
                                              ▲ implements
                          infrastructure.item.ItemRepositoryImpl ──▶ ItemJpaRepository (JPQL GROUP BY)
```

- 컨트롤러는 서비스만 주입받는다.
- 서비스는 domain 인터페이스에만 의존한다.
- 0 채우기와 `total` 계산은 서비스에서 한다.
- 오류 응답은 기존 `GlobalExceptionHandler` 가 만들고, 컨트롤러에는 `try`/`catch` 를 두지 않는다.

## 3. 테스트와 실행 명령

**리포지토리 (`@DataJpaTest`, H2, 기존 시드 사용)**
- M5-1 → `{2:1, 4:1, 5:1}`, M5-3 → `{1:1}`
- 상태가 R · D 인 문항은 빠진다.

**서비스 (Mockito)**
- 문항이 없는 단원과 비어 있는 난이도는 0으로 채운다.
- 단원 순서가 유지된다.
- `total` 은 난이도별 개수의 합과 같다.
- 범위 밖 난이도는 집계에서 빠진다.

**컨트롤러 (`@WebMvcTest` + `@MockBean`)**
- 200 응답과 `$[0].counts.5`, `$[0].total` 값을 확인한다.
- `level-counts` 가 `/items/{id}` 로 잡혀 400이 나지 않는다.

모든 테스트에 `@ActiveProfiles("test")` 를 붙이고, 이름은 camelCase에 한국어 `@DisplayName` 을 단다.

```bash
cd modern/api && ./gradlew test --tests 'com.example.*.item.*'
cd modern/api && ./gradlew test      # 전체, 통과 · 실패 수를 답변에 적음
```

## 4. 완료 기준

- `./gradlew test` 의 기존 테스트와 새 테스트가 모두 통과하고, 삭제되거나 `@Disabled` 된 테스트가 없다.
- `grep` 으로 다음을 확인한다.
  - `interfaces` · `application` · `domain` 파일에 `com.example.infrastructure` import가 없다.
  - `domain` 파일에 `org.springframework.data` import가 없다.
- 컨트롤러는 record DTO만 반환하고, 서비스만 주입받는다.
- 요청 한 번에 쿼리가 두 번만 나간다(집계 쿼리 1번).
- 1번 목록 밖의 파일은 바뀌지 않는다.
- 한 줄 120자 이내이고 와일드카드 import가 없다.

## 5. 이 작업에서 하지 않을 일

- 프론트엔드(`modern/web`) 연동
- assignment 도메인을 4계층으로 옮기는 일, 기존 엔드포인트 동작 변경 (`Assignment.java` 는 import 한 줄만 고침)
- DB 스키마 · 인덱스 · 시드 변경, 마이그레이션 추가
- `build.gradle` 의존성 변경, `application.yml` 변경
- 필터 파라미터(학년 · 상태), 페이지네이션, 캐시 추가
- `legacy/`, `characterization/` 수정
- 커밋 · PR 생성

## 6. 구현 전에 결정할 것

1. **4계층 이동 승인** — 1번의 A~D 목록대로 옮겨도 되는가.
2. **범위 밖 난이도 처리** — 1~5 밖의 값을 집계에서 빼는 처리로 괜찮은가.
