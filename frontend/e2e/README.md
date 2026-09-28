# HomeSense 검증 스크립트 (frontend/e2e/)

AUTH-02 회원가입 / SCR-LEGAL-01 개인정보처리방침 / AUTH-03 비밀번호 찾기 / SCR-SRCH-01 검색결과
목록 / HOME-01 카드 회귀 방지를 검증하는 Playwright 스크립트 30개(+스크린샷 전용 2개). 원래 저장소
밖 `C:\Users\super\homesense-e2e-scripts\`에 있었으나(캐시 정리 시 유실 위험, CLAUDE.md SCR-LEGAL-01
백로그가 이 위험을 지적했다) 저장소 안으로 옮겨 커밋했다.

**CI에는 편입하지 않는다** — 로컬 실행 가능한 상태로만 유지한다. `@playwright/test` 프레임워크로의
전환(CLAUDE.md SCR-LEGAL-01 절이 남긴 완결 필요 항목)도 아직 하지 않았다 — 아래 "알려진 한계" 참고.

## 실행 전제
- Node 18+ (`node -v`), 이 디렉터리에서 `npm install` 1회 (playwright 1.63.0 고정). 브라우저가 이미
  `%LOCALAPPDATA%\ms-playwright`에 있으면 그걸 그대로 쓰고, 없으면 `npx playwright install chromium`.
- 프런트 dev 서버가 떠 있어야 한다: `frontend/homesense`에서 `npm run dev`(기본 http://localhost:5173).
  다른 포트로 띄웠다면 `BASE=http://localhost:5183` 같은 식으로 환경변수를 지정한다.
- 백엔드 필요 여부는 스크립트마다 다르다:
  - AUTH-02/SCR-LEGAL-01 스크립트 대부분과 `auth03-figma-parity-check`/`auth03-transient-token-error-check`/
    `auth03-form-cooldown-check`는 API를 `page.route()`로 목킹해 백엔드가 필요 없다.
  - `auth03-password-reset-check`는 1단계 발송·쿨다운·무효 토큰 검증에 실제 백엔드(`cd ../../backend &&
    ./gradlew bootRun`, 8080)+Redis(6379)가 필요하다 — 로컬에 SES 자격 증명이 없어 실제 발송 메일에서
    원문 토큰을 받을 방법이 없으므로 2단계 "유효한 토큰" 폼만 목킹한다(CLAUDE.md SCR-AUTH-03 절 참고).
  - `srch01-*` 7개(basic/mobile/favorite-and-desktop-back/slider-boundary-and-wolse/keyboard-and-error/
    tablet/draft-carryover)는 전부 실제 백엔드(8080)가 필요하다(Redis 불필요) — MariaDB에 `complex`/`trade` 실데이터가
    있어야 의미 있는 검증이 된다(로컬 개발 DB 기준으로 작성됨, 특정 지역코드에 데이터가 없으면 일부
    단정문이 SKIP 로그만 남기고 통과 처리되도록 방어돼 있다).
  - `srch01-screenshots`는 테스트가 아니라 스크린샷 저장 스크립트다(단정문 없음).
- 스크린샷을 쓰는 스크립트는 `OUT_DIR`(기본 `./out`, `.gitignore`로 제외)에 저장한다.

## 실행
    npm install
    node run-all.mjs                        # run-all.mjs 목록 25개(백엔드+Redis 필요한 것 포함)
    node run-all.mjs signup-age-check        # 일부만 지정
    node signup-age-check.mjs                # 단독 실행(스크립트별 PASS/FAIL 출력)
    BASE=http://localhost:5183 node srch01-basic-check.mjs   # dev 서버가 기본 포트가 아닐 때
    node auth03-figma-parity-check.mjs       # run-all.mjs 목록엔 없음(아래 참고), 백엔드 불필요

