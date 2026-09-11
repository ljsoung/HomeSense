import { createContext } from 'react';
import type { LoginRequest, SignupRequest } from './types';

export interface AuthContextValue {
  isAuthenticated: boolean;
  login: (payload: LoginRequest) => Promise<void>;
  signup: (payload: SignupRequest) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
