import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ANONYMOUS, loadTossPayments, type TossPaymentsWidgets } from '@tosspayments/tosspayments-sdk'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { CartData } from '../api/types'
import { AddressSearchButton, type SelectedAddress } from '../components/AddressSearch'

/**
 * 주문/결제 화면.
 *  1) 배송 정보 입력 + 토스 결제위젯(결제수단/약관) 표시
 *  2) "결제하기" -> 서버에 결제 대기 주문서 생성 -> 토스 결제창 호출
 *  3) 결제창이 끝나면 토스가 /payments/success 또는 /payments/fail 로 돌려보낸다
 */
export default function CheckoutPage() {
  const { auth } = useAuth()
  const [cart, setCart] = useState<CartData | null>(null)
  const [recipientName, setRecipientName] = useState('')
  const [phone, setPhone] = useState('')
  // 우편번호/기본주소는 주소 검색으로만 채우고, 상세주소(동·호수 등)만 직접 입력한다
  const [zonecode, setZonecode] = useState('')
  const [baseAddress, setBaseAddress] = useState('')
  const [detailAddress, setDetailAddress] = useState('')
  const detailAddressRef = useRef<HTMLInputElement>(null)
  const [requestNote, setRequestNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [widgetError, setWidgetError] = useState<string | null>(null)
  const [widgetReady, setWidgetReady] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const widgetsRef = useRef<TossPaymentsWidgets | null>(null)
  // 개발 모드(StrictMode)에서 effect가 두 번 실행돼도 위젯은 한 번만 그리도록
  const widgetInitStarted = useRef(false)

  useEffect(() => {
    if (!auth) return
    api.getCart(auth).then(setCart).catch(() => setError('장바구니를 불러오지 못했습니다.'))
  }, [auth])

  // 주소 검색 창 effect의 의존성이라 매 렌더마다 새 함수가 되지 않게 고정
  const handleAddressSelect = useCallback((selected: SelectedAddress) => {
    setZonecode(selected.zonecode)
    setBaseAddress(selected.address)
    setError(null)
    // 검색 창이 닫힌 뒤 바로 상세주소를 입력할 수 있게
    setTimeout(() => detailAddressRef.current?.focus(), 0)
  }, [])

  const totalAmount = cart?.totalAmount ?? 0
  const hasItems = !!cart && cart.items.length > 0

  // 장바구니를 불러온 뒤 결제위젯(결제수단 선택 + 약관)을 렌더링
  useEffect(() => {
    if (!hasItems || widgetInitStarted.current) return
    widgetInitStarted.current = true
    ;(async () => {
      try {
        const { clientKey } = await api.getPaymentConfig()
        const tossPayments = await loadTossPayments(clientKey)
        // 비회원 결제 방식(ANONYMOUS) - 카드 자동결제(빌링)를 쓰지 않으므로 고객 키가 필요 없다
        const widgets = tossPayments.widgets({ customerKey: ANONYMOUS })
        await widgets.setAmount({ currency: 'KRW', value: totalAmount })
        await Promise.all([
          widgets.renderPaymentMethods({ selector: '#toss-payment-method' }),
          widgets.renderAgreement({ selector: '#toss-agreement' }),
        ])
        widgetsRef.current = widgets
        setWidgetReady(true)
      } catch {
        setWidgetError('결제 모듈을 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.')
      }
    })()
  }, [hasItems, totalAmount])

  if (!auth) {
    return <div className="card">로그인 후 주문할 수 있습니다.</div>
  }

  if (cart && cart.items.length === 0) {
    return (
      <div className="card">
        <p style={{ margin: '0 0 0.75rem' }}>장바구니가 비어 있습니다.</p>
        <Link to="/products" className="btn btn-primary">
          상품 보러 가기
        </Link>
      </div>
    )
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!auth || !widgetsRef.current) return
    if (!recipientName.trim() || !phone.trim()) {
      setError('수령인과 연락처를 입력해주세요.')
      return
    }
    if (!baseAddress) {
      setError('주소 검색으로 배송지를 선택해주세요.')
      return
    }
    // 서버에는 한 줄로 저장: "[우편번호] 기본주소 상세주소"
    const address = `[${zonecode}] ${baseAddress}${detailAddress.trim() ? ' ' + detailAddress.trim() : ''}`

    setSubmitting(true)
    let tossOrderId: string | null = null
    try {
      // 1) 서버에 결제 대기 주문서를 만든다 - 금액은 서버가 장바구니 기준으로 계산한 값을 쓴다
      const order = await api.createOrder(
        { recipientName: recipientName.trim(), phone: phone.trim(), address, requestNote },
        auth,
      )
      tossOrderId = order.tossOrderId
      if (!order.tossOrderId || !order.orderName) throw new Error('주문 정보가 올바르지 않습니다.')

      // 장바구니 화면을 연 뒤 다른 탭에서 수량을 바꿨을 수 있으므로 서버 금액으로 다시 맞춘다
      await widgetsRef.current.setAmount({ currency: 'KRW', value: order.totalAmount })

      // 2) 토스 결제창 - 끝나면 successUrl/failUrl로 페이지가 이동한다
      await widgetsRef.current.requestPayment({
        orderId: order.tossOrderId,
        orderName: order.orderName,
        successUrl: `${window.location.origin}/payments/success`,
        failUrl: `${window.location.origin}/payments/fail`,
        customerName: recipientName.trim(),
      })
    } catch (err) {
      // 약관 미동의, 결제수단 미선택 등은 결제창이 뜨기 전에 여기로 온다
      const message =
        err instanceof ApiError ? err.message : err instanceof Error ? err.message : '결제를 시작하지 못했습니다.'
      setError(message)
      if (tossOrderId) api.failPayment(tossOrderId, null, message, auth).catch(() => {})
      setSubmitting(false)
    }
  }

  return (
    <>
      {cart && (
        <div className="card">
          <h2 className="section-title">주문 상품</h2>
          <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
            {cart.items.map((item) => (
              <li key={item.cartItemId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                <span>
                  {item.productName} × {item.quantity}
                  {item.allergyWarnings.length > 0 && (
                    <span className="cart-allergy-warning" style={{ marginLeft: '0.4rem' }}>
                      ⚠ {item.allergyWarnings.join('·')}
                    </span>
                  )}
                </span>
                <span>{(item.unitPrice * item.quantity).toLocaleString()}원</span>
              </li>
            ))}
          </ul>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '0.6rem', fontWeight: 800 }}>
            <span>총 결제금액</span>
            <span className="price-tag">{cart.totalAmount.toLocaleString()}원</span>
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit} style={{ display: 'contents' }}>
        <div className="card">
          <h2 className="section-title">배송 정보</h2>
          <label htmlFor="recipientName">수령인</label>
          <input
            id="recipientName"
            type="text"
            autoComplete="name"
            value={recipientName}
            onChange={(e) => setRecipientName(e.target.value)}
            style={{ marginBottom: '0.85rem' }}
          />
          <label htmlFor="phone">연락처</label>
          <input
            id="phone"
            type="tel"
            autoComplete="tel"
            placeholder="010-0000-0000"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            style={{ marginBottom: '0.85rem' }}
          />
          <label htmlFor="zonecode">배송지 주소</label>
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <input
              id="zonecode"
              type="text"
              placeholder="우편번호"
              value={zonecode}
              readOnly
              style={{ flex: 1, minWidth: 0 }}
            />
            <AddressSearchButton onSelect={handleAddressSelect} />
          </div>
          <input
            type="text"
            aria-label="기본 주소"
            placeholder="'주소 검색'을 눌러 주소를 선택해주세요"
            value={baseAddress}
            readOnly
            style={{ marginBottom: '0.5rem' }}
          />
          <input
            ref={detailAddressRef}
            type="text"
            aria-label="상세 주소"
            placeholder="상세 주소 (동·호수 등)"
            autoComplete="address-line2"
            value={detailAddress}
            onChange={(e) => setDetailAddress(e.target.value)}
            disabled={!baseAddress}
            maxLength={80}
            style={{ marginBottom: '0.85rem' }}
          />
          <label htmlFor="requestNote">배송 요청사항 (선택)</label>
          <input
            id="requestNote"
            type="text"
            value={requestNote}
            onChange={(e) => setRequestNote(e.target.value)}
          />
        </div>

        <div className="card" style={{ padding: '1rem 0.5rem' }}>
          <h2 className="section-title" style={{ paddingLeft: '0.75rem' }}>
            결제 수단
          </h2>
          {/* 토스 결제위젯이 이 두 영역에 결제수단 선택 UI와 약관 동의 UI를 그린다 */}
          <div id="toss-payment-method" />
          <div id="toss-agreement" />
          {!widgetReady && !widgetError && (
            <div className="status-row" style={{ padding: '0 0.75rem' }}>
              <span className="spinner" />
              <span>결제 모듈을 불러오는 중...</span>
            </div>
          )}
          {widgetError && (
            <div className="error-box" style={{ margin: '0 0.75rem' }}>
              {widgetError}
            </div>
          )}
        </div>

        <div>
          <button type="submit" className="btn btn-primary btn-block" disabled={submitting || !widgetReady}>
            {submitting ? '결제창을 여는 중...' : `${totalAmount.toLocaleString()}원 결제하기`}
          </button>
          <p style={{ margin: '0.6rem 0 0', fontSize: '0.78rem', color: 'var(--text-muted)' }}>
            토스페이먼츠 테스트 환경입니다 - 결제해도 실제로 돈이 빠져나가지 않습니다.
          </p>
          {error && (
            <div className="error-box" style={{ marginTop: '0.6rem' }}>
              {error}
            </div>
          )}
        </div>
      </form>
    </>
  )
}
