# 승인 체크리스트 v1

> **v1 · 2회차** (2026-10-06)
> 1. 테스트 필수 대상을 "네 계층의 새 public 메서드(접근자 · 생성자 제외)"로 정하고, 테스트 누락을 컨벤션에서 테스트 기준으로 옮겼다.
> 2. 치명을 4묶음(비밀값 · SQL · DB 접속 / 데이터 손실 / 개인정보 로그 / 트랜잭션 풀 고갈)으로 열거하고, 심각도에 '주요'를 추가했다.
> 3. 범위 밖 변경과 컨벤션 위반을 [반려] · [경고] 항목으로 나누고, 모든 항목을 예/아니오로 답하는 문장으로 바꿨다.

AI 가 만든 변경분 · 외주사 PR 을 머지하기 전에 리뷰어가 대조하는 목록이다. 네 기준이 **모두** 충족되어야 승인이다.

## 판정 규칙

- 항목마다 **예 · 아니오 · 확인 필요** 중 하나로 답한다. 해당하는 변경이 없으면 "예(해당 없음)"로 적는다.
- [반려] 항목이나 §2 항목에 "아니오"가 하나라도 있으면 그 기준은 **미충족**이고, 판정은 반려다.
- [경고] 항목의 "아니오"는 경고 이슈로 적는다. 승인을 막지 않으며, 수정 요청에는 포함한다.
- 확인할 수 없는 항목은 "확인 필요"로 적고 **충족으로 두지 않는다.**

## 심각도 정의

- **치명** — §2 항목 위반. 1건이라도 있으면 반려.
- **주요** — §1 · §3 · §4 의 [반려] 항목 위반. 해당 기준이 미충족이 되어 반려.
- **경고** — [경고] 항목 위반. 승인을 막지 않는다.
- **제안** — 더 나은 방법이 있다는 의견, 그리고 diff 밖에 원래 있던 위반. 승인 · 반려에 영향을 주지 않는다.

이슈 한 건은 심각도 · `파일:줄번호` · 근거 · 수정 방향 네 항목으로 적는다. 근거 줄번호를 댈 수 없는 지적은 "확인 필요"로 따로 둔다.

## 1. 테스트 통과

- [ ] [반려] 바뀐 폴더별 테스트 명령(`verify` Skill 4단계 표)을 이번 리뷰에서 실제로 실행했는가?
- [ ] [반려] 실행 결과 실패 · 오류가 0건인가?
- [ ] [반려] `modern/web` 을 바꿨다면 `npm run lint` · `npm run typecheck` 의 오류가 0건인가?
- [ ] [반려] 이관 · 리팩토링 변경이면 `characterization` 의 `npm test` 가 전부 통과하고, 스냅샷 파일 변경이 0건인가?
- [ ] [반려] `modern/api` 의 새 public 메서드(`interfaces` · `application` · `domain` · `infrastructure`)마다 그 메서드를 호출하는 테스트가 1개 이상 있는가? (getter · setter · 생성자 · record 컴포넌트 접근자 · `equals` · `hashCode` · `toString` 은 제외)
- [ ] [반려] 새 엔드포인트마다 그 경로를 호출하는 테스트가 1개 이상 있는가?
- [ ] [반려] diff 에서 본문이 바뀐 public 메서드마다, 그 메서드를 호출하고 반환값 · 예외 · 저장 결과를 assert 하는 테스트가 같은 diff 안에서 추가 · 수정되었는가?
- [ ] [반려] 기존 테스트를 지우거나 `@Disabled` · `.skip` 으로 바꾼 곳이 0건인가?

변경된 코드 폴더(`modern/api` · `modern/web` · `characterization`)가 없어 테스트를 실행하지 않았다면 이 기준은 "확인 필요"로 적는다.

## 2. 치명 이슈 0건

모든 항목이 치명이다. "아니오"가 1건이라도 있으면 반려.

