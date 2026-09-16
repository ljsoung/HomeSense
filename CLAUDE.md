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

**작업 유형별 우선 참조 문서:**
- API 엔드포인트/비즈니스 로직을 짤 때 → 프로그램 설계서 3~5장
- 배치 로직을 짤 때 → 프로그램 설계서 4장 + 6.3절(파이프라인 전체 흐름)
- 테이블/컬럼/제약을 확인할 때 → 테이블 정의서 (DDL 원문 8장)
- 화면 동작/API 응답 형태를 확인할 때 → UI 정의서 5장
- "이 기능이 왜 이렇게 설계됐는지" 궁금할 때 → 요구사항 정의서

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

**프로그램 설계서 여러 곳(CPX/TRD/NTF의 Controller 표 등)이 페이지네이션 응답 타입을 `PageResponse<T>`로 표기하지만, 이 클래스는 프로젝트 어디에도 존재하지 않고 앞으로도 도입하지 않습니다.** `ApiResponse.success(Page<T>)` 오버로드 하나로 이미 충분하기 때문입니다 — 새 페이지네이션 API를 짤 때 설계서의 `PageResponse<T>` 표기를 보고 그 이름의 DTO를 새로 만들지 마세요. 실제로 CPX(`ComplexController.search()`)와 TRD(`TradeController.search()`)는 이미 `ApiResponse<List<T>>`(`ApiResponse.success(Page<T>)` 재사용)로 구현돼 있고, NTF(`NotificationController.getNotifications()`)도 이를 그대로 따랐습니다(ADM/STT 도메인은 5단계 선택 범위라 아직 구현되지 않았습니다 — 그 도메인을 시작할 때도 이 관례를 그대로 적용하세요).

### 예외 처리
모든 도메인 예외는 `BusinessException(errorCode, message, HttpStatus)`을 상속합니다. 개별 `@ExceptionHandler`를 도메인마다 추가하지 말고, `common.exception`의 전역 `@RestControllerAdvice` 하나가 전부 처리하게 하세요. 새 예외를 추가할 때는 상속만 하면 됩니다.

**`ValidationExceptionHandler`라는 별도 클래스는 없습니다:** 프로그램 설계서는 COM-VAL-01의 클래스 설계에 `ValidationExceptionHandler`(MethodArgumentNotValidException 처리)를 별도로 정의하지만, 이 예외도 "도메인별 @ExceptionHandler를 추가하지 말라"는 위 COM-EXC-01 원칙의 적용 대상이라 실제로는 `GlobalExceptionHandler.handleMethodArgumentNotValid()`가 그대로 처리합니다(설계서는 이 메서드를 `handleValidationException`이라 부르지만 실제 이름은 스프링 관례를 따른 `handleMethodArgumentNotValid` — 기능은 동일). `@ValidPassword`/`@ValidNickname`(`common.validation`) 같은 COM-VAL-01의 커스텀 제약이 위반되면 `MethodArgumentNotValidException`이 던져지고, 그 어노테이션의 `message()`가 `ApiResponse.FieldError.message`로 그대로 노출됩니다 — 새 커스텀 제약을 추가할 때 별도 핸들러를 만들지 말고 이 경로를 그대로 타게 두세요(검증: `SignupPolicyValidationIntegrationTest`).

**`@ValidPassword`/`@ValidNickname`은 DTO에 단독으로만 쓰세요, `@NotBlank`를 함께 붙이지 마세요:** 두 검증기 모두 null/공백을 자체적으로 무효 처리하도록 만들어졌습니다(그래야 애노테이션 하나만으로 완결된 제약이 됩니다). `@NotBlank`를 같은 필드에 함께 붙이면 공백 입력 시 "must not be blank"와 이 애노테이션의 기본 메시지가 한 필드에 중복으로 실립니다(회귀 테스트: `PasswordValidatorTest`/`NicknameValidatorTest`의 `NotBlank와_함께_쓰면...` 케이스). SVC-AUTH-01(회원가입)·SVC-USER-01(회원정보 수정) DTO를 작성할 때 이 두 필드에는 `@NotBlank` 없이 커스텀 애노테이션만 붙이세요. 참고로 요구사항정의서 3.1절(signup 처리 로직)은 아직 "DTO 단 `@Pattern`"이라는 옛 표현으로 남아 있는데, 실제로 따라야 할 것은 프로그램 설계서 5.8절의 커스텀 애노테이션 방식(`@ValidPassword`/`@ValidNickname`)입니다 — 더 구체적인 검증 로직(길이+문자 조합 조건)을 표현할 수 있어 이쪽으로 구현했습니다.

### 인증
- 모든 요청은 JWT 필터를 통과하되, **토큰이 없어도 요청을 차단하지 않습니다** — 비로그인 조회를 전면 허용하는 게 이 서비스의 원칙입니다.
- 인증이 실제로 필요한 엔드포인트(`POST /api/auth/logout`, `/api/users/**`(SVC-USER-01), 관심등록, 알림설정, 관리자)만 Spring Security 설정에서 별도로 인증을 강제합니다. `logout()`/USER 도메인 세 엔드포인트는 전부 `UserPrincipal me` 파라미터가 있어야 성립하는 연산이라 구현 시점에 순서대로 추가됐습니다 — 요구사항정의서 2.4절 원문은 이 목록에서 로그아웃과 마이페이지를 빠뜨리고 있으니(관심등록/알림설정/관리자만 예시로 듦) 문서 업데이트가 필요합니다.

**알려진 잔여 리스크 — 탈퇴/정지 직후에도 기존 Access Token이 한동안 유효합니다.** `JwtAuthenticationFilter`는 매 요청 DB를 재조회하지 않고 토큰 클레임만 검증하므로, `refreshAccessToken()`(SVC-AUTH-01, 코드리뷰에서 지적되어 이미 상태 검사를 추가함)과 달리 `/api/users/**`(SVC-USER-01의 getMe/updateMe/withdraw)는 계정이 방금 탈퇴·정지됐어도 만료 전(최대 `accessTokenValidity`, 기본 30분)까지 기존 Access Token으로 계속 호출할 수 있습니다. SVC-USER-01 설계 시점에 지성이 검토했고, 지금 세 메서드에 개별적으로 상태 검사를 추가하는 대신 **FAV/NTF 도메인까지 구현한 뒤 COM-SEC-01(`JwtAuthenticationFilter`) 레벨에서 한 번에** 해결하기로 결정했다 — 예: Redis에 `user:status:{userId}` 같은 짧은 TTL 캐시를 두고 필터가 매 요청 그 캐시만 확인(로그아웃/탈퇴/정지 시 evict)하는 식. 프로그램설계서의 "신규 제안, 반영 전 검토 필요"(로그인 실패 잠금, 아래 SVC-AUTH-01 표) 항목과 같은 카테고리의 미확정/유예 사항으로 취급하라 — 개별 도메인 메서드에 임기응변으로 상태 검사를 흩뿌리지 말 것.
- 관리자 엔드포인트는 인증 + `@PreAuthorize("hasRole('ADMIN')")` 이중 체크.
- 비밀번호는 반드시 BCrypt. 평문 저장/로깅 금지.
- 미인증 접근이 거부될 때는 Spring Security 기본 401 대신 `RestAuthenticationEntryPoint`(`common/security/`)가 COM-RES-01 표준 에러 포맷을 유지한다.

### SVC-AUTH-01 구현 결정 사항
프로그램 설계서 3.1절이 상세히 기술하지 않았거나 미확정으로 남겨둔 세부 사항을 구현 시점에 확정한 내용이다. 설계서 자체를 아직 갱신하지 못했으니, 설계서를 다시 볼 때는 아래 표를 함께 참고하고, 가능하면 설계서 쪽에도 반영하라.

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| 로그인 실패 잠금(5회/5분) | "신규 제안, 반영 전 검토 필요"로 미확정 표시 | 사용자 확인 후 구현 확정. Redis 키(`login:fail:{email}`)의 TTL을 **실패마다 5분으로 다시 건다** — 첫 실패 시점 고정 만료가 아니라 마지막 실패로부터 5분 뒤 잠금이 풀리는 슬라이딩 윈도우 | 설계서 문구("약 5분 TTL로 잠금")가 고정/슬라이딩 여부를 명시하지 않아, 마지막 시도 기준으로 5분을 보장하는 쪽이 사용자에게 더 예측 가능하다고 판단해 슬라이딩으로 결정(`LoginAttemptService.recordFailure()`) |
| `logout()` 소유자 검증 | 설계서 3.1절에 세부 로직 없음(시그니처만 `logout(Long userId, String refreshTokenValue)`) | 조회된 Refresh Token의 소유자(`user_id`)가 인자로 받은 `userId`와 다르면 `InvalidRefreshTokenException` | 시그니처가 굳이 `userId`를 받는 이유가 이 검증 외엔 없고, FAV 도메인 등 다른 프로그램의 "소유자 검증 후 삭제" 패턴과 일관됨(`AuthService.logout()`) |

