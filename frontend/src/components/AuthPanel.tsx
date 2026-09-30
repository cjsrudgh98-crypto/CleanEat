import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { api, ApiError } from '../api/client'
import type { AuthMode, EmailCodeResult, FindUsernameResult } from '../api/types'
import { CODE_PATTERN, RESEND_COOLDOWN_SECONDS, sendButtonLabel, useCountdown } from '../lib/emailCode'
import { CodeInput, DevCodeNotice } from './EmailCodeParts'

interface AuthPanelProps {
  mode: AuthMode
  onClose: () => void
  initialError?: string | null
}

type View = 'login' | 'register' | 'findId' | 'findPassword'
type Navigate = (view: View, notice?: string | null) => void

const VIEW_TITLES: Record<View, string> = {
  login: '로그인',
  register: '회원가입',
  findId: '아이디 찾기',
  findPassword: '비밀번호 찾기',
}

// 백엔드 PasswordPolicy / RegisterRequest 검증 규칙과 동일하게 유지해야 한다
const USERNAME_PATTERN = /^[a-z0-9_]{4,20}$/
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s])\S{8,64}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_PATTERN = /^01[016789]-?\d{3,4}-?\d{4}$/
const PASSWORD_RULE_TEXT = '영문, 숫자, 특수문자를 모두 포함해 8~64자'

function errorMessage(err: unknown) {
  return err instanceof ApiError ? err.message : '서버에 연결할 수 없습니다.'
}

export function AuthPanel({ mode, onClose, initialError }: AuthPanelProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  // 여기서는 <dialog> DOM API(showModal/close)만 다룬다 - React 상태는 건드리지 않는다.
  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (mode && !dialog.open) dialog.showModal()
    else if (!mode && dialog.open) dialog.close()
  }, [mode])

  return (
    <dialog
      ref={dialogRef}
      className="card"
      style={{
        width: 'min(380px, 90vw)',
        maxHeight: '90vh',
        overflowY: 'auto',
        border: '1px solid var(--border)',
        padding: '1.4rem',
      }}
      onClose={onClose}
      onCancel={onClose}
    >
      {/* mode가 바뀔 때마다 새로 mount돼서 입력값/에러가 자연스럽게 초기화된다 (effect로 동기화할 필요 없음) */}
      {mode && <AuthPanelBody key={mode} initialView={mode} initialError={initialError} onClose={onClose} />}
    </dialog>
  )
}

function AuthPanelBody({
  initialView,
  initialError,
  onClose,
}: {
  initialView: View
  initialError?: string | null
  onClose: () => void
}) {
  const [view, setView] = useState<View>(initialView)
  // 비밀번호 재설정 후 로그인 화면으로 돌아왔을 때 보여줄 안내 문구
  const [loginNotice, setLoginNotice] = useState<string | null>(null)

  const goTo: Navigate = (next, notice = null) => {
    setLoginNotice(notice)
    setView(next)
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
          {(view === 'findId' || view === 'findPassword') && (
            <button className="icon-btn" aria-label="로그인으로 돌아가기" onClick={() => goTo('login')} type="button">
              ‹
            </button>
          )}
          <h2 style={{ margin: 0, fontSize: '1.1rem' }}>{VIEW_TITLES[view]}</h2>
        </div>
        <button className="icon-btn" aria-label="닫기" onClick={onClose} type="button">
          ×
        </button>
      </div>

      {/* 화면마다 별도 컴포넌트라 전환 시 입력값/에러가 자연스럽게 초기화된다 */}
      {view === 'login' && (
        <LoginForm initialError={initialError} notice={loginNotice} onDone={onClose} onNavigate={goTo} />
      )}
      {view === 'register' && <RegisterForm onDone={onClose} onNavigate={goTo} />}
      {view === 'findId' && <FindIdForm onNavigate={goTo} />}
      {view === 'findPassword' && <FindPasswordForm onNavigate={goTo} />}
    </>
  )
}

