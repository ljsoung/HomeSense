package com.jiseong.homesense.auth.dto;

/** SVC-AUTH-01.signup() 입력. Web 계층(SignupRequest)과 Service 계층을 분리한다. */
public record SignupCommand(String email, String password, String nickname) {
}
