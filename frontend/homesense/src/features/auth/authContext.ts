import { createContext } from 'react';
import type { UserResponse } from '../user/types';
import type { LoginRequest, SignupRequest } from './types';

/**
 * 인증 상태. boolean(`isAuthenticated`)은 "확인 중"과 "비로그인"을 둘 다 false로 표현해, 확인 중인 로그인
 * 사용자를 비로그인으로 단정하는 결함(하트 클릭이 로그인 사용자를 로그인 화면으로 보냄, Codex P2)의 근본
 * 원인이었다. 소비자는 이 값을 `switch`로 분기하고 default에서 `assertNever`를 불러, 상태가 늘면 컴파일이
 * 실패하게 한다. boolean으로 줄이는 호환용 getter는 두지 않는다 — 두면 같은 오판이 그대로 남는다.
 * - `checking`: 저장소에 토큰이 있고 서버 확인(getMe, 필요하면 재발급 1회)이 끝나지 않았다. 로그인·비로그인
 *   어느 쪽으로도 확정하지 않는다(헤더는 자리 표시, 보호 라우트는 대기, 개인화 API는 호출하지 않음).
 * - `authenticated`: 서버가 세션을 확인했다.
 * - `anonymous`: 토큰이 없거나 서버가 거부했다.
 * 모든 전이는 AuthProvider 안에서만 일어나고, 늦게 끝난 복원·재발급 결과는 세대(session.ts
 * `sessionGeneration`) 확인을 거친다.
 */
export type AuthStatus = 'checking' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  status: AuthStatus;
  /**
   * 로그인 직후 GET /api/users/me가 실패하면 `authenticated`여도 null일 수 있다 — GNB 아바타처럼 user가
   * 필요한 UI는 null일 때 기본값으로 대체해야 한다.
   */
  user: UserResponse | null;
  login: (payload: LoginRequest) => Promise<void>;
  signup: (payload: SignupRequest) => Promise<void>;
  /** 서버의 Refresh Token 폐기를 시도한 뒤(실패해도) 로컬 토큰을 지우고 비로그인으로 바꾼다. */
  logout: () => Promise<void>;
  /**
   * 서버 호출 없이 이 브라우저의 세션만 정리한다 — 회원탈퇴·비밀번호 변경 성공 직후처럼 서버가 이미 이 사용자의 토큰을
   * 전부 폐기한 경우에 쓴다(`/logout`을 다시 부르지 않는다). logout()의 로컬 정리와 같은 단계다.
   * `redirectTo`는 보호 화면의 가드가 보낼 곳이다(기본 HOME-01, `signedOutRedirectTo`). 화면은 직접 이동하지 않는다.
   */
  endSession: (options?: { redirectTo?: string }) => void;
  /**
   * 서버가 돌려준 내 정보로 `user`를 바꾼다(MY-05 닉네임 저장 뒤 헤더 갱신). 로그인 상태가 아니거나 다른 회원의 응답이면
   * 무시한다.
   */
  applyUserUpdate: (next: UserResponse) => void;
  /**
   * 지금의 비로그인 상태가 사용자가 직접 끝낸 결과(로그아웃·탈퇴)인지. 보호 라우트 가드가 그 화면에서 로그아웃한
   * 경우를 세션 만료와 구분해, 로그인 화면이 아니라 HOME-01로 보내는 데 쓴다(RequireAuth). 로그인·가입·다시
   * 확인·세션 만료 때 false로 돌아간다.
   */
  signedOutByUser: boolean;
  /**
   * `signedOutByUser`일 때 보호 라우트 가드가 보낼 곳. 로그아웃·탈퇴는 HOME-01('/'), 비밀번호 변경(MY-05)은 로그인 화면
   * ('/login', 복귀 목적지 없음 — 로그인 뒤 HOME-01로 간다)이다.
   */
  signedOutRedirectTo: string;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
