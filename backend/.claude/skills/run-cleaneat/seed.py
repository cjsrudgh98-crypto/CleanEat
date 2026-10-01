"""화면 확인용 데이터: 사용자/관리자 가입, 스캔 기록 22건, 결제 완료 주문 22건(1건은 입금 끝난 가상계좌)."""
import json
import os
import sys
import re
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("CLEANEAT_URL", "http://localhost:8080")
# 가입/인증번호 요청 횟수 제한(10분 5회)은 접속 IP별로 센다. seed는 가입을 4번 하므로 localhost로 보내면
# 뒤이어 돌리는 smoke.py의 가입이 429로 막힌다 -> seed는 다른 루프백 주소(127.0.0.2)로 접속해서 한도를 따로 쓴다.
BASE = BASE.replace("//localhost:", "//127.0.0.2:").replace("//127.0.0.1:", "//127.0.0.2:")


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=60) as r:
        text = r.read().decode()
        return json.loads(text) if text else None


def register(username):
    email = f"{username}@example.com"
    code = call("POST", "/api/auth/email/send-code", {"email": email})["devCode"]
    vt = call("POST", "/api/auth/email/verify", {"email": email, "code": code})["verificationToken"]
    return call("POST", "/api/auth/register", {
        "username": username, "password": "Passw0rd!", "name": "홍길동", "nickname": username,
        "email": email, "phone": "010-1234-5678", "birthDate": "1998-01-01",
        "emailVerificationToken": vt, "agreeTerms": True})


for _ in range(60):
    try:
        urllib.request.urlopen(BASE + "/api/products", timeout=2)
        break
    except Exception:
        time.sleep(1)

try:
    user = register("uiuser")
except urllib.error.HTTPError as e:
    if e.code == 409:
        sys.exit("이미 seed된 서버입니다 (uiuser 존재). 새 데이터가 필요하면 server.sh fresh")
    raise
admin = register("uiadmin")
print("user", user["userId"], user["role"], "| admin", admin["userId"], admin["role"])
token = user["token"]

for i in range(22):
    call("POST", "/api/scan/barcode", {"barcode": "8800000000011" if i % 2 else "8800000001011"}, token)

call("POST", "/api/cart/items", {"productId": 1, "quantity": 1}, token)
order_ids = []
for i in range(22):
    o = call("POST", "/api/orders", {"recipientName": "홍길동", "phone": "010-1234-5678",
                                     "address": f"서울시 테스트로 {i + 1}"}, token)
    order_ids.append(o["id"])
print("orders", order_ids[0], "~", order_ids[-1])

# ---- H2 콘솔로 결제 완료 상태 만들기 (토스 결제 없이 화면 확인용) ----
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor())
page = opener.open(BASE + "/h2-console/").read().decode()
sid = re.search(r"jsessionid=([0-9a-f]+)", page).group(1)
form = urllib.parse.urlencode({"language": "en", "setting": "Generic H2 (Embedded)", "name": "Generic H2 (Embedded)",
                               "driver": "org.h2.Driver", "url": "jdbc:h2:mem:cleaneat", "user": "sa",
                               "password": ""}).encode()
opener.open(f"{BASE}/h2-console/login.do?jsessionid={sid}", form).read()


def sql(statement):
    body = urllib.parse.urlencode({"sql": statement}).encode()
    html = opener.open(f"{BASE}/h2-console/query.do?jsessionid={sid}", body).read().decode()
    m = re.search(r"Update count: (\d+)", html)
    return m.group(1) if m else re.sub(r"<[^>]+>", " ", html)[:300]


print("paid:", sql(f"UPDATE orders SET status='PAID', payment_key='test_pk_' || id, payment_method='카드', "
                   f"paid_at=CURRENT_TIMESTAMP WHERE user_id={user['userId']}"))
va_id = order_ids[-1]
print("va:", sql(f"UPDATE orders SET payment_method='가상계좌', virtual_account_bank='우리은행', "
                 f"virtual_account_number='X1234567890' WHERE id={va_id}"))
print("VA_ORDER_ID", va_id)