### A. 비밀값 · SQL · DB 접속
- [ ] 비밀번호 · 토큰 · 키 리터럴이 코드 · 설정 · 테스트 픽스처에 0건인가? (실습용 더미 `app-pass` 는 제외)
- [ ] `src/main/resources` 의 yml · properties 와 Dockerfile 의 `ENV` · `ARG` · `COPY` 로 jar · 이미지에 들어가는 비밀번호 · 토큰 · 키가 0건인가? (실행 시점 환경 변수 · 시크릿 저장소 주입은 해당 없음)
- [ ] 외부 API 키를 URL 쿼리 파라미터로 보내는 곳이 0건이고, HTTP 클라이언트의 요청 원문 로그(RestClient · WebClient `DEBUG`, Apache HttpClient `wire`)를 켜는 설정이 0건인가?
- [ ] SQL · JPQL 문자열에 `+` · `String.format` · `concat` 으로 메서드 파라미터나 요청 값을 붙이는 곳이 0건인가? (enum 상수 같은 코드 안 고정 문자열은 해당 없음)
- [ ] 운영 DB 호스트(`prod-db` 등)에 접속하는 명령 · 설정이 0건인가?
- [ ] 테스트가 실제 MariaDB 에 접속하는 설정이 0건인가? (테스트 설정은 `src/test/resources/application-test.yml`)
- [ ] `modern/api/src/main/resources/application.yml` 의 DB 접속 정보 · 커넥션 풀 설정이 바뀌지 않았는가?

### B. 데이터 손실
- [ ] `DELETE` · `UPDATE` · `TRUNCATE` 문, 또는 `delete*` · `deleteAll*` · 기존 id 의 `save` 호출이 새로 생겼다면, 그 대상 테이블이 요청 문장에 나와 있는가?
- [ ] DB 스키마(테이블 · 컬럼 · 인덱스) · 시드 데이터 · 마이그레이션 파일이 바뀌었다면, 그 변경이 요청 문장에 나와 있는가?

### C. 개인정보 로그
- [ ] 로그 인자에 학생 식별자(`STU-…`) · 이메일 · 토큰 값이 0건인가?
- [ ] 응답 DTO · 오류 응답(`ErrorResponse`) 메시지에 학생 식별자 · 이메일 · 토큰 값이 0건인가?

### D. 트랜잭션 풀 고갈
- [ ] `@Transactional` 메서드 안(그 메서드가 호출하는 메서드 포함)에서 외부 HTTP 호출이 0건인가?
- [ ] `@Transactional` 메서드 안에서, 컬렉션을 도는 `for` · `forEach` · `stream` 루프 안에서 리포지토리 메서드를 호출하는 곳이 0건인가?

### E. 외부 입력
- [ ] 새 엔드포인트 · 바뀐 엔드포인트의 경로 변수 · 쿼리 파라미터 · 요청 바디가 서버에서 검증되는가? (Bean Validation `@Valid` · 제약 애너테이션, 또는 서비스의 범위 · 길이 · 허용 목록 확인)

## 3. 요청 범위 이탈 없음

- [ ] [반려] 요청 문장을 한 문장으로 확인했는가? (아니오면 판정하지 않고 먼저 묻는다)
- [ ] [반려] 요청 문장에 나온 엔드포인트 · 메서드 · 화면이 diff 안에서 추가 · 변경되었는가?
- [ ] [반려] 변경 파일마다 요청 기능의 코드 · 테스트 · 문서 중 하나에 해당하거나, PR 본문에 그 파일명과 사유가 적혀 있는가?
- [ ] [반려] 의존성(`build.gradle` 의 `dependencies`, `package.json`) · DB 스키마 · 시드 · 설정 파일 · `legacy/` · `.env*` 변경이 있다면, 그 변경이 요청 문장에 나와 있는가?
- [ ] [경고] 요청 기능에 해당하지 않는 파일의 변경이 0건인가? (PR 본문에 사유가 적힌 파일만 여기서 판정한다)

요청 기능에 해당하는 파일 안의 포맷 · import 정리는 이슈로 적지 않는다.

## 4. 컨벤션 준수

- [ ] 팀 `CLAUDE.md` 의 코딩 컨벤션과 금지 사항을 대조했는가? (`convention-check` Skill 이 있으면 그 결과를 쓴다)

