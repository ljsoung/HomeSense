// SCR-MAP-01 — 지역 검색 직후 다음 검색어를 입력하면, 앞 검색의 URL 반영(전환 렌더)이 늦게 와도 그 입력을 지우지 않는다(백로그 17).
// 느린 기기를 흉내 내려고 CPU를 6배 느리게 한다 — 그러면 Enter 뒤 URL 반영이 250ms쯤 늦게 와, 그사이 붙여 넣은 검색어가
// 수정 전 코드에서는 앞 검색어로 되돌아가 다음 Enter가 앞 검색을 다시 보냈다. 지오코딩은 붙잡아 두었다가 풀어 준다(holdGeocode).
// 카카오 SDK는 가짜(fakeKakaoSdk.js), /api/** 는 page.route 목업. 백엔드 불필요. CDP를 쓰므로 Chromium 전용.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import { errBody, okBody } from '../mockApi.mjs';

const FAKE_SDK = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'fakeKakaoSdk.js'), 'utf8');

const FIRST = '서울특별시 강남구';
const SECOND = '경기도 수원시 장안구';
const SECOND_LAT = 37.304; // 가짜 SDK가 SECOND에 돌려주는 좌표

const ITEM = {
  complexId: 1,
  complexName: '검색단지',
  sido: '서울특별시',
  sigungu: '중구',
  dongRi: '태평로1가',
  representativeHousingType: 'APT',
  representativeDealCategory: 'SALE',
  representativeDealDate: '2026-09-10',
  representativeAmount: 35000,
  representativeArea: 84.9,
  matchMethod: 'EXACT',
  precisionBadge: 'PRECISE',
  floor: 5,
  latitude: 37.5666,
  longitude: 126.9784,
  locationPrecision: 'PRECISE',
};

test('느린 기기에서 검색 직후 붙여 넣은 다음 검색어가 지워지지 않는다', async ({ page, context }) => {
  test.setTimeout(120_000);
  await page.setViewportSize({ width: 1280, height: 800 });
  // 지오코딩 결과를 붙잡아 두어, 두 검색 모두 진행 중인 상태에서 입력을 확인한 뒤 풀어 준다.
  await page.addInitScript(() => {
    window.__FAKE_KAKAO_OPTIONS = { holdGeocode: true };
  });
  await page.route('https://dapi.kakao.com/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/javascript', body: FAKE_SDK }),
  );
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/complexes/map') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: okBody({ items: [ITEM], truncated: false, limit: 500 }) });
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: errBody('NOT_FOUND', 'mock 없음') });
  });

  await page.goto('/map');
  const search = page.getByPlaceholder('지역명으로 지도 이동');
  await expect(page.locator('[data-testid="map-canvas"] button[data-complex-id="1"]')).toBeVisible();
  const cdp = await context.newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 6 });

  const urlKeyword = () => new URL(page.url()).searchParams.get('keyword');
  // 한 번에 재현되지 않을 수 있어(전환 렌더 시점이 매번 다르다) 같은 순서를 여덟 번 되풀이한다 — 수정 전 코드로 되돌리면 실행 6번 중 5번 실패했다(2026-10-28).
  for (let round = 1; round <= 8; round++) {
    await search.fill(FIRST);
    await search.press('Enter');
    await page.keyboard.press('Control+A');
    await page.keyboard.insertText(SECOND);
    // 사람이 붙여 넣고 Enter를 누르기까지의 짧은 틈 — 애니메이션 프레임 몇 개(느린 CPU라 수십 ms). 이 사이에 앞 검색의 URL
    // 반영이 끼어든다.
    await page.evaluate(() => new Promise((resolve) => {
      let frames = 4;
      const tick = () => (--frames > 0 ? requestAnimationFrame(tick) : resolve());
      requestAnimationFrame(tick);
    }));
    await page.keyboard.press('Enter');
    await expect.poll(urlKeyword, { message: `${round}회차: 두 번째 검색어가 URL에 남는다` }).toBe(SECOND);
    await expect(search, `${round}회차: 검색창이 두 번째 검색어 그대로다`).toHaveValue(SECOND);
    // 두 지오코딩을 함께 풀면 앞 검색은 밀린 검색이라 버려지고 지도는 두 번째 지역으로 간다.
    await page.evaluate(() => window.__releaseGeocodes());
    await expect
      .poll(() => page.evaluate(() => window.__fakeMap.getCenter().getLat()), { message: `${round}회차: 지도가 두 번째 지역으로 간다`, timeout: 10_000 })
      .toBeCloseTo(SECOND_LAT, 2);
    expect(urlKeyword(), `${round}회차: 검색이 끝난 뒤에도 URL 그대로`).toBe(SECOND);
    await expect(search).toHaveValue(SECOND);
  }
});
