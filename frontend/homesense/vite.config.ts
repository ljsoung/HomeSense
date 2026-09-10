import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // 백엔드는 CORS를 아직 로컬 프로필용으로 구성하지 않았다(application-prod.properties만
      // homesense.cors.allowed-origins를 참조). 백엔드를 건드리지 않고도 브라우저 CORS 제약을
      // 피하려면 Vite dev 서버가 같은 오리진인 것처럼 프록시하는 편이 표준적이다 — 이 프론트엔드
      // 작업 범위에서 백엔드 CORS 설정을 새로 추가하지 않기로 한 판단의 근거.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
