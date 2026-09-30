import { describe, expect, it } from 'vitest'
import { niceTop, shortWon } from './chartScale'

describe('niceTop (y축 최댓값을 깔끔한 금액으로)', () => {
  it('눈금 셋(0 / 가운데 / 맨 위)이 모두 깔끔한 금액이 되도록 올린다', () => {
    expect(niceTop(19500)).toBe(20000)   // 0 / 1만 / 2만
    expect(niceTop(10500)).toBe(20000)
    expect(niceTop(99000)).toBe(100000)  // 0 / 5만 / 10만
    // 14만이면 가운데가 7만이 된다 - 한 단계 위 20만(가운데 10만)으로
    expect(niceTop(123456)).toBe(200000)
  })

  it('매출이 없으면 1만을 기본 눈금으로 (0으로 나누지 않게)', () => {
    expect(niceTop(0)).toBe(10000)
  })
})

describe('shortWon (축 눈금용 짧은 금액)', () => {
  it('1만 미만은 그대로, 이상은 만 단위', () => {
    expect(shortWon(0)).toBe('0')
    expect(shortWon(5000)).toBe('5,000')
    expect(shortWon(20000)).toBe('2만')
    expect(shortWon(15000)).toBe('1.5만')
    expect(shortWon(1200000)).toBe('120만')
  })
})
