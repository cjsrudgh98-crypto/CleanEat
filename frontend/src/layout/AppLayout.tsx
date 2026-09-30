import type { ReactNode } from 'react'
import { Header } from '../components/Header'
import { BottomNav } from '../components/BottomNav'
import { useLayoutMode } from './useLayoutMode'

interface LayoutProps {
  children: ReactNode
  onShowLogin: () => void
  onShowRegister: () => void
}

/**
 * 모바일 앱 디자인. 휴대폰에서는 화면 전체를 쓰고, 넓은 화면에서는 고정 크기(430x860) 폰 프레임 안에 띄운다.
 * 헤더/하단 탭은 고정이고 가운데 영역만 스크롤된다 - 내용 길이에 따라 창 크기가 바뀌지 않는다.
 */
export function AppLayout({ children, onShowLogin, onShowRegister }: LayoutProps) {
  const { canSwitch, setMode } = useLayoutMode()

  return (
    <div className="app-viewport">
      <div className="app-frame">
        <Header onShowLogin={onShowLogin} onShowRegister={onShowRegister} />
        <div className="app-scroll">{children}</div>
        <BottomNav />
      </div>

      {canSwitch && (
        <button type="button" className="btn layout-switch" onClick={() => setMode('web')}>
          🖥 웹 화면으로 보기
        </button>
      )}
    </div>
  )
}
