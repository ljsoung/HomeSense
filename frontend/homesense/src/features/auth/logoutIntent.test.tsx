import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { httpClient } from '../../lib/httpClient';
import { ACCESS_TOKEN_KEY, tokenStorage } from '../../lib/tokenStorage';
import { RequireAuth } from '../../routes/RequireAuth';
import { AuthProvider } from './AuthProvider';
import { __resetSessionStateForTests, setRecheckListener, setSessionExpiredListener } from './session';
import { useAuth } from './useAuth';

// 보호 화면에서 직접 로그아웃하면 HOME-01로 간다(RequireAuth의 signedOutByUser). 로그아웃이 서버 폐기를 기다리는
// 동안 다른 요청의 401 재발급이 세션 종료로 끝나거나 다른 탭이 토큰을 지워도 그 기록이 "세션 만료"로 덮이면
// 안 된다(Codex P2 두 건).

type Handler = (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>;

const ok = <T,>(data: T) => ({ success: true, data, error: null, timestamp: '' });
const failure = (code: string) => ({ success: false, data: null, error: { code, message: code }, timestamp: '' });

function respond(config: InternalAxiosRequestConfig, status: number, data: unknown): Promise<AxiosResponse> {
  const response: AxiosResponse = { data, status, statusText: String(status), headers: {}, config };
  if (status >= 200 && status < 300) return Promise.resolve(response);
  return Promise.reject(new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', config, null, response));
}

function fakeJwt(sub: string): string {
  const b64url = (v: object) => btoa(JSON.stringify(v)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64url({ alg: 'HS256' })}.${b64url({ sub, jti: 'a1' })}.sig`;
}

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

const A1 = fakeJwt('1');
let handler: Handler;
const sent: string[] = [];
const originalAdapter = httpClient.defaults.adapter;

function ProtectedScreen() {
  const { logout } = useAuth();
  return (
    <button type="button" onClick={() => void logout()}>
      로그아웃
    </button>
  );
}

function Tree() {
  return (
    <AuthProvider>
      <MemoryRouter initialEntries={['/my']}>
        <Routes>
          <Route
            path="/my"
            element={
              <RequireAuth>
                <ProtectedScreen />
              </RequireAuth>
            }
          />
          <Route path="/login" element={<p>로그인 화면</p>} />
          <Route path="/" element={<p>홈 화면</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

beforeEach(() => {
  localStorage.clear();
  __resetSessionStateForTests();
  sent.length = 0;
  httpClient.defaults.adapter = (config) => {
    sent.push(config.url ?? '');
    return handler(config);
  };
});

afterEach(() => {
  cleanup();
  httpClient.defaults.adapter = originalAdapter;
  setSessionExpiredListener(null);
  setRecheckListener(null);
});

describe('AuthProvider.logout — 로그아웃 의도는 서버 폐기를 기다리기 전에 기록한다', () => {
  it('로그아웃 중 다른 요청의 재발급이 세션 종료로 끝나도 로그인 화면이 아니라 홈으로 간다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    const slowGet = deferred();
    const logoutGate = deferred();
    handler = async (config) => {
      if (config.url === '/api/users/me') return respond(config, 200, ok({ userId: 1, email: 'a@test.com', nickname: 'A', createdAt: '' }));
      if (config.url === '/api/notifications') {
        await slowGet.promise; // 로그아웃이 시작된 뒤에 401을 받는다.
        return respond(config, 401, failure('UNAUTHORIZED'));
      }
      if (config.url === '/api/auth/refresh') return respond(config, 401, failure('INVALID_REFRESH_TOKEN'));
      if (config.url === '/api/auth/logout') {
        await logoutGate.promise; // 서버 폐기가 늦다 — 로그아웃은 아직 끝나지 않았다.
        return respond(config, 200, ok(null));
      }
      return respond(config, 200, ok([]));
    };
    render(<Tree />);
    await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy());

    const pendingGet = httpClient.get('/api/notifications').catch(() => undefined);
    await waitFor(() => expect(sent).toContain('/api/notifications'));

    act(() => screen.getByRole('button', { name: '로그아웃' }).click());
    await waitFor(() => expect(sent).toContain('/api/auth/logout'));

    // 로그아웃이 서버 응답을 기다리는 동안 느린 GET이 401을 받고, 재발급이 거부돼 세션 만료가 알려진다.
    await act(async () => {
      slowGet.resolve();
      await pendingGet;
    });
    expect(sent).toContain('/api/auth/refresh');
    await waitFor(() => expect(screen.getByText('홈 화면')).toBeTruthy());
    expect(screen.queryByText('로그인 화면')).toBeNull();

    await act(async () => {
      logoutGate.resolve();
    });
    expect(screen.getByText('홈 화면')).toBeTruthy();
    expect(tokenStorage.getAccessToken()).toBeNull();
  });

  it('로그아웃 중 다른 탭이 토큰을 지워도(다시 확인 경로) 로그인 화면이 아니라 홈으로 간다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    const logoutGate = deferred();
    handler = async (config) => {
      if (config.url === '/api/users/me') return respond(config, 200, ok({ userId: 1, email: 'a@test.com', nickname: 'A', createdAt: '' }));
      if (config.url === '/api/auth/logout') {
        await logoutGate.promise; // 서버 폐기가 늦다 — 로그아웃은 아직 끝나지 않았다.
        return respond(config, 200, ok(null));
      }
      return respond(config, 200, ok([]));
    };
    render(<Tree />);
    await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy());

    act(() => screen.getByRole('button', { name: '로그아웃' }).click());
    await waitFor(() => expect(sent).toContain('/api/auth/logout'));

    // 그사이 다른 탭이 로그아웃해 공유 저장소의 토큰을 지운다 → storage 이벤트 → syncWithStoredAccount → beginRecheck.
    act(() => {
      tokenStorage.clearTokens();
      window.dispatchEvent(new StorageEvent('storage', { key: ACCESS_TOKEN_KEY, newValue: null, storageArea: localStorage }));
    });
    await waitFor(() => expect(screen.getByText('홈 화면')).toBeTruthy());
    expect(screen.queryByText('로그인 화면')).toBeNull();

    await act(async () => {
      logoutGate.resolve();
    });
    expect(screen.getByText('홈 화면')).toBeTruthy();
  });

  it('로그아웃 중이 아니면 다른 탭의 로그아웃은 지금처럼 로그인 화면으로 보낸다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    handler = async (config) => {
      if (config.url === '/api/users/me') return respond(config, 200, ok({ userId: 1, email: 'a@test.com', nickname: 'A', createdAt: '' }));
      return respond(config, 200, ok([]));
    };
    render(<Tree />);
    await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy());

    act(() => {
      tokenStorage.clearTokens();
      window.dispatchEvent(new StorageEvent('storage', { key: ACCESS_TOKEN_KEY, newValue: null, storageArea: localStorage }));
    });
    await waitFor(() => expect(screen.getByText('로그인 화면')).toBeTruthy());
  });
});
