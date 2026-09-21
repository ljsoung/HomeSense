package com.jiseong.homesense.auth.dto;

/** SVC-AUTH-01.reactivate() 입력. Web 계층(ReactivateRequest)과 Service 계층을 분리한다. */
public record ReactivateCommand(String email, String password) {
}
