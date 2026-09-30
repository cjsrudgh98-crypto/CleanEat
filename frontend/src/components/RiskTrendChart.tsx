import { useEffect, useRef, useState } from 'react'
import type { StatsBucket } from '../api/types'

// 위험도 3단계 - 아래(안전)부터 쌓는다. 색은 tokens.css의 --risk-*-mark (색약 검증 완료)
const LEVELS = [
  { key: 'low', label: '안전', color: 'var(--risk-low-mark)' },
  { key: 'medium', label: '주의', color: 'var(--risk-medium-mark)' },
  { key: 'high', label: '위험', color: 'var(--risk-high-mark)' },
] as const

const HEIGHT = 190
const MARGIN = { top: 10, right: 8, bottom: 24, left: 30 }
const MAX_BAR = 24
const GAP = 2 // 쌓인 칸 사이 표면색 틈
const RADIUS = 4

function useElementWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)))
    observer.observe(el)
    return () => observer.disconnect()
  }, [])
  return [ref, width] as const
}

// 눈금이 1, 2, 5, 10, 20 ... 처럼 깔끔한 숫자가 되도록 최댓값을 올린다
function niceTop(max: number) {
  if (max <= 2) return 2
  const half = max / 2
  const magnitude = 10 ** Math.floor(Math.log10(half))
  const step = [1, 2, 5, 10].map((m) => m * magnitude).find((s) => s >= half) ?? 10 * magnitude
  return step * Math.ceil(max / step)
}

function bucketLabel(start: string, unit: 'DAY' | 'WEEK', short = true) {
  const d = new Date(start + 'T00:00:00')
  const md = `${d.getMonth() + 1}/${d.getDate()}`
  if (unit === 'DAY') return short ? md : `${d.getMonth() + 1}월 ${d.getDate()}일`
  const end = new Date(d)
  end.setDate(end.getDate() + 6)
  return short ? `${md}~` : `${md} ~ ${end.getMonth() + 1}/${end.getDate()}`
}

