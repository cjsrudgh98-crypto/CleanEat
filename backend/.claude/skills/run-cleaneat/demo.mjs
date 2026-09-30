// 포트폴리오용 시연 영상 녹화 - server.sh fresh 직후에 (NO_RATE_LIMIT=1 권장)
//   node .claude/skills/run-cleaneat/demo.mjs   -> out/demo/frames/*.jpg + frames.txt (ffmpeg concat 목록, 1280x720)
//   ffmpeg -f concat -i out/demo/frames.txt -vf fps=30,format=yuv420p -c:v libx264 -crf 20 demo.mp4
// (Playwright recordVideo는 전용 ffmpeg 다운로드가 필요하고 화질이 낮아서, CDP screencast 프레임을 직접 모은다)
// 화면 위에 단계 자막과 마우스 커서를 그려서, 소리 없이 봐도 무엇을 하는지 알 수 있게 한다.
import { chromium } from 'playwright-core'
import { existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const BASE = process.env.BASE_URL || 'http://localhost:8080'
const OUT = join(dirname(fileURLToPath(import.meta.url)), 'out', 'demo')
rmSync(OUT, { recursive: true, force: true })
mkdirSync(join(OUT, 'frames'), { recursive: true })
const VIEW = { width: 1280, height: 720 }

async function login(username) {
  for (const password of ['Passw0rd!', 'NewPassw0rd!']) {
    const res = await fetch(`${BASE}/api/auth/login`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }),
    })
    if (res.ok) return res.json()
  }
  throw new Error(`${username} 로그인 실패 - server.sh fresh 후에 실행`)
}

const user = await login('uiuser')
// 알레르기 "밀" + 베지테리언 식단 -> 통밀 크래커 스캔 시 경고, 상품 목록 맞춤 표시
await fetch(`${BASE}/api/users/${user.userId}/profile`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${user.token}` },
  body: JSON.stringify({ allergies: ['밀'], dietTypes: ['VEGETARIAN'] }),
})
const accounts = { uiuser: user, uipartial: await login('uipartial'), uireturn: await login('uireturn'), uiadmin: await login('uiadmin') }

const executablePath = [process.env.BROWSER_PATH, 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe', '/usr/bin/chromium'].find((p) => p && existsSync(p))
const browser = await chromium.launch({ executablePath, headless: true })
const context = await browser.newContext({ viewport: VIEW, locale: 'ko-KR' })

// 자막 / 커서 / 타이틀 카드 - 페이지가 바뀔 때마다 다시 붙는다
await context.addInitScript(() => {
  const css = `
    #demo-cursor{position:fixed;z-index:2147483647;width:22px;height:22px;margin:-3px 0 0 -3px;pointer-events:none;
      background:no-repeat center/contain url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M3 2l7 19 2.5-7.5L20 11z' fill='%23111' stroke='white' stroke-width='1.5'/%3E%3C/svg%3E");
      transition:transform .08s}
    #demo-cursor.down{transform:scale(.8)}
    #demo-ripple{position:fixed;z-index:2147483646;width:36px;height:36px;margin:-18px 0 0 -18px;border-radius:50%;
      border:3px solid #16a36a;pointer-events:none;opacity:0}
    #demo-ripple.on{animation:demo-r .5s ease-out}
    @keyframes demo-r{from{opacity:.9;transform:scale(.3)}to{opacity:0;transform:scale(1.4)}}
    #demo-caption{position:fixed;z-index:2147483645;left:50%;bottom:28px;transform:translateX(-50%);max-width:88%;
      padding:12px 26px;border-radius:999px;background:rgba(17,24,39,.88);color:#fff;font:600 21px/1.35 'Pretendard','Malgun Gothic',sans-serif;
      box-shadow:0 8px 30px rgba(0,0,0,.25);pointer-events:none;transition:opacity .3s;white-space:nowrap}
    #demo-caption b{color:#4ade80;margin-right:10px}
    #demo-title{position:fixed;inset:0;z-index:2147483644;display:flex;flex-direction:column;align-items:center;justify-content:center;
      gap:14px;background:linear-gradient(135deg,#0f3d2e,#16a36a);color:#fff;font-family:'Pretendard','Malgun Gothic',sans-serif;transition:opacity .5s}
    body:has(#demo-title) #demo-cursor{display:none}
    #demo-title h1{margin:0;font-size:64px;letter-spacing:-1px}
    #demo-title p{margin:0;font-size:24px;opacity:.9}
    #demo-title small{margin-top:18px;font-size:17px;opacity:.75}`
  const mount = () => {
    if (document.getElementById('demo-cursor')) return
    const style = document.createElement('style'); style.textContent = css; document.head.appendChild(style)
    for (const id of ['demo-cursor', 'demo-ripple', 'demo-caption']) {
      const el = document.createElement('div'); el.id = id; document.body.appendChild(el)
    }
    const saved = JSON.parse(sessionStorage.getItem('demo-state') || '{}')
    const cursor = document.getElementById('demo-cursor')
    cursor.style.left = (saved.x ?? 640) + 'px'; cursor.style.top = (saved.y ?? 360) + 'px'
    const cap = document.getElementById('demo-caption')
    if (saved.caption) cap.innerHTML = saved.caption; else cap.style.opacity = '0'
    document.addEventListener('mousemove', (e) => {
      cursor.style.left = e.clientX + 'px'; cursor.style.top = e.clientY + 'px'
      const s = JSON.parse(sessionStorage.getItem('demo-state') || '{}'); s.x = e.clientX; s.y = e.clientY
      sessionStorage.setItem('demo-state', JSON.stringify(s))
    }, true)
    document.addEventListener('mousedown', (e) => {
      cursor.classList.add('down')
      const r = document.getElementById('demo-ripple'); r.style.left = e.clientX + 'px'; r.style.top = e.clientY + 'px'
      r.classList.remove('on'); void r.offsetWidth; r.classList.add('on')
    }, true)
    document.addEventListener('mouseup', () => cursor.classList.remove('down'), true)
  }
  window.__caption = (html) => {
    const s = JSON.parse(sessionStorage.getItem('demo-state') || '{}'); s.caption = html
    sessionStorage.setItem('demo-state', JSON.stringify(s))
    const cap = document.getElementById('demo-caption'); if (!cap) return
    cap.style.opacity = html ? '1' : '0'; if (html) cap.innerHTML = html
  }
  window.__title = (h1, p, small) => {
    const t = document.createElement('div'); t.id = 'demo-title'
    t.innerHTML = `<h1>${h1}</h1><p>${p}</p>${small ? `<small>${small}</small>` : ''}`
    document.body.appendChild(t)
  }
  window.__untitle = () => { const t = document.getElementById('demo-title'); if (t) { t.style.opacity = '0'; setTimeout(() => t.remove(), 500) } }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount); else mount()
})

