import axios from 'axios';
import {
  httpClient,
  sentAccessTokenOf,
  setUnauthorizedRecovery,
  type UnauthorizedRecovery,
} from '../../lib/httpClient';
import { tokenStorage } from '../../lib/tokenStorage';
import type { ApiResponse } from '../../types/api';
import { getMe } from '../user/api';
import type { UserResponse } from '../user/types';
import type { LoginResponse } from './types';

/**
 * 새로고침 시 localStorage에 남은 토큰이 실제로 유효한지 서버로 확인한 결과.
 * - `authenticated`: GET /api/users/me 성공(필요하면 인터셉터가 refresh 1회 후 재시도).
 * - `invalid`: 서버가 토큰을 거부했고 refresh도 실패 — 토큰을 지웠다(비로그인으로 확정).
 * - `unreachable`: 네트워크 오류·5xx·timeout·락 대기 초과로 판정 불가 — 토큰은 지우지 않는다(일시 장애로
 *   세션을 잃지 않게). 화면은 비로그인으로 보이고, 다음 로드에서 다시 확인한다.
 */
export type SessionResult =
  | { kind: 'authenticated'; user: UserResponse }
  | { kind: 'invalid' }
  | { kind: 'unreachable' };

/** 서버가 응답은 했지만 요청을 거부한 경우(4xx) — 토큰 자체가 무효라는 뜻이다. */
function isRejected(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response !== undefined && error.response.status < 500;
}

// ---------------------------------------------------------------------------------------------------
// 세션 세대(generation)
// ---------------------------------------------------------------------------------------------------

let sessionGeneration = 0;

/**
 * 로그인·가입이 성공했을 때(토큰 저장 직전)와 로그아웃이 시작될 때 올린다. 재발급·복원은 시작 시점의 값을
 * 기억했다가 끝났을 때 값이 바뀌었으면 인증 상태를 건드리지 않고 원 요청도 재시도하지 않는다 — 사용자가
 * 직접 바꾼 세션을 늦게 끝난 재발급 결과가 되돌리지 않게 한다. 로그인 "시작"에 올리지 않는 이유: 비밀번호가
 * 틀려 로그인이 실패하면 진행 중인 복원 결과까지 버려져 헤더가 확인 중 상태에 머문다.
 */
export function advanceSessionGeneration(): number {
  sessionGeneration += 1;
  return sessionGeneration;
}

export function currentSessionGeneration(): number {
  return sessionGeneration;
}

let sessionExpiredListener: (() => void) | null = null;

/**
 * 재발급이 세션 종료로 끝났을 때(Refresh Token 만료·폐기·재사용 탐지, 또는 다른 탭의 로그아웃으로 저장소가
 * 빈 경우) 호출될 함수를 등록한다. 세대가 그대로일 때만 호출된다. AuthProvider가 비로그인으로 바꾼다.
 */
export function setSessionExpiredListener(listener: (() => void) | null): void {
  sessionExpiredListener = listener;
}

// ---------------------------------------------------------------------------------------------------
// 재발급 — 앱의 유일한 재발급 경로
// ---------------------------------------------------------------------------------------------------

/**
 * 재발급 요청의 응답 대기 상한. 락 대기 상한(`LOCK_WAIT_TIMEOUT_MS`)보다 길어야 한다 — 아래 설명 참고.
 * 예산 계산은 CLAUDE.md "401 자동 재발급과 요청 timeout" 절.
 */
export const REFRESH_TIMEOUT_MS = 5_000;

/**
 * 다른 탭이 쥔 재발급 락을 기다리는 상한. 초과하면 결과를 알 수 없는 상태로 끝나고 토큰은 지우지 않는다.
 *
 * 재발급 timeout보다 짧게 둔 이 순서가 막는 것은 재발급이 시작될 때 이미 락을 기다리던 탭뿐이다 — 그
 * 탭은 락을 쥔 탭의 timeout보다 먼저 포기하므로, 결과를 알 수 없게 끝난(서버에서는 이미 교체됐을 수도
 * 있는) 재발급 직후에 같은 Refresh Token으로 다시 재발급하지 않는다. 재발급 시작 후 늦게 열린 탭은 막지
 * 못한다: 상한 안에 락을 얻고 저장소의 옛 토큰으로 다시 재발급할 수 있으며, 첫 재발급이 서버에서 토큰을
 * 교체했다면 재사용 탐지로 세션이 끝난다(실패 방향이 로그아웃이라 보안상 안전).
 */
