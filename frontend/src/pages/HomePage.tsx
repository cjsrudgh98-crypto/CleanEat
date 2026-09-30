import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ScanCard } from '../components/ScanCard'
import { ResultCard } from '../components/ResultCard'
import { ReceiptResultCard } from '../components/ReceiptResultCard'
import { HistoryCard } from '../components/HistoryCard'
import { InstallCard } from '../components/InstallCard'
import { useAuth } from '../auth/AuthContext'
import type { ReceiptScanResult, ScanResult } from '../api/types'
import { useLayoutMode } from '../layout/useLayoutMode'

// 마지막으로 한 검사 결과 하나만 보여준다 (단일 제품 또는 영수증)
type LastResult = { kind: 'product'; data: ScanResult } | { kind: 'receipt'; data: ReceiptScanResult }

export default function HomePage() {
  const { auth } = useAuth()
  const { isWeb } = useLayoutMode()
  const [result, setResult] = useState<LastResult | null>(null)
  const [historyRefresh, setHistoryRefresh] = useState(0)

  function show(next: LastResult) {
    setResult(next)
    // 로그인 상태면 서버가 검사 기록을 남겼으므로 기록 목록을 새로 불러온다
    if (auth) setHistoryRefresh((n) => n + 1)
  }

  const intro = '바코드·성분표 사진·영수증으로 유해성분과 알레르기 성분을 확인하세요.'
  const scanCard = (
    <ScanCard
      onResult={(data) => show({ kind: 'product', data })}
      onReceiptResult={(data) => show({ kind: 'receipt', data })}
    />
  )
  const resultCard =
    result?.kind === 'product' ? (
      <ResultCard result={result.data} />
    ) : result?.kind === 'receipt' ? (
      <ReceiptResultCard result={result.data} />
    ) : null

  // 웹: 왼쪽 스캔/결과, 오른쪽 검사 기록 2단
  if (isWeb) {
    return (
      <>
        <div>
          <h1 style={{ margin: '0 0 0.3rem', fontSize: '1.5rem' }}>성분 검사</h1>
          <p className="muted-text" style={{ margin: 0, fontSize: '0.92rem' }}>
            {intro}{' '}
            <Link to="/ingredients" className="link-btn" style={{ textDecoration: 'none', fontSize: '0.88rem' }}>
              어떤 성분을 찾나요? 유해성분 사전 ›
            </Link>
          </p>
        </div>
        <div className="home-web">
          <div className="home-col">
            {scanCard}
            {resultCard}
          </div>
          <div className="home-col">
            {auth ? (
              <HistoryCard refreshSignal={historyRefresh} />
            ) : (
              <div className="card">
                <h2 className="section-title">검사 기록</h2>
                <p className="muted-text" style={{ margin: 0 }}>
                  로그인하면 검사한 제품 기록을 모아보고 PDF 리포트로 받을 수 있어요.
                </p>
              </div>
            )}
          </div>
        </div>
      </>
    )
  }

  return (
    <>
      <p style={{ margin: '0 0 0.25rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
        {intro}{' '}
        <Link to="/ingredients" className="link-btn" style={{ textDecoration: 'none' }}>
          유해성분 사전 ›
        </Link>
      </p>
      <InstallCard dismissible />
      {scanCard}
      {resultCard}
      {auth && <HistoryCard refreshSignal={historyRefresh} />}
    </>
  )
}
