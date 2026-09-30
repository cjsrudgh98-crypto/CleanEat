import type { OrderData, OrderStatus } from '../api/types'

interface Step {
  label: string
  time: string | null
}

// 진행 단계 순서 (결제 전/실패/취소 주문은 진행 바를 보여주지 않는다)
const STEP_INDEX: Partial<Record<OrderStatus, number>> = {
  AWAITING_DEPOSIT: 0,
  PLACED: 0,
  PAID: 0,
  PREPARING: 1,
  SHIPPING: 2,
  DELIVERED: 3,
  // 반품은 배송이 끝난 뒤 일이라 진행 바는 배송 완료로 두고, 반품 상태는 주문 카드에 따로 보여준다
  RETURN_REQUESTED: 3,
  RETURNED: 3,
}

const DELIVERY_FINISHED: OrderStatus[] = ['DELIVERED', 'RETURN_REQUESTED', 'RETURNED']

function formatTime(iso: string | null) {
  if (!iso) return null
  return new Date(iso).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/** 결제 완료 → 배송 준비 → 배송중 → 배송 완료 진행 바 */
export function OrderProgress({ order }: { order: OrderData }) {
  const current = STEP_INDEX[order.status]
  if (current === undefined) return null

  const steps: Step[] = [
    {
      label: order.status === 'AWAITING_DEPOSIT' ? '입금 대기' : '결제 완료',
      time: formatTime(order.paidAt ?? (order.status === 'PLACED' ? order.orderedAt : null)),
    },
    { label: '배송 준비', time: formatTime(order.preparingAt) },
    { label: '배송중', time: formatTime(order.shippedAt) },
    { label: '배송 완료', time: formatTime(order.deliveredAt) },
  ]

  return (
    <ol className="order-progress" aria-label="배송 진행 상황">
      {steps.map((step, i) => {
        const state = i < current ? 'done' : i === current ? 'current' : 'todo'
        return (
          <li key={step.label} className={`order-step ${state}`} aria-current={state === 'current' ? 'step' : undefined}>
            <span className="order-step-dot" aria-hidden>
              {state === 'done' || (state === 'current' && DELIVERY_FINISHED.includes(order.status)) ? '✓' : i + 1}
            </span>
            <span className="order-step-label">{step.label}</span>
            {step.time && state !== 'todo' && <span className="order-step-time">{step.time}</span>}
          </li>
        )
      })}
    </ol>
  )
}

/** 운송장 번호 + 배송 조회 링크 (택배사가 목록에 없으면 번호만) */
export function TrackingInfo({ order }: { order: OrderData }) {
  if (!order.trackingNumber) return null
  return (
    <p style={{ margin: '0.35rem 0 0', fontSize: '0.82rem' }}>
      {order.courier} {order.trackingNumber}
      {order.trackingUrl && (
        <>
          {' · '}
          <a href={order.trackingUrl} target="_blank" rel="noreferrer" className="link-btn" style={{ textDecoration: 'none' }}>
            배송 조회 ↗
          </a>
        </>
      )}
    </p>
  )
}
