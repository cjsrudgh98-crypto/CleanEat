// CleanEat 화면 조종 - 설치된 Edge/Chrome을 headless로 조종 (playwright-core, 브라우저 다운로드 없음)
//
//   node driver.mjs shot <경로> [--as <아이디>] [--scroll <글자>] [--out <파일명>] [--full]
//       로그인한 상태(--as)로 화면을 열어 캡처 -> out/shots/<파일명>
//   node driver.mjs check
//       이번 변경들의 화면 시나리오 전체 (server.sh fresh 직후에만 - 데이터를 바꾼다)
//
// 환경변수: CLEANEAT_URL (기본 http://localhost:8080), BROWSER_PATH (기본: Edge -> Chrome 순서로 찾음)
import { chromium } from 'playwright-core'
import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const BASE = process.env.CLEANEAT_URL ?? 'http://localhost:8080'
const SHOTS = fileURLToPath(new URL('./out/shots/', import.meta.url))
mkdirSync(SHOTS, { recursive: true })
const results = []
const check = (name, ok, detail = '') => {
  results.push(ok)
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? `  [${detail}]` : ''}`)
}

// seed.py가 만든 계정의 비밀번호 (check가 uiuser 비밀번호를 바꾸므로 둘 다 시도)
const PASSWORDS = ['Passw0rd!', 'NewPassw0rd!']

async function login(username) {
  for (const password of PASSWORDS) {
    const res = await fetch(`${BASE}/api/auth/login`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    if (res.ok) return res.json()
    if (res.status === 429) throw new Error('로그인 횟수 제한(429) - 5분 기다리거나 NO_RATE_LIMIT=1 로 서버를 다시 띄우세요')
  }
  throw new Error(`${username} 로그인 실패 - server.sh fresh 로 데이터를 준비했는지 확인`)
}

function browserPath() {
  const candidates = [
    process.env.BROWSER_PATH,
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/microsoft-edge',
  ]
  const found = candidates.find((p) => p && existsSync(p))
  if (!found) throw new Error('Edge/Chrome을 찾지 못했습니다 - BROWSER_PATH로 지정하세요')
  return found
}

const browser = await chromium.launch({ executablePath: browserPath(), headless: true })

async function newPage(auth) {
  const context = await browser.newContext({ viewport: { width: 420, height: 900 }, locale: 'ko-KR' })
  // 처음 한 번만 넣는다 - 매번 넣으면 앱이 바꿔 저장한 새 토큰을 옛 토큰으로 되돌려 버린다
  if (auth) {
    await context.addInitScript((a) => {
      if (!localStorage.getItem('cleaneat_auth')) localStorage.setItem('cleaneat_auth', JSON.stringify(a))
    }, auth)
  }
  const page = await context.newPage()
  page.consoleErrors = []
  page.on('console', (m) => m.type() === 'error' && page.consoleErrors.push(m.text()))
  page.on('pageerror', (e) => page.consoleErrors.push(String(e)))
  return page
}

const [command, ...args] = process.argv.slice(2)
const option = (name) => {
  const i = args.indexOf(name)
  return i >= 0 ? args[i + 1] : undefined
}

// Git Bash(MSYS)는 "/orders" 같은 인자를 "C:/Program Files/Git/orders"로 바꿔서 넘긴다 -
// 앞 슬래시 없이("orders") 넘기는 것을 권장하고, 바뀌어 들어온 경우도 되돌린다
function pagePath(raw = '/') {
  const unmangled = raw.replace(/^[A-Za-z]:[\\/].*?[\\/]Git(?=[\\/]|$)/, '')
  return unmangled.startsWith('/') ? unmangled : '/' + unmangled
}

if (command === 'shot') {
  const path = pagePath(args.find((a, i) => !a.startsWith('--') && !args[i - 1]?.startsWith('--')))
  const as = option('--as')
  const page = await newPage(as ? await login(as) : null)
  await page.goto(BASE + path)
  await page.waitForLoadState('networkidle')
  const scrollTo = option('--scroll')
  if (scrollTo) await page.getByText(scrollTo).first().scrollIntoViewIfNeeded()
  const file = SHOTS + (option('--out') ?? `${path.replace(/[^\w가-힣]+/g, '_').replace(/^_|_$/g, '') || 'home'}.png`)
  await page.screenshot({ path: file, fullPage: args.includes('--full') })
  console.log(`screenshot: ${file}`)
  if (page.consoleErrors.length) console.log(`console errors: ${page.consoleErrors.join(' | ')}`)
  await browser.close()
  process.exit(0)
}

if (command !== 'check') {
  console.error('사용법: node driver.mjs shot <경로> [--as <아이디>] [--scroll <글자>] [--out <파일>] [--full] | node driver.mjs check')
  await browser.close()
  process.exit(2)
}

// ================= check: 화면 시나리오 전체 (server.sh fresh 직후 데이터 기준) =================
// ---------------- 고객 ----------------
const user = await login('uiuser')
const page = await newPage(user)

console.log('=== 스캔 기록 (홈) ===')
await page.goto(BASE + '/')
const history = page.locator('.card', { has: page.getByRole('heading', { name: '최근 스캔 기록' }) })
await history.getByRole('button', { name: '더 보기' }).waitFor()
const historyCount = () => history.locator('li').count()
check('처음 20개 + 더 보기 버튼', (await historyCount()) === 20, `${await historyCount()}개`)
await history.scrollIntoViewIfNeeded()
await page.screenshot({ path: SHOTS + '1-history-first-page.png', fullPage: true })
await history.getByRole('button', { name: '더 보기' }).click()
// 누르면 글자가 "불러오는 중..."으로 바뀌므로, 글자가 아니라 버튼 자체가 없어질 때까지(마지막 페이지) 기다린다
await history.locator('button', { hasText: /더 보기|불러오는 중/ }).waitFor({ state: 'detached' })
check('더 보기 후 22개, 버튼 사라짐', (await historyCount()) === 22, `${await historyCount()}개`)

console.log('\n=== 주문 내역 ===')
await page.goto(BASE + '/orders')
const orderCard = page.locator('.card', { has: page.getByRole('heading', { name: '주문 내역' }) })
await orderCard.getByRole('button', { name: '더 보기' }).waitFor()
const orderItems = orderCard.locator(':scope > ul > li')
check('처음 20건 + 더 보기 버튼', (await orderItems.count()) === 20, `${await orderItems.count()}건`)
await orderCard.getByRole('button', { name: '더 보기' }).click()
await orderCard.locator('button', { hasText: /더 보기|불러오는 중/ }).waitFor({ state: 'detached' })
check('더 보기 후 22건', (await orderItems.count()) === 22, `${await orderItems.count()}건`)

console.log('\n=== 가상계좌 주문 취소 - 환불 계좌 입력 폼 ===')
// 가장 최근 주문(22번)이 입금 끝난 가상계좌 주문
const vaOrder = orderItems.first()
const dialogs = []
let answers = []
page.on('dialog', async (d) => {
  dialogs.push(`${d.type()}: ${d.message().split('\n').pop()}`)
  const a = answers.shift()
  if (a === undefined || a === '__dismiss__') await d.dismiss()
  else if (a === '__accept__') await d.accept()
  else await d.accept(a)
})
let cancelRequests = 0
page.on('request', (r) => { if (r.url().includes('/cancel')) cancelRequests++ })

const form = page.getByRole('dialog', { name: '주문 취소 · 환불 계좌 입력' })
await vaOrder.getByRole('button', { name: '주문 취소' }).click()
await form.waitFor()
check('취소 버튼 -> 환불 계좌 폼이 뜸 (브라우저 확인창 없이)', dialogs.length === 0 && await form.isVisible())
check('폼에 주문 요약 표시', await form.getByText('#22').isVisible(), await form.locator('.cancel-dialog-order').textContent())
check('처음 포커스는 은행 선택', await page.evaluate(() => document.activeElement?.id) === 'order-action-bank')
await page.screenshot({ path: SHOTS + '8-refund-form.png' })

await form.getByRole('button', { name: '취소하고 환불 요청' }).click()
const fieldErrors = await form.locator('.cancel-dialog-error').allTextContents()
check('빈 칸으로 제출 -> 칸마다 오류 3개, 요청 없음', fieldErrors.length === 3 && cancelRequests === 0, fieldErrors.join(' / '))
await page.screenshot({ path: SHOTS + '9-refund-form-errors.png' })

await form.getByLabel('은행').selectOption({ label: '신한은행' })
await form.getByLabel('계좌번호').fill('abc')
await form.getByLabel('예금주').fill('홍길동')
await form.getByRole('button', { name: '취소하고 환불 요청' }).click()
const afterFix = await form.locator('.cancel-dialog-error').allTextContents()
check('계좌번호 형식 오류만 남음', afterFix.length === 1 && afterFix[0].includes('계좌번호') && cancelRequests === 0, afterFix.join(' / '))
check('잘못된 칸은 aria-invalid 표시', await form.getByLabel('계좌번호').getAttribute('aria-invalid') === 'true')

await form.getByRole('button', { name: '닫기' }).click()
await form.waitFor({ state: 'detached' })
check('닫기 -> 폼 닫힘, 요청 없음', cancelRequests === 0)

await vaOrder.getByRole('button', { name: '주문 취소' }).click()
await form.waitFor()
check('다시 열면 빈 폼', await form.getByLabel('계좌번호').inputValue() === '')
await page.keyboard.press('Escape')
await form.waitFor({ state: 'detached' })
check('Esc -> 폼 닫힘, 요청 없음', cancelRequests === 0)

await vaOrder.getByRole('button', { name: '주문 취소' }).click()
await form.waitFor()
await form.getByLabel('은행').selectOption({ label: '신한은행' })
await form.getByLabel('계좌번호').fill(' 110-123-456789 ')
await form.getByLabel('예금주').fill(' 홍길동 ')
const cancelRequest = page.waitForRequest((r) => r.url().includes('/api/orders/22/cancel'))
await form.getByRole('button', { name: '취소하고 환불 요청' }).click()
const sent = JSON.parse((await cancelRequest).postData() ?? 'null')
check('정상 입력 -> 앞뒤 공백 없이 환불 계좌 전송 (신한=88)',
  sent?.refundAccount?.bankCode === '88' && sent.refundAccount.accountNumber === '110-123-456789'
  && sent.refundAccount.holderName === '홍길동', JSON.stringify(sent))
await form.waitFor({ state: 'detached' })
const errorBox = vaOrder.locator('.error-box')
await errorBox.waitFor()
// 가짜 paymentKey라 토스 테스트 서버가 거절하는 것이 정상 - 화면에 토스 메시지가 그대로 보여야 한다
check('토스 응답(가짜 결제키 거절)이 그 주문 아래 표시', (await errorBox.textContent()).length > 0, await errorBox.textContent())
check('취소가 실패해도 주문 목록은 그대로 (22건)', (await orderItems.count()) === 22, `${await orderItems.count()}건`)
await vaOrder.scrollIntoViewIfNeeded()
await page.screenshot({ path: SHOTS + '2-va-cancel-result.png', fullPage: false })

console.log('\n=== 일반 카드 주문 취소 - 환불 계좌를 묻지 않음 ===')
await page.goto(BASE + '/orders/21')
await page.getByRole('button', { name: '주문 취소' }).waitFor()
dialogs.length = 0
answers = ['__accept__']
const cardCancel = page.waitForRequest((r) => r.url().includes('/api/orders/21/cancel'))
await page.getByRole('button', { name: '주문 취소' }).click()
const cardReq = await cardCancel
check('확인창 1번만, 본문 없이 요청', dialogs.length === 1 && !cardReq.postData(), `${dialogs.join(' | ')} body=${cardReq.postData()}`)
await page.locator('.error-box').waitFor()
check('상세 화면에서 취소 실패해도 주문 상세는 그대로', await page.getByText('서울시 테스트로 21').isVisible()
  && await page.getByRole('link', { name: '주문 내역으로' }).isVisible())
await page.screenshot({ path: SHOTS + '7-detail-cancel-failed.png', fullPage: false })

console.log('\n=== 비밀번호 변경 후에도 이 기기는 로그인 유지 ===')
await page.goto(BASE + '/mypage')
await page.locator('#currentPassword').fill('Passw0rd!')
await page.locator('#newPassword').fill('NewPassw0rd!')
await page.locator('#confirmPassword').fill('NewPassw0rd!')
const pwRes = page.waitForResponse((r) => r.url().includes('/password'))
await page.getByRole('button', { name: '비밀번호 변경' }).click()
const pw = await pwRes
await page.getByText('비밀번호가 변경되었습니다.').waitFor()
const storedToken = await page.evaluate(() => JSON.parse(localStorage.getItem('cleaneat_auth')).token)
check('응답 헤더의 새 토큰으로 저장된 토큰이 바뀜', pw.headers()['x-auth-token'] === storedToken && storedToken !== user.token)
await page.screenshot({ path: SHOTS + '3-password-changed.png', fullPage: false })
await page.goto(BASE + '/orders')
await orderCard.getByRole('button', { name: '더 보기' }).waitFor()
check('변경 후 주문 내역 다시 열어도 로그인 유지(목록 표시)', (await orderItems.count()) === 20)
const oldTokenMe = await fetch(`${BASE}/api/auth/me`, { headers: { Authorization: `Bearer ${user.token}` } })
check('변경 전 토큰은 더 이상 안 됨 (다른 기기 로그아웃)', oldTokenMe.status === 401, `${oldTokenMe.status}`)
check('고객 화면 콘솔 에러 없음 (토스 거절 400 제외)',
  page.consoleErrors.filter((e) => !e.includes('400')).length === 0, page.consoleErrors.join(' | '))

// ---------------- 관리자 ----------------
console.log('\n=== 관리자 주문 목록 ===')
const admin = await login('uiadmin')
const adminPage = await newPage(admin)
await adminPage.goto(BASE + '/admin')
const more = adminPage.getByRole('button', { name: '더 보기' })
await more.waitFor()
const adminRows = adminPage.locator('ul.admin-list > li')
// uiuser 22건(21번 취소는 토스가 거절해서 그대로) + uipartial 1건 = 23건 모두 "배송 준비 필요"
check('배송 준비 필요 20건 + 더 보기', (await adminRows.count()) === 20, `${await adminRows.count()}건`)
await more.click()
await adminPage.locator('button', { hasText: /더 보기|불러오는 중/ }).waitFor({ state: 'detached' })
check('더 보기 후 23건', (await adminRows.count()) === 23, `${await adminRows.count()}건`)
await adminPage.screenshot({ path: SHOTS + '4-admin-orders.png', fullPage: false })
console.log('\n=== 관리자 - 가상계좌 주문 취소: 사유 + 환불 계좌를 한 폼에서 ===')
let adminPrompts = 0
adminPage.on('dialog', (d) => { adminPrompts++; d.dismiss() })
const vaRow = adminRows.filter({ hasText: '#22' })
await vaRow.getByRole('button', { name: '주문 취소' }).click()
const adminForm = adminPage.getByRole('dialog', { name: '주문 취소 · 환불 계좌 입력' })
await adminForm.waitFor()
check('브라우저 prompt 없이 폼 하나로', adminPrompts === 0)
check('사유 칸에 기본 사유가 채워져 있고 먼저 포커스',
  await adminForm.getByLabel('취소 사유 (고객에게 보여요)').inputValue() === '판매자 사정으로 주문 취소'
  && await adminPage.evaluate(() => document.activeElement?.id) === 'order-action-reason')
check('관리자 폼은 "고객 계좌로" 안내', (await adminForm.locator('.cancel-dialog-desc').textContent()).includes('고객 계좌'))
await adminForm.getByLabel('취소 사유 (고객에게 보여요)').fill('재고 부족으로 취소')
await adminForm.getByRole('button', { name: '취소하고 환불 요청' }).click()
check('환불 계좌를 비우면 계좌 오류 3개만 (사유는 통과)', (await adminForm.locator('.cancel-dialog-error').count()) === 3)
await adminPage.screenshot({ path: SHOTS + '11-admin-va-form-errors.png' })
await adminForm.getByLabel('은행').selectOption({ label: 'KB국민은행' })
await adminForm.getByLabel('계좌번호').fill('123456789012')
await adminForm.getByLabel('예금주').fill('홍길동')
const adminCancelReq = adminPage.waitForRequest((r) => r.url().includes('/api/admin/orders/22/cancel'))
await adminForm.getByRole('button', { name: '취소하고 환불 요청' }).click()
const adminSent = JSON.parse((await adminCancelReq).postData() ?? 'null')
check('관리자 요청에 사유 + 환불 계좌 (국민=06)', adminSent?.reason === '재고 부족으로 취소'
  && adminSent?.refundAccount?.bankCode === '06' && adminSent.refundAccount.accountNumber === '123456789012',
  JSON.stringify(adminSent))
await vaRow.locator('.error-box').waitFor()
check('토스 거절 메시지가 그 주문 줄에 표시', (await vaRow.locator('.error-box').textContent()).length > 0,
  await vaRow.locator('.error-box').textContent())

console.log('\n=== 관리자 - 카드 주문 취소: 사유만 묻는 폼 ===')
const cardRow = adminRows.filter({ hasText: '#20' })
await cardRow.getByRole('button', { name: '주문 취소' }).click()
const cardForm = adminPage.getByRole('dialog', { name: '주문 취소', exact: true })
await cardForm.waitFor()
check('은행/계좌 칸 없음, 환불 안내 표시', (await cardForm.getByLabel('은행').count()) === 0
  && (await cardForm.locator('.cancel-dialog-desc').textContent()).includes('고객에게 환불'),
  await cardForm.locator('.cancel-dialog-desc').textContent())
await adminPage.screenshot({ path: SHOTS + '12-admin-card-form.png' })
await cardForm.getByLabel('취소 사유 (고객에게 보여요)').fill('')
const cardAdminReq = adminPage.waitForRequest((r) => r.url().includes('/api/admin/orders/20/cancel'))
await cardForm.getByRole('button', { name: '주문 취소' }).click()
const cardSent = JSON.parse((await cardAdminReq).postData() ?? 'null')
check('사유를 비우면 빈 사유로 전송 (서버가 기본 사유 사용), 환불 계좌 없음',
  cardSent?.reason === '' && cardSent.refundAccount === undefined, JSON.stringify(cardSent))
await cardRow.locator('.error-box').waitFor()
await vaRow.scrollIntoViewIfNeeded()
await adminPage.screenshot({ path: SHOTS + '10-admin-cancel-result.png' })
// ---------------- 반품 (seed.py의 uireturn: A 카드 / B 가상계좌 / C 신청됨(결제키 없음) / D 기간 지남) ----------------
console.log('\n=== 반품 - 고객 ===')
const returner = await newPage(await login('uireturn'))
await returner.goto(BASE + '/orders')
const retCard = returner.locator('.card', { has: returner.getByRole('heading', { name: '주문 내역' }) })
const retItems = retCard.locator(':scope > ul > li')
await retItems.first().waitFor()
// 최신순: D, C, B, A. 버튼/폼 이름은 exact로 찾는다 ('반품 신청'이 '반품 신청 철회'에도 부분 일치하므로)
const [rowD, rowC, rowB, rowA] = [0, 1, 2, 3].map((i) => retItems.nth(i))
check('기간 지난 주문(D)에는 반품 버튼 없음', (await rowD.getByRole('button', { name: '반품 신청', exact: true }).count()) === 0)
check('신청된 주문(C)은 "반품 신청됨" + 철회 버튼', (await rowC.getByText('반품 신청됨').count()) === 1
  && (await rowC.getByRole('button', { name: '반품 신청 철회' }).count()) === 1)
check('배송 완료 주문(A)에 반품 신청 버튼 + 마감일 안내', (await rowA.getByRole('button', { name: '반품 신청', exact: true }).count()) === 1
  && (await rowA.getByText(/까지 반품을 신청할 수 있어요/).count()) === 1)
await rowA.scrollIntoViewIfNeeded()
await returner.screenshot({ path: SHOTS + '13-return-buttons.png' })

const returnForm = returner.getByRole('dialog', { name: '반품 신청', exact: true })
await rowA.getByRole('button', { name: '반품 신청', exact: true }).click()
await returnForm.waitFor()
check('카드 주문 반품 폼: 사유 칸만 (은행 칸 없음), 사유에 포커스',
  (await returnForm.getByLabel('은행').count()) === 0 && await returner.evaluate(() => document.activeElement?.id) === 'order-action-reason')
await returnForm.getByRole('button', { name: '반품 신청', exact: true }).click()
check('사유 없이 제출 -> 오류', (await returnForm.locator('.cancel-dialog-error').allTextContents()).join('').includes('반품 사유'))
await returner.screenshot({ path: SHOTS + '14-return-form.png' })
await returnForm.getByLabel('반품 사유').fill('상자가 찌그러져 왔어요')
const retReq = returner.waitForRequest((r) => r.url().endsWith(`/return`) && r.method() === 'POST')
await returnForm.getByRole('button', { name: '반품 신청', exact: true }).click()
const retBody = JSON.parse((await retReq).postData() ?? 'null')
await rowA.getByText('반품 신청됨').waitFor()
check('신청 -> 사유 전송, 상태 "반품 신청", 철회 버튼', retBody?.reason === '상자가 찌그러져 왔어요'
  && (await rowA.locator('.badge').first().textContent()) === '반품 신청'
  && (await rowA.getByRole('button', { name: '반품 신청 철회' }).count()) === 1, JSON.stringify(retBody))

returner.once('dialog', (d) => d.accept())
await rowA.getByRole('button', { name: '반품 신청 철회' }).click()
await rowA.getByRole('button', { name: '반품 신청', exact: true }).waitFor()
check('철회 -> 배송 완료로 돌아가고 다시 신청 가능', (await rowA.locator('.badge').first().textContent()) === '배송 완료')
// 관리자 확인용으로 다시 신청해 둔다
await rowA.getByRole('button', { name: '반품 신청', exact: true }).click()
await returnForm.getByLabel('반품 사유').fill('상자가 찌그러져 왔어요')
await returnForm.getByRole('button', { name: '반품 신청', exact: true }).click()
await rowA.getByText('반품 신청됨').waitFor()

await rowB.getByRole('button', { name: '반품 신청', exact: true }).click()
const vaReturnForm = returner.getByRole('dialog', { name: '반품 신청 · 환불 계좌 입력' })
await vaReturnForm.waitFor()
await vaReturnForm.getByLabel('반품 사유').fill('단순 변심')
await vaReturnForm.getByLabel('은행').selectOption({ label: '카카오뱅크' })
await vaReturnForm.getByLabel('계좌번호').fill('3333-01-1234567')
await vaReturnForm.getByLabel('예금주').fill('반품고객')
const vaRetReq = returner.waitForRequest((r) => r.url().endsWith(`/return`) && r.method() === 'POST')
await vaReturnForm.getByRole('button', { name: '반품 신청', exact: true }).click()
const vaRetBody = JSON.parse((await vaRetReq).postData() ?? 'null')
await rowB.getByText('반품 신청됨').waitFor()
check('가상계좌 주문 반품: 환불 계좌까지 전송 (카카오뱅크=90)', vaRetBody?.refundAccount?.bankCode === '90'
  && vaRetBody.refundAccount.holderName === '반품고객', JSON.stringify(vaRetBody))

console.log('\n=== 반품 - 관리자 ===')
await adminPage.goto(BASE + '/admin')
const returnTile = adminPage.locator('.admin-tile', { hasText: '반품 신청' })
await returnTile.waitFor()
check('요약에 반품 신청 3건 (강조 표시)', (await returnTile.locator('strong').textContent()) === '3건'
  && (await returnTile.getAttribute('class')).includes('alert'))
await adminPage.getByRole('button', { name: '반품 신청', exact: true }).click()
const retRows = adminPage.locator('ul.admin-list > li')
await retRows.filter({ hasText: '사이즈가 달라요' }).waitFor()
check('"반품 신청" 필터 -> 3건', (await retRows.count()) === 3, `${await retRows.count()}건`)
const adminRowB = retRows.filter({ hasText: '단순 변심' })
check('가상계좌 반품 줄에 환불 계좌 요약(끝 4자리만)', (await adminRowB.getByText('카카오뱅크 ****4567 반품고객').count()) === 1)
await adminPage.screenshot({ path: SHOTS + '15-admin-returns.png' })

// C: 결제키 없는 주문 -> 토스 없이 승인이 끝까지 된다
const adminRowC = retRows.filter({ hasText: '사이즈가 달라요' })
await adminRowC.getByRole('button', { name: '반품 승인(환불)' }).click()
const approveForm = adminPage.getByRole('dialog', { name: '반품 승인 · 환불' })
await approveForm.waitFor()
check('승인 폼에 고객 사유와 환불 금액 안내', (await approveForm.locator('.cancel-dialog-desc').textContent()).includes('사이즈가 달라요'))
await adminPage.screenshot({ path: SHOTS + '16-admin-approve-form.png' })
const approveRes = adminPage.waitForResponse((r) => r.url().includes('/return/approve'))
await approveForm.getByRole('button', { name: '승인하고 환불' }).click()
const approved = await (await approveRes).json()
check('승인 -> 반품 완료', approved.status === 'RETURNED' && !!approved.returnedAt, approved.status)

// A: 가짜 결제키 -> 토스가 거절 -> 그 줄에 오류, 상태 그대로
const adminRowA = retRows.filter({ hasText: '상자가 찌그러져 왔어요' })
await adminRowA.getByRole('button', { name: '반품 승인(환불)' }).click()
await approveForm.waitFor()
await approveForm.getByRole('button', { name: '승인하고 환불' }).click()
await adminRowA.locator('.error-box').waitFor()
check('토스 환불 거절 -> 그 줄에 오류, 반품 신청 상태 유지', (await adminRowA.locator('.badge').first().textContent()) === '반품 신청',
  await adminRowA.locator('.error-box').textContent())

// B: 거절 (사유 필수)
await adminRowB.getByRole('button', { name: '반품 거절' }).click()
const rejectForm = adminPage.getByRole('dialog', { name: '반품 거절' })
await rejectForm.waitFor()
await rejectForm.getByRole('button', { name: '반품 거절' }).click()
check('거절 사유 없이 제출 -> 오류', (await rejectForm.locator('.cancel-dialog-error').count()) === 1)
await rejectForm.getByLabel('거절 사유 (고객에게 보여요)').fill('개봉 후 사용한 상품은 반품이 어렵습니다')
const rejectRes = adminPage.waitForResponse((r) => r.url().includes('/return/reject'))
await rejectForm.getByRole('button', { name: '반품 거절' }).click()
const rejected = await (await rejectRes).json()
check('거절 -> 배송 완료 + 거절 사유', rejected.status === 'DELIVERED' && rejected.returnRejectReason === '개봉 후 사용한 상품은 반품이 어렵습니다')

console.log('\n=== 반품 - 고객이 결과 확인 ===')
await returner.goto(BASE + '/orders')
await retItems.first().waitFor()
check('C: 반품 완료 안내', (await rowC.getByText('반품 완료').count()) >= 1)
check('B: 거절 사유 안내, 다시 신청 버튼 없음', (await rowB.getByText('개봉 후 사용한 상품은 반품이 어렵습니다').count()) === 1
  && (await rowB.getByRole('button', { name: '반품 신청', exact: true }).count()) === 0)
await rowB.scrollIntoViewIfNeeded()
await returner.screenshot({ path: SHOTS + '17-return-results.png' })
check('반품 고객 화면 콘솔 에러 없음', returner.consoleErrors.length === 0, returner.consoleErrors.join(' | '))

// ---------------- 부분 취소 (seed.py의 uipartial: 두 줄짜리 결제 완료 주문, 결제키 없음) ----------------
console.log('\n=== 부분 취소 ===')
const partial = await newPage(await login('uipartial'))
await partial.goto(BASE + '/orders')
const partRow = partial.locator('li', { hasText: '서울시 부분취소로 1' }).first()
await partRow.waitFor()
const itemButtons = partRow.getByRole('button', { name: '이 상품만 취소', exact: true })
check('두 줄짜리 결제 완료 주문에 상품마다 "이 상품만 취소"', (await itemButtons.count()) === 2)
await itemButtons.nth(1).click()
const itemForm = partial.getByRole('dialog', { name: '상품 취소', exact: true })
await itemForm.waitFor()
check('상품 취소 폼에 그 상품과 환불 금액', (await itemForm.textContent()).includes('만 환불합니다'), (await itemForm.locator('.cancel-dialog-desc').textContent()))
await partial.screenshot({ path: SHOTS + '18-partial-cancel-form.png' })
await itemForm.getByRole('button', { name: '이 상품 취소' }).click()
await partRow.getByText('취소됨').waitFor()
check('취소한 줄에 "취소됨", 합계에 부분 취소 안내, 남은 한 줄은 버튼 없음',
  (await partRow.getByText(/부분 취소·환불/).count()) === 1 && (await itemButtons.count()) === 0)
await partRow.scrollIntoViewIfNeeded()
await partial.screenshot({ path: SHOTS + '19-partial-cancel-done.png' })
check('부분 취소 화면 콘솔 에러 없음', partial.consoleErrors.length === 0, partial.consoleErrors.join(' | '))

// ---------------- 재입고 알림 (시드 상품 중 품절인 것) ----------------
console.log('\n=== 재입고 알림 ===')
const products = await (await fetch(`${BASE}/api/products`)).json()
const soldOutProduct = products.find((p) => p.stock === 0)
check('시드 데이터에 품절 상품이 있음', !!soldOutProduct, soldOutProduct?.name)
const restockUser = await newPage(await login('uireturn'))
await restockUser.goto(`${BASE}/products/${soldOutProduct.productId}`)
await restockUser.getByText('품절된 상품입니다').waitFor()
await restockUser.getByRole('button', { name: '재입고 알림 받기' }).click()
await restockUser.getByText(/재입고 알림을 신청했어요/).waitFor()
check('품절 상품에서 알림 신청 -> 신청됨 안내 + 취소 버튼',
  (await restockUser.getByRole('button', { name: '알림 취소' }).count()) === 1)
await restockUser.screenshot({ path: SHOTS + '20-restock-subscribed.png' })

// 관리자가 재고를 채우면 바로 메일 (개발 모드라 메일 대신 서버 로그에 남는다)
const serverLog = fileURLToPath(new URL('./out/server.log', import.meta.url))
const logBefore = readFileSync(serverLog, 'utf8').length
const stockRes = await fetch(`${BASE}/api/admin/products/${soldOutProduct.productId}/stock`, {
  method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${admin.token}` },
  body: JSON.stringify({ delta: 5 }),
})
check('관리자 재고 +5', stockRes.ok, `${stockRes.status}`)
await new Promise((r) => setTimeout(r, 800))
const newLog = readFileSync(serverLog, 'utf8').slice(logBefore)
check('재고가 생기자 신청자에게 재입고 메일 (개발 모드 로그)', newLog.includes('재입고 알림') && newLog.includes('uireturn@example.com'),
  newLog.split('\n').find((l) => l.includes('재입고')) ?? '(로그 없음)')
