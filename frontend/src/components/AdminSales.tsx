import { useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'
import type { AuthState, SalesPeriod, SalesReport } from '../api/types'
import { SalesChart } from './SalesChart'

const PERIODS: { key: SalesPeriod; label: string }[] = [
  { key: '7d', label: '최근 7일' },
  { key: '30d', label: '최근 30일' },
  { key: '12m', label: '최근 12개월' },
]

/**
 * 관리자 매출 통계 - 기간 선택 -> 합계/주문 수/객단가 -> 일별(월별) 매출 막대 -> 많이 팔린 상품.
 * 금액은 부분 취소를 뺀 실제 결제 금액, 결제일 기준 (전체 취소/반품 완료는 빠짐).
 */
export function AdminSales({ auth }: { auth: AuthState }) {
  const [period, setPeriod] = useState<SalesPeriod>('30d')
  const [report, setReport] = useState<SalesReport | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .adminSales(period, auth)
      .then((data) => {
        if (cancelled) return
        setReport(data)
        setError(null)
      })
      .catch((err) => !cancelled && setError(err instanceof ApiError ? err.message : '매출을 불러오지 못했습니다.'))
    return () => {
      cancelled = true
    }
  }, [auth, period])

  return (
    <div className="card">
      <div className="chip-row" role="group" aria-label="기간">
        {PERIODS.map((p) => (
          <button
            key={p.key}
            type="button"
            className={`chip${p.key === period ? ' chip-active' : ''}`}
            aria-pressed={p.key === period}
            onClick={() => {
              setReport(null)
              setPeriod(p.key)
            }}
          >
            {p.label}
          </button>
        ))}
      </div>
      {error && <div className="error-box">{error}</div>}
      {!error && !report && <p className="muted-text">불러오는 중...</p>}
      {report && (
        <>
          <div className="admin-tiles" style={{ marginTop: '0.75rem' }}>
            <div className="admin-tile">
              <span className="admin-tile-label">매출</span>
              <strong>{report.totalSales.toLocaleString()}원</strong>
              <span className="admin-tile-label">{report.from.replaceAll('-', '.')} ~ {report.to.replaceAll('-', '.')}</span>
            </div>
            <div className="admin-tile">
              <span className="admin-tile-label">주문</span>
              <strong>{report.totalOrders.toLocaleString()}건</strong>
            </div>
            <div className="admin-tile">
              <span className="admin-tile-label">객단가</span>
              <strong>{report.averageOrderAmount.toLocaleString()}원</strong>
            </div>
          </div>

          <h3 className="section-title" style={{ fontSize: '0.95rem', marginTop: '1rem' }}>
            {report.unit === 'day' ? '일별' : '월별'} 매출
          </h3>
          <SalesChart points={report.series} unit={report.unit} />

          <h3 className="section-title" style={{ fontSize: '0.95rem', marginTop: '1rem' }}>많이 팔린 상품</h3>
          {report.topProducts.length === 0 ? (
            <p className="muted-text">이 기간에 팔린 상품이 없습니다.</p>
          ) : (
            <table className="sales-top-table">
              <thead>
                <tr>
                  <th scope="col">순위</th>
                  <th scope="col">상품</th>
                  <th scope="col">수량</th>
                  <th scope="col">매출</th>
                </tr>
              </thead>
              <tbody>
                {report.topProducts.map((p, i) => (
                  <tr key={p.productName}>
                    <td>{i + 1}</td>
                    <th scope="row">{p.productName}</th>
                    <td>{p.quantity.toLocaleString()}개</td>
                    <td>{p.sales.toLocaleString()}원</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p className="muted-text" style={{ margin: '0.75rem 0 0', fontSize: '0.75rem' }}>
            결제일 기준 실제 결제 금액입니다 (부분 취소 금액 제외, 전체 취소·반품 완료 주문 제외).
          </p>
        </>
      )}
    </div>
  )
}
