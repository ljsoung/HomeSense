import { createContext } from 'react';
import type { LoginRequest } from './types';

export interface AuthContextValue {
  isAuthenticated: boolean;
  login: (payload: LoginRequest) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
