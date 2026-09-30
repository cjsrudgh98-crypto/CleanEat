import type { OrderItemData } from '../api/types'

/** 주문 상품 목록 한 줄 - 부분 취소된 상품은 줄을 긋는다. onCancel이 있으면 "이 상품만 취소" */
export function OrderItemLine({ item, onCancel, busy }: { item: OrderItemData; onCancel?: () => void; busy?: boolean }) {
  return (
    <li style={{ fontSize: '0.88rem' }}>
      <span style={item.cancelled ? { textDecoration: 'line-through', color: 'var(--text-muted)' } : undefined}>
        {item.productName} × {item.quantity} ({item.unitPrice.toLocaleString()}원)
      </span>
      {item.cancelled && <span className="badge badge-high" style={{ marginLeft: '0.35rem' }}>취소됨</span>}
      {onCancel && !item.cancelled && (
        <button type="button" className="link-btn" style={{ marginLeft: '0.4rem' }} disabled={busy} onClick={onCancel}>
          이 상품만 취소
        </button>
      )}
    </li>
  )
}
