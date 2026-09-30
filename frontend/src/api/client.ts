import type {
  AdminProduct,
  AdminProductInput,
  AdminSummary,
  DictionaryIngredient,
  IngredientInput,
  AuthState,
  EmailCodeResult,
  EmailVerified,
  EatingStats,
  StatsDays,
  CartData,
  CreateOrderInput,
  DietType,
  FindUsernameResult,
  OrderData,
  PageData,
  RefundAccount,
  SalesPeriod,
  SalesReport,
  OrderStatus,
  ReviewItem,
  ReviewList,
  PasswordResetTicket,
  PaymentConfig,
  PaymentConfirmInput,
  ProductRecommendation,
  ReceiptScanResult,
  RegisterInput,
  ScanHistoryItem,
  ScanResult,
  StoreListing,
  UserProfileData,
} from './types'
import { shrinkImage } from '../lib/shrinkImage'

const AUTH_STORAGE_KEY = 'cleaneat_auth'

export function loadAuthState(): AuthState | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    return raw ? (JSON.parse(raw) as AuthState) : null
  } catch {
    return null
  }
}

export function saveAuthState(state: AuthState | null) {
  try {
    if (state) localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(state))
    else localStorage.removeItem(AUTH_STORAGE_KEY)
  } catch {
    // 프라이빗 브라우징 등 localStorage 접근 불가 상황은 무시
  }
}

class ApiError extends Error {
  status: number
  // 서버가 보낸 오류 응답 본문 (code 등 추가 정보가 필요한 화면용, JSON이 아니면 null)
  body: Record<string, unknown> | null
  constructor(status: number, message: string, body: Record<string, unknown> | null = null) {
    super(message)
    this.status = status
    this.body = body
  }
}

export { ApiError }

// AuthProvider가 마운트 시 등록 - 어디서든 401이 뜨면 로그인 상태를 정리해서 화면이 "로그인 필요" 상태로 되돌아가게 한다
// (안 그러면 토큰 만료 시 요청만 조용히 실패하고 화면엔 아무 변화가 없어서 "왜 안 보이지" 하고 헷갈리게 됨)
let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

// 서버는 토큰 유효시간이 절반 이하로 남으면 X-Auth-Token 헤더로 새 토큰을 준다 - 받아서 바꿔 끼우면 활동 중에는 로그인이 유지된다
let onTokenRefreshed: ((token: string) => void) | null = null
export function setTokenRefreshedHandler(handler: ((token: string) => void) | null) {
  onTokenRefreshed = handler
}

async function parseError(response: Response): Promise<{ message: string; body: Record<string, unknown> | null }> {
  try {
    const data = await response.json()
    if (data && typeof data === 'object') {
      return { message: typeof data.message === 'string' ? data.message : '요청을 처리하지 못했습니다.', body: data }
    }
  } catch {
    // JSON이 아니면 무시
  }
  return { message: '요청을 처리하지 못했습니다.', body: null }
}

async function parseErrorMessage(response: Response): Promise<string> {
  return (await parseError(response)).message
}

