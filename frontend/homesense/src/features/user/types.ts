/** UserResponse.java 실제 필드 그대로(role/status 등 내부 필드는 노출하지 않는다). */
export interface UserResponse {
  userId: number;
  email: string;
  nickname: string;
  createdAt: string;
}
