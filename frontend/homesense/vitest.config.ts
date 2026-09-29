import { defineConfig } from 'vitest/config';

// 단위 테스트 전용 설정. 앱 빌드 설정(vite.config.ts)의 Tailwind·dev 프록시는 필요 없어 분리했다.
// localStorage·AbortSignal 등 브라우저 API가 필요해 jsdom 환경을 쓴다. jsdom에는 navigator.locks가
// 없어 탭 사이 락은 여기서 검증되지 않는다 — 그 부분은 e2e(home01-multitab-refresh-check,
// auth-interceptor-multitab-check)가 맡는다.
export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
  },
});
