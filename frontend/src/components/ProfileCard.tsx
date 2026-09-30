import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { DietType } from '../api/types'
import { COMMON_ALLERGIES, DIET_DESCRIPTIONS, DIET_OPTIONS } from './dietAllergyOptions'

/**
 * 식단 · 알레르기 설정. 여기서 저장한 값으로
 *  - 상품 목록에서 알레르기 상품 숨김/경고
 *  - 상품 화면 "맞춤 추천"
 *  - 성분 검사 결과의 대안 제품 추천
 * 이 달라진다.
 */
export function ProfileCard() {
  const { auth } = useAuth()
  const [allergies, setAllergies] = useState<string[]>([])
  // 여러 개 선택 가능 - 비어 있으면 "제한 없음"
  const [dietTypes, setDietTypes] = useState<DietType[]>([])
  const [allergyInput, setAllergyInput] = useState('')
  const [status, setStatus] = useState<'idle' | 'saving'>('idle')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (!auth) return
    api
      .getProfile(auth.userId, auth)
      .then((data) => {
        setAllergies(data.allergies)
        setDietTypes(data.dietTypes)
      })
      .catch(() => {
        // 프로필 로드 실패는 조용히 무시 (기본값 유지)
      })
  }, [auth])

  if (!auth) return null

  // "제한 없음"을 누르면 전부 해제, 다른 식단은 켜고 끄기
  function toggleDiet(value: DietType) {
    setSuccess(false)
    if (value === 'NONE') {
      setDietTypes([])
      return
    }
    setDietTypes((prev) => (prev.includes(value) ? prev.filter((d) => d !== value) : [...prev, value]))
  }

  function toggleAllergy(name: string) {
    setSuccess(false)
    setAllergies((prev) => (prev.includes(name) ? prev.filter((a) => a !== name) : [...prev, name]))
  }

  function addCustomAllergy() {
    const name = allergyInput.trim()
    if (!name) return
    if (!allergies.includes(name)) setAllergies([...allergies, name])
    setAllergyInput('')
    setSuccess(false)
  }

  async function save() {
    if (!auth) return
    setError(null)
    setSuccess(false)
    setStatus('saving')
    try {
      await api.saveProfile(auth.userId, allergies, dietTypes, auth)
      setSuccess(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '서버에 연결할 수 없습니다.')
    } finally {
      setStatus('idle')
    }
  }

  // 목록에 없는 알레르기(직접 입력한 것)도 선택된 칩으로 보여준다
  const customAllergies = allergies.filter((a) => !COMMON_ALLERGIES.includes(a))

  return (
    <div className="card">
      <h2 className="section-title">알레르기 · 식단 설정</h2>
      <p className="muted-text" style={{ margin: '0 0 1rem' }}>
        설정하면 알레르기 성분이 든 상품은 빼고, 식단에 맞는 상품을 추천해 드려요.
      </p>

      <label>
        식단 <span style={{ fontWeight: 500 }}>(여러 개 선택 가능)</span>
      </label>
      <div className="diet-options" role="group" aria-label="식단">
        {DIET_OPTIONS.map((opt) => {
          const selected = opt.value === 'NONE' ? dietTypes.length === 0 : dietTypes.includes(opt.value)
          return (
            <button
              key={opt.value}
              type="button"
              aria-pressed={selected}
              className={`diet-option${selected ? ' active' : ''}`}
              onClick={() => toggleDiet(opt.value)}
            >
              <strong>
                {selected && opt.value !== 'NONE' && '✓ '}
                {opt.label}
              </strong>
              <span>{DIET_DESCRIPTIONS[opt.value]}</span>
            </button>
          )
        })}
      </div>
      {dietTypes.length > 1 && (
        <p className="muted-text" style={{ margin: '0.45rem 0 0', fontSize: '0.75rem' }}>
          고른 식단을 모두 만족하는 상품만 추천돼요. 조건이 많을수록 추천 상품이 줄어들 수 있어요.
        </p>
      )}

      <label style={{ marginTop: '1.1rem' }}>
        알레르기 <span style={{ fontWeight: 500 }}>(해당하는 것을 모두 눌러주세요)</span>
      </label>
      <div className="allergy-chips">
        {[...COMMON_ALLERGIES, ...customAllergies].map((name) => {
          const selected = allergies.includes(name)
          return (
            <button
              key={name}
              type="button"
              aria-pressed={selected}
              className={`chip${selected ? ' chip-danger' : ''}`}
              onClick={() => toggleAllergy(name)}
            >
              {selected && '✓ '}
              {name}
            </button>
          )
        })}
      </div>
      <p className="muted-text" style={{ margin: '0.45rem 0 0.6rem', fontSize: '0.75rem' }}>
        '견과류'를 고르면 아몬드·호두·잣 등 견과가 든 상품이 모두 제외돼요.
      </p>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
        <input
          type="text"
          aria-label="알레르기 직접 입력"
          placeholder="목록에 없으면 직접 입력"
          autoComplete="off"
          value={allergyInput}
          onChange={(e) => setAllergyInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              addCustomAllergy()
            }
          }}
        />
        <button className="btn" type="button" onClick={addCustomAllergy}>
          추가
        </button>
      </div>

      <button className="btn btn-primary" onClick={save} disabled={status === 'saving'}>
        저장
      </button>
      {status === 'saving' && (
        <div className="status-row" style={{ marginTop: '0.6rem' }}>
          <span className="spinner" />
          <span>저장 중...</span>
        </div>
      )}
      {error && (
        <div className="error-box" style={{ marginTop: '0.6rem' }}>
          {error}
        </div>
      )}
      {success && (
        <p style={{ margin: '0.6rem 0 0', color: 'var(--brand-dark)', fontSize: '0.85rem', fontWeight: 600 }}>
          저장되었습니다.{' '}
          <Link to="/products" className="link-btn" style={{ textDecoration: 'none' }}>
            맞춤 추천 보러 가기 ›
          </Link>
        </p>
      )}
    </div>
  )
}
