import { createContext } from 'react';
import type { UserResponse } from '../user/types';
import type { LoginRequest, SignupRequest } from './types';

export interface AuthContextValue {
  /** 서버가 세션을 확인한 경우에만 true — localStorage에 토큰이 있다는 것만으로는 true가 아니다. */
  isAuthenticated: boolean;
  /**
   * 새로고침 후 저장된 토큰을 서버로 확인(getMe, 필요하면 refresh 1회)하는 동안 true. 이 동안은
   * 로그인/비로그인 어느 쪽 UI로도 확정하지 말고 비워 두거나 로딩으로 보여야 한다.
   */
  authChecking: boolean;
  /**
   * 로그인 직후 GET /api/users/me가 실패하면 인증 상태여도 null일 수 있다 — GNB 아바타처럼 user가
   * 필요한 UI는 null일 때 기본값으로 대체해야 한다.
   */
  user: UserResponse | null;
  login: (payload: LoginRequest) => Promise<void>;
  signup: (payload: SignupRequest) => Promise<void>;
  /** 서버의 Refresh Token 폐기를 시도한 뒤(실패해도) 로컬 토큰을 지우고 비로그인으로 바꾼다. */
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
