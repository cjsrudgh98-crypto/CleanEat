import type { OrderStatus } from '../api/types'

// 고객 주문 내역과 관리자 주문 관리가 같은 이름/색을 쓴다
export const STATUS_LABEL: Record<OrderStatus, string> = {
  PLACED: '접수됨',
  PENDING_PAYMENT: '결제 대기',
  AWAITING_DEPOSIT: '입금 대기',
  PAID: '결제 완료',
  PREPARING: '배송 준비중',
  SHIPPING: '배송중',
  DELIVERED: '배송 완료',
  RETURN_REQUESTED: '반품 신청',
  RETURNED: '반품 완료',
  PAYMENT_FAILED: '결제 실패',
  CANCELLED: '취소됨',
}

export const STATUS_CLASS: Record<OrderStatus, string> = {
  PLACED: 'badge-low',
  PENDING_PAYMENT: 'badge-medium',
  AWAITING_DEPOSIT: 'badge-medium',
  PAID: 'badge-low',
  PREPARING: 'badge-low',
  SHIPPING: 'badge-low',
  DELIVERED: 'badge-low',
  RETURN_REQUESTED: 'badge-medium',
  RETURNED: 'badge-high',
  PAYMENT_FAILED: 'badge-high',
  CANCELLED: 'badge-high',
}
