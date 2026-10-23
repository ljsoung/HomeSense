import { describe, expect, it } from 'vitest';
import type { Location } from 'react-router-dom';
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

  it.each(['//evil.example', 'https://evil.example', '/\\evil'])('앱 밖 경로(%s)는 홈', (pathname) => {
    expect(getRedirectPath(at({ pathname, search: '', hash: '' }))).toBe('/');
  });

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
