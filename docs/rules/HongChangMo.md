# 실 공통 규칙 후보 — HongChangMo

- 출처: `.claude/settings.json`(permissions · hooks) · `hooks/block-dangerous.mjs` · `hooks/secret-scan.mjs` · `scripts/hook-node.sh`
- 제외: `.claude/settings.local.json`(개인 설정, `docker compose ps *` allow · MCP 서버 활성화) — 실 공통 후보가 아니다.
- 주 스택: spring
- 스택 코드: `spring`(Gradle · Spring Boot) · `node`(npm · 웹 프론트) · `python`(pip · pytest) · `docker`(docker compose) · `itembank`(이 실습 저장소 전용 MCP)
- 분류 기준: 파괴 명령 · 운영 DB · 외부 전송 · 비밀 파일 보호 · 시크릿 탐지는 공통, Spring 설정 경로와 특정 빌드 · 패키지 도구의 명령은 해당 스택 코드로 둔다.
- 현재 규칙: permissions 53개(deny 20 · ask 11 · allow 22)와 Hook 동작 설명 6개. 아래 추가 제안은 설정 · Hook 에 아직 반영하지 않은 후보이다.
- 검토 기준: 2026-10-13, 로컬 Claude Code 2.1.296 및 공식 문서. 테스트 환경과 도구 구성은 저장소마다 확인해야 한다.

## 규칙 표

