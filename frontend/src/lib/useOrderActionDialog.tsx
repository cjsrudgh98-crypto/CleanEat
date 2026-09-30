import { useCallback, useState } from 'react'
import type { OrderData, OrderItemData } from '../api/types'
import {
  OrderActionDialog,
  type OrderActionAnswer,
  type OrderActionCopy,
  type ReasonField,
} from '../components/OrderActionDialog'
import { needsRefundAccount } from './refundAccount'

export const DEFAULT_CANCEL_REASON = '판매자 사정으로 주문 취소'
export const DEFAULT_ITEM_CANCEL_REASON = '판매자 사정으로 부분 취소'

/**
 * 폼을 여는 목적
 *  - customerCancel: 고객 주문 취소 (가상계좌 입금 완료 주문만 폼 - 환불 계좌)
 *  - adminCancel: 관리자 주문 취소 (취소 사유 + 가상계좌면 환불 계좌)
 *  - requestReturn: 고객 반품 신청 (반품 사유 + 가상계좌면 환불 계좌)
 *  - approveReturn: 관리자 반품 승인 (입력 없이 확인 - 환불 금액/계좌 안내)
 *  - rejectReturn: 관리자 반품 거절 (거절 사유)
 *  - customerCancelItem / adminCancelItem: 상품 한 줄만 취소 (item 필요 - 그 금액만 환불, 가상계좌면 환불 계좌)
 */
export type OrderAction =
  | 'customerCancel'
  | 'adminCancel'
  | 'requestReturn'
  | 'approveReturn'
  | 'rejectReturn'
  | 'customerCancelItem'
  | 'adminCancelItem'

interface FormSpec {
  copy: OrderActionCopy
  reasonField: ReasonField | null
  needsRefundAccount: boolean
}

// 부분 취소를 뺀 실제 결제 금액 (환불 안내에 쓴다)
function won(order: OrderData) {
  return `${(order.netAmount ?? order.totalAmount).toLocaleString()}원`
}

// 실제로 받은 돈이 있는 상태 (취소하면 환불) - 입금 대기는 아직 받은 돈이 없어서 계좌 발급만 취소된다
function refunds(order: OrderData) {
  return !['PLACED', 'PENDING_PAYMENT', 'AWAITING_DEPOSIT'].includes(order.status)
}

