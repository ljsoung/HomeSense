import { z } from 'zod';

/**
 * AUTH-01 클라이언트 검증 범위: "필수 입력 여부"만 확인해 버튼 활성/비활성을 제어한다
 * (AUTH-02의 비밀번호 정책 실시간 힌트는 이 화면 범위 밖).
 */
export const loginSchema = z.object({
  email: z.string().min(1, '이메일을 입력해주세요.'),
  password: z.string().min(1, '비밀번호를 입력해주세요.'),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
