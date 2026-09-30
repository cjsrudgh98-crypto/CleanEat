import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { NavLink, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type {
  AdminProduct,
  AdminProductInput,
  AdminSummary,
  AuthState,
  DietType,
  OrderData,
  OrderItemData,
  OrderStatus,
  ProductCategory,
} from '../api/types'
import { STATUS_CLASS, STATUS_LABEL } from '../components/orderStatus'
import { OrderItemLine } from '../components/OrderItemLine'
import { TrackingInfo } from '../components/OrderProgress'
import { CATEGORY_LABELS } from '../components/categoryIcons'
import { COMMON_ALLERGIES, DIET_LABELS } from '../components/dietAllergyOptions'
import { AdminIngredients } from '../components/AdminIngredients'
import { AdminSales } from '../components/AdminSales'
import { appendUnique } from '../lib/paging'
import { useOrderActionDialog } from '../lib/useOrderActionDialog'

function errorMessage(err: unknown, fallback: string) {
  return err instanceof ApiError ? err.message : fallback
}

/**
 * 관리자 화면. 메뉴 노출은 auth.role로 판단하지만 실제 권한 검사는 서버(/api/admin/**)가 매 요청 한다.
 */
export default function AdminPage() {
  const { auth, isAdmin } = useAuth()
  const { tab } = useParams()
  const [summary, setSummary] = useState<AdminSummary | null>(null)

  const refreshSummary = useCallback(() => {
    if (!auth || !isAdmin) return
    api.adminSummary(auth).then(setSummary).catch(() => setSummary(null))
  }, [auth, isAdmin])

  useEffect(refreshSummary, [refreshSummary])

  if (!auth) return <div className="card">관리자 계정으로 로그인해주세요.</div>
  if (!isAdmin) return <div className="card"><div className="error-box">관리자만 볼 수 있는 화면입니다.</div></div>

  return (
    <>
      <div className="card">
        <h2 className="section-title">관리자</h2>
        {summary ? <SummaryTiles summary={summary} /> : <p className="muted-text">요약을 불러오는 중...</p>}
        <nav className="admin-tabs" aria-label="관리 메뉴">
          <NavLink to="/admin" end className={({ isActive }) => `chip${isActive ? ' chip-active' : ''}`}>
            주문 관리
          </NavLink>
          <NavLink to="/admin/products" className={({ isActive }) => `chip${isActive ? ' chip-active' : ''}`}>
            상품·재고 관리
          </NavLink>
          <NavLink to="/admin/ingredients" className={({ isActive }) => `chip${isActive ? ' chip-active' : ''}`}>
            유해성분 사전
          </NavLink>
          <NavLink to="/admin/sales" className={({ isActive }) => `chip${isActive ? ' chip-active' : ''}`}>
            매출 통계
          </NavLink>
        </nav>
      </div>
      {tab === 'products' ? (
        <ProductsAdmin auth={auth} onChanged={refreshSummary} />
      ) : tab === 'sales' ? (
        <AdminSales auth={auth} />
      ) : tab === 'ingredients' ? (
        <AdminIngredients auth={auth} />
      ) : (
        <OrdersAdmin auth={auth} onChanged={refreshSummary} />
      )}
    </>
  )
}

function SummaryTiles({ summary }: { summary: AdminSummary }) {
  const tiles = [
    { label: '배송 준비 필요', value: `${summary.paid}건`, alert: summary.paid > 0 },
    { label: '배송 준비중', value: `${summary.preparing}건`, alert: false },
    { label: '배송중', value: `${summary.shipping}건`, alert: false },
    { label: '입금 대기', value: `${summary.awaitingDeposit}건`, alert: false },
    { label: '반품 신청', value: `${summary.returnRequested}건`, alert: summary.returnRequested > 0 },
    { label: `재고 ${summary.lowStockThreshold}개 이하`, value: `${summary.lowStock}개`, alert: summary.lowStock > 0 },
    { label: '품절', value: `${summary.soldOut}개`, alert: summary.soldOut > 0 },
    { label: '오늘 매출', value: `${summary.todaySales.toLocaleString()}원`, sub: `${summary.todayOrders}건`, alert: false },
  ]
  return (
    <div className="admin-tiles">
      {tiles.map((t) => (
        <div key={t.label} className={`admin-tile${t.alert ? ' alert' : ''}`}>
          <span className="admin-tile-label">{t.label}</span>
          <strong>{t.value}</strong>
          {t.sub && <span className="admin-tile-label">{t.sub}</span>}
        </div>
      ))}
    </div>
  )
}

// ---------------- 주문 관리 ----------------

const ORDER_FILTERS: { key: string; label: string; statuses: OrderStatus[] }[] = [
  { key: 'todo', label: '배송 준비 필요', statuses: ['PAID', 'PLACED'] },
  { key: 'preparing', label: '배송 준비중', statuses: ['PREPARING'] },
  { key: 'shipping', label: '배송중', statuses: ['SHIPPING'] },
  { key: 'deposit', label: '입금 대기', statuses: ['AWAITING_DEPOSIT'] },
  { key: 'delivered', label: '배송 완료', statuses: ['DELIVERED'] },
  { key: 'returns', label: '반품 신청', statuses: ['RETURN_REQUESTED'] },
  { key: 'returned', label: '반품 완료', statuses: ['RETURNED'] },
  { key: 'cancelled', label: '취소', statuses: ['CANCELLED'] },
  { key: 'all', label: '전체', statuses: [] },
]

// 서버 OrderService.ADMIN_ITEM_CANCELABLE과 같게 유지 - 상품 한 줄만 취소 (배송 준비중까지)
const ADMIN_ITEM_CANCELABLE: OrderStatus[] = ['PLACED', 'PAID', 'PREPARING']
// 서버 OrderService.ADMIN_CANCELABLE과 같게 유지 - 발송 후(배송중~)에는 취소 대신 반품으로 처리해야 한다
const ADMIN_CANCELABLE: OrderStatus[] = ['PLACED', 'PAID', 'AWAITING_DEPOSIT', 'PREPARING']
// 서버(/api/admin/couriers)에서 못 받아오면 쓰는 기본 목록
const DEFAULT_COURIERS = ['CJ대한통운', '우체국택배', '한진택배', '롯데택배', '로젠택배', '경동택배']

function OrdersAdmin({ auth, onChanged }: { auth: AuthState; onChanged: () => void }) {
  const [filterKey, setFilterKey] = useState('todo')
  const [orders, setOrders] = useState<OrderData[] | null>(null)
  const [page, setPage] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [couriers, setCouriers] = useState<string[]>(DEFAULT_COURIERS)

  useEffect(() => {
    api.adminCouriers(auth).then(setCouriers).catch(() => {})
  }, [auth])

  const filter = ORDER_FILTERS.find((f) => f.key === filterKey) ?? ORDER_FILTERS[0]

  useEffect(() => {
    let cancelled = false
    api
      .adminOrders(filter.statuses, auth)
      .then((data) => {
        if (cancelled) return
        setOrders(data.items)
        setPage(0)
        setHasNext(data.hasNext)
        setError(null)
      })
      .catch((err) => !cancelled && setError(errorMessage(err, '주문 목록을 불러오지 못했습니다.')))
    return () => {
      cancelled = true
    }
  }, [auth, filter])

  async function loadMore() {
    setLoadingMore(true)
    try {
      const data = await api.adminOrders(filter.statuses, auth, page + 1)
      setOrders((prev) => appendUnique(prev ?? [], data.items))
      setPage(data.page)
      setHasNext(data.hasNext)
    } catch (err) {
      setError(errorMessage(err, '주문 목록을 더 불러오지 못했습니다.'))
    } finally {
      setLoadingMore(false)
    }
  }

  function replaceOrder(updated: OrderData) {
    setOrders((prev) => prev?.map((o) => (o.id === updated.id ? updated : o)) ?? null)
    onChanged()
  }

  return (
    <div className="card">
      <div className="chip-row">
        {ORDER_FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            className={`chip${f.key === filterKey ? ' chip-active' : ''}`}
            onClick={() => {
              setOrders(null)
              setFilterKey(f.key)
            }}
          >
            {f.label}
          </button>
        ))}
      </div>
      {error && <div className="error-box">{error}</div>}
      {!error && orders === null && <p className="muted-text">불러오는 중...</p>}
      {orders?.length === 0 && <p className="muted-text">해당하는 주문이 없습니다.</p>}
      <ul className="admin-list">
        {orders?.map((o) => (
          <AdminOrderRow key={o.id} order={o} auth={auth} couriers={couriers} onUpdated={replaceOrder} />
        ))}
      </ul>
      {hasNext && (
        <button className="btn" type="button" style={{ width: '100%' }} disabled={loadingMore} onClick={loadMore}>
          {loadingMore ? '불러오는 중...' : '더 보기'}
        </button>
      )}
    </div>
  )
}

