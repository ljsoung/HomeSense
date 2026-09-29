import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GENERIC_ERROR_MESSAGE, getErrorMessage } from '../../lib/apiError';
import { DEFAULT_REQUEST_TIMEOUT_MS, httpClient } from '../../lib/httpClient';
import { tokenStorage } from '../../lib/tokenStorage';
import {
  __resetSessionStateForTests,
  advanceSessionGeneration,
  currentSessionGeneration,
  jwtSubject,
  markTabAuthenticated,
  REFRESH_TIMEOUT_MS,
  restoreSession,
  setSessionExpiredListener,
} from './session';

// httpClient의 axios 어댑터를 교체해 서버를 흉내 낸다. jsdom에는 navigator.locks가 없어 탭 안의 직렬화만
// 검증된다(탭 사이는 e2e).

type Handler = (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>;

const unauthorized = { success: false, data: null, error: { code: 'UNAUTHORIZED', message: '인증이 필요합니다' }, timestamp: '' };
const ok = <T,>(data: T) => ({ success: true, data, error: null, timestamp: '' });

function respond(config: InternalAxiosRequestConfig, status: number, data: unknown): Promise<AxiosResponse> {
  const response: AxiosResponse = { data, status, statusText: String(status), headers: {}, config };
  if (status >= 200 && status < 300) return Promise.resolve(response);
  return Promise.reject(new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', config, null, response));
}

/** 서명 없는 JWT 모양 토큰(payload에 sub만). 클라이언트는 서명을 검증하지 않으므로 계정 비교 검증에 충분하다. */
function fakeJwt(sub: string, nonce: string): string {
  const b64url = (v: object) => btoa(JSON.stringify(v)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64url({ alg: 'HS256' })}.${b64url({ sub, jti: nonce })}.sig`;
}

function bearer(config: InternalAxiosRequestConfig): string | null {
  const header = config.headers.get('Authorization');
  return typeof header === 'string' ? header.replace('Bearer ', '') : null;
}

function body(config: InternalAxiosRequestConfig): { refreshToken?: string } {
  return typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {});
}

let refreshCalls: Array<{ refreshToken?: string; timeout?: number }>;
let protectedCalls: Array<string | null>;
let handler: Handler;
const originalAdapter = httpClient.defaults.adapter;

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

/** 기본 서버: A2만 유효, R1 → (A2, R2) 재발급. */
function defaultServer(overrides: { refresh?: Handler } = {}): Handler {
  return async (config) => {
    if (config.url === '/api/auth/refresh') {
      refreshCalls.push({ ...body(config), timeout: config.timeout });
      if (overrides.refresh) return overrides.refresh(config);
      if (body(config).refreshToken === 'R1') return respond(config, 200, ok({ accessToken: 'A2', refreshToken: 'R2', expiresIn: 1800 }));
      return respond(config, 401, { ...unauthorized, error: { code: 'INVALID_REFRESH_TOKEN', message: '유효하지 않은 Refresh Token입니다' } });
    }
    if (config.url === '/api/favorites/properties' || config.url === '/api/users/me') {
      protectedCalls.push(bearer(config));
      if (bearer(config) === 'A2' || bearer(config) === 'B-access') {
        return respond(config, 200, ok(config.url === '/api/users/me' ? { userId: 1, email: 'a@test.com', nickname: '닉', createdAt: '' } : []));
      }
      return respond(config, 401, unauthorized);
    }
    return respond(config, 404, { success: false, data: null, error: { code: 'NOT_FOUND', message: 'x' }, timestamp: '' });
  };
}

beforeEach(() => {
  localStorage.clear();
  __resetSessionStateForTests();
  refreshCalls = [];
  protectedCalls = [];
  handler = defaultServer();
  httpClient.defaults.adapter = (config) => handler(config);
});

afterEach(() => {
  httpClient.defaults.adapter = originalAdapter;
  setSessionExpiredListener(null);
});

describe('401 인터셉터 — 재발급 후 재시도', () => {
  it('만료된 Access Token이면 재발급 1회 후 새 토큰으로 재시도해 성공한다', async () => {
    tokenStorage.setTokens('A1', 'R1');

    const { data } = await httpClient.get('/api/favorites/properties');

    expect(data.success).toBe(true);
    expect(refreshCalls).toHaveLength(1);
    expect(protectedCalls).toEqual(['A1', 'A2']);
    expect(tokenStorage.getRefreshToken()).toBe('R2');
  });

  it('같은 탭에서 동시에 난 401 N건은 재발급 1회를 공유한다', async () => {
    tokenStorage.setTokens('A1', 'R1');

    const results = await Promise.all(Array.from({ length: 5 }, () => httpClient.get('/api/favorites/properties')));

    expect(results.every((r) => r.data.success)).toBe(true);
    expect(refreshCalls).toHaveLength(1);
  });

  it('저장소의 Access Token이 보낸 것보다 새것이면 재발급하지 않고 저장소 값으로 재시도한다', async () => {
    tokenStorage.setTokens('A1', 'R1');
    const server = defaultServer();
    handler = async (config) => {
      // 요청이 나간 뒤 다른 곳(다른 탭)에서 재발급이 끝났다.
      if (bearer(config) === 'A1') tokenStorage.setTokens('A2', 'R2');
      return server(config);
    };

    await httpClient.get('/api/favorites/properties');

    expect(refreshCalls).toHaveLength(0);
    expect(protectedCalls).toEqual(['A1', 'A2']);
  });

  it('재시도가 또 401이면 두 번째 재발급 없이 호출자에게 넘긴다', async () => {
    tokenStorage.setTokens('A1', 'R1');
    handler = async (config) => {
      if (config.url === '/api/auth/refresh') {
        refreshCalls.push(body(config));
        // 두 번째 재발급이 나가면(재시도 표시가 빠진 결함) 거부해, 무한 반복 대신 단언으로 드러나게 한다.
        if (refreshCalls.length > 1) return respond(config, 401, { ...unauthorized, error: { code: 'INVALID_REFRESH_TOKEN', message: 'x' } });
        return respond(config, 200, ok({ accessToken: 'A2-still-rejected', refreshToken: 'R2', expiresIn: 1800 }));
      }
      protectedCalls.push(bearer(config));
      return respond(config, 401, unauthorized);
    };

    await expect(httpClient.get('/api/favorites/properties')).rejects.toMatchObject({ response: { status: 401 } });
    expect(refreshCalls).toHaveLength(1);
    expect(protectedCalls).toEqual(['A1', 'A2-still-rejected']);
  });

  it('재발급 요청에는 재발급 전용 timeout을 쓰고, 일반 요청에는 기본 timeout이 걸린다', async () => {
    tokenStorage.setTokens('A1', 'R1');
    await httpClient.get('/api/favorites/properties');
    expect(refreshCalls[0].timeout).toBe(REFRESH_TIMEOUT_MS);
    expect(httpClient.defaults.timeout).toBe(DEFAULT_REQUEST_TIMEOUT_MS);
  });
});

describe('재발급 대상이 아닌 401', () => {
  it('비즈니스 401(현재 비밀번호 불일치 INVALID_CREDENTIALS)은 재발급하지 않는다', async () => {
    tokenStorage.setTokens('A1', 'R1');
    handler = async (config) => {
      if (config.url === '/api/auth/refresh') refreshCalls.push(body(config));
      return respond(config, 401, { ...unauthorized, error: { code: 'INVALID_CREDENTIALS', message: '비밀번호가 일치하지 않습니다' } });
    };

    markTabAuthenticated(null); // 로그인 상태로 확정된 탭(회원정보 수정 화면)에서 보낸 요청이다.

    await expect(httpClient.put('/api/users/me', {})).rejects.toMatchObject({ response: { status: 401 } });
    expect(refreshCalls).toHaveLength(0);
  });

  it('로그인 실패(인증 엔드포인트의 401)는 재발급하지 않는다', async () => {
    tokenStorage.setTokens('A1', 'R1');
    handler = async (config) => {
      if (config.url === '/api/auth/refresh') refreshCalls.push(body(config));
      return respond(config, 401, { ...unauthorized, error: { code: 'INVALID_CREDENTIALS', message: '비밀번호가 일치하지 않습니다' } });
    };

    await expect(httpClient.post('/api/auth/login', {})).rejects.toMatchObject({ response: { status: 401 } });
    expect(refreshCalls).toHaveLength(0);
  });

  it('토큰 없이 보낸 요청의 401은 재발급하지 않는다', async () => {
    await expect(httpClient.get('/api/favorites/properties')).rejects.toMatchObject({ response: { status: 401 } });
    expect(refreshCalls).toHaveLength(0);
    expect(protectedCalls).toEqual([null]);
  });
});

describe('재발급 도중 세션이 바뀐 경우', () => {
  it('재발급 도중 로그아웃(세대 변경)이 일어나면 저장하지 않고, 상태를 건드리지 않고, 재시도하지 않는다', async () => {
    tokenStorage.setTokens('A1', 'R1');
    const gate = deferred<void>();
    handler = defaultServer({
      refresh: async (config) => {
        await gate.promise;
        return respond(config, 200, ok({ accessToken: 'A2', refreshToken: 'R2', expiresIn: 1800 }));
      },
    });
    const expired = vi.fn();
    setSessionExpiredListener(expired);

    const request = httpClient.get('/api/favorites/properties');
    await vi.waitFor(() => expect(refreshCalls).toHaveLength(1));
    advanceSessionGeneration(); // 로그아웃 시작
    tokenStorage.clearTokens();
    gate.resolve();

    await expect(request).rejects.toMatchObject({ response: { status: 401 } });
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(expired).not.toHaveBeenCalled();
    expect(protectedCalls).toEqual(['A1']); // 재시도 없음
  });

  it('테스트마다 세대가 0에서 시작한다(바로 앞 테스트가 올린 값이 남지 않는다)', () => {
    expect(currentSessionGeneration()).toBe(0);
  });

  it('재발급 도중 다른 탭이 다른 토큰을 저장하면 재발급 응답을 버린다', async () => {
    tokenStorage.setTokens('A1', 'R1');
    const gate = deferred<void>();
    handler = defaultServer({
      refresh: async (config) => {
        await gate.promise;
        return respond(config, 200, ok({ accessToken: 'A2', refreshToken: 'R2', expiresIn: 1800 }));
      },
    });

    const request = httpClient.get('/api/favorites/properties');
    await vi.waitFor(() => expect(refreshCalls).toHaveLength(1));
    tokenStorage.setTokens('B-access', 'B-refresh'); // 다른 탭에서 B로 로그인
    gate.resolve();
    await request;

    expect(tokenStorage.getAccessToken()).toBe('B-access');
    expect(tokenStorage.getRefreshToken()).toBe('B-refresh');
  });
});

describe('재발급 실패', () => {
  it('재발급이 401이면 토큰을 지우고 세션 종료를 알리고 원 요청을 실패시킨다', async () => {
    tokenStorage.setTokens('A1', 'R-expired');
    const expired = vi.fn();
    setSessionExpiredListener(expired);

    await expect(httpClient.get('/api/favorites/properties')).rejects.toMatchObject({ response: { status: 401 } });
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(tokenStorage.getRefreshToken()).toBeNull();
    expect(expired).toHaveBeenCalledTimes(1);
  });

  it('재발급 실패(세션 종료)도 로그인·로그아웃과 같은 증가 함수로 세대를 올린다', async () => {
    tokenStorage.setTokens('A1', 'R-expired');
    const before = currentSessionGeneration();
    await httpClient.get('/api/favorites/properties').catch(() => {});
    expect(currentSessionGeneration()).toBe(before + 1);
  });

  it('재발급이 timeout이면 토큰을 유지하고 세션 종료를 알리지 않는다', async () => {
    tokenStorage.setTokens('A1', 'R1');
    handler = defaultServer({
      refresh: async (config) => Promise.reject(new AxiosError(`timeout of ${config.timeout}ms exceeded`, 'ECONNABORTED', config)),
    });
    const expired = vi.fn();
    setSessionExpiredListener(expired);

    const error = await httpClient.get('/api/favorites/properties').catch((e: unknown) => e);

    expect((error as AxiosError).code).toBe('ECONNABORTED');
    expect(tokenStorage.getAccessToken()).toBe('A1');
    expect(tokenStorage.getRefreshToken()).toBe('R1');
    expect(expired).not.toHaveBeenCalled();
  });

  it('재발급이 네트워크 오류·5xx여도 토큰을 유지한다', async () => {
    for (const fail of [
      (config: InternalAxiosRequestConfig) => Promise.reject(new AxiosError('Network Error', 'ERR_NETWORK', config)),
      (config: InternalAxiosRequestConfig) => respond(config, 503, { success: false, data: null, error: { code: 'X', message: 'x' }, timestamp: '' }),
    ]) {
      localStorage.clear();
      __resetSessionStateForTests();
      tokenStorage.setTokens('A1', 'R1');
      handler = defaultServer({ refresh: fail });
      const expired = vi.fn();
      setSessionExpiredListener(expired);

      await expect(httpClient.get('/api/favorites/properties')).rejects.toBeDefined();
      expect(tokenStorage.getRefreshToken()).toBe('R1');
      expect(expired).not.toHaveBeenCalled();
    }
  });
});

describe('세션 복원(restoreSession)도 같은 재발급 경로를 쓴다', () => {
  it('만료된 토큰이면 재발급 1회 후 authenticated', async () => {
    tokenStorage.setTokens('A1', 'R1');
    const result = await restoreSession();
    expect(result.kind).toBe('authenticated');
    expect(refreshCalls).toHaveLength(1);
  });

  it('재발급이 거부되면 invalid이고 토큰이 지워진다', async () => {
    tokenStorage.setTokens('A1', 'R-expired');
    const result = await restoreSession();
    expect(result.kind).toBe('invalid');
    expect(tokenStorage.getAccessToken()).toBeNull();
  });

  it('재발급이 timeout이면 unreachable이고 토큰은 유지된다', async () => {
    tokenStorage.setTokens('A1', 'R1');
    handler = defaultServer({
      refresh: async (config) => Promise.reject(new AxiosError('timeout', 'ECONNABORTED', config)),
    });
    const result = await restoreSession();
    expect(result.kind).toBe('unreachable');
    expect(tokenStorage.getRefreshToken()).toBe('R1');
  });
});

describe('오류 문구', () => {
  it('timeout·네트워크 오류는 서버 문구가 없어 클라이언트 대체 문구를 쓴다', () => {
    const config = { headers: {} } as InternalAxiosRequestConfig;
    expect(getErrorMessage(new AxiosError('timeout', 'ECONNABORTED', config))).toBe(GENERIC_ERROR_MESSAGE);
    expect(getErrorMessage(new AxiosError('Network Error', 'ERR_NETWORK', config))).toBe(GENERIC_ERROR_MESSAGE);
  });

  it('서버 문구가 있으면 그대로 쓴다', async () => {
    handler = async (config) => respond(config, 400, { success: false, data: null, error: { code: 'X', message: '서버 문구' }, timestamp: '' });
    const error = await httpClient.get('/api/anything').catch((e: unknown) => e);
    expect(getErrorMessage(error)).toBe('서버 문구');
  });
});

describe('다른 계정 토큰으로 재시도하지 않는다(JWT sub 비교)', () => {
  const A1 = fakeJwt('1', 'a1');
  const A2 = fakeJwt('1', 'a2');
  const B = fakeJwt('2', 'b');

  /** 계정 1의 새 토큰(A2)만 통과시키는 서버. 계정 2(B)도 유효한 토큰이지만 원 요청은 계정 1 것이다. */
  function accountServer(refresh?: Handler): Handler {
    return async (config) => {
      if (config.url === '/api/auth/refresh') {
        refreshCalls.push(body(config));
        if (refresh) return refresh(config);
        return respond(config, 200, ok({ accessToken: A2, refreshToken: 'RA2', expiresIn: 1800 }));
      }
      protectedCalls.push(bearer(config));
      if (bearer(config) === A2 || bearer(config) === B) return respond(config, 200, ok([]));
      return respond(config, 401, unauthorized);
    };
  }

  it('jwtSubject는 payload의 sub를 읽고, JWT가 아니면 null', () => {
    expect(jwtSubject(A1)).toBe('1');
    expect(jwtSubject(B)).toBe('2');
    expect(jwtSubject('A1')).toBeNull();
    expect(jwtSubject('x.@@@.y')).toBeNull();
  });

  it('[저장소 값으로 바로 재시도하는 경로] 저장소가 다른 계정(B)의 토큰이면 재시도하지 않고 상태도 건드리지 않는다', async () => {
    tokenStorage.setTokens(A1, 'RA1');
    const server = accountServer();
    handler = async (config) => {
      // 요청이 나간 뒤 다른 탭에서 계정 2로 로그인했다.
      if (bearer(config) === A1) tokenStorage.setTokens(B, 'RB');
      return server(config);
    };
    const expired = vi.fn();
    setSessionExpiredListener(expired);
    const generation = currentSessionGeneration();

    await expect(httpClient.get('/api/favorites/properties')).rejects.toMatchObject({ response: { status: 401 } });
    expect(protectedCalls).toEqual([A1]); // B로 재시도하지 않음
    expect(refreshCalls).toHaveLength(0);
    expect(tokenStorage.getAccessToken()).toBe(B);
    expect(expired).not.toHaveBeenCalled();
    expect(currentSessionGeneration()).toBe(generation);
  });

  it('[저장소 값으로 바로 재시도하는 경로] 같은 계정의 새 토큰이면 재시도한다', async () => {
    tokenStorage.setTokens(A1, 'RA1');
    const server = accountServer();
    handler = async (config) => {
      if (bearer(config) === A1) tokenStorage.setTokens(A2, 'RA2');
      return server(config);
    };

    await httpClient.get('/api/favorites/properties');
    expect(protectedCalls).toEqual([A1, A2]);
  });

  it('[재발급 후 재시도하는 경로] 재발급 도중 다른 탭이 다른 계정(B)으로 로그인하면 재시도하지 않는다', async () => {
    tokenStorage.setTokens(A1, 'RA1');
    const gate = deferred<void>();
    handler = accountServer(async (config) => {
      await gate.promise;
      return respond(config, 200, ok({ accessToken: A2, refreshToken: 'RA2', expiresIn: 1800 }));
    });
    const expired = vi.fn();
    setSessionExpiredListener(expired);

    const request = httpClient.get('/api/favorites/properties');
    await vi.waitFor(() => expect(refreshCalls).toHaveLength(1));
    tokenStorage.setTokens(B, 'RB'); // 다른 탭에서 계정 2로 로그인 — 재발급 결과는 compare-and-set이 버린다
    gate.resolve();

    await expect(request).rejects.toMatchObject({ response: { status: 401 } });
    expect(protectedCalls).toEqual([A1]); // B로 재시도하지 않음
    expect(tokenStorage.getAccessToken()).toBe(B);
    expect(expired).not.toHaveBeenCalled();
  });

  it('[재발급 후 재시도하는 경로] 같은 계정으로 재발급되면 새 토큰으로 재시도한다', async () => {
    tokenStorage.setTokens(A1, 'RA1');
    handler = accountServer();

    await httpClient.get('/api/favorites/properties');
    expect(refreshCalls).toHaveLength(1);
    expect(protectedCalls).toEqual([A1, A2]);
  });
});
