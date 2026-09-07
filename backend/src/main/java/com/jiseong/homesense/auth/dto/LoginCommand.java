package com.jiseong.homesense.auth.dto;

/** SVC-AUTH-01.login() 입력. Web 계층(LoginRequest)과 Service 계층을 분리한다. */
public record LoginCommand(String email, String password) {
}
