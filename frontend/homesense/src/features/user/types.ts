/** UserResponse.java 실제 필드 그대로(role/status 등 내부 필드는 노출하지 않는다). */
export interface UserResponse {
  userId: number;
  email: string;
  nickname: string;
  createdAt: string;
}

/** UpdateUserRequest.java — 모두 선택. 이메일 필드는 없다(이메일 변경은 범위 밖, MY-05 D1). */
export interface UpdateUserRequest {
  nickname?: string;
  currentPassword?: string;
  newPassword?: string;
}
