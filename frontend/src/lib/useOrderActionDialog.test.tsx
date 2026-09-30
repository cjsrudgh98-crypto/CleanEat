import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import type { OrderData } from '../api/types'
import { makeDeliveredOrder, makeOrder, makeVirtualAccountOrder } from '../test/fixtures'
import { type OrderAction, useOrderActionDialog } from './useOrderActionDialog'

// 버튼을 누르면 폼을 열고, 결과를 화면에 JSON으로 적어 두는 시험용 화면
function Harness({ order, action }: { order: OrderData; action: OrderAction }) {
  const { ask, dialog } = useOrderActionDialog()
  const [result, setResult] = useState('')
  return (
    <>
      <button onClick={async () => setResult(JSON.stringify(await ask(order, action, order.items[0])))}>열기</button>
      <output>{result}</output>
      {dialog}
    </>
  )
}

async function open(order: OrderData, action: OrderAction) {
  const user = userEvent.setup()
  render(<Harness order={order} action={action} />)
  await user.click(screen.getByRole('button', { name: '열기' }))
  return user
}

describe('useOrderActionDialog - 목적별 폼 구성', () => {
  it('고객 반품 신청 (카드): 마감일 안내 + 필수 사유, 은행 칸 없음', async () => {
    await open(makeDeliveredOrder(), 'requestReturn')
    const dialog = screen.getByRole('dialog', { name: '반품 신청' })
    expect(dialog).toHaveTextContent('10월 6일까지 신청할 수 있어요')
    expect(dialog).toHaveTextContent('결제한 수단으로 환불됩니다')
    expect(screen.getByLabelText('반품 사유')).toBeInTheDocument()
    expect(screen.queryByLabelText('은행')).not.toBeInTheDocument()
  })

  it('고객 반품 신청 (가상계좌): 사유 + 환불 계좌', async () => {
    await open(makeDeliveredOrder({ paymentMethod: '가상계좌', virtualAccountNumber: 'X1' }), 'requestReturn')
    expect(screen.getByRole('dialog', { name: '반품 신청 · 환불 계좌 입력' })).toHaveTextContent('아래 계좌로 환불됩니다')
    expect(screen.getByLabelText('반품 사유')).toBeInTheDocument()
    expect(screen.getByLabelText('은행')).toBeInTheDocument()
  })

  it('관리자 반품 승인: 입력 없이 고객 사유/환불 금액/계좌 요약을 보여주고 확인만', async () => {
    const user = await open(makeVirtualAccountOrder({
      status: 'RETURN_REQUESTED', returnReason: '사이즈가 달라요', returnRefundAccountSummary: '신한은행 ****6789 홍길동',
    }), 'approveReturn')
    const dialog = screen.getByRole('dialog', { name: '반품 승인 · 환불' })
    expect(dialog).toHaveTextContent('고객 사유: 사이즈가 달라요')
    expect(dialog).toHaveTextContent('9,000원이 환불되고 재고가 복구됩니다')
    expect(dialog).toHaveTextContent('환불 계좌: 신한은행 ****6789 홍길동')
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '승인하고 환불' }))
    expect(screen.getByRole('status')).toHaveTextContent('{}')
  })

  it('관리자 반품 거절: 거절 사유 필수', async () => {
    const user = await open(makeDeliveredOrder({ status: 'RETURN_REQUESTED', returnReason: '변심' }), 'rejectReturn')
    await user.click(screen.getByRole('button', { name: '반품 거절' }))
    expect(screen.getByRole('alert')).toHaveTextContent('거절 사유를 입력해주세요.')
    await user.type(screen.getByLabelText('거절 사유 (고객에게 보여요)'), '사용 흔적')
    await user.click(screen.getByRole('button', { name: '반품 거절' }))
    expect(screen.getByRole('status')).toHaveTextContent('{"reason":"사용 흔적"}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('관리자 취소: 기본 사유가 채워져 있고, 입금 대기 주문은 환불 안내를 하지 않는다', async () => {
    await open(makeOrder({ status: 'AWAITING_DEPOSIT' }), 'adminCancel')
    expect(screen.getByLabelText('취소 사유 (고객에게 보여요)')).toHaveValue('판매자 사정으로 주문 취소')
    expect(screen.getByRole('dialog', { name: '주문 취소' })).toHaveTextContent('주문을 취소합니다.')
  })

  it('관리자 상품 한 줄 취소 (가상계좌): 그 상품 금액만 환불 안내 + 기본 사유 + 고객 환불 계좌', async () => {
    await open(makeVirtualAccountOrder({ items: [{ id: 5, productName: '구운 아몬드', unitPrice: 3000, quantity: 2, cancelled: false }] }),
      'adminCancelItem')
    const dialog = screen.getByRole('dialog', { name: '상품 취소 · 환불 계좌 입력' })
    expect(dialog).toHaveTextContent('구운 아몬드 × 2을(를) 취소하고 6,000원만 환불합니다')
    expect(dialog).toHaveTextContent('고객 계좌로 환불됩니다')
    expect(screen.getByLabelText('취소 사유 (고객에게 보여요)')).toHaveValue('판매자 사정으로 부분 취소')
    expect(screen.getByLabelText('은행')).toBeInTheDocument()
  })

  it('반품 승인 금액은 부분 취소를 뺀 실제 결제 금액', async () => {
    await open(makeOrder({ status: 'RETURN_REQUESTED', returnReason: '변심', totalAmount: 12000, cancelledAmount: 3000, netAmount: 9000 }),
      'approveReturn')
    expect(screen.getByRole('dialog', { name: '반품 승인 · 환불' })).toHaveTextContent('9,000원이 환불되고')
  })

  it('닫으면 null이 돌아온다', async () => {
    const user = await open(makeDeliveredOrder(), 'requestReturn')
    await user.click(screen.getByRole('button', { name: '닫기' }))
    expect(screen.getByRole('status')).toHaveTextContent('null')
  })
})
