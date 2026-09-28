import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AuthContext } from './authContext';
import { login as loginRequest, signup as signupRequest } from './api';
import { restoreSession } from './session';
import { getMe } from '../user/api';
import type { UserResponse } from '../user/types';
import { tokenStorage } from '../../lib/tokenStorage';
import type { LoginRequest, SignupRequest } from './types';

type AuthStatus = 'checking' | 'authenticated' | 'anonymous';

export function AuthProvider({ children }: { children: ReactNode }) {
  // 토큰이 localStorage에 있다는 것만으로 로그인 상태로 보지 않는다 — 예전엔 그렇게 해서, 만료되거나
  // 폐기된 토큰만 남은 비로그인 사용자가 GNB에서 로그인된 것처럼(빈 아바타) 보였다. 토큰이 있으면
  // 'checking'으로 시작해 서버 확인(restoreSession) 결과로 확정한다.
  const [status, setStatus] = useState<AuthStatus>(() =>
    tokenStorage.getAccessToken() !== null ? 'checking' : 'anonymous',
  );
  const [user, setUser] = useState<UserResponse | null>(null);

  useEffect(() => {
    if (status !== 'checking') {
      return;
    }
    let cancelled = false;
    void restoreSession().then((result) => {
      if (cancelled) return;
      if (result.kind === 'authenticated') {
        setUser(result.user);
        setStatus('authenticated');
      } else {
        setUser(null);
        setStatus('anonymous');
      }
    });
    return () => {
      cancelled = true;
    };
  }, [status]);

  // LoginResponse엔 nickname이 없어(토큰 3필드뿐) 로그인 직후 GET /api/users/me로 채운다. 조회가
  // 실패해도 로그인 자체는 성공이라 인증 상태는 유지한다(GNB는 닉네임 없이 기본값으로 그린다).
  const login = useCallback(async (payload: LoginRequest) => {
    const result = await loginRequest(payload);
    tokenStorage.setTokens(result.accessToken, result.refreshToken);
    setStatus('authenticated');
    try {
      setUser(await getMe());
    } catch {
      setUser(null);
    }
  }, []);

  // SVC-AUTH-01.signup()이 가입+자동 로그인을 한 번에 처리하고 응답에 nickname까지 평탄하게
  // 포함하므로, login()과 달리 별도 getMe() 호출 없이 그 자리에서 바로 user를 채운다.
  const signup = useCallback(async (payload: SignupRequest) => {
    const result = await signupRequest(payload);
    tokenStorage.setTokens(result.accessToken, result.refreshToken);
    setUser({ userId: result.userId, email: result.email, nickname: result.nickname, createdAt: '' });
    setStatus('authenticated');
  }, []);

  const logout = useCallback(() => {
    tokenStorage.clearTokens();
    setStatus('anonymous');
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({
      isAuthenticated: status === 'authenticated',
      authChecking: status === 'checking',
      user,
      login,
      signup,
      logout,
    }),
    [status, user, login, signup, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
