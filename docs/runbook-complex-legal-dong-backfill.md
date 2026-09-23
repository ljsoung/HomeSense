# 운영 반영 runbook — complex.legal_dong_cd 백필 · 성능 인덱스 13건

대상 브랜치: `feature/backend/search-region-keyword`
관련 프로그램: BAT-MAT-02(백필), API-CPX-01/SVC-CPX-01(regionCode 검색), API-SEARCH-01(검색 기록),
테이블정의서 v2.1 7.2절(인덱스)

## 운영 반영 순서 (반드시 이 순서로)

1. **백업**(1절)
2. **인덱스 13건 추가**(2절) — 배치가 돌지 않는 시간대에 실행한다
3. **새 버전 JAR로 백필**(3~6절) — non-web 러너라 운영 중인 앱(구 버전)을 그대로 둔 채 실행한다
4. **앱 교체** — 새 버전 JAR로 웹 애플리케이션을 재시작한다
5. **(그 다음에야) SRCH-01 프론트 배포**

이 순서인 이유는 다음과 같다.
- 새 API의 `regionCode` 검색은 `complex.legal_dong_cd`로만 거른다. 백필 전에 앱을 먼저 교체하면 지역 검색이 전부 0건이다.
- 새 API는 `regionCode`나 `keyword`가 없으면 400(`MISSING_SEARCH_CONDITION`)이다.
- 검색 기록은 `POST /api/search/logs`로 옮겨졌다.
- 따라서 구 계약에 맞춘 프론트가 새 앱을 부르거나, 새 프론트가 구 앱을 부르면 둘 다 깨진다.

### 선행 조건 — SRCH-01 프론트는 운영 백필과 앱 교체가 끝난 뒤 배포한다

**배포 경로(2026-09-23 확인):**
- **백엔드:** 저장소의 유일한 워크플로(`.github/workflows/ci.yml`)는 `main` push/PR에서 빌드·테스트만 한다.
  **배포 단계가 없으므로 `main`에 머지해도 백엔드는 자동 배포되지 않는다** — 위 1~4단계는 수동이다.
- **프론트엔드:** Vercel Production Branch가 **`develop`**이다(CLAUDE.md, 지성 확인 기록). 프론트 변경이
  `develop`에 머지되는 순간 hmss.site에 배포된다.

**그래서 이렇게 조정한다:**
- 이 백엔드 브랜치는 프론트 코드를 바꾸지 않으므로 `develop`에 먼저 머지해도 된다. 머지해도 Vercel이 다시
  빌드할 뿐 화면은 바뀌지 않는다.
- **SRCH-01 프론트 PR은 위 1~4단계가 운영에서 끝나고 5절 검증이 통과한 뒤에만 `develop`에 머지한다.**
  머지가 곧 배포이기 때문이다.
- Vercel 설정은 저장소 밖에 있어 이 runbook 작성 시점에 콘솔에서 다시 확인하지 않았다. 배포 직전에 Production
  Branch가 여전히 `develop`인지 확인하라.

**참고 — 백엔드가 아직 어디에도 배포된 적이 없다면**(CLAUDE.md 2026-09-21 기록 기준 로컬에서만 실행 중),
"운영 DB"는 첫 배포 때 새로 만들어진다. 그 경우에도 순서는 같다. 스키마(`schema_all.sql`, 인덱스 포함) →
단지·법정동코드 적재 → 백필 → 앱 기동 → 프론트.

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

`schema/indexes_v2_1.sql`은 13건 모두 `CREATE INDEX IF NOT EXISTS … ALGORITHM=INPLACE LOCK=NONE`이다.
- 다시 실행해도 안전하다.
- 테이블 복사나 쓰기 잠금 없이 만든다. 이 조건으로 만들 수 없으면 MariaDB가 잠금 방식으로 조용히 바꾸지 않고
  에러를 낸다. 그러니 에러가 나면 **그 자리에서 멈추고** 보고한다.

**실행 시간대 — 배치가 돌지 않을 때:**
- BAT-SCH-01 수집은 매일 03:00(KST)에 시작한다. 재시도 큐가 최대 180분 이어질 수 있어 06:00대까지 갈 수 있다.
- BAT-USR-01 파기는 05:00이다.
- **02:30~07:00을 피한다.**
- 실행 직전에 보조 확인을 한다:
  `SELECT COUNT(*) FROM batch_log WHERE started_at >= CURDATE() AND finished_at IS NULL;` → 0이어야 한다.
