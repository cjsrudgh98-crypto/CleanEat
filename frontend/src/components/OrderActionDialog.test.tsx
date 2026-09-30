import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { OrderActionDialog, type OrderActionCopy, type ReasonField } from './OrderActionDialog'

const copy: OrderActionCopy = { title: '반품 신청', description: '설명', submitLabel: '신청하기', danger: false }
const requiredReason: ReasonField = { label: '반품 사유', initial: '', required: true, maxLength: 300 }

function renderDialog(props: { reasonField?: ReasonField | null; needsRefundAccount?: boolean } = {}) {
  const onAnswer = vi.fn()
  render(
    <OrderActionDialog
      copy={copy}
      orderSummary="#1 현미 과자 · 9,000원"
      reasonField={props.reasonField === undefined ? requiredReason : props.reasonField}
      needsRefundAccount={props.needsRefundAccount ?? false}
      onAnswer={onAnswer}
    />,
  )
  return { onAnswer, user: userEvent.setup() }
}

describe('OrderActionDialog (주문 취소/반품 공용 폼)', () => {
  it('제목·주문 요약을 보여주고 첫 입력칸에 포커스한다', () => {
    renderDialog()
    expect(screen.getByRole('dialog', { name: '반품 신청' })).toBeInTheDocument()
    expect(screen.getByText('#1 현미 과자 · 9,000원')).toBeInTheDocument()
    expect(screen.getByLabelText('반품 사유')).toHaveFocus()
  })

  it('필수 사유를 비우면 제출하지 않고 오류를 보여준다', async () => {
    const { onAnswer, user } = renderDialog()
    await user.click(screen.getByRole('button', { name: '신청하기' }))
    expect(onAnswer).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('반품 사유를 입력해주세요.')
    expect(screen.getByLabelText('반품 사유')).toHaveAttribute('aria-invalid', 'true')
  })

  it('고치기 시작하면 그 칸의 오류는 바로 사라진다', async () => {
    const { user } = renderDialog()
    await user.click(screen.getByRole('button', { name: '신청하기' }))
    await user.type(screen.getByLabelText('반품 사유'), '파')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('사유는 앞뒤 공백을 빼서 넘긴다', async () => {
    const { onAnswer, user } = renderDialog()
    await user.type(screen.getByLabelText('반품 사유'), '  상자가 찌그러졌어요  ')
    await user.click(screen.getByRole('button', { name: '신청하기' }))
    expect(onAnswer).toHaveBeenCalledWith({ reason: '상자가 찌그러졌어요' })
  })

  it('선택 사유(관리자 취소)는 비워도 제출된다', async () => {
    const { onAnswer, user } = renderDialog({ reasonField: { ...requiredReason, required: false, initial: '기본 사유' } })
    await user.clear(screen.getByLabelText('반품 사유'))
    await user.click(screen.getByRole('button', { name: '신청하기' }))
    expect(onAnswer).toHaveBeenCalledWith({ reason: '' })
  })

  describe('환불 계좌 (입금 끝난 가상계좌 주문)', () => {
    it('은행/계좌번호/예금주를 모두 검사한다', async () => {
      const { onAnswer, user } = renderDialog({ reasonField: null, needsRefundAccount: true })
      expect(screen.getByLabelText('은행')).toHaveFocus()
      await user.click(screen.getByRole('button', { name: '신청하기' }))
      expect(onAnswer).not.toHaveBeenCalled()
      expect(screen.getAllByRole('alert').map((e) => e.textContent)).toEqual([
        '환불 받을 은행을 선택해주세요.',
        '계좌번호를 입력해주세요.',
        '예금주를 입력해주세요.',
      ])
    })

    it('계좌번호 형식이 틀리면 그 칸만 오류', async () => {
      const { onAnswer, user } = renderDialog({ reasonField: null, needsRefundAccount: true })
      await user.selectOptions(screen.getByLabelText('은행'), '신한은행')
      await user.type(screen.getByLabelText('계좌번호'), 'abc')
      await user.type(screen.getByLabelText('예금주'), '홍길동')
      await user.click(screen.getByRole('button', { name: '신청하기' }))
      expect(onAnswer).not.toHaveBeenCalled()
      expect(screen.getAllByRole('alert')).toHaveLength(1)
      expect(screen.getByRole('alert')).toHaveTextContent('계좌번호는 숫자와 - 로 6~20자입니다.')
    })

    it('정상 입력이면 은행 코드와 공백 뺀 값을 넘긴다', async () => {
      const { onAnswer, user } = renderDialog({ needsRefundAccount: true })
      await user.type(screen.getByLabelText('반품 사유'), '단순 변심')
      await user.selectOptions(screen.getByLabelText('은행'), '카카오뱅크')
      await user.type(screen.getByLabelText('계좌번호'), ' 3333-01-1234567 ')
      await user.type(screen.getByLabelText('예금주'), ' 홍길동 ')
      await user.click(screen.getByRole('button', { name: '신청하기' }))
      expect(onAnswer).toHaveBeenCalledWith({
        reason: '단순 변심',
        refundAccount: { bankCode: '90', accountNumber: '3333-01-1234567', holderName: '홍길동' },
      })
    })
  })

  it('닫기 버튼 -> null (아무것도 하지 않음)', async () => {
    const { onAnswer, user } = renderDialog()
    await user.click(screen.getByRole('button', { name: '닫기' }))
    expect(onAnswer).toHaveBeenCalledWith(null)
  })

  it('Esc(dialog cancel 이벤트) -> null', () => {
    const { onAnswer } = renderDialog()
    fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }))
    expect(onAnswer).toHaveBeenCalledWith(null)
  })
})
