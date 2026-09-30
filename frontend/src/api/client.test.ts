import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { makeOrder, testAuth } from '../test/fixtures'
import { api, ApiError, setTokenRefreshedHandler, setUnauthorizedHandler } from './client'

// fetch 한 번의 응답을 흉내 낸다
function respond(status: number, body?: unknown, headers: Record<string, string> = {}) {
  const fetchMock = vi.fn<typeof fetch>(async () =>
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json', ...headers },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('API 요청 공통 처리 (request)', () => {
  const onUnauthorized = vi.fn()
  const onTokenRefreshed = vi.fn()

  beforeEach(() => {
    setUnauthorizedHandler(onUnauthorized)
    setTokenRefreshedHandler(onTokenRefreshed)
  })
  afterEach(() => {
    setUnauthorizedHandler(null)
    setTokenRefreshedHandler(null)
    vi.unstubAllGlobals()
  })

  it('로그인 정보가 있으면 Authorization 헤더에 토큰을 싣는다', async () => {
    const fetchMock = respond(200, makeOrder())
    await api.getOrder(1, testAuth)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/orders/1')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer test-token')
  })

  it('응답에 X-Auth-Token이 오면 새 토큰으로 바꿔 끼운다 (만료 연장 / 비밀번호 변경 직후)', async () => {
    respond(204, undefined, { 'X-Auth-Token': 'new-token' })
    await api.changePassword(1, 'Old1234!', 'New1234!', testAuth)
    expect(onTokenRefreshed).toHaveBeenCalledWith('new-token')
  })

  it('토큰을 실은 요청이 401이면 로그인 상태를 정리한다 (다른 기기에서 비밀번호 변경 등)', async () => {
    respond(401, { message: '인증이 필요합니다' })
    await expect(api.getOrder(1, testAuth)).rejects.toMatchObject({ status: 401, message: '로그인이 필요합니다.' })
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('다른 오류는 서버 메시지와 본문을 그대로 전달한다 (예: 알레르기 확인이 필요한 장바구니 담기 409)', async () => {
    respond(409, { message: '알레르기 성분이 있어요', code: 'ALLERGY_CONFIRM_REQUIRED' })
    const error = await api.getOrder(1, testAuth).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, message: '알레르기 성분이 있어요', body: { code: 'ALLERGY_CONFIRM_REQUIRED' } })
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('오류 본문이 JSON이 아니면 기본 메시지', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('<html>Bad Gateway</html>', { status: 502 })))
    await expect(api.getOrder(1, testAuth)).rejects.toMatchObject({ status: 502, message: '요청을 처리하지 못했습니다.' })
  })

  it('반품 신청은 사유와 (가상계좌면) 환불 계좌를 JSON으로 보낸다', async () => {
    const fetchMock = respond(200, makeOrder({ status: 'RETURN_REQUESTED' }))
    const refund = { bankCode: '88', accountNumber: '110-123-456789', holderName: '홍길동' }
    await api.requestReturn(7, '포장 불량', testAuth, refund)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/orders/7/return')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ reason: '포장 불량', refundAccount: refund })
  })
})