### SVC-USER-01 구현 결정 사항

| 항목 | 설계서 상태 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| `InvalidCredentialsException` 소속 | 원래 `auth.exception` | `common.exception`으로 이동 | SVC-USER-01 설계서가 `updateUser()`의 현재 비밀번호 불일치를 AUTH-01과 **같은 클래스명**으로 지정해, 도메인 전용이 아니라 여러 도메인이 함께 쓰는 COM-EXC-01 공통 예외로 승격했다. 기본 메시지도 "이메일 또는 비밀번호가 일치하지 않습니다"에서 "비밀번호가 일치하지 않습니다"로 좁혔다 — updateUser()/withdraw() 문맥엔 이메일이 없어 원래 문구가 맞지 않았고, login()의 익명성 보장(이메일 미존재/비밀번호 불일치를 구분 못 하게 함)은 문구가 아니라 "두 실패가 같은 메시지를 공유한다"는 사실에서 나오므로 이 변경으로도 그 보장은 유지된다. |
| `UpdateUserRequest`의 닉네임/비밀번호 검증 | "닉네임/비밀번호 각각 선택적"만 언급, 검증 방식은 없음 | `@ValidNickname`/`@ValidPassword`를 그대로 못 쓰고 `@ValidNicknameIfPresent`/`@ValidPasswordIfPresent`(`common/validation/`) 신설 | 기존 두 애노테이션은 null을 무효로 처리하도록 설계돼(COM-VAL-01, 회원가입처럼 필수 필드 전용) 부분 수정의 "필드 생략 시 변경 안 함" 의미와 정면으로 충돌한다. 새 애노테이션은 null만 유효로 통과시키고 non-null 값은 기존 `NicknameValidator`/`PasswordValidator`에 그대로 위임한다(로직 중복 없음). |
| **닉네임 "특수문자 제한" — 3.2절과 5.8절이 서로 다른 말을 했던 문제(해결됨, 지성 확인 완료)** | 3.2절(`updateUser()` 처리 로직)은 "2~12자·**특수문자 제한 규칙**(COM-VAL-01)을 통과한 값으로 갱신"이라고 적어 뒀지만, COM-VAL-01을 확정 정의하는 5.8절은 "2~12자"만 규정하고 문자 집합 제한은 아예 언급하지 않는다. **요구사항정의서 FR-1.1**(이메일·비밀번호·닉네임 입력/이메일 형식·중복 확인/비밀번호 단방향 암호화만 명시, 닉네임 형식 제약 없음)과 **UI정의서 5.1절 AUTH-02 예외처리표**("비밀번호 정책 미충족" 행만 있고 닉네임 관련 행 자체가 없음) 어디에도 "특수문자 제한"의 근거가 없다 — 이 문구는 프로그램설계서 3.2절 한 곳에만 고립돼 등장한다. **결론(지성 확정): 3.2절의 "특수문자 제한 규칙" 문구는 COM-VAL-01이 5.8절로 확정되기 전 초안 단계에서 남은 낡은 서술이며, 실제로 반영된 적이 없다 — 5.8절(2~12자, 문자 제한 없음)이 확정 스펙이 맞다.** 3.2절 쪽을 5.8절과 일치하도록 갱신하는 것이 다음 문서 동기화 시점의 할 일이다. | `NicknameValidator`/`NicknameIfPresentValidator`는 길이(codePointCount 2~12)만 검사하고 문자 집합 검사는 없다. `UserService.changeNickname()`도 추가 검증 없이 그대로 대입한다 — **이 구현이 맞는 스펙(5.8절)을 그대로 따르고 있으므로 코드 변경 불필요.** SCR-AUTH-02 프론트(`features/auth/validation.ts`의 `isValidNickname()`)도 이 확정된 동작을 그대로 미러링해 길이만 검사한다 — 이 역시 수정 불필요 | 최초엔 "코드에 특수문자 제한이 구현/제거된 흔적이 없다"(테스트에 특수문자 케이스 없음, git log상 관련 파일을 건드린 커밋이 COM-VAL-01 최초 구현 `1a8e48b`/SVC-USER-01 `e2bc1db` 둘뿐)는 것을 드리프트 가설의 근거로 들었는데, 이건 틀린 추론이었다(지성 지적) — "3.2절이 방치된 드리프트다"와 "COM-VAL-01 구현이 3.2절 요구사항을 놓쳤다" 두 가설 모두 정확히 같은 코드 증거를 만들어내는 대칭적 증거라 코드만으로는 구분이 불가능했다. **실제 결정력 있는 근거는 상위 문서였다** — 진짜 의도된 설계였다면 요구사항정의서나 UI정의서 예외처리표 어딘가에는 흔적이 남았을 텐데, "특수문자 제한"이 3.2절 한 곳에만 고립돼 등장한다는 사실이 드리프트 가설을 사실상 확정 지었다(지성 교차 확인). 이 항목은 프론트/백엔드 어느 쪽 코드도 고칠 필요가 없다는 점에서 종료됐고, 남은 액션은 3.2절 문서 자체를 5.8절에 맞춰 갱신하는 것뿐이다. |
| `WithdrawRequest` 필드 구성 | 설계서에 검증 언급 없음(컨트롤러 시그니처만 `withdraw(UserPrincipal me, WithdrawRequest req)`) | `password`(필수, `@NotBlank`, `PasswordEncoder.matches()`로 재확인 실패 시 `InvalidCredentialsException`) + `reason`(선택, 검증 없음, 저장하지 않고 받기만 함) | UI정의서 MY-01 이벤트 정의("탈퇴 사유 확인 → 최종 확인 다이얼로그")에 `reason`이 이미 명시돼 있다. `password`는 세션 탈취·오조작으로 인한 계정 삭제 사고를 막는 통상적 방어선이며, 컨트롤러가 애초에 body를 받도록 설계된 것 자체가 빈 바디가 아니었다는 정황 증거다. `reason`을 저장할 스키마가 없어 지금은 버리고, 필요해지면 `User` row가 물리 삭제되지 않으므로 나중에 컬럼을 추가해도 된다. |
| 계정 status=ACTIVE 검사 | 예외표에 없음 | getMe/updateMe/withdraw 세 메서드 모두 **추가하지 않음** | 위 "알려진 잔여 리스크" 참고 — FAV/NTF까지 구현한 뒤 COM-SEC-01 레벨에서 한 번에 처리하기로 결정. |

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

### API-SEARCH-01/SVC-SEARCH-01 인기 검색어 (신규 제안 — 반영 전 검토 필요)

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
것으로 보인다. **실질적 영향:** 이 저장소를 새로 clone한 사람(다른 개발자, CI, 다음 세션)은 11개
프로덕션 테이블(user/refresh_token/legal_district_code/complex/trade/favorite_property/favorite_region/
recent_view/notification_setting/notification/batch_log) 중 무엇 하나도 저장소만으로는 만들 수 없다 —
`spring.jpa.hibernate.ddl-auto=validate`라 스키마가 없으면 애플리케이션이 기동 자체를 거부한다. **완결
필요(우선순위 높음, HOME-01 범위 밖) —** 테이블 정의서 8장 원문을 `schema_all.sql`(저장소 루트 또는
`backend/schema_all.sql`, `.gitignore` 예외가 이미 둘 다 커버한다)로 옮겨 커밋하라. 이 프론트 세션은
그 문서 원문에 접근할 수 없어(백엔드 전용 프로젝트 지식) 직접 처리하지 못했다 — 다음에 백엔드 작업을
하는 세션이나 지성이 직접 처리해야 한다.

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
| `complexDetailV2::{complexId}` | 단지 상세 조회 |
| `popularComplexes::{limit}` | 인기 단지 목록 |
| `regionAutocomplete::{query}` | 지역 자동완성 |

거래 검색/이력 조회는 배치 직후 변경 가능성이 있어 **캐시를 적용하지 않습니다.**

**캐시 이름에 담긴 DTO에 필드를 추가/변경할 때는 캐시 이름 자체를 버전업하세요(`complexDetail` → `complexDetailV2`처럼).** Redis가 배포 사이에도 살아남는 환경에서는 옛 필드 구성으로 직렬화된 엔트리가 새 배포 후에도 남아있고, 새로 추가된 필드는 역직렬화 시 예외 없이 조용히 null로 채워집니다. 그 null이 소비 로직에서 정상적인 분기를 타 버릴 수 있습니다 — 실제로 SVC-RCV-01에서 `ComplexDetailResponse`에 `housingType`을 추가했을 때, 배포 전 캐시된 옛 엔트리가 `housingType=null`로 역직렬화되면 `RecentViewService.record()`가 이를 "housingType 미확정"과 구분하지 못해 조용히 기록을 스킵하는 문제가 있었습니다(Codex 코드리뷰 지적, 검증: `ComplexDetailCache.java`). 캐시 이름을 올리면 옛 엔트리를 애초에 다시 읽지 않게 되고, 그 옛 엔트리는 각자의 TTL로 자연 만료됩니다 — 배포 시점에 수동으로 Redis를 flush할 필요가 없습니다.

