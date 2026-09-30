import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

/**
 * 토스 결제창이 돌려보내는 successUrl 화면.
 * 쿼리스트링의 paymentKey/orderId/amount를 서버에 보내 "결제 승인"을 받아야 결제가 최종 완료된다
 * (여기까지 오기만 하고 승인하지 않으면 토스 쪽 결제는 일정 시간 뒤 자동 취소됨).
 */
export function PaymentSuccessPage() {
  const { auth } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [error, setError] = useState<string | null>(null)
  // StrictMode에서 effect가 두 번 돌아도 승인 요청은 한 번만 (서버도 멱등하게 처리하지만 불필요한 호출 방지)
  const requested = useRef(false)

  const paymentKey = params.get('paymentKey')
  const orderId = params.get('orderId')
  const amount = Number(params.get('amount'))
  const invalidParams = !paymentKey || !orderId || !amount

  useEffect(() => {
    if (!auth || requested.current || !paymentKey || !orderId || !amount) return
    requested.current = true
    api
      .confirmPayment({ paymentKey, orderId, amount }, auth)
      .then((order) => navigate(`/orders/${order.id}`, { replace: true }))
      .catch((err) => setError(err instanceof ApiError ? err.message : '결제 승인에 실패했습니다.'))
  }, [auth, paymentKey, orderId, amount, navigate])

  if (!auth) {
    return <div className="card">로그인 정보가 없습니다. 다시 로그인한 뒤 주문 내역을 확인해주세요.</div>
  }

  if (error || invalidParams) {
    return (
      <div className="card">
        <h2 className="section-title">결제를 완료하지 못했습니다</h2>
        <div className="error-box" style={{ marginBottom: '1rem' }}>
          {error ?? '결제 정보가 올바르지 않습니다.'}
        </div>
        <p className="muted-text" style={{ margin: '0 0 1rem' }}>
          결제 승인이 되지 않았으므로 요금은 청구되지 않습니다.
        </p>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <Link to="/checkout" className="btn btn-primary">
            다시 결제하기
          </Link>
          <Link to="/cart" className="btn">
            장바구니
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="card">
      <div className="status-row">
        <span className="spinner" />
        <span>결제를 승인하는 중입니다... 창을 닫지 마세요.</span>
      </div>
    </div>
  )
}

/** 토스 결제창이 돌려보내는 failUrl 화면 (결제 실패 또는 사용자가 결제창을 닫은 경우) */
export function PaymentFailPage() {
  const { auth } = useAuth()
  const [params] = useSearchParams()
  const reported = useRef(false)

  const code = params.get('code')
  const message = params.get('message')
  const orderId = params.get('orderId')
  const userCancelled = code === 'PAY_PROCESS_CANCELED' || code === 'USER_CANCEL'

  useEffect(() => {
    if (!auth || !orderId || reported.current) return
    reported.current = true
    api.failPayment(orderId, code, message, auth).catch(() => {})
  }, [auth, orderId, code, message])

  return (
    <div className="card">
      <h2 className="section-title">{userCancelled ? '결제를 취소했습니다' : '결제에 실패했습니다'}</h2>
      {!userCancelled && message && (
        <div className="error-box" style={{ marginBottom: '1rem' }}>
          {message}
          {code && <span style={{ opacity: 0.7 }}> ({code})</span>}
        </div>
      )}
      <p className="muted-text" style={{ margin: '0 0 1rem' }}>
        장바구니는 그대로 남아 있습니다. 다시 결제하시려면 아래 버튼을 눌러주세요.
      </p>
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <Link to="/checkout" className="btn btn-primary">
          다시 결제하기
        </Link>
        <Link to="/cart" className="btn">
          장바구니
        </Link>
      </div>
    </div>
  )
}
