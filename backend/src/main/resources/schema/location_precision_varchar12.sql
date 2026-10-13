-- BAT-GEO-01(2026-10-12): complex·trade.location_precision VARCHAR(10) → VARCHAR(12) — 이미 테이블이 있는 DB용(재실행 안전).
-- 새로 만드는 DB는 schema_all.sql에 이미 VARCHAR(12)로 들어 있다.
--
-- 왜 필요한가: 지오코딩 키워드 검색 보완(단지명 장소 검색)으로 얻은 좌표는 location_precision='APPROXIMATE'(11자)로
-- 저장한다. 예전 VARCHAR(10)에서는 이 값이 들어가지 않는다. 이 프로젝트에는 자동 마이그레이션 도구가 없으므로, 이 커밋이
-- 들어간 백엔드로 지오코딩을 돌리기 전에 대상 DB마다 한 번 실행한다. trade 컬럼은 아직 쓰는 곳이 없지만 같은 값 체계라
-- 함께 넓힌다.
--
-- 실행: mysql -h <host> -P 3307 -u <user> -p homesense < backend/src/main/resources/schema/location_precision_varchar12.sql
-- 확인: SHOW COLUMNS FROM complex LIKE 'location_precision';  → varchar(12)
-- 길이만 늘리므로 기존 데이터는 바뀌지 않는다. 이미 12면 아무 변화 없이 끝난다.
ALTER TABLE complex MODIFY location_precision VARCHAR(12) NULL;
ALTER TABLE trade MODIFY location_precision VARCHAR(12) NULL;
