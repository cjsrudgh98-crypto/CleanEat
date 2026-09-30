import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { ReceiptItem, ReceiptScanResult } from '../api/types'
import { AllergenSection, RecommendationRow, RiskBadge } from './ResultCard'

/** 영수증 스캔 결과 - 상품마다 위험도/내 알레르기를 한눈에, 누르면 상세 성분 */
export function ReceiptResultCard({ result }: { result: ReceiptScanResult }) {
  const riskyCount = result.items.filter((i) => i.overallRisk !== 'LOW').length

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.75rem' }}>
        <div>
          <h2 style={{ margin: '0 0 0.2rem', fontSize: '1.05rem' }}>🧾 {result.title}</h2>
          <p className="muted-text" style={{ margin: 0 }}>
            상품 {result.items.length}개 검사
            {riskyCount > 0 && ` · 주의/위험 ${riskyCount}개`}
            {result.failed.length > 0 && ` · 확인 못 함 ${result.failed.length}개`}
          </p>
        </div>
        <RiskBadge level={result.overallRisk} />
      </div>

      {result.source === 'RECEIPT_TEXT' && (
        <p className="muted-text" style={{ margin: '0.6rem 0 0', fontSize: '0.75rem' }}>
          사진의 글자를 읽어 비슷한 이름의 상품을 찾았어요. 상품마다 적힌 "영수증: …" 글자와 맞는지 확인해주세요.
        </p>
      )}

      {result.allergyWarningCount > 0 && (
        <div className="allergy-warning-box" role="alert" style={{ marginTop: '0.85rem', marginBottom: 0 }}>
          ⚠ 내 알레르기 성분이 든 상품이 {result.allergyWarningCount}개 있어요.
        </div>
      )}

      <ul className="receipt-items">
        {result.items.map((item, index) => (
          <ReceiptItemRow key={`${item.barcode}-${index}`} item={item} />
        ))}
      </ul>

      {result.failed.length > 0 && (
        <div style={{ marginTop: '0.85rem' }}>
          <h3 style={{ fontSize: '0.85rem', margin: '0 0 0.35rem' }}>
            {result.source === 'RECEIPT_TEXT' ? '영수증에서 읽었지만 상품을 찾지 못한 줄' : '확인하지 못한 항목'}
          </h3>
          <ul style={{ margin: 0, paddingLeft: '1.1rem' }}>
            {result.failed.map((f) => (
              <li key={f.code} className="muted-text">
                {f.code} — {f.reason}
              </li>
            ))}
          </ul>
        </div>
      )}

      {result.recommendations.length > 0 && (
        <div style={{ marginTop: '1rem' }}>
          <h3 style={{ fontSize: '0.9rem', margin: '0 0 0.5rem' }}>이 대신 이런 상품은 어때요?</h3>
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

function ReceiptItemRow({ item }: { item: ReceiptItem }) {
  const [open, setOpen] = useState(false)
  const warning = item.userAllergyWarnings.length > 0

  return (
    <li className={`receipt-item${warning ? ' has-warning' : ''}`}>
      <button type="button" className="receipt-item-head" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <RiskBadge level={item.overallRisk} />
        <span className="receipt-item-name">
          {item.productName}
          {item.quantity > 1 && <span className="muted-text"> × {item.quantity}</span>}
          {item.matchedText && item.matchedText !== item.productName && (
            <span className="receipt-item-source">영수증: {item.matchedText}</span>
          )}
        </span>
        {warning && <span className="flag flag-danger">⚠ {item.userAllergyWarnings.join('·')}</span>}
        {!warning && item.dietMatch && <span className="flag flag-ok">✓ 내 식단</span>}
        <span aria-hidden className="muted-text">
          {open ? '▲' : '▼'}
        </span>
      </button>

      {open && (
        <div className="receipt-item-body">
          {item.harmfulIngredients.length === 0 ? (
            <p className="muted-text" style={{ margin: 0 }}>
              발견된 유해 성분이 없습니다.
            </p>
          ) : (
            <ul style={{ margin: 0, paddingLeft: '1.1rem' }}>
              {item.harmfulIngredients.map((m) => (
                <li key={m.ingredientName} style={{ fontSize: '0.85rem' }}>
                  <RiskBadge level={m.riskLevel} /> <b>{m.ingredientName}</b> — {m.description}
                </li>
              ))}
            </ul>
          )}
          <AllergenSection allergens={item.allergenMatches} warnings={item.userAllergyWarnings} />
          {item.storeProductId != null && (
            <Link
              to={`/products/${item.storeProductId}`}
              className="link-btn"
              style={{ display: 'inline-block', marginTop: '0.6rem', textDecoration: 'none' }}
            >
              상품 페이지 보기 ›
            </Link>
          )}
        </div>
      )}
    </li>
  )
}
