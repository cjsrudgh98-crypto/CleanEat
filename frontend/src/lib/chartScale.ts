// 차트 축 계산 도우미 (컴포넌트 파일에서 함수를 같이 내보내면 Fast Refresh 규칙에 걸려서 따로 둔다)

// 눈금이 깔끔한 금액(1만, 2만, 5만, 10만 ...)이 되도록 최댓값을 올린다
export function niceTop(max: number) {
  if (max <= 0) return 10000
  const half = max / 2
  const magnitude = 10 ** Math.floor(Math.log10(half))
  const step = [1, 2, 5, 10].map((m) => m * magnitude).find((s) => s >= half) ?? 10 * magnitude
  return step * Math.ceil(max / step)
}

/** 축 눈금용 짧은 금액: 0 / 5,000 / 1.2만 / 120만 */
export function shortWon(value: number) {
  if (value < 10000) return value.toLocaleString()
  const man = value / 10000
  return `${Number.isInteger(man) ? man : man.toFixed(1)}만`
}