async function request<T>(
  path: string,
  options: RequestInit & { auth?: AuthState | null } = {},
): Promise<T> {
  const { auth, headers, ...rest } = options
  const response = await fetch(path, {
    ...rest,
    headers: {
      ...(headers ?? {}),
      ...(auth ? { Authorization: `Bearer ${auth.token}` } : {}),
    },
  })
  const refreshed = auth ? response.headers.get('X-Auth-Token') : null
  if (refreshed) onTokenRefreshed?.(refreshed)
  if (!response.ok) {
    // 토큰을 실어 보낸 요청의 401만 "세션 만료"로 본다 - 로그인 실패 같은 비인증 요청의 401은 서버 메시지를 그대로 보여준다
    if (response.status === 401 && auth) {
      onUnauthorized?.()
      throw new ApiError(401, '로그인이 필요합니다.')
    }
    const { message, body } = await parseError(response)
    throw new ApiError(response.status, message, body)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  register(input: RegisterInput) {
    return request<AuthState>('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
  },

  findUsername(name: string, email: string) {
    return request<FindUsernameResult>('/api/auth/find-username', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, email }),
    })
  },

  // 회원가입 이메일 인증: 인증번호 발송 -> 확인하면 가입 요청에 넣을 토큰을 준다
  sendRegisterCode(email: string) {
    return request<EmailCodeResult>('/api/auth/email/send-code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    })
  },

  verifyRegisterCode(email: string, code: string) {
    return request<EmailVerified>('/api/auth/email/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, code }),
    })
  },

  // 비밀번호 재설정: 아이디+이메일이 맞으면 가입 이메일로 인증번호 발송 (맞지 않아도 같은 응답 - 계정 존재 여부를 숨김)
  sendPasswordResetCode(username: string, email: string) {
    return request<EmailCodeResult>('/api/auth/password-reset/send-code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, email }),
    })
  },

  verifyForPasswordReset(username: string, email: string, code: string) {
    return request<PasswordResetTicket>('/api/auth/password-reset/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, email, code }),
    })
  },

  resetPassword(resetToken: string, newPassword: string) {
    return request<void>('/api/auth/password-reset/confirm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ resetToken, newPassword }),
    })
  },

  // 소셜 로그인 후 주소로 받은 1회용 코드를 로그인 정보로 바꾼다 (토큰을 주소창에 노출하지 않기 위함)
  exchangeOAuthCode(code: string) {
    return request<AuthState>('/api/auth/oauth/exchange', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code }),
    })
  },

  // 저장된 로그인 정보 최신화 (닉네임/관리자 권한)
  me(auth: AuthState) {
    return request<AuthState>('/api/auth/me', { auth })
  },

  login(username: string, password: string) {
    return request<AuthState>('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
  },

  scanBarcode(barcode: string, auth: AuthState | null) {
    return request<ScanResult>('/api/scan/barcode', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ barcode }),
      auth,
    })
  },

  // 영수증 QR/바코드에서 읽은 글자를 그대로 보낸다 (주문번호 또는 상품 바코드 목록)
  scanReceipt(code: string, auth: AuthState | null) {
    return request<ReceiptScanResult>('/api/scan/receipt', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code }),
      auth,
    })
  },

  // QR/바코드가 없는 영수증 사진 - 서버가 상품명을 읽어서 상품을 찾는다
  async scanReceiptImage(file: File, auth: AuthState | null) {
    const formData = new FormData()
    formData.append('file', await shrinkImage(file))
    return request<ReceiptScanResult>('/api/scan/receipt-image', {
      method: 'POST',
      body: formData,
      auth,
    })
  },

  async scanImage(file: File, auth: AuthState | null) {
    const formData = new FormData()
    formData.append('file', await shrinkImage(file))
    return request<ScanResult>('/api/scan/image', {
      method: 'POST',
      body: formData,
      auth,
    })
  },

  // 나의 식습관 통계 (최근 7 / 30 / 90일)
  getEatingStats(userId: number, days: StatsDays, auth: AuthState) {
    return request<EatingStats>(`/api/users/${userId}/stats?days=${days}`, { auth })
  },

  getHistory(userId: number, auth: AuthState, page = 0) {
    return request<PageData<ScanHistoryItem>>(`/api/users/${userId}/history?page=${page}`, { auth })
  },

  deleteHistoryItem(userId: number, historyId: number, auth: AuthState) {
    return request<void>(`/api/users/${userId}/history/${historyId}`, {
      method: 'DELETE',
      auth,
    })
  },

  clearHistory(userId: number, auth: AuthState) {
    return request<void>(`/api/users/${userId}/history`, { method: 'DELETE', auth })
  },

  async downloadReport(userId: number, historyId: number, auth: AuthState) {
    const response = await fetch(`/api/users/${userId}/history/${historyId}/report`, {
      headers: { Authorization: `Bearer ${auth.token}` },
    })
    if (!response.ok) throw new ApiError(response.status, await parseErrorMessage(response))
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `cleaneat-report-${historyId}.pdf`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  },

  getProfile(userId: number, auth: AuthState) {
    return request<UserProfileData>(`/api/users/${userId}/profile`, { auth })
  },

  saveProfile(userId: number, allergies: string[], dietTypes: DietType[], auth: AuthState) {
    return request<UserProfileData>(`/api/users/${userId}/profile`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ allergies, dietTypes }),
      auth,
    })
  },

  getCart(auth: AuthState) {
    return request<CartData>('/api/cart', { auth })
  },

  // 내 알레르기 성분이 든 상품은 처음 담을 때 409(ALLERGY_CONFIRM_REQUIRED)가 온다 -
  // 확인창에서 "그래도 담기"를 누르면 allergyConfirmed=true로 다시 보낸다 (lib/useAddToCart)
  addCartItem(productId: number, quantity: number, auth: AuthState, allergyConfirmed = false) {
    return request<CartData>('/api/cart/items', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ productId, quantity, allergyConfirmed }),
      auth,
    })
  },

  updateCartItem(cartItemId: number, quantity: number, auth: AuthState) {
    return request<CartData>(`/api/cart/items/${cartItemId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ quantity }),
      auth,
    })
  },

  removeCartItem(cartItemId: number, auth: AuthState) {
    return request<CartData>(`/api/cart/items/${cartItemId}`, { method: 'DELETE', auth })
  },

  createOrder(input: CreateOrderInput, auth: AuthState) {
    return request<OrderData>('/api/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
      auth,
    })
  },

  getMyOrders(auth: AuthState, page = 0) {
    return request<PageData<OrderData>>(`/api/orders?page=${page}`, { auth })
  },

  getOrder(orderId: number, auth: AuthState) {
    return request<OrderData>(`/api/orders/${orderId}`, { auth })
  },

  cancelOrder(orderId: number, auth: AuthState, refundAccount?: RefundAccount) {
    return request<OrderData>(`/api/orders/${orderId}/cancel`, {
      method: 'POST',
      ...(refundAccount
        ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refundAccount }) }
        : {}),
      auth,
    })
  },

  // 부분 취소 - 상품 한 줄만 (본문은 입금 끝난 가상계좌 주문일 때만)
  cancelOrderItem(orderId: number, itemId: number, auth: AuthState, refundAccount?: RefundAccount) {
    return request<OrderData>(`/api/orders/${orderId}/items/${itemId}/cancel`, {
      method: 'POST',
      ...(refundAccount
        ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refundAccount }) }
        : {}),
      auth,
    })
  },

  // 반품 신청 (배송 완료 후 기간 안) / 신청 철회 (관리자 처리 전까지)
  requestReturn(orderId: number, reason: string, auth: AuthState, refundAccount?: RefundAccount) {
    return request<OrderData>(`/api/orders/${orderId}/return`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason, refundAccount }),
      auth,
    })
  },

  withdrawReturn(orderId: number, auth: AuthState) {
    return request<OrderData>(`/api/orders/${orderId}/return/withdraw`, { method: 'POST', auth })
  },

  getPaymentConfig() {
    return request<PaymentConfig>('/api/payments/config')
  },

  confirmPayment(input: PaymentConfirmInput, auth: AuthState) {
    return request<OrderData>('/api/payments/confirm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
      auth,
    })
  },

  failPayment(orderId: string, code: string | null, message: string | null, auth: AuthState) {
    return request<OrderData>('/api/payments/fail', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ orderId, code, message }),
      auth,
    })
  },

  // 로그인 상태로 부르면 내 알레르기 경고/식단 일치 여부가 함께 온다
  getProducts(auth: AuthState | null) {
    return request<StoreListing[]>('/api/products', { auth })
  },

  getProduct(productId: number, auth: AuthState | null) {
    return request<StoreListing>(`/api/products/${productId}`, { auth })
  },

  // ---- 찜하기 ----

  getFavorites(auth: AuthState) {
    return request<StoreListing[]>('/api/favorites', { auth })
  },

  addFavorite(productId: number, auth: AuthState) {
    return request<void>(`/api/favorites/${productId}`, { method: 'PUT', auth })
  },

  // 품절 상품 재입고 알림 신청/취소 (입고되면 이메일로 한 번)
  subscribeRestock(productId: number, auth: AuthState) {
    return request<void>(`/api/restock-alerts/${productId}`, { method: 'PUT', auth })
  },

  unsubscribeRestock(productId: number, auth: AuthState) {
    return request<void>(`/api/restock-alerts/${productId}`, { method: 'DELETE', auth })
  },

  removeFavorite(productId: number, auth: AuthState) {
    return request<void>(`/api/favorites/${productId}`, { method: 'DELETE', auth })
  },

  // ---- 리뷰·별점 (목록은 비로그인도, 작성은 구매한 회원만) ----

  getReviews(productId: number, auth: AuthState | null) {
    return request<ReviewList>(`/api/products/${productId}/reviews`, { auth })
  },

  createReview(productId: number, rating: number, content: string, auth: AuthState) {
    return request<ReviewItem>('/api/reviews', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ productId, rating, content }),
      auth,
    })
  },

  updateReview(reviewId: number, rating: number, content: string, auth: AuthState) {
    return request<ReviewItem>(`/api/reviews/${reviewId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rating, content }),
      auth,
    })
  },

  deleteReview(reviewId: number, auth: AuthState) {
    return request<void>(`/api/reviews/${reviewId}`, { method: 'DELETE', auth })
  },

  getRecommendations(auth: AuthState) {
    return request<ProductRecommendation>('/api/products/recommendations', { auth })
  },

  updateNickname(userId: number, nickname: string, auth: AuthState) {
    return request<void>(`/api/users/${userId}/nickname`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nickname }),
      auth,
    })
  },

  changePassword(userId: number, currentPassword: string, newPassword: string, auth: AuthState) {
    return request<void>(`/api/users/${userId}/password`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ currentPassword, newPassword }),
      auth,
    })
  },

  // 이메일 변경(소셜 가입자는 등록): 새 이메일로 인증번호 발송 -> 인증번호와 함께 변경
  sendEmailChangeCode(userId: number, email: string, auth: AuthState) {
    return request<EmailCodeResult>(`/api/users/${userId}/email/send-code`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
      auth,
    })
  },

  changeEmail(userId: number, email: string, code: string, auth: AuthState) {
    return request<void>(`/api/users/${userId}/email`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, code }),
      auth,
    })
  },

  deleteAccount(userId: number, auth: AuthState) {
    return request<void>(`/api/users/${userId}`, { method: 'DELETE', auth })
  },

  // 공개 유해성분 사전 (분류 순)
  getIngredients() {
    return request<DictionaryIngredient[]>('/api/ingredients')
  },

  // ---- 관리자 (서버가 ADMIN 권한을 매 요청 확인) ----

  adminIngredients(auth: AuthState) {
    return request<DictionaryIngredient[]>('/api/admin/ingredients', { auth })
  },

  adminCreateIngredient(input: IngredientInput, auth: AuthState) {
    return request<DictionaryIngredient>('/api/admin/ingredients', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
      auth,
    })
  },

  adminUpdateIngredient(id: number, input: IngredientInput, auth: AuthState) {
    return request<DictionaryIngredient>(`/api/admin/ingredients/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
      auth,
    })
  },

  // 사용 중지 / 다시 사용 (지우지 않음 - 기본 사전 성분은 지워도 서버 재시작 때 다시 들어오기 때문)
  adminSetIngredientEnabled(id: number, enabled: boolean, auth: AuthState) {
    return request<DictionaryIngredient>(`/api/admin/ingredients/${id}/enabled`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled }),
      auth,
    })
  },

  adminSummary(auth: AuthState) {
    return request<AdminSummary>('/api/admin/summary', { auth })
  },

  adminProducts(auth: AuthState) {
    return request<AdminProduct[]>('/api/admin/products', { auth })
  },

  adminCreateProduct(input: AdminProductInput, auth: AuthState) {
    return request<AdminProduct>('/api/admin/products', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
      auth,
    })
  },

  adminUpdateProduct(productId: number, input: AdminProductInput, auth: AuthState) {
    return request<AdminProduct>(`/api/admin/products/${productId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
      auth,
    })
  },

  // 재고 증감 (입고 +, 파손/분실 -)
  adminAdjustStock(productId: number, delta: number, auth: AuthState) {
    return request<AdminProduct>(`/api/admin/products/${productId}/stock`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ delta }),
      auth,
    })
  },

  // statuses를 비우면 결제대기/결제실패를 뺀 전체
  adminOrders(statuses: OrderStatus[], auth: AuthState, page = 0) {
    const query = [...statuses.map((s) => `status=${s}`), `page=${page}`].join('&')
    return request<PageData<OrderData>>(`/api/admin/orders?${query}`, { auth })
  },

  adminAdvanceOrder(orderId: number, status: OrderStatus, courier: string | null, trackingNumber: string | null, auth: AuthState) {
    return request<OrderData>(`/api/admin/orders/${orderId}/status`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status, courier, trackingNumber }),
      auth,
    })
  },

  // 가상계좌 입금 대기 주문을 지금 토스에 확인 (아직 입금 전이면 그대로 입금 대기)
  adminSyncPayment(orderId: number, auth: AuthState) {
    return request<OrderData>(`/api/admin/orders/${orderId}/sync-payment`, { method: 'POST', auth })
  },

  // 배송중 주문의 운송장 정보 수정
  adminUpdateTracking(orderId: number, courier: string, trackingNumber: string, auth: AuthState) {
    return request<OrderData>(`/api/admin/orders/${orderId}/tracking`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ courier, trackingNumber }),
      auth,
    })
  },

  // 배송 조회 링크를 만들 수 있는 택배사 목록
  adminSales(period: SalesPeriod, auth: AuthState) {
    return request<SalesReport>(`/api/admin/sales?period=${period}`, { auth })
  },

  adminCouriers(auth: AuthState) {
    return request<string[]>('/api/admin/couriers', { auth })
  },

  adminCancelOrderItem(orderId: number, itemId: number, reason: string, auth: AuthState, refundAccount?: RefundAccount) {
    return request<OrderData>(`/api/admin/orders/${orderId}/items/${itemId}/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason, refundAccount }),
      auth,
    })
  },

  adminApproveReturn(orderId: number, auth: AuthState) {
    return request<OrderData>(`/api/admin/orders/${orderId}/return/approve`, { method: 'POST', auth })
  },

  adminRejectReturn(orderId: number, reason: string, auth: AuthState) {
    return request<OrderData>(`/api/admin/orders/${orderId}/return/reject`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
      auth,
    })
  },

  adminCancelOrder(orderId: number, reason: string, auth: AuthState, refundAccount?: RefundAccount) {
    return request<OrderData>(`/api/admin/orders/${orderId}/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason, refundAccount }),
      auth,
    })
  },
}
