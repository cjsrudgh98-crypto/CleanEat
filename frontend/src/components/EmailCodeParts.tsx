// 이메일 인증번호 입력 화면 조각 (회원가입 / 비밀번호 찾기 / 마이페이지 이메일 변경)

// 메일 서버가 없는 개발 환경에서는 서버가 인증번호를 응답에 담아준다 (운영에서는 오지 않음)
export function DevCodeNotice({ code }: { code: string | null }) {
  if (!code) return null
  return (
    <p style={{ margin: '0.3rem 0 0', fontSize: '0.75rem', color: 'var(--medium-fg)' }}>
      개발 모드 - 메일 대신 인증번호를 표시합니다: <strong>{code}</strong>
    </p>
  )
}

export function CodeInput({
  id,
  value,
  onChange,
  onEnter,
}: {
  id: string
  value: string
  onChange: (value: string) => void
  onEnter?: () => void
}) {
  return (
    <input
      id={id}
      type="text"
      inputMode="numeric"
      autoComplete="one-time-code"
      maxLength={6}
      placeholder="숫자 6자리"
      value={value}
      onChange={(e) => onChange(e.target.value.replace(/\D/g, ''))}
      onKeyDown={(e) => {
        if (e.key === 'Enter' && onEnter) {
          e.preventDefault()
          onEnter()
        }
      }}
      style={{ flex: 1, minWidth: 0 }}
    />
  )
}
