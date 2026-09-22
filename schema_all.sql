-- ============================================================================
-- HomeSense schema_all.sql
--
-- 출처: HomeSense 테이블 정의서 v2.1 (2026-08-31) 8장 "부록: CREATE TABLE DDL" 원문.
-- 이 파일은 저장소 git 히스토리에 한 번도 존재한 적이 없었음이 확인되어(2026-09,
-- API-SEARCH-01/SCR-HOME-01 작업 중 발견), 원본 문서를 기준으로 재구성했다.
-- CLAUDE.md의 `mysql -u root homesense < schema_all.sql` 명령이 가정하던 바로 그 파일.
--
-- 포함 범위: 테이블정의서 v2.1 8장의 11개 테이블(회원~운영 6개 도메인, 161개 컬럼,
-- FK 17건, CHECK 10건, UNIQUE 8건) + 신규 도메인 search_log(API-SEARCH-01, 인기
-- 검색어 로깅, 2026-09 신규 제안 — 원본 11개 테이블과 달리 이 문서 자체에는 없음).
--
-- 포함하지 않은 것: 테이블정의서 7.2절 "제안 성능 인덱스" 13건. 원본 문서가 스스로
-- "엔티티정의서·schema_all.sql 어디에도 없는 신규 제안이며 반영 전 검토 필요"라고
-- 명시하고 있어, 이 파일도 그 구분을 그대로 유지했다. 필요해지면 7.2절 원문의
-- CREATE INDEX 문 13개를 별도로 반영할 것(EXPLAIN 검토 선행 권장).
--
-- 실행: mysql -u root homesense < schema_all.sql
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 8.0 DROP 순서 (FK 의존성 역순 — 테이블정의서 원문 + search_log 추가)
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS search_log;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS notification_setting;
DROP TABLE IF EXISTS recent_view;
DROP TABLE IF EXISTS favorite_region;
DROP TABLE IF EXISTS favorite_property;
DROP TABLE IF EXISTS trade;
DROP TABLE IF EXISTS complex;
DROP TABLE IF EXISTS refresh_token;
DROP TABLE IF EXISTS batch_log;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS legal_district_code;

-- ----------------------------------------------------------------------------
-- legal_district_code (법정동코드)  ENT-RGN-01  [변경 없음]
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- user (회원)  ENT-USER-01  [변경 없음]
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- batch_log (배치실행이력)  ENT-ADM-01  [변경 없음]
-- ----------------------------------------------------------------------------
CREATE TABLE batch_log (
    batch_log_id     BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    housing_type     VARCHAR(10)      NOT NULL,
    deal_category    VARCHAR(10)      NOT NULL,
    lawd_cd          CHAR(5)          NOT NULL,
    deal_ymd         CHAR(6)          NOT NULL,
    dataset_id       VARCHAR(20)      NOT NULL,
    result_code      VARCHAR(3)       NOT NULL,
    result_message   VARCHAR(200)     NULL,
    success_yn       BOOLEAN          NOT NULL,
    processed_count  INT UNSIGNED     NOT NULL,
    error_count      INT UNSIGNED     NOT NULL,
    started_at       DATETIME         NOT NULL,
    finished_at      DATETIME         NULL,
    created_at       DATETIME         NOT NULL,
    PRIMARY KEY (batch_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='배치실행이력';

-- ----------------------------------------------------------------------------
-- refresh_token (리프레시토큰)  ENT-AUTH-01  [v2.2 변경]
-- v2.2: rotated_yn 추가 — API-AUTH-01 Refresh Token Rotation(2026-09-22). revoked_yn=true가
-- rotation(후속 토큰 발급 성공)으로 인한 것인지 구분한다. 로그아웃이 이미 rotation된 토큰을
-- 제출받으면 재사용 탐지로 전환하는 데 쓰인다(CLAUDE.md SVC-AUTH-01 Refresh Token Rotation 절 참고).
-- ----------------------------------------------------------------------------
CREATE TABLE refresh_token (
    refresh_token_id  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED  NOT NULL,
    token_value       VARCHAR(255)     NOT NULL,
    expires_at        DATETIME         NOT NULL,
    revoked_yn        BOOLEAN          NOT NULL,
    rotated_yn        BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at        DATETIME         NOT NULL,
    PRIMARY KEY (refresh_token_id),
    UNIQUE KEY uk_refresh_token_value (token_value),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id)
        REFERENCES user (user_id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='리프레시토큰';

-- ----------------------------------------------------------------------------
-- complex (단지)  ENT-CPX-01  [v2.1 변경]
-- v2.1: complex_type NOT NULL→NULL 전환(원본 xlsx 21,680건 중 105건 미기재).
--       legal_dong_address VARCHAR(200)→TEXT(지번 목록 최대 1,303자 확인,
--       BAT-MAT-02 매칭 정확도 보존을 위해 원본 비압축 저장).
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- trade (실거래)  ENT-TRD-01  [v2.1 변경]
-- v1.0 대비 officetel_key, masked_address 컬럼 제거 / CHECK 값 범위 축소.
-- v2.1: match_method NOT NULL→NULL 전환, ck_trade_match_method에 IS NULL 분기
-- 추가(매칭이 완전히 실패한 경우를 표현).
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- favorite_property (관심매물)  ENT-FAV-01  [v2.0 변경]
-- v1.0 대비 officetel_key 컬럼과 ck_fav_prop_target 제약 제거, complex_id NOT NULL 전환.
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- favorite_region (관심지역)  ENT-FAV-02  [변경 없음]
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- recent_view (최근조회이력)  ENT-RCV-01  [v2.0 변경]
-- v1.0 대비 officetel_key, legal_dong_cd(및 그 FK) 제거, complex_id NOT NULL 전환.
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- notification_setting (알림설정)  ENT-NTF-01  [변경 없음]
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- notification (알림)  ENT-NTF-02  [v2.0 변경]
-- v1.0 대비 officetel_key 컬럼 제거.
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- search_log (검색로그)  신규 제안 — 원본 테이블정의서 v2.1에는 없음.
-- API-SEARCH-01/SVC-SEARCH-01(2026-09, HOME-01 인기검색어 선행 작업)에서 신규
-- 도입. 기존 backend/src/main/resources/schema/search_log.sql과 동일 정의를
-- 여기에도 반영해 schema_all.sql을 단일 소스로 유지하기로 함(판단 기록 참조).
-- ----------------------------------------------------------------------------
CREATE TABLE search_log (
    search_log_id  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    keyword        VARCHAR(100)     NOT NULL,
    searched_at    DATETIME         NOT NULL,
    PRIMARY KEY (search_log_id),
    KEY idx_search_log_keyword_searched_at (keyword, searched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='검색로그';
