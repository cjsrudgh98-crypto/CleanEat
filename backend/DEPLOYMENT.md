# CleanEat 배포 가이드

## 0. 아키텍처 결정

`SecurityConfig`가 이미 `/`, `/index.html`, `/static/**`를 `permitAll`로 열어두고 있었던 것을 보면,
원래 의도가 **백엔드 하나가 프론트(`frontend/index.html`)까지 같은 origin에서 서빙**하는 구조였던 것으로
보입니다. 이번에 `pom.xml`에 리소스 복사 플러그인을 추가해서 그 구조를 실제로 동작하게 만들었습니다.

- 장점: CORS 설정이 전혀 필요 없음 (프론트의 `fetch('/api/...')`가 상대경로라 그대로 동작), 배포 서비스가 1개로 끝남.
- 프론트가 React(Vite)로 바뀌면서 빌드가 필요해졌습니다. `pom.xml`의 `frontend-maven-plugin`이 `mvn package`/`mvn spring-boot:run` 시
  `../frontend`에서 자동으로 `npm install && npm run build`를 실행하고, 그 결과(`frontend/dist`)를 `target/classes/static`으로 복사합니다.
  Node/npm을 시스템에 따로 설치할 필요 없음 (플러그인이 지정된 버전을 알아서 받아씀 — 첫 빌드는 Node 다운로드 때문에 조금 더 걸립니다).
- 프론트를 별도 서비스(Vercel/Netlify)로 분리하고 싶다면 CORS 설정과 프론트의 API base URL 설정이 추가로 필요합니다 — 원하시면 그 구조로 바꿔드릴 수 있습니다.

## 1. 로컬 브라우저 동작 확인 (배포 전 필수, 제가 대신 못 하는 부분)

이 환경엔 GUI 브라우저가 없어서 실제 클릭 동작은 확인하지 못했습니다. 아래 순서로 직접 확인해주세요.

```powershell
cd C:\TiemProjact\CleanEat\backend
mvn spring-boot:run
```

- `mvn spring-boot:run`은 `generate-resources`/`process-resources` 단계를 거치므로 React 프론트가 자동으로 빌드되어 포함됩니다.
- 서버가 뜨면 브라우저에서 **http://localhost:8080/** 접속 (파일을 직접 열면 `file://`이라 API 호출이 실패합니다).

확인할 것:
1. 회원가입 → 로그인 → JWT로 인증되는지 (콘솔에 401 없는지)
2. **바코드 텍스트 입력 조회** — `barcodeSubmit` 클릭
3. **카메라 바코드 스캔** — `cameraOpenBtn` 클릭 → 브라우저가 카메라 권한을 물어보는지, QuaggaJS가 인식하는지
   - `http://localhost`은 브라우저가 "secure context"로 취급하므로 HTTPS 없이도 `getUserMedia` 권한 요청이 뜹니다.
   - 권한 팝업이 안 뜨면: 브라우저 주소창 자물쇠 아이콘 → 카메라 권한 확인, 또는 다른 앱이 카메라를 점유 중인지 확인.
4. **이미지 업로드 OCR 탭** — 라벨 사진 업로드 후 성분 추출/위험도 분석 결과
5. **PDF 리포트 다운로드** — 히스토리 목록에서 `⬇` 버튼 클릭 → PDF가 다운로드되고 한글이 깨지지 않는지 (나눔고딕 `NanumGothic-Regular.ttf` 임베딩 - SIL OFL이라 재배포 가능. 윈도우 시스템 폰트(맑은 고딕 등)는 재배포 불가라 넣지 말 것)
6. 브라우저 개발자도구 Console/Network 탭에 에러가 없는지

## 2. 운영 DB 전환 (H2 → PostgreSQL)

`application-prod.yml`을 추가했고, `SPRING_PROFILES_ACTIVE=prod`로 활성화됩니다. 필요한 환경변수:

