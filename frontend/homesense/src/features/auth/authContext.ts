import { createContext } from 'react';
import type { UserResponse } from '../user/types';
import type { LoginRequest, SignupRequest } from './types';

export interface AuthContextValue {
  isAuthenticated: boolean;
  /**
   * 로그인 완료 직후 또는 새로고침 후 GET /api/users/me가 아직 응답하기 전에는 null이다 — GNB
   * 아바타(닉네임 이니셜)처럼 user가 필요한 UI는 null일 때 스켈레톤/기본값으로 대체해야 한다.
   */
  user: UserResponse | null;
  login: (payload: LoginRequest) => Promise<void>;
  signup: (payload: SignupRequest) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
