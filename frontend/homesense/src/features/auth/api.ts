import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type {
  EmailCheckResponse,
  LoginRequest,
  LoginResponse,
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  SignupRequest,
  SignupResponse,
} from './types';

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

/**
 * AUTH-03 1단계 — 계정 존재 여부와 무관하게 항상 같은 성공(2xx)으로 응답한다(AuthController.java
 * javadoc). 예외는 재전송 쿨다운(429, PASSWORD_RESET_COOLDOWN)뿐이므로 호출부는 성공/쿨다운/기타
 * 오류 세 갈래만 분기하면 된다 — "이메일이 존재하지 않습니다" 같은 분기는 서버가 애초에 만들지 않는다.
 */
export async function requestPasswordReset(payload: PasswordResetRequest): Promise<void> {
  await httpClient.post<ApiResponse<null>>('/api/auth/password-reset-request', payload);
}

/**
 * AUTH-03 2단계 진입 시 사전 검증(peek) — 토큰을 소비하지 않는다. 만료·미존재·이미 사용됨·계정
 * 비활성 전부 400 INVALID_RESET_TOKEN 하나로 통일돼 있어(InvalidResetTokenException.java) 프론트도
 * 사유를 구분하지 않고 "링크가 만료되었습니다" 한 가지 화면으로만 응답한다.
 */
export async function validatePasswordResetToken(token: string): Promise<void> {
  await httpClient.get<ApiResponse<null>>('/api/auth/password-reset/validate-token', { params: { token } });
}

/** AUTH-03 2단계 제출 — 토큰을 1회 소비하고 비밀번호를 변경한다. 성공 시 서버가 이 사용자의 기존 세션(Refresh Token)을 전부 폐기한다. */
export async function resetPassword(payload: PasswordResetConfirmRequest): Promise<void> {
  await httpClient.post<ApiResponse<null>>('/api/auth/password-reset', payload);
}
