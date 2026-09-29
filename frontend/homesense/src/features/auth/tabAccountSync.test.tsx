import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { useEffect } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionAccountChangedError, SessionNotConfirmedError, getErrorMessage } from '../../lib/apiError';
import { logSearch } from '../search/api';
import { httpClient } from '../../lib/httpClient';
import { ACCESS_TOKEN_KEY, tokenStorage } from '../../lib/tokenStorage';
import { AuthProvider } from './AuthProvider';
import type { AuthStatus } from './authContext';
import {
  __resetSessionStateForTests,
  beginRecheck,
  currentSessionGeneration,
  markTabAuthenticated,
  markTabUnauthenticated,
  setRecheckListener,
  setSessionExpiredListener,
  syncWithStoredAccount,
} from './session';
import { useAuth } from './useAuth';

// 토큰 저장소(localStorage)는 탭끼리 공유된다. 다른 탭이 다른 계정으로 로그인하면 이 탭(A 화면)의 요청이
// 처음부터 그 계정 토큰으로 나갈 수 있다 — 요청 방어와 storage 이벤트 동기화를 검증한다.

type Handler = (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>;

const unauthorized = { success: false, data: null, error: { code: 'UNAUTHORIZED', message: '인증이 필요합니다' }, timestamp: '' };
const ok = <T,>(data: T) => ({ success: true, data, error: null, timestamp: '' });

function respond(config: InternalAxiosRequestConfig, status: number, data: unknown): Promise<AxiosResponse> {
  const response: AxiosResponse = { data, status, statusText: String(status), headers: {}, config };
  if (status >= 200 && status < 300) return Promise.resolve(response);
  return Promise.reject(new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', config, null, response));
}

function fakeJwt(sub: string, nonce: string): string {
  const b64url = (v: object) => btoa(JSON.stringify(v)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64url({ alg: 'HS256' })}.${b64url({ sub, jti: nonce })}.sig`;
}

function bearer(config: InternalAxiosRequestConfig): string | null {
  const header = config.headers.get('Authorization');
  return typeof header === 'string' ? header.replace('Bearer ', '') : null;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

const A1 = fakeJwt('1', 'a1');
const A2 = fakeJwt('1', 'a2');
const B1 = fakeJwt('2', 'b1');
const users: Record<string, { userId: number; nickname: string }> = {
  '1': { userId: 1, nickname: '계정A' },
  '2': { userId: 2, nickname: '계정B' },
};
const subOf = (token: string | null) => (token ? JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).sub : null);

let sent: Array<{ url?: string; token: string | null }>;
let handler: Handler;
const originalAdapter = httpClient.defaults.adapter;

/** 기본 서버: 모든 토큰이 유효하고, /api/users/me는 토큰의 계정을 돌려준다. */
const defaultServer: Handler = async (config) => {
  const token = bearer(config);
  if (config.url === '/api/users/me') {
    const user = users[subOf(token)];
    if (!user) return respond(config, 401, unauthorized);
    return respond(config, 200, ok({ ...user, email: `${user.userId}@test.com`, createdAt: '' }));
  }
  return respond(config, 200, ok([]));
};

/** 다른 탭이 저장소를 바꾼 것처럼 storage 이벤트를 보낸다(같은 탭의 setItem은 이벤트를 내지 않는다). */
function fireStorageEvent(key: string | null = ACCESS_TOKEN_KEY) {
  window.dispatchEvent(new StorageEvent('storage', { key, storageArea: localStorage }));
}

beforeEach(() => {
  localStorage.clear();
  __resetSessionStateForTests();
  sent = [];
  handler = defaultServer;
  httpClient.defaults.adapter = (config) => {
    sent.push({ url: config.url, token: bearer(config) });
    return handler(config);
  };
});

afterEach(() => {
  cleanup();
  httpClient.defaults.adapter = originalAdapter;
  setSessionExpiredListener(null);
  setRecheckListener(null);
});

describe('요청 방어 — 이 탭의 계정과 다른 토큰으로는 요청을 보내지 않는다', () => {
  it('저장소가 다른 계정(B)의 토큰이면 요청을 보내지 않고 거절하며 다시 확인을 시작한다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    expect(markTabAuthenticated(1)).toBe(true);
    const recheck = vi.fn();
    setRecheckListener(recheck);
    const generation = currentSessionGeneration();
    tokenStorage.setTokens(B1, 'RB'); // 다른 탭이 B로 로그인했다(이 탭엔 아직 이벤트 전).

    const error = await httpClient.post('/api/favorites/properties', { complexId: 1 }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(SessionAccountChangedError);
    expect(sent).toEqual([]); // 서버에 도달한 요청이 없다.
    expect(recheck).toHaveBeenCalledWith('checking');
    expect(currentSessionGeneration()).toBe(generation + 1);
    expect(getErrorMessage(error)).toContain('로그인 계정이 바뀌어');
  });

  it('다시 확인이 시작된 뒤의 요청은 막지 않는다(계정 확정 전까지 방어는 쉰다) — 다시 확인도 한 번만', async () => {
    tokenStorage.setTokens(A1, 'RA');
    markTabAuthenticated(1);
    const recheck = vi.fn();
    setRecheckListener(recheck);
    tokenStorage.setTokens(B1, 'RB');

    await httpClient.get('/api/favorites/properties').catch(() => undefined);
    await httpClient.get('/api/users/me');

    expect(recheck).toHaveBeenCalledTimes(1);
    expect(sent).toEqual([{ url: '/api/users/me', token: B1 }]);
  });

  it('같은 계정의 새 토큰(다른 탭의 재발급)이면 그대로 보낸다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    markTabAuthenticated(1);
    const recheck = vi.fn();
    setRecheckListener(recheck);
    tokenStorage.setTokens(A2, 'RA2');

    await httpClient.get('/api/favorites/properties');

    expect(sent).toEqual([{ url: '/api/favorites/properties', token: A2 }]);
    expect(recheck).not.toHaveBeenCalled();
  });

  it('로그인 상태로 확정되지 않은 탭(비로그인·확인 중)의 읽기 요청은 계정 비교 없이 보낸다', async () => {
    tokenStorage.setTokens(B1, 'RB');
    markTabUnauthenticated();

    await httpClient.get('/api/favorites/properties');

    expect(sent).toEqual([{ url: '/api/favorites/properties', token: B1 }]);
  });
});

describe('요청 방어 — 로그인 계정을 확정하지 않은 탭(확인 중·세대 증가 직후)', () => {
  it('checking 중 사용자 동작 요청(관심 단지 추가·해제)은 서버에 가지 않는다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    markTabUnauthenticated(); // 세션 확인 중 — 저장소 토큰이 이 탭 화면의 계정인지 아직 모른다.

    const added = await httpClient.post('/api/favorites/properties', { complexId: 1 }).catch((e: unknown) => e);
    const removed = await httpClient.delete('/api/favorites/properties/9').catch((e: unknown) => e);

    expect(added).toBeInstanceOf(SessionNotConfirmedError);
    expect(removed).toBeInstanceOf(SessionNotConfirmedError);
    expect(sent).toEqual([]);
    expect(getErrorMessage(added)).toContain('로그인 상태를 확인하는 중');
  });

  it('세대가 막 오른 직후(다시 확인 시작)의 사용자 동작 요청도 서버에 가지 않는다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    markTabAuthenticated(1);
    setRecheckListener(() => {});
    beginRecheck();

    const error = await httpClient.post('/api/favorites/properties', { complexId: 1 }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(SessionNotConfirmedError);
    expect(sent).toEqual([]);
  });

  it('checking 중에도 세션 복원(GET /api/users/me)·재발급(/api/auth/**)·읽기 요청은 보낸다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    markTabUnauthenticated();

    await httpClient.get('/api/users/me');
    await httpClient.post('/api/auth/refresh', { refreshToken: 'RA' });
    await httpClient.get('/api/complexes/popular');

    expect(sent.map((s) => s.url)).toEqual(['/api/users/me', '/api/auth/refresh', '/api/complexes/popular']);
  });

  it('계정과 무관하다고 표시한 요청(검색 기록)은 checking 중에도 보낸다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    markTabUnauthenticated();

    await logSearch('강남');

    expect(sent.map((s) => s.url)).toEqual(['/api/search/logs']);
  });

  it('토큰이 없으면(비로그인) 막지 않는다 — 토큰 없는 요청은 어느 계정으로도 실행되지 않는다', async () => {
    markTabUnauthenticated();

    await httpClient.post('/api/search/logs', { keyword: '강남' });
    await httpClient.post('/api/favorites/properties', { complexId: 1 });

    expect(sent).toEqual([
      { url: '/api/search/logs', token: null },
      { url: '/api/favorites/properties', token: null },
    ]);
  });

  it('로그인 계정으로 확정되면 사용자 동작 요청을 보낸다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    markTabAuthenticated(1);

    await httpClient.post('/api/favorites/properties', { complexId: 1 });

    expect(sent).toEqual([{ url: '/api/favorites/properties', token: A1 }]);
  });
});

describe('storage 이벤트 동기화(syncWithStoredAccount)', () => {
  it('로그인 탭: 다른 계정이면 checking, 토큰이 사라지면 anonymous로 다시 확인한다', () => {
    const recheck = vi.fn();
    setRecheckListener(recheck);

    tokenStorage.setTokens(A1, 'RA');
    markTabAuthenticated(1);
    tokenStorage.setTokens(B1, 'RB');
    syncWithStoredAccount();
    expect(recheck).toHaveBeenLastCalledWith('checking');

    tokenStorage.setTokens(A1, 'RA');
    markTabAuthenticated(1);
    tokenStorage.clearTokens();
    syncWithStoredAccount();
    expect(recheck).toHaveBeenLastCalledWith('anonymous');
    expect(recheck).toHaveBeenCalledTimes(2);
  });

  it('같은 계정의 재발급(토큰 값만 바뀜)은 무시하고, 한 번의 로그인이 낸 이벤트 여러 개에는 한 번만 반응한다', () => {
    const recheck = vi.fn();
    setRecheckListener(recheck);
    tokenStorage.setTokens(A1, 'RA');
    markTabAuthenticated(1);

    tokenStorage.setTokens(A2, 'RA2');
    syncWithStoredAccount();
    expect(recheck).not.toHaveBeenCalled();

    tokenStorage.setTokens(B1, 'RB');
    syncWithStoredAccount(); // accessToken 키 이벤트
    syncWithStoredAccount(); // refreshToken 키 이벤트(같은 로그인)
    expect(recheck).toHaveBeenCalledTimes(1);
  });

  it('비로그인 탭: 다른 탭이 로그인하면 checking으로 다시 확인한다', () => {
    const recheck = vi.fn();
    setRecheckListener(recheck);
    markTabUnauthenticated();

    tokenStorage.setTokens(B1, 'RB');
    syncWithStoredAccount();

    expect(recheck).toHaveBeenCalledWith('checking');
  });

  it('이전 세션에서 진행 중이던 재발급은 다시 확인 뒤 끝나도 상태를 바꾸지 않는다(세대 확인)', async () => {
    tokenStorage.setTokens(A1, 'RA');
    markTabAuthenticated(1);
    setRecheckListener(() => {});
    const expired = vi.fn();
    setSessionExpiredListener(expired);
    const refreshGate = deferred<void>();
    handler = async (config) => {
      if (config.url === '/api/auth/refresh') {
        await refreshGate.promise;
        return respond(config, 401, { ...unauthorized, error: { code: 'INVALID_REFRESH_TOKEN', message: 'x' } });
      }
      if (bearer(config) === A1) return respond(config, 401, unauthorized);
      return defaultServer(config);
    };

    const request = httpClient.get('/api/favorites/properties').catch((e: unknown) => e);
    await waitFor(() => expect(sent.some((s) => s.url === '/api/auth/refresh')).toBe(true));
    tokenStorage.setTokens(B1, 'RB'); // 재발급 도중 다른 탭이 B로 로그인
    syncWithStoredAccount();
    refreshGate.resolve();
    await request;

    expect(expired).not.toHaveBeenCalled();
    expect(tokenStorage.getAccessToken()).toBe(B1); // A의 재발급 실패가 B의 토큰을 지우지 않는다.
    expect(sent.filter((s) => s.token === B1 && s.url === '/api/favorites/properties')).toEqual([]);
  });
});

describe('AuthProvider — 다른 탭의 계정 변경을 화면에 반영한다', () => {
  function Probe({ history }: { history: AuthStatus[] }) {
    const { status, user } = useAuth();
    useEffect(() => {
      history.push(status);
    }, [status, history]);
    return <p data-testid="who">{status}:{user?.nickname ?? ''}</p>;
  }

  it('A 화면에서 storage 이벤트로 B 로그인을 받으면 checking을 거쳐 B로 확정한다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    const history: AuthStatus[] = [];
    render(
      <AuthProvider>
        <Probe history={history} />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('who').textContent).toBe('authenticated:계정A'));

    const meGate = deferred<void>();
    handler = async (config) => {
      if (config.url === '/api/users/me') await meGate.promise;
      return defaultServer(config);
    };
    act(() => {
      tokenStorage.setTokens(B1, 'RB');
      fireStorageEvent();
    });
    await waitFor(() => expect(screen.getByTestId('who').textContent).toBe('checking:'));
    meGate.resolve();
    await waitFor(() => expect(screen.getByTestId('who').textContent).toBe('authenticated:계정B'));

    const tail = history.slice(history.lastIndexOf('authenticated', history.length - 2));
    expect(tail).toEqual(['authenticated', 'checking', 'authenticated']);
    expect(sent.filter((s) => s.url === '/api/users/me').map((s) => s.token)).toEqual([A1, B1]);
  });

  it('다른 탭이 로그아웃하면(저장소 비움) 곧바로 anonymous가 되고 서버에 확인하지 않는다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    render(
      <AuthProvider>
        <Probe history={[]} />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('who').textContent).toBe('authenticated:계정A'));
    const before = sent.length;

    act(() => {
      tokenStorage.clearTokens();
      fireStorageEvent();
    });

    await waitFor(() => expect(screen.getByTestId('who').textContent).toBe('anonymous:'));
    expect(sent.length).toBe(before);
  });

  it('A 화면에서 요청 방어가 B 토큰을 막으면 같은 경로로 B로 확정한다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    render(
      <AuthProvider>
        <Probe history={[]} />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('who').textContent).toBe('authenticated:계정A'));

    tokenStorage.setTokens(B1, 'RB'); // 이벤트 없이(아직 전달 전) 저장소만 바뀌었다.
    let error: unknown;
    await act(async () => {
      error = await httpClient.post('/api/favorites/properties', { complexId: 1 }).catch((e: unknown) => e);
    });

    expect(error).toBeInstanceOf(SessionAccountChangedError);
    expect(sent.filter((s) => s.url === '/api/favorites/properties')).toEqual([]);
    await waitFor(() => expect(screen.getByTestId('who').textContent).toBe('authenticated:계정B'));
  });
});
