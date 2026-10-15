// SCR-MAP-01 지도 기반 매물 조회 — 모킹 전용(백엔드 불필요). dev 서버(BASE)만 있으면 된다.
// 카카오맵 SDK 요청은 가짜 SDK(fakeKakaoSdk.js)로 바꾸고(시나리오 5는 차단), /api/** 는 전부 page.route로 응답한다.
// 전제: frontend/homesense/.env.local에 VITE_KAKAO_JS_KEY가 있어야 한다(값은 무엇이든 — 로더가 키 없이는 스크립트를 넣지 않는다).
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { BASE } from './base.mjs';
import { errBody, okBody } from './mockApi.mjs';

const FAKE_SDK = readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'fakeKakaoSdk.js'), 'utf8');
const ACCESS_KEY = 'homesense.accessToken';

let pass = 0;
let fail = 0;
function ok(name, cond) {
  if (cond) {
    pass++;
    console.log(`PASS ${name}`);
  } else {
    fail++;
    console.log(`FAIL ${name}`);
  }
}

// 기본 위치(서울시청 부근, 레벨 6) 주변에 6개. 6번은 정확 매칭 + 근사 좌표, 5번은 유사 매칭.
const CENTER = { lat: 37.5666, lng: 126.9784 };
function item(id, overrides = {}) {
  return {
    complexId: id,
    complexName: `지도단지${id}`,
    sido: '서울특별시',
    sigungu: '중구',
    dongRi: '태평로1가',
    representativeHousingType: 'APT',
    representativeDealCategory: 'SALE',
    representativeDealDate: `2026-09-${String(10 + id).padStart(2, '0')}`,
    representativeAmount: 30000 + id * 5000,
    representativeArea: 84.9,
    matchMethod: 'EXACT',
    precisionBadge: 'PRECISE',
    floor: id + 2,
    latitude: CENTER.lat + (id - 3) * 0.004,
    longitude: CENTER.lng + (id - 3) * 0.006,
    locationPrecision: 'PRECISE',
    ...overrides,
  };
}
const ITEMS = [
  item(1),
  item(2),
  item(3),
  item(4),
  item(5, { matchMethod: 'SIMILAR', precisionBadge: 'APPROXIMATE' }),
  item(6, { locationPrecision: 'APPROXIMATE', precisionBadge: 'APPROXIMATE' }),
];

/**
 * 페이지 하나에 SDK·API 목업을 건다. state를 바꾸면 이후 응답이 바뀐다.
 * state.mapStatus: 200 | 500, state.truncated, state.sdk: 'fake' | 'block', state.loggedIn
 */
async function setup(page, state) {
  state.mapRequests = [];
  state.favoritePosts = [];
  await page.route('https://dapi.kakao.com/**', (route) =>
    state.sdk === 'block' ? route.abort() : route.fulfill({ status: 200, contentType: 'application/javascript', body: FAKE_SDK }),
  );
  await page.route('**/api/**', async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname;
    // 지연 응답은 그사이 앱이 요청을 끊으면 fulfill이 실패한다 — 끊긴 요청이라 무시한다.
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body }).catch(() => {});
    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    if (path === '/api/complexes/map') {
      state.mapRequests.push(url.searchParams);
      if (state.mapStatus === 500) return json(500, errBody('INTERNAL_SERVER_ERROR', '지도 조회 중 서버 오류가 발생했습니다'));
      // 단지 하나 주변의 좁은 범위(선택 단지 조회, features/map/selection.ts) — 위도 폭 0.01 미만이면 nearbyItems를 준다.
      // nearbyItems가 함수면 요청 파라미터(필터)로 고른다. nearbyDelayMs만큼 늦게 답한다.
      const [swLat, , neLat] = (url.searchParams.get('bounds') ?? '').split(',').map(Number);
      if (state.nearbyItems && neLat - swLat < 0.01) {
        state.nearbyRequests = (state.nearbyRequests ?? 0) + 1;
        state.nearbyParams = [...(state.nearbyParams ?? []), url.searchParams];
        state.events = [...(state.events ?? []), 'nearby'];
        const items = typeof state.nearbyItems === 'function' ? state.nearbyItems(url.searchParams) : state.nearbyItems;
        if (state.nearbyDelayMs) await sleep(state.nearbyDelayMs);
        return json(200, okBody({ items, truncated: false, limit: 500 }));
      }
      // state.items가 함수면 요청 파라미터(필터)로 고른다.
      const items = typeof state.items === 'function' ? state.items(url.searchParams) : (state.items ?? ITEMS);
      return json(200, okBody({ items, truncated: !!state.truncated, limit: 500 }));
    }
    if (path === '/api/auth/login') {
      state.loggedIn = true;
      return json(200, okBody({ accessToken: 'A1', refreshToken: 'R1', expiresIn: 1800 }));
    }
    if (path === '/api/users/me') {
      return state.loggedIn
        ? json(200, okBody({ userId: 1, email: 'user@test.com', nickname: '지도테스터', createdAt: '2026-09-01T00:00:00' }))
        : json(401, errBody('UNAUTHORIZED', '인증이 필요합니다'));
    }
    if (path === '/api/favorites/properties' && req.method() === 'GET') return json(200, okBody([]));
    if (path === '/api/favorites/properties' && req.method() === 'POST') {
      state.favoritePosts.push(JSON.parse(req.postData() ?? '{}'));
      return json(200, okBody({ favoritePropertyId: 77, complexId: 2 }));
    }
    if (path === '/api/complexes/search') {
      return json(200, okBody([ITEMS[0]], { page: 0, size: 20, totalElements: 1, totalPages: 1 }));
    }
    const detailMatch = path.match(/^\/api\/complexes\/(\d+)$/);
    if (detailMatch) {
      state.detailRequests = [...(state.detailRequests ?? []), detailMatch[1]];
      state.events = [...(state.events ?? []), `detail:${detailMatch[1]}`];
      if (state.detailDelayMs) await sleep(state.detailDelayMs);
    }
    if (detailMatch && state.details?.[detailMatch[1]]) return json(200, okBody(state.details[detailMatch[1]]));
    if (detailMatch) return json(404, errBody('COMPLEX_NOT_FOUND', '존재하지 않는 단지입니다'));
    if (path.startsWith('/api/trades')) return json(200, okBody([]));
    return json(404, errBody('NOT_FOUND', 'mock 없음'));
  });
}

async function newPage(browser, width, height) {
  const context = await browser.newContext({ viewport: { width, height } });
  const page = await context.newPage();
  return { context, page };
}

const rows = (page) => page.locator('ul[aria-label="지도 단지 목록"] > li');
const pin = (page, id) => page.locator(`[data-testid="map-canvas"] button[data-complex-id="${id}"]`);
const popup = (page) => page.getByTestId('map-popup');

const browser = await chromium.launch();

