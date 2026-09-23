-- 테이블정의서 v2.1 7.2절 성능 인덱스 13건 — 이미 테이블이 있는 DB용(재실행 안전).
-- 새로 만드는 DB는 schema_all.sql 끝에 같은 13건이 들어 있다.
-- 운영 적용 절차: docs/runbook-complex-legal-dong-backfill.md "인덱스 복구" 절.
CREATE INDEX IF NOT EXISTS idx_trade_complex_deal_date ON trade (complex_id, deal_date);
CREATE INDEX IF NOT EXISTS idx_trade_legal_dong_deal_date ON trade (legal_dong_cd, deal_date);
CREATE INDEX IF NOT EXISTS idx_trade_housing_deal_category ON trade (housing_type, deal_category);
CREATE INDEX IF NOT EXISTS idx_trade_deal_date ON trade (deal_date);
CREATE INDEX IF NOT EXISTS idx_complex_name ON complex (complex_name);
CREATE INDEX IF NOT EXISTS idx_complex_region ON complex (sido, sigungu, dong_ri);
CREATE INDEX IF NOT EXISTS idx_complex_location ON complex (latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_legal_district_region_name ON legal_district_code (sido_name, sigungu_name, eupmyeondong_name);
CREATE INDEX IF NOT EXISTS idx_recent_view_user_viewed ON recent_view (user_id, viewed_at);
CREATE INDEX IF NOT EXISTS idx_recent_view_session_viewed ON recent_view (session_id, viewed_at);
CREATE INDEX IF NOT EXISTS idx_notification_user_read_sent ON notification (user_id, is_read, sent_at);
CREATE INDEX IF NOT EXISTS idx_batch_log_started_at ON batch_log (started_at);
CREATE INDEX IF NOT EXISTS idx_batch_log_success_started ON batch_log (success_yn, started_at);
