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

/**
 * 재발급 요청의 응답 대기 상한. axios 기본값은 무제한이라, 응답 없이 매달린 재발급이 락을 영원히 쥐고
 * 다른 모든 탭의 인증을 막을 수 있었다. 초과하면 결과를 알 수 없는 상태(`unreachable`)로 끝나고 토큰은
 * 지우지 않는다.
 */
const REFRESH_TIMEOUT_MS = 10_000;

/**
 * 다른 탭이 쥔 재발급 락을 기다리는 상한. 초과하면 이 탭은 `unreachable`(토큰 유지, 화면은 비로그인)로
 * 끝나고 다음 로드에서 다시 확인한다.
 *
 * 재발급 timeout보다 짧게 둔 이 순서가 막는 것은 재발급이 시작될 때 이미 락을 기다리던 탭뿐이다 — 그
 * 탭은 락을 쥔 탭의 timeout보다 먼저 포기하므로, 결과를 알 수 없게 끝난(서버에서는 이미 교체됐을 수도
 * 있는) 재발급 직후에 같은 Refresh Token으로 다시 재발급하지 않는다. 재발급 시작 후 늦게 열린 탭은 막지
 * 못한다: 상한 안에 락을 얻고 저장소의 옛 토큰으로 다시 재발급할 수 있으며, 첫 재발급이 서버에서 토큰을
 * 교체했다면 재사용 탐지로 세션이 끝난다. 이 경우는 "알려진 한계 — 결과를 알 수 없는 재발급"에 해당한다
 * (실패 방향이 로그아웃이라 보안상 안전).
 * 참고: CLAUDE.md "프론트엔드 세션 복원과 토큰 재발급 조율" 절의 "락 대기 상한" 행.
 */
const LOCK_WAIT_TIMEOUT_MS = 5_000;

/**
 * 재발급한 토큰은 응답을 기다리는 동안 저장소가 바뀌지 않았을 때만 저장한다(compare-and-set). 그사이
 * 로그인·가입(같은 탭이든 다른 탭이든)이나 로그아웃으로 저장소가 바뀌었다면 결과를 버린다 — 조건 없이
 * 덮어쓰면 A 세션 복원 중에 B로 로그인했을 때 B의 토큰이 A의 재발급 결과로 바뀌어, 화면은 B인데 이후 인증
 * 요청은 A로 나가는 상태가 됐다(Codex P1, e2e home01-login-during-restore-check로 재현). 비교와 저장
 * 사이에 await가 없어 이 탭 안에서는 끼어들 틈이 없고, 다른 탭의 로그인 저장은 같은 락(`storeTokens`)을
 * 거친다. 버린 재발급 결과(A의 새 Refresh Token)는 어디에도 저장되지 않는다.
 */
async function refreshTokens(refreshToken: string): Promise<void> {
  const { data } = await httpClient.post<ApiResponse<LoginResponse>>(
    '/api/auth/refresh',
    { refreshToken },
    { timeout: REFRESH_TIMEOUT_MS },
  );
  const tokens = (data as Extract<ApiResponse<LoginResponse>, { success: true }>).data;
  if (tokenStorage.getRefreshToken() !== refreshToken) return;
  tokenStorage.setTokens(tokens.accessToken, tokens.refreshToken);
}

const REFRESH_LOCK_NAME = 'homesense.auth.refresh';

/**
 * 재발급을 브라우저 전체(같은 오리진의 모든 탭)에서 한 번에 하나로 직렬화한다. 탭마다 따로 있는 모듈
 * 변수(아래 `inflight`)만으로는 탭 사이 경쟁을 막지 못한다 — Access Token이 만료된 채 두 탭을 동시에
 * 열면 두 탭이 같은 Refresh Token으로 재발급을 보내고, 백엔드 Rotation의 재사용 탐지가 두 번째 요청을
 * 탈취로 보고 그 사용자의 Refresh Token을 전부 폐기했다(e2e home01-multitab-refresh-check로 재현).
 * Web Locks API가 없는 브라우저(Chrome 69·Firefox 96·Safari 15.4 미만)에서는 탭 안의 직렬화만 남는다.
 */
function withRefreshLock<T>(task: () => T | Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks?.request) {
    // 대기 상한을 넘기면 request가 signal의 사유(DOMException)로 거부된다 — 호출자는 이를 axios 오류가
    // 아닌 "판정 불가"로 처리한다(isRejected가 false).
    return navigator.locks.request(REFRESH_LOCK_NAME, { signal: timeoutSignal(LOCK_WAIT_TIMEOUT_MS) }, async () => task());
  }
  return Promise.resolve(task());
}

/** AbortSignal.timeout이 없는 브라우저(Safari 15.4~15.x는 Web Locks는 있지만 이 API가 없다)용 대체. */
function timeoutSignal(ms: number): AbortSignal {
  if (typeof AbortSignal.timeout === 'function') return AbortSignal.timeout(ms);
  const controller = new AbortController();
  setTimeout(() => controller.abort(new DOMException('Lock wait timed out', 'TimeoutError')), ms);
  return controller.signal;
}

/**
 * 다른 탭이 그사이 새로 받은 토큰을 지우지 않도록, 저장소 값이 이 탭이 쓴 값 그대로일 때만 지운다.
 * 반드시 재발급 락 안에서 부른다 — 락 밖에서 비교와 삭제 사이에 다른 탭의 재발급이 끼어들면 방금 받은
 * 새 토큰을 지운다.
 */
