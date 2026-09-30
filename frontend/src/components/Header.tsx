import { Link } from 'react-router-dom'
import { Brand } from './Brand'
import { useAuth } from '../auth/AuthContext'

interface HeaderProps {
  onShowLogin: () => void
  onShowRegister: () => void
}

export function Header({ onShowLogin, onShowRegister }: HeaderProps) {
  const { auth, isAdmin, logout } = useAuth()

  return (
    <header className="app-bar">
      <Link to="/" style={{ textDecoration: 'none', color: 'inherit' }}>
        <Brand size={28} />
      </Link>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        {auth ? (
          <>
            {isAdmin && (
              <Link className="btn" to="/admin" style={{ padding: '0.4rem 0.7rem' }}>
                관리자
              </Link>
            )}
            <Link className="btn" to="/mypage" style={{ padding: '0.4rem 0.7rem' }}>
              마이페이지
            </Link>
            <button className="btn" style={{ padding: '0.4rem 0.7rem' }} onClick={logout}>
              로그아웃
            </button>
          </>
        ) : (
          <>
            <button className="btn" style={{ padding: '0.4rem 0.7rem' }} onClick={onShowLogin}>
              로그인
            </button>
            <button className="btn btn-primary" style={{ padding: '0.4rem 0.7rem' }} onClick={onShowRegister}>
              회원가입
            </button>
          </>
        )}
      </div>
    </header>
  )
}
