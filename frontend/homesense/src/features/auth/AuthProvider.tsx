import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { AuthContext, type AuthStatus } from './authContext';
import { login as loginRequest, signup as signupRequest } from './api';
import {
  advanceSessionGeneration,
  beginRecheck,
  currentSessionGeneration,
  markTabAuthenticated,
  markTabUnauthenticated,
  restoreSession,
  revokeSessionWithinDeadline,
  setRecheckListener,
  setSessionExpiredListener,
  storeTokens,
  syncWithStoredAccount,
  type RecheckStart,
} from './session';
import { getMe } from '../user/api';
import type { UserResponse } from '../user/types';
import { ACCESS_TOKEN_KEY, tokenStorage } from '../../lib/tokenStorage';
import type { LoginRequest, SignupRequest } from './types';

export function AuthProvider({ children }: { children: ReactNode }) {
  // 토큰이 localStorage에 있다는 것만으로 로그인 상태로 보지 않는다 — 예전엔 그렇게 해서, 만료되거나
  // 폐기된 토큰만 남은 비로그인 사용자가 GNB에서 로그인된 것처럼(빈 아바타) 보였다. 토큰이 있으면
  // 'checking'으로 시작해 서버 확인(restoreSession) 결과로 확정한다.
  const [status, setStatus] = useState<AuthStatus>(() => {
    markTabUnauthenticated();
    return tokenStorage.getAccessToken() !== null ? 'checking' : 'anonymous';
  });
  const [user, setUser] = useState<UserResponse | null>(null);
  const [signedOutByUser, setSignedOutByUser] = useState(false);
  // 다시 확인할 때마다 올린다 — 이미 'checking'인 상태에서 다시 확인이 시작돼도 복원 effect가 다시 돈다.
  const [checkRound, setCheckRound] = useState(0);
  // 진행 중인 로그아웃 수. 로그아웃은 서버 폐기를 최대 5초 기다린 뒤 비로그인으로 바뀌는데, 그사이 다른 경로가 먼저
  // 비로그인으로 바꿀 수 있다 — 로그아웃 시작 뒤 401을 받은 요청의 재발급이 실패하면 세션 만료 알림이 온다(세대를
  // 로그아웃이 이미 올린 뒤라 그 재발급은 새 세대 기준이다). 그때도 사용자가 직접 로그아웃한 것으로 기록해야
  // 보호 화면의 가드가 로그인 화면이 아니라 HOME-01로 보낸다(가드는 첫 비로그인 전환에서 이동하고 언마운트된다).
  const pendingLogouts = useRef(0);

  /**
   * 로그인 상태를 벗어나는 유일한 경로 — 로그아웃·탈퇴, 세션 만료(재발급 실패), 복원 실패, 다시 확인이 모두 여기를 거친다.
   *
   * 직접 로그아웃 기록(`signedOutByUser`)은 한 번 true가 되면 다음 로그인 상태 확정(로그인·가입·복원 성공) 전까지
   * 낮추지 않는다. 로그아웃 응답으로 로컬 로그아웃이 끝난 뒤(진행 중 로그아웃 0건) 같은 렌더 안에서 다른 경로가
   * 비로그인 전환을 한 번 더 하면, 낮췄을 때 가드가 마지막 값(false)만 보고 로그인 화면으로 보낸다(Codex P2 후속).
   *
   * `recheck`는 다시 확인(`beginRecheck`)에서 부를 때만 준다. 'checking'이면 저장소에 토큰이 있어 복원 경로로 다시
   * 확인하므로 비로그인 대신 확인 중으로 두고, 복원 effect가 다시 돌도록 `checkRound`를 올린다. 이 경로에서
   * `markTabUnauthenticated()`가 다시 실행되지만 끄지 않았다 — `beginRecheck`가 같은 동기 흐름에서 이미 불렀고
   * 저장소가 그사이 바뀌지 않아 결과가 같다.
   */
  const becomeAnonymous = useCallback((byUser: boolean, recheck?: RecheckStart) => {
    markTabUnauthenticated();
    setUser(null);
    const signedOut = byUser || pendingLogouts.current > 0;
    setSignedOutByUser((previous) => previous || signedOut);
    setStatus(recheck ?? 'anonymous');
    if (recheck) setCheckRound((round) => round + 1);
  }, []);

  // 사용 중 재발급(httpClient 401 인터셉터)이 세션 종료로 끝나면 비로그인으로 바꾼다. session.ts가 세대
  // (sessionGeneration)가 그대로일 때만 부른다 — 그사이 사용자가 로그인·로그아웃했으면 호출되지 않는다.
  // 화면 이동은 하지 않는다: 보호 라우트의 가드가 AUTH-01로 보내고, 공개 화면은 비로그인으로 계속 보인다.
  useEffect(() => {
    setSessionExpiredListener(() => becomeAnonymous(false));
    return () => setSessionExpiredListener(null);
  }, [becomeAnonymous]);

  // 다른 탭이 저장소 토큰을 바꾸면(다른 계정 로그인, 로그아웃) 이 탭의 로그인 상태를 다시 확인한다. 요청
  // 인터셉터가 다른 계정 토큰을 막았을 때도 같은 경로로 온다(session.ts `beginRecheck`). 세대는 이미 올라가
  // 있어, 이전 세션에서 진행 중이던 복원·재발급 결과는 상태를 바꾸지 못한다.
  // 이 탭의 로그아웃이 서버 폐기를 기다리는 동안 다른 탭이 토큰을 지우면 여기서 먼저 비로그인이 된다 — 그때도
  // 직접 로그아웃으로 기록한다(`becomeAnonymous`가 `pendingLogouts`를 본다, Codex P2).
  useEffect(() => {
    setRecheckListener((next) => becomeAnonymous(false, next));
    const onStorage = (event: StorageEvent) => {
      if (event.storageArea !== localStorage) return;
      if (event.key !== null && event.key !== ACCESS_TOKEN_KEY) return; // key가 null이면 clear()
      syncWithStoredAccount();
    };
    window.addEventListener('storage', onStorage);
    return () => {
      window.removeEventListener('storage', onStorage);
      setRecheckListener(null);
    };
  }, [becomeAnonymous]);

  useEffect(() => {
    if (status !== 'checking') {
      return;
    }
    let cancelled = false;
    // 세대(session.ts)는 로그인·가입 성공 직후, 로그아웃 시작 시, 다시 확인 시작 시 오른다. 끝났을 때 값이
    // 바뀌었으면 결과를 버린다 — 사용자가 직접 바꾼 세션을 늦게 끝난 복원 결과가 덮지 않게 한다.
    const generationAtStart = currentSessionGeneration();
    void restoreSession().then((result) => {
      if (cancelled || currentSessionGeneration() !== generationAtStart) return;
      if (result.kind !== 'authenticated') {
        becomeAnonymous(false);
        return;
      }
      // 복원 도중 다른 탭이 다른 계정으로 로그인했으면(저장소 계정 ≠ 확인한 사용자) 확정하지 않고 다시 확인한다.
      if (!markTabAuthenticated(result.user.userId)) {
        beginRecheck();
        return;
      }
      setUser(result.user);
      setSignedOutByUser(false); // 로그인 상태 확정 — 직접 로그아웃 기록을 내린다(`becomeAnonymous` 설명).
      setStatus('authenticated');
    });
    return () => {
      cancelled = true;
    };
  }, [status, checkRound, becomeAnonymous]);

  // LoginResponse엔 nickname이 없어(토큰 3필드뿐) 로그인 직후 GET /api/users/me로 채운다. 조회가
  // 실패해도 로그인 자체는 성공이라 인증 상태는 유지한다(GNB는 닉네임 없이 기본값으로 그린다).
  const login = useCallback(async (payload: LoginRequest) => {
    const result = await loginRequest(payload);
    const generation = advanceSessionGeneration();
    await storeTokens(result.accessToken, result.refreshToken);
    markTabAuthenticated(null);
    setSignedOutByUser(false);
    setStatus('authenticated');
    try {
      const me = await getMe();
      if (currentSessionGeneration() === generation) setUser(me);
    } catch {
      if (currentSessionGeneration() === generation) setUser(null);
    }
  }, []);

  // SVC-AUTH-01.signup()이 가입+자동 로그인을 한 번에 처리하고 응답에 nickname까지 평탄하게
  // 포함하므로, login()과 달리 별도 getMe() 호출 없이 그 자리에서 바로 user를 채운다.
  const signup = useCallback(async (payload: SignupRequest) => {
    const result = await signupRequest(payload);
    advanceSessionGeneration();
    await storeTokens(result.accessToken, result.refreshToken);
    markTabAuthenticated(result.userId);
    setSignedOutByUser(false);
    setUser({ userId: result.userId, email: result.email, nickname: result.nickname, createdAt: '' });
    setStatus('authenticated');
  }, []);

  // 서버 폐기(최선 노력, 상한 5초) 후 로컬 토큰을 지운다 — 서버가 응답하지 않거나 요청이 멈춰도 상한 뒤
  // finally에서 반드시 로컬 로그아웃을 끝낸다(예전엔 멈춘 요청이 로그아웃을 영원히 붙잡았다, Codex P2).
  // 로그아웃 의도는 서버 폐기를 기다리기 전에 기록한다(`pendingLogouts`, 위 설명).
  const logout = useCallback(async () => {
    pendingLogouts.current += 1;
    advanceSessionGeneration();
    try {
      await revokeSessionWithinDeadline();
    } finally {
      tokenStorage.clearTokens();
      becomeAnonymous(true);
      pendingLogouts.current -= 1;
    }
  }, [becomeAnonymous]);

  // 회원탈퇴 성공 직후 — 서버가 이 사용자의 Refresh Token을 전부 폐기했고(UserService.withdraw) 상태 캐시도
  // WITHDRAWN이라 기존 Access Token도 거부된다. 그래서 /logout을 부르지 않고 logout()의 로컬 정리 단계만 한다.
  // 세대를 먼저 올려, 진행 중이던 복원·재발급 결과가 정리된 세션을 되살리지 않게 한다.
  const endSession = useCallback(() => {
    advanceSessionGeneration();
    tokenStorage.clearTokens();
    becomeAnonymous(true);
  }, [becomeAnonymous]);

  const value = useMemo(
    () => ({
      status,
      user,
      login,
      signup,
      logout,
      endSession,
      signedOutByUser,
    }),
    [status, user, login, signup, logout, endSession, signedOutByUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