// 1·2) 데스크톱: 목록 렌더링·건수, 항목 클릭 → 팝업, Esc → 핀으로 포커스, 근사 핀·문구, 상세보기 → 뒤로 복원
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  ok('1: 목록 6행', (await rows(page).count()) === 6);
  const header = page.locator('aside [aria-live="polite"]');
  ok('1: 목록 헤더 건수(aria-live) "지도 영역 · 6건"', (await header.innerText()).replace(/\s+/g, ' ').includes('지도 영역 · 6건'));
  ok('1: 필터 막대 건수 6건', (await page.locator('main p.ml-auto').innerText()).replace(/\s/g, '') === '6건');
  const firstReq = state.mapRequests.at(-1);
  ok('1: 지도 API에 bounds가 실리고 지역·정렬은 없다', !!firstReq?.get('bounds') && !firstReq.has('regionCode') && !firstReq.has('sort'));
  ok('1: 핀 6개(button, aria-label에 단지명·가격)', (await pin(page, 1).count()) === 1 && (await pin(page, 1).getAttribute('aria-label')).startsWith('지도단지1, '));
  ok('1: 근사 핀은 aria-label에 근사', (await pin(page, 6).getAttribute('aria-label')).endsWith(', 근사'));
  const pinBox = await pin(page, 2).boundingBox();
  ok('1: 핀 터치 영역 44×44 이상', !!pinBox && pinBox.width >= 44 && pinBox.height >= 44);
  ok('1: URL에 lat·lng·level(replace)', /lat=.*lng=.*level=6/.test(page.url()));
  ok('1: 데이터 출처 문구(푸터 대신 목록 아래)', await page.getByText('데이터 출처: 국토교통부 실거래가 공개시스템 (data.go.kr) · 갱신주기: 일 1회').isVisible());
  ok('1: 공통 푸터 없음', (await page.locator('footer').count()) === 0);
  ok('1: 목록·범례·지도 컨트롤은 한 벌', (await page.locator('ul[aria-label="지도 단지 목록"]').count()) === 1 && (await page.locator('[aria-label="핀 색상 범례"]').count()) === 1 && (await page.locator('button[aria-label="지도 확대"]').count()) === 1);
  ok('1: 건수 aria-live는 하나', (await page.locator('[aria-live]').filter({ hasText: /건/ }).count()) === 1);
  const bubbleColor = (id) => pin(page, id).locator('span').evaluate((el) => getComputedStyle(el).color);
  ok('1: 근사 핀 글자색 #bb4d00(흰 바탕 5.03:1)', (await bubbleColor(6)) === 'rgb(187, 77, 0)');
  ok('1: 범례 근사 견본도 #bb4d00', (await page.locator('[aria-label="핀 색상 범례"] span span').nth(1).evaluate((el) => getComputedStyle(el).backgroundColor)) === 'rgb(187, 77, 0)');

  // 목록은 최신순 정렬이라 첫 행이 지도단지6이다 — 이름으로 찾는다.
  await page.getByRole('button', { name: '지도단지3', exact: true }).click();
  await popup(page).waitFor();
  ok('2: 목록 항목 클릭 → 팝업(단지명)', (await popup(page).innerText()).includes('지도단지3'));
  ok('2: URL에 complexId=3', page.url().includes('complexId=3'));
  ok('2: 선택한 핀 aria-pressed', (await pin(page, 3).getAttribute('aria-pressed')) === 'true');
  ok('2: 팝업으로 포커스 이동(닫기 버튼)', await page.evaluate(() => document.activeElement?.getAttribute('aria-label') === '팝업 닫기'));
  ok('2: 정밀 단지 팝업엔 근사 문구 없음', !(await popup(page).innerText()).includes('근사 위치입니다'));
  await page.keyboard.press('Escape');
  await popup(page).waitFor({ state: 'detached' });
  ok('2: Esc로 닫힘, URL에서 complexId 제거', !page.url().includes('complexId='));
  await page.waitForTimeout(100);
  ok('2: 닫으면 그 핀으로 포커스', await page.evaluate(() => document.activeElement?.dataset?.complexId === '3'));

  await pin(page, 6).click();
  await popup(page).waitFor();
  ok('2: 근사 좌표 핀 클릭 → "근사 위치입니다"', (await popup(page).innerText()).includes('근사 위치입니다'));
  ok('2: 핀으로 고르면 목록 행도 선택', (await page.locator('[data-complex-id="6"] [data-map-select]').getAttribute('aria-pressed')) === 'true');
  await popup(page).getByRole('button', { name: '팝업 닫기' }).click();
  await popup(page).waitFor({ state: 'detached' });
  await pin(page, 5).click();
  ok('2: 유사 매칭 팝업은 매칭 문구', (await popup(page).innerText()).includes('일부 정보는 유사 매칭 결과입니다'));
  await page.locator('[data-fake-map="pane"]').click({ position: { x: 5, y: 5 } });
  await popup(page).waitFor({ state: 'detached' });
  ok('2: 지도 빈 곳 클릭으로 닫힘', (await popup(page).count()) === 0);

  await pin(page, 2).click();
  await popup(page).getByRole('link', { name: '상세보기' }).click();
  await page.waitForURL(/\/complexes\/2$/);
  ok('2: 상세보기 → DTL-01', page.url().endsWith('/complexes/2'));
  await page.goBack();
  await popup(page).waitFor();
  ok('2: 뒤로 오면 같은 선택(팝업)과 뷰포트', (await popup(page).innerText()).includes('지도단지2') && /lat=.*level=6.*complexId=2/.test(page.url()));
  ok('2: 복원된 팝업은 포커스를 옮기지 않음', await page.evaluate(() => document.activeElement?.getAttribute('aria-label') !== '팝업 닫기'));
  await context.close();
}

// 3) truncated 안내, 4) API 실패 배너 + 마커 유지 + 재시도
{
  const state = { mapStatus: 200, truncated: true };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  ok('3: truncated → "지도를 더 확대해보세요"', await page.getByRole('status').filter({ hasText: '지도를 더 확대해보세요' }).isVisible());
  ok('3: 건수에 +', (await page.locator('aside [aria-live="polite"]').innerText()).includes('6+건'));

  state.mapStatus = 500;
  state.truncated = false;
  await page.getByRole('button', { name: '지도 확대' }).click();
  const banner = page.getByRole('alert').filter({ hasText: '지도 조회 중 서버 오류가 발생했습니다' });
  await banner.waitFor();
  ok('4: API 실패 → 서버 메시지 배너', await banner.isVisible());
  ok('4: 기존 마커·목록 유지', (await pin(page, 1).count()) === 1 && (await rows(page).count()) === 6);
  state.mapStatus = 200;
  await banner.getByRole('button', { name: '다시 시도' }).click();
  await banner.waitFor({ state: 'detached' });
  ok('4: 다시 시도 성공 → 배너 사라짐', (await page.getByText('지도 조회 중 서버 오류가 발생했습니다').count()) === 0);
  await context.close();
}

// 5) SDK 차단 → 지도 오류 상태 + 목록 정상
{
  const state = { mapStatus: 200, sdk: 'block' };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await page.getByTestId('map-error').waitFor({ timeout: 15000 });
  ok('5: SDK 차단 → 지도 오류 + 다시 시도', await page.getByTestId('map-error').getByRole('button', { name: '다시 시도' }).isVisible());
  await rows(page).first().waitFor();
  ok('5: 목록은 근사 bounds로 계속 채움', (await rows(page).count()) === 6 && !!state.mapRequests.at(-1)?.get('bounds'));
  await page.getByRole('button', { name: '지도단지4', exact: true }).click();
  await page.waitForURL(/\/complexes\/4$/);
  ok('5: 지도가 없으면 목록 항목은 상세로', page.url().endsWith('/complexes/4'));
  await context.close();
}