// 윗면만 둥근 막대 (바닥쪽은 각지게)
function topRoundedRect(x: number, y: number, w: number, h: number) {
  const r = Math.min(RADIUS, w / 2, h)
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`
}

/** 기간별 검사 수를 위험도(안전/주의/위험)로 쌓아서 보여준다 */
export function RiskTrendChart({ buckets, unit }: { buckets: StatsBucket[]; unit: 'DAY' | 'WEEK' }) {
  const [ref, width] = useElementWidth<HTMLDivElement>()
  const [active, setActive] = useState<number | null>(null)

  const plotW = Math.max(0, width - MARGIN.left - MARGIN.right)
  const plotH = HEIGHT - MARGIN.top - MARGIN.bottom
  const top = niceTop(Math.max(...buckets.map((b) => b.low + b.medium + b.high), 0))
  const ticks = [0, top / 2, top]
  const slot = buckets.length ? plotW / buckets.length : 0
  const barW = Math.max(3, Math.min(MAX_BAR, slot * 0.62))
  const scale = plotH / top
  const baseline = MARGIN.top + plotH
  // x축 이름은 처음 / 가운데 / 끝만 (모든 칸에 붙이면 겹친다)
  const labelIndexes = new Set([0, Math.floor((buckets.length - 1) / 2), buckets.length - 1])

  const activeBucket = active !== null ? buckets[active] : null
  const tooltipLeft = active !== null ? MARGIN.left + slot * active + slot / 2 : 0

  return (
    <div className="risk-chart">
      <ul className="chart-legend" aria-label="범례">
        {LEVELS.map((level) => (
          <li key={level.key}>
            <span className="chart-swatch" style={{ background: level.color }} aria-hidden />
            {level.label}
          </li>
        ))}
      </ul>

      <div ref={ref} className="risk-chart-plot">
        {width > 0 && (
          <svg width={width} height={HEIGHT} role="img" aria-label="기간별 위험도별 검사 수 막대 차트">
            {ticks.map((t) => {
              const y = baseline - t * scale
              return (
                <g key={t}>
                  <line
                    x1={MARGIN.left}
                    x2={width - MARGIN.right}
                    y1={y}
                    y2={y}
                    stroke={t === 0 ? 'var(--chart-axis)' : 'var(--chart-grid)'}
                    strokeWidth={1}
                    shapeRendering="crispEdges"
                  />
                  <text x={MARGIN.left - 6} y={y} dy="0.32em" textAnchor="end" className="chart-tick">
                    {t}
                  </text>
                </g>
              )
            })}

            {buckets.map((b, i) => {
              const x = MARGIN.left + slot * i + (slot - barW) / 2
              const values = [b.low, b.medium, b.high]
              const topIndex = values.map((v) => v > 0).lastIndexOf(true)
              let cursor = baseline
              let drawn = 0
              return (
                <g key={b.start} opacity={active !== null && active !== i ? 0.45 : 1}>
                  {values.map((v, level) => {
                    if (v === 0) return null
                    const gap = drawn > 0 ? GAP : 0
                    const h = Math.max(1, v * scale - gap)
                    const y = cursor - gap - h
                    cursor = y
                    drawn++
                    const fill = LEVELS[level].color
                    return level === topIndex ? (
                      <path key={level} d={topRoundedRect(x, y, barW, h)} fill={fill} />
                    ) : (
                      <rect key={level} x={x} y={y} width={barW} height={h} fill={fill} />
                    )
                  })}
                </g>
              )
            })}

            {buckets.map((b, i) =>
              labelIndexes.has(i) ? (
                <text
                  key={b.start}
                  x={MARGIN.left + slot * i + slot / 2}
                  y={HEIGHT - 6}
                  textAnchor={i === 0 ? 'start' : i === buckets.length - 1 ? 'end' : 'middle'}
                  className="chart-tick"
                >
                  {bucketLabel(b.start, unit)}
                </text>
              ) : null,
            )}

            {/* 칸 전체가 마우스/키보드 대상 (막대가 얇아도 가리키기 쉽게) */}
            {buckets.map((b, i) => (
              <rect
                key={b.start}
                x={MARGIN.left + slot * i}
                y={MARGIN.top}
                width={slot}
                height={plotH}
                fill="transparent"
                tabIndex={0}
                aria-label={`${bucketLabel(b.start, unit, false)}: 안전 ${b.low}, 주의 ${b.medium}, 위험 ${b.high}`}
                onPointerEnter={() => setActive(i)}
                onPointerLeave={() => setActive(null)}
                onFocus={() => setActive(i)}
                onBlur={() => setActive(null)}
                style={{ outline: 'none' }}
              />
            ))}
          </svg>
        )}

        {activeBucket && (
          <div
            className="chart-tooltip"
            role="status"
            style={{
              left: Math.min(Math.max(tooltipLeft, 70), width - 70),
            }}
          >
            <div className="chart-tooltip-title">{bucketLabel(activeBucket.start, unit, false)}</div>
            {LEVELS.map((level) => (
              <div key={level.key} className="chart-tooltip-row">
                <span className="chart-tooltip-key" style={{ background: level.color }} aria-hidden />
                <strong>{activeBucket[level.key]}</strong>
                <span>{level.label}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <details className="chart-table">
        <summary>표로 보기</summary>
        <table>
          <thead>
            <tr>
              <th scope="col">{unit === 'DAY' ? '날짜' : '주'}</th>
              <th scope="col">안전</th>
              <th scope="col">주의</th>
              <th scope="col">위험</th>
              <th scope="col">합계</th>
            </tr>
          </thead>
          <tbody>
            {buckets.map((b) => (
              <tr key={b.start}>
                <th scope="row">{bucketLabel(b.start, unit, false)}</th>
                <td>{b.low}</td>
                <td>{b.medium}</td>
                <td>{b.high}</td>
                <td>{b.low + b.medium + b.high}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  )
}
