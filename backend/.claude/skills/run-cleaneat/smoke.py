"""CleanEat 백엔드 실제 HTTP 스모크 - 이번에 고친 항목들을 서버에 직접 요청해서 확인한다."""
import io
import json
import os
import struct
import sys
import time
import urllib.error
import urllib.request
import zlib

BASE = os.environ.get("CLEANEAT_URL", "http://localhost:8080")
RUN = str(int(time.time()))[-6:]
results = []
# 로그인 횟수 제한(5분 10회)은 형식이 틀린 요청까지 모든 POST /api/auth/login을 센다 - 마지막 검사에서 계산에 쓴다
login_posts = 0
LOGIN_LIMIT = 10


def call(method, path, body=None, token=None, headers=None, raw=None, content_type="application/json"):
    global login_posts
    if method == "POST" and path == "/api/auth/login":
        login_posts += 1
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None and content_type:
        req.add_header("Content-Type", content_type)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            text = r.read().decode("utf-8")
            return r.status, (json.loads(text) if text and text[0] in "[{" else text), dict(r.headers)
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8")
        try:
            parsed = json.loads(text)
        except Exception:
            parsed = text
        return e.code, parsed, dict(e.headers)


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail else ""))


def msg(body):
    return body.get("message") if isinstance(body, dict) else body


def register(username, email):
    s, b, _ = call("POST", "/api/auth/email/send-code", {"email": email})
    code = b["devCode"]
    s, b, _ = call("POST", "/api/auth/email/verify", {"email": email, "code": code})
    vtoken = b["token"] if "token" in b else b.get("verificationToken")
    req = {"username": username, "password": "Passw0rd!", "name": "홍길동", "nickname": "스모크",
           "email": email, "phone": "010-1234-5678", "birthDate": "1998-01-01",
           "emailVerificationToken": vtoken, "agreeTerms": True}
    return call("POST", "/api/auth/register", req)


print("=== 1. 잘못된 요청은 4xx ===")
s, b, _ = call("POST", "/api/auth/login", raw=b'{"username": ')
check("깨진 JSON -> 400", s == 400, f"{s} {msg(b)}")
s, b, _ = call("GET", "/api/products/abc")
check("경로 타입 불일치 -> 400", s == 400, f"{s} {msg(b)}")
s, b, _ = call("DELETE", "/api/auth/login")
check("허용 안 된 메서드 -> 405", s == 405, f"{s} {msg(b)}")
s, b, _ = call("POST", "/api/auth/login", raw=b"hello", content_type="text/plain")
check("잘못된 Content-Type -> 415", s == 415, f"{s} {msg(b)}")
s, b, _ = call("POST", "/api/scan/barcode", {"barcode": "a b/../x"})
check("바코드 형식 검증 -> 400", s == 400, f"{s} {msg(b)}")

print("\n=== 2. 가입 - 예약 아이디 차단 / 정상 가입 ===")
s, b, _ = register("kakao_1234567890", f"reserved{RUN}@example.com")
check("kakao_ 접두사 아이디 가입 거절 -> 400", s == 400 and "사용할 수 없는 아이디" in str(msg(b)), f"{s} {msg(b)}")
user = f"smoke{RUN}"
s, b, _ = register(user, f"smoke{RUN}@example.com")
check("정상 가입 -> 201", s == 201, f"{s}")
token, user_id = b["token"], b["userId"]

print("\n=== 3. 장바구니 (가입 시 생성 + 수량 상한) ===")
s, b, _ = call("GET", "/api/cart", token=token)
check("가입 직후 장바구니 조회 -> 200 (빈 장바구니)", s == 200 and b["items"] == [], f"{s}")
s, b, _ = call("POST", "/api/cart/items", {"productId": 1, "quantity": 1000}, token=token)
check("수량 1000 -> 400", s == 400, f"{s} {msg(b)}")
s, b, _ = call("POST", "/api/cart/items", {"productId": 1, "quantity": 2147483647}, token=token)
check("수량 int 최대값 -> 400", s == 400, f"{s} {msg(b)}")
s, b, _ = call("POST", "/api/cart/items", {"productId": 1, "quantity": 2}, token=token)
check("정상 담기 -> 200", s == 200 and b["items"][0]["quantity"] == 2, f"{s}")

print("\n=== 4. 스캔 + 추천 + 스캔 기록 페이지 ===")
s, b, _ = call("POST", "/api/scan/barcode", {"barcode": "8800000000011"}, token=token)
recs = b.get("recommendations", []) if isinstance(b, dict) else []
check("판매 상품 바코드 스캔 -> 200, 추천 있음, 자기 자신 제외", s == 200 and 0 < len(recs) <= 5
      and all(r["barcode"] != "8800000000011" for r in recs), f"{s} 추천 {len(recs)}개: {[r['name'] for r in recs]}")
for _ in range(6):
    call("POST", "/api/scan/barcode", {"barcode": "8800000001011"}, token=token)
