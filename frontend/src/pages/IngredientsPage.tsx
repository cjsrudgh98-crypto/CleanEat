import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'
import type { DictionaryIngredient, RiskLevel } from '../api/types'

const RISK_LABEL: Record<RiskLevel, string> = { HIGH: '위험도 높음', MEDIUM: '위험도 보통', LOW: '위험도 낮음' }
const RISK_BADGE: Record<RiskLevel, string> = { HIGH: 'badge-high', MEDIUM: 'badge-medium', LOW: 'badge-low' }
const RISK_FILTERS: { value: RiskLevel | 'ALL'; label: string }[] = [
  { value: 'ALL', label: '전체 위험도' },
  { value: 'HIGH', label: '높음' },
  { value: 'MEDIUM', label: '보통' },
  { value: 'LOW', label: '낮음' },
]
const UNCATEGORIZED = '기타'

/** 공개 유해성분 사전 - 검사에서 찾는 성분 전체와 그 이유 */
export default function IngredientsPage() {
  const [items, setItems] = useState<DictionaryIngredient[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<string | null>(null)
  const [risk, setRisk] = useState<RiskLevel | 'ALL'>('ALL')

  useEffect(() => {
    api
      .getIngredients()
      .then(setItems)
      .catch(() => setError('성분 사전을 불러오지 못했습니다.'))
  }, [])

  // 서버가 분류 순서대로 주므로 처음 나온 순서가 곧 분류 순서
  const categories = useMemo(() => {
    const counts = new Map<string, number>()
    for (const item of items ?? []) {
      const c = item.category ?? UNCATEGORIZED
      counts.set(c, (counts.get(c) ?? 0) + 1)
    }
    return [...counts.entries()]
  }, [items])

  const groups = useMemo(() => {
    const q = query.trim().toLowerCase()
    const filtered = (items ?? []).filter((item) => {
      if (category && (item.category ?? UNCATEGORIZED) !== category) return false
      if (risk !== 'ALL' && item.riskLevel !== risk) return false
      if (!q) return true
      return (
        item.name.toLowerCase().includes(q) ||
        item.aliases.some((a) => a.toLowerCase().includes(q)) ||
        (item.description ?? '').toLowerCase().includes(q)
      )
    })
    const byCategory = new Map<string, DictionaryIngredient[]>()
    for (const item of filtered) {
      const c = item.category ?? UNCATEGORIZED
      byCategory.set(c, [...(byCategory.get(c) ?? []), item])
    }
    return [...byCategory.entries()]
  }, [items, query, category, risk])

  return (
    <>
      <div className="card">
        <h2 className="section-title">
          유해성분 사전 {items && <span className="muted-text">{items.length}종</span>}
        </h2>
        <p className="muted-text" style={{ margin: '0 0 0.85rem' }}>
          제품을 검사할 때 이 성분들을 찾아 알려드려요. 영어 이름이나 E-번호(E211 등)로 적혀 있어도 찾아요.
        </p>
        <div className="dict-toolbar">
          <input
            type="search"
            aria-label="성분 검색"
            placeholder="성분 이름, 영어 이름, E-번호로 검색 (예: 아스파탐, E211)"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <div className="chip-row" role="group" aria-label="분류" style={{ marginBottom: 0 }}>
            <button type="button" className={`chip${category === null ? ' chip-active' : ''}`} onClick={() => setCategory(null)}>
              전체
            </button>
            {categories.map(([c, count]) => (
              <button
                key={c}
                type="button"
                className={`chip${category === c ? ' chip-active' : ''}`}
                aria-pressed={category === c}
                onClick={() => setCategory(c)}
              >
                {c} <span className="chip-count">{count}</span>
              </button>
            ))}
          </div>
          <div className="chip-row" role="group" aria-label="위험도" style={{ marginBottom: 0 }}>
            {RISK_FILTERS.map((r) => (
              <button
                key={r.value}
                type="button"
                className={`chip${risk === r.value ? ' chip-active' : ''}`}
                aria-pressed={risk === r.value}
                onClick={() => setRisk(r.value)}
              >
                {r.label}
              </button>
            ))}
          </div>
        </div>
        <p className="dict-note">
          위험도는 논란과 연구 결과를 바탕으로 한 참고 정보예요. 대부분은 정해진 허용량 안에서 사용이 허가된
          첨가물이며, 먹으면 바로 해롭다는 뜻은 아니에요.
        </p>
      </div>

      {error && (
        <div className="card">
          <div className="error-box">{error}</div>
        </div>
      )}
      {!error && items === null && <div className="card muted-text">불러오는 중...</div>}
      {items && groups.length === 0 && <div className="card muted-text">찾는 성분이 없어요.</div>}

      {groups.map(([c, list]) => (
        <section key={c} className="card" aria-labelledby={`dict-${c}`}>
          <h3 id={`dict-${c}`} className="dict-group-title">
            {c} <span className="muted-text">{list.length}</span>
          </h3>
          <ul className="dict-list">
            {list.map((item) => (
              <DictionaryItem key={item.id} item={item} />
            ))}
          </ul>
        </section>
      ))}
    </>
  )
}

// "artificial colo"(colour/color), "artificial flavo"(flavour/flavor)처럼 찾기용으로 앞부분만 적은 별칭은 화면에 안 보여준다
const MATCHING_FRAGMENT = /(colo|flavo)$/i

function DictionaryItem({ item }: { item: DictionaryIngredient }) {
  // 성분 이름과 같은 별칭은 빼고 보여준다
  const others = item.aliases
    .filter((a) => a !== item.name && !MATCHING_FRAGMENT.test(a))
    .map((a) => (/^e\d/i.test(a) ? a.toUpperCase() : a))
  return (
    <li className="dict-item">
      <div className="dict-item-head">
        <span className="dict-item-name">{item.name}</span>
        <span className={`badge ${RISK_BADGE[item.riskLevel]}`} style={{ fontSize: '0.7rem', padding: '0.1rem 0.45rem' }}>
          {RISK_LABEL[item.riskLevel]}
        </span>
      </div>
      {item.description && <p className="dict-item-desc">{item.description}</p>}
      {others.length > 0 && <p className="dict-item-aliases">다른 표기: {others.join(' · ')}</p>}
    </li>
  )
}
