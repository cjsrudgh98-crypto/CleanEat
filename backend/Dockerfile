# syntax=docker/dockerfile:1
# 빌드 컨텍스트는 반드시 backend/의 부모 디렉터리(CleanEat/)여야 합니다.
# ../frontend를 함께 COPY해서 백엔드가 같은 origin에서 프론트를 서빙하기 때문입니다.
#   docker build -f backend/Dockerfile -t cleaneat .   (CleanEat/ 에서 실행)

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 의존성만 먼저 받아서 캐시 재사용
COPY backend/pom.xml pom.xml
RUN mvn -B -q dependency:go-offline || true

COPY backend/src src
COPY frontend /frontend

# frontend-maven-plugin이 이 안에서 Node/npm을 직접 받아서 React 앱을 빌드함 (별도 apt-get 불필요, 인터넷 필요)
RUN mvn -B -q package -DskipTests

# 실행 이미지는 Debian 13(trixie) - OCR(tess4j)은 리눅스에서 시스템의 Tesseract/Leptonica를 쓰는데
# (tess4j jar에는 Windows용 DLL만 들어 있음), pom의 tess4j 5.13.0이 기대하는 Leptonica 1.84.1 / Tesseract 5.x를
# 기본 패키지로 주는 안정판이 trixie다. (Ubuntu 22.04/24.04는 Leptonica가 낮아서 OCR 첫 호출에서 UnsatisfiedLinkError)
# musl(alpine)은 JNA 때문에 쓰지 않는다.
FROM debian:trixie-slim
WORKDIR /app

# JRE는 Temurin 공식 이미지의 것을 그대로 가져온다
COPY --from=eclipse-temurin:21-jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends libtesseract5 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/target/CleanEat.jar app.jar
# OCR 학습 데이터 - backend/tessdata는 .gitignore 대상이라 git에서 받은 소스로 빌드하면 없으므로 여기서 받는다.
# 로컬 backend/tessdata와 같은 tessdata_fast 4.1.0 파일이고, 체크섬이 다르면 빌드가 실패한다 (변조/다른 버전 방지)
ADD --checksum=sha256:7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2 \
    https://github.com/tesseract-ocr/tessdata_fast/raw/4.1.0/eng.traineddata /app/tessdata/eng.traineddata
ADD --checksum=sha256:6b85e11d9bbf07863b97b3523b1b112844c43e713df8b66418a081fd1060b3b2 \
    https://github.com/tesseract-ocr/tessdata_fast/raw/4.1.0/kor.traineddata /app/tessdata/kor.traineddata

ENV TESSDATA_PREFIX=/app/tessdata
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