## 스크립트
| 스크립트 | 검증 대상 |
|---|---|
| signup-age-check | 연령 체크박스 게이트, 요청 바디 키(ageConfirmed 포함 4개, true) — 3 뷰포트 |
| checkbox-focus-check | 공유 Checkbox 키보드 포커스 링 — 3 뷰포트 |
| signup-age-server-error-check | 서버 필드 오류 중 미매핑 키의 formError 표시·중복 표시 없음 — 3 뷰포트 |
| signup-test / signup-functional | 회원가입 폼 기본 동작·400 필드 오류 매핑 |
| dup-409-check / server-email-error-clear-test / password-nickname-server-error-clear-test | 서버 에러 표시 우선순위와 값 변경 시 초기화 |
| nickname-trim-test / border-settle-check | 닉네임 trim, 409 후 테두리 상태 |
| signup-policy-newtab-check | 방침 링크 새 탭·폼 상태 보존·체크박스 미토글 |
| privacy-age-fix-check | 방침 6항 문구(현행: 서버 거부·저장 안 함·한계 고지) |
| privacy-withdrawal-purge-check | 방침 2항(탈퇴 후 N일 보관 → 자동 파기)·6항 문구, N이 백엔드 `application.properties`의 grace-days 기본값과 같은지, "직접 철회" 허위 안내 부재 — 데스크톱/모바일 |
| privacy-amendments-check | 방침 13항 시행일/개정 이력(v1.0·v1.1·v1.2 행, v1.2 기준) |
| auth03-password-reset-check | AUTH-03: 이메일 형식 게이트·발송 완료 화면·독립된 두 번째 요청으로 재현하는 실제 60초 쿨다운(429, role=alert)·무효 토큰 화면과 재요청·유효 토큰 폼(비밀번호 정책 체크리스트·확인값 불일치)·성공 후 로그인 이동·모바일 가로 스크롤 없음 — 실제 백엔드+Redis 필요. 2026-09-27, 저장소 편입 후 실 백엔드로 처음 재실행하며 "재설정 다시 요청" 버튼 문구가 Figma 대조 재작업 때 "재설정 링크 다시 요청"으로 바뀐 채 스크립트만 옛 문구로 남아 있던 걸 발견해 고쳤다(23/23) |
| auth03-figma-parity-check | AUTH-03: 실제 Figma AUTH-03 프레임과 대조해 찾은 화면 전용 요소 검증 — 진행 스테퍼·아이콘 배지·뒤로가기 링크 색상·이메일 표시 칩·오라클 방지 안내문·60초 재발송 카운트다운·링크 만료 배지+30분 경고박스+동적 서버 메시지 공존. 백엔드 불필요, `run-all.mjs` 목록에는 없음(단독 실행) |
| auth03-transient-token-error-check | AUTH-03: `validate-token` 실패 원인별 분기(진짜 400 vs 전송 계층 오류) — 9/9, 백엔드 불필요 |
| auth03-form-cooldown-check | AUTH-03: 이메일 입력 폼의 쿨다운 클라이언트 락 — 9/9, 백엔드 불필요 |
| srch01-basic-check | SCR-SRCH-01: regionCode/keyword 검색 렌더링, 조건 없음 시 API 미호출, URL로 복원한 검색어도 요청 전에 서버(SearchKeywordPolicy, 코드포인트 기준 2~50자)와 같은 규칙으로 검사 — 1글자·이모지 1개·51자면 API를 부르지 않고 입력창 아래와 본문에 안내(일반 오류 화면 아님), 50자 경계와 앞뒤 공백 trim은 검색 실행, 공백만이면 조건 없음, regionCode와 함께면 regionCode 우선. 홈 히어로에서 1글자 검색 시 결과 화면이 요청 없이 안내하고 검색 기록도 남기지 않음, 재검색 바에서 1글자·이모지 1개 제출 시 새 검색 요청·검색 기록 요청 0건, URL 그대로, 안내 표시, 입력을 고치면 안내가 사라짐(이모지 제출은 수정 전 코드에서 실패). 매매↔전세 전환 무오류+URL 반영, 정렬 변경 시 검색 로그 미호출, 자유 텍스트 재검색 시 로그 정확히 1회(payload 키워드 일치), 데스크톱 페이지 이동 시 목록 교체(누적 아님). 수정 전 코드에서 16건 실패 확인 — 37/37 |
| srch01-mobile-check | SCR-SRCH-01(392px): 필터 버튼→바텀시트 열림, Esc·백드롭 닫힘, 스크롤 잠금, IntersectionObserver 무한스크롤 30+ 누적, 카드 클릭→DTL-01→뒤로가기 시 누적 목록·스크롤 위치 복원 — 10/10. StrictMode 캐시 오염 버그와 스크롤 복원 값 오류 버그를 이 스크립트가 실측으로 잡았다(CLAUDE.md SCR-SRCH-01 절). **테스트 함정**: `.first()`/`.last()`로 카드를 클릭하면 Playwright가 자동 스크롤해 "그 자리에서 클릭"을 재현 못 한다 — 뷰포트 안에 실제로 보이는 카드를 브라우저 컨텍스트에서 직접 찾아 순수 DOM `.click()`으로 눌러야 한다 |
| srch01-favorite-and-desktop-back-check | SCR-SRCH-01: 비로그인 하트 클릭 시 `/login` 이동, 데스크톱 2페이지 이동 후 카드 클릭→뒤로가기 시 같은 페이지·같은 목록 유지, HOME-01 히어로 검색 회귀 — 7/7 |
| srch01-slider-boundary-and-wolse-check | SCR-SRCH-01: 슬라이더 하한 경계(상한만 옮겨도 하한 파라미터가 요청에 없음을 네트워크로 확인, 면적/금액/건축년도 3종 모두), 1990년 이전 준공 단지가 기본 상태에 포함됨(하한 미적용 방증), 월세 카드가 "보증금 X · 월세 Y만원"으로 표시되고 ㎡당 가격이 빠짐, 슬라이더 라벨이 "보증금"으로 바뀜 — 9/9 |
| srch01-keyboard-and-error-check | SCR-SRCH-01: 자동완성 키보드 네비게이션(↑/↓/Enter/Esc, 미선택 Enter 시 자유 텍스트 폴백, API 실패해도 자유 텍스트 검색 가능), 슬라이더 방향키 조작+숫자 입력 동기화, 바텀시트 포커스 트랩(Tab 30회 반복해도 탈출 없음), 에러 배너(서버 메시지·"다시 시도" 버튼·카드 없음)→재시도가 검색을 한 번 더 요청해 결과 렌더링·배너 사라짐(대기 결과를 단언해 실패 시 시간 초과 대신 FAIL로 드러남), 잘못된 regionCode 서버 메시지 표시 — 20/20 |
| srch01-tablet-check | SCR-SRCH-01(768px): 데스크톱과 같은 사이드바 레이아웃(모바일 바텀시트 버튼 아님), 필터 적용·페이지네이션 왕복 — 6/6 |
| srch01-draft-carryover-check | SCR-SRCH-01: 필터 패널에서 거래유형 등을 바꾸고 "필터 적용"을 누르지 않은 채 재검색바(키워드 제출/지역 자동완성 선택)로 검색해도 그 draft 값이 반영되는지 — 실사용자 버그 리포트("거래유형 선택 후 재검색하면 반영 안 됨")로 발견한 회귀를 잡는다. `handleSubmitKeyword`/`handleSelectRegion`이 `executeSearch`의 base로 `filters`(URL 커밋값)가 아니라 `draft`(패널의 현재 선택)를 넘기도록 고친 수정을 검증(CLAUDE.md SCR-SRCH-01 절 참고) — 6/6 |
| srch01-bottomsheet-focus-check | SCR-SRCH-01 모바일(392px) 필터 바텀시트: (1) 시트 안에서 라디오(방향키)·슬라이더(방향키)·숫자 입력으로 값을 바꿔도 키보드 포커스가 그 컨트롤에 머묾 — 수정 전엔 호출부의 인라인 `onClose`가 매 렌더 새 함수라 BottomSheet effect가 cleanup(트리거로 포커스)→재초기화(닫기 버튼으로 포커스)를 반복했다(`useEffectEvent`로 수정), (2) 시트의 거래유형 라디오가 정확히 하나 체크됨 — 수정 전엔 숨겨진 데스크톱 사이드바 FilterPanel과 라디오 `name`이 같아 체크가 사이드바로 넘어가 시트에는 열었을 때부터 아무것도 선택되지 않은 것처럼 보였다(`useId`로 인스턴스별 name), (3) Esc 닫힘·트리거 포커스 복귀·스크롤 잠금 해제, 시트에서 고른 월세가 URL에 반영, (4) 시트·숨겨진 사이드바가 함께 렌더돼도 문서 전체 중복 id 0개·시트 안 id 참조(`for`/`aria-*`)가 모두 존재하는 요소를 가리킴·두 패널의 라디오 그룹 이름이 다름, 시트 안 라벨("연립다세대"·"월세"·"매매") 클릭 시 시트 쪽 컨트롤이 바뀜. 수정 전 코드에서 7건 실패 확인 — 23/23, 실 백엔드 필요 |
| uic03-autocomplete-race-check | UIC-03 SearchBar 자동완성 경합: `page.route`로 "수원" 응답을 게이트에 붙잡아 순서를 확정적으로 재현한다(고정 지연으로는 디바운스 300ms 창에 맞추기 어렵다). (1) "안성"으로 바꾼 직후 수원 응답을 풀어도 입력이 "안성"인 동안 수원 후보가 한 번도 보이지 않고(MutationObserver로 스쳐 간 오염까지 기록), 최종 목록은 안성 후보만 남으며 비어 있지 않음, (2) 요청이 나간 뒤 blur하면 지연 응답이 도착해도 목록이 닫혀 있음, (3) 지연 없는 입력→마우스 클릭 선택 정상 경로 회귀. 취소 여부는 `route.fulfill` 성패가 아니라 `requestfailed` 이벤트로 판정한다(Playwright는 이미 취소된 요청에 fulfill해도 예외를 던지지 않는다). 수정 전 코드에서 4건 실패함을 확인 — 12/12, 실 백엔드 필요 |
| srch01-screenshots | SCR-SRCH-01: 데스크톱/태블릿/모바일 3개 뷰포트 스크린샷을 `./out/`에 저장(테스트 아님, 단정문 없음) — Figma 육안 대조용 |
| home01-card-check | HOME-01: SRCH-01이 공유 컴포넌트 `ComplexCard`의 `list` variant를 재작업하면서 `grid` variant(HOME-01 인기 단지)에 실수로 영향을 주지 않았는지 1280/768/392 세 뷰포트에서 확인 — 카드에 "건축"(년도)·"만원/㎡"(평단가) 문구가 없음(list 전용 항목 미유입), 하트 버튼이 절대 위치 오버레이 유지, 비로그인 하트 클릭 시 `/login` 이동 — 18/18 |
| home01-card-screenshots | HOME-01: 데스크톱/태블릿/모바일 3개 뷰포트 스크린샷을 `./out/`에 저장(테스트 아님) — Figma 4:1232/24:7860/24:7370 육안 대조용 |

