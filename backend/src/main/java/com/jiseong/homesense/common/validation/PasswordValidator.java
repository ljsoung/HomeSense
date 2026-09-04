package com.jiseong.homesense.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ValidPassword의 실제 판정 로직. null/공백은 무효로 처리한다 — {@code @ValidPassword} 하나만
 * 붙여도(별도 {@code @NotBlank} 없이) 그 자체로 완결된 제약이 되도록 하기 위함이다.
 *
 * <p>영문·숫자·특수문자 세 카테고리 모두 {@code Character.isLetter}/{@code isDigit} 같은 유니코드
 * 전체 판정이 아니라 명시적인 허용 집합으로만 판정한다(ASCII a-z/A-Z, ASCII 0-9, {@link #SPECIAL_CHARS}).
 * 처음에는 "letter도 digit도 아니면 special"로 소거법을 썼는데, 그러면 한글·이모지·제어문자처럼
 * 세 집합 어디에도 속하지 않는 문자가 전부 특수문자로 잘못 인정돼 정작 실제 특수문자가 하나도 없는
 * 비밀번호까지 통과했다(코드리뷰에서 지적됨) — 예를 들어 "abcd1234 "(끝에 공백 하나)가 그 공백을
 * special로 오인해 통과해 버렸다. 세 집합 다 명시적으로 판정하면 그 문제도, "가나다라123!"에 영문이
 * 없어 무효라는 판정도 여전히 정확하다.
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final String SPECIAL_CHARS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank() || value.length() < MIN_LENGTH) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isAsciiLetter(c)) {
                hasLetter = true;
            } else if (isAsciiDigit(c)) {
                hasDigit = true;
            } else if (SPECIAL_CHARS.indexOf(c) >= 0) {
                hasSpecial = true;
            }
            // 위 세 집합 어디에도 속하지 않는 문자(공백, 제어문자, 한글, 이모지 등)는
            // 어떤 카테고리도 만족시키지 않는다 — 의도적으로 아무것도 하지 않는다.
        }
        return hasLetter && hasDigit && hasSpecial;
    }

    private boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
