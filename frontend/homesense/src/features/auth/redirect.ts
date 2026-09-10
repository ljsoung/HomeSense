import type { Location } from 'react-router-dom';

export const DEFAULT_AUTHENTICATED_PATH = '/';

/**
 * 프로그램설계서 6.1절 "이전 목적지 보존" 시나리오: 비로그인 상태로 인증 필요 액션을 시도하다가
 * 401로 /login으로 리다이렉트된 경우, react-router가 navigate('/login', { state: { from: location } })
 * 형태로 넘겨준 원래 위치를 로그인 성공 후 되돌려주기 위해 읽어낸다. 이 로직은 AUTH-01 외의 다른
 * 화면(예: 향후 보호된 라우트)에서도 재사용된다.
 */
export function getRedirectPath(location: Location): string {
  const from = (location.state as { from?: Location } | null)?.from;
  if (!from) {
    return DEFAULT_AUTHENTICATED_PATH;
  }
  return `${from.pathname}${from.search}${from.hash}`;
}
