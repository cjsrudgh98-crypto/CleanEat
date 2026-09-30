import { describe, expect, it } from 'vitest'
import { appendUnique } from './paging'

describe('appendUnique ("더 보기"로 다음 페이지 이어 붙이기)', () => {
  it('새 항목을 뒤에 붙인다', () => {
    expect(appendUnique([{ id: 1 }, { id: 2 }], [{ id: 3 }])).toEqual([{ id: 1 }, { id: 2 }, { id: 3 }])
  })

  it('그 사이 목록이 바뀌어 두 페이지에 걸쳐 온 항목은 한 번만 남긴다 (앞의 것 유지)', () => {
    const result = appendUnique([{ id: 1, v: 'old' }, { id: 2, v: 'old' }], [{ id: 2, v: 'new' }, { id: 3, v: 'new' }])
    expect(result).toEqual([{ id: 1, v: 'old' }, { id: 2, v: 'old' }, { id: 3, v: 'new' }])
  })
})
