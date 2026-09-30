import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { RefundAccount } from '../api/types'
import { BANKS } from '../lib/refundAccount'

// 서버 RefundAccountRequest와 같은 규칙
const ACCOUNT_NUMBER = /^[0-9-]{6,20}$/
const MAX_HOLDER_NAME = 20

export interface OrderActionAnswer {
  // 사유 칸이 있을 때만 (관리자 취소 사유 / 반품 사유 / 반품 거절 사유)
  reason?: string
  // 입금이 끝난 가상계좌 주문일 때만
  refundAccount?: RefundAccount
}

export interface ReasonField {
  label: string
  initial: string
  placeholder?: string
  // 비워도 되는지 (관리자 취소는 비우면 서버 기본 사유, 반품/거절은 필수)
  required: boolean
  maxLength: number
}

export interface OrderActionCopy {
  title: string
  description: string
  submitLabel: string
  // 되돌리기 어려운 동작(취소/환불/거절)은 빨간 버튼
  danger: boolean
}

type Field = 'reason' | keyof RefundAccount
type FieldErrors = Partial<Record<Field, string>>

/**
 * 주문 취소 / 반품 신청 / 반품 승인 / 반품 거절 폼. 제출 버튼을 누르는 것이 곧 확인이다 (따로 확인창을 띄우지 않는다).
 * Esc/닫기는 아무것도 하지 않는다. 칸 구성과 문구는 useOrderActionDialog가 정한다.
 */
export function OrderActionDialog({
  copy,
  orderSummary,
  reasonField,
  needsRefundAccount,
  onAnswer,
}: {
  copy: OrderActionCopy
  // 예: "#22 무첨가 현미 과자 · 4,500원"
  orderSummary: string
  reasonField: ReasonField | null
  needsRefundAccount: boolean
  onAnswer: (answer: OrderActionAnswer | null) => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const reasonRef = useRef<HTMLTextAreaElement>(null)
  const bankRef = useRef<HTMLSelectElement>(null)
  const submitRef = useRef<HTMLButtonElement>(null)
  const [reason, setReason] = useState(reasonField?.initial ?? '')
  const [account, setAccount] = useState<RefundAccount>({ bankCode: '', accountNumber: '', holderName: '' })
  const [errors, setErrors] = useState<FieldErrors>({})

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
    // 폼의 첫 칸에 포커스 (입력할 칸이 없으면 제출 버튼)
    const firstField = reasonRef.current ?? bankRef.current ?? submitRef.current
    firstField?.focus()
  }, [])

  function clearError(field: Field) {
    // 고치는 칸의 오류는 바로 지운다 (나머지는 다시 제출할 때 확인)
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  function updateAccount(field: keyof RefundAccount, value: string) {
    setAccount((prev) => ({ ...prev, [field]: value }))
    clearError(field)
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    const answer: OrderActionAnswer = {}
    const found: FieldErrors = {}
    if (reasonField) {
      answer.reason = reason.trim()
      if (reasonField.required && !answer.reason) found.reason = `${reasonField.label.replace(/\s*\(.*\)$/, '')}를 입력해주세요.`
      else if (answer.reason.length > reasonField.maxLength) found.reason = `${reasonField.maxLength}자 이하로 입력해주세요.`
    }
    if (needsRefundAccount) {
      const refundAccount = {
        bankCode: account.bankCode,
        accountNumber: account.accountNumber.trim(),
        holderName: account.holderName.trim(),
      }
      if (!refundAccount.bankCode) found.bankCode = '환불 받을 은행을 선택해주세요.'
      if (!refundAccount.accountNumber) found.accountNumber = '계좌번호를 입력해주세요.'
      else if (!ACCOUNT_NUMBER.test(refundAccount.accountNumber)) found.accountNumber = '계좌번호는 숫자와 - 로 6~20자입니다.'
      if (!refundAccount.holderName) found.holderName = '예금주를 입력해주세요.'
      else if (refundAccount.holderName.length > MAX_HOLDER_NAME) found.holderName = `예금주는 ${MAX_HOLDER_NAME}자 이하입니다.`
      answer.refundAccount = refundAccount
    }
    setErrors(found)
    if (Object.keys(found).length > 0) return
    onAnswer(answer)
  }

  const invalid = (field: Field) => ({
    'aria-invalid': !!errors[field],
    'aria-describedby': errors[field] ? `order-action-${field}-error` : undefined,
  })
  const fieldError = (field: Field) =>
    errors[field] ? (
      <p id={`order-action-${field}-error`} className="cancel-dialog-error" role="alert">
        {errors[field]}
      </p>
    ) : null

  return (
    <dialog
      ref={dialogRef}
      className="card cancel-dialog"
      aria-labelledby="orderActionTitle"
      aria-describedby="orderActionDesc"
      onCancel={(e) => {
        e.preventDefault()
        onAnswer(null)
      }}
    >
      <form onSubmit={submit} noValidate>
        <h2 id="orderActionTitle" className="cancel-dialog-title">{copy.title}</h2>
        <p className="cancel-dialog-order">{orderSummary}</p>
        <p id="orderActionDesc" className="cancel-dialog-desc">{copy.description}</p>

        {reasonField && (
          <>
            <label htmlFor="order-action-reason">{reasonField.label}</label>
            <textarea
              id="order-action-reason"
              ref={reasonRef}
              rows={2}
              maxLength={reasonField.maxLength}
              placeholder={reasonField.placeholder}
              value={reason}
              onChange={(e) => {
                setReason(e.target.value)
                clearError('reason')
              }}
              {...invalid('reason')}
            />
            {fieldError('reason')}
          </>
        )}

        {needsRefundAccount && (
          <>
            <label htmlFor="order-action-bank">은행</label>
            <select
              id="order-action-bank"
              ref={bankRef}
              value={account.bankCode}
              onChange={(e) => updateAccount('bankCode', e.target.value)}
              {...invalid('bankCode')}
            >
              <option value="">은행 선택</option>
              {BANKS.map((b) => (
                <option key={b.code} value={b.code}>
                  {b.name}
                </option>
              ))}
            </select>
            {fieldError('bankCode')}

            <label htmlFor="order-action-account">계좌번호</label>
            <input
              id="order-action-account"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              placeholder="숫자만 또는 - 포함 (예: 110-123-456789)"
              value={account.accountNumber}
              onChange={(e) => updateAccount('accountNumber', e.target.value)}
              {...invalid('accountNumber')}
            />
            {fieldError('accountNumber')}

            <label htmlFor="order-action-holder">예금주</label>
            <input
              id="order-action-holder"
              type="text"
              autoComplete="name"
              maxLength={MAX_HOLDER_NAME}
              value={account.holderName}
              onChange={(e) => updateAccount('holderName', e.target.value)}
              {...invalid('holderName')}
            />
            {fieldError('holderName')}
          </>
        )}

        <div className="cancel-dialog-actions">
          <button ref={submitRef} type="submit" className={`btn ${copy.danger ? 'btn-danger' : 'btn-primary'}`}>
            {copy.submitLabel}
          </button>
          <button type="button" className="btn" onClick={() => onAnswer(null)}>
            닫기
          </button>
        </div>
      </form>
    </dialog>
  )
}
