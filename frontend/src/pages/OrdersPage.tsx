import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { OrderData, OrderItemData, RefundAccount } from '../api/types'
import { appendUnique } from '../lib/paging'
import { needsRefundAccount } from '../lib/refundAccount'
import { useOrderActionDialog } from '../lib/useOrderActionDialog'
import { ReceiptCodes } from '../components/ReceiptCodes'
import { STATUS_CLASS, STATUS_LABEL } from '../components/orderStatus'
import { OrderItemLine } from '../components/OrderItemLine'
import { OrderProgress, TrackingInfo } from '../components/OrderProgress'
// 결제 연동 전 주문에 저장된 값들 (결제 연동 후에는 토스가 알려준 "카드", "간편결제" 등이 그대로 저장됨)
const PAYMENT_LABEL: Record<string, string> = {
  CARD: '카드 결제',
  BANK_TRANSFER: '무통장 입금',
  VIRTUAL_ACCOUNT: '가상계좌',
  TOSS: '토스페이먼츠',
}

const NOTE_STYLE = { margin: '0.6rem 0 0', fontSize: '0.78rem', color: 'var(--text-muted)' } as const

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })
}

// 취소는 안 되는데 이유가 있는 상태 - 버튼 대신 안내를 보여준다
function CancelBlockedNote({ order }: { order: OrderData }) {
  if (order.status === 'PREPARING') {
    return <p style={NOTE_STYLE}>배송 준비가 시작되어 직접 취소할 수 없어요. 취소는 고객센터로 문의해주세요.</p>
  }
  if (order.status === 'SHIPPING') {
    return <p style={NOTE_STYLE}>이미 발송되어 취소할 수 없어요. 상품을 받으신 뒤 반품을 신청할 수 있어요.</p>
  }
  return null
}

/** 반품 진행 상황 (신청 가능 기간 / 신청됨 / 거절됨 / 완료) */
function ReturnNote({ order }: { order: OrderData }) {
  if (order.status === 'RETURN_REQUESTED') {
    return (
      <div className="notice-box" style={{ marginTop: '0.6rem' }}>
        <strong>반품 신청됨</strong>
        {order.returnRequestedAt && <> · {formatDate(order.returnRequestedAt)}</>}
        <br />사유: {order.returnReason}
        <br />확인 후 승인되면 결제 금액이 환불됩니다.
      </div>
    )
  }
  if (order.status === 'RETURNED') {
    return (
      <div className="notice-box" style={{ marginTop: '0.6rem' }}>
        <strong>반품 완료</strong>
        {order.returnedAt && <> · {formatDate(order.returnedAt)} 환불</>}
        <br />카드 결제는 카드사에 따라 영업일 기준 3~7일 안에 취소가 반영됩니다.
      </div>
    )
  }
  if (order.status === 'DELIVERED' && order.returnRejectReason) {
    return (
      <div className="notice-box" style={{ marginTop: '0.6rem' }}>
        <strong>반품이 거절되었습니다</strong>
        <br />사유: {order.returnRejectReason}
        <br />궁금한 점은 고객센터로 문의해주세요.
      </div>
    )
  }
  if (order.returnable && order.returnDeadline) {
    return <p style={NOTE_STYLE}>{formatDate(order.returnDeadline)}까지 반품을 신청할 수 있어요.</p>
  }
  return null
}

// 영수증 QR은 실제로 결제(구매)가 끝난 주문에만 보여준다 (반품 신청 중도 아직 구매 상태)
function isPurchased(order: OrderData) {
  return ['PLACED', 'PAID', 'PREPARING', 'SHIPPING', 'DELIVERED', 'RETURN_REQUESTED'].includes(order.status)
}

