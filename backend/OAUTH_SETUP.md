# 소셜 로그인 (Google/Kakao/Naver) 설정 가이드

## ⚠️ 먼저 확인해주세요

채팅으로 공유해주신 설정 파일에는 다른 프로젝트("music" 서비스)의 실제 운영 시크릿이 들어 있었습니다
(Kakao/Google/Naver client-secret, Gmail SMTP 앱 비밀번호, YouTube API 키, Toss 키 등). 그 값들은
CleanEat 코드 어디에도 넣지 않았습니다 — 도메인/redirect-uri가 달라서 그대로 써도 동작하지 않고,
채팅 로그에 이미 노출됐으니 **실제로 쓰고 있는 값이면 각 콘솔에서 재발급하시길 권장**합니다.

CleanEat용으로는 새로 Google/Kakao/Naver에 애플리케이션을 등록하고, 거기서 발급받은 값을
아래 환경변수로 넣어주시면 됩니다.

## 동작 방식

- 기본 프로필로 실행하면(`mvn spring-boot:run`, 지금까지 쓰던 방식) 소셜 로그인은 **꺼진 채로** 그대로 잘 동작합니다.
  (아래 env var가 없으면 `spring.security.oauth2.client.registration.*`가 정의되지 않아서
  Spring이 OAuth2 관련 빈을 아예 만들지 않고, `SecurityConfig`가 이를 감지해서 `oauth2Login`을 건너뜁니다.)
- 소셜 로그인을 켜려면 `SPRING_PROFILES_ACTIVE=oauth` (운영에서는 `prod,oauth`)로 실행하고,
  아래 6개 env var를 **전부** 채워야 합니다. 하나라도 비어있으면 기동 자체가 실패합니다 (의도된 동작).
- 로그인 성공 시: 백엔드가 `http://<서버주소>/?token=...&userId=...&username=...` 로 리다이렉트하고,
  프론트(`frontend/index.html`)가 페이지 로드 시 그 쿼리스트링을 읽어서 `localStorage`에 저장한 뒤 URL을 정리합니다.
  실패 시에는 `/?authError=oauth_failed`로 리다이렉트되고 로그인 패널에 에러 메시지가 뜹니다.
- 최초 소셜 로그인 시 계정을 자동 생성합니다. 아이디는 `구글_고유ID` 형식(예: `google_10203040`)이고,
  같은 소셜 계정으로 다시 로그인하면 새 계정을 만들지 않고 기존 계정으로 로그인됩니다.

## 1. Google

1. https://console.cloud.google.com/apis/credentials → 프로젝트 생성 → "OAuth 동의 화면" 설정
2. 사용자 인증 정보 만들기 → OAuth 클라이언트 ID → 애플리케이션 유형: 웹 애플리케이션
3. **승인된 리디렉션 URI**: `http://localhost:8080/login/oauth2/code/google` (배포 후에는 실제 도메인으로 추가)
4. 발급된 클라이언트 ID/시크릿 → `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`

## 2. Kakao

1. https://developers.kakao.com → 애플리케이션 추가
2. 카카오 로그인 활성화, 동의항목에서 닉네임/이메일 사용 설정 (이메일은 비즈앱 전환이 필요할 수 있음)
3. **Redirect URI**: `http://localhost:8080/login/oauth2/code/kakao`
4. 앱 키의 REST API 키 → `KAKAO_CLIENT_ID`, 보안 → Client Secret 활성화 후 발급된 값 → `KAKAO_CLIENT_SECRET`

## 3. Naver

1. https://developers.naver.com/apps → 애플리케이션 등록
2. 사용 API: 네이버 로그인, 제공 정보: 이름/이메일 선택
3. **서비스 URL**: `http://localhost:8080`, **Callback URL**: `http://localhost:8080/login/oauth2/code/naver`
4. 발급된 Client ID/Secret → `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`

## 로컬에서 테스트하기

```powershell
$env:SPRING_PROFILES_ACTIVE = "oauth"
$env:GOOGLE_CLIENT_ID = "..."
$env:GOOGLE_CLIENT_SECRET = "..."
$env:KAKAO_CLIENT_ID = "..."
$env:KAKAO_CLIENT_SECRET = "..."
$env:NAVER_CLIENT_ID = "..."
$env:NAVER_CLIENT_SECRET = "..."
mvn spring-boot:run
```

`http://localhost:8080/` 접속 → 로그인 패널의 Google/카카오/네이버 버튼 클릭 → 각 provider 로그인 화면으로
리다이렉트되는지 확인 (실제 계정으로 로그인까지 진행하는 건 사용자님이 직접 확인해주셔야 합니다).

`backend/application-oauth-secrets.yml`(git 제외)에 키가 있으면 위 환경변수 대신 그 파일을 읽는다.
`.claude/skills/run-cleaneat/server.sh start`는 이 파일이 있으면 `oauth` 프로필을 자동으로 켠다.

### 프론트 개발 서버(Vite, `localhost:5173`)로 테스트할 때

```bash
APP_FRONTEND_URL=http://localhost:5173 bash .claude/skills/run-cleaneat/server.sh start   # 백엔드
cd ../frontend && npm run dev                                                          # 프론트 (5173)
```

- 콜백 주소는 **5173이 아니라 8080**으로 만들어진다 - `vite.config.ts`가 `/oauth2`, `/login`을 `changeOrigin: true`로
  넘겨서 백엔드가 `Host: localhost:8080` 기준으로 `redirect_uri`를 만든다. 그래서 콘솔에 5173을 따로 등록할 필요가 없다.
  (`changeOrigin`이 없으면 `redirect_uri=http://localhost:5173/...`이 되어 구글이 `redirect_uri_mismatch`로 거절한다)
- 로그인이 끝나면 백엔드(8080)가 `APP_FRONTEND_URL`로 돌려보낸다. 이 값이 없으면 8080의 빌드된 화면으로 가서
  개발 서버의 화면이 아닌 곳에서 로그인된다. 반대로 8080 화면만 쓸 때는 이 값을 넣지 말 것 (로그인 후 5173으로 가버림).
- 쿠키는 포트를 가리지 않아서, 5173에서 시작한 로그인 세션이 8080 콜백에서도 그대로 이어진다.

## 배포 시

`DEPLOYMENT.md`의 Railway/Render 절차에서 `SPRING_PROFILES_ACTIVE=prod,oauth`로 설정하고, 위 6개 env var를
실제 값으로 채우세요. 각 provider 콘솔에도 운영 도메인 기준 redirect URI를 추가로 등록해야 합니다
(예: `https://<서비스>.up.railway.app/login/oauth2/code/google`).
