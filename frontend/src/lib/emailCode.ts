import { useEffect, useState } from 'react'

// 이메일 인증번호 입력 공통 (회원가입 / 비밀번호 찾기 / 마이페이지 이메일 변경)
export const CODE_PATTERN = /^\d{6}$/
// 서버 EmailVerificationService.RESEND_COOLDOWN과 같게 유지 (1분에 한 번 발송)
export const RESEND_COOLDOWN_SECONDS = 60

// 인증번호 재발송 대기 시간 (초). 0이면 보낼 수 있음
export function useCountdown() {
  const [left, setLeft] = useState(0)
  useEffect(() => {
    if (left <= 0) return
    const timer = setTimeout(() => setLeft((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [left])
  return [left, setLeft] as const
}

export function sendButtonLabel(sent: boolean, cooldown: number) {
  if (cooldown > 0) return `재발송 (${cooldown}초)`
  return sent ? '재발송' : '인증번호 받기'
}
