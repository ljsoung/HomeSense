import { describe, expect, it } from 'vitest';
import { matchRoutes, type Location } from 'react-router-dom';
import { AUTH_ROUTES } from '../../routes/paths';
import { getRedirectPath, returnState } from './redirect';

function at(from?: Partial<Location>): Location {
  return { pathname: '/login', search: '', hash: '', key: 'k', state: from ? { from } : null } as Location;
}

describe('getRedirectPath', () => {
  it('from이 없으면 홈', () => {
    expect(getRedirectPath(at())).toBe('/');
  });

  it('앱 안 경로면 검색어·해시까지 그대로', () => {
    expect(getRedirectPath(at({ pathname: '/complexes/7', search: '?deal=JEONSE', hash: '#h' }))).toBe('/complexes/7?deal=JEONSE#h');
  });

  it.each(['/login', '/signup', '/password-reset', '/signup/extra'])('인증 화면(%s)은 무시하고 홈', (pathname) => {
    expect(getRedirectPath(at({ pathname, search: '', hash: '' }))).toBe('/');
  });

  it.each([
    '//evil.example',
    'https://evil.example',
    '/\\evil.example', // 브라우저가 \를 /로 읽어 //evil.example
    '\\\\evil.example',
    '/\t/evil.example', // URL 파서가 탭을 지운다
    'javascript:alert(1)',
    '//evil.example/complexes/7', // 다른 출처의 경로를 우리 경로로 바꿔 쓰지도 않는다
    '/\\evil.example/my',
  ])('앱 밖 경로(%j)는 홈', (pathname) => {
    expect(getRedirectPath(at({ pathname, search: '', hash: '' }))).toBe('/');
  });

  it.each(['/LOGIN', '/SignUp/', '/Password-Reset', '/login/', '/%6Cogin', '/LOGIN/%73ub'])('라우터가 인증 화면으로 맞추는 변형(%s)도 홈', (pathname) => {
    expect(getRedirectPath(at({ pathname, search: '', hash: '' }))).toBe('/');
  });

  it.each([
    { pathname: '/', search: '/evil.example', hash: '' },
    { pathname: '/', search: '', hash: '/evil.example' },
  ])('경로로 이어지는 검색어·해시는 홈 (%o)', (from) => {
    expect(getRedirectPath(at(from))).toBe('/');
  });

  it.each(['/login', '/LOGIN', '/login/', '/%6Cogin', '/Signup/x', '/password-reset', '/login-help', '/%2Flogin', '/my', '/logi%6E%2F'])(
    '판정이 라우터의 매칭(matchRoutes)과 같다: %s',
    (pathname) => {
      const routes = Object.values(AUTH_ROUTES).flatMap((path) => [{ path }, { path: `${path}/*` }]);
      const routerSeesAuth = matchRoutes(routes, pathname) !== null;
      expect(getRedirectPath(at({ pathname, search: '', hash: '' })) === '/').toBe(routerSeesAuth);
    },
  );

  it('인증 화면 이름으로 시작하는 다른 경로는 허용', () => {
    expect(getRedirectPath(at({ pathname: '/login-help', search: '', hash: '' }))).toBe('/login-help');
  });
});

describe('returnState', () => {
  it('복귀 목적지를 그대로 이어 준다', () => {
    const from = { pathname: '/complexes/7', search: '', hash: '' };
    expect(returnState(at(from))).toEqual({ from });
  });

  it('쓸 수 없는 목적지는 넘기지 않는다', () => {
    expect(returnState(at({ pathname: '/signup', search: '', hash: '' }))).toBeUndefined();
    expect(returnState(at())).toBeUndefined();
  });
});
