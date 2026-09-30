import type { DietType } from '../api/types'

// 백엔드 DietType 라벨과 같게 유지
export const DIET_LABELS: Record<DietType, string> = {
  NONE: '제한 없음',
  VEGAN: '비건',
  VEGETARIAN: '베지테리언',
  KETO: '케토',
  GLUTEN_FREE: '글루텐프리',
  LOW_SODIUM: '저나트륨',
}

export const DIET_DESCRIPTIONS: Record<DietType, string> = {
  NONE: '식단 제한 없이 추천해요',
  VEGAN: '고기·생선·우유·계란 등 동물성 원료가 없는 상품',
  VEGETARIAN: '고기·생선이 없는 상품 (우유·계란은 포함)',
  KETO: '탄수화물이 매우 낮은 상품',
  GLUTEN_FREE: '밀·보리·귀리가 없는 상품',
  LOW_SODIUM: '소금 무첨가 또는 저나트륨 상품',
}

export const DIET_OPTIONS = (Object.keys(DIET_LABELS) as DietType[]).map((value) => ({
  value,
  label: DIET_LABELS[value],
}))

// 마이페이지에서 눌러서 고를 수 있는 알레르기 목록 (식약처 알레르기 표시 대상 기준).
// "견과류"를 고르면 서버가 아몬드·호두·잣 등을 모두 걸러준다 (AllergenMatcher)
export const COMMON_ALLERGIES = [
  '우유',
  '계란',
  '밀',
  '대두',
  '땅콩',
  '견과류',
  '호두',
  '잣',
  '아몬드',
  '메밀',
  '갑각류',
  '새우',
  '게',
  '조개류',
  '오징어',
  '고등어',
  '돼지고기',
  '쇠고기',
  '닭고기',
  '복숭아',
  '토마토',
  '아황산류',
]
