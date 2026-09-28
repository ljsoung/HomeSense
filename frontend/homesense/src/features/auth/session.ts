import axios from 'axios';
import { httpClient } from '../../lib/httpClient';
import { tokenStorage } from '../../lib/tokenStorage';
import type { ApiResponse } from '../../types/api';
import { getMe } from '../user/api';
import type { UserResponse } from '../user/types';
import type { LoginResponse } from './types';

/**
 * 새로고침 시 localStorage에 남은 토큰이 실제로 유효한지 서버로 확인한 결과.
 * - `authenticated`: GET /api/users/me 성공(필요하면 refresh 1회 후).
 * - `invalid`: 서버가 토큰을 거부했고 refresh도 실패 — 토큰을 지웠다(비로그인으로 확정).
 * - `unreachable`: 네트워크 오류·5xx 등으로 판정 불가 — 토큰은 지우지 않는다(일시 장애로 세션을
 *   잃지 않게). 화면은 비로그인으로 보이고, 다음 로드에서 다시 확인한다.
 */
export type SessionResult =
  | { kind: 'authenticated'; user: UserResponse }
  | { kind: 'invalid' }
  | { kind: 'unreachable' };

/** 서버가 응답은 했지만 요청을 거부한 경우(4xx) — 토큰 자체가 무효라는 뜻이다. */
function isRejected(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response !== undefined && error.response.status < 500;
}

async function refreshTokens(refreshToken: string): Promise<void> {
  const { data } = await httpClient.post<ApiResponse<LoginResponse>>('/api/auth/refresh', { refreshToken });
  const tokens = (data as Extract<ApiResponse<LoginResponse>, { success: true }>).data;
  tokenStorage.setTokens(tokens.accessToken, tokens.refreshToken);
}

async function verify(): Promise<SessionResult> {
  try {
    return { kind: 'authenticated', user: await getMe() };
  } catch (error) {
    if (!isRejected(error)) return { kind: 'unreachable' };
  }

  // Access Token이 만료·폐기됐다 — Refresh Token으로 한 번만 재발급을 시도한다.
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) {
    tokenStorage.clearTokens();
    return { kind: 'invalid' };
  }
  try {
    await refreshTokens(refreshToken);
    return { kind: 'authenticated', user: await getMe() };
  } catch (error) {
    if (!isRejected(error)) return { kind: 'unreachable' };
    tokenStorage.clearTokens();
    return { kind: 'invalid' };
  }
}

let inflight: Promise<SessionResult> | null = null;

/**
 * 동시에 여러 번 호출돼도 서버 확인은 한 번만 한다(single-flight). StrictMode 개발 모드가 마운트
 * effect를 두 번 실행하는데, 같은 Refresh Token으로 refresh가 두 번 나가면 백엔드 Rotation의 재사용
 * 탐지가 발동해 그 사용자의 세션이 전부 폐기된다(CLAUDE.md SVC-AUTH-01 Refresh Token Rotation 절).
 */
export function restoreSession(): Promise<SessionResult> {
  if (!inflight) {
    inflight = verify().finally(() => {
      inflight = null;
    });
  }
  return inflight;
}

async function postLogout(refreshToken: string): Promise<void> {
  await httpClient.post<ApiResponse<null>>('/api/auth/logout', { refreshToken });
}

/**
 * 서버 쪽 Refresh Token을 폐기한다(SVC-AUTH-01.logout). 이 API는 인증이 필요해 Access Token이 만료돼
 * 401이면 한 번 재발급한 뒤 새 Refresh Token으로 다시 로그아웃한다 — 재발급으로 옛 토큰은 이미
 * 교체·폐기되므로 결과적으로 이 기기의 세션이 서버에서 확실히 끊긴다. 실패해도 호출자는 로컬 토큰을
 * 지우고 로그아웃을 완료한다(서버 폐기는 최선 노력).
 */
export async function revokeSessionOnServer(): Promise<void> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return;
  try {
    await postLogout(refreshToken);
    return;
  } catch (error) {
    if (!axios.isAxiosError(error) || error.response?.status !== 401) throw error;
  }
  await refreshTokens(refreshToken);
  const rotated = tokenStorage.getRefreshToken();
  if (rotated) await postLogout(rotated);
}