s, b, _ = call("GET", f"/api/users/{user_id}/history?page=0&size=5", token=token)
check("기록 1페이지 (size=5) -> 5개, hasNext=true", s == 200 and len(b["items"]) == 5 and b["hasNext"] is True,
      f"{s} items={len(b['items'])} hasNext={b['hasNext']}")
first_ids = [i["id"] for i in b["items"]]
s, b, _ = call("GET", f"/api/users/{user_id}/history?page=1&size=5", token=token)
check("기록 2페이지 -> 나머지 2개, hasNext=false, 겹침 없음",
      s == 200 and len(b["items"]) == 2 and b["hasNext"] is False and not set(first_ids) & {i["id"] for i in b["items"]},
      f"{s} items={len(b['items'])} hasNext={b['hasNext']}")
s, b, _ = call("GET", f"/api/users/{user_id}/history?page=-1&size=100000", token=token)
check("이상한 page/size는 안전한 범위로 -> page 0, size 100", s == 200 and b["page"] == 0 and b["size"] == 100,
      f"{s} page={b.get('page')} size={b.get('size')}")
s, b, _ = call("GET", "/api/orders", token=token)
check("내 주문 목록 -> 페이지 형식", s == 200 and set(b) == {"items", "page", "size", "hasNext"}, f"{s} {b}")

print("\n=== 5. OCR 이미지 해상도 제한 ===")


def png_1bit(w, h):
    row = b"\x00" + b"\xff" * ((w + 7) // 8)
    raw = zlib.compress(row * h, 9)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 1, 0, 0, 0, 0))
            + chunk(b"IDAT", raw) + chunk(b"IEND", b""))


huge = png_1bit(10_001, 10_001)
boundary = "----smoke" + RUN
body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"huge.png\"\r\n"
        f"Content-Type: image/png\r\n\r\n").encode() + huge + f"\r\n--{boundary}--\r\n".encode()
t0 = time.time()
s, b, _ = call("POST", "/api/scan/image", raw=body, content_type=f"multipart/form-data; boundary={boundary}")
check(f"1억 화소 PNG({len(huge) // 1024}KB) -> 400, 풀기 전에 거절", s == 400 and "해상도" in str(msg(b)),
      f"{s} {msg(b)} ({time.time() - t0:.2f}s)")

print("\n=== 6. 비밀번호 변경 -> 기존 토큰 무효화 ===")
s, b, _ = call("GET", "/api/auth/me", token=token)
check("변경 전 기존 토큰 /me -> 200", s == 200, f"{s}")
s, b, h = call("PATCH", f"/api/users/{user_id}/password",
               {"currentPassword": "Passw0rd!", "newPassword": "NewPassw0rd!"}, token=token)
new_token = h.get("X-Auth-Token")
check("비밀번호 변경 -> 204 + X-Auth-Token 새 토큰", s == 204 and bool(new_token), f"{s} header={'있음' if new_token else '없음'}")
s, b, _ = call("GET", "/api/auth/me", token=token)
check("기존 토큰 /me -> 401", s == 401, f"{s}")
s, b, _ = call("GET", "/api/auth/me", token=new_token)
check("새 토큰 /me -> 200", s == 200, f"{s}")

print("\n=== 7. 비밀번호 재설정 인증번호 5회 제한 ===")
s, b, _ = call("POST", "/api/auth/password-reset/send-code", {"username": user, "email": f"smoke{RUN}@example.com"})
code = b.get("devCode")
wrong = "000000" if code != "000000" else "111111"
last = None
for i in range(5):
    s, b, _ = call("POST", "/api/auth/password-reset/verify",
                   {"username": user, "email": f"smoke{RUN}@example.com", "code": wrong})
    last = msg(b)
check("5번째 오답 메시지", s == 400 and "5번 틀렸습니다" in str(last), f"{s} {last}")
s, b, _ = call("POST", "/api/auth/password-reset/verify", {"username": user, "email": f"smoke{RUN}@example.com", "code": code})
check("그 뒤 맞는 번호도 거절 -> 400", s == 400 and "만료" in str(msg(b)), f"{s} {msg(b)}")

print("\n=== 8. 레이트리밋 (이 뒤 5분 동안 이 PC에서 로그인이 막힌다 - 그래서 마지막에 둔다) ===")
allowed = LOGIN_LIMIT - login_posts
statuses = []
for i in range(12):
    s, b, h = call("POST", "/api/auth/login", {"username": "nobody", "password": "x"},
                   headers={"X-Forwarded-For": f"10.0.0.{i}"})
    statuses.append(s)
check(f"로그인 {LOGIN_LIMIT}회 초과부터 429 (앞선 {login_posts - 12}회 포함, X-Forwarded-For를 바꿔도)",
      statuses[:allowed] == [401] * allowed and set(statuses[allowed:]) == {429}, f"{statuses}")
s, b, h = call("POST", "/api/auth/login", {"username": "nobody", "password": "x"})
check("429 응답에 Retry-After", s == 429 and "Retry-After" in h, f"{s} Retry-After={h.get('Retry-After')}")

print()
failed = [n for n, ok in results if not ok]
print(f"결과: {len(results) - len(failed)}/{len(results)} 통과")
sys.exit(1 if failed else 0)