// 6) 모바일 Bottom Sheet·칩·하단 탭
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 390, 844);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  const sheet = page.getByTestId('map-list-sheet');
  ok('6: 시트 처음 "반"', (await sheet.getAttribute('data-snap')) === 'half');
  ok('6: 시트 제목 "주변 단지 6건"', (await sheet.innerText()).replace(/\s+/g, ' ').includes('주변 단지 6건'));
  const handle = page.getByRole('button', { name: /목록 높이 조절/ });
  await handle.focus();
  await page.keyboard.press('ArrowUp');
  ok('6: 손잡이 ↑ → 전체', (await sheet.getAttribute('data-snap')) === 'full');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('ArrowDown');
  ok('6: 손잡이 ↓↓ → 접힘', (await sheet.getAttribute('data-snap')) === 'collapsed');
  await page.getByRole('button', { name: '목록 펼치기' }).click();
  ok('6: 화살표 버튼 → 반', (await sheet.getAttribute('data-snap')) === 'half');
  const box = await sheet.boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + 8);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2, box.y - 400, { steps: 8 });
  await page.mouse.up();
  ok('6: 손잡이 끌어 올리기 → 전체', (await sheet.getAttribute('data-snap')) === 'full');
  await page.getByRole('button', { name: '지도단지2', exact: true }).click();
  await popup(page).waitFor();
  ok('6: 목록 항목 → 팝업, 시트는 접힘', (await sheet.getAttribute('data-snap')) === 'collapsed');
  ok('6: 하단 탭 "지도" 활성', (await page.getByRole('link', { name: '지도', exact: true }).getAttribute('class')).includes('text-brand'));
  ok('6: 목록·범례·지도 컨트롤은 한 벌', (await page.locator('ul[aria-label="지도 단지 목록"]').count()) === 1 && (await page.locator('[aria-label="핀 색상 범례"]').count()) === 1 && (await page.locator('button[aria-label="지도 확대"]').count()) === 1);
  ok('6: 건수 aria-live는 하나(시트 제목)', (await page.locator('[aria-live]').filter({ hasText: /건/ }).count()) === 1);
  ok('6: 데스크톱 막대 없음(칩만)', (await page.getByTestId('map-filter-chips').isVisible()) && (await page.locator('aside[aria-label="지도 단지 목록"]').count()) === 0);
  await popup(page).getByRole('button', { name: '팝업 닫기' }).click();
  await page.getByTestId('map-filter-chips').getByRole('button', { name: /거래유형/ }).click();
  const filterSheet = page.getByRole('dialog', { name: '필터' });
  await filterSheet.waitFor();
  await filterSheet.getByLabel('전세').check();
  await filterSheet.getByRole('button', { name: '필터 적용' }).click();
  await page.waitForTimeout(500);
  ok('6: 필터 적용 → URL dealType=전세', decodeURIComponent(page.url()).includes('dealType=전세'));
  ok('6: 필터 적용 → 지도 API rentType=JEONSE(목록·지도 같은 요청)', state.mapRequests.at(-1)?.get('rentType') === 'JEONSE');
  ok('6: 바꾼 칩은 적용 표시', (await page.getByTestId('map-filter-chips').getByRole('button', { name: /거래유형.*적용됨/ }).count()) === 1);
  await context.close();
}

// 7) 비로그인 하트 → 로그인 → 같은 지도로 복귀 + 등록 재생
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map?dealType=%EC%A0%84%EC%84%B8&lat=37.5666&lng=126.9784&level=5`);
  await rows(page).first().waitFor();
  await page.waitForTimeout(400);
  const mapUrl = new URL(page.url());
  await page.getByRole('button', { name: '지도단지2 관심 매물 등록' }).click();
  await page.waitForURL(/\/login/);
  ok('7: 비로그인 하트 → 로그인 화면', page.url().includes('/login'));
  await page.getByLabel('이메일').fill('user@test.com');
  await page.getByLabel('비밀번호', { exact: true }).fill('Passw0rd!');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await page.waitForURL(/\/map\?/);
  const back = new URL(page.url());
  ok('7: 로그인 후 같은 지도 URL(필터·뷰포트)', back.searchParams.get('dealType') === '전세' && back.searchParams.get('level') === mapUrl.searchParams.get('level') && back.searchParams.get('lat') === mapUrl.searchParams.get('lat'));
  await page.waitForTimeout(800);
  ok('7: 미뤘던 관심 등록 재생(POST complexId=2)', state.favoritePosts.some((b) => b.complexId === 2));
  ok('7: 토큰 저장', !!(await page.evaluate((k) => localStorage.getItem(k), ACCESS_KEY)));
  await context.close();
}

// 8) DTL-01 "지도에서 크게 보기" 형식 진입, SRCH-01 ↔ MAP-01 필터 왕복
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  const target = ITEMS[3];
  await page.goto(`${BASE}/map?lat=${target.latitude}&lng=${target.longitude}&level=3&complexId=4`);
  await popup(page).waitFor();
  ok('8: complexId + 좌표 진입 → 그 단지 팝업', (await popup(page).innerText()).includes('지도단지4'));
  ok('8: 그 단지 핀 강조', (await pin(page, 4).getAttribute('aria-pressed')) === 'true');
  ok('8: 진입 레벨 3', page.url().includes('level=3'));
  await context.close();
}
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/search?regionCode=1168000000&regionLabel=${encodeURIComponent('서울특별시 강남구')}&dealType=${encodeURIComponent('월세')}&page=3`);
  const mapLink = page.locator('main a[href^="/map"]').last();
  await mapLink.waitFor();
  const href = await mapLink.getAttribute('href');
  ok('8: SRCH-01 → MAP-01 링크는 page를 버림', !href.includes('page=') && decodeURIComponent(href).includes('dealType=월세'));
  await page.goto(`${BASE}${href}`);
  await rows(page).first().waitFor();
  await page.waitForTimeout(300);
  const req = state.mapRequests.at(-1);
  const [swLat, , neLat] = req.get('bounds').split(',').map(Number);
  ok('8: 지역 라벨 → Geocoder로 그 중심(강남구 37.517)', swLat < 37.5172 && neLat > 37.5172);
  ok('8: 지도 API rentType=WOLSE', req.get('rentType') === 'WOLSE');
  const listHref = await page.getByRole('link', { name: '목록으로' }).getAttribute('href');
  const listParams = new URLSearchParams(listHref.split('?')[1]);
  ok('8: MAP-01 → SRCH-01 링크는 필터 유지·뷰포트 버림', listParams.get('dealType') === '월세' && listParams.get('regionCode') === '1168000000' && !listParams.has('lat') && !listParams.has('page'));
  await context.close();
}

