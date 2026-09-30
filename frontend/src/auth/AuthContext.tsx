import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, loadAuthState, saveAuthState, setTokenRefreshedHandler, setUnauthorizedHandler } from '../api/client'
import type { AuthState, RegisterInput } from '../api/types'

interface AuthContextValue {
  auth: AuthState | null
  isAdmin: boolean
  oauthError: boolean
  login: (username: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => void
  clearOauthError: () => void
  setNickname: (nickname: string) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

// 백엔드가 소셜 로그인 성공 시 /?oauthCode=... (2분짜리 1회용 코드) 로, 실패 시 /?authError=... 로 리다이렉트한다.
// 최초 렌더 시점에 한 번만 읽는다 - 코드는 마운트 후 토큰으로 교환한다.
const initialParams = new URLSearchParams(window.location.search)
const initialOauthCode = initialParams.get('oauthCode')

function readInitialOauthError(): boolean {
  return initialParams.has('authError')
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState | null>(loadAuthState)
  const [oauthError, setOauthError] = useState<boolean>(readInitialOauthError)

  // 소셜 로그인 리다이렉트로 붙은 쿼리스트링은 한 번 읽었으면 주소창에서 지우고, 코드는 로그인 정보로 바꾼다.
  // 다른 쿼리(상품 카테고리, 토스 결제 결과 paymentKey 등)는 각 화면이 써야 하므로 건드리지 않는다.
  useEffect(() => {
    if (initialParams.has('oauthCode') || initialParams.has('authError')) {
      window.history.replaceState({}, '', window.location.pathname)
    }
    if (!initialOauthCode) return
    api
      .exchangeOAuthCode(initialOauthCode)
      .then((state) => {
        saveAuthState(state)
        setAuth(state)
      })
      .catch(() => setOauthError(true))
  }, [])

  // 저장된 로그인 정보의 닉네임/권한을 서버 기준으로 맞춘다 (관리자 권한이 바뀌었거나 예전 버전에서 저장된 경우)
  const userId = auth?.userId
  useEffect(() => {
    const current = loadAuthState()
    if (!current || current.userId !== userId) return
    api
      .me(current)
      .then((me) => {
        setAuth((prev) => {
          if (!prev || prev.userId !== me.userId) return prev
          const next = { ...prev, nickname: me.nickname, role: me.role }
          saveAuthState(next)
          return next
        })
      })
      .catch(() => {
        // 401이면 setUnauthorizedHandler가 정리한다. 네트워크 오류는 저장된 정보를 그대로 쓴다
      })
  }, [userId])

  useEffect(() => {
    setTokenRefreshedHandler((token) => {
      setAuth((prev) => {
        if (!prev) return prev
        const next = { ...prev, token }
        saveAuthState(next)
        return next
      })
    })
    return () => setTokenRefreshedHandler(null)
  }, [])

  // 토큰 만료 등으로 인증 요청이 401을 받으면 저장된 로그인 정보를 지운다
  useEffect(() => {
    setUnauthorizedHandler(() => {
      saveAuthState(null)
      setAuth(null)
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      auth,
      isAdmin: auth?.role === 'ADMIN',
      oauthError,
      async login(username, password) {
        const state = await api.login(username, password)
        saveAuthState(state)
        setAuth(state)
      },
      async register(input) {
        const state = await api.register(input)
        saveAuthState(state)
        setAuth(state)
      },
      logout() {
        saveAuthState(null)
        setAuth(null)
      },
      clearOauthError() {
        setOauthError(false)
      },
      setNickname(nickname) {
        setAuth((prev) => {
          if (!prev) return prev
          const next = { ...prev, nickname }
          saveAuthState(next)
          return next
        })
      },
    }),
    [auth, oauthError],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- Provider와 훅을 한 파일에 두는 흔한 패턴
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth는 AuthProvider 안에서만 사용할 수 있습니다')
  return ctx
}
