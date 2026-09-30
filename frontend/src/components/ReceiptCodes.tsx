import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import JsBarcode from 'jsbarcode'

// 백엔드 ReceiptCodeParser.CLEANEAT_PREFIX 와 같아야 한다
const RECEIPT_PREFIX = 'CLEANEAT-RECEIPT:'

/**
 * CleanEat 주문 영수증용 QR코드 + 바코드.
 * 성분 검사 > 영수증 스캔에서 이걸 찍으면 이 주문의 상품들을 한 번에 검사한다.
 * 코드에는 추측할 수 없는 주문번호(CE-...)만 들어 있고 이름·주소 같은 개인정보는 없다.
 */
export function ReceiptCodes({ tossOrderId }: { tossOrderId: string }) {
  const [qrUrl, setQrUrl] = useState<string | null>(null)
  const barcodeRef = useRef<SVGSVGElement>(null)
  const receiptCode = RECEIPT_PREFIX + tossOrderId

  useEffect(() => {
    let cancelled = false
    QRCode.toDataURL(receiptCode, { margin: 1, width: 220, errorCorrectionLevel: 'M' })
      .then((url) => {
        if (!cancelled) setQrUrl(url)
      })
      .catch(() => {
        if (!cancelled) setQrUrl(null)
      })
    return () => {
      cancelled = true
    }
  }, [receiptCode])

  useEffect(() => {
    if (!barcodeRef.current) return
    // 바코드는 길이를 줄이려고 주문번호만 넣는다 (서버는 CE-코드만 찾으면 됨)
    JsBarcode(barcodeRef.current, tossOrderId, {
      format: 'CODE128',
      width: 1,
      height: 46,
      margin: 0,
      displayValue: false,
      background: 'transparent',
    })
  }, [tossOrderId])

  return (
    <div className="receipt-codes">
      <p style={{ margin: '0 0 0.6rem', fontWeight: 800, fontSize: '0.9rem' }}>🧾 CleanEat 영수증</p>
      {qrUrl && <img src={qrUrl} alt="영수증 QR코드" className="receipt-qr" />}
      <svg ref={barcodeRef} className="receipt-barcode" role="img" aria-label="영수증 바코드" />
      <p className="muted-text" style={{ margin: '0.4rem 0 0', fontSize: '0.72rem', wordBreak: 'break-all' }}>
        {tossOrderId}
      </p>
      <p className="muted-text" style={{ margin: '0.5rem 0 0', fontSize: '0.75rem' }}>
        성분 검사 → 영수증 스캔에서 이 QR을 찍으면 주문한 상품을 한 번에 검사할 수 있어요.
      </p>
    </div>
  )
}