| 변수 | 설명 | 기본값 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://host:5432/db` 형식 (**필수**, 없으면 기동 실패) | 없음 |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자 (**필수**) | 없음 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 (**필수**) | 없음 |
| `APP_JWT_SECRET` | JWT 서명 키 (**필수**, 32바이트 이상 랜덤 문자열 권장) | 없음 |
| `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` | 토스페이먼츠 결제위젯 키 (**필수**, 없으면 기동 실패 - 개발용 공개 테스트 키로 뜨지 않게 강제) | 없음 |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | 신뢰할 프록시 IP 정규식 (아래 "프록시와 클라이언트 IP" 참고) | 사설/CGNAT 대역 |
| `PORT` | Railway/Render가 자동 주입 | 8080 |
| `TESSDATA_PREFIX` | Docker 이미지 기준 이미 세팅됨 | `/app/tessdata` |
| `OCR_LANGUAGE` | | `kor+eng` |
| `APP_JWT_EXPIRATION_MS` | | 3600000 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 소셜 로그인 켤 때만 필요, 자세한 건 `OAUTH_SETUP.md` | 없음 |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | 〃 | 없음 |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | 〃 | 없음 |

소셜 로그인을 켜려면 `SPRING_PROFILES_ACTIVE=prod,oauth`로 프로필을 함께 활성화해야 합니다
(`oauth` 프로필을 빼면 위 6개 값이 없어도 기동에 아무 문제 없음 - 소셜 로그인만 꺼진 채로 동작).

### 프록시와 클라이언트 IP

prod는 `server.forward-headers-strategy: native`(Tomcat RemoteIpValve)를 씁니다. `X-Forwarded-For`를 오른쪽(가장 가까운 프록시)부터
읽어서 **신뢰하는 프록시가 아닌 첫 주소**를 클라이언트 IP로 씁니다. 로그인/인증번호 횟수 제한(`RateLimitFilter`)이 이 IP 기준이라,
클라이언트가 헤더에 아무 값이나 넣어서 제한을 피할 수 없게 하기 위함입니다.

사설/CGNAT 대역(10.x, 172.16~31.x, 192.168.x, 100.64~127.x, 127.x)에서 들어오는 프록시는 기본으로 신뢰합니다.
배포 후 아래 증상이 보이면 플랫폼 프록시가 이 대역 밖이라는 뜻이니 `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`에 그 IP 정규식을 넣으세요.
- 소셜 로그인 리다이렉트 주소가 `https://`가 아니라 `http://`로 만들어짐
- 여러 사용자가 한꺼번에 "요청이 너무 많습니다"(429)를 받음 (모두 프록시 IP 하나로 집계되는 경우)

⚠️ Railway/Render의 Postgres 플러그인이 주는 `DATABASE_URL`은 `postgres://user:pass@host:port/db` 형식이라
Spring이 바로 못 씁니다. 플러그인이 주는 개별 값(host/port/user/password/db)을 조합해서
`SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<db>`로 직접 환경변수를 설정하세요.

랜덤 JWT 시크릿 생성 예시:
```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

## 3. Docker 이미지 빌드 (로컬 검증 — 배포 전 꼭 한번 실행)

**빌드 컨텍스트가 `backend/`가 아니라 `CleanEat/`(부모 디렉터리)인 점이 중요합니다** — `frontend/`를 같이 COPY하기 때문입니다.

```powershell
cd C:\TiemProjact\CleanEat
docker build -f backend/Dockerfile -t cleaneat .

docker run --rm -p 8080:8080 `
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://<host>:5432/<db>" `
  -e SPRING_DATASOURCE_USERNAME="<user>" `
  -e SPRING_DATASOURCE_PASSWORD="<password>" `
  -e APP_JWT_SECRET="<random-secret>" `
  -e TOSS_CLIENT_KEY="<client-key>" `
  -e TOSS_SECRET_KEY="<secret-key>" `
  cleaneat
```

실행 이미지는 **Debian 13(trixie) + Temurin 21 JRE + `libtesseract5`** 입니다.
- tess4j jar에는 **Windows용 OCR 라이브러리(DLL)만** 들어 있어서, 리눅스에서는 시스템의 Tesseract/Leptonica를 쓴다.
- `pom.xml`의 tess4j는 **5.13.0으로 고정** - Leptonica 1.84.1용이고, 이 버전을 기본 패키지로 주는 안정판이 trixie다.
  더 새 tess4j(5.14~)는 Leptonica 1.85~1.87을 요구하는데 아직 어느 배포판 안정판에도 없어서 리눅스에서 OCR 첫 호출에
  `UnsatisfiedLinkError`(undefined symbol)가 난다. Ubuntu 22.04/24.04(jammy/noble)는 Leptonica가 1.82라 5.13도 안 된다.
