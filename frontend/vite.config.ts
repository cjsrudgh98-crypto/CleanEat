import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      // 소셜 로그인: 백엔드는 요청의 Host로 콜백 주소({baseUrl}/login/oauth2/code/...)를 만든다.
      // changeOrigin이 없으면 localhost:5173이 되는데, 구글/카카오/네이버 콘솔에는 localhost:8080만 등록돼 있어서
      // redirect_uri 불일치로 거절된다. 콜백은 8080으로 바로 돌아오고, 로그인 후에는 APP_FRONTEND_URL(5173)로 보낸다
      // (쿠키는 포트를 가리지 않아서 5173에서 시작한 로그인 세션이 8080 콜백에서도 이어진다)
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/login': { target: 'http://localhost:8080', changeOrigin: true },
      '/h2-console': 'http://localhost:8080',
    },
  },
})
