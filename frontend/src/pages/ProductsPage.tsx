import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { ProductCategory, ProductRecommendation, StoreListing } from '../api/types'
import { useLayoutMode } from '../layout/useLayoutMode'
import { CATEGORY_ICONS } from '../components/categoryIcons'
import { ProductGrid } from '../components/ProductCard'

type SortKey = 'default' | 'priceAsc' | 'priceDesc' | 'name' | 'rating' | 'reviews'

const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: 'default', label: '기본순' },
  { value: 'priceAsc', label: '낮은 가격순' },
  { value: 'priceDesc', label: '높은 가격순' },
  { value: 'name', label: '이름순' },
  { value: 'rating', label: '별점 높은순' },
  { value: 'reviews', label: '리뷰 많은순' },
]

// "전체"에서 카테고리마다 미리 보여줄 상품 수 (웹: 4열 한 줄, 앱: 2열 두 줄) - 나머지는 "전체 보기"로
const PREVIEW_COUNT = 4

interface CategoryGroup {
  category: ProductCategory
  label: string
  products: StoreListing[]
}

function sortProducts(products: StoreListing[], sort: SortKey) {
  if (sort === 'default') return products
  const sorted = [...products]
  if (sort === 'priceAsc') sorted.sort((a, b) => a.price - b.price)
  else if (sort === 'priceDesc') sorted.sort((a, b) => b.price - a.price)
  // 별점이 같으면 리뷰가 많은 쪽을 먼저, 리뷰가 없는 상품은 맨 뒤
  else if (sort === 'rating')
    sorted.sort((a, b) => (b.averageRating ?? -1) - (a.averageRating ?? -1) || b.reviewCount - a.reviewCount)
  else if (sort === 'reviews') sorted.sort((a, b) => b.reviewCount - a.reviewCount || (b.averageRating ?? 0) - (a.averageRating ?? 0))
  else sorted.sort((a, b) => a.name.localeCompare(b.name, 'ko'))
  return sorted
}

