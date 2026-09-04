package com.jiseong.homesense.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;

class PasswordValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private record PasswordHolder(@ValidPassword String password) {
    }

    private record NotBlankAndValidPasswordHolder(@NotBlank @ValidPassword String password) {
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcd12!@",           // 정확히 8자, 영문+숫자+특수문자
            "Aa1!Aa1!",
            "password1!longer",   // 8자 초과도 허용
    })
    void 영문_숫자_특수문자를_모두_포함한_8자_이상은_유효하다(String password) {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder(password));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",             // 공백
            " ",            // 공백뿐
            "abc12!@",      // 7자 — 길이 미달
            "abcdefgh",     // 영문만
            "12345678",     // 숫자만
            "abcdefg1",     // 영문+숫자, 특수문자 없음
            "!@#$%^&*",     // 특수문자만
    })
    void 조건을_하나라도_충족하지_못하면_무효하다(String password) {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder(password));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void 위반_시_기본_메시지가_노출된다() {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder("short"));

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 모두 포함해야 합니다");
    }

    /**
     * 코드리뷰에서 지적된 함정을 회귀 테스트로 고정한다 — {@code @ValidPassword}가 이미 null/공백을
     * 무효로 처리하므로, DTO에 {@code @NotBlank}를 함께 붙이면 공백 입력 하나에 "must not be blank"와
     * 이 애노테이션의 메시지가 중복으로 실린다. SVC-AUTH-01 DTO에는 {@code @ValidPassword} 하나만 쓴다.
     */
    @Test
    void NotBlank와_함께_쓰면_공백_입력에_에러_메시지가_중복으로_실린다() {
        Set<ConstraintViolation<NotBlankAndValidPasswordHolder>> violations =
                validator.validate(new NotBlankAndValidPasswordHolder(""));

        assertThat(violations).hasSize(2);
    }

    /**
     * 코드리뷰에서 지적된 함정을 회귀 테스트로 고정한다 — {@code Character.isLetter}/{@code isDigit}는
     * 유니코드 전체를 대상으로 해서, 실제 영문이 하나도 없는 "가나다라123!"도 한글이 letter 플래그를
     * 켜 통과해 버렸다("가나다라123!"). 숫자 쪽도 마찬가지라, ASCII 숫자가 하나도 없는데 아랍-인도
     * 숫자(U+0661~)가 digit 플래그를 켜 통과해 버렸다("abcdefg١٢٣!"). ASCII 범위로
     * 명시한 뒤에는 둘 다 무효로 판정돼야 한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "가나다라123!",              // 한글 4자 + ASCII 숫자 3자 + 특수문자 1자, 영문은 없음
            "abcdefg١٢٣!", // ASCII 영문 7자 + 아랍-인도 숫자 3자 + 특수문자 1자, ASCII 숫자는 없음
    })
    void 영문_숫자가_ASCII_범위를_벗어나면_해당_조건을_충족한_것으로_보지_않는다(String password) {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder(password));

        assertThat(violations).isNotEmpty();
    }

    /**
     * 코드리뷰에서 지적된 함정을 회귀 테스트로 고정한다 — 특수문자 판정을 "letter도 digit도
     * 아니면 special"이라는 소거법으로 하면, 그 두 집합에 안 걸리는 문자(한글, 공백 등)가 전부
     * 특수문자로 잘못 인정돼 실제 특수문자가 하나도 없는 비밀번호도 통과해 버린다 — 예를 들어
     * "abcd1234가"는 한글 한 글자를 특수문자로 오인해 통과했다. 특수문자도 SPECIAL_CHARS라는
     * 명시적 허용 집합으로만 판정한 뒤에는 무효로 판정돼야 한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "abcd1234가",   // 영문+ASCII숫자+한글 1자, 실제 특수문자는 없음
            "abcd1234 ",    // 영문+ASCII숫자+공백(끝), 실제 특수문자는 없음
    })
    void 소거법이_아닌_명시적_집합으로만_특수문자를_인정한다(String password) {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder(password));

        assertThat(violations).isNotEmpty();
    }
}