// 9) DTL-01 "지도에서 크게 보기" → MAP-01(그 단지 팝업), 좌표 없는 단지는 비활성 + 이유
{
  const target = ITEMS[3];
  const detail = (id, lat, lng) => ({
    complexId: id,
    complexName: `지도단지${id}`,
    housingType: 'APT',
    sido: '서울특별시',
    sigungu: '중구',
    dongRi: '태평로1가',
    legalDongCd: '1114010300',
    matchPending: false,
    matchMethod: 'EXACT',
    precisionBadge: 'PRECISE',
    latitude: lat,
    longitude: lng,
    locationPrecision: lat == null ? null : 'PRECISE',
    basicInfo: {},
    extendedInfo: { elevatorPassengerCount: 0, elevatorCargoCount: 0, elevatorCombinedCount: 0 },
  });
  const state = { mapStatus: 200, details: { 4: detail(4, target.latitude, target.longitude), 9: detail(9, null, null) } };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/complexes/4`);
  const link = page.getByRole('link', { name: '지도에서 크게 보기' });
  await link.waitFor();
  const href = await link.getAttribute('href');
  ok('9: DTL-01 지도 링크 = complexId + 좌표 + 레벨 3', /complexId=4/.test(href) && /level=3/.test(href) && href.includes(`lat=${target.latitude}`));
  await link.click();
  await popup(page).waitFor();
  ok('9: 지도에서 그 단지 팝업이 열림', (await popup(page).innerText()).includes('지도단지4'));
  await page.goto(`${BASE}/complexes/9`);
  await page.locator('h1#complex-name').waitFor();
  ok(
    '9: 좌표 없는 단지는 버튼 비활성 + 이유',
    (await page.getByRole('button', { name: '지도에서 크게 보기' }).isDisabled()) &&
      (await page.getByText('위치 정보가 없어 지도에서 볼 수 없습니다').isVisible()),
  );
  await context.close();
}

// 10) 선택한 단지가 새 응답에 없을 때(truncated로 빠짐) — 마지막으로 알던 값으로 핀과 팝업 유지
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  await page.getByRole('button', { name: '지도단지3', exact: true }).click();
  await popup(page).waitFor();
  const before = state.mapRequests.length;
  state.items = ITEMS.filter((i) => i.complexId !== 3);
  state.truncated = true;
  await page.getByRole('button', { name: '지도 축소' }).click();
  await page.waitForFunction(() => document.querySelectorAll('ul[aria-label="지도 단지 목록"] > li').length === 5);
  await page.waitForTimeout(200);
  ok('10: 새 응답 요청이 나감', state.mapRequests.length > before);
  ok('10: 목록에서는 빠짐(5행, "5+건")', (await rows(page).count()) === 5 && (await page.locator('aside [aria-live="polite"]').innerText()).includes('5+건'));
  ok('10: 팝업 유지(지도단지3, 가격)', (await popup(page).count()) === 1 && (await popup(page).innerText()).includes('지도단지3') && (await popup(page).innerText()).includes('억'));
  ok('10: 선택 핀 유지(aria-pressed)', (await pin(page, 3).count()) === 1 && (await pin(page, 3).getAttribute('aria-pressed')) === 'true');
  ok('10: URL complexId 유지', page.url().includes('complexId=3'));
  ok('10: 응답에 있던 단지라 상세 API를 다시 부르지 않음', !(state.detailRequests ?? []).includes('3'));
  await popup(page).getByRole('button', { name: '팝업 닫기' }).click();
  await popup(page).waitFor({ state: 'detached' });
  await page.waitForTimeout(100);
  ok('10: 닫으면 응답에 없는 핀은 사라짐', (await pin(page, 3).count()) === 0);
  await context.close();
}

// 11) DTL-01 딥링크로 넓은 레벨에서 진입 — 응답(잘림)에 그 단지가 없으면 URL 좌표 주변 좁은 범위 조회로 complexId를 골라
//     핀·팝업. 거기 없을 때만 상세 API(최근 조회를 기록한다)를 부른다.
const detailOf = (id, lat, lng, extra = {}) => ({
  complexId: id,
  complexName: `지도단지${id}`,
  housingType: 'APT',
  sido: '서울특별시',
  sigungu: '중구',
  dongRi: '태평로1가',
  legalDongCd: '1114010300',
  matchPending: false,
  matchMethod: 'EXACT',
  precisionBadge: 'PRECISE',
  latitude: lat,
  longitude: lng,
  locationPrecision: 'PRECISE',
  basicInfo: {},
  extendedInfo: {},
  ...extra,
});
{
  const far = item(9, { latitude: CENTER.lat + 0.08, longitude: CENTER.lng + 0.1, representativeAmount: 123000 });
  // 좁은 범위 응답에 다른 단지(8)가 먼저 섞여 있다 — complexId로만 골라야 한다.
  const other = item(8, { latitude: far.latitude + 0.0001, longitude: far.longitude, representativeAmount: 45000 });
  const state = { mapStatus: 200, truncated: true, nearbyItems: [other, far], details: { 9: detailOf(9, far.latitude, far.longitude) } };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map?lat=${far.latitude}&lng=${far.longitude}&level=10&complexId=9`);
  await popup(page).waitFor({ timeout: 8000 });
  ok('11: 넓은 레벨 딥링크 → 그 단지 팝업', (await popup(page).innerText()).includes('지도단지9'));
  ok('11: 좁은 범위 조회의 대표 거래 가격(12.3억)', (await popup(page).innerText()).includes('12억 3,000만원'));
  ok('11: 섞인 다른 단지(4.5억)가 아니라 complexId=9', !(await popup(page).innerText()).includes('지도단지8') && !(await popup(page).innerText()).includes('4억 5,000만원'));
  ok('11: 좁은 범위 조회를 먼저 함', (state.nearbyRequests ?? 0) >= 1);
  ok('11: 좁은 범위에서 찾았으니 상세 API(최근 조회 기록)를 부르지 않음', (state.detailRequests ?? []).length === 0);
  ok('11: 섞인 다른 단지는 핀으로 더하지 않음', (await pin(page, 8).count()) === 0);
  ok('11: 핀 강조(aria-pressed)', (await pin(page, 9).getAttribute('aria-pressed')) === 'true');
  ok('11: 목록에는 없음(범위 조회 응답 6건)', (await rows(page).count()) === 6);
  ok('11: 레벨 10 유지', page.url().includes('level=10'));
  await context.close();
}
{
  // 좌표 있는 딥링크인데 좁은 범위에 다른 단지만 있음(필터에 맞는 거래 없음) — 그때만 상세 API, 상세만으로 핀·팝업
  const lat = CENTER.lat + 0.08;
  const lng = CENTER.lng + 0.1;
  const state = { mapStatus: 200, truncated: true, nearbyItems: [item(8, { latitude: lat, longitude: lng })], details: { 9: detailOf(9, lat, lng) } };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map?lat=${lat}&lng=${lng}&level=10&complexId=9`);
  await popup(page).waitFor({ timeout: 8000 });
  ok('11c: 좁은 범위에 없으면 상세 API를 한 번 부름', (state.detailRequests ?? []).filter((id) => id === '9').length === 1);
  ok('11c: 다른 단지를 고르지 않고 거래 없음 안내', (await popup(page).innerText()).includes('지도단지9') && (await popup(page).innerText()).includes('선택한 조건의 최근 거래가 없습니다'));
  await context.close();
}
{
  // 지금 필터에 맞는 거래가 없는 단지(좁은 범위 조회도 비어 있음) — 상세만으로 핀(단지명)·팝업("거래 없음")
  const state = { mapStatus: 200, truncated: true, nearbyItems: [], details: { 9: detailOf(9, CENTER.lat + 0.08, CENTER.lng + 0.1, { precisionBadge: 'APPROXIMATE', locationPrecision: 'APPROXIMATE' }) } };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map?complexId=9`);
  await popup(page).waitFor({ timeout: 8000 });
  ok('11b: 좌표 없는 complexId 링크도 팝업', (await popup(page).innerText()).includes('지도단지9'));
  // 진입 때 좌표를 얻으려고 상세를 받는다(dev StrictMode는 이 진입 effect를 두 번 돌려 요청이 2건일 수 있다).
  // 선택 단지 조회는 그 상세를 다시 쓰므로 좁은 범위 조회 뒤에는 상세 요청이 없어야 한다.
  const events = state.events ?? [];
  ok('11b: 좁은 범위 조회 뒤에는 상세를 다시 부르지 않음', events.includes('nearby') && !events.slice(events.indexOf('nearby')).some((e) => e.startsWith('detail')));
  ok('11b: 거래 없으면 안내 문구', (await popup(page).innerText()).includes('선택한 조건의 최근 거래가 없습니다'));
  ok('11b: 근사 좌표 문구', (await popup(page).innerText()).includes('근사 위치입니다'));
  ok('11b: 핀 글자는 단지명, aria-label에 거래 정보 없음', (await pin(page, 9).innerText()).includes('지도단지9') && (await pin(page, 9).getAttribute('aria-label')).includes('거래 정보 없음'));
  await context.close();
}

