# 실전 프로젝트 설계 — 백엔드 / 문항 검색(search.php → GET /api/items/search) 이관 검증

> 작성일 2026-10-20 · 브랜치 `day4` · 소요 목표 1차시(약 40분) · 이 문서 작성 시점에 코드 변경 없음

## 1. 목표

레거시 `search.php` 를 이관한 `GET /api/items/search` 가 Day 1-3 의 동작 보존 케이스 16개를 레거시 · 새 API 양쪽에서 실패 0건으로 통과함을 HEAD 기준으로 다시 확인하고, 그 결과를 담은 PR 에서 자동 리뷰 코멘트를 1회 받는다.

## 2. 재료

| 재료 | 있음/없음 | 경로 |
|---|---|---|
| 동작 보존 테스트 — 문항 검색 케이스 16개(Day 1-3) | 있음 | `characterization/tests/item-bank.test.js` |
| 위 케이스의 기준 스냅샷(16개) | 있음 | `characterization/__snapshots__/item-bank.test.js.snap` |
| 경로 대응표 `/search.php` → `/api/items/search` | 있음 | `characterization/lib/target.mjs:21-24` (`PATH_ALIASES`) |
| 응답 정규화 · 대상 주소 | 있음 | `characterization/lib/normalize.mjs`, `characterization/lib/target.mjs` |
| 이관 대상 레거시 원본 | 있음 | `legacy/item-bank-php/search.php` |
| 이관된 엔드포인트(4계층) | 있음 | `modern/api/src/main/java/com/example/interfaces/item/ItemSearchController.java`, `application/item/ItemSearchService.java`, `domain/item/ItemSearchCondition.java`, `infrastructure/item/ItemRepositoryImpl.java` |
| 이관 엔드포인트 단위 테스트 | 있음 | `modern/api/src/test/java/com/example/interfaces/item/ItemSearchControllerTest.java`, `application/item/ItemSearchServiceTest.java`, `domain/item/ItemSearchConditionTest.java`, `infrastructure/item/ItemRepositoryImplTest.java` |
| 비즈니스 규칙(케이스 근거 BR-01~BR-30) | 있음 | `docs/item-bank/BUSINESS-RULES.md`, `docs/item-bank/CROSS-CHECK.md` |
| 이전 검증 기록(characterization 29 통과 · 1 skip · 0 실패) | 있음 | `docs/verify/migration-review-1.md` ~ `migration-review-3.md`, `docs/verify/scope-decision.md` |
| PR 자동 리뷰 워크플로 | 있음 | `.github/workflows/pr-review.yml` (origin/main 에도 있음, 시크릿 `CLAUDE_CODE_OAUTH_TOKEN` 등록됨) |
| 자동 리뷰 기준 체크리스트 | 있음 | `docs/approval-checklist.md` |
| Skill — 머지 전 검증 · 컨벤션 점검 · 영향도 | 있음 | `.claude/skills/verify/SKILL.md`, `.claude/skills/convention-check/SKILL.md`, `.claude/skills/impact/SKILL.md` |
| Skill — 모듈 문서화 · RCA · 배치 점검 | 있음(이번엔 안 씀) | `.claude/skills/document-module/`, `.claude/skills/rca/`, `.claude/skills/batch-check/` |
| Sub-agent — 리뷰어 · 테스터 | 있음 | `.claude/agents/reviewer.md`, `.claude/agents/tester.md` |
| Sub-agent — IaC 리뷰어 | 있음(이번엔 안 씀) | `.claude/agents/iac-reviewer.md` |
| Hook — 위험 명령 차단 · 시크릿 스캔 | 있음 | `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs` (등록: `.claude/settings.json` `PreToolUse` · Bash, 실행기 `scripts/hook-node.sh`) |
| 강사 기준 하네스 한 벌 | 있음(참고용) | `harness-baseline/` |
| pipeline-samples | 있음(데이터 · 인프라 트랙용, 이번엔 안 씀) | `pipeline-samples/` |
| 실전 프로젝트 설계 문서 | 없음 → 새로 만들 것(이 파일) | `docs/project/DESIGN.md` |
| 이번 회차 결과를 담을 PR(`day4` → `main`) | 없음 → 새로 만들 것 (4번 질문 2 결과에 따라) | — |

