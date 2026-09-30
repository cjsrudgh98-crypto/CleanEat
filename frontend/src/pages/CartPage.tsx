import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { CartData } from '../api/types'

export default function CartPage() {
  const { auth } = useAuth()
  const navigate = useNavigate()
  const [cart, setCart] = useState<CartData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  useEffect(() => {
    if (!auth) return
    api.getCart(auth).then(setCart).catch(() => setError('장바구니를 불러오지 못했습니다.'))
  }, [auth])

  if (!auth) {
    return <div className="card">로그인 후 장바구니를 확인할 수 있습니다.</div>
  }

  async function changeQuantity(cartItemId: number, quantity: number) {
    if (!auth || quantity < 1) return
    setBusyId(cartItemId)
    setError(null)
    try {
      setCart(await api.updateCartItem(cartItemId, quantity, auth))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '수량을 변경하지 못했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  async function removeItem(cartItemId: number) {
    if (!auth) return
    setBusyId(cartItemId)
    setError(null)
    try {
      setCart(await api.removeCartItem(cartItemId, auth))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '삭제하지 못했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="card">
      <h2 style={{ margin: '0 0 1rem', fontSize: '1.05rem' }}>장바구니</h2>
      {error && <div className="error-box" style={{ marginBottom: '0.75rem' }}>{error}</div>}

      {!cart || cart.items.length === 0 ? (
        <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.85rem' }}>장바구니가 비어 있습니다.</p>
      ) : (
        <>
          <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {cart.items.map((item) => (
              <li
                key={item.cartItemId}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: '0.75rem',
                  borderBottom: '1px solid var(--border)',
                  paddingBottom: '0.75rem',
                }}
              >
                <div>
                  <b style={{ fontSize: '0.9rem' }}>{item.productName}</b>
                  <p style={{ margin: '0.2rem 0 0', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                    {item.unitPrice.toLocaleString()}원
                  </p>
                  {item.allergyWarnings.length > 0 && (
                    <span className="cart-allergy-warning">⚠ {item.allergyWarnings.join('·')} 함유 (내 알레르기)</span>
                  )}
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                  <button
                    className="btn"
                    style={{ padding: '0.3rem 0.6rem' }}
                    disabled={busyId === item.cartItemId}
                    onClick={() => changeQuantity(item.cartItemId, item.quantity - 1)}
                  >
                    -
                  </button>
                  <span style={{ minWidth: '1.5rem', textAlign: 'center' }}>{item.quantity}</span>
                  <button
                    className="btn"
                    style={{ padding: '0.3rem 0.6rem' }}
                    disabled={busyId === item.cartItemId || item.quantity >= item.availableStock}
                    onClick={() => changeQuantity(item.cartItemId, item.quantity + 1)}
                  >
                    +
                  </button>
                  <button
                    className="icon-btn"
                    aria-label="삭제"
                    disabled={busyId === item.cartItemId}
                    onClick={() => removeItem(item.cartItemId)}
                  >
                    ×
                  </button>
                </div>
              </li>
            ))}
          </ul>

          <div style={{ display: 'flex', justifyContent: 'space-between', margin: '1rem 0' }}>
            <b>총 금액</b>
            <b>{cart.totalAmount.toLocaleString()}원</b>
          </div>

          <button className="btn btn-primary btn-block" onClick={() => navigate('/checkout')}>
            주문하기
          </button>
        </>
      )}
    </div>
  )
}