// 12) 목록 접기·펼치기, 창 크기 변경 → 지도 크기가 바뀌면 바뀐 bounds로 다시 조회(가짜 SDK relayout은 idle을 내지 않는다)
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  await page.waitForTimeout(500);
  const lngSpan = (params) => {
    const [, swLng, , neLng] = params.get('bounds').split(',').map(Number);
    return neLng - swLng;
  };
  const waitForNewRequest = async (count) => {
    for (let i = 0; i < 40 && state.mapRequests.length <= count; i++) await page.waitForTimeout(50);
    return state.mapRequests.length > count;
  };
  const baseSpan = lngSpan(state.mapRequests.at(-1));

  let count = state.mapRequests.length;
  await page.getByRole('button', { name: '목록 접기' }).click();
  ok('12: 목록 접기 → 다시 조회', await waitForNewRequest(count));
  const collapsedSpan = lngSpan(state.mapRequests.at(-1));
  ok('12: 접으면 bounds 경도 폭이 넓어짐(380px만큼)', collapsedSpan > baseSpan * 1.3);

  count = state.mapRequests.length;
  await page.getByRole('button', { name: '목록 펼치기' }).click();
  ok('12: 목록 펼치기 → 다시 조회', await waitForNewRequest(count));
  ok('12: 펼치면 원래 폭으로', Math.abs(lngSpan(state.mapRequests.at(-1)) - baseSpan) < baseSpan * 0.05);

  count = state.mapRequests.length;
  await page.setViewportSize({ width: 1600, height: 900 });
  ok('12: 창 크기 변경 → 다시 조회', await waitForNewRequest(count));
  ok('12: 넓힌 창의 bounds가 더 넓음', lngSpan(state.mapRequests.at(-1)) > baseSpan * 1.2);
  ok('12: URL 뷰포트 유지(레벨 6)', page.url().includes('level=6'));
  await context.close();
}

// 12b) 실제 SDK처럼 relayout 뒤 idle도 내는 경우(디바운스 300ms보다 늦은 700ms) — 같은 bounds·필터라 /map은 한 번만
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await page.addInitScript(() => {
    window.__FAKE_KAKAO_OPTIONS = { idleOnRelayout: true, relayoutIdleDelay: 700 };
  });
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  await page.waitForTimeout(1200);
  const before = state.mapRequests.length;
  const idlesBefore = await page.evaluate(() => window.__fakeRelayoutIdleCount || 0);
  await page.getByRole('button', { name: '목록 접기' }).click();
  await page.waitForTimeout(1500);
  const relayoutIdles = (await page.evaluate(() => window.__fakeRelayoutIdleCount || 0)) - idlesBefore;
  const added = state.mapRequests.slice(before);
  ok(`12b: relayout 뒤 700ms에 idle이 실제로 났음(전제, ${relayoutIdles}회)`, relayoutIdles >= 1);
  ok(`12b: 범위 알림이 두 번 와도 /map은 한 번(${added.length}건)`, added.length === 1);
  ok('12b: 그 한 번은 넓어진 bounds', added.length === 1 && (() => {
    const [, swLng, , neLng] = added[0].get('bounds').split(',').map(Number);
    const [, bSwLng, , bNeLng] = state.mapRequests[before - 1].get('bounds').split(',').map(Number);
    return neLng - swLng > (bNeLng - bSwLng) * 1.3;
  })());
  await context.close();
}

// 13) 지역 검색이 겹칠 때 — 앞 검색의 지오코딩이 뒤 검색보다 늦게 끝나도 지도를 앞 지역으로 되돌리지 않고, 앞 검색의
//     "못 찾음" 토스트도 띄우지 않는다. 대조: 단독 검색이 못 찾으면 토스트가 뜬다.
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await page.addInitScript(() => {
    window.__FAKE_KAKAO_OPTIONS = { geocodeDelays: { '서울특별시 강남구': 800, 없는지역: 400 } };
  });
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  const search = page.getByPlaceholder('지역명으로 지도 이동');
  const toast = page.getByText('위치를 찾을 수 없습니다');
  const urlLat = () => Number(new URL(page.url()).searchParams.get('lat'));
  const searchFor = async (query) => {
    await search.fill(query);
    await search.press('Enter');
  };

  await searchFor('서울특별시 강남구'); // 800ms 뒤에 끝난다
  await searchFor('경기도 수원시 장안구'); // 10ms 뒤에 끝난다
  await page.waitForTimeout(1500);
  ok('13: URL 검색 조건은 뒤 검색(수원 장안구)', new URL(page.url()).searchParams.get('keyword') === '경기도 수원시 장안구');
  ok(`13: 늦게 끝난 앞 검색이 지도를 강남으로 되돌리지 않음(lat ${urlLat()})`, Math.abs(urlLat() - 37.304) < 0.001);

  await searchFor('없는지역'); // 주소 400ms + 키워드 400ms 뒤 못 찾음
  await searchFor('경기도 수원시 장안구');
  await page.waitForTimeout(1500);
  ok('13: 밀린 앞 검색의 "못 찾음" 토스트를 띄우지 않음', (await toast.count()) === 0);

  await page.evaluate(() => {
    window.__FAKE_KAKAO_OPTIONS.geocodeDelays = {};
  });
  await searchFor('없는지역');
  await toast.first().waitFor({ timeout: 3000 }).catch(() => {});
  ok('13: 대조 — 단독 검색이 못 찾으면 토스트', (await toast.count()) > 0);
  await context.close();
}

