// specs/ 공용 — 목업 토큰과 /api 기본 응답.
import { errBody, okBody } from '../mockApi.mjs';

/** 프론트가 `sub`를 읽어 계정을 구분하므로(탭 계정 동기화) 목업 토큰도 JWT 모양이어야 한다. 서명은 보지 않는다. */
export const fakeJwt = (sub, tag) =>
  `${Buffer.from('{"alg":"HS256"}').toString('base64url')}.${Buffer.from(JSON.stringify({ sub: String(sub), tag })).toString('base64url')}.sig`;

export const ACCESS_KEY = 'homesense.accessToken';
export const REFRESH_KEY = 'homesense.refreshToken';

export const json = (data, status = 200) => ({ status, contentType: 'application/json', body: okBody(data) });
export const jsonError = (status, code, message) => ({ status, contentType: 'application/json', body: errBody(code, message) });

/** Authorization 헤더의 토큰에서 sub를 읽는다. 토큰이 없으면 null. */
export function subjectOf(request) {
  const header = request.headers().authorization ?? '';
  const token = header.replace(/^Bearer /, '');
  if (!token) return null;
  try {
    return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()).sub ?? null;
  } catch {
    return null;
  }
}

/** 조건이 참이 될 때까지 기다리는 약속과 그것을 푸는 함수. */
export function gate() {
  let release;
  const promise = new Promise((resolve) => {
    release = resolve;
  });
  return { promise, release };
}
