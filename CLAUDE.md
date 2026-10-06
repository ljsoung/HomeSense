# CLAUDE.md

HomeSense 저장소에서 작업하는 Claude Code를 위한 가이드입니다. 코드를 작성하기 전에 이 문서 전체를 읽으세요.

> **복원 기록(2026-09-15).** 이 파일이 이전 세션 종료 후 디스크에서 사라진 것을 발견했다 — `.gitignore`에
> `CLAUDE.md`가 등록돼 있어 git 이력으로도 복구할 수 없는 상태였다. 이번 세션의 대화 컨텍스트에 남아있던
> 마지막 전체 스냅샷(7라운드 SCR-LEGAL-01 리뷰까지 반영된 버전)을 근거로 그대로 재구성했고, 사용자 요청에
> 따라 `.gitignore`의 `CLAUDE.md` 줄을 제거해 이제부터 git으로 추적·커밋한다 — 로컬 전용 문서라 유실
> 위험이 있었던 이전 방식보다 안전하다. 다음에 이 문서를 여는 세션은 유실 재발 여부를 git 이력으로 바로
> 확인할 수 있다.

## 프로젝트 개요

HomeSense는 국토교통부 실거래가 Open API를 배치로 수집·적재하고, 회원이 관심 매물·지역을 등록하면 시세 변동을 알림으로 받는 개인화 부동산 시세 조회 서비스입니다. 지성(솔로 개발자)의 포트폴리오 프로젝트로, 풀스택 개발·대용량 배치 처리·인증/개인화/캐싱/지도 아키텍처 역량을 증명하는 것이 목적입니다.

## ⚠️ 최우선 규칙 — MVP 범위

**MVP는 아파트·연립다세대만 다룹니다. 오피스텔과 단독·다가구는 명시적으로 범위 밖입니다.**

- `housing_type` 값은 코드 어디에서도 `'APT'`, `'VILLA'` 두 가지만 사용합니다. `'OFFICETEL'`, `'DETACHED'`는 절대 넣지 마세요(DB CHECK 제약이 실제로 막습니다).
- `match_method` 값은 `'EXACT'`, `'SIMILAR'` 두 가지만 사용합니다. `'AGGREGATED'`는 사용하지 않습니다.
- 오피스텔·단독다가구 관련 화면(DTL-02, DTL-03), API(`OfficetelController`, `DetachedHouseController`), 배치(`BAT-MAT-03`, `BAT-MAT-04`)는 **아직 만들지 않습니다.** 사용자가 명시적으로 확장을 요청하기 전까지 구현하지 마세요.
- 이 제약을 어디서 풀어야 하는지는 각 문서의 "향후 확장" 절에 정확히 문서화되어 있습니다(요구사항정의서 10장, 엔티티정의서 9장, 테이블정의서 10장, UI정의서 9장, 프로그램목록서 7장, 프로그램설계서 8장). 확장 작업을 요청받으면 해당 절부터 확인하세요 — 재설계가 아니라 재적용입니다.
- 이유: 아파트·연립다세대는 동일한 단지 마스터(`complex` 테이블)와 지번 기반 매칭 로직을 공유해 MVP로 묶기에 적합했고, 오피스텔(별도 마스터 없음)과 단독·다가구(개인정보 보호로 지번 일부만 공개)는 서로 다른 매칭 전략이 필요해 별도 확장 단계로 분리했습니다.

## 문서 체계 (Source of Truth)

아래 6개 문서가 이 프로젝트의 유일한 근거입니다. 코드와 문서가 어긋나면 문서가 맞고 코드가 틀린 것입니다. 프로젝트 지식에 모두 등록되어 있으니 검색해서 확인하세요.

| 문서 | 버전 | 이 문서에서 확인할 것 |
| --- | --- | --- |
| HomeSense 요구사항 정의서 | v3.0 | FR/NFR 원문, 기능 우선순위, 5단계 MVP 로드맵, 데이터 매칭 전략 |
| HomeSense 엔티티 정의서 | v2.0 | 논리 데이터 모델, 엔티티 관계, 업무 규칙 |
| HomeSense 테이블 정의서 | v2.0 | 물리 컬럼·타입·제약조건, CREATE TABLE DDL 원문, 인덱스 |
| HomeSense UI 정의서 | v2.0 | 화면별 레이아웃·이벤트·예외처리, 화면-API 매핑표, 반응형 정책 |
| HomeSense 프로그램 목록서 | v2.0 | 전체 프로그램 60종 목록, FR 추적 매트릭스 |
| HomeSense 프로그램 설계서 | v2.0 | Controller/Service 클래스·메서드 시그니처, 처리 로직, 예외 처리, 배치 파이프라인 |

**명세 참조 경로(2026-09-28, 지성 지시):** 프로그램 ID·명세 확인은 저장소 `docs/specs/`의 **최신본(v2.1)**으로 한다. Downloads 등 로컬 사본은 기준으로 쓰지 않는다 — 2026-09-28 커밋 태그를 확인할 때 Downloads에 프로그램목록서 v1.0만 있어 구버전으로 확인한 일이 있었다(그때의 `API-AUTH-01` = `POST /api/auth/refresh`는 프로그램설계서 v2.1.1 3.1절에서도 같아 태그는 바뀌지 않았다). **주의: 2026-09-28 기준 `docs/specs/`는 저장소 어느 브랜치에도 아직 없다.** 명세 최신본이 그 경로에 커밋되기 전까지는 확인에 쓴 문서 이름과 버전을 커밋 메시지나 CLAUDE.md에 함께 적는다.

**작업 유형별 우선 참조 문서:**
- API 엔드포인트/비즈니스 로직을 짤 때 → 프로그램 설계서 3~5장
- 배치 로직을 짤 때 → 프로그램 설계서 4장 + 6.3절(파이프라인 전체 흐름)
- 테이블/컬럼/제약을 확인할 때 → 테이블 정의서 (DDL 원문 8장)
- 화면 동작/API 응답 형태를 확인할 때 → UI 정의서 5장
- "이 기능이 왜 이렇게 설계됐는지" 궁금할 때 → 요구사항 정의서

## 백로그 (우선순위 순)

항목 대부분은 각 절에 "완결 필요"로 흩어져 있다. 여기에는 우선순위를 명시적으로 정한 항목만 순서대로 올린다.

1. **[처리완료 2026-09-29, `feature/frontend/auth-refresh-interceptor`] ~~사용 중 Access Token 만료 시 자동 재발급(401 인터셉터).~~** 구현과 판단 기록은 "401 자동 재발급과 요청 timeout" 절에 있다. 아래는 착수 전 요구사항 원문이다. 지금은 새로고침할 때(`restoreSession`)와 로그아웃할 때만 재발급한다. 화면을 쓰는 도중 Access Token(기본 30분)이 만료되면 인증이 필요한 요청이 401로 실패하고 자동으로 복구되지 않는다. `src/lib/httpClient.ts`에 401 → 재발급 → 원 요청 재시도 인터셉터를 붙인다. **구현 시 `features/auth/session.ts`의 Web Locks 기반 재발급 경로(`refreshAcrossTabs`)를 반드시 재사용한다** — 인터셉터가 `/api/auth/refresh`를 따로 호출하는 별도 경로를 만들면, 여러 탭(또는 같은 탭의 동시 요청 여러 개)이 같은 Refresh Token으로 재발급해 재사용 탐지로 세션 전체가 폐기되는 경합이 다시 생긴다(아래 "프론트엔드 세션 복원과 토큰 재발급 조율" 절, 2026-09-28 Codex P1). 동시에 401을 받은 요청들은 재발급 한 번을 기다렸다가 새 토큰으로 재시도해야 한다. **구현 시 다음 세 규칙을 모두 따른다 — 하나라도 빠지면 2026-09-28 PR에서 막은 경합이 다시 생긴다:** (1) 재발급은 `refreshAcrossTabs`(탭 간 Web Locks 락)를 거친다 — 빠지면 여러 탭·여러 요청이 같은 Refresh Token으로 재발급해 재사용 탐지로 세션 전체가 폐기된다. (2) 재발급 결과는 조건부로 저장한다 — 보낸 Refresh Token이 응답 시점에도 여전히 저장소 값일 때만 쓴다(`refreshTokens`의 compare-and-set). 빠지면 재발급 도중 다른 계정으로 로그인한 토큰을 늦게 온 재발급 결과가 덮어써 "화면은 B, 요청은 A"가 된다. (3) 인증 상태 반영은 `AuthProvider`의 세대 확인(`sessionGeneration`)을 거친다 — 빠지면 재발급 도중 사용자가 로그인·로그아웃으로 바꾼 세션을 늦게 끝난 재발급 결과가 되돌린다. **공용 `httpClient`의 기본 timeout도 이 작업에서 함께 도입한다(2026-09-29 추가).** 지금 `httpClient`에는 기본 timeout이 없다(axios 기본값은 무제한). 재발급(`REFRESH_TIMEOUT_MS` 10초)과 로그아웃(`LOGOUT_REQUEST_TIMEOUT_MS` 5초, 폐기 전체 상한 `LOGOUT_REVOKE_DEADLINE_MS` 5초)만 요청별 timeout이 있고, 검색·관심목록·최근 조회 등 나머지 요청은 응답이 멈추면 무기한 대기한다. 인터셉터의 재시도 흐름은 이 기본값을 전제로 설계한다. 원 요청 1회 + 재발급(락 대기 5초 + 10초) + 재시도 1회가 이어질 때 사용자가 기다리는 최악의 시간을 먼저 정하고, 그 안에 들어가게 기본값을 고른다. 개별 timeout과의 관계도 정리한다: 요청별 값이 기본값보다 우선하는지(axios는 요청 설정이 인스턴스 기본값을 덮는다), 재발급 10초·로그아웃 5초를 유지할지 기본값에 맞출지, 재시도 요청이 원 요청의 timeout·`signal`을 이어받는지. 관련 기록: COM-SEC-01/02 절의 "남은 과제", SCR-AUTH-01 결정 표의 "accessToken 자동 갱신 인터셉터" 행.
   - **[2026-10-03 등록] 로그아웃 대기 중 다른 탭이 다른 계정으로 로그인하면, 이 탭의 로그아웃 완료 시 그 토큰도 지워진다.** `AuthProvider.logout()`의 `finally`가 서버 폐기를 기다린 뒤 `tokenStorage.clearTokens()`로 공유 저장소를 무조건 비우기 때문이다 — 그사이 다른 탭이 B로 로그인했으면 B의 토큰이 사라져 B 탭도 다음 확인에서 비로그인이 된다(로그아웃 의도 후속 테스트 `logoutIntent.test.tsx`가 이 순서를 재현하며 토큰이 비는 것을 확인한다). **방향:** 보낸 토큰이 저장소 값과 같을 때만 지운다 — 재발급 경로의 조건부 삭제(`clearTokensIfUnchanged`/`clearTokensIfAccessUnchanged`, 재발급 락 안에서 비교·삭제)를 로그아웃에도 쓴다. 401 인터셉터 작업(위 1번)은 이미 끝났으므로 이 항목은 그 영역의 후속으로 따로 처리한다.
2. **[처리완료 2026-09-29, `feature/frontend/auth-status-tristate`] ~~인증 상태를 boolean 대신 상태 3종으로 소비하게 바꾼다.~~** 구현과 판단 기록은 "인증 상태 3종" 절에 있다. 아래는 착수 전 원문이다. `isAuthenticated`(boolean)가 "확인 중"과 "비로그인"을 모두 `false`로 표현하는 것이 2026-09-28 Codex P2 결함(확인 중 하트 클릭이 로그인 사용자를 로그인 화면으로 보냄)의 근본 원인이다. 인증 상태에 따라 동작을 결정하는 소비자(하트 토글, 최근 조회, 헤더, 관심 지역 카드 등)는 `'checking' | 'authenticated' | 'anonymous'` 상태를 직접 받게 바꿔, 확인 중을 비로그인으로 단정하는 실수를 타입 검사로 막는다(예: `switch`의 exhaustiveness 검사). 지금은 `authChecking` 플래그를 함께 보도록 규칙으로만 막고 있다(`authContext.ts`의 `isAuthenticated` 설명, 아래 "프론트엔드 세션 복원과 토큰 재발급 조율" 절).
3. **화면 크기별로 컴포넌트를 두 벌 렌더하고 CSS로 숨기는 패턴을 새 화면에서 쓰지 않는다.** 이 패턴(검색 결과의 `FilterPanel` 사이드바·바텀시트, 홈 추천 단지의 모바일·데스크톱 카드)이 id·name 중복(라디오 그룹 충돌)과 숨은 쪽 상태 문제를 반복해서 만들었다. **[규칙화 2026-09-29] "반응형 렌더 규칙" 절로 확정했다 — 숨긴 두 번째 트리(`display:none`)도 금지하고, 구조가 달라야 하면 훅 하나로 한 벌만 렌더한다. 기존 사용처는 "기술 부채" 절에 목록으로 남겼다.** ~~새 화면은 한 벌 렌더 + 반응형 레이아웃을 기본으로 하고, 두 벌이 불가피하면 숨긴 쪽을 `display:none`으로 두고 id·name이 겹치지 않게 한다(`useId` 등).~~
4. **Redis 장애 시 토큰을 실은 요청이 공개 API까지 실패한다 — 필터를 비로그인 강등으로 바꾼다(2026-09-29 등록, 별도 브랜치 `fix/backend/redis-outage-degradation`).** 지금 `JwtAuthenticationFilter`는 `UserStatusResolver`(`user:status`)와 `AccessTokenEpochService`(컷오프)를 읽다가 Redis 예외가 나면 그대로 전파한다. 그래서 Redis가 내려가면 토큰을 가진 사용자는 공개 조회 API까지 Redis 타임아웃(2초) 뒤 실패하는데, 이는 "비로그인 조회 전면 허용" 원칙과 충돌한다. 필터 예외는 `@RestControllerAdvice`를 거치지 않으므로 응답도 `ApiResponse` 형식이 아닌 기본 오류 응답일 가능성이 크다(실측 안 함). **방향:** 필터가 Redis 오류를 만나면 인증 정보를 채우지 않고 비로그인 요청으로 넘긴다(WARN 로그). 이렇게 하면 공개 조회는 계속 동작하고, 인증이 필요한 API는 Spring Security가 401을 반환해 fail-closed가 유지된다. 인증 자체가 사라지므로 컷오프가 무시되는 방향으로 뚫리지도 않는다. **범위 확인 사항:** (1) `@Cacheable` 캐시는 이미 처리돼 있다 — `CacheConfig.errorHandler()`가 `LoggingCacheErrorHandler`라 조회 실패는 미스로 보고 DB에서 읽고, 저장·삭제 실패는 로그만 남긴다(2026-09-27, "단지 검색 지역코드·키워드·거래유형" 절). 이 과제에서는 그 동작을 실제 Redis 장애로 함께 확인한다. (2) 캐시 추상화를 거치지 않고 `StringRedisTemplate`을 직접 쓰는 곳은 필터 경로 두 곳 외에 `LoginAttemptService`(로그인·재활성화), `PasswordResetTokenService`(비밀번호 재설정), `RegionCodePrefixResolver`(단지 검색 prefix 맵 버전 — 읽기 실패 시 보유한 맵을 그대로 쓴다고 문서화돼 있다)가 있다. 앞의 둘은 막는 쪽(fail-closed)으로 둔다(2026-09-29 판단): `LoginAttemptService`는 로그인을 통과시키면 장애 중 무차별 대입 방어가 사라지고, 필터를 고친 뒤에도 장애 중에는 로그인한 요청이 비로그인으로 처리되므로 로그인을 허용해도 사용자가 얻는 것이 없다. `PasswordResetTokenService`는 토큰을 저장할 수 없어 이미 막히는 쪽으로 동작하니, 사용자에게 보여 줄 오류 메시지만 정한다. (3) 응답 형식은 강등 후에는 401(`RestAuthenticationEntryPoint`, 표준 포맷)이 되므로 문제가 줄어들지만, 다른 필터 예외가 남는지 실측한다. **무효화 조건:** 인증 상태(회원 상태·컷오프)를 Redis 밖(DB 등)으로 옮기면 이 강등 판단을 다시 본다.
5. **`srch01-mobile-check` 간헐 실패 — "뒤로가기 후 누적 목록이 유지됨(30개 이상)" 단언(2026-09-30 등록).** 전체 e2e 실행 중 1회 실패했다. 같은 실행에서 스크롤 위치는 복원됐다(2721 → 2708). 단독 3회 재실행과 두 번째 전체 실행에서는 통과해 재현되지 않았다. 실패 시 카드 수를 출력하지 않아 몇 개였는지 모른다. **먼저 확인할 것:** 이 단언은 뒤로가기 뒤 첫 카드가 보이기를 기다린(`waitForSelector`) 다음 고정 300ms(`waitForTimeout(300)`)만 기다리고 카드 수를 센다. 누적 목록 복원이 그보다 늦게 끝나면 실패하는 테스트 경쟁 조건인지부터 본다. 그렇다면 고정 대기 대신 "카드 30개 이상"을 조건으로 기다리게 바꾸고, 실패 메시지에 실제 카드 수를 남긴다. 테스트 문제가 아니면 SRCH-01의 뒤로가기 복원 캐시(`scrollCache`, SCR-SRCH-01 절 "버그 발견·수정 1·2")를 본다. 비로그인 화면이라 인증 변경(탭 계정 동기화)과는 무관하다.
6. srch01-slider-boundary-and-wolse-check는 전체 실행 시 부하로 시간 초과가 날 수 있음(단독 통과). 재발 시 대기 조건 보강.
7. **[대부분 처리완료 2026-10-06, BAT-NTF-01 PR에 포함] MY-01 알림 표시를 `createdAt`·`title` 기준으로.** 처음엔 다음 프론트 브랜치로 미뤘으나, 코드리뷰(P1)가 "첫 배치가 알림을 만드는 순간 MY-01이 `formatRelativeTime(undefined)`의 `value.slice`에서 예외를 던져 마이페이지가 깨진다"고 지적해 같은 PR에 넣었다. 한 일: `NotificationPreview`를 `createdAt`·`title` 기준으로, `types.ts`에 `createdAt` 추가·`sentAt` optional, e2e 목업(`my01-mypage-check`·`my01-screenshots`) 갱신, vitest `NotificationPreview.test.tsx`(옛 코드로 되돌리면 정확히 그 예외로 실패). **남은 것:** (a) 알림 점 색을 매물(`complexId` 있음)/지역(`legalDongCd`만)으로 나눌지 결정, (b) 아직 없는 MY-04는 처음부터 `createdAt`·`title` 기준으로 만든다. 근거: "BAT-NTF-01 관심대상 조건평가·알림 생성" 절 D1·D7.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| 백엔드 | Java, Spring Boot |
| 프론트엔드 | React 19, TypeScript, Tailwind, Shadcn/ui |
| 인증 | Spring Security + JWT (Access/Refresh) |
| DB | MariaDB 10.11 |
| 캐시 | Redis (Spring Cache 추상화, `@Cacheable`/`@CacheEvict`) |
| 배치 | Spring Batch 또는 `@Scheduled` |
| 지도 | 카카오맵 REST API + JS SDK |
| 외부 API | 국토교통부 실거래가 Open API 4종 |
| 이메일 | AWS SES |

## 패키지 구조

도메인별 수직 패키지(Package by Feature) + 횡단 관심사는 `common`으로 분리합니다.

```
com.homesense
  ├─ auth           (로그인/회원가입/토큰)
  ├─ user           (회원정보)
  ├─ complex        (단지 검색/상세/지도)
  ├─ trade          (실거래 검색/이력/상세)
  ├─ region         (지역 자동완성/관심지역 요약)
  ├─ recentview     (최근 조회 이력)
  ├─ favorite       (관심 매물/지역)
  ├─ notification   (알림 설정/이력)
  ├─ search         (인기 검색어 — 신규 제안, API-SEARCH-01/SVC-SEARCH-01, 아래 절 참고)
  ├─ statistics     (유형 비교/가격추이 — 5단계 선택 범위)
  ├─ admin          (관리자 — 5단계 선택 범위)
  ├─ batch
  │   ├─ scheduler    (실행 제어 및 조합 순회)
  │   ├─ collector    (Open API 공통 수집기)
  │   ├─ parser       (XML 파싱 및 통합 스키마 매핑)
  │   ├─ matcher      (법정동 매핑, 단지 마스터 매칭)
  │   ├─ loader       (적재 및 중복방지)
  │   ├─ errorhandler (에러코드 판정 및 재시도)
  │   ├─ geocoding    (좌표 변환)
  │   └─ notifier     (조건평가 및 이메일 발송)
  └─ common
      ├─ security    (JWT 필터, 토큰 유틸리티)
      ├─ exception   (전역 예외처리기)
      ├─ cache       (Redis 캐시 설정)
      ├─ logging     (구조화 로깅)
      ├─ config      (외부연동 설정 관리)
      ├─ response    (공통 응답 래퍼)
      └─ validation  (공통 입력값 검증)
```

각 도메인 패키지는 `controller / service / repository / dto` 하위 패키지로 구성하고, **Controller → Service → Repository 3계층을 지킵니다.** Controller에 비즈니스 로직을 두지 마세요.

## 공통 아키텍처 규칙

### 응답 포맷
모든 API는 `ApiResponse<T>`로 감쌉니다.
```java
// 성공: { success: true, data, error: null, timestamp }
// 실패: { success: false, data: null, error: { code, message }, timestamp }
```
목록 조회는 `data`와 함께 `PageMeta(page, size, totalElements, totalPages)`를 포함합니다. `pageMeta` 필드는 목록 조회가 아닌 응답에서는 항상 null이고, `application.properties`의 `spring.jackson.default-property-inclusion=non_null` 설정 때문에 null 필드는 직렬화에서 아예 빠지므로 그 응답의 JSON에는 `pageMeta` 키 자체가 나타나지 않습니다. `PageMeta.from(Page<?>)`는 Spring Data `Page`가 쓰는 0-base `page` 번호를 그대로 노출합니다 — Controller가 요청 파라미터를 `Pageable`로 바꿀 때도 0-base로 맞추면 요청·응답이 일관됩니다. `ApiResponse.success(Page<T>)` 오버로드가 `data`(=`page.getContent()`)와 `pageMeta`를 한 번에 채워 줍니다.

**서버 응답의 nullable 필드는 생략될 수 있으므로 프론트 타입은 `?: T | null`, 검사는 `!= null`로 한다. e2e 목업은 공용 헬퍼(`frontend/e2e/mockApi.mjs`)로 null 키를 뺀다.** `non_null` 때문에 값이 없는 필드는 null이 아니라 키 자체가 없다(undefined). `!== null`·`=== null`로 검사하면 빠진 키가 통과해 "NaN만원"·"undefined층"이 그려진다(2026-10-05 수정, "서버가 뺀 null 필드 처리" 절).

**프로그램 설계서 여러 곳(CPX/TRD/NTF의 Controller 표 등)이 페이지네이션 응답 타입을 `PageResponse<T>`로 표기하지만, 이 클래스는 프로젝트 어디에도 존재하지 않고 앞으로도 도입하지 않습니다.** `ApiResponse.success(Page<T>)` 오버로드 하나로 이미 충분하기 때문입니다 — 새 페이지네이션 API를 짤 때 설계서의 `PageResponse<T>` 표기를 보고 그 이름의 DTO를 새로 만들지 마세요. 실제로 CPX(`ComplexController.search()`)와 TRD(`TradeController.search()`)는 이미 `ApiResponse<List<T>>`(`ApiResponse.success(Page<T>)` 재사용)로 구현돼 있고, NTF(`NotificationController.getNotifications()`)도 이를 그대로 따랐습니다(ADM/STT 도메인은 5단계 선택 범위라 아직 구현되지 않았습니다 — 그 도메인을 시작할 때도 이 관례를 그대로 적용하세요).

### 예외 처리
모든 도메인 예외는 `BusinessException(errorCode, message, HttpStatus)`을 상속합니다. 개별 `@ExceptionHandler`를 도메인마다 추가하지 말고, `common.exception`의 전역 `@RestControllerAdvice` 하나가 전부 처리하게 하세요. 새 예외를 추가할 때는 상속만 하면 됩니다.

**`ValidationExceptionHandler`라는 별도 클래스는 없습니다:** 프로그램 설계서는 COM-VAL-01의 클래스 설계에 `ValidationExceptionHandler`(MethodArgumentNotValidException 처리)를 별도로 정의하지만, 이 예외도 "도메인별 @ExceptionHandler를 추가하지 말라"는 위 COM-EXC-01 원칙의 적용 대상이라 실제로는 `GlobalExceptionHandler.handleMethodArgumentNotValid()`가 그대로 처리합니다(설계서는 이 메서드를 `handleValidationException`이라 부르지만 실제 이름은 스프링 관례를 따른 `handleMethodArgumentNotValid` — 기능은 동일). `@ValidPassword`/`@ValidNickname`(`common.validation`) 같은 COM-VAL-01의 커스텀 제약이 위반되면 `MethodArgumentNotValidException`이 던져지고, 그 어노테이션의 `message()`가 `ApiResponse.FieldError.message`로 그대로 노출됩니다 — 새 커스텀 제약을 추가할 때 별도 핸들러를 만들지 말고 이 경로를 그대로 타게 두세요(검증: `SignupPolicyValidationIntegrationTest`).

**`@ValidPassword`/`@ValidNickname`은 DTO에 단독으로만 쓰세요, `@NotBlank`를 함께 붙이지 마세요:** 두 검증기 모두 null/공백을 자체적으로 무효 처리하도록 만들어졌습니다(그래야 애노테이션 하나만으로 완결된 제약이 됩니다). `@NotBlank`를 같은 필드에 함께 붙이면 공백 입력 시 "must not be blank"와 이 애노테이션의 기본 메시지가 한 필드에 중복으로 실립니다(회귀 테스트: `PasswordValidatorTest`/`NicknameValidatorTest`의 `NotBlank와_함께_쓰면...` 케이스). SVC-AUTH-01(회원가입)·SVC-USER-01(회원정보 수정) DTO를 작성할 때 이 두 필드에는 `@NotBlank` 없이 커스텀 애노테이션만 붙이세요. 참고로 요구사항정의서 3.1절(signup 처리 로직)은 아직 "DTO 단 `@Pattern`"이라는 옛 표현으로 남아 있는데, 실제로 따라야 할 것은 프로그램 설계서 5.8절의 커스텀 애노테이션 방식(`@ValidPassword`/`@ValidNickname`)입니다 — 더 구체적인 검증 로직(길이+문자 조합 조건)을 표현할 수 있어 이쪽으로 구현했습니다.

**`Boolean` 필드는 `@NotNull`과 `@AssertTrue`를 함께 쓰세요:** `@AssertTrue`는 null을 통과시켜 필드 누락을 못 잡습니다(원시 `boolean`은 누락과 `false`를 구분하지 못하니 래퍼 `Boolean`). 두 제약은 서로 다른 경우(누락 vs `false`)에만 걸려 한 필드에 중복 에러가 실리지 않으므로 위 `@NotBlank` 중복 문제와 달리 함께 써도 되고, 커스텀 애노테이션은 만들지 않는다(`SignupRequest.ageConfirmed`, 검증: `AuthControllerTest`).

### 인증
- 모든 요청은 JWT 필터를 통과하되, **토큰이 없어도 요청을 차단하지 않습니다** — 비로그인 조회를 전면 허용하는 게 이 서비스의 원칙입니다.
- 인증이 실제로 필요한 엔드포인트(`POST /api/auth/logout`, `/api/users/**`(SVC-USER-01), 관심등록, 알림설정, 관리자)만 Spring Security 설정에서 별도로 인증을 강제합니다. `logout()`/USER 도메인 세 엔드포인트는 전부 `UserPrincipal me` 파라미터가 있어야 성립하는 연산이라 구현 시점에 순서대로 추가됐습니다 — 요구사항정의서 2.4절 원문은 이 목록에서 로그아웃과 마이페이지를 빠뜨리고 있으니(관심등록/알림설정/관리자만 예시로 듦) 문서 업데이트가 필요합니다.

**[처리완료 2026-09-22] 탈퇴/정지 직후에도 기존 Access Token이 한동안 유효하던 잔여 리스크 — COM-SEC-01 레벨 실시간 상태 체크로 해소.** 이전에는 `JwtAuthenticationFilter`가 매 요청 DB를 재조회하지 않고 토큰 클레임만 검증해, `/api/users/**`(SVC-USER-01의 getMe/updateMe/withdraw)를 포함한 모든 인증 경로가 계정이 방금 탈퇴·정지됐어도 만료 전(최대 `accessTokenValidity`, 기본 30분)까지 기존 Access Token으로 계속 호출 가능했다. FAV/NTF 도메인 구현을 트리거로 예고했던 대로 COM-SEC-01/02 레벨에서 한 번에 해결했다 — 자세한 구현 결정 사항은 바로 다음 "COM-SEC-01/02 실시간 회원 상태 체크" 절 참고.
- 관리자 엔드포인트는 인증 + `@PreAuthorize("hasRole('ADMIN')")` 이중 체크.
- 비밀번호는 반드시 BCrypt. 평문 저장/로깅 금지.
- 미인증 접근이 거부될 때는 Spring Security 기본 401 대신 `RestAuthenticationEntryPoint`(`common/security/`)가 COM-RES-01 표준 에러 포맷을 유지한다.

### COM-SEC-01/02 실시간 회원 상태 체크 (2026-09-22) — Redis `user:status:{userId}` 캐시

바로 위 항목이 예고했던 조치를 실행에 옮겼다. FAV/NTF 도메인 구현이 끝난 시점을 트리거로 개별 도메인
메서드에 상태 검사를 흩뿌리지 않고 COM-SEC-01(`JwtAuthenticationFilter`) 레벨에서 한 번에 처리했다.

| 항목 | 내용 |
| --- | --- |
| 신설 클래스 | `UserStatusCacheService`(`common.security`) — `StringRedisTemplate`만 사용(Repository 직접 의존 없음). `setStatus(userId, status)`/`getStatus(userId): Optional<UserStatus>`. **`UserStatusResolver`(`common.security`, 같은 날 P1 코드리뷰로 추가)** — `UserStatusCacheService`+`UserRepository`를 조합해 캐시미스 시 DB로 폴백한다(`isActive(userId): boolean`). `JwtAuthenticationFilter`는 이제 후자만 의존한다 — 상세는 아래 참고 |
| Redis 키 | `user:status:{userId}` |
| TTL | `JwtProperties.accessTokenValidity()`(기본 1,800,000ms=30분)를 그대로 참조 — Access Token 만료 시각과 정확히 동기화 |
| 쓰기 지점 | **[2026-09-22, 같은 날 두 번째 P1 코드리뷰로 변경]** `UserService.withdraw()` 성공 시 `WITHDRAWN`으로 SETEX하는 것 **하나뿐**이다. `AuthService.loginInternal()`(login/signup/reactivate 공유)과 `refreshAccessToken()`은 더 이상 `ACTIVE`를 캐시에 쓰지 않는다 — 아래 "ACTIVE 쓰기 지점 제거" 단락 참고. `ACTIVE` 캐시는 이제 오직 `UserStatusResolver`의 캐시미스 복구 경로에서만 채워진다. |
| 필터 동작 | `JwtAuthenticationFilter`가 `validateToken()`+`isAccessToken()` 통과 후 `UserStatusResolver.isActive(userId)`를 확인 — `false`면 SecurityContext 설정을 건너뛰고 필터 체인만 계속 진행한다(401을 직접 던지지 않음, 기존 만료 토큰 처리와 동일 패턴). |
| **무효화 조건** | `JwtProperties.accessTokenValidity` 값 자체가 바뀌면 `UserStatusCacheService`의 TTL도 같은 값을 참조하는 생성자 로직이므로 자동으로 함께 바뀐다 — 다만 TTL을 이 값과 별도로 관리하도록 리팩터링하면 그 즉시 동기화가 깨지니 재검토하라. |
| **완결 필요 — `SUSPENDED` 쓰기 지점 없음** | 이 캐시에 `SUSPENDED`를 실제로 쓰는 코드가 아직 없다 — ADM 도메인(5단계 선택 범위, `AdminService.updateUserStatus()`)이 구현되지 않았기 때문이다. ADM-01 구현 시 정지 처리 지점에서 `userStatusCacheService.setStatus(userId, SUSPENDED)`를 반드시 추가하라 — 이 항목이 빠지면 관리자가 계정을 정지시켜도 캐시가 TTL(최대 30분) 동안 옛 `ACTIVE` 값을 계속 반환해 정지가 즉시 반영되지 않는다. **이 쓰기는 아래 "ACTIVE 쓰기 지점 제거" 단락이 설명하는 레이스와 무관하게 안전하다** — WITHDRAWN과 같은 "더 제한적인 방향" 쓰기라, `AdminService`가 읽은 상태가 그 사이 낡아져도 최악의 경우 과잉 차단일 뿐이고 다음 요청에서 `UserStatusResolver`가 스스로 바로잡는다. |
| BAT-USR-01 파기(purge)와의 관계 | `WithdrawnUserPurgeService.purgeOne()`(사용자 행 물리 삭제)은 캐시를 evict하지 않는다 — 파기는 탈퇴 후 유예기간(기본 7일)이 지나야 실행되는데, 그 시점엔 탈퇴 시점에 SETEX한 `WITHDRAWN` 캐시가 TTL(최대 30분)로 이미 자연 만료된 지 오래라 별도 evict가 실질적 의미를 갖지 않는다 — 위 "BAT-USR-01" 절 체크리스트의 "purge에서도 키를 갱신/삭제" 항목은 이 분석에 따라 처리 불필요로 재확인됐다. |

**[처리완료 2026-09-22, 같은 날 코드리뷰 P1로 뒤집힘] "DB 폴백은 두지 않는다"는 최초 결정이 실제로는
전면 로그아웃 버그였다.** 최초 구현은 캐시미스를 곧바로 미인증으로 취급했고, "Redis가 비어 있으면
클라이언트가 401을 받고 `/api/auth/refresh`를 호출해 캐시가 다시 채워지는 자연 치유가 있다"는 전제로
이를 안전하다고 판단했다. **이 전제가 검증 없이 틀렸다** — 코드리뷰(Codex, P1)가 실제 프론트엔드
코드를 확인해 지적했다: `frontend/homesense/src/lib/httpClient.ts`는 Authorization 헤더만 붙일 뿐
401→refresh 인터셉터가 아예 없고(SCR-HOME-01 절의 "accessToken 자동 갱신 인터셉터" 행이 이미 "아직
추가하지 않음"으로 문서화해 둔 사실이었는데, 이 문서 반경 밖에서 별개로 새 기능을 설계하며 그 사실을
다시 확인하지 않았다), `AuthProvider.tsx`도 `getMe()` 실패를 조용히 무시한다. 즉 Redis 재시작·evict
하나만으로 이미 로그인한 모든 사용자가 프론트 재배포나 수동 재로그인 없이는 복구되지 않는 전면
로그아웃을 겪을 수 있었다 — 문서화된 "자연 치유"는 이 세션이 검증 없이 지어낸 전제였다.

**수정: `UserStatusResolver`(신규, `common.security`) 도입 — 캐시미스일 때만 DB로 폴백한다.**
`JwtAuthenticationFilter`는 이제 `UserStatusCacheService`를 직접 보지 않고 이 리졸버 하나만 의존한다.
`isActive(userId)`는 캐시 히트면 그 값을 그대로 쓰고(정상 경로, DB 미접근 — 성능 영향 없음), 캐시미스면
`UserRepository.findById()`로 DB에서 상태를 읽어 반환하면서 캐시도 그 값으로 다시 채운다. DB에도 없는
경우(BAT-USR-01이 이미 파기한 계정)만 그대로 미인증 처리한다 — 이건 캐시 문제가 아니라 실제로 더 이상
존재하지 않는 계정이므로 옳은 동작이다. `UserRepository`(user 도메인)를 `common.security`가 직접
참조하는 새 의존 방향이 생겼지만, `common.config.BatchSchedulerProperties → trade.entity.HousingType`
선례와 같은 성격(공통 계층이 도메인의 leaf 객체를 참조 — 그 반대 방향 의존은 없음)이라 순환 의존은
생기지 않는다. 이 리졸버를 `user.service`에 두는 대안도 검토했으나, 그러면
`common.security(JwtAuthenticationFilter) → user.service(UserStatusResolver) → common.security
(UserStatusCacheService)`로 패키지 순환이 생겨(FAV/RGN 도메인이 이미 겪은 것과 같은 종류의 문제,
CLAUDE.md SVC-FAV-01 절의 `getFavoriteRegions()` 항목 참고) 기각했다.

**[처리완료 2026-09-22, 같은 날 세 번째 P1 코드리뷰] `AuthService`의 ACTIVE 쓰기 자체를 제거 —
"캐시미스 복구"와는 다른 종류의 버그였다.** 위 `UserStatusResolver` 도입으로 캐시미스 문제는
해결됐지만, `AuthService.loginInternal()`/`refreshAccessToken()`이 성공 시 `userStatusCacheService.
setStatus(userId, ACTIVE)`를 무조건 실행하는 코드는 그대로 남아 있었다 — 코드리뷰(Codex, P1)가
정확히 이 지점을 지적했다: **`refreshAccessToken()`이 읽는 `user.getStatus()`는 그 메서드가 시작될
때(정확히는 `findByTokenValue()`가 REPEATABLE READ 스냅샷을 여는 시점)의 값인데, 그 값이 ACTIVE라고
확인한 뒤 실제로 Redis에 쓰기까지 실행되는 사이(GC 정지·스레드 스케줄링 지연 등으로 임의로 길어질 수
있는 창)에 다른 트랜잭션이 같은 사용자를 탈퇴시켜 캐시에 WITHDRAWN을 먼저 써 놓았다면, 뒤늦게 재개된
이 메서드가 그 위에 ACTIVE를 다시 덮어써 버린다.** 그 결과 새로 발급된 Access Token은 만료 전까지
(최대 30분) 필터를 그대로 통과해, 이 기능 전체가 막으려던 "탈퇴 직후에도 기존 토큰이 통용되는" 바로
그 문제를 재현한다 — `loginInternal()`(login/signup/reactivate 공유)도 같은 모양의 코드라 동일한
결함을 안고 있었다.

**수정: 두 메서드 모두에서 `ACTIVE` 캐시 쓰기를 완전히 제거했다** — 버전 관리나 Lua 스크립트 같은
동시성 장치를 추가하는 대신,애초에 "읽은 뒤 나중에 쓰는" 이 패턴 자체를 없앴다. 새로 발급된 토큰으로
오는 바로 다음 인증 요청은 캐시가 비어 있을 것이므로 `UserStatusResolver`가 그 시점에 DB를 다시 읽어
캐시를 채운다. `UserService.withdraw()`의 `WITHDRAWN` 쓰기는 그대로 두었다 — **"차단은 즉시, 해제는
다음 확인 때"라는 의도적 비대칭이 이 설계의 핵심이다.** 더 제한적인 상태(WITHDRAWN)를 먼저 반영해도
최악의 경우 과잉 차단인데, 이는 다음 정상 요청에서 `UserStatusResolver`가 최신 값을 다시 읽으며 스스로
바로잡힌다 — 반대로 더 허용적인 상태(ACTIVE)를 먼저 반영하면 과소 차단(보안 구멍)이 되고, 그 구멍은
캐시 TTL(최대 30분) 동안 자연히 닫히지 않는다. 이 비대칭 때문에 ADM 도메인이 나중에 `SUSPENDED`를
쓰게 되더라도(위 "완결 필요" 행) 그 쓰기는 안전하다 — WITHDRAWN과 마찬가지로 "더 제한적인" 방향이기
때문이다.

**[처리완료 2026-09-22, 네 번째 P1 코드리뷰] 위 문단이 "창이 사실상 없다"고 썼던 것 자체가 틀린
확률적 근거였다 — `UserStatusResolver`의 복구용 쓰기도 무조건 덮어쓰기(SET)였다는 점에서 방금 고친
버그와 구조가 완전히 같았다.** 지성이 직접 지적했다: `UserStatusResolver.refreshFromDatabase()`도
결국 "DB 읽기 → 그 값으로 캐시 SET"이고, 이 SET이 무조건 덮어쓰기라면 창이 좁아졌을 뿐 같은 모양의
race가 그대로 남는다 — T1에 resolver가 DB에서 ACTIVE를 읽고(withdraw 커밋 전), T2에 동시 실행된
withdraw()가 DB+캐시에 WITHDRAWN을 먼저 반영하고, T3(T2보다 늦게, GC 정지 등으로 지연된 뒤)에
resolver가 뒤늦게 캐시에 ACTIVE를 써 WITHDRAWN을 도로 덮어쓸 수 있다. "실제로 이 창이 훨씬 좁다(DB
조회 한 번+캐시 쓰기 한 번)"는 사실이 race 자체를 없애지 않는다 — 위에서 이미 "GC 정지·스레드
스케줄링 지연이 plausible하다"고 스스로 인정해 놓고, 그 전제를 login/refresh 경로에만 국한할 근거가
없었다. 확률로 완화된 취약점을 "낮은 확률"이라는 이유로 남겨두는 것은 방금 P1으로 잡은 것과 본질적으로
같은 종류의 결함이다.

**수정: `UserStatusCacheService`에 `setIfAbsent`(Redis SETNX)를 신설하고, `UserStatusResolver`의
복구용 쓰기를 이걸로 교체했다 — 무조건 덮어쓰기(`setStatus`)는 이제 `UserService.withdraw()`처럼
"그 순간 DB의 최신 상태를 직접 확정한 쓰기"에만 쓴다.** 이렇게 하면 순서와 무관하게 항상 옳은 결과가
나온다: withdraw()의 WITHDRAWN이 먼저 도착하면 resolver의 SETNX는 키가 이미 있어 no-op(WITHDRAWN
보존), resolver가 먼저 도착해도 이후 withdraw()의 무조건 쓰기가 그 위에 WITHDRAWN을 그대로 덮어쓴다
(WITHDRAWN 보존) — "더 제한적인 값이 이긴다"는 원칙이 확률적 근사치가 아니라 실제 불변식이 된다.
이 resolver 자신의 이번 요청 인가 판단(`isActive()`의 반환값)은 SETNX의 성패와 무관하게 자신이 실제로
읽은 DB 값을 그대로 쓴다 — 이미 시작된 이 요청 하나의 판단을 소급 취소할 방법은 없고, 이 수정이
보장하는 것은 "공유 캐시가 오염되지 않아 이후의 모든 요청은 정확히 판단한다"는 것이다(이 값 하나의
staleness는 DB만으로 인가하는 어떤 시스템에도 존재하는 환원 불가능한 최소 창이다). 이 프로젝트가 이미
한 번 쓴 패턴과 같은 방향이다 — BAT-LOD-01의 `dedup_hash` upsert race도 처음엔 `REQUIRES_NEW` 게이트웨이로
우회하려다 결국 원자적 `INSERT ... ON DUPLICATE KEY UPDATE`로 바꿔 타이밍 의존성 자체를 없앴다(위
"`REQUIRES_NEW` 격리 INSERT 게이트웨이 패턴" 절 참고) — 이번에도 "확률적 완화"에서 "원자적 연산으로
구조적 제거"로 한 단계 더 간 것이다.

**완결 필요(우선순위 낮음, 이번에 발견했으나 지금 고치지 않음) — 탈퇴 직후 짧은 시간 안에
재활성화(reactivate)하면 낡은 WITHDRAWN 캐시 때문에 최대 TTL(30분)만큼 오히려 잠길 수 있다.**
`reactivate()`는 (위 두 수정 이후) 성공해도 `ACTIVE`를 캐시에 쓰지 않는다 — 오직 `UserStatusResolver`의
캐시미스 경로만 채운다. 그런데 `withdraw()`가 남긴 `WITHDRAWN` 캐시 엔트리가 아직 TTL(최대 30분)
안에 있다면, reactivate() 성공 직후의 인증 요청은 **캐시 히트**(WITHDRAWN)라 `UserStatusResolver`가
DB를 다시 확인하지 않고 그대로 차단한다 — 방금 도입한 `setIfAbsent`는 이 경로에 전혀 관여하지 않는다
(캐시 미스가 아니라 히트이기 때문). 즉 탈퇴 후 30분 안에 재활성화하면 그 사용자는 자기 계정을 최대
30분 더 못 쓸 수 있다.

**지금 당장 고치지 않는 더 정확한 이유(지성 정정) — "프론트가 안 불러서 안전"이 아니라 "이 엔드포인트
자체가 아직 문서화된 MVP API 표면 밖에 있다".** `POST /api/auth/reactivate`는 프로그램설계서 3.1절
`AuthController`의 문서화된 시그니처(signup/login/refresh/logout/check-email)에도, UI정의서 화면
목록에도 없다 — 위 "BAT-USR-01 / SVC-AUTH-01.reactivate()" 절 제목 자체가 이미 "신규 제안 —
프로그램목록서·설계서 미반영"이라고 명시하고 있다. 프론트 화면이 없다는 사실은 이 부재의 *결과*일
뿐이지 안전성의 *근거*는 아니다 — 더 튼튼한 근거는 이 엔드포인트가 애초에 노출을 의도한 스펙 밖에
있다는 사실 자체다. **참고(향후 확장 시) — 정지(SUSPENDED)에서 복구하는 경로가 필요해지면, 본인이
호출하는 `/api/auth/reactivate`보다는 5단계 ADM 도메인의 `PATCH /api/admin/users`(관리자 전용)가
문서상 더 자연스러운 자리일 수 있다** — ADM-01을 구현하며 이 엔드포인트를 실제로 연결할 때 한 번
검토하라.

**고칠 때의 방향(정정) — CAS/버전 관리가 아니라 evict-after-commit 리스너 하나로 충분하다.** 이전
버전은 "eager write를 유지하되 무엇을 쓸지"로 문제를 좁혀 CAS(버전/타임스탬프 비교)가 필요하다고
결론 냈는데, 이는 과한 처방이었다(지성 지적). `reactivate()`가 커밋 후 `ACTIVE`를 **쓰는** 대신 그
사용자의 캐시 키를 **evict**하기만 하면 CAS 없이도 구조적으로 안전하다 — evict는 "특정 값을 미리
써서 다른 쓰기와 경합하는" 방식이 아니라 "판단을 다시 진실의 원천(DB)에 위임하는" 방식이라, 이번에
고친 것과 같은 클래스의 race가 애초에 발생할 지점이 없다. 다음 요청은 무조건 캐시미스가 되고,
`UserStatusResolver`가 DB를 다시 읽어 `setIfAbsent`로 안전하게 채운다(위에서 이미 고친 경로를 그대로
재사용). 이 프로젝트가 이미 문서화한 **evict-after-commit 패턴**
(`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`, 위 "캐싱" 절의
`CacheEvictionListener`와 같은 모양 — 커밋과 캐시 무효화 사이에 stale 재채움이 끼어들 여지를 없애려고
AFTER_COMMIT에 건다)을 `withdraw()`가 아니라 `reactivate()` 쪽에 리스너 하나 추가하는 정도로 충분하다.
프론트 재활성화(철회) 화면이 실제로 만들어지고 이 엔드포인트가 문서화된 API 표면에 들어오는 시점에
이 항목부터 재검토하라.

**[처리완료 2026-09-29] ~~남은 과제 — 프론트 401→refresh 인터셉터 자체는 여전히 없다.~~** "401 자동 재발급과 요청 timeout" 절에서 구현했다. 아래는 당시 기록이다. 이 절의
세 수정 모두 백엔드가 스스로를 안전하게 지키도록 만든 것뿐이다 — 진짜 만료된 Access Token(캐시가
아니라 JWT 자체의 exp 클레임 만료)에 대해서는 여전히 프론트가 401을 받고도 자동으로 `/api/auth/
refresh`를 호출해 재시도하지 않는다(SCR-HOME-01 절이 이미 이 갭을 "보호된 라우트가 실제로 생기는
시점에 추가"로 유예해 뒀다). 이 갭은 이번 세 수정으로도 닫히지 않았다 — 인터셉터를 실제로 붙일 때
이 항목부터 다시 확인하라.

검증: `UserStatusCacheServiceTest`(키·TTL·직렬화, `setIfAbsent`의 성공/실패 양쪽 반환값), `UserStatusResolverTest`
(캐시 히트 시 DB 미조회, 캐시미스+DB에 ACTIVE/비ACTIVE 각각을 `setIfAbsent`로 재기록, 캐시미스+DB에도
없음, **`setIfAbsent`가 경쟁에서 져도 이 요청 자신의 인가 판단은 자신이 읽은 DB 값을 그대로 반환하고
무조건 쓰기(`setStatus`)는 절대 호출하지 않는다**), `JwtAuthenticationFilterTest`(리졸버가 반환하는
boolean만으로 SecurityContext 설정 여부 결정), `UserServiceTest`(withdraw 성공 경로에서 캐시 쓰기,
실패 경로에서 미호출 검증). `AuthServiceTest`는 이제 `UserStatusCacheService`를 전혀 의존하지 않는다
— ACTIVE 쓰기가 존재하지 않는다는 사실 자체가 (그 의존성을 제거한) 생성자 시그니처로 증명된다, 별도
`verify(..., never())`가 필요 없다. Redis 연결은 `LoginAttemptService`가 이미
요구하던 인프라라 이 변경으로 새로 추가된 테스트 인프라 요구사항은 없다 — **2026-09-22, Docker가
가동 중인 세션에서 `./gradlew integrationTest`로 실제 실행해 확인했다**(13개 MariaDB IT 클래스, 57
테스트 전부 그린, `UserStatusResolver` 리팩터링 이후에도 그린 유지 재확인 — `AuthService`/
`UserService`가 실제 `UserStatusCacheService`+Redis와 함께 조립된 전체 Spring 컨텍스트로 도는 것까지
검증됨, 그중 `AuthServiceMariaDbIT`/`AuthServiceReactivateMariaDbIT`/`UserServiceMariaDbIT`가 이 변경이
건드린 두 서비스를 직접 실행한다).

### SVC-AUTH-01 구현 결정 사항
프로그램 설계서 3.1절이 상세히 기술하지 않았거나 미확정으로 남겨둔 세부 사항을 구현 시점에 확정한 내용이다. 설계서 자체를 아직 갱신하지 못했으니, 설계서를 다시 볼 때는 아래 표를 함께 참고하고, 가능하면 설계서 쪽에도 반영하라.

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| 로그인 실패 잠금(5회/5분) | "신규 제안, 반영 전 검토 필요"로 미확정 표시 | 사용자 확인 후 구현 확정. Redis 키(`login:fail:{email}`)의 TTL을 **실패마다 5분으로 다시 건다** — 첫 실패 시점 고정 만료가 아니라 마지막 실패로부터 5분 뒤 잠금이 풀리는 슬라이딩 윈도우 | 설계서 문구("약 5분 TTL로 잠금")가 고정/슬라이딩 여부를 명시하지 않아, 마지막 시도 기준으로 5분을 보장하는 쪽이 사용자에게 더 예측 가능하다고 판단해 슬라이딩으로 결정(`LoginAttemptService.recordFailure()`) |
| `logout()` 소유자 검증 | 설계서 3.1절에 세부 로직 없음(시그니처만 `logout(Long userId, String refreshTokenValue)`) | 조회된 Refresh Token의 소유자(`user_id`)가 인자로 받은 `userId`와 다르면 `InvalidRefreshTokenException` | 시그니처가 굳이 `userId`를 받는 이유가 이 검증 외엔 없고, FAV 도메인 등 다른 프로그램의 "소유자 검증 후 삭제" 패턴과 일관됨(`AuthService.logout()`) |

### SVC-AUTH-01 Refresh Token Rotation + 재사용 탐지 (2026-09-22)

**배경.** 프로그램설계서 3.1절이 `refreshAccessToken()`을 "신규 Access Token만 발급한다(Refresh Token은
재사용 — Rotation 미적용, NFR-4 참고)"로 명시하고 있었다 — 즉 탈취된 Refresh Token은 만료(14일)까지
무제한 재사용이 가능했다. 이 절이 그 갭을 closed 상태로 옮긴다: Rotation(재발급마다 기존 토큰 폐기 +
새 토큰 발급) + 재사용 탐지(이미 폐기된 토큰으로 재발급이 시도되면 탈취 신호로 보고 해당 사용자의
Refresh Token을 전부 폐기)를 구현했다. **설계서 3.1절 문구 자체는 이 세션에서 고치지 않았다** —
사용자 지시에 따라 문서 원본(claude.ai Project Knowledge) 반영은 별도 claude.ai 세션에서 진행하고,
여기서는 코드와 이 결정 로그만 남긴다.

**최종 구조 — 세 개의 새 클래스로 나뉜 이유가 단순한 리팩터링 취향이 아니라 세 가지 실제 버그를
순서대로 잡아가며 굳어진 결과다(아래 "겪은 문제" 참고).**

| 클래스 | 역할 | 트랜잭션 |
| --- | --- | --- |
| `RefreshTokenRotator`(신규, `auth.service`) | 검증(형식·만료·계정 상태) + 원자적 회전 시도. 결과를 `RefreshRotationResult`(sealed interface: `Rotated`/`ReuseDetected`)로 반환한다 | `@Transactional`(REQUIRED, 자기완결) |
| `AuthService.refreshAccessToken()` | `RefreshTokenRotator`를 부르고 결과에 따라 분기하는 얇은 오케스트레이터. 실제 DB 작업을 전혀 하지 않는다 | `@Transactional(propagation = NOT_SUPPORTED)` — 의도적으로 트랜잭션을 열지 않는다 |
| `RefreshTokenReuseHandler`(신규, `auth.service`) | 재사용 탐지 시 해당 사용자의 Refresh Token 전부 폐기(`RefreshTokenRepository.revokeAllByUserId()` 재사용) + `AuditLogger.logRefreshTokenReuseDetected()` | `@Transactional(propagation = REQUIRES_NEW)` |

`RefreshTokenRepository.revokeIfUnrevoked(refreshTokenId)`(신규, 조건부 UPDATE `WHERE refresh_token_id=:id
AND revoked_yn=false`, affected rows로 판정)가 "이미 폐기됨"의 판정과 폐기 자체를 원자적으로 묶는다 —
`stored.isRevoked()`로 먼저 읽고 나서 revoke하는 TOCTOU 패턴을 쓰지 않는다(`UserRepository.
reactivateIfWithinGrace`와 같은 "affected rows로 경합 판정" 패턴). `RefreshToken` 엔티티에 `isRevoked()`/
`isExpired()`를 신설했다(기존 `isUsable()`은 `!isRevoked() && !isExpired()`로 재정의 — 하위 호환).
`TokenResponse`에 `refreshToken` 필드를 추가해 `LoginResponse`와 필드 순서(`accessToken, refreshToken,
expiresIn`)를 통일했다.

**만료 vs 폐기를 서비스 레이어에서 명확히 분기한다(요구사항 3번).** `stored.isExpired()`는
`revokeIfUnrevoked()`를 시도하지도 않고 곧바로 `InvalidRefreshTokenException`을 던진다(재사용 탐지
미발동) — 평범한 재로그인 유도 상황이다. 반면 `revokeIfUnrevoked()`가 0을 반환하면(이미 `revoked_yn=
true`) `ReuseDetected`로 이어진다. 두 경로 모두 응답은 같은 401(`InvalidRefreshTokenException`)로
통일해 공격자에게 탐지 사실을 드러내지 않는다(요구사항 2번 "재로그인 유도, 문구 구분 안 함") — 실제
구분은 `AuditLogger.logRefreshTokenReuseDetected(userId)`(신규, COM-LOG-01)에만 남는다.

**겪은 문제 세 가지 — 전부 `AuthServiceRefreshRotationMariaDbIT`(신규, Testcontainers, 동시 두 요청이
같은 토큰으로 경쟁)로만 발견됐다. Mockito 단위 테스트(`RefreshTokenRotatorTest`)는 이 중 어느 것도
잡지 못했을 것이다 — 셋 다 "여러 트랜잭션이 실제로 겹칠 때"만 드러나는 종류다.**

1. **JWT NumericDate 초 단위 절삭 — [정정, 2026-09-22 같은 날 P2 코드리뷰] 처음엔 "테스트 아티팩트,
   프로덕션 버그 아님"으로 잘못 결론 냈다. 실제로는 진짜 프로덕션 버그였다.** IT가 시드한 "구" 토큰과
   회전으로 발급되는 "신" 토큰이 `token_value` UNIQUE 제약을 위반했다 — 원인을 처음엔 밀리초 단위
   시계 해상도로 추측해 10ms→100ms로 늘려봤지만 둘 다 불충분했다. 실제 원인은 JWT의 `iat`/`exp`
   클레임(RFC 7519 NumericDate)이 **초 단위**로 잘린다는 사실 — 같은 사용자에 대해 같은 초 안에 두 번
   서명하면 헤더+페이로드+서명까지 완전히 동일한 토큰 문자열이 나온다. 최초엔 IT에 1.1초 sleep을
   넣어 이 창을 피해 가고 "실제 운영에서는 로그인과 재발급 사이에 최소 수 초~수 분이 지나 이 충돌이
   발생하지 않는다"고 결론 냈는데, **이 결론이 틀렸다** — 클라이언트 재시도나 여러 탭에서 짧은 시간
   안에 순차적으로 두 번 재발급을 요청하는 것은 실제로 충분히 일어날 수 있는 시나리오라, "IT가 만든
   비현실적 타이밍"으로 좁혀서 본 것 자체가 근거 없는 가정이었다. `JwtTokenProvider`에 `jti`(무작위
   UUID)를 추가해 구조적으로 제거했다 — 아래 "COM-SEC-02 JwtTokenProvider" 절 참고, IT의 sleep도
   제거했다.
2. **REPEATABLE READ phantom row — `REQUIRES_NEW`만으로는 승자(winner)의 새 토큰이 재사용 탐지의
   전체 폐기를 피해 간다.** 패자(loser)의 트랜잭션은 winner가 커밋하기 훨씬 전에 이미 스냅샷을 열어
   뒀다 — `revokeIfUnrevoked()`가 그 스냅샷을 우회해 "현재" 값을 보는 건 그 UPDATE가 winner가 잠근
   바로 그 행에서 잠금 대기 후 재확인하기 때문이지, loser 트랜잭션 전체가 최신 상태를 보게 되는 게
   아니다. `REQUIRES_NEW`(당시엔 `RefreshTokenReuseHandler` 하나만 있었고 `RefreshTokenRotator`는
   아직 분리 전이었다)로 새 트랜잭션을 열면 이 phantom 문제는 해결됐다 — 그 새 트랜잭션은 winner의
   커밋 이후 시작하는 새 스냅샷에서 출발하기 때문이다. IT의 "`revoked_yn=FALSE` 건수는 0이어야 한다"
   단언이 이 버그를 잡았다(고치기 전엔 1이 나왔다 — winner의 새 토큰이 살아남았다는 뜻).
3. **자기 교착(self-deadlock) — (2)의 수정만으로는 IT가 여전히 타임아웃으로 실패했다.**
   `revokeIfUnrevoked()`가 0건을 갱신했더라도(조건절이 안 맞아서) InnoDB는 WHERE절을 평가하려 그 행을
   조회하는 과정에서 이미 배타 락을 걸어 둔다 — 이 락은 loser의 바깥쪽 트랜잭션이 끝날 때까지 풀리지
   않는다. 그 트랜잭션이 열려 있는 채로 `REQUIRES_NEW`로 `RefreshTokenReuseHandler`를 부르면, 새
   트랜잭션의 `revokeAllByUserId()`가 정확히 그 같은 행을 다시 잠그려다 자기 자신(같은 애플리케이션
   스레드, 다른 DB 커넥션)과 교착한다 — 두 트랜잭션 다 "락을 기다리는 중"이지 "다른 락을 요청하며
   대기 중"이 아니라서 InnoDB의 데드락 탐지기가 이 사이클을 못 잡고, `innodb_lock_wait_timeout`(기본
   50초)까지 그냥 멈춘다. `REQUIRES_NEW` 자체로는 풀 수 없는 문제였다 — 호출자가 이 메서드를 부르기
   *전에* 자신의 트랜잭션을 완전히 끝내야 한다는 게 결론이었고, 이것이 위 표의 최종 3-클래스 구조(
   `RefreshTokenRotator`를 별도 자기완결 트랜잭션으로 분리하고 `AuthService.refreshAccessToken()`은
   `NOT_SUPPORTED`로 트랜잭션 자체를 열지 않는다)로 이어졌다.

**이 프로젝트가 REQUIRES_NEW 게이트웨이 패턴(TradeInsertGateway 등)을 원자적 upsert로 교체한 선례와
모순되지 않는다** — 그때는 "UNIQUE 위반을 피해 INSERT 하나만 격리"하려다 스냅샷 문제를 새로 만든 것이
문제였다(재시도 로직이 필요 없어져야 했는데 남아 있었다). 여기는 재시도가 전혀 없고 "호출자가 이미
트랜잭션을 끝낸 뒤, 최신 커밋을 보는 새 트랜잭션에서 한 번만 실행하고 독립적으로 커밋한다"는
REQUIRES_NEW 본연의 용도다.

**"동시 요청 시나리오는 Testcontainers IT가 필요한지만 판단해서 알려달라"는 원 요청에 대한 답 —
필요했고, 실제로 작성해 위 세 문제를 전부 이걸로 잡았다.** 판단만 하고 미루지 않은 이유: 이 세션에
Docker가 이미 가동 중이었고, `AuthServiceMariaDbIT`가 확립한 "메인 스레드는 조율만, 실제 DB 작업은
워커 스레드 안에서"라는 기존 패턴을 그대로 재사용할 수 있어 작성 비용이 낮았다 — 다만 이번 레이스는
InnoDB의 락 대기가 스레드를 자연히 직렬화해 주므로(먼저 도착한 쪽이 배타 락을 잡고, 늦은 쪽은 그 잠금이
풀릴 때까지 블록됐다가 재평가한다) `CountDownLatch` 오케스트레이션 없이 두 스레드를 그냥 동시에
제출하기만 하면 됐다(`AuthServiceMariaDbIT`의 신규 가입 경쟁 테스트보다 단순하다).

검증: `RefreshTokenRotatorTest`(형식 오류/Access Token 제출/미존재/만료/계정 비활성/재사용 탐지 결과
반환/성공 회전 — 전부 Mockito), `RefreshTokenReuseHandlerTest`(전체 폐기+로그 호출), `AuthServiceTest`
(오케스트레이션만: Rotated 결과 그대로 반환, ReuseDetected 결과 시 핸들러 호출 후 예외, Rotator가 던진
예외 그대로 전파), `AuthServiceRefreshRotationMariaDbIT`(신규 — 동시 경쟁 시 정확히 하나만 성공, 패자는
재사용 탐지로 처리, 최종적으로 이 사용자의 모든 Refresh Token이 폐기됨을 커밋된 DB 상태로 확인).
`./gradlew test`(532 테스트)와 `./gradlew integrationTest`(58 테스트, 이 IT 포함) 전부 그린.

**완결 필요** — 프로그램설계서 3.1절의 "Rotation 미적용" 문구를 이 구현에 맞게 갱신하는 것은 문서
원본 반영 세션(claude.ai)에서 처리한다. 이 세션은 PR 요약과 이 결정 로그만 남긴다.

### SVC-AUTH-01 logout() 재사용 탐지 공백 (2026-09-22, 같은 날 P1 코드리뷰) — `rotated_yn` 컬럼 신설

**위 Rotation 구현이 놓친 구멍 — 공격자가 탈취한 토큰으로 먼저 rotation하면, 정상 사용자의 `logout()`이
그 후속 토큰(공격자 세션)을 전혀 건드리지 못한 채 조용히 성공했다.** 시나리오: 공격자가 탈취한
Refresh Token(T1)으로 `refreshAccessToken()`을 먼저 호출해 rotation에 성공 — T1은 `revoked_yn=true`가
되고 공격자는 새 토큰 T2를 쥔다. 그 사이 정상 사용자는 (아직 살아있는 Access Token으로) 자기 세션을
끝내려고 원래 T1으로 `logout()`을 호출한다 — 기존 코드는 `findByTokenValue()`로 T1을 찾아 소유자
검증을 통과시키고 `stored.revoke()`를 부르는데, T1은 이미 `revoked_yn=true`라 이 호출은 그냥 아무
의미 없는 재확인일 뿐이었다 — **T2에 대해서는 아무 일도 일어나지 않고, `logout()`은 예외 없이
정상 종료된다.** 즉 정상 사용자는 "로그아웃했다"고 믿지만 공격자의 세션(T2)은 살아남아, 계속
회전시키며 사실상 무기한 접근을 유지할 수 있었다.

**두 가지 수정 방향(코드리뷰가 제시)을 검토했다: (1) rotation family linkage(부모-자식 토큰 체인
추적), (2) logout()이 이미 폐기된 제출 토큰을 재사용으로 취급.** (1)은 self-referencing FK나 family
ID 같은 스키마 확장과 체인 순회 로직이 필요해 이 프로젝트 단계에 비해 과한 처방이라고 판단했다(YAGNI
— 이 버그를 고치는 데 체인 전체를 추적할 필요는 없다, "이 토큰이 rotation으로 교체됐는가" 하나만
알면 충분하다). **(2)를 그대로 적용하면 새로운 문제가 생긴다** — `revoked_yn=true`는 rotation
때문일 수도, 단순 중복 로그아웃(더블클릭·네트워크 재시도로 흔히 발생) 때문일 수도, 이미 재사용 탐지로
전체 폐기됐기 때문일 수도 있는데, DB에 이 셋을 구분할 정보가 없으면 (2)는 평범한 중복 로그아웃까지
매번 "이 사용자의 다른 모든 세션을 강제 로그아웃"시키는 과잉 반응이 된다 — refresh(드문 경쟁)와
달리 logout은 클라이언트가 자주 재시도하는 성격의 엔드포인트라 이 부작용이 실제로 자주 트리거될
위험이 있다.

**최종 결정: `refresh_token.rotated_yn`(BOOLEAN NOT NULL DEFAULT FALSE) 신설 — "이 토큰이 rotation으로
교체됐는가"만 구분하는 최소 정보.** `RefreshTokenRepository.revokeIfUnrevoked()`가 `revoked_yn`과
`rotated_yn`을 **같은 UPDATE 문에서 함께** true로 세팅한다(원자적 — 별도 쿼리로 나누면 그 사이
"revoked=true인데 rotated=false"인 순간이 관측될 수 있다). 이 플래그를 세팅하는 지점은 오직 여기
하나뿐이다 — `logout()`의 평범한 `stored.revoke()`도, 재사용 탐지의 `revokeAllByUserId()`도
`rotated_yn`을 건드리지 않는다(둘 다 기본값 `false`로 남는다). 이렇게 하면:
- 평범한 중복 로그아웃(`rotated_yn=false`인 채로 `revoked_yn=true`) → `logout()`은 조용히 재확인만
  하고 넘어간다(기존 동작 그대로, 과잉 반응 없음).
- 이미 재사용 탐지로 전체 폐기된 토큰(`rotated_yn=false`) → 마찬가지로 재트리거하지 않는다 — 그
  가족은 첫 탐지 시점에 이미 다 죽었으므로 추가로 할 일이 없다.
- **rotation으로 교체된 토큰(`rotated_yn=true`)** → `logout()`이 이 경우만 정확히 골라내
  `RefreshTokenReuseHandler.handle(userId)`(기존 클래스 재사용, 새 클래스 없음)로 위임해 그 사용자의
  Refresh Token을 전부 폐기한다. 호출자(정상 사용자)에게는 여전히 예외 없는 정상 종료로 보인다 —
  "로그아웃"이 의도한 결과(내 세션이 끝난다)를 오히려 더 강하게 충족시키기 때문이다(공격자 세션까지
  함께 끊긴다).

**`RefreshTokenReuseHandler`를 `logout()`에서 부를 때는 `refreshAccessToken()`이 겪었던 자기 교착
걱정이 없다 — 그 클래스의 안전 조건을 다시 확인한 결과다.** 실제 필요 조건은 "호출자에게 열린
트랜잭션이 전혀 없어야 한다"가 아니라 "호출자가 이 사용자의 refresh_token 행 중 어느 것에도 아직
락을 쥐고 있지 않아야 한다"는 것이다 — `logout()`은 이 호출 전까지 `findByTokenValue()`(비잠금
SELECT)만 수행하고 `revokeIfUnrevoked()` 같은 조건부 UPDATE를 거치지 않으므로, 클래스 레벨
`@Transactional`(REQUIRED)이 열려 있어도 안전하다(애초에 어떤 행도 잠근 적이 없다). 그래서 `logout()`은
`refreshAccessToken()`처럼 `NOT_SUPPORTED`+별도 자기완결 트랜잭션으로 재구성할 필요가 없었다 — 이
차이를 `RefreshTokenReuseHandler`의 javadoc에 명시해, 다음 호출부를 추가할 때 "트랜잭션이 아예
없어야 한다"는 더 강한 요구로 오해해 불필요하게 패턴을 복제하지 않도록 해뒀다.

**스키마 변경 3곳 동기화** — 프로덕션 배포가 아직 없어(로컬 전용) ALTER 마이그레이션 없이 DDL
자체를 고쳤다: `schema_all.sql`(v2.2, 변경 이력 주석 추가), `testcontainers/user-withdraw-schema.sql`,
`testcontainers/withdrawn-user-purge-schema.sql`. `WithdrawalTestSeed.refreshToken()`의 INSERT는
컬럼을 명시적으로 나열하지 않는 컬럼에 DEFAULT가 적용되므로 수정 불필요.

검증: `AuthServiceTest`(logout이 `rotated_yn=false`면 기존대로 `revoke()`만 하고 핸들러를 부르지
않음, `rotated_yn=true`(ReflectionTestUtils로 세팅)면 핸들러를 부르고 예외 없이 종료), 신규
`AuthServiceRefreshRotationMariaDbIT` 테스트 케이스(공격자 역할로 실제 rotation 호출 → 정상 사용자
역할로 원래 토큰으로 logout() → 후속 토큰까지 포함해 이 사용자의 Refresh Token이 전부 폐기됨을 커밋된
DB 상태로 확인, `AuditLogger.logRefreshTokenReuseDetected` 호출도 함께 확인). `./gradlew test`(533
테스트)와 `./gradlew integrationTest`(59 테스트) 전부 그린.

**완결 필요** — 이 컬럼도 프로그램설계서 3.1절 문서 원본 반영 시 함께 언급해야 한다(문서 반영은
claude.ai 세션에서 별도 진행).

### COM-SEC-02 JwtTokenProvider — jti(무작위 UUID) 클레임 신설 (2026-09-22, 같은 날 P2 코드리뷰)

**위 Rotation IT가 이미 겪었던 `token_value` UNIQUE 충돌 — "테스트가 우연히 만든 비현실적 타이밍"이
아니라 "실제로 재발급 로직 자체에 있던 결함"이었다.** 최초 구현 시점엔 이 충돌을 "IT가 시드와 회전을
같은 메서드 안에서 곧바로 이어 붙여서 생긴 것 — 실제 운영에선 로그인과 재발급 사이에 최소 수 초~수 분이
지나 성립하지 않는다"고 진단하고, IT 쪽에 `Thread.sleep(1100)`을 넣어 초 경계를 피해 가는 것으로
마무리했었다(위 "SVC-AUTH-01 Refresh Token Rotation" 절의 "겪은 문제 세 가지" 항목 1번). **코드리뷰가
이 진단 자체를 뒤집었다** — 클라이언트 재시도나 여러 탭에서 거의 동시에 재발급을 두 번 요청하는
것은(이번 세션이 이미 별도로 다룬 동시 경쟁과는 다른, 그냥 짧은 시간 안에 순차적으로 두 번 호출되는
경우) 실제로 충분히 일어날 수 있는 시나리오라, "IT의 타이밍 문제"로 좁혀서 본 것 자체가 틀렸다.

**근본 원인**: `JwtTokenProvider.buildToken()`이 담는 클레임은 `sub`(userId)/`type`/`iat`/`exp`
넷뿐이었다 — 무작위 요소가 전혀 없다. JWT의 `iat`/`exp`(NumericDate, RFC 7519 §2)는 초 단위로 잘리므로,
같은 사용자에게 같은 초 안에 두 번 발급하면(`role`이 있는 Access Token이든 없는 Refresh Token이든)
클레임이 완전히 같아져 서명까지 포함해 **바이트 단위로 동일한 토큰 문자열**이 나온다. Refresh Token은
이 문자열의 해시를 `refresh_token.token_value`(UNIQUE)에 저장하므로, 그 순간 저장 시도가 제약 위반으로
실패한다 — sleep은 이 창을 피해 가는 것이지 닫는 게 아니었다.

**수정**: `buildToken()`에 `jti`(RFC 7519 §4.1.7, JWT ID 표준 클레임)로 `UUID.randomUUID().toString()`을
담는다. Access/Refresh 두 토큰 타입을 분기하지 않고 공유 헬퍼 하나에 무조건 붙였다 — Access Token은
DB에 저장하지 않아 이 문제 자체가 없었지만, 타입별로 분기하는 것보다 공유 코드 경로 하나에 넣는 쪽이
더 단순하고(코드 두 갈래를 유지보수할 필요가 없다), 토큰 하나마다 몇 바이트 늘어나는 비용은 무시할
수준이다. 이제 같은 사용자에게 같은 초 안에 몇 번을 발급해도 토큰 문자열은 항상 다르다 — 확률적
완화가 아니라 구조적 제거다.

**IT의 `Thread.sleep(1100)` 두 곳을 제거했다** — 남겨 뒀다면 "타이밍을 피해 가는 임시방편이 여전히
필요하다"는 잘못된 인상을 남긴다. 제거 후에도 두 테스트가 그대로 통과해(동시 경쟁 테스트, logout()
재사용 탐지 테스트) sleep 없이도 더 이상 충돌하지 않음을 직접 확인했다 — 이 두 IT가 이제 `jti` 수정의
회귀 테스트도 겸한다.

검증: `JwtTokenProviderTest`에 회귀 테스트 2건 추가(같은 사용자에게 연달아 발급한 Refresh Token 둘이
다름, Access Token도 마찬가지). `AuthServiceRefreshRotationMariaDbIT`의 두 테스트에서 sleep 제거 후
재확인. `./gradlew test`(535 테스트, +2)와 `./gradlew integrationTest`(59 테스트) 전부 그린.

### AUTH-03 비밀번호 찾기(재설정) — 신규 서브도메인 (2026-09-22)

**요구사항정의서·엔티티정의서·테이블정의서·프로그램설계서·프로그램목록서 어디에도 이 기능이
정의돼 있지 않다.** UI정의서 8.1절 화면-API 매핑표만 `POST /api/auth/password-reset-request`/
`POST /api/auth/password-reset`을 언급하고, 8.2절 FR 추적표는 AUTH-03을 "FR-1.2의 연계 화면"으로
잠정 분류하며 "요구사항정의서 갱신 시 별도 FR ID 부여를 권장"한다는 각주를 달아 뒀다 — **완결
필요**: 다음 요구사항정의서 갱신 시 FR-1.5 등으로 별도 ID를 부여하고, 프로그램목록서 3장 총괄표
(61→64종, AUTH-03 관련 API-AUTH-01 확장 + SVC-AUTH-01 확장으로 기록)·프로그램설계서 3.1절
Controller/Service 표에도 아래 세 엔드포인트를 반영해야 한다.

**premise 정정 — "AWS SES 발송(BAT-MAIL-01, 알림 이메일용으로 이미 구축됨)"은 틀린 전제였다.**
작업 지시 문서가 이렇게 전제했지만, 실제로는 `software.amazon.awssdk:ses` Gradle 의존성과
`AWS_SES_ACCESS_KEY`/`AWS_SES_SECRET_KEY` 환경변수 플레이스홀더만 "BAT-MAIL-01" 주석과 함께
선언돼 있었을 뿐, 이 값을 바인딩하는 `@ConfigurationProperties` 클래스도 `SesClient` 빈도, 이메일을
실제로 보내는 코드도 전혀 없었다(`batch.notifier` 패키지 자체가 존재하지 않음, 전수 확인). 이번
작업이 이 프로젝트 최초의 실제 SES 소비자다 — `common.config.AwsSesProperties`/`SesClientConfig`와
`common.mail.MailSender`/`SesMailSender`를 새로 만들었다. `MailSender`를 인터페이스로 분리해 둔
이유는 향후 BAT-MAIL-01(3단계 이후 로드맵)이 SES 클라이언트 조립을 새로 하지 않고 이 인터페이스만
주입받아 재사용할 수 있게 하기 위함이다.

| 항목 | 검토안(작업 지시 원문) | 최종 결정 | 근거 |
| --- | --- | --- | --- |
| 토큰 저장소 | DB 테이블(ENT-AUTH-02 신설) vs Redis TTL 키 | **Redis TTL 키** — `password-reset:token:{tokenHash}`(30분), `password-reset:cooldown:{email}`(60초) | `login:fail:{email}`(LoginAttemptService)과 같은 선례. 재설정 토큰은 단발성·단기 유효라 refresh_token처럼 재사용 탐지·감사 목적의 장기 보관이 필요 없다. 새 테이블/DDL 없이 끝나 문서 동기화 비용도 없다. |
| 토큰 원문 형식 | — | `SecureRandom` 32바이트 → hex 64자(opaque, **JWT 아님** — COM-SEC-02와 혼동 금지) | Redis 키는 원문이 아니라 `RefreshTokenHasher`(같은 패키지, 이미 있는 SHA-256 해셔 재사용)로 해시해 저장한다 — DB가 아니라 Redis에 저장하지만 "원문을 그대로 저장하지 않는다"는 같은 원칙을 적용했다. |
| 토큰 소비 방식 | — | `StringRedisTemplate.opsForValue().getAndDelete()`(GETDEL) — 조회+삭제 원자적 | Refresh Token Rotation의 조건부 UPDATE(affected rows)와 목적은 같지만, Redis 단일 커맨드 자체가 원자적이라 DB의 REPEATABLE READ 스냅샷 문제(RefreshTokenReuseHandler가 두 단계에 걸쳐 발견한 것과 같은 종류)가 애초에 성립하지 않는다. |
| **재발급 시 이전 활성 토큰 무효화(2026-09-23, Codex P1 코드리뷰 지적) — 원 작업 지시엔 없던 새 판단** | 미명시(단일 활성 토큰 모델 여부는 원 작업 지시에 없었다) | **채택** — `password-reset:active-token:{userId}` 포인터로 사용자당 항상 최신 토큰 하나만 유효하게 강제한다. `issueToken()`이 새 토큰을 발급하기 전 이 포인터로 이전 토큰의 해시를 찾아 그 토큰 키를 삭제하고, `consumeToken()`이 성공하면 포인터 자체도 함께 지운다(`PasswordResetTokenService.invalidatePreviousToken()`) | **근거**: 쿨다운(60초) 경과 후 같은 사용자가 재설정을 다시 요청하면, 수정 전에는 각 요청이 독립적인 Redis 키로 저장돼 이전 토큰이 30분 TTL이 끝날 때까지 그대로 살아 있었다 — 오래된 이메일(공유 메일함, 열람 지연 등으로 나중에 읽힐 수 있다)로도 그 사이 계정을 재탈취할 수 있는 구멍이었다. 쿨다운이 사실상 동시 재발급을 막아 `invalidatePreviousToken()`의 GET→새 토큰 SET 사이 레이스는 무시할 수준이고, 그 레이스가 실제로 발생해도 실패 방향은 fail-safe(무효화가 씹혀도 최악의 경우 "구 토큰이 잠깐 더 살아있다"이지 "신 토큰이 깨진다"가 아니다). **무효화 조건**: 여러 기기에서 동시에 유효한 재설정 링크가 필요해지는 요구사항이 생기면(예: PC/모바일 양쪽에서 각자 받은 링크를 나중에 열어야 하는 경우) 이 "사용자당 토큰 1개" 모델 자체를 재검토해야 한다 — 지금은 그런 요구가 없어 가장 단순한 모델을 택했다. 검증: `PasswordResetTokenServiceTest`(재발급 시 이전 토큰 delete 호출/이전 토큰 없으면 미호출/consume 성공 시 포인터 delete), `AuthServicePasswordResetMariaDbIT.같은_사용자가_재설정을_다시_요청하면_이전_토큰은_새_토큰_발급과_동시에_무효화된다`(실 Redis로 2회 발급 후 구 토큰 peek 실패·신 토큰 동작·reset 성공까지 end-to-end 확인). |
| 사전 검증 API 신설 여부 | 신설 검토(`GET /password-reset/validate-token`) | **신설함** — `AuthService.validatePasswordResetToken()`이 토큰을 소비하지 않고(peek) 존재 여부만 확인, 무효면 `InvalidResetTokenException`(400) | UI정의서 예외표의 "2단계 입력 폼 대신 안내 표시"를 만족하려면 폼 렌더링 전에 토큰 유효성을 알아야 한다. 이 API는 공식 매핑표에 없는 확장이라 프로그램설계서 API 매핑표 갱신이 필요하다(위 "완결 필요" 참고). |
| 비활성 계정(WITHDRAWN/SUSPENDED) 처리 | 검토 필요 | **발송하지 않음** — `requestPasswordReset()`이 `user.getStatus() == ACTIVE`일 때만 토큰 발급+메일 발송 | AUTH-01의 "탈퇴/정지 계정은 로그인 자체를 차단" 원칙과 정합. `resetPassword()`도 토큰 소비 직후 다시 한 번 ACTIVE를 확인한다 — 토큰 발급 이후(최대 30분 창) 탈퇴됐다면 `InvalidResetTokenException`으로 거부해, WITHDRAWN 계정이 `reactivate()`의 `authenticate()`(비밀번호 확인)를 우회해 비밀번호를 바꾸는 구멍을 막는다. |
| 계정 존재 여부 비노출 — 응답 통일 | "동일한 성공 응답"(원칙만 명시) | `requestPasswordReset()`은 **항상 같은 `ApiResponse<Void>` 성공**만 반환(계정 존재·ACTIVE 여부와 무관), 예외는 쿨다운(429)뿐 | 계정 조회·발송 성패로 분기하는 메시지 필드를 만들지 않았다 — `ApiResponse<Void>`가 이미 "성공/실패"만 표현하는데 성공 응답 안에 "계정이 있으면 이렇게, 없으면 저렇게"를 담으면 그 문구 차이 자체가 오라클이 된다. |
| **재전송 쿨다운의 오라클 방지 — 작업 지시가 명시하지 않은 채 남겨둔 보안 설계 공백을 이번에 직접 메웠다** | "계정 존재 여부와 무관하게 동일 성공"만 요구, 쿨다운을 언제 세팅할지는 미명시 | **쿨다운 키는 계정 존재 여부와 무관하게 항상 세팅한다** — `isCoolingDown()` 확인 후 `startCooldown()`을 조회보다 먼저 실행 | 만약 쿨다운을 "계정이 실제로 존재해 메일을 보낸 경우"에만 세팅했다면, 같은 이메일을 빠르게 두 번 제출했을 때 존재하는 계정은 2번째 요청에서 429(쿨다운)를, 존재하지 않는 계정은 2번째 요청도 그대로 200을 받아 — 이 응답 차이 자체가 계정 존재를 확인하는 오라클이 됐을 것이다. 쿨다운을 이메일 존재 여부와 완전히 분리해 항상 세팅하면 이 오라클이 원천적으로 성립하지 않는다(검증: `AuthServiceTest.requestPasswordReset_존재하지_않는_이메일이어도_쿨다운을_세팅하고_예외_없이_종료한다`). |
| 토큰 발급+메일 발송의 동기/비동기 | 미명시 | **`@Async`**(`PasswordResetNotifier.notifyAsync()`, self-invocation을 피해 별도 빈으로 분리) | 이유가 두 가지다. (1) NFR — SES 네트워크 호출(샌드박스 상태라 재시도·지연 가능)이 응답 경로를 블로킹하면 안 된다(SVC-RCV-01.record()/SVC-SEARCH-01.record()와 같은 이유). (2) **여기서만 추가로 성립하는 이유** — 동기 호출이었다면 "이메일이 존재해 SES 호출이 실제로 일어나 응답이 느려짐"과 "존재하지 않아 호출 자체가 없어 즉시 응답"이 타이밍 사이드채널이 됐을 것이다. 비동기로 분리하면 API 응답은 계정 존재 여부와 무관하게 항상 즉시 반환된다 — 위 오라클 방지 설계를 완성하는 마지막 조각. |
| 비밀번호 변경 성공 시 기존 세션 전체 폐기 | 권장(검토 필요) | **채택** — `resetPassword()`가 `refreshTokenRepository.revokeAllByUserId(userId)`를 호출(회원탈퇴와 동일 패턴) | 탈취된 비밀번호로 이미 로그인해 둔 세션이 있을 가능성에 대비한다. `user.changePassword()`(dirty) 직후 이 벌크 UPDATE를 호출하는 순서는 `RefreshTokenRepository#revokeAllByUserId`의 `flushAutomatically=true`가 안전하게 처리한다(UserService.withdraw()가 이미 겪은 flush 순서 문제와 같은 함정, 이번엔 재사용). 실 DB로 검증(아래 IT). |
| 재설정 링크 URL 형식 | 예시 `https://hmss.site/password-reset?token=...` | **`{FrontendProperties.baseUrl}/password-reset?token={rawToken}`** — 새 프로퍼티 `homesense.frontend.base-url`(local: `http://localhost:5173`, prod: `${FRONTEND_URL}`) 신설 | 기존 `homesense.cors.allowed-origins`(FRONTEND_URL)을 재사용하지 않았다 — 그 값은 아직 어떤 `CorsConfigurationSource`도 소비하지 않는 죽은 설정(위 "로컬 개발 환경의 CORS 우회" 절 참고)이라, 새 기능을 그 미완결 상태에 얹으면 서로 영향을 주게 된다. 독립 프로퍼티로 분리했다. **프론트 라우트 `/password-reset`을 그대로 이 이름으로 만들어야 한다** — 다르게 만들면 이미 발송된 메일의 링크가 깨진다. |
| SES 자격 증명 해석 | — | accessKey/secretKey가 둘 다 채워져 있으면 `StaticCredentialsProvider`, 비어 있으면 AWS SDK 기본 자격 증명 체인(환경변수→프로파일→인스턴스 역할)에 위임 | `KakaoProperties`처럼 이 두 필드는 `@NotBlank`를 걸지 않았다(EC2/ECS 인스턴스 역할 기반 배포로 옮겨가도 이 클래스를 고칠 필요가 없게). `region`/`senderAddress`는 없으면 이메일을 아예 못 보내 `@NotBlank`로 기동 시 fail-fast한다. |

**신규 예외 2종**(`auth.exception`): `InvalidResetTokenException`(400, 토큰 만료·미존재·이미 사용됨·계정
비활성 전부 동일 메시지로 통일 — `InvalidRefreshTokenException`과 같은 사상), `PasswordResetCooldownException`
(429, `AccountLockedException`과 같은 패턴).

**검증**: `PasswordResetTokenServiceTest`(Mockito+실제 `RefreshTokenHasher` — 무작위 토큰 발급/조회/소비/쿨다운
9건), `PasswordResetNotifierTest`(토큰 발급→메일 발송, SES 실패 시 예외 흡수+감사 로그 2건),
`SesMailSenderTest`(요청 필드 매핑, SES 예외 그대로 전파 2건), `AuthServiceTest`(쿨다운/비활성 계정/ACTIVE
분기/사전검증/토큰 무효·계정 없음·계정 비활성·성공 10건 추가), `AuthControllerTest`(3개 엔드포인트의
성공·검증 실패·예외 변환 8건 추가), `AuthEndpointSecurityTest`(비밀번호 재설정 요청이 login/signup처럼
인증 전 permitAll인지 1건 추가). **`AuthServicePasswordResetMariaDbIT`(신규, Testcontainers)** —
`UserServiceMariaDbIT`와 정확히 같은 이유(Mockito는 "비밀번호 변경 dirty 상태가 뒤이은 벌크 UPDATE
이전에 실제로 flush되는지"를 증명할 수 없다)로, 실제 MariaDB 위에서 `resetPassword()` 커밋 이후 재조회한
DB 상태(새 비밀번호로 `matches()` 성공, RefreshToken 전체 폐기, 토큰 1회성 소비 확인)를 검증한다. Docker가
가동 중인 세션에서 `./gradlew integrationTest`로 실행해 통과 확인(15개 MariaDB IT 클래스, 이 신규 IT 포함).
`./gradlew test`도 전부 그린.

**프론트엔드 작업 시 참고할 최종 계약**(AUTH-03 프론트 프롬프트에 그대로 전달):

| 항목 | 값 |
| --- | --- |
| 1단계 요청 | `POST /api/auth/password-reset-request`, body `{"email": string}`, 성공 시 `ApiResponse<null>`(200) — 계정 존재 여부와 무관하게 항상 같은 성공 |
| 1단계 재전송 제한 | 같은 이메일 60초 이내 재요청 시 `429` + `error.code = "PASSWORD_RESET_COOLDOWN"`, `error.message = "잠시 후 다시 시도해주세요"` |
| 사전 검증(2단계 진입 시) | `GET /api/auth/password-reset/validate-token?token={token}` — 유효하면 `200`, 무효/만료/이미사용/계정비활성이면 전부 `400` + `error.code = "INVALID_RESET_TOKEN"`, `error.message = "유효하지 않거나 만료된 재설정 링크입니다. 다시 요청해주세요"`(토큰을 소비하지 않음 — 이 호출로는 링크가 무효화되지 않는다) |
| 2단계 제출 | `POST /api/auth/password-reset`, body `{"token": string, "newPassword": string}` — `newPassword`는 AUTH-02와 동일한 정책(8자 이상 72바이트 이하, 영문·숫자·특수문자 조합), 위반 시 `400` + `VALIDATION_FAILED` + `fieldErrors[0].field = "newPassword"` |
| 2단계 실패 | 토큰이 이미 위 사전검증/제출로 소비됐거나 만료·계정비활성이면 `400` + `INVALID_RESET_TOKEN`(사전검증과 동일 코드·문구) |
| 토큰 만료 | 30분(발급 시점부터) |
| 재설정 링크 형식 | `{프론트엔드 오리진}/password-reset?token={토큰}` — 프론트 라우트를 정확히 `/password-reset`으로 만들어야 한다(쿼리 파라미터명 `token`) |
| 재설정 성공 후 | 서버가 그 사용자의 모든 Refresh Token을 폐기하고, 이미 발급됐던 Access Token도 다음 요청부터 거부한다(아래 "AUTH-03 resetPassword() Access Token 즉시 무효화" 절 참고) — 다른 기기/탭에 로그인돼 있었다면 전부 로그아웃된다(이 사실을 안내 문구에 반영할지는 프론트 판단) |

### AUTH-03 resetPassword() — Access Token 즉시 무효화 (2026-09-23, Codex P1 코드리뷰 지적) — `AccessTokenEpochService` 신설

**Refresh Token 전체 폐기만으로는 비밀번호 재설정이 막으려는 "계정 탈취" 시나리오를 완전히 방어하지
못했다.** `JwtAuthenticationFilter`는 계정 상태가 ACTIVE이고 서명·만료가 유효하면 이미 발급된 Access
Token을 그대로 인증에 쓴다 — 비밀번호 재설정은 계정 상태(status)를 바꾸지 않으므로(재설정 후에도
여전히 ACTIVE), 공격자가 재설정 이전에 이미 Access Token을 쥐고 있었다면 그 토큰이 자연 만료될
때까지(최대 `accessTokenValidity`, 기본 30분) 재설정 이후에도 계속 인증된 요청을 보낼 수 있었다.

**해결: `password-reset:token:...`와 별개로, "이 사용자에게 이 시각 이전 발급된 Access Token은 전부
무효"라는 컷오프(epoch)를 Redis에 남긴다.** 신규 `AccessTokenEpochService`(`common.security`,
`UserStatusCacheService`와 같은 형태 — `user:tokenEpoch:{userId}` 키, TTL=`accessTokenValidity`)가
컷오프를 저장하고, `JwtTokenProvider.getIssuedAt(token)`(신규, JWT `iat` 클레임 추출)으로 얻은 토큰
발급 시각과 비교한다. `JwtAuthenticationFilter`는 기존 `userStatusResolver.isActive(userId)`에
`accessTokenEpochService.isIssuedAfterCutoff(userId, issuedAt)`을 AND로 추가해, 컷오프 이전에 발급된
토큰이면 예외 없이 SecurityContext 설정만 건너뛴다(만료·상태불일치 토큰과 같은 패턴 — 토큰 상태를
따로 두지 않는 stateless JWT 모델을 유지하면서, 이 필터가 매 요청 이미 하던 Redis 조회 하나를 더
추가하는 것으로 끝난다). `AuthService.resetPassword()`가 `refreshTokenRepository.revokeAllByUserId()`
직후 `accessTokenEpochService.invalidateTokensIssuedBefore(userId, Instant.now())`를 호출한다.

**컷오프를 쓰는 지점은 지금 `resetPassword()` 하나뿐이다 — `RefreshTokenReuseHandler.handle()`
(재사용 탐지로 Refresh Token을 전부 폐기하는 지점)도 구조적으로 같은 갭을 안고 있다는 것을 발견했지만,
이번 P1이 지목한 지점(비밀번호 재설정)만 우선 닫았다.** 공격자가 탈취한 Refresh Token으로 이미
Access Token을 발급받아 둔 상태에서 재사용이 탐지되면, Refresh Token은 전부 죽지만 그 Access Token은
마찬가지로 만료 전까지 계속 통용된다 — 범위가 넓어지는 걸 피하려 이번 P1이 명시한 지점만 고쳤다.
**[처리완료 2026-09-29, `feature/backend/reuse-detection-access-cutoff`] `RefreshTokenReuseHandler.handle()`에도
같은 컷오프를 건다.** Refresh Token 전체 폐기 직후 `accessTokenEpochService.invalidateTokensIssuedBefore(userId,
Instant.now())`를 호출한다 — 재발급(`refreshAccessToken`)과 로그아웃(`logout`, rotation된 토큰 제출) 두 탐지
경로가 모두 이 메서드를 거친다.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| 재사용 탐지 시 Access Token 컷오프 | 채택 | 공격자는 탈취한 Refresh Token으로 먼저 회전할 때 Access Token도 함께 받는다. Refresh Token 폐기만으로는 그 Access Token이 만료(`accessTokenValidity`, 기본 30분)까지 통용됐다 | Access Token 만료를 수 분 이하로 줄이면 컷오프 없이도 노출 창이 충분히 짧아지므로 재검토한다 |
| 호출 위치·순서 **[2026-09-29 같은 날 코드리뷰 P2로 변경]** | Refresh Token 폐기와 감사 로그를 `REQUIRES_NEW` 트랜잭션(`TransactionTemplate`)으로 **먼저 커밋한 뒤** Redis에 컷오프를 쓴다. 컷오프 쓰기가 실패하면 예외를 그대로 던진다(삼키지 않는다) | 처음엔 `resetPassword()`처럼 같은 트랜잭션 안에서 썼는데, Redis 장애로 SET이 예외를 던지면 앞선 `revokeAllByUserId()`까지 롤백돼 공격자가 회전으로 받은 Refresh Token이 Redis 복구 뒤에도 유효했다. 예외를 던지는 이유: 재발급 클라이언트는 5xx를 일시 장애(`unreachable`)로 보고 토큰을 지우지 않아 다음에 같은 토큰으로 다시 재발급하고, 그 요청이 재사용 탐지를 다시 거쳐 컷오프를 재시도한다. `@Transactional` 대신 템플릿을 쓰는 이유는 같은 클래스 안에서 트랜잭션을 끝낸 뒤 이어서 쓰려면 self-invocation을 피해야 해서다 | 로그아웃 경로는 클라이언트가 응답과 무관하게 로컬 로그아웃해 재시도가 없다 — 그때는 공격자 Access Token이 만료까지 남는다(Refresh Token 폐기는 커밋됨). 로그아웃 경로의 이 한계는 받아들였다 — Refresh Token 폐기는 Redis 상태와 무관하게 커밋되고, 남는 위험은 "Redis 장애 중에 탐지가 일어나면 공격자 Access Token이 최대 30분 유효"로 한정된다 | |
| `resetPassword()`의 컷오프 순서 **(확정, 재사용 탐지와 다르게 둔다)** | 비밀번호 변경·Refresh Token 폐기·컷오프 쓰기를 **같은 트랜잭션 안에서** 한다 — 컷오프 SET이 실패하면 셋 다 롤백되고 사용자는 오류를 본다 | 두 경로는 실패 시 필요한 동작이 다르다. 재사용 탐지는 계정 주인이 의도한 요청이 아니고 재시도도 보장되지 않아, 핵심 방어인 폐기가 Redis에 좌우되면 안 된다. 재설정은 사용자가 직접 한 요청이라 실패하면 오류를 보고 다시 요청한다 — 셋이 한꺼번에 성공하거나 실패하는 편이 낫다. 순서를 바꾸면 비밀번호는 바뀌었는데 화면엔 오류가 뜨고 공격자 Access Token은 조용히 30분 살아남는다. 게다가 재설정 토큰도 Redis에 있어 Redis가 내려가 있으면 GETDEL에서 먼저 실패하므로, 문제가 되는 것은 "GETDEL은 성공하고 컷오프 SET만 실패하는" 좁은 경우뿐이다 | 재설정 토큰 저장소를 Redis 밖으로 옮길 때 |
| 경계 **[2026-09-29 같은 날 코드리뷰 P1로 변경]** | 발급 시각(신규 `iatMs` 클레임, 밀리초)과 컷오프(밀리초)를 비교해 **발급 시각 > 컷오프일 때만 통과**한다. Redis 키도 `user:tokenEpochMs:{userId}`로 바꿨다(초 단위 옛 값을 밀리초로 잘못 읽으면 모두 통과하므로). `iatMs`가 없는 옛 토큰은 `iat`(초의 시작)으로 대신한다 | 처음 구현(초 단위, `iat >= cutoff` 통과)은 탐지와 **같은 초**에 회전으로 발급된 공격자 Access Token을 만료(30분)까지 통과시켰다 — 공격자 회전과 탐지는 수 밀리초 안에 연달아 일어나 흔한 경우였고, IT가 탐지 전 `Thread.sleep(1100)`으로 이를 가리고 있었다. 엄격 비교가 안전한 이유: 공격자 토큰은 `RefreshTokenRotator` 트랜잭션 안(커밋 전)에서 발급되고 탐지는 그 커밋 뒤라 같은 밀리초여도 막힌다. 컷오프와 같은 밀리초에 발급된 정당한 토큰도 막히지만 그 토큰 하나뿐이고 다음 로그인은 통과한다. 주의: 이 프로젝트의 JSON 역직렬화기는 숫자 클레임을 `Double`로 돌려줘 `get(name, Long.class)`가 예외를 던진다 — `Number`로 읽는다 | (1) `RefreshTokenRotator`가 Access Token을 **트랜잭션 커밋 전에** 발급한다는 전제다(그 메서드에 불변 조건 주석) — 발급을 커밋 뒤(호출자·커밋 후 리스너 등)로 옮기면 탐지가 발급보다 먼저 일어나 공격자 토큰이 컷오프를 통과할 수 있으므로 이 결정을 다시 본다. (2) 발급과 컷오프가 같은 벽시계를 쓴다는 전제다 — 다중 인스턴스(시계 어긋남)로 가면 발급 세대(generation) 방식이나 공통 시간 소스를 검토한다 |

**Redis 읽기 장애 시 현재 동작(2026-09-29 코드로 확인, 이번에 고치지 않음):** `JwtAuthenticationFilter`는 Bearer 토큰이 있는 요청마다 `UserStatusResolver`(→ `user:status` 조회)와 `AccessTokenEpochService`(→ 컷오프 조회)를 부르는데, 둘 다 Redis 예외를 잡지 않는다 — 그래서 Redis 장애 중에는 **예외가 필터 밖으로 전파돼 토큰을 실은 요청이 실패한다**(컷오프를 무시하고 통과시키는 쪽이 아니다). 공개 API도 프론트가 토큰을 붙여 보내면 똑같이 실패하고, 각 요청은 Redis 타임아웃(2초)을 기다린 뒤 실패한다. 실제로는 `user:status` 조회가 먼저 실패해 컷오프 조회까지 가지 않는다. `UserStatusResolver`와 달리 컷오프는 DB에 기록이 없어 폴백할 곳이 없다. 응답 형태(필터 예외가 어떤 상태·포맷으로 나가는지)는 실측하지 않았다. **후속 과제:** 위 "백로그" 절 4번(필터를 비로그인 강등으로 바꾸기, 별도 브랜치).

검증: `RefreshTokenReuseHandlerTest`(폐기 → 컷오프 순서, 컷오프 시각이 호출 시각), **`RefreshTokenReuseAccessCutoffMariaDbIT`**(신규,
`@AutoConfigureMockMvc`로 실제 `JwtAuthenticationFilter`를 태운다 — 재발급·로그아웃 두 경로 각각 탐지 전 200 → 탐지 후
공격자·정상 사용자의 기존 Access Token으로 `GET /api/users/me` 401 → 새로 로그인하면 200). 컷오프 호출을 빼면 이 IT 2건이
모두 실패함을 확인했다(변형 검증). **경계 변경 후(같은 날):** IT의 `Thread.sleep(1100)`을 없애고 반대로 초의 시작까지 기다린 뒤 회전·탐지를 이어 붙여 공격자 토큰이 탐지와 **같은 초**에 발급되게 맞춘다(보안 단언을 모두 마친 뒤 `assumeTrue`로 같은 초였는지 확인해, 느린 환경에서 초 경계를 넘긴 실행은 실패 대신 건너뜀으로 표시). 비교를 옛 방식(초 단위 `>=`)으로 되돌리면 이 IT 2건이 공격자 토큰 200으로 실패함을 확인했다(변형 검증). **순서 변경 후(같은 날):** 같은 IT에 Redis 장애 케이스를 추가했다 — `@MockitoSpyBean AccessTokenEpochService`로 컷오프 쓰기만 실패시키면 재발급은 오류로 끝나지만 이 사용자의 미폐기 Refresh Token은 0건이고, 정상 동작으로 되돌려 같은 토큰으로 재시도하면 공격자 Access Token이 401이 된다. 컷오프를 트랜잭션 안으로 되돌리면 공격자 토큰 1건이 살아남아 실패함을 확인했다(변형 검증). 방침 8항도 이에 맞춰 "모든 기기에서 즉시 로그인 상태가 해제"로 바꿨다(SCR-LEGAL-01 절).

**정밀도 불일치 버그 — 실 Redis로 작성한 IT가 잡아냈다(Mockito로는 드러나지 않았을 결함).** JWT의
`iat`(NumericDate, RFC 7519)는 라이브러리가 직렬화 시점에 초 단위로 자른다(COM-SEC-02의 `jti` 도입
배경과 같은 특성). 최초 구현은 컷오프를 밀리초 정밀도(`Instant.now().toEpochMilli()`)로 저장하고
초 단위로 잘린 `iat`과 그대로 비교했는데, 재설정 직후 같은 초 안에 정당하게 재로그인해 발급된 새
Access Token의 `iat`이 컷오프보다 초 단위로는 같아도 밀리초로는 여전히 "이전"으로 비교돼 즉시
걸러지는 오탐이 있었다 — 그 토큰은 컷오프 TTL(최대 30분) 동안 계속 거부되는, 실사용자 로그인이
막히는 심각한 회귀였다. `AuthServicePasswordResetMariaDbIT`에 추가한 실 Redis 테스트가 정확히 이
실패를 재현했다(양쪽을 자유롭게 밀리초로 넣을 수 있는 Mockito 목으로는 이 정밀도 불일치 자체가
드러나지 않았을 것이다). **수정: 컷오프·비교 모두 초 단위(`Instant.getEpochSecond()`)로 통일했다** —
이러면 컷오프 발생 직전 같은 초 안에 발급된 진짜 stale 토큰이 최대 1초간 걸러지지 않을 수 있는 반대
방향의 작은 여지가 생기지만, "정당한 로그인을 최대 30분 차단"과 "탈취된 토큰을 최대 1초 늦게 차단"
중 후자가 명백히 더 안전한 트레이드오프라 그대로 받아들였다(`AccessTokenEpochService` javadoc에 근거
기록). **→ 2026-09-29 재변경:** 초 단위 절삭이 재사용 탐지에서 "같은 초에 회전한 공격자 토큰이 30분간
통과"라는 더 큰 구멍을 만들어(코드리뷰 P1), 토큰에 밀리초 발급 시각(`iatMs`)을 따로 싣고 밀리초로 엄격
비교(`>`)하도록 바꿨다 — 위 "재사용 탐지 시 Access Token 컷오프" 표의 "경계" 행 참고. 이로써 위 두 방향의
트레이드오프가 모두 사라졌다(정당한 재로그인은 밀리초로 구분되고, stale 토큰은 같은 초여도 막힌다). 아래
검증의 `Thread.sleep(1100)`도 제거했다.

**검증**: `AccessTokenEpochServiceTest`(Mockito — 초 단위 저장·컷오프 없음/이전/이후/같은 초 4분기),
`JwtTokenProviderTest.getIssuedAt은_토큰_발급_시각을_추출한다`(신규),
`JwtAuthenticationFilterTest.상태는_ACTIVE여도_컷오프_이전에_발급된_토큰이면_인증정보를_채우지_않는다`(신규),
`AuthServiceTest.resetPassword_성공하면_...`(컷오프 호출 검증 추가). **`AuthServicePasswordResetMariaDbIT.
재설정_이전에_발급된_AccessToken은_재설정_이후_컷오프에_걸리고_이후에_발급된_토큰은_통과한다`(신규,
Testcontainers+실 Redis)** — stale 토큰 발급 → `Thread.sleep(1100)`(초 경계를 실제로 건너기 위한
필수 전제조건이지 타이밍 회피가 아니다, iat 자체가 초 단위라 이보다 짧은 간격으로는 이 테스트가
검증하려는 것 자체가 성립하지 않는다) → `resetPassword()` → 새 토큰 발급 → stale은 컷오프에 걸리고
fresh는 통과함을 실 Redis로 확인. `@WebMvcTest` 슬라이스 12개 전부 `JwtAuthenticationFilter`가 항상
컨텍스트에 오르는 기존 패턴(트러블슈팅 노트 참고)대로 `@MockitoBean AccessTokenEpochService`를
추가했다. **2026-09-23, Docker가 가동 중인 세션에서 `./gradlew test`(576 테스트)와
`./gradlew integrationTest`(15개 MariaDB IT 클래스, 62 테스트, 이 신규 케이스 포함) 둘 다 실행해
그린 확인했다.**

**완결 필요**: 프로그램설계서 3.1절(AUTH-03 처리 로직)에 이 컷오프 메커니즘을 반영해야 한다(문서
반영은 claude.ai 세션에서 별도 진행). 위 `RefreshTokenReuseHandler` 확장 항목도 함께.

**완결 필요(문서 추적성) — `AccessTokenEpochService`가 프로그램목록서·프로그램설계서 어디에도 프로그램
ID로 등록돼 있지 않다(코드리뷰 지적, 2026-09-23).** COM-SEC-01(`JwtAuthenticationFilter`/
`UserStatusCacheService`/`UserStatusResolver`)·COM-SEC-02(`JwtTokenProvider`)와 같은 급의 공통 보안
컴포넌트인데, 이번 신설이 신규 프로그램 목록 갱신 없이 기존 COM-SEC-01/02 절 아래 코드로만 들어갔다.
다음 문서 동기화 시점에 **COM-SEC-03**(가칭)으로 프로그램목록서 3장 총괄표·8.2절 FR 추적표에 등록하고
(위 "프로그램 인벤토리" 절의 COM 8개 목록도 함께 9개로 갱신), 프로그램설계서에도 클래스·메서드
시그니처를 반영하라 — 지금 당장 막을 일은 아니다.

### AUTH-03 requestPasswordReset() 쿨다운 획득 경쟁 (2026-09-23, Codex P2 코드리뷰 지적) — SETNX로 원자화

**쿨다운 확인(GET)과 세팅(SET)이 분리된 두 호출이라 TOCTOU였다.** `requestPasswordReset()`은
`passwordResetTokenService.isCoolingDown(email)`으로 조회한 뒤 통과하면 별도로
`startCooldown(email)`을 호출했다 — 같은 이메일로 거의 동시에 여러 요청이 들어오면 그 조회~세팅
사이의 창에서 전부 "쿨다운 없음"을 관측할 수 있어, 광고된 "60초에 한 번" 제한이 무력화된 채 여러
요청이 나란히 통과했다. 각 요청이 `PasswordResetNotifier.notifyAsync()`(비동기 SES 발송+새 토큰
발급)를 중복 실행해, 짧은 시간에 여러 통의 메일과 여러 개의 동시 유효한 재설정 토큰이 발급될 수
있었다(위 "재발급 시 이전 활성 토큰 무효화" 항목이 순차 재발급만 다뤘지, 이 동시 발급 경쟁은 별개
문제였다).

**수정: `isCoolingDown()`+`startCooldown()`을 `PasswordResetTokenService.tryStartCooldown()`
(SETNX, `StringRedisTemplate.opsForValue().setIfAbsent()`) 하나로 합쳤다.** Redis 단일 커맨드가
직렬화를 보장하므로 조회~세팅 사이의 창 자체가 성립하지 않는다 — `consumeToken()`의 GETDEL(위
AUTH-03 절 참고)과 같은 원자적 read-then-write 패턴이다. `requestPasswordReset()`은 이제
`tryStartCooldown()`의 반환값(획득 성공 여부)만 보고 실패하면 즉시 `PasswordResetCooldownException`을
던진다 — 계정 존재 여부와 무관하게 항상 획득을 시도한다는 오라클 방지 원칙(위 AUTH-03 절 참고)은
그대로 유지된다.

**검증**: `PasswordResetTokenServiceTest`(SETNX 성공/실패 각 1건, 기존 `isCoolingDown`×2+`startCooldown`×1
3건을 `tryStartCooldown`×2 2건으로 교체), `AuthServiceTest`(4건 모두 `tryStartCooldown` 목킹으로 갱신).
**`AuthServicePasswordResetMariaDbIT.같은_이메일로_동시에_여러_재설정_요청이_와도_쿨다운_획득은_정확히_하나만_성공한다`
(신규, Testcontainers+실 Redis)** — `CyclicBarrier`로 8개 스레드를 동시에 출발시켜 실제
`AuthService.requestPasswordReset()`을 실 Redis에 대고 경쟁시키고, 정확히 1건만 성공(예외 없음)하고
나머지 7건은 전부 `PasswordResetCooldownException`인지 확인한다 — `AuthServiceMariaDbIT`의 가입 경쟁
테스트와 달리 순서를 인위적으로 강제할 필요가 없었다(SETNX 자체가 원자적이라 순서와 무관하게 정확히
하나만 이긴다). **2026-09-23, Docker가 가동 중인 세션에서 `./gradlew test`(575 테스트)와
`./gradlew integrationTest`(15개 MariaDB IT 클래스, 63 테스트, 이 신규 케이스 포함) 둘 다 실행해
그린 확인했다.**

### AUTH-03 resetPassword() 성공 시 로그인 실패 잠금 해제 (2026-09-23, Codex P2 코드리뷰 지적)

**재설정 성공 후에도 `login:fail:{email}` 잠금이 그대로 남아있었다.** 비밀번호를 5회 틀려
`LoginAttemptService`가 계정을 잠근(`login:fail:{email}` 카운터가 임계치 도달, TTL 5분) 상태에서
사용자가 비밀번호 찾기로 전환해 재설정에 성공해도, `resetPassword()`는 이 Redis 카운터를 전혀
건드리지 않았다 — 새 비밀번호로 곧바로 로그인해도 그 카운터의 TTL이 자연 만료될 때까지(최대 5분)
계속 `AccountLockedException`에 막혔다. 이메일 링크로 받은 재설정 토큰을 원자적으로 소비(GETDEL)해
`resetPassword()`까지 도달했다는 것 자체가 이미 그 메일함(=계정)의 소유를 증명하므로, 더 이상 그
잠금을 유지할 이유가 없다.

**수정: `resetPassword()` 성공 경로 마지막에 `loginAttemptService.reset(user.getEmail())`을 추가했다.**
`user.getEmail()`은 `User.createUser()`가 저장 시점에 이미 `User.normalizeEmail()`로 정규화해 둔 값이라
(`UserRepository.findByEmail()`/`existsByEmail()`이 조회 시에도 같은 정규화를 적용하는 것과 같은 전제),
`authenticate()`가 쓰는 `normalizedEmail`과 동일한 키를 가리킨다 — 별도 정규화 호출이 필요 없다.
`LoginAttemptService.reset()`은 `login()`의 자격 검증 성공 경로가 이미 쓰던 기존 메서드를 그대로
재사용했다(신규 메서드 없음).

**검증**: `AuthServiceTest.resetPassword_성공하면_...`에 `verify(loginAttemptService).reset("user@test.com")`
추가, 세 실패 경로(토큰 무효/사용자 없음/계정 비ACTIVE)에는 `verify(loginAttemptService, never()).reset(...)`
추가. **`AuthServicePasswordResetMariaDbIT.재설정_전에_로그인_잠금이_걸려있었어도_재설정에_성공하면_잠금이_풀린다`
(신규, Testcontainers+실 Redis)** — 실제 `LoginAttemptService.recordFailure()`를 5회 호출해 잠금을
만든 뒤 `AuthService.resetPassword()`를 실행하고, `isLocked()`가 실제로 false로 뒤집히는지 실 Redis로
확인한다(Mockito는 `reset()` 호출 여부까지만 증명하고, 그 호출이 실제로 `isLocked()`의 판정을
뒤집는지는 증명하지 못한다 — 같은 키를 참조하는지, delete가 아니라 예컨대 TTL만 건드리는 식으로
어긋나 있지는 않은지는 실제 Redis 라운드트립으로만 확인된다). **2026-09-23, Docker가 가동 중인
세션에서 `./gradlew test`(575 테스트)와 `./gradlew integrationTest`(15개 MariaDB IT 클래스, 64 테스트,
이 신규 케이스 포함) 둘 다 실행해 그린 확인했다.**

### SVC-USER-01 구현 결정 사항

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| `InvalidCredentialsException` 소속 | 원래 `auth.exception` | `common.exception`으로 이동 | SVC-USER-01 설계서가 `updateUser()`의 현재 비밀번호 불일치를 AUTH-01과 **같은 클래스명**으로 지정해, 도메인 전용이 아니라 여러 도메인이 함께 쓰는 COM-EXC-01 공통 예외로 승격했다. 기본 메시지도 "이메일 또는 비밀번호가 일치하지 않습니다"에서 "비밀번호가 일치하지 않습니다"로 좁혔다 — updateUser()/withdraw() 문맥엔 이메일이 없어 원래 문구가 맞지 않았고, login()의 익명성 보장(이메일 미존재/비밀번호 불일치를 구분 못 하게 함)은 문구가 아니라 "두 실패가 같은 메시지를 공유한다"는 사실에서 나오므로 이 변경으로도 그 보장은 유지된다. |
| `UpdateUserRequest`의 닉네임/비밀번호 검증 | "닉네임/비밀번호 각각 선택적"만 언급, 검증 방식은 없음 | `@ValidNickname`/`@ValidPassword`를 그대로 못 쓰고 `@ValidNicknameIfPresent`/`@ValidPasswordIfPresent`(`common/validation/`) 신설 | 기존 두 애노테이션은 null을 무효로 처리하도록 설계돼(COM-VAL-01, 회원가입처럼 필수 필드 전용) 부분 수정의 "필드 생략 시 변경 안 함" 의미와 정면으로 충돌한다. 새 애노테이션은 null만 유효로 통과시키고 non-null 값은 기존 `NicknameValidator`/`PasswordValidator`에 그대로 위임한다(로직 중복 없음). |
| **닉네임 "특수문자 제한" — 3.2절과 5.8절이 서로 다른 말을 했던 문제(해결됨, 지성 확인 완료)** | 3.2절(`updateUser()` 처리 로직)은 "2~12자·**특수문자 제한 규칙**(COM-VAL-01)을 통과한 값으로 갱신"이라고 적어 뒀지만, COM-VAL-01을 확정 정의하는 5.8절은 "2~12자"만 규정하고 문자 집합 제한은 아예 언급하지 않는다. **요구사항정의서 FR-1.1**(이메일·비밀번호·닉네임 입력/이메일 형식·중복 확인/비밀번호 단방향 암호화만 명시, 닉네임 형식 제약 없음)과 **UI정의서 5.1절 AUTH-02 예외처리표**("비밀번호 정책 미충족" 행만 있고 닉네임 관련 행 자체가 없음) 어디에도 "특수문자 제한"의 근거가 없다 — 이 문구는 프로그램설계서 3.2절 한 곳에만 고립돼 등장한다. **결론(지성 확정): 3.2절의 "특수문자 제한 규칙" 문구는 COM-VAL-01이 5.8절로 확정되기 전 초안 단계에서 남은 낡은 서술이며, 실제로 반영된 적이 없다 — 5.8절(2~12자, 문자 제한 없음)이 확정 스펙이 맞다.** 3.2절 쪽을 5.8절과 일치하도록 갱신하는 것이 다음 문서 동기화 시점의 할 일이다. | `NicknameValidator`/`NicknameIfPresentValidator`는 길이(codePointCount 2~12)만 검사하고 문자 집합 검사는 없다. `UserService.changeNickname()`도 추가 검증 없이 그대로 대입한다 — **이 구현이 맞는 스펙(5.8절)을 그대로 따르고 있으므로 코드 변경 불필요.** SCR-AUTH-02 프론트(`features/auth/validation.ts`의 `isValidNickname()`)도 이 확정된 동작을 그대로 미러링해 길이만 검사한다 — 이 역시 수정 불필요 | 최초엔 "코드에 특수문자 제한이 구현/제거된 흔적이 없다"(테스트에 특수문자 케이스 없음, git log상 관련 파일을 건드린 커밋이 COM-VAL-01 최초 구현 `1a8e48b`/SVC-USER-01 `e2bc1db` 둘뿐)는 것을 드리프트 가설의 근거로 들었는데, 이건 틀린 추론이었다(지성 지적) — "3.2절이 방치된 드리프트다"와 "COM-VAL-01 구현이 3.2절 요구사항을 놓쳤다" 두 가설 모두 정확히 같은 코드 증거를 만들어내는 대칭적 증거라 코드만으로는 구분이 불가능했다. **실제 결정력 있는 근거는 상위 문서였다** — 진짜 의도된 설계였다면 요구사항정의서나 UI정의서 예외처리표 어딘가에는 흔적이 남았을 텐데, "특수문자 제한"이 3.2절 한 곳에만 고립돼 등장한다는 사실이 드리프트 가설을 사실상 확정 지었다(지성 교차 확인). 이 항목은 프론트/백엔드 어느 쪽 코드도 고칠 필요가 없다는 점에서 종료됐고, 남은 액션은 3.2절 문서 자체를 5.8절에 맞춰 갱신하는 것뿐이다. |
| `WithdrawRequest` 필드 구성 | 설계서에 검증 언급 없음(컨트롤러 시그니처만 `withdraw(UserPrincipal me, WithdrawRequest req)`) | `password`(필수, `@NotBlank`, `PasswordEncoder.matches()`로 재확인 실패 시 `InvalidCredentialsException`) + `reason`(선택, 검증 없음, 저장하지 않고 받기만 함) | UI정의서 MY-01 이벤트 정의("탈퇴 사유 확인 → 최종 확인 다이얼로그")에 `reason`이 이미 명시돼 있다. `password`는 세션 탈취·오조작으로 인한 계정 삭제 사고를 막는 통상적 방어선이며, 컨트롤러가 애초에 body를 받도록 설계된 것 자체가 빈 바디가 아니었다는 정황 증거다. `reason`을 저장할 스키마가 없어 지금은 버리고, 필요해지면 `User` row가 물리 삭제되지 않으므로 나중에 컬럼을 추가해도 된다. |
| 계정 status=ACTIVE 검사 | 예외표에 없음 | getMe/updateMe/withdraw 세 메서드 모두 **개별로는 추가하지 않음 — [처리완료 2026-09-22]** COM-SEC-01 레벨(`JwtAuthenticationFilter`)의 `user:status` Redis 캐시 체크가 모든 인증 경로에 일괄 적용돼 이 세 메서드도 자동으로 보호된다(위 "COM-SEC-01/02 실시간 회원 상태 체크" 절 참고). |

### BAT-USR-01 / SVC-AUTH-01.reactivate() 탈퇴 계정 자동 파기와 탈퇴 철회 (신규 제안 — 프로그램목록서·설계서 미반영)

**이 작업 전체가 프로그램목록서·프로그램설계서 어디에도 없다** — 방침(`/privacy`)이 약속할 수 있는 실제 자동 파기와 실제 탈퇴 철회를 만들기 위해 추가한 신규 제안 범위다. 용어: **파기** = `user` 행 물리 삭제(자식 테이블은 DB의 FK CASCADE), **철회** = 유예기간 안에 `WITHDRAWN → ACTIVE`로 복구. 완료 후 프로그램목록서에 BAT-USR-01·`POST /api/auth/reactivate`를 추가해야 한다.

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| 유예기간 N일과 파기·철회 경계 | 없음(설계서 3.2절 withdraw는 소프트 삭제만 다룸) | `homesense.withdrawal.grace-days`(환경변수 `WITHDRAWAL_GRACE_DAYS`, 기본 **7**, `@Min(1)`, 잘못된 값은 기동 시 fail-fast). 경계는 하나의 threshold(`now - N일`)로 정의한다: **파기 대상 `withdrawn_at <= threshold`, 철회 가능 `withdrawn_at > threshold`** — 서로 겹치지도 비지도 않는다(정확히 N일 경과는 파기 쪽, 1초라도 덜 지났으면 철회 쪽). 두 조건 모두 `WithdrawalPolicy`가 계산한 같은 값을 쓴다 | 경계를 두 곳에서 따로 계산하면 정확히 N일에 "파기도 철회도 안 되는 행"이나 "둘 다 되는 행"이 생긴다. 검증: `WithdrawnUserPurgeMariaDbIT.파기와_철회의_조건부_문은_경계에서_서로_배타적이다`, 조건을 `<`로 바꿔 보면 IT 2건이 실제로 실패함을 확인했다(변형 검증). |
| 유예 중 소유 데이터·토큰 | 없음 | 유예 중에는 관심/알림설정 등 어떤 소유 데이터도 건드리지 않는다. 탈퇴 시 폐기된 `refresh_token`은 철회 후에도 **폐기 상태를 유지**하고 새 토큰을 발급한다(절대 되살리지 않음) | 탈퇴 = "이 토큰으로는 더 이상 못 들어온다"는 보증이라 철회가 그 보증을 되돌리면 안 된다. 철회하면 관심/알림설정이 그대로 복구되는 것은 `AuthServiceReactivateMariaDbIT`가 커밋된 DB 상태로 확인한다. |
| 파기 방식 — DDL 변경 없음, `batch_log` 재사용 금지 | 없음 | `UserRepository.deleteIfPurgeable()`(조건부 JPQL DELETE 1문, `@Modifying(flushAutomatically = true, clearAutomatically = true)`)이 `status='WITHDRAWN' AND withdrawn_at <= threshold`를 다시 확인하며 지우고, 자식 6개 테이블은 DB ON DELETE CASCADE가 지운다. **신규 테이블·컬럼·인덱스는 추가하지 않았다(`schema_all.sql` 무수정).** `batch_log`는 수집 전용 스키마(housing_type/lawd_cd 등 NOT NULL)라 쓰지 않고 구조화 로그 1줄(`purged`/`skipped`/`failed`/`malformedWithdrawn`/`graceDays`/`durationMs`)로 남긴다 | FK 전수 확인 결과(아래 표)가 전부 CASCADE/SET NULL이라 애플리케이션이 자식을 일일이 지울 필요가 없다. **무효화 조건:** user를 참조하는 새 테이블이 `ON DELETE RESTRICT/NO ACTION` FK로 추가되면 파기가 그 회원에서 계속 실패한다 — 새 테이블을 추가할 때 이 FK 규칙을 반드시 확인하라. |
| 파기 반복 구조 | 없음 | `WithdrawnUserPurgeScheduler`(`batch.scheduler`, `@Scheduled` 진입점 + 반복문, **트랜잭션 없음**)가 100건 청크를 user_id keyset(`afterId`)으로 순회하며 `WithdrawnUserPurgeService.purgeOne()`(다른 빈, `@Transactional`)을 **사용자 1명당 별도 트랜잭션**으로 부른다. 한 명이 실패해도 ERROR 로그(userId만) 후 다음으로 진행하고 커서가 앞으로만 가서 반드시 끝난다 | 같은 클래스 안에서 `@Transactional` 메서드를 부르면 프록시를 거치지 않아 무력화된다(self-invocation) — `ComplexDetailCache` 분리와 같은 이유. 전체를 한 트랜잭션으로 묶으면 중간 실패 시 전부 롤백되고 진행 상황을 외부에서 볼 수 없다(`TradeRematchRunner`에서 이미 겪은 실수). |
| 경합 처리 — 분산락 미도입 | 없음 | 파기·철회 모두 조건부 UPDATE/DELETE의 **affected rows**로 판정한다. 파기 `0` = 그 사이 철회됐거나 이미 삭제됨(skipped), 철회 `0` = 유예기간 만료 또는 그 사이 파기됨(410 `REACTIVATION_PERIOD_EXPIRED`). 다중 인스턴스 락은 두지 않는다 | 두 문장이 같은 행에서 서로 배타적이고(IT로 확인) 파기는 멱등이라 중복 실행에도 안전하다. **무효화 조건:** 다중 인스턴스 배포에서 같은 배치가 동시에 도는 것 자체(중복 부하·중복 로그)가 문제가 되면 그때 락(예: ShedLock)을 검토하라. |
| 스케줄 설정과 로컬 비활성 | 없음 | `homesense.withdrawal.purge.enabled`(기본 true)/`purge.cron`(기본 `0 0 5 * * *`, KST). `enabled=false`이면 `@ConditionalOnProperty`로 **스케줄러 빈 자체가 등록되지 않는다**. `application-local.properties`와 테스트 컨텍스트(`src/test/resources/application.properties`)는 false | 로컬 DB의 테스트 계정이 며칠 뒤 조용히 삭제되는 것을 막는다. 05:00은 03:00에 시작하는 수집→알림→메일 체인과 겹치지 않게 잡은 값이다. 빈 부재는 `WithdrawnUserPurgeSchedulerConditionalTest`가 검증한다. |
| 철회 API 위치·자동 로그인 | 없음 | `POST /api/auth/reactivate`(`AuthController`, body `ReactivateRequest(email, password)`, 응답 `ApiResponse<LoginResponse>`). 탈퇴 계정은 로그인할 수 없어 인증 전 요청이므로 login/signup처럼 `anyRequest().permitAll()`을 탄다(`AuthEndpointSecurityTest`가 실제 필터 체인으로 확인, 대조군 logout은 401). 성공하면 `loginInternal()`을 재사용해 자동 로그인한다. 자격 검증(잠금 확인 → 정규화 이메일 조회 → BCrypt → 실패 카운트 증가/초기화)은 **`login()`과 같은 private `authenticate()`를 공유**한다. 검증 통과 후 분기: ACTIVE → 409 `ACCOUNT_NOT_WITHDRAWN`, SUSPENDED → 403 `ACCOUNT_SUSPENDED`(정지 계정은 자가 복구 불가), WITHDRAWN → 조건부 UPDATE(`reactivateIfWithinGrace`, `updatedAt` 직접 세팅 — JPQL 벌크 UPDATE는 auditing 우회) → 0건이면 410 | 상태는 비밀번호를 아는 사람에게만 알려준다. 로직을 복사하지 않고 공통 메서드로 뽑아 잠금·카운트 규칙이 두 경로에서 어긋나지 않게 했다. 예외는 전부 `BusinessException` 상속(개별 `@ExceptionHandler` 없음). |
| **`login()` 검증 순서 변경 — 설계서 이탈** | 설계서 3.1절: status 검사가 비밀번호 검증보다 앞 | **비밀번호 검증 → status 검사** 순으로 바꿨다. 비밀번호가 틀리면 상태와 무관하게 `InvalidCredentialsException`이다. `AccountNotActiveException`은 403을 유지하되 errorCode를 **`ACCOUNT_NOT_ACTIVE` → `ACCOUNT_WITHDRAWN`/`ACCOUNT_SUSPENDED`로 분리**했다. 문구: 탈퇴 "탈퇴 처리된 계정입니다.", 정지는 기존 문구 유지. `refreshAccessToken()`의 상태 검사도 같은 예외(상태별 코드)를 쓴다 | 기존 순서는 비밀번호를 몰라도 "탈퇴/정지 계정"임이 드러나 설계서 자신의 "계정 존재 여부 비노출" 원칙과 어긋났다. **탈퇴 문구에 "철회할 수 있습니다"를 넣지 않았다** — 철회 UI가 아직 없어 그렇게 쓰면 또 하나의 허위 안내가 된다(테스트가 문구에 "철회"가 없음을 단언). 프론트는 `ACCOUNT_NOT_ACTIVE` 코드를 참조하지 않아(grep 확인) 코드 분리로 깨지는 곳이 없다. **설계서 3.1절을 이 순서로 갱신해야 한다.** |
| 시간 소스 — 공용 KST `Clock` 도입(신규 판단) | 없음(`User.withdraw()`는 JVM 기본 타임존 `LocalDateTime.now()`를 썼다) | `common/config/ClockConfig`가 KST `Clock` 빈을 등록하고, `WithdrawalPolicy`(`user.service`)가 `now()`/`graceThreshold()`를 제공한다. **탈퇴 기록(`User.withdraw(LocalDateTime)`), 파기, 철회가 모두 같은 소스를 쓴다.** 테스트는 `Clock.fixed`로 "정확히 N일"·"1초 덜"을 결정적으로 만든다 | `DATETIME`은 타임존 정보가 없어 기록은 JVM 기본(UTC일 수 있음)으로, 비교는 KST로 하면 유예기간이 9시간 어긋난다. 이 프로젝트의 다른 클래스는 아직 각자 KST 상수를 쓰는데 이 빈은 그것을 대체하지 않는다(위 "날짜/시간 처리"). **알려진 한계:** JVM 기본 타임존이 KST가 아닌 환경에서 이 변경 **이전에** 기록된 `withdrawn_at`은 옛 소스 기준이다 — 아직 백엔드 배포가 없어(로컬 전용) 영향 없음. 또 `RefreshToken` 만료 시각과 auditing의 `created_at`/`updated_at`은 여전히 JVM 기본 타임존이다. |
| **착수 전 FK 전수 확인(사실)** | 테이블정의서 8장: user 참조 FK 6건 | `information_schema`(`REFERENCED_TABLE_NAME='user'`, `REFERENTIAL_CONSTRAINTS`)로 확인: `refresh_token`·`favorite_property`·`favorite_region`·`recent_view`·`notification_setting`·`notification` 6건, **전부 `ON DELETE CASCADE`**. 자식끼리는 `notification_setting → favorite_property/favorite_region` CASCADE, `notification → complex/legal_district_code/trade`는 SET NULL(부모는 그대로 남음). **RESTRICT/NO ACTION 없음.** FK 없이 user 정보를 저장하는 컬럼도 없다(엔티티 전수 grep): `search_log`에는 user 컬럼 자체가 없어 손댈 것이 없다(근거: 엔티티·DDL·라이브 DB 조회 모두). user 소유 Redis 키는 `login:fail:{email}`(TTL 5분, 슬라이딩) 하나뿐이라 정리하지 않는다 | **확인은 두 곳에서 했다.** (1) 일회용 MariaDB 10.11 컨테이너에 저장소 `schema_all.sql`을 적용해서 조회했다(`schema_all.sql`이 `search_log`를 포함해 12개 테이블 전부가 대상이었다). (2) **2026-09-21 실행 중인 로컬 DB(3307)에서 같은 `information_schema` 조회로 재확인했다**(읽기 전용, `backend/.env`의 자격증명을 프로세스 환경변수로만 써서 값은 어디에도 남기지 않았다): user 참조 FK 6건 전부 CASCADE, `user_id`/`email` 컬럼을 가진 테이블은 `user`와 그 6개 자식뿐, 전체 FK 17건이 CASCADE 11 / SET NULL 6이며 RESTRICT/NO ACTION 없음, `search_log`는 존재하고 컬럼이 `search_log_id`/`keyword`/`searched_at` 세 개뿐이다(엔티티 `SearchLog`·`schema_all.sql`·라이브 DB 세 곳이 일치). 라이브 DB가 DDL과 어긋나 있지 않음이 확인됐다. IT 픽스처 `withdrawn-user-purge-schema.sql`은 `schema_all.sql`에서 스크립트로 추출한 원문이다(FK 규칙이 같음). |
| 방침 문구 동기화 | 방침 2항은 "자동 파기 없음, 수동 삭제 요청"이었다 | `/privacy` 2항을 즉시 비활성화 → N일 보관(삭제 안 함) → 자동 파기(파기 대상: 계정정보·관심 매물/지역·알림 설정·이력·최근 조회 이력·인증 토큰)로 고쳤다. **N은 프론트 상수 `withdrawalGraceDays`(=7)로 두고 백엔드 기본값과 같아야 한다**(저장소 밖 검증 스크립트 `privacy-withdrawal-purge-check`가 `application.properties`의 기본값과 대조). 보호책임자를 통한 즉시 삭제 안내는 유지(수동). 6항의 "2항에 안내된 것과 같이 자동화되어 있지 않고"는 즉시 삭제 요청을 가리키도록 고쳤다. **"직접 철회할 수 있다"는 문구는 넣지 않았다**(UI 없음) | 방침 문구는 배치가 배포된 뒤에만 참이므로 **백엔드와 같은 PR로 동시 배포**한다. **무효화 조건:** 배포 환경에서 `WITHDRAWAL_GRACE_DAYS`를 7이 아닌 값으로 설정하거나 기본값을 바꾸면 방침 상수도 함께 바꿔야 한다. 개정 이력(13항)·시행일은 이번 작업에서 갱신하지 않았다(아래 완결 필요). |
| 유예 중 같은 이메일 가입 | 없음 | **변경하지 않았다** — 유예 기간(탈퇴 후 N일) 동안 `existsByEmail()`가 WITHDRAWN 행도 세므로 같은 이메일 가입은 "이미 사용 중"(409)으로 응답한다. 방침 2항에 "같은 이메일로 다시 가입하려면 보관 기간이 지나 계정이 파기된 뒤에 가능합니다"를 그대로 적었다 | 요청에서 변경 금지로 못 박은 기존 동작이다. 사용자에게는 불편할 수 있으니(철회 UI가 생기면 "철회 CTA"가 자연스러운 출구가 된다) 이 동작의 UX를 다음에 논의하라. |

**구현 시점에 지킬 것(체크리스트):**
- [ ] **BAT-NTF-01/BAT-MAIL-01 구현 시** (BAT-NTF-01은 2026-10-06 ACTIVE 회원만 대상으로 구현 완료 — BAT-MAIL-01이 남았다) `status=ACTIVE` 회원만 대상으로 하고(유예 중 WITHDRAWN 계정에 알림 발송 금지), 실행 시간을 05:00 파기와 겹치지 않게 조정한다(`WITHDRAWAL_PURGE_CRON` 또는 알림 스케줄 쪽).
- [ ] **AdminService `updateUserStatus`(5단계)가 상태를 `WITHDRAWN`으로 바꿀 때 `withdrawn_at`을 반드시 세팅한다** — `WITHDRAWN`인데 `withdrawn_at`이 NULL이면 파기가 그 행을 영영 건드리지 않고 WARN(`malformedWithdrawn`)만 남긴다. 세팅은 `WithdrawalPolicy.now()`를 쓴다.
- [x] **`user:status` Redis 필터 도입** — 2026-09-22 완료. withdraw/reactivate는 각각 `UserService.withdraw()`/`AuthService.loginInternal()`에서 키를 갱신한다. purge는 갱신하지 않기로 확정했다(TTL이 유예기간보다 훨씬 짧아 파기 시점엔 이미 자연 만료됨 — 아래 "COM-SEC-01/02 실시간 회원 상태 체크" 절 참고).
- [ ] **MY-01 탈퇴 확인 모달**에 유예기간(N일)·자동 파기 고지가 있어야 한다(현재 MY-01은 미구현 — 방침과 같은 N을 쓴다).
- [ ] **철회 UI**(AUTH-01의 `ACCOUNT_WITHDRAWN` 에러코드 분기 + 철회 CTA)는 Figma 프레임이 준비된 뒤 후속 작업이다. 이 UI가 생기면 방침 2항·`login()`의 탈퇴 문구에 "철회할 수 있습니다" 안내를 추가할 수 있고, 그때 위 "유예 중 같은 이메일 가입" UX도 함께 정리하라.
- [ ] **배포 시 JVM 기본 타임존을 `Asia/Seoul`로 고정한다**(`-Duser.timezone=Asia/Seoul` 또는 컨테이너 `TZ=Asia/Seoul`). 공용 KST `Clock`은 `withdrawn_at`·파기·철회만 KST로 맞춘다 — `created_at`/`updated_at`(auditing)과 `RefreshToken` 만료 시각은 여전히 JVM 기본 타임존이라, UTC가 기본인 EC2에 그대로 올리면 같은 `user` 행에서 `withdrawn_at`(KST)과 `updated_at`(UTC)이 9시간 어긋난다. JDBC URL의 `serverTimezone=Asia/Seoul`은 이 문제를 해결하지 않는다(애플리케이션이 만든 `LocalDateTime` 값 자체는 그대로 저장된다). JVM 타임존을 고정하면 이 한계가 한 번에 정리된다.
- [ ] **방침의 `withdrawalGraceDays`(=7)와 백엔드 `WITHDRAWAL_GRACE_DAYS`는 항상 같아야 한다 — 어긋나면 방침이 거짓이 된다.** 운영 환경에서는 이 환경변수를 바꾸지 않는 것이 가장 안전하다(바꿔야 한다면 `privacyPolicySections.tsx`의 상수와 `/privacy` 2항을 같은 배포에서 함께 바꾼다). 실서비스를 열 때 prod에서 `WITHDRAWAL_PURGE_ENABLED=true`와 `WITHDRAWAL_GRACE_DAYS=7`이 실제로 적용됐는지 확인한다(`WithdrawnUserPurgeScheduler` 첫 실행 로그의 `graceDays`/`purged`/`failed`로도 확인된다).
- [ ] **방침과 배치의 배포 시점 어긋남.** Vercel Production Branch가 `develop`이라(지성 확인) 머지하면 `/privacy` 문구가 곧바로 hmss.site에 반영되지만 백엔드 배포는 자동으로 같이 나가지 않는다 — "같은 릴리스"가 보장되지 않는다. 백엔드가 아직 배포 전이라 지금은 실해가 없다. **실가입을 받기 전에** 배치가 prod에서 실제로 도는지 확인하고, 그 전까지는 방침의 자동 파기 문구가 사실이 아닐 수 있음을 기억하라.
- [x] **`/privacy` 개정 이력·시행일 확정.** 2항이 실질적으로 바뀌었지만 v1.3 행과 시행일은 아직 없다(임의로 확정하지 않았다). 실가입을 받기 전에 시행일을 확정하고, 그 시점에 변경 고지가 필요한지 다시 확인하라 — 법적 세부는 그때 조문을 직접 확인한다. → 완료(2026-09-29): v1.3 행 신설, 시행일 2026-09-29.

**완결 필요(이 작업이 남긴 것):**
- ~~**`/privacy` 개정 이력(13항)·시행일**~~ — **완료(2026-09-29).** v1.3 행을 신설하고 시행일을 2026-09-29(PR #55 머지일)로 확정했다. 7일 사전 고지 예외 적용 근거와 무효화 조건은 위 체크리스트 항목과 SCR-LEGAL-01 "Refresh Token 블랙리스트 표현" 행을 본다.
- **프론트엔드 철회 화면** — API는 있지만 호출하는 화면이 없어 이용자가 실제로 철회할 방법이 없다(방침도 그렇게 서술한다).
- **파기 실행의 실환경 관측** — IT는 Testcontainers 위에서 통과했지만, 실제 운영 규모의 삭제 시간·부하는 아직 관측한 적 없다(배포 후 첫 실행의 `durationMs`/`failed`를 확인하라).

### SVC-CPX-01 구현 결정 사항

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| `getDetail()`의 SVC-RCV-01.record() 호출 — **완결 필요** | 처리 로직에 명시(설계서 3.6절 "RCV 도메인과 협력", 2.2절 "계층 협력 원칙") | **이번 범위에서 제외** | RCV 도메인은 엔티티/레포지토리(`RecentView`)만 있고 서비스 계층이 없다(지성 확인). RCV-01을 구현하는 시점에 3.6절/2.2절 원문대로 `ComplexService.getDetail()`에 이어붙여라 — 이 표는 그 전까지 미완결 상태임을 나타내는 표식이다. |
| `getPopular()`의 "인기" 정의 | 명시 없음(카운터 컬럼도 스키마에 없음) | **최근 3개월 취소되지 않은 거래량 기준**(`TradeRepository.findTopComplexIdsByRecentTradeVolume`), 이 기준만으로 limit을 못 채우면 UI정의서 5.1절 HOME-01의 "최신 등록 단지" 대체 노출 문구대로 `complex_id DESC`(서로게이트 PK, AUTO_INCREMENT)로 나머지 자리만 패딩(`ComplexRepository.findAllByOrderByComplexIdDesc`, 이미 거래량 기준에 뽑힌 단지는 제외) | RCV/FAV 모두 서비스 계층이 없어 조회수·찜수 기반은 지금 구현 불가능하다(지성 확인). trade 테이블은 이미 데이터가 있어 자기완결적으로 구현 가능. 윈도우(3개월)는 지성 확인. **fallback 기준은 처음엔 `data_updated_at`으로 구현했다가 되돌렸다** — 그 컬럼은 "언제 갱신됐는가"가 아니라 원본 xlsx 스냅샷 기준일이고, 단지 기본정보가 파일 1건을 통째로 적재한 지금은 사실상 전체 row가 같은 값이라 정렬 기준으로 무의미했다(코드리뷰에서 지적됨, 최초 IT 테스트가 fixture마다 이 값을 인위적으로 다르게 넣어 이 동점 문제를 가리고 있었다는 것도 함께 지적됨). complex_id는 등록 순서를 보장하는 유일한 컬럼이라 이걸로 교체했고(지성 확인), IT 테스트도 두 fixture가 같은 data_updated_at을 갖도록 고쳐 실제 운영 데이터 상황을 재현하게 했다. |
| `search()` 정렬(최신순/금액순/면적순)의 대표 거래 | 명시 없음(세 기준이 서로 다른 거래를 가리킬 수 있음을 시사) | **세 기준 모두 "검색조건을 만족하는 거래 중 가장 최근 거래" 하나로 통일**(`ComplexRepositoryCustomImpl`의 2단 상관 서브쿼리: MAX(deal_date) → 그 날짜 안에서 MAX(trade_id)) | 지성 확인. 기준마다 다른 대표 거래를 고르면 서브쿼리 3벌이 필요해 복잡도가 크게 늘어난다. **동률(같은 날짜 여러 건) 시 단지가 검색 결과에 여러 행(카드 여러 장)으로 중복 노출되는 문제는 처음엔 "드문 엣지케이스"로 문서화하고 넘어갔으나, `ComplexSummaryResponse`가 전제하는 "단지 1건 = 카드 1장" 자체와 충돌한다는 코드리뷰 지적을 받아 근본 수정했다** — MAX(trade_id)로 한 번 더 좁혀 대표거래가 항상 정확히 하나만 나오게 했다(검증: `ComplexRepositoryMariaDbIT.대표거래_후보가_같은_날짜로_동률이어도_단지당_정확히_한_행만_나온다`). 이 2단 tie-break 위에, 서로 다른 단지끼리 정렬값(금액·면적)이 동률일 때를 위한 `complex.complexId.asc()` 페이지 tie-break가 별도로 얹혀 있다(코드리뷰에서 지적된 페이지 경계 안정성 이슈 — 검증: `ComplexRepositoryMariaDbIT.정렬_기준이_동률이어도_페이지_경계에서_단지가_중복되거나_누락되지_않는다`). |
| "거래금액" 필터/정렬 대상 컬럼 | "거래금액 범위"라고만 언급, SALE/RENT 구분 없음 | dealCategory가 RENT면 `depositAmount`(보증금), 그 외(SALE·미지정)면 `dealAmount` | 매매와 전월세는 금액 구조 자체가 달라(매매금액 vs 보증금+월세) 하나의 "금액"으로 합산할 기준이 없다. monthlyRentAmount는 별도 필터로 다루지 않는다. |
| `MapFilterRequest` 필드 구성 | 필드 구성 명시 없음 | `housingTypes`(다중, 선택)만 지원 | 지도 조회는 팬/줌마다 자주 나가는 가벼운 쿼리라 search()의 면적/금액/건축년도 범위까지 그대로 옮기면 무거워진다. 필요해지면 추가하라. |
| `GET /popular`의 limit 상한 | Controller 표엔 "기본 8건"만 명시, 상한 없음 | **1~50**(`ComplexController.MIN/MAX_POPULAR_LIMIT`), 벗어나면 `InvalidLimitException`(400) | Codex 코드리뷰(P1) 대응 — limit을 검증 없이 그대로 받으면 `getPopular()`가 값마다 최대 2건씩 추가 쿼리를 내는 N+1 경로라(아래 기술부채 항목 참고) 인증 없는 이 엔드포인트가 자원 고갈 벡터가 됐다. 50은 `map()`의 500건 상한과 같은 성격의 임의 기본값이라, 실제 트래픽을 보고 나중에 조정될 수 있다. |
| `GET /search`/`GET /map` 응답 타입 | Controller 표는 각각 `PageResponse<T>`/`List<T>` | `ApiResponse<List<ComplexSummaryResponse>>`(기존 `ApiResponse.success(Page<T>)` 관례 재사용) / `ApiResponse<ComplexMapSearchResponse>`(points+truncated 래퍼 신설) | `PageResponse<T>`라는 클래스는 프로젝트 어디에도 없다 — COM-RES-01이 이미 정의한 `data`+`pageMeta` 관례로 충분해 새로 만들지 않았다. `map()`은 처리 로직이 명시한 truncated 플래그를 실을 자리가 순수 `List` 반환 타입엔 없어(ApiResponse도 pageMeta 외 별도 슬롯이 없다) `ComplexMapSearchResponse`로 감쌌다. |

이 도메인은 프로젝트에서 QueryDSL을 실제로 쓰는 첫 지점이다(`common/config/QuerydslConfig`가 `JPAQueryFactory` 빈을 처음 등록). `ComplexRepositoryCustomImpl`의 동적 쿼리(특히 대표 거래를 고르는 상관 서브쿼리)는 Mockito로 검증할 수 없는 종류의 로직이라 `ComplexRepositoryMariaDbIT`(Testcontainers)로 별도 검증한다 — 위 "UNIQUE 제약 동시성 회귀 테스트 원칙"과 같은 이유로, Repository를 목킹한 `ComplexServiceTest`는 "Service가 Repository를 올바르게 호출하는지"만 증명하고 "그 쿼리가 실제로 맞는 결과를 내는지"는 증명하지 못한다.

**알려진 기술부채 — `getPopular()`의 N+1 쿼리 구조.** `search()`는 QueryDSL 상관 서브쿼리로 단지+대표거래를 한 번의 쿼리로 가져오지만, `ComplexService.buildSummary()`는 candidate complex_id마다 `findById` + 대표거래 조회를 개별로 날린다(limit당 최대 2건). limit을 1~50으로 막아둔 지금은 최악의 경우도 100쿼리 남짓이고 `popularComplexes` 캐시(TTL 24h)가 있어 캐시 미스일 때만 발생해 당장 막을 이유는 없다(Codex 코드리뷰에서 지적됐지만 지금 손대지 않기로 함) — 나중에 손볼 때는 search()처럼 QueryDSL 서브쿼리 하나로 통합하는 방향을 검토하라.

### SVC-TRD-01 구현 결정 사항

프로그램 설계서가 상세히 기술하지 않았거나 미확정으로 남겨둔 세부 사항을 구현 시점에 확정한 내용이다. **아래 항목 다수가 "지성 확인" 표시 없이 구현됐다 — 다음에 이 도메인을 다시 볼 때 반드시 검토가 필요하다.**

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| `GET /search` 응답 타입 | Controller 표는 `ApiResponse<PageResponse<TradeSummaryResponse>>` | `ApiResponse<List<TradeSummaryResponse>>`(기존 `ApiResponse.success(Page<T>)` 관례 재사용) | SVC-CPX-01과 같은 이유 — `PageResponse<T>`라는 클래스는 프로젝트 어디에도 없다. |
| `TradeSearchCondition`/`TradeSearchRequest` 필드 구성 — **완결 필요(RGN-01 착수 시 하드 체크)** | "지역/조건 기반 통합 검색"이라고만 언급, 필드 나열 없음. 다만 활용 인덱스가 `idx_trade_legal_dong_deal_date`(legal_dong_cd, deal_date)로 명시돼 있고, 이는 CPX-01 search()의 `idx_complex_region`(sido, sigungu, dong_ri)과 다르다 — 두 도메인이 원래 지역 필터링 방식을 다르게 설계했다는 뜻이다(TRD=코드 기반, CPX=텍스트 기반) | `legalDongCd`(단일 코드) + `housingTypes`(다중) + `dealCategory` + `rentType` + 전용면적/금액 범위 + `TradeSortCondition`(LATEST/AMOUNT/AREA, 기본 LATEST) | `legalDongCd` 방향 자체는 idx_trade_legal_dong_deal_date와 정합돼 근거가 있다(지성 확인, 코드리뷰). 다만 두 가지는 RGN-01 착수 전까지 미확정 상태로 남는다 — **RGN-01 처리 로직은 "시도>시군구>읍면동 전체 경로 문자열 조립"만 명시하고 RegionAutocompleteResponse가 legalDongCd 필드를 포함한다는 문장이 없다: RGN-01 구현 시 이 DTO에 legalDongCd를 반드시 포함시켜야 지금 가정이 성립한다.** **또한 SRCH-01 화면의 "필터 적용" 이벤트는 설계서상 `GET /api/complexes/search`를 호출하고, `GET /api/trades/search`는 "관련 API(예시)"로만 언급돼 TradeService.search()가 정확히 어느 시나리오에서 호출되는지 설계서에 명시가 없다 — SRCH-01이 실제로 이 API를 호출하는 시나리오(리스트형 보기 토글 등)를 확인해야 한다.** |
| `TradeSortCondition`을 `complex.dto.SortCondition`과 별도 클래스로 분리 | 명시 없음 | trade 도메인에 `TradeSortCondition`/`InvalidSortConditionException`을 새로 만들고 complex 쪽 것을 재사용하지 않음 | complex는 이미 트레이드 엔티티(Trade/HousingType/DealCategory)에 의존한다 — trade가 complex.dto.SortCondition을 가져다 쓰면 양방향 패키지 의존이 생겨 "도메인별 수직 패키지" 원칙(CLAUDE.md 패키지 구조 절)과 어긋난다. |
| `search()`의 취소된 거래(cancel_yn=true) 포함 여부 — **지성 확인 필요** | 명시 없음 | 제외(`cancelYn=false`만) | 매물 목록에 취소된 거래를 대표가로 그대로 노출하면 사용자가 실거래가로 오인할 소지가 있어 제외했다(지성 확인 필요) — ComplexRepositoryCustomImpl.tradeFilters()와 판단은 같은 방향이지만, CPX.search()는 trade를 집계용으로만 쓰고 cancel_yn을 아예 언급하지 않아 직접 비교 대상은 아니다(코드리뷰에서 지적됨). getHistory()는 반대로 취소 건도 포함하도록 설계서가 명시하고 있어(isCancelled 플래그) 대비된다. |
| `search()`의 `amountPath()`가 RENT를 판단하는 기준 | 명시 없음 | `dealCategory == RENT`뿐 아니라 `rentType != null`도 RENT로 취급 | `TradeSearchCondition`은 `dealCategory`와 `rentType`을 독립적으로 선택 가능한 필드로 뒀는데(레코드가 서로의 nullable 여부를 강제하지 않음), 처음엔 `amountPath()`가 `dealCategory`만 보고 판단해 `rentType=WOLSE`만 지정하고 `dealCategory`를 생략한 호출(예: 프론트가 "월세" 탭만 선택)에서 RENT 행이 보통 NULL인 `dealAmount`를 기준으로 걸러져 금액 범위 필터가 항상 0건을 내고 `sort=AMOUNT`도 `TradeSummaryResponse`가 실제로 노출하는 `depositAmount`가 아니라 `dealAmount`로 정렬되는 버그가 있었다(Codex 코드리뷰 P2 — 검증: `TradeRepositoryMariaDbIT.search_dealCategory_없이_rentType만_지정해도_depositAmount로_필터링된다`, `...AMOUNT_정렬이_depositAmount_기준이다`). |
| WOLSE(월세)의 "거래금액" 필터 범위 — **확정(지성 확인)** | 명시 없음(FR-3.3은 "보증금과 월세금액을 함께 제공"만 요구, 필터 슬라이더가 어느 컬럼을 기준으로 삼을지는 언급 없음) | `TradeSearchCondition`엔 `monthlyRentAmount` 필터 필드가 없다 — WOLSE도 `amountMin`/`amountMax`는 오직 `depositAmount`(보증금) 기준이고 월세금액은 필터링에 관여하지 않는다. **단, 이는 필터 범위 결정과 별개로 응답 노출(FR-3.3) 문제는 아니다** — `monthlyRentAmount`는 `TradeSummaryResponse`/`TradeResponse`/`TradeDetailResponse` 세 응답 모두에 표시용 필드로 이미 포함돼 있어(각각 `t.getMonthlyRentAmount()`로 매핑) WOLSE 거래는 검색 결과·이력·상세 어디서든 월세금액이 항상 함께 내려간다 — FR-3.3 충족 확인됨(지성 확인) | 필터 기준은 SVC-CPX-01의 기존 결정("매매/전월세는 금액 구조가 달라 하나의 '금액'으로 합산할 기준이 없다")을 이어받았고, 국내 부동산 서비스의 "거래금액" 슬라이더가 보통 보증금만 기준으로 삼는 관행과도 맞아 이대로 확정한다. 필터 대상 여부("걸러내는 기준")와 응답 노출 여부("보여주는 값")는 서로 다른 요구사항이라 별도로 검증했다. |
| `getHistory()`의 `housingType` 파라미터 — **완결 필요(재검토 대상)** | Controller 표: `getHistory(Long complexId, String housingType, String dealType)`(3개), Service 표: `getHistory(Long complexId, DealTypeFilter dealType)`(2개) — housingType이 Service 표에서 빠져 있다. 게다가 getHistory 처리 로직 4개 항목(complexId 필수검증/deal_category+rent_type 필터/deal_date DESC 정렬/cancel_yn 처리) 어디에도 housingType 필터링 언급이 없다 | 잠정: `TradeService.getHistory(Long complexId, HousingType housingType, DealTypeFilter dealType)`로 Service 시그니처를 3개로 확장해 필터에 그대로 반영 | complexId로 이미 단지가 고정되고 그 단지의 housing_type도 사실상 고정값이라, housingType 파라미터가 getHistory 맥락에서는 애초에 불필요해서 Service 표에서 빠졌을 가능성이 있다(단순 누락이 아니라 의도적 축소일 수 있음, 코드리뷰에서 제기). **재검토 시 대안: Service는 설계서 그대로 2인자를 유지하고, Controller가 받은 housingType은 필터가 아니라 "해당 단지의 실제 유형과 불일치하면 빈 결과 처리"용 검증에만 쓰는 방향도 검토할 것.** 지금 구현이 서비스를 깨뜨리진 않으므로 유지하되, 의도적 축소인지 단순 누락인지 확인 후 유지/제거를 결정하라. |
| `housingType`/`dealType` 값이 허용 목록 밖이면? | 예외 처리표에 `MissingComplexIdException`/`TradeNotFoundException` 둘만 명시, 값 검증 실패에 대한 언급 없음 | 예외를 던지지 않고 필터 없음(전체)으로 조용히 폴백(`DealTypeFilter.from()`, `TradeController.parseHousingType()`) | 예외 처리표가 완결돼 있다고 보고 표에 없는 예외를 임의로 추가하지 않았다 — `ComplexController`의 `SortCondition.from()`(허용 목록 밖이면 400)과는 반대 방향 선택인데, 그쪽은 명시적으로 "허용 목록"이 강조된 사용자 노출 정렬 옵션이고 이쪽은 DTL-01 탭처럼 고정된 값만 보내는 내부용 파라미터라고 판단했기 때문이다. CPX가 유사 케이스에 예외를 뒀는데 여기 안 둔 게 의도적 배제가 아니라 설계서의 단순 누락일 가능성도 있으나(코드리뷰에서 제기), 조회 API에서의 관대한 폴백은 일반적으로 안전한 선택이라 지금 처리를 유지한다. |
| `TradeSummaryResponse`/`TradeDetailResponse`의 금액 필드 구성 | "거래금액"이라고만 언급, SALE/RENT 세부 구성 없음 | `TradeSummaryResponse`는 `amount`(SALE→dealAmount, RENT→depositAmount) + `monthlyRentAmount`(WOLSE 전용) 2필드, `TradeDetailResponse`는 `dealAmount`/`depositAmount`/`monthlyRentAmount` 3필드를 원본 그대로 노출 | 목록에서는 ComplexSummaryResponse와 같은 "대표 금액 1개" 관례를 따르되 월세만 별도로 뒀고, 상세 모달은 "거래금액"이라는 단일 항목명 아래 실제로는 세 컬럼이 있어 원본 그대로 노출해 프론트가 dealCategory/rentType으로 포맷을 결정하게 했다. |
| `TradeDetailResponse.aptDongPending` 필드명 | "등기 완료 후 제공 안내 플래그"라고만 언급, 필드명 없음 | `aptDongPending`(boolean, aptDong이 NULL이면 true) | `ComplexDetailResponse.matchPending`과 같은 네이밍 관례(원인이 되는 조건을 `xxxPending`으로 표현). |
| `getHistory()`/`getDetail()`의 `isCancelled`/`isRegistered` 필드명 | 처리 로직 원문이 `isCancelled`/`isRegistered`로 직접 명명 | 그대로 사용(record 컴포넌트명 `isCancelled`/`isRegistered`, accessor도 동일) | `ComplexDetailResponse.matchPending`이나 `ComplexMapSearchResponse.truncated`처럼 이 프로젝트의 boolean 필드는 보통 `is` 접두어를 붙이지 않지만, 설계서가 이 두 값만은 명시적으로 `is` 접두어로 이름 붙여뒀어 그 지시를 그대로 따랐다. |

이 도메인도 QueryDSL 동적 쿼리(`TradeRepositoryCustomImpl`)를 쓴다 — `TradeServiceTest`(Mockito)는 "Service가 Repository를 올바르게 호출하는지"만 증명하고, 실제 필터/정렬/조인 결과는 `TradeRepositoryMariaDbIT`(Testcontainers)로 검증한다(SVC-CPX-01과 같은 원칙). trade 검색/이력 조회는 캐시를 적용하지 않는다(위 "캐싱" 절 참고) — `@Cacheable` 관련 결정 사항은 없다.

### SVC-RGN-01 구현 결정 사항

프로그램 설계서 처리 로직 원문이 상세히 기술하지 않은 세부 사항을 구현 시점에 확정한 내용이다. **아래 항목 다수가 "지성 확인" 표시 없이 구현됐다 — 다음에 이 도메인을 다시 볼 때 반드시 검토가 필요하다.**

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| `getInterestSummary()` "평균가" 대상 거래유형 — **지성 확인 필요** | "최근 평균가·전월 대비 변동률"이라고만 언급, SALE/RENT 구분 없음 | 매매(SALE)만 대상, RENT(전월세)는 제외 | SVC-CPX-01/TRD-01의 기존 "금액" 결정(매매·전월세는 금액 구조가 달라 하나의 '금액'으로 합산할 기준이 없다)을 그대로 이어받았다. |
| `getInterestSummary()` "최근/전월" 기간 정의 — **지성 확인 필요** | 캘린더 월(이번 달/지난 달) vs 롤링 윈도우 여부 명시 없음 | 캘린더 월 경계가 아니라 오늘 기준 최근 1개월(현재)과 그 이전 1개월(직전) 롤링 윈도우로 구현(`RegionStatsCalculator`) | 캘린더 월 경계를 쓰면 월초에는 그 달 거래가 거의 없어 "데이터 없음"이 빈번해지는 문제를 피하려는 실용적 선택 — 아직 사용자 확인 전이라 재검토 대상. |
| 대상 기간에 거래가 없을 때 — **지성 확인 필요** | 명시 없음 | 예외를 던지지 않고 `avgPrice`/`changeRate`를 각각 null로 반환(`RegionStats`) | 조회 API에서의 관대한 폴백은 일반적으로 안전한 선택이라는 CPX/TRD 도메인의 기존 판단과 같은 방향으로 선택했다. |
| `InterestRegionSummaryResponse` 필드 범위 — **완결됨(SVC-FAV-01 착수 시 처리)** | 이번 구현 근거로 삼은 처리 로직 원문은 "평균가·변동률" 2개만 언급 | HOME-01 전용 `InterestRegionSummaryResponse`(`favoriteRegionId`/`legalDongCd`/`fullPath`/`avgPrice`/`changeRate` 5필드)는 그대로 유지했다 — 대신 **`RegionStats`에 `pricePerPyeong`/`newTradeCount` 두 필드를 추가**하고 `RegionStatsCalculator.calculate()`가 함께 채우도록 확장했다(`TradeRepository.findAveragePricePerPyeongForSale()`/`countSaleTrades()` 신설). UI정의서 5.5절 MY-02(관심 지역 리스트)가 요구하는 이 두 값은 SVC-FAV-01의 `FavoriteRegionSummaryResponse`(`favorite.dto`)가 노출한다 | MY-02는 SVC-FAV-01 의존 화면이라 RGN-01 시점엔 아직 소비 주체가 아니었다 — 인터페이스 재설계가 아니라 필드 추가만으로 확장됐다(예고된 대로, [[svc_rgn01_interest_summary_scope]] 메모리 참고). `pricePerPyeong`은 거래 1건씩 `dealAmount / (excluUseArea / 3.3058)`로 정규화한 뒤 거래 단위로 단순 평균한다(면적 가중 합계/합계 방식이 아님) — **지성 확인 필요**, 국내 부동산 서비스의 "평당가 평균" 관행에 맞춰 잠정 결정. `newTradeCount`는 `avgPrice`와 같은 모집단(매매·미취소·최근 1개월 창)의 건수다. |

**알려진 잔여 리스크 — 읍면동 단위 표본 크기.** `favorite_region`은 시군구(약 250개)보다 훨씬 잘게 쪼개진 읍면동 단위(`legal_dong_cd`)로 등록된다. 위 롤링 1개월 윈도우 기준으로 특정 읍면동의 월 거래건수가 0~2건에 그치는 경우가 드물지 않을 것으로 보인다 — 거래가 0건이면 `RegionStats`가 null을 반환해 "데이터 없음"으로 명확히 구분되지만, 1~2건인 경우는 null도 아니고 통계적으로 신뢰할 만한 평균도 아닌 애매한 중간 지대다. 게다가 `avgPrice`가 위 항목대로 전용면적 정규화 없는 원시 평균이라, 표본이 작은 달에 우연히 어떤 평형이 거래됐는지에 따라 `changeRate`가 실제 시세 변화와 무관하게 크게 흔들릴 수 있다. 지금은 실제 데이터가 없어 최소 표본 수 하한(예: N건 미만이면 null 처리)을 임의로 정할 근거가 없어 로직을 바꾸지 않았다 — 실거래 데이터가 쌓인 뒤 "특정 지역의 변동률이 이상하게 튄다"는 문의가 들어오면 이 항목부터 확인하라. FAV-01/MY-02가 요구하는 "신규거래 건수" 필드(위 항목)가 구현되면 그 값 자체가 표본 크기를 프론트에 노출하는 신호가 될 수 있어 두 항목을 함께 검토하는 것이 좋다.

`autocomplete()`의 `searchByNameContaining()`은 `is_active=true` 조건을 포함한다(폐지된 법정동이 자동완성에 노출되지 않도록) — `deactivateAll()`/`findDistinctActiveSggCd()`와 같은 관례. **`eupmyeondongName IS NOT NULL` 조건도 반드시 함께 있어야 한다** — `LegalDistrictCodeLoader`는 시도/시군구 대표행(계층 상위 행)을 `eupmyeondongName=null`로 적재하는데, `LegalDistrictMatcher.matchByTradeSggCd()`는 `eupmyeondongName`이 국토부 API의 `umdNm`과 일치하는 행에만 거래를 매칭시킨다. 즉 대표행의 `legal_dong_cd`는 애초에 어떤 거래에도 연결될 수 없어, 이 조건 없이 "강남구"를 검색하면 그 구의 대표행(예: `1168000000`)이 후보로 나오고 그 코드를 실거래 검색이나 `getInterestSummary()`에 넘기면 항상 0건이 되는 막다른 결과를 낳는다(Codex 코드리뷰 P1로 지적됨, 최초 구현에서 이 조건이 빠져 있었다 — 검증: `LegalDistrictCodeRepositoryMariaDbIT.시군구_대표행은_제외되고_활성_읍면동_행만_후보로_반환된다`).

이 두 신규 리포지토리 쿼리 중 `TradeRepository.findAverageSaleAmount()`는 QueryDSL 동적 쿼리가 아니라 고정된 JPQL이라 `TradeRepository.findTopComplexIdsByRecentTradeVolume()`(SVC-CPX-01)과 같은 성격으로 보고 전용 MariaDB IT 없이 `RegionStatsCalculatorTest`(Mockito)로만 검증했다. 반면 `LegalDistrictCodeRepository.searchByNameContaining()`은 위 P1 버그가 정확히 "Mockito로는 절대 드러나지 않는" 종류(WHERE 절 자체가 실제 DB에서 올바른 행을 걸러내는지)라 `LegalDistrictCodeRepositoryMariaDbIT`(Testcontainers)를 별도로 추가했다 — 겉보기엔 둘 다 "고정 JPQL"이라 같은 검증 수준이면 될 것 같지만, 반환값의 정확성이 그 자체로 도메인 불변식(leaf 행만 선택 가능)과 직결되는 쿼리는 이 예외에 해당한다는 선례로 남긴다.

**남은 residual risk — 리(里) 단위 leaf 행의 매칭 가능 여부.** 위 `eupmyeondongName IS NOT NULL` 필터는 시도/시군구 "대표행"만 걸러낼 뿐, 그 아래 리(里) 단위 leaf 행(전체 법정동코드의 약 77%)까지 실제로 거래에 매칭 가능한지는 별개 문제다 — 위 "원본 데이터 읽기" 절의 `LegalDistrictCodeLoader` 항목 참고. 리 행의 `eupmyeondong_name`이 "읍+리" 합친 문자열("기장읍 동부리")로 저장되는데, 실거래 API의 `umdNm`이 읍/면 이름만 준다면 이 leaf 행들도 (대표행과 마찬가지로) 자동완성엔 뜨지만 실거래 검색·관심지역 통계에서는 항상 0건인 막다른 후보가 된다. `eupmyeondongName IS NOT NULL` 조건만으로는 이 케이스를 못 걸러낸다 — 실 API 응답 형식을 확인한 뒤 필요하면 이 쿼리를 다시 검토하라.

### SVC-RCV-01 구현 결정 사항

프로그램 설계서 처리 로직 원문이 다루지 않은 세부 사항, 그리고 설계서와 실제 구현이 정면으로 충돌해 구현 시점에 방향을 정한 사항이다. **아래 항목 다수가 "지성 확인" 표시 없이 구현됐다 — 다음에 이 도메인을 다시 볼 때 반드시 검토가 필요하다.**

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| `record()` 호출 위치 | 2.2절("Service-to-Service 직접 호출 허용")·3.6절("RCV 도메인과 협력")이 `ComplexService.getDetail()` 내부에서 `SVC-RCV-01.record()`를 부르라고 명시 | 설계서 그대로 `ComplexService.getDetail()` 내부에서 `recentViewService.record(...)`를 직접 호출한다. 다만 `getDetail()` 자체에 걸려 있던 `@Cacheable(complexDetail::{complexId})`는 별도 빈 `ComplexDetailCache.get()`으로 옮겼다 | `getDetail()`에 `@Cacheable`을 그대로 두고 그 안에서 record()를 부르면 **캐시 히트마다 조회 이력 기록이 통째로 스킵된다**(코드리뷰에서 지적됨) — 이 캐시는 키가 `#complexId`뿐인 전역 캐시라 첫 조회(캐시 미스) 이후의 모든 사용자는 TTL 24h 동안 기록되지 않는다. 처음엔 이 문제를 Controller가 두 서비스를 나란히 호출하는 방식으로 우회했으나, 이는 설계서의 "Controller는 비즈니스 로직을 갖지 않는다" 원칙과 정면충돌해 다시 지적받았다. 같은 클래스 안에 캐시 전용 메서드를 별도로 둬도 self-invocation(같은 클래스 내부 호출은 프록시를 안 거쳐 `@Cacheable`이 무력화되는 Spring AOP의 알려진 제약) 때문에 해결이 안 돼, 캐시 로직만 `ComplexDetailCache`라는 완전히 별도의 빈으로 떼어냈다 — `ComplexService`가 그 빈을 (자기 자신이 아닌) 외부 빈으로 호출하므로 프록시를 정상적으로 거쳐 캐싱이 유지되고, `getDetail()` 본문은 캐시 히트/미스 여부와 무관하게 매번 실행되어 record()도 매번 호출된다. Controller(`ComplexController.getDetail()`)는 HTTP에서 userId/sessionId를 뽑아 `complexService.getDetail(id, userId, sessionId)`에 그대로 전달하기만 한다. |
| 비로그인 세션 식별 방식 | Controller 시그니처에 `String sessionId`만 명시, 어디서 오는지 언급 없음 | `X-Session-Id` HTTP 헤더(프런트가 브라우저별로 생성·관리)에서 읽는다. `RecentViewController.SESSION_ID_HEADER` 상수를 `ComplexController`가 그대로 참조해 두 곳의 헤더명이 어긋나지 않게 했다 | 이 프로젝트에 세션/쿠키 기반 비로그인 식별 관례가 아직 없어 새로 정했다 — 두 컨트롤러가 서로 다른 헤더명을 쓰면 세션 사용자가 방금 남긴 조회 기록을 다시는 조회할 수 없는 조용한 버그가 생기므로 리터럴을 중복해 두지 않고 상수를 공유했다. |
| `record()`의 비동기 실행 | 3.3절 SVC-CPX-01 처리 로직 원문이 "SVC-RCV-01.record()를 호출해 조회 이력을 비동기적으로 남긴다"고 명시 | `RecentViewService.record()`에 `@Async`를 걸고, 이를 활성화하는 `common/config/AsyncConfig.java`(`@EnableAsync`)를 신설했다. 기본 `SimpleAsyncTaskExecutor`를 그대로 쓰고(전용 Executor 빈 없음), 예외는 기본 `SimpleAsyncUncaughtExceptionHandler`가 로그만 남기고 호출자에게 전파되지 않는다 | 동기로 두면 캐시 히트 경로에서도 매 상세조회 요청마다 DB write(조회+갱신 또는 INSERT, 상한 초과 시 삭제까지)가 응답 경로에 얹혀 NFR-1(평균 200ms 이내)을 해칠 수 있다(코드리뷰에서 지적됨). 조회 이력은 참고 정보일 뿐 DTL-01 상세조회 성공 여부에 영향을 줘서는 안 되므로, 예외가 조용히 로그로만 남는 fire-and-forget 방식이 적절하다고 판단했다 — 이 성격 때문에 `RecentViewServiceTest`(Mockito, Spring 컨텍스트 없이 동기 실행)만으로 로직을 검증하고, `@Async`가 실제로 별도 스레드에서 동작하는지 자체를 검증하는 별도 통합 테스트는 두지 않았다(신뢰: Spring 프레임워크 자체 기능이라는 판단). |
| `RecentView.housing_type`(NOT NULL) 값의 출처 — **지성 확인 필요, 실 데이터로 검증 안 됨** | 명시 없음(Complex 엔티티엔 housing_type 컬럼 자체가 없고 자유 텍스트 `complex_type`만 있음) | `Complex.inferHousingType()`: `complex_type`이 정확히 `"아파트"`면 APT, `complex_type`이 NULL이면(엔티티정의서 4.2절 실사용 감사 기준 약 0.48%·105건) **null**, 그 외(연립다세대 등)는 VILLA로 취급. `ComplexDetailResponse`에 `housingType` 필드(nullable)를 추가해 `ComplexService.getDetail()` 응답에 함께 실어, record() 호출 시 별도 조회 없이 그대로 재사용한다. `RecentViewService.record()`는 `target.housingType()`이 null이면 (actor 누락 때와 동일하게) 조용히 기록을 스킵한다 | trade 도메인은 각 거래가 자기 데이터셋(APT/VILLA)에서 직접 `housing_type`을 받아오지만(BAT-CLC-01), complex 마스터(단지 기본정보 xlsx)는 그 값을 별도 enum 컬럼으로 갖지 않는다. "아파트" 외의 연립다세대 쪽 원본 표기(예: "연립다세대"/"다세대주택")를 실제 xlsx로 아직 확인하지 못해, MVP가 APT/VILLA 2종뿐이라는 CHECK 제약을 근거로 "아파트가 아니면 VILLA"로 잠정 처리했다. NULL인 105건까지 VILLA로 임의 추정하면 잘못된 통계/알림 조건평가로 이어질 수 있어, 이 경우만은 추정하지 않고 기록 자체를 스킵하는 쪽을 택했다(코드리뷰에서 지적됨) — record()가 비동기라 이 스킵이 DTL-01 응답 자체에는 애초에 영향을 주지 않지만, 잘못된 값을 확정 저장하는 것보다는 안전하다고 판단했다. 대표 거래의 housingType을 대신 쓰는 방안(TRD-01의 "complexId 고정 → housingType도 사실상 고정" 판단과 같은 방향)도 검토했으나, 거래가 아직 매칭되지 않은 신규 단지에서 대표 거래가 없어 오히려 더 넓은 범위에서 값을 못 채우는 경우가 생겨 배제했다. 실제 xlsx 표본을 확인하는 즉시 `inferHousingType()`만 고치면 된다. |
| 동일 주체·동일 대상 재조회 시 갱신 방식의 동시성 | "기존 레코드가 있으면 viewed_at만 갱신, 없으면 INSERT"라고만 언급(신규 제안, 반영 전 검토 필요 표시) | "조회 후 없으면 저장" 패턴을 그대로 구현하되, `recent_view`에는 (actor, complex_id) 조합의 UNIQUE 제약이 DDL에 없어 CLAUDE.md의 "UNIQUE 제약 동시성 회귀 테스트 원칙"(TOCTOU를 DB가 잡아주는 경우)이 적용되지 않는다 — 동시 요청 경쟁 시 같은 조합의 행이 중복 INSERT될 이론적 여지가 있으나 별도 방어 로직을 추가하지 않았다 | 조회 이력은 "이 회원이 대략 최근에 이 단지를 봤다"는 참고 정보이지 결제·인증처럼 정합성이 깨지면 사고로 이어지는 데이터가 아니다. 설계서 자체가 이 갱신 정책을 "신규 제안, 반영 전 검토 필요"로 표시해 아직 확정된 요구사항도 아니라, DB 제약 추가나 애플리케이션 레벨 락 같은 방어 코드를 지금 시점에 넣는 것은 과설계로 판단했다. 실제 동시 재조회 빈도가 문제가 되면 (actor, complex_id) 복합 UNIQUE를 테이블 정의서에 추가하는 방향을 검토하라. |
| 보관 건수 상한 초과 시 삭제 방식 | "상한(예: 20건) 초과 시 가장 오래된 레코드부터 삭제"만 언급, 벌크 삭제 여부는 없음 | `@Modifying` 벌크 DELETE가 아니라 초과분 개수만큼 `findByXxxOrderByViewedAtAsc(actor, PageRequest.of(0, overflow))`로 조회한 엔티티 목록을 `deleteAll(List)`로 개별 삭제 | 상한이 20건으로 작아 매 record() 호출마다 최대 1건만 초과분이 생기는 정상 흐름에서는 비용 차이가 무의미하고, `deleteAll(Iterable)`은 벌크 쿼리가 아니라 개별 DELETE라 CLAUDE.md의 "`@Modifying` 벌크 쿼리 — flushAutomatically/clearAutomatically 원칙"(다른 엔티티의 미반영 변경이 clear로 유실되는 함정)이 애초에 적용되지 않는다 — 벌크 쿼리를 썼다면 이 함정을 매번 재검토해야 했을 것이다. |
| `getRecent()`의 `limit` 기본값·하한 가드 | Controller 시그니처가 `int limit`, 기본값 3(HOME-01 구성요소 5번 "최대 2~3건") 명시 | `RecentViewController`가 `@RequestParam(defaultValue = "3")`으로 구현했다. 하한은 예외를 던지지 않고 `Math.max(limit, 1)`로 조용히 보정 — 상한은 별도로 두지 않았다 | `PageRequest.of(0, limit)`은 0 이하에서 `IllegalArgumentException`(500)을 낸다(SVC-CPX-01의 `getPopular()`가 이미 겪은 문제와 동일한 함정). 다만 이 엔드포인트는 `getPopular()`와 달리 주체별 실제 보관 건수가 `MAX_PER_ACTOR`(20)로 이미 상한이 걸려 있어, limit을 아무리 크게 줘도 반환 행 수 자체가 20을 넘지 못한다 — N+1 폭증 같은 자원 고갈 벡터가 없어 상한 검증(및 그에 따르는 예외 클래스)을 추가하지 않았다. |

이 도메인의 두 리포지토리 메서드(`findByXxxOrderByViewedAtDesc`/`Asc`, `findByXxxAndComplex_ComplexId`, `countByXxx`)는 전부 Spring Data 파생 쿼리라 SVC-RGN-01의 `TradeRepository.findAverageSaleAmount()`와 같은 성격으로 보고 `RecentViewServiceTest`(Mockito)로만 검증했다 — WHERE 절 자체의 정확성이 도메인 불변식과 직결되는 종류(SVC-RGN-01의 `searchByNameContaining()` 같은 경우)가 아니라 별도 MariaDB IT를 추가하지 않았다.

### SVC-FAV-01 구현 결정 사항

프로그램 설계서가 다루지 않은 세부 사항을 구현 시점에 확정한 내용이다. **아래 항목 다수가 "지성 확인" 표시 없이 구현됐다 — 다음에 이 도메인을 다시 볼 때 반드시 검토가 필요하다.**

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| `ComplexNotFoundException` 소속 | 원래 `complex.exception` | `common.exception`으로 이동 | `addFavoriteProperty()`가 요청받은 complexId로 단지를 조회할 때도 SVC-CPX-01.getDetail()과 같은 "존재하지 않는 단지" 오류가 필요해, `InvalidCredentialsException`이 auth→common으로 승격된 것과 같은 절차로 옮겼다(SVC-USER-01 절 참고). |
| `complex_type` 원본 미기재(NULL, 약 0.48%) 단지의 관심 매물 등록 — **지성 확인 필요** | 설계서 처리 로직에 언급 없음 | `Complex.inferHousingType()`이 null을 반환하면 새 예외 `HousingTypeUndeterminedException`(422)을 던지고 등록 자체를 막는다(`favorite.exception`, `FavoriteService.addFavoriteProperty()`) | favorite_property.housing_type이 NOT NULL이라 값을 추정해 채울 수 없다. SVC-RCV-01.record()는 같은 상황에서 "조용히 기록 스킵"을 택했지만(CLAUDE.md SVC-RCV-01 절), 그건 비동기 fire-and-forget이라 가능했던 선택이다 — 관심 매물 등록은 사용자가 결과를 즉시 확인하는 동기 쓰기라 조용히 건너뛸 수 없어 명시적 실패로 처리했다. |
| 존재하지 않는 legalDongCd로 관심 지역 등록 — **지성 확인 필요** | 예외표에 없음(`MissingComplexIdException`/`DuplicateFavoriteException`/`AccessDeniedException`/`FavoriteNotFoundException`/`DuplicateFavoriteRegionException` 5개만 명시) | 새 예외 `RegionNotFoundException`(404, `region.exception`)을 신설해 `addFavoriteRegion()`에서 던진다 | 검사 없이 `legalDistrictCodeRepository.getReferenceById()`로 프록시만 만들어 저장을 시도하면 FK 위반이 `DataIntegrityViolationException`으로 터지는데, 이는 addFavoriteRegion()의 UNIQUE 위반 race condition catch와 같은 예외 타입이라 "지역이 아예 없음"과 "동시 등록 경쟁"을 구분하지 못하고 전부 `DuplicateFavoriteRegionException`으로 잘못 응답하게 된다 — 그래서 save() 이전에 존재 여부를 명시적으로 확인한다. `region.exception`에 둔 이유는 `ComplexNotFoundException`이 처음 `complex.exception`(엔티티를 소유한 도메인)에 있었던 것과 같은 위치 기준 — 지금은 FAV만 던지므로 아직 common으로 승격하지 않았다. |
| `legalDongCd` 필수값 누락 처리 | complexId(`MissingComplexIdException`)와 달리 이 예외가 설계서에 명시돼 있지 않음 | `AddFavoriteRegionRequest.legalDongCd`에 `@NotBlank`를 걸어 COM-VAL-01 표준 경로(`VALIDATION_FAILED`, 400)를 그대로 탄다 | complexId는 설계서가 전용 예외 클래스명까지 지정해 그 지시를 그대로 따랐지만(Bean Validation으로 옮기면 그 지정을 어기게 된다), legalDongCd는 지정된 이름이 없어 이 프로젝트의 기본 관례(COM-VAL-01)를 그대로 적용했다. |
| **선택 불가능한(비활성/계층 대표행) legalDongCd로 관심 지역 등록** | 예외표에 없음 — 위 "존재하지 않는 legalDongCd" 항목도 원래는 단순 `findById()`로 "행이 존재하는가"만 확인했다 | `LegalDistrictCodeRepository.findByLegalDongCdAndIsActiveTrueAndEupmyeondongNameIsNotNull()`을 신설해 `findById()` 대신 쓴다 — `isActive=true AND eupmyeondongName IS NOT NULL` 조건은 `searchByNameContaining()`(자동완성)이 이미 거는 조건과 동일하다. 이 조건에 걸리면 (행 자체는 존재하므로) 새 예외를 만들지 않고 기존 `RegionNotFoundException`을 그대로 재사용해 던진다. | **근거:** `POST /api/favorites/regions`는 자동완성을 거치지 않고 legalDongCd를 직접 받는 엔드포인트라, 자동완성이 원천적으로 걸러내는 비활성 코드나 시도/시군구 대표행(`eupmyeondongName=null`, `LegalDistrictMatcher.matchByTradeSggCd()`가 절대 매칭시키지 않는 행)도 그대로 통과해 저장될 수 있었다 — 저장은 성공하지만 `getFavoriteRegions()`의 통계가 영원히 "데이터 없음"만 반환하는 관심 지역이 만들어지는 조용한 버그였다(Codex PR 리뷰 P2 지적, `FavoriteService.java:137~138`). **예외명 결정:** "행이 아예 없음"과 "행은 있지만 선택 불가"를 사용자 입장에서 구분해야 할 이유가 없어(둘 다 "이 지역은 등록할 수 없다"는 동일한 404 응답이면 충분) 새 예외 클래스를 만들지 않고 `RegionNotFoundException`의 발생 조건만 넓혔다 — `HousingTypeUndeterminedException`처럼 별도 실패로 승격해야 할 만큼 원인이 사용자에게 다른 의미를 갖는 케이스가 아니라고 판단했다. **계층 접근 방식:** `RegionService`를 경유하지 않고 `LegalDistrictCodeRepository`에 predicate 메서드를 직접 추가해 `FavoriteService`가 곧바로 호출한다 — `getFavoriteRegions()`가 이미 `RegionService` 전체가 아니라 `RegionStatsCalculator`(계산 컴포넌트)만 가져다 쓰는 것과 같은 이유(위 "getFavoriteRegions() 구현 방식" 행 참고: `RegionService.getInterestSummary()`가 반대 방향으로 `FavoriteRegionRepository`를 이미 참조하고 있어 Service 대 Service로 얽으면 순환 의존이 생긴다)로, 지금은 리포지토리 레벨의 조건 재사용에 그쳤다. **RegionService를 경유하는 협력(예: `RegionService.findSelectableRegion(legalDongCd)` 같은 조회 전용 메서드를 두고 FAV가 그걸 호출)으로 리팩터링하는 것은 지금 당장 하지 않는다** — 이 조건이 지금은 FAV 하나에서만 필요하고, 억지로 Service 경유를 만들면 `getFavoriteRegions()`가 이미 회피한 순환 의존 문제를 다시 끌어올 위험이 있다. RGN 도메인에 legalDongCd 단건 조회가 두 번째로 필요해지는 시점(예: NTF 도메인이 조건평가에서 같은 검사를 필요로 할 때)에 이 항목부터 재검토하라. |
| `getFavoriteProperties()`의 "전월 대비 변동률" 계산 범위 | Service 표는 "각 항목에 최근 거래가·전월 대비 변동률·알림조건 배지 포함"이라고만 언급, 대상 거래유형·기간 정의 없음 | `TradeRepository.findAverageSaleAmountByComplex()`를 신설해 `RegionStatsCalculator`와 같은 창(최근 1개월 vs 그 이전 1개월, KST)·모집단(매매 SALE만, 미취소)을 complex_id 기준으로 계산한다(`FavoriteService.calculatePropertyChangeRate()`) — RegionStatsCalculator 자체는 재사용하지 않고 별도 메서드로 뒀다 | SVC-CPX-01/TRD-01/RGN-01의 기존 "매매·전월세는 금액 구조가 달라 하나로 합산할 기준이 없다" 결정을 그대로 이어받았다. WHERE 절이 legal_dong_cd가 아니라 complex_id라 RegionStatsCalculator를 그대로 재사용할 수 없고, SVC-TRD-01이 TradeSortCondition을 complex.dto.SortCondition과 분리한 것과 같은 "도메인별 수직 패키지" 이유로 별도 컴포넌트를 새로 만들지 않고 FavoriteService 안에 작게 뒀다. **최근 거래가(recentAmount)는 매매/전월세 구분 없이 "가장 최근 거래" 1건을 그대로 쓰는데(ComplexSummaryResponse와 같은 관례) changeRate는 매매만 대상이라, 최근 거래가 전세/월세였다면 changeRate가 null일 수 있다 — 지성 확인 필요.** |
| `getFavoriteProperties()`의 대표 거래 없는 항목 처리 | 명시 없음 | 목록에서 제외하지 않는다 — `recentAmount`/`changeRate`/`recentDealCategory`/`recentDealDate`를 null로 두고 프론트가 "데이터 없음"을 표시하게 한다 | SVC-CPX-01.getPopular()의 `buildSummary()`는 대표 거래 없는 후보를 걸러내지만, 그건 알고리즘이 고른 후보 목록이라 데이터 없는 항목을 빼도 다른 후보로 채워지는 반면, 관심 매물은 사용자가 명시적으로 등록한 대상이라 데이터가 없다고 목록에서 조용히 빼면 안 된다고 판단했다. |
| `getFavoriteRegions()` 구현 방식 — RGN 도메인 재사용 | Service 표 "RGN 도메인 집계 로직 재사용" | `FavoriteService`가 `RegionService` 전체가 아니라 `RegionStatsCalculator`(계산 컴포넌트) 하나만 주입받아 쓴다. `FavoriteRegionSummaryResponse.of()`는 `region.dto.RegionAutocompleteResponse.from()`도 그대로 재사용한다 | `RegionService.getInterestSummary()`가 이미 `FavoriteRegionRepository`를 직접 참조하는 반대 방향 의존을 갖고 있다(HOME-01이 FAV 서비스 계층이 없던 RGN-01 시점에 먼저 구현됨) — 두 Service가 서로를 호출하면 순환 의존이 생기므로, FAV는 RGN의 계산 컴포넌트만 가져다 쓰고 RGN의 기존 코드는 건드리지 않았다. SVC-CPX-01이 SVC-RCV-01.record()를 직접 호출하는 것과 같은 Service-to-Service 협력 패턴(CLAUDE.md 참고). |
| `AccessDeniedException` 소속 | 설계서가 이 이름을 명시적으로 지정 | `favorite.exception.AccessDeniedException`(신규, BusinessException 상속) — Spring Security의 `org.springframework.security.access.AccessDeniedException`과는 별개 클래스 | SVC-AUTH-01.logout()의 owner 검증은 기존 `InvalidRefreshTokenException`을 재사용했지만(CLAUDE.md SVC-AUTH-01 절), FAV 설계서는 이 상황을 위한 이름을 명시적으로 지정해 새 클래스를 그대로 만들었다. |
| `addFavoriteProperty()`/`addFavoriteRegion()`의 UNIQUE 위반 race condition 처리 | "동일 대상 중복 등록"만 언급, 동시성 처리 방식은 없음 | `existsBy()` 조회 → 없으면 `save()`, `save()`가 `DataIntegrityViolationException`을 던지면 같은 Duplicate 예외로 재번역한다(`AuthService.signup()`과 동일 패턴) | CLAUDE.md "UNIQUE 제약 동시성 회귀 테스트 원칙" 대상 — `favorite_property`/`favorite_region` 모두 (user_id, complex_id)/(user_id, legal_dong_cd) UNIQUE 제약이 있고 PK가 `GenerationType.IDENTITY`라 save() 시점에 곧바로 예외가 터진다는 전제가 성립한다. 이 전제 자체는 `FavoriteServiceMariaDbIT`(Testcontainers, 두 스레드 + `CountDownLatch`)로 검증한다 — **2026-09-16, Docker가 가동 중인 세션에서 `./gradlew integrationTest`로 실제 실행해 통과를 확인했다**(작성 당시엔 Docker 부재로 미실행 상태였던 잔여 리스크였음). |

이 도메인은 캐시를 적용하지 않는다(회원별 개인화 데이터, 설계서 명시) — `@Cacheable` 관련 결정 사항은 없다. `FavoritePropertyRepository`/`FavoriteRegionRepository`의 파생 쿼리(`existsBy...`/`findBy...`)는 SVC-RCV-01과 같은 성격(Spring Data 파생 쿼리, WHERE 절이 도메인 불변식과 직결되지 않음)이라 `FavoriteServiceTest`(Mockito)로만 검증했다.

### SVC-NTF-01 구현 결정 사항

Entity(`Notification`/`NotificationSetting`)와 Repository는 이번 작업 이전에 이미 만들어져 있었다(FAV 도메인이 `NotificationSettingRepository.findFavoritePropertyIdsWithSetting()`을 참조하고 있었다) — 이번에 새로 만든 것은 Controller/Service/DTO/예외뿐이다. 설계서가 다루지 않았거나 실제 구현 시점에 확정한 세부 사항은 아래와 같다.

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| "정확히 하나가 아님" vs "대상 미선택"의 예외 분기 | 예외표가 `InvalidNotificationTargetException`(정확히 하나가 아님)과 `MissingTargetException`(대상 미선택)을 별도로 열거 — "정확히 하나가 아님"은 문언상 "둘 다 없음"도 포함할 수 있어 두 예외의 경계가 불명확 | `favoritePropertyId`/`favoriteRegionId`가 **둘 다 채워짐** → `InvalidNotificationTargetException`, **둘 다 비어있음** → `MissingTargetException`으로 배타적으로 나눴다 | 두 예외가 별도 이름으로 존재하는 이상 겹치는 조건이 있어서는 안 된다고 판단했다 — "둘 다 지정"은 API를 잘못 호출한 개발 실수에, "둘 다 미지정"은 MY-03이 프론트에서 저장 버튼을 비활성화해 정상적으로는 막는 UI 상태(예외표의 "프론트는 저장 버튼을 비활성화해 사전 차단"이라는 설명과 정확히 대응)에 각각 대응한다고 해석했다. |
| `updateSettings()`가 참조하는 `favoritePropertyId`/`favoriteRegionId`의 소유자 검증 | 예외표에 없음(`AccessDeniedException`은 markAsRead()의 "타인의 알림 읽음 처리 시도"만 명시) | `favoritePropertyRepository`/`favoriteRegionRepository.findById()`로 존재를 확인하고(없으면 `favorite.exception.FavoriteNotFoundException` 재사용), 소유자가 다르면 `notification.exception.AccessDeniedException`을 던진다 — markAsRead()와 같은 클래스를 재사용 | 검증 없이 저장하면 타인의 관심 매물/지역에 알림 설정을 몰래 걸 수 있는 구멍이 생긴다. `FavoriteNotFoundException`은 "존재하지 않는 관심 등록입니다"라는 메시지가 그대로 들어맞아 재사용했고(RegionNotFoundException을 FAV가 재사용한 것과 같은 패턴), `AccessDeniedException`은 "본인 소유가 아닌 자원에 대한 처리 시도"라는 같은 성격이라 markAsRead()와 하나의 클래스를 공유하도록 메시지를 도메인 특정적이지 않게 잡았다(FAV의 AccessDeniedException과 달리 두 시나리오에 걸쳐 재사용되는 점이 다르다) — **지성 확인 필요.** |
| `markAsRead()`의 notificationId가 아예 존재하지 않는 경우 | 예외표에 없음(AccessDeniedException은 "존재하지만 소유자가 다름"만 다룬다) | 새 예외 `NotificationNotFoundException`(404)을 신설 | FavoriteNotFoundException/AccessDeniedException을 나눠 쓰는 SVC-FAV-01의 remove*()와 같은 구조 — "대상이 없음"과 "대상은 있지만 소유자가 다름"을 구분한다. |
| `updateSettings()`의 UNIQUE 위반 race condition 처리 — **두 번 틀렸다가 원자적 upsert로 확정** | "대상당 1건 제한 upsert"만 언급, 동시성 처리 방식은 없음 | `NotificationSettingRepository.upsert()`(네이티브 `INSERT ... ON DUPLICATE KEY UPDATE`) 단일 문장으로 처리한다 — 애플리케이션 레벨의 "조회 → 있으면 UPDATE, 없으면 INSERT" 분기 자체가 없다 | 이 항목은 두 단계에 걸쳐 틀렸다. **1차 구현:** `existing.isPresent()`로 분기해 없으면 곧바로 `save()`하고, `DataIntegrityViolationException`을 잡아 재조회 후 갱신했다 — `updateSettings()`와 같은 트랜잭션에서 곧바로 `save()`했기 때문에, JPA 스펙상 flush 실패(UNIQUE 위반 포함)가 그 트랜잭션을 rollback-only로 표시해 catch 블록의 재조회·갱신이 커밋 시점에 `UnexpectedRollbackException`으로 무효화되는 결함이 있었다(`TradeChunkLoader.upsertOne()`/`TradeInsertGateway`가 이미 겪은 것과 같은 함정, 코드리뷰에서 지적). **2차 구현:** `TradeInsertGateway`와 동일하게 `NotificationSettingInsertGateway`(REQUIRES_NEW)로 INSERT만 격리해 rollback-only 문제는 해결했지만, **MariaDB 기본 격리수준(REPEATABLE READ)에서는 그 INSERT 실패 이후 같은(바깥) 트랜잭션에서의 재조회가 그 트랜잭션이 이미 확립한 스냅샷에 묶여 경쟁에서 이긴 다른 트랜잭션의 커밋을 여전히 보지 못한다는 점을 놓쳤다** — 재조회가 다시 empty를 반환해 `orElseThrow(() -> raceCondition)`가 원래 예외를 그대로 던지고, upsert가 완료되지 못한 채 예외가 사용자에게 전파된다(Codex 코드리뷰 P1 재지적, 새로 추가한 `NotificationServiceMariaDbIT`가 정확히 이 순서를 재현하도록 작성됐었다). **최종 구현:** 이 스냅샷 문제는 애플리케이션 레벨의 어떤 재시도·트랜잭션 격리 조합으로도 근본적으로 피할 수 없다고 판단해(재시도 자체를 새 트랜잭션으로 실행해도 되지만 복잡도·회귀 위험이 크다), 원자적 `INSERT ... ON DUPLICATE KEY UPDATE`로 교체했다 — 단일 SQL 문장이라 스냅샷 격리 수준과 무관하게 DB가 직접 처리하고, 애플리케이션 레벨 조회·재시도·`NotificationSettingInsertGateway`(REQUIRES_NEW)가 전부 불필요해져 삭제했다. `created_at`/`updated_at`은 이 엔티티의 다른 `@CreatedDate`/`@LastModifiedDate` 필드와 같은 시간 출처(JVM `LocalDateTime.now()`)를 쓰도록 애플리케이션에서 넘기고, DB `NOW()`에 맡기지 않았다(DB 서버와 애플리케이션 서버의 시계가 다를 수 있다는 CLAUDE.md "날짜/시간 처리" 원칙과 같은 이유). 이 SQL이 실제로 INSERT/UPDATE 양쪽 다 올바르게 처리하는지는 `NotificationServiceMariaDbIT`(Testcontainers, 두 스레드가 순서 강제 없이 그냥 동시에 `updateSettings()`를 호출 — 원자적 upsert라 특정 커밋 순서를 인위적으로 만들 필요가 없어졌다)로 검증하도록 다시 작성했다 — 작성 당시엔 Docker 데몬을 쓸 수 없어(`DockerClientProviderStrategy` 초기화 실패로 직접 확인함) 미실행 상태였으나, **2026-09-16 Docker가 가동 중인 세션에서 `./gradlew integrationTest`로 실제 실행해 통과를 확인했다**(SVC-FAV-01과 함께 해소됨). |
| `NotificationSettingResponse`에 대상(단지명/지역 전체경로) 표시용 필드 포함 여부 | Service 표는 "내 알림 설정 목록"이라고만 언급, 필드 구성 없음 | favoritePropertyId/favoriteRegionId(정확히 하나만 non-null) ID만 노출하고 이름/경로는 담지 않는다 | MY-03은 이미 FAV-01의 관심 매물/지역 목록을 화면에 갖고 있어 ID로 조인하면 되고, 여기서 다시 담으려면 `findByUser_UserId()`에 JOIN FETCH를 추가해야 하는 비용이 생긴다. **지성 확인 필요 — 화면이 실제로 두 API 응답을 조인하는 구조가 아니라면 이 판단은 틀렸다.** |
| `GET /api/notifications` 응답 타입 | Controller 표는 `ApiResponse<PageResponse<NotificationResponse>>` | `ApiResponse<List<NotificationResponse>>`(기존 `ApiResponse.success(Page<T>)` 관례 재사용) | 위 "응답 포맷" 절의 `PageResponse<T>` 비도입 원칙 참고 — CPX/TRD 실제 Controller 소스로 이미 확정된 관례임을 재확인했다. |
| `type`(NotificationType) 파라미터가 허용 목록 밖이면? | 예외 처리표에 없음 | 예외를 던지지 않고 필터 없음(전체)으로 조용히 폴백 | `TradeController.getHistory()`의 housingType/dealType과 같은 이유 — 설계서 예외표가 이 파라미터의 검증 실패를 별도 예외로 다루지 않는다. |

이 도메인은 캐시를 적용하지 않는다(회원별 개인화 데이터, 설계서 명시). `NotificationSettingRepository`의 파생 쿼리(`findByUser_UserId`)와 `NotificationRepository`의 파생 쿼리는 FAV/RCV와 같은 성격(Spring Data 파생 쿼리, WHERE 절이 도메인 불변식과 직결되지 않음)이라 `NotificationServiceTest`(Mockito)로만 검증했다. 반면 `NotificationSettingRepository.upsert()`는 네이티브 SQL이 실제 DB에서 INSERT/UPDATE 양쪽 다 올바르게 동작하는지 자체가 Mockito로 증명할 수 없는 종류라 `NotificationServiceMariaDbIT`(Testcontainers)를 별도로 뒀다 — 위 race condition 항목 참고, 작성 시점엔 Docker 데몬 부재로 미실행이었으나 2026-09-16 `./gradlew integrationTest`로 통과 확인됨.

### CPX-RCV-RGN 카드 표시 필드 보강 (2026-09-17) — HOME-01/SRCH-01 DTO 갭 해소

HOME-01 프론트엔드 세션이 남긴 후속 백엔드 작업이다(위 SCR-HOME-01 절의 "ComplexSummaryResponse의
정밀/근사 배지·층수", "RecentViewResponse의 주소·가격·면적·층", "InterestRegionSummaryResponse의
'이번 달 N건'" 세 항목 — 지금까지 "미표시, 확장 필요"로 남아 있던 갭을 여기서 해소했다). 새 테이블·새
엔드포인트·새 프로그램 ID 없이 기존 3개 DTO(`ComplexSummaryResponse`/`RecentViewResponse`/
`InterestRegionSummaryResponse`)에 필드만 추가하는 순수 확장 작업이다.

| 항목 | 사전 전제 | 실제 구현/확인 결과 | 근거 |
| --- | --- | --- | --- |
| `ComplexSummaryResponse.matchMethod`/`floor` 조회 경로 | 작업 지시는 "새 서브쿼리가 아니라 기존 price/area/dealDate를 뽑는 QueryDSL 프로젝션에 두 컬럼만 얹을 것"을 전제했다 | 실제로는 `search()`(QueryDSL `select(complex, trade)`로 Trade **엔티티 전체**를 이미 select)와 `getPopular()`(`findFirstByComplex_ComplexIdAndCancelYnFalseOrderByDealDateDesc()`로 Trade **엔티티 전체**를 이미 조회) 둘 다 대표 거래를 프로젝션이 아니라 완전한 `Trade` 엔티티로 이미 들고 있었다 — `matchMethod`/`floor` 모두 그 엔티티의 필드라, 쿼리 변경 없이 `ComplexSummaryResponse.of(Complex, Trade)`가 `representativeTrade.getMatchMethod()`/`getFloor()`를 추가로 읽기만 하면 됐다. "서로 다른 거래에서 따로 조회해 정합성이 깨질 위험"도 애초에 성립하지 않았다(같은 Trade 인스턴스에서 읽으므로). | 코드 확인(`ComplexRepositoryCustomImpl.search()`, `ComplexService.buildSummary()`) 후 사전 전제보다 더 단순한 경로로 구현했다 — 검증: `ComplexRepositoryMariaDbIT.검색결과는_대표거래의_matchMethod와_floor를_그대로_노출한다`(EXACT+floor 채움/SIMILAR+floor NULL 두 케이스), `ComplexServiceTest.getPopular_대표거래의_matchMethod와_floor를_그대로_응답에_담는다`. |
| `popularComplexes` → `popularComplexesV2` 캐시 이름 변경 | — | `ComplexService.getPopular()`의 `@Cacheable(cacheNames = ...)`, `CacheEvictionListener.POPULAR_COMPLEXES_CACHE`, 관련 테스트·문서 주석을 전부 `popularComplexesV2`로 일괄 변경 | CLAUDE.md 캐싱 절의 기존 원칙(DTO에 필드 추가 시 캐시 이름 버전업 — `complexDetail`→`complexDetailV2` 선례) 그대로 적용. `search()`는 캐시 미적용이라 이 문제와 무관. |
| `RecentViewResponse`의 "주소" 표현 — **사전 전제(단일 `address` 문자열)를 뒤집고 원시 필드 3개로 확정** | 작업 지시는 "`ComplexSummaryResponse`가 이미 만들고 있는 조합 방식(이어붙이는 순서·구분자)을 그대로 재사용"하라고 전제했다 | `ComplexSummaryResponse`를 다시 확인한 결과 **주소를 하나의 문자열로 이어붙이는 로직 자체가 백엔드 어디에도 없다** — `sido`/`sigungu`/`dongRi` 3개 원시 필드를 그대로 노출할 뿐이고, 조합은 프론트(`ComplexCard.tsx`가 `{complex.sigungu} {complex.dongRi}`로 직접 렌더링)가 담당한다. "기존 컨벤션을 재사용하고 새로 정의하지 않는다"는 지시의 취지를 더 faithfully 따르는 선택은 존재하지 않는 조합 로직을 새로 발명하는 것이 아니라, `RecentViewResponse`에도 `sido`/`sigungu`/`dongRi` 3개를 그대로 얹어 `ComplexSummaryResponse`와 완전히 동일한 표현 방식을 쓰는 것이라고 판단해 그렇게 구현했다. 필드명 `address`는 쓰지 않는다 | SCR-AUTH-02가 확립한 "지시받은 전제를 실제 코드로 검증 없이 믿지 않는다" 원칙(CLAUDE.md 해당 절)을 그대로 적용 — 검증: `RecentViewServiceTest.getRecent_단지의_sido_sigungu_dongRi를_그대로_응답에_담는다`/`...NULL이면_그대로_NULL을_응답에_담는다`. |
| `InterestRegionSummaryResponse.tradeCount`의 "이번 달" 정의 — **사전 전제(달력월)를 뒤집고 기존 롤링 윈도우 재사용으로 확정** | 작업 지시는 "달력월(1일~말일)"을 전제하고, avgPrice 집계 기간과 다를 수 있으니 공통 상수로 정리할 것을 권고했다 | `RegionStats`(SVC-FAV-01 착수 시 이미 추가됨)에 `newTradeCount` 필드가 **이미 존재**했고, `RegionStatsCalculator.calculate()`가 `avgPrice`/`changeRate`와 정확히 같은 호출 안에서 같은 `currentFrom~to`(달력월이 아니라 "최근 1개월" 롤링 윈도우, KST, 매매 SALE만·미취소)로 `tradeRepository.countSaleTrades()`를 이미 계산해 두고 있었다 — 새 쿼리도, 새 기간 정의도, 공통 상수로 뽑아내는 리팩터링도 필요 없었다. `InterestRegionSummaryResponse.of()`가 `stats.newTradeCount()`를 그대로 옮겨 담기만 하면 avgPrice와 항상 같은 기간을 가리킨다는 게 애초에 보장돼 있었다(같은 계산 호출의 부산물이므로) | 코드 확인(`RegionStats`, `RegionStatsCalculator.calculate()`) 후 사전 전제(달력월 신규 정의, 정합성 별도 확인)보다 훨씬 단순한 경로로 구현했다 — 검증: `RegionServiceTest.관심지역마다_RegionStatsCalculator_결과를_함께_반환한다`(tradeCount 단언 추가), `...해당_기간_거래가_0건이면_tradeCount는_0이다`. |
| HOME-01/SRCH-01 카드의 matchMethod·floor·address·거래건수 갭 | `scr_home01_backend_dto_gaps` 메모리·CLAUDE.md SCR-HOME-01 절에 "미표시, 확장 필요"로 기록 | **해소됨** — 세 DTO 모두 필드가 추가됐고 `search()`/`getPopular()`/`getRecent()`/`getInterestSummary()` 전 경로에서 채워진다. 프론트엔드에서 카드에 실제로 표시하는 작업(비범위, 별도 프롬프트 예정)만 남았다 | HOME-01 프론트 세션이 남긴 후속 작업이 여기서 완료됐다는 기록. |

세 DTO 모두 실제로 값을 채우는 경로가 이미 존재하는 엔티티/계산 결과를 그대로 통과시키는 것이라, 이번
작업에서 새로 추가된 Repository 쿼리는 없다 — `ComplexRepositoryCustomImpl.search()`/
`ComplexService.buildSummary()`/`RecentViewService.getRecent()`/`RegionStatsCalculator.calculate()`
전부 기존 코드 그대로이고, 세 DTO의 정적 팩토리 메서드(`of()`/`from()`)만 수정했다. `./gradlew test`와
`./gradlew integrationTest`(Docker 가동 세션에서 직접 실행, `ComplexRepositoryMariaDbIT` 포함 10개
클래스 전부 그린) 모두 통과 확인.

### SVC-RCV-01 RecentViewResponse price/area/floor 보강 (2026-09-17) — 위 섹션이 미룬 마지막 갭 해소

위 절의 "완결됨" 표(line 264)가 명시했듯, HOME-01/SRCH-01 갭 중 `RecentViewResponse`의 price/area/floor는
`recent_view` 테이블 자체가 그 값을 갖지 않아(`complex_id`만 보유) 별도 설계가 필요하다는 이유로 그때
범위 밖으로 남겨뒀다. 이번 작업이 그 마지막 갭을 해소한다 — `RecentViewResponse`에 `price`(Long)/
`area`(BigDecimal)/`floor`(Short) 3개 필드를 추가했다.

| 항목 | 사전 전제/우려 | 실제 구현/확인 결과 | 근거 |
| --- | --- | --- | --- |
| **대표 거래 조회 경로 — RCV→CPX Service 호출 금지, 공유 컴포넌트로 추출** | 작업 지시는 "SVC-RCV-01→SVC-CPX-01 Service 호출은 순환 참조가 되므로 금지, Repository/QueryDSL 레벨 공유 컴포넌트를 새로 추출(예: `TradeRepository.findRepresentativeTradesByComplexIds()` 신설)할 것"을 전제했다 | `TradeRepository.findRecentTradesByComplexIds(List<Long>)`가 **이미 존재했다** — SVC-FAV-01.getFavoriteProperties()가 먼저 도입한 배치 조회로, `ComplexRepositoryCustomImpl.search()`와 정확히 같은 2단 동률 판정(MAX(dealDate)→MAX(tradeId))을 QueryDSL 상관 서브쿼리로 구현해 두고 있었다. 새로 추출할 필요 없이 `RecentViewService`가 이 메서드를 세 번째 소비자로 그대로 재사용하면 됐다 — `TradeRepository`를 직접 주입받는 방식도 `ComplexService`가 이미 쓰고 있는 선례(Service가 다른 도메인 Repository를 직접 주입받는 것은 이 프로젝트에서 이미 허용된 패턴, Service-to-Service 호출이 아니므로 순환 참조 우려 자체가 성립하지 않음)를 그대로 따랐다 | 코드 확인(`TradeRepositoryCustom`/`TradeRepositoryCustomImpl`, `FavoriteService.getFavoriteProperties()`) 후 "새로 추출"이 아니라 "기존 것을 세 번째로 재사용"으로 범위가 줄었다. |
| **CPX(getPopular)도 같은 공유 메서드를 쓰도록 리팩토링 — 단일 소스 원칙을 문자 그대로 만족** | 작업 지시는 "이미 커밋된 `ComplexRepository`를 리팩토링할지, RCV만 새 컴포넌트를 쓸지는 판단해도 된다 — 다만 tie-break 규칙만큼은 두 도메인에서 반드시 동일해야 한다"고 재량을 열어뒀다 | `ComplexService.getPopular()`(구 `buildSummary(Long)`, `findById`+`findFirstByComplex_ComplexIdAndCancelYnFalseOrderByDealDateDesc()`로 candidate당 최대 2개 개별 쿼리)를 `findAllById()`+`findRecentTradesByComplexIds()` 배치 호출로 리팩토링했다(`buildSummaries(List<Long>)`) — **리팩토링을 택한 이유**: `getPopular()`의 옛 메서드는 `ORDER BY dealDate DESC` 하나뿐이라 동률(같은 dealDate) 시 tie-break가 MariaDB 구현에 의존하는 비결정적 값이었다 — RCV가 새로 쓰는 결정적 tie-break(MAX tradeId)와 규칙이 일치한다는 보장이 없어, "문서화만으로 규칙을 맞춘다"는 대안은 동률 케이스에서 실제로 어긋날 위험이 있었다. 단일 소스로 통합하면 이 위험이 설계상 원천 차단된다. **부수 효과**: 이 리팩토링이 CLAUDE.md SVC-CPX-01 절에 "알려진 기술부채"로 이미 기록돼 있던 `getPopular()`의 N+1 구조(candidate당 최대 2쿼리)도 함께 해소했다 — candidate 수와 무관하게 고정 2쿼리(`findAllById`+`findRecentTradesByComplexIds`)가 됐다. 이제 쓰이지 않게 된 `TradeRepository.findFirstByComplex_ComplexIdAndCancelYnFalseOrderByDealDateDesc()`는 삭제했다. | 검증: `ComplexServiceTest`의 `getPopular_*` 5개 테스트를 `findAllById`/`findRecentTradesByComplexIds` 배치 목킹으로 재작성(회귀 없음, 응답 순서 보존 확인 포함). |
| **`findRecentTradesByComplexIds()`의 동률 판정 정확성 — 실 DB로 한 번도 검증된 적 없었다** | — (작업 지시에 없던 발견) | FAV-01이 이 메서드를 프로덕션에서 쓰고 있었는데도 `FavoriteServiceTest`(Mockito)만 이 메서드를 목킹했을 뿐, `ComplexRepositoryCustomImpl.search()`가 받았던 것과 같은 수준의 MariaDB IT 검증이 이 메서드에는 없었다 — 이 프로젝트 자신의 원칙("QueryDSL 상관 서브쿼리의 tie-break는 Mockito로 증명 불가, Testcontainers 필요")이 정확히 겨냥하는 공백이었다. CPX/RCV 두 도메인이 이제 이 메서드 하나에 대표 거래 선정을 전부 의존하게 되면서 이 공백을 방치할 수 없어, `TradeRepositoryMariaDbIT`에 4개 테스트를 추가했다: 동률(같은 dealDate) 시 MAX(tradeId) 승리, 여러 complexId를 한 쿼리로 배치 조회, 취소된 거래만 있는 complexId는 결과 Map에서 제외, 거래 자체가 없는 complexId도 제외. | 검증: `./gradlew integrationTest`로 직접 실행, `TradeRepositoryMariaDbIT` 17개 테스트(기존 13 + 신규 4) 전부 그린. |
| **"popular vs recent-views 정합성" 테스트 — 완료 조건이 권장한 형태(HTTP 두 엔드포인트 비교) 대신 두 단계로 분리** | 완료 조건은 "동일 complex_id에 대해 `GET /api/complexes/popular`와 `GET /api/recent-views`가 항상 같은 price/floor를 반환하는지 검증하는 정합성 테스트 1건"을 권장했다 | `ComplexService.getPopular()`는 `@Cacheable`이라 이 메서드를 실제로 실행하는 통합 테스트는 `@SpringBootTest`로 전체 컨텍스트를 띄워야 하는데, 이 프로젝트의 모든 MariaDB IT는 MariaDB만 Testcontainers `@Container`로 자체 완결시키고 Redis는 그런 장치가 없다 — `RedisCacheManager` 빈이 뜨려면 `localhost:6379`(또는 설정된 호스트)에 실제로 연결 가능해야 하므로, 이 컨테이너 밖(호스트)에 Redis가 떠 있어야만 통과하는 테스트가 된다. 이번 세션은 마침 로컬에 Redis 컨테이너가 떠 있어(`docker ps` 확인) 우연히 통과했겠지만, CI나 Redis 없이 로컬 작업하는 다른 세션에서는 이 테스트 자체가 실패해 이 프로젝트가 지금까지 지켜온 "각 IT는 자기 인프라를 자체 완결시킨다" 원칙을 깨게 된다 — HTTP 두 엔드포인트를 직접 비교하는 안은 채택하지 않았다. **대신 두 단계로 나눠 같은 보증을 얻었다**: (1) 위 IT가 "대표 거래 선정"의 유일한 소스가 결정적임을 증명하고, (2) `RecentViewServiceTest.getRecent_가격_계산_분기가_ComplexSummaryResponse와_동일하다`가 동일한 `Trade` 인스턴스를 `RecentViewResponse.from()`과 `ComplexSummaryResponse.of()` 양쪽에 넣어 산출값이 일치하는지 직접 비교한다(Redis 불필요, Mockito만으로 결정적) — (1)+(2)를 합치면 "같은 쿼리가 같은 거래를 고르고, 그 거래에서 같은 값을 뽑아낸다"는 전체 체인이 증명된다. | **완결 필요는 아님** — 다만 향후 이 프로젝트가 Redis를 Testcontainers로 자체 완결시키는 IT 인프라를 갖추게 되면(현재 없음), 그때는 완료 조건이 원래 요청한 형태(HTTP 두 엔드포인트 직접 비교)로 승격하는 것을 검토하라. |
| **`RecentView.complex`(FetchType.LAZY) N+1 — 이번 작업 범위 밖, 발견만 기록** | — (작업 지시에 없던 발견) | 이전 세션(CPX-RCV-RGN 카드 표시 필드 보강)이 `RecentViewResponse.from()`에 `sido`/`sigungu`/`dongRi`/`complexName`을 추가하면서, `RecentView.complex`가 `FetchType.LAZY`라 이 필드들을 읽을 때마다(getRecent() 결과 건별로) 지연 로딩이 발생하는 N+1을 이미 만들어 두고 있었다(당시엔 문서화되지 않음) — `complexId`만 읽는 것은 프록시 식별자라 안전하지만, `getComplexName()` 등 나머지 접근자는 프록시를 초기화시킨다. 이번 작업이 같은 메서드에 손을 대면서 발견했다. **고치지 않기로 했다** — `MAX_PER_ACTOR`(20)로 주체당 실제 보유 건수 자체가 이미 상한이 걸려 있어(getPopular()의 원래 N+1처럼 전역·캐시 대상 API가 아니라 요청당·세션 스코프 API) 최악의 경우도 추가 쿼리 20건 수준이고, `getPopular()`의 N+1이 실제로 문제였던 것도 아니라 "알려진 기술부채로 남겨뒀다가 다른 이유로 해당 메서드를 다시 열 때 함께 고친다"는 이 프로젝트의 기존 선례(CLAUDE.md SVC-CPX-01 절)와 같은 판단이다 | **완결 필요(낮은 우선순위)** — 다음에 `RecentViewRepository`의 두 `findByXxxOrderByViewedAtDesc` 파생 쿼리를 건드릴 일이 생기면, `@Query("... JOIN FETCH r.complex ...")`로 바꾸는 것을 함께 검토하라. |

**완료 조건 재확인**: `GET /api/recent-views` 응답에 price/area/floor 포함(floor NULL 가능, 대표 거래
없으면 셋 다 NULL) — 완료. 대표 거래 선정 로직이 CPX/FAV/RCV 세 도메인 모두 `TradeRepository.
findRecentTradesByComplexIds()` 단일 소스 — 완료(공유 컴포넌트가 이미 있었으므로 "동일한 tie-break
규칙을 양쪽에 문서화"할 필요조차 없어졌다). RCV가 trade 데이터에 접근하는 cross-domain 경로는 "N+1
배치 조회를 위해 명시적으로 허용/리팩토링됨" 판단으로 여기 기록 — 완료. `./gradlew test`/
`./gradlew integrationTest`(10개 MariaDB IT 클래스, `TradeRepositoryMariaDbIT` 17개 포함) 전부 통과.
프론트엔드 반영은 범위 밖으로 남겨둔다.

### API-SEARCH-01/SVC-SEARCH-01 인기 검색어 (신규 제안 — 반영 전 검토 필요)

> **2026-09-23 갱신:** 이 절의 두 결정은 뒤집혔다. "기록 방식"(`ComplexService.search()`가 `@Async`로
> `record()`를 호출)은 `POST /api/search/logs`로 분리됐다. "keyword는 로깅 전용"은 실제 검색 필터로 바뀌었다.
> 현재 기준은 아래 "단지 검색 지역코드·키워드·거래유형" 절이다. 아래 표는 당시 결정의 기록으로 남긴다.
> 100자 truncate 행도 더는 해당하지 않는다 — 이제 2~50자 검증을 통과한 값만 기록된다.

**이 도메인 전체가 요구사항정의서·엔티티정의서·테이블정의서·UI정의서·프로그램설계서·프로그램목록서(60개
프로그램) 어디에도 정의되어 있지 않다.** HOME-01 히어로 검색바를 하드코딩 없이 실제 API로 연동하기로
하면서 지성이 추가한 신규 제안 범위다 — 완료 후 프로그램목록서 3장 총괄표(60→62종, ENT-SEARCH-01
포함 시 엔티티정의서 쪽도 함께)와 8.2절 FR 추적표에 신규 프로그램으로 추가 기록이 필요하다. 아래 표의
"지성 확인 필요" 항목들은 다음에 이 도메인을 다시 열 때 반드시 재검토해야 한다.

기존 6개 SI 문서를 고치는 게 아니라, 기존 문서가 이미 확립한 패턴(설계서 2.2절 계층 협력 원칙,
COM-RES-01/COM-CACHE-01, 프로그램 ID 명명규칙)을 그대로 재사용해 도메인 하나를 새로 얹는 작업이다 —
SVC-CPX-01.getDetail()이 SVC-RCV-01.record()를 호출하는 것과 완전히 같은 구조로, SVC-CPX-01.search()가
SVC-SEARCH-01.record()를 호출한다. 새 엔드포인트를 따로 만들지 않는다.

**신규 테이블 `search_log`(ENT-SEARCH-01, 신규 제안) — DDL은
`backend/src/main/resources/schema/search_log.sql`에 커밋돼 있다.**
`spring.jpa.hibernate.ddl-auto=validate`라 이 파일을 대상 DB에 먼저 적용해야 애플리케이션이 기동된다:

```sql
CREATE TABLE IF NOT EXISTS search_log (
    search_log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    keyword       VARCHAR(100)    NOT NULL,
    searched_at   DATETIME        NOT NULL,
    PRIMARY KEY (search_log_id),
    KEY idx_search_log_keyword_searched_at (keyword, searched_at)
);
```

FK 없음 — user_id/session_id는 인기검색어 집계 목적에 불필요해 "사용하지 않는 컬럼은 추가하지 않는다"
원칙대로 넣지 않았다(누가 검색했는지가 필요해지면 후속 확장에서 추가).

**PR 리뷰 지적(P1) — DDL이 처음엔 이 CLAUDE.md(로컬 전용, gitignore됨)에만 적혀 있어 배포 불가능했다.**
1차 구현 시점에는 위 CREATE TABLE 문을 이 문서에만 적어두고 "로컬 DB에 mysql 클라이언트가 없어 직접
적용하지 못했다"는 메모만 남겼는데, 이 문서 자체가 `.gitignore`에 걸려 있어 커밋에는 전혀 포함되지
않는 파일이었다 — 즉 이 브랜치를 체크아웃해 배포하는 그 누구도(다른 환경, 다른 개발자, CI) DDL을 볼
방법이 없어 `ddl-auto=validate`가 기동 시 100% 실패하는 상태였다(Codex PR 리뷰 지적). 이 프로젝트에
Flyway/Liquibase 같은 마이그레이션 도구가 없고(다른 11개 테이블도 전부 테이블정의서 원문을 수동으로
붙여넣어 적용하는 방식이라 이 자체는 프로젝트 전반의 기존 관행이다 — **다만 "그 11개는 이미 각 개발
시점에 DB에 적용까지 마친 뒤 커밋됐다"는 아래 문장은 틀린 서술이었다, 정정 참고**), 이 PR 하나만으로
신규 테이블이 실제로 배포 가능한 상태여야 하므로 DDL을 `.sql` 파일로 리포지토리에(그리고 빌드된 JAR의
`resources`에도) 커밋해 해결했다.

**정정(SCR-HOME-01 세션, 2026-09-14) — `schema_all.sql`은 이 저장소에 존재한 적이 자체가 없다, 11개
테이블 전부 미커밋 상태다.** 위 문단이 처음 쓰였을 때 "다른 11개 테이블은 이미 커밋까지 마쳤다"고
서술했는데, HOME-01 프론트 작업 중 로컬에 MariaDB가 없어 `schema_all.sql`(명령어 절이 `mysql -u root
homesense < schema_all.sql`로 참조하는 파일)을 찾다가 이 서술이 틀렸다는 걸 발견했다 — `git ls-files`,
`git log --all --full-history -- "**/schema_all.sql"`, 저장소 전체 `find -iname "*.sql"` 세 가지 모두
이 파일이 **어느 커밋에도 존재한 적이 없다**는 것을 확인했다(`git log --all --diff-filter=A --name-only`로
지금까지 이 저장소에 추가된 적 있는 `.sql` 파일 전부를 나열해도 `search_log.sql`과 8개
testcontainers fixture뿐이다). `.gitignore` 57행에 `!schema_all.sql` 예외가 이미 준비돼 있어(search_log
때와 달리 경로 문제가 아니다) 이 파일을 만들어 커밋하는 데 걸림돌은 없다 — 단지 아무도 만든 적이 없다.
즉 "11개는 이미 커밋됐다"는 애초에 이 정정을 쓴 세션이 검증 없이 진술한 추정이었을 가능성이 높다 —
실제로는 각 개발자가 테이블정의서 8장 원문을 로컬 DB에 직접 붙여넣어 적용만 하고(그 자체는 위 문단이
맞게 서술한 프로젝트 관행이다), 그 DDL을 파일로 남겨 커밋하는 절차 자체가 이 프로젝트에 한 번도 없었던
것으로 보인다. **실질적 영향(해소됨, 아래 참고):** 이 저장소를 새로 clone한 사람(다른 개발자, CI, 다음
세션)은 11개 프로덕션 테이블(user/refresh_token/legal_district_code/complex/trade/favorite_property/
favorite_region/recent_view/notification_setting/notification/batch_log) 중 무엇 하나도 저장소만으로는
만들 수 없었다 — `spring.jpa.hibernate.ddl-auto=validate`라 스키마가 없으면 애플리케이션이 기동 자체를
거부한다.

**해소됨(2026-09-14, 같은 SCR-HOME-01 세션 내에서) — `schema_all.sql`이 저장소 루트에 실제로 커밋됐다
(커밋 `81d34db`, "sql").** 위 "완결 필요" 지시가 쓰인 시점엔 아직 미해결이었지만, 그 지시대로 테이블
정의서 8장 원문을 옮겨 담은 `schema_all.sql`(351줄, `.gitignore` 57행의 `!schema_all.sql` 예외를 그대로
활용)이 같은 세션 안에서 만들어져 커밋됐다 — 파일 자체의 헤더 주석도 "이 파일은 git 히스토리에 한 번도
존재한 적이 없었음이 확인되어(API-SEARCH-01/SCR-HOME-01 작업 중 발견) 원본 문서를 기준으로 재구성했다"고
이 경위를 그대로 기록하고 있다. 2026-09-17에 별도 세션이 `git ls-tree`/`git show`로 재확인했다 — 11개
프로덕션 테이블 전부(`CREATE TABLE` 12건, `search_log` 포함) 존재하고, CHECK 제약(`ck_trade_housing_type
CHECK (housing_type IN ('APT','VILLA'))`, `ck_trade_match_method` 등)과 FK `ON UPDATE RESTRICT`까지
CLAUDE.md가 요구해온 설계 원칙을 그대로 담고 있다. **위 문단이 요구했던 "완결 필요(우선순위 높음)"
조치는 이미 완료된 상태다** — 위 발견 서사는 문제가 실제로 있었다는 기록으로 그대로 남겨두되, 다음에
이 절을 읽는 세션은 schema_all.sql을 다시 만들 필요가 없다는 것만 알면 된다. 남은 절차는 새 환경에
배포할 때마다 `mysql -u root homesense < schema_all.sql`을 수동으로 실행하는 것뿐이다(이 프로젝트에
Flyway/Liquibase 같은 자동 마이그레이션이 없다는 사실 자체는 바뀌지 않았다).

**파일 위치 시행착오 — `.gitignore`의 `*.sql` 전면 차단을 처음엔 놓쳤다.** 처음에는
`backend/src/main/resources/db/search_log.sql`에 커밋하려 했는데, `.gitignore` 56행이 "DB 덤프(실거래가
데이터 통째로 유출 방지)" 목적으로 `*.sql`을 프로젝트 전역에서 차단하고 있어 `git status`에 전혀
잡히지 않았다(조용히 무시됨 — 에러도 경고도 없었다). 다행히 같은 `.gitignore` 58행에
`!**/schema/*.sql` 예외가 이미 준비돼 있었다 — 이 프로젝트가 처음부터 "커밋해도 되는 DDL은
`schema/` 디렉터리에 두라"는 관례를 의도했었다는 뜻이다(실제로 쓰인 적은 이번이 처음). 파일을
`backend/src/main/resources/schema/search_log.sql`로 옮겨 해결했다 — **새 테이블을 추가하는 다음
도메인도 반드시 이 경로 패턴(`src/main/resources/schema/{table}.sql`)을 따르라. `db/`나 다른 이름의
디렉터리에 두면 `.gitignore`의 `*.sql` 규칙에 조용히 걸려 커밋되지 않고, `git status`도 아무 경고 없이
그냥 무시하므로 알아채기 어렵다 — 커밋 전 `git status`에 그 파일이 실제로 잡히는지 반드시 확인하라.**

| 항목 | 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| "인기"의 정의 | 신규 제안 | 최근 7일간 검색 빈도 상위 N건(`SearchService.WINDOW_DAYS`) | 전체 누적이 아니라 최근성을 반영 — 창 길이는 상수라 조정 가능. **지성 확인 필요.** |
| 로깅 대상 | 신규 제안 | SRCH-01로 이어지는 "검색 실행"만 카운트 — `GET /api/complexes/search`(API-CPX-01) 호출마다 원문 keyword를 기록. 자동완성 훑어보기, 필터만 바꾸는 재요청은 이 API를 다시 타더라도 keyword 자체가 없거나 이전과 같을 수 있는데, 이 구분은 프론트가 keyword를 "사용자가 실제로 검색을 실행했을 때만" 실어 보내는 것에 의존한다 — 백엔드는 keyword가 실려 오면 무조건 기록한다. | 새 엔드포인트를 두지 않기로 한 이상, "검색 실행"과 "필터 변경"을 백엔드가 구분할 근거(둘 다 같은 API 호출)가 없다. 프론트 계약에 의존하는 구조라는 점을 남겨둔다. |
| 로깅 방식 | 신규 제안 | `ComplexService.search()`가 `SearchService.record(condition.keyword())`를 호출(SVC-CPX-01.getDetail()→SVC-RCV-01.record()와 동일 패턴) | 설계 문서 원안 그대로. |
| keyword 필드 신설 범위 — **로깅 전용으로 확정(지성 확인 완료)** | `ComplexSearchRequest`/`ComplexSearchCondition`에 keyword가 아예 없었고, HOME-01 프론트도 아직 이 저장소에 없어 실제 파라미터명을 대조할 방법이 없었다 | `keyword`를 두 DTO에 추가하되, **이번 범위에서는 실제 단지 검색 결과를 필터링하지 않는다** — `SearchService.record()`에만 전달되고 `ComplexRepositoryCustomImpl`의 QueryDSL 조건에는 관여하지 않는다. 기존 12-인자 호출부(`ComplexRepositoryMariaDbIT` 등)를 건드리지 않도록 `ComplexSearchCondition`에 keyword 없는 12-인자 생성자를 남겨 13-인자 캐노니컬(keyword 포함)에 `null`로 위임한다. | 지성에게 직접 확인해 "로깅 전용, 실제 필터링(complex_name 등 LIKE 매칭)은 별도 후속 과제"로 확정했다 — 실제 텍스트 검색 매칭 로직(대상 컬럼, 매칭 방식)은 이 도메인 범위 밖으로 남긴다. |
| `record()`의 동기/비동기 — **설계 원안과 다르게 반드시 `@Async`여야 한다** | "1차 버전은 동기 호출로 시작해도 무방"(신규 제안 원문) | `@Async`로 구현(SearchService.record()) | 원안이 놓친 기술적 문제: `ComplexService.search()`는 클래스 레벨 `@Transactional(readOnly = true)` 안에서 실행되는데, record()를 동기(`REQUIRED` 전파)로 호출하면 이 메서드의 INSERT가 그 readOnly 트랜잭션에 그대로 합류한다 — 드라이버에 따라 readOnly 트랜잭션의 커넥션이 실제로 쓰기를 거부할 수 있는 위험한 조합이다. SVC-RCV-01.record()와 같은 이유(NFR-1, 응답 경로에 DB write를 얹지 않음)에 더해 이 문제 하나만으로도 동기 호출은 안전하지 않다고 판단해 원안을 따르지 않았다. |
| 캐시 | 신규 제안 | `popularKeywords::{limit}`, TTL 1시간(`CacheConfig`에 별도 `withInitialCacheConfigurations` 오버라이드 추가) — evict 트리거 없음, TTL 만료로만 자연 갱신 | 검색 실행마다 evict하면 쓰기가 빈번해 캐시 이득이 없다. complexDetailV2/popularComplexes/regionAutocomplete(기본 24h + evict 트리거)와 의도적으로 다른 특성 — CLAUDE.md 캐싱 절의 "무효화 트리거는 캐시마다 다르다" 원칙을 그대로 적용한 사례. |
| 집계 키워드 정규화 | 신규 제안 | trim + `isBlank()` 정도만 하고 원문 그대로 저장·집계("강남구"/"강남" 별도 집계 허용) | 1차 버전 범위 — 동의어/정규화 처리는 후속 개선 과제. |
| `PopularKeywordResponse` 필드 구성 | 신규 제안, 필드 명시 없음 | `keyword`만 담고 순위/빈도수는 담지 않는다(리스트 인덱스로 순위 표현) | 빈도수 자체는 GROUP BY 집계 과정에서 이미 계산되지만, 완료 조건이 요구하는 것은 "빈도 내림차순 정렬"뿐이라 YAGNI로 최소 필드만 노출했다. 프론트가 순위/빈도수를 화면에 표시해야 한다는 요구가 확인되면 이때 추가하라. |
| `SearchLogRepository.findTopKeywordsSince()` 검증 수준 | — | 고정 JPQL(GROUP BY + Pageable로 LIMIT, 동적 조건 없음) — `TradeRepository.findTopComplexIdsByRecentTradeVolume()`/`findAverageSaleAmount()`와 같은 성격이라 전용 MariaDB IT 없이 `SearchServiceTest`(Mockito)로만 검증했다(SVC-RGN-01 절의 판단 기준과 동일) | QueryDSL 동적 쿼리(예: ComplexRepositoryCustomImpl)가 아니므로 WHERE 절 정확성이 도메인 불변식과 직결되는 종류가 아니다. |
| `SearchLog.searchedAt`의 타임존 — **날짜/시간 처리 원칙 적용** | — | `RecentView.viewedAt`(정렬 전용, KST 미사용)과 달리 명시적으로 `LocalDateTime.now(KST)`로 계산한다 | searchedAt은 `SearchService.getPopularKeywords()`가 별도로 계산하는 KST 기준 "최근 7일" 경계값과 직접 비교되는 컬럼이라, 두 계산이 서로 다른 타임존을 쓰면(컨테이너 환경의 JVM 기본 타임존이 UTC인 경우) 경계 부근 레코드가 조용히 잘못 집계될 수 있다(CLAUDE.md 날짜/시간 처리 원칙). |
| `InvalidLimitException` 소속 | — | `search.exception`에 별도로 둔다(ComplexController의 것과 이름은 같지만 별개 클래스) | 도메인별 수직 패키지 원칙 — TradeSortCondition을 complex.dto.SortCondition과 분리한 것과 같은 이유. limit 상한은 1~20(기본 5), ComplexController.MAX_POPULAR_LIMIT와 같은 이유(자원 고갈 방지)로 가드를 둔다. |
| `record()`의 100자 초과 keyword 처리 — **reject 대신 truncate로 확정** | 예외표에 없음. `search_log.keyword`는 `VARCHAR(100)`인데 `record()`는 원래 trim만 하고 길이를 검증하지 않았다 | trim 이후 100자를 넘으면 `SearchLog.record()` 호출 전에 `String.substring(0, MAX_KEYWORD_LENGTH)`로 잘라서 저장한다(reject하지 않는다) | Codex PR 리뷰 P2 지적 — trim된 값을 그대로 저장하면 flush/commit 시점에 컬럼 길이 제약 위반으로 이 INSERT만 조용히 실패해, 검색 자체는 200으로 성공하는데 그 검색어의 로그만 유실되어 인기 검색어 집계가 티 안 나게 틀어진다(이 INSERT는 `@Async` 메서드 안에 있어 실패해도 검색 응답에 전혀 드러나지 않는다). reject(요청을 400으로 막기)도 검토했지만 `ComplexSearchRequest.keyword`엔 애초에 길이 제약이 없고(위 "keyword 필드 신설 범위" 행 — 이번 범위는 로깅 전용) keyword가 실제 검색 필터링에 관여하지 않으므로, 로깅 컬럼 제약 때문에 정상적인 검색 요청 자체를 거부하는 것은 과하다고 판단했다 — SVC-RCV-01.record()가 값을 확정할 수 없을 때 "조용히 스킵"을 택한 것과 같은 결(로깅 실패가 본 기능에 영향을 주면 안 된다는 원칙의 연장). `substring`은 trim **이후** 길이 기준으로 잘라(자르는 순서를 반대로 하면 잘린 위치에 후행 공백이 남을 수 있다) 정확히 100자 값은 잘리지 않는다(검증: `SearchServiceTest.record_keyword가_100자를_넘으면_100자로_잘라서_저장한다`, `record_trim_이후_길이가_100자_이하면_자르지_않는다`). **잔여 리스크(지성 확인 필요, 당장 처리 안 함):** `substring`은 `String.length()`(UTF-16 code unit) 기준이라 100번째 위치에 대리쌍(이모지 등)이 걸리면 잘린 문자열이 깨질 이론적 여지가 있다 — 단지명/지역명 검색어에는 사실상 나타나지 않는 입력이라 지금은 막지 않는다. |

지성이 로컬 `homesense` DB(포트 3307)에 `search_log.sql`을 적용해 `GET /api/search/popular`,
`GET /api/complexes/search` 실제 호출로 정상 동작을 확인했다(작성자 확인 완료) — 위 파일은 이 프로젝트의
다른 11개 테이블처럼 새 환경(스테이징, 다른 개발자 로컬 등)에 배포할 때마다 매번 수동으로 먼저 적용해야
한다(자동 마이그레이션 도구 없음).

### API-CPX-01·SVC-CPX-01·API-SEARCH-01 단지 검색 지역코드·키워드·거래유형 (2026-09-23) — 프로그램설계서와 다른 설계 변경

SRCH-01 프론트 착수 전 백엔드 선행 작업이다(브랜치 `feature/backend/search-region-keyword`). 두 공백이
출발점이었다. 첫째, 지역 자동완성 응답(`legalDongCd`, `fullPath`)과 검색 파라미터(`sido`/`sigungu`/`dongRi`
텍스트)의 형태가 맞지 않았다. 둘째, `keyword`가 결과를 거르지 않고 로그에만 쓰였다. 운영 반영 절차는
`docs/runbook-complex-legal-dong-backfill.md`에 있다.

| 항목 | 설계서 상태 | 실제 구현 | 근거 | 무효화 조건 |
| --- | --- | --- | --- | --- |
| **complex.legal_dong_cd 백필 — 방식 선정** | 컬럼만 정의(선택 FK), 채우는 프로그램 없음 | `ComplexLegalDongResolver`(`batch.matcher`)가 **(가) 이름 매칭을 먼저 하고, 실패하면 (나) 주소 prefix 매칭으로 보완**한다. (가)는 (시도, `SigunguNormalizer`로 정규화한 시군구, 동리)가 legal_district_code leaf 행의 (시도, 정규화 시군구, eupmyeondong_name 마지막 토큰)과 같은지 본다. 같은 시군구에 같은 이름의 리가 둘이면 주소에 그 읍·면 이름이 있는지로 좁힌다. (나)는 legal_dong_address가 "시도 정규화시군구 읍면동[ 리]"로 시작하는 가장 긴 leaf 행을 고른다. 활성 코드만 대상이다. 러너는 `backfill-complex-legal-dong` 프로필이고, 기본은 dry-run이며 `--apply`를 붙여야 쓴다 | 로컬 dry-run(21,680건) 결과. (가)만 쓰면 채움 99.96%, 거래 대조 100%. (나)만 쓰면 100%, 99.99%. **조합하면 100%, 100%**(거래가 있는 17,637개 단지를 그 단지 거래들의 legal_dong_cd 최빈값과 대조). (가)가 못 채운 9건은 dong_ri가 NULL인 단지라 (나)가 읍·면 단위(8자리)로 채운다. (가)와 (나)가 서로 다른 1건(14826 화성효행구 봉담읍 동화리)은 첫 주소에 리가 빠진 경우로, 거래 최빈값이 (가)와 같다. 그래서 (가)를 우선한다. 적용 후 분포는 읍면동 코드 18,449건, 리 코드 3,231건 | complex 원본(K-apt xlsx)의 시군구·동리 표기 규칙이 바뀌면 재검증한다(`SigunguNormalizer`와 같은 전제). legal_district_code를 재적재한 뒤 신설 행정구역 단지가 새로 들어오면 러너를 다시 돌린다 |
| **알려진 공백 — 단지 마스터 적재가 저장소 밖에서 수동으로 수행됨(재현 불가)** | 설계서는 단지 기본정보를 적재 대상으로 가정 | 이 저장소에는 complex를 적재하는 코드가 없다(적재기·스크립트·커밋 이력 전무, 원본 xlsx는 로컬 바탕화면에만 있음). 그래서 "적재 경로에서 legal_dong_cd를 채우도록 고치는 fix-forward"가 불가능하다. **대체 절차(승인됨):** 단지를 새로 적재하거나 재적재한 뒤 백필 러너를 다시 실행한다. 러너는 NULL인 행만 채운다(runbook 8절). 적재기는 만들지 않았다 | 21,680건 전부 NULL이었던 원인이 이것이다 | **백로그: 단지 마스터 적재기를 저장소에 편입한다.** 편입하면 그 적재기가 `ComplexLegalDongResolver`를 직접 호출하게 하고(복사 금지), 이 대체 절차는 폐기한다 |
| **백필 러너의 실행 방식** | 없음 | 프로필 `backfill-complex-legal-dong`(`application-backfill-complex-legal-dong.properties`)은 `spring.main.web-application-type=none`이다. `homesense.scheduling.enabled=false`로 `@EnableScheduling`만 끄고(`SchedulingConfig` 안의 조건부 설정으로 분리), 끝나면 `SpringApplication.exit`으로 종료 코드를 반환한다(성공 0, 실패 1). non-web에서는 `HttpSecurity` 빈이 없어 기동이 실패하므로 `SecurityConfig.securityFilterChain`에 `@ConditionalOnWebApplication(SERVLET)`을 달았다. apply 후(청크 중간 실패 포함, `finally`) `complexDetailV3`(legalDongCd·matchPending), `popularComplexesV3`, `regionAutocomplete`(지역 정보 DTO) 캐시를 `invalidate()`로 전체 비운다(아래 캐싱 절의 동기 삭제 규칙). 캐시 이름은 `CacheNames` 상수로 모았다 | 1회성 러너가 03:00 수집 파이프라인을 함께 띄우거나 포트를 점유하면 안 된다 | 다른 1회성 러너(`rematch`, `reload-legal-district`, `backfill-lawd-cd`)는 아직 웹 서버를 띄운 채 끝나지 않는 옛 방식이다. 다시 쓸 일이 생기면 같은 방식으로 옮긴다 |
| **regionCode 도입과 sido/sigungu/dongRi 정리** | 3.3절 search는 지역을 텍스트(시도/시군구/동리)로 받음 | `ComplexSearchRequest.regionCode`(10자리 법정동코드, 선택)를 추가했다. 프론트는 자동완성의 `legalDongCd`를 그대로 보낸다. `RegionCodePrefixResolver`(`region.service`)가 계층 prefix를 구하고, `complex.legal_dong_cd LIKE 'prefix%'`로 거른다. 형식 오류(10자리 숫자가 아님, 전각 숫자 포함)는 400 `INVALID_REGION_CODE`, 존재하지 않거나 폐지된 코드는 빈 페이지다. **`sido`/`sigungu`/`dongRi` 파라미터는 제거했다** — 프론트·백엔드 어디에도 테스트 외 호출자가 없었다(`/search` 화면은 아직 플레이스홀더라 이 API를 부르지 않는다) | 텍스트 방식은 자동완성 fullPath("경기도 수원시 장안구 파장동")를 complex 표기("수원장안구")로 바꿀 방법이 없어 시+구 도시와 리 단위에서 0건이 났다 | legal_dong_cd가 NULL인 단지는 regionCode 검색에서 빠진다(현재 0건). 단지 재적재 후 러너를 안 돌리면 새 단지가 지역 검색에서 누락된다 |
| **prefix 규칙과 실증 근거** | 없음 | 시도(뒤 8자리 0)는 앞 2자리, 시군구 대표행(뒤 5자리 0)은 앞 5자리, 읍면동(뒤 2자리 0)은 앞 8자리, 리는 10자리다. **구를 가진 시는 앞 4자리**다. 구를 가진 시인지는 **이름으로** 판정한다 — 같은 4자리 안에 `"{시군구명} "`으로 시작하는 하위 시군구가 있는지 본다. 판정 맵은 활성 코드로 한 번 만들어 JVM에 들고 있다(요청마다 조회하지 않음). **무효화는 Redis 버전 키 `region:prefix-map:version`으로 한다(2026-09-27, 코드리뷰 P2)** — 재적재는 별도 JVM(`reload-legal-district` 러너)에서 돌아 `LegalDistrictCodeReloadedEvent`가 서비스 중인 서버에 오지 않으므로, 이벤트를 받은 프로세스가 커밋 후 새 UUID를 쓰고 모든 프로세스가 조회마다 그 키를 읽어 자기 맵의 버전과 다르면 다시 만든다. 키가 없으면 null을 버전으로 보고, Redis를 읽지 못하면 보유한 맵을 그대로 쓴다(검색을 막지 않음). 재적재 러너(`reload-legal-district`)는 백필 러너와 같이 웹 서버·스케줄링 없이 돌고 끝나면 스스로 종료한다(성공 0, 실패 1). 발행 결과는 재적재 러너가 요약 로그(`재적재 요약 | prefix 맵 버전 발행: 성공/실패/시도되지 않음`)로 남기고, 실패하면 서비스 중인 앱을 재시작한다(runbook 9절). 매 검색이 Redis GET을 거치므로 `spring.data.redis.timeout`/`connect-timeout`을 2s로 지정했다 — 미설정 시 Lettuce 기본값(명령 60초)이라 Redis가 느려지면 검색이 멈췄다. 2026-09-27 로컬 실측(Redis `docker pause`): 검색은 2.06s 후 보유 맵으로 200, `@Cacheable`인 `/api/complexes/popular`는 2.04s 후 500(`CacheErrorHandler`가 없어 캐시 예외가 그대로 전파 — 이전엔 60초 뒤 500) | 활성 코드 전수 조사(2026-09-23). 구를 가진 시는 13곳이다(수원·성남·안양·부천·안산·고양·용인·화성·청주·천안·포항·창원·전주). 각 4자리 그룹에는 그 시와 산하 구만 있다. 반면 **영동군(43740)과 증평군(43745)은 서로 무관한데 `4374`를 공유**해, "5번째 자리가 0이면 4자리" 같은 숫자 규칙은 영동군 검색에 증평군을 섞는다. 세종(3611000000)은 시군구 계층이 없어 5자리 `36110`이고, 하위 동이 전부 이 prefix로 시작한다 | 행정구역 개편으로 새 "구를 가진 시"가 생기면 이름 규칙이 자동으로 따라간다. 하위 구 이름이 `"{시명} "` 형식을 벗어나는 표기가 CSV에 등장하면 재검증한다 |
| **keyword 필터화와 매칭 대상** | 3.3절에 없음(API-SEARCH-01 때 로깅 전용으로 추가됐던 파라미터) | `keyword`는 trim하고, 공백뿐이면 조건 없음으로 본다. 길이는 코드포인트 기준 2~50자이고 위반하면 400 `INVALID_SEARCH_KEYWORD`(`SearchKeywordPolicy`, `common.validation`). **단지명, legal_dong_address, 그 단지 법정동코드의 legal_dong_name 중 하나에 부분 일치**하면 남긴다. `%`, `_`, 이스케이프 문자(`!`)는 이스케이프한다. legal_dong_name 매칭은 조인이 아니라 `legal_dong_cd IN (서브쿼리)`로 건다. regionCode·필터·정렬·페이지네이션과 AND로 결합된다 | legal_dong_address는 K-apt 표기("경기도 수원장안구 …")라 "수원시"가 걸리지 않는다. 그래서 legal_dong_name까지 포함했다. **조인으로 처음 구현했을 때 키워드 검색이 ~2.1s였다.** OR 조건이 조인된 테이블을 참조해 complex 스캔 단계에서 걸러지지 못하고, 대표거래 상관 서브쿼리가 단지 전체(약 2만)에 먼저 돌았기 때문이다(EXPLAIN으로 확인). IN 서브쿼리로 바꾸자 조건 전체가 complex에 걸려 **76~195ms**(로컬 실측: 래미안·안성시·자이·수원시·%%)가 됐다. LIKE 자체 비용은 21,680건 전체에서 24~27ms라 FULLTEXT(ngram)는 도입하지 않았다 | 단지 수가 크게 늘어 LIKE 스캔이 병목이 되면 그때 FULLTEXT(ngram)를 검토한다. 단지명 자동완성은 이번 범위 밖이다 |
| **검색 로그 엔드포인트 분리** | 신규 제안(API-SEARCH-01): 기록은 `ComplexService.search()` 안에서 `@Async`로 | `ComplexService.search()`에서 기록을 제거했다(목록 조회는 부수효과 없는 순수 조회). **`POST /api/search/logs`**(바디 `{keyword}`, 인증 불필요, `SearchKeywordPolicy.normalizeRequired`로 검증하고 비어 있으면 400)를 신설했다. `SearchService.record()`는 동기 `@Transactional`이고 실패도 전파한다. `GET /api/search/popular` 집계는 이 행을 그대로 쓴다(IT로 확인) | 같은 API가 필터 변경·페이지 이동에도 불려 기록이 부풀려질 수 있었다. "검색을 실행한 순간" 1회 기록은 프론트만 알 수 있는 사건이다. 예전 `@Async`는 readOnly 트랜잭션 합류를 피하려던 것이었는데, 이제 이 메서드 자체가 요청의 목적이라 동기로 바꿨다 | 기록 남용(봇 등)이 문제가 되면 rate limit을 검토한다 — 지금은 없다 |
| **거래유형 전세/월세 구분** | UI정의서 4.4절 거래유형은 매매/전세/월세 3가지인데, search는 `dealCategory(SALE/RENT)`만 있었다 | `rentType`(JEONSE/WOLSE) 파라미터를 추가했다. **trade 값 체계(`deal_category` + `rent_type`)와 `TradeSearchRequest`의 기존 형태를 그대로 따랐다**(단일 SALE/JEONSE/WOLSE 파라미터를 새로 만들지 않음). 매매는 `dealCategory=SALE`, 전세는 `rentType=JEONSE`, 월세는 `rentType=WOLSE`로 보낸다. rentType만 보내도 전월세로 취급해 금액 범위는 `depositAmount`(보증금) 기준이다(`ComplexSearchCondition.isRent()`, TRD가 겪은 버그와 같은 규칙). 대표거래(카드의 "최근 거래가")는 거래 필터가 걸린 상관 서브쿼리로 고르므로 **선택한 유형의 최신 거래**다(IT로 확인). `dealCategory=SALE`과 `rentType`을 함께 보내면 0건이다(TRD와 같은 동작, 400 아님) | 프론트가 이미 알고 있는 trade 값 체계를 새 이름으로 감싸면 두 도메인의 파라미터가 갈라진다 | 카드 표시 필드는 아래 행에서 해소했다 |
| **카드 응답에 rentType·monthlyRentAmount 추가** | 없음(FR-3.3은 월세의 보증금과 월세를 함께 요구) | `ComplexSummaryResponse`에 `rentType`, `monthlyRentAmount`를 **대표거래에서** 채운다. 매매면 둘 다 null(`non_null` 설정이라 JSON에서 키가 빠진다), 전세면 `JEONSE`와 원본 월세값(보통 0), 월세면 `WOLSE`와 월세금액이다. 거래유형 필터를 걸면 대표거래가 그 유형의 최신 거래이므로 카드도 그 유형을 보인다. 이 DTO를 담는 인기 단지 캐시는 `popularComplexesV3`로 올렸다(`CacheNames`) | 이전에는 월세 단지가 보증금만 표시돼("5,000만") 로컬 응답 샘플에서 실제로 오해의 여지가 확인됐다(수원한일타운아파트). 캐시 이름을 올려야 배포 전 V2 엔트리가 두 필드 null로 읽히지 않는다 | 이 DTO에 필드를 또 추가하면 V4로 올린다 |
| **검색 조건 필수화 — regionCode 또는 keyword** | 3.3절 search는 모든 조건이 선택 | 둘 다 없으면 400 `MISSING_SEARCH_CONDITION`(`MissingSearchConditionException`, `BusinessException` 상속, 전역 핸들러). 형식 오류(`INVALID_REGION_CODE`/`INVALID_SEARCH_KEYWORD`/`INVALID_SORT_CONDITION`)를 먼저 검사하고 필수 여부는 마지막에 본다. 공백뿐인 keyword는 조건 없음이다. 검사 위치는 `ComplexSearchRequest.toCondition()`(API 경계)이다 — Service/Repository는 조건 없는 호출을 막지 않는다(IT·내부 호출용) | SRCH-01은 검색 실행으로만 진입하고, 전국 목록 탐색은 명세에 없다. 조건 없는 전국 조회는 1.6~2.2s라 NFR-1(평균 200ms)을 넘는다 | **전국 단위 목록 조회가 요구되면** 이 제약을 풀기 전에 complex 최근거래 비정규화(아래 성능 결정표의 개선안 3)를 먼저 검토한다 |

**넓은 지역 성능 — 개선안 1(count를 EXISTS로) 적용, 2·3은 보류(2026-09-23, 로컬, trade 204,520행).**

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| **개선안 1 — count를 EXISTS로** | **적용.** `ComplexRepositoryCustomImpl.search()`의 전체 건수를 `count(complex) WHERE (complex 조건) AND EXISTS(조건 맞는 trade)`로 센다. 목록 쿼리는 그대로다. 주의: 목록용 `complexFilters` BooleanBuilder는 `and()`가 제자리에서 바꿔 놓으므로 count용은 새로 만든다 | 대표거래는 "조건을 만족하는 거래가 있는 단지마다 정확히 1건"이므로 건수가 같다. `ComplexRepositoryMariaDbIT.전체_건수는_목록_쿼리가_실제로_돌려주는_단지_수와_같다`가 동률·취소 거래·매매/전세 혼재·범위 필터·keyword 7개 조건에서 "total == 전부 받은 목록 행 수, 중복 없음, 페이지 크기와 무관"을 검증한다. 로컬 실데이터 total도 개선 전후가 같다(아래 표). 생성된 count SQL 단독 실행은 54~58ms다(개선 전 113~360ms) | 대표거래 선정 규칙이 "조건 맞는 거래가 있는 단지당 1건"에서 바뀌면(예: 단지당 여러 카드) count도 다시 목록과 같은 방식으로 세야 한다 |
| **개선안 2 — 목록을 윈도 함수로** | **보류.** 다음 단계로도 거치지 않는다 | (1) 네이티브 쿼리가 필요해 동적 필터 로직이 QueryDSL(count)과 네이티브 SQL(목록)에 중복된다 — 유지보수 부담과 두 쿼리의 결과 불일치 위험이 생긴다. (2) 느린 구간은 시도 단위 검색에 한정되고, 시군구 이하는 이미 기준(200ms) 이내다(수원시 장안구 19ms) | — (개선안 3으로 바로 간다) |
| **개선안 3 — 최근거래 비정규화** | **보류** | 개선안 2와 같은 근거(시도 단위에서만 느림). 스키마 변경과 BAT-LOD-01 적재 경로 수정이 따른다 | **운영에서 시도 단위 검색 비중이 유의미하게 확인되거나 전국 조회가 요구되면** 이것을 검토한다(개선안 2는 중간 단계로 거치지 않는다) |

**API 응답 시간(end-to-end) 개선 전후 — 같은 로컬 DB에 개선 전(HEAD `1b47700`)과 개선 후 JAR을 두 포트로
동시에 띄워 요청을 번갈아 보냈다.** 조합마다 5회전 × 정렬 3종 = 15회, 첫 회전은 워밍업으로 제외하고 중앙값을 냈다.

| 지역 | 거래유형 | 개선 전 중앙값(p90) | 개선 후 중앙값(p90) | total(전·후 동일) |
| --- | --- | --- | --- | --- |
| 서울(1100000000) | 매매 | 226ms (250) | **190ms** (209) | 1,381 |
| 서울 | 전세 | 287ms (314) | **215ms** (234) | 1,943 |
| 경기도(4100000000) | 매매 | 719ms (724) | **451ms** (459) | 3,597 |
| 경기도 | 전세 | 780ms (802) | **472ms** (477) | 3,469 |
| 수원시 장안구(4111100000) | 매매 | 22ms (30) | **19ms** (30) | 61 |
| 수원시 장안구 | 전세 | 22ms (24) | **19ms** (19) | 62 |

남은 시간은 대표거래를 고르는 목록 쿼리다(경기도 매매 기준 DB 단독 약 360ms). 서울은 기준선 근처, 경기도는
여전히 기준의 2배 이상이다 — 위 보류 결정은 이 수치를 알고 내린 것이다.

아래는 개선안을 정할 때 쓴 **개선 전** DB 쿼리 시간(warm, ms)과 분석이다:

| 지역 | 거래유형 | count | 목록 최신순 | 목록 금액순 | 목록 면적순 | API 응답(3회) |
| --- | --- | --- | --- | --- | --- | --- |
| 서울(1100000000, 1,381/1,943단지) | 매매 | 113 | 125 | 120 | 126 | 160~240ms |
| 서울 | 전세 | 135 | 149 | 148 | 153 | 196~214ms |
| 경기도(4100000000, 3,597/3,469단지) | 매매 | 322 | 366 | 357 | 364 | 518~650ms |
| 경기도 | 전세 | 360 | 386 | 384 | 377 | 550~583ms |

- **원인은 정렬이 아니다.** 정렬 3종 간 차이는 10ms 안팎이다. 비용은 대표거래를 고르는 **상관 서브쿼리 2단(MAX(deal_date) → MAX(trade_id))이 지역 안의 단지마다 도는 것**이다(EXPLAIN: `complex` range 5,559행마다 `DEPENDENT SUBQUERY` 2개). 게다가 매 페이지 요청이 같은 비용의 count 쿼리를 한 번 더 돈다 — API 시간 ≈ count + 목록.
- **개선안 1 — count를 EXISTS로(가장 싼 수정).** 대표거래는 "조건을 만족하는 거래가 하나라도 있는 단지당 정확히 1건"이다. 그래서 total은 `count(complex) WHERE legal_dong_cd LIKE ? AND EXISTS(조건 맞는 trade)`와 같다. 경기도 매매 **322ms → 45ms**이고, 결과가 같다(매매 3,597/3,597, 전세 3,469/3,469로 확인). 그러면 API 시간이 약 40% 줄지만 목록 쿼리만으로 여전히 360ms대다.
- **개선안 2 — 목록을 윈도 함수로.** `ROW_NUMBER() OVER (PARTITION BY complex_id ORDER BY deal_date DESC, trade_id DESC)`로 대표거래를 한 번에 고른다. 경기도 매매 최신순 **366ms → 80ms**이고, 상위 20건 순서가 현재와 같다(매매·전세 모두 확인). QueryDSL-JPA는 FROM절 서브쿼리·윈도 함수를 지원하지 않아 네이티브 쿼리가 필요하다.
- **개선안 3 — 최근거래 비정규화.** BAT-LOD-01 적재 시 (complex, 거래유형)별 최신 거래 id를 별도 테이블이나 컬럼으로 유지한다. 서브쿼리 자체가 없어져 전국 조회까지 감당할 수 있는 유일한 방향이다. 다만 "사용하지 않는 컬럼 추가 금지" 원칙과 스키마 변경 절차를 거쳐야 한다.
- (당시 권장 순서는 1 → 필요 시 2였으나, 위 결정표대로 1만 적용하고 2는 거치지 않기로 했다.)

**FK 인덱스 흡수(인덱스 13건 적용 시 부수효과).** InnoDB는 FK용 자동 인덱스를, 그 FK를 대신할 인덱스가 생기면
조용히 제거한다. 로컬에서 다음 4쌍이 확인됐다(앞이 사라진 인덱스, 뒤가 FK를 떠맡은 인덱스).
- `fk_trade_complex` → `idx_trade_complex_deal_date`
- `fk_trade_legal_dong` → `idx_trade_legal_dong_deal_date`
- `fk_recent_view_user` → `idx_recent_view_user_viewed`
- `fk_notification_user` → `idx_notification_user_read_sent`

그래서 이 4개 인덱스는 바로 DROP할 수 없다(ERROR 1553). 롤백 절차는 runbook 7절에 있다.
`schema/indexes_v2_1.sql`은 이제 `ALGORITHM=INPLACE LOCK=NONE`을 명시한다. 로컬 `trade` 204,520행에 2컬럼 인덱스를
온라인으로 만드는 데 0.27s였다.

**운영 반영 순서와 배포 경로(runbook 머리말).** 순서는 백업 → 인덱스(02:30~07:00 배치 시간대 회피) → 새 JAR로
백필(non-web) → 앱 교체 → SRCH-01 프론트다. 배포 경로는 다음과 같다.
- 백엔드: `ci.yml`이 `main`에서 빌드·테스트만 하므로 main 머지로 자동 배포되지 않는다.
- 프론트: Vercel Production Branch가 `develop`이라 **머지가 곧 배포**다.

그래서 **SRCH-01 프론트 PR은 운영 백필·앱 교체가 끝난 뒤에만 `develop`에 머지한다**(선행 조건).

**[처리완료 2026-09-27, `fix/backend/cache-list-serialization`] 리스트를 반환하는 `@Cacheable` 3종이 캐시 히트 시
500이던 버그.** `GET /api/search/popular`, `/api/complexes/popular`, `/api/regions?query=`가 첫 호출은 200인데 두
번째 호출(캐시 히트)부터 `SerializationException`으로 500을 냈다. 원인: 모든 캐시가 공유하던
`GenericJacksonJsonRedisSerializer.enableUnsafeDefaultTyping()`은 final 타입에 타입 정보를 붙이지 않는다 — record
DTO에는 `@class`가 붙지만(단건 DTO인 `complexDetailV2`는 정상), `Stream.toList()`가 돌려주는 final
`ImmutableCollections.ListN`은 최상위 배열이 타입 정보 없이 저장되고, 읽을 때는 타입 정보를 기대해 실패했다
(스크래치 테스트로 직렬화 결과를 직접 확인).

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| 수정 방식 | 캐시마다 값 타입을 명시한 `JacksonJsonRedisSerializer`(`CacheConfig.cacheConfigurations()`). `enableUnsafeDefaultTyping`은 제거했다 | 반환값을 `new ArrayList<>(…)`로 감싸도 고쳐지지만, 리스트 캐시가 생길 때마다 그 규칙을 기억해야 한다. 타입을 설정에서 명시하면 역직렬화가 저장된 `@class`가 아니라 설정의 타입을 따르므로 임의 타입 역직렬화 경로도 함께 없어진다 | — |
| 새 캐시 등록 | **새 `@Cacheable` 캐시는 `CacheNames`에 상수를 두고 `CacheConfig.cacheConfigurations()`에 값 타입과 함께 등록해야 한다.** 미등록 이름은 자동 생성하지 않는다(`disableCreateOnMissingCache`) — 등록을 잊으면 첫 호출에서 `IllegalArgumentException`("Cannot find cache")이 난다. **캐시 이름은 반드시 `CacheNames` 상수로 쓴다(`@Cacheable("literal")` 금지) — 테스트가 강제한다(2026-09-27 보완):** `CacheConfigTest`는 등록된 이름이 정확히 `CacheNames` 상수 집합과 같은지, `CacheNameRegistrationTest`(`@SpringBootTest`, `./gradlew test`)는 애플리케이션 빈에 선언된 모든 캐시 이름(`@Cacheable`·`@CachePut`·`@CacheEvict`·`@Caching`, 클래스 레벨 `@CacheConfig` 기본값 포함 — Spring의 `CacheOperationSource`에서 읽는다)이 등록돼 있는지 본다. 실패 메시지에 미등록 이름과 선언 메서드가 나온다. 변형 검증: 네 형태의 미등록 이름을 가진 임시 빈을 넣으면 네 개 모두 잡혔다. **한계:** 기존 상수와 문자열이 같은 리터럴(`@Cacheable("regionAutocomplete")`)은 런타임에 구분할 수 없어 통과한다 — 동작은 같지만 버전업 때 한쪽만 바뀌는 드리프트가 생길 수 있으니 리뷰에서 본다 | 미등록 캐시가 기본 설정으로 조용히 만들어지면 값 직렬화기가 없는 상태가 된다 | — |
| `CacheErrorHandler` | `CacheConfig implements CachingConfigurer`, `errorHandler()`가 Spring의 `LoggingCacheErrorHandler`(WARN, 스택트레이스 없음)를 반환한다. 캐시 조회 실패는 미스로 처리돼 DB에서 다시 읽고, 저장·삭제 실패는 로그만 남긴다 | Redis 장애·타임아웃(2s)·읽을 수 없는 엔트리가 500이 되지 않게 한다. `CacheEvictionListener`는 `Cache`를 직접 호출해 이 핸들러를 타지 않지만 자체 try-catch가 있다 | 직렬화 버그가 다시 생기면 500 대신 WARN 로그와 매 요청 DB 조회로만 드러난다 — 캐시 히트를 실제 Redis로 검증하는 `CacheHitMariaDbIT`가 그 안전망이다 |
| 배포 전 엔트리 | 옛 직렬화기가 저장한 `[{"@class":…,"keyword":…}]` 형태는 새 직렬화기가 그대로 읽는다(Jackson 3는 모르는 속성 `@class`를 무시). 읽을 수 없는 엔트리는 위 핸들러가 미스로 처리하고 새 값으로 덮어쓴다. 배포 시 Redis flush나 캐시 이름 버전업이 필요 없다 | `CacheHitMariaDbIT`로 두 경우 모두 확인 | — |

검증: `CacheConfigTest`(TTL, null 허용, 전 캐시 등록, 네 캐시의 `Stream.toList()`·enum·날짜·금액 포함 왕복),
`CacheHitMariaDbIT`(신규 — 실제 Redis로 네 엔드포인트를 두 번씩 호출해 두 응답이 같음, 옛 형식 엔트리 읽기, 읽을 수
없는 엔트리의 미스 처리). **변형 검증:** `CacheConfig`에 옛 직렬화기와 기본 에러 핸들러를 잠시 되돌리면 이 IT에서
리스트 캐시 3건과 엔트리 2건이 실패하고 단지 상세만 통과한다(버그 범위와 일치). `./gradlew test`,
`./gradlew integrationTest` 전부 통과.

**백로그(테스트 격리) — 통합 테스트가 개발용 로컬 Redis(`homesense-redis`, localhost:6379)에 값을 쓴다.**
MariaDB는 Testcontainers로 테스트마다 격리되지만 Redis는 그런 장치가 없어, IT가 개발 중인 로컬 Redis에 직접
쓴다 — 예: `ComplexSearchRegionKeywordMariaDbIT`가 `region:prefix-map:version`을 덮어쓰고(2026-09-27 확인),
로그인 실패 카운터·`user:status`·캐시 키도 같은 인스턴스에 남는다. 개발자의 로컬 상태를 오염시키고, 로컬 Redis가
없으면 IT가 실패한다. Redis도 Testcontainers로 띄워 IT 컨텍스트에 연결하는 방향으로 고친다(아직 착수 안 함).** 수정 방향: 반환 리스트를 `new ArrayList<>(…)`로
감싸거나, 직렬화기의 타이핑 범위를 바꾼다. 어느 쪽이든 캐시 히트 경로를 실제 Redis로 검증하는 테스트를
함께 둔다.

**영향받는 프론트 호출부(이번에 수정하지 않음):**
- `pages/home/HeroSection.tsx` `runSearch()` — `/search?housingType=..&dealType=..&keyword=원문`으로 이동한다.
  SRCH-01이 이 쿼리를 받아 `/api/complexes/search`로 넘길 때 (1) `dealType`을 `dealCategory=SALE` 또는
  `rentType=JEONSE/WOLSE`로 바꿔야 하고, (2) 검색 실행 시 `POST /api/search/logs`를 1회 호출해야 한다.
  HOME-01 인기검색어 칩도 같은 `runSearch()`를 쓴다.
- `features/search`(인기검색어 조회) — `GET /api/search/popular` 계약은 그대로다.
- `features/region/api.ts` — 자동완성 `legalDongCd`를 이제 `regionCode`로 그대로 보내면 된다.
- `/api/complexes/search`를 직접 부르는 프론트 코드는 아직 없다(`/search`는 `PlaceholderPage`).

**문서와 다르게 구현한 부분(설계서 갱신 필요):** 3.3절 search 파라미터(sido/sigungu/dongRi → regionCode,
keyword 필터, rentType 추가)와 처리 로직(검색 기록 제거). 3.11절 SEARCH 도메인(record 호출 주체 →
`POST /api/search/logs`). 테이블정의서 7.2절 인덱스 13건은 "반영 전 검토 필요"에서 "반영됨"으로(아래 인덱스
항목). UI정의서 4.4절 거래유형 라디오와 파라미터 매핑.

**인덱스 13건 복구(별도 커밋 `5b2759a`).** 테이블정의서 v2.1 7.2절/8장의 CREATE INDEX 13건이
`schema_all.sql`과 로컬 DB **둘 다에 한 건도 없었다**(2026-09-23 전수 대조). 재구성 당시 원본 문서가 이
13건을 "반영 전 검토 필요"로 표시해 빠뜨렸기 때문이다. 문서가 기준이라는 결정에 따라
`schema_all.sql` 끝에 원문대로 추가했다. 기존 DB용으로 `schema/indexes_v2_1.sql`(IF NOT EXISTS, 재실행
안전)을 두고 로컬에 적용했다. 새 MariaDB 10.11 컨테이너에서 `schema_all.sql` 적용과 인덱스 스크립트 재실행을
모두 확인했다. `idx_complex_region`은 검색 API가 지역 텍스트를 더 쓰지 않아도 BAT-MAT-02
(`findBySidoAndSigunguAndDongRi`)가 쓰므로 복구 대상이다.

**EXPLAIN 결과(로컬, 2026-09-23).** regionCode 조건은 Hibernate가 조인 없이 `c1_0.legal_dong_cd like ?`로
만든다. 구 단위(`41111%`) count 쿼리는 `complex`가 `range / fk_complex_legal_dong / rows 91 / Using index`이고,
실행은 5ms다(같은 쿼리를 지역 조건 없이 돌리면 1.40s). keyword 조건은 `complex` ALL(20,905행) 스캔이다.
`%kw%`라 B-tree를 못 쓰는 것은 예상대로이고, legal_district_code는 `MATERIALIZED` 서브쿼리 1회로 끝난다.

**검증:** `ComplexLegalDongResolverTest`(시+구 표기, 세종 연속 공백, 같은 이름 리 좁히기, 14826 사례, 비활성·대표행
제외), `RegionCodePrefixResolverTest`(시도·구 보유 시·구 없는 시·세종·읍면동·리·영동/증평·폐지 코드·1회 로드와
재적재 후 재로드), `SearchKeywordPolicyTest`, `ComplexControllerTest`/`SearchControllerTest`(파라미터 바인딩과
400), **`ComplexSearchRegionKeywordMariaDbIT`**(신규, 15건: regionCode 계층 5종, 영동/증평, 없는 코드, keyword의
단지명·주소·법정동명 일치, `%%`·`__`·`!%`·`0%행` 이스케이프, regionCode+keyword+전세+보증금 범위 결합,
매매/전세/월세 분리와 유형별 대표거래, 전세 보증금 범위, 검색 시 search_log 불변, `POST /logs` 기록과 집계
반영, 형식 오류 400, 매매/전세/월세 카드의 rentType·monthlyRentAmount, 조건 없음 400). `./gradlew test` 608건,
`./gradlew integrationTest` 79건 전부 통과(2026-09-23 count EXISTS 적용 후). 로컬에서 실제 코드로
호출한 결과: 수원시 장안구(4111100000) 82건, 수원시(4111000000) 392건, 안성시(4155000000) 80건, 경기도
4,781건, 세종 172건, 리(4155025021) 3건, 영동군 5건, 폐지 코드(2811000000) 0건.

### DTL-01 백엔드 보강 (2026-09-30, `feature/backend/complex-detail-support`)

SCR-DTL-01(단지 상세) 프론트 착수 전 선행 작업이다. 확인에 쓴 문서: `docs/specs/`가 아직 없어 UI정의서 v2.1.1, 프로그램설계서 v2.1.1(Downloads 사본)을 썼다.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| **`TradeResponse.cancelDate` 추가(API-TRD-01)** | 이력 목록 항목에 해제사유발생일(`cancel_date`)을 싣는다. 해제되지 않았거나 전월세면 null이고, `non_null` 직렬화라 JSON에서 키가 빠진다 | UI정의서 5.3절 예외 처리표가 이력 테이블 행에 "해제" 라벨과 해제사유발생일을 요구한다. 전에는 상세(`TradeDetailResponse`)에만 있었다. 이력 조회는 캐시를 적용하지 않아 캐시 버전업이 필요 없다 | — |
| **`ComplexDetailResponse.matchMethod` 추가(SVC-CPX-01), 캐시 `complexDetailV3`** | 정밀/근사 배지용으로 대표 거래(취소되지 않은 가장 최근 거래, `TradeRepository.findRecentTradesByComplexIds`)의 `match_method`를 싣는다. 대표 거래가 없으면 null(배지 생략). 조회는 `ComplexDetailCache.get()` 안에서 하고 결과를 함께 캐시한다. DTO 필드가 늘어 캐시 이름을 V3로 올렸다 | `match_method`는 `trade` 컬럼이라 단지에는 값이 없다. 카드(`ComplexSummaryResponse`)가 대표 거래의 값으로 배지를 정하므로 같은 선정 함수를 쓴다. 거래 적재 시 BAT-LOD-01이 이 캐시 항목을 이미 evict하므로 대표 거래가 바뀌어도 오래된 배지가 남지 않는다 | **SRCH-01과 정확히 같은 값은 아니다:** SRCH-01 카드의 대표 거래는 검색 필터(기본 매매)를 만족하는 최신 거래라, 같은 단지라도 선택한 거래유형에 따라 배지가 다를 수 있다. 상세는 필터 없는 대표 거래(HOME-01 인기 단지·관심 매물과 같은 규칙)를 쓴다. 상세 배지를 거래유형 탭에 맞추기로 하면 이 결정을 다시 본다 |
| **재매칭 후 상세 캐시 무효화(Codex P2, 2026-09-30)** | `TradeRematchRunner`가 배치(500건)가 커밋될 때마다 그 배치에서 거래가 옮겨가거나 매칭 방식이 바뀌거나 중복 삭제된 단지(옛 단지·새 단지 모두)로 `TradeCacheEvictionEvent`를 발행한다. `CacheEvictionListener`가 적재 때와 똑같이 상세 항목과 인기 단지 전체를 비운다. dedup_hash만 고치는 복구 패스는 행을 지운 단지만 보고한다(해시만 바꾸면 대표 거래가 그대로다) | 재매칭은 `applyRematch()`로 DB만 바꾸고 이벤트를 발행하지 않아, 그 전에 채워진 `complexDetailV3`가 TTL(24h) 동안 옛 배지(또는 null)를 돌려줬다. 실행 끝에 한 번이 아니라 배치마다 발행하는 이유: 배치마다 커밋되므로 중간에 실패해도 이미 커밋된 배치의 캐시는 비워져 있어야 한다. 재매칭은 별도 JVM(`rematch` 프로필)으로 돌지만 캐시가 Redis라 서비스 중인 서버에도 반영된다. 검증: `TradeRematchRunnerTest`(발행 호출을 빼면 실패), `TradeRematchBatchProcessorTest`. **로컬 실검증(별도 프로세스):** 거래 2(단지 1의 대표 거래)의 complex_id를 비운 채 상세를 캐시(EXACT, 대표가 거래 12로 바뀐 상태)하고 `local,rematch` 프로필을 실행 → 거래 2가 단지 1 SIMILAR로 복구(unchanged 17,982 / changed 1)되고 `complexDetailV3::1`·`popularComplexesV3::8`이 삭제, 이후 상세 GET은 SIMILAR. rematch 컨텍스트에는 `CacheEvictionListener`와 `RedisCacheManager`가 그대로 뜨고(러너는 트랜잭션이 없어 리스너가 발행 즉시 실행) | trade의 complex_id·match_method를 바꾸는 경로를 새로 만들면 그 경로도 커밋 뒤 이 이벤트를 발행한다 |
| 최근 조회 기록(캐시 히트 시) | 변경 없음 — `record()`는 이미 캐시 빈 밖(`ComplexService.getDetail()`)에서 호출된다 | SVC-RCV-01 절 참고 | — |
| **`TradeResponse.dealingType` 추가(API-TRD-01, 지성 결정)** | 이력 목록 항목에 거래유형을 싣는다. 값은 BAT-PRS-01(`TradeFieldMapper`)이 정규화한 코드 `AGENT`(중개거래)/`DIRECT`(직거래)이고(2026-09-30 로컬 DB: 매매 73,522건 중 AGENT 68,063·DIRECT 5,459, null 0), 전월세는 원천에 없어 null(키 생략). 화면 문구로 바꾸는 것은 프론트가 한다 | 매매 이력 테이블의 "거래유형" 열(UI정의서 5.3절)을 채우려면 목록 응답에 있어야 한다. 전에는 상세(`TradeDetailResponse`)에만 있었다 | BAT-PRS-01이 코드 체계를 바꾸면 프론트 라벨 매핑도 함께 바꾼다 |
| **`ComplexDetailResponse.legalDongCd` 추가(SVC-CPX-01, 지성 결정)** | 단지의 법정동코드(10자리)를 싣는다. 매칭 대기 단지(`matchPending=true`)는 null(키 생략). 지연 로딩 프록시의 식별자만 읽어 추가 쿼리가 없다. 같은 브랜치에서 이미 `complexDetailV3`로 올려 추가 버전업은 없다 | 브레드크럼의 시도(앞 2자리)·시군구(앞 5자리)를 SRCH-01 지역 검색(`regionCode`) 링크로 만든다 | — |
| **상세정보 6그룹 매핑(지성 확정, 합계 28)** | 분양/세대구성(6): `supply_type`·`sale_household_count`·`rental_household_count`·`public_rental_count`·`private_rental_count`·`developer` / 관리방식(2): `management_type`·`management_company` / 승강기(3): `elevator_passenger_count`·`elevator_cargo_count`·`elevator_combined_count` / 주차/전기차(6): `ground_parking_count`·`underground_parking_count`·`ev_charger_ground_yn`·`ev_charger_underground_yn`·`ev_parking_ground_count`·`ev_parking_underground_count` / 보안/편의시설(4): `cctv_count`·`home_network_yn`·`community_facilities`·`resident_amenities` / 관리사무소 및 건물구조(7): `office_address`·`office_phone`·`corridor_type`·`building_structure`·`heating_type`·`highest_floor_registered`·`basement_floor_count` | 어느 문서에도 매핑이 없었다. `ExtendedInfo` 28개 필드·`complex` DDL 컬럼과 대조해 빠지거나 겹치는 것이 없음을 확인했다(2026-09-30). Figma는 5그룹·샘플 라벨이라 디자인과 다르다 | `ExtendedInfo`에 필드를 더하거나 빼면 이 매핑을 다시 정한다 |

**보고만 하고 고치지 않은 불일치:**
- 중개사 소재지(`estateAgentSggNm`)는 수집·저장하지 않는다(`trade`에 컬럼 없음) — 거래상세 모달에서 그 행을 숨긴다(지성 결정). 아래 완결 필요 참고.

검증: `./gradlew test` 630건, `./gradlew integrationTest` 88건 전부 통과. `ComplexDetailCacheTest`(대표 거래 있음 → 그 matchMethod, 없음 → null), `TradeServiceTest`·`TradeControllerTest`(cancelDate 전달·JSON). 로컬 백엔드 스모크: 단지 1의 matchMethod가 첫 호출·캐시 히트 모두 `SIMILAR`, 단지 14894 매매 이력의 해제 건(trade 391755)에 `cancelDate` 2026-09-17.

### 프로그램설계서 반영 필요 (문서 반영은 claude.ai에서)

- 3.3절 SVC-CPX-01 `getDetail()` 응답: `matchMethod`(대표 거래 기준) 추가, 캐시 이름 `complexDetailV3`(2026-09-30).
- 3.4절 SVC-TRD-01 `getHistory()` 응답 `TradeResponse`: `cancelDate`, `dealingType`(`AGENT`/`DIRECT`) 추가(2026-09-30).
- 3.3절 SVC-CPX-01 `getDetail()` 응답: `legalDongCd` 추가(2026-09-30).
- **테이블정의서 3.5절·8장 `notification.sent_at`: NOT NULL → NULL 허용, 설명 "발송 완료 시각, NULL=발송 대기"(2026-10-06, BAT-NTF-01 D1).**
- 3.8절 SVC-NTF-01 `getNotifications()`: 정렬 `created_at DESC, notification_id DESC`(sent_at 아님), 응답에 `createdAt` 추가, `sentAt`은 미발송 시 null이라 JSON 키가 빠진다(2026-10-06).
- **UI정의서 5.5절 MY-04(알림 이력) 응답·"발생 일시": `createdAt` 기준, 미발송 알림도 표시, `sentAt`은 생략될 수 있음(2026-10-06).**
- 4.6절 BAT-LOD-01: 적재 결과(`LoadResult`)에 영향받은 단지·법정동 집합을 실어 BAT-SCH-01이 BAT-NTF-01에 넘긴다(2026-10-06).
- 4.9절 BAT-NTF-01: 시그니처 `evaluateAfterLoad(NotificationTriggerContext)`, 처리 로직 D2~D7(아래 "BAT-NTF-01 구현 결정 사항")(2026-10-06).
- 4.10절 BAT-MAIL-01: "발송 큐 전달" → `sent_at IS NULL` DB 대기열 인계, `email_alert_yn` 처리는 미결(2026-10-06).
- 4.1절 BAT-SCH-01·6.3/6.4절 흐름: 조합 순회(조기 중단 포함) 직후 BAT-NTF-01 직접 호출, 백필 미호출(2026-10-06).

**완결 필요(DTL-01) — 중개사 소재지 `agent_sgg_nm` 미수집(BAT-PRS-01 매핑 확인 필요).** UI정의서 5.3절 거래상세 모달은 "거래유형 + 중개사 소재지(`estateAgentSggNm`, 시군구 단위)"를 요구하지만 `trade`에 대응 컬럼이 없고 파서(`TradeFieldMapper`)도 매핑하지 않는다. 모달에서는 그 행을 숨긴다. 표시하려면 원천 필드명 확인 → 컬럼 추가(DDL 3곳) → 파서 매핑 → `TradeDetailResponse` 순으로 한다.

### MY-02 백엔드 보강 (2026-10-02, `feature/backend/favorite-summary-support`)

SCR-MY-02(관심 매물·지역 관리) 프론트 착수 전 선행 작업이다. 확인에 쓴 문서: `docs/specs/`가 없어 작업 지시에 옮겨 둔 UI정의서 v2.1 5.5절 MY-02와 프로그램설계서 3.7절 원문.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| **관심 매물 "최근 거래가"를 매매만으로(SVC-FAV-01)** | `FavoritePropertySummaryResponse`의 recent*(거래가·거래일·면적·층)를 취소되지 않은 **매매** 거래 중 최신 1건에서 채운다. `TradeRepository.findRecentSaleTradesByComplexIds()`를 새로 두고, 기존 `findRecentTradesByComplexIds()`와 선정 쿼리를 공유한다(`findRepresentativeTrades(ids, dealCategory)`, 유형 조건은 바깥 행과 두 상관 서브쿼리에 모두 건다). 기존 메서드(CPX·RCV·상세 배지)의 동작은 그대로다. 매매 거래가 없는 단지는 recent*가 모두 null | 전에는 유형을 가리지 않아 최근 거래가 전세면 보증금이 "최근 거래가"로 보였고, 같은 카드의 변동률(매매만 집계)과 기준이 어긋났다. `recentDealCategory`는 이제 SALE 또는 null이지만 MY-01 미리보기 호환을 위해 남겼다. 검증: `TradeRepositoryMariaDbIT` 2건(더 최신 전세·취소된 매매를 건너뜀, 전월세만 있으면 키 없음 — 같은 테스트에서 기존 메서드는 여전히 전세를 고름) | 관심 매물 카드에 전월세 시세를 따로 보여 주게 되면 유형별 필드를 나눈다 |
| **최근 거래 면적·층, 등록일시 추가** | 관심 매물에 `registeredAt`, `recentArea`(전용면적), `recentFloor`. 관심 지역에 `registeredAt`, `sidoName`, `sigunguName`, `eupmyeondongName` | 같은 단지라도 평형별 가격이 달라 면적 없이 최근 거래가를 해석할 수 없다. 등록일시는 "등록순" 정렬과 카드 표시에 쓴다(전에는 응답에 없어 MY-01이 ID로 정렬했다). 지역 이름 조각은 같은 이름의 동을 시군구로 구분하려는 것이다 — fullPath를 공백으로 쪼개면 "수원시 장안구"처럼 공백이 든 시군구에서 틀린다. FAV는 캐시 미적용이라 캐시 이름 버전업이 필요 없다 | — |
| **관심 지역은 읍·면·동 단위만(400 `INVALID_REGION_LEVEL`)** | `addFavoriteRegion()`이 맨 먼저 코드 형태를 검사한다: 10자리 숫자이고 읍면동 자리(6~8번째)가 000이 아니며 리 자리(9~10번째)가 00. 아니면 `InvalidFavoriteRegionLevelException`(400, "읍·면·동 단위 지역만 관심 지역으로 등록할 수 있습니다"). 존재하지 않거나 폐지된 읍면동 코드는 기존대로 404 `REGION_NOT_FOUND` | FR-5.2는 읍면동 단위다. 기존 조회 조건(활성 + eupmyeondongName not null)은 시도·시군구 대표행은 막았지만 리 단위 행(eupmyeondongName "기장읍 동부리")은 통과시켰다. 프론트 필터만으로는 API 직접 호출을 막을 수 없다. 검증: `FavoriteServiceTest` 7개 코드(시도·시군구 대표행·구·리·길이·문자), `FavoriteControllerTest`(400 코드·문구). 테스트 픽스처의 `9999999999`·`1168099999`는 리 자리가 00이 아니라 `…00`으로 바꿨다 | 리 단위 관심 지역이 요구되면 이 검사를 넓히고 통계 집계 단위(`RegionStatsCalculator`)도 함께 본다 |
| 알림 설정 요약 | 관심 매물 응답에 넣지 않았다. MY-02가 `GET /api/notifications/settings`를 함께 불러 `favoritePropertyId`로 조인한다 | SVC-NTF-01이 처음부터 이 조인을 전제로 설계됐다("NotificationSettingResponse에 대상 표시 필드 미포함" 행). 응답에 임계치·신규거래·이메일 여부가 이미 있다 | 조인 비용이나 실패 처리가 문제가 되면 요약 필드를 관심 매물 응답에 넣는다 |
| N+1 | 변경 없음 — 목록은 이미 JOIN FETCH와 배치 집계로 항목 수와 무관한 고정 쿼리 수다 | SVC-FAV-01 절 | — |

**알려진 한계(고치지 않음):** 지역 자동완성(`GET /api/regions?query=`)은 법정동코드 순 상위 10건이라, 시군구 이름으로 검색하면 리 단위 행이 10건을 채워 뒤쪽 읍면동 후보가 잘릴 수 있다(MY-02는 읍면동만 남기도록 화면에서 거른다). 읍면동 이름까지 입력하면 나온다. 서버에 단위 필터를 두려면 `regionAutocomplete` 캐시 키를 바꿔야 한다.

**프로그램설계서 반영 필요:** 3.7절 SVC-FAV-01 `getFavoriteProperties()`(최근 거래는 매매만, 응답 필드 `registeredAt`·`recentArea`·`recentFloor`), `getFavoriteRegions()`(응답 필드 `registeredAt`·`sidoName`·`sigunguName`·`eupmyeondongName`), `addFavoriteRegion()` 예외표(400 `InvalidFavoriteRegionLevelException`).

검증: `./gradlew test` 646건, `./gradlew integrationTest` 90건(1건은 기존 `RefreshTokenReuseAccessCutoffMariaDbIT`의 같은 초 확인 `assumeTrue` 건너뜀) 전부 통과.

### BAT-NTF-01 관심대상 조건평가·알림 생성 (2026-10-06, `feature/backend/notification-trigger`)

BAT-SCH-01 조합 순회가 끝나면 `WatchConditionEvaluator.evaluateAfterLoad()`(`batch.notifier`)가 이번 런과 연관된 `notification_setting`만 평가해 `notification`을 만든다(FR-6.1, FR-6.2). 이메일 발송(BAT-MAIL-01)은 아직 없다. 확인 문서: `docs/specs/`가 없어 작업 지시에 옮긴 프로그램설계서 4.1·4.6·4.9·6.3절, 테이블정의서, UI정의서 MY-03·MY-04 요약.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| **D1. `notification.sent_at` NULL 허용(설계서·테이블정의서 이탈)** | `sent_at`은 BAT-MAIL-01이 발송 후 채우고 BAT-NTF-01은 비워 둔다. `sent_at IS NULL`이 곧 발송 대기열이다(새 컬럼 없음). 알림 목록은 `created_at DESC, notification_id DESC`로 정렬하고 미발송 알림도 숨기지 않는다. `NotificationResponse`에 `createdAt`(발생 일시)을 추가하고 `sentAt`은 유지한다(미발송이면 null → JSON 키 생략). DDL은 `schema_all.sql`·`testcontainers/withdrawn-user-purge-schema.sql`을 고쳤다. **기존 DB 마이그레이션(배포 단계, 코드리뷰 P1로 추가):** `schema_all.sql`의 CREATE TABLE을 고쳐도 이미 만든 DB는 그대로 NOT NULL이라 알림 INSERT가 전부 거부된다. 저장소에 자동 마이그레이션 도구가 없어 `indexes_v2_1.sql`과 같은 방식으로 `backend/src/main/resources/schema/notification_sent_at_nullable.sql`(재실행 안전)을 두고, 이 커밋이 들어간 백엔드를 기동하기 전에 DB마다 실행한다("명령어" 절). 적용을 잊은 경우에 대비해 `WatchConditionEvaluator`가 평가 전에 `information_schema`로 이 컬럼을 확인하고, NOT NULL이면 원인(적용할 파일 경로)을 담은 예외로 평가를 건너뛴다 — 오케스트레이터가 ERROR로 남기고 흡수하므로 수집은 계속된다. 검증: IT가 NOT NULL로 되돌린 DB에서 예외·알림 0건을 확인한 뒤 실제 마이그레이션 파일을 적용해 알림 5건 생성을 확인한다(사전 점검을 끄면 실패) | 설계서 4.9("이 단계에서 sent_at을 확정하지 않는다")와 테이블정의서(NOT NULL)가 충돌해 단계 간 책임 분리를 따랐다. MY-04 "발생 일시"는 생성 시각이고, 4.10은 발송 최종 실패 알림도 MY-04에 노출한다. 응답 필드를 이름만 바꾸거나 sentAt에 created_at 값을 넣지 않은 이유: 둘 다 뜻이 어긋나거나 프론트를 깬다. **인덱스:** `idx_notification_user_read_sent`는 user_id 범위 조회에만 쓰이고 정렬은 filesort다 — sent_at 정렬이던 때도 is_read가 끼어 같았다(인덱스는 추가하지 않음) | BAT-MAIL-01이 별도 발송 큐 테이블을 쓰게 되면 다시 본다 |
| **프론트 계약 변화 — 같은 PR에서 반영** | 미발송 알림에는 `sentAt` 키가 없다. MY-01 `NotificationPreview`가 `sentAt`으로 시각을 그려, 첫 배치가 알림을 만드는 순간 `formatRelativeTime(undefined)`가 예외를 던져 마이페이지가 깨질 상황이었다(코드리뷰 P1). 그래서 프론트 수정을 이 PR에 넣었다: 시각은 `createdAt`, 한 줄 문구는 `title`, 타입의 `sentAt`은 optional. MY-04도 `createdAt`을 쓴다. 딥링크 필드(`complexId`·`legalDongCd`·`tradeId`)는 이미 응답에 있다 | 처음엔 "알림이 아직 없어 실해 없음"으로 다음 브랜치에 미뤘지만, 그 말은 첫 배치 전까지만 맞다 — 백엔드와 프론트가 같은 머지로 나가야 한다 | — |
| **D2. "이번 런에 신규 INSERT된 거래" = `trade.created_at >= 런 시작 시각`(설계서 시그니처 이탈)** | 런 시작 시각(초 절삭)을 `BatchExecutionOrchestrator.orchestrate()`가 순회 전에 잡아 `NotificationTriggerContext`로 넘긴다. affected 집합은 설정 조회 범위를 좁히는 데만 쓴다. 시그니처는 `evaluateAfterLoad(NotificationTriggerContext)`(런 시작 시각·KST 런 날짜·단지·법정동 집합) | 청크 커밋을 넘어 신규 trade_id를 메모리에 모을 필요가 없고, 같은 날 재실행해도 이전 런 적재분은 신규가 아니라 중복 알림이 구조적으로 생기지 않는다. upsert의 `ON DUPLICATE KEY UPDATE`는 `created_at`을 건드리지 않는다(코드 확인). **시간 소스(2026-10-06 PR 전 재확인):** trade INSERT 경로는 네이티브 upsert 하나뿐이고, `created_at`은 SQL `NOW()`·DDL 기본값이 아니라 `TradeChunkLoader`가 바인딩하는 JVM 기본 타임존의 `LocalDateTime.now()`다. 그래서 런 시작 시각도 같은 방식으로 잡는다(DB `time_zone`과 무관). 쓰기(Hibernate)와 비교(JdbcTemplate)의 바인딩 경로가 달라도 드라이버가 타임존을 변환하지 않는다는 것을 IT로 확인했다 — JVM KST·컨테이너 DB UTC 상태에서 저장된 `created_at`이 JVM 벽시계와 같고, 적재 SQL을 `NOW()`로 바꾸면 그 IT가 실패한다 — KST Clock으로 잡으면 UTC JVM에서 9시간 어긋나 알림이 0건이 된다. 기준 기간(계약일)만 KST 날짜다 | 적재기 병렬화, 백필 러너가 정규 배치와 동시에 돌 수 있게 되는 경우, `TradeChunkLoader`의 시간 소스를 바꾸는 경우(런 시작 시각도 같이 바꾼다) |
| affected 집합 전달 | `LoadResult`에 `touchedComplexIds`·`touchedLegalDongCds`를 실었다(캐시 무효화 이벤트와 같은 집합, 4-인자 생성자 유지). 오케스트레이터가 `ingestAndLog`에서 런 단위로 모은다(런마다 초기화) | 이벤트 리스너로 모으면 재매칭·백필도 같은 이벤트를 내 런 경계를 아는 누적기가 따로 필요해진다 | — |
| **D3. NEW_TRADE는 설정당 런당 1건** | 대상 거래: 매매·전월세, 해제 제외. 대표 거래 = 최신 계약일, 같으면 큰 trade_id → `trade_id`. 제목 `{대상명} 신규 실거래 {n}건`, 메시지 `{yyyy.MM.dd} 계약 · 전용 {면적}㎡ · {층}층 · {매매 9억 5,000만원 / 전세 3억 / 월세 1,000/50}` + ` 외 {n-1}건`(층이 없으면 그 구간 생략). 대상명: 관심 매물 `complex.complex_name`, 관심 지역 `legal_district_code.legal_dong_name`(예: "서울특별시 종로구 창신동"). 제목 200자·메시지 500자(코드포인트) 초과 시 자르고, 제목은 대상명만 줄인다 | 거래당 1건이면 인기 단지 관심 사용자에게 하루 수십 건이 쌓인다 | — |
| **D4. PRICE_CHANGE 산정** | 매매·미해제·`deal_amount` 있음·면적>0만. 3.3㎡당 평균가(거래별 `deal_amount / (면적/3.3058)` 후 평균 — `TradeRepository.findAveragePricePerPyeongForSale`과 같은 식). 신규 평균 = 이번 런 신규 매매(1건 이상일 때만). 기준 평균 = 계약일이 런 날짜 기준 직전 3개월 이내이고 `created_at < 런 시작`인 거래, **3건 미만이면 스킵**. 변동률은 소수 첫째 자리 반올림(HALF_UP) 값으로 `abs(r) >= threshold && r != 0.0` 판정. 관심 지역은 주택유형 합산. 기간·최소 표본·청크 크기는 `homesense.notifier.*`(`NotifierProperties`, 기본 3·3·200). 제목 `{대상명} 실거래가 2.1% 상승`, 메시지 `최근 3개월 평균 3.3㎡당 {X}만원 → 신규 매매 {n}건 평균 3.3㎡당 {Y}만원`. 전월세 보증금 변동은 제외 | 반올림 값으로 판정해야 제목 수치와 판정이 일치하고 0% 설정이 MY-03 "변동이 있을 때마다"대로 동작한다. 면적 구성 차이를 상쇄하려고 3.3㎡당 | 지역 알림이 유형 구성비 변화로 오탐한다는 확인, 전세가 알림 요구 |
| **D5. 관심 지역 매칭 = 법정동 계층** | 거래 법정동마다 상위 후보(자신, 앞8+00, 앞5+00000, 앞4+000000, 앞2+00000000 — `LegalDongHierarchy`)를 펼쳐 `favorite_region.legal_dong_cd IN (...)`로 찾는다. 집계 범위는 관심 지역 코드의 prefix(기존 `RegionCodePrefixResolver.prefixOf()` 재사용 — 구를 가진 시는 이름으로 판정)로 정하고, 신규 거래는 Java에서 prefix 비교, 기준 평균은 `legal_dong_cd LIKE prefix%`를 200개씩 OR로 묶어 법정동별 GROUP BY한 뒤 prefix별로 합산한다. prefix를 못 구하는 코드(비활성)는 `REGION_UNRESOLVED`로 스킵 | 리 거래가 읍·면 관심 지역에서 빠지지 않게 한다. **전제 차이:** MY-02 백엔드 보강 이후 관심 지역은 API로 읍면동 코드만 등록된다(그 전 등록분은 리 코드 가능) — 시군구·시도 확장은 지금은 직접 넣은 행에만 해당하지만 무해하다 | — |
| **D6. ACTIVE 회원만** | 설정 조회 쿼리에 `user.status = 'ACTIVE'` | 탈퇴 유예·정지 회원에게 알림 레코드를 만들 이유가 없다. BAT-MAIL-01의 WITHDRAWN 스킵은 이중 방어로 남긴다 | — |
| **D7. 딥링크 필드** | 관심 매물: `complex_id`(+NEW_TRADE면 `trade_id`). 관심 지역: 등록된 `legal_dong_cd` 그대로(+NEW_TRADE면 `trade_id`), `complex_id`는 비운다 | MY-04가 "complexId 있으면 DTL-01, 없으면 SRCH-01 지역"으로 단순 분기한다. MY-01 알림 점 색(대상 종류)도 이제 가를 수 있다("SCR-MY-01" 절 알림 점 색 행) | — |
| **D8. BAT-MAIL-01 인계 = DB 대기열** | `email_alert_yn`과 무관하게 알림을 만든다(MY-04 이력은 인앱 기록). 결과는 `NotificationTriggerResult`(평가 설정 수, NEW_TRADE/PRICE_CHANGE 생성 수, 스킵 사유별 수, 실패 수)로 돌려주고 구조화 로그 1줄로 남긴다 | `batch_log`는 API 조합 단위 스키마(lawd_cd·deal_ymd NOT NULL)라 쓰지 않는다 | — |
| **D9. 실패 격리** | 설정 200건을 `TransactionTemplate` 한 번으로 커밋한다. 청크 안의 저장 하나가 실패하면 트랜잭션이 rollback-only가 돼 청크 전체가 날아가므로, 그 청크만 설정 1건씩 개별 트랜잭션으로 다시 저장해 실패한 설정만 뺀다. 계산 단계 예외도 설정 단위로 잡는다. 오케스트레이터는 평가기 예외를 흡수해(ERROR 로그) 수집 결과·완료 이벤트에 영향을 주지 않는다 | NFR-5. 청크 전체 롤백 함정은 "REQUIRES_NEW 격리 INSERT 게이트웨이 패턴" 절과 같다 | — |
| **D10. 트리거 위치·조기 중단에도 평가(J1, 지시 외 판단)** | `orchestrate()`에서만 직접 호출한다(이벤트 리스너 아님). `orchestrateBackfill()`·재매칭·백필 러너는 호출하지 않는다. **순회가 어떤 경로로 끝나든 평가한다(`runCombinations` 호출을 `try/finally`로 감싸 `finally`에서 평가)** — 정상 완료, `CriticalBatchException` 조기 중단, 그 밖의 예외(인터럽트로 인한 `BatchInterruptedException`, batch_log 저장 실패 등) 모두. 중단 전 커밋된 거래를 이번 런에 평가하지 않으면 다음 런 시작 시각이 더 늦어 영영 신규로 잡히지 않는다(D2 덕에 중복은 없다). 평가는 예외를 흡수하므로 순회의 원래 예외는 그대로 던져진다. 인터럽트 경로에서는 `ApiCallThrottle`·`RetryQueueManager`가 인터럽트 상태를 다시 세운 채 던지므로, 그대로면 평가의 커넥션 획득이 곧바로 실패한다 — 평가하는 동안만 인터럽트 상태를 비우고 끝나면 되돌린다. (처음엔 `CriticalBatchException`만 처리했다가 코드리뷰 P1로 모든 종료 경로로 넓혔다. 검증: `BatchExecutionOrchestratorTest` — batch_log 저장 실패·인터럽트 경로. `finally`를 빼면 2건, 인터럽트 비우기를 빼면 1건 실패.) 스케줄러 활성화 상태는 바꾸지 않았다 — **03:00 스케줄은 기본 활성이다**(`homesense.scheduling.enabled` 기본 true) | 대량 백필이 알림 폭주를 일으키지 않게 한다 | 백필분도 알림이 필요해지면 |
| **D11. 도메인 간 접근** | 평가용 교차 조회는 `batch.notifier.WatchConditionQuery`(NamedParameterJdbcTemplate 네이티브 SQL, projection, IN 1,000개 분할)가 소유하고, 알림 INSERT는 notification 도메인 엔티티·`NotificationRepository`(연관은 `EntityManager.getReference`)로 한다 | 아래 "도메인 간 접근" 표의 batch.matcher 선례와 같은 방향 | — |

**도메인 간 접근(2026-10-06 — 결정이 흩어져 있어 한 표로 모았다):**

| 호출자 | 다른 도메인 접근 방식 | 예 |
| --- | --- | --- |
| 배치 매처·적재기(`batch.matcher`/`batch.loader`) | 다른 도메인 리포지토리를 직접 읽는다 | `ComplexMasterMatcher` → `ComplexRepository`, `LegalDistrictMatcher` → `LegalDistrictCodeRepository` |
| BAT-USR-01(`batch.scheduler`) | 도메인 서비스를 호출한다(쓰기는 그 도메인이 소유) | `WithdrawnUserPurgeScheduler` → `user.service.WithdrawnUserPurgeService` |
| BAT-NTF-01(`batch.notifier`) | 교차 조인 읽기는 notifier 소유 네이티브 쿼리, 쓰기는 notification 엔티티 | `WatchConditionQuery`, `NotificationRepository.save()` |
| 도메인 서비스 | 다른 도메인 리포지토리 직접 주입 허용, 서비스끼리 순환 호출 금지 | `FavoriteService` → `TradeRepository`·`RegionStatsCalculator` |

**BAT-MAIL-01 미결(구현할 때 정할 것):** (1) `email_alert_yn=false` 설정의 알림을 어떻게 거를지 — 알림에 설정 ID가 없으므로 발송 시점에 (user, 대상)으로 설정을 다시 조회하는 등의 방법을 정한다. (2) 발송 후 `sent_at`을 채우는 갱신과 최종 실패 처리 — NULL로 두면 다음 실행에 다시 보내므로 재시도 상한이 필요하다. (3) 실행 시간은 03:00 수집 체인 뒤, 05:00 파기와 겹치지 않게. (4) **도입 전 누적된 미발송 알림.** 03:00 스케줄이 기본 활성이라 이 백엔드를 배포한 날부터 알림이 `sent_at IS NULL`로 매일 쌓인다 — BAT-MAIL-01을 나중에 붙이면 첫 실행에 몇 주치 알림이 한꺼번에 이메일로 나간다. 다음 중 하나를 설계에 넣는다: 발송 대상을 최근 N일 안에 생성된 알림으로 제한하거나, 도입 시점 이전의 미발송분을 발송하지 않는 것으로 정리한다(예: 도입 배포 때 그 이전 생성분을 발송 제외로 표시). 인앱 이력(MY-04)에는 그대로 남긴다.

검증: 단위 `PriceChangeCalculatorTest`(반올림 경계 4.95→5.0, 음수, 임계치 일치, threshold 0에서 0.0 미발송·0.1 발송)·`NotificationTextFormatterTest`(1억, 9,999만원, 10억 5만원, 전세·월세, 층 없음, 200/500자 절단·코드포인트)·`LegalDongHierarchyTest`·`BatchExecutionOrchestratorTest`(적재 집합 전달·런마다 초기화·조기 중단 시 호출·평가 예외 흡수·백필 미호출)·`NotificationControllerTest`(createdAt 직렬화, 미발송 sentAt 키 생략). IT `WatchConditionEvaluatorMariaDbIT` 8건(기존 DB에 sent_at 마이그레이션이 없으면 평가를 멈추고 실제 마이그레이션 파일 적용 후 알림 생성; 실제 적재 경로로 들어온 거래가 런 시작 이후 신규로 잡힘 — JVM KST·DB UTC 상태에서 created_at이 JVM 시각으로 저장됨을 확인, 적재 SQL을 `NOW()`로 바꾸면 실패; 기대 알림만 생성·건수·대표 trade_id·딥링크·`sent_at IS NULL`·WITHDRAWN 미생성·해제 제외·기간 밖 거래 제외·시군구 관심 지역 집계, 새 런 재실행 0건, 임계치 미만, 신규거래 끔, 빈 집합, 미발송 알림이 목록에 created_at 순으로). 변형 검증: 신규 거래 쿼리의 `created_at >= 런 시작`을 빼면 3건, `status = 'ACTIVE'`를 빼면 3건 실패한다. `./gradlew test` 672건, `./gradlew integrationTest` 98건(19개 클래스, 건너뜀 0) 전부 통과(2026-10-06, 기존 DB 마이그레이션 IT 포함). 그 IT의 컨테이너 DB 타임존은 UTC-10으로 고정해 JVM 타임존(KST·UTC)과 항상 다르게 둔다.

### 외부연동 설정(COM-CFG-01)
프로그램 설계서는 `ExternalApiProperties` 하나에 `getDataGoKrServiceKey()`/`getKakaoApiKey()`/`getJwtSecret()` 세 메서드를 두는 단일 클래스로 정의하지만, 실제 구현은 이미 각 도메인이 소유한 `@ConfigurationProperties` 레코드로 나뉘어 있습니다 — 설계서보다 먼저 BAT-CLC-01(`DataGoKrProperties`)과 COM-SEC-01/02(`JwtProperties`)가 구현되며 이미 굳어진 구조라, COM-CFG-01 시점에 하나로 합치지 않고 그대로 두었습니다. 새로 코드를 짤 때는 이 구조를 따르세요.

| 시크릿 | 클래스 | 프리픽스 | 소비자(등록 위치) | fail-fast |
| --- | --- | --- | --- | --- |
| 국토부 serviceKey | `DataGoKrProperties` | `homesense.external.data-go-kr` | BAT-CLC-01, `OpenApiRestClientConfig`가 `@EnableConfigurationProperties` | `@NotBlank` |
| JWT 서명 키 | `JwtProperties` | `homesense.security.jwt` | COM-SEC-01/02, `SecurityConfig`가 `@EnableConfigurationProperties` | `@NotBlank`(길이 검증은 `JwtTokenProvider` 생성자의 `Keys.hmacShaKeyFor()`가 `WeakKeyException`으로 담당) |
| 카카오 API 키 | `KakaoProperties` | `homesense.external.kakao` | 아직 없음(`ExternalApiConfig`가 임시로 `@EnableConfigurationProperties`만) | 없음(의도적) |

카카오 키만 `@NotBlank`를 걸지 않았습니다 — BAT-GEO-01(지오코딩)이 아직 3단계 범위라 구현되지 않아 이 값을 소비하는 코드가 없고, 걸어 버리면 BAT-GEO-01 없이 배포하는 1~2단계에서도 `KAKAO_API_KEY`가 없다는 이유로 기동이 막힙니다. BAT-GEO-01을 구현하는 시점에 `DataGoKrProperties`와 같은 패턴(`@Validated` + `@NotBlank`)을 추가하고, `KakaoProperties` 등록을 `ExternalApiConfig`에서 그 소비자의 설정 클래스로 옮기세요.

`@Validated`/`@NotBlank`는 Spring이 `@ConfigurationProperties`를 바인딩하는 시점에만 동작합니다 — `new JwtProperties("", ...)`처럼 레코드를 직접 생성하는 단위 테스트에서는 트리거되지 않으므로(검증하려면 `ApplicationContextRunner`로 실제 컨텍스트를 띄워야 함, `ExternalApiPropertiesTest` 참고), `JwtTokenProviderTest`가 만료 토큰을 만들려고 `accessTokenValidity`에 음수를 직접 넣는 것과 충돌하지 않습니다.

### 캐싱
Redis, TTL 기본 24시간. 배치 적재 완료 시 관련 캐시를 evict합니다.

| 캐시 키 | 적용 대상 |
| --- | --- |
| `complexDetailV3::{complexId}` | 단지 상세 조회(V3: matchMethod 추가, 2026-09-30) |
| `popularComplexesV3::{limit}` | 인기 단지 목록(V3: rentType·monthlyRentAmount 추가, 2026-09-23) |
| `regionAutocomplete::{query}` | 지역 자동완성 |
| `popularKeywords::{limit}` | 인기 검색어(TTL 1시간) |

캐시 이름은 `common.cache.CacheNames` 상수로 관리한다. `@Cacheable`, `CacheEvictionListener`, 유지보수 러너가 같은 상수를 쓴다. **캐시 이름은 반드시 `CacheNames` 상수로 쓰고, 새 캐시는 `CacheConfig.cacheConfigurations()`에 값 타입과 함께 등록해야 한다(`CacheConfigTest`·`CacheNameRegistrationTest`가 강제)** — 미등록 캐시는 자동 생성되지 않는다(2026-09-27 리스트 캐시 500 hotfix, "단지 검색 지역코드·키워드·거래유형" 절 참고). 캐시 조회·저장 실패는 `CacheErrorHandler`가 로그만 남기고 미스로 처리한다.

**`ComplexDetailResponse`·인기 단지 응답에 들어가는 값(complex 컬럼, 대표 거래의 complex_id/match_method)을 바꾸는 모든 경로는 커밋 후 해당 캐시를 evict한다(단건은 이벤트, 일괄은 전체 clear).** 단건 경로: `TradeDataLoader`(적재), `TradeRematchRunner`(재매칭) → `TradeCacheEvictionEvent`. 일괄 경로: `ComplexLegalDongBackfillService`(complex.legal_dong_cd 백필) → 전체 비움. 단지 마스터 적재기가 저장소에 들어오면 그것도 이 규칙을 따른다.

**코드에서 캐시를 지울 때는 `Cache.evict()`/`clear()`가 아니라 `evictIfPresent()`/`invalidate()`를 쓴다(2026-09-30).** Spring Data Redis 4.1.1은 Lettuce 연결에서 `evict()`/`clear()`를 비동기로 보내고 결과를 기다리지 않는다(`DefaultRedisCacheWriter.writeAsynchronously()`). 그래서 `System.exit`로 끝나는 1회성 러너에서는 삭제가 Redis에 닿기 전에 사라질 수 있고(백필 러너로 재현: `clear()` 뒤 `popularComplexesV3::8`이 남았다), Redis 오류도 호출부의 try-catch에 잡히지 않는다. `evictIfPresent()`/`invalidate()`는 Spring `Cache` 계약상 즉시 실행이다. `@CacheEvict` 애노테이션은 이 경로를 쓰지 않는다 — 쓰게 되면 `beforeInvocation`이나 즉시 실행 여부를 따로 확인한다.

**완결 필요(운영 배포 전) — `invalidate()`가 Redis `KEYS` 명령을 쓴다.** `CacheConfig`는 `RedisCacheManager.builder(connectionFactory)`로 만들어 기본 `RedisCacheWriter.nonLockingRedisCacheWriter(cf)`를 쓰고, 이 기본값의 배치 전략은 `BatchStrategies.keys()`다(spring-data-redis 4.1.1 jar 바이트코드로 확인, 2026-09-30). 그래서 `invalidate()`(`CacheEvictionListener`의 인기 단지·자동완성 전체 비움, 백필 러너)가 `KEYS {cacheName}::*`를 보낸다. `KEYS`는 키 전체를 훑는 동안 Redis를 막는다. 로컬에서는 키가 적어 문제없지만, **운영 배포 시 `RedisCacheWriter`에 `BatchStrategies.scan(1000)`을 지정한다**(`RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(cf, BatchStrategies.scan(1000)))`). 지금은 코드 변경 없음.

거래 검색/이력 조회는 배치 직후 변경 가능성이 있어 **캐시를 적용하지 않습니다.**

**캐시 이름에 담긴 DTO에 필드를 추가/변경할 때는 캐시 이름 자체를 버전업하세요(`complexDetail` → `complexDetailV2`처럼).** Redis가 배포 사이에도 살아남는 환경에서는 옛 필드 구성으로 직렬화된 엔트리가 새 배포 후에도 남아있고, 새로 추가된 필드는 역직렬화 시 예외 없이 조용히 null로 채워집니다. 그 null이 소비 로직에서 정상적인 분기를 타 버릴 수 있습니다 — 실제로 SVC-RCV-01에서 `ComplexDetailResponse`에 `housingType`을 추가했을 때, 배포 전 캐시된 옛 엔트리가 `housingType=null`로 역직렬화되면 `RecentViewService.record()`가 이를 "housingType 미확정"과 구분하지 못해 조용히 기록을 스킵하는 문제가 있었습니다(Codex 코드리뷰 지적, 검증: `ComplexDetailCache.java`). 캐시 이름을 올리면 옛 엔트리를 애초에 다시 읽지 않게 되고, 그 옛 엔트리는 각자의 TTL로 자연 만료됩니다 — 배포 시점에 수동으로 Redis를 flush할 필요가 없습니다.

**캐시 무효화 트리거는 캐시마다 다릅니다 — 하나의 이벤트에 묶지 마세요:**

| 캐시 | 무효화 이벤트 | 발행 주체 | 주기 |
| --- | --- | --- | --- |
| `complexDetailV3::{complexId}` | `TradeCacheEvictionEvent` | BAT-LOD-01(`TradeDataLoader`), BAT-MAT-02 재매칭(`TradeRematchRunner`, 배치 커밋마다) | 일 1회 이상 / 재매칭 실행 시 |
| `popularComplexes` | `TradeCacheEvictionEvent` (전체 evict) | BAT-LOD-01(`TradeDataLoader`), BAT-MAT-02 재매칭(`TradeRematchRunner`) | 일 1회 이상 / 재매칭 실행 시 |
| `regionAutocomplete` | `LegalDistrictCodeReloadedEvent` (전체 evict) | BAT-MAT-01(`LegalDistrictCodeLoader`) | 비정기(CSV 재적재 시에만) |

`regionAutocomplete`를 `TradeCacheEvictionEvent`(일 단위)에 묶으면 정적 데이터를 매일 무효화하게 돼 TTL을 길게 가져가려는 설계 의도가 깨집니다 — 실제로 COM-CACHE-01 1차 구현에서 이 실수가 있었고(`CacheEvictionListener`가 `TradeCacheEvictionEvent.legalDongCds()`로 `regionAutocomplete`까지 evict), 이후 `LegalDistrictCodeReloadedEvent`를 신설해 분리했습니다(검증: `CacheEvictionListener.java`, `LegalDistrictCodeLoader.java`).

**`@Cacheable` 메서드가 null을 반환할 수 있다면 반드시 `unless = "#result == null"`을 붙이세요.** `CacheConfig`는 의도적으로 `disableCachingNullValues()`를 켜지 않습니다 — 그 옵션은 "null은 캐싱을 조용히 건너뛴다"가 아니라 "null을 캐시에 저장하려는 시도 자체를 `IllegalArgumentException`으로 거부한다"로 동작해서(`AbstractValueAdaptingCache.toStoreValue()`), `unless` 없이 null을 정상 반환하는 `@Cacheable` 메서드가 그 순간 예외로 깨집니다(검증: 코드리뷰 PR #14, `CacheConfig.java`). null 캐싱을 피하고 싶으면 캐시 설정이 아니라 해당 `@Cacheable` 애노테이션에 `unless` 조건을 붙이세요.

**이벤트 클래스명이 설계서와 다릅니다:** 설계서 5.4절 원문은 `TradeLoadedEvent`로 되어 있지만, 실제 구현(BAT-LOD-01)은 `TradeCacheEvictionEvent`입니다(complexId·legalDongCd 집합을 담는 필드도 설계서에 없던 내용). 새로 코드를 짤 때는 설계서 원문이 아니라 이 이름을 따르세요 — 설계서 4.6/5.4절은 아직 업데이트되지 않았습니다.

**`CacheEvictionListener`의 두 리스너는 반드시 자기 몸통을 try-catch로 감싸 evict 실패를 흡수해야 합니다.** `TradeCacheEvictionEvent`는 `TradeDataLoader.loadBatch()`가 청크를 전부 커밋한 뒤에만 발행되는데, `@TransactionalEventListener`는 리스너를 발행자와 같은 스레드에서 동기 호출하므로 리스너가 던진 예외는 그대로 `publishEvent()` 호출자(`loadBatch()`)로 전파됩니다. 캐시 인프라 장애(예: Redis 다운)로 `cache.evict()`/`cache.clear()`가 예외를 던지면, `loadBatch()`가 이미 계산해 둔 실제 처리 건수(`LoadResult`)를 반환하지 못하고 그 예외가 대신 전파돼, `BatchExecutionOrchestrator`(BAT-SCH-01, `TradeIngestionPipeline` 경유)가 이미 DB에 커밋된 적재 건을 `batch_log`에 "0건 처리, 0건 에러"로 잘못 기록하는 문제가 있었습니다(Codex 코드리뷰 P2 지적) — 캐시가 stale해지는 부수 효과(최악의 경우 TTL 24h)가 이미 커밋된 적재 결과의 정확한 기록을 훼손할 수는 없다는 게 이 방어의 근거입니다. 새 리스너를 추가할 때도 캐시 백엔드 장애가 발행자 쪽 로직에 영향을 주지 않도록 이 패턴을 그대로 따르세요(검증: `CacheEvictionListenerTest.캐시_인프라_장애로_evict가_실패해도_예외를_전파하지_않는다`).

**[2026-10-06] BAT-NTF-01은 이 이벤트를 구독하지 않고 `orchestrate()`가 직접 호출한다(예외도 호출부에서 흡수) — "BAT-NTF-01 구현 결정 사항" D10. `TradeCollectionCompletedEvent`는 여전히 구독자가 없다. 아래는 당시 기록이다.** **전수 확인(2026-09-09) — 지금은 `@TransactionalEventListener`/`@EventListener`를 쓰는 곳이 `CacheEvictionListener` 하나뿐이다.** `grep -rn "@TransactionalEventListener\|@EventListener" src/main/java`로 확인했다. 다만 발행되는 이벤트는 3종(`TradeCacheEvictionEvent`, `LegalDistrictCodeReloadedEvent`, `TradeCollectionCompletedEvent`)인데 그중 `TradeCollectionCompletedEvent`(`BatchExecutionOrchestrator.orchestrate()`가 조합 순회 완료 시 발행)는 **아직 구독자가 없다** — BAT-NTF-01(관심대상 조건평가→알림 생성, 아직 미구현, "남은 백엔드 작업" 목록 참고)이 이 이벤트에 붙을 유력한 후보다. BAT-NTF-01을 구현할 때 그 리스너가 `@TransactionalEventListener(AFTER_COMMIT)`로 이 이벤트를 받는다면(발행 시점이 `orchestrate()`의 조합 순회 전체가 끝난 뒤라 이 페이즈가 자연스럽다), **이 리스너도 반드시 자기 몸통을 방어적으로 감싸야 한다** — 조건평가·알림 생성·이메일 발송 어느 단계든 예외가 새어나가면 그 예외가 발행자(`orchestrate()`)까지 전파되어, 이미 정상적으로 끝난 배치 순회 자체가 실패한 것처럼 보이거나(로그·모니터링 오염) 최악의 경우 `orchestrate()`의 남은 후처리(예: `TradeCollectionCompletedEvent` 발행 이후 로직이 더 있었다면 그 실행)를 막을 수 있다. 이 문서의 위 문단과 같은 근거로 처리하라.

### 날짜/시간 처리

- **날짜 관련 로직은 반드시 명시적으로 KST(`Asia/Seoul`) 기준으로 계산하세요.** JVM 기본 타임존이 UTC인 배포 환경(컨테이너 등)에서 `LocalDate.now()`/`YearMonth.now()`를 인자 없이 호출하면 서버 로컬 타임존을 따라가 버립니다 — 항상 `ZoneId.of("Asia/Seoul")`을 명시적으로 넘기세요. `spring.jackson.time-zone`은 JSON 직렬화에만 영향을 줄 뿐 이 계산에는 적용되지 않습니다. 공용 KST `Clock` 빈(`common/config/ClockConfig`)은 탈퇴 도메인(`WithdrawalPolicy`, BAT-USR-01)이 처음 도입했고, 나머지 클래스는 여전히 각자 자체 `KST` 상수를 선언합니다(`TradeCollectionScheduler`, `RegionStatsCalculator`, `FavoriteService`) — 새 클래스를 추가할 때는 이 패턴을 따르되, 테스트에서 시간을 고정해야 하는 새 로직이라면 그 `Clock` 빈을 주입해 쓰세요. 배치(BAT-)와 API 서버(SVC-)가 서로 다른 배포 환경에서 돌 수 있어, 이 원칙을 지키지 않으면 서버마다 다른 기준일로 계산되는 조용한 버그가 생깁니다.
- **"현재 시점까지"를 포함하려는 기간 range 쿼리는 상한을 오늘 날짜가 아니라 오늘+1일로 배타적(`<`) 상한을 잡으세요.** `dealDate < :to`처럼 배타적 상한 비교에 `LocalDate.now(KST)`를 그대로 넘기면 오늘 발생한 데이터가 항상 조회에서 빠집니다 — SVC-RGN-01/SVC-FAV-01의 `RegionStatsCalculator.calculate()`/`FavoriteService.calculatePropertyChangeRate()`(`TradeRepository.findAverageSaleAmount`/`findAveragePricePerPyeongForSale`/`countSaleTrades`/`findAverageSaleAmountByComplex`)에서 이 실수가 있었고 상한을 `now.plusDays(1)`로 고쳤습니다(코드리뷰 P2 지적). 같은 유형의 "현재 시점까지" range 쿼리를 새로 짤 때(SVC-CPX-01 최신순 대표거래 서브쿼리, SVC-STT-01(5단계)의 getTypeComparison/getPriceTrend, BAT-NTF-01의 신규 평균가 계산 등) 같은 실수가 재발하지 않도록 주의하세요.

## 데이터베이스 스키마

11개 테이블, 161개 컬럼, FK 17건, CHECK 10건, UNIQUE 8건. 전체 DDL은 테이블 정의서 8장 원문을 그대로 사용하세요 — 여기 요약만 보고 타이핑하지 말고 원문을 복사하는 걸 권장합니다.

### 테이블 목록

| 테이블 | 컬럼수 | 요약 |
| --- | --- | --- |
| `user` | 11 | 회원 (role: USER/ADMIN, status: ACTIVE/SUSPENDED/WITHDRAWN) |
| `refresh_token` | 7 | JWT Refresh Token (`rotated_yn` 추가, 2026-09-22) |
| `legal_district_code` | 7 | 법정동코드 마스터 |
| `complex` | 49 | 단지 마스터 (85개 원본 컬럼 중 41개 채택 + 시스템 컬럼 8개) |
| `trade` | 36 | 실거래 (housing_type: APT/VILLA만) |
| `favorite_property` | 6 | 관심 매물 (`complex_id` NOT NULL — 다형적 참조 없음) |
| `favorite_region` | 5 | 관심 지역 |
| `recent_view` | 7 | 최근 조회 이력 |
| `notification_setting` | 9 | 알림 조건 설정 |
| `notification` | 11 | 발송된 알림 |
| `batch_log` | 14 | 배치 실행 이력 (FK 없는 독립 테이블) |

### 설계 원칙 — 반드시 지킬 것
- **PK는 전부 서로게이트 키**(`BIGINT UNSIGNED AUTO_INCREMENT`)이며 `legal_district_code.legal_dong_cd`(자연키)만 예외.
- **모든 FK의 `ON UPDATE`는 `RESTRICT`**입니다(`CASCADE` 아님). PK가 불변이라 발생할 일이 없고, `CHECK` 제약과 `ON UPDATE CASCADE`가 MariaDB에서 함께 쓰이면 에러(1901)가 납니다.
- `ON DELETE`는 CASCADE(강한 소유 — 예: User→FavoriteProperty) 또는 SET NULL(느슨한 참조 — 예: Complex→Trade) 둘 중 하나. 새 FK를 추가할 때 이 기준으로 판단하세요.
- `trade.dedup_hash`는 **단일 컬럼 UNIQUE**입니다. 복합 UNIQUE로 바꾸지 마세요 — MariaDB는 복합 UNIQUE에서 NULL을 서로 다른 값으로 취급해 중복이 통과됩니다.
- **사용하지 않는 컬럼은 DB에 추가하지 않습니다.** 이 프로젝트의 확정된 설계 원칙입니다. "나중에 쓸 수도 있으니" 컬럼을 미리 만들지 마세요.
- **사용자 입력값을 그대로 저장하는 `DECIMAL(p,s)` 컬럼을 받는 요청 DTO에는 `@DecimalMin`/`@DecimalMax`(범위)뿐 아니라 `@Digits(integer=.., fraction=..)`(그 컬럼의 scale)도 함께 걸어야 합니다.** 범위만 검증하면 `0.04`처럼 컬럼 scale보다 소수 자릿수가 많은 값도 통과해 API는 200을 반환하지만, DB가 컬럼 scale에 맞춰 반올림해 저장한 값이 사용자가 요청한 것과 달라지는 조용한 정밀도 손실이 생깁니다(`UpdateNotificationSettingsRequest.priceChangeThresholdPct` → `DECIMAL(4,1)`, Codex 코드리뷰 P2 지적, 수정 시점 감사 결과 이 필드가 현재 유일한 사용자 입력 `DECIMAL` 컬럼이었습니다 — `Complex`/`Trade`의 나머지 `DECIMAL` 컬럼(`latitude`/`longitude`/`exclu_use_area`/`match_confidence`)은 전부 배치·지오코딩이 채우고 사용자 요청 DTO에 바인딩되지 않아 이 문제에서 자유롭습니다). CPX/TRD 검색 필터의 금액·면적 범위는 조회 조건일 뿐 저장되지 않아 이 문제와 무관합니다 — 새 DECIMAL 컬럼을 사용자 입력으로 추가할 때만 이 체크리스트를 적용하세요.

### CHECK 제약 / ENUM 값 (전체)

| 테이블 | 컬럼 | 허용 값 |
| --- | --- | --- |
| `user` | `role` | `USER`, `ADMIN` |
| `user` | `status` | `ACTIVE`, `SUSPENDED`, `WITHDRAWN` |
| `trade` | `housing_type` | `APT`, `VILLA` |
| `trade` | `deal_category` | `SALE`, `RENT` |
| `trade` | `rent_type` | `JEONSE`, `WOLSE`, 또는 NULL(SALE인 경우) |
| `trade` | `match_method` | `EXACT`, `SIMILAR` |
| `favorite_property` | `housing_type` | `APT`, `VILLA` |
| `notification` | `notification_type` | `PRICE_CHANGE`, `NEW_TRADE` |

다형적 참조 CHECK(대상 컬럼 중 정확히 하나만 채워야 함)는 2건뿐입니다: `recent_view`(user_id 또는 session_id 중 하나), `notification_setting`(favorite_property_id 또는 favorite_region_id 중 하나). `favorite_property`는 v1.0에 있던 다형적 참조가 폐기되어 이제 `complex_id`가 단순 필수 컬럼입니다.

### 원본 데이터 읽기 — 인코딩 주의
- 법정동코드 CSV, 단지 기본정보 xlsx **모두 CP949 인코딩**입니다. UTF-8로 열면 깨집니다.
- 법정동코드는 반드시 **문자열(`dtype=str`)**로 읽으세요 — 숫자로 읽으면 선행 0이 날아갑니다.
- 법정동코드는 `폐지여부='존재'`인 행만 사용합니다(약 49,861건 중 유효 약 20,555건).
- 단지 기본정보 xlsx는 `header=1`(0행은 제목행)입니다.
- 단지 매칭은 시도/시군구/동리 필터링 후 지번 정규식(`(?:^|\s)(산)?\s*([0-9]+(-[0-9]+)?)$`, 문자열 끝 앵커) 추출로 수행하며 목표 성공률은 98.9% 이상입니다. `complex.legal_dong_address`가 "시도 시군구 동리 지번" 형태의 전체 주소라 지번이 맨 끝 토큰으로 옵니다 — 시작 앵커(`^...`)로는 이 컬럼에서 전혀 매치되지 않아 EXACT 매칭이 무력화됩니다(검증: `ComplexMasterMatcher.java`, 커밋 `0191f33`). 설계서 4.5절 원문은 시작 앵커로 남아 있어 업데이트가 필요합니다.
- **`LegalDistrictCodeLoader`가 리(里) 단위 행의 `eupmyeondong_name`을 저장하는 방식 — 완결 필요(지성 확인 필요).** `resolveNameParts()`는 시도/시군구 대표행에만 특수 분기를 두고, 그 이하(읍면동만 있는 행이든 읍면동+리가 있는 행이든)는 전부 "시군구 대표행 이름을 뺀 나머지 전체"를 `eupmyeondong_name` 하나에 그대로 담는다 — 즉 "부산광역시 기장군 기장읍"은 `eupmyeondong_name="기장읍"`으로, "부산광역시 기장군 기장읍 동부리"는 읍/리를 분리하지 않고 `eupmyeondong_name="기장읍 동부리"`로 저장된다(검증: `LegalDistrictCodeLoaderTest`). 형제 리(동부리/서부리)끼리는 서로 다른 문자열로 저장되므로 SVC-RGN-01 자동완성에서 완전히 동일한 문구가 중복 노출되는 문제는 없다 — 다만 **국토교통부 실거래가 API의 `umdNm`이 리 지역에서 읍/면 이름만("기장읍") 주는지, 읍+리를 합친 값을 주는지 이 프로젝트가 아직 실제 API 응답으로 확인하지 못했다.** 전자라면 `LegalDistrictMatcher.matchByTradeSggCd()`(정확 일치만 매칭)가 이런 리 행의 `eupmyeondong_name`과 절대 일치하지 않아 그 행에는 거래가 영원히 매칭되지 않는다 — 법정동코드 CSV 기준 시도+시군구(438건)를 뺀 나머지 중 리 단위(4~5단계)가 약 77%를 차지해(코드리뷰에서 지적, 전체 분포: 시도 25/시군구 438/읍면동만 10,896/읍면동+리 37,450/시군구 하위구 포함 읍면동+리 1,052) 영향 범위가 작지 않다. 실제 배치 파이프라인을 리 지역 표본으로 한 번 돌려(또는 실 API 샘플 응답을 받아) `umdNm` 형식을 확인하기 전까지는 매칭 로직을 임의로 바꾸지 마라 — 잘못 짐작해 문자열을 쪼개면 반대 방향(읍/면 단위까지만 오는 umdNm을 리 단위로 잘못 쪼개 실패시키는) 회귀를 만들 수 있다. **[2026-10-06 메모] 2026-09-16 실 배치 데이터 분석("BAT-MAT-02 실 배치 재실행 추가 조사" 절 ②)에서 리 지역 `umdNm`이 "읍/면+리" 결합형으로 와 BAT-MAT-01 매칭이 성공한다는 것이 이미 확인됐다(버그 C는 BAT-MAT-02 쪽 문제였고 수정됨). 03:00 스케줄이 기본 활성이므로, 남은 확인(실 API 응답으로 리 지역 표본을 직접 보는 것)은 "배치 활성화 전"이 아니라 배포 전 확인 항목으로 본다.**

## API 엔드포인트 (도메인별 base path)

| 도메인 | Base Path | 대표 엔드포인트 |
| --- | --- | --- |
| 인증 | `/api/auth` | `POST /login`, `/signup`, `/refresh`, `/logout`, `GET /check-email` |
| 회원 | `/api/users` | `GET·PUT /me`, `DELETE /me` |
| 단지 | `/api/complexes` | `GET /search`(`regionCode`·`keyword` 중 하나 필수, `dealCategory`/`rentType`·범위 필터·`sort`·`page`/`size`), `/popular`, `/{id}`, `/map` |
| 실거래 | `/api/trades` | `GET /search`, `?complexId=`, `/{tradeId}` |
| 지역 | `/api/regions` | `GET ?query=`, `/interest-summary` |
| 최근조회 | `/api/recent-views` | `GET` |
| 관심 | `/api/favorites` | `GET·POST·DELETE /properties`, `/regions` |
| 알림 | `/api/notifications` | `GET·PUT /settings`, `GET`, `PATCH /{id}/read` |
| 검색(신규 제안) | `/api/search` | `GET /popular`, `POST /logs` |
| 통계(5단계) | `/api/stats` | `GET /type-comparison`, `/price-trend` |
| 관리자(5단계) | `/api/admin` | `GET /batch-logs`, `POST /batch-retry`, `GET·PATCH /users` |

`GET /api/trades`는 `?complexId=` 하나만 받습니다. `?officetelKey=`나 `?dong=` 파라미터는 만들지 마세요(향후 확장 대상).

## 배치 파이프라인

일 1회 이상 실행. 순서를 지키세요 — 뒷 단계는 앞 단계 산출물에 의존합니다.

```
스케줄러(조합 순회, 30 TPS 스로틀링)
  → Open API 수집기 (4종 데이터셋 공통 파라미터화)
  → 에러코드 판정 (CONTINUE/RETRY/ABORT_BATCH)
  → XML 파싱 + 통합 스키마 매핑
  → 법정동코드 매핑
  → 단지 마스터 매칭 (EXACT/SIMILAR 판정)
  → 적재 (dedup_hash upsert) + 캐시 무효화 이벤트 발행
  → 지오코딩 (MVP는 항상 PRECISE)
  → 관심대상 조건평가 → 알림 생성
  → 이메일 발송
```

### 대응 데이터셋 (4종 — 이게 전부입니다, 늘리지 마세요)

| 주택유형 | 거래유형 | data.go.kr ID |
| --- | --- | --- |
| 아파트 | 매매(기본) | 15126469 |
| 아파트 | 매매(상세) | 15126468 |
| 아파트 | 전월세 | 15126474 |
| 연립다세대 | 매매 | 15126467 |
| 연립다세대 | 전월세 | 15126473 |

### 에러코드 판정표

| 코드 | 의미 | 판정 |
| --- | --- | --- |
| 000 | 정상 | CONTINUE |
| 03 | 데이터 없음 | CONTINUE (빈 결과) |
| 01, 02, 04, 05 | 서비스 장애 | RETRY (지수 백오프) |
| 22 | 트래픽 초과 | RETRY |
| 10, 11, 12, 20, 32 | 설정/승인 문제 | ABORT_BATCH (해당 조합만 중단) |
| 30, 31 | 서비스키 오류/만료 | ABORT_BATCH (전체 배치 조기 중단 + 알림) |

**BAT-CLC-01 버그 발견·수정(2026-09-14) — resultCode 30/31이 HTTP 4xx로 오면 이 판정표가 아예 실행되지 않았다.**
법정동코드 재적재(legal_district_code 유실 복구) 직후 실제 운영 서비스키로 BAT-SCH-01을 수동 트리거했다가
발견됐다 — 발견 과정 자체가 근거라 남긴다. `batch_log`를 조회하니 APT+RENT(15126474)는 100% 성공(560건)인데
APT+SALE(15126469/15126468)은 단 한 건도 기록되지 않았고, 조합 순회가 끝난 13:45:35 이후 신규 로그가 20분
넘게 전혀 없었다 — `RetryQueueManager.processRetryQueue()`가 로그 없이 블로킹 백오프(1→5→30분)를 도는 중이었다
(`homesense.batch.retry.max-total-wait-minutes=180`이라 최악의 경우 3시간 가까이 이 상태로 보일 수 있다).
단일 SALE 조합만 직접 호출하는 일회성 진단 러너로 재현한 결과, 실제 원인은 두 겹이었다:

1. **계정 문제(코드와 무관)**: 이 서비스키가 아파트 매매 두 데이터셋에는 활용신청 승인이 안 돼 있다
   (data.go.kr은 같은 인증키라도 API 상품별로 별도 승인이 필요하다) — 실제 응답은 HTTP 403 +
   `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`(returnReasonCode 30). 전월세 데이터셋만 승인돼 있어 그쪽만
   전수 성공한 것과 정확히 일치한다. **지성이 data.go.kr 마이페이지에서 승인 상태를 확인해야
   한다 — 코드로 고칠 수 없는 부분.**
2. **코드 버그(수정 완료)**: `RealEstateApiCollector.requestPage()`가 `RestClient.retrieve().body()`를
   그대로 썼는데, 이건 HTTP 4xx/5xx면 `OpenApiXmlReader.readMeta()`(resultCode/returnReasonCode 추출)가
   실행되기도 전에 `HttpClientErrorException`을 던진다. 그 예외는 `BatchExecutionOrchestrator`의
   `RestClientException` catch절(일시적 전송 계층 실패로 간주)로 흘러들어가 **RETRY로 오분류**됐다 —
   위 판정표대로라면 30/31은 즉시 ABORT_BATCH여야 하는데, resultCode 기반 판정(`ApiErrorCodeClassifier`)
   자체가 이 경로에서 전혀 실행되지 못했다. `OpenApiXmlReader`는 이미 게이트웨이 오류 봉투
   (`OpenAPI_ServiceResponse/cmmMsgHeader/returnReasonCode`)를 정확히 폴백 처리하도록 짜여 있었고
   `RealEstateApiCollectorTest`에도 그 봉투를 판정하는 회귀 테스트(`게이트웨이_오류_봉투의_returnReasonCode도_ABORT_BATCH로_판정한다`)가
   이미 있었다 — **다만 그 테스트는 `withSuccess(...)`로 HTTP 200을 가정해 만들어져 있었다.** 실제
   data.go.kr은 이 오류를 200이 아니라 403으로 내려주므로, 그 테스트는 classifier 로직 자체는
   검증했지만 "이 오류가 실제로 어떤 HTTP 상태로 오는가"라는, 이번 버그의 진짜 원인이 된 부분은
   전혀 커버하지 못하고 있었다.

   **수정(최초, 이후 P1로 대체됨 — 아래 4번 참고)**: `OpenApiRestClientConfig.openApiRestClient()`에
   `defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {})`를 추가해 이 RestClient가 어떤
   HTTP 상태에서도 예외 없이 본문을 그대로 반환하게 했다 — 이 RestClient는 BAT-CLC-01 전용 단일
   소비자라 빈 레벨에서 한 곳만 고치면 된다. 이제 HTTP 상태와 무관하게 항상
   `OpenApiXmlReader`→`ApiErrorCodeClassifier`가 실행되어 판정표가 의도대로 작동한다.
   `RealEstateApiCollectorTest.newCollector()`도 같은 핸들러를 달아 프로덕션 RestClient 구성과
   어긋나지 않게 맞추고, `withStatus(HttpStatus.FORBIDDEN)`로 실제 403 상태를 재현하는 회귀
   테스트(`게이트웨이_오류가_HTTP_403_상태로_와도_ABORT_BATCH로_판정한다`)를 추가했다 — 기존 200
   가정 테스트는 그대로 남겨 두 시나리오(200 봉투, 403 봉투) 모두 커버한다.
3. **잔여 리스크**: 이번에 확인된 건 30(서비스키 미등록)이 403으로 오는 사례뿐이다. 01/02/04/05/22
   (RETRY 대상)나 10/11/12/20/32(ABORT_COMBINATION)도 실제 data.go.kr이 2xx가 아닌 상태로 내려줄 수
   있는지는 아직 실 API로 확인하지 못했다 — 아래 4번의 판정 로직(게이트웨이 봉투 여부로 분기)이 그
   경우도 함께 커버하지만, 각 코드가 실제로 어떤 HTTP 상태를 동반하는지는 다음에 그런 응답을 실제로
   관측하면 여기 추가할 것.
4. **후속 수정(P1, 같은 날) — 2번의 `defaultStatusHandler` no-op 자체가 새로운 버그였다.** 모든 HTTP
   오류 상태에서 예외 없이 본문을 통과시키면, 게이트웨이 봉투가 아닌 진짜 일시적 전송 계층
   실패(429/5xx가 프록시·로드밸런서의 일반 오류 페이지로 오는 경우 등, 본문에
   `resultCode`/`returnReasonCode`가 전혀 없는 경우)까지 통과된다 — 이 경우 `OpenApiXmlReader`가
   봉투를 못 찾아 `OpenApiResponseException`을 던지고, `BatchExecutionOrchestrator`는 이를 재시도
   큐에 넣지 않는 구조적 실패로 기록한다. 원래 `RestClientException`으로 전파돼 재시도 큐를 탔어야
   할 오류가 조용히 유실되는 정반대 방향의 회귀였다(코드리뷰 P1 지적 — 2번 수정이 "판정표가
   실행되게 한다"는 목적은 달성했지만 "그 대가로 무엇을 잃는지"를 검증하지 않은 채 나갔다는 뜻).

   **결정**: OpenAPI 응답의 HTTP 에러 여부와 "분류 가능한 게이트웨이 봉투(`resultCode`/
   `returnReasonCode` 존재)" 여부는 별개로 판정한다. 봉투가 있으면 HTTP 상태와 무관하게
   `ApiErrorCodeClassifier`(BAT-ERR-01)에 위임하고, 봉투가 없는 순수 HTTP 4xx/5xx는
   `RestClientException`으로 전파해 기존 재시도 경로를 태운다. 이 판정은 `OpenApiRestClientConfig`
   (빈 레벨)가 아니라 `RealEstateApiCollector.requestPage()`(유일한 호출부)가 `exchange()`로 응답
   본문을 정확히 한 번만 읽어 수행한다 — `defaultStatusHandler`는 완전히 제거했다.

   **근거**: 빈 레벨 no-op 핸들러는 분류 불가능한 진짜 일시적 장애까지 삼켜 위 버그를 낳았다.
   판정 로직을 유일한 소비자(`RealEstateApiCollector`)로 옮기면 부수적으로 다른 문제도 풀린다 —
   "본문을 먼저 들여다본 뒤 통과 여부를 정하고, 통과라면 그 본문을 다시 읽어 반환"하는 2단계
   설계(처음엔 `BufferingClientHttpRequestFactory`로 시도)는 실제 Apache HttpClient에선 동작해도
   `RealEstateApiCollectorTest`의 `MockRestServiceServer`가 반환하는 응답 스트림은 재읽기가 안 돼
   (두 번째 읽기가 빈 문자열을 반환해 `게이트웨이_오류가_HTTP_403_상태로_와도_ABORT_BATCH로_판정한다`
   테스트가 깨졌다) 프로덕션과 테스트 구성이 갈라질 뻔했다. `exchange()`로 본문을 한 번만 읽고 그
   자리에서 바로 반환하거나 예외를 던지는 지금 구조는 이 문제 자체가 성립하지 않는다.

   **무효화 조건**: `OpenApiRestClientConfig`의 `openApiRestClient` 빈을 `RealEstateApiCollector`
   이외의 다른 클라이언트가 재사용하게 되는 시점 — 그 빈은 이제 상태 코드에 대해 아무 판단도 하지
   않는 순수 RestClient이므로(기본 동작대로 4xx/5xx에서 그대로 예외를 던진다), 새 소비자가 이번
   결정과 같은 종류의 "HTTP 에러 여부와 분류 가능한 오류 봉투 여부를 분리해서 봐야 하는" 요구를
   갖는다면 그 소비자에도 이 `exchange()` 패턴을 각자 적용해야 한다 — `RealEstateApiCollector` 안의
   로직을 억지로 공유하지 말 것(이 로직은 data.go.kr 응답 봉투 형식에 특화돼 있다).

   **검증**: `게이트웨이_봉투가_아닌_HTTP_503은_구조적_실패가_아니라_RestClientException으로_전파된다`
   (신규, `HttpServerErrorException` 경로)를 추가했다. `HttpClientErrorException` 경로(429 등 4xx
   전송 계층 실패)도 별도 회귀 테스트로 추가해 확인했다 — 두 예외 클래스가 갈리는 분기점이라 코드
   리딩만으로는 충분히 확신할 수 없었다.

### 파이프라인 오케스트레이션 (`TradeIngestionPipeline`)

XML 파싱(BAT-PRS-01)→법정동/단지 매칭(BAT-MAT-01/02)→적재(BAT-LOD-01)를 실제로 체이닝하는 코드는 각 컴포넌트가 완성된 뒤에도 한동안 없었다 — `BatchExecutionOrchestrator`는 `collector.collect()`만 호출하고 페이지 수를 `batch_log`에 기록할 뿐이었다. `batch.loader.TradeIngestionPipeline`(public, `TradeDataLoader`와 같은 레벨)이 이 연결을 담당한다: `BatchExecutionOrchestrator.logSuccess()`가 `ApiResponseXml`의 페이지를 `datasetId`별로 묶어 `TradeIngestionPipeline.process(housingType, dealCategory, datasetId, pageBodies)`를 데이터셋마다 호출하고, 반환된 `LoadResult`를 그대로 `batch_log`의 `processed_count`/`error_count`에 기록한다(이전엔 이 두 컬럼에 페이지 수를 대신 넣는 임시값이었다).

- **데이터셋 단위로 나눠 호출하는 이유(합쳐서 한 번에 부르지 않는 이유):** APT+SALE은 기본(15126469)·상세(15126468) 두 데이터셋이 한 조합으로 묶여 들어온다. `DedupHashCalculator`가 `datasetId`를 해시에 넣지 않으므로 같은 거래가 두 데이터셋에 나타나도 같은 `dedup_hash`로 수렴하고 `TradeChunkLoader.upsertOne()`이 이미 "먼저 들어온 건 INSERT, 나중 건 UPDATE"를 보장한다 — 나눠서 두 번 `loadBatch()`를 불러도 합쳐서 한 번 부르는 것과 최종 결과가 같다. 대신 나누면 `batch_log`가 이미 데이터셋 단위 행(`dataset_id` 컬럼)이라 데이터셋마다 정확한 카운트를 그대로 기록할 수 있다.
- **`TradeFieldMapper.supports(housingType, dealCategory)` 게이트 — 아파트 전월세(APT/RENT)는 2026-09-15에 확정, 연립다세대(VILLA)는 매매·전월세 둘 다 2단계로 미룸.** `TradeIngestionPipeline`은 이 게이트가 `false`면 파싱 자체를 시도하지 않고 info 로그만 남기고 빈 `LoadResult`를 반환한다 — 수집(BAT-CLC-01)·`batch_log` 기록은 그대로 되지만 적재는 조용히 건너뛴다. `application.properties`의 `housing-types=APT` 설정과 무관하게 `BatchExecutionOrchestrator`는 `DealCategory.values()`(SALE+RENT)를 조건 없이 순회하므로 APT+RENT 조합은 매일 수집되고 있었는데, `supports()`가 APT+SALE에만 `true`를 반환해(RENT 필드 매핑 자체가 없었음) 전량 스킵되고 있었다 — `SELECT COUNT(*) FROM trade WHERE deal_category='RENT'`가 0건으로 실측 확인됨. **이 근본 원인은 "잘못된 필드명으로 `MalformedTradeItemException`이 나서 스킵된다"가 아니라 애초에 매핑을 시도조차 하지 않는 명시적 게이트였다는 점에 유의하라** — 사용자가 "가설 A"로 짐작한 결과(0건)는 맞았지만 메커니즘은 달랐다.

  data.go.kr 공식 Swagger 스펙(`https://www.data.go.kr/data/{15126474,15126473}/openapi.do`에 임베딩된 `swaggerJson`을 직접 fetch, 제3자 블로그·라이브러리로 교차 확인도 완료)으로 두 RENT 데이터셋의 실제 필드명을 모두 확정했다 — **다만 실제로 구현·활성화한 건 아파트(15126474)뿐이다:**

  | 데이터셋 | 필드 | 비고 |
  | --- | --- | --- |
  | 아파트 전월세(15126474, `RTMSDataSvcAptRent`) — **구현·활성화 완료** | `sggCd`·`umdNm`·`aptNm`·`jibun`·`excluUseAr`·`dealYear`·`dealMonth`·`dealDay`·`deposit`(보증금액, 만원)·`monthlyRent`(월세금액, 만원)·`floor`·`buildYear`·`contractTerm`·`contractType`·`useRRRight`·`preDeposit`·`preMonthlyRent` | `aptDong`/`dealingGbn`/`estateAgentSggNm`/`rgstDate`/`slerGbn`/`buyerGbn`/`landLeaseholdGbn`/`cdealType`/`cdealDay`는 공식 스펙에 **없음**(SALE 전용 개념) |
  | 연립다세대 전월세(15126473, `RTMSDataSvcRHRent`) — **필드명만 확정, 구현은 2단계로 보류** | 위와 동일 구조 | 단지명 필드가 `aptNm`이 아니라 **`mhouseNm`**(연립다세대명) — 2단계 착수 시 이 값을 바로 가져다 쓰면 된다 |

  JEONSE/WOLSE 판정은 `monthlyRent`가 "0"이면 JEONSE, 0보다 크면 WOLSE다 — 공식 스펙 description에는 명시되지 않지만 이 데이터셋을 다루는 독립된 두 소스(라이브러리 소스코드, 실사용 블로그)가 동일하게 기술하는 업계 표준 관행이다. `contractTerm`/`contractType`/`useRRRight`/`preDeposit`/`preMonthlyRent`는 `TradeDraft`/`trade`에 대응 컬럼이 없어 매핑하지 않는다("사용하지 않는 컬럼은 추가하지 않는다" 원칙, FR-3.3은 `monthlyRentAmount` 표시만 요구).

  **VILLA/RENT를 함께 열지 않은 이유(코드리뷰 지적으로 축소, 최초엔 VILLA/RENT까지 같이 구현했었다) — 요구사항정의서 9장 2단계 로드맵과의 충돌.** 이 문서의 "개발 단계" 절은 2단계(연립다세대 확장)를 "신규 프로그램 0개, 1단계 프로그램의 housingType 파라미터 범위를 APT에서 APT,VILLA로 넓히기만 하면 된다"고 명시한다 — SALE/RENT를 나눠 순차 활성화하는 설계가 아니라 **VILLA 전체(SALE+RENT)를 한 시점에 함께 연다는 전제**다. RENT 필드명이 먼저 확정됐다고 VILLA/RENT만 먼저 열면 이 전제가 깨져, 정작 2단계를 시작할 때 "VILLA/RENT는 이미 되는데 왜 VILLA/SALE만 막혀 있지?" 하는 혼란을 남긴다. 그래서 `supports()`는 `dealCategory==RENT`여도 `housingType==APT`일 때만 `true`이고, VILLA는 매매·전월세 모두 여전히 `UnsupportedOperationException`을 던진다. `TradeFieldMapper.mapRentToUnifiedModel()`은 이제 housingType 분기 없이 `aptNm`만 읽는다(어차피 `supports()`가 APT만 통과시켜 이 메서드는 항상 housingType=APT로만 호출된다) — VILLA 분기를 미리 심어두는 대신, 2단계에서 VILLA/SALE 필드명을 마저 확정할 때 이 표의 `mhouseNm`을 그대로 가져다 함께 추가하면 된다.

  **"대응 데이터셋" 표(위 배치 파이프라인 절) 표기 불일치 — 참고용 전체 목록이지 1단계 구현 범위가 아님.** 그 표의 헤더는 "4종"인데 실제 행은 5개(연립다세대 전월세 15126473 포함)라 사소한 문서 내 불일치가 있다 — 이 표는 국토부 API 4종 데이터셋(아파트 매매 기본/상세는 1조합으로 묶여 행이 5개가 된다) 전체를 참고용으로 나열한 것이지, "연립다세대 전월세도 1단계 범위"라는 뜻이 아니다. 실제 1단계 구현 범위는 이 절(`TradeFieldMapper.supports()`)이 최종 권위다.

  **검증:** `TradeFieldMapperTest`(Mockito)에 APT+RENT의 JEONSE/WOLSE 케이스, 보증금 누락 시 예외 케이스, VILLA+RENT가 VILLA+SALE과 마찬가지로 여전히 예외를 던지는 회귀 테스트를 추가했다. 실제 배치 재실행(Docker + 국토부 API 실 호출)으로 `trade.deal_category='RENT'` 행이 실제로 적재되는지는 이번 세션 범위 밖이라 확인하지 못했다 — 다음에 배치를 재실행하면 `processed_count > 0`과 실제 RENT 행 적재를 재확인하라.
- **파싱/매핑 에러의 단위:** 페이지 하나가 통째로 파싱 실패(`TradeXmlParsingException`)하면 그 페이지만 스킵하고 나머지 페이지는 계속 처리한다. 항목 하나가 매핑 실패(`MalformedTradeItemException`)해도 그 항목만 스킵한다(`TradeChunkLoader.loadChunk()`가 개별 draft 예외를 스킵하는 것과 같은 결). 둘 다 `LoadResult`의 `errorCount`에 합산된다.
- **법정동/단지 매칭 실패는 에러가 아니다:** FR-2.5 목표 성공률(98.9% 이상)이 이미 100% 미만을 전제하므로, 매칭 실패 draft도 그대로 로더에 넘어가 `complex_id`/`legal_dong_cd`가 `NULL`인 채로 적재된다(`TradeChunkLoader`의 참조 헬퍼가 이미 null-safe).
- **`LegalDistrictMatcher.matchByTradeSggCd()` 반환 타입이 설계서와 다르다** — 아래 "BAT-MAT-01/BAT-MAT-02 구현 결정 사항" 표 참고.
- **`TradeIngestionPipeline.process()` 호출이 예상 못한 런타임 예외를 던지면** `BatchExecutionOrchestrator`가 그 데이터셋만 실패로 기록(`successYn=false`)하고 다음 데이터셋·조합은 계속 진행한다 — `processCombination()`의 기존 catch 목록(`OpenApiResultCodeException` 등)은 API 호출 실패만 다루므로, 이 안전망이 없으면 예상 못한 예외가 `orchestrate()`의 조합 순회 전체를 멈춰버린다.

### BAT-MAT-01/BAT-MAT-02 구현 결정 사항

두 항목 모두 이번 `TradeIngestionPipeline` 작업 이전부터 코드에 있었다 — `ComplexMasterMatcher`의 2인자 시그니처는 원래 구현 커밋(`0ddf0e4`, "단지·건물 마스터 매칭기 개발")부터 지금 형태였고, 이 작업 전까지 두 매처를 실제로 체이닝하는 호출부가 없어서(오케스트레이션 공백) 이 이탈이 드러나지 않았을 뿐이다. 두 항목 다 근본 원인이 같아 하나로 묶는다.

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| `LegalDistrictMatcher.matchByTradeSggCd()` 반환 타입 | 설계서 4.4절: `Optional<String> matchByTradeSggCd(String sggCdFromApi, String umdNm)` — `trade.legal_dong_cd`가 `CHAR(10)` 컬럼이라 문자열 반환과 맞아떨어짐 | `Optional<LegalDistrictCode>`(엔티티) | BAT-MAT-02(`ComplexMasterMatcher`)의 1차 필터링(`findBySidoAndSigunguAndDongRi`)이 요구하는 시도/시군구/동리 이름은 `trade`나 `TradeDraft`가 아니라 `legal_district_code.sido_name`/`sigungu_name`/`eupmyeondong_name`에서만 나온다. 코드 문자열만 반환하면 이 이름들을 얻으려 `legal_district_code`를 `legalDongCd`로 다시 조회하는 왕복이 한 번 더 필요해진다 — 엔티티를 그대로 넘기면 그 왕복이 사라진다. |
| `ComplexMasterMatcher.matchComplex()` 파라미터 | 설계서 4.5절: `MatchResult matchComplex(TradeDraft draft)` 단일 인자 — `draft.legalDongCd()`가 이미 채워져 있다고 가정하는 시그니처로 읽힌다 | `MatchResult matchComplex(TradeDraft draft, LegalDistrictCode legalDistrictCode)` 2인자 | 단일 인자로는 위 문제가 메서드 내부로 옮겨질 뿐 해결되지 않는다 — `draft.legalDongCd()`는 BAT-MAT-01이 채우기 전까지 `null`이라 이 메서드가 스스로 `LegalDistrictCode`를 조회해야 하는데, 그러면 BAT-MAT-01(`LegalDistrictMatcher`)의 조회 로직(활성 코드 필터링 등)을 BAT-MAT-02 안에 다시 구현하거나 `LegalDistrictMatcher`를 이 메서드가 직접 의존하는 형태가 된다. 대신 호출자(`TradeIngestionPipeline`)가 BAT-MAT-01 결과를 그대로 전달하는 2인자 형태를 택해 두 매처의 책임을 분리했다 — `legalDistrictCode`가 `null`이면(법정동 매칭 자체가 실패) "법정동코드 매핑 실패로 후보 지역을 특정할 수 없음" 사유로 로깅까지 마친 `unmatched()`를 곧바로 반환한다(`ComplexMasterMatcher.java`). |

**주의 — BAT-MAT-02의 다른 판단들도 같은 식으로 미문서화됐을 수 있다.** 위 2인자 시그니처가 원래 구현 커밋(`0ddf0e4`)부터 있었는데도 근거가 커밋 메시지에도 CLAUDE.md에도 전혀 남아있지 않았다는 건, 그 커밋 시점에 내려진 다른 판단(지번 정규식의 "산" 접두 처리, 트라이그램 유사도 임계치 0.500/신뢰도 매핑 구간 0.600~0.850의 근거 등 `ComplexMasterMatcher.java` 안의 매직넘버들)도 같은 이유로 누락됐을 가능성이 있다는 뜻이다. 지금 전수 감사할 필요는 없지만, 다음에 `ComplexMasterMatcher`/BAT-MAT-02를 다시 열어볼 일이 생기면 — 특히 그 매직넘버들을 건드릴 때 — 커밋 로그(`git log -- .../ComplexMasterMatcher.java`)에 근거가 남아있는지 먼저 확인하라. 없다면 "왜 이 값인지" 자체가 유실된 상태라는 뜻이므로, 바꾸기 전에 지성에게 확인이 필요하다.

### BAT-MAT-02 버그 수정 (2026-09-15) — 실 DB 데이터로 확정한 1차 필터링·지번 매칭 결함 2건

**버그 A — `complex.sigungu` ↔ `legal_district_code.sigungu_name` 표기 불일치로 "시+구" 구조 도시의 1차 필터링 후보가 항상 0건이었다.** `ComplexMasterMatcher.matchComplex()`가 `complexRepository.findBySidoAndSigunguAndDongRi()`에 `legalDistrictCode.getSigunguName()`을 가공 없이 그대로 넘겼는데, 실제 로컬 DB(`complex` 21,680건/`legal_district_code` 20,555건)를 직접 조회해 보니 두 원천의 표기가 "시+구" 구조 도시(구가 설치된 시)에서 체계적으로 다르다 — K-apt 단지 기본정보 xlsx 유래 `complex.sigungu`는 "수원장안구"(공백 없음, "시" 생략)인데 행정안전부 법정동코드 CSV 유래 `legal_district_code.sigungu_name`은 "수원시 장안구"(공백 있음, "시" 유지)다. 수원·성남·안양·부천·안산·고양·용인·청주·천안·창원·전주·포항 등 구가 설치된 모든 시에서 동일 패턴으로 재현되고, 반대로 구가 없는 단일 시/군(목포시 등)과 광역시 소속 구(종로구 등)는 원래도 표기가 같아 문제가 없었다.

**수정:** `ComplexMasterMatcher.normalizeSigungu(String raw)` 신설 — 공백 제거 후 문자열 끝이 아닌 위치의 "시"만 제거한다("목포시"처럼 "시"가 마지막 글자면 보존). `legal_district_code.sigungu_name`에만 SQL 파라미터 바인딩 직전(애플리케이션 레벨)에 적용하고 `complex.sigungu`(xlsx 원본)는 절대 건드리지 않는다 — 두 원천 모두 정부 원본 표기를 DB에 그대로 보존해야 하므로 정규화는 비교 시점에만 수행한다. `idx_complex_region(sido, sigungu, dong_ri)` 인덱스는 정규화가 파라미터 바인딩 이전에 일어나므로 그대로 탄다. 세종특별자치시(구 자체 없음, 양쪽 다 `sigungu(_name)=NULL`)는 애초에 문제가 아니었다 — Spring Data 파생 쿼리가 null 파라미터를 자동으로 `IS NULL`로 바인딩해 이미 정상 매칭된다.

**근거(무효화 조건):** 정규화 규칙은 `complex.sigungu`(distinct 253건)와 `legal_district_code.sigungu_name`(distinct 264건) 전체를 정규화해 교차 검증했다 — 사용자가 예시로 든 수원·성남·청주·천안·창원·안양·부천·안산·고양·용인·전주·포항은 이 규칙 하나로 완전히 해소된다. **K-apt xlsx의 시군구 표기 규칙(공백 없음·"시" 생략)이 향후 데이터 갱신 시 바뀌면 이 규칙도 재검증이 필요하다** — 예를 들어 xlsx가 어느 시점부터 "수원시 장안구"처럼 공백을 포함해 표기하기 시작하면 이 정규화가 오히려 불일치를 만든다.

**잔여 불일치 2,399개 단지(전체의 ~11%) — 표기 문제가 아니라 데이터 시점 차이, 이번 범위 밖으로 확정(지성 승인):**

| 그룹 | 단지 수 | 원인 |
| --- | --- | --- |
| `전남광주통합특별시` | 1,615 | `complex.sido` 자체가 광주+전남을 합친 통합 표기(xlsx가 최신 행정구역 반영). `legal_district_code`는 아직 `광주광역시`/`전라남도`로 분리된 구버전 — **sido 레벨부터 불일치**해 sigungu 정규화로는 해결 불가 |
| 화성시 신설 일반구(동탄/병점/효행/만세구) | 422 | `legal_district_code`에 이 구들이 아예 없고 `화성시`만 있음(신설구가 별도 법정동코드를 받았는지 미확인) |
| 인천 신설 자치구(검단/서해/영종/제물포구) | 362 | `legal_district_code`가 구 개편 이전(서구/동구/중구 등) 상태 |

세 그룹 모두 xlsx(2026-08 스냅샷, 최신 행정구역 반영)와 legal_district_code CSV(구버전) 사이의 데이터 시점 차이다. 실제 수정(법정동코드 CSV 재적재, 또는 sido/sigungu 별도 매핑 테이블)은 후속 과제로 남긴다 — 광주-전남 sido 매핑은 검증 안 된 최근(2026년) 행정 개편을 전제하고, 화성/인천 신설구가 실제로 별도 법정동코드를 받았는지도 확인되지 않아 지금 하드코딩하면 회귀 위험이 있다고 판단했다(지성 확인 후 범위 제외 승인).

**버그 B — `legal_dong_address`가 실제로는 "...동리 지번 단지명" 형태라 지번 뒤에 단지명이 이어 붙는데, 예전 정규식이 문자열 끝($) anchor라 EXACT가 전국 0건이었다.** 클래스 javadoc과 이 문서는 이 컬럼이 "시도 시군구 동리 지번"으로 끝난다고 가정했지만, 실 DB(예: complex_id=19 "종로청계힐스테이트", `legal_dong_address`="서울특별시 종로구 숭인동 766 종로청계힐스테이트")를 조회해 보니 지번 뒤에 단지명이 그대로 붙어 있었다 — 대응 실거래(jibun="766")가 지번 완전일치임에도 `$` anchor가 절대 매치되지 않아 SIMILAR(0.850)로 오분류됐다. **기존 `ComplexMasterMatcherTest`의 모든 fixture가 단지명 없이 지번으로 끝나는 비현실적 주소("서울특별시 강남구 역삼동 123-4")를 썼던 게 이 버그가 발견되지 않은 이유다** — "legal_dong_address가 전체 주소여도 EXACT로 매칭한다"는 이름의 회귀 테스트조차 실제 데이터 형태를 반영하지 않아 이 버그를 전혀 잡지 못했다.

**수정:** `Complex.dongRi`(이미 엔티티에 존재)로 주소 안에서 동리 텍스트가 끝나는 위치를 먼저 찾고, 그 바로 뒤에서 지번 토큰을 시작 앵커(`Matcher.lookingAt()`)로 추출하는 `extractJibunFromAddress(Complex)`로 교체했다 — 단지명이 무엇으로 시작하든 영향받지 않는다. `draft.jibun()`(API 원본의 단일 토큰)은 `normalizeStandaloneJibun(String)`으로 전체 일치(`matches()`)를 그대로 검사한다. 콤마로 여러 지번이 나열된 주소(예: 필지 두 곳에 걸친 단지, complex_id=13139)는 시작 앵커 특성상 첫 번째 지번만 추출되는데, 매칭 실패로 치지 않고 그 지번으로 EXACT를 시도하는 관대한 폴백으로 남겨뒀다(SVC-CPX-01/TRD-01의 기존 "조회 API의 관대한 폴백" 철학과 일치).

**부차 발견(수정 대상 아님, 알려진 한계로 기록) — trade.jibun의 블록-로트 표기(162건)는 매칭 대상 자체가 못 된다.** `trade.jibun` 35,177건 중 162건(0.46%)이 `"가-238"`, `"BL-91-2"` 같은 블록-로트 표기(신도시 택지지구)다. `complex.legal_dong_address`에는 이런 표기가 전혀 없어(표본 확인) 지번 매칭이 원천적으로 불가능하다 — 버그가 아니라 알려진 한계다. "산" 접두 지번(36건)은 기존 로직이 이미 정상 처리하고 있었다(재확인 완료, 수정 불필요).

**검증 1(Mockito) — 최초 검증.** `ComplexMasterMatcherTest`(14건 — 기존 8건 fixture를 실제 주소 형태로 교정 + 신규 6건: 실제 사례(종로청계힐스테이트) EXACT 재현, 콤마 다중지번 폴백, 블록-로트 비매칭, 시+구/단일시군/광역시구 정규화 3종). 코드리뷰 지적: 수정 원인 자체가 "실제 데이터 형태가 fixture와 다르다"였는데, 검증이 손으로 고른 사례 1건 + 그걸 본뜬 fixture로만 이뤄져 "Mockito로는 영속성 컨텍스트/실 데이터 버그를 못 잡는다"는 이 프로젝트 스스로의 원칙(UNIQUE 제약 동시성 절 등)과 같은 함정에 빠질 위험이 있었다.

**검증 2(실 DB 전수 검증, 2026-09-15 추가) — `complex` 21,680건 전체에 수정된 `extractJibunFromAddress()` 로직을 그대로 재현해 돌렸다.** 배치 재실행이나 API 호출 없이 기존 테이블 데이터(`complex_id`/`dong_ri`/`legal_dong_address`)만으로 가능한 검증이라 비용이 거의 들지 않았다 — Python으로 Java 정규식·로직을 그대로 옮겨 21,680행 전체에 적용했다(스크래치 스크립트, 저장소에 커밋되지 않음).

- **complex 마스터 지번 추출 성공률(정규식 로직 자체의 건전성 확인용): 98.953%(21,453/21,680).** **이 수치를 "FR-2.5 목표 달성"으로 읽으면 안 된다** — FR-2.5가 정의하는 목표치(98.9% 이상)는 스펙 원문상 "배치 완료 후 `batch_log.processed_count` 대비 실제 trade가 `complex_id`를 얻는 비율"이라 재는 대상 자체가 다르다. 이번 검증은 complex 21,680건이라는 고정 데이터셋 안에서 `extractJibunFromAddress()`(추출 축)가 주소 문자열에서 지번을 뽑아낼 수 있는지만 확인한 것이고, `trade.jibun`(실 API 응답값)과의 실제 비교(비교 축)는 반영하지 않는다 — 비교 축은 여전히 `ComplexMasterMatcherTest`의 fixture 6건 수준으로만 검증된 상태다(위 "부차 발견"의 블록-로트 표기 162건처럼 trade 쪽에만 있는 포맷 이슈는 이 수치에 전혀 잡히지 않는다). 98.953%와 98.9%가 근접한 건 우연이지 증명이 아니다 — **FR-2.5의 실제 매칭 성공률(거래 데이터 기준)은 배치를 재실행해 DB에 반영된 결과로 별도 확인해야 확정된다.**
- **실패 227건(1.05%) 원인 재확인 — 애초 "블록-로트 표기 때문"이라던 추정이 틀렸다.** 실 데이터를 까보니 블록-로트 표기는 `complex.legal_dong_address`에 단 한 건도 없었고, 실패는 전부 **complex 마스터 원본 데이터 자체의 결측**이었다:
  - **218건**: `legal_dong_address`에 지번 숫자 자체가 없음 — 동리 뒤가 공백 두 칸 후 바로 단지명/인근 역명으로 이어진다(예: `"...망우동  신내역 힐데스하임아파트"`). xlsx 원본에 지번이 미기재된 것으로 보인다.
  - **9건**: `dong_ri` 자체가 NULL — 대부분 필지 여러 곳에 걸친 콤마 구분 다중주소(위 "동리 뒤에 여러 지번이 콤마로 나열" 사례와 같은 패턴)라 외부 xlsx 적재 과정이 단일 `dong_ri` 값을 못 뽑은 것으로 보인다. **세종특별자치시(216건, `sigungu=NULL`)와는 무관하다** — 세종 complex는 `dong_ri`가 정상적으로 채워져 있다(처음엔 두 NULL을 같은 원인으로 착각했었다, 코드리뷰에서 정정됨).
  
  두 유형 모두 `extractJibunFromAddress()` 로직의 결함이 아니라 complex 마스터 원본의 결측이라 EXACT가 원천적으로 불가능하고 SIMILAR/미매칭으로 정상적으로 떨어진다 — 버그가 아니라 알려진 한계로 남긴다.

실제 배치 재실행(Docker + 국토부 API 실 호출)으로 `trade.match_method='EXACT'`가 실제로 0건에서 벗어나는지는 이번 세션 범위 밖이라 못 했다 — 다음에 배치를 재실행하면 이것과, "시+구" 도시(수원/성남/청주 등)의 `complex_id` 매칭률이 오르는지 재확인하라.

**남은 절차:** 이 두 시그니처는 이제 실제로 서로를 호출하며 검증됐으니(`TradeIngestionPipelineTest`), 프로그램 설계서 4.4/4.5절을 이 표대로 갱신하는 것이 다음 문서 동기화 시점의 할 일이다 — 지금은 CLAUDE.md에 근거를 남기는 것으로 갈음한다.

### BAT-MAT-02 실 배치 재실행 추가 조사 (2026-09-16) — stale 매칭 통계, 신규 버그 C, lawd_cd 커버리지 공백

위 버그 A/B 수정 후 "`trade.match_method='EXACT'`가 실제로 0건에서 벗어나는지" 재확인하는 과정에서 시작된 조사다. 로컬 DB(포트 3307, SALE 전체 51,957건)를 직접 조회해 세 가지를 확인했다 — 배치 재실행이나 API 호출 없이 기존 테이블 데이터만으로 가능한 검증이었다.

**① `TradeRepository.upsert()`가 매칭 필드를 갱신하지 않아, 버그 A/B 수정 전 stale row가 집계를 오염시킨다(설계는 의도된 것이었지만, 그 하류 효과는 이번에 처음 실측했다).** `created_at`(수집 시각) 기준으로 SALE 트레이드를 쪼개보면:

| 수집 시각 | EXACT | SIMILAR | 미매칭 | 미매칭률 |
| --- | --- | --- | --- | --- |
| 09-14 14~15시(버그 A/B 수정 **전** 최초 수집, 35,177건) | 0 | 13,947 | 21,230 | 60.7% |
| 09-15 18~19시(버그 A/B 수정 **후** 재수집, 16,780건) | 14,861 | 969 | 950 | **5.7%** |

버그 A/B 수정으로 미매칭률이 60.7%→5.7%까지 실제로 떨어졌다는 뜻이지만, `upsert()`의 `ON DUPLICATE KEY UPDATE`가 `cancel_yn`/`cancel_date`/`registration_date`/`apt_dong`만 갱신하고 `complex_id`/`match_method`는 최초 적재 시점 값을 그대로 유지하도록 이미 설계돼 있어(`TradeRepository.java` javadoc, "재매칭은 이 upsert의 책임이 아니다"), 09-14에 먼저 들어온 21,230건은 이후 몇 차례 배치가 재실행돼도 영구히 "수정 전" 매칭 결과로 DB에 남는다 — 실제로 이 21,230건의 `updated_at`은 `created_at`과 동일해(재처리된 적이 없음) 확인됐다. **결론: 지금 이 DB의 SALE 미매칭 22,180건 중 21,230건(95.7%)은 새 매칭 로직을 반영하지 않은 stale 데이터다 — 이 상태로 매칭 실패 원인을 통계적으로 분석하면 결론이 왜곡된다.** 아래 ②는 이 오염을 배제하고 순수 post-fix(09-15) 950건만으로 다시 쪼갠 결과다. **완결 필요(우선순위 높음) — 신뢰할 수 있는 매칭 통계를 얻으려면 `trade` 테이블을 비우고 전체 재수집하거나, 기존 미매칭 행에 대해 매칭만 다시 돌리는 별도 배치(재적재가 아니라 재매칭)를 추가해야 한다. 후자를 택한다면 `upsert()`가 매칭 필드를 보존하는 지금 설계와 충돌하지 않도록 별도 경로(예: `complex_id IS NULL`인 행만 골라 `ComplexMasterMatcher`를 다시 돌리고 그 결과만 업데이트하는 일회성 마이그레이션)로 만들어야 한다 — `upsert()` 자체를 바꾸면 위 javadoc이 이미 근거를 남긴 "재매칭은 upsert 책임이 아니다"라는 설계를 깨게 된다.**

**② post-fix(09-15) 950건만 대상으로 "1차 필터링 후보 존재 여부"로 재분류하면 두 개의 서로 다른 원인이 나온다:**

| 구분 | 건수 | 비율 |
| --- | --- | --- |
| 후보없음(1차 필터링에서 이미 탈락) | 366 | 38.5% |
| 후보있음(후보는 있는데 지번 비교에서 탈락) | 582 | 61.3% |

- **후보없음 366건 중 354건(96.7%) — 신규 버그 C(미수정), 결정론적·재현 100%.** `complex.dong_ri`는 xlsx 원본이 어떤 경우에도 공백을 포함하지 않고 "리"만 저장하는데(`SELECT COUNT(*) FROM complex WHERE dong_ri LIKE '% %'` → 0), `legal_district_code.eupmyeondong_name`은 리(里) 단위 leaf 행에서 "읍/면+리"를 공백으로 결합해 저장한다(예: "팽성읍 송화리", `LegalDistrictCodeLoader.resolveNameParts()`가 원래부터 이렇게 저장 — CLAUDE.md의 기존 "리(里) 단위 leaf 행의 매칭 가능 여부" residual risk 항목 참고). `ComplexMasterMatcher`의 1차 필터링이 `c.dong_ri = l.eupmyeondong_name`로 정확 일치를 요구하는 이상, 리 단위 지역은 공백 유무 때문에 **구조적으로 후보가 0건일 수밖에 없다** — 실제로 이 366건 중 공백을 포함한 eupmyeondong_name(읍/면+리 결합형)은 354건 전부가 후보없음으로 떨어졌고, 공백 없는(단일 레벨) eupmyeondong_name 12건만 다른 원인이다. 이 residual risk 항목이 "umdNm이 읍/면 이름만 줄 가능성"으로 우려했던 반대 방향 — 실제로는 umdNm이 "읍/면+리"를 결합해서 주고(BAT-MAT-01의 `matchByTradeSggCd()`가 정확히 이 결합형 이름과 일치시켜 legal_dong_cd 매칭 자체는 성공한다), **BAT-MAT-02가 그 결합형 이름을 그대로 complex.dong_ri와 비교하려다 실패**하는 것으로 실측 확정됐다. 수정 방향(아직 미착수) — `ComplexMasterMatcher`가 `legalDistrictCode.eupmyeondongName`에서 읍/면 접두어를 제거하고 순수 "리" 이름만 추출해 `dong_ri`와 비교하도록 정규화하는 것이 유력해 보이나, 읍/면 이름 자체에 "리"로 끝나는 경우(드묾)나 공백 두 개 이상인 경우 등 엣지케이스를 실 데이터로 더 확인해야 한다 — **지성 확인 필요, 이번 세션에 코드 수정하지 않았다.**
- **후보있음 582건 — 코드 버그로 보이지 않음, K-apt 원본 데이터의 커버리지 한계로 추정(전수 확인 안 함).** 샘플 확인(강북구 수유동 등) 결과 같은 동에 매칭 후보 단지가 존재함에도(예: 수유동에 "래미안수유"/"수유벽산" 등 8개), 실제 미매칭 거래의 건물명("경원북한산휴그린", "동대문솔하임", "스타파크" 등)이 `complex` 테이블 전체를 뒤져도 단 한 건도 존재하지 않는다 — 지번이 안 맞는 게 아니라 그 건물 자체가 K-apt 단지 기본정보에 아예 등록돼 있지 않다. 소규모/개별("나홀로") 아파트가 K-apt 등록 대상에서 빠지는 경우로 추정되나, 이 프로젝트가 xlsx 원본의 등록 기준(세대수 등)을 직접 확인한 적은 없다 — **완결 필요, 등록 기준을 확인하지 못하면 이 582건은 FR-2.5 목표치 산정 시 분모에서 빼야 하는지(애초에 매칭 대상이 아닌 거래)도 판단할 수 없다.**

**③ lawd_cd 커버리지 공백 — 별도 미해결 이슈로 기록(이번 세션 범위 밖, 수정하지 않음).** `LegalDistrictCodeRepository.findDistinctActiveSggCd()`(`SELECT DISTINCT SUBSTRING(legal_dong_cd,1,5) ... WHERE is_active=true`)가 만드는 BAT-SCH-01 순회 목록(280개)은 활성 행이면 계층 레벨(시도/시군구/구) 구분 없이 전부 5자리로 뭉쳐 넣는다 — 그래서 실제로는 "시군구(약 250여 개)"보다 많은 280개가 나오는데, `batch_log`에서 `SUM(processed_count)=0`(전 기간·전 데이터셋에서 실거래가 단 한 건도 없음)인 lawd_cd 62개를 실측해 세 그룹으로 갈렸다:

| 그룹 | 개수 | 실제 상태 |
| --- | --- | --- |
| 시도 대표코드(중간 3자리=`000`, 예: `11000`) | 16 | 무해 — data.go.kr이 시도 단위 조회는 처음부터 지원하지 않아 항상 빈 결과, 실제 데이터는 하위 시군구 코드로 정상 수집됨 |
| 시(市) 대표코드인데 구(區) 코드가 legal_district_code에 별도로 존재(예: `41110` 수원시 ↔ `41111`/`41113`/`41115`/`41117`) | 12(수원/성남/안양/부천/안산/고양/용인/청주/천안/포항/창원/전주) | 무해 — 위 "버그 A"(시+구 도시 sigungu 표기 정규화)가 다룬 것과 같은 시+구 구조. 실거래는 구 코드로 정상 수집되고, 시 대표코드는 API 호출만 낭비하는 중복일 뿐 데이터 손실은 없다 |
| **하위 구 코드 자체가 legal_district_code에 없음 — 진짜 커버리지 공백(데이터 손실 확정)** | **화성시(41590) 1개 + 인천 중구/동구/서구/옹진군(28110/28140/28260/28720) 4개 + 광주 5개구 전체(29110/29140/29155/29170/29200) + 전라남도 22개 시군구 전체(46110~46910)** = 32개 | `trade` 테이블에 `legal_dong_cd LIKE '29%'` 또는 `'46%'`인 행이 **0건** — 광주광역시+전라남도 전체(인구 약 300만)가 실거래 데이터 수집 자체에서 완전히 빠져 있다. 화성시·인천 3구도 마찬가지로 raw 데이터가 0건. 이미 CLAUDE.md가 "잔여 불일치 2,399개 단지"(complex 매칭 실패)로 문서화했던 화성 신설 일반구/인천 신설 자치구/광주-전남 통합 이슈가, 사실은 **complex 매칭 단계가 아니라 그보다 훨씬 앞선 원시 수집 단계(BAT-CLC-01)에서부터 100% 실패**하고 있었다는 뜻이다 — data.go.kr이 이 지역들에 대해 이미 신설/개편된 lawd_cd를 요구하는데, 우리 `legal_district_code`(구버전 CSV)는 그 신설 코드를 아예 갖고 있지 않아 옛 코드로 질의하면 항상 빈 결과(result_code 000, "정상"으로 위장된 데이터 없음)만 돌아온다. **부분 해소(2026-09-17, 아래 "BAT-MAT-01/BAT-MAT-02 법정동코드 참조자료 재적재" 절) — legal_district_code의 32개 코드 자체는 재적재로 복구돼(is_active=false→신규 코드 활성) 이 표의 원인 진단은 그대로 유효하다. 다만 `trade` 실거래 데이터는 여전히 0건이다 — legal_district_code를 최신화한다고 과거 누락분이 저절로 채워지지 않는다, 소급 수집(4단계)을 별도로 돌려야 한다. 이 행의 "우선순위 높음" 태그는 소급 수집이 끝나기 전까지 유지한다 — 사용자가 체감하는 실제 문제(이 3개 지역 실거래가 안 보임)는 아직 그대로다. 다음 액션은 data.go.kr serviceKey의 일일 호출 한도 확인부터.** |
| 분류 불가(소규모 도서/산간 지역, 정상적으로 0건일 가능성) | 2(울릉군 47940, 남해군 48840) | 하위 구 코드도 없고 인구가 매우 적어 해당 2개월 창에 아파트 매매가 실제로 0건이었을 가능성을 배제할 수 없다 — 별도 조치 없이 다음 배치 결과로 재확인 |

### BAT-MAT-02 버그 C 수정 + `TradeRematchRunner` 재매칭 인프라 신설 (2026-09-16)

위 조사(①②)에서 확정한 두 원인 중 버그 C(리 단위 dong_ri 결합형 표기 불일치)를 실제로 수정하고,
`TradeRepository.upsert()`가 매칭 필드를 보존해(설계상 의도) 기존 stale 행이 재수집만으로는 절대
갱신되지 않는 문제를 메우는 재매칭 전용 배치를 신설했다. 이 절이 그 전체 과정과 도중에 발견한
추가 버그(시흥시 회귀), 그리고 최종 결과 수치를 기록한다.

**버그 C 수정** — `ComplexMasterMatcher.extractComparableDongRi(String)` 신설. 1차 필터링 직전
`legalDistrictCode.getEupmyeondongName()`이 "읍/면+리" 결합형(예: "팽성읍 송화리")이면 마지막 공백
이후 토큰("송화리")만 취해 `complex.dong_ri`(항상 공백 없는 "리" 이름만)와 비교하도록 정규화했다.
`normalizeSigungu()`와 같은 원칙(legal_district_code 쪽 값에만 적용, complex.dong_ri는 안 건드림).
검증: `ComplexMasterMatcherTest`에 회귀 테스트 2건 추가.

**`TradeRematchRunner`/`TradeRematchBatchProcessor` — 재매칭 전용 유지보수 배치(신규, `batch.matcher`
패키지).** 매일 도는 BAT-SCH-01 파이프라인에는 배선하지 않고, `rematch` 스프링 프로필로만 활성화되는
`TradeRematchCommandLineRunner`로 수동 트리거한다(`./gradlew bootRun --args='--spring.profiles.active=local,rematch'`,
2차 패스는 `--mode=full` 추가). 두 가지 패스를 지원한다:

- **1차 패스(`rematchUnmatched()`)**: `complex_id IS NULL AND legal_dong_cd IS NOT NULL`인 행만 대상.
- **2차 패스(`rematchUpdatedBefore(cutoff)`)**: `complex_id` 유무와 무관하게 `updated_at < cutoff`인
  행 전부 대상 — **1차 패스만으로는 불충분하다는 게 이번에 실측으로 확인됐다.** 버그 A/B(지번 $ 앵커)
  수정 전 지번 비교가 막혀 있던 시절 "그나마 비슷한 단지"로 SIMILAR 오배정된 행은 `complex_id`가
  이미 채워져 있어 1차 패스가 건너뛴다 — 그 오배정을 바로잡으려면 이미 매칭된 행도 다시 돌려야 한다.

두 패스 모두 트레이드ID 커서로 순회하며 `applyRematch()`(새 네이티브 UPDATE, 매칭 3필드+updated_at만
갱신)는 결과가 이전과 같으면 아예 호출하지 않는다(멱등) — 그래서 1차 패스가 이미 고친 행이 2차 패스
대상에 다시 포함돼도 안전하게 unchanged로만 집계된다.

**설계 실수와 수정 — 배치 전체를 하나의 트랜잭션으로 묶으면 안 된다(중요, 재발 방지용으로 남김).**
최초 구현은 `rematchUnmatched()` 메서드 전체(커서 순회 루프 전부)에 `@Transactional`을 걸었다 —
그 결과 수만 건을 처리하는 전체 실행(1시간 반 이상)이 **단 하나의 트랜잭션**이 됐다. 실행 중 앱
로그는 재배정을 계속 찍는데도 다른 커넥션(DB 직접 조회)에서는 `MAX(updated_at)`이 몇 시간째 전혀
움직이지 않는 것으로 발견했다 — 커밋되지 않은 변경은 그 트랜잭션을 연 커넥션 밖에서 전혀 보이지
않기 때문이다. 이 설계의 위험: (1) 중간에 프로세스가 죽거나 커넥션이 끊기면 그때까지의 작업이
전부 롤백돼 사라진다, (2) 외부에서 진행 상황을 전혀 관측할 수 없다. **수정**: 배치(500건) 단위
조회~매칭~갱신을 `TradeRematchBatchProcessor`(별도 빈)의 `@Transactional` 메서드 하나에 담아 배치가
끝나는 즉시 커밋되게 분리했다 — `TradeRematchRunner`(오케스트레이터)는 이제 트랜잭션을 갖지 않고
커서만 들고 이 빈을 반복 호출한다. 같은 클래스 내부 self-invocation으로는 `@Transactional` 프록시가
안 걸리는 이 프로젝트의 기존 함정(COM-CACHE-01 `ComplexDetailCache` 분리와 같은 이유, 위 SVC-RCV-01
절 참고) 때문에 별도 빈으로 뺐다 — 배치 조회와 그 결과(지연 로딩되는 `legalDistrictCode`/`complex`
연관관계) 사용이 반드시 같은 트랜잭션 안에서 일어나야 하므로, 조회 자체도 이 빈 안에서 수행한다.
검증: `TradeRematchBatchProcessorTest`(배치 단위 매칭/갱신 로직, 4건), `TradeRematchRunnerTest`(커서
오케스트레이션, mocked `TradeRematchBatchProcessor`로 재작성, 3건).

**도중 발견한 추가 버그 — `normalizeSigungu()`가 "시흥시"를 "흥시"로 망가뜨리는 회귀(이번 세션 것과
무관, 2026-09-15 버그 A 수정 때부터 있었음).** 2차 패스 실행 중 `SIMILAR→null`(오히려 매칭이 풀리는)
전환이 376건 나와 조사한 결과 발견했다 — 전부 시흥시(경기도) 소속 거래였다. 원인: `normalizeSigungu()`
가 "공백 제거 후 문자열 끝이 아닌 위치의 '시'를 제거"하는데, 이 조건은 공백 유무를 보지 않는다.
"시흥시"는 "시+구" 구조가 전혀 아닌 단일 시(구 분리 없음)인데, 도시 이름 자체가 "시"로 시작해서
(시흥+시) 그 첫 글자가 "시+구" 분리자로 오인돼 지워지며 "흥시"가 됐다 — `complex.sigungu="시흥시"`
(xlsx 원본, 불변)와 영원히 달라져 **시흥시 소속 거래 전체(실측 2,189건)가 1차 필터링 후보 0건으로
떨어져 있었다.** "목포시"가 이 버그를 우연히 피해간 건 "시"가 마지막 글자였을 뿐, 근본 원인은 같았다.
**수정**: 원본에 공백이 있을 때만(`raw.contains(" ")`, 즉 진짜 "시 구" 구조일 때만) 정규화를
수행하도록 전제 조건을 추가했다 — 공백 없는 단일 시/군 표기는 이제 "시"가 몇 번, 어느 위치에
있든 무조건 원본 그대로 반환한다. DB 전수 확인 결과 이 패턴에 걸리는 다른 단일 시/군 이름은
없었다(시흥시가 유일). 검증: `ComplexMasterMatcherTest`에 회귀 테스트 추가.

**최종 결과 — 1차/2차/3차(시흥시 수정 후 재검토) 패스 전부 실행 완료, 로컬 DB(포트 3307) 실측.**

| 패스 | 대상 | unchanged | changed | changed 세부 |
| --- | --- | --- | --- | --- |
| 1차(`rematchUnmatched`) | complex_id IS NULL (37,390건, SALE+RENT) | 12,251 | 25,139 | null→EXACT/SIMILAR |
| 2차(`rematchUpdatedBefore`, 버그 C 반영) | 전체(125,237건) | 113,515 | 11,722 | SIMILAR→EXACT 11,346 / SIMILAR→null(시흥시 회귀 발견) 376 |
| 3차(`rematchUpdatedBefore`, 시흥시 수정 반영) | 전체(125,237건) | 123,168 | 2,069 | null→EXACT 1,589 / null→SIMILAR 480(전부 시흥시) — 추가 회귀 없음 |

**세션 시작 대비 SALE 최종 수치(`trade.deal_category='SALE'`, 51,957건):**

| 지표 | 세션 시작(이 문서 작성 시점 스냅샷) | 최종 |
| --- | --- | --- |
| EXACT | 14,861 | 43,933 |
| SIMILAR | 14,916 | 4,595 |
| 미매칭(legal_dong_cd 있음) | 22,149 | 3,398 |

미매칭이 22,149건 → 3,398건으로 84.7% 줄었다. 전체(SALE+RENT, 125,312건) 기준으로는 EXACT 101,132
(80.7%)/SIMILAR 13,547(10.8%, 합산 **91.5%**)/미매칭(legal_dong_cd 있음) 10,558(8.4%)/미매칭(legal_dong_cd
없음) 75(0.06%)이다. 3차 패스가 `null→X` 전환만 내고 `X→null` 같은 역행이 전혀 없었다는 것이 이 시점에서
매처가 안정적으로 수렴했다는 신호다 — 남은 미매칭 10,558건은 위 ②에서 이미 분류한 두 원인(K-apt
단지 기본정보 커버리지 한계로 추정되는 "후보 있음" 쪽, 그리고 아직 다루지 않은 소수의 기타 "후보
없음" 12건류)이 대부분일 것으로 보이나 전수 재확인은 하지 않았다 — **완결 필요**, 다음에 이 도메인을
열 때 최신 수치로 ②의 breakdown을 다시 돌려 갱신하라.

**91.5%(EXACT+SIMILAR)를 "FR-2.5 목표(98.9%) 거의 근접"으로 읽으면 안 된다 — 이 수치는 위 ③의 lawd_cd
커버리지 공백(32개 코드: 화성시 1 + 인천 신설 자치구 4 + 광주 5구 전체 + 전라남도 22개 시군구 전체)이
그대로 남아있는 상태에서 나왔다.** 이 32개 지역은 `trade` 테이블에 단 한 건도 수집되지 않아(원시
수집 단계 자체가 실패) 애초에 이번 재매칭 대상 모집단(125,312건)에 포함되지도 못했다 — 즉 91.5%는
"현재 수집된 것 중에서의 비율"이지 전국 기준이 아니다. 이 공백이 채워지면(신설/개편된 lawd_cd를
`legal_district_code`에 반영하고 그 지역 거래가 실제로 수집되기 시작하면) 그 신규 거래들은
`legal_district_code`에 대응 코드가 없어 legal_dong_cd 매핑 단계(BAT-MAT-01)부터 막혀 구조적으로
미매칭으로 들어올 가능성이 높다 — 이번 세션에서 고친 5개 버그(sigungu 표기/EXACT 0건/RENT 0건/버그
C/시흥시 회귀)와는 무관한 별개 이슈다(③ 참고). **FR-2.5의 98.9% 목표는 이 lawd_cd 공백이 메워지기
전까지는 애초에 도달 불가능한 수치라는 뜻이므로, 91.5%를 목표 대비 진척도로 해석하지 말 것.**

**남은 절차**: `TradeRematchRunner`/`TradeRematchBatchProcessor`는 배치 파이프라인에 상시 배선되지
않는 일회성 유지보수 도구로 코드베이스에 남겨뒀다 — 다음에 BAT-MAT-02(또는 BAT-MAT-01) 매처 로직을
또 고치면 같은 방식(1차: complex_id IS NULL, 2차: `--mode=full`로 전체 재검토)으로 재사용하라.

### BAT-MAT-02 재매칭 — dedup_hash 미갱신 버그 수정 + 기존 stale 행 복구 (2026-09-16, PR 리뷰 지적)

위 재매칭 인프라를 PR로 올린 뒤 리뷰(Codex, P1)에서 지적된 결함이다: `applyRematch()`가 complex_id/
match_method/match_confidence만 갱신하고 **dedup_hash는 그대로 뒀다.** `DedupHashCalculator`는
complex_id가 있으면 그 값을, 없으면 `UNMATCHED|sggCd|umdNm|buildingName|jibun`을 식별자로 써서 해시를
만든다(그 클래스 javadoc 참고) — 재매칭으로 complex_id가 바뀌었는데 dedup_hash를 그대로 두면, 다음
정상 수집(BAT-SCH-01)이 같은 실거래를 다시 파싱할 때는 새 complex_id 기준 해시를 계산하므로 이 행의
저장된(옛) 해시와 달라진다. `upsert()`의 UNIQUE 매칭이 빗나가 같은 실거래가 두 행으로 중복 적재된다.

**수정**: `DedupHashCalculator`를 `batch.loader` 전용에서 `public`으로 승격해 `batch.matcher`가 재사용할
수 있게 했다. `TradeRematchBatchProcessor.applyChange()`가 complex_id가 실제로 바뀔 때만(매칭 방법만
바뀌는 경우는 해시가 영향받지 않으므로 제외) 새 draft로 dedup_hash를 재계산해 `applyRematch()`(dedup_hash
파라미터 추가)에 함께 실어 보낸다. **재계산한 해시를 이미 다른 행이 쓰고 있으면**(두 행이 재매칭 결과
사실상 같은 실거래로 수렴한 경우) `reconcileHashCollision()`이 그 다른 행(이미 정상 경로로 올바른
정체성을 가진 행)을 그대로 두고 이 stale 행을 삭제해 UNIQUE 제약을 위반하지 않으면서 중복을 없앤다.

**기존에 이미 만들어진 stale dedup_hash 복구 — `repairDedupHashes()`(`--mode=repair-hash`) 신설.** 이
수정 전에 이미 실행한 1~3차 재매칭 패스(위 절, 합계 changed 38,930건)는 이 수정이 없던 코드로
실행됐으므로, 그 행들의 dedup_hash는 여전히 옛 상태로 DB에 남아있었다 — 코드를 고친 것만으로는
이미 오염된 기존 행이 저절로 복구되지 않는다. 매칭 결과는 건드리지 않고 현재 저장된 complex_id/
match_method/match_confidence 기준으로 dedup_hash만 재계산해 바로잡는 별도 패스를 추가해 로컬 DB에
실행했다.

**실행 결과(로컬 DB 실측) — 예상보다 훨씬 큰 규모의 실제 중복이 발견·정리됨.** `unchanged=97,905,
changed=27,407`(그중 13,149건은 해시만 갱신, **14,258건은 진짜 중복이라 삭제**) — trade 총 건수가
125,312 → **111,054**로 줄었다. 이 대량 삭제가 버그인지 정당한 정리인지 별도로 검증했다:

- 삭제된 14,258건 중 13,704건(96.1%)이 09-14 14~15시(버그 A/B 수정 **전** 최초 수집분, stale
  UNMATCHED 해시)에 집중돼 있었다 — 그 배치가 처리한 34,996건 중 13,864건이 이번에 삭제됐다.
- 09-15 18~19시(버그 A/B 수정 **후** 재수집분, 애초부터 올바른 complex_id 기준 해시로 들어온 행)
  89,759건은 단 한 건도 삭제되지 않고 전부 보존됐다.
- 복구 후 `(complex_id, deal_date, floor, exclu_use_area, deal_amount)` 조합 기준으로 남은 중복
  그룹을 다시 조회하면 **0건**이다.

즉 이 14,258건은 "같은 실거래가 09-14(구버전 매처, 미매칭이라 UNMATCHED 식별자로 해시)와 09-15
(신버전 매처, 처음부터 올바른 complex_id로 해시)에 각각 다른 trade_id로 중복 적재돼 있던 것"이
이번에 하나로 합쳐진 것이다 — PR 리뷰가 지적한 시나리오(재매칭 후 dedup_hash 미조정 → 다음 정상
수집이 중복 INSERT)가 **이 세션의 1~3차 재매칭 자체가 원인이 되어 이미 실제로 벌어지고 있었다**는
뜻이다(9-15 재수집이 이미 정상적으로 "새 행"을 만들어냈고, 그 옛 짝이 stale 채로 방치돼 있었을 뿐).

**PR 재리뷰 지적 두 가지, 모두 반영 완료(2026-09-16).**

1. **이 mutation 로직(충돌 시 행 삭제) 자체가 실 DB로 검증된 적이 없었다** — 이 프로젝트가 이미 여러
   차례 확인한 원칙("UNIQUE 제약 동시성/데이터 변형 로직은 Mockito로 증명 불가, Testcontainers 필요")이
   정확히 겨냥하는 종류의 코드인데, `repairDedupHashes()`/`applyChange()`의 충돌-삭제 분기는 Mockito
   단위 테스트(5건)만 있고 MariaDB IT가 없었다. `TradeRematchBatchProcessorMariaDbIT`(Testcontainers,
   `trade-race-schema.sql` 재사용)를 신설해 "같은 identity를 가진 두 행 중 target은 보존, stale은
   삭제, 살아남은 행의 데이터는 훼손되지 않음"을 실 DB 기준으로 검증했다 — `./gradlew integrationTest`
   통과 확인(10개 MariaDB IT 클래스, 34 테스트 전부 그린). 다음에 이 스크립트류를 재사용할 일이 생기면
   (다른 프로젝트든 재발 상황이든) 이 IT가 안전망이 돼 준다.
2. **삭제 기준(target hash 보유 여부)이 데이터 완전성과 직결되지 않는다는 지적** — 복합키
   (complex_id, deal_date, floor, exclu_use_area, deal_amount) 기준 중복 0건 확인은 dedup_hash 자체가
   이 키들로 만들어지니 같은 신호의 재확인일 뿐, 그 키 밖의 필드(등기일자/거래유형/동정보/취소여부)는
   못 잡는다는 지적이 맞다. 삭제된 행 자체는 스냅샷이 없어 복구 불가능하므로, **생존한 행 5건을
   data.go.kr 상세(15126468, RTMSDataSvcAptTradeDev) 실 API로 직접 재조회해 스팟체크**했다(종로구
   11110/202608, `curl`로 원본 XML 수신 후 jibun·건물명·금액·층·면적으로 매칭 확인) —
   `rgstDate`(등기일자)·`dealingGbn`(거래유형)·`aptDong`(동정보)·`cdealType`(취소여부) 4개 필드
   모두 5건 전부 실 API와 정확히 일치했다(등기일자·동정보는 5건 모두 원본 자체가 공란이라 우리
   DB의 NULL이 데이터 손실이 아니라 정확한 반영임도 함께 확인됨). 완벽한 보증은 아니지만(5건 표본),
   복합키 밖 필드에서도 이상 징후는 발견되지 않았다.

   **부수적으로 확인된 사실**: `datagokr-apt-sale-approval-pending` 메모리(2026-09-14 작성, "SALE
   두 데이터셋이 활용신청 승인 안 됨")가 이제 stale하다 — 이 스팟체크에서 15126468에 대해 실제로
   HTTP 200 + resultCode 000 + 정상 데이터를 받았다. 승인이 그 사이 완료된 것으로 보인다(메모리
   갱신 완료).

**교훈(다음에 비슷한 일괄 삭제를 동반하는 복구를 돌릴 때 참고) — 삭제 전 스냅샷을 남기지 않았고,
삭제 로그에 어떤 행(target)과 병합됐는지 tradeId를 남기지 않았다.** 두 로그(`applyChange()`/
`repairDedupHashBatch()`의 충돌 삭제 분기)는 삭제되는 쪽의 tradeId만 남기고 살아남는 target의
tradeId는 남기지 않는다 — 사후 검증을 created_at 시간대 분포(간접 증거)로 대신할 수 있었으니 이번엔
운이 좋았지만, 다음에 이 경로를 또 타면 로그에 target tradeId도 함께 남기는 것을 검토하라. 또한 대량
삭제가 예상되는 복구 작업 전에는 `mysqldump`나 최소한 영향받을 행의 tradeId 목록을 미리 떠 두는
습관이 필요하다 — 이번엔 사후에 로그와 시간대 분포로 재구성해 검증했지만, 그 로그가 마침 삭제되지
않고 남아있었던 것도 우연이었다.

**진짜 최종 수치(중복 제거 후, 로컬 DB 실측) — 위 "최종 결과" 절의 91.5%는 이제 stale하다.** 중복
14,258건이 전부 이미 매칭된(EXACT 또는 SIMILAR) 행끼리의 중복이었으므로(미매칭 건수 10,558/3,398은
전혀 변하지 않았다), 제거 후 비율은 오히려 소폭 낮아진다 — 전체(111,054건) EXACT 87,713(79.0%)/
SIMILAR 12,708(11.4%, 합산 **90.4%**)/미매칭(legal_dong_cd 있음) 10,558(9.5%)/미매칭(legal_dong_cd
없음) 75(0.07%). SALE만(37,699건, 매매 하나의 실거래를 두 datasetId·두 수집일에 걸쳐 중복 집계하던
것이 없어지며 총 건수 자체가 51,957→37,699로 줄었다)은 EXACT 30,514(81.0%)/SIMILAR 3,756(10.0%)/
미매칭 3,398(9.0%). 위 ③에서 지적한 lawd_cd 커버리지 공백(광주/전남/화성·인천 신설구 32개 코드) 전제는
이 수치에도 동일하게 적용된다 — 여전히 FR-2.5 목표(98.9%)와 직접 비교하면 안 된다.

### BAT-MAT-01/BAT-MAT-02 법정동코드 참조자료 재적재 — 위 ③ lawd_cd 커버리지 공백 32개 코드 중 시군구
코드 자체를 해소 (2026-09-17)

위 "BAT-MAT-02 실 배치 재실행 추가 조사" 절 ③이 확정만 하고 해소하지 않은 채 남겨뒀던 lawd_cd
커버리지 공백(화성시 신설 일반구, 인천 행정체제 개편, 전남광주통합특별시 출범 — legal_district_code
참조자료가 2025-08 스냅샷에 머물러 2026년 두 차례 개편을 반영하지 못한 문제)의 **원인(legal_district_code에
신설 코드 자체가 없음)만 이번 세션에서 해소했다.**

**✅ 해소됨(2026-09-18) — 사용자가 실제로 체감하는 문제(전남광주·화성·인천 신설구 실거래가가 안 보이던
것)까지 완전히 닫혔다.** 이 절이 처음 쓰였을 때는 "인프라를 안전하게 만드는 것"(legal_district_code가
최신 행정구역을 알게 됨, 매칭 로직이 새 표기를 정확히 처리함, 재발 방지 체크가 자동으로 붙음)까지만
끝난 상태였고 `trade` 테이블의 해당 지역 실거래는 여전히 0건이었다 — **아래 "BAT-CLC-01 lawd_cd
커버리지 공백 소급 수집(백필) 완료" 절이 4단계(소급 수집)를 실행해 이 잔여 이슈를 닫았다.** 우선순위
높음 태그는 그 절의 완료 기준 확인으로 해제한다.

**1단계 조사 결과 — 예상과 달리 코드 변경이 거의 필요 없었다.** 행정안전부 법정동코드 전체자료 신규
스냅샷(`src/main/resources/data/법정동코드.txt`로 교체, distinct sgg_cd 280→284, 신규 36/폐지 32 —
사전 diff와 정확히 일치)을 검토한 결과:
- `LegalDistrictCodeLoader.loadInitial()`은 이미 `deactivateAll()`(전체 비활성화) → 이번 CSV의 "존재"
  행만 upsert 재활성화 패턴이라 DELETE를 전혀 쓰지 않는다 — 폐지된 32개 코드는 애초에 삭제되지 않고
  `is_active=false`로 자동 보존된다. 코드 변경 불필요.
- 파서(`readExistingRecords()`)는 이미 탭/콤마 구분자를 자동판별한다. 코드 변경 불필요.
- 화성시 신설구("경기도 화성시 만세구" 등)는 `resolveNameParts()`가 `sigunguName="화성시 만세구"`
  (공백 포함)로 파싱하는데, `complex.sigungu`(xlsx 원본)는 "화성만세구"(붙여쓰기)다 — 표기가 다르다는
  우려가 맞았지만, **기존 `ComplexMasterMatcher.normalizeSigungu()`("시+구" 구조 도시의 공백 제거 정규화,
  버그A 수정 당시 도입)가 이미 이 패턴을 일반적으로 처리해 "화성시 만세구"→"화성만세구"로 정확히
  변환한다** — 별도 정규화 로직 추가 불필요. 인천 신설구·전남광주통합특별시 하위 시군구는 원본에
  공백이 없어 정규화 자체가 필요 없다. 라이브 로컬 DB(21,680건 complex)로 leaf 레벨(`dong_ri`)까지
  교차검증해 세 그룹 모두 기존 매칭 로직이 그대로 통한다는 것을 확인했다.

**⚠️ premise 정정 — `complex.legal_dong_cd`는 21,680건 전부(100%) NULL인 미사용 컬럼이었다.** 사용자
지시문은 이 컬럼으로 "영향받은 complex 건수"를 세라고 요청했는데, 실제로는 이 컬럼에 값을 쓰는 코드가
프로젝트 어디에도 없다(전수 grep 확인) — `Complex.legalDistrictCode`(`@ManyToOne`, FK 컬럼명
`legal_dong_cd`)는 실제 매칭 파이프라인(BAT-MAT-02)이 채우지 않는, 스키마상으로만 존재하는 선택 FK다.
실제 매칭 신호는 `trade.legal_dong_cd`(BAT-MAT-01)/`trade.complex_id`+`match_method`(BAT-MAT-02)에
있다. **사용자 확인 후 이 컬럼은 이번 작업 범위에서 그대로 두기로 확정했다** — 채우는 로직을 추가하지
않는다. 대신 trade 테이블 기준으로 재측정한 결과, 3개 영향 그룹(전남광주통합특별시 1,709개
complex·화성시 4개 신설구 422개·인천 4개 신설구 362개, 합계 2,493건 = 전체의 11.5%, 지시문의 추정치와
일치) 전부 해당 lawd_cd의 trade 건수가 **0건**이었다 — 매칭 실패가 아니라 원시 수집(BAT-CLC-01)
단계부터의 누락임을 재확인했다(위 ③과 같은 결론).

**⚠️ 별도로 짚어야 할 발견 — 법정동코드 재적재는 지금까지 프로덕션 코드 어디에서도 호출된 적이 없었다.**
`loadInitial(` 호출부를 전수 grep한 결과 테스트 밖에는 단 한 곳도 없었다 — 즉 재적재 전 DB에 있던
20,555건은 이 저장소의 정상 운영 경로로 들어간 게 아니라, 이 프로젝트 어딘가의 일회성 스크립트나
수동 조작으로 적재된 것으로 보인다(정확한 출처는 이번 세션에서 추적하지 않았다). 위 "캐싱" 절이
`regionAutocomplete` 캐시 무효화 트리거를 이미 "법정동코드 재적재(비정기)"라고 전제하고 있었는데, 그
전제를 실행할 실제 진입점이 없었던 셈이다.

**결정(사용자 확인) — 아래 `LegalDistrictCodeReloadCommandLineRunner`를 이번 한 번만 쓰는 일회성
도구가 아니라, 앞으로 법정동코드 참조자료가 갱신될 때마다(행정구역 개편 등, 비정기) 쓰는 표준
재적재 절차로 확정한다.** 절차: (1) 행정표준코드관리시스템에서 새 "법정동코드 전체자료"를 내려받아
`backend/src/main/resources/data/법정동코드.txt`로 교체, (2) `./gradlew bootRun
--args='--spring.profiles.active=local,reload-legal-district'`로 재적재 실행(2026-09-27부터 웹 서버·
스케줄링 없이 돌고 끝나면 스스로 종료한다 — 성공 0/실패 1, 운영 절차는 runbook 9절),
(3) `RegionCoverageChecker`가 재적재 이벤트에 자동으로 붙어 커버리지 공백을 로그로 알려준다 — 이번처럼
"공백 0건"을 확인하는 것이 재적재가 온전히 반영됐다는 최소 신호다. **다음에 이 절차를 또 잊고
"재적재 경로가 없다"는 걸 다시 발견하는 일이 없도록, 여기 이 문단을 남긴다.**

**2단계 — 실제 재적재 실행 + 안전성 검증.**
- `LegalDistrictCodeReloadCommandLineRunner`(신규, `reload-legal-district` 프로필 전용,
  `TradeRematchCommandLineRunner`와 같은 "평소 부팅에 관여하지 않는 수동 유지보수 진입점" 패턴)를
  신설해 `src/main/resources/data/법정동코드.txt`를 `ClassPathResource`로 읽어 `loadInitial()`을
  호출한다.
- `LegalDistrictCodeLoaderMariaDbIT`(신규, Testcontainers) — "폐지된 코드는 삭제되지 않고 `trade` FK가
  깨지지 않는지"는 Mockito로 증명 불가능한 종류라(`LegalDistrictCodeLoaderTest`는 `saveAll()` 인자만
  검증) 1라운드→2라운드 재적재를 실제 FK 제약이 걸린 DB 위에서 재현했다. **최초 구현은 라운드 사이에
  같은 PK를 미리 `findById()`로 로드해 뒀다가 1차 캐시(영속성 컨텍스트) staleness로 거짓 실패가 났다**
  — `deactivateAll()`(벌크 UPDATE)은 DB를 즉시 바꾸지만 이미 로드된 managed 엔티티의 인메모리 상태는
  갱신하지 않는다(CLAUDE.md의 "`@Modifying` 벌크 쿼리" 원칙과 같은 함정의 변형, 다만 이번엔 순수
  테스트 방법론 문제였지 프로덕션 버그는 아니었다 — 실제 `loadInitial()` 각 호출은 항상 독립된
  영속성 컨텍스트에서 실행된다). 라운드 사이에 `entityManager.clear()`를 넣어 해결했다.
- **실제로 로컬 DB(포트 3307, complex 21,680건·trade 111,054건 보유한 실 데이터)에 재적재를
  실행했다** — `./gradlew bootRun --args='--spring.profiles.active=local,reload-legal-district'`.
  결과: `legal_district_code` 20,555→24,080건(전부 추가, 삭제 없음), 활성 20,555→20,560건, 활성
  distinct sgg_cd 280→284(정확히 일치). 샘플 검증한 폐지 12개 sgg(28110/28140/28260/29000/29110/
  29140/29155/29170/29200/46000/46110/46710)는 전부 `is_active=false`로 보존, 신규 10개 sgg(12000/
  12110/28125/28155/28275/28290/41591/41593/41595/41597)는 전부 `is_active=true`로 정상 추가됐다.
  `complex_count`/`trade_count`는 재적재 전후 불변(21,680/111,054) — FK 데이터 손실 없음 확인.

**3단계 — complex 매칭 복구는 "로직 준비 완료"까지만, 실행할 대상이 아직 없다.** 위 premise 정정에서
확인했듯 3개 영향 그룹의 trade가 현재 0건이라, `TradeRematchRunner`를 지금 돌려도 이 3개 그룹에 대해
재매칭할 대상 자체가 없다(전체 재매칭은 과거 1시간 반 이상 걸린 전례가 있어, 코드 변경이 전혀 없는
지금 실행하는 것은 낭비로 판단해 돌리지 않았다). 정규화 로직(`SigunguNormalizer`, 아래 5단계 참고)이
이미 이 3개 그룹의 (시도, 시군구) 조합을 정확히 커버한다는 것은 재적재 직후 `RegionCoverageChecker`
실행으로 확인했다(공백 0건) — 4단계(소급 수집)로 실제 trade 데이터가 들어오는 즉시 정상적으로
매칭될 준비가 되어 있다는 뜻이다.

**4단계(소급 수집) — 완료(2026-09-18). 실행 내용·검증 결과는 아래 "BAT-CLC-01 lawd_cd 커버리지 공백
소급 수집(백필) 완료" 절 참고.** 이 문단이 처음 쓰였을 때는 API 호출 한도 확인이 먼저 필요하다는
이유로 세션 범위에서 보류돼 있었다 — 이후 세션이 실제 호출 한도(10,000/일)와 오늘자 사용량(0회)을
확인한 뒤 새 클래스 없이 기존 `TradeCollectionScheduler`/`BatchExecutionOrchestrator`를 재사용하는
방향(지시문 원안대로)으로 36개 코드 전체를 실행해 완료했다.

**5단계 — 재발 방지 경량 커버리지 체크 신설.**
- `SigunguNormalizer`(신규, `batch.matcher`) — `ComplexMasterMatcher`의 private
  `normalizeSigungu()`를 그대로 추출했다. 소비자가 2곳(`ComplexMasterMatcher`, 아래
  `RegionCoverageChecker`)으로 늘어나는 시점에 각자 로직을 복제하면 한쪽만 고쳐지는 드리프트 위험이
  있어 공유 클래스로 뽑았다 — 정규화 규칙 자체(공백 있을 때만 "시+구" 분리, `SigunguNormalizerTest`
  참고)는 전혀 바꾸지 않았다. **위 "버그 A"/"시흥시 회귀" 절이 언급하는 `ComplexMasterMatcher.
  normalizeSigungu()`는 이 이관 이전의 역사적 기록이라 그대로 뒀다 — 지금 코드에서 같은 동작을 하는
  것은 `SigunguNormalizer.normalize()`다.**
- `RegionCoverageChecker`(신규) — complex 마스터가 실제로 쓰는 (시도, 시군구) 조합 전체와
  `legal_district_code` 활성 코드가 커버하는 (시도, 정규화된 시군구) 조합을 대조해, 매칭 후보를 찾을
  수 없는 조합을 `log.warn`으로 남긴다. `ADM-01`(5단계 선택 범위, 미구현)이나 `BAT-ERR-01`(API
  result_code 판정 전용, 이 목적과 결이 다름)에 억지로 얹지 않고, 이미 존재하는
  `LegalDistrictCodeReloadedEvent`(BAT-MAT-01 재적재 완료 시 발행)를 구독하는
  `RegionCoverageCheckListener`로 자연스러운 트리거 시점(재적재 직후)에 붙였다 —
  `CacheEvictionListener`와 같은 `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)`
  + try-catch 패턴(이 체크의 실패가 이미 커밋된 재적재 결과나 캐시 evict 리스너에 영향을 주면 안 됨).
  실제 재적재 실행 로그로 "공백 0건"을 확인했다(정상 커버리지 케이스) — 화성/인천/광주전남 문제가
  실제로 해소됐다는 방증이기도 하다. "정교한 설계는 필요 없다"는 지시대로 완벽한 탐지가 아니라
  로그 신호 하나가 목적이다.

**완료 기준 재확인**: legal_district_code distinct 활성 sgg_cd 284개(확인) — 완료. 폐지된 32개는
삭제되지 않고 is_active=false로 보존(확인) — 완료. 화성시/인천/광주전남 관련 complex 레코드의
legal_dong_cd NULL 건수 대폭 감소는 — 위 premise 정정대로 그 컬럼 자체가 미사용이라 애초에 측정
대상이 아니었다(사용자 확인 후 범위 제외). 로컬 DB에서 재현 가능한 검증 스크립트/테스트 — 완료
(`LegalDistrictCodeLoaderMariaDbIT` 2건 + `RegionCoverageCheckerTest`/`RegionCoverageCheckListenerTest`/
`SigunguNormalizerTest`, 전체 `./gradlew test`(468) + `./gradlew integrationTest`(42) 그린 확인).
**남은 절차**: 4단계(소급 수집)를 착수할 때 이 절의 lawd_cd·기간 목록을 그대로 재사용하라.

### BAT-CLC-01 lawd_cd 커버리지 공백 소급 수집(백필) 완료 (2026-09-18)

위 절이 4단계로 미뤄뒀던 소급 수집을 이번 세션에서 실행해 lawd_cd 커버리지 공백 이슈 전체를 닫았다.
새 배치 프레임워크를 만들지 않고 기존 BAT-SCH-01(`BatchExecutionOrchestrator`)/BAT-CLC-01
(`RealEstateApiCollector`)을 그대로 재사용하는 1회성 실행 진입점만 추가했다 —
`LegalDistrictCodeReloadCommandLineRunner`와 같은 패턴.

**대상 36개 sgg_cd를 하드코딩 없이 도출한 방법.** `legal_district_code`는 자연키(`legal_dong_cd`) upsert라
재적재 시 "이전 목록" 스냅샷을 남기지 않는다 — 대신 `batch_log.lawd_cd`(정규 배치가 실제로 순회한 적
있는 코드 전체, 재적재 이전엔 사실상 이전 280개 목록과 동일)를 산 증거로 삼아 차집합을 취했다:

```sql
SELECT DISTINCT SUBSTRING(legal_dong_cd,1,5) FROM legal_district_code WHERE is_active=TRUE
  AND SUBSTRING(legal_dong_cd,1,5) NOT IN (SELECT DISTINCT lawd_cd FROM batch_log);
```

정확히 36건이 나왔고(화성 4/인천 4/전남광주통합 28), 역방향(현재 비활성인데 과거 batch_log엔 있던
코드)도 정확히 32건이라 재적재 세션의 "신규 36/폐지 32" 기록과 완전히 일치했다 — `BatchLogRepository.
findDistinctLawdCd()` 신설.

**백필 기간 — 실제 API 호출로 소급 재태깅 여부를 검증한 뒤 균일 기간으로 확정.** 신코드로 개편 발효일
이전 월(`LAWD_CD=41591`×`202601`, `LAWD_CD=12110`×`202603`)을 직접 질의한 결과 **resultCode=000과 함께
실거래 데이터가 정상 반환**됐고(추측이 아니라 실제 curl 호출로 확인), 반대로 구코드(`41590`/`46110`)는
항상 `totalCount=0`이었다 — data.go.kr이 과거 거래를 이미 현재 행정구역 기준으로 소급 재태깅해 뒀다는
뜻이라, 코드별로 정확한 개편일을 따로 관리할 필요가 없었다. 사전에 `trade`를 조회해 이 32개 구코드로
수집된 행이 과거에도 전혀 없었음을 확인해(중복 적재 위험 없음) 별도 dedup 로직도 추가하지 않았다 —
`DedupHashCalculator`가 매칭 성공 건은 애초에 sgg_cd를 해시에 넣지 않아(complex_id만 사용) 안전하고,
미매칭 건(sgg_cd가 해시에 들어감)도 과거 수집 이력 자체가 없어 문제가 성립하지 않았다. 결정:
**36개 전체를 2026-02~2026-09(8개월) 균일 적용.**

**`BackfillCommandLineRunner`(신규, `batch.scheduler`, `backfill-lawd-cd` 프로필 전용) — 시범 실행이
자기 자신의 대상 도출 로직을 오염시키는 자기참조 함정을 실행 중 발견·수정.** `BatchExecutionOrchestrator`
에 `orchestrateBackfill(List<String>, List<YearMonth>)`(기존 `orchestrate(YearMonth)`와 조합 순회 본체
`runCombinations()`를 공유, `TradeCollectionCompletedEvent`는 발행하지 않음 — 이 이벤트는 "정규 일일
사이클 완료"를 의미하는데 백필은 대응하는 단일 targetMonth가 없어 향후 리스너가 붙었을 때 오인시키지
않기 위함)을 추가해 재사용했다. `--code=<sggCd>`/`--month=<yyyyMM>`(반복 지정 가능) 인자로 부분 실행도
지원한다.

시범 실행(`41591`×`202602`)을 먼저 돌려 정상 적재(523건, 519건 매칭 99.2%)를 확인한 뒤 전체 실행에
들어갔는데, **시범 실행이 남긴 `batch_log` 행 때문에 전체 실행의 차집합 도출이 `41591`을 "이미 다룬
코드"로 오인해 35개 코드만 대상으로 잡는 자기참조 버그가 실행 중 발견됐다** — 시범 실행이 검증하려는
바로 그 파이프라인을 통해 `batch_log`에 흔적을 남기고, 그 흔적이 전체 실행의 입력 조건 자체를 바꿔버린
것이다. 즉시 `--month=` 인자를 여러 번 지정할 수 있도록 러너를 확장해(`41591`의 나머지 2026-03~09
7개월만 콕 집어 보충 실행) 메웠다 — 근본 수정(예: 도출 쿼리가 이번 실행 자신이 방금 남긴 로그를
제외하도록 타임스탬프 경계를 두는 것)은 하지 않았다, 이 도구는 상시 배선되지 않는 수동 유지보수
진입점이라 다음에 또 이 도구를 쓸 때 같은 함정을 인지하고 있으면 충분하다고 판단했다.

**실행 결과(로컬 DB 실측, `batch_log` 기준).**

| 실행 | 대상 | 결과 |
| --- | --- | --- |
| 시범 | `41591` × `202602` | 3건 전부 성공, `trade` 523건(519건 매칭, 99.2%) |
| 전체 | 35개 코드(시범이 다룬 `41591` 자동 제외) × 8개월 | 840건 전부 성공, 실패 0 |
| 보충 | `41591` × `202603~202609`(7개월) | 21건 전부 성공, 실패 0 |
| **합계** | **36개 코드 × 8개월** | **864건, 실패 0, 원본 API 처리 137,169건** |

36개 코드 중 35개는 실데이터가 있었고(코드당 3~15,778건), `12000`(전남광주통합특별시 시도 대표행)만
`trade` 0건이었다 — 이건 결함이 아니라 기존에 이미 문서화된 "시도 대표코드는 API가 시도 단위 조회를
지원하지 않아 항상 0건" 패턴과 정확히 같다(위 "lawd_cd 커버리지 공백" 절의 16개 시도 대표코드 케이스
참고).

**재매칭 검증(`TradeRematchRunner.rematchUnmatched()`).**

```
BAT-MAT-02 재매칭(1차, complex_id IS NULL) 완료: unchanged=17982, changed=0
```

`changed=0`은 백필된 거래가 이미 적재 시점(`TradeIngestionPipeline`)에 최신 매처 로직으로 정확히
매칭이 끝나 재매칭이 개선할 여지가 없었다는 뜻이다(설계대로 정상 — BAT-MAT-01/02는 적재 파이프라인
안에서 이미 수행된다). 교차검증: `17982 - 이번 36개 코드의 미매칭(7,424) = 10,558`, 위
"BAT-MAT-02 재매칭 — dedup_hash 미갱신 버그 수정" 절이 기록해 둔 "진짜 최종 수치"의 미매칭 건수
(10,558)와 정확히 일치해 계산 정합성을 재확인했다.

**최종 매칭 통계(그룹별).**

| 그룹 | 영향 complex 수(distinct) | trade 건수 | 매칭 성공(EXACT+SIMILAR) | 매칭률 |
| --- | --- | --- | --- | --- |
| 화성시 4개 신설구 | 376 | 28,132 | 25,969 (EXACT 17,611 / SIMILAR 8,358) | 92.3% |
| 인천 4개 신설구 | 328 | 17,242 | 15,475 (EXACT 14,530 / SIMILAR 945) | 89.8% |
| 전남광주통합특별시 | 1,599 | 48,092 | 44,598 (EXACT 41,127 / SIMILAR 3,471) | 92.7% |
| **합계** | **2,303** | **93,466** | **86,042** | **92.1%** |

**92.1%를 FR-2.5 목표(98.9%)와 직접 비교하면 안 된다** — 위 "BAT-MAT-02 실 배치 재실행 추가 조사"
절이 이미 지적했듯 그 98.9%는 lawd_cd 공백이 있는 상태에서 나온 수치가 아니라 애초에 도달 불가능한
목표였다. 대신 이 백필 직전 프로젝트 전체의 실측 기준선(**90.4%**, "BAT-MAT-02 재매칭 — dedup_hash
미갱신 버그 수정" 절의 최종 수치)과 비교하는 것이 맞는 기준이다 — 92.1%는 그 기준선과 비슷하거나
소폭 높아, 신규 지역이라고 매칭 품질이 특별히 나쁘지 않다는 뜻이다.

**미매칭 7,424건 breakdown — 새 원인 없음을 직접 검증(추정으로 덮지 않음).** `ComplexMasterMatcher`의
1차 필터링 로직(`SigunguNormalizer.normalize()` + `extractComparableDongRi()`)을 SQL로 그대로 재현해
분류했다:

| 그룹 | 후보없음(1차 필터링 0건) | 후보있음(지번/이름 매칭 실패) |
| --- | --- | --- |
| 화성시 4개 신설구 | 22 | 2,141 |
| 인천 4개 신설구 | 31 | 1,736 |
| 전남광주통합특별시 | 455 | 3,039 |
| **합계** | **508 (6.8%)** | **6,916 (93.2%)** |

508건("후보없음")을 sido/sigungu 조건 없이 `dong_ri` 원본값만으로 재검색해 검증한 결과(`SELECT * FROM
complex WHERE dong_ri='주봉리'` 등 3개 표본), **정규화 로직 오류가 아니라 해당 리/동 자체에 K-apt
단지 기본정보 등록 단지가 통째로 하나도 없는 것**으로 확인됐다(508건이 72개 distinct 리/동에 분포,
`complex.sido`도 이미 `'전남광주통합특별시'`로 갱신돼 있어 sido 표기 불일치도 아니다). **결론: 새로운
제3의 원인은 없다** — 508건("리/동 전체에 등록 단지 없음")과 6,916건("리/동은 있는데 특정 건물만
없음")은 둘 다 기존에 문서화된 "K-apt 단지 기본정보 커버리지 한계"가 granularity만 다르게 드러난
것이지 버그 A/B/C 같은 코드 결함이 아니다.

**완료 기준 재확인**: 36개 코드 각각 batch_log 최소 1건 이상 수집 성공(또는 정당한 0건, `12000`)—
완료. 재매칭 후 매칭률이 프로젝트 실측 기준선(90.4%) 근처(92.1%)로 나옴 — 완료. 미매칭 원인이 기존
카테고리와 같은 성격임을 실제 조회로 확인(추정 아님) — 완료. 위 "lawd_cd 커버리지 공백" 절의 우선순위
높음 태그는 이 절의 완료로 해제한다. **남은 절차 없음** — 이 이슈는 완전히 닫혔다.

## 개발 단계 (MVP 로드맵)

요구사항 정의서 9장 기준. 순서대로 진행하세요.

1. **1단계 (핵심 검증, 아파트 중심)** — 인증, 단지/실거래 배치·매칭·조회, 캐싱. 여기서 만든 배치·매칭·화면·API는 전부 아파트+연립다세대 겸용으로 설계되어 있습니다.
2. **2단계 (연립다세대 확장)** — **신규 프로그램 0개.** 1단계에서 만든 프로그램의 `housingType` 파라미터 범위를 `APT`에서 `APT, VILLA`로 넓히기만 하면 됩니다. 별도 클래스를 만들지 마세요.
3. **3단계 (지도 기반 조회)** — 지오코딩, 지도 화면/API.
4. **4단계 (개인화)** — 관심 매물/지역, 알림.
5. **5단계 (선택 범위)** — 통계 비교, 관리자, 소셜 로그인. 우선순위 "하".

## 프로그램 인벤토리

총 61개 프로그램(백엔드 39 + 프론트엔드 22)입니다 — 프로그램 목록서에 ID로 정의된 60개(백엔드 38 + 프론트엔드 22)에 BAT-USR-01(신규 제안, 목록서 미반영)을 더한 수치입니다. 새 클래스를 만들 때 대응하는 프로그램 ID를 확인하고, 커밋 메시지나 PR에 ID를 남기면 추적이 쉽습니다 (예: `[API-CPX-01] 단지 검색 엔드포인트 구현`).

- **API/SVC** (도메인당 1쌍, 10개 도메인): AUTH, USER, CPX, TRD, RGN, RCV, FAV, NTF, STT(5단계), ADM(5단계)
- **BAT** (11개, 파이프라인 순서): SCH-01, CLC-01, PRS-01, MAT-01, MAT-02, LOD-01, ERR-01, GEO-01, NTF-01, MAIL-01 + USR-01(탈퇴 계정 자동 파기 — 수집 파이프라인 밖의 독립 배치, 신규 제안)
- **COM** (8개): SEC-01, SEC-02, EXC-01, CACHE-01, LOG-01, CFG-01, RES-01, VAL-01
- **SCR/UIC** (프론트엔드 22개): UI 정의서 2.2절·4장 참조

전체 시그니처와 처리 로직은 프로그램 설계서 3~5장에 프로그램별로 정리되어 있습니다.

## 절대 하지 말 것

- [ ] `housing_type`에 `'OFFICETEL'`, `'DETACHED'` 넣기 — CHECK 제약이 막지만 코드 레벨에서도 만들지 마세요.
- [ ] `match_method`에 `'AGGREGATED'` 넣기.
- [ ] 오피스텔/단독다가구 화면·API·배치를 먼저 요청받지 않고 구현하기.
- [ ] FK `ON UPDATE`에 `CASCADE` 쓰기 — 항상 `RESTRICT`.
- [ ] 비밀번호 평문 저장/로그 출력 — 항상 BCrypt.
- [ ] `serviceKey`, 카카오 API 키, JWT 시크릿을 코드에 하드코딩 — 전부 환경변수(`COM-CFG-01`)로.
- [ ] CSV/xlsx를 UTF-8로 열기 — CP949입니다.
- [ ] 법정동코드를 숫자 타입으로 파싱 — 선행 0이 날아갑니다.
- [ ] `trade.dedup_hash`를 복합 UNIQUE로 바꾸기.
- [ ] `favorite_property.complex_id`를 nullable로 되돌리기 — MVP에서는 항상 NOT NULL입니다.
- [ ] 탈퇴 요청(API) 시점의 물리 삭제 — 항상 `status='WITHDRAWN'` 소프트 삭제. 유예기간 경과 후의 물리 삭제는 BAT-USR-01(`WithdrawnUserPurgeScheduler`)만 수행한다.
- [ ] 사용하지 않을 컬럼을 "나중을 위해" 미리 추가하기.

## 프론트엔드 구현 결정 사항

`frontend/homesense/`가 실제 Vite 프로젝트 루트다(`frontend/` 바로 아래가 아니다 — 최초 스캐폴딩 커밋 때부터 이 구조였고 지금은 그대로 유지한다). SCR-AUTH-01(로그인 화면)이 이 저장소의 첫 프론트엔드 화면 구현이라, 이후 화면(AUTH-02/03, HOME-01 등)이 재사용할 공통 기반도 이때 함께 확정했다.

| 항목 | 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| accessToken/refreshToken 저장 방식 | 프로그램설계서 미명시 | `localStorage`(`src/lib/tokenStorage.ts`) | SVC-AUTH-01 로그인 응답이 두 토큰을 httpOnly `Set-Cookie`가 아니라 JSON body로 그대로 내려주므로, 브라우저가 자동 관리하는 httpOnly 쿠키 저장은 백엔드 변경 없이는 애초에 선택지가 아니었다(이번 작업은 프론트엔드 전용 범위). 트레이드오프: XSS로 임의 스크립트 실행이 가능해지면 두 토큰 모두 탈취될 수 있다 — in-memory 저장이 더 안전해 보이지만, refreshToken 자체가 body로 내려오는 이상 어딘가엔 영속 저장해야 해 새로고침마다 재로그인을 강제하지 않고는 근본적으로 더 안전해지지 않는다. **더 안전한 방향(httpOnly refresh 쿠키 + in-memory access token)은 SVC-AUTH-01이 refreshToken을 Set-Cookie로 내려주도록 백엔드를 바꿔야 가능하다 — 향후 개선 과제로 남긴다.** |
| "이전 목적지 보존" 리다이렉트 처리 구조 | 프로그램설계서 6.1절에 시나리오만 언급, 구현 방식 없음 | `react-router`의 `location.state.from`을 `getRedirectPath()`(`src/features/auth/redirect.ts`)로 읽어 로그인 성공 시 `navigate(getRedirectPath(location), { replace: true })`. 로그인/토큰 상태 자체는 `AuthContext`+`useAuth()`(`src/features/auth/`)로 감싸 컨텍스트 하나로 로그인·로그아웃·인증 여부를 노출한다 | 이후 보호된 라우트(FAV/NTF/마이페이지 등 프론트 구현 시)가 401 시 `navigate('/login', { state: { from: location } })` 형태로 넘기기만 하면 이 화면이 그대로 되돌려준다 — 재사용을 염두에 둔 분리(`getRedirectPath`는 순수 함수라 단위 테스트도 쉽다). 다만 이번 작업 범위엔 그 생산자 쪽(실제 보호된 라우트)이 아직 없어, "state.from이 있을 때 정확히 그 경로로 돌아가는지"는 코드 경로로만 검증했고 실제 401→리다이렉트→로그인→복귀 전체 왕복은 보호된 라우트가 생기는 시점에 재확인이 필요하다. |
| 로컬 개발 환경의 CORS 우회 | 백엔드는 `application.properties`(local 프로필)에 CORS 설정이 없다(`application-prod.properties`만 `homesense.cors.allowed-origins`를 참조하고, 이를 실제로 소비하는 `CorsConfigurationSource`/`WebMvcConfigurer`는 코드베이스 어디에도 없다 — grep으로 확인) | 백엔드를 건드리지 않고 `vite.config.ts`의 dev server `proxy`(`/api` → `http://localhost:8080`)로 우회한다 | 이 작업은 프론트엔드 전용 범위라 백엔드에 CORS 설정을 새로 추가하지 않기로 했다 — Vite dev 프록시는 브라우저 입장에서 같은 오리진으로 보이게 하는 표준적인 방법이라 별도 백엔드 변경이 필요 없다. **다만 이 프록시는 dev 서버 전용이다 — 실제 배포 환경(프런트/백엔드가 다른 오리진)에서는 백엔드에 CORS 설정을 추가하거나 리버스 프록시로 같은 오리진에 두는 방향을 별도로 결정해야 한다.** |
| 스타일링 스택 | 가정 스택은 Tailwind CSS + shadcn/ui | Tailwind CSS v4(`@tailwindcss/vite` 플러그인, CSS-first `@theme` 설정, 별도 `tailwind.config.js` 없음)만 도입하고 shadcn/ui CLI 스캐폴딩(`components.json`, Radix 기반 프리미티브)은 도입하지 않았다 — 대신 `src/components/ui/`에 이 화면이 실제로 필요로 한 `Button`/`TextField`만 순수 Tailwind로 직접 작성 | Figma 디자인이 매우 구체적인 커스텀 값(24px/14px/16px radius, 정확한 hex 컬러, 정확한 px 폰트 크기 등)이라 shadcn 기본 프리미티브를 오버라이드하는 비용이 오히려 크고, 화면이 하나뿐인 시점에 컴포넌트 라이브러리 전체를 스캐폴딩하는 것은 과설계로 판단했다. 이후 화면이 늘어나 재사용 가능한 variant 체계가 실제로 필요해지면 그때 shadcn CLI를 도입해도 이 두 컴포넌트를 이관하는 비용은 작다. |
| 한글 폰트(Pretendard Variable) 로딩 | Figma 디자인 토큰이 `Pretendard Variable`을 지정 | 외부 CDN 링크가 아니라 npm 패키지 `pretendard`를 설치해 `pretendardvariable-dynamic-subset.css`를 `index.css`에서 `@import`(유니코드 범위별로 쪼개진 woff2를 브라우저가 실제 사용하는 문자만 골라 받는 방식) | 런타임에 외부 네트워크(Google Fonts 등에는 애초에 Pretendard가 없다)에 의존하지 않고 Vite 빌드에 포함시켜 오프라인·사내망 환경에서도 깨지지 않게 했다. |
| AUTH-01 아이콘 자산 | Figma 노드(19:5679/20:5782)가 제공하는 SVG를 그대로 써야 함(원본 재작성 금지 원칙) | 홈 로고 아이콘·눈(표시) 아이콘·Google "G" 아이콘·에러 경고 아이콘 4종은 Figma가 내려준 정확한 path 데이터를 그대로 React 컴포넌트로 옮겼다(`src/components/icons/`). **단, 비밀번호 "숨김" 토글 아이콘(`EyeOffIcon`)은 두 Figma 프레임 모두 "표시" 상태 하나만 캡처하고 있어 원본이 없다 — `EyeIcon`과 같은 스타일(16x16, stroke 1.33333, round cap)로 직접 작성한 손 그림이며 Figma 원본이 아니다.** | 두 정적 프레임(정상/에러)만 받았고 인터랙션 상태(눈 아이콘 토글의 두 번째 상태)는 애초에 디자인에 존재하지 않아 발생한 불가피한 예외다. |
| accessToken 자동 갱신 인터셉터 | "이후 화면 작업에서 별도로 진행" (프롬프트 명시 범위 밖) | `src/lib/httpClient.ts`에 axios 인스턴스만 분리해두고 인터셉터는 아직 추가하지 않았다 | 보호된 라우트가 실제로 생기는 시점(FAV/NTF 등 인증 필요 화면 구현 시)에 이 인스턴스에 `401`→`/api/auth/refresh` 인터셉터를 끼워 넣는다. **2026-09-28: 백로그 최우선(문서 앞부분 "백로그" 절 1번)으로 올렸다 — 재발급은 반드시 `session.ts`의 Web Locks 경로를 재사용한다.** **2026-09-29 처리완료: "401 자동 재발급과 요청 timeout" 절.** |

**로컬 개발 환경 이슈(해결됨, 기록용) — Redis 미기동 시 로그인이 401 대신 500을 반환한다.** SCR-AUTH-01 구현 직후 실제 백엔드(`localhost:8080`)에 대해 틀린 비밀번호로 로그인을 시도했더니 `INTERNAL_SERVER_ERROR`(500)가 나왔다 — `AuthService.login()`이 자격 증명을 확인하기 전에 `LoginAttemptService.isLocked()`(로그인 실패 잠금, Redis 키 `login:fail:{email}` 조회)를 먼저 호출하는데, 그 세션 환경엔 Redis가 아예 떠 있지 않아 여기서 커넥션 예외가 나 500으로 샜다. 원인은 두 단계였다: (1) 로컬에 Redis 자체가 없었고, (2) Docker로 Redis를 띄운 뒤에도 컨테이너가 `-p 6379:6379` 없이 실행돼 컨테이너 내부에만 포트가 열려 있어(`docker ps`에 `6379/tcp`만 보이고 `0.0.0.0:6379->6379/tcp`가 없었다) 호스트에서 실행 중인 백엔드가 여전히 연결하지 못했다. 컨테이너를 포트 매핑과 함께 재시작(`docker run -d -p 6379:6379 redis:latest`)한 뒤 실제 백엔드로 재검증 — `POST /api/auth/login`이 정확히 `401 INVALID_CREDENTIALS`("비밀번호가 일치하지 않습니다")를 반환하고, 로그인 화면의 인라인 에러 UI도 Figma 에러 상태와 동일하게 렌더링됨을 확인했다. `AuthService.login()` 코드 자체는 처음부터 정상이었다 — 로컬 인프라(Redis) 문제였을 뿐 백엔드 결함이 아니었다는 최초 판단이 맞았다. 다음에 이 저장소를 새로 체크아웃해 로그인이 500만 낸다면, Redis 컨테이너가 떠 있는지뿐 아니라 포트가 호스트에 실제로 게시됐는지(`docker ps` 출력에 `0.0.0.0:6379->`가 있는지)까지 확인하라.

### SCR-AUTH-02 구현 결정 사항

AUTH-01이 만든 공통 기반(`httpClient`, `tokenStorage`, `AuthContext`/`useAuth`, `Button`/`TextField`)을 그대로 재사용했고, 회원가입 화면 자체가 필요로 한 요소(이메일 중복확인, 비밀번호 정책 체크리스트, 약관동의 체크박스, 성공/실패 FieldHint)는 이번에 컴포넌트 단위로 분리해 이후 화면(MY-05 등)이 재사용할 수 있게 했다(`src/components/ui/FieldHint.tsx`, `PasswordChecklist.tsx`, `Checkbox.tsx`, `Input.tsx`, `src/components/icons/CheckIcon.tsx`).

| 항목 | 프롬프트/설계서 전제 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| **닉네임 "특수문자 화이트리스트" — 전제 자체가 틀렸다(단, 근거 없는 지어낸 말은 아니었다 — 아래 참고)** | 작업 지시가 "닉네임의 특수문자 제한 허용 문자셋은 화이트리스트 방식으로 구현되어 있다(CLAUDE.md 학습사항 참고)"고 전제했다 | 실제 `NicknameValidator.java`를 읽어보면 문자 집합 검사는 **아예 없다** — `codePointCount`로 2~12자 길이만 검사한다(`@NotBlank` 없이 null/공백도 자체 처리). CLAUDE.md 어디에도 이런 학습사항이 없었다(grep으로 확인). 프론트도 길이만 검사하고 문자 화이트리스트를 만들지 않았다(`src/features/auth/validation.ts`의 `isValidNickname()`) | 지시받은 전제를 그대로 믿지 않고 실제 백엔드 코드(`common/validation/NicknameValidator.java`)를 직접 열어 확인했다 — 존재하지 않는 화이트리스트를 프론트에서 새로 만들었다면 백엔드가 허용하는 값을 프론트가 부당하게 거부하는 조용한 회귀가 됐을 것이다. **후속 확인(지성 지적, 이후 확정): 이 전제가 허공에서 나온 게 아니라 프로그램설계서 3.2절이 실제로 "특수문자 제한 규칙"을 언급하고 있었다** — 5.8절(COM-VAL-01 정의)과 3.2절(SVC-USER-01.updateUser 처리 로직)이 서로 다른 말을 하는 문서 내 불일치였다. 위 SVC-USER-01 절의 "닉네임 '특수문자 제한'" 행에서 최종 확정됐다: 3.2절 문구는 COM-VAL-01이 5.8절로 확정되기 전 초안 단계의 낡은 서술이고, 5.8절(2~12자, 문자 제한 없음)이 맞는 스펙이다 — 근거는 요구사항정의서 FR-1.1과 UI정의서 5.1절 예외처리표 어디에도 "특수문자 제한"이 등장하지 않고 3.2절 한 곳에만 고립돼 있다는 교차 확인. 즉 프론트가 문자 화이트리스트 없이 길이만 검사하는 지금 구현이 그대로 맞는 스펙이라 코드 변경은 필요 없다. |
| `EmailCheckResponse` 필드명 | 문서에 명시 없음 | `duplicate`(boolean) — `available`이 아니다 | `EmailCheckResponse.java`(`record EmailCheckResponse(boolean duplicate)`) 실제 코드 확인. |
| `SignupResponse` 응답 모양 | 프롬프트가 `{accessToken, refreshToken, expiresIn, user: {...}}`(중첩 `user` 객체)로 예시를 들었다 | 실제로는 `accessToken/refreshToken/expiresIn/userId/email/nickname`이 전부 평탄하게 나열된다(중첩 객체 없음) — `SignupResponse.java` 확인. 프론트 타입(`features/auth/types.ts`)도 이 실제 모양대로 정의했다 | 예시 JSON을 그대로 믿지 않고 DTO 소스를 확인 — 중첩 `user.xxx`로 접근하는 코드를 짰다면 런타임에 전부 `undefined`가 됐을 것이다. |
| 클라이언트 비밀번호 정책 미러링 | "실제 코드(어노테이션 구현체)를 직접 확인해서 정확히 동일한 규칙으로 맞출 것" | `PasswordValidator.java`를 그대로 미러링(`features/auth/validation.ts`의 `evaluatePassword()`): 8자 이상(UTF-16 code unit 길이), ASCII 영문/숫자/특수문자(`SPECIAL_CHARS` 화이트리스트 문자열 그대로 복사) 3종 명시적 판정, **72바이트(UTF-8) 상한까지 포함**. 72바이트 상한은 Figma 체크리스트 4항목엔 없는 5번째 숨은 규칙이라, 위반 시 체크리스트에 별도 칸을 추가하지 않고 `isValid`만 false로 두어 제출 버튼을 막는다(초과하는 입력 자체가 극히 드문 엣지케이스라 UI를 새로 그리지 않았다) | `PasswordValidator`의 주석이 이미 "letter도 digit도 아니면 special로 소거하면 공백/한글/이모지가 특수문자로 오인된다"는 함정을 경고하고 있어, 프론트도 소거법이 아니라 3개 집합을 각각 명시적으로 판정했다. |
| 닉네임 유효성 실패 문구 | "정확한 문구는 UI정의서에 명시 없음 — 판단 필요" | `ValidNickname.message()`의 백엔드 기본 메시지를 그대로 재사용: "닉네임은 2자 이상 12자 이하여야 합니다" | 프론트 검증이 먼저 막아 평소엔 노출될 일이 없지만, 400 방어 응답이 오는 경우에도 문구가 어긋나지 않도록 서버 메시지 원문과 동일하게 맞췄다. |
| 클라이언트 이메일 형식 정규식 | 없음(신규 판단) | `/^[^\s@]+@[^\s@]+\.[^\s@]+$/` — Hibernate Validator의 `@Email` 내부 구현과 문자 단위로 동일하지 않은 실용적 근사치 | `GET /check-email`은 서버 쪽에 `@Email` 검증이 없어(파라미터에 애노테이션 없음) 형식이 이상한 문자열도 그대로 조회해버린다 — 그 결과가 우연히 "사용 가능"으로 나와도 실제 가입 제출(`POST /signup`은 `@Email` 적용)에서 걸러지므로 최종 권위는 항상 서버에 있다. 프론트 정규식은 "이 값을 API에 보낼 가치가 있는 모양인가"만 거르는 게이트라 완벽히 동일할 필요가 없다고 판단했다. |
| 약관동의 체크박스 — **완결 필요** | "서버 SignupRequest에 동의 여부 필드가 있다는 근거가 없으므로 서버로 전송하지 않는다" | `POST /signup` 바디에 체크박스 값을 포함하지 않는다 — 클라이언트 전용 게이트(가입 버튼 활성화 조건 중 하나)로만 쓴다 | 실제 서비스로 전환할 때는 동의 시각·약관 버전을 서버에 기록해야 할 가능성이 높다 — `SignupRequest`에 필드를 추가하고 이 프론트 코드도 함께 바꿔야 한다. |
| **"만 14세 이상입니다" 확인 체크박스 신설(2026-09-19, 지성 확정) — UI정의서 v2.1 AUTH-02에 정의 없는 판단, Figma에도 없음** | 없음 — UI정의서 AUTH-02 구성요소·이벤트 정의·예외 처리 표 어디에도 연령 확인 항목이 없고, Figma AUTH-02 프레임(데스크톱 SignupCard `21:5997` 포함 6개 프레임)에도 구분선 아래 약관 체크박스 행 하나뿐이다(메타데이터로 직접 확인). 개인정보 보호법 제22조의2(만 14세 미만 아동은 법정대리인 동의 필요) 대응이 출처다 | 약관 동의 체크박스 **바로 위**에 필수 체크박스 1개("만 14세 이상입니다 (필수)")를 추가했고, `agreeToTerms`와 똑같은 **클라이언트 전용 게이트**로 취급한다 — 서버로 전송하지 않고(`POST /signup` 바디는 여전히 email/password/nickname 3필드, Playwright로 요청 바디 키 확인), `SignupRequest`/DB 스키마는 건드리지 않았다. 가입 버튼 활성화 조건은 기존 5개(이메일 중복확인 통과+비밀번호 정책+비밀번호 일치+닉네임 유효+약관 동의)에 이 체크를 더한 6개를 모두 만족해야 하고, 체크 해제 시 다시 비활성화된다. 컴포넌트는 기존 `Checkbox`와 그 `pt-1` 행 래퍼를 그대로 재사용해 새 디자인 토큰·간격 값을 만들지 않았다(두 행 사이 8px = 컨테이너 `gap-1` + 행 `pt-1`, 기존 값의 합) | 서버 저장은 `SignupRequest`·`user` 스키마 변경(백엔드+DB)이 필요한 별도 결정이고, 자기 확인 체크박스는 저장해도 그 값을 읽어 검증·집행하는 소비자가 지금 없어 "사용하지 않는 컬럼 추가 금지" 원칙과 충돌한다. **무효화 조건 — 아래 중 하나라도 도입되면 이 판단을 재검토하고 SCR-LEGAL-01 6번 문구(`privacyPolicySections.tsx`)도 반드시 함께 갱신하라:** (1) 서버 측 연령 검증·저장(예: 동의 이력을 감사 목적으로 남기는 컬럼), (2) 본인인증(PASS 등)이나 생년월일 수집, (3) 법정대리인 동의 절차. **(당시 알려진 한계였던 포커스 링 부재는 같은 날 후속 작업으로 해소됨 — 바로 아래 행 참고.)****(2026-09-21 갱신 — 이 행의 "서버로 전송하지 않고 … `SignupRequest` 필드 추가 금지" 판단 중 연령 확인 부분은 뒤집혔다: `ageConfirmed`는 이제 요청에 실려 서버가 `@NotNull` + `@AssertTrue`로 검증하고 저장 없이 폐기한다. `agreeToTerms`는 이 행의 원래 결정(클라이언트 전용) 그대로다. 위 무효화 조건 (1)에 해당하는 변경이며 6항 문구도 함께 갱신했다 — SCR-LEGAL-01 "연령 확인 절차" 행 6단계 참고.)** |
| **공유 `Checkbox` 키보드 포커스 표시 추가(2026-09-19, WCAG 2.4.7, 지성 요청)** | 없음 — Figma에는 체크박스 포커스 상태가 정의돼 있지 않다(전체 메타데이터에서 `focus`/`포커스`가 이름에 든 노드는 이메일 라벨 텍스트뿐, `Checkbox`/`CheckboxRow` 노드도 기본·체크 상태만). 기존 코드의 포커스 스타일은 `Input.tsx`의 `focus:border-brand` 계열 테두리색 변화뿐이고 링 토큰은 없다 | `Checkbox.tsx`의 sr-only input에 `peer`를 달고 형제 시각 박스에 `peer-focus-visible:ring-2 peer-focus-visible:ring-brand peer-focus-visible:ring-offset-2`를 적용했다. `:focus-visible`이라 **Tab 등 키보드 포커스에서만** 링이 나타나고 마우스 클릭(박스·라벨 텍스트 모두)에서는 나타나지 않는다. 색은 기존 `brand` 토큰, 두께·오프셋은 Tailwind 기본 스케일이라 새 토큰은 만들지 않았다. box-shadow라 레이아웃이 밀리지 않고(박스 rect 불변 확인), 체크 상태(브랜드 배경)에서도 흰 오프셋 덕에 링이 구분된다. 이 컴포넌트의 소비처는 `SignupPage`의 2곳(`#confirmedAge14`, `#agreeToTerms`)뿐이다(grep 확인) | Figma에 값이 없어 "Figma 우선" 규칙을 적용할 수 없었고, 접근성 요건(WCAG 2.4.7)은 디자인 부재와 무관하게 충족돼야 해 기존 토큰 범위 안에서 구현했다 — **Figma 갱신 필요**(체크박스 포커스 상태 정의: 링 색 brand, 두께 2px, 오프셋 2px 흰색). Playwright(desktop/tablet/mobile 각 17개 = 51개)로 Tab 시 링 표시·Space 토글 중 유지·포커스 이동 시 링 이동·마우스 클릭 시 미표시·체크 상태 포함·가로 스크롤 없음을 검증했다. **범위 밖으로 남긴 것:** 앱의 다른 포커스 가능 요소(Button, Link, 아이콘 버튼 등)도 `focus-visible` 표시가 없다 — 이번 요청은 `Checkbox`에 한정돼 손대지 않았다. |
| "이용약관"/"개인정보처리방침" 링크 — **완결 필요** | "실제 문서·화면 ID가 아직 없으므로 클릭 가능한 링크로 만들지 않는다" | `<Link>`/`<a>`가 아니라 순수 `<span>`으로 렌더링(클릭 핸들러 없음) — 색상(`text-brand`)과 밑줄(모바일·태블릿만, `underline lg:no-underline`)만 Figma대로 재현해 시각적으로는 링크처럼 보이되 실제로는 비활성이다 | 실제 약관/정책 페이지가 생기면 이 두 `<span>`을 `<Link to="/terms">`/`<Link to="/privacy">`로 교체하기만 하면 된다. |
| `TextField`의 `error: boolean` → `status: 'default'\|'success'\|'error'` 리팩터 | 없음(AUTH-01은 이분법 에러만 필요했음) | AUTH-02가 성공(초록)/실패(빨강)/기본 3단 상태를 필요로 해 `TextField`를 확장하는 대신, 입력 상자 자체(테두리 색상 로직 포함)를 `Input`(`components/ui/Input.tsx`)으로 분리하고 `TextField`는 "label + Input" 조합만 담당하게 재구성했다. `LoginPage`도 새 `status` prop을 쓰도록 함께 바꿨다(하위 호환 shim 없이 — 사내 컴포넌트라 두 소비자를 그냥 함께 고치는 쪽이 더 간단하다고 판단) | 이메일 필드가 "중복확인" 버튼과 가로로 나란히 배치돼야 해서(Figma), 라벨까지 포함한 `TextField` 한 덩어리로는 이 레이아웃을 표현할 수 없었다 — 입력 상자만 따로 쓸 수 있어야 했다. |
| `CheckIcon` 하나로 3곳 재사용 | 없음(신규 판단) | FieldHint 성공 아이콘(14px, `#00a63e`), 비밀번호 체크리스트 충족 아이콘(12px, `#00c950`), 약관 체크박스 체크마크(12px, 흰색) — Figma가 내려준 세 SVG가 전부 동일한 체크마크 도형을 스케일·색상만 바꾼 것이었다(좌표를 14/12배 하면 정확히 일치) | 세 파일로 쪼개지 않고 `strokeWidth` prop 하나로 통일 — G4(제공된 힌트 우선순위) 적용 시 "동일 글리프면 재사용"이 3개의 근사 중복 아이콘보다 나은 선택이라고 판단했다. |
| AUTH-01 소급 정정 — 카드 padding/breakpoint | 없음(회고) | `AuthLayout`(`components/layout/AuthLayout.tsx`)으로 배경+카드 셸을 공통화하면서, AUTH-01 최초 구현 당시 Figma 모바일 목업이 없어 추정했던 모바일 padding(24px, `sm:` 640px 브레이크포인트)을 이번에 Figma로 확정된 AUTH-02 수치(28px, `md:` 768px 브레이크포인트 — tablet은 desktop과 동일한 40px/16px)로 두 화면 모두 맞췄다 | 프롬프트가 "카드/배경 등 전역 토큰은 AUTH-01과 동일하다"고 명시했는데, 실제로는 AUTH-01 쪽이 근거 없는 추정치였다 — 이번에 확인된 진짜 값으로 두 화면을 통일하는 것이 "같은 토큰"이라는 전제를 사후적으로 참으로 만드는 유일한 방법이었다. |
| 이메일 중복확인의 stale 응답 경쟁 상태 | 없음(PR 리뷰 지적, Codex) | 이메일을 A→B로 빠르게 바꿔가며 중복확인을 두 번 트리거하면, 먼저 보낸 A의 느린 응답이 나중에 도착해 이미 B로 갱신된 최신 상태를 덮어쓸 수 있었다 — 요청마다 증가하는 카운터(`emailCheckRequestId`, `useRef`)로 응답이 도착했을 때 "여전히 최신 요청인지"를 확인해 아니면 폐기하고, 값이 바뀌는 순간(`resetEmailCheck`)에도 카운터를 먼저 올려 그 시점에 진행 중이던 요청을 전부 폐기 대상으로 만든다 | Playwright로 정확히 이 경쟁 상태(느린 A, 빠른 B)를 재현해 수정 확인. **일반화 메모(지성 지적, 지금 당장 처리할 필요 없음):** 이 "느린 요청이 늦게 도착해 최신 상태를 덮어쓰는" 패턴은 blur/입력 기반 비동기 검증 전반(예: SRCH-01 지역 자동완성, 다른 화면의 실시간 검증)에서 재현될 수 있는 일반적인 문제다. 지금은 `emailCheckRequestId` 카운터 로직이 `SignupPage.tsx`에 로컬로 박혀 있는데, 이 패턴이 두 번째로 필요해지는 시점에 `useLatestRequest` 같은 공용 훅으로 뽑아 이 로직을 재사용하는 방향을 검토하라 — 위 "재사용 가능하게 분리한 컴포넌트" 원칙과 같은 결이다. 지금 미리 뽑아두는 것은 아직 두 번째 소비자가 없어 과설계로 판단해 하지 않았다. |
| **서버 제출 에러 vs 클라이언트 검증 결과 — 표시 우선순위 규칙(UI정의서 미명시, 신규 판단)** | 없음 — UI정의서 어디에도 "같은 필드에 서버 에러와 클라이언트 실시간 검증 결과가 동시에 존재할 때 무엇을 먼저 보여줄지"에 대한 규칙이 없다 | **서버가 반환한 필드별 에러(`serverFieldErrors`)가 항상 클라이언트 쪽 실시간 검증/조회 결과보다 우선 표시되고, 그 필드의 값이 바뀌기 전까지 유지된다.** `email`/`password`/`nickname` 세 필드 모두 `register(field, { onChange: ... })`에서 공용 `clearServerFieldError(field)`를 호출해 값이 바뀌는 즉시 해당 필드의 서버 에러를 지운다(이메일은 `emailCheck` 상태 초기화까지 겸하는 `handleEmailChange`가 별도로 감싼다) | 가입 버튼 클릭처럼 사용자가 "이 정도면 됐다"고 확신한 시점에 서버가 실패를 알렸다면, 그 직후 클라이언트 쪽의 낙관적인 실시간 판정(중복확인 "사용 가능" 캐시 등)이 그 실패를 가려선 안 된다고 판단했다 — 서버 판정이 최종 권위이므로 사용자가 실제로 값을 바꿔 문제를 해결하기 전까지는 실패 상태를 계속 보여주는 쪽이 안전하다. **처음엔 이 규칙을 이메일 필드에만 적용했다가(PR 리뷰에서 옛 409 에러가 새 이메일 확인 성공 후에도 남아있던 버그로 지적돼 수정), 이 판단표 항목을 쓰면서 재점검하다가 `password`/`nickname`은 서버 에러를 우선 표시는 하면서도 값이 바뀌어도 지우는 로직 자체가 아예 없다는 걸 뒤늦게 발견했다 — 같은 결함이 두 필드에 그대로 남아있었던 것이다.** 발견한 김에 세 필드 모두 같은 `clearServerFieldError` 헬퍼를 쓰도록 통일해 그 자리에서 함께 고쳤다(Playwright로 password/nickname 각각 재현·검증). 앞으로 비슷한 서버/클라이언트 에러 병존 상황이 새 화면에서 생기면 이 우선순위(서버 우선, 값 변경 시 초기화)를 처음부터 전체 필드에 적용하라 — 한 필드에서만 고치고 넘어가면 이번처럼 나머지 필드에 같은 결함이 남는다. |
| **서버가 반환한 필드 오류 중 폼 필드로 매핑되지 않는 키의 표시(2026-09-21, 신규 판단)** | 없음 — UI정의서·설계서에 "폼에 대응 입력이 없는 필드의 서버 오류를 어떻게 보일지"에 대한 규정이 없다 | `SignupPage.onSubmit`의 400 처리에서 `fieldErrors` 중 `email`/`password`/`nickname`이 아닌 키(예: `ageConfirmed`)의 `message`를 기존 폼 단위 에러(`formError`, 제출 버튼 아래 `FieldHint`)로 그대로 보여준다. **`ageConfirmed` 전용 처리가 아니라 signup 폼 전체의 미매핑 키에 적용된다.** 같은 문구가 반복되지 않게 미매핑 message끼리는 중복을 제거하고, 이미 필드 아래 인라인으로 보이는 message와 같은 것도 폼 단위에서는 뺀다. 미매핑 오류가 여러 건이면 공백으로 이어 한 줄로 표시한다. | 이전에는 매핑된 오류가 하나도 없으면 `setServerFieldErrors({})` 후 return해 사용자에게 아무 표시도 없이 제출만 조용히 실패했다. 체크박스 게이트를 우회한 요청에서만 닿는 경로라 화면 조작으로는 재현되지 않지만, 서버에 새 검증 필드가 생길 때마다 같은 침묵 실패가 재발할 구조였다. 새 UI를 만들지 않고 기존 `formError` 경로를 재사용했다. **검증(2026-09-21, `signup-age-server-error-check`, 3 뷰포트 33/33):** (1) 기존 동작 불변 — 매핑된 오류만 오면 인라인 1회, 폼 단위 오류·범용 폴백 문구 없음(직전 제출의 미매핑 오류는 재제출 시 사라짐); (2) 매핑+미매핑이 함께 오면 각각 1회; (3) 미매핑 두 건이 같은 message면 **처음엔 한 요소 안에 2회 표시되는 결함이 있었다** — 요소 수만 세던 첫 검사는 통과해 버렸고 렌더링된 텍스트의 등장 횟수로 검사를 바꾼 뒤에야 드러나 중복 제거로 수정했다(`db9c4be`); (4) 매핑 message와 미매핑 message가 같아도 1회. 폼 단위 에러는 다음 제출에서만 초기화되고 값을 편집해도 지워지지 않는다(기존 범용 오류 문구와 같은 동작이며 이번에 바꾸지 않았다). **무효화 조건:** 프로젝트 공통 서버 오류 표시 규약(공통 토스트·에러 배너·필드 매핑 규칙 등)이 정해지면 이 화면도 그 규약에 맞춘다. |

### SCR-AUTH-03 구현 결정 사항 (2026-09-23) — 비밀번호 찾기, 신규 제안(프로그램목록서 미반영)

백엔드(`feature/backend/find-password`, 커밋 `ac445de`, `develop`에 이미 머지됨)가 확정한 3개
엔드포인트(`POST /password-reset-request`, `GET /password-reset/validate-token`, `POST
/password-reset`)를 그대로 소비한다. AUTH-01/02가 만든 공통 기반(`AuthLayout`, `Input`/`TextField`/
`Button`/`FieldHint`, `PasswordChecklist`, `evaluatePassword`/`isValidEmailFormat`)을 새 컴포넌트
분리 없이 그대로 재사용했다 — `PasswordChecklist`는 이미 AUTH-02 시점에 공유 컴포넌트로 분리돼 있어
프롬프트의 "아직 분리 안 됐으면 분리 권장" 조건 자체가 성립하지 않았다.

| 항목 | 전제/설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| 라우트 경로 | `AppRouter.tsx`엔 `/forgot-password`(`PlaceholderPage`)가 이미 있었다 | `/password-reset`으로 교체(플레이스홀더 라우트 삭제, `LoginPage`의 "비밀번호 찾기" 링크도 함께 변경) | 백엔드가 발송하는 재설정 링크가 `{baseUrl}/password-reset?token=...` 형식으로 고정돼 있다(AUTH-03 프론트 프롬프트 1절, 확정값) — 프론트 라우트가 이와 다르면 이미 발송된 메일의 링크가 전부 깨진다. |
| 1단계 완료 후 재시도 경로 — **프롬프트에 없던 추가** | 프롬프트 3절은 "안내 문구로 전환"만 요구, 그 뒤 무엇을 보여줄지는 명시 없음 | 완료 화면에 "다른 이메일로 다시 시도" 버튼을 추가해 클릭 시 폼으로 되돌아간다(이메일 오타로 다른 계정에 보냈거나 재전송이 필요한 경우의 유일한 탈출구) | 뒤로 가기 외에 경로가 없으면 오타를 낸 사용자가 화면 새로고침(입력값 유실) 말고는 재시도할 방법이 없었다 — 쿨다운(60초)이 남용을 이미 막고 있어 버튼을 열어둬도 안전하다. |
| 2단계 게이팅 — 사전 검증(peek) 실사용 | 프롬프트 1절이 "구현됨 — 선택 항목이 채택됨"이라고 이미 확정해 뒀다 | `useEffect`에서 `GET .../validate-token`을 마운트 시 1회 호출해 `validating`/`valid`/`invalid` 3단 상태로 폼 렌더링을 게이팅한다. 실패 시 서버 `error.message`(`InvalidResetTokenException` 기본 문구)를 그대로 보여준다 | 프롬프트가 이미 구현 여부를 확정해 준 항목이라 재판단하지 않고 그대로 따랐다 — 이 API 없이 폼부터 보여주면 만료된 링크로도 새 비밀번호를 입력하게 한 뒤에야(제출 시점에) 실패를 알리게 돼 UX가 나빠진다. |
| 제출 시점 `INVALID_RESET_TOKEN` — 사전 검증과 같은 화면으로 통일 | 프롬프트 예외표는 "토큰 만료/재사용/미존재"를 한 행으로만 다룬다 | `resetPassword()` 호출이 400을 받으면(사전 검증 통과 이후 시간 경과, 다른 탭/기기에서 먼저 소비된 경우 등) 폼을 그대로 두지 않고 `validate-token` 실패와 같은 "링크가 만료되었습니다" 화면으로 전환한다 | 백엔드가 두 엔드포인트 모두 같은 `INVALID_RESET_TOKEN` 하나로 사유를 통일해 뒀으므로(`InvalidResetTokenException.java` javadoc — 사유별 응답 분기 자체가 보안상 의도적으로 없다) 프론트도 두 실패 지점을 같은 화면 하나로 처리하는 것이 일관적이다. |
| 에러 표시 — 429만 서버 문구, 그 외는 범용 문구 | LoginPage는 401/403/429 세 상태 코드를 인라인 표시 대상으로 삼는다(`INLINE_ERROR_STATUS_CODES`) | 1단계는 429(`PASSWORD_RESET_COOLDOWN`)만 서버 message를 그대로 보여주고, 그 외 실패는 전부 `GENERIC_ERROR_MESSAGE`다 | 이 엔드포인트는 계정 존재 여부와 무관하게 항상 같은 200을 반환하도록 설계돼 있어(`AuthController.java` javadoc) 429 외의 4xx가 정상적으로 발생할 여지가 없다(이메일 형식은 이미 클라이언트에서 막아 제출 자체가 안 됨) — LoginPage처럼 여러 상태 코드를 분기할 이유가 없었다. |
| 로컬 검증의 한계 — "유효한 토큰" 경로는 route 목킹으로만 검증됨 | 프롬프트 7절이 요구하는 전체 플로우(1단계→발송→2단계 진입→폼→변경 완료)를 실제 백엔드로 왕복 검증하는 것이 이상적이다 | 1단계(발송/쿨다운)·2단계 무효 토큰 경로는 로컬에서 도는 실제 백엔드(포트 8080)+Redis로 검증했다. **2단계 "유효한 토큰" 경로(비밀번호 정책 체크리스트, 확인값 불일치, 제출→변경 완료)는 `GET validate-token`/`POST password-reset` 두 엔드포인트만 Playwright `page.route()`로 목킹해 검증했다** — 로컬 환경엔 AWS SES 자격 증명이 없어 실제로 발송된 메일에서 원문 토큰을 받아올 방법이 없기 때문이다(백엔드는 토큰을 해시로만 저장하고 평문은 메일 본문에만 실린다). | 검증 스크립트: `auth03-password-reset-check.mjs`(22/22, 저장소 밖 `C:\Users\super\homesense-e2e-scripts\`에 보관 — CLAUDE.md SCR-LEGAL-01 절의 "보존 현황"과 같은 이유로 npx 캐시가 아니라 이 경로에 둔다). **완결 필요(낮은 우선순위)** — 실제 백엔드가 SES로 메일을 보낼 수 있는 환경(실 자격 증명 또는 로컬 메일 캐처)이 갖춰지면, 목킹 없이 1→2단계 전체를 실제로 왕복 검증하라. |

**완결 필요(프롬프트 8절, 이번 세션 범위 밖)** — 요구사항정의서 v3.0에 AUTH-03용 FR ID(예: FR-1.5)
부여, 프로그램목록서 3장 총괄표·8.2절 FR 추적표 신규 등록. 백엔드 쪽 프로그램설계서 반영은 이미 별도
백엔드 세션에서 진행 중인 것으로 보인다(CLAUDE.md SVC-AUTH-01 "AUTH-03 비밀번호 찾기" 절 참고, 문서
반영은 claude.ai 세션에서 별도 처리한다고 기록돼 있다) — 프론트 쪽(UI정의서 5.1절은 이미 있음, FR ID·
프로그램목록서만 비어 있음)은 이 항목으로 남긴다.

**Figma 미조회 — 최초 판단은 틀렸다, 이후 실제 대조로 확인·수정 완료(2026-09-23, 같은 날 후속 작업).**
최초 구현 시점엔 "AUTH-01/02가 확립한 공유 컴포넌트만으로 구성되고 텍스트는 프롬프트에 다 명시돼
있어 Figma를 다시 조회할 필요가 없다"고 판단하고 건너뛰었다 — 하지만 실제로 Figma AUTH-03 프레임
(`bStwE4wZ5kXm7K6fBMeg6Z`, 데스크톱/모바일/태블릿 × 4상태: 이메일 입력/발송 완료/비밀번호 설정/링크
만료)을 조회해 스크린샷·`get_design_context`로 대조해보니, 이 판단 자체가 틀렸다는 게 드러났다 —
"공유 컴포넌트 재사용이면 됐다"는 전제가 이 화면에 실제로 필요했던 화면 전용 요소(스테퍼, 아이콘
배지, 재발송 카운트다운 등)의 존재를 놓쳤다. 실제로 대조해 찾은 불일치와 수정 내역:

| 불일치 | 수정 |
| --- | --- |
| 진행 스테퍼("① 이메일 입력 ─ ② 비밀번호 설정")가 카드 어디에도 없었다 | `StepIndicator` 신설 — 1단계는 브랜드색 원, 2단계는 완료 시 초록 체크(`CheckIcon` 14px 재사용), 미완료는 회색. 색 토큰은 Figma에서 직접 뽑음(`#0f5c54`/`#00c950`/`#e5e7eb`/`#99a1af`) |
| 자물쇠/체크/시계 아이콘 배지가 전혀 없었다(제목 위 시각 요소 부재) | `IconBadge`(56px, `rounded-2xl` — **원이 아니라 둥근 사각형**, 처음엔 이 모양 차이도 몰랐다) 신설. 아이콘 4종을 Figma 원본 SVG 그대로 새 컴포넌트로 추출: `LockIcon`/`MailIcon`/`ArrowLeftIcon`/`AlertTriangleIcon`(전부 `get_design_context`로 받은 실제 path, `currentColor`로만 치환) + `CircleCheckIcon`(단순 체크마크인 기존 `CheckIcon`과는 다른 도형 — "발송 완료"/"변경 완료" 화면 전용 원호+체크 아이콘, 이것도 실제로 대조하기 전엔 같은 아이콘인 줄 알았다) |
| "다른 이메일로 다시 시도" 버튼 — Figma에 없는, 이번 세션이 지어낸 UI였다 | 삭제. Figma는 이메일을 표시 칩(봉투 아이콘+주소)으로 재확인시키고, "오라클 방지 설계"를 이용자에게 그대로 밝히는 안내문("보안상 이메일 존재 여부와 무관하게...")과 60초 카운트다운 재발송 버튼(`{n}초 후 재발송 가능` → 0초 시 활성화, 같은 이메일로 재요청)을 보여준다 — 실제 구현에 그대로 반영했다. 서버가 그사이 429를 주면(카운트다운은 클라이언트 근사치일 뿐 서버가 최종 권위) 쿨다운을 다시 60초로 건다 |
| "링크가 만료되었습니다" 화면에 배지·경고 박스가 없었다, 아이콘도 원형 경고(AlertCircleIcon)였다 | 스테퍼 자리에 빨간 알약 배지("⏱ 링크 만료") 추가, 메인 아이콘을 기존 `ClockIcon`(이미 정확히 같은 도형, 스케일만 다름 — 재사용)으로 교체, 그 아래 "재설정 링크는 발송 후 **30분간** 유효합니다. 스팸함도 확인해보세요." 고정 경고 박스(`AlertTriangleIcon`) 추가. **다만 이 박스의 메인 설명 문구 자체는 Figma의 고정 카피("비밀번호 재설정 링크의 유효 시간이 지났습니다")로 바꾸지 않고 서버 `error.message`를 그대로 유지했다** — 프롬프트 5절·AUTH-01 원칙("서버 메시지 그대로, 상태 코드별 분기 문구 만들지 말 것")이 Figma 정적 카피보다 우선한다고 판단한 유일한 의도적 예외. **무효화 조건**: 이 프로젝트 전체의 "서버 에러 메시지를 그대로 노출한다" 원칙(AUTH-01부터 SignupPage/LoginPage/AUTH-03 전체가 공유) 자체가 재검토되거나, 백엔드가 `INVALID_RESET_TOKEN`을 사유별로 세분화해 "만료"만을 위한 전용 메시지를 내려주게 되면 그때 Figma 고정 카피 채택 여부를 다시 판단하라 — 그 전까지는 이 화면만 따로 정적 카피로 바꾸지 말 것(다른 상태 코드·다른 화면과 규칙이 어긋난다) |
| "로그인으로 돌아가기" 링크가 브랜드 초록색 굵게였다 | Figma 실측 색은 `#6a7282`(회색) + `font-semibold`(굵게 아님) + 앞에 14px 화살표 아이콘 — `ArrowLeftIcon` 추가하고 색·굵기 수정 |
| 비밀번호 확인 필드 placeholder "새 비밀번호 재입력" | Figma 정확히 "비밀번호 재입력"(앞에 "새" 없음) — 수정 |

**의도적으로 남긴 차이 — 로고 위치.** Figma는 HomeSense 로고를 카드 밖 페이지 좌상단 고정 위치에
둔다(`LogoMark`, AUTH-01/02엔 없던 레이아웃). AUTH-01/02는 로고를 카드 안 중앙에 두는 공유
`AuthLayout` 패턴을 이미 확립해 뒀고, 이 화면 하나만을 위해 그 두 화면(이미 여러 라운드 코드리뷰로
검증됨)의 레이아웃까지 건드리면 회귀 위험이 더 크다고 판단해 카드 안 중앙 배치를 그대로 유지했다 —
문서화된 의도적 이탈로 남긴다. 3단계(태블릿) 프레임도 이번엔 조회하지 않았다(데스크톱/모바일만
확인) — 데스크톱·모바일이 이렇게까지 정밀히 일치한 이상 태블릿도 같은 패턴을 따를 가능성이 높지만
확인된 사실은 아니다. **무효화 조건**: 이 차이는 "지금 AUTH-03 화면 하나만 고치는 비용 대비 AUTH-01/02
회귀 위험"을 저울질한 판단이지, Figma 배치가 틀렸다는 판단이 아니다 — 다음에 `AuthLayout` 자체를
디자인 리프레시하거나 세 인증 화면(AUTH-01/02/03)의 로고·헤더 배치를 한 번에 재검토하는 별도 작업이
생기면, 그때 Figma대로 로고를 카드 밖 페이지 좌상단 고정으로 세 화면 모두 함께 옮길지 결정하라.
**그 전까지는 "Figma와 다르다"는 이유만으로 AUTH-03의 로고 위치를 단독으로 고치지 말 것** —
AUTH-01/02는 그대로 두고 AUTH-03만 바꾸면 지금 없던 화면 간 불일치가 새로 생긴다.

**검증**: `auth03-figma-parity-check.mjs`(신규, 19/19 — 스테퍼 문구, 아이콘 배지 렌더링, 뒤로가기 링크
색상, 이메일 표시 칩, 오라클 안내문, 60초 카운트다운 → `page.clock`으로 시간을 앞당겨 실제로 재발송
버튼이 활성화되는지, 활성화 후 429를 받으면 쿨다운이 다시 걸리는지, 링크만료 배지·경고박스·동적
서버 메시지 공존, step2 스테퍼(1단계 체크+2단계 활성) 확인 — 백엔드 없이 `page.route()` 목킹만으로
전부 검증). 스크린샷을 Figma 원본과 나란히 대조해 눈으로도 재확인했다. 저장소 밖
`C:\Users\super\homesense-e2e-scripts\`에 보관(15번째 스크립트가 아니라 16번째 — 이 스크립트는 실제
백엔드가 전혀 필요 없다, `auth03-password-reset-check`와는 그 점에서 다르다).

**교훈 — "이미 검증된 공유 컴포넌트 재사용"이라는 전제 자체를 실제로 검증하지 않았다.** 최초 판단은
"화면 구성 요소가 전부 기존에 있던 것"이라고 가정했는데, 실제로는 이 화면에만 필요한 신규 요소
(스테퍼, 아이콘 배지, 카운트다운 재발송, 만료 배지+경고박스)가 대부분이었다 — 프롬프트에 텍스트
카피가 다 있다는 사실이 "시각 요소도 다 있다"는 뜻은 아니었다. AUTH-02가 이미 확립한 "지시받은
전제를 코드로 검증하지 않고 믿지 않는다" 원칙이 Figma 조회 생략 판단 자체에도 똑같이 적용됐어야
했다.

**P2 코드리뷰 지적 반영(2026-09-23) — 토큰 사전 검증(`validate-token`) 실패를 전부 "링크 만료"로
단정하고 있었다.** `ConfirmStep`의 검증 `useEffect`가 `.catch()`에서 400(`INVALID_RESET_TOKEN`)
여부를 확인 없이 무조건 `tokenStatus`를 `'invalid'`로 바꿨다 — 오프라인이거나 서버가 5xx를 반환한
경우(네트워크/서버 일시 장애)에도 똑같이 "링크가 만료되었습니다" 화면이 뜨고 "재설정 링크 다시
요청" 버튼만 보여줬다. 실제로는 링크가 여전히 유효한데도 사용자에게 완전히 새 링크를 다시
요청하라고 잘못 안내하는 셈이었다 — 같은 파일의 `resetPassword()` 제출 핸들러(`onSubmit`)는 이미
400과 그 외를 구분해 그 외는 `formError`로 폼에 남겨 재시도 가능하게 처리하고 있었는데, 사전 검증
쪽만 이 구분이 빠져 있었다.

**수정**: `TokenStatus`에 `'error'`(전송 계층/서버 일시 장애 — 무효 토큰과 무관)를 추가했다.
`catch` 블록은 이제 `axios.isAxiosError` + `response?.status === 400`일 때만 `'invalid'`로 가고,
그 외(오프라인, 5xx, 그 외 예외)는 전부 `'error'`로 간다. `'error'` 화면은 "링크를 확인할 수
없습니다" + `GENERIC_ERROR_MESSAGE`(다른 곳과 동일한 범용 문구)를 보여주고, "다시 시도" 버튼이
`validationAttempt` state를 증가시켜 같은 토큰으로 검증 `useEffect`를 다시 실행한다(재발급 요청이
아니라 같은 요청의 재시도라는 점에서 `'invalid'` 화면의 "재설정 링크 다시 요청"과 의도적으로 다른
문구다).

**검증**: 신규 `auth03-transient-token-error-check.mjs`(9/9, 백엔드 불필요) — (A) 진짜 400은 여전히
"링크가 만료되었습니다" + 서버 메시지 그대로 + "재설정 링크 다시 요청"을 보여주는 회귀 없음 확인,
(B) 네트워크 실패(`route.abort()`)는 "링크를 확인할 수 없습니다" + 범용 문구 + "다시 시도"로 분리
확인, (C) 5xx 이후 "다시 시도" 클릭이 실제로 재검증을 트리거해 성공 시 비밀번호 폼까지 도달하는지
확인. **테스트 작성 중 발견한 함정**: 이 앱은 `main.tsx`에서 React `StrictMode`가 켜져 있어 dev
빌드에서 마운트 시 `useEffect`가 두 번 호출된다 — 최초 시도한 "첫 호출만 실패시키는" 목이
StrictMode의 두 번째 자동 호출에서 이미 성공해 버려 사용자가 버튼을 누르기도 전에 통과하는 거짓
양성을 냈다. "다시 시도" 버튼을 누르기 직전에야 성공 응답으로 전환하는 방식으로 고쳐 해결했다 —
같은 함정이 재발할 수 있으니 이 화면의 다른 재시도 로직을 테스트할 때도 참고하라. 저장소 밖
`C:\Users\super\homesense-e2e-scripts\`에 보관(17번째 스크립트).

**P2 코드리뷰 지적 반영(2026-09-23) — 페이지가 열려 있는 채로(또는 새로고침해서) 이미 쿨다운 중인
이메일을 다시 제출하면, 첫 429 이후로도 제출 버튼이 계속 눌려 매번 429만 다시 받았다.** `RequestStep`은
`cooldown`을 상태로 들고 있었지만, 이 값을 실제로 소비해 버튼을 잠그는 곳은 `phase === 'sent'`(발송
완료 화면)의 재발송 버튼뿐이었다 — 폼이 아직 `phase === 'form'`인 상태(예: 429를 받았지만 성공한 적은
없어 `sent`로 전환되지 않은 경우, 또는 새로고침으로 `phase`가 초기화된 뒤 같은 이메일을 다시 입력한
경우)에서는 제출 버튼의 `disabled` 조건이 `!emailValid || isRequesting`뿐이라 `cooldown`을 전혀
참조하지 않았다. 사용자는 60초 내내 버튼을 계속 누를 수 있었고 그때마다 서버에 요청이 나갔다(서버
쿨다운이 최종 방어선이라 실질 피해는 없지만, 광고된 "60초 클라이언트 락"이 이 경로에서만 무력화돼
있었다).

**수정**: `cooldownEmail`(신규 state) — 쿨다운이 걸린 실제 이메일을 함께 기억한다. 서버 쿨다운 키가
이메일별이라(`PasswordResetTokenService.tryStartCooldown(email)`, 위 AUTH-03 백엔드 절 참고) `cooldown`
값 하나만으로 폼 전체를 잠그면 사용자가 **다른**(쿨다운 없는) 이메일로 바꿔 정상적으로 제출하려는
것까지 막아버린다 — 그래서 "현재 입력값이 `cooldownEmail`과 같을 때만" 잠근다
(`isCoolingDownForCurrentEmail`). 429를 받으면 `setCooldownEmail(targetEmail)`도 함께 기록한다. `form`
단계의 제출 버튼은 이제 `isCoolingDownForCurrentEmail`이면 `sent` 단계와 똑같은 잠긴 카운트다운
박스("{n}초 후 다시 시도 가능")로 바뀌고, 카운트다운이 끝나면 다시 진짜 버튼으로 돌아온다. **버튼을
숨기는 것만으로는 불충분했다** — 이 폼은 입력 필드가 하나뿐이라 브라우저의 암묵적 단일 필드 제출
규칙상 제출 버튼이 화면에 없어도 이메일 필드에서 Enter를 치면 네이티브 `submit` 이벤트가 발생할 수
있다. 그래서 `onSubmit` 핸들러 자체에도 같은 가드(`cooldown > 0 && cooldownEmail === trimmed`이면
`submitRequest()`를 호출하지 않고 반환)를 걸어, 버튼 렌더링 우회와 무관하게 실제 API 호출 자체를
막았다.

**검증**: 신규 `auth03-form-cooldown-check.mjs`(9/9, 백엔드 불필요) — (A) 신고된 버그 재현: 폼이 idle
상태에서 429를 받으면 제출 버튼이 즉시 잠긴 카운트다운으로 바뀌고, 그 상태에서 이메일 필드에 Enter를
쳐도 추가 요청이 나가지 않으며(`requestCount` 단언), 60초(`page.clock.runFor(1000)` 61회 반복 — 위
"카운트다운 테스트는 runFor를 1초 단위로 반복 호출하라" 메모와 같은 이유) 후 실제 버튼이 돌아오는지
확인. (B) 쿨다운 중인 이메일에서 **다른** 이메일로 바꾸면 즉시 다시 진짜 버튼으로 돌아오고, 그 다른
이메일은 실제로 정상 제출(POST 1회)되는지 확인 — `cooldownEmail` 비교 로직이 과잉 차단하지 않음을
증명. 기존 `auth03-transient-token-error-check`(9/9)·`auth03-figma-parity-check`(19/19)도 재실행해
회귀 없음 확인(합계 37/37). 저장소 밖 `C:\Users\super\homesense-e2e-scripts\`에 보관(18번째 스크립트).

### SCR-HOME-01 / UIC-01~03,05,07~09 구현 결정 사항

HOME-01은 이 저장소가 GNB 있는 화면을 만드는 첫 사례라, AUTH-01/02의 `AuthLayout`(중앙 카드, GNB 없음)과
분리된 신규 `MainLayout`(GNB/모바일 헤더 + 본문 + Footer + 모바일 하단 탭)을 도입했다 — 이후
SRCH-01/DTL-01/MAP-01/MY-01 등이 이 레이아웃을 공유한다. 함께 만든 7개 공용 컴포넌트(UIC-01 `Gnb`,
UIC-02 `BottomTabNav`, UIC-03 `SearchBar`, UIC-05 `ComplexCard`, UIC-07 `ToastProvider`/`useToast`,
UIC-08 `EmptyState`/`Spinner`, UIC-09 `DataTrustBadge`)도 같은 이유로 이 절에 함께 기록한다.

| 항목 | 설계서/프롬프트 전제 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| **`ComplexSummaryResponse`의 정밀/근사 배지·층수 — 사용자 확인 완료, 2026-09-17 백엔드 갭 해소됨(프론트 미반영)** | 작업 지시는 UI정의서 4.9절대로 정밀(EXACT)/근사(SIMILAR) 배지와 층수를 카드에 표시하라고 했다 | `ComplexSummaryResponse.java`를 직접 읽어 확인한 결과 `matchMethod`/`floor` 필드 자체가 없다(둘 다 `Trade` 엔티티엔 존재하지만 이 DTO가 노출하지 않음) — `AskUserQuestion`으로 "프론트에서 생략(권장)" vs "백엔드 DTO 확장" 중 선택을 요청해 **생략**으로 확정했다. `DataTrustBadge`(UIC-09)는 `matchMethod?: 'EXACT'\|'SIMILAR'`를 선택적 prop으로 남겨 필드가 없으면 정밀/근사 배지를 그리지 않고, 값이 들어오면(백엔드가 나중에 노출하면) 그대로 렌더링한다 — housingType 배지(아파트/연립다세대)는 이 DTO에 이미 있어 항상 그린다. `ComplexCard`(UIC-05)의 층수 캡션도 같은 이유로 생략(`전용 {area}㎡ · {dealDate}`만 표시, Figma의 "· {floor}층"은 뺐다) | 이 DTO는 `search()`(SRCH-01)와 `popular()`(HOME-01) 양쪽이 공유하는 계약이라, `ComplexRepositoryCustomImpl` QueryDSL·`ComplexServiceTest`·`ComplexControllerTest`·`ComplexRepositoryMariaDbIT`·`FavoritePropertySummaryResponse`·`TradeSummaryResponse`까지 건드리는 확장을 이번 프론트 전용 작업 범위에서 임의로 진행하지 않기로 했다(백엔드/공유 계약을 사전 승인 없이 건드리지 않는다는 이 세션의 운영 원칙). **2026-09-17: 완전히 해소됨** — 백엔드가 "CPX-RCV-RGN 카드 표시 필드 보강" 작업으로 `matchMethod`/`floor` 필드를 추가한 데 이어, 프론트도 같은 날 반영을 마쳤다. `features/complex/types.ts`에 `matchMethod: MatchMethod | null`/`floor: number | null` 추가(`MatchMethod` 타입을 이 파일에서 export해 `DataTrustBadge`가 로컬에 중복 정의하던 걸 정리), `ComplexCard`가 `complex.matchMethod`를 `DataTrustBadge`에 그대로 넘기고 `floor`가 null이 아닐 때만 메타 라인에 "· {floor}층" 세그먼트를 붙인다(null이면 세그먼트 자체를 생략 — "전용 84㎡ · null층" 같은 문자열이 나오지 않게). Playwright로 EXACT/SIMILAR/null 세 경우와 floor null 케이스를 모두 검증(`home01-cardfields-check.mjs`, 스크래치). |
| **`RecentViewResponse`의 주소·가격·면적·층 — 위와 같은 종류의 데이터 갭, 재확인 없이 동일 원칙 적용. 2026-09-17 백엔드 갭(주소만) 해소됨(프론트 미반영)** | Figma 최근 조회 카드는 주소/가격/전용면적·층을 표시한다 | `RecentViewResponse.java`를 확인한 결과 `complexId`/`complexName`/`housingType`/`viewedAt` 4개뿐이다 — 위 `ComplexSummaryResponse` 항목과 정확히 같은 성격의 갭이라 별도로 `AskUserQuestion`을 다시 묻지 않고 같은 원칙(프론트에서 생략)을 그대로 적용했다. `RecentViews`(pages/home)는 주소/가격/면적·층 대신 housingType 라벨만 캡션으로 보여준다 | 이미 한 번 확정된 "백엔드 DTO를 승인 없이 확장하지 않는다"는 원칙을 매 데이터 갭마다 다시 물을 필요는 없다고 판단했다 — 대신 이 표에 남겨 다음에 RCV 도메인을 다시 열 때 "카드에 실제 정보를 채우려면 이 DTO부터 확장해야 한다"는 사실을 확인할 수 있게 했다. **2026-09-17: 주소 갭 완전히 해소됨(프론트까지)** — 백엔드가 `RecentViewResponse`에 `sido`/`sigungu`/`dongRi` 3개 필드를 추가했고(`ComplexSummaryResponse`와 동일한 원시 필드 표현, 단일 `address` 문자열이 아님), 프론트는 `lib/format.ts`에 신설한 `formatAddress(sigungu, dongRi)`(둘 다 nullable, 있는 쪽만 공백으로 이어붙임)를 `ComplexCard`와 `RecentViews` 양쪽이 공유하도록 했다 — "ComplexCard의 기존 조합 로직과 동일하게 처리"하라는 지시를, 새 규칙을 만드는 대신 그 규칙 자체를 공유 함수로 뽑아 두 컴포넌트가 항상 같은 결과를 내도록 구현했다. `RecentViews`는 주소와 housingType 라벨을 `[address, label].filter(Boolean).join(' · ')`로 한 줄에 합쳐 보여주고, 주소 세 필드가 전부 null이면 그 줄에 housingType 라벨만 남는다(불필요한 " · " 구분자나 "null" 문자열이 남지 않음). 가격/면적/층 갭은 여전히 범위 밖(`recent_view` 테이블 자체에 대응 데이터가 없어 별도 검토 필요). Playwright로 주소 있음/전부 null 두 케이스 검증(`home01-cardfields-check.mjs`).** |
| **`InterestRegionSummaryResponse`의 "이번 달 N건" — 같은 종류의 갭. 2026-09-17 백엔드 갭 해소됨(프론트 미반영)** | Figma 관심 지역 카드 캡션이 "평균 거래가 · 이번 달 23건"처럼 거래 건수를 함께 보여준다 | `InterestRegionSummaryResponse`엔 `avgPrice`/`changeRate`뿐이라(건수 필드 없음) 캡션을 "평균 거래가"로 줄였다 | 위 두 항목과 같은 원칙. `SVC-RGN-01`이 향후 `newTradeCount`를 노출하면(CLAUDE.md SVC-RGN-01 절 — MY-02용으로 이미 `RegionStats`엔 추가돼 있으나 이 DTO엔 아직 안 실렸다) 그대로 이어붙이면 된다. **2026-09-17: 완전히 해소됨(프론트까지) — 문구를 "이번 달"이 아니라 "최근 1개월"로 확정했다(신규 판단).** 백엔드가 `InterestRegionSummaryResponse.tradeCount` 필드를 추가해 `RegionStats.newTradeCount()`를 그대로 노출한다. 이 작업의 원래 지시문은 Figma 원본 카피("이번 달 N건")를 그대로 쓰거나 "최근 30일 N건"으로 바꾸는 두 선택지를 전제했는데, `RegionStatsCalculator.calculate()` 소스를 직접 읽어보니 실제 계산은 `LocalDate.now(KST).minusMonths(1)`로 만든 롤링 윈도우다 — 날짜 수 고정(30일)이 아니라 달력상 "1개월 전"이라 두 표현 다 지시문의 "최근 30일"과 정확히 일치하지 않는다. "이번 달"은 달력월 경계로 잘못 읽혀(예: 매달 1~2일경엔 실제로 지난 30여 일 치를 세면서도 "이번 달"이라 자칭하는 오독), "최근 30일"은 실제 구현이 일수 고정이 아니라는 점에서 근사치일 뿐이라, 구현 표현과 글자 그대로 일치하는 "최근 1개월"로 확정했다(지시문이 검토하라고 열어둔 두 선택지 중 어느 쪽도 그대로 쓰지 않고 코드 확인 후 제3의 문구를 택한 사례). `RegionCard`의 캡션을 "평균 거래가 · 최근 1개월 {tradeCount}건"으로 바꿨고, `tradeCount`가 0이어도(avgPrice/changeRate가 null인 "데이터 없음" 지역 포함) 캡션 자체는 계속 렌더링해 항목이 숨겨지지 않는다. Playwright로 tradeCount>0/tradeCount=0(무통계) 두 케이스 검증(`home01-cardfields-check.mjs`).** |
| **로그아웃 사용자의 "최근 조회한 단지" — 세션 기반 실데이터 vs 항상 로그인 유도 문구, 판단 완료** | 작업 지시가 두 옵션(A: 세션 기반 실데이터 노출 — 권장, B: Figma 정적 목업 그대로 항상 유도 문구)을 놓고 판단을 요구했다. Figma 모바일/데스크톱 로그아웃 프레임을 직접 조회해 보니 실제로는 **항상** "로그인하면 최근 조회한 단지가 자동으로 저장돼요"만 보여준다(빈 상태가 아니라 고정 문구) | **A(세션 기반)로 확정.** `RecentViewController`가 이미 `X-Session-Id` 헤더로 비로그인 조회 이력을 지원하도록 설계돼 있어(`RecentViewService.getRecent()`, userId 없으면 sessionId로 조회) 이 기능을 쓰지 않을 이유가 없었다. `getRecentViews()`(features/recentview/api.ts)는 로그인 여부와 무관하게 항상 `X-Session-Id`를 실어 보내고(로그인 시엔 백엔드가 userId를 우선해 무시하므로 무해함 — `RecentViewService.getRecent()` 55~57행 확인), `RecentViews` 컴포넌트는 결과가 **비어있을 때만** 유도 문구를 보여준다. 세션 ID는 `crypto.randomUUID()`로 생성해 `localStorage`(브라우저 단위, 탭 단위 아님)에 저장한다(`lib/sessionId.ts`) — DTL-01이 구현되기 전까지는 이 저장소에 조회 이력을 쌓을 경로가 없어 신규 세션은 항상 빈 배열을 받고, 결과적으로 Figma가 보여주는 고정 문구와 동일한 화면이 된다 | Figma가 "빈 상태" 대신 "항상 고정 문구"를 보여주는 것처럼 보이는 이유는 그 목업이애초에 조회 이력이 있는 비로그인 세션을 표현할 방법이 없는 정적 이미지이기 때문이라고 해석했다 — RCV 도메인이 세션 기반 조회를 명시적으로 설계해 뒀는데 프론트가 그 값을 절대 쓰지 않고 항상 문구만 보여주면 그 설계 자체가 죽은 코드가 된다. A안은 B안의 시각적 결과를 완전히 포함하는 상위 호환(신규 세션 = B안과 동일 화면)이라 리스크 없이 A를 택했다. **로그인한 사용자가 조회 이력이 0건일 때**는 "로그인하면…" 문구를 그대로 쓰면 명백히 틀린 말이 되므로, `isAuthenticated` 여부로 문구를 분기했다("아직 조회한 단지가 없어요" vs "로그인하면…") — 이건 A/B 판단과 별개로 필요했던 보완이다. |
| **비로그인 하트 클릭 → 로그인 → 원래 동작 재생 메커니즘** | 작업 지시는 AUTH-01의 `location.state.from` 패턴을 재사용하라고만 명시, 구체적 메커니즘은 없음 | `location.state.from`(AUTH-01의 `getRedirectPath`, `features/auth/redirect.ts`)은 그대로 재사용해 "로그인 후 돌아갈 경로"만 책임지게 두고, **"클릭했던 complexId"는 별도로 `sessionStorage`("homesense.pendingFavoriteComplexId")에 담아** 로그인 후 재마운트된 `useFavoriteToggle` 훅이 `isAuthenticated`가 true로 바뀌는 시점에 정확히 한 번(`useRef` 가드) 자동 재생한다(`pages/home/useFavoriteToggle.ts`) | `redirect.ts`의 `getRedirectPath()`는 미래의 다른 보호된 라우트(FAV/NTF 화면 등)도 공유할 범용 계약이라, "찜하려던 complexId" 같은 HomeSense 한 기능 전용 필드를 얹으면 그 계약이 이 기능에 결합돼 재사용성이 떨어진다고 판단했다 — 대신 기능별 pending-action은 각 기능이 자기 책임으로 `sessionStorage`에 들고 있게 분리했다. Playwright로 목킹된 백엔드(POST /api/auth/login, POST /api/favorites/properties 등)를 태워 하트클릭→/login 리다이렉트→로그인→`/`로 복귀→POST 자동 발사→성공 토스트→하트 채워짐 전체 왕복을 검증했다. |
| **관심 매물 POST 실패 시 에러 노출** | 완료 조건이 "실패 시 처리"를 구체적으로 명시하지 않음 | AUTH-01이 확립한 관례(서버 `error.message`를 가공 없이 그대로 노출)를 그대로 재사용 — `error.response.data.error.message`가 있으면 토스트(error variant)로 그대로 보여주고, 없으면(네트워크 오류 등) 범용 메시지("일시적인 오류가 발생했습니다…") | 409(`DuplicateFavoriteException`) 같은 도메인 예외 메시지가 이미 사용자에게 의미 있는 한국어 문장이라 별도로 재작성할 이유가 없다. |
| **단지 이미지 자리표시** | Figma는 실제 단지 스톡 사진을 쓴다 | `Complex` 엔티티에 이미지/사진 URL 컬럼 자체가 없다(엔티티 확인 완료) — 실사진 대신 브랜드 톤 그라디언트 위에 옅은 `HomeIcon`을 올린 중립 자리표시를 `ComplexCard`에 내장했다 | Figma 스톡 사진은 가상의 단지명(반포자이 등)에 맞춰진 것이라 실제 배치 데이터의 단지와 매칭될 수 없다 — 실사진처럼 보이는 가짜 이미지를 노출하는 것보다 자리표시라는 사실이 드러나는 중립 비주얼이 낫다고 판단했다. |
| **모바일 매물유형/거래유형 토글 — Figma 재확인으로 프롬프트 가정을 뒤집음** | 작업 지시는 모바일에서 이 토글이 가로 스크롤 칩으로 바뀐다고 가정했다 | 모바일 로그인 Figma 프레임(24:6736)을 직접 조회해 확인한 결과 **데스크톱과 동일한 두 줄 알약 버튼 그룹을 그대로 축소해 쓴다**(가로 스크롤 아님) — 확인된 픽셀 그대로 `SegmentedToggle` 하나로 두 브레이크포인트를 공유하게 구현했다. 인기 검색어 칩과 추천 단지 그리드는 반대로 실제 가로 스크롤이 맞다(같은 프레임에서 `Container:scroll-content` 구조로 명시적으로 확인) | "완료 조건" 문서 원문의 가정보다 실제 Figma 픽셀을 우선했다 — AUTH-02의 "지시받은 전제를 검증 없이 믿지 않는다" 원칙과 같은 결(CLAUDE.md SCR-AUTH-02 절 참고). |
| **모바일 헤더 비로그인 우측 아이콘 — 미확인 글리프를 임의로 그리지 않음** | Figma 모바일 로그아웃(24:7370) 헤더 우측에 아이콘 버튼이 하나 있다 | 이 프레임은 `download_assets` 호출 대상이 아니었어서(desktop-login 노드에서만 아이콘을 받았다) 정확한 SVG를 확인하지 못했다 — 대신 데스크톱 로그아웃 GNB(4:1232, 이번에 재조회해 직접 확인)와 기능적으로 동일하도록 `MobileHeader`에서 "로그인" 텍스트 링크로 대체했다 | 확인 안 된 아이콘을 추측해서 그리면 실제 Figma와 다른 그림이 코드에 박제된다 — 기능(로그인 진입점 제공)은 지키되 시각 요소는 검증된 것만 쓰기로 했다. |
| **GNB 아바타 클릭 동작 — 로그아웃 버튼이 아니라 MY-01 링크** | 없음(Figma 정적 목업엔 아바타 클릭 시 드롭다운/로그아웃 어포던스가 없음) | `Gnb`/`MobileHeader`의 아바타를 `<Link to="/my">`(MY-01 자리표시)로 구현 — 로그아웃 트리거로 쓰지 않았다 | 정적 목업에 없는 드롭다운 메뉴를 임의로 설계하는 것은 과설계이자 추측이라고 판단했다. 로그아웃은 실제 MY-01 화면이 구현되는 시점에 그 화면 안에 넣는 것이 자연스럽다. |
| **태블릿 브레이크포인트 — 별도 Figma 조회 없이 Tailwind 보간** | 프롬프트가 태블릿 로그인/로그아웃 노드(24:8792, 24:7860)도 조회 대상으로 명시했다 | 시간 제약상 이 두 노드는 이번 세션에 조회하지 않았다 — `md:`(768px)/`lg:`(1024px) 브레이크포인트로 모바일↔데스크톱 사이를 보간하는 일반적인 반응형 규칙만 적용했다(GNB는 `md:` 이상에서 노출, 추천 그리드는 `md:grid-cols-2 lg:grid-cols-4`) | **완결 필요** — 다음에 이 화면을 다시 열 때 태블릿 두 노드를 실제로 조회해 이 보간이 Figma 태블릿 전용 수치(간격·컬럼 수 등)와 어긋나지 않는지 확인하라. |
| **AuthContext에 `user` 필드 신설 — LoginResponse엔 nickname이 없다는 사실 발견** | GNB 아바타(닉네임 이니셜)를 그리려면 로그인한 사용자의 닉네임이 필요하다 | `LoginResponse.java`를 확인한 결과 `accessToken`/`refreshToken`/`expiresIn` 3필드뿐이라 로그인 응답만으로는 닉네임을 알 수 없다 — `AuthContext`에 `user: UserResponse \| null`을 추가하고, `AuthProvider`가 로그인 성공 후(또는 새로고침으로 토큰만 남아있을 때) `GET /api/users/me`를 호출해 채운다. `SignupResponse`는 반대로 `nickname`을 이미 평탄하게 포함하고 있어(SCR-AUTH-02 절에서 이미 확인된 사실) 가입 직후엔 추가 호출 없이 그 자리에서 바로 채운다 | 이 저장소 최초로 "로그인된 사용자 정보를 화면에 표시해야 하는" 요구가 생겨 AuthContext를 확장했다 — AUTH-01/02는 인증 여부(`isAuthenticated`)만 알면 됐지 사용자 정보 자체가 필요 없었다. |
| **`httpClient`에 Authorization 헤더 인터셉터 신설** | 없음(AUTH-01 당시 "보호된 라우트가 실제로 생기는 시점에 추가"로 유보돼 있던 항목 — 프론트엔드 구현 결정 사항 표 "accessToken 자동 갱신 인터셉터" 행 참고) | `httpClient.interceptors.request.use()`로 `accessToken`이 있으면 항상 `Authorization: Bearer` 헤더를 붙인다(401→refresh 자동 갱신 인터셉터는 여전히 별도 과제로 남겨둠 — 이번엔 헤더 첨부만) | HOME-01이 이 저장소 최초로 인증이 필요한 API(`interest-summary`, `POST /favorites/properties`)를 호출한다 — 백엔드가 토큰 없어도 요청을 막지 않는 원칙(CLAUDE.md 인증 절)이라 이 헤더를 무조건 붙여도 비로그인 전용 엔드포인트에 해가 없다. |
| **`X-Session-Id` 프론트 생성/저장 방식** | CLAUDE.md SVC-RCV-01 절이 헤더 이름(`X-Session-Id`)만 백엔드 쪽에서 확정해 뒀고, 프론트가 어떻게 생성·저장할지는 미정이었다 | `crypto.randomUUID()`로 생성해 `localStorage`(`homesense.sessionId`)에 저장 — 탭이 아니라 브라우저에 귀속되도록 `sessionStorage`가 아니라 `localStorage`를 썼다(`lib/sessionId.ts`) | "브라우저별로 생성해 관리하는 세션 식별자"라는 SVC-RCV-01 설계 의도(CLAUDE.md 참고)를 그대로 따르려면 새로고침·새 탭에서도 값이 유지돼야 한다 — `sessionStorage`는 탭이 닫히면 사라져 이 의도와 맞지 않는다. |
| **검색 자동완성(UIC-03 관련) — 미구현, 범위 밖 확정** | 프롬프트가 "자동완성 API 연동은 선택/유예 가능"이라고 명시 | `SearchBar`(UIC-03)는 순수 텍스트 입력+버튼만 구현하고 `GET /api/regions`(자동완성) 연동은 하지 않았다 | SRCH-01 자체가 아직 자리표시 화면이라 자동완성 결과를 클릭해 이동할 목적지가 없다 — SRCH-01을 실제로 구현하는 시점에 `RegionAutocompleteResponse`를 연동하라. |
| **백엔드 미기동으로 인한 통합 검증 한계 — 완결 필요(전제였던 schema_all.sql 부재는 같은 세션 안에서 해소됨)** | 완료 조건이 실제 API 연동(`GET /api/complexes/popular` 등)이 올바르게 동작하는지 확인하라고 요구한다 | 이번 세션엔 로컬에 MariaDB가 떠 있지 않았고(Redis만 기동, `docker ps` 확인) **당시엔** 저장소에 `schema_all.sql`도 없어(테이블 정의서 DDL은 외부 문서 전용) 실제 백엔드를 새로 기동해 검증하지 못했다 — 대신 (1) 백엔드 없이 뜬 프런트에서 각 fetch가 실패해도 빈 배열로 우아하게 폴백하는지, (2) Playwright로 `page.route()`를 이용해 5개 엔드포인트를 전부 목킹해 실제 응답 스키마(`ComplexSummaryResponse` 등)를 넣었을 때 카드 렌더링·하트클릭·로그인·자동재생·토스트 전체 왕복이 올바른지 검증했다 | **완결 필요(부분 해소)** — `schema_all.sql`은 같은 세션 뒷부분에서 만들어져 커밋됐다(위 "정정" 문단의 "해소됨(2026-09-14)" 참고, 2026-09-17 재확인 완료) — 스키마 부재라는 전제 자체는 더 이상 걸림돌이 아니다. 다만 실제로 로컬 MariaDB에 그 스키마를 적용하고 `./gradlew bootRun`으로 백엔드를 띄운 뒤 `GET /api/complexes/popular`/`GET /api/regions/interest-summary`/`GET /api/recent-views`/`GET /api/search/popular` 4개를 실 데이터로 재확인하는 작업 자체는 아직 아무도 하지 않았다 — 이 부분만 여전히 완결 필요다(SVC-FAV-01/SVC-NTF-01 절의 "Docker 없어 IT 미실행" 잔여 리스크와 같은 성격). |
| **하트 클릭이 항상 POST만 호출 — 코드리뷰(P2) 지적, 수정 완료** | 초기 구현은 `favoritedIds`를 항상 빈 Set에서 시작하고 `toggleFavorite`가 항상 `addFavoriteProperty()`(POST)만 호출했다 — UI는 아바타 하트처럼 토글로 보이지만 실제로는 추가 전용이었다 | 이미 관심 매물로 등록된 단지는 새로고침 후에도 빈 하트로 보이고, 클릭하면 해제가 아니라 `DuplicateFavoriteException`(409)만 받았다 — `GET`/`DELETE` 엔드포인트가 이미 있는데도 프론트가 전혀 쓰지 않고 있었다. 로그인 상태 마운트 시 `GET /api/favorites/properties`로 `complexId→favoritePropertyId` 맵을 하이드레이트하고(`useFavoriteToggle.ts`), 이미 등록된 항목은 `toggleFavorite`가 `DELETE /api/favorites/properties/{favoritePropertyId}`(경로의 `{id}`는 complexId가 아니라 favoritePropertyId — `FavoriteController.removeFavoriteProperty()` 확인)를 호출하도록 분기했다. 이 하이드레이션 effect는 로그인 여부 분기가 `getFavoriteProperties()` 호출 **이전**에 있어 비로그인 사용자에게는 이 GET 자체가 나가지 않는다(Playwright로 별도 검증: 마운트·클릭 어느 시점에도 `/api/favorites/properties` 호출 0건, `/login` 리다이렉트만 발생) | `Set<number>`(complexId만)로는 DELETE를 호출할 방법이 없어 `Map<complexId, favoritePropertyId>`로 상태 구조 자체를 바꿔야 했다. Playwright로 "이미 찜한 단지는 채워진 하트로 렌더 → 클릭 시 DELETE(POST 아님) → 해제 토스트"와 "안 찜한 단지는 빈 하트 → 클릭 시 POST → 등록 토스트" 둘 다 목킹된 백엔드로 검증했다. |
| **GNB/모바일 헤더 알림 벨 — 세 차례 코드리뷰(P2)로 점진적으로 바로잡음, 최종 확정** | 초기 구현은 `Gnb`/`MobileHeader` 둘 다 알림 벨을 `onClick` 없는 `<button>`으로 그려 뒀다(`/notifications` 라우트가 이미 있는데도 탭해도 아무 반응이 없었다) | **1차 수정(불완전):** 두 벨을 전부 `<Link to="/notifications">`로 바꿔 클릭 가능하게 만들었다 — 이때 `/notifications`가 렌더링하는 자리표시 화면을 `programId="MY-03"`(잘못됨, 아래 참고)로 임의 지정했다. **2차 수정(불완전):** 코드리뷰에서 (1) UI정의서 2.3/4.1/4.2절이 모바일 알림 진입점을 GNB 벨이 아니라 하단 탭 "마이" 아이콘의 배지로 명시하고 있고(하단 탭을 5개로 유지하기 위해 알림을 별도 탭으로 두지 않는 설계), 모바일 헤더 벨은 애초에 Figma 글리프를 확인한 적 없는 추정 아이콘이었다는 점, (2) `/notifications`가 실제로는 두 개의 다른 화면(MY-03 알림 설정, MY-04 알림 이력)을 가리킬 수 있는데 `NotificationController.getNotifications()`/`NotificationResponse`의 Javadoc이 명시적으로 "MY-04 알림 이력"이라 적어 둔 것을 확인 안 하고 MY-03(알림 설정, `GET/PUT /api/notifications/settings` 전용)으로 잘못 연결했다는 점, 두 가지를 지적받았다. 모바일 헤더 벨은 완전히 제거(`MobileHeader.tsx`)했고, `/notifications` 목적지는 MY-04로 정정(`AppRouter.tsx`)했지만 — 이때는 데스크톱 GNB 벨(Figma 3:2 프레임에 빨간 점 배지와 함께 그려져 있던 것) 자체는 "픽셀 증거가 있다"는 이유로 그대로 유지하며 완결 필요로만 남겨뒀다. **3차 수정(최종):** 지성이 UI정의서 2.3절/4.1절 원문을 직접 대조해, GNB 구성이 "로고 / 주메뉴(지역·단지 검색·지도로 보기·관심목록·알림) / 우측 영역(비로그인: 로그인·회원가입, 로그인: 프로필 아이콘)"으로만 정의돼 있고 벨은 어디에도 언급되지 않는다는 것을 확인해 주었다 — **데스크톱 GNB 벨도 완전히 제거**하고(`Gnb.tsx`, `BellIcon` import까지 함께 삭제), 데스크톱의 유일한 알림 진입점을 중앙 네비 "알림" 텍스트 링크(MY-04) 하나로 확정했다 | 벨을 "클릭 가능하게" 고치는 것과 "이 벨이 애초에 존재해야 하는가"는 서로 다른 질문인데, 1차 수정은 전자만 보고 후자를 검토하지 않았다. 2차 수정은 후자를 모바일에는 적용했지만 데스크톱엔 "Figma 픽셀 증거"를 근거로 예외를 뒀는데, 이 프로젝트 스스로가 명시한 "코드와 문서가 어긋나면 문서가 맞다" 원칙(문서 체계 절 — Figma는 6개 근거 문서에 포함되지 않는다) 아래에서는 그 예외 자체가 근거 부족이었다. Figma 픽셀은 "임의 추측"이었던 모바일 벨보다는 근거가 있었지만, 그 근거가 애초에 6개 근거 문서 밖에 있다는 점은 동일했다 — 이번에 지성이 UI정의서 원문을 직접 확인해 주어 완결 필요 상태에서 확정된 결정으로 종결됐다. **남은 완결 필요는 1건뿐:** 하단 탭 "마이" 아이콘의 미읽음 카운트 배지 자체는 아직 구현하지 않았다 — `GET /api/notifications`가 항목별 `isRead`는 주지만 전용 미읽음 카운트 엔드포인트가 없어(값을 구하려면 전체 목록을 받아 클라이언트에서 세야 하는데 페이지네이션 때문에 부정확하다) 백엔드에 카운트 엔드포인트를 추가하는 논의가 먼저 필요하다 — UI정의서 4.2절은 이 배지를 명시적으로 요구하는데 API-NTF-01(프로그램목록서·설계서)엔 이를 뒷받침할 엔드포인트가 없어, UI정의서 요구사항이 프로그램설계서보다 앞서 있는 별도의 문서 간 갭이다(지성 확인). |

### 프론트엔드 세션 복원과 토큰 재발급 조율 (2026-09-28, `feature/frontend/home-header-auth`)

`features/auth/session.ts`가 새로고침 시 저장된 토큰을 서버로 확인하고, 필요하면 Refresh Token으로 재발급한다.
백엔드 Rotation의 재사용 탐지(위 "SVC-AUTH-01 Refresh Token Rotation" 절) 때문에 **같은 Refresh Token으로
재발급이 두 번 나가면 그 사용자의 세션이 전부 폐기된다** — 아래 결정은 전부 이 제약에서 나온다.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| 로그인 상태 판정 | 토큰이 있다는 것만으로 로그인으로 보지 않는다. `GET /api/users/me` → 4xx면 재발급 1회 → 다시 `getMe`. 확인 중에는 `authChecking`이 켜져 헤더 우측·관심 지역 카드가 어느 쪽으로도 확정하지 않는다 | 만료·폐기된 토큰만 남은 비로그인 사용자가 로그인 상태(빈 아바타)로 보였다 | — |
| 결과 3종 | `authenticated` / `invalid`(서버가 거부 → 토큰 삭제) / `unreachable`(네트워크 오류·5xx·timeout·락 대기 초과 → **토큰 유지**, 화면은 비로그인, 다음 로드에서 다시 확인) | 일시 장애로 세션을 잃지 않게 한다 | — |
| 탭 안 직렬화 | 모듈 변수 `inflight`로 같은 탭의 동시 호출을 한 번으로 합친다 | StrictMode 개발 모드가 마운트 effect를 두 번 실행한다 | — |
| **탭 사이 직렬화(Codex P1)** | 재발급을 Web Locks(`navigator.locks`, 락 이름 `homesense.auth.refresh`)로 감싸 같은 오리진의 모든 탭에서 한 번에 하나만 실행한다. 락을 얻은 뒤 저장소를 **다시 읽어**, 그사이 다른 탭이 재발급했으면(저장된 Refresh Token이 바뀌었으면) 재발급하지 않고 그 토큰을 쓰고, 비어 있으면 세션 없음으로 본다. 로그아웃 중의 재발급도 같은 락을 거친다 | 탭마다 따로 있는 `inflight`로는 탭 사이 경쟁을 못 막는다 — Access Token이 만료된 채 두 탭을 동시에 열면 두 탭이 같은 토큰으로 재발급해 재사용 탐지로 세션 전체가 폐기되고, 거절당한 탭이 공유 `localStorage`까지 지웠다(e2e `home01-multitab-refresh-check`로 재현, 수정 전 13건 실패) | — |
| 조건부 삭제 | 토큰은 저장소 값이 이 탭이 쓴 값 그대로일 때만 지우고(`clearTokensIfUnchanged`), **비교와 삭제를 반드시 재발급 락 안에서** 한다 | 락 밖이면 비교와 삭제 사이에 다른 탭의 재발급이 끼어들어 방금 받은 새 토큰을 지울 수 있다 | — |
| **재발급 결과 저장은 compare-and-set(Codex P1, 2026-09-28)** | 재발급 응답을 받은 뒤 저장소의 Refresh Token이 요청에 보낸 것 그대로일 때만 새 토큰을 쓰고, 바뀌었으면 결과를 버린다(`refreshTokens`, 비교와 저장 사이에 await 없음). 로그인·가입의 토큰 저장(`storeTokens`)도 같은 재발급 락을 거친다 — 락 대기가 상한을 넘겨도 방금 로그인한 결과는 반드시 저장한다 | 전에는 재발급 결과를 조건 없이 덮어써, A 세션 복원 중 B로 로그인하면 B의 토큰이 늦게 도착한 A의 재발급 결과로 바뀌었다 — 화면은 B인데 이후 인증 요청은 A로 나갔다(e2e `home01-login-during-restore-check`로 재현, 수정 전 4건 실패). Web Locks가 있으면 로그인 저장이 락을 기다리는 것으로, 없으면 compare-and-set으로 막힌다(compare-and-set을 빼면 Web Locks 없는 경우만 4건 실패) | 버린 재발급 결과(A의 새 Refresh Token)는 어디에도 저장되지 않지만 서버에서는 만료(14일)까지 유효하다 — 아무도 갖고 있지 않아 실제 위험은 없다고 판단했다 |
| **복원 결과의 세대 확인** | `AuthProvider`가 로그인·가입 **성공 직후**(토큰 저장 직전)와 로그아웃 시작 시 세대 값(`sessionGeneration`)을 올리고, 복원은 시작 시점의 값과 달라졌으면 결과를 버린다 | 사용자가 직접 바꾼 세션을 늦게 끝난 복원 결과가 덮지 않게 한다. 로그인 **시작**에 올리지 않는 이유: 비밀번호가 틀려 로그인이 실패하면 복원 결과까지 버려져 헤더가 확인 중 상태에 머문다(e2e로 확인 — 시작 시점에 올리는 변형은 실패) | — |
| **확인 중 인증 의존 동작은 판정까지 미룬다(Codex P2, 2026-09-28)** | `authChecking`이 true인 동안에는 로그인 사용자도 `isAuthenticated`가 false다. 이 값만 보고 비로그인으로 단정하는 소비자는 확인이 끝날 때까지 동작을 미뤄야 한다. `useFavoriteToggle`: 확인 중 하트 클릭은 마지막 하나만 기억했다가, 판정이 나고 그 판정 기준으로 하트 상태(관심 목록)까지 채워진 뒤 처리한다 — 비로그인이면 기존대로 로그인 화면(클릭은 로그인 후 재생), 로그인이면 등록/해제. **[2026-09-30 변경] 클릭은 토글이 아니라 클릭 순간 보이던 하트 기준의 의도(빈 하트 → 등록, 채워진 하트 → 해제)로 기억하고, 목록이 도착했을 때 이미 그 상태면 요청을 보내지 않는다("탭 계정 동기화" 절).** `RecentViews`: 확인 중에는 불러오지 않고 로딩으로 둔다. 헤더·관심 지역 카드는 이미 `authChecking`을 본다 | 예전엔 복원이 느리면 로그인 사용자가 하트를 눌렀을 때 곧바로 `/login`으로 가고 복원이 성공해도 그 화면에 남았다. 하트 상태를 채우기 전에 처리하면 이미 찜한 단지도 해제 대신 등록을 시도해 409를 받는다(e2e `home01-auth-checking-actions-check`: 수정 전 코드 7건 실패, 하트 상태 대기를 빼면 3건 실패). 당시에는 이미 찜한 단지를 확인 중에 누르면 목록을 불러온 뒤 해제했는데, 확인 중에는 하트가 비어 보이므로 사용자가 본 적 없는 해제였다 — 2026-09-30 의도 재생으로 바꿨다 | 인증에 따라 동작이 갈리는 소비자를 새로 만들 때 이 규칙을 따른다(`authContext.ts`의 `isAuthenticated` 설명에도 적었다) |
| **확인 중 하트 클릭 — 대기 표시와 "전역 마지막 하나만" 처리** | 확인 중에 누른 하트에는 곧바로 대기 표시(`aria-busy="true"`, 브랜드색 링·깜빡임 — `components/ui/favoritePending.ts`)를 달고, 판정이 나서 처리되거나(등록/해제 요청 발송) 로그인 화면으로 이동할 때 푼다. 확인 중에 서로 다른 카드 X, Y를 연달아 누르면 **(가) 전역으로 마지막 하나(Y)만 처리**한다 — X의 클릭은 버리고 X의 대기 표시도 즉시 풀린다(대기 상태를 값 하나로 두어 자연히 그렇게 된다) | (나) 카드별로 기억해 모두 처리하는 방식은 대기 목록 관리, 판정 뒤 등록·해제 요청 여러 건의 순차 처리, 비로그인 판정 시 재생 대상을 하나로 줄이는 규칙이 더 필요하다. 확인 구간은 보통 짧아(재발급 1회 왕복) 그사이 여러 카드를 누를 일이 드물고, 버려진 클릭은 대기 표시가 풀려 사용자가 알 수 있으므로 (가)로 충분하다고 판단했다. 검증: e2e `home01-auth-checking-actions-check` — 클릭 직후 대기 표시, 처리 후 해제, X·Y 연속 클릭 시 Y만 등록되고 X의 표시는 풀림(대기 표시를 켜지 않으면 6건, 끄지 않으면 3건 실패) | 확인 구간이 길어지거나(느린 네트워크가 흔한 환경) 여러 카드를 연달아 찜하는 사용 패턴이 확인되면 (나)로 바꾼다 |
| **로그인 저장이 락을 기다리는 구간(2026-09-28 확인)** | 로그인·가입은 `await storeTokens(...)`가 끝난 **뒤에야** 인증 상태를 바꾸고(`setStatus('authenticated')`·`getMe`), 화면 이동도 `await auth.login()`/`auth.signup()` 뒤다(`LoginPage`·`SignupPage`). 그래서 A 재발급이 락을 쥔 동안 B의 저장이 기다리는 구간에는 화면이 로그인 화면 그대로다 — 이 구간의 인증 요청은 저장소에 남은 A의 토큰으로 나가지만 화면은 B가 아니다. 락 대기가 5초 상한을 넘으면 `locks.request`가 거부되고 `storeTokens`의 `catch`가 락 없이 B를 저장한다(방금 로그인한 결과는 반드시 저장). 이때 아직 진행 중인 A 재발급의 응답은 compare-and-set이 버린다 | "화면은 B, API 요청은 A 토큰"이 생기지 않음을 e2e로 확인했다: 재발급을 풀기 전에 앱의 `httpClient`로 보낸 인증 요청은 B 토큰이 아니고 화면도 로그인 화면이며, 화면이 B로 바뀐 뒤의 인증 요청은 1.5초·7초(상한 초과) 대기 모두 전부 B 토큰이다. `login()`에서 `storeTokens`를 기다리지 않게 바꾸면 9건이 실패한다 | 로그인 완료 처리(상태 전환·화면 이동)를 `storeTokens` 완료 전으로 옮기지 않는다 |
| **로그아웃의 서버 폐기에 상한(Codex P2, 2026-09-28)** | 로그아웃 요청마다 timeout 5초(`LOGOUT_REQUEST_TIMEOUT_MS`)를 두고, 서버 폐기 전체를 5초(`LOGOUT_REVOKE_DEADLINE_MS`)까지만 기다린다(`revokeSessionWithinDeadline`). 결과와 무관하게 `AuthProvider.logout`의 `finally`에서 로컬 로그아웃(토큰 삭제·비로그인 전환)을 끝낸다. 상한을 넘기면 폐기 작업에 중단 신호를 보낸다 — 진행 중인 로그아웃 요청은 axios `signal`로 취소되고, 재발급 뒤 단계는 `signal.aborted`를 확인해 멈춘다. 늦게 도착한 재발급 결과는 저장소가 이미 비었거나 바뀌었으므로 조건부 저장이 버린다 | 전에는 서버 폐기(`await`)가 끝나야 로컬 토큰을 지웠는데 로그아웃 요청에 timeout이 없어, 서버가 응답하지 않으면 로그아웃이 영원히 끝나지 않고 버튼도 비활성으로 남았다. 요청별 timeout만으로는 부족하다 — 로그아웃 → 401 → 재발급(락 대기 5초 + 재발급 10초) → 로그아웃으로 이어지면 20초를 넘길 수 있다. 중단 신호가 필요한 이유: 폐기 작업은 재발급 뒤 저장소를 다시 읽어 로그아웃을 보내는데, 상한 뒤 사용자가 B로 다시 로그인했다면 그 요청이 B의 토큰으로 나가 서버에서 B의 세션을 폐기한다(Web Locks가 없는 환경에서 재현, e2e `home01-logout-hang-check`: 수정 전 5건 실패, 상한을 빼면 3건, 중단 신호를 쓰지 않으면 2건 실패) | 상한을 넘겨 버려진 재발급 결과(A의 새 Refresh Token)는 서버에서 만료(14일)까지 유효하다 — 복원 중 로그인의 조건부 저장과 같은 성격의 잔여이며, 아무도 갖고 있지 않아 실제 위험은 없다고 판단했다 |
| **재발급 요청 timeout** | **[2026-09-29 변경: 5초 — "401 자동 재발급과 요청 timeout" 절의 예산]** 10초(`REFRESH_TIMEOUT_MS`, 요청별 axios `timeout`). 초과하면 `unreachable` | axios 기본값은 무제한이라, 응답 없이 매달린 재발급이 락을 영원히 쥐고 다른 모든 탭의 인증을 막을 수 있었다 | — |
| **락 대기 상한** | **[2026-09-29 변경: 2초 — 같은 절, "락 대기 < 재발급 timeout" 불변식은 유지]** 5초(`LOCK_WAIT_TIMEOUT_MS`, `navigator.locks.request`의 `signal`; `AbortSignal.timeout`이 없는 Safari 15.4~15.x는 `AbortController`+`setTimeout`으로 대체). **초과 시 처리: 재시도하지 않고 `unreachable`로 끝낸다**(토큰 유지, 화면은 비로그인, 다음 로드에서 다시 확인) | 대기 상한을 재발급 timeout보다 **짧게** 둔 순서가 막는 것은 **재발급이 시작될 때 이미 락을 기다리던 탭뿐이다.** 그 탭은 락을 쥔 탭의 timeout(10초)보다 먼저 포기하므로, 결과를 알 수 없게 끝난 재발급(서버에서는 이미 토큰을 교체했을 수도 있다) 직후에 같은 토큰으로 다시 재발급하지 않는다. **재발급 시작 후 늦게 열린 탭은 막지 못한다** — 재발급이 시작되고 5초 넘게 지나 열린 탭은 상한 안에 락을 얻고, 저장소에는 여전히 옛 토큰이 있으므로 다시 재발급한다. 첫 재발급이 서버에서 토큰을 교체했다면 이 재발급은 재사용 탐지로 세션을 끝낸다 — 아래 "알려진 한계 — 결과를 알 수 없는 재발급"에 해당하는 경우이고, 실패 방향이 로그아웃이라 보안상 안전하다. 검증: e2e에서 재발급 응답을 무기한 붙잡으면 탭 2는 약 5초 뒤, 탭 1은 10초 뒤 비로그인으로 끝나고 토큰은 남으며 재발급 요청은 1회뿐이다. 두 상한을 빼면 이 시나리오가 4건 실패한다(탭이 확인 중 상태에 영원히 머묾) | 정상적인 재발급이 5초를 넘기는 환경이면 기다리던 탭이 그 로드에서 비로그인으로 보인다(토큰은 남아 새로고침하면 복구). 이런 일이 잦으면 두 값을 함께 다시 정한다 |
| 알려진 한계 — 결과를 알 수 없는 재발급 | timeout으로 끝난 재발급이 서버에서는 실제로 토큰을 교체했을 수 있다. 그러면 옛 토큰으로 보내는 다음 재발급이 재사용 탐지로 세션을 끝낸다 — 다음 로드의 재발급과, 위 "락 대기 상한" 행이 막지 못하는 **재발급 시작 후 늦게 열린 탭**의 재발급이 모두 이 경로다 | Rotation 구조상 클라이언트만으로는 없앨 수 없다. 실패 방향은 "로그아웃"이라 보안상 안전하다 | — |
| **Web Locks 미지원 환경** | Chrome 69·Firefox 96·Safari 15.4 미만에서는 `navigator.locks`가 없어 **탭 안 직렬화만 적용된다** — 탭 사이 재발급 경쟁은 막히지 않는다. 폴백에서도 단일 탭 재발급은 정상 동작한다(e2e로 확인) | — | 지원 브라우저 하한을 이보다 올리면 이 폴백 서술을 정리한다 |
| **보안 컨텍스트 요구** | `navigator.locks`는 보안 컨텍스트(HTTPS 또는 `localhost`)에서만 존재한다. **LAN IP의 http(예: `http://192.168.x.x:5173`)로 개발 서버에 접속하면 폴백 경로(탭 안 직렬화만)가 동작한다** — 휴대폰 실기기로 여러 탭 동작을 확인할 때는 이 점을 감안하라 | — | — |

검증: e2e `home01-multitab-refresh-check`(탭 2·3개 동시 복원 시 재발급 1회·모든 탭 로그인 유지·서버에서 토큰 유효, 멈춘 재발급 시나리오, Web Locks 없음 폴백), `home01-header-session-check`, `home01-logout-check`.

### 401 자동 재발급과 요청 timeout (2026-09-29, `feature/frontend/auth-refresh-interceptor`)

백로그 1번을 구현했다. 화면을 쓰는 도중 Access Token이 만료돼 보호 API가 401을 받으면 `httpClient` 응답 인터셉터가 한 번 재발급하고 원 요청을 한 번 재시도한다. 재발급 경로는 앱에 하나뿐이다 — 세션 복원(`restoreSession`)도 이제 따로 재발급하지 않고 `getMe` 하나만 부르며, 401이면 같은 인터셉터가 재발급한다. 로그아웃의 재발급(`/api/auth/logout`는 인증 엔드포인트라 인터셉터 대상이 아니다)도 같은 `refreshSession`을 부른다.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| **재발급 대상 401 판별** | 네 조건을 모두 만족할 때만 재발급한다(`refreshableAccessToken`, `lib/httpClient.ts`): (1) 응답 `error.code`가 `UNAUTHORIZED`, (2) 요청에 Bearer 토큰이 실렸음, (3) `/api/auth/**`가 아님, (4) 아직 재시도하지 않은 요청(`config._authRetried`) | 백엔드의 401은 세 종류다(2026-09-29 코드 전수 확인). `UNAUTHORIZED`는 Spring Security가 인증 없는 보호 엔드포인트 요청을 막을 때 `RestAuthenticationEntryPoint`가 내는 코드이고, 만료·폐기·컷오프된 토큰은 필터가 인증 정보를 채우지 않아 전부 여기로 온다. 나머지 둘은 비즈니스 401이라 재발급 대상이 아니다 — `INVALID_CREDENTIALS`(로그인 실패, 회원정보 수정·탈퇴의 현재 비밀번호 불일치, 설계서 3.2절), `INVALID_REFRESH_TOKEN`(재발급·로그아웃 실패, 인증 엔드포인트라 (3)으로도 걸러진다). 상태 코드가 아니라 에러 코드로 판별한다 — "서버 error.message를 그대로 보여 준다"는 기존 규칙은 문구 표시에 관한 것이라 충돌하지 않는다. **재시도가 안전한 이유:** `UNAUTHORIZED`는 필터 단계에서 컨트롤러 실행 전에 거부된 요청이라, POST·DELETE를 재시도해도 두 번 실행되지 않는다 | 백엔드가 `UNAUTHORIZED` 외의 코드로 토큰 무효를 알리게 되거나(예: 컷오프를 별도 코드로 응답), 비즈니스 로직이 `UNAUTHORIZED`를 쓰게 되면 다시 본다. 컨트롤러가 실행된 뒤 401을 내는 경로가 생기면 재시도 안전성도 다시 본다 |
| **재발급 흐름과 세 조건** | 실패한 요청의 Access Token과 저장소 값이 다르면 재발급 없이 저장소 값으로 재시도한다. 같으면 `refreshSession`(탭 안 single-flight) → `refreshAcrossTabs`(Web Locks, 락 안에서 저장소를 다시 읽음) → `refreshTokens`(compare-and-set 저장). 재발급이 끝났을 때 세대(`sessionGeneration`, 이제 `session.ts` 모듈 변수)가 바뀌었으면 상태를 건드리지 않고 재시도도 하지 않는다 | 같은 Refresh Token으로 재발급이 두 번 나가면 재사용 탐지로 모든 기기의 세션이 끝난다. 세대를 `AuthProvider`의 ref에서 모듈 변수로 옮긴 이유: 인터셉터는 React 밖에서 돌아 ref를 볼 수 없다 | — |
| **실패 분기** | 재발급이 4xx(만료·폐기·재사용 탐지, 계정 비활성 403 포함)면 락 안에서 조건부로 토큰을 지우고, 로그인·로그아웃과 같은 `advanceSessionGeneration()`으로 세대를 올린 뒤 `setSessionExpiredListener`로 `AuthProvider`에 알려 비로그인으로 바꾼 뒤 원래 401을 던진다. 저장소가 이미 비어 있어도(다른 탭 로그아웃) 같게 처리한다. timeout·네트워크 오류·5xx·락 대기 초과면 토큰을 지우지 않고 그 오류를 던진다(원 요청만 실패). 인터셉터는 화면 이동을 하지 않는다 | 4xx를 전부 세션 종료로 본 것은 기존 복원 로직(`isRejected`)과 같다. 결과를 알 수 없는 실패는 Refresh Token이 아직 유효할 수 있어 지우지 않는다 | 보호 라우트 가드가 아직 없다 — 생기면 가드가 비로그인 상태를 보고 AUTH-01로 보내고 `location.state.from`을 유지한다(기존 규약) |
| **다른 계정 토큰으로 재시도하지 않는다(JWT `sub` 비교, 2026-09-29 PR 전 수정)** | 재시도 직전에 실패한 요청이 보낸 Access Token의 `sub`와 재시도에 쓸 토큰의 `sub`를 비교해, 다르면 재시도하지 않고 원 요청을 실패시킨다. 인증 상태·세대는 건드리지 않는다(`retryIfSameAccount`, `features/auth/session.ts`). 두 경로 모두에 적용한다: 저장소 값이 달라 재발급 없이 재시도하는 경로와 재발급 뒤 재시도하는 경로. JWT는 payload만 base64url로 디코드하고 서명은 검증하지 않으며 새 라이브러리는 쓰지 않는다(`jwtSubject`). 보낸 토큰에서 `sub`를 읽을 수 없으면(JWT가 아니면) 비교를 건너뛴다. 재시도 요청은 비교에 쓴 바로 그 토큰으로 나가도록 config에 고정한다(`_retryAccessToken`, `lib/httpClient.ts` — 요청 인터셉터가 저장소를 다시 읽지 않는다) | 이전에는 다른 탭에서 B로 로그인한 직후 A 탭의 401이 B 토큰으로 재시도돼, A 화면에서 시작한 요청(관심 등록 등)이 B 계정으로 실행됐다. 세대는 탭마다 따로라 막지 못했다. 서명 검증은 서버의 일이고 여기서는 같은 계정인지만 알면 된다. 토큰을 config에 고정하지 않으면 비교와 실제 전송 사이에 저장소가 또 바뀔 수 있다. 검증: 단위 테스트 5건(디코드, 두 경로 × 같은/다른 계정), e2e `auth-interceptor-multitab-check` 교차 계정 시나리오. 비교를 빼면 단위 2건, e2e 2건(상태 200, 요청 토큰 `["A1","B1"]`)이 실패한다(변형 검증) | Access Token에 `sub`가 없어지거나 사용자 식별자가 다른 클레임으로 옮겨지면 비교 대상을 바꾼다 |
| **timeout 예산** | 사용자 동작 1회의 최악 대기 **15초**. 값: 일반 요청 **4초**(`DEFAULT_REQUEST_TIMEOUT_MS`, `httpClient` 기본값), 재발급 **5초**(`REFRESH_TIMEOUT_MS`), 락 대기 **2초**(`LOCK_WAIT_TIMEOUT_MS`). 최악 경로 = 원 요청 4 + 락 대기 2 + 재발급 5 + 재시도 4 = **15초** — 락을 기다리던 탭이 락을 얻고 직접 재발급하는 경우(락을 쥔 탭의 재발급이 빨리 실패해 저장소가 그대로인 경우)까지 포함한다. 락을 쥔 탭 쪽은 4 + 0 + 5 + 4 = 13초 | NFR-1(조회 응답 평균 500ms) 대비 여유를 두고 모바일 저속망을 감안했다. **작업 지시의 권장값(재발급 3초, 락 4초)을 쓰지 않았다** — 락 대기가 재발급 timeout보다 길어져, 결과를 알 수 없게 끝난(서버에서는 이미 교체됐을 수 있는) 재발급 직후 기다리던 탭이 같은 Refresh Token으로 다시 재발급해 재사용 탐지로 전체 로그아웃될 수 있다(위 "락 대기 상한" 행의 불변식). 처음엔 재발급 4초·락 3초로 정했으나 e2e `home01-multitab-refresh-check`의 "멈춤" 시나리오가 실패했다 — 두 값의 차이(1초)가 좁아, 재발급이 시작되고 1초 넘게 지나 락을 기다리기 시작한 탭이 상대의 재발급 timeout 뒤 락을 얻어 같은 토큰으로 다시 재발급했다. 차이를 3초로 넓혔다(정상 재발급은 수백 ms라 락 대기 2초면 충분하다). 이 차이 안에서 락을 기다리기 시작한 탭만 보호된다 — 그보다 늦은 탭은 위 절의 "알려진 한계"다 | NFR-1 변경, 응답이 4초를 넘기는 엔드포인트 추가(그 엔드포인트만 요청별 timeout을 주고 예산을 다시 계산한다), Access Token 수명 변경, 재발급 응답이 평소 2초 가까이 걸리게 되는 경우(락 대기 상한을 늘리면 차이가 줄어든다) |
| **불변식: 락 대기 상한 < 재발급 timeout** | 두 값을 바꿀 때 반드시 지킨다. 지금 2초 < 5초(차이 3초) | 락을 기다리던 탭은 락을 쥔 탭의 재발급이 결과를 알 수 없게(timeout) 끝나기 전에 먼저 포기해야 한다. 반대면 그 탭이 락을 얻어 저장소에 남은 같은 Refresh Token으로 다시 재발급하고, 첫 재발급이 서버에서 이미 교체됐다면 재사용 탐지로 모든 기기에서 로그아웃된다. 차이가 곧 보호 창이다 — 재발급 시작 후 그 안에 기다리기 시작한 탭만 보호된다 | 백엔드가 재사용 유예 구간(아래 남은 위험)을 도입하면 이 불변식의 중요도가 낮아진다 |
| **단위 테스트 러너 vitest 도입** | `vitest` + `jsdom`(devDependency), 설정 `vitest.config.ts`, 실행 `npm test`(`vitest run`). 테스트 파일은 `src/**/*.test.ts` | 이 작업 전에는 프론트엔드 단위 테스트 러너가 없었다. 인터셉터의 경합 조건(동시 401, 재발급 도중 로그아웃·다른 탭 로그인, 재발급 실패 분기)은 e2e로는 타이밍을 확정하기 어려워, axios 어댑터를 바꿔 끼워 결정적으로 검증할 러너가 필요했다. Vite 프로젝트라 설정 공유가 쉬운 vitest를 골랐다. jsdom에는 `navigator.locks`가 없어 탭 사이 락은 e2e가 맡는다. **Windows 주의:** 작업 디렉터리의 드라이브 문자가 대문자(`C:\...`)면 "Vitest failed to find the runner"로 테스트 파일을 하나도 못 불러온다(모듈이 두 경로로 두 번 로드되는 문제로 보인다) — 소문자 `c:\...`에서 실행한다(2026-09-29 확인) | vitest가 이 문제를 고치면 주의 문구를 지운다 |
| **요청별 timeout과의 관계** | 요청별 `timeout`이 기본값을 덮는다(axios 규칙). 재발급 5초·로그아웃 요청 4초(`LOGOUT_REQUEST_TIMEOUT_MS`)·로그아웃 폐기 전체 상한 5초(`LOGOUT_REVOKE_DEADLINE_MS`). 재시도 요청은 원 요청의 config를 그대로 쓰므로 timeout과 `signal`을 이어받는다 | 로그아웃은 전체 상한(5초)이 따로 있어 예산을 넘지 않는다. 재발급 timeout(5초)이 로그아웃 상한과 같아, 로그아웃 중 멈춘 재발급은 로컬 로그아웃과 거의 동시에 클라이언트에서 끊긴다 — 늦은 응답이 오더라도 compare-and-set이 버린다 | — |
| **timeout·네트워크 오류 문구** | 서버 `error.message`가 없는 실패는 `lib/apiError.ts`의 `GENERIC_ERROR_MESSAGE` 하나로 보여 준다(`getErrorMessage`). 화면 5곳에 복사돼 있던 같은 상수를 이 모듈로 모았다 | 문구 정규화 지점이 따로 없어 새로 만들었다 | — |

**남은 위험:** (1) ~~다른 탭 로그인 뒤의 새 요청~~ — **[처리완료 2026-09-30, `feature/frontend/auth-tab-account-sync`] "탭 계정 동기화" 절로 옮겼다.** (2) Web Locks가 없는 환경(구형 브라우저, LAN IP의 http)에서는 탭 사이 직렬화가 없어 두 탭이 동시에 만료되면 재사용 탐지로 로그아웃될 수 있다(위 절의 기존 한계와 같다). (3) **재발급이 클라이언트 timeout으로 끝났지만 서버에서는 Rotation이 적용된 경우**, 저장소에는 옛 Refresh Token이 남는다 — 이후 그 토큰으로 나가는 재발급(다음 401, 다음 로드, 불변식의 보호 창 밖에서 락을 얻은 탭)은 재사용 탐지에 걸려 모든 기기에서 로그아웃된다. 서버가 교체했는지 프론트는 알 수 없어 **프론트만으로는 막지 못한다.** 실패 방향은 로그아웃이라 보안상 안전하다. **백로그 후보(이번에 구현하지 않음):** 백엔드에 짧은 재사용 유예 구간(예: 교체 직후 수 초 안에 같은 옛 토큰이 다시 오면 탈취가 아니라 재시도로 보고 이미 발급한 새 토큰 쌍을 다시 돌려주거나 거부만 하고 전체 폐기는 하지 않음)을 두는 방안 — 유예가 탈취 탐지 창을 넓히는 트레이드오프라 SVC-AUTH-01 재사용 탐지 설계와 함께 따로 결정한다.

검증: 단위 테스트 `src/features/auth/session.test.ts`(vitest, 25건 — 다른 계정 토큰 재시도 금지 5건, 테스트마다 세대 초기화, 재발급 실패 시 세대 증가, 재발급 1회 후 재시도, 동시 401 N건에 재발급 1회, 저장소가 더 새 토큰이면 재발급 0회, 재발급 도중 로그아웃·다른 탭 로그인, 재발급 401·timeout·네트워크 오류·5xx, 재시도 401에 두 번째 재발급 없음, 비즈니스 401·토큰 없는 401·인증 엔드포인트 401, 오류 문구, 세션 복원 3경로). CAS·세대 확인·single-flight·에러 코드 판별·재시도 표시·테스트 간 세대 초기화·재발급 실패 시 세대 증가를 하나씩 빼면 각각 해당 테스트가 실패한다(변형 검증). e2e `auth-interceptor-multitab-check`(백엔드 불필요, 17/17) — 같은 context의 두 탭이 동시에 사용 중 401을 받아도 재발급이 브라우저 전체에서 1회, 두 탭 모두 로그인 유지. 교차 계정 시나리오: 탭 1의 A 요청 응답(401)을 붙잡은 채 탭 2가 B로 로그인하면, 탭 1은 B 토큰으로 재시도하지 않고 401로 끝나며 재발급도 없다. Web Locks를 끄면 R1이 두 번 나가 세션이 지워지며 5건 실패한다. **단위 테스트 러너(vitest)는 이 작업에서 처음 도입했다**(`npm test`, 설정 `vitest.config.ts`, jsdom) — 이전에는 러너가 없었다. **기존 e2e 3개를 고쳤다:** (1) `home01-multitab-refresh-check` "멈춤" — 기다리던 탭이 끝나는 시간 창을 새 락 대기 상한(2초)에 맞췄다(4.5~9초 → 2~6초). 락을 쥔 탭 1에는 경과 시간 단언이 없었다 — 대기 합(5·9·8초)이 넉넉해 예산을 넘겨도 통과했다. 페이지 이동부터 확정까지 사용자 대기 예산 15초 + 여유 1초(16초) 안이어야 한다는 단언을 추가했다(실측 약 6.3초). (2) `home01-logout-hang-check` "늦게 온 재발급 응답" — 재발급 timeout(5초)이 로그아웃 상한(5초)과 같아져, 붙잡힌 재발급이 클라이언트 timeout으로 먼저 끊길 수 있다. "늦은 200이 도착했거나 클라이언트가 요청을 끊었음(`requestfailed`)"으로 바꿨다 — 보호 단언(지운 토큰을 되살리지 않음)은 그대로다. (3) `home01-login-during-restore-check` — 응답 리스너가 도착 시각을 토큰 파싱보다 먼저 기록해, 대기 루프가 파싱 전에 끝나 "B 로그인 응답을 받음"이 간헐 실패했다(이번 변경 전부터 있던 테스트 경쟁 조건). 파싱 뒤에 기록하도록 고쳤다. (4) `srch01-favorite-and-desktop-back-check` — 2페이지 버튼을 누른 뒤 고정 600ms만 기다려, 경기도 전체 검색 응답(수백 ms)이 오기 전의 1페이지 목록을 "2페이지"로 찍고 있었다(재현 확인). 2페이지 응답을 기다린 뒤 찍도록 고쳤다 — 인터셉터와 무관한 기존 테스트 경쟁 조건이다. **e2e 기본 주소를 `frontend/e2e/base.mjs` 한 곳으로 모았다** — SRCH-01 계열 11개 스크립트만 기본값이 5183이라 5173에서 뜬 dev 서버에 접속하지 못했다(README와 다른 스크립트는 5173이 기본이라 5183은 의도된 값이 아니었다).

### 인증 상태 3종 (2026-09-29, `feature/frontend/auth-status-tristate`)

백로그 2번을 구현했다. `AuthContext`는 `status: 'checking' | 'authenticated' | 'anonymous'`만 내놓고, `isAuthenticated`·`authChecking`은 호환용 getter 없이 지웠다(컴파일 에러로 드러난 소비자 5곳 — `Gnb`, `MobileHeader`, `InterestRegionSummary`, `RecentViews`, `useFavoriteToggle` — 을 하나씩 고쳤다). 이 문서의 이전 절들이 말하는 `authChecking`은 이제 `status === 'checking'`이다.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| **분기 방식** | 상태에 따라 동작이 갈리는 곳은 `switch`로 분기하고 default에서 `assertNever`(`lib/assertNever.ts`)를 부른다. 상태를 boolean으로 줄이는 지역 변수(`const isAuthenticated = status === 'authenticated'`)도 만들지 않는다 | 상태가 늘면 처리하지 않은 분기가 컴파일 에러로 드러난다. type-aware lint(`@typescript-eslint/switch-exhaustiveness-check`)는 이 프로젝트에 설정돼 있지 않아 켜지 않았다(lint 설정 범위를 넓히지 않음) | type-aware lint를 도입하면 `switch-exhaustiveness-check`를 함께 켠다 |
| **상태 전이 규칙** | 초기값: 저장소에 Access Token이 있으면 `checking`, 없으면 `anonymous`. `checking → authenticated/anonymous`는 세션 복원 결과로만(세대가 그대로일 때). 로그인·가입 성공 → `authenticated`, 로그아웃 → `anonymous`, 사용 중 재발급이 세션 종료로 끝남 → `anonymous`. 세 전이 모두 먼저 세대를 올리고(`advanceSessionGeneration`), 늦게 끝난 복원·재발급 결과는 세대가 바뀌었으면 버린다. **[2026-09-30 추가]** 다른 탭이 저장소의 계정을 바꾸면 `authenticated/anonymous → checking`(저장소에 토큰이 없으면 `→ anonymous`)으로 다시 확인한다 — 이 전이도 먼저 세대를 올린다("탭 계정 동기화" 절). 그 밖에 `checking`으로 되돌아가는 전이는 없다 | 사용자가 직접 바꾼 세션을 늦게 끝난 비동기 결과가 되돌리지 않게 한다("401 자동 재발급과 요청 timeout" 절의 조건 3) | 다른 이유(예: 탭 복귀 시 재검증)로 `checking`으로 돌아가는 전이를 추가하면 같은 다시 확인 경로(`beginRecheck`)를 쓴다 |
| **`checking`일 때 화면** | 헤더: 로그인 링크도 계정 메뉴도 아닌 자리 표시(`data-testid="account-placeholder"`). 크기는 실측한 비로그인 영역에 맞췄다 — 데스크톱 144×32px(로그인·회원가입), 모바일 35×28px(로그인 링크 35×20과 아바타 28 중 큰 쪽). 보호 라우트: 리다이렉트하지 않고 기다린다. HOME-01 개인화: 관심 지역 요약·관심 매물 API를 부르지 않고 가입 유도 CTA도 띄우지 않는다(로딩 표시) | 확인 중인 로그인 사용자를 비로그인으로 단정하지 않는다. 데스크톱 중앙 메뉴는 우측 영역 너비에 따라 위치가 바뀌므로, 처음 방문하는 대부분의 상태(비로그인)와 같은 너비로 자리를 잡는다. 헤더 높이는 60px 고정이라 세로 이동은 없다 | 헤더 우측 버튼 구성이 바뀌면 자리 표시 크기를 다시 잰다 |
| **`RequireAuth` 가드** | `src/routes/RequireAuth.tsx` 하나. `checking`은 대기(스피너), `anonymous`는 `/login`으로 `replace` 이동하며 `state.from`에 원래 위치를 남기고, `authenticated`는 화면을 그린다. **[2026-10-01 적용] MY-01(`/my`)과 MY-02~05 자리 표시 라우트에 적용했다** — "SCR-MY-01" 절. 화면이 `checkingFallback`으로 자기 스켈레톤을 넘기면 스피너 대신 그것을 보인다. 사용자가 직접 로그아웃·탈퇴한 경우(`signedOutByUser`)는 로그인 화면이 아니라 HOME-01로 보낸다. 관리자 권한 가드는 범위 밖이다 | vitest 6건(세 상태 + 직접 로그아웃 → 홈, 세션 종료 → 로그인, 이미 비로그인으로 진입 → 로그인), e2e `my01-mypage-check` | 보호 화면을 새로 만들면 그 라우트도 감싼다 |

검증: vitest `RequireAuth.test.tsx`(3건), e2e `auth-status-checking-check`(22/22 — 세션 확인 응답을 붙잡은 동안 데스크톱·모바일 헤더에 자리 표시만 있고 로그인 링크·계정 메뉴 없음, 개인화 API 호출 없음, 가입 유도 CTA 없음, 풀면 계정 메뉴로 확정되고 헤더 높이 그대로, 토큰이 없으면 처음부터 로그인 링크). 헤더와 관심 지역 카드가 확인 중을 비로그인처럼 그리게 바꾸면 4건 실패한다(변형 검증). 기존 인증·홈 e2e 9개도 통과했다.

### 탭 계정 동기화 (2026-09-30, `feature/frontend/auth-tab-account-sync`)

"401 자동 재발급과 요청 timeout" 절의 남은 위험 (1)을 닫았다. 토큰 저장소(`localStorage`)는 탭끼리 공유된다. 그래서 탭 2가 B로 로그인한 뒤 A를 보여 주는 탭 1이 새로 보내는 요청(재시도가 아닌 첫 요청)이 처음부터 B 토큰으로 나갔다. 탭 1 화면에서 누른 관심 등록이 B 계정에 실행됐다. 로직은 `features/auth/session.ts`(요청 방어·다시 확인)와 `AuthProvider.tsx`(`storage` 이벤트·상태 전이)에 있다.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| **탭의 계정** | 탭은 자기가 보여 주는 계정의 JWT `sub`(`tabSubject`)와 확정 여부(`tabConfirmed`)를 모듈 변수로 든다. 세션 복원이 성공하고 `/api/users/me`의 `userId`가 저장소 토큰의 `sub`와 같을 때, 또는 로그인·가입 직후에 확정한다(`markTabAuthenticated`). 복원 결과의 사용자가 저장소 토큰의 `sub`와 다르면(복원 도중 다른 탭이 바꿈) 확정하지 않고 다시 확인한다. 세대를 올리면(`advanceSessionGeneration`) 확정이 풀린다 | "401 자동 재발급과 요청 timeout" 절의 재시도 `sub` 비교와 같은 값을 쓴다(payload만 base64url 디코드, 서명 검증 없음 — 서명은 서버가 본다). 실제 백엔드 JWT의 `sub`가 회원 ID 문자열임을 e2e로 확인했다 | Access Token의 사용자 식별 클레임이 바뀌면 비교 대상을 바꾼다 |
| **요청 방어** | `httpClient` 요청 인터셉터가 토큰을 싣기 직전에 검사한다(`setRequestTokenCheck` 등록 방식 — 순환 의존 방지). 확정된 탭이 다른 `sub`의 토큰을 실으려 하면 요청을 보내지 않고 `SessionAccountChangedError`로 거절하고 다시 확인을 시작한다(`beginRecheck`). 같은 계정의 새 토큰(재발급)은 통과한다. `sub`를 읽을 수 없는 토큰은 비교를 건너뛴다 | `storage` 이벤트만으로는 막지 못한다 — 이벤트가 오기 전(또는 이벤트 처리 전)에 누른 요청이 B 토큰으로 나간다. 요청 직전 검사가 유일하게 확실한 지점이다 | — |
| **확정 전(확인 중) 요청** | 탭이 계정을 확정하지 않은 동안(확인 중, 로그인·로그아웃·다시 확인 직후) 토큰이 실리는 요청은 **GET·HEAD, 인증 엔드포인트(`/api/auth/**`), `_accountIndependent` 표시 요청만** 보낸다. 나머지(POST·PUT·PATCH·DELETE — 관심 등록 등 사용자 동작)는 `SessionNotConfirmedError`로 거절한다. 토큰이 없으면 막지 않는다. `_accountIndependent`는 지금 검색 기록(`logSearch`, search_log에 회원 컬럼 없음) 하나에만 붙어 있다 | 확정 전에는 저장소 토큰이 탭 화면의 계정인지 알 수 없다. 세션 복원(`/api/users/me`)·재발급은 확정을 위해 필요하고, 공개 조회(인기 단지·검색 결과)는 확인 중에도 그려야 한다. **GET을 통과시켜도 되는 전제는 두 가지다.** (1) 보호 조회는 호출하는 쪽이 확인이 끝날 때까지 기다린다 — 지금은 소비자마다 `status === 'checking'`을 보고 부르지 않는 규칙("인증 상태 3종" 절)으로 지키고 있다. MY-01·02·04에 `RequireAuth`를 적용하면 그 화면의 조회는 확인 완료 뒤에만 마운트되므로 구조적으로 보장된다. (2) GET은 부수효과가 없다 — 잘못된 계정 토큰으로 나가도 서버 상태를 바꾸지 않고, 그 응답은 다시 확인이 끝나면 버려지거나 새 계정 기준으로 다시 그려진다. 확인 중 하트 클릭은 이미 `useFavoriteToggle`이 판정까지 미루므로 이 거절에 닿지 않는다 — 이 규칙은 미루지 않는 새 소비자를 막는 안전망이다 | 확인 중에도 보내야 하는 쓰기 요청이 생기면 그 요청이 계정과 무관한지 먼저 확인하고 `_accountIndependent`를 붙인다(계정에 따라 결과가 달라지면 붙이지 않는다). 상태를 바꾸는 GET이 생기면(전제 2가 깨짐) GET 통과 규칙을 다시 본다. 보호 조회를 확인 중에 부르는 소비자가 생기면(전제 1이 깨짐) 그 소비자를 고치거나 `RequireAuth` 뒤로 옮긴다 |
| **오류 문구** | 두 오류는 `lib/apiError.ts`에 두고 `getErrorMessage`에서만 처리한다(서버 응답이 없어 클래스의 클라이언트 문구를 그대로 쓴다). 화면별 분기는 없다 — `useFavoriteToggle`도 `getErrorMessage`를 쓰게 바꿨다 | 오류 문구 정규화 지점을 하나로 유지한다 | — |
| **`storage` 이벤트 동기화** | `AuthProvider`가 `localStorage`의 Access Token 키(또는 전체 clear, `key === null`) 변경을 받으면 `syncWithStoredAccount()`로 저장소 계정을 탭의 계정(확정 전이면 마지막으로 관찰한 계정)과 비교한다. 다르면 `beginRecheck()`: 세대를 올리고 확정을 풀고, 진행 중인 복원(`inflight`)을 버린 뒤 저장소에 토큰이 있으면 `checking`으로 바꿔 **기존 복원 경로**를 다시 돌리고, 없으면(다른 탭 로그아웃) 서버 호출 없이 `anonymous`로 바꾼다 | 새 확인 경로를 만들지 않고 복원을 재사용해 세대·재발급 규칙을 그대로 따른다. 로그인 한 번에 이벤트가 두 번(Access·Refresh 키) 오고, 같은 계정의 재발급도 이벤트를 낸다 — 계정 비교로 두 경우 모두 한 번만(또는 전혀) 반응한다. `restoreSession`의 `finally`는 자기가 시작한 promise일 때만 `inflight`를 비운다 — 다시 확인이 시작한 새 복원을 옛 복원의 정리가 지우지 않게 한다 | 토큰 저장소를 탭별(`sessionStorage`)이나 메모리로 옮기면 이 절 전체를 다시 본다 |
| **계정별 화면 상태는 확인 중에 버린다(Codex P2, 2026-09-30)** | 계정마다 달라지는 화면 상태를 들고 있는 소비자는 `checking`에 들어갈 때 그 상태와 "무엇 기준으로 채웠는지" 표시를 함께 버린다. 대상(2026-09-30 전수 확인): `useFavoriteToggle`(하트 상태 `favorites`와 `hydratedFor`), `InterestRegionSummary`(관심 지역 `regions`·`loading`), `RecentViews`(최근 조회 `views`·`loading`), `AuthProvider`(`user` — 다시 확인 리스너가 비운다). `UserMenu`는 확인 중에 헤더가 자리 표시로 바뀌어 언마운트되므로 상태가 남지 않는다. 검색 결과(`SearchResultsPage`의 누적 목록·스크롤 캐시)는 공개 데이터라 대상이 아니다. 알림·알림 설정·마이페이지는 프론트 화면이 아직 없다 | 다른 탭의 계정 변경은 `authenticated(A) → checking → authenticated(B)`로 가서 상태 값이 전과 같다. `useFavoriteToggle`은 `hydratedFor`가 `'authenticated'` 그대로 남아, B로 확정되는 순간 미룬 클릭이 B의 목록을 받기 전에 A의 하트 상태로 처리됐다 — A의 `favoritePropertyId`로 DELETE를 B 토큰으로 보낼 수 있었다. 두 홈 카드는 `loading`이 `false`로 남아 B 확정 첫 커밋에 A의 관심 지역·최근 조회가 그려졌다 — 상태 변경이 사용자 이벤트가 아니라 비동기 복원에서 오므로 `useEffect`가 칠한 뒤에 돌아 한 프레임 보였다. 검증: vitest `useFavoriteToggle.test.tsx`, `accountScopedState.test.tsx`(커밋마다 DOM을 기록하는 레이아웃 effect 탐침으로 B 확정 첫 커밋을 본다 — `act()`는 useEffect까지 끝낸 뒤 돌려주므로 탐침 없이는 이 결함이 드러나지 않았다). 수정을 빼면 각각 실패한다 | 계정별 상태를 들고 있는 소비자를 새로 만들면 같은 규칙을 따른다(`checking`에서 버림). MY-01·02·03·04를 구현하면 그 화면의 상태도 이 목록에 넣는다. 상태 대신 계정 식별자(`user.userId`)로 버전을 매기는 방식으로 바꾸면 이 규칙을 다시 본다 |
| **확인 중 하트 클릭은 의도로 재생** | 확인 중에 누른 하트는 클릭 순간 보이던 하트 기준으로 의도를 기록한다(빈 하트 → 등록, 채워진 하트 → 해제). 판정과 목록 도착 뒤, 로그인이면 현재 상태가 의도와 다를 때만 요청을 보내고 같으면 보내지 않는다. 비로그인이면 등록 의도만 로그인 화면으로 넘겨 로그인 후 재생한다(`resolveDeferredClick`, `useFavoriteToggle.ts`). 계정 전환 뒤 확인 중과 첫 로딩 확인 중 모두 같다 | 확인 중에는 하트가 비어 보인다(위 행). 토글로 재생하면 이미 등록된 단지의 빈 하트를 누른 사용자에게 본 적 없는 해제 요청이 나갔다 — 의도는 "채우기"였다. 검증: vitest 3건(계정 전환·첫 로딩에서 이미 등록된 단지 → 요청 0건, 미등록 → 등록 1회), e2e `home01-auth-checking-actions-check` 2번 시나리오(실 백엔드: 클릭 순간 빈 하트, 요청 0건, 서버 등록 유지, 하트 채워짐). 의도 비교를 토글로 바꾸면 vitest 2건이 DELETE로, e2e 3건이 실패하고 서버에서 관심 매물이 실제로 해제된다 | 확인 중에도 하트 상태를 보여 주게 되면(예: 캐시된 목록) 채워진 하트 클릭은 해제 의도가 된다 — 규칙은 그대로 적용된다. 로그인 후 재생(`homesense.pendingFavoriteComplexId`)은 이 규칙 밖이다 — 아래 남은 한계 참고 || **옛 세션의 진행 중 요청** | 다시 확인 전에 시작된 요청(옛 세션의 복원·재발급)이 늦게 끝나도 상태를 바꾸지 않는다 — 세대가 올라가 결과를 버리고, 재발급 결과 저장은 compare-and-set이라 B의 토큰을 덮거나 지우지 않는다 | "401 자동 재발급과 요청 timeout" 절의 조건 2·3과 같은 장치다 | — |

검증: vitest `src/features/auth/tabAccountSync.test.tsx`(17건 — 요청 방어 4, 확정 전 요청 6, `syncWithStoredAccount` 4, `AuthProvider` 3). 변형 검증: 요청 방어(`sub` 비교)를 빼면 3건, `storage` 리스너를 빼면 2건, 확정 전 차단을 빼면 2건 실패한다. 기존 `session.test.ts`의 비즈니스 401 PUT 테스트는 전제가 로그인 사용자라 탭을 확정한 뒤 보내도록 고쳤다. e2e `auth-tab-account-sync-check`(실 백엔드+Redis, 21/21 — `page.route`는 확인 중 구간을 붙잡는 응답 지연에만 쓴다): 탭 1의 `storage` 이벤트를 막은 채 탭 2가 B로 로그인하고 탭 1에서 하트를 누르면 요청이 서버에 가지 않고(서버에서 A·B 관심 매물 모두 비어 있음) 안내 토스트 뒤 탭 1이 B를 보여 줌, 이벤트를 받으면 자리 표시를 거쳐 B로 확정, 탭 2 로그아웃으로 탭 1 비로그인. `sub` 비교를 빼면 4건 실패하고 B 계정에 관심 매물이 실제로 등록된다. **기존 e2e 1개를 고쳤다:** `home01-login-during-restore-check`의 "저장 전 인증 요청" 탐침이 `import('/src/lib/httpClient.ts')`로 모듈을 불러왔는데, dev 서버 실행 중 파일이 바뀌면 앱은 `httpClient.ts?t=…`를 쓰므로 탐침은 인터셉터가 연결되지 않은 별도 인스턴스가 되어 곧바로 401을 받고 통과했다(develop에서 확인 — 앱의 클라이언트를 전혀 검증하지 않았다). 모듈이 새것이면 탐침이 앱 인스턴스가 되어, 테스트가 탐침이 끝난 뒤에야 풀어 주는 A 재발급에 합류해 5초 재발급 timeout까지 멈췄고, "마지막 `/me`"로 요청을 찾는 방식이 로그인 뒤의 B `getMe`를 집어 실패했다. 앱이 실제로 불러온 모듈 URL을 import하고, 탐침 요청에 표식(`?probe=`)을 붙여 보낸 순간의 토큰을 확인하며, 결과는 재발급을 풀어 준 뒤에 받도록 바꿨다(52/52). `login()`에서 `storeTokens`를 기다리지 않는 변형은 여전히 실패한다(예전 9건 → 2건 — 탭 계정 확정 규칙이 그 경로의 일부를 흡수한다). 같은 전체 실행에서 `srch01-mobile-check`의 "뒤로가기 후 누적 목록 30개 이상"이 1회 실패했다 — 비로그인 검색 화면이라 이 변경과 무관하고 3회 재실행은 모두 통과했다(원인 미확정).

**남은 한계:** (1) 로그인 후 재생(비로그인 하트 클릭 → 로그인 → 자동 등록)은 목록을 불러오기 전에 바로 등록을 보낸다. 이미 등록된 단지면 서버가 409로 알려 주고 토스트가 뜬다 — 의도 재생 규칙은 적용하지 않았다(이번 범위는 확인 중 클릭). (2) `storage` 이벤트를 받기 전, 확인 중이 아닌 탭이 보내는 **GET**은 `sub` 비교로 막히지만(확정된 탭), 탭이 확정 전일 때의 GET은 저장소 토큰으로 나간다 — 조회라 상태를 바꾸지 않고, 그 응답은 다시 확인이 끝나면 버려지거나 새 계정 기준으로 다시 그려진다.

### 서버가 뺀 null 필드 처리 (2026-10-05, `fix/frontend/null-omitted-fields`)

**규약: 서버 응답의 nullable 필드는 생략될 수 있으므로 타입은 `?: T | null`, 검사는 `!= null`. e2e 목업은 공용 헬퍼(`frontend/e2e/mockApi.mjs`의 `okBody`/`errBody`)로 null 키를 뺀다.** 값 하나를 그리는 포매터(`formatKoreanPrice`·`formatArea`·`formatChangeRate` 등)는 undefined를 받지 않는다 — 호출하는 쪽에서 값 유무를 판단하고 없을 때의 표시를 정한다. 예외로 "있는 부분만 잇는" 헬퍼(`formatAddress`의 시군구·동리, `describeDealAmount`의 rentType·monthlyRentAmount)는 이미 null을 "부분 없음"으로 받던 매개변수라 undefined도 같은 뜻으로 받게 넓혔다. 빈 값 표시는 MY-02 기준: 거래 없음은 "거래 없음", 변동률이 없으면 "—"(스크린리더 "변동 정보 없음").

**nullable 응답 필드 목록(백엔드 DTO 기준, null이 되는 조건):**

| DTO(프론트 타입) | 필드 | null이 되는 조건 |
| --- | --- | --- |
| `ComplexSummaryResponse` | `sido`·`sigungu`·`dongRi`·`householdCount`·`buildingCount`·`approvalDate` | complex 컬럼이 NULL 허용(단지 기본정보 원본 미기재). 2026-10-05 로컬: sigungu 216건(세종), dongRi·buildingCount 9건 |
| 〃 | `matchMethod`·`floor` | 대표 거래의 매칭 방식·층이 없을 때(컬럼 NULL 허용, 로컬 데이터에는 아직 없음) |
| 〃 | `rentType`·`monthlyRentAmount` | 대표 거래가 매매면 항상 없음 |
| `ComplexDetailResponse`(+`BasicInfo`·`ExtendedInfo`) | 단지 컬럼 대부분, `legalDongCd`, `matchMethod`, 좌표 | 단지 컬럼 NULL 허용, 매칭 대기 단지, 대표 거래 없음, 지오코딩 전 — 이미 전부 선택 필드였다 |
| `TradeResponse`·`TradeDetailResponse` | `rentType`·`floor`·금액 3종·`dealingType`·`cancelDate`·`aptDong`·등기일자·매도/매수자·`landLeaseYn` | 매매/전월세 구분(전월세는 거래유형·등기·해제·동이 원천에 없음), 미해제 — 이미 선택 필드였다 |
| `RecentViewResponse` | `sido`·`sigungu`·`dongRi` | 단지 컬럼 NULL 허용(price·area·floor도 내려오지만 화면이 안 써 타입에 없음) |
| `InterestRegionSummaryResponse` | `avgPrice` | 최근 1개월 매매 0건 |
| 〃 | `changeRate` | 최근 1개월 또는 직전 1개월 매매 0건, 직전 평균 0 |
| `FavoritePropertySummaryResponse` | `sido`·`sigungu`·`dongRi` | 단지 컬럼 NULL 허용 |
| 〃 | `recentDealCategory`·`recentDealDate`·`recentAmount`·`recentArea`·`recentFloor`·`changeRate` | 취소되지 않은 매매 거래 없음(층은 그 거래에 층이 없을 때도), 변동률은 위와 같은 조건 — 이미 선택 필드였다 |
| `FavoriteRegionSummaryResponse` | `sidoName`·`sigunguName`·`avgPrice`·`changeRate`·`pricePerPyeong` | 법정동 이름 NULL 허용, 최근 1개월 매매 0건 — 이미 선택 필드였다 |
| `NotificationResponse` | `message`·`complexId`·`legalDongCd`·`tradeId` | 컬럼 NULL 허용, 알림 대상에 따라 일부만 채움 |
| `NotificationSettingResponse` | `favoritePropertyId`·`favoriteRegionId` | 둘 중 하나만 채움 — 이미 선택 필드였다 |

항상 있는 것(바꾸지 않음): 대표 거래의 금액(`TradeFieldMapper`가 매매 dealAmount·전월세 deposit을 필수로 받는다 — 컬럼은 NULL 허용이지만 로컬 0건)·면적·날짜, `FavoriteRegionSummaryResponse.eupmyeondongName`(등록 시 읍면동 행만 허용), 사용자·인증 응답 전부. `TradeSummaryResponse`(`GET /api/trades/search`)는 프론트 소비처가 없어 타입이 없다.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| 바꾼 타입 | `ComplexSummaryResponse` 10개 필드, `RecentViewResponse` 3, `InterestRegionSummaryResponse` 2, `FavoritePropertySummaryResponse` 3, `NotificationResponse` 4를 `?: T \| null`로. tsc가 3개 파일 9곳을 찾았다(`ComplexCard` 4, `InterestRegionSummary` 4, `RecentViews` 1) | 위 목록 | 백엔드가 `non_null`을 끄면(모든 응답 모양이 바뀐다) 이 규약을 다시 본다 |
| tsc가 못 잡은 곳 | `ComplexCard`의 `floor !== null`(템플릿 문자열 `${floor}층`, 두 variant), `approvalDate.slice(0, 4)`(값이 없으면 런타임 오류 — 타입을 바꾸자 tsc가 잡았다). 앱 코드의 `=== null`·`!== null` 전수(2026-10-05): 응답 값을 직접 비교하는 곳은 위 둘과 `favoritesModel.compareRate`뿐이었고, 후자는 호출부가 `changeRate ?? null`로 바꿔 넘겨 안전했다 | grep | — |
| HOME-01 관심 지역 카드 빈 값 | 평균가가 없으면 "거래 없음", 변동률이 없으면 배지 자리에 "—"(회색, 스크린리더 "변동 정보 없음"). 두 값을 따로 판단한다 — 예전엔 둘 중 하나만 없어도 둘 다 숨겼다("데이터 없음") | Figma에 빈 값 모양이 없어 MY-02 지역 카드와 같은 방식(지시) | Figma에 빈 상태가 생기면 따른다 |
| MY-01 최근 알림 행 | `message`가 없으면 `title`을 보인다(`message`는 NULL 허용 컬럼, `title`은 NOT NULL) | 빈 행이 그려지는 것을 막는다. 알림 데이터가 아직 없어(BAT-NTF-01 미구현) 화면에 나타난 적은 없다 | BAT-NTF-01이 title/message 내용을 정하면 다시 본다 |
| e2e 목업 | `mockApi.mjs`(`dropNulls`·`okBody`·`errBody`)로 모았다 — my01·my02·dtl01·srch01·auth-interceptor·auth-status-checking·modal-widths·스크린샷 2개. 회원가입·AUTH-03 스크립트는 목업에 nullable 데이터 필드가 없어(최상위 data/error null뿐) 그대로 뒀다 | 목업이 null을 그대로 보내면 회귀를 못 잡는다(MY-02에서 처음 발견) | — |

검증: vitest `ComplexCard.test.tsx`(4)·`InterestRegionSummary.test.tsx`(3) — develop의 두 컴포넌트로 되돌리면 5건 실패. e2e `home01-null-fields-check`(신규, 18/18 — 되돌리면 12건 실패, "−NaN% NaN만원"·"· undefined층"), `srch01-basic-check`에 층 없는 결과 카드 케이스(1280/390, 6건). 실 백엔드: 역삼동(최근 1개월 매매 0건)을 관심 지역으로 등록하면 HOME-01 카드가 "거래 없음"·"—"(1280/390, 응답에 avgPrice·changeRate 키 없음 확인). vitest 135건, tsc, lint(기존 경고 3건), 빌드, run-all 39/39(크래시 없음) 통과.

### 반응형 렌더 규칙 — 화면 크기별 두 벌 렌더 금지 (2026-09-29)

**새 화면은 컴포넌트 트리 하나로 렌더한다.**
- 배치 차이는 CSS 미디어 쿼리(Tailwind 반응형 접두사)로 처리한다. 브레이크포인트는 UI정의서 6.1절을 따른다.
- 구조가 정말 달라야 하면 `useMediaQuery` 같은 훅 하나로 판단해 **한 벌만** 렌더한다.
- `display:none`(`hidden md:block` 등)으로 숨긴 두 번째 트리도 금지한다. 숨겨도 DOM과 React 트리에는 그대로 있다.

**이유:**
- 두 트리의 이펙트와 API 호출이 중복된다.
- id가 중복돼 `label`·`aria-describedby` 연결이 깨진다(NFR-8 접근성). 라디오 그룹은 name까지 겹쳐 서로 간섭한다.
- 같은 역할의 요소가 두 개라 Playwright strict mode에서 locator가 여러 요소에 걸려 실패한다(지금 테스트들은 `:visible`로 우회하고 있다).
- 숨은 쪽의 폼 상태가 보이는 쪽과 따로 움직인다.

**무효화 조건:** 서버 렌더링 도입 등으로 첫 렌더에서 화면 크기를 알 수 없어 한 벌 렌더가 깜빡임을 만들면, 그 화면에 한해 다시 판단하고 여기에 기록한다.

### SCR-MY-01 마이페이지 홈 (2026-10-01, `feature/frontend/mypage`)

UI정의서 v2.1 5.5절 MY-01(FR-1.4)을 구현했다. 경로 상수는 `src/routes/paths.ts`의 `MY_ROUTES`, 화면은 `src/pages/my/`이다.
- 프로필·관심 매물·최근 알림을 동시에 따로 불러(`useLoadable`) 한 영역의 실패가 다른 영역을 막지 않는다.
- 탈퇴 유예 일수는 `features/user/withdrawalPolicy.ts`의 `WITHDRAWAL_GRACE_DAYS` 하나를 방침과 탈퇴 안내가 함께 쓴다.

**착수 전 확인(2절):**
- (a) 401 인터셉터·인증 상태 3종은 develop에 있다.
- (b) `UserResponse.createdAt`이 이미 있어 백엔드를 고치지 않았다.
- (c) `WithdrawRequest`는 password(필수, 재확인)와 reason(선택)을 받지만, reason은 `toCommand()`가 넘기지 않아 저장되지 않는다.
- (d) `FavoritePropertySummaryResponse`에 전용면적·등록일시가 없고, 목록 쿼리에 ORDER BY가 없다.
- (e) `GET /api/notifications`는 `ApiResponse.success(Page<T>)` 관례를 따르고 sentAt 내림차순이며, `isRead`가 있다.
- (f) `/my`·`/favorites`·`/notifications`가 자리 표시로 있었다. 헤더 계정 메뉴에 "마이페이지"가 있었고 하단 탭 "마이"는 `/my`였다. 경로 상수 파일은 없어 새로 만들었다.
- (g) `Modal` + `useDialogBehavior`(포커스 트랩·Esc·트리거로 포커스 복귀)가 있다.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| 계정 타일 → 선택 다이얼로그 | "로그아웃·회원탈퇴" 타일은 버튼이다. 누르면 "로그아웃 / 회원탈퇴 / 취소" 선택 다이얼로그를 연다. 로그아웃은 확인 없이 바로 실행하고, 헤더 계정 메뉴와 같은 `useLogoutAction`을 쓴다(로그아웃 경로를 두 벌로 만들지 않음) | 타일 하나에 두 동작이 묶여 있어 무엇을 할지 먼저 고르게 했다 | Figma에 로그아웃·탈퇴가 따로 정의되면 |
| 직접 로그아웃·탈퇴 후 이동 | `AuthContext.signedOutByUser`(로그아웃·`endSession`에서 true, 로그인·가입·복원 성공에서 false — 2026-10-03부터 그 밖의 경로는 낮추지 않는다, 아래 후속)와 가드의 "로그인 상태를 본 적이 있는가"(`sawAuthenticated`, 렌더 중 상태 조정)를 함께 본다. 둘 다 참이면 `RequireAuth`가 HOME-01로 replace한다. 그 외 비로그인은 기존대로 `/login` + `state.from`이다. 화면은 직접 navigate하지 않는다 | 보호 화면에서 로그아웃한 사람을 "로그인하면 돌아온다"는 로그인 화면으로 보내면 안 된다. 헤더 메뉴로 보호 화면에서 로그아웃해도 같은 규칙이 적용된다. 예전에 로그아웃한 채로 하단 탭 "마이"를 누르면 플래그는 남아 있지만 가드가 로그인 상태를 본 적이 없어 로그인 화면으로 간다. 검증: vitest 6건, e2e 시나리오 5·6. 두 조건 중 하나를 빼면 각각 vitest 1건이 실패하고, 홈 이동을 끄면 e2e 20건이 실패한다(변형 검증). **[2026-10-02, Codex P2] 로그아웃 의도는 서버 폐기를 기다리기 전에 기록한다(`AuthProvider`의 `pendingLogouts`).** 로그아웃은 세대를 올린 뒤 서버 폐기를 최대 5초 기다리는데, 그사이 401을 받은 GET의 재발급이 거부되면 세션 만료 알림이 `becomeAnonymous(false)`를 먼저 불러 가드가 로그인 화면으로 이동하고 언마운트됐다 — 뒤늦은 `becomeAnonymous(true)`로는 되돌릴 수 없었다. 로그아웃이 진행 중이면 어느 경로로 비로그인이 돼도 `signedOutByUser`를 true로 둔다. **[2026-10-03, Codex P2] 다시 확인 경로도 같다** — 로그아웃이 서버 폐기를 기다리는 동안 다른 탭이 토큰을 지우면 storage 이벤트 → `syncWithStoredAccount` → `beginRecheck`의 리스너가 `becomeAnonymous`를 거치지 않고 직접 비로그인으로 바꾸므로, 이 리스너도 `pendingLogouts`를 본다. 로그아웃 중이 아니면 기존대로 false(로그인 화면). **[2026-10-03 후속] 비로그인 전환 경로를 `becomeAnonymous` 하나로 모으고, 기록은 한 번 true가 되면 다음 로그인 상태 확정(로그인·가입·복원 성공) 전까지 낮추지 않는다.** 다시 확인 리스너도 `becomeAnonymous(false, next)`를 부른다(`next`가 'checking'이면 비로그인 대신 확인 중으로 두고 `checkRound`를 올린다. 리스너 경로에서 `markTabUnauthenticated()`가 한 번 더 돌지만 `beginRecheck`가 같은 동기 흐름에서 이미 불러 결과가 같아 끄지 않았다). 순서가 반대인 경우가 있었다: 로그아웃 중 다른 탭이 토큰을 지우고 곧바로 B로 로그인하면 다시 확인이 B로 복원을 시작하는데, 이 탭의 로그아웃 응답이 먼저 와 로컬 로그아웃을 끝낸(진행 중 로그아웃 0건) 뒤 B 응답이 같은 렌더 안에서 오면, 저장소가 비어 B로 확정하지 못하고 다시 확인 → 리스너가 기록을 false로 덮어 가드가 로그인 화면으로 갔다. 복원 성공에서 기록을 내리는 이유: 내리지 않으면 직접 로그아웃 뒤 다른 탭의 로그인으로 이 탭이 B로 확정되고 B 세션이 만료될 때 로그인 화면이 아니라 홈으로 간다(전에는 리스너가 false로 내려 로그인 화면이었다 — 그 동작을 유지). 검증: vitest `logoutIntent.test.tsx` 5건(재발급 경로, 다시 확인 경로, 순서가 반대인 경우, 복원 성공 시 기록 내림, 로그아웃 중이 아닐 때 기존 동작) — 89e50c5 코드에서는 순서 반대 1건만 실패, 기록 유지를 빼면 같은 1건, 복원 성공 시 내림을 빼면 복원 테스트 1건이 실패한다(변형 검증) | 로그아웃 뒤 이동 정책이 화면별로 달라지면 |
| 탈퇴 후 세션 정리 | `AuthContext.endSession()`은 세대를 올리고 로컬 토큰을 지운 뒤 비로그인으로 전환한다. `/api/auth/logout`은 부르지 않는다 | 서버(`UserService.withdraw`)가 이미 Refresh Token을 모두 폐기했고 `user:status`를 WITHDRAWN으로 썼다 | 탈퇴 API가 토큰을 폐기하지 않게 바뀌면 |
| 탈퇴 확인 다이얼로그 | `role="alertdialog"`, 안내는 `aria-describedby`로 연결한다. 순서: 처리 안내 → "안내 사항을 확인했습니다" 체크 → 비밀번호 재확인 → [탈퇴하기]. 체크하고 비밀번호를 입력해야 버튼이 활성화된다. 실패하면 서버 `error.message`를 다이얼로그 안에 보이고 비밀번호 칸으로 포커스를 돌린다. 열 때마다 새로 마운트해 이전 입력이 남지 않는다. 안내 문구는 방침 2항과 코드에 있는 사실만 쓴다(즉시 로그인 불가·토큰 폐기, 7일 보관 후 매일 새벽 자동 파기, 파기 대상, 재로그인으로 복구되지 않음, 같은 이메일 재가입은 파기 뒤) | 비밀번호 불일치는 401 `INVALID_CREDENTIALS`(비즈니스 401)라 인터셉터가 재발급하지 않는다 | — |
| 탈퇴 비밀번호 불일치 응답(2026-10-01 확인, 백엔드 변경 없음) | `DELETE /api/users/me`와 `PUT /api/users/me`의 비밀번호 불일치는 둘 다 `UserService`가 던지는 `InvalidCredentialsException` → **401 `INVALID_CREDENTIALS`**("비밀번호가 일치하지 않습니다")다. 프론트 401 인터셉터는 `error.code === 'UNAUTHORIZED'`일 때만 재발급하므로(`refreshableAccessToken`) 이 응답에는 재발급·재시도·세션 정리 중 아무것도 하지 않는다. 그래서 백엔드 브랜치(`feature/backend/password-confirm-status`, 400 전용 코드로 바꾸기)는 만들지 않았고, `WithdrawRequest.reason` 제거도 그 브랜치에 묶여 있어 하지 않았다 | 충돌이 실제로 생기는 조건이 아니다(인터셉터 판별 기준이 상태가 아니라 에러 코드). 검증: vitest `session.test.ts` "탈퇴 비밀번호 불일치"(재발급 0회·DELETE 1회·세대 불변·만료 리스너 미호출·토큰 유지·서버 문구), 인터셉터의 코드 판별을 빼면 이 테스트와 기존 PUT 테스트가 실패한다(변형 검증). e2e `my01-mypage-check` 시나리오 6(실제 백엔드와 같은 401 바디, 재발급 0회, 헤더 로그인 유지, 다이얼로그 안 메시지) | 인터셉터가 상태 코드(401)만으로 재발급하게 바뀌거나, 다른 클라이언트·프록시가 401을 세션 만료로 취급하게 되면 400 전용 코드로 바꾸는 백엔드 작업을 다시 연다. 실 백엔드로는 아직 확인하지 않았다(아래 완결 필요) |
| 탈퇴 사유 미수집 | 사유 입력란을 두지 않는다. 요청 본문은 password만 보낸다 | 서버가 받아도 저장하지 않는다(user 테이블에 컬럼 없음). 쓰지 않는 데이터는 모으지 않는다 | 사유 저장 컬럼·API가 생기면 |
| 탈퇴 다이얼로그에서 철회 미안내 | 철회 가능성을 안내하지 않는다 | `POST /api/auth/reactivate`는 있지만 호출하는 화면이 없고, 방침도 철회를 안내하지 않는다 | 철회 화면이 생기면(`/privacy` 2항과 함께 갱신) |
| MY-02~05 자리 표시 | `/favorites`(MY-02), `/notifications/settings`(MY-03, 새 경로), `/notifications`(MY-04), `/my/profile`(MY-05, 새 경로)를 보호 라우트 아래 "준비 중" 화면(`MyPreparingPage`, MainLayout + 마이페이지로 돌아가기)으로 둔다. **부수 효과:** 비로그인으로 GNB "관심목록"·하단 탭 "찜"·"알림"을 누르면 이제 로그인 화면으로 간다(전에는 공개 자리 표시였다) | MY-01 메뉴 링크가 404나 빈 화면이 되지 않게 한다. 세 화면 모두 로그인이 필요하다 | 해당 화면을 구현하면 |
| 미리보기의 하트·알림 읽음 | 관심 매물 행의 하트는 표시 전용(`aria-hidden`)이고, 해제는 MY-02의 일이다. 최근 알림은 읽음 처리(PATCH)를 하지 않는다 | UI정의서 MY-01에 해제·읽음 처리가 정의돼 있지 않다 | UI정의서가 바뀌면 |
| 관심 매물 "최근 등록순" | `favoritePropertyId` 내림차순 상위 2건(`pickRecentFavorites`) | 응답에 등록일시가 없고 목록 쿼리에 정렬이 없다. ID는 AUTO_INCREMENT이고 등록 시각은 저장 시점이라 ID 순서가 등록 순서와 같다. 행에는 응답에 있는 가격만 쓰고(전월세는 "보증금"), 면적은 응답에 없어 생략한다 | 응답에 등록일시나 면적이 추가되면 |
| 알림 점 색 **[2026-10-01 변경]** | 한 색(Primary)으로 통일하고 읽음 여부는 MY-01에서 표시하지 않는다. 상대 시간은 공용 `lib/relativeTime.ts`(서버 LocalDateTime을 KST로 읽음, 7일 이상은 날짜) | Figma(지성 전달)에서 점 색은 알림 대상의 종류다 — 관심 매물 = Primary, 관심 지역 = amber. 하지만 응답으로 대상을 가를 수 없다: `NotificationResponse`의 `complexId`·`legalDongCd`·`tradeId`는 상호 배타가 아니고(엔티티·DTO 주석: 신규 거래 알림은 complex와 trade가 함께 채워질 수 있다), 알림을 만드는 BAT-NTF-01이 없어 대상별로 어떤 조합이 오는지 정한 계약도 없다(2026-10-01 코드 확인 — `Notification` 생성 호출부 0건). 처음 구현(읽지 않음 = 브랜드, 읽음 = 회색)은 Figma와 의미가 달라 바꿨다. 검증: e2e `my01-mypage-check`(점 3개가 모두 Primary) | 알림 응답에 대상 종류(예: `favoritePropertyId`/`favoriteRegionId` 또는 대상 유형 필드)가 생기거나 BAT-NTF-01이 대상별 필드 조합을 정하면 두 색으로 나눈다 — MY-04를 구현할 때 같은 규칙을 쓴다. **[2026-10-06] BAT-NTF-01이 조합을 정했다(D7): 관심 매물 알림은 `complexId`가 있고, 관심 지역 알림은 `complexId` 없이 `legalDongCd`만 있다 — 두 색으로 나눌 수 있게 됐다(프론트 후속)** |
| 프로필 오류 | 401이 아닌 실패만 상단 배너(서버 메시지 + 다시 시도)로 알린다. 401은 가드가 로그인 화면으로 보내므로 배너를 띄우지 않는다 | UI정의서 MY-01 예외 처리 | — |
| 확인 중 화면 | `RequireAuth`에 `checkingFallback`을 추가했다. MY-01은 레이아웃이 같은 스켈레톤(`MyPageSkeleton`)을 넘긴다 | 확인 중에 리다이렉트하지 않고 화면 모양을 유지한다 | — |
| 레이아웃·토큰(2026-10-01 Figma 대조 반영) | 한 벌 렌더(반응형 렌더 규칙). 지성이 Figma 7:5373·26:15193·26:14966에서 추출한 값을 그대로 썼다: 본문 최대 폭 1000(패딩 포함)·패딩 48/32·섹션 간격 28(모바일 24/16·20), 보이는 제목 "마이페이지" 26/39(모바일 22/33) ExtraBold, 카드 radius 16·테두리 #f3f4f6·그림자 0 1px 4px 5%(프로필만 6%), 섹션 제목(메뉴·관심 매물·최근 알림)은 카드 밖 위 14/21 Bold #99a1af(모바일 "메뉴"만 13/20·간격 8), "전체 보기"는 13/20 SemiBold brand + 화살표 14. 메뉴는 md(768) 이상 4열 타일(간격 16, 여백 32/16, 아이콘 칸 48/16·#e8f2f0, 라벨 13/18 #1c1c1e), 모바일은 카드 하나 안 1열 목록(행 16/20, 아이콘 칸 36/14, 라벨 14/21 + chevron). 위젯은 md 이상 2열(간격 24)로 바꿨다(처음엔 xl 이상). 목록 행 16/20·간격 14·구분선 #f3f4f6, 썸네일 56×42/14, 단지명 13/20 Bold, 가격 12/18 #99a1af. 하단 탭 여백은 MainLayout(`pb-16`). 검증: e2e `my01-mypage-check`(제목 크기·아바타·메뉴 열·위젯 열·라벨 색), 스크린샷 `my01-screenshots` | UI정의서 6.2절의 "2열→1열"은 Figma와 달라 Figma를 따랐다(코드 주석에만 기록). `ArrowRightIcon`(14)·`ChevronRightIcon`(16)은 Figma 크기와 같아 그대로 썼다 | Figma가 바뀌면 |
| Figma와 의도적으로 다르게 둔 것 | (1) 로그아웃 타일 라벨 #6a7282(Figma #9ca3af는 흰 배경 대비 2.54:1, 지성 결정). (2) 모바일 프로필의 가입일을 보인다(Figma는 숨김 — UI정의서 필수 항목, 기존 결정). (3) 데스크톱·태블릿 부제(14/21 #99a1af)를 넣지 않았다 — 문구를 받지 못했다. (4) 최근 알림 행은 Figma처럼 한 줄 문구라 `message`만 보이고 `title`은 쓰지 않는다 — BAT-NTF-01이 없어 두 필드의 내용이 정해지지 않았다. **[2026-10-05] `message`가 없으면(NULL 허용 컬럼, 키 생략) `title`을 대신 보인다.** (5) "전체 관심 매물 보기" 뒤의 개수 표시를 없앴다(Figma에 없음, 함께 쓰던 `FavoritePreview.total`도 제거). (6) 위젯 섹션 제목 앞 아이콘(하트·종)을 없앴다(Figma는 글자만) | — | (3) 부제 문구를 받으면 넣는다(모바일은 숨김). (4) BAT-NTF-01이 title/message 내용을 정하면 다시 본다 — **[2026-10-06] 정해졌다(D3·D4): title은 "{대상명} 신규 실거래 3건"·"{대상명} 실거래가 2.1% 상승"처럼 한 줄 요약, message는 거래·평균가 상세다. 한 줄 문구로는 title이 맞아 MY-01 행을 title로 바꿨다(같은 날, BAT-NTF-01 PR). 시각도 `sentAt` 대신 `createdAt`** |
| **대비 미달(결정 필요)** | Figma 회색 #99a1af 글자를 그대로 썼다: 이메일·가입일, 가격 줄, 알림 상대 시간(흰 카드 위 2.60:1), 섹션 제목(페이지 배경 #f7f8fa 위 2.45:1). NFR-8 기준 4.5:1에 못 미친다. 로그아웃 라벨처럼 올릴지(예: #6a7282 4.84:1) 지성 결정이 필요하다 — 같은 #99a1af가 다른 화면에도 63곳 쓰여 화면 단위가 아니라 공통 결정이 낫다 | 대조값은 지성이 준 Figma 값이고, 로그아웃 라벨만 명시적으로 바꾸라고 했다 | 결정이 나면 이 행을 바꾼다 |

**문서와 다른 점(코드 주석에만 기록, 문서 동기화 필요):** UI정의서 6.2절 MY-01 그리드 표기("2열→1열", 실제는 Figma대로 4열→1열). 프로그램설계서 3.2절 하단 메모 "MY-01 회원정보 수정은 인라인/모달"(UI정의서 v2.1은 MY-05 이동). 프로그램설계서 3.2절 `withdraw()` 처리 로직에 비밀번호 재확인 단계(`PasswordEncoder.matches()`)와 불일치 시 `InvalidCredentialsException`(401 `INVALID_CREDENTIALS`)을 반영해야 한다 — 지금 설계서에는 이 단계가 없다.

**완결 필요:**
- **Figma 대조 미실시(2026-10-01에도 Figma MCP 미연결).** 7:5373·26:15193·26:14966과 참조 프레임(6:4995·6:5286·38:1526)을 보지 못했다. 색·간격·위젯 배치는 기존 화면(HOME-01·DTL-01)의 토큰으로 맞췄다. **아이콘은 2026-10-01 지성이 전달한 Figma SVG로 교체했다** — 메뉴 `MenuHeartIcon`·`MenuBellIcon`(20px Figma 버전, 공용 `HeartIcon`·`BellIcon`과 크기·획이 달라 따로 둠)·`BellRingIcon`·`LogOutIcon`, "회원정보 수정" 버튼 `PencilIcon`(14px). 모두 `currentColor`·`aria-hidden`. 색: 메뉴 3개 `brand`(Primary), 로그아웃 `#9ca3af`, 연필 `#4a5565` — 색 토큰은 `brand`뿐이라 나머지 둘은 코드베이스가 이미 쓰는 같은 hex를 그대로 썼다. e2e가 장식 여부와 색을 검사한다. 하단 탭 라벨은 코드 "찜", Figma "관심"으로 다르다(보고만 함, 공용 UIC-02라 바꾸지 않았다).
- **[처리완료 2026-10-02, "MY-02 백엔드 보강" 절] ~~MY-02 선행 — 관심 매물 요약 응답 확장(백엔드).~~** UIC-05 카드가 요구하는 최근 거래의 전용면적·층·거래일을 `FavoritePropertySummaryResponse`에 추가하고, 목록 정렬(등록순)을 서버에서 명시한다(지금 `findByUser_UserId`에 ORDER BY가 없다). 들어오면 MY-01 미리보기에 면적을 붙이고 클라이언트 ID 정렬을 걷어낸다(`FavoritePreview.tsx`의 TODO).
- **`docs/specs/` 부재 원인(2026-10-01 확인).** 무시 규칙이 아니다(`git check-ignore` 결과 없음, `.gitignore`에 docs 항목 없음). 워크트리는 하나뿐이고, 원격 `develop`·`main`과 로컬의 모든 브랜치·태그 이력에 `docs/specs` 경로가 한 번도 없다(`git log --all -- docs/specs` 0건, `ls-remote`로 원격 헤드가 로컬과 같음을 확인). 명세 최신본이 아직 커밋된 적이 없다는 뜻이다 — Downloads에는 프로그램설계서 xlsx만 있다.
- **방침 6항 갱신 필요.** "마이페이지를 통한 회원정보 수정·탈퇴 자기서비스 기능은 준비 중"이라는 문구는 MY-01 배포 뒤 탈퇴에 대해서는 사실이 아니다(수정은 MY-05 미구현이라 여전히 사실). 개정 문구와 시행일(개정 이력 v1.4, 실가입이 있으면 7일 사전 고지)은 지성이 정한다 — 이번에 고치지 않았다.
- **하단 탭 "마이" 미읽음 배지.** 여전히 없다. 미읽음 개수 API가 없다(SCR-HOME-01 절 "알림 벨" 행).
- **실 백엔드 확인 미실시(2026-10-01에도 Docker 꺼짐).** (1) `home01-logout-check` — 헤더 계정 메뉴의 로그아웃을 `useLogoutAction`으로 옮겼다. (2) 탈퇴 수동 확인: 비밀번호 틀림 → 세션 유지, 맞음 → 탈퇴 후 HOME-01, 같은 계정 로그인 거부(403 `ACCOUNT_WITHDRAWN`). 다음에 백엔드를 띄우면 이것부터 한다.

검증: `npm run lint`(오류 0), `tsc -b`, `npm run build`, vitest 93건(로그아웃 중 세션 만료·다른 탭 토큰 삭제·순서 반대 → 홈, 복원 성공 시 기록 내림, `relativeTime`·`pickRecentFavorites`·`RequireAuth`·탈퇴 비밀번호 불일치 포함). e2e `my01-mypage-check` 285/285(390/768/1280, 백엔드 불필요 — 브라우저가 중간에 죽는 환경에서는 `VIEWPORTS=모바일` 처럼 뷰포트별로 나눠 실행한다). 2026-10-03 실 백엔드로 `auth-tab-account-sync-check` 21/21, `home01-logout-check` 29/29, `my01-mypage-check` 285/285(뷰포트별 97·94·94). 회귀 확인: `auth-status-checking-check` 22/22, `auth-interceptor-multitab-check` 17/17.

### SCR-MY-02 관심 매물·지역 관리 (2026-10-02, `feature/frontend/favorites`)

UI정의서 v2.1 5.5절 MY-02(FR-5.1~5.3)를 구현했다. 화면은 `src/pages/favorites/`, 경로는 `MY_ROUTES.favorites`(`/favorites`, 보호 라우트). 선행 백엔드는 "MY-02 백엔드 보강" 절(`feature/backend/favorite-summary-support`, 이 브랜치는 그 위에 있다). 확인 문서: 작업 지시에 옮긴 UI정의서 5.5절·프로그램설계서 3.7절 원문. Figma MCP가 연결되지 않아 처음엔 MY-01·DTL-01 토큰으로 만들었고, **같은 날 후속 작업에서 지성이 Figma 파일에서 뽑아 준 9개 노드의 수치로 대조·보정했다**(아래 "Figma 대조" 표). 화면에서 프레임을 직접 열어 나란히 본 것은 아니다.

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| D1 반응형 구조 | 1280px 이상은 관심 매물 → 관심 지역 → 관심 지역 추가를 세로로 쌓고 정렬은 chip(radiogroup), 그 아래는 "관심 매물 / 관심 지역" 탭(tablist, 화살표·Home·End)과 정렬 `<select>`. `useMediaQuery(MEDIA_XL_UP)`로 한 벌만 렌더한다 — 지역 목록은 탭이 선택되지 않으면 DOM에 없다. 카드 컴포넌트는 한 벌 공유. 공용 훅 `lib/useMediaQuery.ts`를 새로 두고 SRCH-01의 인라인 훅도 이것으로 바꿨다 | Figma가 태블릿·모바일에서 일관되게 탭을 쓴다. 반응형 렌더 규칙 | UI정의서가 모바일을 탭 없는 단일 스크롤로 정하거나 Figma가 바뀌면 |
| D2 매물 카드 **[2026-10-02 Figma 대조로 변경]** | 한 컴포넌트(`PropertyCard`)가 화면 크기별 Figma 카드의 합집합을 담고, 화면이 넘긴 `layout`(모바일·태블릿·데스크톱)으로 배치 한 벌만 그린다: 썸네일(모든 크기 — 데스크톱 60×46, 태블릿 90×70, 모바일 64×64)·주택유형·단지명·"85㎡ · 9층"·최근 매매가·변동률·등록일·알림조건 배지·삭제. 처음 넣었던 주소와 "날짜 거래"는 어느 Figma 프레임에도 없어 뺐다. 데스크톱 행은 Figma에 없는 주택유형·면적·층을 등록일 줄에 글자로 붙였다(합집합 유지). UIC-05는 확장하지 않았다(하트 토글과 삭제 동작이 달라 분기가 늘어난다) | 알림조건 배지는 명세 필수이고 MY-03 진입 경로라 모바일·태블릿에도 둔다(Figma는 데스크톱에만 있다). 처음엔 "모바일 썸네일 숨김"으로 지시받았으나 Figma 세 크기 모두에 썸네일이 있어 정정됐다 | — |
| D3 삭제 버튼 | 모든 크기에서 텍스트 버튼 "삭제", `aria-label="{단지명} 관심 매물 삭제"`(지역은 "{읍면동} 관심 지역 삭제"). 태블릿 "상세보기"는 만들지 않고 카드 전체 링크로 대신한다 | 데스크톱 Figma에 삭제가 없고, 텍스트 라벨이 발견성·접근성에 낫다 | — |
| D4 알림조건 배지 | `GET /api/notifications/settings`를 함께 불러 `favoritePropertyId`로 조인한다(SVC-NTF-01 설계 그대로, 백엔드 변경 없음). 문구: 설정 없음 "알림 설정"(흰 바탕·브랜드 외곽선), 이메일 꺼짐 "알림 꺼짐"(회색), 임계치+신규거래 "±5% · 신규거래", 임계치만 "±2.5% 알림"(브랜드색 바탕 — Figma에 있는 모양은 이것뿐). 10px 글자라 세 변형 모두 4.5:1 이상인지 확인했다: 설정됨 흰 글자/#0f5c54 7.84:1, 미설정 #0f5c54/흰 바탕 7.84:1, 꺼짐 #4a5565/#f3f4f6 6.87:1. **[2026-10-02 변경] 알림 설정 목록을 아직 받지 못했거나 조회가 실패하면 배지를 숨긴다**(`resolveNotificationBadge`, 매물 목록은 그대로). 처음엔 실패 시 `hasNotificationSetting`만으로 "알림 설정됨"/"알림 설정"을 보였는데, 그 값으로는 "알림 꺼짐"을 가릴 수 없고 로딩 중에 문구가 바뀌어 보였다 | Figma 문구("지역변동 9%")는 데이터와 대응하지 않는 더미다. 임계치는 DB에서 NOT NULL이라 "신규거래만"은 없다 — 임계치 0이면 "±0% 알림"으로 보인다(MY-03이 0을 허용). 숨김 검증: vitest `resolveNotificationBadge`, e2e 시나리오 8(설정 조회 500 → 목록 4건·배지 0개·"알림 설정" 문구 없음). 옛 대체 동작으로 되돌리면 e2e가 badges=4로 실패한다(변형 검증) | MY-03이 임계치 0의 의미를 따로 정하면 문구를 다시 본다 |
| D5 정렬 | 화면에서 정렬하고 한 상태가 두 목록에 같이 적용된다. 등록순 = `registeredAt` 내림차순(같으면 ID 내림차순), 변동률순 = 내림차순·null 맨 뒤·같으면 등록순. 정렬·탭은 URL `?sort=rate`·`?tab=regions`에 replace로 남긴다(기본값은 생략) | 단지 상세·검색 결과에서 뒤로 오면 같은 탭·정렬 | — |
| D6 지연 삭제 | `usePendingDeletion`: 확인 다이얼로그(공용 `Modal` alertdialog, "삭제하시겠어요?" / "{이름}을(를) 삭제하면 해당 항목의 알림도 함께 중지됩니다.") → 곧바로 숨김(섹션·탭 개수도 줄어든다) → "삭제했어요 · 실행취소" 토스트 5초 → 지나면 DELETE. 실행취소는 숨김만 풀어 현재 정렬대로 원래 자리에 돌아온다. `FAVORITE_NOT_FOUND`는 성공으로 보고, 그 밖의 실패는 되살리고 서버 문구로 오류 토스트. 대기 중 다른 항목을 지우면 앞 건을 즉시 DELETE(토스트는 마지막 하나). 화면을 떠나면(언마운트) 대기 건을 즉시 DELETE하고 결과를 기다리지 않는다(실패는 console.warn). 5초 타이머는 토스트가 가진다 — 마우스를 올리거나 키보드 포커스가 있으면 멈춘다(WCAG 2.2.1) | 프로그램설계서 3.7절: 실행취소는 프론트가 DELETE를 5초 늦춰 구현한다. **한계(의도):** 탭 닫기·새로고침 때 대기 건은 삭제되지 않는다 — 인증 헤더가 필요한 DELETE를 pagehide에서 확실히 보낼 방법이 없고(keepalive fetch는 axios 인터셉터·재발급을 거치지 않는다), 남는 쪽이 데이터를 잃는 쪽보다 안전하다. 다른 탭의 계정 변경으로 화면이 내려가면 언마운트 확정이 일어나지만, 그때는 탭이 계정을 확정하지 않은 상태라 요청 인터셉터가 DELETE를 보내지 않는다("탭 계정 동기화" 절) | 백엔드에 복원(undo) API나 소프트 삭제가 생기면 |
| 공용 토스트 확장(UIC-07) | `showToast(message, variant, { action, durationMs, onExpire })`가 id를 돌려주고 `dismissToast(id)`를 새로 뒀다. 버튼이 있는 토스트만 마우스·포커스 중 멈춘다. 버튼 없는 토스트는 예전처럼 3초(vitest로 확인). 버튼을 누르거나 dismiss로 닫히면 `onExpire`를 부르지 않는다 | 다른 화면의 동작을 바꾸지 않으려고 멈춤을 버튼 있는 토스트로 한정했다 | — |
| D7 관심 지역 추가 | `RegionAdder` — 자동완성 후보를 고른 경우에만 "추가" 활성. 후보는 법정동코드 기준 읍면동 단위만(`isEupmyeondongCode`: 읍면동 자리 ≠ 000, 리 자리 = 00 — 서버 400 규칙과 같다). 디바운스·취소는 UIC-03에서 뽑은 `features/region/useRegionAutocomplete.ts`를 검색창과 같이 쓴다(SearchBar를 이 훅으로 바꿨다). **[2026-10-05, Codex P2] 훅은 응답을 `{query, items}`로 보관하고, 보이는 후보·활성 후보·"결과 없음"을 그 검색어가 지금 입력(trim)과 같을 때만 계산한다** — 전에는 응답을 받을 때 후보를 화면 상태에 옮겨 두어, 입력을 바꾼 뒤 디바운스·요청 동안 이전 검색어의 후보가 남아 클릭이나 남은 활성 후보의 Enter로 무관한 지역이 선택됐다(검색창도 같았다). 선택 직전 `isVisible`로 한 번 더 막고, 늦은 응답은 취소에 더해 저장 시점에 검색어를 비교해 버린다(순서 역전은 옛 코드에서도 abort가 막아 재현되지 않았다). 고른 뒤 입력을 고치면 선택이 풀린다. 후보 위 Enter = 선택, 선택 상태 Enter = 추가. 2자 이상인데 걸러진 후보가 없으면 "지역을 찾을 수 없습니다", 목록에 있는 코드면 요청 없이 "이미 등록된 지역입니다"(서버 409도 서버 문구 그대로), 안내는 aria-live. 성공하면 정렬을 등록순으로 되돌리고 목록을 다시 받아 새 항목이 맨 위, 입력 초기화, 토스트. 같은 지역이 삭제 대기 중이면 추가 전에 그 삭제를 먼저 확정한다. 지역 카드에는 읍면동명 위에 시도·시군구를 보인다. 태블릿·모바일은 관심 지역 탭 맨 위, 데스크톱은 섹션 맨 아래 | 같은 이름의 동이 여러 시군구에 있다(Figma에는 읍면동명만 — 의도적 이탈) | — |
| D8 빈 상태 | 두 목록이 모두 비면 안내 카드("아직 등록한 관심 매물·지역이 없어요" + "매물 둘러보기" → `/`) 아래에 관심 지역 추가 입력을 남긴다. 한쪽만 비면 그 섹션(탭) 안에 짧은 문구 | 명세의 빈 상태는 "리스트 영역"이고, 추가 입력이 MY-02에서 지역을 등록하는 유일한 경로다(Figma 빈 상태에는 입력이 없다 — 의도적 이탈) | — |
| D9 이동 | 매물 → `/complexes/{complexId}`, 지역 → `/search?regionCode=…&regionLabel=…`(SRCH-01 `serializeSearchParams`), 알림 배지 → **MY-03 URL 계약 `/notifications/settings?favoritePropertyId={id}`** — MY-03을 구현할 때 이 대상을 사전 선택해야 한다(지금은 "준비 중" 화면). 카드 전체 클릭은 제목 링크의 `::after`가 카드를 덮는 방식이라 링크 안에 버튼이 없다(삭제·배지는 위 레이어). DTL-01 관심 등록 완료 토스트에 "목록 보기"(→ MY-02)를 달았다(`useFavoriteToggle({ listLinkInToast: true })`, 홈·검색 카드 토스트는 그대로). 하단 탭 "찜"은 `/favorites`에서 활성 | 명세 접근 경로 | — |
| D10 변동률 표기 | 라벨 **"직전 1개월 대비"**(작업 지시의 "전월 대비"가 아님) — 실제 계산이 최근 1개월 매매 평균 vs 그 직전 1개월 매매 평균(롤링)이라 달력상 지난달 비교로 읽히지 않게 했다(HOME-01이 "이번 달" 대신 "최근 1개월"을 쓴 것과 같은 이유). 상승 ▲ 빨강(#e7000b)·하락 ▼ 파랑(#155dfc)(HOME-01 관심 지역 카드와 같은 색), 0.0 → "변동없음", 값 없음 → "—". 스크린리더에는 "직전 1개월 대비 2.1% 상승" 문장 | 가격은 공용 `formatKoreanPrice`, 최근 거래가 라벨은 매매만이라 "최근 매매가" | 변동률을 달력월 기준으로 바꾸면 "전월 대비"로 되돌린다 |
| 서버가 null 필드를 뺀다(실 백엔드 확인에서 발견) | `spring.jackson.default-property-inclusion=non_null`이라 값이 없는 필드는 null이 아니라 **키 자체가 없다**(undefined). MY-02가 쓰는 관심 매물·지역·알림 설정 타입의 nullable 필드를 선택 필드(`?: T \| null`)로 바꿔 TypeScript가 `undefined`를 강제하게 했고, 검사는 `== null`로 한다. MY-01 미리보기의 `recentAmount === null`도 같은 이유로 고쳤다(매매 거래가 없는 단지에서 "NaN만원"이 됐을 것). e2e 모킹 서버도 null 필드를 빼도록 바꿨다 | 처음 e2e는 null을 명시적으로 보내 이 버그를 가렸다. 실 백엔드에서 매매 거래가 없는 단지(행복주택)로 화면이 깨지는 것을 보고 찾았다. 변형 검증: `!= null`을 `!== null`로 되돌리면 e2e가 "전용 NaN㎡"로 실패 | — |

**Figma 대조(2026-10-02, 지성이 추출한 수치 기준 — 데스크톱 6-4695/6-4995/6-5286, 태블릿 28-16161/29-16508/29-16879, 모바일 27-15395/28-15713/28-16055).** 고친 것은 그대로 맞췄고, 아래는 맞추지 않은 것과 이유다.

| 요소 | Figma | 적용 | 이유 |
| --- | --- | --- | --- |
| 모바일 삭제 글자·아이콘 색 | #d1d5dc(흰 바탕 약 1.5:1) | #6a7282(약 4.8:1) | MY-01 로그아웃 라벨과 같은 처리 — 되돌리기 어려운 동작의 버튼이 거의 안 보인다 |
| 모바일 삭제·알림 배지 터치 영역 | 35×15 | 보이는 크기는 그대로, `::before`로 세로 45px 이상 | UI정의서 6.3절 44px. e2e가 버튼 중심 위아래 20px 지점을 같은 버튼이 받는지 본다 |
| 변동률 색 | 상승 데스크톱 #ef4444·태블릿 #fb2c36, 하락 #2b7fff | 상승 #e7000b·하락 #155dfc | 같은 뜻의 색을 하나로 — HOME-01 관심 지역 카드의 기존 값 |
| 변동률 라벨 | "전월 대비" | "직전 1개월 대비" | D10 |
| 모바일 라벨 열 | 60px 고정 | 가장 긴 라벨에 맞춘 열(grid auto) | "직전 1개월 대비"(11px)가 60px에 들어가지 않는다. 두 줄의 값 시작 위치는 맞춘다(e2e로 확인) |
| 최근 거래가 라벨 | "최근 거래가" | "최근 매매가" | 값이 매매 거래만이다(D10, 백엔드 보강) |
| 삭제 다이얼로그 크기 **[2026-10-03 수정]** | 1280px 이상(6-4995): 오버레이 40%, 폭 400, 안쪽 32, 그림자 0 16px 48px 20%, 아이콘 56px 원 아래 20, 제목 18px ExtraBold, 본문 14px lh 22.75 위 10, 버튼 영역 위 28·간격 12·높이 47. 그 아래(29-16508·28-15713): 45%, 320, 24, 0 20px 60px 25%, 48px radius 16 아래 16, 16px Bold, 13px lh 21.125 위 8, 위 24·간격 10·높이 43 | 그대로(공용 `Modal`과 `MODAL_FOOTER_ROW_CLASS`, 경계 1280px) | 처음엔 공용 Modal 값(그림자 24 등)이 Figma에서 왔다고 보고 토큰을 우선했는데 틀렸다 — **Figma 파일 전체에서 다이얼로그는 MY-02 세 프레임뿐**이라 공용 Modal의 근거도 이 프레임이다(DTL-01이 "6:5261·28:15995"로 적은 근거 노드는 확인되지 않는다). 이 값은 공용 Modal의 **확인형**에만 적용한다(아래 행). 버튼은 모든 크기에서 가로다 — 처음엔 방향이 수치에 없어 768px 미만을 세로로 뒀는데, 28-15713·29-16508도 가로 auto-layout(간격 10, 두 버튼이 약 130×43으로 폭을 나눔)이라 2026-10-03에 고쳤다. 취소 버튼 테두리 1px만큼 확인 버튼이 좁아지지 않게 확인 버튼에 투명 테두리를 둔다 |
| 공용 Modal 확인형/내용형 **[2026-10-03 신설]** | Figma 근거는 MY-02 다이얼로그 세 프레임뿐 | `Modal` `variant`: **확인형(`confirm`)** = MY-02 프레임 값, 경계 1280px — MY-02 매물·지역 삭제, MY-01 "로그아웃·회원탈퇴" 선택(제목·짧은 본문·버튼). **내용형(`content`, 기본)** = 34bbba3 이전 값(폭 320 → 768px 이상 400, 그림자 0 20px 30px / 0 16px 24px, 제목→본문 16) — DTL-01 거래상세(항목 목록), MY-01 회원탈퇴(안내 목록·체크박스·비밀번호 폼). 버튼 클래스도 `CONFIRM_*` / `MODAL_*`로 나뉜다 | 34bbba3에서 MY-02 값을 Modal 전체에 적용하자 거래상세와 회원탈퇴 폼이 768·1024px에서 320px로 줄었다(`modal-widths.mjs`로 측정). 확인형 값의 근거는 MY-02 프레임이고, 내용형은 Figma 근거가 없어 기존 값을 유지한다. 선택 다이얼로그는 세 버튼을 세로로 쌓는 선택지 목록이라 확인형이어도 버튼은 세로다. 검증: e2e dtl01 "768px 거래상세 모달은 320px보다 넓음", my02 "확인 다이얼로그 버튼 가로(390 포함)" |
| 다이얼로그 문구 | 1280px 이상 "삭제하시겠어요?" / "{이름}을(를) 삭제하면 해당 항목의 알림도 함께 중지됩니다.", 그 아래 "관심 매물 삭제" / "{이름}를 관심 매물에서 삭제할까요?" | 데스크톱 문구 하나 | 삭제하면 알림 설정이 FK CASCADE로 실제로 지워지므로 크기와 상관없이 알린다. 모바일 제목은 지역 삭제에 쓸 수 없다 |
| 다이얼로그 삭제 버튼 | 1280px 이상 #ef4444 Bold, 그 아래 #fb2c36 SemiBold | #e7000b, 굵기는 Figma대로(1280px 이상 Bold) | 흰 글자 대비 #ef4444 3.76:1, #fb2c36 3.81:1 < 4.5:1, #e7000b 4.77:1. 취소 글자색은 Figma대로 #4a5565 / #364153 |
| 다이얼로그 정렬 | 지정 없음 | 왼쪽 정렬(공용 모달) | `Modal`의 `icon` prop으로 아이콘 상자를 제목 위에 둔다 |
| 모바일 하단 여백 | 56px | 64px(`MainLayout` `pb-16`) | 토큰 우선 — 하단 탭 높이에 맞춘 공용 값 |
| 모바일 앱 바 | 뒤로·제목·종 아이콘, 공용 헤더 대신 | 공용 모바일 헤더 아래에 뒤로·제목만 | 공용 헤더는 DTL-01·MY-01도 유지했다. 종 아이콘은 UI정의서에 헤더 알림 진입점이 없어 HOME-01에서 뺀 것과 같은 판단 |
| 모바일 주택유형 배지 | 9px Bold, 2/6 | 10px SemiBold, 2/8(`DataTrustBadge` sm) | 토큰 우선 — 카드 배지 공용 컴포넌트 |
| 데스크톱 행 | 단지명·배지·등록일·가격·변동률 | + 주택유형·면적·층(등록일 줄), 삭제 버튼 | D2 합집합, D3 |
| 지역 행 | 읍면동명·평균가·신규거래 | + 시도·시군구, 변동률, 삭제. 등록일은 넣지 않음 | D7, D3. 변동률은 HOME-01 관심 지역 카드와 같은 정보 |
| 빈 상태 | 안내 카드만 | 아래에 관심 지역 추가 유지 | D8 |
| 부제 | 데스크톱·태블릿 문구가 다름 | 데스크톱 문구로 통일, 모바일은 없음 | 지시 |
| 아이콘 **[2026-10-03 원본으로 교체]** | Figma 원본 SVG(지성 추출) | 원본: `TrashIcon`(24, 선 2)·`TrashSmallIcon`(12, 선 1)·`MapPinIcon`(16)·`EmptyHeartIcon`(36, 선 2.25)·`SortLinesIcon`(14). 종 10px·CTA 화살표 16px·드롭다운 화살표 14px는 기존 `BellIcon`(18)·`ArrowRightIcon`(14)·`ChevronDownIcon`(16)을 줄이거나 키운 값이 원본과 좌표·선 굵기까지 같아 그대로 쓴다 | 휴지통·핀은 직접 그린 것이었고, 하트(기존 15px 확대 시 선 3)·정렬(SRCH-01 슬라이더 도형)은 빌려 쓴 아이콘이 원본과 달랐다. clipPath는 넣지 않았다 |
| 태블릿 카드 오른쪽 아이콘 | 채워진 하트 16px #ff6467 + "상세보기" | 삭제 버튼 | D3 — 이 자리는 삭제 버튼으로 대체 |

**완결 필요(MY-02에서 등록):**
- **Figma 대조 — 남은 것은 프레임과 화면 스크린샷을 나란히 놓고 보는 육안 대조 하나다.** 수치 대조·아이콘 원본 교체·다이얼로그 값은 2026-10-02~03에 끝났다. Figma에 프레임이 없는 상태(알림 미설정·꺼짐 배지, 태블릿·모바일 지역 카드)는 위 표에 판단을 적었고 대조 대상이 아니다. 스크린샷 스크립트: `frontend/e2e/my02-screenshots.mjs`(→ `out/my02-*.png`, 매물·지역 삭제 다이얼로그 포함).
- ~~**다른 화면의 같은 null 생략 버그(이번에 고치지 않음):** HOME-01 `InterestRegionSummary`의 `region.avgPrice !== null && region.changeRate !== null`(거래 없는 지역에서 "NaN만원"), `ComplexCard`의 `complex.floor !== null`(층 없는 대표 거래에서 "· undefined층"). 각각 `!= null`로 바꾸고 타입을 선택 필드로 정리한다. 전체 응답 타입을 한 번에 점검하는 편이 낫다.~~ **[처리완료 2026-10-05, `fix/frontend/null-omitted-fields`] "서버가 뺀 null 필드 처리" 절.** 2026-10-05 develop 통합 검증의 실 백엔드 시나리오(역삼동 관심 지역 → HOME-01 "−NaN% NaN만원")로 재현됐다.
- **MY-01 미리보기 정리** — 이제 응답에 `recentArea`·`registeredAt`이 있어 `FavoritePreview`의 TODO(면적 표시, ID 정렬 대신 등록일시)를 처리할 수 있다. 이 브랜치에서는 null 검사만 고쳤다.
- **지역 자동완성 상위 10건 한계** — "MY-02 백엔드 보강" 절 참고.

검증(Modal 확인형/내용형 분리·버튼 가로 배치 후, 2026-10-03): vitest 115건, e2e `my02-favorites-check` 159/159, `dtl01-detail-check` 102/102(실 백엔드 포함), `my01-mypage-check` 282/282. 검증(원본 아이콘·다이얼로그 보정 후, 2026-10-03): vitest 115건, e2e `run-all` 전 목록 + AUTH-03 단독 3개 모두 통과 — `my02-favorites-check` 156/156(다이얼로그 크기 검사 추가), `dtl01-detail-check` 101/101·`my01-mypage-check` 282/282(공용 `Modal` 변경 회귀). 검증(Figma 보정 후): vitest 115건, e2e `my02-favorites-check` 153/153(데스크톱 49·태블릿 50·모바일 54 — 썸네일 크기, 모바일 라벨 열·삭제 터치 영역·글자색, 설정 조회 실패 시 배지 숨김 추가), `my01-mypage-check` 282/282(공용 `Modal` 회귀). 처음 검증: vitest 114건(정렬·배지·읍면동 판정·SRCH-01 링크, `usePendingDeletion` 가짜 타이머 8건 — 실행취소·5초·연속 삭제·언마운트·404·실패 복원·hover/focus 멈춤, 버튼 없는 토스트 3초). 변형 검증: 토스트 멈춤 제거, 연속 삭제 시 앞 건 확정 제거, 언마운트 확정 제거 각각 1건씩 실패. e2e `my02-favorites-check` 144/144(뷰포트별 실행 — 아래 환경 메모). 실 백엔드(로컬 MariaDB·Redis, 이 브랜치 백엔드)로 새 계정 → 관심 매물 등록 → 응답 필드 확인, 리·시군구·시도 코드 POST 400 `INVALID_REGION_LEVEL`, 화면 렌더(매매 거래 없는 단지 포함)를 확인했다.

**환경 메모(2026-10-02):** 이 세션 후반에 Playwright `newPage`가 간헐적으로 "Target crashed"로 실패했다(같은 시점에 Git Bash도 `fork: Permission denied`). 사소한 페이지는 열리고 실패 지점이 매번 달라 코드 문제가 아니라 프로세스 생성 문제로 판단했다. e2e를 뷰포트별 프로세스로 나눠(`VIEWPORTS=…`) 돌렸다.

### 기술 부채 — 반응형 두 벌 렌더 기존 사용처 (2026-09-29 목록화, 이번에 고치지 않음)

위 규칙 이전에 만든 곳이다. 해당 화면을 다시 열 때 한 벌 렌더로 합친다.

| 위치 | 두 벌의 내용 | 드러난 문제 |
| --- | --- | --- |
| `components/layout/MainLayout.tsx:15-19` | 헤더: `Gnb`(`hidden md:block`)와 `MobileHeader`(`md:hidden`)를 둘 다 렌더 | 두 헤더가 각자 `useAuth`를 구독하고, e2e가 `header:visible`로 골라야 한다. 인증 상태 3종 작업에서 자리 표시도 두 곳에 넣어야 했다 |
| `pages/search/SearchResultsPage.tsx:276-278, 457-459` | 필터: 데스크톱 사이드바의 `FilterPanel`(`hidden md:block`)과, 모바일 `BottomSheet` 안의 `FilterPanel`(열렸을 때) | 시트가 열린 동안 `FilterPanel`이 두 벌이라 id·name이 겹친다(라디오 그룹 충돌, 백로그 3번의 원래 사례) |
| `pages/home/RecommendedComplexes.tsx:67, 79` | 추천 단지: 모바일 가로 스크롤(`md:hidden`)과 데스크톱 그리드(`hidden md:grid`)에 같은 카드 목록을 두 번 렌더 | 하트 버튼이 카드 수의 두 배로 DOM에 있어 e2e가 `:visible`로 골라야 한다(SCR-SRCH-01 절 참고) |

참고: `pages/search/SearchResultsPage.tsx:305`(모바일 전용 필터·지도 버튼 행)와 `components/layout/BottomTabNav.tsx`(모바일 하단 탭)는 같은 내용의 두 번째 트리가 없는 **한쪽 전용 요소**라 이 목록에 넣지 않았다.

### SCR-LEGAL-01 개인정보처리방침 (신규 제안 — 법률 자문 대체 아님, 배포 전 변호사 검토 권장)

**이 화면도 API-SEARCH-01과 같은 성격의 신규 제안이다 — 요구사항정의서·UI정의서·프로그램목록서 어디에도
정의돼 있지 않다.** 「개인정보 보호법」이 서비스 출시 전 반드시 게시하도록 요구하는 법정 필수 문서라
HOME-01/AUTH-02 완료 이후 별도로 추가했다. 완료 후 프로그램목록서 3장 총괄표에 SCR-LEGAL-01로
신규 기록이 필요하다.

**구현**: `src/pages/legal/PrivacyPolicyPage.tsx`(레이아웃+목차, `MainLayout`으로 감쌈) +
`src/pages/legal/privacyPolicySections.tsx`(13개 항목의 실제 문구, 데이터 배열) +
`src/pages/legal/privacyPolicyPrimitives.tsx`(문단/목록/표 등 최소 프레젠테이션 컴포넌트) 세
파일로 분리했다 — 문구만 고칠 때는 `privacyPolicySections.tsx` 하나만 건드리면 된다. 목차는
페이지 상단의 앵커 링크 카드이고(사이드바 sticky 같은 새 레이아웃 패턴을 만들지 않았다), 클릭 시
`document.documentElement.style.scrollBehavior`를 이 페이지 마운트 동안만 `smooth`로 켜서 자연스럽게
스크롤되게 했다(전역 CSS에 걸면 다른 화면까지 영향을 받는다).

**법률 자문 대체 아님.** 이 문서는 실제 코드베이스를 확인해 사실관계를 채웠지만 법률 전문가의 검토를
거치지 않았다 — 배포 전 변호사 검토를 권장한다(`privacyPolicySections.tsx` 최상단 주석에도 동일하게
남겨뒀다). 처리하는 개인정보의 범위가 바뀌는 시점(3단계 지도, 4단계 알림 확장, 결제 기능 도입 등)에는
반드시 문서 전체를 다시 검토해야 한다.

| 항목 | 브리핑 전제 | 실제 확인 결과 | 반영 |
| --- | --- | --- | --- |
| **카카오맵 API의 개인정보 제3자 제공 여부** | "카카오맵 API는 지도 표시 기능만 사용하며 회원 식별 정보를 전송하지 않는다는 점을 확인 후 반영"이 전제였다 | 코드베이스를 확인한 결과 카카오맵 SDK/API 연동 자체가 **아직 어디에도 없다** — `KakaoProperties`(백엔드)는 소비 코드가 없는 예약 설정뿐이고, 프론트엔드엔 "kakao"/"카카오맵" 문자열이 지도 관련 코드에 전혀 없다(로그인 화면의 비활성 카카오 소셜로그인 버튼은 지도와 무관한 별개 기능이다). 즉 "확인 후 반영"할 대상 자체가 존재하지 않았다 | 4번 항목(제3자 제공)을 현재형이 아니라 미래형으로 썼다 — "현재 지도 서비스는 제공되지 않으며, 향후 도입 시 좌표 정보 전송에 한정하고 이용자 식별 정보는 전송하지 않도록 구현할 예정, 도입 시 갱신해 고지"로 서술해 아직 없는 기능을 있는 것처럼 서술하지 않았다. |
| **자동 수집 장치(쿠키) 문구** | "로컬스토리지 방식인지 쿠키 방식인지 코드베이스에서 확인 후 실제 저장 방식에 맞게 기재"가 전제였다 | `tokenStorage.ts`가 `localStorage`만 쓰고(`ACCESS_TOKEN_KEY`/`REFRESH_TOKEN_KEY`), 프론트엔드 전체에 `document.cookie`/쿠키 라이브러리/`Set-Cookie` 처리 코드가 전혀 없다(SCR-AUTH-01 구현 결정 사항 표에 이미 기록된 사실과도 일치) | 9번 항목을 "쿠키를 사용하지 않으며, 인증 토큰을 브라우저 로컬 저장소에 저장한다"로 명확히 기재했다 — 대다수 국내 개인정보처리방침 템플릿이 가정하는 "쿠키 사용"을 그대로 베끼면 실제와 다른 문서가 된다. |
| **소셜 로그인 컬럼 존재 여부** | "소셜 로그인은 DB 스키마에 예약 컬럼만 있고 아직 미구현"이 전제였다 | `User.java`에 `social_provider`(`VARCHAR(20)`)/`social_id`(`VARCHAR(100)`) 컬럼이 실제로 존재하며 `schema_all.sql`에도 NULL 허용으로 정의돼 있다 — 다만 `User.createUser()`/빌더 어디서도 값을 채우지 않아 모든 행이 NULL인 상태(존재하되 미사용). 전제가 정확했다 | 3번 항목에 "현재 이메일 회원가입만 지원, 소셜 로그인 항목은 수집하지 않음, 도입 시 별도 고지"로 반영 — 컬럼이 예약돼 있다는 사실 자체는 이용자에게 노출할 정보가 아니라(수집하지 않는 이상 처리방침에 적을 항목이 없다) 본문에는 신지 않고 이 판단표에만 근거로 남긴다. |
| **연령 확인 절차 — P1 코드리뷰 지적으로 2단계에 걸쳐 확정, 공개 문구까지 수정 완료 → 2026-09-19 3단계: 자기 확인 체크박스 도입, 문구 갱신(잔여 부분 해소)** | "회원가입 시 연령 확인 절차가 없다면 '만 14세 미만은 이용 제한' 문구를 넣고, 나이 검증 로직이 없다는 점은 별도로 백로그에 남길 것"이 전제였다 | `SignupRequest.java`엔 email/password/nickname 3개 필드뿐 나이·생년월일 필드가 없고, 백엔드/프론트 어디에도 연령 검증 로직이 없다 — 즉 만 14세 미만을 포함한 누구나 성인과 동일한 절차로 가입할 수 있다. **1단계(불충분했음):** 6번 항목에 "만 14세 미만 아동을 대상으로 하지 않으며 수집하지 않는다"는 무조건적 단정문을 넣고, 실제로는 보장되지 않는다는 사실은 이 판단표(개발자 전용 문서)에만 적어뒀다. **코드리뷰 재지적:** 개발자 메모로 갭을 인정해 놓고 이용자가 실제로 읽는 공개 문구는 그대로 검증 가능한 거짓 진술로 남겨둔 것 자체가 불충분하다는 지적을 받았다 — 더구나 아동의 개인정보는 「개인정보 보호법」 제22조의2(법정대리인 동의 요건)가 특별히 보호하는 범주라 다른 "구현보다 앞서 나간 문구" 사례들보다 무겁게 다뤄야 했다 | **2단계(공개 문구 수정):** "만 14세 이상을 대상으로 서비스를 제공하나 현재 연령 확인 절차가 없어 가입을 기술적으로 차단하지 못하며, 제22조의2에 따른 법정대리인 동의 절차도 갖추지 못했다는 사실을 그대로 밝히고, 발견 시 지체 없이 삭제하겠다"는 문구로 교체했다 — 목표 대상(14세 이상)과 반응적 구제(발견 즉시 삭제)는 정직하게 약속하되, 사전에 차단한다는 거짓 주장은 하지 않는다. 나이 검증 게이트(자기인증 체크박스든 생년월일 수집이든)를 실제로 추가하는 것도 검토했으나, 회원가입 폼·백엔드 DTO를 함께 바꾸는 새 기능 결정(어떤 방식으로 확인할지부터 지성 판단 필요)이라 정책 문구 수정 세션에서 임의로 만들지 않았다. **완결 필요(우선순위 높음) — 2026-09-19 최소 조치는 해소됨(아래 3단계), 잔여는 부분 해소 상태로 남음.** 원래 요구는 "실사용자 유입 전에 최소한 자기인증 체크박스("만 14세 이상입니다")라도 회원가입 폼에 추가하라 — 완전한 방지는 아니지만 '합리적 조치'로 인정받는 업계 관행이고, '사전 차단 수단 전무' 상태보다는 낫다"였다. **3단계(2026-09-19, 지성 확정):** AUTH-02에 그 체크박스를 실제로 추가했다(클라이언트 전용 게이트, 서버 미전송·미저장 — SCR-AUTH-02 절 "만 14세 이상입니다 확인 체크박스" 판단 기록 참고). 이에 따라 6번 항목의 "현재 연령을 확인하는 별도 절차를 두고 있지 않아"라는 문구는 더는 사실이 아니게 돼 공개 문구를 갱신했다: 체크해야 가입할 수 있다는 사실과 그것이 **이용자 자기 확인에 의존하는 방식일 뿐 생년월일·본인인증 등 별도 검증 수단이 없다는 한계**, 법정대리인 동의 절차 부재, 가입 사실을 인지하면 삭제하되 자동 탐지·삭제 절차는 없고 10번 보호책임자 연락을 통한 수동 처리라는 점을 그대로 남겼다(체크 이력이 서버에 저장·검증된다거나 본인인증·법정대리인 동의 확인 절차가 있다는 표현은 코드로 뒷받침되지 않아 쓰지 않았다). **후속 점검(2026-09-19):** 6항의 삭제 관련 문구를 다시 점검해 (1) 실제 수동 삭제 절차가 서술된 곳은 10항이 아니라 2항(보호책임자 이메일 요청→수동 처리, 자동 파기 없음)이라 그 절차를 가리키도록 "회사 주도 삭제"와 "요청 기반 처리"가 섞인 문장을 정리하고, (2) 새 검증 수단 도입 계획이 확정되지 않았는데 그런 인상을 주던 "추가적인 연령 확인 절차를 마련하는 대로 갱신·고지하겠다" 문장을 삭제했다(개정 고지는 13항이 일반 약속으로 커버). "지체 없이"는 2항·6항 앞 문단의 기존 표현과 같은 수준이라 유지했다 — 다만 그 표현들 자체가 "지킬 수 있는 약속인지"는 보호책임자(=1인 운영자)의 실제 처리 능력에 달려 있어 지성 확인 대상이다. **잔여 완결 필요:** 실제 연령 검증 수단(본인인증·생년월일 수집)과 법정대리인 동의 절차는 여전히 없다 — 공개 문구가 그 사실을 그대로 밝히고 있으므로 도입 시 문구도 함께 고쳐야 한다. **이 문구의 시행일/개정 이력(13번)은 당시 갱신하지 않았다(2026-09-21 갱신됨 — 아래 5단계 참고)** — 시행일 `2026-09-15`/`v1.0 최초 제정` 그대로이고, 실제 개정 시행일(고지 최소 7일 전 원칙 포함)은 지성이 정해야 하는 값이라 임의로 채우지 않았다. **4단계(2026-09-19, Codex P1 지적 — "가입 API에서 연령 확인을 강제하라") — 결정은 유지하고 문구만 정정했다.** 지적 자체는 사실이다: `SignupRequest`(email/password/nickname)에 확인 값이 실리지 않고 `AuthController.signup`도 검증하지 않아, 화면을 거치지 않고 `POST /api/auth/signup`을 직접 호출하거나 옛 클라이언트를 쓰면 체크 없이 계정이 만들어진다(코드로 직접 확인). 그래서 3단계 문구의 "체크해야 가입할 수 있으나"는 API 직접 호출 경로에는 거짓이었다. 그러나 서버 강제(요청에 `ageConfirmed` 필드 추가 + 서버에서 누락·false 거부)는 이번 작업의 명시 결정("프론트엔드 전용, 서버로 전송하지 않음, `SignupRequest`에 필드 추가 금지", SCR-AUTH-02 절 "만 14세 이상입니다" 행)과 정면으로 충돌하는 API 계약 변경이라 문구 수정 세션에서 임의로 구현하지 않았다 — 대신 공개 문구를 실제 동작으로 좁혔다: "회원가입 화면에서는 체크해야 가입 버튼이 활성화되나, 이는 자기 확인에 의존하며 회원가입 화면에서만 확인하고 체크 여부가 서버로 전송되거나 서버에서 검증되지는 않는다"(`privacyPolicySections.tsx` 6항, 검증 스크립트 18/18). API 직접 호출로 우회된다는 점을 문구에서 굳이 광고하지는 않되 "화면에서만 확인·서버 미검증"이라는 한계는 숨기지 않았다. **판단 정정(SCR-AUTH-02 행의 근거 보강):** 그 행은 "저장하지 않는다"의 근거로 "사용하지 않는 컬럼 추가 금지" 원칙을 들었는데, 이 원칙이 막는 것은 *저장*(DB 컬럼)이지 *검증*이 아니다 — 서버가 값을 검증만 하고 버리면 DB 컬럼은 필요 없다. 따라서 서버 검증의 걸림돌은 컬럼 원칙이 아니라 (1) API 계약 변경(필드를 보내지 않는 기존·옛 클라이언트가 400으로 깨짐), (2) 이번 작업의 "백엔드·API 스펙 불변" 범위 결정 두 가지뿐이다. **완결 필요:** 서버 검증 도입은 이 표 아래 별도 항목 "가입 API 연령 확인 서버 검증"으로 등록했다(처리 방안·선행 확인·계약 변경 주의·문서 갱신 포함) — 도입되면 위 무효화 조건 (1)에 해당하므로 6항 문구도 함께 갱신해야 한다. **5단계(2026-09-21, 시행일 확정) — 위 4단계 문구가 실제로 시행되며 13번도 갱신했다, 사전 고지는 하지 않았다.** `privacyPolicySections.tsx`의 13번(처리방침의 변경) 시행일을 `2026-09-21`(v1.1)로 갱신하고 개정 이력에 "6항 연령 확인 관련 문구 수정: 회원가입 시 만 14세 이상 자기 확인 항목 도입 반영" 행을 추가했다 — v1.0 행(`2026-09-15`, 최초 제정)은 그대로 두었다. **다만 13항 본문 자체는 "내용의 추가·삭제 및 수정이 있을 경우에는 개정 최소 7일 전부터 서비스 내 공지사항(또는 이메일)을 통하여 고지하겠습니다"라고 사전 고지를 약속하고 있는데, v1.1은 그 약속을 지키지 않고(사전 고지 없이) 그대로 적용한 첫 사례다** — 사유는 아직 실사용자(기존 가입자)가 없는 출시 전 단계라 고지할 대상 자체가 없기 때문이다 — **전제: 실제 가입자 없음(배포 시점에 재확인 필요)**, 지성이 확인해 준 사실이 아니라 지시문이 전제로 준 것이라 그대로 믿지 않고 배포 직전 재확인해야 한다(→ **2026-09-21 사용자가 "가입자 없음"을 확인함 — 7단계 참고**). **무효화 조건:** 실제 가입자가 생긴 뒤에는 이 예외가 더는 성립하지 않으므로, 다음 개정부터는 13항의 사전 고지 약속을 그대로 지켜야 한다 — 이 항목을 다시 열 때 가입자 유무부터 확인하라. **6단계(2026-09-21, 서버 검증 도입 — 4단계 잔여 해소):** 결정: `SignupRequest`에 `Boolean ageConfirmed`(`@NotNull` + `@AssertTrue`)를 추가해 가입 API가 서버에서 검증하고, **검증 후 폐기한다** — `SignupCommand`·서비스·엔티티·DB 스키마·로그 어디에도 남기지 않는다(컬럼 없음). 사유: client-only 확인은 `POST /api/auth/signup` 직접 호출·옛 클라이언트로 우회할 수 있었고, 그래서 6항 방침 문구도 "화면에서만 확인"이라는 한계를 밝혀야 하는 상태였다(Codex P1). 서버 강제는 저장이 아니라 검증이라 "사용하지 않는 컬럼 추가 금지" 원칙과 충돌하지 않는다(4단계 정정 참고). 프론트는 체크박스 상태를 `ageConfirmed`로 그대로 전송하고(true 하드코딩 아님) `agreeToTerms`는 여전히 보내지 않는다. 6항 문구는 "체크하지 않으면 서버에서도 가입을 거부, 체크 여부는 저장하지 않고 검증에만 사용, 서버가 확인하는 것은 체크 여부일 뿐 실제 연령이 아님"으로 바꿨고 자기 확인 의존·본인인증/생년월일 확인 부재·법정대리인 동의 절차 부재·자동 탐지 없음·수동 삭제 경로 고지는 유지했다. 검증: `AuthControllerTest`(누락·null·false → 400 + `ageConfirmed` 필드 오류 + `AuthService.signup` 미호출, true → 기존 성공 경로, 불리언이 아닌 값 → 표준 포맷 400), 실행 중인 백엔드에 실요청(false·누락 → 400, 계정 미생성 확인), 프론트 3개 뷰포트 스크립트. **무효화 조건:** (1) 동의 이력을 소비하는 감사·집행 기능이 생기면 저장(컬럼 추가)을 재검토하라, (2) 본인인증이나 법정대리인 동의 절차를 도입하면 자기 확인 방식 전체를 재검토하고 6항도 함께 갱신하라. **개정 이력(13번)은 6단계 시점에는 갱신하지 않았고 7단계에서 해소했다.** **7단계(2026-09-21, v1.2 시행일 확정):** 사용자가 시행일 `2026-09-21`을 확정하고 "가입자 없음 확인됨"을 확인해 줘 6단계의 완결 필요 항목을 해소했다. `privacyPolicySections.tsx` 13번 개정 이력에 v1.2 행("6항 연령 확인 관련 문구 수정: 가입 시 서버에서도 연령 확인 항목을 검증하도록 변경 반영")을 추가했다. v1.0(`2026-09-15`)·v1.1(`2026-09-21`) 행은 자기 날짜를 유지한다 — v1.1 행이 `currentEffectiveDate`를 참조하고 있어 그대로 두면 현행 시행일을 따라 움직이므로 v1.1 전용 상수(`v11EffectiveDate`)로 분리했다. **v1.1과 v1.2의 시행일이 같은 날이라 v1.1은 별도 기간 동안 적용된 적이 없다** — 표에 두 행이 같은 날짜로 나란히 보이는 것은 의도한 결과다. **7일 사전 고지: v1.2에도 5단계와 같은 예외를 적용했다** — 13항 본문의 "개정 최소 7일 전 고지" 약속을 지키지 않았고, 사유는 고지할 기존 가입자가 없기 때문이며 **그 전제(가입자 없음)는 사용자가 확인했다(2026-09-21).** 5단계의 "배포 시점 재확인 필요"는 이 확인으로 v1.1·v1.2에 대해 충족됐다. **무효화 조건은 그대로다:** 실제 가입자가 생긴 뒤의 개정(v1.3~)에는 이 예외가 성립하지 않으므로 13항의 사전 고지 약속을 지켜야 한다. 검증: `privacy-amendments-check` 8/8(행 단위로 세 행의 순서·날짜·비고와 v1.1 불변을 확인). |
| **Refresh Token "블랙리스트" 표현** | "JWT 기반 인증, Refresh Token 블랙리스트 처리(로그아웃/재발급 시)"라는 문구를 그대로 쓰라는 전제였다 | 이 세션에서 `RefreshTokenRepository`의 정확한 폐기 구현(별도 블랙리스트 테이블인지, 해당 행 삭제/플래그인지)까지 코드로 재확인하지는 않았다 — CLAUDE.md SVC-AUTH-01/USER-01 절에 이미 기록된 "로그아웃 시 소유자 검증 후 처리", "탈퇴 시 `revokeAllByUserId()`" 정도의 사실만 근거로 삼았다 | "블랙리스트"라는 특정 구현 방식을 단정하지 않고 "기존 Refresh Token을 즉시 폐기하여 재사용을 방지한다"는 더 중립적인 문장으로 완화했다 — 실제 구현과 다른 특정 메커니즘(예: 별도 블랙리스트 테이블)을 확정적으로 진술해 나중에 틀린 게 되는 리스크를 피했다. **후속 정정(7라운드, P2 코드리뷰 지적) — "재발급 시에도 폐기"는 실제로 틀린 진술이었다.** 위에서 "재발급 시에도 폐기"라고 문구를 완화만 하고 실제 `AuthService.refreshAccessToken()`을 재확인하지 않았는데, 이번 라운드에서 직접 읽어보니 이 메서드는 제출된 Refresh Token을 검증하고 새 Access Token만 발급할 뿐(`jwtTokenProvider.createAccessToken()`) `stored.revoke()`를 호출하지도, 새 Refresh Token을 발급하지도 않는다 — `revoke()`를 실제로 호출하는 곳은 `logout()`뿐이다(103~130행 확인). 즉 유효한 Refresh Token은 로그아웃하기 전까지 만료 시각까지 몇 번이고 재사용해 Access Token을 계속 재발급받을 수 있다 — "재발급 시 폐기"는 모든 재발급 호출에 대해 거짓이었다. 8번(안전성 확보조치) 섹션 문구를 "로그아웃 시에만 폐기, 재발급 자체는 교체·폐기 없이 재사용 가능"으로 좁혀 실제 동작과 일치시켰다. **완결 필요(Refresh Token 로테이션 미도입)** — 재발급마다 새 Refresh Token을 발급하고 기존 것을 폐기하는 로테이션(탈취된 토큰의 장기 재사용을 막는 표준적 방어)이 없다는 뜻이다. 이는 `AuthController`/`AuthService`의 인증 흐름 자체를 바꾸는 백엔드 기능 결정이라 정책 문구 수정 세션에서 임의로 구현하지 않았다 — 로테이션을 도입하면 이 섹션 문구를 다시 "재발급 시에도 폐기"로 되돌릴 수 있다. **→ 완료(2026-09-29, `fix/frontend/privacy-token-rotation`) — 2026-09-22 Rotation·재사용 탐지(`0f5e8a0`/`2ae48cb`, develop 머지 확인)가 들어온 뒤에도 8항은 "교체·폐기하지 않는다"로 남아 있었다(되돌리지 않은 채 1주일 방치).** 백엔드 코드를 다시 읽어 확인한 동작만 썼다: 재발급마다 새 토큰 발급·기존 토큰 즉시 폐기(`RefreshTokenRotator.attempt()`의 조건부 UPDATE), 로그아웃은 해당 토큰만 폐기, 비밀번호 재설정은 계정 전체 폐기, 이미 폐기된 토큰이 다시 쓰이면 계정의 모든 Refresh Token 폐기(`RefreshTokenReuseHandler`). 처음(`cb1f576`)엔 재사용 탐지가 Access Token 컷오프를 걸지 않아 "최대 30분 뒤 다시 로그인"으로 썼다. **같은 PR에서 재사용 탐지에도 컷오프를 추가해(AUTH-03 "Access Token 즉시 무효화" 절) "모든 기기에서 즉시 로그인 상태가 해제되며 다시 로그인해야 한다"로 바꿨다** — 처음엔 탐지와 같은 초에 발급된 토큰이 예외였으나 같은 날 코드리뷰 P1로 밀리초 엄격 비교로 바꿔 그 예외도 없앴다. 감사 로그(`logRefreshTokenReuseDetected`)는 userId만 남겨(IP·User-Agent 없음) 3·9항은 바꾸지 않았다. 검증: `frontend/e2e/privacy-token-rotation-check`(8항 문구·v1.3 행, 옛 문구 재등장 차단). **개정 이력(13항) v1.3 신설** — 2항(BAT-USR-01 탈퇴 계정 자동 파기·유예)과 8항 변경을 한 행에 담았다(지성 결정). **시행일 2026-09-29(PR #55 머지일)로 확정.** 처음에는 `2026-09-28`로 기입했다가(`7077aae`) 머지일 기준에 맞춰 정정했다. **7일 사전 고지: v1.3에도 v1.1·v1.2와 같은 예외를 적용했다(사전 고지 없음).** 근거: 백엔드가 아직 운영 환경에 배포되지 않아 hmss.site에서 가입할 경로 자체가 없다 — 그래서 고지할 기존 가입자가 없다. 이 예외는 백엔드를 배포해 실제 가입을 받기 시작한 뒤의 개정(v1.4~)에는 성립하지 않으므로, 그때부터는 13항의 7일 사전 고지 약속을 지켜야 한다(위 "연령 확인 절차" 행 7단계 무효화 조건과 같다). **무효화 조건:** (1) 재사용 탐지의 Access Token 컷오프를 제거하면 8항의 "즉시 해제"를 되돌린다, (2) 로그아웃·재설정·탈퇴의 폐기 범위가 바뀌면 8항을 다시 확인한다. |
| **체크박스 링크가 동의 상태를 토글하는 문제** | "약관 동의 자체는 클라이언트 전용 게이트이므로 정책 페이지 열람이 체크박스 상태에 영향을 주면 안 된다"는 주의사항이 있었다 | `Checkbox.tsx`가 시각적 박스+라벨 전체를 하나의 `<label>`로 감싸는 구조라(표준 커스텀 체크박스 패턴), 라벨 안에 `<Link>`를 그대로 넣으면 그 클릭이 페이지 이동과 체크박스 토글을 동시에 발생시킨다 | `SignupPage.tsx`의 "개인정보처리방침" `<Link>`에 `onClick={(e) => e.stopPropagation()}`을 걸어 부모 `<label>`로의 클릭 버블링만 막았다(페이지 이동 자체는 `Link`의 기본 동작이라 막히지 않는다) — Playwright로 "정책 페이지 방문 후 뒤로가기해도 체크박스가 여전히 미체크 상태"를 검증했다. |
| **보유기간 섹션이 존재하지 않는 탈퇴 라이프사이클을 약속함 — P1 코드리뷰 지적, 2라운드에 걸쳐 수정** | 초안은 "탈퇴 시 계정 비활성화, 7일 유예기간 후 개인정보 완전 파기, 유예기간 중 재로그인으로 탈퇴 철회 가능"이라고 적었다 — 브리핑이 준 문구를 그대로 옮긴 것으로, 실제 구현을 코드로 재확인하지 않고 작성했다 | **1라운드:** `UserService.withdraw()`를 직접 읽어 확인한 결과 `user.withdraw()`(status=WITHDRAWN, `withdrawnAt` 기록)와 `refreshTokenRepository.revokeAllByUserId()`만 호출할 뿐, 유예기간이나 예약 파기를 다루는 코드가 전혀 없다. `AuthService.login()`은 `user.getStatus() != ACTIVE`면 무조건 `AccountNotActiveException`을 던진다 — 즉 **탈퇴한 계정은 재로그인 자체가 막혀 있어 "재로그인으로 철회"가 코드상 불가능한 동작을 약속하고 있었다.** 저장소 전체에서 "purge"/"reactivat"/예약 파기 배치를 검색해도 아무것도 나오지 않아 "7일 후 파기"도 사실이 아니었다(WITHDRAWN 상태로 무기한 보관) — 실제 코드 동작(즉시 비활성화, 인증 토큰 즉시 폐기, 재로그인으로 복구 불가)만 기술하도록 고치고, 7번 항목(파기 절차)의 "별도의 DB로 옮겨져 보관"이라는 마찬가지로 코드에 없는 구체적 기술 주장도 함께 제거했다(국내 템플릿에 관행적으로 들어가는 문구이지만 이 서비스엔 해당 메커니즘이 없다). **2라운드(지성 지적):** 1라운드가 남긴 "지체 없이 파기하는 것을 원칙으로 합니다"라는 문구 자체도 여전히 실제와 다를 수 있다는 지적을 받았다 — 이 문장은 법 조문(원칙론)을 인용한 것이지 탈퇴 계정의 개인정보를 실제로 자동 삭제하는 배치가 있다는 뜻이 아닌데, "계정이 즉시 비활성화된다"는 사실과 "개인정보가 실제로 파기된다"는 주장을 문서가 뭉뚱그리면 방침과 실제 처리가 다르다는 지적을 받을 여지가 남는다는 게 핵심이었다 — 개인정보처리방침은 법 원칙을 인용하는 문서가 아니라 서비스가 실제로 무엇을 하는지 고지하는 문서이기 때문이다 | **2라운드 반영:** "계정 비활성화(자동, 즉시)"와 "개인정보의 완전한 삭제(아직 미자동화)"를 문서에서 명확히 분리하는 문단을 추가하고, 즉시 삭제를 원하는 이용자에게 보호책임자 앞 수동 요청 경로를 제공했다(자동화 여부와 무관하게 삭제 요청권 자체는 지금도 충족되도록) — 존재하지 않는 자동 배치를 있는 것처럼 쓰지 않으면서도, 이용자에게 아무 수단도 없는 상태로 남겨두지 않는 절충이다. **완결 필요(우선순위 높음, 이 세션 범위 밖 — 지성이 백엔드 작업 우선순위를 높게 잡을 것을 명시적으로 요청함)** — 탈퇴 계정을 일정 기간 후 자동으로 파기하는 배치(또는 진짜 탈퇴 철회 플로우)가 실제로 만들어지기 전까지는 이 완화된 문구가 유지돼야 한다. 사용자 데이터에 대한 예약 작업·하드 삭제를 새로 만드는 결정이라 프론트엔드 전용 세션에서 임의로 구현하지 않았다 — 다음에 백엔드 작업을 하는 세션이나 지성이 직접 판단해야 한다(재로그인으로 철회 가능하게 할지, 별도 마이페이지 버튼으로 철회를 만들지, 파기 배치를 얼마나 빨리 도입할지 등). 이 항목은 단순 버그가 아니라 "방침 문장이 법적으로 성립하려면 반드시 구현이 뒤따라야 하는 항목"이라는 점에 유의하라. **운영 함의(지성 지적) — "연락하면 삭제해준다"는 문서상 약속이 아니라 실제 프로세스다.** 방침이 "즉시 삭제를 원하면 보호책임자에게 요청하라"고 명시한 이상, 실제로 그 요청이 오면 지성이 수동으로 처리할 수 있어야 한다(현재는 1인 운영 체제라 본인이 곧 보호책임자라 문제 없음). **또한 나중에 자동 파기 배치를 실제로 구현하면 "수동 요청 시 삭제"와 "자동 파기"가 동시에 존재하게 되므로, 그 시점에 이 섹션 문구를 다시 한 번 다듬어야 한다** — 지금 문구는 "자동화가 전혀 없다"는 전제로 쓰여 있어, 배치가 생기면 그 전제 자체가 깨진다(예: 수동 요청은 즉시 처리, 미요청 시에도 N일 후 자동 파기하는 이중 트랙으로 문구를 다시 나눠야 할 가능성이 높다). **→ 2026-09-21 해소(자동 파기 부분): BAT-USR-01이 탈퇴 후 N일(기본 7) 경과 계정을 매일 새벽 자동 파기하고 2항을 그 이중 트랙(즉시 삭제 요청은 수동, 미요청은 N일 후 자동)으로 다시 썼다 — 위 "BAT-USR-01 / SVC-AUTH-01.reactivate()" 절 참고. "진짜 탈퇴 철회 플로우"는 API(`POST /api/auth/reactivate`)까지만 만들었고 이를 호출하는 화면은 아직 없어 방침은 철회를 안내하지 않는다.** |
| **6번(정보주체 권리) 섹션이 존재하지 않는 마이페이지 자기서비스 기능을 안내함 — P1 코드리뷰 지적, 수정 완료** | 초안은 "개인정보 열람·정정 요구: 마이페이지의 '회원정보 수정' 화면에서 직접 열람·정정할 수 있습니다", "개인정보 삭제 요구: 마이페이지의 '회원 탈퇴'를 통해 요청할 수 있습니다"라고 적었다 — 마이페이지가 실제로 그 기능을 제공한다고 가정하고 작성했다 | `AppRouter.tsx`를 확인한 결과 `/my`는 여전히 `PlaceholderPage`(programId `MY-01`, "추후 구현 예정입니다")로만 연결돼 있고, `features/user/api.ts`엔 `getMe()`만 있을 뿐 `updateUser`/`withdraw`를 호출하는 클라이언트 코드 자체가 없다(백엔드 `PUT`/`DELETE /api/users/me`는 이미 있지만 프론트가 아직 붙이지 않았다) — 즉 이 절차를 그대로 따라가면 이용자는 "추후 구현 예정" 화면만 보고 아무것도 할 수 없다 | 2번(보유기간) 섹션에서 이미 확립한 원칙(존재하지 않는 자동화를 약속하는 대신 실제로 작동하는 채널로 안내)을 그대로 적용했다 — 마이페이지 언급을 없애고 "마이페이지 자기서비스 기능은 준비 중이며, 그 전까지는 10번(보호책임자) 이메일로 요청하면 지체 없이 처리한다"로 바꿨다. **완결 필요** — MY-01(마이페이지) 프론트 구현은 이 세션 범위를 벗어나는 기능 개발이라 하지 않았지만, 백엔드 엔드포인트가 이미 있어 신규 설계가 아니라 기존 UI정의서 화면을 붙이는 작업에 가깝다 — 다음 프론트엔드 세션에서 우선순위 높게 다루면 이 섹션 문구도 "마이페이지에서 직접"으로 되돌릴 수 있다. |
| **3번(처리 항목) 표가 비로그인 방문자에게도 생성되는 영속 세션 식별자를 "로그인 후"로만 서술 — P1 코드리뷰 지적, 수정 완료** | 표는 "최근 조회 이력"을 "관심 매물/관심 지역 등록 정보, 알림 조건 설정값"과 한 행으로 묶어 "로그인 후 개인화 기능 이용 시"에만 생성된다고 적었다 | `lib/sessionId.ts`의 `getOrCreateSessionId()`가 `crypto.randomUUID()`로 만든 값을 `localStorage`(`homesense.sessionId`)에 영구 저장하고, `features/recentview/api.ts`의 `getRecentViews()`는 로그인 여부와 무관하게 이 값을 `X-Session-Id` 헤더로 **항상** 전송하며, `RecentViews.tsx`는 이 호출을 홈 화면 마운트 시 **인증 게이트 없이** 실행한다 — 즉 비로그인 방문자도 홈 화면에 들어오는 즉시 이 식별자가 생성·전송되고, 백엔드 `RecentViewController`/`RecentViewService.getRecent()`가 이를 비회원 조회 이력의 조회 주체로 그대로 사용한다(SVC-RCV-01, CLAUDE.md 해당 절 참고). "최근 조회 이력"을 로그인 전용 항목과 묶은 건 부정확했다 | "최근 조회 이력"을 로그인 필요 항목에서 분리해 별도 행("기기 식별 정보(로그인 불필요)")으로 신설 — 식별자 자체(무작위 생성값), 목적(최근 조회 이력을 기기 단위로 유지), 실제 수집 시점(홈 화면 등에서 단지 정보를 조회하는 즉시, 로그인 여부 무관)을 명시했다. 9번(자동 수집 장치) 섹션도 함께 갱신해 "쿠키를 쓰지 않는다"는 기존 문장 아래 로컬스토리지에 저장되는 두 항목(인증 토큰/세션 식별자)을 표로 나열했다. **초안에서 걸러낸 사실 오류(자체 발견, 리뷰 지적 아님):** 처음에는 "로그인 시 그 계정의 이력으로 전환되어 이어서 조회된다"고 쓰려 했으나, `RecentViewService.getRecent()`/`record()`를 다시 읽어보니 `userId`가 있으면 `sessionId` 이력을 전혀 참조하지 않고(둘 중 하나로만 조회·기록, 병합 로직 없음) 로그인 후에는 완전히 새로운 빈 이력이 시작된다는 것을 확인해 문구를 "서로 연결되지 않습니다"로 정정한 뒤 커밋했다 — 검증 없이 그대로 나갔다면 이번 라운드 자체가 새로운 "구현보다 앞선 문구" 사례가 될 뻔했다. |

**완결 필요(신규)** — 이용약관(ToS) 페이지가 아직 없다. `SignupPage.tsx`의 "이용약관" 텍스트는
SCR-AUTH-02 구현 시점의 기존 결정대로 여전히 순수 `<span>`이고(클릭 불가), `Footer.tsx`의 "이용약관"도
마찬가지다 — 이번에 "개인정보처리방침"만 실제 페이지로 연결했다. 이용약관도 동일 패턴(`MainLayout` +
섹션 데이터 배열 + 목차)으로 별도 작업이 필요하다 — 근거 법령·필수 조항 구성이 개인정보처리방침과
다르므로(전자상거래법/약관규제법 관점) 이 파일들을 그대로 복사해 쓰지 말고 새로 검토해서 작성하라.
**이 항목을 작성할 때 확인한 것:** `agreeToTerms`도 `confirmedAge14`와 같은 클라이언트 전용 게이트라(위 AUTH-02
"약관동의 체크박스" 행) 이용약관 페이지를 실제로 쓸 때 서버 검증(동의 시각·약관 버전 기록 포함) 여부를
아래 "가입 API 연령 확인 서버 검증" 항목과 함께 재검토하라.
**비대칭(2026-09-21):** 연령 확인(`ageConfirmed`)은 서버 검증이 도입됐지만 약관 동의(`agreeToTerms`)는 여전히 client-only라, 가입 API 직접 호출로는 약관 동의 없이도 가입된다 — 약관 페이지를 쓸 때 이 비대칭을 그대로 둘지(동의 검증·기록 도입 여부) 함께 결정하라.

**✅ 종결(2026-09-21) — 가입 API 연령 확인 서버 검증 (Codex P1 지적, PR 스레드 참조).** 아래 처리 방안대로 한 브랜치(`feature/backend/signup-age-confirmation`)에 [API-AUTH-01] 백엔드 검증 / [AUTH-02] 요청 바디 / [SCR-LEGAL-01] 6항 문구로 나눠 묶어 도입했다. **아래 "선행 확인 필요"(옛 백엔드가 모르는 JSON 필드를 무시하는지) 조건은 프론트·백엔드를 같은 릴리스로 묶었으므로 무효가 됐다** — 단 그 전제는 반드시 동시 배포여야 한다(백엔드만 먼저 나가면 옛 프론트의 가입이 400이 되고, 프론트만 먼저 나가면 6항의 "서버에서도 거부" 문구가 거짓이 된다). 6항 문구는 처리 방안 (3)의 "체크해야 가입할 수 있다" 표현 대신 "체크하지 않으면 서버에서도 가입을 거부한다"로 썼다(서버가 강제하는 것이 체크 여부일 뿐 실제 연령이 아님을 문장에서 흐리지 않기 위함). **문서 갱신 필요(아직 미반영):** 프로그램설계서 SVC-AUTH-01 signup 처리 로직·Controller/DTO 절, 5.8 COM-VAL-01 적용 위치와 예외 처리 표. 이하는 종결 전 원문(경위 기록)이다.

현재
"만 14세 이상입니다" 확인은 **client-only**다 — `POST /api/auth/signup`은 email/password/nickname만 받고
서버는 이 확인을 보지도 검증하지도 않아, 화면을 거치지 않는 직접 호출·옛 클라이언트로는 체크 없이 가입된다
(`SignupRequest.java`/`AuthController.signup` 직접 확인, 2026-09-19). 프론트엔드 전용 브랜치의 명시 범위 결정에
따라 이번엔 구현하지 않았고, 6항 공개 문구는 이 한계("화면에서만 확인, 서버로 전송·검증되지 않음")를 그대로
밝히는 상태(`dd210f3`)로 둔다. **처리 방안(한 PR로 묶을 것):** (1) `SignupRequest`에 `Boolean ageConfirmed`
(`@NotNull` + `@AssertTrue`, **저장하지 않고 검증 후 폐기** — DB 컬럼 불필요, "사용하지 않는 컬럼 추가 금지"
원칙과 충돌하지 않음), (2) 프론트가 가입 요청에 `ageConfirmed: true` 전송, (3) `privacyPolicySections.tsx` 6항
문구를 "…체크해야 가입할 수 있다"로 복원. **선행 확인 필요:** 이 프로젝트 백엔드가 모르는 JSON 필드를 무시하는지
— `application.properties`에 `fail-on-unknown-properties` 명시 설정이 없어 Spring Boot 기본 동작에 기대고 있고
직접 검증하지 않았다. **계약 변경 주의(배포 순서):** 백엔드가 `ageConfirmed`를 `@NotNull`로 먼저
배포하면, 아직 이 필드를 보내지 않는 프론트의 가입 요청이 프론트가 배포될 때까지 400으로 실패한다 —
프론트를 먼저 배포하는 순서는 옛 백엔드가 모르는 JSON 필드를 무시할 때만 안전한데 이 동작 자체가
위에서 "선행 확인 필요"로 남긴 상태라 전제할 수 없다. 가장 단순한 방법은 프론트·백엔드를 같은 PR로
묶어 동시에 배포하는 것이다. **문서 갱신 필요:**
프로그램설계서 SVC-AUTH-01 signup 요청 명세에 `ageConfirmed`를 반영해야 한다(설계서는 아직 이 필드를
모른다). **무효화 조건:** 서버 검증이 도입되면 이 항목은 종결한다(SCR-LEGAL-01 "연령 확인 절차" 행의
4단계 잔여도 함께 정리).

**완결 필요(신규, 우선순위 낮음, 지성 지적) — 이 화면의 회귀 이력을 지켜줄 커밋된 테스트가
저장소에 없다.** 이 페이지는 5라운드 코드리뷰 내내 반복적으로 "방침 문구가 실제 구현보다 앞서
나감" 종류의 버그(보유기간 문구, 6번 정보주체 권리 경로, 회원가입 폼 상태 보존)를 냈고, 매번
Playwright로 검증은 했지만 그 스크립트들은 전부 `C:\Users\super\AppData\Local\npm-cache\_npx\...`
아래의 스크래치 파일이었다 — 이 프로젝트엔 현재 커밋된 Playwright 테스트 스위트 자체가 없어서
(`@playwright/test` devDependency도, 테스트 디렉터리도 없음) 이번에 잡은 회귀들을 지켜줄 자동화된
안전망이 저장소에 전혀 남지 않는다. 특히 위험한 지점 — 나중에 누군가 MY-01(마이페이지)을 구현하면서
6번 섹션 문구("마이페이지 준비 중")를 되돌리는 걸 잊거나, AUTH-02 회원가입 폼을 건드리면서
"개인정보처리방침" 링크의 `target="_blank"`(폼 상태 보존을 위한 조치)를 실수로 지울 수 있다.
**최소한 다음 세 가지만이라도 커밋된 테스트로 남기는 것을 백로그에 올린다(지금 당장 착수하지
않음):** (1) 회원가입 폼에 값을 채운 뒤 "개인정보처리방침" 클릭 시 새 탭이 열리고 원래 탭의
폼 값(이메일/비밀번호/닉네임/체크박스)이 그대로 유지되는지, (2) 그 클릭이 동의 체크박스를
토글하지 않는지(`stopPropagation`), (3) `/privacy`의 목차 앵커 클릭이 실제로 해당 섹션으로
스크롤되는지. 착수 시 `@playwright/test`를 devDependency로 추가하고 `npm run test:e2e`류 스크립트를
`package.json`에 신설하는 것부터 시작하라 — 지금까지처럼 스크래치 스크립트를 매번 새로 짜는 대신
저장소에 남는 형태로.

**보존 현황(2026-09-29 갱신):** 이 화면의 검증 스크립트를 포함한 e2e 스크립트 전부가 **저장소 `frontend/e2e/`에 커밋돼 있다**(실행 전제·목록은 `frontend/e2e/README.md`). 예전에 "저장소 밖(npx 캐시 경로, 로컬 `homesense-e2e-scripts` 폴더)에만 있어 유실될 수 있다"고 적었던 것은 더는 사실이 아니다. CI에는 편입하지 않았다. **남은 한계:** `signup-test`·`signup-functional`·`dup-409-check`·`server-email-error-clear-test`·`password-nickname-server-error-clear-test`·`nickname-trim-test`·`border-settle-check` 7개는 값을 출력만 해 종료코드 0이 곧 통과를 뜻하지 않는다(아래 `@playwright/test` 전환 항목). **`signup-policy-newtab-check`는 과거 전체 실행 중 1회 원인 미상으로 실패한 적이 있다** — 이후 반복 실행에서 재현되지 않았고, 새 탭의 `about:blank` 경합 하나를 고쳤지만 그것이 원인이라고 확정되지는 않았다. `run-all.mjs`는 실패한 스크립트의 전체 출력을 `out/failed-*.log`에 남기므로 다시 실패하면 그 파일부터 보라.

**완결 필요(신규, 우선순위 중간 — SRCH-01 착수 전) — 검증 스크립트를 `@playwright/test` 단정문 기반으로 전환한다.** 위 13개 중 7개(`signup-test`·`signup-functional`·`dup-409-check`·`server-email-error-clear-test`·`password-nickname-server-error-clear-test`·`nickname-trim-test`·`border-settle-check`)는 기대값과 실제값이 달라도 종료코드에 반영하지 않고 값만 출력한다(스크립트가 예외를 던져 죽을 때만 비0) — 종료코드 0이 통과를 뜻하지 않으므로 이 스크립트들의 "통과"는 출력을 사람이 읽어야 확인된다. SRCH-01을 시작하기 전에 `@playwright/test`를 도입하고 단정문(`expect`) 기반으로 옮겨라 — 새 화면이 늘수록 출력을 눈으로 대조하는 검증이 누적된다. 나머지 6개는 이미 실패 시 종료코드 1이라 옮길 때 그대로 단정문으로 바꾸면 된다.

**완결 필요(신규, 2026-09-28, 이번엔 기록만) — 전체 e2e 스크립트에서 실패할 수 없는 검증을 찾아 실제 검증으로 교체한다.** 대상은 하드코딩된 `ok('...', true)`, 값을 출력만 하고 단언하지 않는 확인(위 항목의 7개 스크립트), 수집만 하고 검사하지 않는 변수(`void x`)다. **근거 — 실제 버그를 가린 사례:** `srch01-basic-check`의 "1글자 keyword로 진입해도 검색 실행 안 함" 항목이 `ok(..., true)`로 하드코딩돼 있었고, 수집한 `apiCalled`는 `void apiCalled`로 버려졌다. 그래서 `/search?keyword=래`가 그대로 서버로 가 400 → 일반 오류 화면이 뜨는 결함이 스크립트는 PASS인 채 남아 있다가 코드리뷰(P2)로 발견됐다(`fffa2d7`에서 수정, 그 항목도 실제 단정으로 교체 — 수정 전 코드에서 12건 실패 확인). **2026-09-28 검색으로 찾은 후보 — 전부 해결됨:** (1) `srch01-keyboard-and-error-check`의 `true` 2곳과 `srch01-basic-check` 재검색 시나리오의 `void searchCalls` — 해결됨(`68495ed`, 검색 브랜치). (2) `home01-logout-check`의 "모바일: 로그아웃 후 로그인 링크 복귀" `true` — 해결됨(`520ed4e`, `feature/frontend/home-header-auth`를 develop 위로 옮긴 뒤의 해시). 대기 결과를 단언하도록 바꾸고, 모바일 시나리오에도 계정 메뉴 사라짐·로컬 토큰 삭제 검사를 추가했다. 메뉴 `boundingBox()`가 `null`이면 FAIL로 나오게 했다(이전엔 `TypeError`로 스크립트가 죽었다). "로그아웃" 대신 "마이페이지"를 누르는 변형으로 FAIL 3건·종료코드 1을 확인했다. 이 항목의 나머지 대상(값을 출력만 하는 7개 스크립트)은 그대로 남아 있다. 위 `@playwright/test` 전환 항목과 함께 처리하면 `expect`로 자연히 정리된다. **무효화 조건:** 이 항목을 끝낸 뒤 새 스크립트를 쓸 때도 `ok(..., true)`를 쓰지 않는다 — 검증할 수 없는 항목이면 PASS로 세지 말고 SKIP으로 출력한다.

**완결 필요(신규, 출시 전 처리 대상) — 잘못된 형식의 JSON 값에 대한 400 응답이 Jackson 원문(내부 클래스명 포함)을 그대로 노출한다.** `{"ageConfirmed":"abc"}`처럼 필드 타입과 맞지 않는 값이 오면 `HttpMessageNotReadableException`이 `GlobalExceptionHandler.handleExceptionInternal()`(4xx에 `e.getMessage()`를 그대로 사용)을 거쳐 `error.message`에 `JSON parse error: Cannot deserialize value of type java.lang.Boolean from String "abc": only "true" or "false" recognized`가 실린다(2026-09-21 `AuthControllerTest`로 확인 — 상태 400·`ApiResponse` 포맷은 정상이라 기능상 문제가 아니라 정보 노출·문구 문제다). **`ageConfirmed`에 한정되지 않는다:** 다른 필드의 파싱 오류, 깨진 JSON, 인코딩 오류도 같은 경로이고(실제로 인코딩이 깨진 요청에 `JSON parse error: Invalid UTF-8 start byte 0xb4`가 반환됐다) 모든 `@RequestBody` 엔드포인트에 공통이다. 처리 방향: COM-EXC-01에서 `HttpMessageNotReadableException`을 고정 문구(예: "요청 본문을 읽을 수 없습니다")로 변환하고 원문은 로그로만 남긴다. `ageConfirmed` 작업이 전역 예외 처리 동작을 바꾸지 않는 범위였기 때문에 그대로 남겼다.

**완결 필요(신규, 우선순위 낮음) — `logging.level.com.homesense`가 실제 패키지와 달라 적용되지 않는다.** `application.properties`(`DEBUG`)·`application-prod.properties`(`INFO`) 모두 `com.homesense`를 지정하지만 실제 패키지는 `com.jiseong.homesense`라 앱 코드의 로그 레벨 설정이 전혀 먹지 않는다(`org.hibernate.SQL`은 패키지가 맞아 적용됨 — 기본 프로필 `DEBUG`, prod `WARN`). 지금은 앱 코드에 DEBUG/TRACE 로그 호출이 없어(2026-09-21 grep 확인) 실질 영향이 없지만, 수정하면 기본 프로필에서 `com.jiseong.homesense` 전체가 DEBUG로 바뀐다. **수정할 때 DEBUG 로그에 요청 값·이메일이 섞이는지 함께 확인하라** — 그 시점에 추가돼 있을 로그 호출과 Spring이 앱 패키지 로거로 남기는 내용을 모두 점검 대상으로 본다.

**완결 필요(신규, 출시 전 개인정보 로깅 점검 대상) — 5xx 경로의 예외 메시지에 저장 값이 섞일 수 있다.** `GlobalExceptionHandler.handleUnexpected()`와 5xx `handleExceptionInternal()`은 `AuditLogger.logBatchFailure()`를 호출하는데, 이 메서드는 예외를 `setCause(e)`로 그대로 실어 스택트레이스와 예외 메시지를 로그에 남긴다. DB 오류 원문(예: 중복 키·컬럼 길이 초과 메시지)이 예외 메시지에 실리면 이메일·닉네임 같은 저장 값이 로그에 섞일 수 있다(`ageConfirmed`는 저장하지 않아 해당하지 않는다). 지금은 예외 메시지를 가리지 않고, 4xx는 이 경로를 타지 않는다. 출시 전에 개인정보 로깅 점검(예외 메시지 마스킹 여부, 로그 보관·접근 범위)을 하라. 참고로 이 메서드는 이름·필드가 배치 실패(`BATCH_FAILURE`, `auditSeverity=CRITICAL`)로 고정돼 있어 API 5xx를 같은 이벤트로 남기는 것 자체가 부적절할 수 있다 — 점검할 때 함께 보라.

### SCR-SRCH-01 / UIC-04·06 구현 결정 사항 (2026-09-27)

`feature/frontend/search-result` — 검색결과 목록 화면. HOME-01이 만든 `MainLayout`/`ComplexCard`/
`EmptyState`/`Spinner`/`SearchBar`/`useFavoriteToggle`을 그대로 재사용하고, 새로 UIC-04(`FilterPanel`+
`RangeSlider`)·UIC-06(`Pagination`)·`BottomSheet`(모바일 필터 전용)를 추가했다. `SearchBar`(UIC-03)에는
지역 자동완성(ARIA combobox)을 얹었다.

**0단계 계약 대조(실 로컬 백엔드로 직접 호출해 확인, 문서/프롬프트 추정에 기대지 않았다):**

| 항목 | 확인 결과 |
| --- | --- |
| `GET /api/complexes/search` | `regionCode`/`keyword`(상호 배타, 최소 하나 필수 — 없으면 400 `MISSING_SEARCH_CONDITION`) + `housingTypes`(반복 파라미터) + `dealCategory`/`rentType` + `areaMin/Max`·`amountMin/Max`(만원)·`buildYearMin/Max` + `sort`(LATEST/AMOUNT/AREA) + `page`(0-base)/`size`. 응답에 `pageMeta`(0-base page) 형제 필드 — CLAUDE.md 응답 포맷 절 그대로 |
| `sort` 파라미터와 Spring `Pageable`의 기본 `sort` 파라미터 이름 충돌 | 실제로 문제 없음(실측 확인) — `ComplexRepositoryCustomImpl`이 `pageable.getSort()`를 쓰지 않고 `ComplexSearchCondition.sort()`(LATEST/AMOUNT/AREA enum)로만 정렬해, Spring이 "AMOUNT"를 Pageable Sort 프로퍼티명으로 잘못 파싱해도 아무 영향이 없다 |
| SALE 응답에 `rentType`/`monthlyRentAmount` | 키 자체가 응답 JSON에서 빠진다(`non_null` 직렬화) — `ComplexSummaryResponse` 프론트 타입에 `?:`(선택 프로퍼티, `| undefined`가 아니라 키 자체 부재)로 반영 |
| `GET /api/regions?query=` | `{legalDongCd, fullPath}[]` — 자동완성이 반환하는 값 그대로 `regionCode`/`regionLabel`로 쓰면 됨(실제 폐지된 시군구 대표행은 이미 서버가 걸러줌, CLAUDE.md "선택 불가능한 legalDongCd" 절 참고) |
| `POST /api/search/logs` | `{keyword}` 바디, 인증 불필요, 성공 시 `ApiResponse<null>` |
| 건축년도 슬라이더 하한 | 로컬 DB `MIN(YEAR(approval_date))=1968`(2개 단지), 1970년 준공 1개 — 둘 다 꼬리값이라 더 둥근 **1970**을 하한으로 확정(`BUILD_YEAR_MIN`, `searchParams.ts`) |
| HOME-01 기존 구현 | `HeroSection`이 이미 `/search?housingType=..&dealType=SALE\|JEONSE\|WOLSE&keyword=..`로 navigate하고 있었다 — 실제 백엔드 계약(`housingTypes` 복수/반복, `dealCategory`+`rentType`)과 맞지 않는 옛 파라미터 모양이라 이번에 `useExecuteSearch` 공유 훅으로 교체했다(아래 표) |

**확정 사항(프롬프트가 요구한 9개 항목 — 근거/구현 위치):**

| # | 확정 내용 | 구현 위치 |
| --- | --- | --- |
| 1 | HOME-01 히어로·인기검색어 칩·SRCH-01 재검색이 `useExecuteSearch()` 하나를 공유한다. `base`(현재 SearchFilters)를 넘기면 그 필터를 유지한 채 regionCode/keyword만 교체(재검색), 안 넘기면 기본 필터로 새로 시작(히어로) | `features/search/useExecuteSearch.ts` |
| 2 | 로그는 `mode:'region'`(fullPath)·`mode:'keyword'`(자유 텍스트)에서만 발생, `mode:'chip'`(인기검색어)은 로그 없음. SRCH-01 안의 필터/정렬/페이지/새로고침/뒤로가기는 애초에 `logSearch()`를 호출하는 경로 자체가 없다(재검색 바 제출/자동완성 선택만 호출) | `useExecuteSearch.ts`, `features/search/api.ts`(`logSearch`는 실패를 삼키는 fire-and-forget) |
| 3 | regionCode/keyword는 `serializeSearchParams()`/`useExecuteSearch`가 항상 상호 배타적으로만 싣는다(하나 채우면 다른 하나는 `undefined`) | `searchParams.ts` |
| 4 | 조건 없이 진입 시 API 호출 없이 안내 문구(`hasSearchCondition` 가드), 2자 미만 키워드는 클라이언트에서 막고(`isKeywordTooShort`) 서버 호출 자체를 안 함, `maxLength=50` | `SearchResultsPage.tsx`, `SearchBar.tsx` |
| 5 | `regionLabel`은 표시 전용(API에 안 실림) — URL에 없으면 "선택한 지역"으로 폴백 | `SearchResultsPage.tsx`의 `conditionLabel` |
| 6 | 매매=`dealCategory=SALE`, 전세=`rentType=JEONSE`, 월세=`rentType=WOLSE`. 금액 슬라이더 라벨은 매매="거래금액", 전세·월세="보증금". 기본값(매매/APT+VILLA/LATEST/1페이지/슬라이더 전체범위)은 URL에서 생략 | `searchParams.ts`(`dealTypeToApiParams`, `AMOUNT_LABEL`) |
| 7 | 면적 10~200㎡, 금액 0~20억(만원 단위 0~200000), 건축년도 1970~올해(동적) — 손잡이가 상한에 있으면 그 파라미터를 아예 생략(서버 입장에선 "이상") | `searchParams.ts`(`AREA_RANGE`/`AMOUNT_RANGE`/`BUILD_YEAR_MIN`, `buildApiQuery`) |
| 8 | 매물유형 체크박스는 마지막 하나를 해제할 수 없다(최소 1개 유지) | `FilterPanel.tsx`(`toggleHousingType`) |
| 9 | `/map` 플레이스홀더 라우트 | 이미 존재 확인, 변경 불필요(`AppRouter.tsx`) |

**SIMILAR 카드 캡션 정정** — Figma 원문("검색 조건과 정확히 일치하지 않는 유사 매물입니다")은 SIMILAR가
실제로 뜻하는 바(BAT-MAT-02의 단지 마스터 매칭 신뢰도 문제, "검색 조건 불일치"가 아니다)를 잘못 설명해
정정한 문구("지번 등 일부 정보가 정확히 일치하지 않아 유사도 기준으로 추정 매칭된 결과입니다")로 바꿨다
— `ComplexCard.tsx`의 `SIMILAR_CAPTION` 상수, 코드 주석에 정정 근거를 남겼다.

**태블릿 레이아웃 — 드로어 아님, 데스크톱과 같은 사이드바+리스트 구조.** 이전 세션(HOME-01)이 이미
Figma 태블릿 결과 화면 스크린샷을 확인해 둔 결과와 일치 — `md:` 단일 브레이크포인트로 데스크톱/태블릿을
공유하고 `<768px`만 모바일(바텀시트+무한스크롤) 레이아웃으로 분기했다. 768px 실측 스크린샷으로 재확인함.

**버그 발견·수정 1 — StrictMode 개발 모드 이중 마운트가 뒤로가기 복원 캐시를 텅 빈 상태로 오염시켜
실제 검색 API 호출 자체가 스킵되는 결함(Playwright로 실측 발견, 코드 리딩만으로는 못 잡았을 종류).**
뒤로가기 복원용 모듈 스코프 캐시(`scrollCache`, `location.key`로 색인)를 "스크롤 이벤트 + effect
cleanup" 양쪽에서 쓰도록 설계했는데, React 19 StrictMode의 개발 모드 mount→cleanup→remount가 데이터
조회 effect보다 스크롤-저장 effect의 클린업을 먼저(또는 같은 틱에) 실행시키면서 `dataRef.current`가
아직 초기값(`accumulated=[]`, `pageMeta=null`)인 상태를 그 URL의 캐시 엔트리로 그대로 저장해버렸다 —
뒤이어 실행되는 진짜 데이터 조회 effect가 이 "캐시 히트"를 신뢰해 실제 API 호출 자체를 건너뛰고 빈
목록을 "조건에 맞는 단지가 없습니다"로 잘못 렌더링했다(정상 regionCode로 진입해도 항상 이 상태가
됐다 — `npm run dev`로 뜬 개발 서버에서 100% 재현). **수정**: `hasFetchedRef`(실제 응답을 한 번이라도
받았는지) 가드를 추가해, 이 가드가 `true`가 되기 전에는 캐시에 쓰지 않는다.

**버그 발견·수정 2 — (수정 1 이후) 카드 클릭→DTL-01 이동 시 캐시에 저장되는 스크롤 위치 자체가
틀린 값(0)이었다.** 원래 effect cleanup(언마운트) 시점에 `window.scrollY`를 다시 읽어 캐시에 저장하는
로직이 있었는데, 이 값이 항상 `0`으로 기록돼 실제 위치(예: 2993px)가 아니라 페이지 맨 위로 복원되는
버그가 있었다. 원인: 카드 클릭으로 짧은 DTL-01 자리표시 페이지가 마운트되는 순간 브라우저가 "문서
높이가 현재 스크롤 위치보다 짧아졌다"는 이유로 스크롤을 즉시 0으로 clamp하는데, React가 SRCH-01
컴포넌트의 cleanup(그 안의 재저장 호출)을 실행하는 시점엔 이미 그 clamp가 끝난 뒤라 옳은 값을 읽을
방법이 없었다. **수정**: 언마운트 시점의 재저장을 아예 제거하고, 'scroll' 이벤트가 실제로 발생하는
동안의 저장만으로 충분하다는 결론(페이지를 떠나기 직전의 마지막 'scroll' 이벤트가 이미 올바른 값을
남겨 둔다)으로 단순화했다. Playwright로 모바일 30+ 아이템 누적 후 카드 클릭→뒤로가기 시나리오를 직접
재현해 두 수정 모두 확인했다(`srch01-mobile-check.mjs`).

**버그 발견·수정 3 — 재검색 바가 URL의 기존 `regionLabel`/`keyword`로 미리 채워진 채 마운트되면,
사용자가 손대지 않았는데도 자동완성 드롭다운이 자동으로 열려 바로 아래 "필터" 버튼 등의 클릭을
가로챘다(Playwright `locator.click()`의 pointer-events 가로채기 에러로 실측 발견).** `SearchBar`의
자동완성 디바운스 effect가 `value`가 바뀔 때만이 아니라 **마운트 시에도** 한 번 실행돼, 초기값이 2자
이상이면 사용자가 포커스하지 않았어도 자동완성을 조회·오픈했다. **수정**: `focused` state를 추가해
입력이 실제로 포커스된 동안에만 자동완성 조회·오픈이 일어나도록 게이트를 걸었다(`SearchBar.tsx`).

**검증(Playwright, 실 로컬 백엔드+dev 서버, 저장소 `frontend/e2e/`, 총 61/61 통과)** — 아래 "후속
코드리뷰 대응" 절 참고, `srch01-*.mjs` 6개 스크립트로 나뉜다(상세는 `frontend/e2e/README.md`):
- `srch01-basic-check.mjs`(13) — regionCode/keyword 검색 렌더링, 조건 없음/1자 키워드 시 API
  미호출, 매매↔전세 전환 무오류, 정렬 변경 시 로그 미호출, 자유 텍스트 재검색 시 로그 정확히 1회
  (payload 키워드 일치 포함), 데스크톱 페이지 이동 시 목록 교체(누적 아님).
- `srch01-mobile-check.mjs`(10) — 필터 버튼→바텀시트 열림/Esc·백드롭 닫힘/스크롤 잠금, 무한스크롤
  30+ 누적, 카드 클릭→뒤로가기 시 누적 목록·스크롤 위치 복원.
- `srch01-favorite-and-desktop-back-check.mjs`(7) — 비로그인 하트 클릭→`/login` 이동, 데스크톱
  2페이지 이동 후 카드 클릭→뒤로가기 시 같은 페이지(page=2)·같은 목록 유지, HOME-01 히어로 검색
  회귀(무오류, `/search`로 정상 이동).
- `srch01-slider-boundary-and-wolse-check.mjs`(9) — 슬라이더 하한 경계(아래 "코드리뷰 확인 질문
  답변" 참고), 월세 카드 표시.
- `srch01-keyboard-and-error-check.mjs`(16) — 자동완성 키보드 네비게이션, 슬라이더 방향키, 바텀시트
  포커스 트랩, 에러 배너 재시도, 잘못된 regionCode 서버 메시지.
- `srch01-tablet-check.mjs`(6) — 768px 사이드바 레이아웃, 필터·페이지네이션 왕복.

### SCR-SRCH-01 후속 코드리뷰 대응 (2026-09-27, 같은 날 2차)

PR 리뷰가 확인을 요구한 두 질문과, Figma 9개 노드를 실제로 스크린샷 대조해 찾은 구조적 불일치를
정리한다. 브랜치는 그대로 `feature/frontend/search-result`.

**확인 질문 1 — StrictMode 버그 수정 방식.** StrictMode를 끄거나 우회하지 않았다(`main.tsx`의
`<StrictMode>`는 그대로 있다, 확인 완료). 수정은 effect를 멱등하게 만드는 방식이었다 —
`hasFetchedRef`(실제 응답을 한 번이라도 받았는지) 가드를 추가해, StrictMode의 개발 모드
mount→cleanup→remount가 몇 번을 돌든 "아직 데이터를 못 받은 상태"의 저장 시도는 전부 no-op이 되게
했다. 이 가드는 StrictMode 유무와 무관하게 항상 성립하는 불변식(빈 상태를 캐시에 쓰지 않는다)이라,
StrictMode를 끄더라도 그대로 안전하게 남는 코드다.

**확인 질문 2 — 슬라이더 하한 경계.** 세 슬라이더(전용면적/거래금액/건축년도) 모두 `buildApiQuery()`/
`serializeSearchParams()`의 생략 조건이 `filters.xxxMin !== defaults.xxxMin`으로 대칭적으로
구현돼 있어, **상한만 옮기고 하한을 건드리지 않으면 하한 파라미터 자체가 요청에 실리지 않는다**
(값이 "1970"으로 전송되는 게 아니라 파라미터 키 자체가 없다 — 서버 입장에서는 "하한 필터 없음"과
동일하다). 즉 건축년도 UI 하한(1970)이 실제 DB 최솟값(1968)보다 높아도, 사용자가 하한을 건드리지
않는 한 1968~69년 준공 단지가 걸러지는 일은 없다. 이전엔 이 사실을 코드 리딩만으로 판단했는데, 이번에
Playwright 네트워크 캡처로 직접 확인했다 — 상한만 바꿔 "필터 적용"을 눌렀을 때 실제 요청 URL에
`buildYearMin`/`areaMin`/`amountMin` 파라미터가 전혀 없음을 확인했고, 기본 상태(필터 미적용)에서
실제로 "건축 1987년"처럼 1990년 이전 준공 단지가 목록에 포함됨을 확인했다(검증:
`srch01-slider-boundary-and-wolse-check.mjs`).

**Figma 시각 비교 — 9개 노드(fileKey `bStwE4wZ5kXm7K6fBMeg6Z`) 스크린샷 대조로 발견한 구조적
불일치.** 처음 구현할 때는 그리드 카드(HOME-01)의 레이아웃을 그대로 가져다 세로로만 재배치했는데,
실제 Figma(4:1704 데스크톱/24:9405 모바일/24:10388 태블릿 결과 목록)를 스크린샷으로 대조해보니 여러
군데가 실제와 달랐다:

| 불일치 | 수정 |
| --- | --- |
| 재검색 바에 제출 버튼이 없었다(Enter만 가능) | `SearchBar`의 `inline` variant에 검색 아이콘+"재검색" 텍스트 버튼을 추가했다(Figma 노드 24:9445 구조) — 입력창 옆에 나란히, `type="submit"`이라 Enter와 동일한 `submit()` 경로를 탄다. |
| 필터 패널에 "필터" 제목과 상단 초기화 링크가 없었다(하단 "초기화"+"필터 적용" 버튼 쌍만 있었음) | `FilterPanel`에 `showHeader` prop(기본 true)을 추가해 "필터" 제목 + 작은 "↺ 초기화" 링크를 위에 그린다(아이콘은 Figma 노드 4:1777 실제 SVG, `RotateCcwIcon` 신설). 모바일 `BottomSheet`는 자기 헤더에 이미 "필터" 제목이 있어 중복을 피하려 `showHeader={false}`로 끈다 — 데스크톱/태블릿 사이드바만 이 헤더를 그린다. |
| 카드 레이아웃이 그리드 카드처럼 가격을 주소 아래 왼쪽 컬럼에 세로로 쌓고 있었다 | 실제 Figma는 [썸네일 \| 배지·이름·주소·메타(건축년도 포함, flex-1) \| 하트·가격·㎡당가격(오른쪽, `items-end`)] 3분할 구조다. 메타 줄에서 ㎡당가격을 빼고 대신 "· 건축 {year}년"을 추가했다(`complex.approvalDate.slice(0,4)`) — ㎡당가격은 오른쪽 컬럼의 가격 바로 아래로 옮겼다. SIMILAR 배너는 이 3분할 행 전체 아래에 카드 전체 너비로 걸치도록 바꿨다(이전엔 왼쪽 텍스트 컬럼 안에만 있었다). |
| 모바일에 "필터"/"지도" 버튼이 각각 따로였다(필터 버튼 전체너비 하나, 지도 링크는 정렬 바 안에) | Figma(24:9405)는 이 둘을 한 행에 나란히 두고(필터 버튼엔 카운트가 "필터 3"처럼 이어붙은 문자열이 아니라 원형 배지로 분리돼 있다 — `<span>` 배지로 구현), 정렬 바(총 N건+정렬칩)에는 모바일에서 지도 링크를 다시 넣지 않는다(`md:flex` 이상에서만 표시). |
| `FilterIcon`이 Figma 미대조 추정 아이콘이었다 | Figma 노드 24:9456(모바일 필터 버튼 아이콘)의 실제 SVG로 교체했다 — 3줄 슬라이더 픽토그램. |
| 월세 카드에도 ㎡당가격이 표시되고 있었다 | 월세는 대표거래 금액이 보증금뿐이라 월세금액을 빼고 계산한 "㎡당가격"이 실제 총비용을 왜곡한다 — `complex.rentType === 'WOLSE'`일 때 이 줄 자체를 렌더링하지 않는다(검증: `srch01-slider-boundary-and-wolse-check.mjs`). |

**태블릿(768px) 레이아웃도 스크린샷(24:10388)으로 재확인 — 데스크톱과 완전히 같은 사이드바 구조,
드로어 아님(기존 판단 재확인).** `srch01-tablet-check.mjs`로 사이드바 표시·모바일 전용 필터
알약 버튼 미표시·필터 적용/페이지네이션 왕복까지 실제 상호작용으로 검증했다(6/6) — 스크린샷 확인만
하고 상호작용은 안 해봤던 이전 상태의 완결 필요를 해소했다.

**추가 접근성 보완 — `aria-valuetext`.** 네이티브 `<input type="range">`는 `aria-valuenow`/min/max는
자동으로 노출하지만 사람이 읽는 값 설명(`aria-valuetext`, 예: "10㎡", "20억 이상")은 붙지 않는다 —
`RangeSlider`의 두 range input에 `aria-valuetext`를 추가해 스크린리더가 실제 단위·"이상" 의미까지
읽어주게 했다.

**검증**: 자동완성 키보드 네비게이션(↑/↓로 하이라이트 이동, Esc로 닫힘, 하이라이트 상태에서 Enter 시
지역 선택, 아무 것도 선택 안 한 채 Enter 시 자유 텍스트 검색 폴백, 자동완성 API 실패해도 자유 텍스트
검색 가능), 슬라이더 방향키 조작+숫자 입력 동기화, 바텀시트 포커스 트랩(Tab 30회 반복해도 시트 밖으로
안 나감), 에러 배너(서버 5xx 메시지 그대로 표시) → "다시 시도" 재요청 성공, 잘못된 regionCode 형식의
서버 메시지("지역 코드가 올바르지 않습니다") 표시 — 전부 `srch01-keyboard-and-error-check.mjs`(16/16).

**e2e 스크립트를 저장소로 이동 완료.** 저장소 밖 `C:\Users\super\homesense-e2e-scripts\`(캐시 정리
시 유실 위험이 있던 임시 보관 위치)에 있던 27개 Playwright 스크립트 전부를 `frontend/e2e/`로 옮겨
커밋했다 — AUTH-02/AUTH-03/SCR-LEGAL-01/SCR-SRCH-01 전체(위 각 절이 "저장소 밖에 보관"이라고 적어둔
문장들은 이제 stale하다, 실제 위치는 `frontend/e2e/`). CI 편입은 하지 않았다(요청 범위 밖) — 실행
전제·명령어는 `frontend/e2e/README.md` 참고. `run-all.mjs`에 SRCH-01 6개 스크립트를 추가했다. 저장소
편입 후 처음으로 실 백엔드로 전체 스위트를 돌리며 이 작업과 무관한 잔여 문제 하나를 발견·수정했다 —
`auth03-password-reset-check.mjs`가 여전히 옛 버튼 문구("재설정 다시 요청")를 찾고 있었다(AUTH-03
Figma 대조 재작업 때 "재설정 링크 다시 요청"으로 바뀌었지만, 당시 Docker가 꺼져 있어 실 백엔드로
재실행해 확인한 적이 없었다 — CLAUDE.md SCR-AUTH-03 절에 이미 "다음에 백엔드가 떠 있는 세션에서 한 번
돌려 확인하라"로 남겨져 있던 항목). 스크립트의 locator 문자열만 고쳐 23/23으로 통과시켰다 — SRCH-01/
HOME-01 코드와는 무관하다.

**후속 확인(PR 리뷰 요청, 2026-09-27 3차) — `ComplexCard`(UIC-05)는 HOME-01과 공유하는 컴포넌트라,
`list` variant 재작업이 `grid` variant(HOME-01 인기 단지)에 새어들지 않았는지 별도로 확인했다.**
코드 확인 — `grid` 분기(122~147행)는 이번 세션 전체에서 단 한 줄도 건드리지 않았다(이 세션에서 실제로
수정한 것은 51~120행의 `list` 분기와 그 안에서만 쓰는 `favoriteButtonList`뿐이고, `grid` 분기가 쓰는
`favoriteButton`은 1차 라운드에서 이미 확정된 형태 그대로다). Figma 대조 — HOME-01 프레임(3:2 로그인/
4:1232 비로그인 데스크톱, 24:7860 태블릿, 24:7370 모바일)을 스크린샷으로 다시 받아, 같은 `bStwE4wZ5kXm7K6fBMeg6Z`
파일 안에 HOME-01과 SRCH-01이 나란히 있다는 것도 함께 확인했다(별도 파일이 아니다). 라이브 렌더링
대조 — 실제 로컬 백엔드로 뜬 HOME-01을 1280/768/392 세 뷰포트에서 스크린샷으로 캡처해(`home01-card-
screenshots.mjs`) Figma와 나란히 비교한 결과 완전히 일치했다(원형 하트가 썸네일 위 오버레이, 배지
좌하단, 가격이 주소 바로 아래 같은 컬럼, 건축년도·㎡당가격 없음, 모바일은 가로 스크롤 캐러셀) —
의도치 않은 변화 없음. 이 확인을 매번 스크린샷 육안 대조로 반복하지 않도록 `home01-card-check.mjs`
(신규, 18/18 — 3개 뷰포트 × "건축"/"만원당㎡" 문구 없음·하트 절대위치·pageerror 없음·비로그인 하트
클릭 시 `/login` 이동)로 자동화해 `run-all.mjs`에 추가했다. **테스트 작성 중 발견한 사실(버그
아님)** — `RecommendedComplexes.tsx`가 모바일 가로 스크롤용과 데스크톱/태블릿 그리드용 두 세트의
카드를 항상 함께 렌더링하고 CSS(`hidden md:grid` 류)로 뷰포트에 맞는 쪽만 보이게 하는 기존 구조라(둘
다 렌더되므로 `button[aria-pressed]` 개수가 항상 카드 수의 2배로 나온다), 테스트는 `:visible` 필터로
현재 뷰포트에서 실제로 보이는 카드만 골라야 한다 — 이 프로젝트의 다른 반응형 분기(위 `srch01-tablet-
check.mjs`가 "필터 적용" 버튼과 모바일 전용 "필터" pill을 혼동했던 것과 같은 종류의 테스트 함정).

**최종 e2e 실행 결과(2026-09-27, 이번 카드 재작업 이후의 코드로 실 백엔드+dev 서버 재기동 후 전체
재실행)**: `frontend/e2e/`의 25개 테스트 스크립트(스크린샷 전용 2개 제외) **전부 통과** —
`run-all.mjs`의 22개 스크립트가 전부 exit 0(`node run-all.mjs`로 확인), `run-all.mjs` 목록에 없는
독립 스크립트 3개(`auth03-figma-parity-check` 19/19, `auth03-transient-token-error-check` 9/9,
`auth03-form-cooldown-check` 9/9)도 개별 실행해 확인했다. SRCH-01 전용 스위트만 좁혀 보면 여전히
61/61(basic 13/mobile 10/favorite-and-desktop-back 7/slider-boundary-and-wolse 9/keyboard-and-error
16/tablet 6)이고, 이번에 추가한 `home01-card-check`(18/18)는 이 61건과 별도로 카운트한다 — 즉 SRCH-01
자체의 61/61은 카드 재작업 전후로 변함이 없고(재작업이 `list` 분기 안에서만 일어났으므로), 이번에
새로 확인·자동화한 것은 "그 재작업이 `grid` 분기(HOME-01)에 영향을 주지 않았다"는 사실이다.

**MAP-01 재사용을 위한 설계 — `FilterPanel`은 SRCH-01 전용 요소(결과 카운트, URL 동기화)를 갖지 않고
`draft`+콜백 4개(`onChangeDraft`/`onApply`/`onReset`)만 받는다.** MAP-01이 이 컴포넌트를 그대로
가져다 쓰되, 지도 뷰포트 기반 필터(팬/줌 시 자동 갱신 등 MAP-01 고유 요구)는 별도로 얹어야 한다 —
`RangeSlider`도 마찬가지로 범용이다.

### SCR-SRCH-01 버그 수정 — 필터 패널의 미적용 draft가 재검색 시 조용히 무시됨 (2026-09-27, 실사용자 리포트)

**증상(사용자 리포트 원문)**: "메인화면에서 거래유형을 매매/전세/월세 원하는 것을 클릭한 뒤에 검색을
하면 거래 유형에 맞게 잘 검색이 되는데 검색 결과 화면에서 거래 유형을 선택하고나서 재검색 하려고
하면 재검색이 되지 않습니다." — HOME-01에서는 정상, SRCH-01에서만 재현.

**재현(Playwright로 실측 확인)**: SRCH-01에서 필터 패널로 월세를 선택하고 "필터 적용"을 눌러
`rentType=WOLSE`를 URL에 커밋한다. 이어서 필터 패널에서 매매로 바꾸되 **"필터 적용"을 누르지 않고**
재검색바(SearchBar)에서 같은 지역명으로 Enter를 치면, 실제 API 요청은 여전히 `rentType=WOLSE`로
나갔다 — 방금 화면에서 선택한 "매매"가 반영되지 않고 조용히 이전 값으로 검색됐다.

**원인**: `SearchResultsPage.handleSubmitKeyword()`/`handleSelectRegion()`이 공유 훅
`useExecuteSearch()`를 호출할 때 `base` 인자로 `filters`(URL에서 파싱한, 이미 커밋된 값)를 넘기고
있었다 — `draft`(FilterPanel이 들고 있는, "필터 적용"을 누르기 전까지는 URL에 반영되지 않는 현재
선택 상태)를 넘겨야 했는데 반대로 짰다. `useExecuteSearch()`는 `dealType`/`housingTypes`를
`overrides`가 없으면 `base`(=`start`)에서 그대로 가져오므로(`features/search/useExecuteSearch.ts`),
재검색 시 `draft`에만 존재하는 미적용 변경이 통째로 버려지고 URL의 옛 값으로 대체됐다. HOME-01의
히어로 검색은 이 문제가 없는데, HOME-01은 애초에 "적용" 버튼이 있는 별도 draft 상태 없이 토글을
누르는 즉시 그 값으로 검색을 실행하기 때문이다(SRCH-01만의 2단계 draft→적용 구조에서만 성립하는
버그).

**수정**: 두 핸들러 모두 `base` 인자를 `filters` → `draft`로 교체했다(`SearchResultsPage.tsx`). 이제
재검색은 "필터 패널에 현재 보이는 선택 상태"를 그대로 유지한 채 지역/키워드만 교체한다 — 사용자
입장에서는 "화면에 선택된 대로 검색된다"는 직관과 일치한다. 재검색 후에는 URL이 바뀌므로 기존
동기화 로직(`filters !== filtersSnapshot`이면 `setDraft(filters)`)이 곧바로 draft를 새 URL 값으로
다시 맞춘다 — 그래서 재검색 직후 필터 패널도 "매매"가 선택된 상태로 정확히 보인다(별도 처리 불필요,
기존 동기화 effect가 이미 담당).

**검증**: 신규 `frontend/e2e/srch01-draft-carryover-check.mjs`(6/6) — 필터 적용으로 월세 커밋 →
"적용" 없이 매매로 바꾸고 키워드 재검색 → 요청에 `dealCategory=SALE`이 실리고 `rentType=WOLSE`가
남지 않음 → URL에는 매매(기본값)라 `dealType` 파라미터 자체가 생략됨 → 재검색 후 필터 패널이
"매매"를 선택 상태로 보여줌(draft/filters 재동기화) → 같은 방식으로 지역 자동완성 선택 경로도
전세(`rentType=JEONSE`)가 정확히 반영됨. `run-all.mjs`에 추가(23개 스크립트 전체 재실행, 전부 통과 —
SRCH-01 기존 61건 + `home01-card-check` 18건 + 이번 신규 6건, 회귀 없음).

### SCR-DTL-01 단지 상세 구현 결정 사항 (2026-09-30, `feature/frontend/complex-detail`)

`/complexes/:id` 화면. 백엔드 보강은 위 "DTL-01 백엔드 보강" 절. 확인에 쓴 문서: UI정의서 v2.1.1·프로그램설계서 v2.1.1(Downloads 사본, `docs/specs/` 없음). **Figma 대조 완료(2026-09-30, 같은 브랜치 후속 커밋)** — 처음 구현할 때는 MCP 커넥터 인가가 안 돼 Figma 없이 만들었고, 인가 후 fileKey `bStwE4wZ5kXm7K6fBMeg6Z`의 데스크톱 4:2972·6:3539, 태블릿 24:12996·24:13557, 모바일 24:11647·24:12220을 360/768/1280 화면과 나란히 대조해 고쳤다(아래 "Figma 대조" 표). 직접 그렸던 아이콘 9종은 Figma SVG로 바꿨다: `ShareIcon`·`ChevronDownIcon`(접기는 같은 도형을 180° 회전 — Figma 6:3756과 같다)·`UsersIcon`·`BuildingIcon`·`CalendarIcon`·`CarIcon`·`LayersIcon`은 경로를 그대로 옮겼고, `HammerIcon`은 Figma가 렌치라 `WrenchIcon`으로 바꿨으며, `MapPinIcon`은 Figma에 대응 아이콘이 없어(주소 앞 핀 없음, 지도 버튼은 기존 `MapFoldIcon`과 같은 도형) 지웠다. 뒤로가기·경고·정보·하트 아이콘은 Figma와 같은 도형의 기존 아이콘(`ArrowLeftIcon`·`AlertTriangleIcon`·`InfoCircleIcon`·`HeartIcon`)을 재사용했다. 거래상세 모달은 Figma에 전용 노드가 없어 MY-02 삭제 다이얼로그(데스크톱 6:4995, 모바일 28:15713)를 기준으로 공용 `Modal` 모양을 맞췄다(아래 "거래상세 모달" 행).

**Figma 대조 — 고친 것과 남긴 차이(2026-09-30)**

| 영역 | 고친 것 | 남긴 차이와 이유 |
| --- | --- | --- |
| 페이지 | 좌우 여백 모바일·태블릿 16px, 1280px 이상 32px, 위 20·아래 40px, 경로 줄 뒤 20px·카드 사이 16px, 본문·사이드바 간격 20px(`xl:gap-x-5`), 사이드바 300px | 데이터 출처 두 줄은 본문에 넣지 않는다(공용 Footer가 같은 문구를 보여 준다 — 아래 "데이터 출처 두 줄" 행) |
| 경로 | "← 뒤로 \| 시도 › 시군구 › 단지명", 뒤로 16px, 경로 12.5px, 구분자 #d1d5dc | — |
| 헤더 카드 | 여백 모바일 20·그 이상 24px, 배지 11px 굵게(`DataTrustBadge size="md"` 신설 — 카드용 sm은 그대로), 단지명 모바일 24px·그 이상 28px 자간 −0.5, 주소 13px #99a1af(핀 아이콘 없음), 버튼 radius 14·테두리 #e5e7eb·12.5px, 모바일 아이콘만 46×34, 근사 안내 상자를 카드 맨 위로 옮기고 색·크기(#fffbeb·#fee685, 제목 12.5·본문 11.5) | 근사 안내 본문은 Figma 문구("일부 정보는 유사 매칭 결과입니다…") 대신 기존 정정 문구를 유지했다(결정 5, SRCH-01 카드와 같은 뜻). 버튼 글자는 Figma대로 "관심등록"(붙여 씀), 등록된 상태 "관심등록됨"은 Figma에 없어 추가 |
| 기본정보 요약 | 제목 "단지 기본정보 요약"(13px 굵게 회색) 추가, 아이콘 타일 36px·radius 14, 라벨 11px #99a1af, 값 14px 굵게, 간격 16px, 모바일 2열 | — |
| 상세정보 | 접힘: 왼쪽 ▾+제목, 오른쪽 안내(모바일 숨김). 펼침: 회색 상자 대신 그룹 제목 11px 브랜드색·아래 선, 라벨 120px·값 오른쪽 정렬 12.5px 반굵게, 2열 간격 40/24px | 그룹 구성·제목은 지성 확정 6그룹 그대로(Figma는 5그룹, "분양 / 세대 구성"처럼 띄어 쓴 제목) |
| 거래유형 탭 | 이력 카드 안 회색 세그먼트 → 가운데 정렬한 별도 알약 카드(선택 브랜드 배경·흰 글자, 13.5px) | — |
| 이력 표 | 제목 15px, 머리행 #f9fafb·11px 굵게 회색, 숫자 열 오른쪽 정렬, 행 교차 배경(#fafafa), 해제 행 옅은 빨강 배경·회색 취소선·거래유형 배지 반투명, 직거래 배지 파랑, 등기 전·해제 배지 10px 굵게, "완료" 회색, 범례를 점(●)+문구로 바꿔 카드 아래쪽에(모바일 세로 쌓임) | 범례 문구는 지성 정정대로("등기 전 — 소유권 이전등기가 아직 확인되지 않은 거래", "해제 — 계약 해제"). 해제일 표시와 "더보기" 버튼은 Figma에 없지만 스펙(UI정의서 5.3)·결정 5 때문에 유지 |
| 가격 추이 | 여백 20px, 제목 14px, 금액 17px 브랜드색, 상승 #fb2c36, 차트 높이 140px·점선 가로선 3개·부드러운 곡선·작은 채운 점, 아래 정보 아이콘+10.5px 안내 | 축 월 표기는 우리 집계 창(첫 달·5번째·9번째·마지막 달)이라 Figma 예시 달과 다를 수 있다 |
| 위치 | 여백 20px, 제목 14px, 지도 자리 160px·radius 14, 주소 11px, 버튼 브랜드 외곽선+지도 아이콘 | Figma의 지도 그림(핀이 꽂힌 가짜 지도)은 좌표가 없는 지금 실제 위치처럼 읽혀 쓰지 않고 "지도 위치는 준비 중입니다" 자리표시를 유지했다. 버튼은 MAP-01이 없어 비활성(반투명)과 "지도 화면 준비 중" 안내 유지(결정 2) |
| GNB·하단 탭 | — | 공용 레이아웃이라 대조 범위 밖. Figma 데스크톱의 알림 벨은 SCR-HOME-01 결정(UI정의서에 없음)대로 넣지 않는다 |

| 항목 | 결정 | 근거 | 무효화 조건 |
| --- | --- | --- | --- |
| 1. 가격 추이 클라이언트 집계 | 매매 이력(해제 제외)으로 이번 달 포함 12개월을 화면에서 집계한다(`pages/complex/priceTrend.ts`). 대표 면적 구간(전용면적 소수점 버림)은 건수 → 최근 거래 → tradeId 순으로 고르고 월 평균을 잇는다. 데이터 월이 2개 미만이면 "최근 1년 매매 거래가 적어 추이를 표시할 수 없습니다". 변동률 기간은 첫~마지막 데이터 월을 포함한 개월 수("(7개월)"), 12개월 이상이면 "(1년)"(지성 결정 7). 차트는 의존성 없이 SVG로, 폭은 카드 폭을 재서 viewBox로 쓴다(늘리면 글자가 가로로 늘어났다) | 통계 API(API-STT-01)가 5단계 범위라 없다 | API-STT-01이 생기면 그 응답으로 바꾼다 |
| 2. 위치 자리표시 | 주소와 "지도 위치는 준비 중입니다", 비활성 "지도에서 크게 보기"와 "지도 화면 준비 중". `locationPrecision`이 PRECISE가 아니면 "근사 위치입니다". 카카오 SDK는 불러오지 않는다 | 로컬 DB 좌표 0건(BAT-GEO-01 미구현), MAP-01 없음 | BAT-GEO-01과 MAP-01이 생기면 지도로 바꾼다 |
| 3. 상세정보 6그룹 | DTO 기준 6그룹·28필드(백엔드 절의 매핑), 값이 모두 없는 그룹은 숨기고 주차/전기차 끝에 파생값 "세대당 주차대수"(총주차 ÷ 세대수, 소수 둘째 자리). Figma의 5그룹과 다르다 | 지성 확정 | `ExtendedInfo` 필드가 바뀌면 |
| 4. 전월세 탭 열 구성 | 전세: 계약일·면적·층·보증금, 월세: +월세. 거래유형·등기여부 열과 범례는 매매 탭에만 있고 전월세 행에는 "등기 전"을 띄우지 않는다 | 전월세 원천에는 거래유형·등기일자·해제·동이 없다(Phase 0 DB 확인) | 원천이 그 값을 주게 되면 |
| 5. 이력 20행 "더보기" | 이력 API가 목록 전체를 주므로 화면에서 20행씩 늘린다(탭별로 따로 센다). 거래유형은 공용 유틸 `features/trade/labels.ts` `dealingTypeLabel()`(AGENT → 중개거래, DIRECT → 직거래, 없음·모르는 코드 → "-")을 표와 모달이 같이 쓴다 | 단지당 최대 매매 209·전세 389·월세 988건(Phase 0) | 이력 API에 페이지네이션이 생기면 |
| 6. 비로그인 관심 등록 재생 | 기존 `useFavoriteToggle`의 sessionStorage `homesense.pendingFavoriteComplexId`를 그대로 쓴다(만료 없음 — 탭을 닫으면 사라진다). 로그인 후 상세로 돌아오면 재생된다. **가입으로 진행하면 가입 후 `/`로 가지만 재생은 홈에서 된다**(AUTH 화면은 바꾸지 않았다 — 아래 완결 필요 (a)) | 지성 결정 4 — localStorage였다면 생성 시각+10분 만료를 넣기로 했으나 sessionStorage라 변경 없음 | 저장소를 localStorage로 옮기면 만료를 넣는다 |
| 관심 등록/해제 실패 | 상태 코드를 가르지 않고 서버 문구 토스트(`getErrorMessage`) 뒤 관심 목록을 다시 받아 버튼을 서버 상태에 맞춘다(`resyncFavorites`). 같은 단지에 요청이 진행 중이면 두 번째 클릭은 보내지 않고 버튼을 비활성으로 둔다(`processingIds`). 스펙의 409 전용 문구는 쓰지 않는다. 확인 중(checking) 클릭은 비활성이 아니라 기존 규칙대로 대기 표시 후 판정 뒤 처리한다. **[2026-10-01, Codex P2] 등록/해제와 재동기화는 시작할 때 계정을 잡아 두고(`captureAccount` — 훅 안의 인증 상태 변경 번호 + `currentSessionGeneration()`), 응답이 왔을 때 둘 중 하나라도 바뀌었으면 하트 상태에 쓰지 않는다.** 재동기화는 시작 전에도 같은 판정을 한다. 버린 응답은 성공·실패 토스트도 띄우지 않는다(새 계정 화면에 이전 계정의 결과를 알리지 않음). 예외: 요청을 보내지 않은 인터셉터 거절(`SessionAccountChangedError`·`SessionNotConfirmedError`)은 방금 누른 클릭이 처리되지 않았다는 안내라 계정이 바뀌었어도 보인다 — 계정 변경 거절은 스스로 다시 확인(세대 증가)을 시작하므로 계정 판정으로 걸러 내면 이 안내가 사라진다. 진행 중 표시(`processingIds`)는 `runExclusive`의 `finally`가 결과 사용 여부와 무관하게 푼다 | 지성 결정 4, CLAUDE.md "확인 중 하트 클릭" 행. 홈·검색 카드도 같은 훅이라 같이 적용된다. 상태 값만 보면 다른 탭의 계정 변경(authenticated A → checking → authenticated B)을 구분하지 못해, A에서 시작한 재동기화·등록 응답이 B의 하트 상태를 덮어쓰고 B의 다음 해제가 A의 `favoritePropertyId`로 나가 접근 거부를 받았다. 검증: vitest 6건(재동기화·등록 성공·해제 성공·등록 실패의 늦은 응답 — 하트 상태 불변·토스트 없음·진행 중 해제, 상태 그대로 세대만 바뀐 경우, 인터셉터 거절 안내 유지) — 각 판정을 빼면 해당 테스트가 실패한다. e2e `home01-auth-checking-actions-check`·`auth-tab-account-sync-check`·`dtl01-detail-check` 통과(2026-10-01) | 계정별 상태를 쓰는 비동기 작업을 이 훅에 더하면 같은 `captureAccount`를 거친다 |
| matchMethod·근사 안내 | `matchMethod`가 null이면 정밀·근사 배지와 주황 안내 모두 없다. SIMILAR면 "유사 매칭 결과" 제목의 주황 상자(문구는 SRCH-01 카드의 정정 문구와 같은 뜻 — UI정의서 5.3 "일부 정보는 유사 매칭 결과입니다"·7.1 "참고용 정보입니다"). 상세 배지는 필터 없는 대표 거래 기준이라 SRCH-01 카드(필터 적용 대표 거래)와 다를 수 있다(백엔드 절) | 지성 결정 5. `DataTrustBadge`는 `housingType`이 없으면 유형 배지를 생략하도록 넓혔다(홈·검색 카드는 항상 값이 있어 영향 없음) | — |
| 데이터 로딩 | 상세와 이력은 독립(`useComplexDetailData`). 잘못된 id(양의 정수가 아님)는 요청 없이 "존재하지 않는 단지입니다", 404도 같은 빈 상태 + "홈으로". 상세 실패는 상단 배너와 다시 시도(상세만), 이력 실패는 이력 영역·가격 추이 카드의 오류와 다시 시도(이력만). 매매 이력은 탭과 무관하게 한 번 받아 매매 탭·가격 추이가 같이 쓰고, 전세·월세는 처음 열 때 한 번씩 받는다(지성 결정 8, TanStack Query 없음). 진행 중 이력 요청은 탭을 바꿔도 취소하지 않는다 — 취소한 요청의 뒤처리가 새 요청 상태를 덮는 경합을 피한다. 단지 id가 바뀌면 `key`로 페이지를 새로 마운트한다 | 스펙 6.1 | — |
| `?deal=` | 탭은 `?deal=JEONSE`·`?deal=WOLSE`로 replace 동기화하고 매매는 생략한다. 없거나 모르는 값은 매매로 보고 URL에서 지운다 | 스펙 6.3 | — |
| 배치 | 트리 하나에 grid-template-areas만 바꾼다(반응형 렌더 규칙). 1280px 이상: 왼쪽 본문 + 300px 사이드바(가격 추이·위치, 안쪽 `sticky top-6`), 그 아래: 경로 → 헤더 → 요약 → 토글 → 가격 추이 → 위치 → 탭·이력. sticky 오프셋이 고정값인 이유: GNB가 sticky가 아니다. 이력 표는 자기 영역 안에서만 가로 스크롤한다. GNB "지역·단지 검색"과 모바일 탭 "검색"을 `/complexes/*`에서 활성으로 둔다 | 스펙 6.2 | GNB를 sticky로 바꾸면 사이드바 오프셋을 그 높이만큼 늘린다 |
| 데이터 출처 두 줄 | 본문에 다시 넣지 않는다 — 공용 `Footer`가 같은 두 문장을 모든 화면에 이미 보여 줘, 넣으면 연달아 두 번 보였다 | 스펙 구성요소 9는 푸터로 충족 | Footer 문구가 바뀌면 이 화면에 출처를 따로 둔다 |
| 뒤로가기·공유·제목 | 앱 안에서 들어왔으면 `navigate(-1)`, 직접 진입(react-router 첫 위치 key `default`)이면 `/`. 공유는 `navigator.clipboard.writeText(location.href)` → "링크를 복사했습니다", 실패 시 "링크를 복사하지 못했습니다". `document.title = "{단지명} \| HomeSense"`(언마운트 시 되돌림) | 스펙 6.2 | — |
| 거래상세 모달 | 공용 `components/ui/Modal`(dialog·aria-modal·aria-labelledby, 포커스 트랩·Esc·배경 클릭·행으로 포커스 복귀·스크롤 잠금·85vh)을 새로 만들고, `BottomSheet`의 같은 동작을 `useDialogBehavior`로 뽑아 둘이 공유한다(지성 결정 9). 열 때 `GET /api/trades/{id}`, 자체 스켈레톤·오류·다시 시도. 동 없음 → "등기 완료 후 제공", 등기일자 없음 → "등기 전", 해제면 금액 취소선+해제일, 토지임대부 배지, 중개사 소재지 행 없음(결정 10), 전월세는 값 있는 항목만. **모양(2026-09-30 — [2026-10-03] 이 모달은 공용 Modal의 내용형이라 아래 값을 그대로 쓴다. MY-02 프레임 값은 확인형에만 적용한다, SCR-MY-02 절 "공용 Modal 확인형/내용형" 행. 이 값에는 Figma 근거가 없다 — Figma의 다이얼로그는 MY-02 삭제 다이얼로그뿐이다):** 오버레이 검정 40%(768px 미만 45%), 폭 400px(768px 미만 320px), 안쪽 32px(24px), radius 16, 그림자 0 16px 24px 20%(0 20px 30px 25%), 제목 18px 가장 굵게(모바일 16px 굵게), 닫기는 X 아이콘 대신 아래쪽 "닫기" 버튼(테두리 #e5e7eb·radius 14·14px 반굵게, Figma "취소" 버튼과 같은 모양). 제목 아래 구분선은 없앴다 | 스펙 6.4. 항목 구성은 UI정의서 5.3 모달 표 그대로 | 공용 `Modal`이라 MY-02 삭제 다이얼로그 등 이후 모달도 이 모양을 쓴다 — 다른 모양이 필요하면 prop으로 나눈다 |
| 7. 완결 필요 | (1) MY-02가 생기면 관심 등록 성공 토스트에 "목록 보기" 링크. (2) STAT-01이 생기면 가격 추이 카드에서 통계 화면 링크. (3) 기존 가격 포맷(`formatKoreanPrice`)은 "원"까지 붙인다("4억 9,800만원") — 스펙 예시와 다를 수 있으나 공용 포맷을 그대로 썼다 | — | — |
| 이력 범례 문구(2026-09-30 지성 정정) | 배지는 "등기 전"·"해제" 그대로 두고, 범례 설명은 "소유권 이전등기가 아직 확인되지 않은 거래"와 "계약 해제"로 쓴다 | 처음 쓴 "등기 신고 전"은 틀린 용어였다 — 부동산 거래신고(실거래 신고)와 소유권이전등기는 별개 절차이고, `isRegistered`는 원천 `rgstDate`(등기일자) 유무만 뜻한다. "확인되지 않은"으로 쓴 이유: 등기일자가 비어 있는 것은 아직 등기 전일 수도, 수집 시점 이후에 등기돼 반영되지 않았을 수도 있다(아래 완결 필요 (b)) | 원천이 등기 여부를 다른 필드로 주게 되면 다시 본다 |
| 비로그인 조회 이력 헤더 | 상세 요청(`GET /api/complexes/{id}`)은 로그인 여부와 무관하게 `X-Session-Id`를 싣는다(`features/complex/api.ts`, 최근 조회 조회와 같은 규칙) | e2e로 요청 헤더가 저장된 세션 ID(UUID)와 같고, 비로그인이라 `Authorization`이 없으며, 그 세션 ID로 `GET /api/recent-views`를 부르면 이 단지가 기록돼 있음을 실 백엔드로 확인했다. 헤더를 빼면 이 검사가 실패한다(변형 검증) | — |
| 모바일 푸터 | 360px에서 맨 아래까지 내렸을 때 공용 푸터의 출처 문구 하단(609px)이 고정 하단 탭 상단(723px)보다 위다 — 가려지지 않는다(`MainLayout`의 `pb-16`). e2e로 검사한다 | 출처 두 줄을 본문 대신 푸터에 맡겼으므로 모바일에서 실제로 읽을 수 있어야 한다 | 하단 탭 높이나 `MainLayout` 하단 여백을 바꾸면 다시 잰다 |
| 번들 크기 경고 | 코드 변경 없음. develop(`e6206fb`)에서도 이미 청크 경고가 난다(557.31kB) — 이 화면으로 약 39kB 늘어 596kB | 2026-09-30 임시 worktree에서 develop을 빌드해 확인 | 코드 분할은 별도 작업으로 판단한다 |

**완결 필요(DTL-01에서 등록)**
- **(a) 가입 후 원래 화면으로 복귀 — 별도 브랜치 `fix/frontend/signup-return-path`.** 지금 AUTH-01(로그인)은 `location.state.from`으로 돌아가지만, 로그인 화면에서 AUTH-02(회원가입)로 넘어갈 때 `from`을 넘기지 않아 가입 후에는 항상 `/`로 간다. 단지 상세에서 비로그인으로 관심 등록 → 로그인 화면 → 회원가입을 거치면 상세로 돌아오지 못하고, 관심 등록 재생도 홈에서 일어난다. AUTH-01 → AUTH-02 링크에 `state.from`을 이어 주고 가입 성공 시 `getRedirectPath()`로 복귀하게 한다.
- **(b) 배포 전 확인 — 계약 후 2개월이 지난 거래의 해제·등기 갱신(수집 범위 재설계).** **[2026-10-06 범위 축소]** 정규 배치가 전월+당월을 수집하므로 "월말 계약분 누락"은 생기지 않는다(그 전제는 틀렸다). 남는 문제는 2개월이 지난 계약의 해제·등기 정보가 더는 갱신되지 않고 굳는 것뿐이다. 03:00 스케줄은 이미 기본 활성이라 "활성화 전"이 아니라 배포 전에 확인한다. 이하 원문: 이력 표의 "등기 전"·"해제"는 `trade.registration_date`·`cancel_yn`·`cancel_date`에 달려 있다. 등기·해제는 계약보다 몇 주~몇 달 늦게 원천에 반영된다. **2026-09-30 코드로 확인한 사실:** (1) upsert SQL(`TradeRepository.upsert()`의 `ON DUPLICATE KEY UPDATE`)은 `cancel_yn`·`cancel_date`·`registration_date`·`apt_dong`을 갱신한다 — 다만 실 배치로 값이 실제로 바뀌는지는 검증하지 않았다. (2) **[2026-10-06 정정] 정규 배치는 전월+당월 두 달을 수집한다**(`orchestrate(targetMonth)`가 `targetMonth.minusMonths(1)`과 `targetMonth`를 순회 — 처음 "이번 달만"이라고 쓴 것은 `TradeCollectionScheduler`만 보고 쓴 오류였다). 그래도 계약 다음 달이 지나 원천에 반영되는 등기·해제는 다시 받아오지 않는다 — 계약 후 두 달 안에 등기되지 않은 거래는 영원히 "등기 전", 그 뒤 해제된 거래는 영원히 "해제" 없이 남는다(Phase 0 실측: 매매 등기일자 NULL 비율이 2월 6% → 9월 94%, 최근 달일수록 높다). 활성화 전에 재수집 범위(예: 최근 N개월 재조회)를 정하고, 그 범위에서 upsert가 세 필드를 실제로 바꾸는지 실 배치로 확인한다.


검증: vitest 64건(추가: `priceTrend.test.ts` 10건, `useFavoriteToggle.test.tsx` 실패 시 서버 문구·재동기화 2건과 진행 중 중복 클릭 1건 — 재동기화 호출을 빼면 2건 실패). e2e `dtl01-detail-check` 101/101(모킹 + 실 백엔드 360/768/1280, 세션 헤더·최근 조회 기록·360px 푸터 검사 포함). 거래상세 모달은 360×640에서 항목이 가장 많은 매매 거래(동·등기일자·해제·토지임대부)로 열어 본문이 넘치는 상태에서 "닫기" 버튼이 스크롤 영역 밖에 있고 연 직후 뷰포트 안인지 검사한다 — 버튼을 스크롤 영역 안으로 옮기면 2건 실패한다(변형 검증, 2026-10-01). `tsc -b`, `eslint`(기존 경고 3건), `npm run build` 통과(번들 596kB로 청크 크기 경고 — develop부터 있던 경고, 위 행).

### 배포(Vercel) — SPA 클라이언트 라우팅 rewrite

**증상(2026-09-16 세션)**: 실 배포(hmss.site)에서 `/privacy`를 새 탭으로 열거나 새로고침하면 404,
반면 앱 내부 `<Link>` 클릭으로 이동하면 정상 렌더링됐다. 원인은 `AppRouter.tsx`가 `BrowserRouter`를
쓴다는 사실 자체 — `/`를 제외한 모든 라우트(`/login`/`/signup`/`/privacy`/`/search`/`/complexes/:id`/
`/map`/`/my`/`/favorites`/`/notifications`)가 클라이언트 사이드 히스토리 API에 의존하는데, Vercel의
정적 파일 서버는 그 경로들에 대응하는 실제 파일이 없어 직접 요청·새로고침 시 404를 낸다 — `/privacy`
전용 버그가 아니라 `/` 이외의 모든 라우트에 동일하게 적용되는 배포 설정 문제였다.

**수정**: `frontend/homesense/vercel.json`(Vite 프로젝트 실제 루트, Vercel Root Directory가 이 폴더를
가리키고 있다는 전제하에 이 경로에 둠 — 기존 zero-config 빌드가 이미 성공하고 있었다는 사실 자체가
그 전제의 근거)에 Vercel 표준 SPA rewrite를 추가했다:
```json
{ "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }] }
```
Vercel은 rewrite보다 실제 정적 파일 매치를 항상 우선하므로 `/assets/*.js|css|woff2` 등 빌드 산출물은
영향받지 않는다.

**로컬에서는 이 종류의 버그를 재현할 수 없다 — 다음에 "로컬은 되는데 배포에서만 깨진다"는 라우팅
이슈가 생기면 먼저 이 항목부터 의심하라.** `vite preview`는 자체적으로 SPA fallback을 내장하고 있어
`vercel.json` 유무와 무관하게 항상 200을 반환한다(직접 확인: `/privacy`에 curl, 200) — 즉 이 클래스의
버그는 로컬 개발/프리뷰 서버로는 원천적으로 검증 불가능하고, 실제 Vercel 배포 후에만 확인할 수 있다.

**후속 버그(2026-09-21) — 위 rewrite의 `/(.*)`가 `/api/*`까지 그대로 삼켜 배포된 홈 화면이 완전히
흰 화면으로 죽었다.** 실 배포(https://home-sense-six.vercel.app/)를 열면 `#root`가 비어 있었고,
Playwright로 잡은 `pageerror`는 `TypeError: Cannot read properties of undefined (reading 'length')`
였다 — `RecommendedComplexes.tsx`의 `complexes.length === 0` 줄에서 터졌다. **원인 체인:** (1)
`VITE_API_BASE_URL`이 빌드에 주입된 적이 없어(Vercel 프로젝트 환경변수 미설정, `httpClient.ts`의
`?? ''` 폴백이 그대로 적용됨) 모든 API 호출이 프런트와 같은 오리진(`home-sense-six.vercel.app`)으로
나간다 — 이 배포엔 백엔드(Spring Boot)가 함께 올라가 있지 않아 `/api/*`에 대응하는 서버리스
함수나 정적 파일이 애초에 없다. (2) 그런데 위 rewrite(`/(.*)`  → `/index.html`)가 경로 패턴을 전혀
가리지 않아 `/api/complexes/popular` 같은 요청도 이 규칙에 걸려 index.html을 `200 text/html`로
돌려준다 — 진짜 404 대신 "가짜 성공" 응답이 나가는 것. (3) `features/*/api.ts`의 모든 함수는
`(data as Extract<ApiResponse<T>, { success: true }>).data`로 무조건 캐스팅한다(주석: "성공 응답은
HTTP 2xx로만 오므로 이 시점의 data는 항상 success:true다") — 이 전제가 실제 백엔드에 대해서는
참이지만, (2) 때문에 index.html 문자열이 `data`로 들어와 `.data`가 `undefined`가 된다. axios는
2xx라 reject하지 않으므로 `.then()` 성공 분기가 그대로 실행되고, `RecommendedComplexes`가
`setComplexes(undefined)`를 호출한 뒤 `complexes.length`를 가드 없이 읽어 크래시한다 — 에러
바운더리가 없어 React 트리 전체가 사라지고 흰 화면만 남는다.

**수정**: `vercel.json`의 rewrite 패턴에 음의 전방탐색을 추가해 `/api/`로 시작하는 경로를 제외했다.
```json
{ "rewrites": [{ "source": "/((?!api/).*)", "destination": "/index.html" }] }
```
이제 `/api/*`는 이 rewrite에 걸리지 않아(대응하는 정적 파일도 서버리스 함수도 없으므로) Vercel이
진짜 404를 반환한다 — axios가 정상적으로 reject하고, 각 `.then()/.catch()`가 이미 그렇게 설계된
대로(CLAUDE.md SCR-HOME-01 절 "관심 매물 POST 실패 시 에러 노출" 등) 빈 배열/숨김 상태로 우아하게
폴백한다.

**검증(로컬, 위 문단의 "vite preview로 재현 불가" 원칙을 우회한 방법)**: `vite preview`가
`vercel.json`을 전혀 해석하지 않는다는 사실 자체는 그대로다 — 대신 Playwright의 `page.route()`로
`/api/**` 응답을 직접 조작해 두 시나리오를 분리 재현했다: (A) 지금의 버그 그대로 `200
text/html`(index.html 본문)을 돌려주면 `pageerror`가 정확히 재현된다, (B) 수정 후 실제로 나올
`404`를 돌려주면 `pageerror` 0건에 `#root`가 정상적으로 채워진다(20,733자, 실제 백엔드가 500을
반환했을 때와 동일한 길이 — 즉 실패 경로가 정확히 같은 정상 렌더링으로 수렴함을 확인). 이 방식은
"Vercel의 rewrite 엔진 자체"는 검증하지 못하지만(그건 여전히 실 배포 후에만 확인 가능), "그 rewrite가
만들어내는 정확한 응답 모양을 로컬에서 흉내 내 컴포넌트가 실제로 크래시/복구하는지"는 배포 없이도
검증할 수 있다는 걸 보여준다 — 위 "로컬에서는 재현할 수 없다"는 rewrite 엔진 자체에 대해서만 여전히
맞고, 그 rewrite가 유발하는 하류 버그는 응답을 흉내 내면 로컬에서도 검증 가능하다는 refinement로
남긴다.

**완결 필요 — `VITE_API_BASE_URL`이 여전히 설정되지 않았다(확인됨: 백엔드는 아직 어디에도 배포된 적
없고 지성 로컬에서만 실행 중, 2026-09-21 확인).** 이번 수정은 "흰 화면"을 "정상적으로 렌더링되지만
데이터가 비어 있는 홈 화면"으로 바꿀 뿐이다 — 이건 지금 시점의 최선이자 정확한 최종 상태다: 배포된
백엔드 오리진 자체가 존재하지 않으므로 `VITE_API_BASE_URL`에 넣을 값이 없다(로컬 `localhost:8080`을
넣는 건 사용자 브라우저에서 접근 불가능해 의미가 없고, 잘못된 URL을 넣으면 CORS 에러 등 다른 실패
모드로 바뀔 뿐이라 하지 않았다). **무효화 조건:** 백엔드가 실제로 배포되면(AWS 등 — 개인정보처리방침
8번 항목이 이미 ap-northeast-2 리전 호스팅을 언급하고 있다) 그 오리진을 `VITE_API_BASE_URL`로
설정하는 작업이 남는다 — 이 시점까지는 프로덕션 홈 화면이 항상 빈 상태로 보이는 것이 정상이다.

**완결 필요(우선순위 낮음, 이번엔 손대지 않음) — 앱 최상위에 React 에러 바운더리가 없다.** 이번
크래시가 흰 화면으로 번진 것도, `features/*/api.ts` 전체가 "2xx면 무조건 success:true"를 캐스팅으로만
강제하는 것도(런타임 검증 없음) 구조적으로 같은 계열의 취약점이다 — 라우팅이 다시 한 번 무언가를
잘못 삼키거나 백엔드가 예상과 다른 200을 내려주면 똑같이 흰 화면으로 죽는다. `main.tsx`/`App.tsx`
최상위에 에러 바운더리를 두면 이런 클래스의 실패가 "흰 화면"이 아니라 최소한의 에러 UI로 완충된다 —
다만 이번 수정의 범위(rewrite 설정 하나)를 넘어서는 아키텍처 추가라 임의로 만들지 않았다.

## 명령어

```bash
# 백엔드 (Spring Boot, Gradle) — backend/
./gradlew bootRun          # 로컬 실행 (8080)
./gradlew test             # 단위 테스트
./gradlew integrationTest  # Testcontainers 기반 IT (@Tag("integration"), Docker 필요)
./gradlew build            # 빌드

# 프론트엔드 (Vite + React + TS) — frontend/homesense/ (frontend/ 바로 아래가 아니라 한 단계
# 더 들어간 위치다 — 최초 스캐폴딩 시점에 이렇게 생성됐고 지금까지 유지하고 있다)
npm install
npm run dev                # 개발 서버 (5173, /api는 vite.config.ts 프록시로 8080에 전달)
npm run build              # 프로덕션 빌드 (tsc -b && vite build)
npm run lint
# e2e(frontend/e2e): 모든 스크립트가 base.mjs의 BASE(기본 http://localhost:5173)를 쓴다.
#   다른 포트면 BASE=http://localhost:5183 node run-all.mjs 처럼 덮는다.
npm test                   # 단위 테스트(vitest, jsdom). Windows에서 작업 디렉터리의 드라이브 문자가 대문자(C:)면
                           # "Vitest failed to find the runner"로 실패할 수 있다 — 소문자 c:\ 경로에서 실행한다(2026-09-29 확인)

# DB — 테이블 정의서 8장 DDL 원문을 그대로 실행(성능 인덱스 13건 포함)
mysql -u root homesense < schema_all.sql
# 이미 테이블이 있는 DB에 인덱스 13건만 추가(재실행 안전)
mysql -u root homesense < backend/src/main/resources/schema/indexes_v2_1.sql
# 기존 DB 마이그레이션(재실행 안전) — 자동 마이그레이션 도구가 없으므로, 해당 커밋이 들어간 백엔드를 기동하기 전에 DB마다 실행한다
#   BAT-NTF-01(2026-10-06): notification.sent_at NULL 허용. 빠지면 알림 INSERT가 전부 거부된다(평가기가 확인 후 건너뜀)
mysql -u root homesense < backend/src/main/resources/schema/notification_sent_at_nullable.sql

# complex.legal_dong_cd 백필 — 단지를 (재)적재한 뒤마다 실행. 기본 dry-run이고 --apply를 붙여야 쓴다.
# 웹 서버·스케줄러 없이 돌고 종료 코드를 반환한다. 운영 절차: docs/runbook-complex-legal-dong-backfill.md
./gradlew bootRun --args='--spring.profiles.active=local,backfill-complex-legal-dong'          # dry-run
./gradlew bootRun --args='--spring.profiles.active=local,backfill-complex-legal-dong --apply'  # 적용
```

## UNIQUE 제약 동시성 회귀 테스트 원칙

새 엔티티를 저장할 때 "먼저 존재 여부를 조회하고(exists/find) 없으면 저장한다"는 패턴은 전부 TOCTOU race를 안고 있습니다 — 조회 이후 저장 이전에 다른 요청이 같은 값으로 먼저 커밋하면 UNIQUE 제약이 대신 막아주고, 그 예외를 올바른 도메인 예외로 번역해야 합니다(예: SVC-AUTH-01 `AuthService.signup()`의 `DataIntegrityViolationException` → `DuplicateEmailException`, BAT-LOD-01 `TradeChunkLoader.upsertOne()`의 같은 예외 → `dedup_hash` 재시도).

- **이 번역 로직 자체(catch 블록이 올바른 예외를 던지는지)는 Mockito로 `save()`가 예외를 던지도록 목킹해 빠르게 검증해도 됩니다** — `AuthServiceTest`의 `signup_동시_가입_경쟁으로_UNIQUE_제약이_깨지면_DuplicateEmailException으로_변환한다`처럼.
- **하지만 "그 예외가 실제로 `save()` 호출 시점에 곧바로 터지는가"는 Mock으로 증명할 수 없고, `GenerationType.IDENTITY`에 의존합니다.** `SEQUENCE`/`AUTO`처럼 Hibernate가 INSERT를 flush(커밋) 시점까지 미룰 수 있는 전략이었다면, 같은 UNIQUE 위반이 메서드가 이미 정상 반환된 뒤 바깥쪽 `@Transactional`의 커밋 시점에 터져 그 안의 `try/catch`를 완전히 비껴가고 그대로 500으로 샙니다.
- **이 전제를 검증하는 테스트는 반드시 실제 서비스가 쓰는 것과 동일한 `@Transactional` 경계 안에서, Testcontainers 실제 DB로 검증해야 합니다** — repository 메서드를 트랜잭션 바깥에서 단독 호출하면 Spring Data JPA가 그 호출 하나만을 위한 짧은 자체 트랜잭션을 열고 반환 전에 커밋까지 마치므로, 실제로는 flush가 지연되는 전략이었어도 "save() 호출 시점에 곧바로 예외가 난 것처럼" 보이는 거짓 양성이 생깁니다. `AuthServiceMariaDbIT`가 `TradeChunkLoaderMariaDbIT`와 같은 방식(두 스레드 + `CountDownLatch`)으로 실제 `AuthService.signup()` 호출 경로를 그대로 태워 이 전제까지 함께 검증합니다.
- Docker가 필요해 `./gradlew test`가 아니라 `./gradlew integrationTest`로만 실행됩니다(`@Tag("integration")`).

**`REQUIRES_NEW` 격리 INSERT 게이트웨이 패턴 — `TradeInsertGateway`(BAT-LOD-01)/`NotificationSettingInsertGateway`(SVC-NTF-01) 둘 다 결국 원자적 upsert로 대체되며 삭제됐다(역사적 기록).** 두 클래스는 같은 구조였다: 바깥 트랜잭션이 커넥션을 쥔 채 `@Transactional(REQUIRES_NEW)`로 INSERT 하나만 격리해 두 번째 커넥션을 추가로 잡고, INSERT가 UNIQUE 위반으로 실패하면 같은 청크/요청 트랜잭션 안에서 재조회 후 UPDATE로 재시도했다. 이 패턴에는 **서로 독립적인 두 종류의 위험**이 있었는데, 이 프로젝트가 그 둘을 서로 다른 시점에 따로 발견했다는 점이 교훈이다 — **커넥션 풀 고갈 위험을 분석해 "안전하다"는 결론을 내렸다고 해서, 스냅샷 가시성 위험까지 안전하다는 뜻은 아니다.**

1. **커넥션 풀 고갈 위험(NTF에서 지적, LOD는 안전하다고 결론 냈었음).** Codex 코드리뷰가 NTF 쪽에서 지적한 위험은 "동시 호출 수가 커넥션 풀 크기(`application-prod.properties`의 `spring.datasource.hikari.maximum-pool-size=20`)에 근접하면, 모든 커넥션이 바깥 트랜잭션에 묶인 채 REQUIRES_NEW가 요청할 여분의 커넥션이 없어 타임아웃으로 줄줄이 실패한다"는 것이었다 — HTTP 요청마다 독립적으로 바깥 트랜잭션이 열리는 SVC-NTF-01 경로에서만 성립한다. `TradeChunkLoader`(BAT-LOD-01)는 배치 전체가 항상 단일 스레드로 실행되므로(`BatchExecutionOrchestrator.orchestrate()`가 `ExecutorService`/`@Async`/`parallelStream` 없이 단일 스레드 for문으로 조합을 순회) 이 위험이 없다고 정확히 결론 냈었다 — 동시에 열리는 커넥션이 "청크 트랜잭션 1개 + REQUIRES_NEW 1개" = 최대 2개뿐이라 풀 크기에 전혀 위협이 되지 않았다.
2. **스냅샷 가시성 위험(LOD에서 실제로 터짐, 실 배치 재실행으로 발견) — 커넥션 풀 분석과는 완전히 별개의 문제였다.** `TradeChunkLoader`가 단일 스레드라 "동시 경쟁"은 없었지만, data.go.kr 페이지네이션이 응답 페이지 사이에 같은 거래를 중복으로 돌려주면(당월 데이터가 계속 갱신되는 도중 페이지를 나눠 조회하는 경우 등) **같은 청크 트랜잭션 안에서 같은 dedup_hash가 두 번 등장**한다 — 이것만으로도 MariaDB REPEATABLE READ 스냅샷 문제가 재현된다(진짜 동시 실행이 전혀 필요 없다: 첫 항목의 REQUIRES_NEW INSERT가 커밋된 뒤, 같은 청크 트랜잭션의 두 번째 항목이 재조회해도 그 트랜잭션이 첫 항목 처리 이전에 이미 확립한 스냅샷에 묶여 방금 자신이 커밋시킨 그 행조차 보지 못한다). 실제 로컬 배치 재실행(2026-09-15, 버그 1~3 수정 검증을 위해 돌린 배치)에서 **처리 대상의 2.2%(150,485건 중 3,352건)가 이 경로로 조용히 유실**됨을 실측 확인했다 — 이번 3개 버그와 무관한 별도 결함으로, 사용자 요청에 따라 같은 세션에서 함께 수정했다(아래 "BAT-LOD-01 버그 수정" 절 참고).

앞으로 이 패턴(바깥 트랜잭션 보유 + REQUIRES_NEW 격리 INSERT + 재조회 재시도)을 다시 보게 되면, 커넥션 풀 고갈 위험만 확인하고 안전하다고 결론 내지 말 것 — **재조회가 그 트랜잭션의 스냅샷에 묶여 있는지도 반드시 함께 확인하라.** 이 프로젝트는 이제 이 패턴 자체를 쓰지 않는다(NTF/LOD 둘 다 원자적 `INSERT ... ON DUPLICATE KEY UPDATE`로 대체) — 새로 upsert가 필요해지면 재조회·REQUIRES_NEW 격리 방식이 아니라 처음부터 원자적 upsert를 택하라.

### BAT-LOD-01 버그 수정 (2026-09-15) — TradeChunkLoader dedup_hash 재시도가 REPEATABLE READ 스냅샷에 묶여 2.2% 유실

위 버그 1~3(BAT-MAT-02/BAT-PRS-01) 수정을 실 배치로 검증하는 과정에서 발견한, 이번 3개 버그와는 무관한 별도 결함이다(사용자 요청으로 같은 세션에서 함께 수정). **원인·수정 근거는 바로 위 "`REQUIRES_NEW` 격리 INSERT 게이트웨이 패턴" 절의 2번 항목과 `TradeRepository#upsert` javadoc에 상세히 남겼다** — 요약만 여기 적는다.

- **원인**: `TradeChunkLoader.upsertOne()`이 "조회 → 없으면 INSERT(REQUIRES_NEW로 격리) → UNIQUE 위반 시 재조회 후 UPDATE 재시도" 패턴이었는데, 같은 청크 트랜잭션 안에서 같은 dedup_hash가 두 번 등장하면(data.go.kr 페이지네이션 중복 응답 등) 재조회가 REPEATABLE READ 스냅샷에 묶여 방금 커밋된 행을 보지 못해 재시도까지 실패, 해당 건이 스킵됐다.
- **수정**: `TradeRepository.upsert()`(네이티브 `INSERT ... ON DUPLICATE KEY UPDATE`) 신설 — SVC-NTF-01의 `NotificationSettingRepository#upsert`와 완전히 같은 패턴. `TradeChunkLoader`에서 `ComplexRepository`/`LegalDistrictCodeRepository`/`TradeInsertGateway` 의존성이 전부 제거됐다(JPA 엔티티 참조 없이 원시 컬럼 값만 네이티브 SQL에 바인딩하므로 더 이상 필요 없음) — `TradeInsertGateway.java` 삭제.
- **영향 범위(실측)**: 처리 대상의 2.2%(150,485건 중 3,352건)가 유실되고 있었다 — 전부 이 경로였다(같은 배치 실행에서 발생한 에러 3,352건 전수가 `TradeChunkLoader`발 에러, 다른 원인(파싱·매핑 오류 등)은 0건).
- **검증**: `TradeChunkLoaderTest`(Mockito, upsert 반환값 1/2에 따른 inserted/updated 집계, 예외 시 스킵) 재작성. `TradeChunkLoaderMariaDbIT`(Testcontainers)도 원자적 upsert에 맞춰 단순화했다 — 예전처럼 `CountDownLatch`로 커밋 순서를 인위적으로 강제할 필요가 없어져, 두 스레드가 순서 강제 없이 동시에 같은 dedup_hash를 놓고 경쟁해도 정확히 한 행만 남는지만 확인한다(SVC-NTF-01의 `NotificationServiceMariaDbIT`가 같은 이유로 단순화된 것과 동일한 결). **이번 세션은 Docker가 실제로 가동 중이라 `./gradlew integrationTest`로 직접 실행해 통과를 확인했다** — 이 저장소의 다른 여러 MariaDB IT와 달리 "작성만 하고 Docker 부재로 미실행"이 아니라 실제로 그린을 확인한 드문 사례다.

### BAT-LOD-01 후속 버그 수정 (2026-09-16, PR 리뷰 지적) — 원자적 upsert가 옛 게이트웨이의 "부수적" 격리 효과까지 함께 없앴다

바로 위 절이 삭제한 `TradeInsertGateway`(REQUIRES_NEW로 INSERT만 격리하던 옛 게이트웨이)는 UNIQUE
경쟁 문제를 해결하려 만든 것이었지만, **그 과정에서 "청크 안 한 건의 DB 제약 위반이 나머지 499건까지
막지 못하게 하는" 효과도 부수적으로 제공하고 있었다** — 원자적 upsert로 교체하며 이 부수 효과가
있었다는 사실 자체를 놓쳤다. `TradeChunkLoader.loadChunk()`는 여전히 최대 500건을 한
`@Transactional` 안에서 처리하며 건마다 `try/catch`로 개별 실패를 스킵하는데, 오버사이즈 문자열
(VARCHAR 초과)·UNSIGNED 음수·잘못된 FK 같은 **진짜 제약 위반**(UNIQUE 경쟁이 아닌)이 한 건이라도
나면 JPA 스펙상 그 예외가 트랜잭션을 rollback-only로 표시하고, catch가 그 건만 스킵한 것처럼 보여도
`loadChunk()` 커밋 시점에 `UnexpectedRollbackException`이 터져 청크 전체(최대 500건)가 롤백된다 — 이
프로젝트가 이미 SVC-NTF-01 `NotificationSettingRepository` 1차 구현과 옛 `TradeInsertGateway` 자체의
존재 이유로 두 번 겪은 패턴이 세 번째로 재발한 것이다.

**수정**: `TradeUpsertGateway`(신규, `batch.loader`) — `TradeRepository#upsert` 호출 단 하나만
`@Transactional(propagation = REQUIRES_NEW)`로 감싸 청크 트랜잭션과 분리한다. 옛 게이트웨이와 달리
격리 대상이 원자적 SQL 문장 하나뿐이라 INSERT 실패 후 재조회·UPDATE 재시도 로직 자체가 필요 없고,
그래서 그 로직이 겪었던 REPEATABLE READ 스냅샷 문제(재조회가 격리된 트랜잭션의 커밋을 못 보는 문제)도
재현되지 않는다 — 재조회를 아예 하지 않기 때문이다. `TradeChunkLoader`는 `TradeRepository` 대신 이
게이트웨이에 의존한다. **커넥션 풀 고갈 위험도 스냅샷 문제와 별개로 확인했다** — "스냅샷 가시성이
안전하다"와 "커넥션 풀 관점이 안전하다"는 서로 다른 질문이라(SVC-NTF-01의 반대 사례: 그쪽은 풀은
검토했지만 스냅샷을 놓쳤다) 하나를 확인했다고 다른 하나도 넘겨짚으면 안 된다. `loadChunk()`가 청크
(최대 500건)를 단일 for문으로 순차 처리하므로 한 청크 안에 제약 위반 건이 여럿 있어도 동시에 열리는
REQUIRES_NEW 커넥션은 항상 최대 1개, 여기에 BAT-SCH-01의 조합 순회 자체도 단일 스레드라 배치
파이프라인 전체로도 동시 커넥션은 "청크 트랜잭션 1개 + REQUIRES_NEW 1개" = 최대 2개뿐이다(위
"REQUIRES_NEW 격리 INSERT 게이트웨이 패턴" 절의 1번 항목과 같은 논거). **무효화 조건**: 이 안전성은
"배치가 항상 단일 스레드로 순차 실행된다"는 전제에만 기댄다 — 청크 처리나 조합 순회를 병렬화하게
되면 동시 REQUIRES_NEW 커넥션 수가 스레드 수만큼 늘어나므로 그 시점에 커넥션 풀 크기 대비 이 분석을
재검증해야 한다.

**검증(회귀 재현 포함)**: `TradeChunkLoaderTest`(Mockito)는 `TradeRepository` 대신
`TradeUpsertGateway`를 목킹하도록 갱신. `TradeChunkLoaderMariaDbIT`에 새 테스트를 추가해
`building_name`을 VARCHAR(100) 초과로 만든 건과 정상 건을 한 청크에 같이 넣고, 정상 건은 커밋되고
위반 건만 스킵되는지 실 DB로 확인했다 — **이 수정을 일부러 잠깐 되돌려(REQUIRES_NEW 제거) 같은
테스트를 돌려 본 결과 정확히 `UnexpectedRollbackException`이 재현됐다**(회귀 테스트가 실제로 이 버그를
잡는다는 것을 확인한 뒤 원상복구). `./gradlew integrationTest`로 10개 MariaDB IT 클래스(35 테스트)
전부 그린 확인.

## `@Modifying` 벌크 쿼리 — flushAutomatically/clearAutomatically 원칙

`@Modifying` 벌크 UPDATE/DELETE는 영속성 컨텍스트의 더티 체킹을 거치지 않고 DB에 직접 SQL을 날린다. 같은 트랜잭션 안에서 그 직전에 **다른 엔티티**(벌크 쿼리의 대상 테이블과 다른 테이블)를 도메인 메서드로 수정해 뒀다면, 그 변경은 아직 flush되지 않은 상태로 남아있을 수 있다 — Hibernate의 자동 flush는 쿼리가 실제로 참조하는 "query space"(테이블)만 보고 판단하므로, FK 컬럼을 통해 간접적으로만 연관된 다른 테이블의 미반영 변경은 감지하지 못한다.

**`clearAutomatically = true`를 `flushAutomatically = true` 없이 단독으로 쓰면, 그 미반영 변경이 벌크 쿼리 직후 영속성 컨텍스트 clear로 통째로 버려진다** — SVC-USER-01 `RefreshTokenRepository.revokeAllByUserId()`가 정확히 이 함정이었다. `UserService.withdraw()`가 `user.withdraw()`(status=WITHDRAWN, 아직 미반영)를 호출한 직후 이 메서드를 불렀는데, 벌크 쿼리의 query space가 refresh_token뿐이라 User의 변경을 flush하지 못했고, `clearAutomatically`만 걸려 있어 그 변경이 그대로 유실됐다 — refresh_token은 정상적으로 폐기되는데 user.status는 ACTIVE로 남아 탈퇴한 사용자가 즉시 재로그인할 수 있는 심각한 결함이었다(코드리뷰에서 지적됨).

- **새 `@Modifying` 벌크 쿼리를 추가할 때, 그 직전에 다른 엔티티를 도메인 메서드로 수정하는 호출부가 하나라도 있다면 `flushAutomatically = true`를 함께 걸어라** — `clearAutomatically`만으로는 안전하지 않다. 두 플래그를 함께 쓰는 것이 Spring Data JPA의 권장 조합이다(flush로 먼저 반영시킨 뒤에야 clear가 안전해진다).
- **이 클래스의 버그는 Mockito 단위 테스트로는 절대 드러나지 않는다** — Repository를 목킹하면 실제 EntityManager/영속성 컨텍스트 자체가 없어 "메서드가 호출됐다"만 증명되고 "그 변경이 진짜 커밋됐다"는 증명되지 않는다. `UserServiceMariaDbIT`처럼 실제 MariaDB(Testcontainers) 위에서 서비스 메서드 호출이 끝난 뒤 **별도로 재조회**해 커밋된 DB 상태를 직접 확인해야 한다(같은 영속성 컨텍스트를 재사용하는 재조회는 캐시된 값을 그대로 돌려줘 착시를 일으킬 수 있으니, `@SpringBootTest`가 각 리포지토리 호출마다 별도 트랜잭션/영속성 컨텍스트를 여는 것에 의존한다).

## MariaDB IT 테스트에 `@Test` 메서드가 둘 이상이면 `@Transactional`을 클래스에 걸어라

`@SpringBootTest`는 클래스 안의 모든 테스트 메서드가 같은(캐시된) ApplicationContext — 즉 같은 Testcontainers 인스턴스 — 를 공유한다. `@BeforeEach`에서 `saveAndFlush()`로 고정된 값(예: `source_complex_cd`처럼 UNIQUE 제약이 걸린 컬럼)을 가진 fixture를 심는다면, 그 호출 자체가 자기완결 트랜잭션이라 즉시 커밋된다 — 클래스 레벨 정리 장치가 없으면 **두 번째 테스트 메서드부터 setUp()이 같은 값을 다시 삽입하려다 UNIQUE 위반으로 실패해, 그 클래스의 테스트가 사실상 처음 하나 말고는 전부 못 돈다**(`ComplexRepositoryMariaDbIT`가 정확히 이 함정이었다, 코드리뷰에서 지적됨).

- **`@Test` 메서드가 하나뿐인 IT 클래스**(`AuthServiceMariaDbIT`, `UserServiceMariaDbIT`, `TradeChunkLoaderMariaDbIT`)는 이 문제에서 애초에 자유롭다 — `@BeforeEach`로 매번 새로 심을 fixture가 여러 메서드에 걸쳐 충돌할 일이 없다. 새 IT 테스트를 이 패턴만 보고 베끼면 이 함정을 놓치기 쉽다.
- **`@Test` 메서드가 둘 이상이고 `@BeforeEach`에서 UNIQUE 제약이 걸린 고정값을 심는다면, 클래스에 `@Transactional`을 걸어라.** `@SpringBootTest`의 기본 리스너에 이미 `TransactionalTestExecutionListener`가 포함돼 있어 별도 설정 없이 각 테스트 메서드(그 안의 `@BeforeEach` 포함)를 트랜잭션으로 감싸고 끝나면 자동 롤백한다 — `saveAndFlush()`는 그 트랜잭션 안에서도 여전히 즉시 flush되어 같은 메서드의 뒤이은 조회에는 정상적으로 보이고, 테스트가 끝나면 커밋 없이 버려지므로 다음 메서드가 깨끗한 상태에서 시작한다. 테스트 안에서 `deleteAll()`을 직접 호출하는 수동 정리보다 이 방식을 우선하라 — FK 삭제 순서를 신경 쓸 필요가 없고, 나중에 테이블이 늘어나도 그대로 안전하다.
- **예외 — `ExecutorService`로 여러 스레드를 띄워 실제 커밋 가시성을 검증하는 동시성 IT 테스트에는 이 원칙을 기계적으로 적용하지 마라.** Spring의 테스트 트랜잭션은 테스트를 실행하는 메인 스레드에만 바인딩된다. `ExecutorService`로 띄운 워커 스레드는 별도 커넥션·별도 트랜잭션을 쓰므로, 클래스에 `@Transactional`을 걸었는데 fixture를 메인 스레드(`@BeforeEach`나 테스트 본문 앞부분)에서 준비하는 구조라면 그 fixture는 테스트가 끝날 때까지 커밋되지 않아 워커 스레드에서 안 보인다 — 검증하려던 동시성 시나리오 자체가 깨진다(코드리뷰에서 지적됨). `TradeChunkLoaderMariaDbIT`는 fixture 준비까지 전부 두 워커 스레드 안에서 하고 메인 스레드는 두 Future가 끝난 뒤 커밋된 결과만 재조회해 이 문제를 피해 간다 — 새 동시성 IT를 쓸 때도 이 구조(메인 스레드는 조율·최종 검증만, 실제 DB 작업은 워커 스레드 안에서)를 따르고, `@Test`가 하나뿐이라면(지금까지는 전부 그랬다) 애초에 클래스 레벨 `@Transactional`이 필요 없다는 점도 함께 확인하라.

## 트러블슈팅 노트 (Spring Boot 4.1.1)

이 프로젝트는 Spring Boot 4.1.1 / Spring Framework 7을 씁니다. 버전이 올라오며 테스트 슬라이스 관련 패키지·동작이 바뀐 부분이 있어, `@WebMvcTest` 등을 새로 작성할 때마다 재발하지 않도록 기록합니다(검증: COM-EXC-01 `GlobalExceptionHandlerTest` 작성 중 확인).

- **`@WebMvcTest`/`@AutoConfigureMockMvc` 패키지 이동**: `org.springframework.boot.test.autoconfigure.web.servlet`이 아니라 `org.springframework.boot.webmvc.test.autoconfigure`에서 import하세요. 구 패키지는 이 버전에 존재하지 않아 컴파일 에러가 납니다.
- **`JwtAuthenticationFilter`는 모든 `@WebMvcTest` 슬라이스에 항상 올라옵니다**: `Filter` 빈이라 `@WebMvcTest(controllers = ...)`로 특정 컨트롤러만 좁혀도 컴포넌트 스캔에 걸립니다. 생성자 의존성인 `JwtTokenProvider`가 슬라이스 컨텍스트에 없으면 `UnsatisfiedDependencyException`으로 컨텍스트 로딩 자체가 실패하므로, 컨트롤러 슬라이스 테스트에는 습관적으로 `@MockitoBean private JwtTokenProvider jwtTokenProvider;`를 추가하세요. `@AutoConfigureMockMvc(addFilters = false)`는 MockMvc 필터 체인 적용만 막을 뿐 빈 생성 자체는 막지 않습니다.
- **테스트 클래스 내부 nested static `@RestController`는 `@WebMvcTest(controllers = X.class)`만으로 빈 등록이 안 될 수 있습니다**: 컴포넌트 스캔에 잡히지 않는 현상이 재현됐습니다(원인 미상). `@Import(X.class)`로 명시적으로 등록하면 해결됩니다.
- **`@AuthenticationPrincipal` 컨트롤러를 `@AutoConfigureMockMvc(addFilters = false)`로 테스트할 때 `SecurityMockMvcRequestPostProcessors.authentication(...)`을 쓰면 안 됩니다**: 그 post-processor는 `SecurityContext`를 세션 속성(`SPRING_SECURITY_CONTEXT`)에만 저장하고, 그걸 실제로 `SecurityContextHolder`(스레드로컬)로 옮기는 건 `SecurityContextHolderFilter`/`SecurityContextPersistenceFilter`의 역할입니다 — `addFilters=false`면 그 필터가 안 돌아서 컨트롤러가 호출될 때 `SecurityContextHolder.getContext().getAuthentication()`이 여전히 비어 있고, `AuthenticationPrincipalArgumentResolver`가 `null`을 그대로 주입해 `@AuthenticationPrincipal` 파라미터를 곧장 역참조하는 코드가 `NullPointerException`으로 깨집니다(SVC-AUTH-01 `AuthController.logout()` 테스트 작성 중 재현). `JwtAuthenticationFilterTest`가 이미 하듯 `SecurityContextHolder.getContext().setAuthentication(...)`을 요청 전에 직접 채우고 `@AfterEach`에서 `SecurityContextHolder.clearContext()`로 정리하세요 — 필터 체인 실행 여부와 무관하게 같은 스레드에서 바로 반영됩니다(검증: `AuthControllerTest`).

## 향후 확장 시 (오피스텔·단독다가구)

확장 작업을 요청받으면 새로 설계하지 말고 아래 순서로 기존 문서의 "향후 확장" 절을 그대로 적용하세요. v1.0에서 이미 설계·검증됐던 내용입니다.

1. 테이블 정의서 10장 — `ALTER TABLE` 초안 원문 그대로 실행 (officetel_key, masked_address 컬럼 복원, CHECK 값 확장)
2. 엔티티 정의서 9장 — 복원되는 컬럼/관계 확인
3. 프로그램 설계서 8장 — `OfficetelController`/`OfficetelService`, `DetachedHouseController`/`DetachedHouseService`, `BAT-MAT-03`/`BAT-MAT-04`의 클래스명·메서드 시그니처·핵심 로직이 이미 정리되어 있습니다
4. UI 정의서 9장 — DTL-02/DTL-03 화면 사양
5. 프로그램 목록서 7장 — 재도입 대상 8개 프로그램과 함께 확장되는 기존 프로그램 목록(`API-TRD-01` 파라미터 복원 등)

데이터셋 ID도 이미 확인되어 있습니다: 오피스텔 매매 15126464, 오피스텔 전월세 15126475, 단독·다가구 매매 15126465, 단독·다가구 전월세 15126472.
