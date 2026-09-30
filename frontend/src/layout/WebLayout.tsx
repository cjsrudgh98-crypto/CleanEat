import type { ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { useAuth } from '../auth/AuthContext'
import { useLayoutMode } from './useLayoutMode'

interface LayoutProps {
  children: ReactNode
  onShowLogin: () => void
  onShowRegister: () => void
}

const NAV_ITEMS = [
  { to: '/', label: '성분 검사' },
  { to: '/products', label: '상품' },
  { to: '/orders', label: '주문내역' },
  { to: '/ingredients', label: '성분 사전' },
]

/**
 * PC 웹 디자인. 상단 메뉴바는 고정이고 본문만 스크롤된다.
 * 본문 폭은 고정(상품 화면 1200px, 나머지 760px)이라 내용에 따라 폭이 들쭉날쭉하지 않다.
 */
export function WebLayout({ children, onShowLogin, onShowRegister }: LayoutProps) {
  const { auth, isAdmin, logout } = useAuth()
  const { setMode } = useLayoutMode()
  const { pathname } = useLocation()
  // 상품 목록/상세는 그리드라 넓게, 폼 위주 화면은 읽기 편한 폭으로
  const wide = pathname === '/' || pathname.startsWith('/products') || pathname.startsWith('/admin')

  return (
    <div className="web-shell">
      <header className="web-header">
        <div className="web-container web-header-inner">
          <Link to="/" style={{ textDecoration: 'none', color: 'inherit' }}>
            <Brand size={32} />
          </Link>

          <nav className="web-nav" aria-label="주 메뉴">
            {[...NAV_ITEMS, ...(auth ? [{ to: '/stats', label: '식습관 통계' }] : [])].map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) => `web-nav-link${isActive ? ' active' : ''}`}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="web-header-actions">
            {auth && (
              <NavLink to="/favorites" className={({ isActive }) => `web-nav-link${isActive ? ' active' : ''}`}>
                ♡ 찜
              </NavLink>
            )}
            <NavLink to="/cart" className={({ isActive }) => `web-nav-link${isActive ? ' active' : ''}`}>
              🛒 장바구니
            </NavLink>
            {auth ? (
              <>
                {isAdmin && (
                  <NavLink to="/admin" className={({ isActive }) => `web-nav-link${isActive ? ' active' : ''}`}>
                    관리자
                  </NavLink>
                )}
                <NavLink to="/mypage" className={({ isActive }) => `web-nav-link${isActive ? ' active' : ''}`}>
                  {auth.nickname}님
                </NavLink>
                <button className="btn" onClick={logout}>
                  로그아웃
                </button>
              </>
            ) : (
              <>
                <button className="btn" onClick={onShowLogin}>
                  로그인
                </button>
                <button className="btn btn-primary" onClick={onShowRegister}>
                  회원가입
                </button>
              </>
            )}
            <button
              type="button"
              className="icon-btn"
              title="앱 화면으로 보기"
              aria-label="앱 화면으로 보기"
              onClick={() => setMode('app')}
            >
              📱
            </button>
          </div>
        </div>
      </header>

      <main className="web-main">
        <div className={`web-container web-content${wide ? '' : ' web-content-narrow'}`}>{children}</div>
        <footer className="web-footer">
          <div className="web-container">
            <Brand size={20} /> · 성분을 확인하고 건강하게 드세요
          </div>
        </footer>
      </main>
    </div>
  )
}