## 3. 범위

### 한다

1. HEAD 로 띄운 `modern/api`(8080)와 레거시 PHP(8081)를 대상으로 `characterization` 의 `item-bank` 16개 케이스를 각각 돌려 실패 0건을 확인한다.
2. 실패가 나오면 `modern/api` 의 item 4계층 코드만 고쳐 다시 통과시킨다(실패가 없으면 코드 변경 없음).
3. 결과를 PR 로 올려 `Claude Auto Review` 워크플로가 남긴 코멘트를 1회 확인한다.

### 하지 않는다

- 새 엔드포인트 이관 — Day 1-3 케이스는 `search.php` 에만 있고, 다른 엔드포인트는 입력 케이스가 없어 40분 안에 케이스 설계부터 해야 한다.
- `characterization/` 테스트 · 스냅샷 수정 — CLAUDE.md §5, 비교 기준이 바뀌면 "동작 보존"을 증명할 수 없다.
- `legacy/` 수정 — CLAUDE.md 상단 규칙(분석 · 이관 대상, 허락 없이 수정 금지).
- 레거시 의심 동작 고치기 — CLAUDE.md §5, 이관 중에는 고치지 않고 목록으로만 보고한다.
- `modern/web` · assignment · grade 모듈 변경 — 백엔드 트랙의 이관 1개 범위 밖이다.
- 의존성 · DB 스키마 · 시드 · `application.yml` 변경 — CLAUDE.md §3, 먼저 물어야 하는 변경이고 이번 목표에 필요 없다.

## 4. 미해결 질문

