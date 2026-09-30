import { useCallback, useSyncExternalStore } from 'react'

export type LayoutMode = 'web' | 'app'

// 이 폭 이상이면 기본으로 웹 디자인, 미만이면 앱 디자인
const WEB_MIN_WIDTH = 1024
// 이 폭 미만(실제 휴대폰)에서는 웹 디자인이 들어갈 자리가 없으므로 전환 버튼도 숨기고 앱 디자인으로 고정
const WEB_SWITCHABLE_MIN_WIDTH = 768
const OVERRIDE_KEY = 'cleaneat_layout_mode'
const OVERRIDE_EVENT = 'cleaneat-layout-override'

function readOverride(): LayoutMode | null {
  try {
    const value = localStorage.getItem(OVERRIDE_KEY)
    return value === 'web' || value === 'app' ? value : null
  } catch {
    return null
  }
}

function subscribe(callback: () => void) {
  window.addEventListener('resize', callback)
  window.addEventListener(OVERRIDE_EVENT, callback)
  return () => {
    window.removeEventListener('resize', callback)
    window.removeEventListener(OVERRIDE_EVENT, callback)
  }
}

// 스냅샷은 원시값(문자열)이어야 useSyncExternalStore가 불필요한 리렌더를 하지 않는다
function getSnapshot(): string {
  const width = window.innerWidth
  if (width < WEB_SWITCHABLE_MIN_WIDTH) return 'app|locked'
  const mode = readOverride() ?? (width >= WEB_MIN_WIDTH ? 'web' : 'app')
  return `${mode}|switchable`
}

export function useLayoutMode() {
  const snapshot = useSyncExternalStore(subscribe, getSnapshot)
  const [mode, lock] = snapshot.split('|') as [LayoutMode, string]

  const setMode = useCallback((next: LayoutMode) => {
    try {
      localStorage.setItem(OVERRIDE_KEY, next)
    } catch {
      // 프라이빗 브라우징 등 저장 불가 환경에서는 전환 없이 화면 폭 기준 기본값을 유지한다
    }
    window.dispatchEvent(new Event(OVERRIDE_EVENT))
  }, [])

  return { mode, isWeb: mode === 'web', canSwitch: lock === 'switchable', setMode }
}
