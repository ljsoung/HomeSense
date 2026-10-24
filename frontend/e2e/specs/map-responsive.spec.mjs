// SCR-MAP-01 — 화면 폭이 768px 경계를 넘어도 지도가 다시 만들어지지 않는다(백로그 12·13·14).
// 카카오 SDK는 가짜(fakeKakaoSdk.js), /api/** 는 page.route 목업. 백엔드 불필요.
// 전제: dev 서버에 VITE_KAKAO_JS_KEY가 있어야 한다(값은 무엇이든). playwright.config가 띄우는 서버는 넣어 준다 — 이미 떠
// 있는 서버를 재사용하면 그 서버의 .env.local에 키가 있어야 한다.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import { errBody, okBody } from '../mockApi.mjs';

const FAKE_SDK = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'fakeKakaoSdk.js'), 'utf8');

// 기본 위치(서울시청 부근, 레벨 6) 주변 단지 4개.
const CENTER = { lat: 37.5666, lng: 126.9784 };
const ITEMS = [1, 2, 3, 4].map((id) => ({
  complexId: id,
  complexName: `반응형단지${id}`,
  sido: '서울특별시',
  sigungu: '중구',
  dongRi: '태평로1가',
  representativeHousingType: 'APT',
  representativeDealCategory: 'SALE',
  representativeDealDate: '2026-09-10',
  representativeAmount: 30000 + id * 5000,
  representativeArea: 84.9,
  matchMethod: 'EXACT',
  precisionBadge: 'PRECISE',
  floor: 5,
  latitude: CENTER.lat + (id - 2) * 0.003,
  longitude: CENTER.lng + (id - 2) * 0.004,
  locationPrecision: 'PRECISE',
}));

async function mockApis(page) {
  await page.route('https://dapi.kakao.com/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/javascript', body: FAKE_SDK }),
  );
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/complexes/map') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: okBody({ items: ITEMS, truncated: false, limit: 500 }) });
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: errBody('NOT_FOUND', 'mock 없음') });
  });
}

const pins = (page) => page.locator('[data-testid="map-canvas"] button[data-complex-id]');
const sidebar = (page) => page.getByRole('complementary', { name: '지도 단지 목록' });

/** 지금 지도 객체의 상태 — 같은 객체인지는 표식(__tag)으로 본다. */
const mapSnapshot = (page) =>
  page.evaluate(() => {
    const map = window.__fakeMap;
    return {
      created: window.__fakeMapCreatedCount,
      tag: map.__tag ?? null,
      lat: map.getCenter().getLat(),
      lng: map.getCenter().getLng(),
      level: map.getLevel(),
      relayouts: window.__fakeRelayoutCount ?? 0,
    };
  });

/** 화면 폭을 바꾸고 지도가 새 크기로 relayout될 때까지 기다린다. */
async function resizeTo(page, width, layout) {
  const before = (await mapSnapshot(page)).relayouts;
  await page.setViewportSize({ width, height: 900 });
  if (layout === 'desktop') await expect(sidebar(page)).toBeVisible();
  else await expect(sidebar(page)).toHaveCount(0);
  await expect.poll(async () => (await mapSnapshot(page)).relayouts).toBeGreaterThan(before);
}

test('767 → 1024 → 767: 지도 객체·위치·레벨·핀·URL·히스토리가 그대로이고 뒤로가기는 이전 화면으로', async ({ page }) => {
  await mockApis(page);
  await page.setViewportSize({ width: 767, height: 900 });
  await page.goto('/privacy');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

  // 앱 안 이동(하단 탭)으로 지도에 들어간다 — 뒤로가기가 이 화면으로 돌아와야 한다.
  await page.getByRole('link', { name: '지도', exact: true }).click();
  await expect(page).toHaveURL(/\/map\?.*lat=/);
  await expect(pins(page)).toHaveCount(ITEMS.length);

  // 시작 위치가 아닌 상태로 만든다 — 확대 한 단계.
  await page.getByRole('button', { name: '지도 확대' }).click();
  await expect(page).toHaveURL(/level=5/);
  await expect(pins(page)).toHaveCount(ITEMS.length);

  // 지도 객체와 핀 DOM에 표식을 단다 — 다시 만들어지면 표식이 사라진다.
  await page.evaluate(() => {
    window.__fakeMap.__tag = 'original';
    document.querySelector('[data-testid="map-canvas"] button[data-complex-id="1"]').dataset.e2eTag = 'original';
  });
  const start = await mapSnapshot(page);
  expect(start.created).toBe(1);
  expect(start.level).toBe(5);
  const startUrl = page.url();
  const startHistory = await page.evaluate(() => window.history.length);

  for (const [width, layout] of [
    [1024, 'desktop'],
    [767, 'mobile'],
  ]) {
    await resizeTo(page, width, layout);
    const now = await mapSnapshot(page);
    expect(now.created, `${width}px: 지도를 다시 만들지 않는다`).toBe(1);
    expect(now.tag).toBe('original');
    expect(now.level).toBe(start.level);
    expect(now.lat).toBeCloseTo(start.lat, 9);
    expect(now.lng).toBeCloseTo(start.lng, 9);
    await expect(pins(page)).toHaveCount(ITEMS.length);
    await expect(page.locator('[data-testid="map-canvas"] button[data-complex-id="1"]')).toHaveAttribute('data-e2e-tag', 'original');
    expect(page.url(), `${width}px: URL이 그대로`).toBe(startUrl);
    expect(await page.evaluate(() => window.history.length)).toBe(startHistory);
  }

  await page.goBack();
  await expect(page).toHaveURL(/\/privacy$/);
});

test('지역 검색이 진행 중일 때 768px 경계를 넘어도 검색이 끝나면 그 위치로 옮긴다(백로그 14)', async ({ page }) => {
  await mockApis(page);
  await page.addInitScript(() => {
    window.__FAKE_KAKAO_OPTIONS = { holdGeocode: true };
  });
  await page.setViewportSize({ width: 1024, height: 900 });
  await page.goto('/map?lat=37.5666&lng=126.9784&level=6');
  await expect(pins(page)).toHaveCount(ITEMS.length);

  const search = page.getByPlaceholder('지역명으로 지도 이동');
  await search.fill('경기도 수원시 장안구');
  await search.press('Enter');
  await expect.poll(() => page.evaluate(() => window.__heldGeocodes.length)).toBeGreaterThan(0);

  // 검색 결과를 붙잡은 채 모바일 폭으로 바꾼 뒤 풀어 준다.
  await resizeTo(page, 767, 'mobile');
  await page.evaluate(() => window.__releaseGeocodes());

  await expect.poll(async () => (await mapSnapshot(page)).lat).toBeCloseTo(37.304, 6);
  const now = await mapSnapshot(page);
  expect(now.created).toBe(1);
  expect(now.lng).toBeCloseTo(127.01, 6);
  expect(now.level).toBe(5);
  await expect(page).toHaveURL(/lat=37\.304&lng=127\.01&level=5/);
  await expect(page).toHaveURL(/keyword=/);
});
