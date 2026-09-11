import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { AuthContext } from './authContext';
import { login as loginRequest, signup as signupRequest } from './api';
import { tokenStorage } from '../../lib/tokenStorage';
import type { LoginRequest, SignupRequest } from './types';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(() => tokenStorage.getAccessToken() !== null);

  const login = useCallback(async (payload: LoginRequest) => {
    const result = await loginRequest(payload);
    tokenStorage.setTokens(result.accessToken, result.refreshToken);
    setIsAuthenticated(true);
  }, []);

  // SVC-AUTH-01.signup()이 가입+자동 로그인을 한 번에 처리하므로 로그인과 동일하게 토큰만 저장한다.
  const signup = useCallback(async (payload: SignupRequest) => {
    const result = await signupRequest(payload);
    tokenStorage.setTokens(result.accessToken, result.refreshToken);
    setIsAuthenticated(true);
  }, []);

  const logout = useCallback(() => {
    tokenStorage.clearTokens();
    setIsAuthenticated(false);
  }, []);

  const value = useMemo(
    () => ({ isAuthenticated, login, signup, logout }),
    [isAuthenticated, login, signup, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
