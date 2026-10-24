/** localStorage 키. 다른 탭의 변경을 `storage` 이벤트로 받을 때 이 키로 거른다. */
export const ACCESS_TOKEN_KEY = 'homesense.accessToken';
const REFRESH_TOKEN_KEY = 'homesense.refreshToken';
/**
 * 로그인 세션 하나를 가리키는 무작위 ID. 로그인·가입으로 토큰을 처음 저장할 때 새로 만들고, 재발급(Rotation)으로
 * 토큰 값만 바뀔 때는 그대로 둔다. 로그아웃은 시작할 때 이 값을 기억했다가 끝났을 때 같을 때만 토큰을 지운다 —
 * 그사이 다른 탭이 새로 로그인한 토큰(다른 세션)은 지우지 않고, 같은 세션을 다른 탭이 재발급한 토큰은 지운다.
 * 토큰 값만으로는 "같은 세션의 재발급"과 "새 로그인"을 구분할 수 없어 따로 둔다(CLAUDE.md "로그아웃 조건부 삭제" 절).
 */
const SESSION_ID_KEY = 'homesense.authSessionId';

function newSessionId(): string {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

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
  /** 지금 저장된 토큰의 세션 ID. 이 값을 도입하기 전에 저장된 토큰이면 null. */
  getSessionId(): string | null {
    return localStorage.getItem(SESSION_ID_KEY);
  },
  /** 로그인·가입 — 새 세션으로 저장한다. */
  startSession(accessToken: string, refreshToken: string): void {
    localStorage.setItem(SESSION_ID_KEY, newSessionId());
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  /** 재발급 — 같은 세션의 토큰 값만 바꾼다(세션 ID 유지). */
  setTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clearTokens(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(SESSION_ID_KEY);
  },
};