function Field({ id, label, hint, children }: { id: string; label: string; hint?: string; children: ReactNode }) {
  return (
    <div style={{ marginBottom: '0.85rem' }}>
      <label htmlFor={id}>{label}</label>
      {children}
      {hint && <p style={{ margin: '0.3rem 0 0', fontSize: '0.75rem', color: 'var(--text-muted)' }}>{hint}</p>}
    </div>
  )
}

function SubmitArea({ label, submitting, error }: { label: string; submitting: boolean; error: string | null }) {
  return (
    <>
      <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
        {label}
      </button>
      {submitting && (
        <div className="status-row" style={{ marginTop: '0.6rem' }}>
          <span className="spinner" />
          <span>처리 중...</span>
        </div>
      )}
      {error && (
        <div className="error-box" style={{ marginTop: '0.6rem' }}>
          {error}
        </div>
      )}
    </>
  )
}

function TextLink({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <a
      href="#"
      style={{ color: 'var(--brand)', fontWeight: 600 }}
      onClick={(e) => {
        e.preventDefault()
        onClick()
      }}
    >
      {children}
    </a>
  )
}

function NoticeBox({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        background: 'var(--brand-tint)',
        color: 'var(--brand-dark)',
        borderRadius: 'var(--radius-sm)',
        padding: '0.55rem 0.75rem',
        fontSize: '0.85rem',
        marginBottom: '0.85rem',
      }}
    >
      {children}
    </div>
  )
}

function LoginForm({
  initialError,
  notice,
  onDone,
  onNavigate,
}: {
  initialError?: string | null
  notice: string | null
  onDone: () => void
  onNavigate: Navigate
}) {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(initialError ?? null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!username.trim() || !password) {
      setError('아이디와 비밀번호를 입력해주세요.')
      return
    }
    setSubmitting(true)
    try {
      await login(username.trim(), password)
      onDone()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {notice && <NoticeBox>{notice}</NoticeBox>}
      <form onSubmit={handleSubmit}>
        <Field id="loginUsernameInput" label="아이디">
          <input
            id="loginUsernameInput"
            type="text"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </Field>
        <Field id="loginPasswordInput" label="비밀번호">
          <input
            id="loginPasswordInput"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <SubmitArea label="로그인" submitting={submitting} error={error} />
      </form>

      <p
        style={{
          display: 'flex',
          justifyContent: 'center',
          gap: '0.6rem',
          margin: '0.85rem 0 0',
          fontSize: '0.82rem',
          color: 'var(--text-muted)',
        }}
      >
        <TextLink onClick={() => onNavigate('findId')}>아이디 찾기</TextLink>
        <span aria-hidden>|</span>
        <TextLink onClick={() => onNavigate('findPassword')}>비밀번호 찾기</TextLink>
        <span aria-hidden>|</span>
        <TextLink onClick={() => onNavigate('register')}>회원가입</TextLink>
      </p>

      <SocialLogin />
    </>
  )
}

const EMPTY_REGISTER_FORM = {
  username: '',
  password: '',
  passwordConfirm: '',
  name: '',
  nickname: '',
  email: '',
  phone: '',
  birthDate: '',
  agreeTerms: false,
}

