import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { tokenStorage } from './tokenStorage';

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 401 → 재발급 → 재시도를 이미 한 번 거친 요청. 두 번째 재발급을 막는다. */
    _authRetried?: boolean;
  }
}

/**
 * 요청 하나의 기본 응답 대기 상한. axios 기본값은 무제한이라 응답이 멈추면 화면이 무기한 기다렸다.
 * 값은 "원 요청 → 401 → 락 대기 → 재발급 → 재시도" 최악 경로의 예산(15초)에서 거꾸로 정했다
 * (CLAUDE.md "401 자동 재발급과 요청 timeout" 절). 요청별 `timeout`은 이 값을 덮어쓴다(axios 규칙).
 */
export const DEFAULT_REQUEST_TIMEOUT_MS = 4_000;

/**
 * COM-CFG-01 프론트엔드 대응: 백엔드 base URL은 개발 환경에서는 vite.config.ts의 서버 프록시
 * (/api -> http://localhost:8080)를 그대로 태우므로 비워둔다. 배포 환경에서 프런트/백엔드가
 * 다른 오리진에 떠 있다면 VITE_API_BASE_URL을 빌드 시 주입하라.
 */
export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: DEFAULT_REQUEST_TIMEOUT_MS,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * 저장된 accessToken을 요청마다 싣는다. 백엔드는 토큰이 없어도 요청을 차단하지 않으므로(CLAUDE.md 인증 절)
 * 비로그인 전용 엔드포인트에도 해가 없다. 재시도 요청도 이 인터셉터를 다시 거쳐 저장소의 새 토큰을 싣는다.
 */
httpClient.interceptors.request.use((config) => {
  const accessToken = tokenStorage.getAccessToken();
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return config;
});

/** 인증 엔드포인트(AuthController, `/api/auth/**`)는 재발급 대상에서 뺀다 — 재발급 호출 자체의 재귀도 이것으로 막힌다. */
function isAuthEndpoint(url: string | undefined): boolean {
  return url !== undefined && url.startsWith('/api/auth/');
}

/** 요청에 실린 Bearer 토큰. 없으면 null. */
export function sentAccessTokenOf(config: InternalAxiosRequestConfig | undefined): string | null {
  const header = config?.headers?.get?.('Authorization');
  if (typeof header !== 'string' || !header.startsWith('Bearer ')) return null;
  return header.slice('Bearer '.length);
}

/**
 * 재발급할 401인지 판별한다. 네 조건을 모두 만족할 때만 요청이 보낸 Access Token을 돌려준다.
 * - 응답 코드가 `UNAUTHORIZED`다 — Spring Security가 인증 없는 보호 엔드포인트 요청을 막을 때
 *   `RestAuthenticationEntryPoint`가 내는 코드다. 비즈니스 401(`INVALID_CREDENTIALS`: 로그인 실패·현재
 *   비밀번호 불일치, `INVALID_REFRESH_TOKEN`)은 토큰 만료가 아니므로 재발급하지 않는다.
 * - Access Token을 실어 보낸 요청이다(토큰 없이 받은 401은 재발급할 세션이 없다).
 * - 인증 엔드포인트 요청이 아니다.
 * - 아직 재시도하지 않은 요청이다.
 */
export function refreshableAccessToken(error: AxiosError): string | null {
  const config = error.config;
  if (!config || config._authRetried || isAuthEndpoint(config.url)) return null;
  if (error.response?.status !== 401) return null;
  const code = (error.response.data as { error?: { code?: string } } | undefined)?.error?.code;
  if (code !== 'UNAUTHORIZED') return null;
  return sentAccessTokenOf(config);
}

export type UnauthorizedRecovery =
  | { kind: 'retry' }
  | { kind: 'fail'; error?: unknown };

let recoverUnauthorized: ((sentAccessToken: string) => Promise<UnauthorizedRecovery>) | null = null;

/**
 * 401을 받았을 때 세션을 복구할 함수를 등록한다(features/auth/session.ts). httpClient가 인증 모듈을 직접
 * import하지 않도록 등록 방식으로 연결한다(순환 의존 방지).
 */
export function setUnauthorizedRecovery(recover: typeof recoverUnauthorized): void {
  recoverUnauthorized = recover;
}

/**
 * 401 → 재발급 → 원 요청 1회 재시도. 재시도는 원 요청의 config를 그대로 쓰므로 timeout·signal을 이어받는다.
 * 재시도가 또 401이면 `_authRetried` 때문에 그대로 호출자에게 넘어간다(두 번째 재발급 없음). 재발급이 결과를
 * 알 수 없게 끝나면(timeout·네트워크 오류·5xx·락 대기 초과) 그 오류를, 세션이 끝났으면 원래 401을 넘긴다.
 */
httpClient.interceptors.response.use(undefined, async (error: unknown) => {
  if (!axios.isAxiosError(error) || !recoverUnauthorized) throw error;
  const sentAccessToken = refreshableAccessToken(error);
  if (sentAccessToken === null || !error.config) throw error;
  error.config._authRetried = true;
  const result = await recoverUnauthorized(sentAccessToken);
  if (result.kind === 'retry') return httpClient.request(error.config);
  throw result.error ?? error;
});
