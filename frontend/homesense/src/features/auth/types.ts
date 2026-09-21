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
  /**
   * "만 14세 이상입니다" 자기 확인 체크박스 값. 서버가 @NotNull + @AssertTrue로 검증하고 저장 없이
   * 폐기한다(SignupRequest.java) — 항상 체크박스 상태 그대로 보내며 true로 하드코딩하지 않는다.
   */
  ageConfirmed: boolean;
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
