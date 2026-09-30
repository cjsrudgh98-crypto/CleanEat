import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

/**
 * 품절 상품의 "재입고 알림 받기". 신청하면 재고가 생겼을 때 가입 이메일로 한 번 알려준다.
 * subscribed: 서버가 알려준 신청 여부 (비로그인이면 null)
 */
export function RestockAlertButton({
  productId,
  subscribed,
  onChange,
  onRequireLogin,
}: {
  productId: number
  subscribed: boolean | null
  onChange: (subscribed: boolean) => void
  onRequireLogin: () => void
}) {
  const { auth } = useAuth()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function toggle() {
    if (!auth) {
      onRequireLogin()
      return
    }
    setBusy(true)
    setError(null)
    try {
      if (subscribed) await api.unsubscribeRestock(productId, auth)
      else await api.subscribeRestock(productId, auth)
      onChange(!subscribed)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '알림을 신청하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="restock-alert">
      {subscribed ? (
        <>
          <p className="restock-alert-note">✓ 재입고 알림을 신청했어요. 입고되면 이메일로 한 번 알려드려요.</p>
          <button type="button" className="btn" disabled={busy} onClick={toggle}>
            알림 취소
          </button>
        </>
      ) : (
        <button type="button" className="btn btn-primary btn-block" disabled={busy} onClick={toggle}>
          재입고 알림 받기
        </button>
      )}
      {error && (
        <div className="error-box" role="alert" style={{ marginTop: '0.5rem' }}>
          {error}
          {error.includes('이메일') && (
            <> <Link to="/mypage">마이페이지로 가기</Link></>
          )}
        </div>
      )}
    </div>
  )
}
