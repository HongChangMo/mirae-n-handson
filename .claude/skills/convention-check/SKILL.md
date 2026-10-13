---
name: convention-check
description: 변경 파일(또는 지정 경로)이 CLAUDE.md 의 코딩 컨벤션 · 금지 사항을 지키는지 점검하고 위반을 파일:줄번호로 보고한다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인", "규칙 위반 있는지 봐 줘" 같은 요청에 쓴다.
argument-hint: "[점검할 경로 ...] (생략하면 git 변경 파일)"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash(git diff *)
  - Bash(git status *)
---

# 컨벤션 점검

## 원칙
- 코드를 직접 고치지 않는다. 보고만 한다.
- 근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.

## 1. 점검 대상 정하기
- `$ARGUMENTS` 에 경로가 있으면 그 경로(디렉터리면 그 아래 파일 전체)만 본다.
- 없으면 `git status --porcelain` 과 `git diff HEAD --name-only` 에 잡힌 파일을 합친다.
  `??`(아직 add 하지 않은 새 파일)도 포함하고, 삭제된 파일은 뺀다.
- 대상이 없으면 "점검 대상 없음"만 출력하고 끝낸다.

## 2. 점검 항목 (예 = 통과)
1. **계층 import** — `interfaces` · `application` · `domain` 파일에 `com.example.infrastructure` import 가 없고,
   `domain` 파일에 `org.springframework.data` import 가 없는가
2. **컨트롤러** — `@RestController` 클래스에 `try`/`catch`, 리포지토리 주입, SQL 문자열, 엔티티 반환 타입이 없는가
3. **예외 삼키기** — 빈 `catch`, 로그만 남기고 다시 던지지 않는 `catch` 가 없는가
4. **로깅** — `System.out` · `System.err` · `printStackTrace()` 가 없고, 로그 인자에 `STU-` · 이메일 · 토큰이 없는가
5. **api 형식** — 인자 없는 `LocalDateTime.now()` · `Instant.now()`, 와일드카드 import, 120자 넘는 줄이 없는가
6. **web 코드** — `src/api/client.ts` 밖에 `fetch(` 가 없고, `any` · `as unknown as` · `@ts-ignore` ·
   `dangerouslySetInnerHTML` · `console.log` · `React.FC` 가 없으며, `eslint-disable` 에 같은 줄 사유가 있는가
7. **테스트** — `@Disabled` · `.skip` · `toMatchSnapshot` 이 새로 생기지 않았고,
   `modern/api` 테스트 클래스에 `@ActiveProfiles("test")` 가 있는가
8. **금지 파일 · 값** — `build.gradle` dependencies · `package.json` · `package-lock.json` · 마이그레이션/시드 ·
   `application.yml` DB 설정이 바뀌지 않았고, `app-pass` 외 비밀번호 · 토큰 · 키 리터럴이 없는가

- 항목에 해당하지 않는 파일(예: web 파일에 1~5번)은 "해당 없음"으로 둔다.
- 7 · 8번의 "새로 생김 / 바뀜"은 `git diff HEAD -- <파일>` 로 확인한다. 새 파일은 전체가 추가분이다.
- 주석 · 문자열 안의 일치는 실제 위반인지 읽어 보고 판단한다.

## 3. 출력 형식 (순서 고정)

### 판정 요약
| # | 항목 | 판정 |
|---|---|---|
| 1 | 계층 import | 통과 / 위반 N건 / 확인 필요 / 해당 없음 |

점검 파일 수, 위반 총건수, 확인 필요 건수를 한 줄로 덧붙인다.

### 위반 목록
| 파일:줄번호 | 어긴 규칙 | 수정 방향 |
|---|---|---|
| `modern/api/.../X.java:42` | #3 예외 삼키기 | 도메인 예외로 감싸 원인과 함께 던진다 |

- 위반이 없으면 표 대신 "위반 없음"이라고 쓴다.
- "확인 필요"는 표 아래에 목록으로 따로 적고, 왜 판정할 수 없었는지 한 줄로 밝힌다.
