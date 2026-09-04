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

class NicknameValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private record NicknameHolder(@ValidNickname String nickname) {
    }

    private record NotBlankAndValidNicknameHolder(@NotBlank @ValidNickname String nickname) {
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "가나",             // 2자 — 최소 경계
            "닉네임123",
            "abcdefghijkl",     // 12자 — 최대 경계
    })
    void 길이가_2에서_12자_사이면_유효하다(String nickname) {
        Set<ConstraintViolation<NicknameHolder>> violations = validator.validate(new NicknameHolder(nickname));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",                  // 공백
            " ",                 // 공백뿐
            "가",                // 1자 — 최소 미달
            "abcdefghijklm",     // 13자 — 최대 초과
    })
    void 길이가_2에서_12자_범위를_벗어나면_무효하다(String nickname) {
        Set<ConstraintViolation<NicknameHolder>> violations = validator.validate(new NicknameHolder(nickname));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void 위반_시_기본_메시지가_노출된다() {
        Set<ConstraintViolation<NicknameHolder>> violations = validator.validate(new NicknameHolder("가"));

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("닉네임은 2자 이상 12자 이하여야 합니다");
    }

    /**
     * 코드리뷰에서 지적된 함정을 회귀 테스트로 고정한다 — {@code @ValidNickname}이 이미 null/공백을
     * 무효로 처리하므로, DTO에 {@code @NotBlank}를 함께 붙이면 공백 입력 하나에 "must not be blank"와
     * 이 애노테이션의 메시지가 중복으로 실린다. SVC-AUTH-01 DTO에는 {@code @ValidNickname} 하나만 쓴다.
     */
    @Test
    void NotBlank와_함께_쓰면_공백_입력에_에러_메시지가_중복으로_실린다() {
        Set<ConstraintViolation<NotBlankAndValidNicknameHolder>> violations =
                validator.validate(new NotBlankAndValidNicknameHolder(""));

        assertThat(violations).hasSize(2);
    }
}
