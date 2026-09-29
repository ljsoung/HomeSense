import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AuthContext, type AuthStatus } from './authContext';
import { login as loginRequest, signup as signupRequest } from './api';
import {
  advanceSessionGeneration,
  currentSessionGeneration,
  restoreSession,
  revokeSessionWithinDeadline,
  setSessionExpiredListener,
  storeTokens,
} from './session';
import { getMe } from '../user/api';
import type { UserResponse } from '../user/types';
import { tokenStorage } from '../../lib/tokenStorage';
import type { LoginRequest, SignupRequest } from './types';

export function AuthProvider({ children }: { children: ReactNode }) {
  // 토큰이 localStorage에 있다는 것만으로 로그인 상태로 보지 않는다 — 예전엔 그렇게 해서, 만료되거나
  // 폐기된 토큰만 남은 비로그인 사용자가 GNB에서 로그인된 것처럼(빈 아바타) 보였다. 토큰이 있으면
  // 'checking'으로 시작해 서버 확인(restoreSession) 결과로 확정한다.
  const [status, setStatus] = useState<AuthStatus>(() =>
    tokenStorage.getAccessToken() !== null ? 'checking' : 'anonymous',
  );
  const [user, setUser] = useState<UserResponse | null>(null);

  // 사용 중 재발급(httpClient 401 인터셉터)이 세션 종료로 끝나면 비로그인으로 바꾼다. session.ts가 세대
  // (sessionGeneration)가 그대로일 때만 부른다 — 그사이 사용자가 로그인·로그아웃했으면 호출되지 않는다.
  // 화면 이동은 하지 않는다: 보호 라우트의 가드가 AUTH-01로 보내고, 공개 화면은 비로그인으로 계속 보인다.
  useEffect(() => {
    setSessionExpiredListener(() => {
      setUser(null);
      setStatus('anonymous');
    });
    return () => setSessionExpiredListener(null);
  }, []);

  useEffect(() => {
    if (status !== 'checking') {
      return;
    }
    let cancelled = false;
    // 세대(session.ts)는 로그인·가입 성공 직후와 로그아웃 시작 시 오른다. 끝났을 때 값이 바뀌었으면 결과를
    // 버린다 — 사용자가 직접 바꾼 세션을 늦게 끝난 복원 결과가 덮지 않게 한다.
    const generationAtStart = currentSessionGeneration();
    void restoreSession().then((result) => {
      if (cancelled || currentSessionGeneration() !== generationAtStart) return;
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
    advanceSessionGeneration();
    await storeTokens(result.accessToken, result.refreshToken);
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
    advanceSessionGeneration();
    await storeTokens(result.accessToken, result.refreshToken);
    setUser({ userId: result.userId, email: result.email, nickname: result.nickname, createdAt: '' });
    setStatus('authenticated');
  }, []);

  // 서버 폐기(최선 노력, 상한 5초) 후 로컬 토큰을 지운다 — 서버가 응답하지 않거나 요청이 멈춰도 상한 뒤
  // finally에서 반드시 로컬 로그아웃을 끝낸다(예전엔 멈춘 요청이 로그아웃을 영원히 붙잡았다, Codex P2).
  const logout = useCallback(async () => {
    advanceSessionGeneration();
    try {
      await revokeSessionWithinDeadline();
    } finally {
      tokenStorage.clearTokens();
      setStatus('anonymous');
      setUser(null);
    }
  }, []);

  const value = useMemo(
    () => ({
      status,
      user,
      login,
      signup,
      logout,
    }),
    [status, user, login, signup, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