function AdminOrderRow({
  order,
  auth,
  couriers,
  onUpdated,
}: {
  order: OrderData
  auth: AuthState
  couriers: string[]
  onUpdated: (order: OrderData) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [courier, setCourier] = useState(order.courier ?? couriers[0])
  const [trackingNumber, setTrackingNumber] = useState(order.trackingNumber ?? '')
  // 입금 확인 결과 안내 (아직 입금 전일 때)
  const [notice, setNotice] = useState<string | null>(null)
  // 배송중 주문의 운송장 수정 입력창 열림 여부
  const [editingTracking, setEditingTracking] = useState(false)
  const courierOptions = couriers.includes(courier) ? couriers : [courier, ...couriers]
  const { ask, dialog: actionDialog } = useOrderActionDialog()

  // 성공하면 true (실패하면 에러를 보여주고 false)
  async function run(action: () => Promise<OrderData>) {
    setBusy(true)
    setError(null)
    try {
      onUpdated(await action())
      return true
    } catch (err) {
      setError(errorMessage(err, '처리하지 못했습니다.'))
      return false
    } finally {
      setBusy(false)
    }
  }

  function advance(status: OrderStatus) {
    if (status === 'SHIPPING' && !trackingNumber.trim()) {
      setError('운송장 번호를 입력해주세요.')
      return
    }
    run(() =>
      api.adminAdvanceOrder(
        order.id,
        status,
        status === 'SHIPPING' ? courier : null,
        status === 'SHIPPING' ? trackingNumber.trim() : null,
        auth,
      ),
    )
  }

  // 웹훅/10분 주기 확인을 기다리지 않고 지금 토스에 입금 여부를 묻는다
  async function syncPayment() {
    setNotice(null)
    let stillWaiting: boolean = false
    const ok = await run(async () => {
      const updated = await api.adminSyncPayment(order.id, auth)
      stillWaiting = updated.status === 'AWAITING_DEPOSIT'
      return updated
    })
    if (ok && stillWaiting) setNotice('아직 입금되지 않았습니다.')
  }

  async function saveTracking() {
    if (!trackingNumber.trim()) {
      setError('운송장 번호를 입력해주세요.')
      return
    }
    if (await run(() => api.adminUpdateTracking(order.id, courier, trackingNumber.trim(), auth))) {
      setEditingTracking(false)
    }
  }

  const trackingInputs = (
    <>
      <select aria-label="택배사" value={courier} onChange={(e) => setCourier(e.target.value)} style={{ width: 'auto' }}>
        {courierOptions.map((c) => (
          <option key={c}>{c}</option>
        ))}
      </select>
      <input
        type="text"
        aria-label="운송장 번호"
        placeholder="운송장 번호"
        value={trackingNumber}
        onChange={(e) => setTrackingNumber(e.target.value)}
        style={{ width: '11rem' }}
      />
    </>
  )

  async function cancel() {
    // 취소 사유 + (입금이 끝난 가상계좌 주문이면) 고객에게 받은 환불 계좌를 한 폼에서 받는다
    const answer = await ask(order, 'adminCancel')
    if (!answer) return
    run(() => api.adminCancelOrder(order.id, answer.reason ?? '', auth, answer.refundAccount))
  }

  // 상품 한 줄만 취소 (품절/파손 등) - 사유 + 가상계좌면 고객 환불 계좌. 남은 상품이 하나면 버튼을 안 보인다 (주문 취소로)
  async function cancelItem(item: OrderItemData) {
    const answer = await ask(order, 'adminCancelItem', item)
    if (!answer) return
    run(() => api.adminCancelOrderItem(order.id, item.id, answer.reason ?? '', auth, answer.refundAccount))
  }
  const itemCancelable = ADMIN_ITEM_CANCELABLE.includes(order.status)
    && order.items.filter((i) => !i.cancelled).length > 1

  // 반품 승인: 입력 없이 확인 폼 (고객 사유, 환불 금액/계좌 안내) -> 토스 환불 + 재고 복구
  async function approveReturn() {
    if (!(await ask(order, 'approveReturn'))) return
    run(() => api.adminApproveReturn(order.id, auth))
  }

  async function rejectReturn() {
    const answer = await ask(order, 'rejectReturn')
    if (!answer) return
    run(() => api.adminRejectReturn(order.id, answer.reason ?? '', auth))
  }

  return (
    <li className="admin-row">
      {actionDialog}
      <div className="admin-row-head">
        <span className={`badge ${STATUS_CLASS[order.status]}`}>{STATUS_LABEL[order.status]}</span>
        <strong>#{order.id}</strong>
        <span className="muted-text">
          {order.customerNickname ?? '-'} · {new Date(order.orderedAt).toLocaleString('ko-KR')}
        </span>
        <span className="price-tag" style={{ marginLeft: 'auto' }}>
          {order.netAmount.toLocaleString()}원
          {order.cancelledAmount > 0 && (
            <span className="muted-text" style={{ display: 'block', fontSize: '0.72rem', fontWeight: 400 }}>
              부분 취소 -{order.cancelledAmount.toLocaleString()}원
            </span>
          )}
        </span>
      </div>
      <ul style={{ margin: '0.4rem 0 0.2rem', paddingLeft: '1.1rem' }}>
        {order.items.map((item) => (
          <OrderItemLine key={item.id} item={item} busy={busy}
                         onCancel={itemCancelable ? () => cancelItem(item) : undefined} />
        ))}
      </ul>
      <p className="muted-text" style={{ margin: 0 }}>
        {order.recipientName} · {order.phone} · {order.address}
        {order.requestNote && <> · 요청: {order.requestNote}</>}
      </p>
      <p className="muted-text" style={{ margin: '0.15rem 0 0' }}>
        {order.paymentMethod}
        {order.paidAt && <> · 결제 {new Date(order.paidAt).toLocaleString('ko-KR')}</>}
        {order.status === 'CANCELLED' && order.failReason && <> · 취소 사유: {order.failReason}</>}
      </p>
      <TrackingInfo order={order} />
      {order.status === 'RETURN_REQUESTED' && (
        <div className="notice-box" style={{ marginTop: '0.4rem' }}>
          <strong>반품 신청</strong>
          {order.returnRequestedAt && <> · {new Date(order.returnRequestedAt).toLocaleString('ko-KR')}</>}
          <br />사유: {order.returnReason}
          {order.returnRefundAccountSummary && <><br />환불 계좌: {order.returnRefundAccountSummary}</>}
        </div>
      )}
      {order.status === 'RETURNED' && order.returnedAt && (
        <p className="muted-text" style={{ margin: '0.15rem 0 0' }}>
          반품 완료(환불) {new Date(order.returnedAt).toLocaleString('ko-KR')} · 사유: {order.returnReason}
        </p>
      )}
      {order.status === 'DELIVERED' && order.returnRejectReason && (
        <p className="muted-text" style={{ margin: '0.15rem 0 0' }}>반품 거절 · 사유: {order.returnRejectReason}</p>
      )}
      {order.status === 'AWAITING_DEPOSIT' && (
        <p className="muted-text" style={{ margin: '0.15rem 0 0' }}>
          {order.virtualAccountBank} {order.virtualAccountNumber}
          {order.virtualAccountDueDate && <> · {new Date(order.virtualAccountDueDate).toLocaleString('ko-KR')}까지 입금</>}
        </p>
      )}
      {notice && <p style={{ margin: '0.4rem 0 0', fontSize: '0.8rem', color: 'var(--medium-fg)' }}>{notice}</p>}

      <div className="admin-actions">
        {(order.status === 'PAID' || order.status === 'PLACED') && (
          <button type="button" className="btn btn-primary" disabled={busy} onClick={() => advance('PREPARING')}>
            배송 준비 시작
          </button>
        )}
        {order.status === 'AWAITING_DEPOSIT' && (
          <button type="button" className="btn" disabled={busy} onClick={syncPayment}>
            입금 확인
          </button>
        )}
        {order.status === 'PREPARING' && (
          <>
            {trackingInputs}
            <button type="button" className="btn btn-primary" disabled={busy} onClick={() => advance('SHIPPING')}>
              발송 처리
            </button>
          </>
        )}
        {order.status === 'SHIPPING' && !editingTracking && (
          <>
            <button type="button" className="btn btn-primary" disabled={busy} onClick={() => advance('DELIVERED')}>
              배송 완료 처리
            </button>
            <button type="button" className="btn" disabled={busy} onClick={() => setEditingTracking(true)}>
              운송장 수정
            </button>
          </>
        )}
        {order.status === 'SHIPPING' && editingTracking && (
          <>
            {trackingInputs}
            <button type="button" className="btn btn-primary" disabled={busy} onClick={saveTracking}>
              저장
            </button>
            <button
              type="button"
              className="btn"
              onClick={() => {
                setEditingTracking(false)
                setCourier(order.courier ?? couriers[0])
                setTrackingNumber(order.trackingNumber ?? '')
                setError(null)
              }}
            >
              취소
            </button>
          </>
        )}
        {order.status === 'RETURN_REQUESTED' && (
          <>
            <button type="button" className="btn btn-primary" disabled={busy} onClick={approveReturn}>
              반품 승인(환불)
            </button>
            <button type="button" className="btn btn-danger" disabled={busy} onClick={rejectReturn}>
              반품 거절
            </button>
          </>
        )}
        {ADMIN_CANCELABLE.includes(order.status) && (
          <button type="button" className="btn btn-danger" disabled={busy} onClick={cancel}>
            주문 취소
          </button>
        )}
      </div>
      {error && <div className="error-box" style={{ marginTop: '0.5rem' }}>{error}</div>}
    </li>
  )
}

// ---------------- 상품 / 재고 관리 ----------------

type StockFilter = 'all' | 'low' | 'soldout'

const CATEGORIES = Object.keys(CATEGORY_LABELS) as ProductCategory[]
const SELECTABLE_DIETS = (Object.keys(DIET_LABELS) as DietType[]).filter((d) => d !== 'NONE')

function ProductsAdmin({ auth, onChanged }: { auth: AuthState; onChanged: () => void }) {
  const [products, setProducts] = useState<AdminProduct[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [stockFilter, setStockFilter] = useState<StockFilter>('all')
  // null: 폼 닫힘 / 'new': 새 상품 / 상품: 수정
  const [editing, setEditing] = useState<AdminProduct | 'new' | null>(null)

  useEffect(() => {
    api
      .adminProducts(auth)
      .then(setProducts)
      .catch((err) => setError(errorMessage(err, '상품 목록을 불러오지 못했습니다.')))
  }, [auth])

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (products ?? []).filter((p) => {
      if (stockFilter === 'low' && !(p.stock > 0 && p.stock <= 5)) return false
      if (stockFilter === 'soldout' && p.stock > 0) return false
      return !q || p.name.toLowerCase().includes(q) || p.barcode.includes(q)
    })
  }, [products, query, stockFilter])

  function upsert(saved: AdminProduct) {
    setProducts((prev) => {
      if (!prev) return [saved]
      return prev.some((p) => p.productId === saved.productId)
        ? prev.map((p) => (p.productId === saved.productId ? saved : p))
        : [saved, ...prev]
    })
    onChanged()
  }

  return (
    <>
      {editing && (
        <ProductForm
          key={editing === 'new' ? 'new' : editing.productId}
          auth={auth}
          product={editing === 'new' ? null : editing}
          onSaved={(saved) => {
            upsert(saved)
            setEditing(null)
          }}
          onClose={() => setEditing(null)}
        />
      )}

      <div className="card">
        <div className="admin-toolbar">
          <input
            type="search"
            placeholder="상품명 또는 바코드 검색"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <select value={stockFilter} onChange={(e) => setStockFilter(e.target.value as StockFilter)} aria-label="재고 필터">
            <option value="all">전체 재고</option>
            <option value="low">재고 5개 이하</option>
            <option value="soldout">품절</option>
          </select>
          <button type="button" className="btn btn-primary" onClick={() => setEditing('new')}>
            + 상품 등록
          </button>
        </div>

        {error && <div className="error-box">{error}</div>}
        {!error && products === null && <p className="muted-text">불러오는 중...</p>}
        {products && visible.length === 0 && <p className="muted-text">해당하는 상품이 없습니다.</p>}
        <ul className="admin-list">
          {visible.map((p) => (
            <AdminProductRow key={p.productId} product={p} auth={auth} onSaved={upsert} onEdit={() => setEditing(p)} />
          ))}
        </ul>
      </div>
    </>
  )
}

