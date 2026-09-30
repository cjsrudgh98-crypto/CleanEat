export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export type AuthMode = 'login' | 'register' | null

export type Role = 'USER' | 'ADMIN'

export interface AuthState {
  token: string
  userId: number
  username: string
  nickname: string
  // 예전 버전에서 저장된 로그인 정보에는 없을 수 있다 (앱 시작 시 /api/auth/me로 채움)
  role?: Role
}

export interface RegisterInput {
  username: string
  password: string
  name: string
  nickname: string
  email: string
  phone: string
  birthDate: string // YYYY-MM-DD
  agreeTerms: boolean
  // 이메일 인증번호 확인 후 받은 1회용 토큰 (30분 유효)
  emailVerificationToken: string
}

export interface EmailCodeResult {
  message: string
  expiresInSeconds: number
  // 메일 서버가 없는 개발 모드에서만 채워진다 (운영에서는 항상 null)
  devCode: string | null
}

export interface EmailVerified {
  verificationToken: string
  expiresInSeconds: number
}

export interface FindUsernameResult {
  maskedUsername: string
  joinedAt: string
}

export interface PasswordResetTicket {
  resetToken: string
  expiresInSeconds: number
}

export interface IngredientMatch {
  ingredientName: string
  riskLevel: RiskLevel
  description: string
}

export interface ProductSummary {
  id: number
  name: string
  barcode: string
  riskLevel: RiskLevel
  // CleanEat이 실제로 판매하는 상품이면 값이 있음 (있을 때만 "담기" 버튼을 보여준다)
  price: number | null
  imageUrl: string | null
}

export interface ScanResult {
  productName: string
  barcode: string | null
  overallRisk: RiskLevel
  harmfulIngredients: IngredientMatch[]
  // 이 제품에 들어 있는 알레르기 유발 성분 전체
  allergenMatches: string[]
  // 그중 내가 마이페이지에 등록한 알레르기 (비로그인이면 빈 배열)
  userAllergyWarnings: string[]
  recommendations: ProductSummary[]
}

export interface ReceiptItem {
  productName: string
  barcode: string | null
  quantity: number
  // CleanEat 판매 상품이면 상세 페이지로 갈 수 있는 id
  storeProductId: number | null
  overallRisk: RiskLevel
  harmfulIngredients: IngredientMatch[]
  allergenMatches: string[]
  userAllergyWarnings: string[]
  dietMatch: boolean | null
  // 영수증 사진의 상품명으로 찾은 경우, 영수증에서 읽은 글자
  matchedText: string | null
}

export interface ReceiptScanResult {
  source: 'CLEANEAT_ORDER' | 'BARCODES' | 'RECEIPT_TEXT'
  title: string
  overallRisk: RiskLevel
  allergyWarningCount: number
  items: ReceiptItem[]
  failed: { code: string; reason: string }[]
  recommendations: ProductSummary[]
}

export interface ScanHistoryItem {
  id: number
  productName: string
  barcode: string | null
  overallRisk: RiskLevel
  matchedHarmfulIngredients: string[]
  matchedAllergens: string[]
  scannedAt: string
}

export type DietType = 'NONE' | 'VEGAN' | 'VEGETARIAN' | 'KETO' | 'GLUTEN_FREE' | 'LOW_SODIUM'

export interface UserProfileData {
  userId: number
  username: string
  nickname: string
  provider: string | null
  // 소셜 로그인으로 가입해 이메일을 등록하지 않았으면 null
  email: string | null
  allergies: string[]
  // 여러 개 선택 가능 (빈 배열이면 제한 없음)
  dietTypes: DietType[]
}

export interface CartItemData {
  cartItemId: number
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  availableStock: number
  // 이 상품에 든 내 알레르기 성분 (없으면 빈 배열)
  allergyWarnings: string[]
}

export interface CartData {
  items: CartItemData[]
  totalAmount: number
}

export interface CreateOrderInput {
  recipientName: string
  phone: string
  address: string
  requestNote: string
}

