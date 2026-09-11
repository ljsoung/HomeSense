export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface SignupRequest {
  email: string;
  password: string;
  nickname: string;
}

/**
 * SignupResponse.java 실제 필드 그대로 — 토큰과 회원 요약(userId/email/nickname)이 중첩된
 * `user` 객체 없이 평탄하게 내려온다.
 */
export interface SignupResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userId: number;
  email: string;
  nickname: string;
}

/** EmailCheckResponse.java 실제 필드명 — `available`이 아니라 `duplicate`다. */
export interface EmailCheckResponse {
  duplicate: boolean;
}
