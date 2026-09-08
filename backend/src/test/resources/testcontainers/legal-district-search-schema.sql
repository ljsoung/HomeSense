-- LegalDistrictCodeRepositoryMariaDbIT 전용 스키마. 이 통합 테스트는 legal_district_code
-- 테이블 하나만 건드리므로 테이블정의서 8장 DDL 원문 중 그 부분만 옮겨왔다.

CREATE TABLE legal_district_code (
    legal_dong_cd VARCHAR(10) PRIMARY KEY,
    legal_dong_name VARCHAR(60) NOT NULL,
    sido_name VARCHAR(20),
    sigungu_name VARCHAR(20),
    eupmyeondong_name VARCHAR(20),
    is_active BOOLEAN NOT NULL,
    data_version DATE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
