#!/usr/bin/env bash
# CleanEat 서버 수명 관리: build / start / stop / status / fresh
# 경로는 스크립트 위치 기준으로 잡으므로 어디서 실행해도 된다.
set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$(cd "$SKILL_DIR/../../.." && pwd)"
OUT="$SKILL_DIR/out"
PORT="${PORT:-8080}"
BASE="http://localhost:$PORT"
mkdir -p "$OUT"

# ---- JDK 21: JAVA_HOME이 틀린 경우가 있어서(없는 jdk-26을 가리킴) 실제로 실행되는지 확인하고 고른다
find_java_home() {
  local candidates=()
  [ -n "${JAVA_HOME:-}" ] && candidates+=("$JAVA_HOME")
  candidates+=("$HOME"/.jdks/jdk-21* /c/Users/*/.jdks/jdk-21* "/c/Program Files/Java"/jdk-21* /usr/lib/jvm/*21*)
  for home in "${candidates[@]}"; do
    [ -x "$home/bin/java" ] || [ -x "$home/bin/java.exe" ] || continue
    if "$home/bin/java" -version 2>&1 | grep -q 'version "21'; then echo "$home"; return; fi
  done
  echo "JDK 21을 찾지 못했습니다 (JAVA_HOME을 JDK 21로 지정하세요)" >&2; exit 1
}

# ---- Maven: PATH에 없으면 IntelliJ에 들어 있는 것을 쓴다
find_mvn() {
  if command -v mvn >/dev/null 2>&1; then command -v mvn; return; fi
  local m
  for m in "/c/Program Files/JetBrains/"*/plugins/maven/lib/maven3/bin/mvn; do
    [ -x "$m" ] && { echo "$m"; return; }
  done
  echo "Maven을 찾지 못했습니다" >&2; exit 1
}

pid_on_port() {
  if command -v netstat >/dev/null 2>&1 && netstat -ano 2>/dev/null | grep -q LISTENING; then
    # Windows(Git Bash): "TCP 0.0.0.0:8080 ... LISTENING <pid>"
    netstat -ano | awk -v p=":$PORT" '$2 ~ p"$" && $4 == "LISTENING" { print $5; exit }'
  else
    lsof -ti:"$PORT" -sTCP:LISTEN 2>/dev/null | head -1
  fi
}

wait_until_up() {
  for _ in $(seq 1 90); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/products" || true)" = "200" ] && return 0
    sleep 1
  done
  echo "서버가 90초 안에 뜨지 않았습니다 - $OUT/server.log 확인" >&2; tail -30 "$OUT/server.log" >&2; exit 1
}

cmd_build() {
  export JAVA_HOME; JAVA_HOME="$(find_java_home)"
  local extra=()
  [ "${1:-}" = "--api-only" ] && extra+=(-Dskip.frontend.copy=true)
  echo "빌드 중 (JAVA_HOME=$JAVA_HOME)..."
  (cd "$BACKEND" && "$(find_mvn)" -B -q -o package -DskipTests "${extra[@]}")
  ls -la "$BACKEND/target/CleanEat.jar"
}

cmd_test() {
  export JAVA_HOME; JAVA_HOME="$(find_java_home)"
  # 합계 줄("Tests run: N, ..." 로 끝나는 줄)과 실패만 보여준다
  (cd "$BACKEND" && "$(find_mvn)" -B -o test -Dskip.frontend.copy=true 2>&1) \
    | grep -E 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$|<<< FAIL|ERROR\]|BUILD' | tail -15
  # 프론트: 타입체크 -> 린트 -> Vitest. pipefail이 있어야 vitest가 실패했을 때 grep 성공에 가려지지 않는다
  (
    set -o pipefail
    cd "$BACKEND/../frontend" && export PATH="$PWD/node:$PATH"
    npx tsc -b && npx eslint . \
      && npx vitest run 2>&1 | grep -E "Test Files|Tests |FAIL|×"
  ) && echo "frontend: tsc + eslint + vitest OK" || echo "frontend: 실패 (위 출력 확인)"
}

