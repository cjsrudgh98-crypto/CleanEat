// 휴대폰 원본 사진(수 MB~10MB 이상)은 서버 업로드 한도(10MB)를 넘을 수 있어서, 올리기 전에 브라우저에서 줄인다.
// 긴 변 2400px이면 성분표/영수증 글자를 OCR로 읽기에 충분하고, JPEG로 다시 저장하면 보통 1MB 안팎이 된다.
const MAX_SIDE = 2400
const SKIP_BELOW_BYTES = 1.5 * 1024 * 1024
const JPEG_QUALITY = 0.9

export async function shrinkImage(file: File): Promise<File> {
  if (!file.type.startsWith('image/') || file.type === 'image/gif') return file

  try {
    // imageOrientation: 'from-image' - 세로로 찍은 사진이 옆으로 눕지 않게 EXIF 회전값을 반영한다
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height))
    if (scale === 1 && file.size < SKIP_BELOW_BYTES) {
      bitmap.close()
      return file
    }

    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      bitmap.close()
      return file
    }
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close()

    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY))
    if (!blob || blob.size >= file.size) return file

    const name = file.name.replace(/\.[^.]+$/, '') + '.jpg'
    return new File([blob], name, { type: 'image/jpeg' })
  } catch {
    // HEIC처럼 브라우저가 못 여는 형식이면 원본을 그대로 보낸다 (서버가 판단)
    return file
  }
}