| 규칙 | 종류(deny / ask / allow / Hook) | 적용 범위(공통 또는 스택 코드) | 막는 사고 또는 허용 이유 |
|---|---|---|---|
| `Bash(rm -rf *)` | deny | 공통 | 경로 실수 · 변수 미설정(`rm -rf $DIR/`)으로 저장소나 홈 디렉터리를 통째로 지우는 사고 |
| `Bash(sudo *)` | deny | 공통 | 에이전트가 시스템 패키지 · 권한 · 시스템 파일을 바꾸는 사고 |
| `Bash(git push --force *)` | deny | 공통 | 원격 브랜치 이력을 덮어써 다른 사람 커밋을 잃는 사고 |
| `Bash(git push -f *)` | deny | 공통 | 위와 같음(축약 플래그) |
| `Bash(git reset --hard *)` | deny | 공통 | 커밋하지 않은 작업 변경분을 되돌릴 수 없게 버리는 사고 |
| `Bash(*prod-db*)` | deny | 공통 | 명령문에 `prod-db` 가 포함된 접근을 차단해 운영 DB 오접속을 줄임. 실제 연결 대상 판별은 아님 |
| `Bash(*prod.db*)` | deny | 공통 | 위와 같음(점 표기 호스트명) |
| `Bash(curl *)` | deny | 공통 | 코드 · 시크릿을 외부로 전송하거나, 검증되지 않은 스크립트를 내려받는 사고 |
| `Bash(wget *)` | deny | 공통 | 위와 같음 |
| `Read(./.env)` | deny | 공통 | 비밀 값이 모델 컨텍스트 · 로그 · 대화 기록으로 새는 사고 |
| `Read(./.env.*)` | deny | 공통 | 위와 같음(`.env.local` · `.env.prod` 등) |
| `Read(./secrets/**)` | deny | 공통 | 위와 같음(시크릿 폴더) |
| `Read(**/*.pem)` | deny | 공통 | 개인키 · 인증서가 컨텍스트로 새는 사고 |
| `Read(~/.ssh/**)` | deny | 공통 | SSH 개인키 유출 |
| `Read(~/.aws/**)` | deny | 공통 | AWS 자격 증명 유출 |
| `Edit(./.env)` | deny | 공통 | 비밀 값 · 접속 정보를 에이전트가 덮어써 환경이 깨지는 사고 |
| `Edit(./.env.*)` | deny | 공통 | 위와 같음 |
| `Edit(./secrets/**)` | deny | 공통 | 위와 같음 |
| `mcp__github__delete_file` | deny | 공통 | 원격 저장소 파일을 리뷰 없이 직접 삭제하는 사고 |
| `mcp__github__merge_pull_request` | deny | 공통 | 사람 승인 없이 PR 을 머지하는 사고 |
| `Bash(git push *)` | ask | 공통 | 외부(원격)로 내보내는 행위라 사람이 대상 브랜치를 확인한다 |
| `mcp__github__create_pull_request` | ask | 공통 | PR 본문을 원격 저장소로 보내므로 내용 · 대상 · 공개 범위를 생성 전 확인 |
| `mcp__github__push_files` | ask | 공통 | 원격에 직접 쓰는 외부 전송이라 확인 |
| `mcp__dbhub` | ask | 공통 | DB 서버의 도구 호출 전 대상 · 쿼리를 확인. ask 자체가 운영 DB 접근을 금지하지는 않음 |
| `Bash(npm install *)` | ask | node | 의존성 · `package-lock.json` 변경(CLAUDE.md: 의존성 변경은 먼저 묻는다), 공급망 위험 |
| `Bash(npm i *)` | ask | node | 위와 같음(축약 명령) |
| `Bash(npm ci *)` | ask | node | 대량 다운로드 · `node_modules` 재생성이라 실행 전 확인 |
| `Bash(pip install *)` | ask | python | 환경에 패키지를 추가하는 의존성 변경 · 공급망 위험 |
| `Bash(docker compose down *)` | ask | docker | 컨테이너 제거와 `-v` 볼륨 삭제로 DB 데이터를 잃는 사고. 영구 볼륨이 없으면 `-v` 없이도 손실 가능 |
| `Bash(docker compose --profile *)` | ask | docker | 프로필로 추가 서비스(DB 등)를 띄우거나 내리는 동작을 확인 |
| `Bash(./gradlew bootRun *)` | ask | spring | 장시간 점유 프로세스 · 포트 8080 점유 · 실제 DB 접속이 일어나므로 확인 |
| `Bash(npm test *)` | allow | node | 검증된 로컬 테스트의 반복 실행. 실제 DB · 외부 서비스 접근 여부와 스크립트 부작용을 먼저 확인해야 함 |
| `Bash(npm run test *)` | allow | node | 위와 같음 |
| `Bash(npm run lint *)` | allow | node | 검증된 검사 스크립트의 반복 실행. 자동 수정 옵션 · 추가 처리의 영향 확인 필요 |
| `Bash(npm run typecheck *)` | allow | node | 검증된 타입 검사 스크립트의 반복 실행 |
| `Bash(npm run build *)` | allow | node | 검증된 로컬 빌드의 반복 실행. 배포 · 외부 전송을 포함한 스크립트는 별도 확인 |
| `Bash(./gradlew test *)` | allow | spring | 이 저장소는 H2 test 프로필 사용을 전제로 함. 다른 저장소의 DB 격리와 뒤에 붙는 추가 태스크는 별도 확인 |
| `Bash(./gradlew check *)` | allow | spring | 검증된 테스트 · 정적 검사의 반복 실행. 연결된 태스크와 추가 태스크의 영향 확인 필요 |
| `Bash(./gradlew build *)` | allow | spring | 검증된 로컬 빌드의 반복 실행(테스트 포함). 외부 전송 · DB 변경 태스크가 연결될 수 있음 |
| `Bash(pytest *)` | allow | python | 외부 영향이 없도록 격리한 테스트의 반복 실행 |
| `Bash(git status *)` | allow | 공통 | 읽기 전용 |
| `Bash(git diff *)` | allow | 공통 | 읽기 전용 |
| `Bash(git log *)` | allow | 공통 | 읽기 전용 |
| `Bash(git add *)` | allow | 공통 | 로컬 스테이징만 바꾸며 되돌리기 쉽다 |
| `Bash(git commit *)` | allow | 공통 | 로컬 커밋 생성. `secret-scan` 은 일부 명령 형식과 staged 추가 줄만 검사하므로 검사 한계 확인 필요 |
| `WebFetch(domain:docs.spring.io)` | allow | spring | 공식 문서 조회. URL · 쿼리에 코드나 시크릿을 넣지 않는 것을 전제로 허용 |
| `WebFetch(domain:docs.gradle.org)` | allow | spring | 위와 같음 |
| `WebFetch(domain:developer.mozilla.org)` | allow | node | 위와 같음 |
| `mcp__github__get_file_contents` | allow | 공통 | 원격 저장소 읽기 전용 |
| `mcp__github__list_pull_requests` | allow | 공통 | 읽기 전용 |
| `mcp__github__pull_request_read` | allow | 공통 | 읽기 전용 |
| `mcp__github__list_issues` | allow | 공통 | 읽기 전용 |
| `mcp__item-bank__search_items` | allow | itembank | 이 저장소 전용 읽기 전용 검색 도구 |
| `block-dangerous`: `rm` 재귀+강제 변형(`-fr` · `-r -f` · `-Rf` · `--recursive --force`) | Hook | 공통 | deny 패턴 `rm -rf *` 가 놓치는 변형 표기로 대량 삭제하는 사고 |
| `block-dangerous`: `DROP TABLE` / `DROP DATABASE` | Hook | 공통 | Bash 명령문에 해당 SQL 문자열이 있으면 차단. 파일 내부 SQL · MCP 질의는 검사하지 않음 |
| `block-dangerous`: `prod[-.]db` | Hook | 공통 | Bash 명령문 중간 · 파이프 안의 지정 문자열도 차단. 실제 DB 환경 판별은 아님 |
| `block-dangerous`: `chmod 777`(`0777` · `-R 777`) | Hook | 공통 | 숫자 권한 표기로 모든 사용자에게 쓰기 권한을 여는 사고. `chmod a+rwx` 는 탐지하지 않음 |
| `secret-scan`: `git commit` 으로 시작하는 명령의 staged 추가 줄에서 AKIA 형식 AWS Access Key ID · 따옴표 있는 password 대입 · PEM 개인키 탐지 | Hook | 공통 | 일부 시크릿의 커밋을 차단. 값은 출력하지 않고 패턴명 · 건수만 보고하며 모든 커밋 형식 · 시크릿 형식을 검사하지는 않음 |
| `scripts/hook-node.sh`: node 를 찾지 못하면 명령 차단(fail-closed) | Hook | 공통 | PATH · nvm · 후보 경로에서 Node 탐색 실패 시 exit 2. Hook 자체의 모든 오류를 차단한다는 의미는 아님 |

