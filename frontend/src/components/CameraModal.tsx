import { useEffect, useRef, useState } from 'react'
import Quagga from '@ericblade/quagga2'
import { PRODUCT_BARCODE_READERS, RECEIPT_BARCODE_READERS, decodeQrFromCanvas } from '../lib/codeReader'

interface CameraModalProps {
  open: boolean
  onClose: () => void
  onDetected: (code: string) => void
  // product: 상품 바코드(EAN/UPC)만 / receipt: 영수증 QR코드 + 영수증 바코드(Code 128 등)까지
  mode?: 'product' | 'receipt'
}

// 영수증 모드에서 QR을 찾기 위해 카메라 화면을 캡처하는 간격
const QR_SCAN_INTERVAL_MS = 250

export function CameraModal({ open, onClose, onDetected, mode = 'product' }: CameraModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const viewportRef = useRef<HTMLDivElement>(null)
  const [hint, setHint] = useState('카메라 권한을 허용해주세요.')
  const onDetectedRef = useRef(onDetected)

  useEffect(() => {
    onDetectedRef.current = onDetected
  }, [onDetected])

  useEffect(() => {
    const dialog = dialogRef.current
    const viewport = viewportRef.current
    if (!open || !dialog || !viewport) return

    dialog.showModal()
    let active = true
    let qrTimer: number | undefined
    const readyHint =
      mode === 'receipt' ? '영수증의 QR코드나 바코드를 화면 중앙에 맞춰주세요.' : '바코드를 카메라 화면 중앙에 맞춰주세요.'

    // 한 번 인식하면 카메라를 멈추고 결과를 넘긴다 (QR과 바코드가 동시에 잡혀도 한 번만)
    const finish = (code: string) => {
      if (!active) return
      active = false
      window.clearInterval(qrTimer)
      try {
        Quagga.stop()
      } catch {
        // 이미 정지된 경우 무시
      }
      onDetectedRef.current(code)
    }

    Quagga.init(
      {
        inputStream: {
          type: 'LiveStream',
          target: viewport,
          constraints: { facingMode: 'environment' },
        },
        decoder: {
          readers: mode === 'receipt' ? RECEIPT_BARCODE_READERS : PRODUCT_BARCODE_READERS,
        },
        locate: true,
      },
      (err) => {
        if (!active) return
        if (err) {
          setHint('카메라를 시작할 수 없습니다. 권한을 확인해주세요.')
          return
        }
        Quagga.start()
        setHint(readyHint)

        // Quagga는 QR을 못 읽으므로, 영수증 모드에서는 Quagga가 띄운 영상을 주기적으로 캡처해 jsQR로 읽는다
        if (mode === 'receipt') {
          const canvas = document.createElement('canvas')
          qrTimer = window.setInterval(() => {
            const video = viewport.querySelector('video')
            if (!video || video.readyState < 2 || !video.videoWidth) return
            canvas.width = video.videoWidth
            canvas.height = video.videoHeight
            canvas.getContext('2d', { willReadFrequently: true })?.drawImage(video, 0, 0)
            const qr = decodeQrFromCanvas(canvas)
            if (qr) finish(qr)
          }, QR_SCAN_INTERVAL_MS)
        }
      },
    )

    const handleDetected = (result: { codeResult: { code: string | null } }) => {
      const code = result.codeResult.code
      if (code) finish(code)
    }
    Quagga.onDetected(handleDetected)

    return () => {
      active = false
      window.clearInterval(qrTimer)
      Quagga.offDetected(handleDetected)
      try {
        Quagga.stop()
      } catch {
        // 이미 정지된 경우 무시
      }
    }
  }, [open, mode])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!open && dialog?.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={dialogRef}
      className="card"
      style={{ width: 'min(420px, 92vw)', padding: '1.2rem' }}
      onClose={onClose}
      onCancel={onClose}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
        <h3 style={{ margin: 0, fontSize: '1rem' }}>
          {mode === 'receipt' ? '영수증 QR코드 · 바코드 스캔' : '카메라로 바코드 스캔'}
        </h3>
        <button className="icon-btn" aria-label="닫기" onClick={onClose} type="button">
          ×
        </button>
      </div>
      <div
        ref={viewportRef}
        style={{
          position: 'relative',
          width: '100%',
          aspectRatio: '4 / 3',
          background: '#000',
          borderRadius: 'var(--radius-sm)',
          overflow: 'hidden',
        }}
      />
      <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', margin: '0.6rem 0 0' }}>{hint}</p>
      <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'flex-end' }}>
        <button className="btn" onClick={onClose} type="button">
          닫기
        </button>
      </div>
    </dialog>
  )
}
