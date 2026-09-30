import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { StoreListing } from '../api/types'
import { ProductImage } from './ProductImage'

const LOW_STOCK_THRESHOLD = 10

export function ProductGrid({
  products,
  columns,
  onFavoriteChange,
}: {
  products: StoreListing[]
  columns: number
  onFavoriteChange?: (productId: number, favorite: boolean) => void
}) {
  return (
    <div className="product-grid" style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
      {products.map((p) => (
        <ProductCard key={p.productId} product={p} onFavoriteChange={onFavoriteChange} />
      ))}
    </div>
  )
}

export function ProductCard({
  product: p,
  onFavoriteChange,
}: {
  product: StoreListing
  onFavoriteChange?: (productId: number, favorite: boolean) => void
}) {
  const soldOut = p.stock === 0
  const warning = p.allergyWarnings.length > 0
  const favoritable = p.favorite !== null
  return (
    // 찜 버튼은 링크 밖에 겹쳐 둔다 (링크 안에 버튼을 넣으면 잘못된 HTML이고, 누르면 상세로 이동해 버림)
    <div className={`product-card-shell${favoritable ? ' favoritable' : ''}`}>
      <Link
        to={`/products/${p.productId}`}
        className={`card card-interactive product-card${soldOut ? ' sold-out' : ''}${warning ? ' has-warning' : ''}`}
      >
        <div className="product-card-media">
          <ProductImage product={p} className="product-card-image" />
          {(warning || p.dietMatch) && (
            <div className="product-flags">
              {warning && <span className="flag flag-danger">⚠ {p.allergyWarnings.join('·')} 함유</span>}
              {!warning && p.dietMatch && <span className="flag flag-ok">✓ 내 식단</span>}
            </div>
          )}
        </div>
        <p className="product-name clamp-2">{p.name}</p>
        {p.description && <p className="clamp-2 product-desc">{p.description}</p>}
        <RatingSummary averageRating={p.averageRating} reviewCount={p.reviewCount} compact />
        <div className="product-card-footer">
          <span className="price-tag">{p.price.toLocaleString()}원</span>
          {soldOut ? (
            <span className="badge badge-high">품절</span>
          ) : (
            p.stock <= LOW_STOCK_THRESHOLD && <span className="badge badge-medium">{p.stock}개 남음</span>
          )}
        </div>
      </Link>
      {favoritable && (
        <FavoriteButton
          productId={p.productId}
          productName={p.name}
          initial={p.favorite === true}
          className="product-card-fav"
          onChange={(fav) => onFavoriteChange?.(p.productId, fav)}
        />
      )}
    </div>
  )
}

/** 별점 요약 (★ 4.3 · 리뷰 12). 리뷰가 없으면 compact에선 아무것도, 아니면 "아직 리뷰가 없어요" */
export function RatingSummary({
  averageRating,
  reviewCount,
  compact = false,
}: {
  averageRating: number | null
  reviewCount: number
  compact?: boolean
}) {
  if (averageRating === null || reviewCount === 0) {
    return compact ? null : <span className="rating-summary muted-text">아직 리뷰가 없어요</span>
  }
  return (
    <span className={`rating-summary${compact ? ' compact' : ''}`} aria-label={`별점 5점 중 ${averageRating}점, 리뷰 ${reviewCount}개`}>
      <span className="rating-star" aria-hidden>
        ★
      </span>
      <strong>{averageRating.toFixed(1)}</strong>
      <span className="muted-text">({reviewCount.toLocaleString()})</span>
    </span>
  )
}

/**
 * 찜 하트. 누르는 즉시 바꿔 보여주고(서버 응답을 기다리지 않음), 실패하면 되돌린다.
 * 로그인 전이면 onRequireLogin을 부른다 (상세 화면에서만 로그인 전에도 보여줌).
 */
export function FavoriteButton({
  productId,
  productName,
  initial,
  className = '',
  onChange,
  onRequireLogin,
}: {
  productId: number
  productName: string
  initial: boolean
  className?: string
  onChange?: (favorite: boolean) => void
  onRequireLogin?: () => void
}) {
  const { auth } = useAuth()
  const [favorite, setFavorite] = useState(initial)
  const [busy, setBusy] = useState(false)

  async function toggle() {
    if (!auth) {
      onRequireLogin?.()
      return
    }
    if (busy) return
    const next = !favorite
    setFavorite(next)
    setBusy(true)
    try {
      if (next) await api.addFavorite(productId, auth)
      else await api.removeFavorite(productId, auth)
      onChange?.(next)
    } catch (err) {
      setFavorite(!next)
      window.alert(err instanceof ApiError ? err.message : '찜하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <button
      type="button"
      className={`fav-btn${favorite ? ' on' : ''} ${className}`}
      aria-pressed={favorite}
      aria-label={favorite ? `${productName} 찜 해제` : `${productName} 찜하기`}
      title={favorite ? '찜 해제' : '찜하기'}
      onClick={toggle}
    >
      <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden>
        <path
          d="M12 20.5s-7.5-4.6-9.3-9.2C1.5 8 3.6 4.5 7.1 4.5c2 0 3.6 1.1 4.9 2.8 1.3-1.7 2.9-2.8 4.9-2.8 3.5 0 5.6 3.5 4.4 6.8-1.8 4.6-9.3 9.2-9.3 9.2Z"
          fill={favorite ? 'currentColor' : 'none'}
          stroke="currentColor"
          strokeWidth="2"
          strokeLinejoin="round"
        />
      </svg>
    </button>
  )
}