// 백엔드 OrderStatus 주석의 상태 흐름 참고
// PENDING_PAYMENT -> PAID(가상계좌는 AWAITING_DEPOSIT -> PAID) -> PREPARING -> SHIPPING -> DELIVERED
export type OrderStatus =
  | 'PLACED'
  | 'PENDING_PAYMENT'
  | 'AWAITING_DEPOSIT'
  | 'PAID'
  | 'PREPARING'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'RETURN_REQUESTED'
  | 'RETURNED'
  | 'PAYMENT_FAILED'
  | 'CANCELLED'

export interface OrderItemData {
  id: number
  productName: string
  unitPrice: number
  quantity: number
  // 부분 취소된 상품 (줄을 긋고 합계에서 뺀다)
  cancelled: boolean
}

// 목록 한 페이지 (서버 PageResponse) - 전체 개수 없이 다음 페이지가 있는지만 알려준다
export interface PageData<T> {
  items: T[]
  page: number
  size: number
  hasNext: boolean
}

// 입금이 끝난 가상계좌 주문을 취소할 때 환불 받을 계좌 (bankCode는 토스 은행 코드 두 자리)
export interface RefundAccount {
  bankCode: string
  accountNumber: string
  holderName: string
}

export interface OrderData {
  id: number
  status: OrderStatus
  items: OrderItemData[]
  totalAmount: number
  recipientName: string
  phone: string
  address: string
  requestNote: string | null
  paymentMethod: string
  orderedAt: string
  tossOrderId: string | null
  orderName: string | null
  paidAt: string | null
  receiptUrl: string | null
  failReason: string | null
  virtualAccountBank: string | null
  virtualAccountNumber: string | null
  virtualAccountDueDate: string | null
  courier: string | null
  trackingNumber: string | null
  // 택배사 배송 조회 페이지 (목록에 없는 택배사면 null)
  trackingUrl: string | null
  preparingAt: string | null
  shippedAt: string | null
  deliveredAt: string | null
  cancelledAt: string | null
  // 고객이 직접 취소할 수 있는 상태인지 (서버가 판단)
  cancelable: boolean
  // 고객이 상품 한 줄만 취소할 수 있는지 (결제 완료 + 남은 상품 2개 이상)
  itemCancelable: boolean
  // 부분 취소로 환불된 금액 / 실제 결제 금액 (totalAmount - cancelledAmount)
  cancelledAmount: number
  netAmount: number
  // ---- 반품 ----
  // 지금 반품 신청할 수 있는지 (배송 완료 + 기간 안 + 거절된 적 없음 - 서버가 판단) / 신청 마감 (배송 완료 주문만)
  returnable: boolean
  returnDeadline: string | null
  returnReason: string | null
  returnRequestedAt: string | null
  returnRejectReason: string | null
  returnRejectedAt: string | null
  returnedAt: string | null
  // 가상계좌 반품의 환불 계좌 요약 (예: "신한은행 ****6789 홍길동")
  returnRefundAccountSummary: string | null
  customerNickname: string | null
}

export interface PaymentConfig {
  clientKey: string
}

export interface PaymentConfirmInput {
  paymentKey: string
  orderId: string
  amount: number
}

export interface StoreListing {
  productId: number
  name: string
  price: number
  stock: number
  imageUrl: string | null
  description: string | null
  category: ProductCategory
  categoryLabel: string
  // 상품 알레르기 성분 / 맞는 식단
  allergens: string[]
  diets: DietType[]
  // 로그인 사용자의 알레르기 중 이 상품에 들어있는 것 (없으면 빈 배열)
  allergyWarnings: string[]
  // 사용자가 식단을 설정했을 때만 true/false, 아니면 null
  dietMatch: boolean | null
  // 로그인했으면 찜 여부, 비로그인이면 null
  favorite: boolean | null
  // 로그인했으면 재입고 알림을 신청했는지, 비로그인이면 null
  restockAlert: boolean | null
  // 평균 별점(소수점 한 자리, 리뷰가 없으면 null) / 리뷰 수
  averageRating: number | null
  reviewCount: number
}

export interface ReviewItem {
  id: number
  nickname: string
  rating: number
  content: string | null
  createdAt: string
  updatedAt: string | null
  // 내가 쓴 리뷰인지 (수정/삭제 버튼)
  mine: boolean
}

