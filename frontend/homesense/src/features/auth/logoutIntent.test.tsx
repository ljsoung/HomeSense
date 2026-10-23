import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
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
          <Route
            path="/"
            element={
              <>
                <p>홈 화면</p>
                <Link to="/my">마이페이지로</Link>
              </>
            }
          />
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

  it('로그아웃 응답이 먼저 오고 그 뒤에 다른 탭 때문에 시작된 다시 확인이 끝나면, 이 탭은 B 토큰을 지우지 않고 B로 확정된다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    const B1 = fakeJwt('2');
    const logoutGate = deferred();
    const meGateB = deferred();
    handler = async (config) => {
      if (config.url === '/api/users/me') {
        if (String(config.headers.Authorization) === `Bearer ${B1}`) {
          await meGateB.promise; // B 확인은 이 탭의 로그아웃이 끝난 뒤에 끝난다.
          return respond(config, 200, ok({ userId: 2, email: 'b@test.com', nickname: 'B', createdAt: '' }));
        }
        return respond(config, 200, ok({ userId: 1, email: 'a@test.com', nickname: 'A', createdAt: '' }));
      }
      if (config.url === '/api/auth/logout') {
        await logoutGate.promise;
        return respond(config, 200, ok(null));
      }
      return respond(config, 200, ok([]));
    };
    render(<Tree />);
    await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy());

    act(() => screen.getByRole('button', { name: '로그아웃' }).click());
    await waitFor(() => expect(sent).toContain('/api/auth/logout'));

    // 그사이 다른 탭이 B로 로그인한다(새 세션). 이벤트를 받아 다시 확인하고 B로 /api/users/me를 보낸다.
    act(() => {
      tokenStorage.startSession(B1, 'RB');
      window.dispatchEvent(new StorageEvent('storage', { key: ACCESS_TOKEN_KEY, newValue: B1, storageArea: localStorage }));
    });
    await waitFor(() => expect(sent.filter((url) => url === '/api/users/me')).toHaveLength(2));

    // 이 탭의 로그아웃 응답이 먼저 온다. 예전엔 여기서 저장소를 비워 B까지 로그아웃시켰다(백로그 1번 하위).
    // 이제는 A 세션만 지우므로 B 토큰이 남고, 이어서 B 확인이 끝나 이 탭도 B로 확정된다.
    await act(async () => {
      logoutGate.resolve();
      await new Promise((resolve) => setTimeout(resolve, 0));
      meGateB.resolve();
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy());
    expect(screen.queryByText('로그인 화면')).toBeNull();
    expect(tokenStorage.getAccessToken()).toBe(B1);
  });

  it('직접 로그아웃 뒤 다른 탭의 로그인으로 이 탭이 B로 확정되면 기록이 내려가, B 세션 만료 시 로그인 화면으로 간다', async () => {
    tokenStorage.setTokens(A1, 'RA');
    const B1 = fakeJwt('2');
    handler = async (config) => {
      if (config.url === '/api/users/me') {
        const isB = String(config.headers.Authorization) === `Bearer ${B1}`;
        return respond(config, 200, ok({ userId: isB ? 2 : 1, email: 'x@test.com', nickname: isB ? 'B' : 'A', createdAt: '' }));
      }
      if (config.url === '/api/notifications') return respond(config, 401, failure('UNAUTHORIZED'));
      if (config.url === '/api/auth/refresh') return respond(config, 401, failure('INVALID_REFRESH_TOKEN'));
      return respond(config, 200, ok(null));
    };
    render(<Tree />);
    await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy());

    // 이 탭에서 직접 로그아웃 → 홈.
    await act(async () => {
      screen.getByRole('button', { name: '로그아웃' }).click();
    });
    await waitFor(() => expect(screen.getByText('홈 화면')).toBeTruthy());

    // 다른 탭이 B로 로그인 → 이 탭이 다시 확인해 B로 확정된다.
    act(() => {
      tokenStorage.startSession(B1, 'RB');
      window.dispatchEvent(new StorageEvent('storage', { key: ACCESS_TOKEN_KEY, newValue: B1, storageArea: localStorage }));
    });
    await waitFor(() => expect(sent.filter((url) => url === '/api/users/me')).toHaveLength(2));

    act(() => screen.getByRole('link', { name: '마이페이지로' }).click());
    await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy());

    // B 세션이 만료된다(401 → 재발급 거부). B는 이 탭에서 로그아웃한 적이 없으니 로그인 화면으로 간다.
    await act(async () => {
      await httpClient.get('/api/notifications').catch(() => undefined);
    });
    await waitFor(() => expect(screen.getByText('로그인 화면')).toBeTruthy());
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

