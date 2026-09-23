# 운영 반영 runbook — complex.legal_dong_cd 백필 · 성능 인덱스 13건

대상 브랜치: `feature/backend/search-region-keyword`
관련 프로그램: BAT-MAT-02(백필), API-CPX-01/SVC-CPX-01(regionCode 검색), 테이블정의서 v2.1 7.2절(인덱스)

두 작업은 서로 독립적이다. **순서는 인덱스 복구 → 백필**을 권장한다. `regionCode` 검색은
`complex.legal_dong_cd`의 FK 인덱스만 쓰므로 인덱스 복구 여부와 무관하게 동작한다. 다만 인덱스를 먼저
만들면 백필 검증 쿼리도 빨라진다.

**새 API 배포와의 순서:** 새 API(`regionCode` 검색)는 백필이 끝난 DB에서만 의미 있는 결과를 낸다.
`legal_dong_cd`가 NULL인 단지는 `regionCode` 검색에서 빠진다. 따라서 **백필을 먼저 끝내고 API를 배포**한다.
백필 러너는 새 버전 JAR에 들어 있으므로, 새 JAR로 백필만 먼저 실행하고 웹 서버는 기존 버전을 유지해도 된다.

---

## 0. 공통 준비

- 새 버전 JAR을 빌드한다: `cd backend && ./gradlew bootJar` → `backend/build/libs/<artifact>.jar`
- 러너는 운영 애플리케이션과 **같은 환경변수**로 실행한다. `prod` 프로필이 요구하는 값은 전부 필요하다
  (`DB_HOST`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `DATA_GO_KR_SERVICE_KEY`, SES 관련 값, Redis
  접속 정보 등). 기동 시 `@Validated` 설정 검증을 통과해야 하기 때문이다.
- 러너 프로필 `backfill-complex-legal-dong`은 다음 성질을 가진다
  (`application-backfill-complex-legal-dong.properties`).
  - 웹 서버를 띄우지 않는다(`spring.main.web-application-type=none`). 포트 충돌이 없다.
  - `@Scheduled` 배치(03:00 수집, 05:00 탈퇴 파기)를 등록하지 않는다.
  - 끝나면 종료 코드를 반환한다. 성공 0, 실패 1.

## 1. 사전 백업

```bash
# complex 테이블만 백업하면 된다(백필이 쓰는 테이블은 complex 하나뿐).
mysqldump -h "$DB_HOST" -P 3307 -u "$DB_USERNAME" -p --single-transaction homesense complex \
  > complex_before_legal_dong_backfill_$(date +%Y%m%d_%H%M).sql

# 사전 상태 기록
mysql -h "$DB_HOST" -P 3307 -u "$DB_USERNAME" -p homesense -e \
  "SELECT COUNT(*) total, COUNT(legal_dong_cd) filled FROM complex;"
```

## 2. 인덱스 복구 (13건)

`schema/indexes_v2_1.sql`은 `CREATE INDEX IF NOT EXISTS`라 다시 실행해도 안전하다.

```bash
mysql -h "$DB_HOST" -P 3307 -u "$DB_USERNAME" -p homesense \
  < backend/src/main/resources/schema/indexes_v2_1.sql
```

- `trade`(약 11만 행 규모)에 인덱스 4건이 생긴다. MariaDB 10.11은 대부분 온라인(ALGORITHM=INPLACE)으로
  만들지만, 트래픽이 적은 시간에 실행하는 것을 권장한다.
- 검증:

```sql
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) cols
FROM information_schema.statistics
WHERE table_schema = 'homesense' AND index_name IN (
  'idx_trade_complex_deal_date','idx_trade_legal_dong_deal_date','idx_trade_housing_deal_category',
  'idx_trade_deal_date','idx_complex_name','idx_complex_region','idx_complex_location',
  'idx_legal_district_region_name','idx_recent_view_user_viewed','idx_recent_view_session_viewed',
  'idx_notification_user_read_sent','idx_batch_log_started_at','idx_batch_log_success_started')
GROUP BY table_name, index_name;   -- 13행이어야 한다
```

## 3. 백필 dry-run (DB 쓰기 없음)

```bash
java -jar homesense.jar --spring.profiles.active=prod,backfill-complex-legal-dong
echo "exit=$?"   # 0이어야 한다
```

로그에서 다음을 확인한다(로컬 2026-09-23 기준값을 함께 적는다).

| 로그 | 로컬 기준값 | 판단 |
| --- | --- | --- |
| `strategy=COMBINED 채움 a/b` | 21680/21680 (100%) | 98% 미만이면 **중단**하고 미매칭 분포 로그를 보고한다 |
| `strategy=COMBINED 거래 대조 일치` | 17637/17637 (100%) | 99% 미만이면 **중단**. `거래 대조 불일치` 샘플 로그를 보고한다 |
| `NAME·PREFIX … 결과가 다른 단지` | 1건(complex_id 14826) | 조합 방식은 NAME을 우선하므로 정상 |

