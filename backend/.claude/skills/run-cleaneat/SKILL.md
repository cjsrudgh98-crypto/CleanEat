---
name: run-cleaneat
description: Build, start, stop, test, and drive the CleanEat app (Spring Boot backend that also serves the React frontend). Use when asked to run/start/launch the CleanEat server, build the jar, run its tests, seed test data, take a screenshot of a CleanEat screen (orders, admin, mypage, home), check a UI change in the browser, or smoke-test the API.
---

CleanEat은 `backend/pom.xml` 하나가 `../frontend`(React)까지 빌드해서 jar 하나로 같은 주소에서 서빙한다.
에이전트는 **`.claude/skills/run-cleaneat/server.sh`로 띄우고**, 화면은 **`driver.mjs`**(설치된 Edge를
playwright-core로 조종), API는 **`smoke.py`**(urllib)로 확인한다.

모든 경로는 `backend/` 기준. 검증 환경: Windows 11 + Git Bash, JDK 21, Edge. (Linux 분기 - `nohup`/`lsof`/
`/usr/bin/chromium` - 는 스크립트에 있지만 실행해 보지 않았다.)

## Prerequisites

- JDK 21 - `server.sh`가 알아서 찾는다 (`JAVA_HOME`이 틀려 있어도 `~/.jdks/jdk-21*` 등을 확인해서 씀)
- Maven - PATH에 없으면 IntelliJ에 들어 있는 `C:/Program Files/JetBrains/*/plugins/maven/lib/maven3/bin/mvn`을 씀
- Node는 따로 필요 없음 (`../frontend/node/`에 프론트 빌드가 받아 둔 Node가 있고, 드라이버도 그걸로 돈다 - 시스템 Node도 됨)
- Python 3, Edge(또는 Chrome)

## Setup (한 번)

```bash
npm install --prefix .claude/skills/run-cleaneat --no-audit --no-fund   # playwright-core만 (브라우저 다운로드 없음)
```

## Build

```bash
bash .claude/skills/run-cleaneat/server.sh build              # 프론트 포함 jar (npm install + vite build 포함, 1~2분)
bash .claude/skills/run-cleaneat/server.sh build --api-only   # 프론트 빌드를 건너뜀 (백엔드만 고쳤을 때 빠르게)
```

`-o`(오프라인)로 빌드한다 - 의존성이 `~/.m2`에 이미 있어야 함.
`--api-only`는 프론트를 **새로 빌드/복사하지 않을 뿐** 빼지는 않는다 - 전에 빌드한 프론트가 `target/classes/static`에 있으면
그대로 jar에 들어간다 (`mvn clean` 뒤라면 프론트 없는 jar, "/"는 404). 프론트를 고쳤으면 반드시 `--api-only` 없이.

## Run (agent path)

```bash
bash .claude/skills/run-cleaneat/server.sh fresh    # 종료 -> 백그라운드로 시작 -> 화면 확인용 데이터 준비 (약 20초)
node .claude/skills/run-cleaneat/driver.mjs shot orders --as uiuser --scroll "더 보기" --out orders-bottom.png
node .claude/skills/run-cleaneat/driver.mjs shot admin --as uiadmin
node .claude/skills/run-cleaneat/driver.mjs check   # 화면 시나리오 전체 (66개) - fresh 직후에만
bash .claude/skills/run-cleaneat/server.sh stop
```

스크린샷 -> `.claude/skills/run-cleaneat/out/shots/`, 서버 로그 -> `.claude/skills/run-cleaneat/out/server.log`.
**스크린샷은 반드시 열어서 볼 것** (Read로 png를 열면 보인다).

