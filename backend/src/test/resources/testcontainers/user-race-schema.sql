-- AuthServiceMariaDbIT 전용 최소 스키마. 테이블정의서 8장 DDL 원문 중 이 통합 테스트가 실제로
-- 건드리는 user 테이블만 재구성한 것이다(TradeChunkLoaderMariaDbIT의 trade-race-schema.sql과 같은
-- 패턴) — 권위 있는 전체 DDL은 테이블정의서 8장을 그대로 따라야 한다. refresh_token 등 다른 테이블은
-- 이 테스트의 회귀 시나리오(DuplicateEmailException 경로)가 닿지 않아 포함하지 않았다.

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
