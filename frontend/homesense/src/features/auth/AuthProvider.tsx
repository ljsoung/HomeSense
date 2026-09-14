import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AuthContext } from './authContext';
import { login as loginRequest, signup as signupRequest } from './api';
import { getMe } from '../user/api';
import type { UserResponse } from '../user/types';
import { tokenStorage } from '../../lib/tokenStorage';
import type { LoginRequest, SignupRequest } from './types';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(() => tokenStorage.getAccessToken() !== null);
  const [user, setUser] = useState<UserResponse | null>(null);

  // 새로고침 시 토큰은 localStorage에 남아있지만 user는 메모리 상태라 유실된다 — LoginResponse엔
  // nickname이 없어(SignupResponse와 달리 토큰 3필드뿐, CLAUDE.md 참고 불필요할 만큼 백엔드 소스로
  // 직접 확인함) 마운트 시점에 GET /api/users/me로 다시 채운다. 실패해도(예: accessToken 만료)
  // 로그인 여부 자체는 바꾸지 않는다 — 401 인터셉터가 없는 지금은 그대로 두고, 이후 화면이 실제로
  // accessToken 갱신 흐름을 추가할 때 이 자리도 함께 재검토한다.
  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    let cancelled = false;
    getMe()
      .then((me) => {
        if (!cancelled) {
          setUser(me);
        }
      })
      .catch(() => {
        // 조회 실패는 GNB가 스켈레톤/기본값으로 대체하므로 조용히 무시한다.
      });
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  const login = useCallback(async (payload: LoginRequest) => {
    const result = await loginRequest(payload);
    tokenStorage.setTokens(result.accessToken, result.refreshToken);
    setIsAuthenticated(true);
  }, []);

  // SVC-AUTH-01.signup()이 가입+자동 로그인을 한 번에 처리하고 응답에 nickname까지 평탄하게
  // 포함하므로, login()과 달리 별도 getMe() 호출 없이 그 자리에서 바로 user를 채운다.
  const signup = useCallback(async (payload: SignupRequest) => {
    const result = await signupRequest(payload);
    tokenStorage.setTokens(result.accessToken, result.refreshToken);
    setUser({ userId: result.userId, email: result.email, nickname: result.nickname, createdAt: '' });
    setIsAuthenticated(true);
  }, []);

  const logout = useCallback(() => {
    tokenStorage.clearTokens();
    setIsAuthenticated(false);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ isAuthenticated, user, login, signup, logout }),
    [isAuthenticated, user, login, signup, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
