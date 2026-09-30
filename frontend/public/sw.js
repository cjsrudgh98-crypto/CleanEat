// CleanEat 서비스 워커 - 홈 화면에 추가(PWA)용.
// 원칙: 로그인/결제/API는 절대 건드리지 않는다. 화면(앱 껍데기)과 빌드 파일만 캐시한다.
//  - 페이지 이동: 네트워크 먼저 (항상 최신 화면), 오프라인이면 마지막 화면 -> 그것도 없으면 offline.html
//  - /assets/* (빌드 파일, 이름에 해시가 붙어 내용이 바뀌지 않음): 캐시 먼저
//  - 아이콘/상품 이미지: 캐시를 먼저 보여주고 뒤에서 새로 받아 둔다
// 캐시 구조를 바꿀 때만 VERSION을 올린다 (올리면 예전 캐시는 activate 때 지워짐)
const VERSION = 'v1'
const SHELL_CACHE = `cleaneat-shell-${VERSION}`
const ASSET_CACHE = `cleaneat-assets-${VERSION}`
const IMAGE_CACHE = `cleaneat-images-${VERSION}`
const SHELL_KEY = '/__app-shell'
const OFFLINE_URL = '/offline.html'
// 배포할 때마다 새 해시 파일이 쌓이므로 오래된 것부터 정리한다
const MAX_ASSETS = 60
const MAX_IMAGES = 120

// 서비스 워커가 가로채면 안 되는 경로 (API, 소셜 로그인, H2 콘솔)
const BYPASS_PREFIXES = ['/api/', '/oauth2/', '/login/', '/h2-console']

self.addEventListener('install', (event) => {
  // 오프라인 안내 화면과 거기서 쓰는 아이콘은 미리 받아 둔다
  event.waitUntil(
    Promise.all([
      caches.open(SHELL_CACHE).then((cache) => cache.add(new Request(OFFLINE_URL, { cache: 'reload' }))),
      caches.open(IMAGE_CACHE).then((cache) => cache.add('/icons/icon-192.png')),
    ]).then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  const keep = [SHELL_CACHE, ASSET_CACHE, IMAGE_CACHE]
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith('cleaneat-') && !keep.includes(k)).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') return
  const url = new URL(request.url)
  // 다른 사이트(토스 결제, 폰트 CDN, 주소 검색 등)는 브라우저에 맡긴다
  if (url.origin !== self.location.origin) return
  if (BYPASS_PREFIXES.some((prefix) => url.pathname.startsWith(prefix))) return

  if (request.mode === 'navigate') {
    event.respondWith(networkFirstPage(request))
  } else if (url.pathname.startsWith('/assets/')) {
    event.respondWith(cacheFirst(request, ASSET_CACHE, MAX_ASSETS))
  } else if (url.pathname.startsWith('/icons/') || url.pathname.startsWith('/images/')) {
    event.respondWith(staleWhileRevalidate(request, IMAGE_CACHE, MAX_IMAGES))
  }
})

async function networkFirstPage(request) {
  try {
    const response = await fetch(request)
    // 모든 화면이 같은 index.html이라 하나만 "마지막 화면"으로 보관 (리다이렉트/오류 응답은 보관하지 않음)
    if (response.ok && response.type === 'basic' && !response.redirected) {
      const cache = await caches.open(SHELL_CACHE)
      await cache.put(SHELL_KEY, response.clone())
    }
    return response
  } catch {
    const cache = await caches.open(SHELL_CACHE)
    return (await cache.match(SHELL_KEY)) || (await cache.match(OFFLINE_URL)) || Response.error()
  }
}

async function cacheFirst(request, cacheName, maxEntries) {
  const cache = await caches.open(cacheName)
  const cached = await cache.match(request)
  if (cached) return cached
  const response = await fetch(request)
  if (response.ok) {
    await cache.put(request, response.clone())
    trim(cache, maxEntries)
  }
  return response
}

async function staleWhileRevalidate(request, cacheName, maxEntries) {
  const cache = await caches.open(cacheName)
  const cached = await cache.match(request)
  const refresh = fetch(request)
    .then((response) => {
      if (response.ok) {
        cache.put(request, response.clone()).then(() => trim(cache, maxEntries))
      }
      return response
    })
    .catch(() => cached)
  return cached || refresh
}

async function trim(cache, maxEntries) {
  const keys = await cache.keys()
  // 먼저 넣은 것부터 지운다
  for (let i = 0; i < keys.length - maxEntries; i++) {
    await cache.delete(keys[i])
  }
}
