import Quagga, { type QuaggaJSCodeReader } from '@ericblade/quagga2'
import jsQR from 'jsqr'

// 상품 바코드 (EAN-13/8, UPC)
export const PRODUCT_BARCODE_READERS: QuaggaJSCodeReader[] = ['ean_reader', 'ean_8_reader', 'upc_reader', 'upc_e_reader']

// 영수증 바코드는 보통 Code 128 / Code 39 (영수증 번호, 주문번호) - 상품 바코드도 함께 읽는다
export const RECEIPT_BARCODE_READERS: QuaggaJSCodeReader[] = ['code_128_reader', 'code_39_reader', ...PRODUCT_BARCODE_READERS]

export function decodeQrFromCanvas(canvas: HTMLCanvasElement): string | null {
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx || !canvas.width || !canvas.height) return null
  const image = ctx.getImageData(0, 0, canvas.width, canvas.height)
  const result = jsQR(image.data, image.width, image.height, { inversionAttempts: 'attemptBoth' })
  return result?.data || null
}

// 큰 사진은 줄여서 읽는다 (휴대폰 원본 사진은 인식이 느리고 오히려 잘 안 잡힘)
const MAX_DECODE_SIZE = 1600

function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('이미지를 열 수 없습니다.'))
    }
    img.src = url
  })
}

/**
 * 영수증 사진에서 QR코드 -> 없으면 바코드 순서로 읽는다.
 * 둘 다 못 찾으면 null.
 */
export async function decodeReceiptImage(file: File): Promise<string | null> {
  const img = await loadImage(file)
  const scale = Math.min(1, MAX_DECODE_SIZE / Math.max(img.naturalWidth, img.naturalHeight))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(img.naturalWidth * scale)
  canvas.height = Math.round(img.naturalHeight * scale)
  canvas.getContext('2d', { willReadFrequently: true })?.drawImage(img, 0, 0, canvas.width, canvas.height)

  const qr = decodeQrFromCanvas(canvas)
  if (qr) return qr

  try {
    const result = await Quagga.decodeSingle({
      src: canvas.toDataURL('image/png'),
      numOfWorkers: 0,
      inputStream: { size: canvas.width },
      decoder: { readers: RECEIPT_BARCODE_READERS },
      locate: true,
    })
    return result?.codeResult?.code ?? null
  } catch {
    return null
  }
}