운영 DB의 complex 행 수나 법정동코드 버전이 로컬과 다르면 수치도 다를 수 있다. 판단 기준은 위 표의
"판단" 열이다.

## 4. 백필 apply

```bash
java -jar homesense.jar --spring.profiles.active=prod,backfill-complex-legal-dong --apply
echo "exit=$?"   # 0이어야 한다
```

- `legal_dong_cd IS NULL`인 행만 갱신한다. 중간에 실패해도 다시 실행하면 남은 행만 채운다(idempotent).
  500건 단위로 커밋한다.
- 로그 `[backfill apply] strategy=COMBINED 대상 N건, 매칭 N건, 실제 갱신 N건, 미매칭 0건`을 확인한다.
- 이어서 `캐시 비움: complexDetailV2 / popularComplexesV2 / regionAutocomplete` 3줄이 나와야 한다.
  `캐시 비우기 실패` WARN이 있으면 6단계를 수동으로 실행한다.

## 5. 검증 쿼리

```sql
-- (1) 채움
SELECT COUNT(*) total, COUNT(legal_dong_cd) filled FROM complex;       -- filled = total

-- (2) 코드 단위 분포. 모두 leaf(읍면동 8자리 + '00' 또는 리 10자리)여야 한다.
SELECT CASE WHEN RIGHT(legal_dong_cd,2)='00' THEN 'eupmyeondong' ELSE 'ri' END lvl, COUNT(*)
FROM complex GROUP BY lvl;                         -- 로컬: 읍면동 18,449 / 리 3,231

-- (3) 시군구 대표행/시도 대표행에 붙은 단지가 없어야 한다(0건)
SELECT COUNT(*) FROM complex WHERE legal_dong_cd LIKE '%00000';

-- (4) 폐지 코드에 붙은 단지가 없어야 한다(0건)
SELECT COUNT(*) FROM complex c JOIN legal_district_code l USING (legal_dong_cd) WHERE l.is_active = FALSE;

-- (5) 거래와의 교차검증: 단지 코드 ≠ 그 단지 거래의 최빈 코드인 단지 수(로컬 0건)
SELECT COUNT(*) FROM complex c
JOIN (SELECT complex_id, legal_dong_cd, ROW_NUMBER() OVER (PARTITION BY complex_id ORDER BY COUNT(*) DESC, legal_dong_cd) rn
      FROM trade WHERE complex_id IS NOT NULL AND legal_dong_cd IS NOT NULL GROUP BY complex_id, legal_dong_cd) m
  ON m.complex_id = c.complex_id AND m.rn = 1
WHERE m.legal_dong_cd <> c.legal_dong_cd;
```

## 6. 캐시 비우기 (4단계 로그에 WARN이 있었거나, 재확인이 필요할 때)

`legal_dong_cd`가 바뀌면 `ComplexDetailResponse.matchPending` 값이 달라진다. 지역 정보를 담는 캐시는
전부 비운다.

```bash
for c in complexDetailV2 popularComplexesV2 regionAutocomplete; do
  redis-cli -h "$REDIS_HOST" --scan --pattern "${c}::*" | xargs -r redis-cli -h "$REDIS_HOST" del
done
```

`popularKeywords`(검색어 문자열만 담음)는 비울 필요가 없다.

## 7. 롤백

- 백필: 1단계 백업으로 되돌리거나, 코드를 비우기만 하면 된다.
  `UPDATE complex SET legal_dong_cd = NULL;` 후 6단계 캐시 비우기. 되돌리면 `regionCode` 검색 결과가
  비므로 **새 API도 함께 이전 버전으로 되돌린다**.
- 인덱스: `DROP INDEX <name> ON <table>;`. 기능에는 영향이 없고 성능만 바뀐다.

## 8. 단지 마스터를 다시 적재한 뒤 (fix-forward 대체 절차)

단지 기본정보(K-apt xlsx) 적재는 **이 저장소 밖에서 수동으로** 한다(재현 가능한 적재기가 없다 —
CLAUDE.md "알려진 공백"). 새 단지를 넣거나 재적재한 뒤에는 **반드시 3~6단계(dry-run → apply → 검증 →
캐시)를 다시 실행**한다. 러너는 NULL인 행만 채우므로 기존 행은 건드리지 않는다.

재적재로 기존 단지의 **주소가 바뀐** 경우는 러너가 다시 계산하지 않는다(값이 이미 있음). 그 행의
`legal_dong_cd`를 NULL로 되돌린 뒤 러너를 실행한다.
