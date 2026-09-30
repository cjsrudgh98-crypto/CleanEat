import { useState } from 'react'
import { useInstallPrompt } from '../lib/pwa'

const DISMISS_KEY = 'cleaneat_install_dismissed_at'
// 닫으면 2주 동안 다시 안 띄운다
const DISMISS_DAYS = 14

function readDismissed() {
  try {
    const at = Number(localStorage.getItem(DISMISS_KEY) ?? 0)
    return at > 0 && Date.now() - at < DISMISS_DAYS * 24 * 60 * 60 * 1000
  } catch {
    return false
  }
}

/**
 * 홈 화면에 추가 안내.
 *  - dismissible: 홈 화면 배너처럼 닫을 수 있게 (닫으면 2주 동안 숨김)
 *  - 마이페이지에서는 항상 보여주고, 이미 설치해서 앱으로 열었으면 그렇다고 알려준다
 */
export function InstallCard({ dismissible = false }: { dismissible?: boolean }) {
  const { mode, install } = useInstallPrompt()
  const [dismissed, setDismissed] = useState(() => dismissible && readDismissed())
  const [showIosSteps, setShowIosSteps] = useState(false)

  if (dismissed) return null
  if (dismissible && (mode === 'installed' || mode === 'unsupported')) return null
  if (!dismissible && mode === 'unsupported') return null

  function dismiss() {
    try {
      localStorage.setItem(DISMISS_KEY, String(Date.now()))
    } catch {
      // 저장이 안 되는 환경이면 이번 화면에서만 숨긴다
    }
    setDismissed(true)
  }

  return (
    <div className="card install-card" role="region" aria-label="홈 화면에 추가">
      <img src="/icons/icon-192.png" alt="" className="install-card-icon" width={44} height={44} />
      <div className="install-card-body">
        {mode === 'installed' ? (
          <>
            <strong>홈 화면 앱으로 사용 중이에요</strong>
            <p>CleanEat이 홈 화면에 설치되어 있어요.</p>
          </>
        ) : (
          <>
            <strong>CleanEat을 홈 화면에 추가하세요</strong>
            <p>앱처럼 바로 열어서 장보다가 바코드를 빠르게 검사할 수 있어요.</p>
            {mode === 'ios' && showIosSteps && (
              <ol className="install-steps">
                <li>
                  사파리 아래쪽 <b>공유</b> 버튼(
                  <svg width="14" height="14" viewBox="0 0 24 24" aria-label="공유 아이콘" role="img" style={{ verticalAlign: '-2px' }}>
                    <path d="M12 3v12M7 8l5-5 5 5M5 12v8h14v-8" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                  )을 누르세요
                </li>
                <li>
                  목록에서 <b>홈 화면에 추가</b>를 누르세요
                </li>
              </ol>
            )}
          </>
        )}
      </div>
      {mode === 'prompt' && (
        <button type="button" className="btn btn-primary install-card-action" onClick={() => install()}>
          설치
        </button>
      )}
      {mode === 'ios' && !showIosSteps && (
        <button type="button" className="btn btn-primary install-card-action" onClick={() => setShowIosSteps(true)}>
          방법 보기
        </button>
      )}
      {dismissible && (
        <button type="button" className="icon-btn install-card-close" aria-label="닫기" onClick={dismiss}>
          ×
        </button>
      )}
    </div>
  )
}
