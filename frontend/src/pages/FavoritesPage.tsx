import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { StoreListing } from '../api/types'
import { ProductGrid } from '../components/ProductCard'
import { useLayoutMode } from '../layout/useLayoutMode'

/** 찜한 상품 (최근에 찜한 순) */
export default function FavoritesPage() {
  const { auth } = useAuth()
  const { isWeb } = useLayoutMode()
  const [products, setProducts] = useState<StoreListing[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!auth) return
    api
      .getFavorites(auth)
      .then(setProducts)
      .catch(() => setError('찜한 상품을 불러오지 못했습니다.'))
  }, [auth])

  if (!auth) return <div className="card">로그인 후 찜한 상품을 볼 수 있습니다.</div>

  return (
    <div className="card">
      <h2 className="section-title">찜한 상품 {products && products.length > 0 && <span className="muted-text">{products.length}</span>}</h2>
      {error && <div className="error-box">{error}</div>}
      {!error && products === null && <p className="muted-text">불러오는 중...</p>}
      {products?.length === 0 && (
        <>
          <p style={{ margin: '0 0 1rem', fontSize: '0.9rem' }}>아직 찜한 상품이 없어요. 상품의 ♡를 눌러 모아보세요.</p>
          <Link className="btn btn-primary" to="/products">
            상품 보러 가기
          </Link>
        </>
      )}
      {products && products.length > 0 && (
        <ProductGrid
          products={products}
          columns={isWeb ? 4 : 2}
          // 여기서 찜을 해제하면 목록에서 바로 뺀다
          onFavoriteChange={(productId, favorite) => {
            if (!favorite) setProducts((prev) => prev?.filter((p) => p.productId !== productId) ?? null)
          }}
        />
      )}
    </div>
  )
}
