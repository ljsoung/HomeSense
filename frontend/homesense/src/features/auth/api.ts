import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { EmailCheckResponse, LoginRequest, LoginResponse, SignupRequest, SignupResponse } from './types';

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const { data } = await httpClient.post<ApiResponse<LoginResponse>>('/api/auth/login', payload);
  // 성공 응답은 HTTP 2xx로만 오므로 이 시점의 data는 항상 success:true다.
  return (data as Extract<ApiResponse<LoginResponse>, { success: true }>).data;
}

export async function signup(payload: SignupRequest): Promise<SignupResponse> {
  const { data } = await httpClient.post<ApiResponse<SignupResponse>>('/api/auth/signup', payload);
  return (data as Extract<ApiResponse<SignupResponse>, { success: true }>).data;
}

export async function checkEmail(email: string): Promise<EmailCheckResponse> {
  const { data } = await httpClient.get<ApiResponse<EmailCheckResponse>>('/api/auth/check-email', {
    params: { email },
  });
  return (data as Extract<ApiResponse<EmailCheckResponse>, { success: true }>).data;
}
