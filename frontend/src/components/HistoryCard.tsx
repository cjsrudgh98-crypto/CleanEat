import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { RiskLevel, ScanHistoryItem } from '../api/types'
import { appendUnique } from '../lib/paging'

const RISK_LABEL: Record<RiskLevel, string> = { LOW: '안전', MEDIUM: '주의', HIGH: '위험' }
const RISK_CLASS: Record<RiskLevel, string> = { LOW: 'badge-low', MEDIUM: 'badge-medium', HIGH: 'badge-high' }

interface HistoryCardProps {
  refreshSignal: number
}

export function HistoryCard({ refreshSignal }: HistoryCardProps) {
  const { auth } = useAuth()
  const [items, setItems] = useState<ScanHistoryItem[]>([])
  const [page, setPage] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    if (!auth) return
    api
      .getHistory(auth.userId, auth)
      .then((data) => {
        setItems(data.items)
        setPage(0)
        setHasNext(data.hasNext)
      })
      .catch(() => setError('기록을 불러오지 못했습니다.'))
  }, [auth])

  async function loadMore() {
    if (!auth) return
    setLoadingMore(true)
    try {
      const data = await api.getHistory(auth.userId, auth, page + 1)
      setItems((prev) => appendUnique(prev, data.items))
      setPage(data.page)
      setHasNext(data.hasNext)
    } catch {
      setError('기록을 더 불러오지 못했습니다.')
    } finally {
      setLoadingMore(false)
    }
  }

  useEffect(() => {
    load()
  }, [load, refreshSignal])

  if (!auth) return null

  async function deleteItem(id: number) {
    if (!auth || !window.confirm('이 기록을 삭제할까요?')) return
    try {
      await api.deleteHistoryItem(auth.userId, id, auth)
      setItems((prev) => prev.filter((item) => item.id !== id))
    } catch {
      setError('삭제하지 못했습니다.')
    }
  }

  async function clearAll() {
    if (!auth || items.length === 0 || !window.confirm('전체 기록을 삭제할까요?')) return
    try {
      await api.clearHistory(auth.userId, auth)
      setItems([])
      setHasNext(false)
    } catch {
      setError('삭제하지 못했습니다.')
    }
  }

  async function downloadReport(id: number) {
    if (!auth) return
    try {
      await api.downloadReport(auth.userId, id, auth)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'PDF를 내려받지 못했습니다.')
    }
  }

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
        <h2 style={{ margin: 0, fontSize: '1.05rem' }}>최근 스캔 기록</h2>
        {items.length > 0 && (
          <div style={{ display: 'flex', gap: '0.4rem' }}>
            <Link className="btn" to="/stats">
              식습관 통계
            </Link>
            <button className="btn" onClick={clearAll} type="button">
              전체 삭제
            </button>
          </div>
        )}
      </div>

      {error && <div className="error-box" style={{ marginBottom: '0.75rem' }}>{error}</div>}

      {items.length === 0 ? (
        <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.85rem' }}>스캔 기록이 없습니다.</p>
      ) : (
        <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
          {items.map((item) => (
            <li
              key={item.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: '0.5rem',
                borderBottom: '1px solid var(--border)',
                paddingBottom: '0.6rem',
              }}
            >
              <div>
                <span className={`badge ${RISK_CLASS[item.overallRisk]}`} style={{ marginRight: '0.5rem' }}>
                  {RISK_LABEL[item.overallRisk]}
                </span>
                <b style={{ fontSize: '0.88rem' }}>{item.productName}</b>
                <p style={{ margin: '0.15rem 0 0', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  {new Date(item.scannedAt).toLocaleString('ko-KR')}
                </p>
              </div>
              <div style={{ display: 'flex', gap: '0.25rem', flexShrink: 0 }}>
                <button
                  className="icon-btn"
                  aria-label="리포트 다운로드"
                  title="PDF 리포트 다운로드"
                  onClick={() => downloadReport(item.id)}
                >
                  ⬇
                </button>
                <button
                  className="icon-btn"
                  aria-label="기록 삭제"
                  title="기록 삭제"
                  onClick={() => deleteItem(item.id)}
                >
                  ×
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {hasNext && (
        <button className="btn" type="button" style={{ marginTop: '0.75rem', width: '100%' }}
                disabled={loadingMore} onClick={loadMore}>
          {loadingMore ? '불러오는 중...' : '더 보기'}
        </button>
      )}
    </div>
  )
}