function AdminProductRow({
  product,
  auth,
  onSaved,
  onEdit,
}: {
  product: AdminProduct
  auth: AuthState
  onSaved: (p: AdminProduct) => void
  onEdit: () => void
}) {
  const [delta, setDelta] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const stockClass = product.stock <= 0 ? 'badge-high' : product.stock <= 5 ? 'badge-medium' : 'badge-low'

  async function adjust(event: FormEvent) {
    event.preventDefault()
    const value = Number(delta)
    if (!Number.isInteger(value) || value === 0) {
      setError('늘릴 수량은 +10, 줄일 수량은 -2처럼 0이 아닌 정수로 입력해주세요.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      onSaved(await api.adminAdjustStock(product.productId, value, auth))
      setDelta('')
    } catch (err) {
      setError(errorMessage(err, '재고를 바꾸지 못했습니다.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className="admin-row">
      <div className="admin-row-head">
        <strong>{product.name}</strong>
        <span className="muted-text">
          {product.categoryLabel} · {product.barcode}
        </span>
        <span className="price-tag" style={{ marginLeft: 'auto' }}>
          {product.price.toLocaleString()}원
        </span>
      </div>
      <form className="admin-actions" onSubmit={adjust}>
        <span className={`badge ${stockClass}`}>{product.stock <= 0 ? '품절' : `재고 ${product.stock}개`}</span>
        <input
          type="number"
          step={1}
          aria-label={`${product.name} 재고 증감`}
          placeholder="+10 / -2"
          value={delta}
          onChange={(e) => setDelta(e.target.value)}
          style={{ width: '7rem' }}
        />
        <button type="submit" className="btn" disabled={busy || !delta}>
          재고 반영
        </button>
        <button type="button" className="btn" onClick={onEdit} style={{ marginLeft: 'auto' }}>
          정보 수정
        </button>
      </form>
      {error && <div className="error-box" style={{ marginTop: '0.5rem' }}>{error}</div>}
    </li>
  )
}

function toInput(product: AdminProduct | null): AdminProductInput {
  return {
    name: product?.name ?? '',
    barcode: product?.barcode ?? '',
    price: product?.price ?? 0,
    stock: product?.stock ?? 0,
    category: product?.category ?? 'SNACK',
    imageUrl: product?.imageUrl ?? '',
    description: product?.description ?? '',
    rawIngredientsText: product?.rawIngredientsText ?? '',
    allergens: product?.allergens ?? [],
    diets: product?.diets ?? [],
  }
}

function ProductForm({
  auth,
  product,
  onSaved,
  onClose,
}: {
  auth: AuthState
  product: AdminProduct | null
  onSaved: (p: AdminProduct) => void
  onClose: () => void
}) {
  const [form, setForm] = useState<AdminProductInput>(() => toInput(product))
  const [extraAllergen, setExtraAllergen] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function set<K extends keyof AdminProductInput>(key: K, value: AdminProductInput[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function toggle<T>(list: T[], value: T) {
    return list.includes(value) ? list.filter((v) => v !== value) : [...list, value]
  }

  function addExtraAllergen() {
    const name = extraAllergen.trim()
    if (name && !form.allergens.includes(name)) set('allergens', [...form.allergens, name])
    setExtraAllergen('')
  }

  // 목록에 없는 알레르기(직접 추가한 것)도 칩으로 보여서 뺄 수 있게 한다
  const allergenOptions = [...COMMON_ALLERGIES, ...form.allergens.filter((a) => !COMMON_ALLERGIES.includes(a))]

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const saved = product
        ? await api.adminUpdateProduct(product.productId, form, auth)
        : await api.adminCreateProduct(form, auth)
      onSaved(saved)
    } catch (err) {
      setError(errorMessage(err, '저장하지 못했습니다.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="card admin-form" onSubmit={submit}>
      <h2 className="section-title">{product ? '상품 정보 수정' : '새 상품 등록'}</h2>

      <div className="admin-form-grid">
        <div>
          <label htmlFor="ap-name">상품명</label>
          <input id="ap-name" type="text" required maxLength={200} value={form.name} onChange={(e) => set('name', e.target.value)} />
        </div>
        <div>
          <label htmlFor="ap-barcode">바코드</label>
          <input
            id="ap-barcode"
            type="text"
            required
            maxLength={50}
            value={form.barcode}
            onChange={(e) => set('barcode', e.target.value)}
          />
        </div>
        <div>
          <label htmlFor="ap-category">카테고리</label>
          <select id="ap-category" value={form.category} onChange={(e) => set('category', e.target.value as ProductCategory)}>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABELS[c]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="ap-price">가격 (원)</label>
          <input
            id="ap-price"
            type="number"
            required
            min={0}
            step={10}
            value={form.price}
            onChange={(e) => set('price', Number(e.target.value))}
          />
        </div>
        {!product && (
          <div>
            <label htmlFor="ap-stock">처음 재고</label>
            <input
              id="ap-stock"
              type="number"
              required
              min={0}
              step={1}
              value={form.stock}
              onChange={(e) => set('stock', Number(e.target.value))}
            />
          </div>
        )}
        <div>
          <label htmlFor="ap-image">이미지 주소</label>
          <input
            id="ap-image"
            type="text"
            maxLength={500}
            placeholder="/images/products/바코드.jpg 또는 https://..."
            value={form.imageUrl}
            onChange={(e) => set('imageUrl', e.target.value)}
          />
        </div>
      </div>

      <label htmlFor="ap-desc">상품 설명</label>
      <textarea id="ap-desc" rows={2} maxLength={500} value={form.description} onChange={(e) => set('description', e.target.value)} />

      <label htmlFor="ap-raw">원재료명</label>
      <textarea
        id="ap-raw"
        rows={3}
        maxLength={2000}
        value={form.rawIngredientsText}
        onChange={(e) => set('rawIngredientsText', e.target.value)}
      />

      <label>알레르기 유발 성분</label>
      <div className="admin-chip-wrap">
        {allergenOptions.map((a) => (
          <button
            key={a}
            type="button"
            className={`chip${form.allergens.includes(a) ? ' chip-danger' : ''}`}
            onClick={() => set('allergens', toggle(form.allergens, a))}
          >
            {a}
          </button>
        ))}
        <input
          type="text"
          aria-label="알레르기 성분 직접 추가"
          placeholder="직접 추가 후 Enter"
          value={extraAllergen}
          maxLength={30}
          onChange={(e) => setExtraAllergen(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              addExtraAllergen()
            }
          }}
          style={{ width: '10rem' }}
        />
      </div>

      <label>맞는 식단</label>
      <div className="admin-chip-wrap">
        {SELECTABLE_DIETS.map((d) => (
          <button
            key={d}
            type="button"
            className={`chip${form.diets.includes(d) ? ' chip-active' : ''}`}
            onClick={() => set('diets', toggle(form.diets, d))}
          >
            {DIET_LABELS[d]}
          </button>
        ))}
      </div>

      {product && (
        <p className="muted-text" style={{ margin: '0 0 0.75rem' }}>
          재고는 목록의 "재고 반영"으로 바꿔주세요. 판매를 멈추려면 재고를 0으로 만들면 됩니다.
        </p>
      )}
      {error && <div className="error-box" style={{ marginBottom: '0.75rem' }}>{error}</div>}
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? '저장 중...' : product ? '수정 저장' : '등록'}
        </button>
        <button type="button" className="btn" onClick={onClose}>
          닫기
        </button>
      </div>
    </form>
  )
}