function clearTokensIfUnchanged(refreshToken: string | null): void {
  if (tokenStorage.getRefreshToken() === refreshToken) tokenStorage.clearTokens();
}

/**
 * `rejectedRefreshToken`은 이 탭이 거부당한 시점에 저장소에 있던 Refresh Token이다. 락을 얻은 뒤 저장소
 * 값이 이미 바뀌어 있으면 다른 탭이 먼저 재발급(또는 새로 로그인)한 것이므로 다시 재발급하지 않고 그
 * 토큰을 그대로 쓴다 — 이미 교체된 옛 토큰을 서버로 보내면 재사용 탐지가 발동한다. 저장소가 비어 있으면
 * 다른 탭이 로그아웃했거나 세션이 끝난 것이다. 서버가 재발급을 거부(4xx)하면 예외를 그대로 던진다.
 */
async function refreshAcrossTabs(rejectedRefreshToken: string | null): Promise<'refreshed' | 'no-session'> {
  return withRefreshLock(async () => {
    const current = tokenStorage.getRefreshToken();
    if (!current) return 'no-session';
    if (current !== rejectedRefreshToken) return 'refreshed';
    try {
      await refreshTokens(current);
      return 'refreshed';
    } catch (error) {
      if (isRejected(error)) clearTokensIfUnchanged(current);
      throw error;
    }
  });
}

async function verify(): Promise<SessionResult> {
  // getMe가 거부되면, 그 요청에 쓰인 토큰 쌍의 Refresh Token을 기준으로 재발급 여부를 판단한다.
  const refreshTokenAtRequest = tokenStorage.getRefreshToken();
  try {
    return { kind: 'authenticated', user: await getMe() };
  } catch (error) {
    if (!isRejected(error)) return { kind: 'unreachable' };
  }

  // Access Token이 만료·폐기됐다 — 브라우저 전체에서 한 번만 재발급하고(다른 탭이 이미 했으면 그 결과를
  // 쓰고) 다시 확인한다.
  try {
    if ((await refreshAcrossTabs(refreshTokenAtRequest)) === 'no-session') {
      return { kind: 'invalid' };
    }
  } catch (error) {
    // 재발급 거부(4xx)면 refreshAcrossTabs가 락 안에서 이미 저장소를 정리했다. 재발급 timeout·네트워크
    // 오류·락 대기 초과는 결과를 알 수 없으므로 토큰을 지우지 않는다.
    return isRejected(error) ? { kind: 'invalid' } : { kind: 'unreachable' };
  }

  // 새 토큰으로도 거부되면(예: 그사이 탈퇴·정지) 세션을 끝낸다 — 단, 확인에 쓴 토큰이 저장소에 그대로일 때만,
  // 그리고 비교와 삭제를 락 안에서 한 번에 한다.
  const refreshTokenAfterRefresh = tokenStorage.getRefreshToken();
  try {
    return { kind: 'authenticated', user: await getMe() };
  } catch (error) {
    if (!isRejected(error)) return { kind: 'unreachable' };
  }
  try {
    await withRefreshLock(() => clearTokensIfUnchanged(refreshTokenAfterRefresh));
    return { kind: 'invalid' };
  } catch {
    return { kind: 'unreachable' }; // 락 대기 초과 — 지우지 못했으므로 다음 로드에서 다시 확인한다.
  }
}

/**
 * 로그인·가입으로 받은 토큰을 저장한다. 재발급과 같은 락을 거쳐, 다른 탭에서 진행 중인 재발급의
 * compare-and-set이나 조건부 삭제와 순서가 섞이지 않게 한다. 락 대기가 상한을 넘겨도(다른 탭이 락을 쥔
 * 채 멈춘 경우) 방금 로그인한 결과는 반드시 저장한다 — 그때도 재발급 쪽 compare-and-set이 이 값을
 * 덮어쓰지 않게 막는다.
 */
export async function storeTokens(accessToken: string, refreshToken: string): Promise<void> {
  try {
    await withRefreshLock(() => tokenStorage.setTokens(accessToken, refreshToken));
  } catch {
    tokenStorage.setTokens(accessToken, refreshToken);
  }
}

let inflight: Promise<SessionResult> | null = null;

/**
 * 같은 탭 안에서 동시에 여러 번 호출돼도 서버 확인은 한 번만 한다(StrictMode 개발 모드가 마운트 effect를
 * 두 번 실행한다). 탭 사이의 재발급 경쟁은 이것으로 막히지 않고 `withRefreshLock`이 막는다 — 같은
 * Refresh Token으로 재발급이 두 번 나가면 백엔드 Rotation의 재사용 탐지가 그 사용자의 세션을 전부
 * 폐기한다(CLAUDE.md SVC-AUTH-01 Refresh Token Rotation 절).
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
  // 다른 탭과 재발급이 겹치지 않게 같은 락을 거친다(다른 탭이 이미 재발급했으면 그 토큰으로 로그아웃).
  if ((await refreshAcrossTabs(refreshToken)) === 'no-session') return;
  const rotated = tokenStorage.getRefreshToken();
  if (rotated) await postLogout(rotated);
}
