# 단원별 · 난이도별 문항 수 조회 API — 작업 계획

> 상태: 계획 (미구현). 근거는 현재 코드 · 기존 테스트 · DB 스키마.

## 0. 설계 가정 (확인 필요)

- 엔드포인트: `GET /api/items/level-counts` — 기존 `ItemController` 의 `/api` + `/items/...` 매핑에 맞춘다.
- 집계 대상은 공개 문항(`ItemStatus.ACTIVE`, `'A'`)만. `ItemStatus` 주석: "외부에 노출하는 문항은 ACTIVE 만".
- 응답 순서는 `/api/units` 와 같다(`UnitRepository.findAllByOrderByGradeAscCodeAsc`).
- 문항이 없는 단원 · 난이도도 0으로 채운다. 난이도 범위 1~5 는 `Item.level` 주석과 `db/mariadb/init/01-schema.sql:25` 근거.
- 쿼리는 요청당 2번: 단원 목록 1번 + `GROUP BY unit, level` 집계 1번. 단원마다 `countByUnitIdAndStatus` 를 부르는 방식(N+1)은 쓰지 않는다.

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

## 1. 새로 만들거나 고칠 파일 (`modern/api/src/...`)

| 구분 | 경로 | 내용 |
|---|---|---|
| 수정 | `main/java/com/example/item/ItemRepository.java` | `@Query` 로 `unit.id`, `level`, `count` 를 묶어 세는 메서드 추가 |
| 신규 | `main/java/com/example/item/UnitLevelCount.java` | 집계 결과 인터페이스 프로젝션(`unitId`, `level`, `cnt`) |
| 신규 | `main/java/com/example/item/UnitLevelCountResponse.java` | 응답 record(`unitCode`, `unitName`, `grade`, `counts`, `total`) |
| 수정 | `main/java/com/example/item/ItemService.java` | `countActiveItemsByUnitAndLevel()` 추가, 난이도 범위는 `MIN_LEVEL` · `MAX_LEVEL` 상수 |
| 수정 | `main/java/com/example/item/ItemController.java` | `@GetMapping("/items/level-counts")` 추가 |
| 수정 | `test/java/com/example/item/ItemRepositoryTest.java` | 집계 쿼리 테스트 |
| 수정 | `test/java/com/example/item/ItemServiceTest.java` | 조합 · 0 채우기 테스트 |
| 수정 | `test/java/com/example/item/ItemControllerTest.java` | 엔드포인트 테스트 |

## 2. 계층과 호출 방향

```
ItemController ──▶ ItemService (@Transactional(readOnly = true), DTO 변환)
                     ├─▶ UnitRepository.findAllByOrderByGradeAscCodeAsc()  (기존)
                     └─▶ ItemRepository 집계 메서드                          (신규)
```

- 컨트롤러는 서비스만 호출한다.
- 엔티티는 서비스 안에서 DTO 로 바꾼다(OSIV 꺼져 있음).
- 오류 응답은 `GlobalExceptionHandler` 가 만든다.

## 3. 테스트와 실행 명령

- `ItemRepositoryTest` (`@DataJpaTest`, H2, 기존 시드 사용)
  - M5-1 → `{2:1, 4:1, 5:1}`, M5-3 → `{1:1}`
  - 상태가 R · D 인 문항은 제외된다.
- `ItemServiceTest` (Mockito)
  - 빈 단원 · 빈 난이도는 0으로 채운다.
  - 단원 순서가 유지된다.
  - `total` 은 난이도별 개수의 합과 같다.
- `ItemControllerTest` (`@WebMvcTest` + `@MockBean`)
  - 200 과 JSON 필드(`$[0].counts.5`, `$[0].total`) 확인.
  - `level-counts` 가 `/items/{id}` 로 잡혀 400 이 나지 않는다.

```bash
cd modern/api && ./gradlew test --tests 'com.example.item.*'
cd modern/api && ./gradlew test
```

## 4. 완료 기준

- 새 테스트와 기존 테스트가 모두 통과한다(`./gradlew test`).
- 요청 한 번에 쿼리가 2번만 나간다.
- 응답은 엔티티가 아니라 record DTO 다.
- 1번 목록에 없는 파일은 바뀌지 않는다.

## 5. 하지 않을 일

- 프론트엔드(`modern/web`) 연동
- DB 스키마 · 인덱스 · 시드 변경
- `build.gradle` · `application.yml` 변경
- 필터 파라미터(학년 · 상태), 페이지네이션, 캐시 추가
- 패키지 구조 개편
- `assignment` 도메인이나 기존 엔드포인트 동작 변경
- 커밋 · PR 생성