export const LOCK_WAIT_TIMEOUT_MS = 2_000;

/**
 * [조건 2] 재발급한 토큰은 응답을 기다리는 동안 저장소가 바뀌지 않았을 때만 저장한다(compare-and-set). 그사이
 * 로그인·가입(같은 탭이든 다른 탭이든)이나 로그아웃으로 저장소가 바뀌었다면 결과를 버린다 — 조건 없이
 * 덮어쓰면 A 세션 재발급 중에 B로 로그인했을 때 B의 토큰이 A의 재발급 결과로 바뀌어, 화면은 B인데 이후 인증
 * 요청은 A로 나가는 상태가 된다(Codex P1). 비교와 저장 사이에 await가 없어 이 탭 안에서는 끼어들 틈이 없고,
 * 다른 탭의 로그인 저장은 같은 락(`storeTokens`)을 거친다.
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
 * [조건 1] 재발급을 브라우저 전체(같은 오리진의 모든 탭)에서 한 번에 하나로 직렬화한다. 탭마다 따로 있는 모듈
 * 변수만으로는 탭 사이 경쟁을 막지 못한다 — 두 탭이 같은 Refresh Token으로 재발급을 보내면 백엔드 Rotation의
 * 재사용 탐지가 두 번째 요청을 탈취로 보고 그 사용자의 Refresh Token을 전부 폐기한다(e2e
 * home01-multitab-refresh-check로 재현). Web Locks API가 없는 브라우저(Chrome 69·Firefox 96·Safari 15.4
 * 미만)와 비보안 컨텍스트(LAN IP의 http)에서는 탭 안의 직렬화만 남는다.
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
 * 반드시 재발급 락 안에서 부른다.
 */
function clearTokensIfUnchanged(refreshToken: string | null): void {
  if (tokenStorage.getRefreshToken() === refreshToken) tokenStorage.clearTokens();
}

/** 요청에 쓰인 Access Token이 아직 저장소 값일 때만 지운다. 반드시 재발급 락 안에서 부른다. */
function clearTokensIfAccessUnchanged(accessToken: string | null): void {
  if (accessToken !== null && tokenStorage.getAccessToken() === accessToken) tokenStorage.clearTokens();
}

/**
 * `rejectedRefreshToken`은 거부당한 요청에 쓰인 토큰 쌍의 Refresh Token이다. 락을 얻은 뒤 저장소를 다시
 * 읽어, 값이 이미 바뀌어 있으면 다른 탭이 먼저 재발급(또는 새로 로그인)한 것이므로 다시 재발급하지 않고 그
 * 토큰을 쓴다 — 이미 교체된 옛 토큰을 서버로 보내면 재사용 탐지가 발동한다. 저장소가 비어 있으면 다른 탭이
 * 로그아웃했거나 세션이 끝난 것이다. 서버가 재발급을 거부(4xx)하면 락 안에서 조건부로 토큰을 지우고 예외를
 * 그대로 던진다.
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

export type RefreshOutcome =
  | { kind: 'refreshed' }
  | { kind: 'no-session' }
  | { kind: 'rejected' }
  | { kind: 'unreachable'; error: unknown };

let refreshInflight: Promise<RefreshOutcome> | null = null;

/**
 * 같은 탭 안의 동시 재발급을 진행 중인 Promise 하나로 합친다(single-flight). 탭 사이 직렬화는
 * `refreshAcrossTabs`의 Web Locks가 맡는다. 예외를 던지지 않고 결과를 돌려준다.
 * - `rejected`: 서버가 재발급을 거부했다(4xx — Refresh Token 만료·폐기·재사용 탐지, 계정 비활성). 토큰은 이미 지웠다.
 * - `unreachable`: timeout·네트워크 오류·5xx·락 대기 초과 — Refresh Token은 아직 유효할 수 있어 지우지 않는다.
 */
