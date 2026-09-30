// 모든 테스트 전에 한 번 - jest-dom 매처(toBeInTheDocument 등)와 jsdom에 없는 브라우저 기능 채우기
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// 테스트가 그린 화면을 매번 치운다 (globals를 안 쓰므로 자동 정리가 안 됨)
afterEach(() => cleanup())

// jsdom에는 <dialog>의 showModal/close가 없다 - 주문 취소/반품 폼(OrderActionDialog)이 쓰므로 open 속성만 흉내 낸다.
// (Esc로 닫기는 jsdom이 cancel 이벤트를 만들지 않으므로 테스트에서 fireEvent(dialog, new Event('cancel'))로 보낸다)
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.removeAttribute('open')
  }
}

// jsdom에는 ResizeObserver가 없다 - 차트(SalesChart/RiskTrendChart)가 너비를 재는 데 쓰므로, 관찰을 시작하면
// 바로 폭 400px로 한 번 알려주는 가짜를 둔다 (실제 크기 계산은 브라우저 확인에서)
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    private readonly callback: ResizeObserverCallback
    constructor(callback: ResizeObserverCallback) {
      this.callback = callback
    }
    observe(target: Element) {
      this.callback([{ target, contentRect: { width: 400, height: 200 } } as ResizeObserverEntry], this)
    }
    unobserve() {}
    disconnect() {}
  }
}
