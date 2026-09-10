import axios from 'axios';

/**
 * COM-CFG-01 프론트엔드 대응: 백엔드 base URL은 개발 환경에서는 vite.config.ts의 서버 프록시
 * (/api -> http://localhost:8080)를 그대로 태우므로 비워둔다. 배포 환경에서 프런트/백엔드가
 * 다른 오리진에 떠 있다면 VITE_API_BASE_URL을 빌드 시 주입하라.
 *
 * accessToken 만료 시 /api/auth/refresh를 자동 호출하는 인터셉터는 이번 SCR-AUTH-01 범위 밖이다
 * (보호된 라우트가 실제로 생기는 시점에 별도로 추가) — 이 인스턴스를 그때 그대로 확장한다.
 */
export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: {
    'Content-Type': 'application/json',
  },
});
