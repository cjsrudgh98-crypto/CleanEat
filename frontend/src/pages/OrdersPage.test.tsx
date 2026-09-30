import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { OrderData } from '../api/types'
import { makeDeliveredOrder, makeOrder, page, testAuth } from '../test/fixtures'
import OrdersPage from './OrdersPage'

// 서버 대신 목(mock) - ApiError는 실제 클래스를 쓴다 (화면이 instanceof로 메시지를 꺼냄)
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return {
    ...actual,
    api: {
      getMyOrders: vi.fn(),
      getOrder: vi.fn(),
      cancelOrder: vi.fn(),
      cancelOrderItem: vi.fn(),
      requestReturn: vi.fn(),
      withdrawReturn: vi.fn(),
    },
  }
})
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ auth: testAuth }) }))

const { api, ApiError } = await import('../api/client')
const mocked = vi.mocked(api)

function renderOrders(path = '/orders') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/orders" element={<OrdersPage />} />
        <Route path="/orders/:orderId" element={<OrdersPage />} />
      </Routes>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

// 주문 목록의 한 줄 (주소로 찾는다 - 주문마다 다르게 만들어 둠)
async function orderRow(address: string) {
  return (await screen.findByText(new RegExp(address))).closest('li') as HTMLElement
}

describe('OrdersPage - 주문 내역', () => {
  beforeEach(() => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('처음 불러오기에 실패하면 화면 전체를 오류로 바꾼다 (보여줄 목록이 없으므로)', async () => {
    mocked.getMyOrders.mockRejectedValue(new Error('network'))
    renderOrders()
    expect(await screen.findByText('주문 내역을 불러오지 못했습니다.')).toBeInTheDocument()
  })

  it('취소가 실패해도 목록은 그대로 두고, 오류는 그 주문 아래에만 보여준다 (예전 버그: 목록 전체가 사라짐)', async () => {
    mocked.getMyOrders.mockResolvedValue(page([
      makeOrder({ id: 2, address: '서울시 두번째로' }),
      makeOrder({ id: 1, address: '서울시 첫번째로' }),
    ]))
    mocked.cancelOrder.mockRejectedValue(new ApiError(400, '존재하지 않는 결제 정보 입니다.'))
    const user = renderOrders()

    const second = await orderRow('서울시 두번째로')
    await user.click(within(second).getByRole('button', { name: '주문 취소' }))

    expect(await within(second).findByRole('alert')).toHaveTextContent('존재하지 않는 결제 정보 입니다.')
    expect(await orderRow('서울시 첫번째로')).toBeInTheDocument()
    expect(within(await orderRow('서울시 첫번째로')).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('"더 보기"가 실패해도 목록은 그대로 두고 버튼 위에 오류', async () => {
    mocked.getMyOrders
      .mockResolvedValueOnce(page([makeOrder({ address: '서울시 첫번째로' })], true))
      .mockRejectedValueOnce(new ApiError(500, '서버 내부 오류가 발생했습니다'))
    const user = renderOrders()
    await user.click(await screen.findByRole('button', { name: '더 보기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('서버 내부 오류가 발생했습니다')
    expect(await orderRow('서울시 첫번째로')).toBeInTheDocument()
  })

  describe('부분 취소', () => {
    const twoItems = () => makeOrder({
      id: 9, address: '서울시 부분로', totalAmount: 12000, netAmount: 12000, itemCancelable: true,
      items: [
        { id: 91, productName: '현미 과자', unitPrice: 4500, quantity: 2, cancelled: false },
        { id: 92, productName: '구운 아몬드', unitPrice: 3000, quantity: 1, cancelled: false },
      ],
    })

    it('상품마다 "이 상품만 취소" -> 폼 확인 -> 그 줄에 줄이 그어지고 합계가 줄어든다', async () => {
      const order = twoItems()
      mocked.getMyOrders.mockResolvedValue(page([order]))
      mocked.cancelOrderItem.mockResolvedValue({
        ...order, itemCancelable: false, cancelledAmount: 3000, netAmount: 9000,
        items: [order.items[0], { ...order.items[1], cancelled: true }],
      })
      const user = renderOrders()

      const row = await orderRow('서울시 부분로')
      const buttons = within(row).getAllByRole('button', { name: '이 상품만 취소' })
      expect(buttons).toHaveLength(2)
      await user.click(buttons[1])
      const dialog = screen.getByRole('dialog', { name: '상품 취소' })
      expect(dialog).toHaveTextContent('구운 아몬드 × 1을(를) 취소하고 3,000원만 환불합니다')
      await user.click(within(dialog).getByRole('button', { name: '이 상품 취소' }))

      expect(mocked.cancelOrderItem).toHaveBeenCalledWith(9, 92, testAuth, undefined)
      expect(await within(row).findByText('취소됨')).toBeInTheDocument()
      expect(within(row).getByText(/총 9,000원/)).toBeInTheDocument()
      expect(within(row).getByText(/12,000원 중 3,000원 부분 취소/)).toBeInTheDocument()
      // 한 줄만 남으면 부분 취소 버튼은 없어진다 (남은 건 주문 취소로)
      expect(within(row).queryByRole('button', { name: '이 상품만 취소' })).not.toBeInTheDocument()
    })

    it('서버가 부분 취소 불가라고 하면(배송 준비 등) 상품 취소 버튼을 보이지 않는다', async () => {
      mocked.getMyOrders.mockResolvedValue(page([{ ...twoItems(), itemCancelable: false }]))
      renderOrders()
      const row = await orderRow('서울시 부분로')
      expect(within(row).queryByRole('button', { name: '이 상품만 취소' })).not.toBeInTheDocument()
    })
  })

  describe('반품', () => {
    it('신청 -> 폼에서 사유 입력 -> 상태가 "반품 신청"으로 바뀌고 철회 버튼', async () => {
      const delivered = makeDeliveredOrder({ id: 5, address: '서울시 반품로' })
      mocked.getMyOrders.mockResolvedValue(page([delivered]))
      mocked.requestReturn.mockResolvedValue({
        ...delivered, status: 'RETURN_REQUESTED', returnable: false, returnReason: '상자가 찌그러졌어요',
        returnRequestedAt: '2026-09-30T10:00:00',
      } satisfies OrderData)
      const user = renderOrders()

      const row = await orderRow('서울시 반품로')
      expect(within(row).getByText('10월 6일까지 반품을 신청할 수 있어요.')).toBeInTheDocument()
      await user.click(within(row).getByRole('button', { name: '반품 신청' }))
      await user.type(screen.getByLabelText('반품 사유'), '상자가 찌그러졌어요')
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '반품 신청' }))

      expect(mocked.requestReturn).toHaveBeenCalledWith(5, '상자가 찌그러졌어요', testAuth, undefined)
      expect(await within(row).findByText('반품 신청됨')).toBeInTheDocument()
      expect(within(row).getByRole('button', { name: '반품 신청 철회' })).toBeInTheDocument()
      expect(within(row).queryByRole('button', { name: '반품 신청' })).not.toBeInTheDocument()
    })

    it('폼을 닫으면 요청하지 않는다', async () => {
      mocked.getMyOrders.mockResolvedValue(page([makeDeliveredOrder({ address: '서울시 반품로' })]))
      const user = renderOrders()
      await user.click(within(await orderRow('서울시 반품로')).getByRole('button', { name: '반품 신청' }))
      await user.click(screen.getByRole('button', { name: '닫기' }))
      expect(mocked.requestReturn).not.toHaveBeenCalled()
    })

    it('철회는 확인창을 거쳐 배송 완료로 되돌린다', async () => {
      const requested = makeDeliveredOrder({ id: 5, status: 'RETURN_REQUESTED', returnable: false, returnReason: '변심', address: '서울시 반품로' })
      mocked.getMyOrders.mockResolvedValue(page([requested]))
      mocked.withdrawReturn.mockResolvedValue(makeDeliveredOrder({ id: 5, address: '서울시 반품로' }))
      const user = renderOrders()

      const row = await orderRow('서울시 반품로')
      await user.click(within(row).getByRole('button', { name: '반품 신청 철회' }))
      expect(window.confirm).toHaveBeenCalled()
      expect(mocked.withdrawReturn).toHaveBeenCalledWith(5, testAuth)
      expect(await within(row).findByRole('button', { name: '반품 신청' })).toBeInTheDocument()
    })

    it('상태별 안내: 거절 사유 / 반품 완료 / 발송 후에는 받은 뒤 반품 안내', async () => {
      mocked.getMyOrders.mockResolvedValue(page([
        makeDeliveredOrder({ id: 3, returnable: false, returnRejectReason: '사용 흔적이 있어요', address: '서울시 거절로' }),
        makeDeliveredOrder({ id: 2, status: 'RETURNED', returnable: false, returnedAt: '2026-09-30T10:00:00', address: '서울시 완료로' }),
        makeOrder({ id: 1, status: 'SHIPPING', cancelable: false, address: '서울시 배송로' }),
      ]))
      renderOrders()

      const rejected = await orderRow('서울시 거절로')
      expect(within(rejected).getByText(/사용 흔적이 있어요/)).toBeInTheDocument()
      expect(within(rejected).queryByRole('button', { name: '반품 신청' })).not.toBeInTheDocument()
      expect(within(await orderRow('서울시 완료로')).getByText('반품 완료', { selector: 'strong' })).toBeInTheDocument()
      expect(within(await orderRow('서울시 배송로')).getByText(/받으신 뒤 반품을 신청할 수 있어요/)).toBeInTheDocument()
    })
  })
})
