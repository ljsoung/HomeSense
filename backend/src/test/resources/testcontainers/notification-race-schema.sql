-- NotificationServiceMariaDbIT 전용 최소 스키마. 테이블정의서 8장 DDL 원문 중 이 통합 테스트가 실제로
-- 건드리는 user/legal_district_code/complex/favorite_property/favorite_region/notification_setting
-- 여섯 테이블만 재구성한 것이다(FavoriteServiceMariaDbIT의 favorite-race-schema.sql과 같은 이유로
-- complex는 전체 컬럼을 그대로 옮겨왔다) — 권위 있는 전체 DDL은 테이블정의서 8장을 그대로 따라야 한다.

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
    legal_dong_cd VARCHAR(10),
    complex_name VARCHAR(100) NOT NULL,
    complex_type VARCHAR(20) NOT NULL,
    sido VARCHAR(20),
    sigungu VARCHAR(20),
    dong_ri VARCHAR(20),
    legal_dong_address VARCHAR(200),
    supply_type VARCHAR(10),
    approval_date DATE,
    building_count SMALLINT,
    household_count INT,
    sale_household_count INT,
    rental_household_count INT,
    public_rental_count INT,
    private_rental_count INT,
    management_type VARCHAR(20),
    heating_type VARCHAR(20),
    corridor_type VARCHAR(20),
    building_structure VARCHAR(30),
    constructor VARCHAR(100),
    developer VARCHAR(100),
    management_company VARCHAR(100),
    elevator_passenger_count SMALLINT NOT NULL,
    elevator_cargo_count SMALLINT NOT NULL,
    elevator_combined_count SMALLINT NOT NULL,
    total_parking_count INT,
    ground_parking_count INT,
    underground_parking_count INT,
    cctv_count SMALLINT,
    home_network_yn BOOLEAN,
    office_address VARCHAR(200),
    office_phone VARCHAR(20),
    community_facilities VARCHAR(500),
    resident_amenities VARCHAR(500),
    highest_floor SMALLINT,
    highest_floor_registered SMALLINT,
    basement_floor_count SMALLINT,
    ev_charger_ground_yn BOOLEAN,
    ev_charger_underground_yn BOOLEAN,
    ev_parking_ground_count SMALLINT,
    ev_parking_underground_count SMALLINT,
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    location_precision VARCHAR(10),
    data_updated_at DATE NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_complex_legal_dong_cd FOREIGN KEY (legal_dong_cd)
        REFERENCES legal_district_code (legal_dong_cd) ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE favorite_property (
    favorite_property_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    housing_type VARCHAR(10) NOT NULL CHECK (housing_type IN ('APT', 'VILLA')),
    complex_id BIGINT UNSIGNED NOT NULL,
    registered_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_fav_prop_user_complex UNIQUE (user_id, complex_id),
    CONSTRAINT fk_fav_prop_user_id FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_fav_prop_complex_id FOREIGN KEY (complex_id)
        REFERENCES complex (complex_id) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE favorite_region (
    favorite_region_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    legal_dong_cd VARCHAR(10) NOT NULL,
    registered_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_fav_region_user_dong UNIQUE (user_id, legal_dong_cd),
    CONSTRAINT fk_fav_region_user_id FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_fav_region_legal_dong_cd FOREIGN KEY (legal_dong_cd)
        REFERENCES legal_district_code (legal_dong_cd) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_setting (
    notification_setting_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    favorite_property_id BIGINT UNSIGNED,
    favorite_region_id BIGINT UNSIGNED,
    price_change_threshold_pct DECIMAL(4, 1) NOT NULL,
    new_trade_alert_yn BOOLEAN NOT NULL,
    email_alert_yn BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT ck_ntf_setting_target CHECK (
        (favorite_property_id IS NOT NULL AND favorite_region_id IS NULL)
        OR (favorite_property_id IS NULL AND favorite_region_id IS NOT NULL)
    ),
    CONSTRAINT uk_ntf_setting_user_property UNIQUE (user_id, favorite_property_id),
    CONSTRAINT uk_ntf_setting_user_region UNIQUE (user_id, favorite_region_id),
    CONSTRAINT fk_ntf_setting_user_id FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_ntf_setting_fav_prop_id FOREIGN KEY (favorite_property_id)
        REFERENCES favorite_property (favorite_property_id) ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_ntf_setting_fav_region_id FOREIGN KEY (favorite_region_id)
        REFERENCES favorite_region (favorite_region_id) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
