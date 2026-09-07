package com.jiseong.homesense.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * SVC-USER-01의 부분 수정(닉네임 변경이 선택적) 시나리오를 검증한다 — {@link ValidNickname}과 정확히
 * 같은 정책이되, null(필드 생략)만은 예외적으로 유효해야 한다는 점이 이 애노테이션의 존재 이유다.
 */
class NicknameIfPresentValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private record NicknameHolder(@ValidNicknameIfPresent String nickname) {
    }

    @Test
    void null이면_유효하다() {
        Set<ConstraintViolation<NicknameHolder>> violations = validator.validate(new NicknameHolder(null));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"가나", "닉네임123", "abcdefghijkl"})
    void 값이_있고_2에서_12자_사이면_유효하다(String nickname) {
        Set<ConstraintViolation<NicknameHolder>> violations = validator.validate(new NicknameHolder(nickname));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "가", "abcdefghijklm"})
    void 값이_있는데_정책을_벗어나면_무효하다(String nickname) {
        Set<ConstraintViolation<NicknameHolder>> violations = validator.validate(new NicknameHolder(nickname));

        assertThat(violations).isNotEmpty();
    }
}
