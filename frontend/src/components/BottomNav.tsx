import { NavLink } from 'react-router-dom'
import type { ReactElement } from 'react'

interface NavItem {
  to: string
  label: string
  icon: (active: boolean) => ReactElement
}

function iconStroke(active: boolean) {
  return active ? 'var(--brand)' : 'var(--text-muted)'
}

const items: NavItem[] = [
  {
    to: '/',
    label: '홈',
    icon: (active) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
        <rect x="3" y="3" width="7" height="7" rx="2" stroke={iconStroke(active)} strokeWidth="2" />
        <rect x="14" y="3" width="7" height="7" rx="2" stroke={iconStroke(active)} strokeWidth="2" />
        <rect x="3" y="14" width="7" height="7" rx="2" stroke={iconStroke(active)} strokeWidth="2" />
        <path d="M14 17.5 L21 17.5 M17.5 14 L17.5 21" stroke={iconStroke(active)} strokeWidth="2" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/products',
    label: '상품',
    icon: (active) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
        <path
          d="M5 8h14l-1 12H6L5 8Z"
          stroke={iconStroke(active)}
          strokeWidth="2"
          strokeLinejoin="round"
        />
        <path d="M9 8V6a3 3 0 0 1 6 0v2" stroke={iconStroke(active)} strokeWidth="2" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/cart',
    label: '장바구니',
    icon: (active) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
        <path d="M4 6h2l1.6 10.2A2 2 0 0 0 9.6 18h7.8a2 2 0 0 0 2-1.7L20.5 9H6" stroke={iconStroke(active)} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="9.5" cy="21" r="1.4" fill={iconStroke(active)} />
        <circle cx="17" cy="21" r="1.4" fill={iconStroke(active)} />
      </svg>
    ),
  },
  {
    to: '/orders',
    label: '주문내역',
    icon: (active) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
        <path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z" stroke={iconStroke(active)} strokeWidth="2" strokeLinejoin="round" />
        <path d="M9 8h6M9 12h6" stroke={iconStroke(active)} strokeWidth="2" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/mypage',
    label: '마이페이지',
    icon: (active) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="8" r="3.5" stroke={iconStroke(active)} strokeWidth="2" />
        <path d="M4.5 20c1.2-3.6 4.2-5.5 7.5-5.5s6.3 1.9 7.5 5.5" stroke={iconStroke(active)} strokeWidth="2" strokeLinecap="round" />
      </svg>
    ),
  },
]

export function BottomNav() {
  return (
    <nav className="bottom-nav">
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} end={item.to === '/'} className="bottom-nav-item">
          {({ isActive }) => (
            <>
              {item.icon(isActive)}
              <span style={{ color: isActive ? 'var(--brand)' : 'var(--text-muted)' }}>{item.label}</span>
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )
}