export default function ProductsPage() {
  const { isWeb } = useLayoutMode()
  const { auth } = useAuth()
  const [allProducts, setAllProducts] = useState<StoreListing[]>([])
  const [recommendation, setRecommendation] = useState<ProductRecommendation | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  // 내 알레르기 성분이 든 상품은 기본으로 숨긴다 (끄면 경고 배지를 달고 보여줌)
  const [hideAllergy, setHideAllergy] = useState(true)
  const [dietOnly, setDietOnly] = useState(false)
  // 카테고리/정렬은 쿼리스트링에 둬서 상세 페이지에 갔다가 뒤로 와도 유지되게 한다
  const [searchParams, setSearchParams] = useSearchParams()
  const selected = searchParams.get('category') as ProductCategory | null
  const sort = (searchParams.get('sort') as SortKey | null) ?? 'default'

  // 로그인 상태가 바뀌면 (로그인/로그아웃) 내 설정 기준으로 다시 불러온다
  useEffect(() => {
    api
      .getProducts(auth)
      .then(setAllProducts)
      .catch(() => setError('상품을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
    if (auth) {
      api.getRecommendations(auth).then(setRecommendation).catch(() => setRecommendation(null))
    }
  }, [auth])

  const allergyCount = allProducts.filter((p) => p.allergyWarnings.length > 0).length
  const hasDiet = allProducts.some((p) => p.dietMatch !== null)
  // 로그아웃하면 이전 사용자의 추천이 남지 않게
  const activeRecommendation = auth ? recommendation : null

  const products = useMemo(
    () =>
      allProducts.filter(
        (p) => !(hideAllergy && p.allergyWarnings.length > 0) && !(dietOnly && p.dietMatch === false),
      ),
    [allProducts, hideAllergy, dietOnly],
  )

  // 카테고리 목록(개수 포함)은 검색어와 무관하게 전체 상품 기준으로 만든다.
  // 서버가 이미 카테고리 순 -> 이름 순으로 정렬해서 주므로 등장 순서대로 묶기만 하면 된다.
  const groups = useMemo(() => {
    const map = new Map<ProductCategory, CategoryGroup>()
    for (const p of products) {
      const group = map.get(p.category) ?? { category: p.category, label: p.categoryLabel, products: [] }
      group.products.push(p)
      map.set(p.category, group)
    }
    return [...map.values()]
  }, [products])

  const keyword = query.trim().toLowerCase()
  const searchResults = useMemo(() => {
    if (!keyword) return []
    const matched = products.filter(
      (p) => p.name.toLowerCase().includes(keyword) || (p.description ?? '').toLowerCase().includes(keyword),
    )
    return sortProducts(selected ? matched.filter((p) => p.category === selected) : matched, sort)
  }, [products, keyword, selected, sort])

  function updateParams(next: { category?: ProductCategory | null; sort?: SortKey }) {
    const params = new URLSearchParams(searchParams)
    if (next.category !== undefined) {
      if (next.category) params.set('category', next.category)
      else params.delete('category')
    }
    if (next.sort !== undefined) {
      if (next.sort === 'default') params.delete('sort')
      else params.set('sort', next.sort)
    }
    setSearchParams(params, { replace: true })
  }

  const selectedGroup = selected ? groups.find((g) => g.category === selected) : undefined
  const columns = isWeb ? 4 : 2

  const toolbar = (
    <div className="products-toolbar">
      <input
        type="search"
        aria-label="상품 검색"
        placeholder="상품명, 설명으로 검색"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <select aria-label="정렬" value={sort} onChange={(e) => updateParams({ sort: e.target.value as SortKey })}>
        {SORT_OPTIONS.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  )

  // 로그인 사용자에게만: 식단/알레르기 필터 스위치
  const filters = auth && (allergyCount > 0 || hasDiet) && (
    <div className="filter-row">
      {allergyCount > 0 && (
        <label className="switch-label">
          <input type="checkbox" checked={hideAllergy} onChange={(e) => setHideAllergy(e.target.checked)} />
          내 알레르기 상품 숨기기 <span className="muted-text">({allergyCount}개)</span>
        </label>
      )}
      {hasDiet && activeRecommendation && (
        <label className="switch-label">
          <input type="checkbox" checked={dietOnly} onChange={(e) => setDietOnly(e.target.checked)} />
          {activeRecommendation.dietLabel} 식단 상품만 보기
        </label>
      )}
    </div>
  )

  const body = (
    <>
      {!keyword && !selected && auth && (
        <RecommendationSection
          recommendation={activeRecommendation}
          nickname={auth.nickname}
          columns={columns}
          previewCount={isWeb ? 8 : 4}
        />
      )}

      {keyword && (
        <section>
          <SectionHeading icon="🔍" title={`'${query.trim()}' 검색 결과`} count={searchResults.length} />
          {searchResults.length > 0 ? (
            <ProductGrid products={searchResults} columns={columns} />
          ) : (
            <p className="muted-text">검색 결과가 없습니다.</p>
          )}
        </section>
      )}

      {!keyword && selectedGroup && (
        <section>
          <SectionHeading
            icon={CATEGORY_ICONS[selectedGroup.category]}
            title={selectedGroup.label}
            count={selectedGroup.products.length}
          />
          <ProductGrid products={sortProducts(selectedGroup.products, sort)} columns={columns} />
        </section>
      )}
      {!keyword && selected && !selectedGroup && products.length > 0 && (
        <p className="muted-text">이 카테고리에는 상품이 없습니다.</p>
      )}

      {!keyword &&
        !selected &&
        groups.map((group) => (
          <section key={group.category} style={{ marginBottom: '1.5rem' }}>
            <SectionHeading
              icon={CATEGORY_ICONS[group.category]}
              title={group.label}
              count={group.products.length}
              action={
                group.products.length > PREVIEW_COUNT ? (
                  <button type="button" className="link-btn" onClick={() => updateParams({ category: group.category })}>
                    전체 보기 ›
                  </button>
                ) : null
              }
            />
            <ProductGrid products={sortProducts(group.products, sort).slice(0, PREVIEW_COUNT)} columns={columns} />
          </section>
        ))}

      {!loading && !error && products.length === 0 && <p className="muted-text">판매 중인 상품이 없습니다.</p>}
    </>
  )

  const summary = !loading && !error && (
    <span className="muted-text">
      총 <strong style={{ color: 'var(--text)' }}>{products.length}</strong>개 · {groups.length}개 카테고리
    </span>
  )

  const status = (
    <>
      {error && <div className="error-box">{error}</div>}
      {loading && (
        <div className="status-row">
          <span className="spinner" />
          <span>상품을 불러오는 중...</span>
        </div>
      )}
    </>
  )

  // ---------- 웹 디자인: 왼쪽 카테고리 사이드바 + 오른쪽 4열 그리드 ----------
  if (isWeb) {
    return (
      <div className="products-web">
        <aside className="category-sidebar card" aria-label="상품 카테고리">
          <p className="category-sidebar-title">카테고리</p>
          <CategoryItem
            icon="🏪"
            label="전체"
            count={products.length}
            active={!selected}
            onClick={() => updateParams({ category: null })}
          />
          {groups.map((g) => (
            <CategoryItem
              key={g.category}
              icon={CATEGORY_ICONS[g.category]}
              label={g.label}
              count={g.products.length}
              active={selected === g.category}
              onClick={() => updateParams({ category: g.category })}
            />
          ))}
        </aside>

        <div style={{ minWidth: 0 }}>
          <div className="products-web-head">
            <div>
              <h1 style={{ margin: '0 0 0.25rem', fontSize: '1.5rem' }}>
                {selectedGroup ? selectedGroup.label : 'CleanEat 판매 상품'}
              </h1>
              {summary}
            </div>
            {allProducts.length > 0 && toolbar}
          </div>
          {filters}
          {status}
          {body}
        </div>
      </div>
    )
  }

  // ---------- 앱 디자인: 상단 칩(가로 스크롤) + 2열 그리드 ----------
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
        <h2 className="section-title">CleanEat 판매 상품</h2>
        {summary}
      </div>
      {status}
      {allProducts.length > 0 && (
        <>
          <div style={{ marginBottom: '0.75rem' }}>{toolbar}</div>
          {filters}
          <HorizontalScroller className="chip-row" role="tablist" ariaLabel="상품 카테고리">
            <CategoryChip
              label="전체"
              count={products.length}
              active={!selected}
              onClick={() => updateParams({ category: null })}
            />
            {groups.map((g) => (
              <CategoryChip
                key={g.category}
                label={`${CATEGORY_ICONS[g.category]} ${g.label}`}
                count={g.products.length}
                active={selected === g.category}
                onClick={() => updateParams({ category: g.category })}
              />
            ))}
          </HorizontalScroller>
        </>
      )}
      {body}
    </div>
  )
}

// 마우스 휠(세로)로도 가로 스크롤되게 해서 PC에서 앱 화면을 볼 때도 칩 목록을 넘겨볼 수 있게 한다
function HorizontalScroller({
  children,
  className,
  role,
  ariaLabel,
}: {
  children: ReactNode
  className: string
  role?: string
  ariaLabel?: string
}) {
  return (
    <div
      className={className}
      role={role}
      aria-label={ariaLabel}
      onWheel={(e) => {
        const el = e.currentTarget
        if (el.scrollWidth > el.clientWidth && Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
          el.scrollLeft += e.deltaY
        }
      }}
    >
      {children}
    </div>
  )
}

function SectionHeading({
  icon,
  title,
  count,
  action,
}: {
  icon: string
  title: string
  count: number
  action?: ReactNode
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '0 0 0.7rem' }}>
      <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.95rem', margin: 0 }}>
        <span aria-hidden>{icon}</span>
        {title}
        <span style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-muted)' }}>{count}개</span>
      </h3>
      {action}
    </div>
  )
}

