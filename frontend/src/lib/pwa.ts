import { useEffect, useState } from 'react'

/**
 * 홈 화면에 추가(PWA).
 *  - 서비스 워커는 배포 빌드에서만 등록한다 (개발 서버에서 캐시가 끼면 수정이 안 보여서 헷갈림)
 *  - 안드로이드/크롬/엣지: 브라우저가 주는 beforeinstallprompt 이벤트를 붙잡아 두었다가 "설치" 버튼에서 띄운다
 *  - iOS 사파리: 설치 이벤트가 없어서 "공유 → 홈 화면에 추가" 안내를 보여준다
 */

// 크롬 계열의 설치 이벤트 (표준 타입에 없음)
interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

let deferredPrompt: BeforeInstallPromptEvent | null = null
const listeners = new Set<() => void>()
const notify = () => listeners.forEach((l) => l())

export function registerPwa() {
  if (typeof window === 'undefined') return
  window.addEventListener('beforeinstallprompt', (event) => {
    // 브라우저 기본 미니 안내 대신 우리 버튼으로 띄운다
    event.preventDefault()
    deferredPrompt = event as BeforeInstallPromptEvent
    notify()
  })
  window.addEventListener('appinstalled', () => {
    deferredPrompt = null
    notify()
  })
  if (import.meta.env.PROD && 'serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch(() => {
        // 등록 실패(사설 모드 등)해도 앱은 그대로 동작한다
      })
    })
  }
}

/** 이미 홈 화면 앱으로 실행 중인지 */
export function isStandalone() {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    // iOS 사파리
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  )
}

function isIosSafari() {
  const ua = navigator.userAgent
  const ios = /iPad|iPhone|iPod/.test(ua) || (ua.includes('Macintosh') && navigator.maxTouchPoints > 1)
  // iOS의 크롬/파이어폭스 등은 홈 화면 추가를 지원하지 않거나 방법이 달라서 사파리만
  return ios && /Safari/.test(ua) && !/CriOS|FxiOS|EdgiOS/.test(ua)
}

export type InstallMode = 'prompt' | 'ios' | 'installed' | 'unsupported'

export function useInstallPrompt() {
  const [, setTick] = useState(0)

  useEffect(() => {
    const listener = () => setTick((n) => n + 1)
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  }, [])

  const mode: InstallMode = isStandalone()
    ? 'installed'
    : deferredPrompt
      ? 'prompt'
      : isIosSafari()
        ? 'ios'
        : 'unsupported'

  async function install() {
    if (!deferredPrompt) return false
    const prompt = deferredPrompt
    await prompt.prompt()
    const { outcome } = await prompt.userChoice
    // 한 번 쓴 이벤트는 다시 쓸 수 없다
    deferredPrompt = null
    notify()
    return outcome === 'accepted'
  }

  return { mode, install }
}