- tess4j를 올리려면 그 버전의 Leptonica를 기본 패키지로 주는 배포판으로 실행 이미지도 같이 바꿔야 한다.
- alpine(musl)은 JNA 때문에 쓰지 않는다.

## 4. Railway 배포

1. Railway 프로젝트 생성 → PostgreSQL 플러그인 추가 (자동으로 `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` 제공)
2. 백엔드 서비스 추가 → GitHub 리포지토리 연결
3. Service Settings:
   - **Root Directory**: 리포지토리 루트 (`CleanEat/`) — `backend`로 좁히면 `frontend`에 접근 못 함
   - **Dockerfile Path**: `backend/Dockerfile`
4. Variables 탭에 위 표의 환경변수 설정 (PG 변수들을 조합해서 `SPRING_DATASOURCE_URL` 구성)
5. `PORT`는 Railway가 자동 주입하므로 별도 설정 불필요
6. Settings → Deploy → **Healthcheck Path**: `/actuator/health` (아래 "헬스체크")
7. Deploy 후 `https://<서비스>.up.railway.app/` 접속해서 1번 체크리스트 재확인

## 5. Render 배포

1. Render Dashboard → New → PostgreSQL (Internal Database URL 확인)
2. New → Web Service → 리포지토리 연결, **Root Directory**: `.` (리포 루트), **Dockerfile Path**: `backend/Dockerfile`
3. Environment 탭에 환경변수 설정 (Postgres의 Internal Database URL을 `jdbc:postgresql://...`로 변환해서 입력)
4. Render는 `PORT` 환경변수를 자동 주입 — 애플리케이션이 이미 `${PORT:8080}`을 읽도록 설정됨
5. Health Check Path: `/actuator/health` (아래 "헬스체크")

## 5-1. 토스페이먼츠 웹훅 등록 (가상계좌 입금 확인)

가상계좌로 결제하면 주문은 "입금 대기"로 남고, 고객이 입금하면 토스가 웹훅으로 알려줘서 "결제 완료"로 바뀐다.

1. 토스페이먼츠 개발자센터 → 웹훅 → **웹훅 등록**
   - URL: `https://<배포 도메인>/api/payments/webhook`
   - 이벤트: `DEPOSIT_CALLBACK`(가상계좌 입금), `PAYMENT_STATUS_CHANGED`(결제 상태 변경) 둘 다 선택
2. 개발자센터의 **웹훅 테스트 전송**으로 200 응답이 오는지 확인 (모르는 주문번호여도 200이 정상)
3. 테스트 키로 가상계좌 결제 → 개발자센터에서 입금 처리 → 주문 내역이 "결제 완료"로 바뀌는지 확인

동작 방식:
- 웹훅 내용은 믿지 않는다. 주문번호만 꺼내서 토스 API로 결제 상태를 다시 조회한 결과만 반영한다.
- 가상계좌 발급 때 받은 `secret`을 주문에 저장해 두고, 입금 알림의 `secret`이 다르면 위조로 보고 무시한다.
- 같은 알림이 여러 번 와도(토스 재전송) 주문 행을 잠그고 처리하므로 한 번만 반영된다.
- 토스 조회가 실패하면 200이 아닌 응답을 줘서 토스가 다시 보내게 한다.
- 웹훅이 못 오는 환경(로컬 `localhost` 등)을 위해 서버가 10분마다 입금 대기 주문을 토스에 직접 확인한다
  (`app.orders.deposit-check-interval-ms`). 관리자 화면의 **입금 확인** 버튼으로 바로 확인할 수도 있다.
- 입금이 확인되거나 기한이 지나 취소되면 고객 이메일로 안내 메일을 보낸다 (SMTP 설정 필요).

## 5-2. 메일 발송(SMTP) 설정 — 이메일 인증번호 / 주문 안내

회원가입·비밀번호 찾기·이메일 변경 인증번호와 발송/입금 안내 메일을 보낸다.
운영(prod)에서는 인증번호를 화면에 보여주지 않으므로 **SMTP가 없으면 회원가입이 불가능**하다.