await restockUser.reload()
// isVisible()은 기다리지 않는다 - 새로고침 뒤 상품을 다시 불러와 버튼이 그려질 때까지 기다린다
await restockUser.getByRole('button', { name: '장바구니 담기' }).waitFor()
check('재입고되면 알림 버튼 대신 구매 버튼', (await restockUser.getByRole('button', { name: '재입고 알림 받기' }).count()) === 0)

// ---------------- 매출 통계 ----------------
console.log('\n=== 매출 통계 ===')
await adminPage.goto(BASE + '/admin/sales')
const salesChart = adminPage.getByRole('img', { name: '일별 매출 막대 차트' })
await salesChart.waitFor()
const bars = await salesChart.locator('path').count()
check('최근 30일 일별 막대 (결제일을 흩어 둔 만큼 여러 개)', bars >= 5, `${bars}개`)
check('합계/주문/객단가 칸', (await adminPage.locator('.admin-tile', { hasText: '객단가' }).count()) === 1)
check('많이 팔린 상품 표', (await adminPage.locator('.sales-top-table tbody tr').count()) >= 1)
// 가장 높은 막대 칸에 마우스 -> 툴팁
const slots = salesChart.locator('rect[tabindex="0"]')
await slots.last().hover()
check('막대 칸을 가리키면 툴팁 (날짜/금액/건수)', (await adminPage.getByRole('status').textContent()).includes('원'),
  await adminPage.getByRole('status').textContent())
await salesChart.scrollIntoViewIfNeeded()
await adminPage.screenshot({ path: SHOTS + '21-sales-30d.png' })
await adminPage.getByRole('button', { name: '최근 12개월' }).click()
const monthly = adminPage.getByRole('img', { name: '월별 매출 막대 차트' })
await monthly.waitFor()
check('12개월 -> 월별 12칸', (await monthly.locator('rect[tabindex="0"]').count()) === 12)
await monthly.scrollIntoViewIfNeeded()
await adminPage.screenshot({ path: SHOTS + '22-sales-12m.png' })

check('관리자 화면 콘솔 에러 없음 (토스 거절 400 제외)', adminPage.consoleErrors.filter((e) => !e.includes('400')).length === 0,
  adminPage.consoleErrors.join(' | '))

await browser.close()
const passed = results.filter(Boolean).length
console.log(`\n결과: ${passed}/${results.length} 통과, 스크린샷: ${SHOTS}`)
process.exit(passed === results.length ? 0 : 1)
