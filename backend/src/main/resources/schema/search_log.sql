-- ENT-SEARCH-01(신규 제안 — CLAUDE.md API-SEARCH-01 절 참고).
-- spring.jpa.hibernate.ddl-auto=validate이므로, 이 프로젝트의 다른 11개 테이블과 마찬가지로
-- 애플리케이션을 기동하기 전에 대상 DB에 반드시 먼저 적용해야 한다.
--
--   mysql -u root homesense < src/main/resources/schema/search_log.sql
--
-- FK 없음 — user_id/session_id는 인기검색어 집계 목적에 불필요하므로 넣지 않았다("사용하지 않는
-- 컬럼은 추가하지 않는다" 원칙과 동일한 판단).
CREATE TABLE IF NOT EXISTS search_log (
    search_log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    keyword       VARCHAR(100)    NOT NULL,
    searched_at   DATETIME        NOT NULL,
    PRIMARY KEY (search_log_id),
    KEY idx_search_log_keyword_searched_at (keyword, searched_at)
);