### modern/api — [반려]
- [ ] `interfaces` · `application` · `domain` 파일에 `com.example.infrastructure` import 가 0건인가?
- [ ] `domain` 파일에 `org.springframework.data` · `com.example.interfaces` · `com.example.application` import 가 0건인가?
- [ ] 새 도메인 코드가 `com.example.<계층>.<도메인>` 패키지에 있는가? (`common` · `config` 에는 도메인에 걸치지 않는 코드만 둔다)
- [ ] `application` 파일이 리포지토리 · 외부 연동을 `domain` 의 인터페이스 타입으로 주입받고, 그 구현체가 `infrastructure` 에서 `implements` 하는가?
- [ ] `interfaces` 파일이 `application` 의 서비스만 주입받고, 리포지토리 주입 · SQL 문자열이 0건인가?
- [ ] 컨트롤러 메서드에 `try` / `catch` 가 0건인가?
- [ ] 다른 도메인의 리포지토리를 주입받는 곳이 0건인가?
- [ ] 컨트롤러 메서드의 반환 타입에 엔티티가 0건인가?
- [ ] 빈 `catch` 블록, 그리고 로그만 남기고 다시 던지지 않는 `catch` 블록이 0건인가?
- [ ] `catch` 블록에서 던지는 예외가 `com.example.domain` · `com.example.common` 패키지의 예외 클래스이고, 생성자에 원인 예외를 넘기는가? (`RuntimeException` · `IllegalStateException` · `IllegalArgumentException` 은 해당하지 않음)
- [ ] 409 로 매핑되는 예외가 모두 `common.ConflictException` 을 상속하는가?
- [ ] `System.out` · `System.err` · `printStackTrace()` 가 0건인가?
- [ ] 인자 없는 `LocalDateTime.now()` · `Instant.now()` 호출이 0건인가?

### modern/web — [반려]
- [ ] `src/api/client.ts` 밖에 `fetch(` 가 0건인가?
- [ ] `any` · `as unknown as` · `@ts-ignore` · `dangerouslySetInnerHTML` · `console.log` 가 0건인가?
- [ ] `React.FC` · 클래스 컴포넌트 · `defaultProps` 가 0건인가?
- [ ] 컴포넌트에서 HTTP 상태 코드를 비교하는 곳이 0건인가?
- [ ] `toMatchSnapshot` 이 0건이고, 테스트에서 실제 `localhost:8080` 을 호출하는 곳이 0건인가?

### modern/api — [경고]
- [ ] 도메인 리포지토리는 `<X>Repository`(인터페이스), 구현체는 `<X>RepositoryImpl`, Spring Data 는 `<X>JpaRepository` 로 이름 지었는가?
- [ ] 들여쓰기 4칸, 한 줄 120자 이내이고 와일드카드 import 가 0건인가?
- [ ] 규칙 값(난이도 상한 등)이 `UPPER_SNAKE_CASE` 상수로 선언되어 있는가?
- [ ] 테스트 메서드 이름이 camelCase 이고 `@DisplayName` 에 한국어 설명이 있는가?
- [ ] 컨트롤러 테스트는 `@WebMvcTest` + `@MockBean`, 서비스 테스트는 Mockito, 리포지토리 쿼리 테스트는 `@DataJpaTest` 이고 모두 `@ActiveProfiles("test")` 인가?
- [ ] 새 엔드포인트가 `/api/<도메인 복수형>` 아래에 있는가?

### modern/web — [경고]
- [ ] 서버 조회 훅이 `src/hooks/` 에 있고 `useApiQuery` 를 호출하는가?
- [ ] 컴포넌트가 `QueryState.status` 값으로 로딩 · 오류 · 성공을 분기하는가?
- [ ] 응답 타입이 `src/api/types.ts` 에 있고 백엔드 JSON 필드명과 같은가?
- [ ] 테스트에서 `getByTestId` · `container.querySelector` 를 쓴 곳이 0건인가?
- [ ] `eslint-disable` 주석마다 같은 줄에 사유가 적혀 있는가?

## 판정 형식

첫 줄에 `판정: 승인` 또는 `판정: 반려`. 이어서 네 기준의 결과 표(충족 · 미충족 · 확인 필요), 그다음 이슈 목록(심각도 · `파일:줄번호` · 근거 · 수정 방향). 코드는 수정하지 않는다.