const page = await context.newPage()

// 화면이 바뀔 때마다 오는 프레임을 시각과 함께 저장 -> 프레임 간격을 그대로 영상 길이로
const frames = []
const cdp = await context.newCDPSession(page)
cdp.on('Page.screencastFrame', ({ data, metadata, sessionId }) => {
  const file = `f${String(frames.length).padStart(5, '0')}.jpg`
  writeFileSync(join(OUT, 'frames', file), Buffer.from(data, 'base64'))
  frames.push({ file, t: metadata.timestamp })
  cdp.send('Page.screencastFrameAck', { sessionId }).catch(() => {})
})
await page.goto(BASE + '/')
await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 92, maxWidth: VIEW.width, maxHeight: VIEW.height, everyNthFrame: 1 })
const wait = (ms) => page.waitForTimeout(ms)
const caption = (step, text) => page.evaluate((h) => window.__caption(h), step ? `<b>${step}</b>${text}` : '')

async function as(name, path) {
  await page.evaluate((a) => localStorage.setItem('cleaneat_auth', JSON.stringify(a)), accounts[name])
  await page.goto(BASE + path)
  await page.waitForLoadState('networkidle')
}
// 커서를 요소까지 천천히 옮긴 뒤 클릭
async function click(locator, { pause = 350 } = {}) {
  await locator.scrollIntoViewIfNeeded()
  const box = await locator.boundingBox()
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 18 })
  await wait(pause)
  await locator.click()
}
async function hover(locator) {
  const box = await locator.boundingBox()
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 14 })
}
// 앱의 스크롤 영역(안쪽 div)까지 포함해서 부드럽게 스크롤
async function smoothTo(locator, block = 'center') {
  await locator.evaluate((el, b) => el.scrollIntoView({ behavior: 'smooth', block: b }), block)
  await wait(900)
}

// ---------------- 0. 타이틀 ----------------
await as('uiuser', '/')
await page.evaluate(() => window.__title('CleanEat', '바코드 하나로 성분 분석부터 안심 쇼핑까지',
  'Spring Boot · React · 토스페이먼츠 · Tesseract OCR'))
await wait(3000)
await page.evaluate(() => window.__untitle())
await wait(700)

// ---------------- 1. 바코드 성분 검사 ----------------
await caption('STEP 1', '바코드로 식품 성분 검사')
const barcode = page.getByPlaceholder('예: 3017620422003')
await click(barcode)
await barcode.pressSequentially('8800000001059', { delay: 70 })
await wait(400)
await click(page.getByRole('button', { name: '조회' }))
await page.getByRole('heading', { name: '대안 제품 추천' }).waitFor()
await wait(900)
await caption('STEP 2', '내가 등록한 알레르기 성분(밀)을 바로 경고')
await smoothTo(page.getByText('내가 등록한 알레르기 성분이 들어 있어요', { exact: false }).first())
await wait(2200)
await caption('STEP 3', '위험도가 낮고 내 식단에 맞는 대안 제품 추천')
await smoothTo(page.getByRole('heading', { name: '대안 제품 추천' }), 'start')
await wait(1200)
await click(page.getByRole('button', { name: '담기' }).first())
await wait(1800)

