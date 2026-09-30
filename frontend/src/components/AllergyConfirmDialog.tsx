import { useEffect, useRef } from 'react'

// 받침이 있으면 "이", 없으면 "가" (우유가 / 대두가 / 땅콩이)
function subjectParticle(word: string) {
  const code = word.charCodeAt(word.length - 1) - 0xac00
  if (code < 0 || code > 11171) return '이(가)'
  return code % 28 === 0 ? '가' : '이'
}

/**
 * 내 알레르기 성분이 든 상품을 장바구니에 담기 전 확인창.
 * 기본 선택(Enter/Esc)은 안전한 쪽인 "담지 않기"다.
 */
export function AllergyConfirmDialog({
  productName,
  allergens,
  onAnswer,
}: {
  productName: string
  allergens: string[]
  onAnswer: (confirmed: boolean) => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const cancelRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
    cancelRef.current?.focus()
  }, [])

  const names = allergens.join('·')
  return (
    <dialog
      ref={dialogRef}
      className="card allergy-dialog"
      aria-labelledby="allergyDialogTitle"
      aria-describedby="allergyDialogDesc"
      onCancel={(e) => {
        e.preventDefault()
        onAnswer(false)
      }}
    >
      <p className="allergy-dialog-icon" aria-hidden>
        ⚠
      </p>
      <h2 id="allergyDialogTitle" className="allergy-dialog-title">
        {names}{subjectParticle(allergens[allergens.length - 1] ?? '')} 들어 있어요
      </h2>
      <p id="allergyDialogDesc" className="allergy-dialog-desc">
        <strong>{productName}</strong>에 마이페이지에 등록한 알레르기 성분이 들어 있어요. 그래도 장바구니에 담을까요?
      </p>
      <div className="allergy-dialog-chips">
        {allergens.map((a) => (
          <span key={a} className="chip chip-danger">
            {a}
          </span>
        ))}
      </div>
      <div className="allergy-dialog-actions">
        <button ref={cancelRef} type="button" className="btn btn-primary" onClick={() => onAnswer(false)}>
          담지 않기
        </button>
        <button type="button" className="btn" onClick={() => onAnswer(true)}>
          그래도 담기
        </button>
      </div>
    </dialog>
  )
}
