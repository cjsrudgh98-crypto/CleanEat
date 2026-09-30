import { useState } from 'react'
import { BarcodeTab } from './BarcodeTab'
import { ImageScanTab } from './ImageScanTab'
import { ReceiptTab } from './ReceiptTab'
import type { ReceiptScanResult, ScanResult } from '../api/types'

interface ScanCardProps {
  onResult: (result: ScanResult) => void
  onReceiptResult: (result: ReceiptScanResult) => void
}

type Tab = 'barcode' | 'image' | 'receipt'

const TABS: { value: Tab; label: string }[] = [
  { value: 'barcode', label: '바코드 스캔' },
  { value: 'image', label: '이미지 스캔' },
  { value: 'receipt', label: '영수증 스캔' },
]

export function ScanCard({ onResult, onReceiptResult }: ScanCardProps) {
  const [tab, setTab] = useState<Tab>('barcode')

  return (
    <div className="card">
      <div role="tablist" style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        {TABS.map((t) => (
          <button
            key={t.value}
            role="tab"
            id={`tab-${t.value}-btn`}
            aria-selected={tab === t.value}
            aria-controls={`tab-${t.value}`}
            className="btn"
            style={tab === t.value ? { borderColor: 'var(--brand)', color: 'var(--brand)' } : undefined}
            onClick={() => setTab(t.value)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <section id="tab-barcode" role="tabpanel" aria-labelledby="tab-barcode-btn" hidden={tab !== 'barcode'}>
        <BarcodeTab onResult={onResult} />
      </section>
      <section id="tab-image" role="tabpanel" aria-labelledby="tab-image-btn" hidden={tab !== 'image'}>
        <ImageScanTab onResult={onResult} />
      </section>
      <section id="tab-receipt" role="tabpanel" aria-labelledby="tab-receipt-btn" hidden={tab !== 'receipt'}>
        <ReceiptTab onResult={onReceiptResult} />
      </section>
    </div>
  )
}
