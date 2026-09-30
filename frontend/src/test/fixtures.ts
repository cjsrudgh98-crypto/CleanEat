// 테스트용 데이터 - 필요한 값만 덮어써서 쓴다
import type { AuthState, OrderData, PageData } from '../api/types'

export const testAuth: AuthState = { token: 'test-token', userId: 1, username: 'uiuser', nickname: '길동', role: 'USER' }

export function makeOrder(overrides: Partial<OrderData> = {}): OrderData {
  return {
    id: 1,
    status: 'PAID',
    items: [{ id: 11, productName: '무첨가 현미 과자', unitPrice: 4500, quantity: 2, cancelled: false }],
    totalAmount: 9000,
    cancelledAmount: 0,
    netAmount: 9000,
    itemCancelable: false,
    recipientName: '홍길동',
    phone: '010-1234-5678',
    address: '서울시 테스트로 1',
    requestNote: null,
    paymentMethod: '카드',
    orderedAt: '2026-09-27T10:00:00',
    tossOrderId: 'CE-test',
    orderName: '무첨가 현미 과자',
    paidAt: '2026-09-27T10:01:00',
    receiptUrl: null,
    failReason: null,
    virtualAccountBank: null,
    virtualAccountNumber: null,
    virtualAccountDueDate: null,
    courier: null,
    trackingNumber: null,
    trackingUrl: null,
    preparingAt: null,
    shippedAt: null,
    deliveredAt: null,
    cancelledAt: null,
    cancelable: true,
    returnable: false,
    returnDeadline: null,
    returnReason: null,
    returnRequestedAt: null,
    returnRejectReason: null,
    returnRejectedAt: null,
    returnedAt: null,
    returnRefundAccountSummary: null,
    customerNickname: '길동',
    ...overrides,
  }
}

/** 입금이 끝난 가상계좌 주문 (취소/반품 때 환불 계좌가 필요) */
export function makeVirtualAccountOrder(overrides: Partial<OrderData> = {}): OrderData {
  return makeOrder({ paymentMethod: '가상계좌', virtualAccountBank: '우리은행', virtualAccountNumber: 'X1234567890', ...overrides })
}

/** 반품 신청 기간 안의 배송 완료 주문 */
export function makeDeliveredOrder(overrides: Partial<OrderData> = {}): OrderData {
  return makeOrder({
    status: 'DELIVERED',
    cancelable: false,
    returnable: true,
    shippedAt: '2026-09-28T10:00:00',
    deliveredAt: '2026-09-29T10:00:00',
    returnDeadline: '2026-10-06T10:00:00',
    ...overrides,
  })
}

export function page<T>(items: T[], hasNext = false): PageData<T> {
  return { items, page: 0, size: 20, hasNext }
}
