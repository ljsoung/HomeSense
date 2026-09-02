package com.jiseong.homesense.common.security;

/**
 * JwtAuthenticationFilter가 유효한 토큰에서 뽑아 SecurityContext에 싣는 인증 주체.
 * role은 CHECK 제약과 동일한 "USER"/"ADMIN" 원문 값이며, GrantedAuthority로 변환할 때만
 * "ROLE_" 접두어를 붙인다(hasRole()과의 관례를 맞추기 위함일 뿐 이 레코드 자체엔 접두어를 두지 않는다).
 */
public record UserPrincipal(Long userId, String role) {
}
