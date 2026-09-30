import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { StoreListing } from '../api/types'
import { ProductImage } from '../components/ProductImage'
import { CATEGORY_ICONS } from '../components/categoryIcons'
import { DIET_LABELS } from '../components/dietAllergyOptions'
import { useLayoutMode } from '../layout/useLayoutMode'
import { FavoriteButton, RatingSummary } from '../components/ProductCard'
import { ReviewSection } from '../components/ReviewSection'
import { useAddToCart } from '../lib/useAddToCart'
import { RestockAlertButton } from '../components/RestockAlertButton'

export default function ProductDetailPage() {
  const { productId } = useParams()
  const { isWeb } = useLayoutMode()
  const { auth } = useAuth()
  const navigate = useNavigate()
  const [product, setProduct] = useState<StoreListing | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<'add' | 'buy' | null>(null)
  const [loginNotice, setLoginNotice] = useState(false)
  // 내 알레르기 성분이 든 상품이면 담기 전에 확인창을 띄운다
  const { addToCart: addWithAllergyCheck, dialog: allergyDialog } = useAddToCart()

  // 리뷰를 쓰거나 고치면 제목 옆 별점 요약도 바로 맞춘다
  const updateRating = useCallback((averageRating: number | null, reviewCount: number) => {
    setProduct((prev) => (prev ? { ...prev, averageRating, reviewCount } : prev))
  }, [])

  useEffect(() => {
    if (!productId) return
    // 로그인 상태면 내 알레르기 경고/식단 일치 여부가 함께 온다
    api.getProduct(Number(productId), auth).catch(() => setError('상품을 찾을 수 없습니다.')).then((data) => {
      if (data) setProduct(data)
    })
  }, [productId, auth])

  if (error) return <div className="card"><div className="error-box">{error}</div></div>
  if (!product) return <div className="card">불러오는 중...</div>

  async function addToCart() {
    if (!auth || !product) return
    setBusy('add')
    setError(null)
    try {
      if (await addWithAllergyCheck(product.productId, quantity)) navigate('/cart')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '담지 못했습니다.')
    } finally {
      setBusy(null)
    }
  }

  async function buyNow() {
    if (!auth || !product) return
    setBusy('buy')
    setError(null)
    try {
      if (await addWithAllergyCheck(product.productId, quantity)) navigate('/checkout')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '담지 못했습니다.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
    <div className={`card product-detail${isWeb ? ' web' : ''}`}>
      <ProductImage product={product} className="product-detail-image" />
      <div>
      {isWeb && (
        <p style={{ margin: '0 0 1rem' }}>
          <Link to="/products" className="link-btn" style={{ textDecoration: 'none' }}>
            ‹ 상품 목록
          </Link>
        </p>
      )}
      <Link
        to={`/products?category=${product.category}`}
        className="tag"
        style={{ textDecoration: 'none', marginBottom: '0.5rem' }}
      >
        {CATEGORY_ICONS[product.category]} {product.categoryLabel}
      </Link>
      <div className="product-detail-title">
        <h2 style={{ margin: '0 0 0.3rem', fontSize: isWeb ? '1.6rem' : '1.1rem' }}>{product.name}</h2>
        <FavoriteButton
          key={`${product.productId}-${product.favorite}`}
          productId={product.productId}
          productName={product.name}
          initial={product.favorite === true}
          onRequireLogin={() => setLoginNotice(true)}
        />
      </div>
      {loginNotice && (
        <p style={{ margin: '0 0 0.4rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>로그인 후 찜할 수 있어요.</p>
      )}
      <a href="#reviewTitle" className="rating-link">
        <RatingSummary averageRating={product.averageRating} reviewCount={product.reviewCount} />
      </a>
      <p className="price-tag" style={{ margin: '0 0 0.6rem', fontSize: isWeb ? '1.5rem' : '1.15rem' }}>
        {product.price.toLocaleString()}원
      </p>
      {product.description && (
        <p style={{ margin: '0 0 0.85rem', color: 'var(--text-muted)', fontSize: '0.88rem' }}>{product.description}</p>
      )}
      {product.allergyWarnings.length > 0 && (
        <div className="allergy-warning-box" role="alert">
          ⚠ 회원님이 등록한 알레르기 성분({product.allergyWarnings.join(', ')})이 들어 있어요.
        </div>
      )}

      <dl className="product-facts">
        <dt>알레르기 유발 성분</dt>
        <dd>{product.allergens.length > 0 ? product.allergens.join(', ') : '없음'}</dd>
        <dt>맞는 식단</dt>
        <dd>
          {product.diets.length > 0 ? (
            <span style={{ display: 'inline-flex', flexWrap: 'wrap', gap: '0.3rem' }}>
              {product.diets.map((d) => (
                <span key={d} className="tag" style={{ fontSize: '0.72rem', padding: '0.1rem 0.5rem' }}>
                  {DIET_LABELS[d]}
                </span>
              ))}
            </span>
          ) : (
            '-'
          )}
          {product.dietMatch === true && <span className="flag flag-ok" style={{ marginLeft: '0.4rem' }}>✓ 내 식단</span>}
        </dd>
        <dt>재고</dt>
        <dd>{product.stock}개</dd>
      </dl>

      {product.stock === 0 ? (
        <>
          <span className="badge badge-high">품절된 상품입니다</span>
          <RestockAlertButton
            productId={product.productId}
            subscribed={product.restockAlert}
            onChange={(restockAlert) => setProduct((prev) => (prev ? { ...prev, restockAlert } : prev))}
            onRequireLogin={() => setLoginNotice(true)}
          />
        </>
      ) : auth ? (
        <>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.85rem' }}>
            <button className="btn" onClick={() => setQuantity((q) => Math.max(1, q - 1))}>
              -
            </button>
            <span style={{ minWidth: '1.5rem', textAlign: 'center' }}>{quantity}</span>
            <button className="btn" onClick={() => setQuantity((q) => Math.min(product.stock, q + 1))}>
              +
            </button>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button className="btn btn-block" disabled={busy !== null} onClick={addToCart}>
              장바구니 담기
            </button>
            <button className="btn btn-primary btn-block" disabled={busy !== null} onClick={buyNow}>
              바로 구매
            </button>
          </div>
        </>
      ) : (
        <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>로그인 후 구매할 수 있습니다.</p>
      )}
      {error && <div className="error-box" style={{ marginTop: '0.75rem' }}>{error}</div>}
      </div>
    </div>
    <ReviewSection productId={product.productId} onSummaryChange={updateRating} />
    {allergyDialog}
    </>
  )
}