export interface ReviewList {
  productId: number
  averageRating: number | null
  reviewCount: number
  // [0]이 1점 ... [4]가 5점
  ratingCounts: number[]
  reviews: ReviewItem[]
  myReview: ReviewItem | null
  canWrite: boolean
  writeBlockedReason: 'LOGIN_REQUIRED' | 'NOT_PURCHASED' | 'ALREADY_WRITTEN' | null
}

export interface ProductRecommendation {
  loggedIn: boolean
  dietTypes: DietType[]
  // 예: "비건·글루텐프리"
  dietLabel: string
  allergies: string[]
  excludedByAllergy: number
  products: StoreListing[]
}

export type ProductCategory =
  | 'SNACK'
  | 'NUTS'
  | 'BAKERY'
  | 'MEAL'
  | 'GRAIN'
  | 'DAIRY'
  | 'BEVERAGE'
  | 'PROTEIN'
  | 'SAUCE'
  | 'ETC'

// ---- 관리자 ----

export interface AdminSummary {
  paid: number
  preparing: number
  shipping: number
  awaitingDeposit: number
  returnRequested: number
  lowStock: number
  soldOut: number
  lowStockThreshold: number
  todaySales: number
  todayOrders: number
}

// 관리자 기간별 매출 (서버 SalesReportResponse)
export type SalesPeriod = '7d' | '30d' | '12m'

export interface SalesPoint {
  // 화면 표시용 (일: "9/30", 월: "2026.09") / 그 칸의 첫날 (YYYY-MM-DD)
  label: string
  start: string
  sales: number
  orders: number
}

export interface SalesReport {
  period: SalesPeriod
  unit: 'day' | 'month'
  from: string
  to: string
  totalSales: number
  totalOrders: number
  averageOrderAmount: number
  series: SalesPoint[]
  topProducts: { productName: string; quantity: number; sales: number }[]
}

export interface AdminProduct {
  productId: number
  barcode: string
  name: string
  price: number
  stock: number
  category: ProductCategory
  categoryLabel: string
  imageUrl: string | null
  description: string | null
  rawIngredientsText: string | null
  allergens: string[]
  diets: DietType[]
}

export interface AdminProductInput {
  name: string
  barcode: string
  price: number
  // 등록할 때만 쓰인다 (수정 시 서버가 무시 - 재고는 증감으로만 변경)
  stock: number
  category: ProductCategory
  imageUrl: string
  description: string
  rawIngredientsText: string
  allergens: string[]
  diets: DietType[]
}

// ---- 나의 식습관 통계 (검사 기록 기반) ----

export type StatsDays = 7 | 30 | 90

export interface StatsSummary {
  scans: number
  low: number
  medium: number
  high: number
  // 유해성분이 하나라도 나온 검사 수 / 내 알레르기 성분이 들어 있던 검사 수
  withHarmful: number
  withMyAllergen: number
}

export interface StatsBucket {
  start: string // YYYY-MM-DD
  low: number
  medium: number
  high: number
}

export interface EatingStats {
  days: StatsDays
  from: string
  to: string
  // 추이 한 칸의 단위 (7·30일은 하루, 90일은 1주)
  bucketUnit: 'DAY' | 'WEEK'
  current: StatsSummary
  // 바로 앞 같은 길이 기간 (비교용)
  previous: StatsSummary
  trend: StatsBucket[]
  topHarmful: { name: string; count: number; riskLevel: RiskLevel | null; description: string | null }[]
  myAllergens: { name: string; count: number }[]
  hasAllergySettings: boolean
}

// ---- 유해성분 사전 ----

export interface DictionaryIngredient {
  id: number
  name: string
  riskLevel: RiskLevel
  category: string | null
  description: string | null
  // 성분표에서 찾을 때 쓰는 다른 표기 (영어 이름, E-번호 등)
  aliases: string[]
  // 검사에 쓰이는지 (관리자 화면용 - 공개 목록은 사용 중인 것만)
  enabled: boolean
}

export interface IngredientInput {
  name: string
  riskLevel: RiskLevel
  category: string
  description: string
  aliases: string[]
}
