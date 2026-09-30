import { useState } from 'react'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { ScanResult } from '../api/types'
import { CameraModal } from './CameraModal'

interface BarcodeTabProps {
  onResult: (result: ScanResult) => void
}

export function BarcodeTab({ onResult }: BarcodeTabProps) {
  const { auth } = useAuth()
  const [barcode, setBarcode] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [cameraOpen, setCameraOpen] = useState(false)

  async function submit(code: string) {
    const trimmed = code.trim()
    if (!trimmed) return
    setError(null)
    setLoading(true)
    try {
      const result = await api.scanBarcode(trimmed, auth)
      onResult(result)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '서버에 연결할 수 없습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <label htmlFor="barcodeInput">바코드 번호</label>
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <input
          id="barcodeInput"
          type="text"
          inputMode="numeric"
          autoComplete="off"
          placeholder="예: 3017620422003"
          value={barcode}
          onChange={(e) => setBarcode(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') submit(barcode)
          }}
        />
        <button className="btn btn-primary" onClick={() => submit(barcode)} disabled={loading}>
          조회
        </button>
      </div>
      <div style={{ marginTop: '0.5rem' }}>
        <button className="btn" type="button" onClick={() => setCameraOpen(true)}>
          📷 카메라로 스캔
        </button>
      </div>

      {loading && (
        <div className="status-row" style={{ marginTop: '0.75rem' }}>
          <span className="spinner" />
          <span>조회 중...</span>
        </div>
      )}
      {error && (
        <>
          <div className="error-box" style={{ marginTop: '0.75rem' }}>
            {error}
          </div>
          <button className="btn" style={{ marginTop: '0.5rem' }} onClick={() => submit(barcode)}>
            재시도
          </button>
        </>
      )}

      <CameraModal
        open={cameraOpen}
        onClose={() => setCameraOpen(false)}
        onDetected={(code) => {
          setBarcode(code)
          setCameraOpen(false)
          submit(code)
        }}
      />
    </div>
  )
}
