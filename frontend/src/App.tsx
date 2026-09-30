import { useState } from 'react'
import { Route, Routes } from 'react-router-dom'
import { AuthPanel } from './components/AuthPanel'
import HomePage from './pages/HomePage'
import ProductsPage from './pages/ProductsPage'
import ProductDetailPage from './pages/ProductDetailPage'
import CartPage from './pages/CartPage'
import CheckoutPage from './pages/CheckoutPage'
import OrdersPage from './pages/OrdersPage'
import MyPage from './pages/MyPage'
import AdminPage from './pages/AdminPage'
import StatsPage from './pages/StatsPage'
import FavoritesPage from './pages/FavoritesPage'
import IngredientsPage from './pages/IngredientsPage'
import { PaymentFailPage, PaymentSuccessPage } from './pages/PaymentResultPage'
import { useAuth } from './auth/AuthContext'
import { AppLayout } from './layout/AppLayout'
import { WebLayout } from './layout/WebLayout'
import { useLayoutMode } from './layout/useLayoutMode'
import type { AuthMode } from './api/types'

export default function App() {
  const { oauthError, clearOauthError } = useAuth()
  const { isWeb } = useLayoutMode()
  // oauthError는 최초 렌더 시점에 이미 URL에서 확정된 값이라 여기서 바로 초기 모드로 반영해도 안전함
  const [authMode, setAuthMode] = useState<AuthMode>(() => (oauthError ? 'login' : null))

  // 화면(라우트)은 하나로 두고, 감싸는 레이아웃만 웹/앱 디자인으로 나눈다
  const Layout = isWeb ? WebLayout : AppLayout

  return (
    <>
      <Layout onShowLogin={() => setAuthMode('login')} onShowRegister={() => setAuthMode('register')}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/products" element={<ProductsPage />} />
          <Route path="/products/:productId" element={<ProductDetailPage />} />
          <Route path="/cart" element={<CartPage />} />
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/orders/:orderId" element={<OrdersPage />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/payments/success" element={<PaymentSuccessPage />} />
          <Route path="/payments/fail" element={<PaymentFailPage />} />
          <Route path="/stats" element={<StatsPage />} />
          <Route path="/favorites" element={<FavoritesPage />} />
          <Route path="/ingredients" element={<IngredientsPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="/admin/:tab" element={<AdminPage />} />
        </Routes>
      </Layout>

      <AuthPanel
        mode={authMode}
        onClose={() => {
          setAuthMode(null)
          clearOauthError()
        }}
        initialError={oauthError ? '소셜 로그인에 실패했습니다. 다시 시도해주세요.' : null}
      />
    </>
  )
}