// 14~17 공통 — 현재 위치(navigator.geolocation)를 지연·성공/실패를 조절할 수 있는 가짜로 바꾼다.
function stubGeolocation() {
  window.__geo = { delay: 800, ok: true, lat: 35.1796, lng: 129.0756 };
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: {
      getCurrentPosition(success, error) {
        const g = window.__geo;
        setTimeout(
          () =>
            g.ok
              ? success({ coords: { latitude: g.lat, longitude: g.lng } })
              : error({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 }),
          g.delay,
        );
      },
    },
  });
}
const BUSAN_LAT = 35.1796;
const SUWON_LAT = 37.304;
const mapHelpers = (page) => ({
  search: page.getByPlaceholder('지역명으로 지도 이동'),
  locate: page.getByRole('button', { name: '현재 위치로 이동' }),
  notFoundToast: page.getByText('위치를 찾을 수 없습니다'),
  geoToast: page.getByText(/위치 권한이 없어|현재 위치를 가져오지 못했습니다/),
  urlLat: () => Number(new URL(page.url()).searchParams.get('lat')),
  searchFor: async (query) => {
    const input = page.getByPlaceholder('지역명으로 지도 이동');
    await input.fill(query);
    await input.press('Enter');
  },
  setGeo: (patch) => page.evaluate((p) => Object.assign(window.__geo, p), patch),
});

// 14) 현재 위치 — 조회 중에 다른 이동·선택을 하면 결과(이동·토스트)를 버리고 버튼 잠금만 푼다.
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await page.addInitScript(stubGeolocation);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  const h = mapHelpers(page);

  await h.locate.click();
  await page.waitForTimeout(1500);
  ok(`14: 대조 — 방해가 없으면 현재 위치로 이동(lat ${h.urlLat()})`, Math.abs(h.urlLat() - BUSAN_LAT) < 0.001);

  await h.locate.click();
  await h.searchFor('경기도 수원시 장안구');
  await page.waitForTimeout(1500);
  ok(`14: 조회 중 지역 검색 → 늦게 온 현재 위치로 옮기지 않음(lat ${h.urlLat()})`, Math.abs(h.urlLat() - SUWON_LAT) < 0.001);
  ok('14: 버튼 잠금은 풀림', (await h.locate.count()) === 1 && (await h.locate.isEnabled()));

  await h.setGeo({ ok: false });
  await page.getByRole('button', { name: '현재 위치로 이동' }).click();
  await rows(page).first().locator('[data-map-select]').click();
  await page.waitForTimeout(1500);
  ok('14: 조회 중 목록 선택 → 늦게 온 실패 토스트를 띄우지 않음', (await h.geoToast.count()) === 0);
  ok('14: 실패해도 버튼 잠금은 풀림', (await h.locate.count()) === 1 && (await h.locate.isEnabled()));

  await h.setGeo({ ok: true });
  await h.locate.click();
  await page.getByTestId('map-canvas').dispatchEvent('pointerdown');
  await page.waitForTimeout(1500);
  ok(`14: 조회 중 지도를 직접 만지면 옮기지 않음(lat ${h.urlLat()})`, Math.abs(h.urlLat() - BUSAN_LAT) > 0.5);

  await h.setGeo({ ok: false });
  await h.locate.click();
  await h.geoToast.first().waitFor({ timeout: 3000 }).catch(() => {});
  ok('14: 대조 — 방해가 없으면 실패 토스트', (await h.geoToast.count()) > 0);
  await context.close();
}

// 15) 딥링크 선택 조회 중 필터를 바꾸면 옛 필터 결과를 버리고 새 필터로 다시 찾는다.
{
  const far = item(9, { latitude: CENTER.lat + 0.08, longitude: CENTER.lng + 0.1, representativeAmount: 123000 });
  const farJeonse = { ...far, representativeDealCategory: 'RENT', rentType: 'JEONSE', representativeAmount: 77000 };
  const state = {
    mapStatus: 200,
    truncated: true,
    nearbyDelayMs: 800,
    nearbyItems: (params) => [params.get('rentType') === 'JEONSE' ? farJeonse : far],
  };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map?lat=${far.latitude}&lng=${far.longitude}&level=10&complexId=9`);
  for (let i = 0; i < 100 && !(state.nearbyRequests >= 1); i++) await page.waitForTimeout(50);
  ok('15: 전제 — 선택 단지 조회가 진행 중', state.nearbyRequests === 1);
  await page.getByRole('button', { name: /거래유형/ }).click();
  const filterDialog = page.getByRole('dialog', { name: '필터' });
  await filterDialog.getByLabel('전세').check();
  await filterDialog.getByRole('button', { name: '필터 적용' }).click();
  await popup(page).waitFor({ timeout: 8000 });
  await page.waitForTimeout(1200);
  const text = await popup(page).innerText();
  ok('15: 팝업은 새 필터(전세 7.7억) 결과', text.includes('7억 7,000만원'));
  ok('15: 옛 필터(매매 12.3억) 결과를 넣지 않음', !text.includes('12억 3,000만원'));
  ok('15: 새 필터로 다시 조회', state.nearbyParams.at(-1)?.get('rentType') === 'JEONSE');
  await context.close();
}

// 16) 지도가 뜨기 전(시작 위치 지오코딩·진입 단지 좌표 조회 중)에 지역을 검색하면 그 지역에서 연다.
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await page.addInitScript(() => {
    window.__FAKE_KAKAO_OPTIONS = { geocodeDelays: { '서울특별시 강남구': 2000 } };
  });
  await setup(page, state);
  await page.goto(`${BASE}/map?regionCode=1168000000&regionLabel=${encodeURIComponent('서울특별시 강남구')}`);
  const h = mapHelpers(page);
  await h.search.waitFor();
  ok('16: 전제 — 검색할 때 지도는 아직 불러오는 중', await page.getByText('지도를 불러오는 중').isVisible());
  await h.searchFor('경기도 수원시 장안구');
  await page.waitForTimeout(3000);
  ok(`16: 시작 지오코딩 중 검색 → 검색한 지역에서 엶(lat ${h.urlLat()})`, Math.abs(h.urlLat() - SUWON_LAT) < 0.001);
  ok('16: "못 찾음" 토스트 없음', (await h.notFoundToast.count()) === 0);
  await context.close();
}
// (현재 위치 버튼 등 지도 컨트롤은 지도가 준비된 뒤에만 그려져, 지도가 뜨기 전의 현재 위치 조회는 화면에서 일어나지 않는다.)
{
  const lat = CENTER.lat + 0.08;
  const lng = CENTER.lng + 0.1;
  const state = { mapStatus: 200, detailDelayMs: 2000, details: { 9: detailOf(9, lat, lng) } };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map?complexId=9`);
  const h = mapHelpers(page);
  await h.search.waitFor();
  ok('16: 전제 — 검색할 때 지도 컴포넌트가 아직 없음(진입 단지 좌표 조회 중)', (await page.getByTestId('map-canvas').count()) === 0);
  await h.searchFor('경기도 수원시 장안구');
  await page.waitForTimeout(3000);
  ok(`16: 진입 단지 좌표 조회 중 검색 → 검색한 지역에서 엶(lat ${h.urlLat()})`, Math.abs(h.urlLat() - SUWON_LAT) < 0.001);
  ok('16: 진입 단지 좌표 조회 중 검색 → "못 찾음" 토스트 없음', (await h.notFoundToast.count()) === 0);
  await context.close();
}