function RegisterForm({ onDone, onNavigate }: { onDone: () => void; onNavigate: Navigate }) {
  const { register } = useAuth()
  const [form, setForm] = useState(EMPTY_REGISTER_FORM)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // 이메일 인증 - 인증번호 확인에 성공하면 가입 요청에 함께 보낼 토큰을 받는다. 인증한 이메일을 바꾸면 다시 인증해야 한다
  const [codeSent, setCodeSent] = useState<EmailCodeResult | null>(null)
  const [code, setCode] = useState('')
  const [verified, setVerified] = useState<{ email: string; token: string } | null>(null)
  const [emailBusy, setEmailBusy] = useState(false)
  const [emailError, setEmailError] = useState<string | null>(null)
  const [cooldown, setCooldown] = useCountdown()

  const normalizedEmail = form.email.trim().toLowerCase()
  const emailVerified = verified !== null && verified.email === normalizedEmail

  function update<K extends keyof typeof EMPTY_REGISTER_FORM>(key: K, value: (typeof EMPTY_REGISTER_FORM)[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function changeEmail(value: string) {
    update('email', value)
    // 인증번호를 받은 뒤 이메일을 고치면 이전 인증번호/인증은 무효 - 새 이메일로 다시 받게 한다
    if (codeSent || verified) {
      setCodeSent(null)
      setCode('')
      setVerified(null)
    }
    setEmailError(null)
  }

  async function sendCode() {
    if (!EMAIL_PATTERN.test(form.email.trim())) {
      setEmailError('이메일 형식이 올바르지 않습니다.')
      return
    }
    setEmailBusy(true)
    setEmailError(null)
    try {
      setCodeSent(await api.sendRegisterCode(form.email.trim()))
      setCode('')
      setCooldown(RESEND_COOLDOWN_SECONDS)
    } catch (err) {
      setEmailError(errorMessage(err))
    } finally {
      setEmailBusy(false)
    }
  }

  async function verifyCode() {
    if (!CODE_PATTERN.test(code)) {
      setEmailError('인증번호 6자리를 입력해주세요.')
      return
    }
    setEmailBusy(true)
    setEmailError(null)
    try {
      const result = await api.verifyRegisterCode(form.email.trim(), code)
      setVerified({ email: normalizedEmail, token: result.verificationToken })
      setCodeSent(null)
    } catch (err) {
      setEmailError(errorMessage(err))
    } finally {
      setEmailBusy(false)
    }
  }

  function validate(): string | null {
    if (!USERNAME_PATTERN.test(form.username)) return '아이디는 영문 소문자, 숫자, 밑줄(_)로 4~20자여야 합니다.'
    if (!PASSWORD_PATTERN.test(form.password)) return `비밀번호는 ${PASSWORD_RULE_TEXT}여야 합니다.`
    if (form.password !== form.passwordConfirm) return '비밀번호 확인이 일치하지 않습니다.'
    if (!form.name.trim()) return '이름을 입력해주세요.'
    const nickname = form.nickname.trim()
    if (nickname.length < 2 || nickname.length > 20) return '닉네임은 2~20자여야 합니다.'
    if (!EMAIL_PATTERN.test(form.email.trim())) return '이메일 형식이 올바르지 않습니다.'
    if (!emailVerified) return '이메일 인증을 완료해주세요.'
    if (!PHONE_PATTERN.test(form.phone.trim())) return '휴대폰 번호 형식이 올바르지 않습니다. (예: 010-1234-5678)'
    if (!form.birthDate) return '생년월일을 입력해주세요.'
    if (!form.agreeTerms) return '개인정보 수집 및 이용에 동의해주세요.'
    return null
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const invalid = validate()
    setError(invalid)
    if (invalid || !verified) return

    setSubmitting(true)
    try {
      await register({
        username: form.username,
        password: form.password,
        name: form.name.trim(),
        nickname: form.nickname.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        birthDate: form.birthDate,
        agreeTerms: form.agreeTerms,
        emailVerificationToken: verified.token,
      })
      onDone()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const today = new Date().toISOString().slice(0, 10)

  return (
    <>
      <form onSubmit={handleSubmit} noValidate>
        <Field id="regUsername" label="아이디" hint="영문 소문자, 숫자, 밑줄(_) 4~20자">
          <input
            id="regUsername"
            type="text"
            autoComplete="username"
            value={form.username}
            onChange={(e) => update('username', e.target.value.toLowerCase())}
          />
        </Field>
        <Field id="regPassword" label="비밀번호" hint={PASSWORD_RULE_TEXT}>
          <input
            id="regPassword"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={(e) => update('password', e.target.value)}
          />
        </Field>
        <Field id="regPasswordConfirm" label="비밀번호 확인">
          <input
            id="regPasswordConfirm"
            type="password"
            autoComplete="new-password"
            value={form.passwordConfirm}
            onChange={(e) => update('passwordConfirm', e.target.value)}
          />
          {form.passwordConfirm && form.password !== form.passwordConfirm && (
            <p style={{ margin: '0.3rem 0 0', fontSize: '0.75rem', color: 'var(--high-fg)' }}>
              비밀번호가 일치하지 않습니다.
            </p>
          )}
        </Field>
        <Field id="regName" label="이름" hint="아이디 찾기에 사용됩니다">
          <input
            id="regName"
            type="text"
            autoComplete="name"
            value={form.name}
            onChange={(e) => update('name', e.target.value)}
          />
        </Field>
        <Field id="regNickname" label="닉네임" hint="화면에 표시되는 이름 (2~20자)">
          <input
            id="regNickname"
            type="text"
            autoComplete="nickname"
            value={form.nickname}
            onChange={(e) => update('nickname', e.target.value)}
          />
        </Field>
        <Field id="regEmail" label="이메일" hint="인증번호를 받아 확인해주세요. 아이디 / 비밀번호 찾기에 사용됩니다">
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input
              id="regEmail"
              type="email"
              autoComplete="email"
              value={form.email}
              onChange={(e) => changeEmail(e.target.value)}
              style={{ flex: 1, minWidth: 0 }}
            />
            {!emailVerified && (
              <button
                type="button"
                className="btn"
                style={{ flexShrink: 0 }}
                disabled={emailBusy || cooldown > 0}
                onClick={sendCode}
              >
                {sendButtonLabel(codeSent !== null, cooldown)}
              </button>
            )}
          </div>
          {codeSent && !emailVerified && (
            <>
              <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
                <CodeInput id="regEmailCode" value={code} onChange={setCode} onEnter={verifyCode} />
                <button
                  type="button"
                  className="btn btn-primary"
                  style={{ flexShrink: 0 }}
                  disabled={emailBusy}
                  onClick={verifyCode}
                >
                  확인
                </button>
              </div>
              <p style={{ margin: '0.3rem 0 0', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                {codeSent.message} ({Math.round(codeSent.expiresInSeconds / 60)}분 안에 입력)
              </p>
              <DevCodeNotice code={codeSent.devCode} />
            </>
          )}
          {emailVerified && (
            <p style={{ margin: '0.3rem 0 0', fontSize: '0.75rem', color: 'var(--low-fg)', fontWeight: 700 }}>
              ✓ 이메일 인증이 완료되었습니다
            </p>
          )}
          {emailError && <p style={{ margin: '0.3rem 0 0', fontSize: '0.75rem', color: 'var(--high-fg)' }}>{emailError}</p>}
        </Field>
        <Field id="regPhone" label="휴대폰 번호">
          <input
            id="regPhone"
            type="tel"
            autoComplete="tel"
            placeholder="010-1234-5678"
            value={form.phone}
            onChange={(e) => update('phone', e.target.value)}
          />
        </Field>
        <Field id="regBirthDate" label="생년월일">
          <input
            id="regBirthDate"
            type="date"
            autoComplete="bday"
            max={today}
            value={form.birthDate}
            onChange={(e) => update('birthDate', e.target.value)}
          />
        </Field>

        <label
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: '0.5rem',
            fontWeight: 500,
            fontSize: '0.8rem',
            marginBottom: '0.85rem',
            cursor: 'pointer',
          }}
        >
          <input
            type="checkbox"
            checked={form.agreeTerms}
            onChange={(e) => update('agreeTerms', e.target.checked)}
            style={{ marginTop: '0.15rem' }}
          />
          <span>
            [필수] 개인정보 수집 및 이용에 동의합니다.
            <br />
            <span style={{ fontSize: '0.72rem' }}>
              수집 항목: 이름, 이메일, 휴대폰 번호, 생년월일 · 목적: 회원 식별 및 계정 찾기
            </span>
          </span>
        </label>

        <SubmitArea label="회원가입" submitting={submitting} error={error} />
      </form>

      <p style={{ margin: '0.85rem 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
        이미 계정이 있으신가요? <TextLink onClick={() => onNavigate('login')}>로그인</TextLink>
      </p>
    </>
  )
}

function FindIdForm({ onNavigate }: { onNavigate: Navigate }) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [result, setResult] = useState<FindUsernameResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!name.trim() || !EMAIL_PATTERN.test(email.trim())) {
      setError('이름과 올바른 이메일을 입력해주세요.')
      return
    }
    setSubmitting(true)
    try {
      setResult(await api.findUsername(name.trim(), email.trim()))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (result) {
    return (
      <div>
        <p style={{ margin: '0 0 0.6rem', fontSize: '0.88rem' }}>입력하신 정보와 일치하는 아이디입니다.</p>
        <div className="card" style={{ padding: '0.9rem', textAlign: 'center', marginBottom: '0.6rem' }}>
          <p style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800, letterSpacing: '0.05em' }}>
            {result.maskedUsername}
          </p>
          <p style={{ margin: '0.3rem 0 0', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
            가입일 {result.joinedAt}
          </p>
        </div>
        <p style={{ margin: '0 0 0.9rem', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          개인정보 보호를 위해 아이디 일부를 * 로 표시했습니다.
        </p>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button type="button" className="btn btn-primary" style={{ flex: 1 }} onClick={() => onNavigate('login')}>
            로그인하기
          </button>
          <button type="button" className="btn" style={{ flex: 1 }} onClick={() => onNavigate('findPassword')}>
            비밀번호 찾기
          </button>
        </div>
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <p style={{ margin: '0 0 0.9rem', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
        회원가입 시 입력한 이름과 이메일을 입력해주세요.
      </p>
      <Field id="findIdName" label="이름">
        <input id="findIdName" type="text" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />
      </Field>
      <Field id="findIdEmail" label="이메일">
        <input
          id="findIdEmail"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </Field>
      <SubmitArea label="아이디 찾기" submitting={submitting} error={error} />
    </form>
  )
}

function FindPasswordForm({ onNavigate }: { onNavigate: Navigate }) {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  // 1단계: 아이디+이메일 입력 -> 가입 이메일로 인증번호 발송
  // 2단계: 인증번호 확인 -> 서버가 10분짜리 1회용 재설정 토큰을 준다
  // 3단계: 새 비밀번호 입력
  const [codeSent, setCodeSent] = useState<EmailCodeResult | null>(null)
  const [code, setCode] = useState('')
  const [resetToken, setResetToken] = useState<string | null>(null)
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [cooldown, setCooldown] = useCountdown()

  async function sendCode() {
    setError(null)
    if (!username.trim() || !EMAIL_PATTERN.test(email.trim())) {
      setError('아이디와 올바른 이메일을 입력해주세요.')
      return
    }
    setSubmitting(true)
    try {
      setCodeSent(await api.sendPasswordResetCode(username.trim(), email.trim()))
      setCode('')
      setCooldown(RESEND_COOLDOWN_SECONDS)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleSendCode(e: FormEvent) {
    e.preventDefault()
    await sendCode()
  }

  async function handleVerify(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!CODE_PATTERN.test(code)) {
      setError('인증번호 6자리를 입력해주세요.')
      return
    }
    setSubmitting(true)
    try {
      const ticket = await api.verifyForPasswordReset(username.trim(), email.trim(), code)
      setResetToken(ticket.resetToken)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleReset(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!resetToken) return
    if (!PASSWORD_PATTERN.test(newPassword)) {
      setError(`비밀번호는 ${PASSWORD_RULE_TEXT}여야 합니다.`)
      return
    }
    if (newPassword !== newPasswordConfirm) {
      setError('비밀번호 확인이 일치하지 않습니다.')
      return
    }
    setSubmitting(true)
    try {
      await api.resetPassword(resetToken, newPassword)
      onNavigate('login', '비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (resetToken) {
    return (
      <form onSubmit={handleReset} noValidate>
        <NoticeBox>본인확인이 완료되었습니다. 10분 안에 새 비밀번호를 설정해주세요.</NoticeBox>
        <Field id="resetNewPassword" label="새 비밀번호" hint={PASSWORD_RULE_TEXT}>
          <input
            id="resetNewPassword"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
        </Field>
        <Field id="resetNewPasswordConfirm" label="새 비밀번호 확인">
          <input
            id="resetNewPasswordConfirm"
            type="password"
            autoComplete="new-password"
            value={newPasswordConfirm}
            onChange={(e) => setNewPasswordConfirm(e.target.value)}
          />
        </Field>
        <SubmitArea label="비밀번호 변경" submitting={submitting} error={error} />
      </form>
    )
  }

  if (codeSent) {
    return (
      <form onSubmit={handleVerify} noValidate>
        <NoticeBox>
          {codeSent.message}
          <br />
          <span style={{ fontSize: '0.78rem' }}>
            {Math.round(codeSent.expiresInSeconds / 60)}분 안에 입력해주세요. 메일이 오지 않으면 스팸함을 확인해주세요.
          </span>
        </NoticeBox>
        <Field id="findPwCode" label="인증번호">
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <CodeInput id="findPwCode" value={code} onChange={setCode} />
            <button
              type="button"
              className="btn"
              style={{ flexShrink: 0 }}
              disabled={submitting || cooldown > 0}
              onClick={sendCode}
            >
              {sendButtonLabel(true, cooldown)}
            </button>
          </div>
          <DevCodeNotice code={codeSent.devCode} />
        </Field>
        <SubmitArea label="확인" submitting={submitting} error={error} />
        <p style={{ margin: '0.85rem 0 0', fontSize: '0.8rem' }}>
          <TextLink
            onClick={() => {
              setCodeSent(null)
              setError(null)
            }}
          >
            아이디 / 이메일 다시 입력
          </TextLink>
        </p>
      </form>
    )
  }

  return (
    <form onSubmit={handleSendCode} noValidate>
      <p style={{ margin: '0 0 0.9rem', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
        아이디와 가입할 때 입력한 이메일을 입력하면, 그 이메일로 인증번호를 보내드립니다.
      </p>
      <Field id="findPwUsername" label="아이디">
        <input
          id="findPwUsername"
          type="text"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
      </Field>
      <Field id="findPwEmail" label="이메일">
        <input
          id="findPwEmail"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </Field>
      <SubmitArea label="인증번호 받기" submitting={submitting} error={error} />
      <p style={{ margin: '0.85rem 0 0', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
        소셜 로그인(Google·카카오·네이버)으로 가입한 계정은 해당 서비스에서 비밀번호를 관리합니다.
      </p>
    </form>
  )
}

function SocialLogin() {
  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '0.6rem',
          margin: '1rem 0 0.75rem',
          color: 'var(--text-muted)',
          fontSize: '0.78rem',
        }}
      >
        <span style={{ flex: 1, height: 1, background: 'var(--border)' }} />
        또는
        <span style={{ flex: 1, height: 1, background: 'var(--border)' }} />
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        <a className="btn btn-block" style={{ textAlign: 'center', color: '#3c4043' }} href="/oauth2/authorization/google">
          Google로 계속하기
        </a>
        <a
          className="btn btn-block"
          style={{ textAlign: 'center', background: '#FEE500', borderColor: '#FEE500', color: '#191600' }}
          href="/oauth2/authorization/kakao"
        >
          카카오로 계속하기
        </a>
        <a
          className="btn btn-block"
          style={{ textAlign: 'center', background: '#03C75A', borderColor: '#03C75A', color: '#ffffff' }}
          href="/oauth2/authorization/naver"
        >
          네이버로 계속하기
        </a>
      </div>
    </>
  )
}
