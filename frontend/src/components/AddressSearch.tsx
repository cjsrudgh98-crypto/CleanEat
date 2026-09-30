import { useEffect, useRef, useState } from 'react'

// 카카오(다음) 우편번호 서비스 - 무료, API 키 불필요. https://postcode.map.daum.net/guide
const POSTCODE_SCRIPT_URL = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'

interface PostcodeResult {
  zonecode: string
  roadAddress: string
  jibunAddress: string
  userSelectedType: 'R' | 'J'
  bname: string
  buildingName: string
  apartment: 'Y' | 'N'
}

interface PostcodeInstance {
  embed: (element: HTMLElement, options?: { autoClose?: boolean }) => void
}

declare global {
  interface Window {
    daum?: {
      Postcode: new (options: {
        oncomplete: (data: PostcodeResult) => void
        width?: string | number
        height?: string | number
      }) => PostcodeInstance
    }
  }
}

export interface SelectedAddress {
  zonecode: string
  address: string
}

// 스크립트는 한 번만 불러오고 이후엔 같은 Promise를 재사용한다
let scriptPromise: Promise<void> | null = null
function loadPostcodeScript(): Promise<void> {
  if (window.daum?.Postcode) return Promise.resolve()
  if (!scriptPromise) {
    scriptPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = POSTCODE_SCRIPT_URL
      script.async = true
      script.onload = () => resolve()
      script.onerror = () => {
        scriptPromise = null
        reject(new Error('주소 검색 서비스를 불러오지 못했습니다.'))
      }
      document.head.appendChild(script)
    })
  }
  return scriptPromise
}

// 도로명 주소 뒤에 (법정동, 건물명)을 붙여서 택배 기사님이 찾기 쉽게 만든다 - 카카오 가이드 권장 방식
function formatAddress(data: PostcodeResult) {
  if (data.userSelectedType === 'J') return data.jibunAddress
  const extras = [data.bname && /[동로가]$/.test(data.bname) ? data.bname : '', data.apartment === 'Y' ? data.buildingName : '']
    .filter(Boolean)
    .join(', ')
  return extras ? `${data.roadAddress} (${extras})` : data.roadAddress
}

/** "주소 검색" 버튼 + 누르면 뜨는 우편번호 검색 창 */
export function AddressSearchButton({ onSelect }: { onSelect: (address: SelectedAddress) => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    else if (!open && dialog.open) dialog.close()
  }, [open])

  // 창이 열릴 때마다 검색 화면을 새로 그린다 (이전 검색 결과가 남지 않게)
  useEffect(() => {
    if (!open) return
    let cancelled = false
    loadPostcodeScript()
      .then(() => {
        const container = containerRef.current
        if (cancelled || !container || !window.daum) return
        container.innerHTML = ''
        new window.daum.Postcode({
          width: '100%',
          height: '100%',
          oncomplete: (data) => {
            onSelect({ zonecode: data.zonecode, address: formatAddress(data) })
            setOpen(false)
          },
        }).embed(container, { autoClose: false })
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [open, onSelect])

  return (
    <>
      <button
        type="button"
        className="btn"
        style={{ flexShrink: 0 }}
        onClick={() => {
          setError(null)
          setOpen(true)
        }}
      >
        주소 검색
      </button>

      <dialog
        ref={dialogRef}
        className="card"
        style={{ width: 'min(460px, 92vw)', padding: '1rem', border: '1px solid var(--border)' }}
        onClose={() => setOpen(false)}
        onCancel={() => setOpen(false)}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.6rem' }}>
          <h2 style={{ margin: 0, fontSize: '1rem' }}>주소 검색</h2>
          <button type="button" className="icon-btn" aria-label="닫기" onClick={() => setOpen(false)}>
            ×
          </button>
        </div>
        {error ? (
          <div className="error-box">{error}</div>
        ) : (
          <div ref={containerRef} style={{ height: 'min(470px, 70vh)', border: '1px solid var(--border)' }} />
        )}
      </dialog>
    </>
  )
}