1. **이관 대상 엔드포인트가 이미 이관돼 있다.** `GET /api/items/search` 는 `6dd92da`(PR #1 · #2)에서 이관을 마쳤고, 이전 기록상 characterization 16개가 8080 에서 통과했다(`docs/verify/migration-review-3.md:26-29`). 이 설계는 "같은 엔드포인트를 HEAD 기준으로 재검증"으로 잡았다. 오늘 새 엔드포인트를 이관해야 하는 것이라면 범위의 "Day 1-3 케이스 그대로 사용"과 충돌하므로 어느 쪽을 따를지 정해 주세요.
2. **"3회차에 받은 PR 주소"가 어느 PR 인지 확인하지 못했다.** 후보:
   - #5 `day3` — https://github.com/HongChangMo/mirae-n-handson/pull/5 (MERGED, `claude` 코멘트 1 · 리뷰 2 이미 있음). 머지된 PR 이라 새 push 로 워크플로가 다시 돌지 않는다.
   - #4 `practice/bad-pr` — https://github.com/HongChangMo/mirae-n-handson/pull/4 (OPEN, 실습용 불량 PR).
   완성 기준 ②가 "이미 받은 코멘트로 충족"인지, "이번 결과로 새 PR(`day4`)을 열어 새 코멘트를 받아야" 하는지 정해 주세요. 이 설계의 5단계 3은 후자를 가정했다.
3. 지금 레거시 PHP 컨테이너(8081)와 8080 서버가 떠 있는지 확인하지 않았다(`docker compose ps` 미실행). 이전 기록의 8080 통과도 "HEAD 빌드인지 미확인"이었다(`docs/verify/migration-review-2.md:49`).
4. GitHub MCP 서버가 토큰 만료로 연결되지 않았다. 이 문서의 PR 정보는 `gh` CLI 로 읽었다. 자동 리뷰 워크플로 자체는 저장소 시크릿을 쓰므로 영향이 없는지 실제 실행으로 확인해야 한다.

## 5. 단계

### 단계 1 — 기준선 재확인(레거시 · 새 API 양쪽)

- 할 일: 레거시 PHP 를 띄우고 스냅샷과 비교 → HEAD 에서 `modern/api` 를 띄우고 같은 테스트를 대상 주소만 바꿔 실행 → 단위 테스트 실행.
- 만들거나 고칠 파일: 없음.
- 확인 방법:
  ```bash
  docker compose --profile php up -d && docker compose ps                    # php 서비스 Up
  cd characterization && npm test -- tests/item-bank.test.js                 # 16 passed, 0 failed
  cd modern/api && ./gradlew bootRun                                          # 별도 터미널, HEAD 에서
  cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test -- tests/item-bank.test.js   # 16 passed, 0 failed
  cd modern/api && ./gradlew test                                             # BUILD SUCCESSFUL, 실패 0
  git status --short characterization/                                        # 출력 없음
  ```

### 단계 2 — 차이가 있을 때만 이관 코드 수정

- 할 일: 단계 1 의 8080 비교가 실패하면 실패 케이스 이름과 스냅샷 차이(`-`/`+` 줄)를 그대로 기록하고, 원인을 (a) 규칙 (b) 형식 (c) 데이터 · 환경으로 나눈 뒤 `modern/api` 쪽만 고친다. 레거시 근거는 `legacy/item-bank-php/search.php:줄번호` 로 적는다. 실패가 0건이면 이 단계는 건너뛰고 "변경 없음"으로 적는다.
- 만들거나 고칠 파일(실패 시에만): `modern/api/src/main/java/com/example/{interfaces,application,domain,infrastructure}/item/` 중 원인 파일, 그 동작을 검증하는 `modern/api/src/test/java/com/example/.../item/*Test.java`.
- 확인 방법: 단계 1 의 8080 비교 명령 → `16 passed, 0 failed`. `./gradlew test` → 실패 0. `git diff --stat characterization/ legacy/` → 출력 없음. `/convention-check` 결과 위반 0건.

### 단계 3 — PR 과 자동 리뷰

- 할 일: `day4` 를 push 하고 `main` 대상 PR 을 연다(본문 초안을 먼저 보여 주고 승인받은 뒤 `gh pr create --repo HongChangMo/mirae-n-handson`). 본문에 단계 1 · 2 의 통과 · 실패 수를 적는다. 워크플로 실행이 끝나면 코멘트를 확인한다. (4번 질문 2 의 답이 "기존 PR 로 충족"이면 PR 생성 없이 해당 PR 의 코멘트만 확인한다.)
- 만들거나 고칠 파일: 없음(PR 본문만).
- 확인 방법:
  ```bash
  gh run list --repo HongChangMo/mirae-n-handson --workflow pr-review.yml --limit 1     # 이 PR 브랜치 실행이 completed · success
  gh pr view <PR번호> --repo HongChangMo/mirae-n-handson --json comments,reviews \
    --jq '[.comments[],.reviews[]|select(.author.login=="claude")]|length'              # 1 이상
  ```
  눈으로 볼 결과: PR 화면의 Conversation 탭에 `claude` 계정의 한국어 코멘트가 `docs/approval-checklist.md` 항목을 인용해 1개 이상 달려 있다.

## 6. 완성 기준 (Day 4-1에서 점검)

- ☐ 레거시 대상 `cd characterization && npm test -- tests/item-bank.test.js` 결과가 16 통과 · 0 실패다.
- ☐ HEAD 로 띄운 8080 대상 `TARGET_BASE_URL=http://localhost:8080 npm test -- tests/item-bank.test.js` 결과가 16 통과 · 0 실패다.
- ☐ `cd modern/api && ./gradlew test` 결과가 실패 0건이다(통과 수를 PR 본문에 적었다).
- ☐ `git status --short characterization/ legacy/` 출력이 0줄이다(테스트 · 스냅샷 · 레거시 무수정).
- ☐ 4번 질문 2 에서 정한 PR 주소에 `claude` 계정의 자동 리뷰 코멘트가 1개 이상 있고, `gh run list --workflow pr-review.yml` 의 해당 실행이 success 다.