## 알려진 한계
- `signup-test`/`signup-functional`/`dup-409-check`/`server-email-error-clear-test`/
  `password-nickname-server-error-clear-test`/`nickname-trim-test`/`border-settle-check` 7개는 기대값과
  실제값이 달라도 종료코드에 반영하지 않고 값만 출력한다 — 종료코드 0이 통과를 뜻하지 않으므로 이
  스크립트들의 "통과"는 출력을 사람이 읽어야 확인된다. `@playwright/test`(`expect`) 기반으로 전환하면
  이 문제가 해소된다(아직 미착수).
- `privacy-retention-fix-check`(이 디렉터리에 없음, 옛 npx 캐시에만 있었다)는 "탈퇴 계정 자동 파기
  없음"을 전제로 한 옛 검증이라 BAT-USR-01 이후 무효다 — `privacy-withdrawal-purge-check`를 쓴다.
- `privacy-withdrawal-purge-check`는 `REPO`(기본 `C:/Users/super/IdeaProjects/HomeSense`) 아래
  `backend/src/main/resources/application.properties`를 읽는다 — 저장소를 다른 경로에 clone했다면
  `REPO=/path/to/HomeSense`로 지정한다.
- `auth03-figma-parity-check`의 카운트다운 테스트는 `page.clock.runFor(1000)`을 61번 반복 호출한다 —
  한 번에 `runFor(61000)`을 호출하면 재귀 `setTimeout` 체인 중 하나만 발화하고 나머지는 건너뛴다
  (Playwright clock과 React 이펙트 스케줄링의 상호작용, 실측 확인) — 같은 패턴의 카운트다운을 또
  테스트할 때는 `runFor`를 1초 단위로 반복 호출하라.
- `srch01-*` 스크립트는 로컬 개발 DB(수원시 장안구=4111100000, 경기도=4100000000 등 특정
  regionCode)에 이미 적재된 실거래 데이터를 전제한다 — 다른 환경에서 데이터가 없으면 일부 단정문이
  `SKIP` 로그만 남기고 종료코드에 영향을 주지 않도록 방어돼 있지만, 전체 커버리지를 보려면 해당
  지역에 실데이터가 있는 DB로 돌려야 한다.