| 명령 | 하는 일 |
|---|---|
| `server.sh fresh` | stop + start + `seed.py`. H2 메모리 DB라 재시작하면 데이터가 비고 레이트리밋 카운터도 초기화된다 |
| `server.sh start` / `stop` / `status` | 8080 포트 기준. `status`는 server.log의 ERROR 줄 수도 보여줌 |
| `NO_RATE_LIMIT=1 server.sh start` | 로그인 등 횟수 제한 끄고 시작 (여러 번 로그인하며 탐색할 때) |
| (자동) 소셜 로그인 | `application-oauth-secrets.yml`이 있으면 `oauth` 프로필로 뜬다 (`UP:` 줄에 `프로필 oauth`). 끄려면 `SPRING_PROFILES_ACTIVE=default` |
| `APP_FRONTEND_URL=http://localhost:5173 server.sh start` | Vite 개발 서버로 볼 때 - 소셜 로그인 후 5173으로 돌아오게 |
| `driver.mjs shot <경로> [--as <아이디>] [--scroll <글자>] [--out <파일>] [--full]` | 로그인한 상태로 그 화면을 캡처. 경로는 **앞 슬래시 없이** (`orders`, `admin`, `mypage`, `orders/22`) |
| `driver.mjs check` | 스캔 기록/주문/관리자 "더 보기", 주문 취소 폼(가상계좌 환불 계좌, 관리자 사유), 취소 실패 표시, 비밀번호 변경 후 로그인 유지, 반품 신청/철회/승인/거절, 부분 취소, 재입고 알림(신청 -> 관리자 재고 -> 메일 로그), 매출 통계 차트 |
| `python smoke.py` | API 25개 확인 (4xx 응답, 가입, 장바구니 상한, 추천, 페이지 응답, OCR 해상도 제한, 토큰 무효화, 인증번호 5회 제한, 레이트리밋). `fresh` 직후에 |

`seed.py`가 만드는 데이터 (`fresh`에 포함):

