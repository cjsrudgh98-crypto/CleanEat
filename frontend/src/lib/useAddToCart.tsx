import { useCallback, useState } from 'react'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { CartData } from '../api/types'
import { AllergyConfirmDialog } from '../components/AllergyConfirmDialog'

interface PendingConfirm {
  productName: string
  allergens: string[]
  resolve: (confirmed: boolean) => void
}

/**
 * 장바구니 담기 + 알레르기 확인. 서버가 "내 알레르기 성분이 든 상품"이라고 409로 알려주면 확인창을 띄우고,
 * "그래도 담기"를 누르면 확인 표시를 붙여 다시 담는다.
 * 반환값: 담긴 뒤 장바구니 / 확인창에서 취소하면 null. 그 밖의 오류는 그대로 던진다.
 * 화면에는 반드시 dialog를 함께 렌더링할 것.
 */
export function useAddToCart() {
  const { auth } = useAuth()
  const [pending, setPending] = useState<PendingConfirm | null>(null)

  const addToCart = useCallback(
    async (productId: number, quantity: number): Promise<CartData | null> => {
      if (!auth) throw new ApiError(401, '로그인이 필요합니다.')
      try {
        return await api.addCartItem(productId, quantity, auth)
      } catch (err) {
        const body = err instanceof ApiError ? err.body : null
        if (!(err instanceof ApiError) || err.status !== 409 || body?.code !== 'ALLERGY_CONFIRM_REQUIRED') throw err
        const confirmed = await new Promise<boolean>((resolve) =>
          setPending({
            productName: String(body.productName ?? ''),
            allergens: Array.isArray(body.allergens) ? body.allergens.map(String) : [],
            resolve,
          }),
        )
        if (!confirmed) return null
        return await api.addCartItem(productId, quantity, auth, true)
      }
    },
    [auth],
  )

  const dialog = pending ? (
    <AllergyConfirmDialog
      productName={pending.productName}
      allergens={pending.allergens}
      onAnswer={(confirmed) => {
        pending.resolve(confirmed)
        setPending(null)
      }}
    />
  ) : null

  return { addToCart, dialog }
}
