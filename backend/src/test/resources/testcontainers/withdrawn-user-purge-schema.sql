-- 탈퇴 계정 자동 파기(BAT-USR-01)·탈퇴 철회 IT용 스키마 픽스처.
-- 저장소 루트 schema_all.sql(테이블정의서 v2.1 8장 DDL 원문)에서 user와 그 자식/부모 10개 테이블의 CREATE TABLE
-- 문을 스크립트로 그대로 추출했다(FK ON DELETE 규칙까지 동일) — 손으로 옮겨 적다 CASCADE/SET NULL 방향이
-- 어긋나는 것을 막기 위함이다. batch_log·search_log는 user와 무관해 제외했다.
-- schema_all.sql의 FK/컬럼이 바뀌면 이 파일도 같은 방식으로 다시 추출해야 한다.

CREATE TABLE legal_district_code (
    legal_dong_cd      CHAR(10)     NOT NULL,
    legal_dong_name    VARCHAR(60)  NOT NULL,
    sido_name          VARCHAR(20)  NULL,
    sigungu_name       VARCHAR(20)  NULL,
    eupmyeondong_name  VARCHAR(20)  NULL,
    is_active          BOOLEAN      NOT NULL,
    data_version       DATE         NOT NULL,
    PRIMARY KEY (legal_dong_cd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='법정동코드';

CREATE TABLE user (
    user_id          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    email            VARCHAR(100)     NOT NULL,
    password         VARCHAR(255)     NOT NULL,
    nickname         VARCHAR(20)      NOT NULL,
    role             VARCHAR(10)      NOT NULL,
    status           VARCHAR(10)      NOT NULL,
    social_provider  VARCHAR(20)      NULL,
    social_id        VARCHAR(100)     NULL,
    withdrawn_at     DATETIME         NULL,
    created_at       DATETIME         NOT NULL,
    updated_at       DATETIME         NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_email (email),
    CONSTRAINT ck_user_role CHECK (role IN ('USER','ADMIN')),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE','SUSPENDED','WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원';

CREATE TABLE refresh_token (
    refresh_token_id  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED  NOT NULL,
    token_value       VARCHAR(255)     NOT NULL,
    expires_at        DATETIME         NOT NULL,
    revoked_yn        BOOLEAN          NOT NULL,
    created_at        DATETIME         NOT NULL,
    PRIMARY KEY (refresh_token_id),
    UNIQUE KEY uk_refresh_token_value (token_value),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='리프레시토큰';

CREATE TABLE complex (
    complex_id                    BIGINT UNSIGNED    NOT NULL AUTO_INCREMENT,
    source_complex_cd             VARCHAR(20)        NOT NULL,
    legal_dong_cd                 CHAR(10)           NULL,
    complex_name                  VARCHAR(100)       NOT NULL,
    complex_type                  VARCHAR(20)        NULL,
    sido                          VARCHAR(20)        NULL,
    sigungu                       VARCHAR(20)        NULL,
    dong_ri                       VARCHAR(20)        NULL,
    legal_dong_address            TEXT               NULL,
    supply_type                   VARCHAR(10)        NULL,
    approval_date                 DATE               NULL,
    building_count                SMALLINT UNSIGNED  NULL,
    household_count               INT UNSIGNED       NULL,
    sale_household_count          INT UNSIGNED       NULL,
    rental_household_count        INT UNSIGNED       NULL,
    public_rental_count           INT UNSIGNED       NULL,
    private_rental_count          INT UNSIGNED       NULL,
    management_type               VARCHAR(20)        NULL,
    heating_type                  VARCHAR(20)        NULL,
    corridor_type                 VARCHAR(20)        NULL,
    building_structure            VARCHAR(30)        NULL,
    constructor                   VARCHAR(100)       NULL,
    developer                     VARCHAR(100)       NULL,
    management_company            VARCHAR(100)       NULL,
    elevator_passenger_count      SMALLINT UNSIGNED  NOT NULL,
    elevator_cargo_count          SMALLINT UNSIGNED  NOT NULL,
    elevator_combined_count       SMALLINT UNSIGNED  NOT NULL,
    total_parking_count           INT UNSIGNED       NULL,
    ground_parking_count          INT UNSIGNED       NULL,
    underground_parking_count     INT UNSIGNED       NULL,
    cctv_count                    SMALLINT UNSIGNED  NULL,
    home_network_yn               BOOLEAN            NULL,
    office_address                VARCHAR(200)       NULL,
    office_phone                  VARCHAR(20)        NULL,
    community_facilities          VARCHAR(500)       NULL,
    resident_amenities            VARCHAR(500)       NULL,
    highest_floor                 SMALLINT           NULL,
    highest_floor_registered      SMALLINT           NULL,
    basement_floor_count          SMALLINT UNSIGNED  NULL,
    ev_charger_ground_yn          BOOLEAN            NULL,
    ev_charger_underground_yn     BOOLEAN            NULL,
    ev_parking_ground_count       SMALLINT UNSIGNED  NULL,
    ev_parking_underground_count  SMALLINT UNSIGNED  NULL,
    latitude                      DECIMAL(10,7)      NULL,
    longitude                     DECIMAL(10,7)      NULL,
    location_precision            VARCHAR(10)        NULL,
    data_updated_at               DATE               NOT NULL,
    created_at                    DATETIME           NOT NULL,
    updated_at                    DATETIME           NOT NULL,
    PRIMARY KEY (complex_id),
    UNIQUE KEY uk_complex_source_cd (source_complex_cd),
    CONSTRAINT fk_complex_legal_dong FOREIGN KEY (legal_dong_cd)
        REFERENCES legal_district_code (legal_dong_cd) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='단지';

CREATE TABLE trade (
    trade_id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    housing_type         VARCHAR(10)      NOT NULL,
    deal_category        VARCHAR(10)      NOT NULL,
    rent_type            VARCHAR(10)      NULL,
    dataset_id           VARCHAR(20)      NOT NULL,
    sgg_cd               CHAR(5)          NOT NULL,
    legal_dong_cd        CHAR(10)         NULL,
    umd_nm               VARCHAR(60)      NULL,
    complex_id           BIGINT UNSIGNED  NULL,
    building_name        VARCHAR(100)     NULL,
    jibun                VARCHAR(20)      NULL,
    road_address         VARCHAR(200)     NULL,
    exclu_use_area       DECIMAL(6,2)     NOT NULL,
    floor                SMALLINT         NULL,
    build_year           SMALLINT         NULL,
    deal_date            DATE             NOT NULL,
    deal_amount          BIGINT UNSIGNED  NULL,
    deposit_amount       BIGINT UNSIGNED  NULL,
    monthly_rent_amount  BIGINT UNSIGNED  NULL,
    apt_dong             VARCHAR(20)      NULL,
    dealing_type         VARCHAR(10)      NULL,
    agent_sgg_nm         VARCHAR(30)      NULL,
    registration_date    DATE             NULL,
    seller_type          VARCHAR(10)      NULL,
    buyer_type           VARCHAR(10)      NULL,
    land_lease_yn        BOOLEAN          NULL,
    cancel_yn            BOOLEAN          NOT NULL,
    cancel_date          DATE             NULL,
    match_method         VARCHAR(12)      NULL,
    match_confidence     DECIMAL(4,3)     NULL,
    latitude             DECIMAL(10,7)    NULL,
    longitude            DECIMAL(10,7)    NULL,
    location_precision   VARCHAR(10)      NULL,
    dedup_hash           CHAR(64)         NOT NULL,
    created_at           DATETIME         NOT NULL,
    updated_at           DATETIME         NOT NULL,
    PRIMARY KEY (trade_id),
    UNIQUE KEY uk_trade_dedup_hash (dedup_hash),
    CONSTRAINT fk_trade_legal_dong FOREIGN KEY (legal_dong_cd)
        REFERENCES legal_district_code (legal_dong_cd) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_trade_complex FOREIGN KEY (complex_id)
        REFERENCES complex (complex_id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_trade_housing_type CHECK (housing_type IN ('APT','VILLA')),
    CONSTRAINT ck_trade_deal_category CHECK (deal_category IN ('SALE','RENT')),
    CONSTRAINT ck_trade_rent_type CHECK (rent_type IS NULL OR rent_type IN ('JEONSE','WOLSE')),
    CONSTRAINT ck_trade_match_method CHECK (match_method IS NULL OR match_method IN ('EXACT','SIMILAR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='실거래';

CREATE TABLE favorite_property (
    favorite_property_id  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id               BIGINT UNSIGNED  NOT NULL,
    housing_type          VARCHAR(10)      NOT NULL,
    complex_id            BIGINT UNSIGNED  NOT NULL,
    registered_at         DATETIME         NOT NULL,
    created_at            DATETIME         NOT NULL,
    PRIMARY KEY (favorite_property_id),
    UNIQUE KEY uk_fav_prop_user_complex (user_id, complex_id),
    CONSTRAINT fk_favorite_property_user FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_favorite_property_complex FOREIGN KEY (complex_id)
        REFERENCES complex (complex_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_fav_prop_housing_type CHECK (housing_type IN ('APT','VILLA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관심매물';

CREATE TABLE favorite_region (
    favorite_region_id  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED  NOT NULL,
    legal_dong_cd       CHAR(10)         NOT NULL,
    registered_at       DATETIME         NOT NULL,
    created_at          DATETIME         NOT NULL,
    PRIMARY KEY (favorite_region_id),
    UNIQUE KEY uk_fav_region_user_dong (user_id, legal_dong_cd),
    CONSTRAINT fk_favorite_region_user FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_favorite_region_legal_dong FOREIGN KEY (legal_dong_cd)
        REFERENCES legal_district_code (legal_dong_cd) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관심지역';

CREATE TABLE recent_view (
    recent_view_id  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED  NULL,
    session_id      VARCHAR(100)     NULL,
    housing_type    VARCHAR(10)      NOT NULL,
    complex_id      BIGINT UNSIGNED  NOT NULL,
    viewed_at       DATETIME         NOT NULL,
    created_at      DATETIME         NOT NULL,
    PRIMARY KEY (recent_view_id),
    CONSTRAINT fk_recent_view_user FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_recent_view_complex FOREIGN KEY (complex_id)
        REFERENCES complex (complex_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_recent_view_actor CHECK (user_id IS NOT NULL OR session_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='최근조회이력';

CREATE TABLE notification_setting (
    notification_setting_id     BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id                     BIGINT UNSIGNED  NOT NULL,
    favorite_property_id        BIGINT UNSIGNED  NULL,
    favorite_region_id          BIGINT UNSIGNED  NULL,
    price_change_threshold_pct  DECIMAL(4,1)     NOT NULL,
    new_trade_alert_yn          BOOLEAN          NOT NULL,
    email_alert_yn              BOOLEAN          NOT NULL,
    created_at                  DATETIME         NOT NULL,
    updated_at                  DATETIME         NOT NULL,
    PRIMARY KEY (notification_setting_id),
    UNIQUE KEY uk_ntf_setting_user_fav_prop (user_id, favorite_property_id),
    UNIQUE KEY uk_ntf_setting_user_fav_region (user_id, favorite_region_id),
    CONSTRAINT fk_notification_setting_user FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_notification_setting_fav_prop FOREIGN KEY (favorite_property_id)
        REFERENCES favorite_property (favorite_property_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_notification_setting_fav_region FOREIGN KEY (favorite_region_id)
        REFERENCES favorite_region (favorite_region_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_ntf_setting_target CHECK (
        (favorite_property_id IS NOT NULL AND favorite_region_id IS NULL) OR
        (favorite_property_id IS NULL AND favorite_region_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알림설정';

CREATE TABLE notification (
    notification_id    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED  NOT NULL,
    notification_type  VARCHAR(15)      NOT NULL,
    title               VARCHAR(200)     NOT NULL,
    message             VARCHAR(500)     NULL,
    complex_id          BIGINT UNSIGNED  NULL,
    legal_dong_cd       CHAR(10)         NULL,
    trade_id            BIGINT UNSIGNED  NULL,
    is_read             BOOLEAN          NOT NULL,
    sent_at             DATETIME         NOT NULL,
    created_at          DATETIME         NOT NULL,
    PRIMARY KEY (notification_id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_notification_complex FOREIGN KEY (complex_id)
        REFERENCES complex (complex_id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_notification_legal_dong FOREIGN KEY (legal_dong_cd)
        REFERENCES legal_district_code (legal_dong_cd) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_notification_trade FOREIGN KEY (trade_id)
        REFERENCES trade (trade_id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_notification_type CHECK (notification_type IN ('PRICE_CHANGE','NEW_TRADE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알림';
