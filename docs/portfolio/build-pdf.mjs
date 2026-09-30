// portfolio.html -> CleanEat-portfolio.pdf (설치된 Edge/Chrome으로 인쇄)
//   node docs/portfolio/build-pdf.mjs
// playwright-core는 backend/.claude/skills/run-cleaneat/node_modules 것을 쓴다 (npm install --prefix 그 폴더)
import { createRequire } from 'node:module'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const require = createRequire(join(here, '../../backend/.claude/skills/run-cleaneat/package.json'))
const { chromium } = require('playwright-core')

const executablePath = [process.env.BROWSER_PATH, 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe', '/usr/bin/chromium', '/usr/bin/google-chrome'].find((p) => p && existsSync(p))
const browser = await chromium.launch({ executablePath, headless: true })
const page = await browser.newPage()
await page.goto(pathToFileURL(join(here, 'portfolio.html')).href, { waitUntil: 'networkidle' })
await page.evaluate(() => document.fonts.ready)
const out = join(here, 'CleanEat-portfolio.pdf')
await page.pdf({ path: out, format: 'A4', printBackground: true, preferCSSPageSize: true })
await browser.close()
console.log('saved', out)
