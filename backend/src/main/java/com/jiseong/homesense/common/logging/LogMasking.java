package com.jiseong.homesense.common.logging;

import java.util.regex.Pattern;

/**
 * COM-LOG-01. 로그 문자열에서 개인정보·자격증명을 가린다. {@link LogMaskingJsonMembersCustomizer}가 구조화 로그의 모든 문자열
 * 값(메시지, 스택트레이스, key-value)에 적용한다.
 *
 * <ul>
 * <li>이메일 {@code alice@example.com} → {@code a***@e***.com} — DB 오류 원문("Duplicate entry 'x@y.com'")처럼 예외 메시지에
 * 섞여 들어오는 경우가 대표적이다.</li>
 * <li>{@code Bearer <토큰>} → {@code Bearer ***}</li>
 * <li>JWT(Access·Refresh Token, {@code eyJ…} 세 조각) → {@code ***JWT***}</li>
 * <li>쿼리 문자열의 {@code token=…}(비밀번호 재설정 링크) → {@code token=***}</li>
 * </ul>
 */
public final class LogMasking {

    private static final Pattern EMAIL = Pattern.compile(
            "([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9-])[A-Za-z0-9.-]*(\\.[A-Za-z]{2,})");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer)\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern TOKEN_PARAM = Pattern.compile("([?&]token=)[^&\\s\"']+");

    private LogMasking() {
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String masked = value;
        if (masked.indexOf('@') >= 0) {
            masked = EMAIL.matcher(masked).replaceAll("$1***@$2***$3");
        }
        masked = BEARER.matcher(masked).replaceAll("$1 ***");
        if (masked.contains("eyJ")) {
            masked = JWT.matcher(masked).replaceAll("***JWT***");
        }
        if (masked.contains("token=")) {
            masked = TOKEN_PARAM.matcher(masked).replaceAll("$1***");
        }
        return masked;
    }
}
