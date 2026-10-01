import { act, cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthContext, type AuthContextValue, type AuthStatus } from '../features/auth/authContext';
import { RequireAuth } from './RequireAuth';

function authValue(status: AuthStatus, signedOutByUser = false): AuthContextValue {
  return {
    status,
    user: null,
    login: async () => {},
    signup: async () => {},
    logout: async () => {},
    endSession: () => {},
    signedOutByUser,
  };
}

function LoginProbe() {
  const location = useLocation();
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '';
  return <p>로그인 화면(from={from})</p>;
}

function Tree() {
  return (
    <MemoryRouter initialEntries={['/favorites']}>
      <Routes>
        <Route
          path="/favorites"
          element={
            <RequireAuth>
              <p>관심목록 화면</p>
            </RequireAuth>
          }
        />
        <Route path="/login" element={<LoginProbe />} />
        <Route path="/" element={<p>홈 화면</p>} />
      </Routes>
    </MemoryRouter>
  );
}

function renderAt(status: AuthStatus, signedOutByUser = false) {
  return render(
    <AuthContext.Provider value={authValue(status, signedOutByUser)}>
      <Tree />
    </AuthContext.Provider>,
  );
}

/** 로그인 상태로 화면을 연 뒤 상태를 바꿀 수 있게 한다. rerender는 같은 트리를 유지해 가드의 상태가 이어진다. */
function renderSwitchable() {
  const { rerender } = render(
    <AuthContext.Provider value={authValue('authenticated')}>
      <Tree />
    </AuthContext.Provider>,
  );
  return (value: AuthContextValue) =>
    act(() =>
      rerender(
        <AuthContext.Provider value={value}>
          <Tree />
        </AuthContext.Provider>,
      ),
    );
}

afterEach(cleanup);

describe('RequireAuth', () => {
  it('확인 중(checking)에는 리다이렉트하지 않고 기다린다', () => {
    renderAt('checking');
    expect(screen.getByRole('status', { name: '로그인 상태 확인 중' })).toBeTruthy();
    expect(screen.queryByText('관심목록 화면')).toBeNull();
    expect(screen.queryByText(/로그인 화면/)).toBeNull();
  });

  it('로그인 확인(authenticated)이면 화면을 그린다', () => {
    renderAt('authenticated');
    expect(screen.getByText('관심목록 화면')).toBeTruthy();
  });

  it('비로그인(anonymous)이면 로그인 화면으로 보내고 원래 위치를 state.from에 남긴다', () => {
    renderAt('anonymous');
    expect(screen.getByText('로그인 화면(from=/favorites)')).toBeTruthy();
    expect(screen.queryByText('관심목록 화면')).toBeNull();
  });

  it('화면을 보던 중 사용자가 직접 로그아웃하면 로그인 화면이 아니라 홈으로 보낸다', () => {
    const update = renderSwitchable();
    expect(screen.getByText('관심목록 화면')).toBeTruthy();
    update(authValue('anonymous', true));
    expect(screen.getByText('홈 화면')).toBeTruthy();
    expect(screen.queryByText(/로그인 화면/)).toBeNull();
  });

  it('화면을 보던 중 세션이 끝나면(사용자 동작 아님) 로그인 화면으로 보낸다', () => {
    const update = renderSwitchable();
    update(authValue('anonymous', false));
    expect(screen.getByText('로그인 화면(from=/favorites)')).toBeTruthy();
  });

  it('예전에 로그아웃한 상태로 들어오면 로그인 화면으로 보낸다(로그인 상태를 본 적 없는 가드)', () => {
    renderAt('anonymous', true);
    expect(screen.getByText('로그인 화면(from=/favorites)')).toBeTruthy();
  });
});