| 환경변수 | 예시 (Gmail) | 설명 |
|---|---|---|
| `SPRING_MAIL_HOST` | `smtp.gmail.com` | SMTP 서버 (비우면 메일 발송 불가) |
| `SPRING_MAIL_PORT` | `587` | STARTTLS 포트 (기본 587) |
| `SPRING_MAIL_USERNAME` | `cleaneat.noreply@gmail.com` | SMTP 로그인 계정 |
| `SPRING_MAIL_PASSWORD` | 앱 비밀번호 16자리 | Gmail은 계정 비밀번호가 아니라 **앱 비밀번호** |
| `MAIL_FROM` | `cleaneat.noreply@gmail.com` | 보내는 사람 주소 (보통 USERNAME과 같게) |

Gmail 앱 비밀번호: Google 계정 → 보안 → 2단계 인증 켜기 → "앱 비밀번호"에서 생성.
Gmail은 하루 발송량 제한(약 500통)이 있으므로 사용자가 늘면 네이버 웍스/AWS SES/SendGrid 등으로 바꾼다
(호스트/계정 환경변수만 바꾸면 됨).

확인: 배포 후 회원가입 화면에서 "인증번호 받기" → 실제 메일 수신 확인.
로컬 개발에서는 SMTP 없이 인증번호가 화면과 서버 로그에 표시된다 (`MAIL_DEV_MODE=true`, 기본값).

## 5-3. 헬스체크

`GET /actuator/health` → `{"status":"UP"}` (로그인 불필요, 세부 정보는 숨김). 배포 플랫폼의 헬스체크 경로로 쓴다.
- DB 연결까지 확인한다 (DB가 끊기면 DOWN -> 플랫폼이 재시작).
- **메일 서버는 헬스에서 뺐다** - SMTP가 잠깐 안 돼도 주문/조회는 되는데 DOWN으로 보고되면 서버가 계속 재시작되기 때문.
- 기동 직후 1~2초는 `OUT_OF_SERVICE` (시드 데이터 등 시작 작업이 끝나기 전) -> 끝나면 `UP`. 플랫폼 헬스체크의 대기 시간은
  기동 시간(로컬 기준 12~25초)보다 넉넉하게.
- `/actuator/health/liveness`(프로세스 생존), `/actuator/health/readiness`(요청 받을 준비)도 있다. 다른 actuator 기능은 열지 않았다.

## 5-4. DB 스키마 변경 (Flyway)

테이블은 **Flyway**가 만든다 - `src/main/resources/db/migration/{h2,mysql,postgresql}/V*__*.sql`.
Hibernate는 `ddl-auto: validate`라 테이블을 고치지 않고, 엔티티와 안 맞으면 **기동을 실패시킨다** (마이그레이션 누락을 바로 알 수 있게).

**엔티티를 바꿀 때 (컬럼/테이블 추가 등)**
1. 세 폴더(h2, mysql, postgresql)에 같은 버전의 파일을 추가한다 - 예: `V2__order_returns.sql`(반품 컬럼 추가, 실제 사례).
   이미 배포된 파일(`V1__init.sql` 등)은 고치지 않는다 - Flyway가 체크섬으로 변경을 감지해서 기동이 실패한다.
2. 로컬 `mvn test` - 테스트가 H2에서 Flyway + validate로 뜨므로 H2 파일이 틀리면 여기서 실패한다.
3. MySQL/PostgreSQL 파일은 문법이 다르다 (예: `auto_increment` vs `generated by default as identity`, `datetime(6)` vs `timestamp(6)`).
   V1 파일들을 참고할 것. 열거형 컬럼은 CHECK 제약 없이 `varchar(20)`으로 (값을 추가해도 DB를 안 고쳐도 되게).

**Flyway 도입 전에 만들어진 DB (기존 `ddl-auto: update` 시절 DB)를 옮기기 - 한 번만**

