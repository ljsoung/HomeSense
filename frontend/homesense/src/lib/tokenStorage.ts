const ACCESS_TOKEN_KEY = 'homesense.accessToken';
const REFRESH_TOKEN_KEY = 'homesense.refreshToken';

/**
 * 판단 기록 — accessToken/refreshToken을 localStorage에 저장한다 (SVC-AUTH-01 응답이 두 토큰을
 * httpOnly Set-Cookie가 아니라 JSON body로 그대로 내려주므로, 브라우저가 자동 관리하는 httpOnly
 * 쿠키 저장은 백엔드 변경 없이는 애초에 선택지가 아니다). 트레이드오프: XSS로 임의 스크립트 실행이
 * 가능해지면 두 토큰 모두 탈취될 수 있다 — in-memory 저장(새로고침 시 유실, refreshToken으로 재발급
 * 필요)이 더 안전하지만 그 refreshToken 자체도 body로 내려오는 이상 어딘가엔 영속 저장해야 해
 * 근본적으로 안전해지지 않는다. httpOnly refresh 쿠키 + in-memory access token 조합이 실제로
 * 더 안전한 방향이나, 이는 SVC-AUTH-01이 Set-Cookie로 refreshToken을 내려주도록 백엔드를 바꿔야
 * 가능하다 — 이번 프론트엔드 전용 작업 범위 밖이라 향후 개선 과제로 남긴다.
 */
export const tokenStorage = {
  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  },
  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  },
  setTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clearTokens(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};
