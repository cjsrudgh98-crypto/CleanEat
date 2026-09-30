// "더 보기"로 다음 페이지를 이어 붙일 때 - 그 사이 목록이 바뀌어(새 주문/삭제) 같은 항목이 두 페이지에 걸쳐 올 수 있어서 id로 거른다
export function appendUnique<T extends { id: number }>(prev: T[], next: T[]): T[] {
  const seen = new Set(prev.map((item) => item.id))
  return [...prev, ...next.filter((item) => !seen.has(item.id))]
}