// 17) 지역 검색·현재 위치 조회 중에 화면을 떠나면(목록으로) 늦게 온 결과의 토스트를 다른 화면에 띄우지 않는다.
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await page.addInitScript(() => {
    window.__FAKE_KAKAO_OPTIONS = { geocodeDelays: { 없는지역: 400 } };
  });
  await page.addInitScript(stubGeolocation);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  const h = mapHelpers(page);
  await h.searchFor('없는지역'); // 주소 400ms + 키워드 400ms 뒤 못 찾음
  await page.getByRole('link', { name: '목록으로' }).click();
  await page.waitForURL(/\/search/);
  await page.waitForTimeout(1500);
  ok('17: 검색 중 화면을 떠나면 "못 찾음" 토스트를 띄우지 않음', (await h.notFoundToast.count()) === 0);

  await page.goto(`${BASE}/map?keyword=${encodeURIComponent('경기도 수원시 장안구')}`);
  await rows(page).first().waitFor();
  await h.setGeo({ ok: false });
  await h.locate.click();
  await page.getByRole('link', { name: '목록으로' }).click();
  await page.waitForURL(/\/search/);
  await page.waitForTimeout(1500);
  ok('17: 현재 위치 조회 중 화면을 떠나면 실패 토스트를 띄우지 않음', (await h.geoToast.count()) === 0);
  await context.close();
}

// 18) 선택 단지 조회는 확대·축소·지도 터치로 끊기지 않는다 — 선택이 그대로라 팝업 가격이 채워져야 한다(지도 이동·현재 위치용
//     동작 번호와 분리. 다른 단지 선택·팝업 닫기·필터 변경·언마운트에만 끊는다).
{
  const far = item(9, { latitude: CENTER.lat + 0.08, longitude: CENTER.lng + 0.1, representativeAmount: 123000 });
  const state = { mapStatus: 200, truncated: true, nearbyDelayMs: 1200, nearbyItems: [far] };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map?lat=${far.latitude}&lng=${far.longitude}&level=10&complexId=9`);
  for (let i = 0; i < 100 && !(state.nearbyRequests >= 1); i++) await page.waitForTimeout(50);
  ok('18: 전제 — 선택 단지 조회가 진행 중', state.nearbyRequests === 1);
  await page.getByRole('button', { name: '지도 확대' }).click();
  const canvas = page.getByTestId('map-canvas');
  await canvas.dispatchEvent('pointerdown');
  await canvas.dispatchEvent('wheel');
  await popup(page).waitFor({ timeout: 5000 }).catch(() => {});
  ok('18: 조회 중 확대·지도 터치 뒤에도 팝업이 뜸', (await popup(page).count()) === 1);
  ok('18: 팝업 가격이 채워짐(12.3억)', (await popup(page).count()) === 1 && (await popup(page).innerText()).includes('12억 3,000만원'));
  ok('18: 선택 조회를 다시 보내지 않음(1건)', state.nearbyRequests === 1);
  await context.close();
}

// 19) 화면 폭이 768px 경계를 넘으면(태블릿 회전 등) 지도가 다시 마운트된다 — 진입 위치로 돌아가지 않고 마지막 위치·레벨에서
//     다시 시작하고, URL도 그 위치를 유지한다(데스크톱 → 모바일 → 데스크톱).
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  const h = mapHelpers(page);
  const urlLevel = () => Number(new URL(page.url()).searchParams.get('level'));
  await h.searchFor('경기도 수원시 장안구');
  for (let i = 0; i < 40 && Math.abs(h.urlLat() - SUWON_LAT) >= 0.001; i++) await page.waitForTimeout(50);
  await page.getByRole('button', { name: '지도 축소' }).click();
  for (let i = 0; i < 40 && urlLevel() !== 6; i++) await page.waitForTimeout(50);
  ok(`19: 전제 — 수원으로 옮기고 레벨 6(lat ${h.urlLat()}, level ${urlLevel()})`, Math.abs(h.urlLat() - SUWON_LAT) < 0.001 && urlLevel() === 6);

  for (const [width, label] of [[390, '모바일'], [1280, '데스크톱']]) {
    await page.setViewportSize({ width, height: 800 });
    await page.waitForTimeout(800);
    const center = await page.evaluate(() => ({ lat: window.__fakeMap.getCenter().getLat(), level: window.__fakeMap.getLevel() }));
    ok(`19: ${label}로 바뀐 뒤 새 지도가 마지막 위치에서 시작(lat ${center.lat}, level ${center.level})`, Math.abs(center.lat - SUWON_LAT) < 0.001 && center.level === 6);
    ok(`19: ${label}로 바뀐 뒤 URL 위치 유지(lat ${h.urlLat()}, level ${urlLevel()})`, Math.abs(h.urlLat() - SUWON_LAT) < 0.001 && urlLevel() === 6);
  }
  await context.close();
}

// 19b) 같은 경계 전환에서 선택·팝업·필터도 유지된다 — 전세 필터를 적용하고 단지를 고른 뒤 그 단지가 응답에서 빠진 상태
//      (마지막으로 알던 값으로 핀·팝업을 그리는 경로)로 1280 → 390 → 1280.
{
  const state = { mapStatus: 200 };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  await page.getByRole('button', { name: /거래유형/ }).click();
  const filterDialog = page.getByRole('dialog', { name: '필터' });
  await filterDialog.getByLabel('전세').check();
  await filterDialog.getByRole('button', { name: '필터 적용' }).click();
  for (let i = 0; i < 40 && state.mapRequests.at(-1)?.get('rentType') !== 'JEONSE'; i++) await page.waitForTimeout(50);
  await page.getByRole('button', { name: '지도단지3', exact: true }).click();
  await popup(page).waitFor();
  state.items = ITEMS.filter((i) => i.complexId !== 3);
  state.truncated = true;
  await page.getByRole('button', { name: '지도 축소' }).click();
  await page.waitForFunction(() => document.querySelectorAll('ul[aria-label="지도 단지 목록"] > li').length === 5);
  await page.waitForTimeout(200);
  ok('19b: 전제 — 전세 필터 적용, 선택 단지가 응답에서 빠진 채 팝업·핀 유지',
    decodeURIComponent(page.url()).includes('dealType=전세') && (await popup(page).count()) === 1 && (await pin(page, 3).getAttribute('aria-pressed')) === 'true');

  for (const [width, label] of [[390, '모바일'], [1280, '데스크톱']]) {
    const before = state.mapRequests.length;
    await page.setViewportSize({ width, height: 800 });
    await page.waitForTimeout(800);
    const params = new URL(page.url()).searchParams;
    ok(`19b: ${label} — URL 선택·필터 유지`, params.get('complexId') === '3' && params.get('dealType') === '전세');
    ok(`19b: ${label} — 팝업 유지(지도단지3, 가격)`, (await popup(page).count()) === 1 && (await popup(page).innerText()).includes('지도단지3') && (await popup(page).innerText()).includes('억'));
    ok(`19b: ${label} — 응답에 없는 선택 핀 유지(aria-pressed)`, (await pin(page, 3).count()) === 1 && (await pin(page, 3).getAttribute('aria-pressed')) === 'true');
    ok(`19b: ${label} — 새 지도의 조회도 전세 필터`, state.mapRequests.length > before && state.mapRequests.slice(before).every((p) => p.get('rentType') === 'JEONSE'));
    ok(`19b: ${label} — 필터 칩 적용 표시`, (await page.getByText('(적용됨)').count()) > 0);
    ok(`19b: ${label} — 선택 단지를 상세 API로 다시 부르지 않음`, !(state.detailRequests ?? []).includes('3'));
  }
  await context.close();
}

// 20) 자유 텍스트 검색어 검증 — SRCH-01과 같은 규칙(keywordIssue, 2~50자). 어긴 검색어는 URL·지도를 바꾸지 않고 안내만 띄운다.
//     URL로 들어온 잘못된 검색어(공유 링크)는 "목록으로" 링크를 두지 않는다(SRCH-01이 결과 대신 검색어 오류를 보이므로).
{
  const state = { mapStatus: 200 };
  const SUWON = '경기도 수원시 장안구';
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map?keyword=${encodeURIComponent(SUWON)}`);
  await rows(page).first().waitFor();
  await page.waitForTimeout(500);
  const h = mapHelpers(page);
  const listLink = page.getByRole('link', { name: '목록으로' });
  const keywordOf = () => new URL(page.url()).searchParams.get('keyword');
  const latBefore = h.urlLat();
  const linkBefore = await listLink.getAttribute('href');
  const tooShort = page.getByText('검색어는 2자 이상 입력해주세요.');

  await h.searchFor('래');
  await tooShort.first().waitFor({ timeout: 3000 }).catch(() => {});
  await page.waitForTimeout(500);
  ok('20: 1자 검색어 → 2자 이상 안내', (await tooShort.count()) > 0);
  ok(`20: 1자 검색어는 URL에 남기지 않음(keyword ${keywordOf()})`, keywordOf() === SUWON);
  ok('20: 1자 검색어로 지도를 옮기지 않음', Math.abs(h.urlLat() - latBefore) < 1e-6);
  ok('20: "목록으로" 링크는 이전 검색어 그대로', (await listLink.getAttribute('href')) === linkBefore);

  // 51자 이상은 입력란 maxLength(50)가 막는다 — 50자 초과 규칙 자체는 keywordIssue 단위 테스트가 맡는다.
  await h.search.fill('가'.repeat(51));
  ok('20: 검색 입력란은 50자까지만 받음', (await h.search.inputValue()).length === 50);
  await h.search.fill('');

  await h.searchFor('수원');
  await page.waitForTimeout(500);
  ok('20: 대조 — 2자 검색어는 URL에 남김', keywordOf() === '수원');

  await page.goto(`${BASE}/map?keyword=${encodeURIComponent('래')}`);
  await rows(page).first().waitFor();
  ok('20: URL로 들어온 1자 검색어 → "목록으로" 링크 없음', (await listLink.count()) === 0);
  await context.close();

  // 모바일 — 검색 시트는 받아들인 검색어일 때만 닫는다.
  const m = await newPage(browser, 390, 800);
  await setup(m.page, { mapStatus: 200 });
  await m.page.goto(`${BASE}/map`);
  await m.page.getByTestId('map-filter-chips').waitFor();
  await m.page.getByTestId('map-filter-chips').getByRole('button').first().click();
  const sheet = m.page.getByRole('dialog', { name: '지역 검색' });
  await sheet.waitFor();
  const input = sheet.getByRole('combobox');
  await input.fill('래');
  await input.press('Enter');
  await m.page.getByText('검색어는 2자 이상 입력해주세요.').first().waitFor({ timeout: 3000 }).catch(() => {});
  ok('20: 모바일 1자 검색어 → 안내가 보임', await m.page.getByText('검색어는 2자 이상 입력해주세요.').first().isVisible().catch(() => false));
  ok('20: 모바일 1자 검색어 → 시트가 열린 채', await sheet.isVisible());
  ok('20: 모바일 1자 검색어 → URL에 keyword 없음', !new URL(m.page.url()).searchParams.has('keyword'));
  await input.fill('수원');
  await input.press('Enter');
  await sheet.waitFor({ state: 'detached', timeout: 3000 }).catch(() => {});
  ok('20: 모바일 2자 검색어 → 시트 닫힘·URL에 남김', !(await sheet.isVisible().catch(() => false)) && new URL(m.page.url()).searchParams.get('keyword') === '수원');
  await m.context.close();
}

