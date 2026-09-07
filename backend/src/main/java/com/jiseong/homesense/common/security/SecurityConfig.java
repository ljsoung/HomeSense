package com.jiseong.homesense.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.jiseong.homesense.common.config.JwtProperties;

import lombok.RequiredArgsConstructor;

/**
 * COM-SEC-01. JwtAuthenticationFilter를 Spring Security 필터체인에 등록한다.
 * 세션을 쓰지 않는 JWT 기반 인증이라 CSRF를 끄고 STATELESS로 둔다. 인증이 실제로 필요한
 * 엔드포인트(관심등록, 알림설정, 마이페이지, 관리자)는 해당 도메인 프로그램이 구현되는 시점에
 * authorizeHttpRequests에 개별 규칙을 추가한다 — 그 외는 permitAll로 전면 개방한다(비로그인
 * 조회 허용 원칙, UI정의서 1.5절).
 *
 * <p>SVC-AUTH-01의 logout()과 SVC-USER-01의 /api/users/** 세 엔드포인트(getMe/updateMe/withdraw)는
 * 전부 UserPrincipal(로그인한 본인)이 반드시 있어야 성립하는 연산이라 인증 필수 규칙으로 추가한다.
 * 미인증 접근은 Spring Security 기본 401 대신 {@link RestAuthenticationEntryPoint}로 COM-RES-01
 * 포맷을 유지한다.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers("/api/users/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(restAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
