-- UserServiceMariaDbIT 전용 최소 스키마. 테이블정의서 8장 DDL 원문 중 이 통합 테스트가 실제로
-- 건드리는 user/refresh_token 두 테이블만 재구성한 것이다(AuthServiceMariaDbIT의
-- user-race-schema.sql과 같은 패턴, refresh_token만 추가됐다) — 권위 있는 전체 DDL은 테이블정의서
-- 8장을 그대로 따라야 한다.

CREATE TABLE user (
    user_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(20) NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    status VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    social_provider VARCHAR(20),
    social_id VARCHAR(100),
    withdrawn_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_token (
    refresh_token_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    token_value VARCHAR(255) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    revoked_yn BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_refresh_token_user_id FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