// ---------------- 2. 맞춤 상품 목록 / 상세 ----------------
await caption('STEP 4', '알레르기 상품은 숨기고, 내 식단 상품을 먼저 보여주는 쇼핑몰')
await click(page.getByRole('link', { name: '상품', exact: true }))
await page.waitForLoadState('networkidle')
await wait(1800)
const firstCard = page.locator('a[href^="/products/"]').first()
await hover(firstCard)
await wait(600)
await smoothTo(page.locator('a[href^="/products/"]').nth(6))
await wait(1200)
await caption('STEP 5', '상품 상세 · 성분/식단 정보 · 장바구니')
await click(page.locator('a[href^="/products/"]').nth(1))
await page.waitForLoadState('networkidle')
await wait(1500)
await click(page.getByRole('button', { name: '장바구니 담기' }))
await wait(1500)

// ---------------- 3. 장바구니 -> 결제 ----------------
await caption('STEP 6', '장바구니 → 토스페이먼츠 결제 (카드 · 가상계좌)')
await click(page.getByRole('link', { name: /장바구니/ }).first())
await page.waitForLoadState('networkidle')
await wait(1800)
await click(page.getByRole('button', { name: /주문하기|결제하기|구매하기/ }).first())
await page.waitForLoadState('networkidle')
await wait(2500)
await smoothTo(page.getByRole('heading', { name: '배송 정보' }), 'start')
await wait(1500)
await page.mouse.wheel(0, 600)
await wait(2500)

// ---------------- 4. 부분 취소 ----------------
await caption('STEP 7', '주문 내역 · 상품 단위 부분 취소 (해당 금액만 환불)')
await as('uipartial', '/orders')
const partRow = page.locator('li', { hasText: '서울시 부분취소로 1' }).first()
await partRow.waitFor()
await wait(1500)
await click(partRow.getByRole('button', { name: '이 상품만 취소', exact: true }).last())
const itemForm = page.getByRole('dialog', { name: '상품 취소', exact: true })
await itemForm.waitFor()
await wait(2000)
await click(itemForm.getByRole('button', { name: '이 상품 취소' }))
await partRow.getByText('취소됨').waitFor()
await wait(2500)

// ---------------- 5. 반품 신청 ----------------
await caption('STEP 8', '배송 완료 주문 반품 신청 · 사유 입력 폼')
await as('uireturn', '/orders')
const retItems = page.locator('.card', { has: page.getByRole('heading', { name: '주문 내역' }) }).locator(':scope > ul > li')
await retItems.first().waitFor()
const rowA = retItems.nth(3)
await smoothTo(rowA)
await wait(800)
await click(rowA.getByRole('button', { name: '반품 신청', exact: true }))
const returnForm = page.getByRole('dialog', { name: '반품 신청', exact: true })
await returnForm.waitFor()
await wait(700)
await returnForm.getByLabel('반품 사유').pressSequentially('상자가 찌그러져 왔어요', { delay: 90 })
await wait(600)
await click(returnForm.getByRole('button', { name: '반품 신청', exact: true }))
await rowA.getByText('반품 신청됨').waitFor()
await smoothTo(rowA)
await wait(2200)

// ---------------- 6. 관리자 ----------------
await caption('STEP 9', '관리자 대시보드 · 처리할 주문/반품/재고를 한눈에')
await as('uiadmin', '/admin')
await wait(2500)
await click(page.getByRole('button', { name: '반품 신청', exact: true }))
await wait(2500)
await caption('STEP 10', '매출 통계 · 일별/월별 추이와 많이 팔린 상품')
await click(page.getByRole('link', { name: '매출 통계' }))
const chart = page.getByRole('img', { name: '일별 매출 막대 차트' })
await chart.waitFor()
await smoothTo(chart)
const slots = chart.locator('rect[tabindex="0"]')
const n = await slots.count()
for (const i of [n - 7, n - 5, n - 4, n - 3, n - 2, n - 1]) { // 최근 며칠은 매출이 있는 날
  if (i >= 0 && i < n) { await hover(slots.nth(i)); await wait(550) }
}
await wait(800)
await click(page.getByRole('button', { name: '최근 12개월' }))
await page.getByRole('img', { name: '월별 매출 막대 차트' }).waitFor()
await wait(2200)

// ---------------- 끝 ----------------
await caption('', '')
await page.evaluate(() => window.__title('CleanEat', '먹기 전에, 한 번 더 확인하세요',
  'github.com/cjsrudgh98-crypto/CleanEat'))
await wait(3000)

await cdp.send('Page.stopScreencast')
const end = Date.now() / 1000
const lines = frames.map((f, i) => `file 'frames/${f.file}'\nduration ${((frames[i + 1]?.t ?? end) - f.t).toFixed(3)}`)
lines.push(`file 'frames/${frames.at(-1).file}'`) // concat은 마지막 파일의 duration을 무시하므로 한 번 더
writeFileSync(join(OUT, 'frames.txt'), lines.join('\n') + '\n')
console.log(`frames: ${frames.length}, 길이 ${(end - frames[0].t).toFixed(1)}초 -> ${join(OUT, 'frames.txt')}`)
await browser.close()
