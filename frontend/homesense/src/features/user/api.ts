import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { UpdateUserRequest, UserResponse } from './types';

export async function getMe(): Promise<UserResponse> {
  const { data } = await httpClient.get<ApiResponse<UserResponse>>('/api/users/me');
  return (data as Extract<ApiResponse<UserResponse>, { success: true }>).data;
}

/**
 * MY-05 회원정보 수정(PUT /api/users/me). 바뀐 항목만 보낸다 — 닉네임이 바뀌었으면 nickname, 비밀번호를 바꾸면
 * currentPassword·newPassword. 현재 비밀번호가 틀리면 400 INVALID_CURRENT_PASSWORD, 확인 실패가 쌓이면 429
 * PASSWORD_CONFIRM_LOCKED(탈퇴와 공용). 비밀번호를 바꾸면 서버가 이 회원의 모든 세션을 끝낸다(이 기기도 재로그인).
 */
export async function updateMe(body: UpdateUserRequest): Promise<UserResponse> {
  const { data } = await httpClient.put<ApiResponse<UserResponse>>('/api/users/me', body);
  return (data as Extract<ApiResponse<UserResponse>, { success: true }>).data;
}

/**
 * MY-01 회원 탈퇴(DELETE /api/users/me). WithdrawRequest.java는 password(필수, 재확인)와 reason(선택)을 받지만
 * reason은 저장되지 않고 버려져(toCommand()가 넘기지 않음) 보내지 않는다 — 쓰지 않는 데이터를 모으지 않는다.
 * 비밀번호가 틀리면 400 INVALID_CURRENT_PASSWORD, 비밀번호 확인 실패(회원정보 수정과 합산)가 쌓이면 429
 * PASSWORD_CONFIRM_LOCKED로 실패한다. 다이얼로그는 코드로 분기하지 않고 서버 문구를 그대로 보인다.
 */
export async function withdraw(password: string): Promise<void> {
  await httpClient.delete<ApiResponse<void>>('/api/users/me', { data: { password } });
}
