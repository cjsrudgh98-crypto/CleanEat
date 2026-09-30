import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { ProductSummary, RiskLevel, ScanResult } from '../api/types'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useAddToCart } from '../lib/useAddToCart'

const RISK_LABEL: Record<RiskLevel, string> = { LOW: '안전', MEDIUM: '주의', HIGH: '위험' }
const RISK_CLASS: Record<RiskLevel, string> = { LOW: 'badge-low', MEDIUM: 'badge-medium', HIGH: 'badge-high' }

interface ResultCardProps {
  result: ScanResult
}

/**
 * 알레르기 표시 - "이 제품에 들어 있는 것"과 "그중 내가 등록한 알레르기"를 구분해서 보여준다.
 * 내 알레르기에 해당하면 빨간 경고로 먼저 보여준다.
 */
export function AllergenSection({ allergens, warnings }: { allergens: string[]; warnings: string[] }) {
  const { auth } = useAuth()
  return (
    <div style={{ marginTop: '1rem' }}>
      <h3 style={{ fontSize: '0.9rem', margin: '0 0 0.5rem' }}>알레르기 유발 성분</h3>
      {warnings.length > 0 && (
        <div className="allergy-warning-box" role="alert">
          ⚠ 내가 등록한 알레르기 성분이 들어 있어요: {warnings.join(', ')}
        </div>
      )}
      {allergens.length === 0 ? (
        <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          성분표에서 알레르기 유발 성분을 찾지 못했습니다.
        </p>
      ) : (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem' }}>
          {allergens.map((name) => (
            <span key={name} className={`tag${warnings.includes(name) ? ' tag-danger' : ''}`}>
              {name}
            </span>
          ))}
        </div>
      )}
      {!auth && allergens.length > 0 && (
        <p className="muted-text" style={{ margin: '0.45rem 0 0', fontSize: '0.75rem' }}>
          로그인하고 마이페이지에 알레르기를 등록하면 해당 성분을 따로 경고해 드려요.
        </p>
      )}
    </div>
  )
}

export function ResultCard({ result }: ResultCardProps) {
  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.75rem' }}>
        <div>
          <h2 style={{ margin: '0 0 0.2rem', fontSize: '1.05rem' }}>{result.productName}</h2>
          {result.barcode && (
            <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.85rem' }}>바코드: {result.barcode}</p>
          )}
        </div>
        <span className={`badge ${RISK_CLASS[result.overallRisk]}`}>{RISK_LABEL[result.overallRisk]}</span>
      </div>

      <div style={{ marginTop: '1rem' }}>
        <h3 style={{ fontSize: '0.9rem', margin: '0 0 0.5rem' }}>유해 성분</h3>
        {result.harmfulIngredients.length === 0 ? (
          <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.85rem' }}>발견된 유해 성분이 없습니다.</p>
        ) : (
          <ul style={{ margin: 0, paddingLeft: '1.1rem', display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
            {result.harmfulIngredients.map((m) => (
              <li key={m.ingredientName} style={{ fontSize: '0.88rem' }}>
                <span className={`badge ${RISK_CLASS[m.riskLevel]}`} style={{ marginRight: '0.4rem' }}>
                  {RISK_LABEL[m.riskLevel]}
                </span>
                <b>{m.ingredientName}</b> — {m.description}
              </li>
            ))}
          </ul>
        )}
      </div>

      <AllergenSection allergens={result.allergenMatches} warnings={result.userAllergyWarnings} />

      {result.recommendations.length > 0 && (
        <div style={{ marginTop: '1rem' }}>
          <h3 style={{ fontSize: '0.9rem', margin: '0 0 0.5rem' }}>대안 제품 추천</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
            {result.recommendations.map((p) => (
              <RecommendationRow key={p.id} product={p} />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export function RiskBadge({ level }: { level: RiskLevel }) {
  return <span className={`badge ${RISK_CLASS[level]}`}>{RISK_LABEL[level]}</span>
}

export function RecommendationRow({ product }: { product: ProductSummary }) {
  const { auth } = useAuth()
  // 내 알레르기 성분이 든 상품이면 담기 전에 확인창을 띄운다
  const { addToCart: addWithAllergyCheck, dialog: allergyDialog } = useAddToCart()
  const [status, setStatus] = useState<'idle' | 'adding' | 'added'>('idle')
  const [error, setError] = useState<string | null>(null)

  async function addToCart() {
    if (!auth) return
    setStatus('adding')
    setError(null)
    try {
      // 확인창에서 "담지 않기"를 누르면 null - 담기 전 상태로 되돌린다
      setStatus((await addWithAllergyCheck(product.id, 1)) ? 'added' : 'idle')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '담지 못했습니다.')
      setStatus('idle')
    }
  }

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '0.75rem',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-sm)',
        padding: '0.6rem',
      }}
    >
      {product.imageUrl && (
        <img
          src={product.imageUrl}
          alt={product.name}
          style={{ width: 48, height: 48, borderRadius: '8px', objectFit: 'cover', flexShrink: 0 }}
        />
      )}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
          <span className={`badge ${RISK_CLASS[product.riskLevel]}`}>{RISK_LABEL[product.riskLevel]}</span>
          <span style={{ fontSize: '0.88rem', fontWeight: 600 }}>{product.name}</span>
        </div>
        {product.price != null && (
          <p style={{ margin: '0.2rem 0 0', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
            {product.price.toLocaleString()}원
          </p>
        )}
        {error && <p style={{ margin: '0.2rem 0 0', fontSize: '0.78rem', color: 'var(--high-fg)' }}>{error}</p>}
      </div>

      {product.price != null &&
        (auth ? (
          <button
            className="btn"
            style={{ flexShrink: 0 }}
            disabled={status !== 'idle'}
            onClick={addToCart}
          >
            {status === 'added' ? (
              <Link to="/cart" style={{ color: 'inherit', textDecoration: 'none' }}>
                장바구니로 →
              </Link>
            ) : status === 'adding' ? (
              '담는 중...'
            ) : (
              '담기'
            )}
          </button>
        ) : (
          <span style={{ flexShrink: 0, fontSize: '0.75rem', color: 'var(--text-muted)' }}>로그인 후 주문 가능</span>
        ))}
      {allergyDialog}
    </div>
  )
}
