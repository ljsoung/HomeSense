import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { UserResponse } from './types';

export async function getMe(): Promise<UserResponse> {
  const { data } = await httpClient.get<ApiResponse<UserResponse>>('/api/users/me');
  return (data as Extract<ApiResponse<UserResponse>, { success: true }>).data;
}

/**
 * MY-01 회원 탈퇴(DELETE /api/users/me). WithdrawRequest.java는 password(필수, 재확인)와 reason(선택)을 받지만
 * reason은 저장되지 않고 버려져(toCommand()가 넘기지 않음) 보내지 않는다 — 쓰지 않는 데이터를 모으지 않는다.
 * 비밀번호가 틀리면 401 INVALID_CREDENTIALS(비즈니스 401이라 인터셉터가 재발급하지 않는다)로 실패한다.
 */
export async function withdraw(password: string): Promise<void> {
  await httpClient.delete<ApiResponse<void>>('/api/users/me', { data: { password } });
}
