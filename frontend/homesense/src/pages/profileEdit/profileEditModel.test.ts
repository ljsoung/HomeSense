import { describe, expect, it } from 'vitest';
import {
  buildUpdateBody,
  evaluateSave,
  initialForm,
  isDirty,
  resolveSaveError,
  type ProfileForm,
} from './profileEditModel';

const BASE = '지성';
const VALID_NEW = 'Efgh5678!';

function form(overrides: Partial<ProfileForm> = {}): ProfileForm {
  return { ...initialForm(BASE), ...overrides };
}

const openWith = (current: string, next: string, confirm: string) =>
  form({ passwordOpen: true, currentPassword: current, newPassword: next, confirmPassword: confirm });

describe('evaluateSave — D8 저장 버튼 활성 조건', () => {
  it.each<[string, ProfileForm, boolean, boolean]>([
    ['변경 없음', form(), false, false],
    ['닉네임 앞뒤 공백만 추가(trim 후 같음)', form({ nickname: ` ${BASE} ` }), false, false],
    ['닉네임만 변경(규칙 통과)', form({ nickname: '새닉네임' }), false, true],
    ['닉네임 1자(규칙 위반)', form({ nickname: '가' }), false, false],
    ['닉네임 13자(규칙 위반)', form({ nickname: '가'.repeat(13) }), false, false],
    ['섹션만 열고 입력 없음', form({ passwordOpen: true }), false, false],
    ['현재 비밀번호만 입력(부분 입력)', openWith('Abcd1234!', '', ''), false, false],
    ['현재 비밀번호 없이 새 비밀번호·확인', openWith('', VALID_NEW, VALID_NEW), false, false],
    ['새 비밀번호 정책 미충족(특수문자 없음)', openWith('Abcd1234!', 'Efgh5678', 'Efgh5678'), false, false],
    ['확인값 불일치', openWith('Abcd1234!', VALID_NEW, 'Efgh5678?'), false, false],
    ['비밀번호 세 필드 모두 충족', openWith('Abcd1234!', VALID_NEW, VALID_NEW), false, true],
    ['저장 진행 중', openWith('Abcd1234!', VALID_NEW, VALID_NEW), true, false],
    ['닉네임 유효 + 비밀번호 부분 입력', { ...openWith('Abcd1234!', '', ''), nickname: '새닉네임' }, false, false],
    ['닉네임 위반 + 비밀번호 충족', { ...openWith('Abcd1234!', VALID_NEW, VALID_NEW), nickname: '가' }, false, false],
  ])('%s', (_label, value, saving, expected) => {
    expect(evaluateSave(BASE, value, saving).canSave).toBe(expected);
  });

  it('확인 필드가 비어 있으면 일치 여부를 보이지 않는다(null)', () => {
    expect(evaluateSave(BASE, openWith('a', VALID_NEW, ''), false).confirmMatches).toBeNull();
    expect(evaluateSave(BASE, openWith('a', VALID_NEW, VALID_NEW), false).confirmMatches).toBe(true);
    expect(evaluateSave(BASE, openWith('a', VALID_NEW, 'x'), false).confirmMatches).toBe(false);
  });

  it('닉네임이 바뀌지 않았으면 규칙 위반으로 보지 않는다(원래 값이 규칙 밖이어도)', () => {
    expect(evaluateSave('가', initialForm('가'), false).nicknameInvalid).toBe(false);
  });
});

describe('buildUpdateBody — 바뀐 항목만, 확인값은 보내지 않는다', () => {
  it('닉네임만', () => {
    expect(buildUpdateBody(BASE, form({ nickname: '  새닉네임 ' }))).toEqual({ nickname: '새닉네임' });
  });

  it('비밀번호만 — 확인값 없음', () => {
    const body = buildUpdateBody(BASE, openWith('Abcd1234!', VALID_NEW, VALID_NEW));
    expect(body).toEqual({ currentPassword: 'Abcd1234!', newPassword: VALID_NEW });
    expect(JSON.stringify(body)).not.toContain('confirm');
  });

  it('둘 다', () => {
    expect(buildUpdateBody(BASE, { ...openWith('Abcd1234!', VALID_NEW, VALID_NEW), nickname: '새닉네임' })).toEqual({
      nickname: '새닉네임',
      currentPassword: 'Abcd1234!',
      newPassword: VALID_NEW,
    });
  });

  it('섹션이 접혀 있으면 비밀번호 값이 남아 있어도 보내지 않는다(D6)', () => {
    expect(buildUpdateBody(BASE, { ...openWith('Abcd1234!', VALID_NEW, VALID_NEW), passwordOpen: false, nickname: '새닉네임' })).toEqual({
      nickname: '새닉네임',
    });
  });
});

describe('isDirty — 이탈 방지(D7)', () => {
  it('변경이 없거나 섹션만 열면 dirty 아님, 하나라도 입력하면 dirty', () => {
    expect(isDirty(BASE, form())).toBe(false);
    expect(isDirty(BASE, form({ passwordOpen: true }))).toBe(false);
    expect(isDirty(BASE, openWith('', '', 'x'))).toBe(true);
    expect(isDirty(BASE, form({ nickname: '새닉네임' }))).toBe(true);
  });
});

describe('resolveSaveError — 자리는 error.code로, 문구는 서버 그대로', () => {
  it('현재 비밀번호 불일치·잠금 → 현재 비밀번호 필드', () => {
    expect(resolveSaveError({ code: 'INVALID_CURRENT_PASSWORD', message: '현재 비밀번호가 일치하지 않습니다' })).toEqual({
      target: 'currentPassword',
      message: '현재 비밀번호가 일치하지 않습니다',
    });
    expect(resolveSaveError({ code: 'PASSWORD_CHANGE_LOCKED', message: '잠김' }).target).toBe('currentPassword');
  });

  it('검증 실패 → fieldErrors의 필드, 알 수 없는 필드면 배너', () => {
    expect(
      resolveSaveError({
        code: 'VALIDATION_FAILED',
        message: '입력값이 유효하지 않습니다',
        fieldErrors: [{ field: 'nickname', message: '닉네임은 2자 이상 12자 이하여야 합니다' }],
      }),
    ).toEqual({ target: 'nickname', message: '닉네임은 2자 이상 12자 이하여야 합니다' });
    expect(
      resolveSaveError({ code: 'VALIDATION_FAILED', message: '입력값이 유효하지 않습니다', fieldErrors: [{ field: 'x', message: 'm' }] }),
    ).toEqual({ target: 'banner', message: '입력값이 유효하지 않습니다' });
  });

  it('그 밖의 코드·본문 없음 → 배너', () => {
    expect(resolveSaveError({ code: 'INTERNAL_SERVER_ERROR', message: '서버 오류' })).toEqual({ target: 'banner', message: '서버 오류' });
    expect(resolveSaveError(undefined)).toEqual({ target: 'banner', message: undefined });
  });
});
