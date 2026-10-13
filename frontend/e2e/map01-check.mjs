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
  await page.route('**/api/**', (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname;
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body });
    if (path === '/api/complexes/map') {
      state.mapRequests.push(url.searchParams);
      if (state.mapStatus === 500) return json(500, errBody('INTERNAL_SERVER_ERROR', '지도 조회 중 서버 오류가 발생했습니다'));
      return json(200, okBody({ items: state.items ?? ITEMS, truncated: !!state.truncated, limit: 500 }));
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

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
