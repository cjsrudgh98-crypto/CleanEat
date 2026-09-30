import type { ProductCategory } from '../api/types'

// 카테고리 아이콘 (사이드바/칩/섹션 제목, 이미지가 없는 상품의 대체 이미지)
export const CATEGORY_ICONS: Record<ProductCategory, string> = {
  SNACK: '🍪',
  NUTS: '🥜',
  BAKERY: '🥐',
  MEAL: '🍱',
  GRAIN: '🌾',
  DAIRY: '🥛',
  BEVERAGE: '🧃',
  PROTEIN: '💪',
  SAUCE: '🫙',
  ETC: '🛒',
}

// 백엔드 ProductCategory 라벨과 같게 유지 (관리자 상품 등록 화면용 - 선언 순서가 표시 순서)
export const CATEGORY_LABELS: Record<ProductCategory, string> = {
  SNACK: '과자·스낵',
  NUTS: '견과·건과일',
  BAKERY: '베이커리·시리얼',
  MEAL: '간편식',
  GRAIN: '쌀·잡곡·면',
  DAIRY: '유제품·두유',
  BEVERAGE: '음료·차',
  PROTEIN: '단백질·건강식',
  SAUCE: '소스·양념',
  ETC: '기타',
}
