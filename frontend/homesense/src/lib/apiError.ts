import axios from 'axios';
import type { ApiResponse } from '../types/api';

/**
 * 서버가 error.message를 주지 못한 실패(timeout, 네트워크 오류, 응답 본문이 없는 5xx 등)에 보여 줄 클라이언트
 * 대체 문구. 화면마다 따로 두지 않고 이 상수 하나를 쓴다 — 서버 문구가 있으면 그것을 그대로 쓴다는 규칙
 * (AUTH-01부터)은 그대로이고, 이 문구는 서버 문구가 없을 때만 쓰인다.
 */
export const GENERIC_ERROR_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';

/** 서버가 내려 준 error.message. 응답이 없거나(timeout·네트워크 오류) 본문에 없으면 undefined. */
export function getServerErrorMessage(error: unknown): string | undefined {
  if (!axios.isAxiosError(error)) return undefined;
  const data = error.response?.data as ApiResponse<unknown> | undefined;
  return data?.error?.message ?? undefined;
}

/** 화면에 보여 줄 오류 문구 — 서버 문구가 있으면 그것을, 없으면 대체 문구를 돌려준다. */
export function getErrorMessage(error: unknown): string {
  return getServerErrorMessage(error) ?? GENERIC_ERROR_MESSAGE;
}
