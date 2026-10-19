import { evaluatePassword, isValidNickname, type PasswordPolicyResult } from '../../features/auth/validation';
import type { UpdateUserRequest } from '../../features/user/types';
import type { ApiError } from '../../types/api';

/** MY-05 입력 상태. 비밀번호 세 필드는 섹션을 접으면 비운다(D6). */
export interface ProfileForm {
  nickname: string;
  passwordOpen: boolean;
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export const EMPTY_PASSWORD_FIELDS = { currentPassword: '', newPassword: '', confirmPassword: '' } as const;

export function initialForm(nickname: string): ProfileForm {
  return { nickname, passwordOpen: false, ...EMPTY_PASSWORD_FIELDS };
}

export function isNicknameChanged(baselineNickname: string, form: ProfileForm): boolean {
  return form.nickname.trim() !== baselineNickname;
}

/** 비밀번호 섹션이 열려 있고 세 필드 중 하나라도 입력됐다. 접힌 상태에서는 비밀번호 변경을 요청하지 않는다(D6). */
export function isPasswordEntered(form: ProfileForm): boolean {
  return form.passwordOpen && (form.currentPassword !== '' || form.newPassword !== '' || form.confirmPassword !== '');
}

/** 저장하지 않은 변경이 있다 — 저장 버튼 활성(D8)과 이탈 방지(D7)가 같은 정의를 쓴다. */
export function isDirty(baselineNickname: string, form: ProfileForm): boolean {
  return isNicknameChanged(baselineNickname, form) || isPasswordEntered(form);
}

export interface SaveState {
  dirty: boolean;
  /** 닉네임이 바뀌었는데 규칙(AUTH-02와 같은 isValidNickname)을 어긴다. */
  nicknameInvalid: boolean;
  policy: PasswordPolicyResult;
  /** 확인 필드가 비어 있으면 null(표시하지 않음). */
  confirmMatches: boolean | null;
  canSave: boolean;
}

/**
 * D8 — 아래를 모두 만족할 때만 저장할 수 있다: 변경 있음, 바뀐 닉네임이 규칙 통과, 비밀번호 입력이 있으면 현재 비밀번호
 * 있음 + 새 비밀번호 정책 충족 + 확인값 일치, 저장 중 아님.
 */
export function evaluateSave(baselineNickname: string, form: ProfileForm, saving: boolean): SaveState {
  const nicknameChanged = isNicknameChanged(baselineNickname, form);
  const nicknameInvalid = nicknameChanged && !isValidNickname(form.nickname.trim());
  const passwordEntered = isPasswordEntered(form);
  const policy = evaluatePassword(form.newPassword);
  const confirmMatches = form.confirmPassword === '' ? null : form.confirmPassword === form.newPassword;
  const passwordReady = !passwordEntered || (form.currentPassword !== '' && policy.isValid && confirmMatches === true);
  const dirty = nicknameChanged || passwordEntered;
  return { dirty, nicknameInvalid, policy, confirmMatches, canSave: dirty && !nicknameInvalid && passwordReady && !saving };
}

/** 바뀐 항목만 담는다. 새 비밀번호 확인값은 클라이언트 전용이라 보내지 않는다(D8). */
export function buildUpdateBody(baselineNickname: string, form: ProfileForm): UpdateUserRequest {
  const body: UpdateUserRequest = {};
  if (isNicknameChanged(baselineNickname, form)) body.nickname = form.nickname.trim();
  if (isPasswordEntered(form)) {
    body.currentPassword = form.currentPassword;
    body.newPassword = form.newPassword;
  }
  return body;
}

export type ErrorTarget = 'nickname' | 'currentPassword' | 'newPassword' | 'banner';

const FIELD_TARGETS: ReadonlySet<string> = new Set(['nickname', 'currentPassword', 'newPassword']);

/**
 * 저장 실패를 어느 자리에 보일지 — 문구는 서버 error.message를 그대로 쓰고, 자리만 error.code로 정한다(상태 코드로
 * 분기하지 않는다). 서버 본문이 없으면(네트워크·timeout) 배너.
 */
export function resolveSaveError(error: ApiError | undefined): { target: ErrorTarget; message: string | undefined } {
  if (!error) return { target: 'banner', message: undefined };
  switch (error.code) {
    case 'INVALID_CURRENT_PASSWORD':
    case 'PASSWORD_CONFIRM_LOCKED':
      return { target: 'currentPassword', message: error.message };
    case 'VALIDATION_FAILED': {
      const field = error.fieldErrors?.find((fe) => FIELD_TARGETS.has(fe.field));
      if (field) return { target: field.field as ErrorTarget, message: field.message };
      return { target: 'banner', message: error.message };
    }
    default:
      return { target: 'banner', message: error.message };
  }
}
