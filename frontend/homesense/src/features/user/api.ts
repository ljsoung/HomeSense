import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { UserResponse } from './types';

export async function getMe(): Promise<UserResponse> {
  const { data } = await httpClient.get<ApiResponse<UserResponse>>('/api/users/me');
  return (data as Extract<ApiResponse<UserResponse>, { success: true }>).data;
}