- **다만 이 쿼리만 믿지 않는다.** 재시도 큐(`RetryQueueManager`)는 백오프 대기 중에 `batch_log`를 남기지 않아,
  행이 전부 끝난 것처럼 보여도 재시도가 진행 중일 수 있다. 시간대 기준(02:30~07:00 회피)을 우선한다.

```bash
mysql -h "$DB_HOST" -P 3307 -u "$DB_USERNAME" -p homesense   < backend/src/main/resources/schema/indexes_v2_1.sql
```

- 소요 시간 참고(로컬): `trade` 204,520행에 2컬럼 인덱스를 온라인으로 만드는 데 0.27s였다.
- **FK 인덱스 흡수 — 알고 있어야 할 부수효과.** InnoDB는 FK용으로 자동 생성한 인덱스를, 그 FK를 대신할 수
  있는 인덱스가 새로 생기면 조용히 제거한다. 로컬에서는 다음 4개가 사라지고 새 인덱스가 FK를 떠맡았다.

  | 사라진 FK 인덱스 | 떠맡은 새 인덱스 |
  | --- | --- |
  | `fk_trade_complex` | `idx_trade_complex_deal_date` |
  | `fk_trade_legal_dong` | `idx_trade_legal_dong_deal_date` |
  | `fk_recent_view_user` | `idx_recent_view_user_viewed` |
  | `fk_notification_user` | `idx_notification_user_read_sent` |

  기능상 문제는 없다. 다만 롤백 방법이 달라진다(7절).
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
- 이어서 `캐시 비움: complexDetailV2 / popularComplexesV3 / regionAutocomplete` 3줄이 나와야 한다.
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
for c in complexDetailV2 popularComplexesV3 regionAutocomplete; do
  redis-cli -h "$REDIS_HOST" --scan --pattern "${c}::*" | xargs -r redis-cli -h "$REDIS_HOST" del
done
```

`popularKeywords`(검색어 문자열만 담음)는 비울 필요가 없다.

## 7. 롤백

- 백필: 1단계 백업으로 되돌리거나, 코드를 비우기만 하면 된다.
  `UPDATE complex SET legal_dong_cd = NULL;` 후 6단계 캐시 비우기. 되돌리면 `regionCode` 검색 결과가
  비므로 **새 API도 함께 이전 버전으로 되돌린다**.
- 캐시 이름 참고: 새 앱은 인기 단지를 `popularComplexesV3`에 담는다. 이전 앱이 쓰던 `popularComplexesV2`
  엔트리는 읽히지 않고 TTL(24h)로 자연 만료되므로 따로 지울 필요가 없다.
- 인덱스: `DROP INDEX <name> ON <table>;`로 되돌린다. 기능에는 영향이 없고 성능만 바뀐다. **단, FK를 떠맡은
  4개(2절 표)는 바로 지우면 `ERROR 1553 (needed in a foreign key constraint)`가 난다.** 먼저 단일 컬럼 인덱스를
  만든 뒤 지운다. 예:
  `CREATE INDEX fk_trade_complex ON trade (complex_id) ALGORITHM=INPLACE LOCK=NONE;`
  `DROP INDEX idx_trade_complex_deal_date ON trade;`
- 앱: 이전 버전 JAR로 재시작한다. 이전 버전 앱은 `legal_dong_cd`와 새 인덱스를 무시하므로, 백필·인덱스를
  그대로 둔 채 앱만 되돌려도 된다. 이 경우 SRCH-01 프론트도 함께 되돌려야 한다(계약이 다르다).

## 8. 단지 마스터를 다시 적재한 뒤 (fix-forward 대체 절차)

단지 기본정보(K-apt xlsx) 적재는 **이 저장소 밖에서 수동으로** 한다(재현 가능한 적재기가 없다 —
CLAUDE.md "알려진 공백"). 새 단지를 넣거나 재적재한 뒤에는 **반드시 3~6단계(dry-run → apply → 검증 →
캐시)를 다시 실행**한다. 러너는 NULL인 행만 채우므로 기존 행은 건드리지 않는다.

재적재로 기존 단지의 **주소가 바뀐** 경우는 러너가 다시 계산하지 않는다(값이 이미 있음). 그 행의
`legal_dong_cd`를 NULL로 되돌린 뒤 러너를 실행한다.
