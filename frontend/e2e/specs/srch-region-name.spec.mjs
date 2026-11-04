// [SRCH-01] URL에 regionCode만 있고 regionLabel이 없을 때(직접 진입·새로고침·MY-04 알림 링크) 코드로 지역 이름을 조회해
// 검색창·제목·지도 링크에 쓴다. 없는 코드(404)는 이름 없이 검색만 한다. 이름 조회는 검색을 다시 부르지 않고, 그사이 입력한
// 검색어를 덮지 않는다. 백엔드 불필요(/api/** 는 page.route 목업, 이름 조회 응답은 gate로 붙잡는다).
import { expect, test } from '@playwright/test';
import { errBody, okBody } from '../mockApi.mjs';
import { gate } from './helpers.mjs';

const COMPLEXES = [1, 2].map((id) => ({
  complexId: id,
  complexName: `이름조회단지${id}`,
  sido: '경기도',
  sigungu: '수원장안구',
  dongRi: '파장동',
  householdCount: 500,
  approvalDate: '2005-06-01',
  representativeHousingType: 'APT',
  representativeDealCategory: 'SALE',
  representativeDealDate: '2026-09-10',
  representativeAmount: 50000,
  representativeArea: 84.9,
  matchMethod: 'EXACT',
  precisionBadge: 'PRECISE',
  floor: 7,
}));

const CODE = '4111112900';
const NAME = '경기도 수원시 장안구 파장동';

/** /api/** 목업. lookup이 'hold'면 이름 조회 응답을 붙잡는다. */
async function mockApis(page, { lookup }) {
  const calls = { search: [], lookup: [] };
  const held = gate();
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const ok = (data, pageMeta) => route.fulfill({ status: 200, contentType: 'application/json', body: okBody(data, pageMeta) });
    if (url.pathname === '/api/complexes/search') {
      calls.search.push(url.search);
      return ok(COMPLEXES, { page: 0, size: 20, totalElements: COMPLEXES.length, totalPages: 1 });
    }
    if (url.pathname.startsWith('/api/regions/') && url.pathname !== '/api/regions/interest-summary') {
      calls.lookup.push(url.pathname);
      if (lookup === 'hold') await held.promise;
      if (lookup === 'missing') {
        return route.fulfill({ status: 404, contentType: 'application/json', body: errBody('REGION_NOT_FOUND', '존재하지 않는 지역입니다') });
      }
      return ok({ legalDongCd: CODE, fullPath: NAME });
    }
    if (url.pathname === '/api/users/me') {
      return route.fulfill({ status: 401, contentType: 'application/json', body: errBody('UNAUTHORIZED', '인증이 필요합니다') });
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: errBody('NOT_FOUND', 'mock 없음') });
  });
  return { calls, release: held.release };
}

const searchBox = (page) => page.getByPlaceholder('지역명·단지명(건물명)으로 재검색');
const title = (page) => page.getByText(/검색 결과$/);

test('코드만 있는 URL로 들어오면 이름을 조회해 검색창·제목·지도 링크에 쓰고, 검색은 한 번만 부른다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  const { calls, release } = await mockApis(page, { lookup: 'hold' });
  await page.goto(`/search?regionCode=${CODE}`);

  // 이름이 오기 전: 검색은 이미 돌고, 제목은 "선택한 지역", 검색창은 비어 있다.
  await expect(page.getByText('이름조회단지1')).toBeVisible();
  await expect(title(page)).toContainText('선택한 지역');
  await expect(searchBox(page)).toHaveValue('');
  expect(calls.lookup).toEqual([`/api/regions/${CODE}`]);

  release();
  await expect(title(page)).toContainText(NAME);
  await expect(searchBox(page)).toHaveValue(NAME);
  // URL은 그대로(이름을 쓰지 않는다) — 검색 조회가 다시 돌지 않는다.
  expect(new URL(page.url()).searchParams.get('regionLabel')).toBeNull();
  expect(calls.search).toHaveLength(1);
  // 지도 링크는 이름을 함께 넘겨 MAP-01이 그 지역으로 연다.
  const mapHref = await page.locator(`main a[href^="/map?"][href*="regionCode=${CODE}"]`).first().getAttribute('href');
  expect(new URLSearchParams(mapHref.split('?')[1]).get('regionLabel')).toBe(NAME);
});

test('없거나 폐지된 코드(404)면 이름 없이 검색만 한다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const { calls } = await mockApis(page, { lookup: 'missing' });
  await page.goto('/search?regionCode=2811000000');

  await expect(page.getByText('이름조회단지1')).toBeVisible();
  await expect.poll(() => calls.lookup.length).toBe(1);
  await expect(title(page)).toContainText('선택한 지역');
  await expect(searchBox(page)).toHaveValue('');
  expect(calls.search).toHaveLength(1);
});

test('이름이 오기 전에 입력한 검색어는 덮지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  const { calls, release } = await mockApis(page, { lookup: 'hold' });
  await page.goto(`/search?regionCode=${CODE}`);
  await expect(page.getByText('이름조회단지1')).toBeVisible();
  await expect.poll(() => calls.lookup.length).toBe(1);

  await searchBox(page).fill('래미안');
  release();
  await expect(title(page)).toContainText(NAME);
  await expect(searchBox(page)).toHaveValue('래미안');
});

test('이름을 이미 알면(regionLabel) 조회하지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  const { calls } = await mockApis(page, { lookup: 'ok' });
  await page.goto(`/search?regionCode=${CODE}&regionLabel=${encodeURIComponent(NAME)}`);
  await expect(page.getByText('이름조회단지1')).toBeVisible();
  await expect(title(page)).toContainText(NAME);
  expect(calls.lookup).toEqual([]);
});
