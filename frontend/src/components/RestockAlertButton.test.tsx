import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthState } from '../api/types'
import { testAuth } from '../test/fixtures'
import { RestockAlertButton } from './RestockAlertButton'

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, api: { subscribeRestock: vi.fn(), unsubscribeRestock: vi.fn() } }
})
let currentAuth: AuthState | null = testAuth
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ auth: currentAuth }) }))

const { api, ApiError } = await import('../api/client')
const mocked = vi.mocked(api)

function renderButton(subscribed: boolean | null) {
  const onChange = vi.fn()
  const onRequireLogin = vi.fn()
  render(
    <MemoryRouter>
      <RestockAlertButton productId={7} subscribed={subscribed} onChange={onChange} onRequireLogin={onRequireLogin} />
    </MemoryRouter>,
  )
  return { onChange, onRequireLogin, user: userEvent.setup() }
}

describe('RestockAlertButton (품절 상품 재입고 알림)', () => {
  beforeEach(() => {
    currentAuth = testAuth
  })

  it('비로그인이면 신청 대신 로그인 안내', async () => {
    currentAuth = null
    const { onRequireLogin, user } = renderButton(null)
    await user.click(screen.getByRole('button', { name: '재입고 알림 받기' }))
    expect(onRequireLogin).toHaveBeenCalled()
    expect(mocked.subscribeRestock).not.toHaveBeenCalled()
  })

  it('신청 -> 서버에 신청하고 신청됨으로 바꾼다', async () => {
    mocked.subscribeRestock.mockResolvedValue(undefined)
    const { onChange, user } = renderButton(false)
    await user.click(screen.getByRole('button', { name: '재입고 알림 받기' }))
    expect(mocked.subscribeRestock).toHaveBeenCalledWith(7, testAuth)
    expect(onChange).toHaveBeenCalledWith(true)
  })

  it('신청한 상태면 안내와 알림 취소 버튼', async () => {
    mocked.unsubscribeRestock.mockResolvedValue(undefined)
    const { onChange, user } = renderButton(true)
    expect(screen.getByText(/재입고 알림을 신청했어요/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '알림 취소' }))
    expect(mocked.unsubscribeRestock).toHaveBeenCalledWith(7, testAuth)
    expect(onChange).toHaveBeenCalledWith(false)
  })

  it('이메일이 없는 계정이면 서버 안내와 마이페이지 링크', async () => {
    mocked.subscribeRestock.mockRejectedValue(
      new ApiError(400, '재입고 알림은 메일로 보내드려요. 마이페이지에서 이메일을 먼저 등록해주세요'))
    const { onChange, user } = renderButton(false)
    await user.click(screen.getByRole('button', { name: '재입고 알림 받기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('이메일을 먼저 등록해주세요')
    expect(screen.getByRole('link', { name: '마이페이지로 가기' })).toHaveAttribute('href', '/mypage')
    expect(onChange).not.toHaveBeenCalled()
  })
})
