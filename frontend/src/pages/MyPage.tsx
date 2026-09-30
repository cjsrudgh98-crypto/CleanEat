import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { ProfileCard } from '../components/ProfileCard'
import { InstallCard } from '../components/InstallCard'
import type { EmailCodeResult, UserProfileData } from '../api/types'
import { CODE_PATTERN, RESEND_COOLDOWN_SECONDS, sendButtonLabel, useCountdown } from '../lib/emailCode'
import { CodeInput, DevCodeNotice } from '../components/EmailCodeParts'

const PROVIDER_LABEL: Record<string, string> = { google: 'Google', kakao: '카카오', naver: '네이버' }

export default function MyPage() {
  const { auth, setNickname: setAuthNickname, logout } = useAuth()
  const navigate = useNavigate()
  const [profile, setProfile] = useState<UserProfileData | null>(null)

  useEffect(() => {
    if (!auth) return
    api.getProfile(auth.userId, auth).then(setProfile).catch(() => {})
  }, [auth])

  if (!auth) {
    return <div className="card">로그인 후 이용할 수 있습니다.</div>
  }

  return (
    <>
      <AccountInfoCard profile={profile} onNicknameSaved={setAuthNickname} />
      {profile && (
        <EmailCard
          currentEmail={profile.email}
          onChanged={(email) => setProfile((prev) => (prev ? { ...prev, email } : prev))}
        />
      )}
      {profile && !profile.provider && <PasswordCard />}
      <ProfileCard />
      <div className="card" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.75rem' }}>
        <div>
          <h2 className="section-title" style={{ marginBottom: '0.25rem' }}>찜한 상품</h2>
          <p style={{ margin: 0, fontSize: '0.8rem', color: 'var(--text-muted)' }}>하트를 눌러 모아둔 상품을 한눈에 봐요</p>
        </div>
        <Link className="btn btn-primary" to="/favorites" style={{ flexShrink: 0 }}>
          보기
        </Link>
      </div>
      <div className="card" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.75rem' }}>
        <div>
          <h2 className="section-title" style={{ marginBottom: '0.25rem' }}>나의 식습관 통계</h2>
          <p style={{ margin: 0, fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            검사 기록으로 자주 나온 유해성분과 알레르기 성분을 모아 봐요
          </p>
        </div>
        <Link className="btn btn-primary" to="/stats" style={{ flexShrink: 0 }}>
          보기
        </Link>
      </div>
      <InstallCard />
      <DangerZoneCard
        onDeleted={() => {
          logout()
          navigate('/')
        }}
      />
    </>
  )
}

function AccountInfoCard({
  profile,
  onNicknameSaved,
}: {
  profile: UserProfileData | null
  onNicknameSaved: (nickname: string) => void
}) {
  const { auth } = useAuth()
  if (!auth) return null

  return (
    <div className="card">
      <h2 className="section-title">계정 정보</h2>
      <label htmlFor="usernameField">아이디</label>
      <input id="usernameField" type="text" value={auth.username} disabled style={{ marginBottom: '0.85rem' }} />

      {/* profile이 로드된 후 실제 닉네임 값으로 다시 mount되도록 key를 줌 (effect로 동기화할 필요 없음) */}
      <NicknameForm key={profile?.nickname ?? 'loading'} initialNickname={profile?.nickname ?? auth.nickname} onSaved={onNicknameSaved} />

      <span className="tag">
        {profile?.provider ? `${PROVIDER_LABEL[profile.provider] ?? profile.provider} 로그인` : '일반 로그인'}
      </span>
    </div>
  )
}

function NicknameForm({
  initialNickname,
  onSaved,
}: {
  initialNickname: string
  onSaved: (nickname: string) => void
}) {
  const { auth } = useAuth()
  const [nickname, setNicknameInput] = useState(initialNickname)
  const [status, setStatus] = useState<'idle' | 'saving'>('idle')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  if (!auth) return null

  async function save() {
    if (!auth) return
    setError(null)
    setSuccess(false)
    if (!nickname.trim()) {
      setError('닉네임을 입력해주세요.')
      return
    }
    setStatus('saving')
    try {
      await api.updateNickname(auth.userId, nickname.trim(), auth)
      onSaved(nickname.trim())
      setSuccess(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '변경하지 못했습니다.')
    } finally {
      setStatus('idle')
    }
  }

  return (
    <>
      <label htmlFor="nicknameField">닉네임</label>
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
        <input
          id="nicknameField"
          type="text"
          value={nickname}
          onChange={(e) => setNicknameInput(e.target.value)}
        />
        <button className="btn btn-primary" onClick={save} disabled={status === 'saving'}>
          저장
        </button>
      </div>

      {error && <div className="error-box" style={{ marginBottom: '0.6rem' }}>{error}</div>}
      {success && (
        <p style={{ margin: '0 0 0.6rem', color: 'var(--brand-dark)', fontSize: '0.85rem', fontWeight: 600 }}>
          닉네임이 변경되었습니다.
        </p>
      )}
    </>
  )
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * 이메일 변경 (소셜 로그인 가입자는 처음 등록). 새 이메일로 받은 인증번호를 확인해야 바뀐다.
 * 주문 안내 메일과 아이디/비밀번호 찾기가 이 이메일로 간다.
 */
function EmailCard({ currentEmail, onChanged }: { currentEmail: string | null; onChanged: (email: string) => void }) {
  const { auth } = useAuth()
  const [editing, setEditing] = useState(false)
  const [email, setEmail] = useState('')
  const [codeSent, setCodeSent] = useState<EmailCodeResult | null>(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [cooldown, setCooldown] = useCountdown()

  if (!auth) return null

  function reset() {
    setEditing(false)
    setEmail('')
    setCodeSent(null)
    setCode('')
    setError(null)
  }

  async function sendCode() {
    if (!auth) return
    setError(null)
    setSuccess(null)
    if (!EMAIL_PATTERN.test(email.trim())) {
      setError('이메일 형식이 올바르지 않습니다.')
      return
    }
    setBusy(true)
    try {
      setCodeSent(await api.sendEmailChangeCode(auth.userId, email.trim(), auth))
      setCode('')
      setCooldown(RESEND_COOLDOWN_SECONDS)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '인증번호를 보내지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  async function confirm() {
    if (!auth) return
    setError(null)
    if (!CODE_PATTERN.test(code)) {
      setError('인증번호 6자리를 입력해주세요.')
      return
    }
    setBusy(true)
    try {
      await api.changeEmail(auth.userId, email.trim(), code, auth)
      setSuccess(currentEmail ? '이메일이 변경되었습니다.' : '이메일이 등록되었습니다.')
      onChanged(email.trim().toLowerCase())
      reset()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '이메일을 변경하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card">
      <h2 className="section-title">이메일</h2>
      <p style={{ margin: '0 0 0.6rem', fontSize: '0.9rem', fontWeight: 600 }}>
        {currentEmail ?? <span style={{ color: 'var(--text-muted)', fontWeight: 500 }}>등록된 이메일이 없습니다</span>}
      </p>
      <p style={{ margin: '0 0 0.85rem', fontSize: '0.78rem', color: 'var(--text-muted)' }}>
        주문·배송 안내 메일과 아이디/비밀번호 찾기에 사용됩니다.
      </p>

      {success && (
        <p style={{ margin: '0 0 0.6rem', color: 'var(--brand-dark)', fontSize: '0.85rem', fontWeight: 600 }}>
          {success}
        </p>
      )}

      {!editing ? (
        <button className="btn" onClick={() => setEditing(true)}>
          {currentEmail ? '이메일 변경' : '이메일 등록'}
        </button>
      ) : (
        <>
          <label htmlFor="newEmail">새 이메일</label>
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <input
              id="newEmail"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value)
                // 인증번호를 받은 뒤 주소를 고치면 새 주소로 다시 받아야 한다
                setCodeSent(null)
                setCode('')
              }}
              style={{ flex: 1, minWidth: 0 }}
            />
            <button className="btn" style={{ flexShrink: 0 }} disabled={busy || cooldown > 0} onClick={sendCode}>
              {sendButtonLabel(codeSent !== null, cooldown)}
            </button>
          </div>
          {codeSent && (
            <>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <CodeInput id="emailChangeCode" value={code} onChange={setCode} onEnter={confirm} />
                <button className="btn btn-primary" style={{ flexShrink: 0 }} disabled={busy} onClick={confirm}>
                  확인
                </button>
              </div>
              <p style={{ margin: '0.3rem 0 0', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                {codeSent.message} ({Math.round(codeSent.expiresInSeconds / 60)}분 안에 입력)
              </p>
              <DevCodeNotice code={codeSent.devCode} />
            </>
          )}
          {error && <div className="error-box" style={{ marginTop: '0.6rem' }}>{error}</div>}
          <button className="link-btn" style={{ marginTop: '0.75rem' }} onClick={reset}>
            취소
          </button>
        </>
      )}
    </div>
  )
}

function PasswordCard() {
  const { auth } = useAuth()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [status, setStatus] = useState<'idle' | 'saving'>('idle')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  if (!auth) return null

  async function save() {
    if (!auth) return
    setError(null)
    setSuccess(false)
    // 백엔드 PasswordPolicy와 같은 규칙
    if (!/^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s])\S{8,64}$/.test(newPassword)) {
      setError('새 비밀번호는 영문, 숫자, 특수문자를 모두 포함해 8~64자여야 합니다.')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('새 비밀번호가 서로 일치하지 않습니다.')
      return
    }
    setStatus('saving')
    try {
      await api.changePassword(auth.userId, currentPassword, newPassword, auth)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setSuccess(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '변경하지 못했습니다.')
    } finally {
      setStatus('idle')
    }
  }

  return (
    <div className="card">
      <h2 className="section-title">비밀번호 변경</h2>
      <label htmlFor="currentPassword">현재 비밀번호</label>
      <input
        id="currentPassword"
        type="password"
        autoComplete="current-password"
        value={currentPassword}
        onChange={(e) => setCurrentPassword(e.target.value)}
        style={{ marginBottom: '0.85rem' }}
      />
      <label htmlFor="newPassword">새 비밀번호</label>
      <input
        id="newPassword"
        type="password"
        autoComplete="new-password"
        value={newPassword}
        onChange={(e) => setNewPassword(e.target.value)}
        style={{ marginBottom: '0.85rem' }}
      />
      <label htmlFor="confirmPassword">새 비밀번호 확인</label>
      <input
        id="confirmPassword"
        type="password"
        autoComplete="new-password"
        value={confirmPassword}
        onChange={(e) => setConfirmPassword(e.target.value)}
        style={{ marginBottom: '1rem' }}
      />
      <button className="btn btn-primary" onClick={save} disabled={status === 'saving'}>
        비밀번호 변경
      </button>
      {error && <div className="error-box" style={{ marginTop: '0.6rem' }}>{error}</div>}
      {success && (
        <p style={{ margin: '0.6rem 0 0', color: 'var(--brand-dark)', fontSize: '0.85rem', fontWeight: 600 }}>
          비밀번호가 변경되었습니다.
        </p>
      )}
    </div>
  )
}

function DangerZoneCard({ onDeleted }: { onDeleted: () => void }) {
  const { auth } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)

  if (!auth) return null

  async function deleteAccount() {
    if (!auth) return
    if (!window.confirm('정말 탈퇴하시겠어요? 스캔 기록, 장바구니, 찜, 식단 설정이 삭제되며 되돌릴 수 없습니다.')) return
    setDeleting(true)
    setError(null)
    try {
      await api.deleteAccount(auth.userId, auth)
      onDeleted()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '탈퇴하지 못했습니다.')
      setDeleting(false)
    }
  }

  return (
    <div className="card">
      <h2 className="section-title">회원 탈퇴</h2>
      <p style={{ margin: '0 0 0.85rem', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
        탈퇴하면 스캔 기록, 장바구니, 찜, 식단 설정이 삭제되며 복구할 수 없습니다. 결제한 주문 기록은
        전자상거래법에 따라 5년간 보관되고, 이름·연락처 등 회원 정보는 지워집니다. 배송이 끝나지 않은 주문이 있으면
        탈퇴할 수 없습니다.
      </p>
      <button className="btn btn-danger" onClick={deleteAccount} disabled={deleting}>
        회원 탈퇴
      </button>
      {error && <div className="error-box" style={{ marginTop: '0.6rem' }}>{error}</div>}
    </div>
  )
}
