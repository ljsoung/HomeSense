import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthContext, type AuthContextValue, type AuthStatus } from '../features/auth/authContext';
import { RequireAuth } from './RequireAuth';

function authValue(status: AuthStatus): AuthContextValue {
  return { status, user: null, login: async () => {}, signup: async () => {}, logout: async () => {} };
}

function LoginProbe() {
  const location = useLocation();
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '';
  return <p>로그인 화면(from={from})</p>;
}

function renderAt(status: AuthStatus) {
  return render(
    <AuthContext.Provider value={authValue(status)}>
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
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
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
});