# ---- 반품 확인용: 별도 회원 uireturn (uiuser 주문 목록/개수에 영향 없게) ----
#   A 카드, 1일 전 배송 완료 -> 고객이 반품 신청/철회 (승인하면 가짜 결제키라 토스가 거절)
#   B 가상계좌, 1일 전 배송 완료 -> 반품 신청 때 환불 계좌 폼 / 관리자 거절
#   C 결제키 없음(결제 연동 전 주문), 반품 신청됨 -> 관리자 승인이 토스 없이 끝까지 된다 (재고 복구)
#   D 카드, 10일 전 배송 완료 -> 반품 기간(7일) 지남
ret = register("uireturn")
ret_token = ret["token"]
call("POST", "/api/cart/items", {"productId": 2, "quantity": 1}, ret_token)
ret_ids = [call("POST", "/api/orders", {"recipientName": "반품고객", "phone": "010-2222-3333",
                                        "address": f"서울시 반품로 {i + 1}"}, ret_token)["id"] for i in range(4)]
a, b, c, d = ret_ids
# 배송까지 끝난 주문의 공통 값 (status는 주문마다 따로 - 한 SET에 같은 컬럼을 두 번 쓰면 H2가 거절한다)
shipped = ("payment_method='카드', paid_at=DATEADD('DAY', -3, CURRENT_TIMESTAMP), "
           "shipped_at=DATEADD('DAY', -2, CURRENT_TIMESTAMP), courier='CJ대한통운', tracking_number='123456789012'")
results = [
    sql(f"UPDATE orders SET status='DELIVERED', {shipped}, payment_key='test_pk_' || id, "
        f"delivered_at=DATEADD('DAY', -1, CURRENT_TIMESTAMP) WHERE id IN ({a}, {b})"),
    sql(f"UPDATE orders SET payment_method='가상계좌', virtual_account_bank='우리은행', virtual_account_number='X9990001' "
        f"WHERE id={b}"),
    sql(f"UPDATE orders SET status='RETURN_REQUESTED', {shipped}, payment_key=NULL, "
        f"delivered_at=DATEADD('DAY', -1, CURRENT_TIMESTAMP), return_reason='사이즈가 달라요', "
        f"return_requested_at=CURRENT_TIMESTAMP WHERE id={c}"),
    sql(f"UPDATE orders SET status='DELIVERED', {shipped}, payment_key='test_pk_' || id, "
        f"delivered_at=DATEADD('DAY', -10, CURRENT_TIMESTAMP) WHERE id={d}"),
]
# 모두 "Update count" 숫자여야 한다 - 아니면 H2 오류 화면이므로 멈춘다
if not all(r.isdigit() for r in results):
    sys.exit(f"반품 데이터 준비 실패: {results}")
print("returns:", results)
print("RETURN_ORDERS", "A", a, "B(va)", b, "C(requested)", c, "D(expired)", d)

# ---- 부분 취소 확인용: uipartial - 상품 두 줄짜리 결제 완료 주문 (결제키 없음 -> 토스 없이 끝까지 됨) ----
part = register("uipartial")
part_token = part["token"]
call("POST", "/api/cart/items", {"productId": 1, "quantity": 2}, part_token)
call("POST", "/api/cart/items", {"productId": 2, "quantity": 1}, part_token)
part_id = call("POST", "/api/orders", {"recipientName": "부분고객", "phone": "010-4444-5555",
                                       "address": "서울시 부분취소로 1"}, part_token)["id"]
r = sql(f"UPDATE orders SET status='PAID', payment_method='카드', payment_key=NULL, paid_at=CURRENT_TIMESTAMP WHERE id={part_id}")
if not r.isdigit():
    sys.exit(f"부분 취소 데이터 준비 실패: {r}")
print("PARTIAL_ORDER", part_id)

# ---- 매출 통계 확인용: uiuser 결제일을 최근 한 달에 흩어 둔다 (차트에 막대가 여러 개 보이게) ----
r = sql(f"UPDATE orders SET paid_at=DATEADD('DAY', -MOD(id * 3, 28), CURRENT_TIMESTAMP) WHERE user_id={user['userId']}")
print("sales spread:", r)
