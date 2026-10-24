import { matchPath, type Location } from 'react-router-dom';
import { AUTH_ROUTES } from '../../routes/paths';

export const DEFAULT_AUTHENTICATED_PATH = '/';

/** 복귀 목적지 — 브라우저가 해석한 같은 출처 URL의 경로·검색어·해시. */
type ReturnTarget = Pick<Location, 'pathname' | 'search' | 'hash'>;

/**
 * 라우터가 경로를 맞추기 전에 하는 디코딩과 같다(react-router `matchRoutes`의 `decodePath` — 공개 API가 아니라 옮겨
 * 적었다). 구간마다 퍼센트 인코딩을 풀고 `/`로 풀린 문자는 다시 `%2F`로 둔다. 잘못된 인코딩이면 그대로 둔다.
 */
function decodeLikeRouter(pathname: string): string {
  try {
    return pathname
      .split('/')
      .map((segment) => decodeURIComponent(segment).replace(/\//g, '%2F'))
      .join('/');
  } catch {
    return pathname;
  }
}

/**
 * 인증 화면(그 하위 경로 포함)인지 — 라우터의 `matchPath`로 맞춰 대소문자·끝 슬래시·퍼센트 인코딩을 라우터와 똑같이
 * 본다. 문자열 비교로 하면 라우터는 같은 화면으로 보는데 판정은 다르게 보는 변형(`/LOGIN`, `/login/`, `/%6Cogin`)이 남는다.
 */
function isAuthScreen(pathname: string): boolean {
  const decoded = decodeLikeRouter(pathname);
  return Object.values(AUTH_ROUTES).some((path) => matchPath({ path, end: false }, decoded) !== null);
}

/**
 * state.from을 복귀 목적지로 쓸 수 있으면 정규화해 돌려준다. 외부 주소 판정은 문자열 규칙이 아니라 브라우저가 해석한
 * URL의 출처로 한다 — `//host`, `/\host`(브라우저가 `\`를 `/`로 읽는다), 검색어·해시를 이어 붙여 생기는 `//host`까지
 * 한 번에 걸러진다. 그다음 해석된 경로로 인증 화면을 거른다(돌아가면 다시 인증 화면으로 고리를 만든다).
 */
function returnTarget(location: Location): ReturnTarget | null {
  const from = (location.state as { from?: Partial<Location> } | null)?.from;
  if (!from || typeof from.pathname !== 'string') return null;
  const search = typeof from.search === 'string' ? from.search : '';
  const hash = typeof from.hash === 'string' ? from.hash : '';
  let url: URL;
  try {
    url = new URL(`${from.pathname}${search}${hash}`, window.location.origin);
  } catch {
    return null;
  }
  if (url.origin !== window.location.origin || isAuthScreen(url.pathname)) return null;
  return { pathname: url.pathname, search: url.search, hash: url.hash };
}

/**
 * 프로그램설계서 6.1절 "이전 목적지 보존" 시나리오: 비로그인 상태로 인증 필요 액션을 시도하다가
 * /login으로 보내진 경우, navigate('/login', { state: { from: location } })로 넘겨준 원래 위치를 로그인·가입
 * 성공 후 되돌려주기 위해 읽어낸다. AUTH-01·AUTH-02가 함께 쓴다. 원래 동작(관심 등록 등)의 재실행은 돌아간
 * 화면의 훅이 맡는다(useFavoriteToggle의 sessionStorage 재생) — 로그인·가입 모두 같은 화면으로 돌아가므로
 * 같은 재생이 일어난다.
 */
export function getRedirectPath(location: Location): string {
  const target = returnTarget(location);
  return target ? `${target.pathname}${target.search}${target.hash}` : DEFAULT_AUTHENTICATED_PATH;
}

/**
 * 인증 화면끼리 옮겨 갈 때(AUTH-01 ↔ AUTH-02) 넘겨줄 state. 복귀 목적지를 잃지 않게 이어 준다 — 예전엔 로그인
 * 화면에서 회원가입으로 넘어가면 `from`이 사라져 가입 후 항상 HOME-01로 갔다. 넘기는 값은 정규화한 목적지다.
 */
export function returnState(location: Location): { from: ReturnTarget } | undefined {
  const target = returnTarget(location);
  return target ? { from: target } : undefined;
}