## 추가 제안 (spring 에 필요한데 지금 없는 규칙)

Spring 개발에 필요한 공통 보강안도 포함한다. 아래는 후보이며 현재 적용된 규칙이 아니다. 파일 경로 권한은 `Read` · `Edit` 로 작성한다. `Edit(경로)` 는 새 파일 작성도 처리하며 `Write(경로)` 규칙은 파일 경로 권한 검사에 사용되지 않는다. [공식 파일 권한 문서](https://code.claude.com/docs/en/permissions#read-and-edit)

| 규칙 | 종류(deny / ask / allow / Hook) | 적용 범위(공통 또는 스택 코드) | 막는 사고 또는 허용 이유 |
|---|---|---|---|
| `Read(**/.env)` · `Read(**/.env.*)` · `Edit(**/.env)` · `Edit(**/.env.*)` | deny | 공통 | 중첩 모듈의 비밀 파일도 보호. 비밀 값 없는 example 파일의 취급은 별도 결정 |
| `Read(**/secrets/**)` · `Edit(**/secrets/**)` | deny | 공통 | 중첩 모듈의 시크릿 폴더를 읽거나 덮어쓰는 사고 |
| `Read(**/application-prod*.yml)` · `Read(**/application-prod*.yaml)` · `Read(**/application-prod*.properties)` | deny | spring | 비밀 값을 담는 운영 프로필 설정 유출. 환경변수 참조만 있는 설정까지 일괄 차단할지는 별도 결정 |
| `Read(**/*.jks)` · `Read(**/*.p12)` · `Read(**/*.keystore)` | deny | 공통 | TLS · 서명 개인키가 들어 있는 키스토어 유출. 공개 인증서만 든 파일은 보호 대상 구분 필요 |
| 자격 증명이 있는 `gradle.properties` 의 실제 경로를 `Read` · `Edit` deny 로 지정 | deny | 공통 | 저장소와 `~/.gradle/gradle.properties` 의 저장소 비밀번호 · 토큰 보호. 비밀 값 없는 일반 설정까지 일괄 차단하지 않도록 대상 확인 |
| `Edit(**/build.gradle*)` · `Edit(**/settings.gradle*)` · `Edit(**/gradle/libs.versions.toml)` | ask | spring | 의존성 · 플러그인 · 저장소 정의 변경 전 확인. 파일 전체 변경을 묻는 규칙이므로 일반 빌드 설정 수정도 승인 대상 |
| `Edit(**/gradle/wrapper/gradle-wrapper.properties)` · `Edit(**/gradle/wrapper/gradle-wrapper.jar)` · `Edit(**/gradlew)` · `Edit(**/gradlew.bat)` | ask | spring | 실행할 빌드 도구와 다운로드 URL · 체크섬 · 실행 스크립트 변경에 따른 공급망 위험 |
| `Edit(**/src/main/resources/application*.yml)` · `Edit(**/src/main/resources/application*.yaml)` · `Edit(**/src/main/resources/application*.properties)` | ask | spring | 일반 애플리케이션 설정 변경 전 확인. DB 접속 정보 · 풀 설정의 변경 금지를 대신하는 규칙은 아님 |
| `Edit(**/db/migration/**)` | ask | spring | 기존 마이그레이션 수정과 새 파일 작성 전 승인. 실제 프로젝트의 마이그레이션 경로도 확인 |
| `Bash(./gradlew flywayClean *)` | deny | spring | Flyway 플러그인 사용 시 스키마 삭제 차단. 태스크 이름만 들어 있어 기존 DROP 문자열 Hook 은 놓침 |
| `Bash(./gradlew flywayMigrate *)` | ask | spring | Flyway 플러그인 사용 시 대상 DB · 변경 내용을 확인하고 마이그레이션 실행 |
| `Bash(./gradlew publish *)` · `Bash(./gradlew jib *)` | ask | spring | 해당 플러그인 사용 시 아티팩트 · 이미지를 외부 저장소로 내보내기 전 확인 |
| `Bash(./gradlew clean)` · `Bash(./gradlew dependencies)` · `Bash(./gradlew --version)` | allow | spring | 검증된 정리 · 조회 명령만 정확히 허용. 추가 인자 · 태스크는 별도 확인하며 clean 도 프로젝트 정의를 먼저 검토 |
| `Bash(java -jar *)` | ask | spring | Spring 애플리케이션 실행 시 프로세스 · 포트 · 접속 DB 확인. 절대 경로 · 래퍼를 통한 실행도 별도 검토 |
| Gradle PreToolUse Hook: 전체 태스크 목록 · 옵션 · 멀티모듈 경로 검사 | Hook | spring | `test bootRun` · `test flywayClean` · `:api:publish` 등의 추가 · 변형 태스크가 기존 allow 에 포함되는 사고. 기존의 넓은 allow 도 함께 조정 |
| 시크릿 탐지 보강: YAML · JSON · properties · JDBC URL, 환경변수 참조 구분 | Hook | 공통 | `password: xxx` · `spring.datasource.password=xxx` 등의 누락과 `${DB_PASSWORD}` 오탐 방지. 승인된 더미값 예외는 파일 · 용도를 한정 |
| 커밋 검사 보강: 복합 명령 · `git -C` · `-a` · 파일 지정 커밋의 대상 변경 검사 | Hook | 공통 | staged diff 와 실제 커밋될 변경이 다른 경우를 포함해 시크릿 검사 누락 방지. Git 훅 · CI 검사도 함께 검토 |
| Hook 내부 검사 실패 시 exit 2, command Hook 에 `onFailure: "block"` 설정 | Hook | 공통 | JSON · diff 검사 실패, Hook 실행 실패 · 시간 초과로 정책이 통과하는 사고. 내부 exit 0 은 onFailure 로 차단되지 않음 |
| `block-dangerous` 로그 보강: 차단 사유만 출력하거나 비밀 인자 마스킹 | Hook | 공통 | 현재 출력하는 명령 전체에 비밀번호 · 토큰이 있으면 Claude 컨텍스트와 로그로 노출되는 사고 |
| 강제 push 검사 Hook: 옵션 위치 · `git -C` · `+refspec` 등 확인 | Hook | 공통 | 단순 명령 접두어 deny 가 놓치는 이력 덮어쓰기. `--force-with-lease` 허용 여부도 팀 정책으로 결정 |
| DB 접근 PreToolUse Hook: Bash · DB MCP 에서 실제 연결 대상 확인 | Hook | 공통 | 별칭 · 다른 호스트명 · MCP 를 통한 운영 DB 접근. 대상 판별 불가 시 차단하고 DB 계정 권한도 제한 |
| Edit/Write PreToolUse Hook: 금지된 DB 접속 정보 · Hikari 풀 설정 변경 차단 | Hook | spring | 일반 설정 ask 와 별개로 변경 전후 보호 대상 필드를 비교해 CLAUDE.md §3 의 변경 금지를 적용 |
| Edit/Write PreToolUse Hook: 실제 DB 대상 설정의 위험한 ddl-auto 변경 차단 | Hook | spring | `yml` · `yaml` · `properties` 의 create · create-drop · update 로 실제 DB 가 변경되는 사고. 격리된 H2 테스트 설정은 구분 |

Flyway · Liquibase · Jib 는 현재 `modern/api/build.gradle` 에 설치되어 있지 않다. 해당 도구를 사용하는 프로젝트에만 태스크 규칙을 적용하고, Liquibase 의 삭제 · 마이그레이션 태스크는 설치된 플러그인의 실제 이름을 확인해 추가한다. Maven 을 쓰는 Spring 팀은 `mvn` · `mvnw` · `pom.xml` 에 같은 승인 · 전송 정책을 별도로 정한다.

## 주의 (다른 스택에 그대로 걸면 일을 막을 수 있는 규칙)

- **권한 우선순위**: `deny > ask > allow` 이며 좁은 allow 를 추가해도 넓은 deny · ask 를 덮어쓰지 못한다. 예외를 허용하려면 기존 deny · ask 범위를 조정해야 한다. [공식 권한 문서](https://code.claude.com/docs/en/permissions#manage-permissions)
- **`scripts/hook-node.sh` 와 오류 처리**: Node 를 끝내 못 찾으면 모든 Bash 호출이 exit 2 로 차단된다. Bash · Node 설치는 공통 적용의 전제 조건이다. 그러나 JSON 입력 오류 · git diff 실패는 현재 exit 0 으로 통과하며, Bash 실행 실패 · Hook 모듈 누락 · 시간 초과도 기본 설정에서는 차단을 보장하지 않는다. command Hook 의 `onFailure: "block"` 은 2.1.295 이상에서 지원한다. [공식 Hook 실패 처리](https://code.claude.com/docs/en/hooks#block-on-hook-failure)
- **`Bash(curl *)` · `Bash(wget *)` deny**: 로컬 API · 헬스 체크도 막힌다. localhost allow 추가만으로는 예외가 되지 않는다. 기존 deny 를 조정하고 Hook 또는 샌드박스로 대상 호스트 · 포트 · 리다이렉트 · 전송 방식을 검사하는 정책을 검토한다. localhost POST 도 데이터를 바꿀 수 있다.
- **`Bash(rm -rf *)` deny + rm 재귀 강제 Hook**: `rm -rf node_modules` · `rm -rf dist` · `rm -rf build` 같은 정리도 막힌다. `npm ci` · `./gradlew clean` 등 검증된 대안과 필요한 승인 절차를 안내해야 한다.
- **DROP TABLE · DROP DATABASE Hook**: `grep -rn "DROP TABLE" legacy/` 같은 검색도 막힌다. 로컬 개발 DB 초기화도 차단되므로 실제 DB 삭제와 분석 · 격리된 테스트 작업을 구분할 정책이 필요하다.
- **prod-db deny · Hook**: `cat prod-db-runbook.md` 나 `terraform plan -target=module.prod-db` 같은 분석 명령도 막힌다. 반대로 다른 호스트명 · 환경변수 · 설정 파일을 통한 연결은 놓친다. DB MCP 는 현재 Bash Hook 대상이 아니며 `mcp__dbhub` ask 만으로 운영 접근을 금지할 수 없다.
- **`Bash(sudo *)` deny**: 인프라 · 리눅스 서버 담당자의 패키지 설치 · 서비스 재시작이 막힌다. 담당자가 수행할 작업과 절차를 별도로 안내해야 한다.
- **테스트 · 빌드 allow**: H2 격리는 이 저장소의 전제이며 다른 Spring 프로젝트에 자동으로 성립하지 않는다. `./gradlew test bootRun` 은 `test *` allow 에 맞고 `bootRun *` ask 에는 맞지 않는다. 추가 태스크 · 옵션 · 플러그인 의존 태스크를 검사해야 하며 npm · pytest 도 스크립트와 환경을 확인해야 한다.
- **`Bash(npm ci *)` ask**: 표준 설치 명령마다 승인을 받는다. lock 파일을 바꾸지 않지만 다운로드 · 설치 스크립트 실행은 가능하므로 검증된 프로젝트에서만 allow 전환을 검토하고 기존 ask 도 조정한다.
- **`Bash(docker compose --profile *)` ask**: `ps` · `logs` 같은 읽기 명령도 매번 확인을 받는다. 좁은 allow 추가만으로는 해결되지 않으므로 기존 ask 범위를 조정하고 데이터 삭제 명령 승인은 유지한다.
- **secret-scan 의 탐지 형식**: 더미 비밀번호뿐 아니라 `password="${DB_PASSWORD}"` 도 차단한다. 반대로 YAML · JSON · 따옴표 없는 properties 와 AKIA 외의 키 형식 등은 탐지하지 못한다. 환경변수 참조와 더미 예외는 실제 비밀값 탐지를 약화시키지 않도록 한정한다.
- **secret-scan 의 검사 대상**: `cd x && git commit` · `git -C x commit` 은 검사 자체를 건너뛴다. `git commit -a` · 파일 지정 커밋은 검사 시점의 staged diff 와 커밋 대상이 다를 수 있다. 현재 Hook 만으로 모든 커밋의 시크릿 검사를 보장하지 않는다.
- **강제 push deny 의 빈틈(공통)**: `git push origin main --force` · `git push origin +main` 은 기존 force deny 대신 push ask 만 적용된다. `git -C x push` 같은 다른 호출 형식도 기존 접두어 규칙이 놓친다. 문자열 정규식 하나로 충분하다고 단정하지 않고 실제 명령 · 옵션 · refspec 을 검토한다.
- **Read · Edit 와 Bash 의 경계**: 현재 공식 동작에서 일부 Bash 파일 명령 · 리다이렉트도 파일 권한을 검사하지만, Python · Node 등 임의 프로그램의 간접 파일 접근까지 보호하지는 않는다. 강제 보호에는 파일 · 네트워크 샌드박스가 필요하며 PowerShell 과 다른 실행 도구도 별도 적용 대상이다. [공식 파일 권한 범위](https://code.claude.com/docs/en/permissions#read-and-edit)
- **운영 설정 · 인증서 deny**: `application-prod*` 가 비밀값 없는 환경변수 참조 설정이면 분석을 막을 수 있다. `.env.example` · 공개 PEM 인증서 · 테스트 키스토어도 일괄 차단될 수 있으므로 실제 비밀 파일과의 구분을 먼저 정한다.
- **Spring 설정 ask 와 변경 금지**: ask 는 승인 후 수정할 수 있으므로 DB 접속 정보 · 풀 설정의 변경 금지를 강제하지 않는다. 보호 필드 변경은 별도 Hook 으로 차단하고, ddl-auto 차단도 격리된 테스트의 create-drop 사용과 구분한다.
- **`Bash(git add *)` · `Bash(git commit *)` allow**: 커밋을 확인 없이 허용한다. 커밋 정책이 "요청할 때만"인 팀이면 ask 로 두는 편이 맞다.

## 실 전체 적용 조건

- 이 저장소의 `.claude/settings.json` 은 프로젝트 규칙이다. 조직에서 강제할 공통 정책은 managed settings 로 배포하고, 저장소별 스택 규칙과 승인된 예외의 배포 위치를 정한다. [공식 설정 문서](https://code.claude.com/docs/en/settings)
- 현재 Hook 은 `$CLAUDE_PROJECT_DIR` 아래의 `scripts/hook-node.sh` · `hooks/*.mjs` 를 참조한다. 모든 대상 저장소에 파일을 배포하거나 조직이 관리하는 공통 설치 경로로 변경해야 한다. Bash · Node · Claude Code 최소 버전과 Hook 파일 변경 권한도 정한다.
- 적용 후 `/status` · `/permissions` 로 로드된 설정과 규칙을 확인하고 `claude doctor` 로 무시된 설정을 확인한다. 정상 작업과 차단 대상의 모의 입력 검사를 각 지원 환경에서 수행한다.
- 이 문서는 후보 정리이며 실제 설정 · Hook 은 변경하지 않았다. 현재 검증은 원문 대조와 Hook 모의 입력 검사이며, 실제 Git 커밋 · DB 명령 · Claude 세션 전체 권한 흐름은 실행하지 않았다.