**캐시 무효화 트리거는 캐시마다 다릅니다 — 하나의 이벤트에 묶지 마세요:**

| 캐시 | 무효화 이벤트 | 발행 주체 | 주기 |
| --- | --- | --- | --- |
| `complexDetailV2::{complexId}` | `TradeCacheEvictionEvent` | BAT-LOD-01(`TradeDataLoader`) | 일 1회 이상 |
| `popularComplexes` | `TradeCacheEvictionEvent` (전체 evict) | BAT-LOD-01(`TradeDataLoader`) | 일 1회 이상 |
| `regionAutocomplete` | `LegalDistrictCodeReloadedEvent` (전체 evict) | BAT-MAT-01(`LegalDistrictCodeLoader`) | 비정기(CSV 재적재 시에만) |

`regionAutocomplete`를 `TradeCacheEvictionEvent`(일 단위)에 묶으면 정적 데이터를 매일 무효화하게 돼 TTL을 길게 가져가려는 설계 의도가 깨집니다 — 실제로 COM-CACHE-01 1차 구현에서 이 실수가 있었고(`CacheEvictionListener`가 `TradeCacheEvictionEvent.legalDongCds()`로 `regionAutocomplete`까지 evict), 이후 `LegalDistrictCodeReloadedEvent`를 신설해 분리했습니다(검증: `CacheEvictionListener.java`, `LegalDistrictCodeLoader.java`).