export function refreshSession(rejectedRefreshToken: string | null): Promise<RefreshOutcome> {
  if (!refreshInflight) {
    refreshInflight = refreshAcrossTabs(rejectedRefreshToken)
      .then((result): RefreshOutcome => ({ kind: result }))
      .catch((error: unknown): RefreshOutcome => (isRejected(error) ? { kind: 'rejected' } : { kind: 'unreachable', error }))
      .finally(() => {
        refreshInflight = null;
      });
  }
  return refreshInflight;
}

/**
 * httpClient 응답 인터셉터가 재발급 대상 401을 받았을 때 부른다(`setUnauthorizedRecovery`로 연결).
 * 1. 저장소의 Access Token이 실패한 요청이 보낸 것과 다르면 다른 곳에서 이미 재발급했으므로 재발급 없이 재시도한다.
 * 2. 같으면 그 토큰 쌍의 Refresh Token으로 재발급한다(single-flight + 탭 간 락, 락 안에서 다시 확인).
 * 3. [조건 3] 재발급이 끝났을 때 세대가 시작 시점과 다르면(그사이 로그인·로그아웃) 상태를 건드리지 않고 재시도도 하지 않는다.
 * 4. 세션이 끝났으면 세션 종료를 알리고 원 요청을 실패시킨다. 결과를 알 수 없으면 원 요청만 실패시킨다.
 */
export async function recoverFromUnauthorized(sentAccessToken: string): Promise<UnauthorizedRecovery> {
  const generationAtStart = sessionGeneration;
  const storedAccess = tokenStorage.getAccessToken();
  if (storedAccess === null) return { kind: 'fail' };
  if (storedAccess !== sentAccessToken) return { kind: 'retry' };

  const outcome = await refreshSession(tokenStorage.getRefreshToken());
  if (sessionGeneration !== generationAtStart) return { kind: 'fail' };
  switch (outcome.kind) {
    case 'refreshed':
      return { kind: 'retry' };
    case 'no-session':
    case 'rejected':
      // 세션 종료도 로그인·로그아웃과 같은 증가 함수로 세대를 올린다 — 이 시점 이후에 끝나는 다른 복구·복원
      // 결과가 이미 끝난 세션을 되살리지 않게 한다.
      advanceSessionGeneration();
      sessionExpiredListener?.();
      return { kind: 'fail' };
    case 'unreachable':
      return { kind: 'fail', error: outcome.error };
    default:
      return assertNeverOutcome(outcome);
  }
}

function assertNeverOutcome(value: never): never {
  throw new Error(`Unhandled refresh outcome: ${JSON.stringify(value)}`);
}

setUnauthorizedRecovery(recoverFromUnauthorized);

// ---------------------------------------------------------------------------------------------------
// 세션 복원(새로고침)
// ---------------------------------------------------------------------------------------------------

/**
 * getMe 한 번으로 확인한다. Access Token이 만료·폐기됐으면 httpClient 인터셉터가 위 재발급 경로로 한 번
 * 재발급하고 재시도한다(재발급 경로는 하나다). 그래도 거부되면(예: 그사이 탈퇴·정지, 또는 재발급 거부) 확인에
 * 쓴 토큰이 저장소에 그대로일 때만, 락 안에서 지운다.
 */