// 21) 필터를 바꾼 뒤 새 응답에 선택 단지가 없으면 — 옛 필터(매매)의 가격을 팝업에 남기지 않고 새 필터(전세)로 다시 조회한다.
//     새 필터에 맞는 거래가 없으면 "선택한 조건의 최근 거래가 없습니다".
for (const [label, jeonse] of [['전세 거래 있음', true], ['전세 거래 없음', false]]) {
  const target = ITEMS[2]; // 지도단지3, 매매 4억 5,000만원
  const targetJeonse = { ...target, representativeDealCategory: 'RENT', rentType: 'JEONSE', representativeAmount: 22000 };
  const state = {
    mapStatus: 200,
    items: (params) => (params.get('rentType') === 'JEONSE' ? ITEMS.filter((i) => i.complexId !== 3) : ITEMS),
    nearbyItems: (params) => (params.get('rentType') === 'JEONSE' && jeonse ? [targetJeonse] : []),
    details: { 3: detailOf(3, target.latitude, target.longitude) },
  };
  const { context, page } = await newPage(browser, 1280, 800);
  await setup(page, state);
  await page.goto(`${BASE}/map`);
  await rows(page).first().waitFor();
  await page.getByRole('button', { name: '지도단지3', exact: true }).click();
  await popup(page).waitFor();
  ok(`21(${label}): 전제 — 매매 가격 팝업`, (await popup(page).innerText()).includes('4억 5,000만원'));
  await page.getByRole('button', { name: /거래유형/ }).click();
  const filterDialog = page.getByRole('dialog', { name: '필터' });
  await filterDialog.getByLabel('전세').check();
  await filterDialog.getByRole('button', { name: '필터 적용' }).click();
  await page.waitForFunction(() => document.querySelectorAll('ul[aria-label="지도 단지 목록"] > li').length === 5);
  for (let i = 0; i < 60 && !(state.nearbyParams ?? []).some((p) => p.get('rentType') === 'JEONSE'); i++) await page.waitForTimeout(50);
  await page.waitForTimeout(800);
  const text = (await popup(page).count()) === 1 ? await popup(page).innerText() : '';
  ok(`21(${label}): 전세 기준으로 다시 조회`, (state.nearbyParams ?? []).some((p) => p.get('rentType') === 'JEONSE'));
  ok(`21(${label}): 팝업에 매매 가격이 남지 않음`, !text.includes('4억 5,000만원'));
  if (jeonse) {
    ok(`21(${label}): 팝업은 전세 가격(2억 2,000만원)`, text.includes('2억 2,000만원'));
  } else {
    ok(`21(${label}): 팝업은 "선택한 조건의 최근 거래가 없습니다"`, text.includes('선택한 조건의 최근 거래가 없습니다'));
  }
  ok(`21(${label}): URL 선택 유지`, page.url().includes('complexId=3'));
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
