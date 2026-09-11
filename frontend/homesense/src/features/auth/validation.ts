/**
 * COM-VAL-01과 동일 규칙(이중 방어선) — backend PasswordValidator/NicknameValidator
 * (common/validation/)를 그대로 미러링한다. 백엔드가 바뀌면 이 파일도 함께 바꿔야 한다.
 */

const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_BYTES = 72;
// PasswordValidator.SPECIAL_CHARS와 동일한 명시적 허용 집합 — 소거법(letter도 digit도 아니면
// special)을 쓰면 공백/한글/이모지 같은 문자가 특수문자로 잘못 인정된다(백엔드 코드 주석 참고).
const SPECIAL_CHARS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

export interface PasswordPolicyResult {
  minLength: boolean;
  hasLetter: boolean;
  hasDigit: boolean;
  hasSpecial: boolean;
  withinByteLimit: boolean;
  isValid: boolean;
}

function isAsciiLetter(char: string): boolean {
  return (char >= 'a' && char <= 'z') || (char >= 'A' && char <= 'Z');
}

function isAsciiDigit(char: string): boolean {
  return char >= '0' && char <= '9';
}

export function evaluatePassword(password: string): PasswordPolicyResult {
  const minLength = password.length >= PASSWORD_MIN_LENGTH;
  const withinByteLimit = new TextEncoder().encode(password).length <= PASSWORD_MAX_BYTES;

  let hasLetter = false;
  let hasDigit = false;
  let hasSpecial = false;
  for (const char of password) {
    if (isAsciiLetter(char)) {
      hasLetter = true;
    } else if (isAsciiDigit(char)) {
      hasDigit = true;
    } else if (SPECIAL_CHARS.includes(char)) {
      hasSpecial = true;
    }
  }

  return {
    minLength,
    hasLetter,
    hasDigit,
    hasSpecial,
    withinByteLimit,
    isValid: minLength && hasLetter && hasDigit && hasSpecial && withinByteLimit,
  };
}

const NICKNAME_MIN_LENGTH = 2;
const NICKNAME_MAX_LENGTH = 12;

/** NicknameValidator와 동일하게 코드 포인트 단위로 길이를 센다(서로게이트 쌍이 2자로 잘못 세어지지 않도록). */
export function isValidNickname(nickname: string): boolean {
  if (nickname.trim().length === 0) {
    return false;
  }
  const length = Array.from(nickname).length;
  return length >= NICKNAME_MIN_LENGTH && length <= NICKNAME_MAX_LENGTH;
}

// 서버의 @Email(Hibernate Validator)과 문자 단위로 동일하지 않은 실용적 근사치다 — 최종 권위는
// 항상 서버(중복확인 API 호출 전 게이트, 가입 제출 시 재검증)에 있으므로 여기서는 "이 값을
// 중복확인 API에 보낼 가치가 있는 모양인가"만 걸러낸다.
const EMAIL_FORMAT_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isValidEmailFormat(email: string): boolean {
  return EMAIL_FORMAT_REGEX.test(email.trim());
}
