-- TradeChunkLoaderMariaDbIT 전용 최소 스키마. 테이블정의서 8장 DDL 원문의 일부만 재구성한 것으로,
-- 이 통합 테스트가 실제로 건드리는 컬럼(주로 trade 전체와 legal_district_code/complex는 FK 대상으로만
-- 필요한 최소 컬럼)만 담았다 — 권위 있는 전체 DDL은 테이블정의서 8장을 그대로 따라야 한다.

CREATE TABLE legal_district_code (
    legal_dong_cd VARCHAR(10) PRIMARY KEY,
    legal_dong_name VARCHAR(60) NOT NULL,
    sido_name VARCHAR(20),
    sigungu_name VARCHAR(20),
    eupmyeondong_name VARCHAR(20),
    is_active BOOLEAN NOT NULL,
    data_version DATE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE complex (
    complex_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source_complex_cd VARCHAR(20) NOT NULL UNIQUE,
    complex_name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trade (
    trade_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    housing_type VARCHAR(10) NOT NULL CHECK (housing_type IN ('APT', 'VILLA')),
    deal_category VARCHAR(10) NOT NULL CHECK (deal_category IN ('SALE', 'RENT')),
    rent_type VARCHAR(10),
    dataset_id VARCHAR(20) NOT NULL,
    sgg_cd VARCHAR(5) NOT NULL,
    legal_dong_cd VARCHAR(10),
    umd_nm VARCHAR(60),
    complex_id BIGINT UNSIGNED,
    building_name VARCHAR(100),
    jibun VARCHAR(20),
    road_address VARCHAR(200),
    exclu_use_area DECIMAL(6, 2) NOT NULL,
    floor SMALLINT,
    build_year SMALLINT,
    deal_date DATE NOT NULL,
    deal_amount BIGINT,
    deposit_amount BIGINT,
    monthly_rent_amount BIGINT,
    apt_dong VARCHAR(20),
    dealing_type VARCHAR(10),
    agent_sgg_nm VARCHAR(30),
    registration_date DATE,
    seller_type VARCHAR(10),
    buyer_type VARCHAR(10),
    land_lease_yn BOOLEAN,
    cancel_yn BOOLEAN NOT NULL,
    cancel_date DATE,
    match_method VARCHAR(12) CHECK (match_method IN ('EXACT', 'SIMILAR')),
    match_confidence DECIMAL(4, 3),
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    location_precision VARCHAR(10),
    dedup_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_trade_legal_dong_cd FOREIGN KEY (legal_dong_cd)
        REFERENCES legal_district_code (legal_dong_cd) ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_trade_complex_id FOREIGN KEY (complex_id)
        REFERENCES complex (complex_id) ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
