import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { AuthContext } from './authContext';
import { login as loginRequest } from './api';
import { tokenStorage } from '../../lib/tokenStorage';
import type { LoginRequest } from './types';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(() => tokenStorage.getAccessToken() !== null);

  const login = useCallback(async (payload: LoginRequest) => {
    const result = await loginRequest(payload);
    tokenStorage.setTokens(result.accessToken, result.refreshToken);
    setIsAuthenticated(true);
  }, []);

  const logout = useCallback(() => {
    tokenStorage.clearTokens();
    setIsAuthenticated(false);
  }, []);

  const value = useMemo(() => ({ isAuthenticated, login, logout }), [isAuthenticated, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