describe('AuthProvider.logout — 로그아웃을 시작한 세션만 지운다(백로그 1번 하위)', () => {
  const B1 = fakeJwt('2');
  const logoutBodies: string[] = [];

  /** 로그아웃 응답을 gate가 풀릴 때까지 붙잡는다. status는 풀렸을 때 돌려줄 상태 코드. */
  function holdLogout(gate: { promise: Promise<void> }, status: number) {
    handler = async (config) => {
      if (config.url === '/api/users/me') {
        const isB = String(config.headers.Authorization) === `Bearer ${B1}`;
        return respond(config, 200, ok({ userId: isB ? 2 : 1, email: 'x@test.com', nickname: isB ? 'B' : 'A', createdAt: '' }));
      }
      if (config.url === '/api/auth/logout') {
        logoutBodies.push(JSON.parse(String(config.data)).refreshToken);
        await gate.promise;
        return status === 200 ? respond(config, 200, ok(null)) : respond(config, status, failure(status === 401 ? 'UNAUTHORIZED' : 'ERROR'));
      }
      return respond(config, 200, ok([]));
    };
  }

  async function clickLogout() {
    render(<Tree />);
    await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy());
    act(() => screen.getByRole('button', { name: '로그아웃' }).click());
    await waitFor(() => expect(sent).toContain('/api/auth/logout'));
  }

  beforeEach(() => {
    logoutBodies.length = 0;
  });

  it.each([200, 503])(
    '응답(%i)을 기다리는 동안 다른 탭이 로그인하면 — storage 이벤트가 아직 안 와도 — B 토큰을 남기고 이 탭이 B를 따라간다',
    async (status) => {
      tokenStorage.startSession(A1, 'RA');
      const gate = deferred();
      holdLogout(gate, status);
      await clickLogout();

      act(() => tokenStorage.startSession(B1, 'RB')); // 이벤트는 보내지 않는다 — 응답이 이벤트보다 먼저 처리되는 순서
      await act(async () => {
        gate.resolve();
      });

      // 다시 확인이 B 토큰으로 /api/users/me를 보내 B로 확정된다(보호 화면이 다시 보인다).
      await waitFor(() => expect(sent.filter((url) => url === '/api/users/me')).toHaveLength(2));
      await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy());
      expect(tokenStorage.getAccessToken()).toBe(B1);
      expect(tokenStorage.getRefreshToken()).toBe('RB');
      expect(logoutBodies).toEqual(['RA']);
    },
  );

  it('같은 세션을 다른 탭이 그사이 재발급했으면(토큰 값만 바뀜) 그 토큰도 지우고 비로그인으로 끝난다', async () => {
    tokenStorage.startSession(A1, 'RA');
    const gate = deferred();
    holdLogout(gate, 200);
    await clickLogout();

    act(() => tokenStorage.setTokens(fakeJwt('1'), 'RA2')); // 재발급 — 세션 ID 유지
    await act(async () => {
      gate.resolve();
    });

    await waitFor(() => expect(screen.getByText('홈 화면')).toBeTruthy());
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(tokenStorage.getRefreshToken()).toBeNull();
  });

  it('로그아웃이 401로 재발급을 거칠 때 그사이 다른 탭이 로그인했으면 B 토큰으로 두 번째 로그아웃을 보내지 않는다', async () => {
    tokenStorage.startSession(A1, 'RA');
    const gate = deferred();
    holdLogout(gate, 401); // Access Token 만료 — 로그아웃은 재발급 후 다시 보내야 한다
    await clickLogout();

    act(() => tokenStorage.startSession(B1, 'RB'));
    await act(async () => {
      gate.resolve();
    });

    await waitFor(() => expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy());
    // 재발급은 락 안에서 저장소가 이미 바뀐 것을 보고 요청 없이 끝나고, B 세션이라 두 번째 로그아웃을 보내지 않는다.
    expect(sent).not.toContain('/api/auth/refresh');
    expect(logoutBodies).toEqual(['RA']);
    expect(tokenStorage.getAccessToken()).toBe(B1);
  });
});
