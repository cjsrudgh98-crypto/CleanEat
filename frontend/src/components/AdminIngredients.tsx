import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import type { AuthState, DictionaryIngredient, IngredientInput, RiskLevel } from '../api/types'

const RISK_LABEL: Record<RiskLevel, string> = { HIGH: '높음', MEDIUM: '보통', LOW: '낮음' }
const RISK_BADGE: Record<RiskLevel, string> = { HIGH: 'badge-high', MEDIUM: 'badge-medium', LOW: 'badge-low' }

function errorMessage(err: unknown, fallback: string) {
  return err instanceof ApiError ? err.message : fallback
}

// 별칭은 한 줄에 하나 또는 쉼표로 구분해서 입력
function parseAliases(text: string) {
  return text
    .split(/[\n,]/)
    .map((a) => a.trim())
    .filter((a) => a.length > 0)
}

/**
 * 관리자: 유해성분 사전. 추가/수정하면 다음 검사부터 바로 반영된다.
 * 지우는 대신 "사용 중지" - 기본 사전 성분은 지워도 서버 재시작 때 다시 들어오기 때문.
 */
export function AdminIngredients({ auth }: { auth: AuthState }) {
  const [items, setItems] = useState<DictionaryIngredient[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [showDisabledOnly, setShowDisabledOnly] = useState(false)
  // null: 폼 닫힘 / 'new': 새 성분 / 성분: 수정
  const [editing, setEditing] = useState<DictionaryIngredient | 'new' | null>(null)

  useEffect(() => {
    api
      .adminIngredients(auth)
      .then(setItems)
      .catch((err) => setError(errorMessage(err, '성분 사전을 불러오지 못했습니다.')))
  }, [auth])

  const categories = useMemo(
    () => [...new Set((items ?? []).map((i) => i.category).filter((c): c is string => !!c))],
    [items],
  )

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (items ?? []).filter((i) => {
      if (showDisabledOnly && i.enabled) return false
      return !q || i.name.toLowerCase().includes(q) || i.aliases.some((a) => a.toLowerCase().includes(q))
    })
  }, [items, query, showDisabledOnly])

  function upsert(saved: DictionaryIngredient) {
    setItems((prev) => {
      if (!prev) return [saved]
      return prev.some((i) => i.id === saved.id) ? prev.map((i) => (i.id === saved.id ? saved : i)) : [saved, ...prev]
    })
  }

  async function toggle(item: DictionaryIngredient) {
    const next = !item.enabled
    if (!next && !window.confirm(`'${item.name}'을(를) 사용 중지할까요? 다음 검사부터 이 성분을 찾지 않습니다.`)) return
    try {
      upsert(await api.adminSetIngredientEnabled(item.id, next, auth))
    } catch (err) {
      window.alert(errorMessage(err, '바꾸지 못했습니다.'))
    }
  }

  const disabledCount = (items ?? []).filter((i) => !i.enabled).length

  return (
    <>
      {editing && (
        <IngredientForm
          key={editing === 'new' ? 'new' : editing.id}
          auth={auth}
          item={editing === 'new' ? null : editing}
          categories={categories}
          onSaved={(saved) => {
            upsert(saved)
            setEditing(null)
          }}
          onClose={() => setEditing(null)}
        />
      )}
      <div className="card">
        <div className="admin-toolbar">
          <input type="search" placeholder="성분 이름·별칭 검색" value={query} onChange={(e) => setQuery(e.target.value)} />
          <label
            className="switch-label"
            style={{ margin: 0, display: 'inline-flex', alignItems: 'center', gap: '0.35rem', whiteSpace: 'nowrap', flexShrink: 0 }}
          >
            <input type="checkbox" checked={showDisabledOnly} onChange={(e) => setShowDisabledOnly(e.target.checked)} />
            사용 중지만 ({disabledCount})
          </label>
          <button type="button" className="btn btn-primary" onClick={() => setEditing('new')}>
            + 성분 추가
          </button>
        </div>
        <p className="muted-text" style={{ margin: '0 0 0.75rem' }}>
          총 {items?.length ?? 0}종 · 추가하거나 고치면 다음 검사부터 바로 반영돼요. 기본 성분은 지우는 대신 사용 중지해 주세요.
        </p>
        {error && <div className="error-box">{error}</div>}
        {!error && items === null && <p className="muted-text">불러오는 중...</p>}
        <ul className="dict-list">
          {visible.map((item) => (
            <li key={item.id} className={`dict-item${item.enabled ? '' : ' disabled'}`}>
              <div className="dict-item-head">
                <span className="dict-item-name">{item.name}</span>
                <span className={`badge ${RISK_BADGE[item.riskLevel]}`} style={{ fontSize: '0.7rem', padding: '0.1rem 0.45rem' }}>
                  {RISK_LABEL[item.riskLevel]}
                </span>
                {item.category && <span className="muted-text">{item.category}</span>}
                {!item.enabled && <span className="badge badge-high" style={{ fontSize: '0.7rem', padding: '0.1rem 0.45rem' }}>사용 중지</span>}
              </div>
              {item.aliases.length > 0 && <p className="dict-item-aliases">별칭: {item.aliases.join(' · ')}</p>}
              <div className="dict-item-actions">
                <button type="button" className="link-btn" onClick={() => setEditing(item)}>
                  수정
                </button>
                <button
                  type="button"
                  className="link-btn"
                  style={item.enabled ? { color: 'var(--high-fg)' } : undefined}
                  onClick={() => toggle(item)}
                >
                  {item.enabled ? '사용 중지' : '다시 사용'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </>
  )
}

function IngredientForm({
  auth,
  item,
  categories,
  onSaved,
  onClose,
}: {
  auth: AuthState
  item: DictionaryIngredient | null
  categories: string[]
  onSaved: (saved: DictionaryIngredient) => void
  onClose: () => void
}) {
  const [name, setName] = useState(item?.name ?? '')
  const [riskLevel, setRiskLevel] = useState<RiskLevel>(item?.riskLevel ?? 'MEDIUM')
  const [category, setCategory] = useState(item?.category ?? '')
  const [description, setDescription] = useState(item?.description ?? '')
  const [aliasText, setAliasText] = useState((item?.aliases ?? []).join('\n'))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const input: IngredientInput = { name, riskLevel, category, description, aliases: parseAliases(aliasText) }
    setSaving(true)
    setError(null)
    try {
      onSaved(item ? await api.adminUpdateIngredient(item.id, input, auth) : await api.adminCreateIngredient(input, auth))
    } catch (err) {
      setError(errorMessage(err, '저장하지 못했습니다.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="card admin-form" onSubmit={submit}>
      <h2 className="section-title">{item ? '성분 수정' : '새 성분 추가'}</h2>
      <div className="admin-form-grid">
        <div>
          <label htmlFor="ing-name">성분 이름</label>
          <input id="ing-name" type="text" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div>
          <label htmlFor="ing-risk">위험도</label>
          <select id="ing-risk" value={riskLevel} onChange={(e) => setRiskLevel(e.target.value as RiskLevel)}>
            <option value="HIGH">높음</option>
            <option value="MEDIUM">보통</option>
            <option value="LOW">낮음</option>
          </select>
        </div>
        <div>
          <label htmlFor="ing-category">분류</label>
          <input
            id="ing-category"
            type="text"
            list="ing-categories"
            maxLength={30}
            placeholder="예: 감미료"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          />
          <datalist id="ing-categories">
            {categories.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
        </div>
      </div>
      <label htmlFor="ing-desc">설명</label>
      <textarea
        id="ing-desc"
        rows={3}
        maxLength={1000}
        placeholder="어떤 성분이고 왜 주의가 필요한지 (확인된 사실 위주로)"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <label htmlFor="ing-aliases">별칭 (한 줄에 하나)</label>
      <textarea
        id="ing-aliases"
        rows={4}
        placeholder={'영어 이름, E-번호, 다른 한국어 표기\n예: potassium bromate\nE924'}
        value={aliasText}
        onChange={(e) => setAliasText(e.target.value)}
      />
      <p className="muted-text" style={{ margin: '-0.4rem 0 0.85rem' }}>
        성분 이름 자체는 별칭 없이도 찾아요. 4글자 이하 영문(MSG 등)과 E-번호는 단어로 떨어져 있을 때만 찾아요.
      </p>
      {error && <div className="error-box" style={{ marginBottom: '0.75rem' }}>{error}</div>}
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? '저장 중...' : item ? '수정 저장' : '추가'}
        </button>
        <button type="button" className="btn" onClick={onClose}>
          닫기
        </button>
      </div>
    </form>
  )
}
