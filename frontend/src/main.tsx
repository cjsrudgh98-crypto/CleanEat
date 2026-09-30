import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './styles/global.css'
import App from './App.tsx'
import { AuthProvider } from './auth/AuthContext'
import { registerPwa } from './lib/pwa'

// 설치 이벤트(beforeinstallprompt)는 화면이 뜨기 전에 올 수 있어서 가장 먼저 등록한다
registerPwa()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