cmd_start() {
  if [ -n "$(pid_on_port)" ]; then echo "이미 $PORT 포트에서 실행 중 (pid $(pid_on_port))"; return 0; fi
  [ -f "$BACKEND/target/CleanEat.jar" ] || { echo "jar가 없습니다 - 먼저 build" >&2; exit 1; }
  local java_home args=(-Dfile.encoding=UTF-8 -jar target/CleanEat.jar)
  java_home="$(find_java_home)"
  [ "${NO_RATE_LIMIT:-}" = "1" ] && args+=(--app.rate-limit.enabled=false)
  # 소셜 로그인은 oauth 프로필에서만 켜진다 - 키 파일(application-oauth-secrets.yml, git 제외)이 있으면 자동으로 켠다.
  # 프로필을 직접 정하려면 SPRING_PROFILES_ACTIVE (예: 빈 값이 아닌 "default"로 끄기)
  local profiles="${SPRING_PROFILES_ACTIVE:-}"
  [ -z "$profiles" ] && [ -f "$BACKEND/application-oauth-secrets.yml" ] && profiles="oauth"
  [ -n "$profiles" ] && args+=("--spring.profiles.active=$profiles")
  # 관리자 아이디는 "가입할 때" 권한이 정해지므로 seed 전에 이 값으로 서버가 떠 있어야 한다
  export ADMIN_USERNAMES="${ADMIN_USERNAMES:-uiadmin}"
  rm -f "$OUT/server.log"
  if command -v powershell.exe >/dev/null 2>&1 && command -v cygpath >/dev/null 2>&1; then
    # Windows(Git Bash): nohup ... & 로 띄우면 java가 호출한 쪽의 출력 파이프 핸들을 상속해서
    # (리다이렉트해도 마찬가지) 이 스크립트를 부른 명령이 서버가 끝날 때까지 끝나지 않는다.
    # Start-Process(ShellExecute)는 핸들을 상속하지 않는다. 로그는 스프링이 직접 파일로 쓴다.
    args+=("--logging.file.name=$(cygpath -w "$OUT/server.log")")
    local arglist; arglist="$(printf "'%s'," "${args[@]}")"
    powershell.exe -NoProfile -Command "Start-Process -FilePath '$(cygpath -w "$java_home/bin/java.exe")' \
      -ArgumentList ${arglist%,} -WorkingDirectory '$(cygpath -w "$BACKEND")' -WindowStyle Hidden"
  else
    (cd "$BACKEND" && nohup "$java_home/bin/java" "${args[@]}" < /dev/null > "$OUT/server.log" 2>&1 &)
  fi
  wait_until_up
  echo "UP: $BASE (pid $(pid_on_port), 프로필 ${profiles:-default}, 로그 $OUT/server.log)"
}

cmd_stop() {
  local pid; pid="$(pid_on_port)"
  if [ -z "$pid" ]; then echo "실행 중인 서버 없음"; return 0; fi
  if command -v taskkill >/dev/null 2>&1; then taskkill //PID "$pid" //F >/dev/null; else kill "$pid"; fi
  for _ in $(seq 1 20); do [ -z "$(pid_on_port)" ] && { echo "stopped (pid $pid)"; return 0; }; sleep 1; done
  echo "종료되지 않았습니다 (pid $pid)" >&2; exit 1
}

cmd_status() {
  local pid; pid="$(pid_on_port)"
  if [ -n "$pid" ]; then echo "running pid=$pid $BASE"; else echo "stopped"; fi
  echo "server.log ERROR 줄: $(grep -c ' ERROR ' "$OUT/server.log" 2>/dev/null || true)"
}

# H2 메모리 DB라 재시작하면 데이터가 비워진다 -> 새로 띄우고 화면 확인용 데이터까지 준비
cmd_fresh() {
  cmd_stop
  cmd_start
  PYTHONIOENCODING=utf-8 python "$SKILL_DIR/seed.py"
}

case "${1:-}" in
  build) shift; cmd_build "$@" ;;
  test) cmd_test ;;
  start) cmd_start ;;
  stop) cmd_stop ;;
  status) cmd_status ;;
  fresh) cmd_fresh ;;
  *) echo "사용법: server.sh build [--api-only] | test | start | stop | status | fresh" >&2; exit 2 ;;
esac