async function verify(): Promise<SessionResult> {
  try {
    return { kind: 'authenticated', user: await getMe() };
  } catch (error) {
    if (!isRejected(error)) return { kind: 'unreachable' };
    const usedAccessToken = axios.isAxiosError(error) ? sentAccessTokenOf(error.config) : null;
    try {
      await withRefreshLock(() => clearTokensIfAccessUnchanged(usedAccessToken));
      return { kind: 'invalid' };
    } catch {
      return { kind: 'unreachable' }; // 락 대기 초과 — 지우지 못했으므로 다음 로드에서 다시 확인한다.
    }
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
 * 두 번 실행한다).
 */
export function restoreSession(): Promise<SessionResult> {
  if (!inflight) {
    inflight = verify().finally(() => {
      inflight = null;
    });
  }
  return inflight;
}

// ---------------------------------------------------------------------------------------------------
// 로그아웃
// ---------------------------------------------------------------------------------------------------

/** 로그아웃 요청 하나의 응답 대기 상한(기본값보다 길게 둘 이유가 없어 기본값을 그대로 쓴다). */
const LOGOUT_REQUEST_TIMEOUT_MS = 4_000;

/**
 * 로그아웃 버튼을 누른 뒤 서버 폐기를 기다리는 전체 상한. 넘기면 서버 폐기를 중단하고 로컬 로그아웃을 끝낸다
 * (서버 폐기는 최선 노력). 로그아웃 → 401 → 재발급(락 대기 + 재발급) → 로그아웃으로 이어지면 요청별
 * timeout만으로는 오래 걸릴 수 있어 전체 상한을 따로 둔다.
 */
const LOGOUT_REVOKE_DEADLINE_MS = 5_000;

async function postLogout(refreshToken: string, signal: AbortSignal): Promise<void> {
  await httpClient.post<ApiResponse<null>>('/api/auth/logout', { refreshToken }, { timeout: LOGOUT_REQUEST_TIMEOUT_MS, signal });
}

/**
 * 서버 쪽 Refresh Token을 폐기한다(SVC-AUTH-01.logout). 이 API는 인증이 필요해 Access Token이 만료돼
 * 401이면 한 번 재발급한 뒤 새 Refresh Token으로 다시 로그아웃한다 — 인증 엔드포인트라 인터셉터가 재발급하지
 * 않으므로 여기서 같은 재발급 경로(`refreshSession`)를 직접 부른다.
 *
 * `signal`이 중단되면(전체 상한 초과) 진행 중인 로그아웃 요청을 취소하고 다음 단계로 가지 않는다 — 호출자가
 * 로컬 로그아웃을 끝낸 뒤 사용자가 다른 계정으로 다시 로그인했을 수 있는데, 뒤늦게 이어진 이 작업이 저장소를
 * 다시 읽어 그 새 계정의 토큰으로 로그아웃을 보내면 안 된다.
 */
async function revokeSessionOnServer(signal: AbortSignal): Promise<void> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return;
  try {
    await postLogout(refreshToken, signal);
    return;
  } catch (error) {
    if (!axios.isAxiosError(error) || error.response?.status !== 401) throw error;
  }
  if (signal.aborted) return;
  if ((await refreshSession(refreshToken)).kind !== 'refreshed') return;
  if (signal.aborted) return;
  const rotated = tokenStorage.getRefreshToken();
  if (rotated) await postLogout(rotated, signal);
}

/**
 * 서버 폐기를 `LOGOUT_REVOKE_DEADLINE_MS` 안에서만 기다린다. 넘기면 중단 신호를 보내고 곧바로 돌아온다 —
 * 호출자(`AuthProvider.logout`)는 결과와 무관하게 `finally`에서 로컬 로그아웃을 끝낸다. 예외는 던지지 않는다.
 */
export async function revokeSessionWithinDeadline(): Promise<void> {
  const controller = new AbortController();
  const deadline = new Promise<void>((resolve) => {
    const timer = setTimeout(() => {
      controller.abort();
      resolve();
    }, LOGOUT_REVOKE_DEADLINE_MS);
    controller.signal.addEventListener('abort', () => clearTimeout(timer));
  });
  try {
    await Promise.race([revokeSessionOnServer(controller.signal), deadline]);
  } catch {
    // 네트워크 오류·timeout 등 — 서버 폐기는 최선 노력이다.
  } finally {
    controller.abort(); // 정상 종료든 상한 초과든, 남은 단계가 이어지지 않게 한다.
  }
}

/** 테스트 전용: 모듈 상태를 초기화한다. */
export function __resetSessionStateForTests(): void {
  sessionGeneration = 0;
  sessionExpiredListener = null;
  refreshInflight = null;
  inflight = null;
}