**`@Cacheable` 메서드가 null을 반환할 수 있다면 반드시 `unless = "#result == null"`을 붙이세요.** `CacheConfig`는 의도적으로 `disableCachingNullValues()`를 켜지 않습니다 — 그 옵션은 "null은 캐싱을 조용히 건너뛴다"가 아니라 "null을 캐시에 저장하려는 시도 자체를 `IllegalArgumentException`으로 거부한다"로 동작해서(`AbstractValueAdaptingCache.toStoreValue()`), `unless` 없이 null을 정상 반환하는 `@Cacheable` 메서드가 그 순간 예외로 깨집니다(검증: 코드리뷰 PR #14, `CacheConfig.java`). null 캐싱을 피하고 싶으면 캐시 설정이 아니라 해당 `@Cacheable` 애노테이션에 `unless` 조건을 붙이세요.

**이벤트 클래스명이 설계서와 다릅니다:** 설계서 5.4절 원문은 `TradeLoadedEvent`로 되어 있지만, 실제 구현(BAT-LOD-01)은 `TradeCacheEvictionEvent`입니다(complexId·legalDongCd 집합을 담는 필드도 설계서에 없던 내용). 새로 코드를 짤 때는 설계서 원문이 아니라 이 이름을 따르세요 — 설계서 4.6/5.4절은 아직 업데이트되지 않았습니다.

**`CacheEvictionListener`의 두 리스너는 반드시 자기 몸통을 try-catch로 감싸 evict 실패를 흡수해야 합니다.** `TradeCacheEvictionEvent`는 `TradeDataLoader.loadBatch()`가 청크를 전부 커밋한 뒤에만 발행되는데, `@TransactionalEventListener`는 리스너를 발행자와 같은 스레드에서 동기 호출하므로 리스너가 던진 예외는 그대로 `publishEvent()` 호출자(`loadBatch()`)로 전파됩니다. 캐시 인프라 장애(예: Redis 다운)로 `cache.evict()`/`cache.clear()`가 예외를 던지면, `loadBatch()`가 이미 계산해 둔 실제 처리 건수(`LoadResult`)를 반환하지 못하고 그 예외가 대신 전파돼, `BatchExecutionOrchestrator`(BAT-SCH-01, `TradeIngestionPipeline` 경유)가 이미 DB에 커밋된 적재 건을 `batch_log`에 "0건 처리, 0건 에러"로 잘못 기록하는 문제가 있었습니다(Codex 코드리뷰 P2 지적) — 캐시가 stale해지는 부수 효과(최악의 경우 TTL 24h)가 이미 커밋된 적재 결과의 정확한 기록을 훼손할 수는 없다는 게 이 방어의 근거입니다. 새 리스너를 추가할 때도 캐시 백엔드 장애가 발행자 쪽 로직에 영향을 주지 않도록 이 패턴을 그대로 따르세요(검증: `CacheEvictionListenerTest.캐시_인프라_장애로_evict가_실패해도_예외를_전파하지_않는다`).

**전수 확인(2026-09-09) — 지금은 `@TransactionalEventListener`/`@EventListener`를 쓰는 곳이 `CacheEvictionListener` 하나뿐이다.** `grep -rn "@TransactionalEventListener\|@EventListener" src/main/java`로 확인했다. 다만 발행되는 이벤트는 3종(`TradeCacheEvictionEvent`, `LegalDistrictCodeReloadedEvent`, `TradeCollectionCompletedEvent`)인데 그중 `TradeCollectionCompletedEvent`(`BatchExecutionOrchestrator.orchestrate()`가 조합 순회 완료 시 발행)는 **아직 구독자가 없다** — BAT-NTF-01(관심대상 조건평가→알림 생성, 아직 미구현, "남은 백엔드 작업" 목록 참고)이 이 이벤트에 붙을 유력한 후보다. BAT-NTF-01을 구현할 때 그 리스너가 `@TransactionalEventListener(AFTER_COMMIT)`로 이 이벤트를 받는다면(발행 시점이 `orchestrate()`의 조합 순회 전체가 끝난 뒤라 이 페이즈가 자연스럽다), **이 리스너도 반드시 자기 몸통을 방어적으로 감싸야 한다** — 조건평가·알림 생성·이메일 발송 어느 단계든 예외가 새어나가면 그 예외가 발행자(`orchestrate()`)까지 전파되어, 이미 정상적으로 끝난 배치 순회 자체가 실패한 것처럼 보이거나(로그·모니터링 오염) 최악의 경우 `orchestrate()`의 남은 후처리(예: `TradeCollectionCompletedEvent` 발행 이후 로직이 더 있었다면 그 실행)를 막을 수 있다. 이 문서의 위 문단과 같은 근거로 처리하라.

### 날짜/시간 처리

- **날짜 관련 로직은 반드시 명시적으로 KST(`Asia/Seoul`) 기준으로 계산하세요.** JVM 기본 타임존이 UTC인 배포 환경(컨테이너 등)에서 `LocalDate.now()`/`YearMonth.now()`를 인자 없이 호출하면 서버 로컬 타임존을 따라가 버립니다 — 항상 `ZoneId.of("Asia/Seoul")`을 명시적으로 넘기세요. `spring.jackson.time-zone`은 JSON 직렬화에만 영향을 줄 뿐 이 계산에는 적용되지 않습니다. 아직 공용 `Clock` 빈은 없고 각 클래스가 자체 `KST` 상수를 선언합니다(`TradeCollectionScheduler`, `RegionStatsCalculator`, `FavoriteService`) — 새 클래스를 추가할 때도 이 패턴을 그대로 따르세요. 배치(BAT-)와 API 서버(SVC-)가 서로 다른 배포 환경에서 돌 수 있어, 이 원칙을 지키지 않으면 서버마다 다른 기준일로 계산되는 조용한 버그가 생깁니다.
- **"현재 시점까지"를 포함하려는 기간 range 쿼리는 상한을 오늘 날짜가 아니라 오늘+1일로 배타적(`<`) 상한을 잡으세요.** `dealDate < :to`처럼 배타적 상한 비교에 `LocalDate.now(KST)`를 그대로 넘기면 오늘 발생한 데이터가 항상 조회에서 빠집니다 — SVC-RGN-01/SVC-FAV-01의 `RegionStatsCalculator.calculate()`/`FavoriteService.calculatePropertyChangeRate()`(`TradeRepository.findAverageSaleAmount`/`findAveragePricePerPyeongForSale`/`countSaleTrades`/`findAverageSaleAmountByComplex`)에서 이 실수가 있었고 상한을 `now.plusDays(1)`로 고쳤습니다(코드리뷰 P2 지적). 같은 유형의 "현재 시점까지" range 쿼리를 새로 짤 때(SVC-CPX-01 최신순 대표거래 서브쿼리, SVC-STT-01(5단계)의 getTypeComparison/getPriceTrend, BAT-NTF-01의 신규 평균가 계산 등) 같은 실수가 재발하지 않도록 주의하세요.

## 데이터베이스 스키마

11개 테이블, 161개 컬럼, FK 17건, CHECK 10건, UNIQUE 8건. 전체 DDL은 테이블 정의서 8장 원문을 그대로 사용하세요 — 여기 요약만 보고 타이핑하지 말고 원문을 복사하는 걸 권장합니다.

### 테이블 목록

| 테이블 | 컬럼수 | 요약 |
| --- | --- | --- |
| `user` | 11 | 회원 (role: USER/ADMIN, status: ACTIVE/SUSPENDED/WITHDRAWN) |
| `refresh_token` | 6 | JWT Refresh Token |
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
- **`LegalDistrictCodeLoader`가 리(里) 단위 행의 `eupmyeondong_name`을 저장하는 방식 — 완결 필요(지성 확인 필요).** `resolveNameParts()`는 시도/시군구 대표행에만 특수 분기를 두고, 그 이하(읍면동만 있는 행이든 읍면동+리가 있는 행이든)는 전부 "시군구 대표행 이름을 뺀 나머지 전체"를 `eupmyeondong_name` 하나에 그대로 담는다 — 즉 "부산광역시 기장군 기장읍"은 `eupmyeondong_name="기장읍"`으로, "부산광역시 기장군 기장읍 동부리"는 읍/리를 분리하지 않고 `eupmyeondong_name="기장읍 동부리"`로 저장된다(검증: `LegalDistrictCodeLoaderTest`). 형제 리(동부리/서부리)끼리는 서로 다른 문자열로 저장되므로 SVC-RGN-01 자동완성에서 완전히 동일한 문구가 중복 노출되는 문제는 없다 — 다만 **국토교통부 실거래가 API의 `umdNm`이 리 지역에서 읍/면 이름만("기장읍") 주는지, 읍+리를 합친 값을 주는지 이 프로젝트가 아직 실제 API 응답으로 확인하지 못했다.** 전자라면 `LegalDistrictMatcher.matchByTradeSggCd()`(정확 일치만 매칭)가 이런 리 행의 `eupmyeondong_name`과 절대 일치하지 않아 그 행에는 거래가 영원히 매칭되지 않는다 — 법정동코드 CSV 기준 시도+시군구(438건)를 뺀 나머지 중 리 단위(4~5단계)가 약 77%를 차지해(코드리뷰에서 지적, 전체 분포: 시도 25/시군구 438/읍면동만 10,896/읍면동+리 37,450/시군구 하위구 포함 읍면동+리 1,052) 영향 범위가 작지 않다. 실제 배치 파이프라인을 리 지역 표본으로 한 번 돌려(또는 실 API 샘플 응답을 받아) `umdNm` 형식을 확인하기 전까지는 매칭 로직을 임의로 바꾸지 마라 — 잘못 짐작해 문자열을 쪼개면 반대 방향(읍/면 단위까지만 오는 umdNm을 리 단위로 잘못 쪼개 실패시키는) 회귀를 만들 수 있다.

## API 엔드포인트 (도메인별 base path)

| 도메인 | Base Path | 대표 엔드포인트 |
| --- | --- | --- |
| 인증 | `/api/auth` | `POST /login`, `/signup`, `/refresh`, `/logout`, `GET /check-email` |
| 회원 | `/api/users` | `GET·PUT /me`, `DELETE /me` |
| 단지 | `/api/complexes` | `GET /search`, `/popular`, `/{id}`, `/map` |
| 실거래 | `/api/trades` | `GET /search`, `?complexId=`, `/{tradeId}` |
| 지역 | `/api/regions` | `GET ?query=`, `/interest-summary` |
| 최근조회 | `/api/recent-views` | `GET` |
| 관심 | `/api/favorites` | `GET·POST·DELETE /properties`, `/regions` |
| 알림 | `/api/notifications` | `GET·PUT /settings`, `GET`, `PATCH /{id}/read` |
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
| **하위 구 코드 자체가 legal_district_code에 없음 — 진짜 커버리지 공백(데이터 손실 확정)** | **화성시(41590) 1개 + 인천 중구/동구/서구/옹진군(28110/28140/28260/28720) 4개 + 광주 5개구 전체(29110/29140/29155/29170/29200) + 전라남도 22개 시군구 전체(46110~46910)** = 32개 | `trade` 테이블에 `legal_dong_cd LIKE '29%'` 또는 `'46%'`인 행이 **0건** — 광주광역시+전라남도 전체(인구 약 300만)가 실거래 데이터 수집 자체에서 완전히 빠져 있다. 화성시·인천 3구도 마찬가지로 raw 데이터가 0건. 이미 CLAUDE.md가 "잔여 불일치 2,399개 단지"(complex 매칭 실패)로 문서화했던 화성 신설 일반구/인천 신설 자치구/광주-전남 통합 이슈가, 사실은 **complex 매칭 단계가 아니라 그보다 훨씬 앞선 원시 수집 단계(BAT-CLC-01)에서부터 100% 실패**하고 있었다는 뜻이다 — data.go.kr이 이 지역들에 대해 이미 신설/개편된 lawd_cd를 요구하는데, 우리 `legal_district_code`(구버전 CSV)는 그 신설 코드를 아예 갖고 있지 않아 옛 코드로 질의하면 항상 빈 결과(result_code 000, "정상"으로 위장된 데이터 없음)만 돌아온다. **완결 필요(우선순위 높음) — 실제 data.go.kr LAWD_CD 목록(또는 최신 법정동코드 CSV)에서 이 32개 지역의 현재 유효 코드를 확인해 legal_district_code를 갱신해야 한다. 이번 세션은 이 원인 확정까지만 하고 코드/데이터 수정은 하지 않았다.** |
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

## 개발 단계 (MVP 로드맵)

요구사항 정의서 9장 기준. 순서대로 진행하세요.

1. **1단계 (핵심 검증, 아파트 중심)** — 인증, 단지/실거래 배치·매칭·조회, 캐싱. 여기서 만든 배치·매칭·화면·API는 전부 아파트+연립다세대 겸용으로 설계되어 있습니다.
2. **2단계 (연립다세대 확장)** — **신규 프로그램 0개.** 1단계에서 만든 프로그램의 `housingType` 파라미터 범위를 `APT`에서 `APT, VILLA`로 넓히기만 하면 됩니다. 별도 클래스를 만들지 마세요.
3. **3단계 (지도 기반 조회)** — 지오코딩, 지도 화면/API.
4. **4단계 (개인화)** — 관심 매물/지역, 알림.
5. **5단계 (선택 범위)** — 통계 비교, 관리자, 소셜 로그인. 우선순위 "하".

## 프로그램 인벤토리

총 60개 프로그램(백엔드 38 + 프론트엔드 22)이 프로그램 목록서에 ID로 정의되어 있습니다. 새 클래스를 만들 때 대응하는 프로그램 ID를 확인하고, 커밋 메시지나 PR에 ID를 남기면 추적이 쉽습니다 (예: `[API-CPX-01] 단지 검색 엔드포인트 구현`).

- **API/SVC** (도메인당 1쌍, 10개 도메인): AUTH, USER, CPX, TRD, RGN, RCV, FAV, NTF, STT(5단계), ADM(5단계)
- **BAT** (10개, 파이프라인 순서): SCH-01, CLC-01, PRS-01, MAT-01, MAT-02, LOD-01, ERR-01, GEO-01, NTF-01, MAIL-01
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
- [ ] 회원 탈퇴 시 물리 삭제 — 항상 `status='WITHDRAWN'` 소프트 삭제.
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
| accessToken 자동 갱신 인터셉터 | "이후 화면 작업에서 별도로 진행" (프롬프트 명시 범위 밖) | `src/lib/httpClient.ts`에 axios 인스턴스만 분리해두고 인터셉터는 아직 추가하지 않았다 | 보호된 라우트가 실제로 생기는 시점(FAV/NTF 등 인증 필요 화면 구현 시)에 이 인스턴스에 `401`→`/api/auth/refresh` 인터셉터를 끼워 넣는다. |

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
| "이용약관"/"개인정보처리방침" 링크 — **완결 필요** | "실제 문서·화면 ID가 아직 없으므로 클릭 가능한 링크로 만들지 않는다" | `<Link>`/`<a>`가 아니라 순수 `<span>`으로 렌더링(클릭 핸들러 없음) — 색상(`text-brand`)과 밑줄(모바일·태블릿만, `underline lg:no-underline`)만 Figma대로 재현해 시각적으로는 링크처럼 보이되 실제로는 비활성이다 | 실제 약관/정책 페이지가 생기면 이 두 `<span>`을 `<Link to="/terms">`/`<Link to="/privacy">`로 교체하기만 하면 된다. |
| `TextField`의 `error: boolean` → `status: 'default'\|'success'\|'error'` 리팩터 | 없음(AUTH-01은 이분법 에러만 필요했음) | AUTH-02가 성공(초록)/실패(빨강)/기본 3단 상태를 필요로 해 `TextField`를 확장하는 대신, 입력 상자 자체(테두리 색상 로직 포함)를 `Input`(`components/ui/Input.tsx`)으로 분리하고 `TextField`는 "label + Input" 조합만 담당하게 재구성했다. `LoginPage`도 새 `status` prop을 쓰도록 함께 바꿨다(하위 호환 shim 없이 — 사내 컴포넌트라 두 소비자를 그냥 함께 고치는 쪽이 더 간단하다고 판단) | 이메일 필드가 "중복확인" 버튼과 가로로 나란히 배치돼야 해서(Figma), 라벨까지 포함한 `TextField` 한 덩어리로는 이 레이아웃을 표현할 수 없었다 — 입력 상자만 따로 쓸 수 있어야 했다. |
| `CheckIcon` 하나로 3곳 재사용 | 없음(신규 판단) | FieldHint 성공 아이콘(14px, `#00a63e`), 비밀번호 체크리스트 충족 아이콘(12px, `#00c950`), 약관 체크박스 체크마크(12px, 흰색) — Figma가 내려준 세 SVG가 전부 동일한 체크마크 도형을 스케일·색상만 바꾼 것이었다(좌표를 14/12배 하면 정확히 일치) | 세 파일로 쪼개지 않고 `strokeWidth` prop 하나로 통일 — G4(제공된 힌트 우선순위) 적용 시 "동일 글리프면 재사용"이 3개의 근사 중복 아이콘보다 나은 선택이라고 판단했다. |
| AUTH-01 소급 정정 — 카드 padding/breakpoint | 없음(회고) | `AuthLayout`(`components/layout/AuthLayout.tsx`)으로 배경+카드 셸을 공통화하면서, AUTH-01 최초 구현 당시 Figma 모바일 목업이 없어 추정했던 모바일 padding(24px, `sm:` 640px 브레이크포인트)을 이번에 Figma로 확정된 AUTH-02 수치(28px, `md:` 768px 브레이크포인트 — tablet은 desktop과 동일한 40px/16px)로 두 화면 모두 맞췄다 | 프롬프트가 "카드/배경 등 전역 토큰은 AUTH-01과 동일하다"고 명시했는데, 실제로는 AUTH-01 쪽이 근거 없는 추정치였다 — 이번에 확인된 진짜 값으로 두 화면을 통일하는 것이 "같은 토큰"이라는 전제를 사후적으로 참으로 만드는 유일한 방법이었다. |
| 이메일 중복확인의 stale 응답 경쟁 상태 | 없음(PR 리뷰 지적, Codex) | 이메일을 A→B로 빠르게 바꿔가며 중복확인을 두 번 트리거하면, 먼저 보낸 A의 느린 응답이 나중에 도착해 이미 B로 갱신된 최신 상태를 덮어쓸 수 있었다 — 요청마다 증가하는 카운터(`emailCheckRequestId`, `useRef`)로 응답이 도착했을 때 "여전히 최신 요청인지"를 확인해 아니면 폐기하고, 값이 바뀌는 순간(`resetEmailCheck`)에도 카운터를 먼저 올려 그 시점에 진행 중이던 요청을 전부 폐기 대상으로 만든다 | Playwright로 정확히 이 경쟁 상태(느린 A, 빠른 B)를 재현해 수정 확인. **일반화 메모(지성 지적, 지금 당장 처리할 필요 없음):** 이 "느린 요청이 늦게 도착해 최신 상태를 덮어쓰는" 패턴은 blur/입력 기반 비동기 검증 전반(예: SRCH-01 지역 자동완성, 다른 화면의 실시간 검증)에서 재현될 수 있는 일반적인 문제다. 지금은 `emailCheckRequestId` 카운터 로직이 `SignupPage.tsx`에 로컬로 박혀 있는데, 이 패턴이 두 번째로 필요해지는 시점에 `useLatestRequest` 같은 공용 훅으로 뽑아 이 로직을 재사용하는 방향을 검토하라 — 위 "재사용 가능하게 분리한 컴포넌트" 원칙과 같은 결이다. 지금 미리 뽑아두는 것은 아직 두 번째 소비자가 없어 과설계로 판단해 하지 않았다. |
| **서버 제출 에러 vs 클라이언트 검증 결과 — 표시 우선순위 규칙(UI정의서 미명시, 신규 판단)** | 없음 — UI정의서 어디에도 "같은 필드에 서버 에러와 클라이언트 실시간 검증 결과가 동시에 존재할 때 무엇을 먼저 보여줄지"에 대한 규칙이 없다 | **서버가 반환한 필드별 에러(`serverFieldErrors`)가 항상 클라이언트 쪽 실시간 검증/조회 결과보다 우선 표시되고, 그 필드의 값이 바뀌기 전까지 유지된다.** `email`/`password`/`nickname` 세 필드 모두 `register(field, { onChange: ... })`에서 공용 `clearServerFieldError(field)`를 호출해 값이 바뀌는 즉시 해당 필드의 서버 에러를 지운다(이메일은 `emailCheck` 상태 초기화까지 겸하는 `handleEmailChange`가 별도로 감싼다) | 가입 버튼 클릭처럼 사용자가 "이 정도면 됐다"고 확신한 시점에 서버가 실패를 알렸다면, 그 직후 클라이언트 쪽의 낙관적인 실시간 판정(중복확인 "사용 가능" 캐시 등)이 그 실패를 가려선 안 된다고 판단했다 — 서버 판정이 최종 권위이므로 사용자가 실제로 값을 바꿔 문제를 해결하기 전까지는 실패 상태를 계속 보여주는 쪽이 안전하다. **처음엔 이 규칙을 이메일 필드에만 적용했다가(PR 리뷰에서 옛 409 에러가 새 이메일 확인 성공 후에도 남아있던 버그로 지적돼 수정), 이 판단표 항목을 쓰면서 재점검하다가 `password`/`nickname`은 서버 에러를 우선 표시는 하면서도 값이 바뀌어도 지우는 로직 자체가 아예 없다는 걸 뒤늦게 발견했다 — 같은 결함이 두 필드에 그대로 남아있었던 것이다.** 발견한 김에 세 필드 모두 같은 `clearServerFieldError` 헬퍼를 쓰도록 통일해 그 자리에서 함께 고쳤다(Playwright로 password/nickname 각각 재현·검증). 앞으로 비슷한 서버/클라이언트 에러 병존 상황이 새 화면에서 생기면 이 우선순위(서버 우선, 값 변경 시 초기화)를 처음부터 전체 필드에 적용하라 — 한 필드에서만 고치고 넘어가면 이번처럼 나머지 필드에 같은 결함이 남는다. |

### SCR-HOME-01 / UIC-01~03,05,07~09 구현 결정 사항

HOME-01은 이 저장소가 GNB 있는 화면을 만드는 첫 사례라, AUTH-01/02의 `AuthLayout`(중앙 카드, GNB 없음)과
분리된 신규 `MainLayout`(GNB/모바일 헤더 + 본문 + Footer + 모바일 하단 탭)을 도입했다 — 이후
SRCH-01/DTL-01/MAP-01/MY-01 등이 이 레이아웃을 공유한다. 함께 만든 7개 공용 컴포넌트(UIC-01 `Gnb`,
UIC-02 `BottomTabNav`, UIC-03 `SearchBar`, UIC-05 `ComplexCard`, UIC-07 `ToastProvider`/`useToast`,
UIC-08 `EmptyState`/`Spinner`, UIC-09 `DataTrustBadge`)도 같은 이유로 이 절에 함께 기록한다.

| 항목 | 설계서/프롬프트 전제 | 실제 구현 | 근거 |
| --- | --- | --- | --- |
| **`ComplexSummaryResponse`의 정밀/근사 배지·층수 — 사용자 확인 완료** | 작업 지시는 UI정의서 4.9절대로 정밀(EXACT)/근사(SIMILAR) 배지와 층수를 카드에 표시하라고 했다 | `ComplexSummaryResponse.java`를 직접 읽어 확인한 결과 `matchMethod`/`floor` 필드 자체가 없다(둘 다 `Trade` 엔티티엔 존재하지만 이 DTO가 노출하지 않음) — `AskUserQuestion`으로 "프론트에서 생략(권장)" vs "백엔드 DTO 확장" 중 선택을 요청해 **생략**으로 확정했다. `DataTrustBadge`(UIC-09)는 `matchMethod?: 'EXACT'\|'SIMILAR'`를 선택적 prop으로 남겨 필드가 없으면 정밀/근사 배지를 그리지 않고, 값이 들어오면(백엔드가 나중에 노출하면) 그대로 렌더링한다 — housingType 배지(아파트/연립다세대)는 이 DTO에 이미 있어 항상 그린다. `ComplexCard`(UIC-05)의 층수 캡션도 같은 이유로 생략(`전용 {area}㎡ · {dealDate}`만 표시, Figma의 "· {floor}층"은 뺐다) | 이 DTO는 `search()`(SRCH-01)와 `popular()`(HOME-01) 양쪽이 공유하는 계약이라, `ComplexRepositoryCustomImpl` QueryDSL·`ComplexServiceTest`·`ComplexControllerTest`·`ComplexRepositoryMariaDbIT`·`FavoritePropertySummaryResponse`·`TradeSummaryResponse`까지 건드리는 확장을 이번 프론트 전용 작업 범위에서 임의로 진행하지 않기로 했다(백엔드/공유 계약을 사전 승인 없이 건드리지 않는다는 이 세션의 운영 원칙). |
| **`RecentViewResponse`의 주소·가격·면적·층 — 위와 같은 종류의 데이터 갭, 재확인 없이 동일 원칙 적용** | Figma 최근 조회 카드는 주소/가격/전용면적·층을 표시한다 | `RecentViewResponse.java`를 확인한 결과 `complexId`/`complexName`/`housingType`/`viewedAt` 4개뿐이다 — 위 `ComplexSummaryResponse` 항목과 정확히 같은 성격의 갭이라 별도로 `AskUserQuestion`을 다시 묻지 않고 같은 원칙(프론트에서 생략)을 그대로 적용했다. `RecentViews`(pages/home)는 주소/가격/면적·층 대신 housingType 라벨만 캡션으로 보여준다 | 이미 한 번 확정된 "백엔드 DTO를 승인 없이 확장하지 않는다"는 원칙을 매 데이터 갭마다 다시 물을 필요는 없다고 판단했다 — 대신 이 표에 남겨 다음에 RCV 도메인을 다시 열 때 "카드에 실제 정보를 채우려면 이 DTO부터 확장해야 한다"는 사실을 확인할 수 있게 했다. |
| **`InterestRegionSummaryResponse`의 "이번 달 N건" — 같은 종류의 갭** | Figma 관심 지역 카드 캡션이 "평균 거래가 · 이번 달 23건"처럼 거래 건수를 함께 보여준다 | `InterestRegionSummaryResponse`엔 `avgPrice`/`changeRate`뿐이라(건수 필드 없음) 캡션을 "평균 거래가"로 줄였다 | 위 두 항목과 같은 원칙. `SVC-RGN-01`이 향후 `newTradeCount`를 노출하면(CLAUDE.md SVC-RGN-01 절 — MY-02용으로 이미 `RegionStats`엔 추가돼 있으나 이 DTO엔 아직 안 실렸다) 그대로 이어붙이면 된다. |
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
| **백엔드 미기동으로 인한 통합 검증 한계 — 완결 필요** | 완료 조건이 실제 API 연동(`GET /api/complexes/popular` 등)이 올바르게 동작하는지 확인하라고 요구한다 | 이번 세션엔 로컬에 MariaDB가 떠 있지 않았고(Redis만 기동, `docker ps` 확인) 저장소에 `schema_all.sql`도 없어(테이블 정의서 DDL은 외부 문서 전용) 실제 백엔드를 새로 기동해 검증하지 못했다 — 대신 (1) 백엔드 없이 뜬 프런트에서 각 fetch가 실패해도 빈 배열로 우아하게 폴백하는지, (2) Playwright로 `page.route()`를 이용해 5개 엔드포인트를 전부 목킹해 실제 응답 스키마(`ComplexSummaryResponse` 등)를 넣었을 때 카드 렌더링·하트클릭·로그인·자동재생·토스트 전체 왕복이 올바른지 검증했다 | **완결 필요** — 로컬 MariaDB(포트 3307)에 스키마를 적용하고 `./gradlew bootRun`으로 실제 백엔드를 띄운 뒤, 실 데이터로 `GET /api/complexes/popular`/`GET /api/regions/interest-summary`/`GET /api/recent-views`/`GET /api/search/popular` 4개를 다시 확인하라(SVC-FAV-01/SVC-NTF-01 절의 "Docker 없어 IT 미실행" 잔여 리스크와 같은 성격). |
| **하트 클릭이 항상 POST만 호출 — 코드리뷰(P2) 지적, 수정 완료** | 초기 구현은 `favoritedIds`를 항상 빈 Set에서 시작하고 `toggleFavorite`가 항상 `addFavoriteProperty()`(POST)만 호출했다 — UI는 아바타 하트처럼 토글로 보이지만 실제로는 추가 전용이었다 | 이미 관심 매물로 등록된 단지는 새로고침 후에도 빈 하트로 보이고, 클릭하면 해제가 아니라 `DuplicateFavoriteException`(409)만 받았다 — `GET`/`DELETE` 엔드포인트가 이미 있는데도 프론트가 전혀 쓰지 않고 있었다. 로그인 상태 마운트 시 `GET /api/favorites/properties`로 `complexId→favoritePropertyId` 맵을 하이드레이트하고(`useFavoriteToggle.ts`), 이미 등록된 항목은 `toggleFavorite`가 `DELETE /api/favorites/properties/{favoritePropertyId}`(경로의 `{id}`는 complexId가 아니라 favoritePropertyId — `FavoriteController.removeFavoriteProperty()` 확인)를 호출하도록 분기했다. 이 하이드레이션 effect는 로그인 여부 분기가 `getFavoriteProperties()` 호출 **이전**에 있어 비로그인 사용자에게는 이 GET 자체가 나가지 않는다(Playwright로 별도 검증: 마운트·클릭 어느 시점에도 `/api/favorites/properties` 호출 0건, `/login` 리다이렉트만 발생) | `Set<number>`(complexId만)로는 DELETE를 호출할 방법이 없어 `Map<complexId, favoritePropertyId>`로 상태 구조 자체를 바꿔야 했다. Playwright로 "이미 찜한 단지는 채워진 하트로 렌더 → 클릭 시 DELETE(POST 아님) → 해제 토스트"와 "안 찜한 단지는 빈 하트 → 클릭 시 POST → 등록 토스트" 둘 다 목킹된 백엔드로 검증했다. |
| **GNB/모바일 헤더 알림 벨 — 세 차례 코드리뷰(P2)로 점진적으로 바로잡음, 최종 확정** | 초기 구현은 `Gnb`/`MobileHeader` 둘 다 알림 벨을 `onClick` 없는 `<button>`으로 그려 뒀다(`/notifications` 라우트가 이미 있는데도 탭해도 아무 반응이 없었다) | **1차 수정(불완전):** 두 벨을 전부 `<Link to="/notifications">`로 바꿔 클릭 가능하게 만들었다 — 이때 `/notifications`가 렌더링하는 자리표시 화면을 `programId="MY-03"`(잘못됨, 아래 참고)로 임의 지정했다. **2차 수정(불완전):** 코드리뷰에서 (1) UI정의서 2.3/4.1/4.2절이 모바일 알림 진입점을 GNB 벨이 아니라 하단 탭 "마이" 아이콘의 배지로 명시하고 있고(하단 탭을 5개로 유지하기 위해 알림을 별도 탭으로 두지 않는 설계), 모바일 헤더 벨은 애초에 Figma 글리프를 확인한 적 없는 추정 아이콘이었다는 점, (2) `/notifications`가 실제로는 두 개의 다른 화면(MY-03 알림 설정, MY-04 알림 이력)을 가리킬 수 있는데 `NotificationController.getNotifications()`/`NotificationResponse`의 Javadoc이 명시적으로 "MY-04 알림 이력"이라 적어 둔 것을 확인 안 하고 MY-03(알림 설정, `GET/PUT /api/notifications/settings` 전용)으로 잘못 연결했다는 점, 두 가지를 지적받았다. 모바일 헤더 벨은 완전히 제거(`MobileHeader.tsx`)했고, `/notifications` 목적지는 MY-04로 정정(`AppRouter.tsx`)했지만 — 이때는 데스크톱 GNB 벨(Figma 3:2 프레임에 빨간 점 배지와 함께 그려져 있던 것) 자체는 "픽셀 증거가 있다"는 이유로 그대로 유지하며 완결 필요로만 남겨뒀다. **3차 수정(최종):** 지성이 UI정의서 2.3절/4.1절 원문을 직접 대조해, GNB 구성이 "로고 / 주메뉴(지역·단지 검색·지도로 보기·관심목록·알림) / 우측 영역(비로그인: 로그인·회원가입, 로그인: 프로필 아이콘)"으로만 정의돼 있고 벨은 어디에도 언급되지 않는다는 것을 확인해 주었다 — **데스크톱 GNB 벨도 완전히 제거**하고(`Gnb.tsx`, `BellIcon` import까지 함께 삭제), 데스크톱의 유일한 알림 진입점을 중앙 네비 "알림" 텍스트 링크(MY-04) 하나로 확정했다 | 벨을 "클릭 가능하게" 고치는 것과 "이 벨이 애초에 존재해야 하는가"는 서로 다른 질문인데, 1차 수정은 전자만 보고 후자를 검토하지 않았다. 2차 수정은 후자를 모바일에는 적용했지만 데스크톱엔 "Figma 픽셀 증거"를 근거로 예외를 뒀는데, 이 프로젝트 스스로가 명시한 "코드와 문서가 어긋나면 문서가 맞다" 원칙(문서 체계 절 — Figma는 6개 근거 문서에 포함되지 않는다) 아래에서는 그 예외 자체가 근거 부족이었다. Figma 픽셀은 "임의 추측"이었던 모바일 벨보다는 근거가 있었지만, 그 근거가 애초에 6개 근거 문서 밖에 있다는 점은 동일했다 — 이번에 지성이 UI정의서 원문을 직접 확인해 주어 완결 필요 상태에서 확정된 결정으로 종결됐다. **남은 완결 필요는 1건뿐:** 하단 탭 "마이" 아이콘의 미읽음 카운트 배지 자체는 아직 구현하지 않았다 — `GET /api/notifications`가 항목별 `isRead`는 주지만 전용 미읽음 카운트 엔드포인트가 없어(값을 구하려면 전체 목록을 받아 클라이언트에서 세야 하는데 페이지네이션 때문에 부정확하다) 백엔드에 카운트 엔드포인트를 추가하는 논의가 먼저 필요하다 — UI정의서 4.2절은 이 배지를 명시적으로 요구하는데 API-NTF-01(프로그램목록서·설계서)엔 이를 뒷받침할 엔드포인트가 없어, UI정의서 요구사항이 프로그램설계서보다 앞서 있는 별도의 문서 간 갭이다(지성 확인). |

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
| **연령 확인 절차 — P1 코드리뷰 지적으로 2단계에 걸쳐 확정, 공개 문구까지 수정 완료** | "회원가입 시 연령 확인 절차가 없다면 '만 14세 미만은 이용 제한' 문구를 넣고, 나이 검증 로직이 없다는 점은 별도로 백로그에 남길 것"이 전제였다 | `SignupRequest.java`엔 email/password/nickname 3개 필드뿐 나이·생년월일 필드가 없고, 백엔드/프론트 어디에도 연령 검증 로직이 없다 — 즉 만 14세 미만을 포함한 누구나 성인과 동일한 절차로 가입할 수 있다. **1단계(불충분했음):** 6번 항목에 "만 14세 미만 아동을 대상으로 하지 않으며 수집하지 않는다"는 무조건적 단정문을 넣고, 실제로는 보장되지 않는다는 사실은 이 판단표(개발자 전용 문서)에만 적어뒀다. **코드리뷰 재지적:** 개발자 메모로 갭을 인정해 놓고 이용자가 실제로 읽는 공개 문구는 그대로 검증 가능한 거짓 진술로 남겨둔 것 자체가 불충분하다는 지적을 받았다 — 더구나 아동의 개인정보는 「개인정보 보호법」 제22조의2(법정대리인 동의 요건)가 특별히 보호하는 범주라 다른 "구현보다 앞서 나간 문구" 사례들보다 무겁게 다뤄야 했다 | **2단계(공개 문구 수정):** "만 14세 이상을 대상으로 서비스를 제공하나 현재 연령 확인 절차가 없어 가입을 기술적으로 차단하지 못하며, 제22조의2에 따른 법정대리인 동의 절차도 갖추지 못했다는 사실을 그대로 밝히고, 발견 시 지체 없이 삭제하겠다"는 문구로 교체했다 — 목표 대상(14세 이상)과 반응적 구제(발견 즉시 삭제)는 정직하게 약속하되, 사전에 차단한다는 거짓 주장은 하지 않는다. 나이 검증 게이트(자기인증 체크박스든 생년월일 수집이든)를 실제로 추가하는 것도 검토했으나, 회원가입 폼·백엔드 DTO를 함께 바꾸는 새 기능 결정(어떤 방식으로 확인할지부터 지성 판단 필요)이라 정책 문구 수정 세션에서 임의로 만들지 않았다. **완결 필요(우선순위 높음)** — 실사용자 유입 전에 최소한 자기인증 체크박스("만 14세 이상입니다")라도 회원가입 폼에 추가하는 것을 검토하라 — 완전한 방지는 아니지만 "합리적 조치"로 인정받는 업계 관행이고, 지금의 "사전 차단 수단 전무" 상태보다는 낫다. |
| **Refresh Token "블랙리스트" 표현** | "JWT 기반 인증, Refresh Token 블랙리스트 처리(로그아웃/재발급 시)"라는 문구를 그대로 쓰라는 전제였다 | 이 세션에서 `RefreshTokenRepository`의 정확한 폐기 구현(별도 블랙리스트 테이블인지, 해당 행 삭제/플래그인지)까지 코드로 재확인하지는 않았다 — CLAUDE.md SVC-AUTH-01/USER-01 절에 이미 기록된 "로그아웃 시 소유자 검증 후 처리", "탈퇴 시 `revokeAllByUserId()`" 정도의 사실만 근거로 삼았다 | "블랙리스트"라는 특정 구현 방식을 단정하지 않고 "기존 Refresh Token을 즉시 폐기하여 재사용을 방지한다"는 더 중립적인 문장으로 완화했다 — 실제 구현과 다른 특정 메커니즘(예: 별도 블랙리스트 테이블)을 확정적으로 진술해 나중에 틀린 게 되는 리스크를 피했다. **후속 정정(7라운드, P2 코드리뷰 지적) — "재발급 시에도 폐기"는 실제로 틀린 진술이었다.** 위에서 "재발급 시에도 폐기"라고 문구를 완화만 하고 실제 `AuthService.refreshAccessToken()`을 재확인하지 않았는데, 이번 라운드에서 직접 읽어보니 이 메서드는 제출된 Refresh Token을 검증하고 새 Access Token만 발급할 뿐(`jwtTokenProvider.createAccessToken()`) `stored.revoke()`를 호출하지도, 새 Refresh Token을 발급하지도 않는다 — `revoke()`를 실제로 호출하는 곳은 `logout()`뿐이다(103~130행 확인). 즉 유효한 Refresh Token은 로그아웃하기 전까지 만료 시각까지 몇 번이고 재사용해 Access Token을 계속 재발급받을 수 있다 — "재발급 시 폐기"는 모든 재발급 호출에 대해 거짓이었다. 8번(안전성 확보조치) 섹션 문구를 "로그아웃 시에만 폐기, 재발급 자체는 교체·폐기 없이 재사용 가능"으로 좁혀 실제 동작과 일치시켰다. **완결 필요(Refresh Token 로테이션 미도입)** — 재발급마다 새 Refresh Token을 발급하고 기존 것을 폐기하는 로테이션(탈취된 토큰의 장기 재사용을 막는 표준적 방어)이 없다는 뜻이다. 이는 `AuthController`/`AuthService`의 인증 흐름 자체를 바꾸는 백엔드 기능 결정이라 정책 문구 수정 세션에서 임의로 구현하지 않았다 — 로테이션을 도입하면 이 섹션 문구를 다시 "재발급 시에도 폐기"로 되돌릴 수 있다. |
| **체크박스 링크가 동의 상태를 토글하는 문제** | "약관 동의 자체는 클라이언트 전용 게이트이므로 정책 페이지 열람이 체크박스 상태에 영향을 주면 안 된다"는 주의사항이 있었다 | `Checkbox.tsx`가 시각적 박스+라벨 전체를 하나의 `<label>`로 감싸는 구조라(표준 커스텀 체크박스 패턴), 라벨 안에 `<Link>`를 그대로 넣으면 그 클릭이 페이지 이동과 체크박스 토글을 동시에 발생시킨다 | `SignupPage.tsx`의 "개인정보처리방침" `<Link>`에 `onClick={(e) => e.stopPropagation()}`을 걸어 부모 `<label>`로의 클릭 버블링만 막았다(페이지 이동 자체는 `Link`의 기본 동작이라 막히지 않는다) — Playwright로 "정책 페이지 방문 후 뒤로가기해도 체크박스가 여전히 미체크 상태"를 검증했다. |
| **보유기간 섹션이 존재하지 않는 탈퇴 라이프사이클을 약속함 — P1 코드리뷰 지적, 2라운드에 걸쳐 수정** | 초안은 "탈퇴 시 계정 비활성화, 7일 유예기간 후 개인정보 완전 파기, 유예기간 중 재로그인으로 탈퇴 철회 가능"이라고 적었다 — 브리핑이 준 문구를 그대로 옮긴 것으로, 실제 구현을 코드로 재확인하지 않고 작성했다 | **1라운드:** `UserService.withdraw()`를 직접 읽어 확인한 결과 `user.withdraw()`(status=WITHDRAWN, `withdrawnAt` 기록)와 `refreshTokenRepository.revokeAllByUserId()`만 호출할 뿐, 유예기간이나 예약 파기를 다루는 코드가 전혀 없다. `AuthService.login()`은 `user.getStatus() != ACTIVE`면 무조건 `AccountNotActiveException`을 던진다 — 즉 **탈퇴한 계정은 재로그인 자체가 막혀 있어 "재로그인으로 철회"가 코드상 불가능한 동작을 약속하고 있었다.** 저장소 전체에서 "purge"/"reactivat"/예약 파기 배치를 검색해도 아무것도 나오지 않아 "7일 후 파기"도 사실이 아니었다(WITHDRAWN 상태로 무기한 보관) — 실제 코드 동작(즉시 비활성화, 인증 토큰 즉시 폐기, 재로그인으로 복구 불가)만 기술하도록 고치고, 7번 항목(파기 절차)의 "별도의 DB로 옮겨져 보관"이라는 마찬가지로 코드에 없는 구체적 기술 주장도 함께 제거했다(국내 템플릿에 관행적으로 들어가는 문구이지만 이 서비스엔 해당 메커니즘이 없다). **2라운드(지성 지적):** 1라운드가 남긴 "지체 없이 파기하는 것을 원칙으로 합니다"라는 문구 자체도 여전히 실제와 다를 수 있다는 지적을 받았다 — 이 문장은 법 조문(원칙론)을 인용한 것이지 탈퇴 계정의 개인정보를 실제로 자동 삭제하는 배치가 있다는 뜻이 아닌데, "계정이 즉시 비활성화된다"는 사실과 "개인정보가 실제로 파기된다"는 주장을 문서가 뭉뚱그리면 방침과 실제 처리가 다르다는 지적을 받을 여지가 남는다는 게 핵심이었다 — 개인정보처리방침은 법 원칙을 인용하는 문서가 아니라 서비스가 실제로 무엇을 하는지 고지하는 문서이기 때문이다 | **2라운드 반영:** "계정 비활성화(자동, 즉시)"와 "개인정보의 완전한 삭제(아직 미자동화)"를 문서에서 명확히 분리하는 문단을 추가하고, 즉시 삭제를 원하는 이용자에게 보호책임자 앞 수동 요청 경로를 제공했다(자동화 여부와 무관하게 삭제 요청권 자체는 지금도 충족되도록) — 존재하지 않는 자동 배치를 있는 것처럼 쓰지 않으면서도, 이용자에게 아무 수단도 없는 상태로 남겨두지 않는 절충이다. **완결 필요(우선순위 높음, 이 세션 범위 밖 — 지성이 백엔드 작업 우선순위를 높게 잡을 것을 명시적으로 요청함)** — 탈퇴 계정을 일정 기간 후 자동으로 파기하는 배치(또는 진짜 탈퇴 철회 플로우)가 실제로 만들어지기 전까지는 이 완화된 문구가 유지돼야 한다. 사용자 데이터에 대한 예약 작업·하드 삭제를 새로 만드는 결정이라 프론트엔드 전용 세션에서 임의로 구현하지 않았다 — 다음에 백엔드 작업을 하는 세션이나 지성이 직접 판단해야 한다(재로그인으로 철회 가능하게 할지, 별도 마이페이지 버튼으로 철회를 만들지, 파기 배치를 얼마나 빨리 도입할지 등). 이 항목은 단순 버그가 아니라 "방침 문장이 법적으로 성립하려면 반드시 구현이 뒤따라야 하는 항목"이라는 점에 유의하라. **운영 함의(지성 지적) — "연락하면 삭제해준다"는 문서상 약속이 아니라 실제 프로세스다.** 방침이 "즉시 삭제를 원하면 보호책임자에게 요청하라"고 명시한 이상, 실제로 그 요청이 오면 지성이 수동으로 처리할 수 있어야 한다(현재는 1인 운영 체제라 본인이 곧 보호책임자라 문제 없음). **또한 나중에 자동 파기 배치를 실제로 구현하면 "수동 요청 시 삭제"와 "자동 파기"가 동시에 존재하게 되므로, 그 시점에 이 섹션 문구를 다시 한 번 다듬어야 한다** — 지금 문구는 "자동화가 전혀 없다"는 전제로 쓰여 있어, 배치가 생기면 그 전제 자체가 깨진다(예: 수동 요청은 즉시 처리, 미요청 시에도 N일 후 자동 파기하는 이중 트랙으로 문구를 다시 나눠야 할 가능성이 높다). |
| **6번(정보주체 권리) 섹션이 존재하지 않는 마이페이지 자기서비스 기능을 안내함 — P1 코드리뷰 지적, 수정 완료** | 초안은 "개인정보 열람·정정 요구: 마이페이지의 '회원정보 수정' 화면에서 직접 열람·정정할 수 있습니다", "개인정보 삭제 요구: 마이페이지의 '회원 탈퇴'를 통해 요청할 수 있습니다"라고 적었다 — 마이페이지가 실제로 그 기능을 제공한다고 가정하고 작성했다 | `AppRouter.tsx`를 확인한 결과 `/my`는 여전히 `PlaceholderPage`(programId `MY-01`, "추후 구현 예정입니다")로만 연결돼 있고, `features/user/api.ts`엔 `getMe()`만 있을 뿐 `updateUser`/`withdraw`를 호출하는 클라이언트 코드 자체가 없다(백엔드 `PUT`/`DELETE /api/users/me`는 이미 있지만 프론트가 아직 붙이지 않았다) — 즉 이 절차를 그대로 따라가면 이용자는 "추후 구현 예정" 화면만 보고 아무것도 할 수 없다 | 2번(보유기간) 섹션에서 이미 확립한 원칙(존재하지 않는 자동화를 약속하는 대신 실제로 작동하는 채널로 안내)을 그대로 적용했다 — 마이페이지 언급을 없애고 "마이페이지 자기서비스 기능은 준비 중이며, 그 전까지는 10번(보호책임자) 이메일로 요청하면 지체 없이 처리한다"로 바꿨다. **완결 필요** — MY-01(마이페이지) 프론트 구현은 이 세션 범위를 벗어나는 기능 개발이라 하지 않았지만, 백엔드 엔드포인트가 이미 있어 신규 설계가 아니라 기존 UI정의서 화면을 붙이는 작업에 가깝다 — 다음 프론트엔드 세션에서 우선순위 높게 다루면 이 섹션 문구도 "마이페이지에서 직접"으로 되돌릴 수 있다. |
| **3번(처리 항목) 표가 비로그인 방문자에게도 생성되는 영속 세션 식별자를 "로그인 후"로만 서술 — P1 코드리뷰 지적, 수정 완료** | 표는 "최근 조회 이력"을 "관심 매물/관심 지역 등록 정보, 알림 조건 설정값"과 한 행으로 묶어 "로그인 후 개인화 기능 이용 시"에만 생성된다고 적었다 | `lib/sessionId.ts`의 `getOrCreateSessionId()`가 `crypto.randomUUID()`로 만든 값을 `localStorage`(`homesense.sessionId`)에 영구 저장하고, `features/recentview/api.ts`의 `getRecentViews()`는 로그인 여부와 무관하게 이 값을 `X-Session-Id` 헤더로 **항상** 전송하며, `RecentViews.tsx`는 이 호출을 홈 화면 마운트 시 **인증 게이트 없이** 실행한다 — 즉 비로그인 방문자도 홈 화면에 들어오는 즉시 이 식별자가 생성·전송되고, 백엔드 `RecentViewController`/`RecentViewService.getRecent()`가 이를 비회원 조회 이력의 조회 주체로 그대로 사용한다(SVC-RCV-01, CLAUDE.md 해당 절 참고). "최근 조회 이력"을 로그인 전용 항목과 묶은 건 부정확했다 | "최근 조회 이력"을 로그인 필요 항목에서 분리해 별도 행("기기 식별 정보(로그인 불필요)")으로 신설 — 식별자 자체(무작위 생성값), 목적(최근 조회 이력을 기기 단위로 유지), 실제 수집 시점(홈 화면 등에서 단지 정보를 조회하는 즉시, 로그인 여부 무관)을 명시했다. 9번(자동 수집 장치) 섹션도 함께 갱신해 "쿠키를 쓰지 않는다"는 기존 문장 아래 로컬스토리지에 저장되는 두 항목(인증 토큰/세션 식별자)을 표로 나열했다. **초안에서 걸러낸 사실 오류(자체 발견, 리뷰 지적 아님):** 처음에는 "로그인 시 그 계정의 이력으로 전환되어 이어서 조회된다"고 쓰려 했으나, `RecentViewService.getRecent()`/`record()`를 다시 읽어보니 `userId`가 있으면 `sessionId` 이력을 전혀 참조하지 않고(둘 중 하나로만 조회·기록, 병합 로직 없음) 로그인 후에는 완전히 새로운 빈 이력이 시작된다는 것을 확인해 문구를 "서로 연결되지 않습니다"로 정정한 뒤 커밋했다 — 검증 없이 그대로 나갔다면 이번 라운드 자체가 새로운 "구현보다 앞선 문구" 사례가 될 뻔했다. |

**완결 필요(신규)** — 이용약관(ToS) 페이지가 아직 없다. `SignupPage.tsx`의 "이용약관" 텍스트는
SCR-AUTH-02 구현 시점의 기존 결정대로 여전히 순수 `<span>`이고(클릭 불가), `Footer.tsx`의 "이용약관"도
마찬가지다 — 이번에 "개인정보처리방침"만 실제 페이지로 연결했다. 이용약관도 동일 패턴(`MainLayout` +
섹션 데이터 배열 + 목차)으로 별도 작업이 필요하다 — 근거 법령·필수 조항 구성이 개인정보처리방침과
다르므로(전자상거래법/약관규제법 관점) 이 파일들을 그대로 복사해 쓰지 말고 새로 검토해서 작성하라.

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

# DB — 테이블 정의서 8장 DDL 원문을 그대로 실행
mysql -u root homesense < schema_all.sql
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
