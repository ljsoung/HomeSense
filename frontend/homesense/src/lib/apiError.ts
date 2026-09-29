import axios from 'axios';
import type { ApiResponse } from '../types/api';

/**
 * 서버가 error.message를 주지 못한 실패(timeout, 네트워크 오류, 응답 본문이 없는 5xx 등)에 보여 줄 클라이언트
 * 대체 문구. 화면마다 따로 두지 않고 이 상수 하나를 쓴다 — 서버 문구가 있으면 그것을 그대로 쓴다는 규칙
 * (AUTH-01부터)은 그대로이고, 이 문구는 서버 문구가 없을 때만 쓰인다.
 */
export const GENERIC_ERROR_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';

/**
 * 요청 인터셉터가 요청을 보내지 않고 거절했다 — 공유 저장소의 토큰이 이 탭이 보여 주는 계정과 다른 계정이다
 * (다른 탭에서 다른 계정으로 로그인). 보냈다면 이 탭 화면에서 시작한 요청이 다른 계정으로 실행됐다. 거절과
 * 함께 이 탭은 로그인 상태를 다시 확인한다(features/auth/session.ts `beginRecheck`).
 */
export class SessionAccountChangedError extends Error {
  constructor() {
    super('다른 창에서 로그인 계정이 바뀌어 요청을 보내지 않았습니다. 로그인 상태를 다시 확인합니다.');
    this.name = 'SessionAccountChangedError';
  }
}

/**
 * 요청 인터셉터가 요청을 보내지 않고 거절했다 — 이 탭이 아직 로그인 계정을 확정하지 않았는데(확인 중, 로그인·
 * 로그아웃·다시 확인 직후) 저장소 토큰을 실어 사용자 동작 요청(POST·PUT·PATCH·DELETE)을 보내려 했다. 확정
 * 전에는 그 토큰이 이 탭 화면의 계정인지 알 수 없다. 확정되면 다시 시도할 수 있다.
 */
export class SessionNotConfirmedError extends Error {
  constructor() {
    super('로그인 상태를 확인하는 중이라 요청을 보내지 않았습니다. 잠시 후 다시 시도해주세요.');
    this.name = 'SessionNotConfirmedError';
  }
}

/** 서버가 내려 준 error.message. 응답이 없거나(timeout·네트워크 오류) 본문에 없으면 undefined. */
export function getServerErrorMessage(error: unknown): string | undefined {
  if (!axios.isAxiosError(error)) return undefined;
  const data = error.response?.data as ApiResponse<unknown> | undefined;
  return data?.error?.message ?? undefined;
}

/** 화면에 보여 줄 오류 문구 — 서버 문구가 있으면 그것을, 없으면 대체 문구를 돌려준다. */
export function getErrorMessage(error: unknown): string {
  // 요청 인터셉터가 보내지 않고 거절한 요청 — 서버 응답이 없어 클라이언트 문구를 그대로 쓴다.
  if (error instanceof SessionAccountChangedError || error instanceof SessionNotConfirmedError) return error.message;
  return getServerErrorMessage(error) ?? GENERIC_ERROR_MESSAGE;
}
