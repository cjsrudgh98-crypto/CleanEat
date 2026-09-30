import { useRef, useState } from 'react'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { ReceiptScanResult } from '../api/types'
import { CameraModal } from './CameraModal'
import { decodeReceiptImage } from '../lib/codeReader'

interface ReceiptTabProps {
  onResult: (result: ReceiptScanResult) => void
}

/**
 * 영수증의 QR코드/바코드로 상품 목록을 불러와 한꺼번에 성분 검사한다.
 * 읽는 방법: 카메라 / 영수증 사진 업로드 / 코드 직접 붙여넣기
 */
export function ReceiptTab({ onResult }: ReceiptTabProps) {
  const { auth } = useAuth()
  const [code, setCode] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loadingText, setLoadingText] = useState<string | null>(null)
  const [cameraOpen, setCameraOpen] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  async function submit(raw: string) {
    const trimmed = raw.trim()
    if (!trimmed) return
    setError(null)
    setLoading(true)
    try {
      onResult(await api.scanReceipt(trimmed, auth))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '서버에 연결할 수 없습니다.')
    } finally {
      setLoading(false)
    }
  }

  // 사진에 QR/바코드가 있으면 그걸로(정확함), 없으면 서버에서 상품명 글자를 읽어 찾는다
  async function handleFile(file: File | undefined) {
    if (!file) return
    setError(null)
    setLoading(true)
    try {
      const decoded = await decodeReceiptImage(file).catch(() => null)
      if (decoded) {
        setCode(decoded)
        await submit(decoded)
        return
      }
      setLoadingText('영수증의 상품명을 읽는 중... (10초 정도 걸릴 수 있어요)')
      onResult(await api.scanReceiptImage(file, auth))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '사진을 읽지 못했습니다.')
    } finally {
      setLoading(false)
      setLoadingText(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  return (
    <div>
      <p className="muted-text" style={{ margin: '0 0 0.75rem' }}>
        영수증의 <b>QR코드·바코드</b>나 <b>상품명</b>을 읽어서 구매한 상품들을 한 번에 검사해요.
        일반 마트 영수증은 <b>사진 올리기</b>를 이용해주세요 - 상품명을 읽어서 찾아드려요.
      </p>

      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '0.75rem' }}>
        <button className="btn btn-primary" type="button" onClick={() => setCameraOpen(true)} disabled={loading}>
          📷 카메라로 스캔
        </button>
        <button className="btn" type="button" onClick={() => fileInputRef.current?.click()} disabled={loading}>
          🧾 영수증 사진 올리기
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => handleFile(e.target.files?.[0])}
        />
      </div>

      <label htmlFor="receiptCodeInput">또는 코드 직접 입력</label>
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <input
          id="receiptCodeInput"
          type="text"
          autoComplete="off"
          placeholder="CLEANEAT-RECEIPT:CE-… 또는 상품 바코드들"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') submit(code)
          }}
        />
        <button className="btn" type="button" onClick={() => submit(code)} disabled={loading}>
          검사
        </button>
      </div>

      {loading && (
        <div className="status-row" style={{ marginTop: '0.75rem' }}>
          <span className="spinner" />
          <span>{loadingText ?? '영수증 상품을 검사하는 중...'}</span>
        </div>
      )}
      {error && (
        <div className="error-box" style={{ marginTop: '0.75rem' }}>
          {error}
        </div>
      )}

      <CameraModal
        open={cameraOpen}
        mode="receipt"
        onClose={() => setCameraOpen(false)}
        onDetected={(detected) => {
          setCode(detected)
          setCameraOpen(false)
          submit(detected)
        }}
      />
    </div>
  )
}
