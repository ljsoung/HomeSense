import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { LoginRequest, LoginResponse } from './types';

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const { data } = await httpClient.post<ApiResponse<LoginResponse>>('/api/auth/login', payload);
  // 성공 응답은 HTTP 2xx로만 오므로 이 시점의 data는 항상 success:true다.
  return (data as Extract<ApiResponse<LoginResponse>, { success: true }>).data;
}
