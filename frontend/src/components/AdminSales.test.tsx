import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { SalesReport } from '../api/types'
import { testAuth } from '../test/fixtures'
import { AdminSales } from './AdminSales'

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, api: { adminSales: vi.fn() } }
})
const { api } = await import('../api/client')
const mocked = vi.mocked(api)

function report(overrides: Partial<SalesReport> = {}): SalesReport {
  return {
    period: '30d',
    unit: 'day',
    from: '2026-09-01',
    to: '2026-09-30',
    totalSales: 19500,
    totalOrders: 3,
    averageOrderAmount: 6500,
    series: [
      { label: '9/28', start: '2026-09-28', sales: 9000, orders: 1 },
      { label: '9/29', start: '2026-09-29', sales: 0, orders: 0 },
      { label: '9/30', start: '2026-09-30', sales: 10500, orders: 2 },
    ],
    topProducts: [
      { productName: '현미 과자', quantity: 3, sales: 13500 },
      { productName: '구운 아몬드', quantity: 2, sales: 6000 },
    ],
    ...overrides,
  }
}

describe('AdminSales (관리자 매출 통계)', () => {
  it('합계/주문/객단가, 많이 팔린 상품 순위를 보여준다', async () => {
    mocked.adminSales.mockResolvedValue(report())
    render(<AdminSales auth={testAuth} />)

    expect(await screen.findByText('19,500원')).toBeInTheDocument()
    expect(screen.getByText('3건')).toBeInTheDocument()
    expect(screen.getByText('6,500원')).toBeInTheDocument()
    expect(screen.getByText('2026.09.01 ~ 2026.09.30')).toBeInTheDocument()
    expect(mocked.adminSales).toHaveBeenCalledWith('30d', testAuth)

    // 표는 두 개: 차트의 "표로 보기"와 많이 팔린 상품 (마지막)
    const top = screen.getAllByRole('table').at(-1)!
    expect(within(top).getAllByRole('row').slice(1).map((r) => r.textContent)).toEqual([
      '1현미 과자3개13,500원',
      '2구운 아몬드2개6,000원',
    ])
  })

  it('차트: 0원인 칸은 막대를 그리지 않고, 칸을 가리키면 금액/주문 수 툴팁', async () => {
    mocked.adminSales.mockResolvedValue(report())
    render(<AdminSales auth={testAuth} />)
    // 차트는 데이터가 온 뒤 자기 너비를 잰 다음 렌더에서 그려진다 - 글자가 아니라 차트를 기다린다
    const chart = await screen.findByRole('img', { name: '일별 매출 막대 차트' })

    // 막대는 매출이 있는 두 칸만
    expect(chart.querySelectorAll('path')).toHaveLength(2)
    fireEvent.focus(screen.getByLabelText('9/30: 매출 10,500원, 주문 2건'))
    const tooltip = screen.getByRole('status')
    expect(tooltip).toHaveTextContent('9/30')
    expect(tooltip).toHaveTextContent('10,500원')
    expect(tooltip).toHaveTextContent('2건')
  })

  it('표로 보기에 모든 칸 (0원 포함)', async () => {
    mocked.adminSales.mockResolvedValue(report())
    render(<AdminSales auth={testAuth} />)
    await screen.findByText('19,500원')
    const chartTable = screen.getAllByRole('table')[0]
    expect(within(chartTable).getAllByRole('row').slice(1).map((r) => r.textContent)).toEqual([
      '9/289,000원1건', '9/290원0건', '9/3010,500원2건',
    ])
  })

  it('기간을 바꾸면 그 기간으로 다시 불러온다 (월별이면 제목도 월별)', async () => {
    mocked.adminSales.mockResolvedValueOnce(report()).mockResolvedValueOnce(report({
      period: '12m', unit: 'month', series: [{ label: '2026.09', start: '2026-09-01', sales: 19500, orders: 3 }],
    }))
    const user = userEvent.setup()
    render(<AdminSales auth={testAuth} />)
    await screen.findByText('일별 매출')

    await user.click(screen.getByRole('button', { name: '최근 12개월' }))

    expect(mocked.adminSales).toHaveBeenLastCalledWith('12m', testAuth)
    expect(await screen.findByText('월별 매출')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '최근 12개월' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('팔린 상품이 없으면 안내 문구', async () => {
    mocked.adminSales.mockResolvedValue(report({ totalSales: 0, totalOrders: 0, averageOrderAmount: 0, topProducts: [] }))
    render(<AdminSales auth={testAuth} />)
    expect(await screen.findByText('이 기간에 팔린 상품이 없습니다.')).toBeInTheDocument()
  })
})
