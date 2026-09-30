import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { EatingStats, RiskLevel, StatsDays, StatsSummary } from '../api/types'
import { RiskTrendChart } from '../components/RiskTrendChart'

const PERIODS: { days: StatsDays; label: string }[] = [
  { days: 7, label: '최근 7일' },
  { days: 30, label: '최근 30일' },
  { days: 90, label: '최근 90일' },
]

const RISK_LABEL: Record<RiskLevel, string> = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음' }
const RISK_BADGE: Record<RiskLevel, string> = { LOW: 'badge-low', MEDIUM: 'badge-medium', HIGH: 'badge-high' }

function percent(part: number, whole: number) {
  return whole > 0 ? Math.round((part / whole) * 100) : 0
}

/** 나의 식습관 통계 - 검사 기록으로 만든 기간별 요약 */
export default function StatsPage() {
  const { auth } = useAuth()
  const [days, setDays] = useState<StatsDays>(30)
  const [stats, setStats] = useState<EatingStats | null>(null)
  const [error, setError] = useState<string | null>(null)
  // 불러온 통계의 기간이 지금 고른 기간과 다르면 새로 불러오는 중
  const loading = stats !== null && stats.days !== days

  useEffect(() => {
    if (!auth) return
    let cancelled = false
    api
      .getEatingStats(auth.userId, days, auth)
      .then((result) => {
        if (cancelled) return
        setStats(result)
        setError(null)
      })
      .catch((err) => !cancelled && setError(err instanceof ApiError ? err.message : '통계를 불러오지 못했습니다.'))
    return () => {
      cancelled = true
    }
  }, [auth, days])

  if (!auth) return <div className="card">로그인 후 식습관 통계를 볼 수 있습니다.</div>

  return (
    <>
      <div className="card">
        <h2 className="section-title">나의 식습관 통계</h2>
        {/* 기간 선택은 한 줄, 아래 모든 숫자에 같이 적용된다 */}
        <div className="chip-row" role="group" aria-label="기간" style={{ marginBottom: 0 }}>
          {PERIODS.map((p) => (
            <button
              key={p.days}
              type="button"
              className={`chip${p.days === days ? ' chip-active' : ''}`}
              aria-pressed={p.days === days}
              onClick={() => setDays(p.days)}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div className="card">
          <div className="error-box">{error}</div>
        </div>
      )}
      {!stats && !error && <div className="card muted-text">불러오는 중...</div>}

      {/* 다시 불러오는 동안에는 이전 화면을 흐리게 유지한다 (깜빡임/레이아웃 튐 없음) */}
      {stats && (
        <div className="stats-body" style={{ opacity: loading ? 0.55 : 1 }} aria-busy={loading}>
          {stats.current.scans === 0 ? (
            <EmptyStats days={stats.days} />
          ) : (
            <>
              <Insight stats={stats} />
              <SummaryTiles current={stats.current} previous={stats.previous} days={stats.days} />
              <div className="card">
                <h2 className="section-title">검사한 제품의 위험도</h2>
                <p className="muted-text" style={{ margin: '0 0 0.75rem' }}>
                  {stats.bucketUnit === 'DAY' ? '하루' : '한 주'} 단위로 검사한 제품 수를 위험도별로 쌓았어요
                </p>
                <RiskTrendChart buckets={stats.trend} unit={stats.bucketUnit} />
              </div>
              <HarmfulRanking stats={stats} />
              <AllergenRanking stats={stats} />
            </>
          )}
        </div>
      )}
    </>
  )
}

function EmptyStats({ days }: { days: number }) {
  return (
    <div className="card">
      <p style={{ margin: '0 0 0.75rem' }}>최근 {days}일 동안 검사한 제품이 없어요.</p>
      <p className="muted-text" style={{ margin: '0 0 1rem' }}>
        바코드나 성분표 사진으로 제품을 검사하면 자주 나오는 유해성분과 알레르기 성분을 모아서 보여드려요.
      </p>
      <Link className="btn btn-primary" to="/">
        제품 검사하러 가기
      </Link>
    </div>
  )
}

function Insight({ stats }: { stats: EatingStats }) {
  const { current, topHarmful, myAllergens, days } = stats
  const safeRate = percent(current.low, current.scans)
  const topIngredient = topHarmful[0]
  const topAllergen = myAllergens[0]
  return (
    <div className="card stats-insight">
      <p>
        최근 {days}일 동안 <strong>{current.scans}개</strong> 제품을 검사했고, 그중 <strong>{current.low}개({safeRate}%)</strong>가
        유해성분 걱정이 적은 안전 등급이었어요.
      </p>
      {topIngredient && (
        <p>
          가장 자주 나온 유해성분은 <strong>{topIngredient.name}</strong>이에요 ({topIngredient.count}개 제품).
        </p>
      )}
      {topAllergen && (
        <p>
          내 알레르기 성분 <strong>{topAllergen.name}</strong>이(가) {topAllergen.count}개 제품에 들어 있었어요. 검사로 미리
          확인해서 피할 수 있었어요.
        </p>
      )}
    </div>
  )
}

function Delta({ value, unit, goodWhenUp }: { value: number; unit: string; goodWhenUp?: boolean }) {
  if (value === 0) return <span className="stat-delta">변화 없음</span>
  const up = value > 0
  // 좋고 나쁨이 있는 지표만 색을 쓰고, 항상 화살표 + 글자로도 방향을 보여준다
  const tone = goodWhenUp === undefined ? '' : up === goodWhenUp ? ' good' : ' bad'
  return (
    <span className={`stat-delta${tone}`}>
      <span aria-hidden>{up ? '▲' : '▼'}</span> {Math.abs(value)}
      {unit} {up ? '늘었어요' : '줄었어요'}
    </span>
  )
}

function SummaryTiles({ current, previous, days }: { current: StatsSummary; previous: StatsSummary; days: number }) {
  const safeRate = percent(current.low, current.scans)
  const prevSafeRate = percent(previous.low, previous.scans)
  const compare = `이전 ${days}일보다`
  return (
    <div className="stat-tiles">
      <div className="stat-tile">
        <span className="stat-label">검사한 제품</span>
        <strong className="stat-value">{current.scans}개</strong>
        <span className="stat-sub">
          {compare} <Delta value={current.scans - previous.scans} unit="개" />
        </span>
      </div>
      <div className="stat-tile">
        <span className="stat-label">안전 등급 비율</span>
        <strong className="stat-value">{safeRate}%</strong>
        <span className="stat-sub">
          {previous.scans > 0 ? (
            <>
              {compare} <Delta value={safeRate - prevSafeRate} unit="%p" goodWhenUp />
            </>
          ) : (
            '비교할 이전 기록 없음'
          )}
        </span>
      </div>
      <div className="stat-tile">
        <span className="stat-label">유해성분이 나온 제품</span>
        <strong className="stat-value">{current.withHarmful}개</strong>
        <span className="stat-sub">검사한 제품의 {percent(current.withHarmful, current.scans)}%</span>
      </div>
      <div className="stat-tile">
        <span className="stat-label">내 알레르기가 든 제품</span>
        <strong className="stat-value">{current.withMyAllergen}개</strong>
        <span className="stat-sub">검사한 제품의 {percent(current.withMyAllergen, current.scans)}%</span>
      </div>
    </div>
  )
}

// 순위 막대 - 한 가지 값(나온 제품 수)이라 한 색으로, 값은 막대 끝에 적는다
function RankBars({ items }: { items: { key: string; name: string; count: number; meta?: ReactNode }[] }) {
  const max = Math.max(...items.map((i) => i.count), 1)
  return (
    <ol className="rank-bars">
      {items.map((item) => (
        <li key={item.key} className="rank-bar-row">
          <div className="rank-bar-head">
            <span className="rank-bar-name">{item.name}</span>
            {item.meta}
          </div>
          <div className="rank-bar-track" aria-hidden>
            <span className="rank-bar-fill" style={{ width: `${(item.count / max) * 100}%` }} />
            <span className="rank-bar-value">{item.count}개</span>
          </div>
          <span className="sr-only">{item.count}개 제품</span>
        </li>
      ))}
    </ol>
  )
}

function HarmfulRanking({ stats }: { stats: EatingStats }) {
  return (
    <div className="card">
      <h2 className="section-title">자주 나온 유해성분</h2>
      <p className="muted-text" style={{ margin: '0 0 0.75rem' }}>
        이 성분이 들어 있던 제품 수예요. 검사로 확인했으니 다음엔 더 쉽게 피할 수 있어요.
      </p>
      {stats.topHarmful.length === 0 ? (
        <p style={{ margin: 0, fontSize: '0.88rem' }}>검사한 제품에서 유해성분이 나오지 않았어요. 👍</p>
      ) : (
        <RankBars
          items={stats.topHarmful.map((h) => ({
            key: h.name,
            name: h.name,
            count: h.count,
            meta: (
              <>
                {h.riskLevel && (
                  <span className={`badge ${RISK_BADGE[h.riskLevel]}`} style={{ fontSize: '0.7rem', padding: '0.1rem 0.45rem' }}>
                    위험도 {RISK_LABEL[h.riskLevel]}
                  </span>
                )}
                {h.description && <span className="rank-bar-desc">{h.description}</span>}
              </>
            ),
          }))}
        />
      )}
    </div>
  )
}

function AllergenRanking({ stats }: { stats: EatingStats }) {
  return (
    <div className="card">
      <h2 className="section-title">자주 걸린 내 알레르기 성분</h2>
      {!stats.hasAllergySettings ? (
        <p style={{ margin: 0, fontSize: '0.88rem' }}>
          마이페이지에서 알레르기를 설정하면 검사한 제품 중 내 알레르기 성분이 든 제품을 모아 보여드려요.{' '}
          <Link to="/mypage" className="link-btn" style={{ textDecoration: 'none' }}>
            알레르기 설정하기
          </Link>
        </p>
      ) : stats.myAllergens.length === 0 ? (
        <p style={{ margin: 0, fontSize: '0.88rem' }}>검사한 제품에 내 알레르기 성분이 없었어요.</p>
      ) : (
        <>
          <p className="muted-text" style={{ margin: '0 0 0.75rem' }}>
            마이페이지에 설정한 알레르기 기준이에요.
          </p>
          <RankBars items={stats.myAllergens.map((a) => ({ key: a.name, name: a.name, count: a.count }))} />
        </>
      )}
    </div>
  )
}
