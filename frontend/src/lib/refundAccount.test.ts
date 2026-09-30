import { describe, expect, it } from 'vitest'
import { makeOrder, makeVirtualAccountOrder } from '../test/fixtures'
import { BANKS, needsRefundAccount } from './refundAccount'

describe('needsRefundAccount (서버 OrderService.needsRefundAccount와 같은 기준)', () => {
  it('카드 결제는 원래 수단으로 환불되므로 필요 없다', () => {
    expect(needsRefundAccount(makeOrder())).toBe(false)
  })

  it('입금이 끝난 가상계좌 주문은 필요하다 (결제 완료/배송 완료/반품 신청 모두)', () => {
    for (const status of ['PAID', 'PREPARING', 'DELIVERED', 'RETURN_REQUESTED'] as const) {
      expect(needsRefundAccount(makeVirtualAccountOrder({ status }))).toBe(true)
    }
  })

  it('입금 전(입금 대기) 가상계좌 주문은 돌려줄 돈이 없어서 필요 없다', () => {
    expect(needsRefundAccount(makeVirtualAccountOrder({ status: 'AWAITING_DEPOSIT' }))).toBe(false)
  })

  it('주문이 없으면 false', () => {
    expect(needsRefundAccount(null)).toBe(false)
    expect(needsRefundAccount(undefined)).toBe(false)
  })
})

describe('BANKS (서버 BankCodes와 같은 목록)', () => {
  it('은행 코드는 두 자리이고 겹치지 않는다', () => {
    expect(BANKS.every((b) => /^\d{2}$/.test(b.code))).toBe(true)
    expect(new Set(BANKS.map((b) => b.code)).size).toBe(BANKS.length)
  })
})