| | |
|---|---|
| `uiuser` / `Passw0rd!` | 일반 회원 (id 1). 스캔 기록 22건, **결제 완료 주문 22건** (#22는 입금 끝난 가상계좌) |
| `uiadmin` / `Passw0rd!` | 관리자 (id 2) - 서버를 `ADMIN_USERNAMES=uiadmin`으로 띄워야 가입 때 ADMIN이 됨 (`server.sh`가 기본으로 넣음) |
| `uipartial` / `Passw0rd!` | 부분 취소 확인용: 상품 두 줄짜리 결제 완료 주문 (결제키 없음 -> 토스 없이 끝까지 됨) |
| `uireturn` / `Passw0rd!` | 반품 확인용 (주문 #23~26): A 카드 배송 완료, B 가상계좌 배송 완료, C 반품 신청됨(결제키 없음 -> 승인이 토스 없이 끝까지 됨), D 10일 전 배송(기간 지남) |

결제 완료 주문은 토스 결제 없이 만들 수 없어서, API로 주문서를 만든 뒤 **H2 콘솔(`/h2-console`)로 상태를 PAID로 바꾼다**.
그래서 이 주문들의 결제키는 가짜 -> 취소하면 토스 테스트 서버가 `존재하지 않는 결제 정보 입니다.`로 거절하는 것이 정상.

`check`는 데이터를 바꾼다 (uiuser 비밀번호 -> `NewPassw0rd!`). 다시 돌리려면 `server.sh fresh`부터.
`smoke.py`는 마지막에 로그인 횟수 제한을 일부러 걸어서 **이후 5분간 이 PC에서 로그인이 막힌다** -> `check`/`shot`보다 뒤에,
또는 `fresh`로 재시작 후.

```bash
bash .claude/skills/run-cleaneat/server.sh fresh && PYTHONIOENCODING=utf-8 python .claude/skills/run-cleaneat/smoke.py
```

## Run (human path)

```bash
java -jar target/CleanEat.jar   # -> http://localhost:8080 을 브라우저로. Ctrl-C로 종료. (JDK 21의 java)
```

프론트를 고치면서 볼 때 (저장하면 바로 반영) - 백엔드를 먼저 띄우고:

```bash
APP_FRONTEND_URL=http://localhost:5173 bash .claude/skills/run-cleaneat/server.sh fresh
cd ../frontend && PATH="$PWD/node:$PATH" npm run dev -- --port 5173 --strictPort   # 에이전트는 백그라운드로 실행
cmd //c start "" "http://localhost:5173"                                             # 사용자 브라우저로 열기
```

Vite가 `/api`, `/oauth2`, `/login`, `/h2-console`을 8080으로 넘긴다.

## Test

```bash
bash .claude/skills/run-cleaneat/server.sh test   # 백엔드 mvn test + 프론트 tsc/eslint
```

기대 결과: `Tests run: 282, Failures: 0` + `Tests 52 passed` + `frontend: tsc + eslint + vitest OK` (약 1분 반).

프론트 테스트만 (Vitest + Testing Library, jsdom - `../frontend/src/**/*.test.ts(x)`):

```bash
cd ../frontend && PATH="$PWD/node:$PATH" npx vitest run            # 한 번 (npm test)
cd ../frontend && PATH="$PWD/node:$PATH" npx vitest run src/pages  # 일부만
```

화면 테스트는 `vi.mock('../api/client')`로 서버를, `vi.mock('../auth/AuthContext')`로 로그인을 대신한다 (예: `src/pages/OrdersPage.test.tsx`). 테스트용 주문 데이터는 `src/test/fixtures.ts`. 같은 확인을 리눅스에서 하는 CI는 `../.github/workflows/ci.yml` (`DEPLOYMENT.md` 5-5).

## Gotchas

- **서버가 떠 있으면 `build`가 `Unable to rename '...CleanEat.jar'`로 실패한다 (Windows)** - 실행 중인 java가 jar를 잠근다.
  -> `server.sh stop` 후 build.
- **Hibernate는 `ddl-auto: validate`** - 테이블은 Flyway(`src/main/resources/db/migration/{h2,mysql,postgresql}`)가 만든다.
  엔티티를 바꾸고 마이그레이션을 안 넣으면 `Schema-validation: missing ...`로 기동이 실패한다 (H2 포함 - `server.sh test`도 실패).
  Flyway 도입 전 DB를 옮기는 방법은 `DEPLOYMENT.md` 5-4.
- **Node는 22.12 이상** (Vite 8/rolldown 요구, `pom.xml`은 22.23.3). 낮으면 리눅스(CI/Docker)에서만 `Cannot find native binding`으로
  프론트 빌드가 깨진다 - npm이 버전이 안 맞는 네이티브 바이너리를 조용히 건너뛴다.
- **`pom.xml`의 Node 버전을 바꾼 뒤 빌드가 `npm error Class extends value undefined is not a constructor or null`** -
  플러그인이 `../frontend/node`에 새 Node를 덮어쓰면서 옛 npm 파일이 섞였다 -> `rm -rf ../frontend/node` 후 다시 build
  (생성물 폴더라 지워도 됨). Vite 개발 서버가 그 node.exe로 떠 있으면 파일이 잠기므로 먼저 끌 것.
- **Vitest(jsdom)에는 `<dialog>.showModal()`이 없다** - `src/test/setup.ts`가 open 속성만 흉내 낸다. Esc로 닫기는
  jsdom이 cancel 이벤트를 만들지 않으므로 `fireEvent(dialog, new Event('cancel', { cancelable: true }))`로 보낸다.
- **Testing Library/Playwright의 이름 찾기는 기본이 부분 일치** - `'반품 신청'`이 `'반품 신청 철회'` 버튼에도 걸린다.
  비슷한 이름이 있으면 `exact: true`.
- **`... | grep`으로 테스트 결과를 거르면 실패 종료 코드가 가려진다** - `server.sh test`는 `set -o pipefail`로 막아 둠.
- **헬스체크** `curl localhost:8080/actuator/health` -> `{"status":"UP",...}`. 기동 직후 1~2초는 `OUT_OF_SERVICE`(시작 작업 중).
- **소셜 로그인 버튼이 404** - `oauth` 프로필 없이 뜬 서버. `server.sh start`는 키 파일이 있으면 자동으로 켠다
  (`UP:` 줄의 프로필 확인). 확인: `curl -s -o /dev/null -w "%{http_code} %{redirect_url}" localhost:8080/oauth2/authorization/google`
  -> `302 https://accounts.google.com/...redirect_uri=http://localhost:8080/login/oauth2/code/google`.
- **Vite(5173)로 소셜 로그인하면 `redirect_uri_mismatch`** - 백엔드는 요청 Host로 콜백 주소를 만드는데, 콘솔에는 8080만
  등록돼 있다 -> `vite.config.ts`의 `/oauth2`, `/login` 프록시에 `changeOrigin: true` (적용됨). 로그인 후 5173으로 돌아오려면
  `APP_FRONTEND_URL`. 실제 계정 로그인은 사람이 해야 한다 - 에이전트는 provider 로그인 화면이 뜨는 데까지 확인 가능.
- **`nohup java ... &`로 띄우면 호출한 명령이 안 끝난다 (Windows)** - java가 호출한 쪽의 출력 파이프 핸들을 상속한다
  (`</dev/null >log 2>&1`로 리다이렉트해도 마찬가지). `server.sh fresh | tail`이 서버가 죽을 때까지 멈췄다.
  -> `server.sh`는 PowerShell `Start-Process`(핸들 상속 안 함)로 띄우고 로그는 `--logging.file.name`으로 스프링이 직접 쓴다.
- **Git Bash가 `/orders` 인자를 `C:/Program Files/Git/orders`로 바꾼다** -> 드라이버에는 `orders`처럼 앞 슬래시 없이.
  (드라이버가 바뀐 형태도 되돌리긴 한다)
- **`JAVA_HOME`이 없는 JDK(`jdk-26.0.1`)를 가리켜서 mvn이 바로 실패한다** -> `server.sh`가 JDK 21을 찾아서 씀. mvn을 직접 부를 땐
  `export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1"`.
- **로그인 횟수 제한(5분 10회)은 형식이 틀린 요청까지 모든 `POST /api/auth/login`을 센다**, 메모리에 있어서 재시작하면 초기화.
  `driver.mjs`는 실행마다 1~3번 로그인한다.
- **가입/인증번호 요청 제한(10분 5회)도 접속 IP별** - `seed.py`는 가입을 4번 해서, localhost로 보내면 이어서 돌린
  `smoke.py`의 가입이 `devCode` KeyError(실제로는 429)로 멈췄다. -> `seed.py`는 `127.0.0.2`로 접속해 한도를 따로 쓴다
  (루프백이라 H2 콘솔도 됨). seed를 localhost로 되돌리면 이 문제가 다시 생긴다.
- **관리자 권한은 가입할 때 정해진다** - `ADMIN_USERNAMES` 없이 띄운 서버에서 seed하면 uiadmin이 USER. 서버 재시작 시에도
  목록 기준으로 다시 맞춰진다.
- **이메일 인증번호는 응답의 `devCode`로 받는다** - 기본 프로필은 메일 서버가 없어서 개발 모드 (`app.mail.dev-mode`).
- **Playwright `addInitScript`로 로그인 정보를 넣을 때 매번 덮어쓰면 안 된다** - 앱이 비밀번호 변경 후 새 토큰으로 바꿔
  저장해도 다음 페이지 이동에서 옛 토큰으로 되돌아가서 "로그아웃된 것처럼" 보인다. 드라이버는 비어 있을 때만 넣는다.
- **"더 보기"를 누르면 버튼 글자가 "불러오는 중..."으로 바뀐다** - 이름으로 찾은 버튼의 `detached`를 기다리면 데이터가
  오기 전에 통과한다. `hasText: /더 보기|불러오는 중/`인 버튼이 사라질 때까지 기다릴 것.
- **`screenshot({ fullPage: true })`도 화면 높이만 찍힌다** (앱이 안쪽 스크롤 영역을 씀) -> `--scroll <글자>`로 그 부분까지 내려서 찍는다.
- **Python 출력의 한국어가 `\udcec��` 식으로 깨진다** (콘솔 인코딩) -> `PYTHONIOENCODING=utf-8`. `curl ... | python -c`로
  JSON을 읽을 때도 같은 문제 - 스크립트처럼 urllib로 직접 받으면 안전하다.
- **chromium-cli / Playwright 브라우저가 없다** -> 설치된 Edge(`C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe`)를
  `executablePath`로 쓴다. 다른 브라우저는 `BROWSER_PATH`로 지정.
- **프론트 빌드 중 `npm warn cleanup ... EPERM ... sharp-webcontainers-wasm32`** - 경고일 뿐, 빌드는 성공한다.
- **`server.sh stop`(taskkill /F) 후 백그라운드 작업이 "failed (exit 1)"로 표시될 수 있다** - 강제 종료라서 그렇다, 정상.

## Troubleshooting

- **`The JAVA_HOME environment variable is not defined correctly`** (mvn 직접 실행 시): 위 `JAVA_HOME` 항목.
- **`page.goto: Cannot navigate to invalid URL ... http://localhost:8080C:/Program Files/Git/orders`**: 경로를 앞 슬래시 없이.
- **드라이버 `로그인 횟수 제한(429)`**: 5분 기다리거나 `server.sh fresh`(재시작하면 초기화) 또는 `NO_RATE_LIMIT=1`로 시작.
- **`seed.py`: `이미 seed된 서버입니다`**: `server.sh fresh`로 새로 띄울 것.
- **`서버가 90초 안에 뜨지 않았습니다`**: 8080을 다른 프로세스가 쓰는지 `server.sh status`, 로그는 `out/server.log`.