function CategoryChip({
  label,
  count,
  active,
  onClick,
}: {
  label: string
  count: number
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      className={`chip${active ? ' chip-active' : ''}`}
      onClick={onClick}
    >
      {label}
      <span className="chip-count">{count}</span>
    </button>
  )
}

function CategoryItem({
  icon,
  label,
  count,
  active,
  onClick,
}: {
  icon: string
  label: string
  count: number
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-current={active ? 'true' : undefined}
      className={`category-item${active ? ' active' : ''}`}
      onClick={onClick}
    >
      <span aria-hidden>{icon}</span>
      <span style={{ flex: 1, textAlign: 'left' }}>{label}</span>
      <span className="category-item-count">{count}</span>
    </button>
  )
}


function RecommendationSection({
  recommendation,
  nickname,
  columns,
  previewCount,
}: {
  recommendation: ProductRecommendation | null
  nickname: string
  columns: number
  previewCount: number
}) {
  if (!recommendation) return null
  const personalized = recommendation.dietTypes.length > 0 || recommendation.allergies.length > 0

  return (
    <section className="recommend-section">
      <div className="recommend-head">
        <div>
          <h3 style={{ margin: '0 0 0.3rem', fontSize: '1rem' }}>🥗 {nickname}님 맞춤 추천</h3>
          {personalized ? (
            <div className="recommend-tags">
              {recommendation.dietTypes.length > 0 && <span className="tag">{recommendation.dietLabel} 식단</span>}
              {recommendation.allergies.length > 0 && (
                <span className="tag tag-danger">{recommendation.allergies.join('·')} 제외</span>
              )}
              {recommendation.excludedByAllergy > 0 && (
                <span className="muted-text" style={{ fontSize: '0.75rem' }}>
                  알레르기 성분이 든 {recommendation.excludedByAllergy}개 상품은 뺐어요
                </span>
              )}
            </div>
          ) : (
            <p className="muted-text" style={{ margin: 0 }}>
              식단과 알레르기를 설정하면 나에게 맞는 상품만 골라드려요.
            </p>
          )}
        </div>
        <Link to="/mypage" className="link-btn" style={{ textDecoration: 'none', whiteSpace: 'nowrap' }}>
          {personalized ? '설정 변경 ›' : '설정하기 ›'}
        </Link>
      </div>
      {recommendation.products.length > 0 ? (
        <ProductGrid products={recommendation.products.slice(0, previewCount)} columns={columns} />
      ) : (
        <p className="muted-text" style={{ margin: 0 }}>
          조건에 맞는 상품이 아직 없어요. 설정을 조금 바꿔보세요.
        </p>
      )}
    </section>
  )
}

