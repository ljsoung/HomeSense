import type { Location } from 'react-router-dom';

export const DEFAULT_AUTHENTICATED_PATH = '/';

/** 돌아가면 다시 인증 화면으로 고리를 만드는 경로 — 복귀 목적지로 쓰지 않는다. */
const AUTH_PATHS = ['/login', '/signup', '/password-reset'];

/** 앱 안의 경로이고 인증 화면이 아닐 때만 복귀 목적지로 쓴다. `//host`(프로토콜 상대 URL)도 막는다. */
function isReturnablePath(pathname: string): boolean {
  if (!pathname.startsWith('/') || pathname.startsWith('//') || pathname.includes('\\')) return false;
  return !AUTH_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`));
}

function returnTarget(location: Location): Location | null {
  const from = (location.state as { from?: Partial<Location> } | null)?.from;
  if (!from || typeof from.pathname !== 'string' || !isReturnablePath(from.pathname)) return null;
  return from as Location;
}

/**
 * 프로그램설계서 6.1절 "이전 목적지 보존" 시나리오: 비로그인 상태로 인증 필요 액션을 시도하다가
 * /login으로 보내진 경우, navigate('/login', { state: { from: location } })로 넘겨준 원래 위치를 로그인·가입
 * 성공 후 되돌려주기 위해 읽어낸다. AUTH-01·AUTH-02가 함께 쓴다. 원래 동작(관심 등록 등)의 재실행은 돌아간
 * 화면의 훅이 맡는다(useFavoriteToggle의 sessionStorage 재생) — 로그인·가입 모두 같은 화면으로 돌아가므로
 * 같은 재생이 일어난다.
 */
export function getRedirectPath(location: Location): string {
  const from = returnTarget(location);
  if (!from) {
    return DEFAULT_AUTHENTICATED_PATH;
  }
  return `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`;
}

/**
 * 인증 화면끼리 옮겨 갈 때(AUTH-01 ↔ AUTH-02) 넘겨줄 state. 복귀 목적지를 잃지 않게 그대로 이어 준다 —
 * 예전엔 로그인 화면에서 회원가입으로 넘어가면 `from`이 사라져 가입 후 항상 HOME-01로 갔다.
 */
export function returnState(location: Location): { from: Location } | undefined {
  const from = returnTarget(location);
  return from ? { from } : undefined;
}
