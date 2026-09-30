import { useState } from 'react'
import type { StoreListing } from '../api/types'
import { CATEGORY_ICONS } from './categoryIcons'

// 이미지가 없거나 로드에 실패해도 깨진 아이콘 대신 카테고리 아이콘을 보여준다
export function ProductImage({ product, className }: { product: StoreListing; className: string }) {
  const [failed, setFailed] = useState(false)
  if (!product.imageUrl || failed) {
    return (
      <div aria-hidden className={`${className} product-image-fallback`}>
        {CATEGORY_ICONS[product.category] ?? '🛒'}
      </div>
    )
  }
  return (
    <img
      src={product.imageUrl}
      alt={product.name}
      loading="lazy"
      className={className}
      onError={() => setFailed(true)}
    />
  )
}