이런 DB는 테이블은 있는데 Flyway 이력이 없다. 그대로 띄우면 V1으로 표시(baseline)만 하고 validate에서
`Schema-validation: missing table [...]`로 기동이 실패한다 (최근 추가된 테이블/컬럼이 없어서).
1. **백업**: `mysqldump -uroot -p --single-transaction --databases cleaneat_db > backup.sql` (PostgreSQL은 `pg_dump`)
2. **따라잡기 1회** - 예전처럼 Hibernate가 부족한 테이블/컬럼을 추가하고, `LegacySchemaMigrator`가 ENUM 컬럼을 VARCHAR로 바꾼다:
   ```bash
   java -jar target/CleanEat.jar --spring.profiles.active=mysql --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=update
   ```
   기동 로그(`Started CleanEatApplication`)가 나오고 몇 초 뒤 종료(Ctrl-C).
3. **평소대로 실행** -> Flyway가 V1으로 표시(`<< Flyway Baseline >>`)하고 validate 통과.

(로컬 MySQL `cleaneat_db` 복사본으로 이 순서를 그대로 해서 기존 회원 유지, ENUM 8개 -> VARCHAR, validate 통과를 확인함)

## 5-5. CI (GitHub Actions)

`.github/workflows/ci.yml` (저장소 루트 = `CleanEat/`) - 푸시/PR마다:
- **backend**: `debian:trixie` 컨테이너(배포 이미지와 같은 OCR 라이브러리)에서 `mvn test`. tessdata는 Dockerfile과 같은 파일/체크섬으로 받는다.
- **frontend**: Node 22.23.3로 `npm ci` -> `npm run lint` -> `npm test`(Vitest) -> `npm run build`.
- **docker**: 위 둘이 통과하면 `backend/Dockerfile` 이미지 빌드 (푸시는 안 함).

Node 버전은 `pom.xml`(frontend-maven-plugin), CI, `frontend/package.json`의 `engines`를 같이 맞춘다.
Vite 8(rolldown)은 **Node `^20.19.0 || >=22.12.0`** 이 필요하고, 낮으면 리눅스에서 npm이 네이티브 바이너리를 조용히 건너뛰어
`Cannot find native binding` 으로 빌드가 실패한다 (Windows에서는 우연히 되기도 해서 놓치기 쉽다).

## 6. 최종 체크리스트

- [ ] 로컬에서 `mvn spring-boot:run` 후 브라우저로 6가지 기능 전부 수동 확인
- [ ] `APP_JWT_SECRET`을 개발용 기본값이 아닌 랜덤 값으로 설정
- [ ] `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY`에 실제 가맹점 키 설정 (라이브 키는 `live_`로 시작)
- [ ] Docker 이미지 로컬 빌드/실행 성공 (이미지 안에서 OCR(`/api/scan/image`)까지 확인)
- [ ] 배포 플랫폼 헬스체크 경로를 `/actuator/health`로 설정 (5-3)
- [ ] GitHub에 올린 뒤 Actions 탭에서 CI 3개 잡(backend/frontend/docker)이 초록색인지 (5-5)
- [ ] Postgres 연결 문자열이 `jdbc:postgresql://...` 형식인지 확인 (`postgres://`가 아님)
- [ ] 배포 후 실제 URL에서 회원가입 → 바코드 조회 → PDF 다운로드까지 한 번 더 확인
- [ ] H2 콘솔이 운영에서 비활성화되어 있는지 확인 (`application-prod.yml`에 이미 반영됨)
- [ ] 토스 개발자센터에 웹훅 URL 등록 후 테스트 전송 200 확인 (5-1)
- [ ] SMTP 환경변수 설정 후 회원가입 인증번호 메일이 실제로 오는지 확인 (5-2)
- [ ] 홈 화면에 추가(PWA): **HTTPS 주소에서만 동작** (Railway/Render 기본 주소는 HTTPS라 그대로 됨). 배포 후 휴대폰
      크롬에서 "설치" 버튼 / 아이폰 사파리에서 "공유 → 홈 화면에 추가"가 되는지 확인.
      서비스 워커(`frontend/public/sw.js`)는 로그인·결제·API를 캐시하지 않고 화면과 빌드 파일만 캐시한다.
      캐시 방식 자체를 바꿀 때만 sw.js의 `VERSION`을 올린다 (일반 배포는 그대로 두어도 새 화면이 바로 반영됨)