export default function OrdersPage() {
  const { auth } = useAuth()
  const { orderId } = useParams()
  const [orders, setOrders] = useState<OrderData[]>([])
  const [order, setOrder] = useState<OrderData | null>(null)
  // 처음 불러오기 실패 - 보여줄 목록이 없으므로 페이지 전체를 오류로 바꾼다
  const [error, setError] = useState<string | null>(null)
  // 취소/더 보기 실패 - 목록은 그대로 두고, 실패한 주문 아래(더 보기는 버튼 위)에만 보여준다
  const [actionError, setActionError] = useState<{ orderId: number | null; message: string } | null>(null)
  // 취소/반품 요청을 보내는 중인 주문 (버튼 두 번 누르기 방지)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [page, setPage] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const { ask, dialog: actionDialog } = useOrderActionDialog()

  useEffect(() => {
    if (!auth) return
    if (orderId) {
      api.getOrder(Number(orderId), auth).then(setOrder).catch(() => setError('주문을 찾을 수 없습니다.'))
    } else {
      api
        .getMyOrders(auth)
        .then((data) => {
          setOrders(data.items)
          setPage(0)
          setHasNext(data.hasNext)
        })
        .catch(() => setError('주문 내역을 불러오지 못했습니다.'))
    }
  }, [auth, orderId])

  if (!auth) {
    return <div className="card">로그인 후 주문 내역을 확인할 수 있습니다.</div>
  }

  async function loadMore() {
    if (!auth) return
    setLoadingMore(true)
    setActionError(null)
    try {
      const data = await api.getMyOrders(auth, page + 1)
      setOrders((prev) => appendUnique(prev, data.items))
      setPage(data.page)
      setHasNext(data.hasNext)
    } catch (err) {
      setActionError({ orderId: null, message: err instanceof ApiError ? err.message : '주문 내역을 더 불러오지 못했습니다.' })
    } finally {
      setLoadingMore(false)
    }
  }

  // 주문 하나에 대한 요청 - 성공하면 목록/상세의 그 주문만 바꾸고, 실패하면 그 주문 아래에 오류를 보여준다
  async function runOrderAction(id: number, action: () => Promise<OrderData>, failMessage: string) {
    setBusyId(id)
    setActionError(null)
    try {
      const updated = await action()
      if (order) setOrder(updated)
      setOrders((prev) => prev.map((o) => (o.id === id ? updated : o)))
    } catch (err) {
      setActionError({ orderId: id, message: err instanceof ApiError ? err.message : failMessage })
    } finally {
      setBusyId(null)
    }
  }

  async function cancelOrder(target: OrderData) {
    if (!auth) return
    let refundAccount: RefundAccount | undefined
    if (needsRefundAccount(target)) {
      // 입금이 끝난 가상계좌 주문은 환불 받을 계좌가 있어야 취소(환불)할 수 있다 - 이 폼이 취소 확인을 겸한다
      const answer = await ask(target, 'customerCancel')
      if (!answer) return
      refundAccount = answer.refundAccount
    } else {
      const question =
        target.status === 'PAID' || target.status === 'AWAITING_DEPOSIT'
          ? '주문을 취소하고 결제 금액을 환불할까요?'
          : '이 주문을 취소할까요?'
      if (!window.confirm(question)) return
    }
    await runOrderAction(target.id, () => api.cancelOrder(target.id, auth, refundAccount), '취소하지 못했습니다.')
  }

  // 상품 한 줄만 취소 - 폼이 확인을 겸한다 (가상계좌면 환불 계좌도 여기서)
  async function cancelItem(target: OrderData, item: OrderItemData) {
    if (!auth) return
    const answer = await ask(target, 'customerCancelItem', item)
    if (!answer) return
    await runOrderAction(target.id, () => api.cancelOrderItem(target.id, item.id, auth, answer.refundAccount),
      '상품을 취소하지 못했습니다.')
  }

  async function requestReturn(target: OrderData) {
    if (!auth) return
    // 반품 사유 + (가상계좌면) 환불 계좌 - 이 폼이 신청 확인을 겸한다
    const answer = await ask(target, 'requestReturn')
    if (!answer) return
    await runOrderAction(target.id, () => api.requestReturn(target.id, answer.reason ?? '', auth, answer.refundAccount),
      '반품을 신청하지 못했습니다.')
  }

  async function withdrawReturn(target: OrderData) {
    if (!auth || !window.confirm('반품 신청을 철회할까요? 기간 안에는 다시 신청할 수 있어요.')) return
    await runOrderAction(target.id, () => api.withdrawReturn(target.id, auth), '반품 신청을 철회하지 못했습니다.')
  }

  if (error) {
    return <div className="card"><div className="error-box">{error}</div></div>
  }

  const errorFor = (orderId: number | null) =>
    actionError && actionError.orderId === orderId ? (
      <div className="error-box" role="alert" style={{ marginTop: '0.6rem' }}>{actionError.message}</div>
    ) : null

  // 주문 하나의 안내 + 버튼(취소 / 반품 신청 / 반품 철회) + 그 주문의 오류 - 목록과 상세가 같이 쓴다
  const orderActions = (o: OrderData) => (
    <>
      <CancelBlockedNote order={o} />
      <ReturnNote order={o} />
      {(o.cancelable || o.returnable || o.status === 'RETURN_REQUESTED') && (
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginTop: '0.6rem' }}>
          {o.cancelable && (
            <button className="btn btn-danger" disabled={busyId === o.id} onClick={() => cancelOrder(o)}>
              주문 취소
            </button>
          )}
          {o.returnable && (
            <button className="btn" disabled={busyId === o.id} onClick={() => requestReturn(o)}>
              반품 신청
            </button>
          )}
          {o.status === 'RETURN_REQUESTED' && (
            <button className="btn" disabled={busyId === o.id} onClick={() => withdrawReturn(o)}>
              반품 신청 철회
            </button>
          )}
        </div>
      )}
      {errorFor(o.id)}
    </>
  )

  if (orderId) {
    if (!order) return <div className="card">불러오는 중...</div>
    return (
      <div className="card">
        {actionDialog}
        <h2 style={{ margin: '0 0 0.75rem', fontSize: '1.05rem' }}>
          {order.status === 'PAID' ? '결제가 완료되었습니다' : '주문 상세'}
        </h2>
        <p className={`badge ${STATUS_CLASS[order.status]}`} style={{ marginBottom: '1rem' }}>
          {STATUS_LABEL[order.status]}
        </p>
        <OrderDetail order={order} onCancelItem={order.itemCancelable ? (item) => cancelItem(order, item) : undefined}
                     busy={busyId === order.id} />
        {order.tossOrderId && isPurchased(order) && (
          <ReceiptCodes tossOrderId={order.tossOrderId} />
        )}
        {orderActions(order)}
        <Link to="/orders" className="btn" style={{ marginTop: '1rem', marginLeft: '0.5rem', display: 'inline-block' }}>
          주문 내역으로
        </Link>
      </div>
    )
  }

  return (
    <div className="card">
      {actionDialog}
      <h2 className="section-title">주문 내역</h2>
      {orders.length === 0 ? (
        <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.85rem' }}>주문 내역이 없습니다.</p>
      ) : (
        <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {orders.map((o) => (
            <li key={o.id} style={{ borderBottom: '1px solid var(--border)', paddingBottom: '1rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span className={`badge ${STATUS_CLASS[o.status]}`}>{STATUS_LABEL[o.status]}</span>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                  {new Date(o.orderedAt).toLocaleString('ko-KR')}
                </span>
              </div>
              <OrderDetail order={o} onCancelItem={o.itemCancelable ? (item) => cancelItem(o, item) : undefined}
                           busy={busyId === o.id} />
              {orderActions(o)}
            </li>
          ))}
        </ul>
      )}
      {errorFor(null)}
      {hasNext && (
        <button className="btn" type="button" style={{ marginTop: '1rem', width: '100%' }}
                disabled={loadingMore} onClick={loadMore}>
          {loadingMore ? '불러오는 중...' : '더 보기'}
        </button>
      )}
    </div>
  )
}

function OrderDetail({ order, onCancelItem, busy }: {
  order: OrderData
  onCancelItem?: (item: OrderItemData) => void
  busy?: boolean
}) {
  return (
    <div>
      <OrderProgress order={order} />
      <ul style={{ margin: '0 0 0.5rem', paddingLeft: '1.1rem' }}>
        {order.items.map((item) => (
          <OrderItemLine key={item.id} item={item} busy={busy}
                         onCancel={onCancelItem ? () => onCancelItem(item) : undefined} />
        ))}
      </ul>
      <p style={{ margin: '0 0 0.3rem', fontWeight: 700 }}>
        총 {order.netAmount.toLocaleString()}원
        {order.cancelledAmount > 0 && (
          <span style={{ marginLeft: '0.35rem', fontSize: '0.78rem', fontWeight: 400, color: 'var(--text-muted)' }}>
            (주문 {order.totalAmount.toLocaleString()}원 중 {order.cancelledAmount.toLocaleString()}원 부분 취소·환불)
          </span>
        )}
      </p>
      <p style={{ margin: '0 0 0.2rem', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
        {order.recipientName} · {order.phone} · {order.address}
      </p>
      <p style={{ margin: 0, fontSize: '0.82rem', color: 'var(--text-muted)' }}>
        결제 방법: {PAYMENT_LABEL[order.paymentMethod] ?? order.paymentMethod}
        {order.paidAt && <> · 결제일시 {new Date(order.paidAt).toLocaleString('ko-KR')}</>}
      </p>
      {order.status === 'AWAITING_DEPOSIT' && order.virtualAccountNumber && (
        <p style={{ margin: '0.35rem 0 0', fontSize: '0.82rem' }}>
          입금 계좌: {order.virtualAccountBank} {order.virtualAccountNumber}
          {order.virtualAccountDueDate && <> · {new Date(order.virtualAccountDueDate).toLocaleString('ko-KR')}까지</>}
        </p>
      )}
      {order.status === 'AWAITING_DEPOSIT' && (
        <p style={{ margin: '0.2rem 0 0', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          {order.totalAmount.toLocaleString()}원을 입금하면 자동으로 결제 완료로 바뀌어요. 기한이 지나면 주문이 취소됩니다.
        </p>
      )}
      <TrackingInfo order={order} />
      {order.status === 'CANCELLED' && order.failReason && (
        <p style={{ margin: '0.35rem 0 0', fontSize: '0.82rem', color: 'var(--text-muted)' }}>취소 사유: {order.failReason}</p>
      )}
      {order.receiptUrl && (
        <a
          href={order.receiptUrl}
          target="_blank"
          rel="noreferrer"
          className="link-btn"
          style={{ display: 'inline-block', marginTop: '0.35rem', textDecoration: 'none' }}
        >
          영수증 보기 ↗
        </a>
      )}
    </div>
  )
}
