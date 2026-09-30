import { useRef, useState } from 'react'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { ScanResult } from '../api/types'

interface ImageScanTabProps {
  onResult: (result: ScanResult) => void
}

export function ImageScanTab({ onResult }: ImageScanTabProps) {
  const { auth } = useAuth()
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dragOver, setDragOver] = useState(false)

  function pickFile(f: File | undefined | null) {
    if (!f) return
    setFile(f)
    setError(null)
    setPreviewUrl(URL.createObjectURL(f))
  }

  async function submit() {
    if (!file) return
    setError(null)
    setLoading(true)
    try {
      const result = await api.scanImage(file, auth)
      onResult(result)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '서버에 연결할 수 없습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <div
        role="button"
        tabIndex={0}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') inputRef.current?.click()
        }}
        onDragOver={(e) => {
          e.preventDefault()
          setDragOver(true)
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragOver(false)
          pickFile(e.dataTransfer.files[0])
        }}
        style={{
          border: `2px dashed ${dragOver ? 'var(--brand)' : 'var(--border)'}`,
          borderRadius: 'var(--radius-sm)',
          padding: '1.5rem',
          textAlign: 'center',
          cursor: 'pointer',
          color: 'var(--text-muted)',
          background: dragOver ? 'var(--brand-tint)' : 'transparent',
        }}
      >
        {previewUrl ? (
          <div>
            <img
              src={previewUrl}
              alt="업로드한 이미지 미리보기"
              style={{ maxHeight: 160, borderRadius: 'var(--radius-sm)' }}
            />
            <p style={{ margin: '0.5rem 0 0', fontSize: '0.82rem' }}>{file?.name}</p>
          </div>
        ) : (
          <p style={{ margin: 0 }}>성분표 사진을 드래그하거나 클릭해서 업로드하세요</p>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        hidden
        onChange={(e) => pickFile(e.target.files?.[0])}
      />

      <button
        className="btn btn-primary btn-block"
        style={{ marginTop: '0.75rem' }}
        disabled={!file || loading}
        onClick={submit}
      >
        분석하기
      </button>

      {loading && (
        <div className="status-row" style={{ marginTop: '0.75rem' }}>
          <span className="spinner" />
          <span>분석 중...</span>
        </div>
      )}
      {error && (
        <>
          <div className="error-box" style={{ marginTop: '0.75rem' }}>
            {error}
          </div>
          <button className="btn" style={{ marginTop: '0.5rem' }} onClick={submit}>
            재시도
          </button>
        </>
      )}
    </div>
  )
}
