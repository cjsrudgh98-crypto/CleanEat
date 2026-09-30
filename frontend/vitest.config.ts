import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

// 프론트 자동 테스트 (npm test). 브라우저 대신 jsdom에서 컴포넌트를 그려서 확인한다.
// 테스트 파일: src/**/*.test.ts(x), 공통 준비: src/test/setup.ts
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      // 테스트마다 목(mock) 호출 기록/구현을 초기화 - 테스트끼리 영향을 주지 않게
      restoreMocks: true,
    },
  }),
)