function specFor(order: OrderData, action: OrderAction, item?: OrderItemData): FormSpec {
  const virtualAccount = needsRefundAccount(order)
  switch (action) {
    case 'customerCancelItem':
    case 'adminCancelItem': {
      if (!item) throw new Error(`${action}에는 취소할 상품이 필요합니다`)
      const amount = `${(item.unitPrice * item.quantity).toLocaleString()}원`
      return {
        copy: {
          title: virtualAccount ? '상품 취소 · 환불 계좌 입력' : '상품 취소',
          description: `${item.productName} × ${item.quantity}을(를) 취소하고 ${amount}만 환불합니다. 나머지 상품은 그대로 보내드려요.`
            + (virtualAccount ? `\n가상계좌로 결제한 주문은 ${action === 'adminCancelItem' ? '고객' : '본인'} 계좌로 환불됩니다.` : ''),
          submitLabel: '이 상품 취소',
          danger: true,
        },
        reasonField: action === 'adminCancelItem'
          ? { label: '취소 사유 (고객에게 보여요)', initial: DEFAULT_ITEM_CANCEL_REASON, placeholder: DEFAULT_ITEM_CANCEL_REASON, required: false, maxLength: 200 }
          : null,
        needsRefundAccount: virtualAccount,
      }
    }
    case 'customerCancel':
    case 'adminCancel': {
      const who = action === 'adminCancel' ? '고객' : '본인'
      return {
        copy: {
          title: virtualAccount ? '주문 취소 · 환불 계좌 입력' : '주문 취소',
          description: virtualAccount
            ? `가상계좌(무통장 입금)로 결제한 주문은 ${who} 계좌로 환불됩니다. 계좌를 입력하고 환불을 요청하면 주문이 취소됩니다.`
            : refunds(order)
              ? `주문을 취소하면 결제 금액이 ${action === 'adminCancel' ? '고객에게 ' : ''}환불됩니다.`
              : '주문을 취소합니다.',
          submitLabel: virtualAccount ? '취소하고 환불 요청' : '주문 취소',
          danger: true,
        },
        reasonField: action === 'adminCancel'
          ? { label: '취소 사유 (고객에게 보여요)', initial: DEFAULT_CANCEL_REASON, placeholder: DEFAULT_CANCEL_REASON, required: false, maxLength: 200 }
          : null,
        needsRefundAccount: virtualAccount,
      }
    }
    case 'requestReturn': {
      const deadline = order.returnDeadline
        ? new Date(order.returnDeadline).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })
        : null
      return {
        copy: {
          title: virtualAccount ? '반품 신청 · 환불 계좌 입력' : '반품 신청',
          description: `${deadline ? `${deadline}까지 신청할 수 있어요. ` : ''}확인 후 반품이 승인되면 결제 금액 ${won(order)}이 `
            + (virtualAccount ? '아래 계좌로 환불됩니다.' : '결제한 수단으로 환불됩니다.')
            + ' 처리 전에는 신청을 철회할 수 있어요.',
          submitLabel: '반품 신청',
          danger: false,
        },
        reasonField: { label: '반품 사유', initial: '', placeholder: '예: 상품이 파손되어 도착했어요', required: true, maxLength: 300 },
        needsRefundAccount: virtualAccount,
      }
    }
    case 'approveReturn':
      return {
        copy: {
          title: '반품 승인 · 환불',
          description: `고객 사유: ${order.returnReason ?? '-'}\n승인하면 결제 금액 ${won(order)}이 환불되고 재고가 복구됩니다.`
            + (order.returnRefundAccountSummary ? `\n환불 계좌: ${order.returnRefundAccountSummary}` : ''),
          submitLabel: '승인하고 환불',
          danger: true,
        },
        reasonField: null,
        needsRefundAccount: false,
      }
    case 'rejectReturn':
      return {
        copy: {
          title: '반품 거절',
          description: `고객 사유: ${order.returnReason ?? '-'}\n거절하면 배송 완료 상태로 돌아가고, 고객은 다시 신청할 수 없습니다.`,
          submitLabel: '반품 거절',
          danger: true,
        },
        reasonField: { label: '거절 사유 (고객에게 보여요)', initial: '', placeholder: '예: 개봉 후 사용한 상품은 반품이 어렵습니다', required: true, maxLength: 300 },
        needsRefundAccount: false,
      }
  }
}

function summarize(order: OrderData) {
  const name = order.orderName ?? order.items[0]?.productName ?? '주문'
  return `#${order.id} ${name} · ${won(order)}`
}

interface Pending {
  order: OrderData
  spec: FormSpec
  resolve: (answer: OrderActionAnswer | null) => void
}

/**
 * 주문 취소/반품 폼을 띄우고 입력 결과를 돌려준다 (닫으면 null).
 * 화면에는 반드시 dialog를 함께 렌더링할 것 (useAddToCart와 같은 방식).
 */
export function useOrderActionDialog() {
  const [pending, setPending] = useState<Pending | null>(null)

  const ask = useCallback(
    (order: OrderData, action: OrderAction, item?: OrderItemData) =>
      new Promise<OrderActionAnswer | null>((resolve) => setPending({ order, spec: specFor(order, action, item), resolve })),
    [],
  )

  const dialog = pending ? (
    <OrderActionDialog
      copy={pending.spec.copy}
      orderSummary={summarize(pending.order)}
      reasonField={pending.spec.reasonField}
      needsRefundAccount={pending.spec.needsRefundAccount}
      onAnswer={(answer) => {
        pending.resolve(answer)
        setPending(null)
      }}
    />
  ) : null

  return { ask, dialog }
}
