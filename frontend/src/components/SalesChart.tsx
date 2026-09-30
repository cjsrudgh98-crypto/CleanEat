import { useEffect, useRef, useState } from 'react'
import type { SalesPoint } from '../api/types'
import { niceTop, shortWon } from '../lib/chartScale'

// 막대 하나짜리 시계열 - 색은 tokens.css의 --chart-bar (라이트/다크 표면 대비·색 검증 통과)
const HEIGHT = 200
const MARGIN = { top: 12, right: 8, bottom: 24, left: 40 }
const MAX_BAR = 24
const MIN_GAP = 2 // 이웃 막대 사이 최소 틈
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

// 윗면만 둥근 막대 (바닥쪽은 기준선에 붙게 각지게)
function topRoundedRect(x: number, y: number, w: number, h: number) {
  const r = Math.min(RADIUS, w / 2, h)
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`
}

/** 기간별 매출 막대 (일별/월별). 마우스/키보드로 칸을 가리키면 금액과 주문 수를 보여주고, 표로도 볼 수 있다 */
export function SalesChart({ points, unit }: { points: SalesPoint[]; unit: 'day' | 'month' }) {
  const [ref, width] = useElementWidth<HTMLDivElement>()
  const [active, setActive] = useState<number | null>(null)

  const plotW = Math.max(0, width - MARGIN.left - MARGIN.right)
  const plotH = HEIGHT - MARGIN.top - MARGIN.bottom
  const top = niceTop(Math.max(...points.map((p) => p.sales), 0))
  const ticks = [0, top / 2, top]
  const slot = points.length ? plotW / points.length : 0
  const barW = Math.max(2, Math.min(MAX_BAR, slot * 0.62, slot - MIN_GAP))
  const scale = plotH / top
  const baseline = MARGIN.top + plotH
  // x축 이름은 처음 / 가운데 / 끝만 (30칸에 모두 붙이면 겹친다)
  const labelIndexes = new Set([0, Math.floor((points.length - 1) / 2), points.length - 1])
  const unitName = unit === 'day' ? '날짜' : '월'

  const activePoint = active !== null ? points[active] : null
  const tooltipLeft = active !== null ? MARGIN.left + slot * active + slot / 2 : 0

  return (
    <div className="risk-chart">
      <div ref={ref} className="risk-chart-plot">
        {width > 0 && (
          <svg width={width} height={HEIGHT} role="img" aria-label={`${unit === 'day' ? '일별' : '월별'} 매출 막대 차트`}>
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
                    {shortWon(t)}
                  </text>
                </g>
              )
            })}

            {points.map((p, i) => {
              if (p.sales <= 0) return null
              const h = Math.max(1, p.sales * scale)
              const x = MARGIN.left + slot * i + (slot - barW) / 2
              return (
                <path
                  key={p.start}
                  d={topRoundedRect(x, baseline - h, barW, h)}
                  fill="var(--chart-bar)"
                  opacity={active !== null && active !== i ? 0.45 : 1}
                />
              )
            })}

            {points.map((p, i) =>
              labelIndexes.has(i) ? (
                <text
                  key={p.start}
                  x={MARGIN.left + slot * i + slot / 2}
                  y={HEIGHT - 6}
                  textAnchor={i === 0 ? 'start' : i === points.length - 1 ? 'end' : 'middle'}
                  className="chart-tick"
                >
                  {p.label}
                </text>
              ) : null,
            )}

            {/* 칸 전체가 마우스/키보드 대상 (막대가 얇거나 0원이어도 가리키기 쉽게) */}
            {points.map((p, i) => (
              <rect
                key={p.start}
                x={MARGIN.left + slot * i}
                y={MARGIN.top}
                width={slot}
                height={plotH}
                fill="transparent"
                tabIndex={0}
                aria-label={`${p.label}: 매출 ${p.sales.toLocaleString()}원, 주문 ${p.orders}건`}
                onPointerEnter={() => setActive(i)}
                onPointerLeave={() => setActive(null)}
                onFocus={() => setActive(i)}
                onBlur={() => setActive(null)}
                style={{ outline: 'none' }}
              />
            ))}
          </svg>
        )}

        {activePoint && (
          <div className="chart-tooltip" role="status" style={{ left: Math.min(Math.max(tooltipLeft, 70), width - 70) }}>
            <div className="chart-tooltip-title">{activePoint.label}</div>
            <div className="chart-tooltip-row">
              <span className="chart-tooltip-key" style={{ background: 'var(--chart-bar)' }} aria-hidden />
              <strong>{activePoint.sales.toLocaleString()}원</strong>
            </div>
            <div className="chart-tooltip-row">
              <span className="chart-tooltip-key" aria-hidden />
              <strong>{activePoint.orders}</strong>
              <span>건</span>
            </div>
          </div>
        )}
      </div>

      <details className="chart-table">
        <summary>표로 보기</summary>
        <table>
          <thead>
            <tr>
              <th scope="col">{unitName}</th>
              <th scope="col">매출</th>
              <th scope="col">주문</th>
            </tr>
          </thead>
          <tbody>
            {points.map((p) => (
              <tr key={p.start}>
                <th scope="row">{p.label}</th>
                <td>{p.sales.toLocaleString()}원</td>
                <td>{p.orders}건</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  )
}
