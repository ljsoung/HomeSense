// [SRCH-01] 모바일 무한 스크롤 — 카드를 눌러 상세로 갔다가 뒤로 오면 누적 목록과 스크롤 위치가 그대로고, 이미 받은 페이지를
// 다시 요청하지 않는다. MY-04(specs/my04-scroll-restore)와 같은 방법이다. 백엔드 불필요(/api/** 는 page.route 목업).
import { expect, test } from '@playwright/test';
import { errBody, okBody } from '../mockApi.mjs';

const MOBILE = { width: 390, height: 844 };
const TOTAL = 60;
const SIZE = 20;

const COMPLEXES = Array.from({ length: TOTAL }, (_, index) => ({
  complexId: 3000 + index,
  complexName: `복원단지${index + 1}`,
  sido: '경기도',
  sigungu: '수원장안구',
  dongRi: '파장동',
  approvalDate: '2005-01-01',
  representativeHousingType: 'APT',
  representativeDealCategory: 'SALE',
  representativeDealDate: '2026-09-01',
  representativeAmount: 50000 + index,
  representativeArea: 84.5,
  matchMethod: 'EXACT',
  precisionBadge: 'PRECISE',
  floor: 5,
}));

async function setUp(page) {
  const searchGets = [];
  await page.route('**/api/**', (route) => {
    const url = new URL(route.request().url());
    const ok = (data, pageMeta) => route.fulfill({ status: 200, contentType: 'application/json', body: okBody(data, pageMeta) });
    if (url.pathname === '/api/complexes/search') {
      const pageNo = Number(url.searchParams.get('page') ?? 0);
      searchGets.push(pageNo);
      return ok(COMPLEXES.slice(pageNo * SIZE, pageNo * SIZE + SIZE), {
        page: pageNo,
        size: SIZE,
        totalElements: TOTAL,
        totalPages: TOTAL / SIZE,
      });
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: errBody('NOT_FOUND', 'mock 없음') });
  });
  return { searchGets };
}

const cards = (page) => page.locator('a[href^="/complexes/"]');
const scrollY = (page) => page.evaluate(() => window.scrollY);
const cardAt = (page, point) =>
  page.evaluate(({ x, y }) => document.elementFromPoint(x, y)?.closest('a[href^="/complexes/"]')?.getAttribute('href') ?? null, point);

test('카드를 눌러 상세로 갔다가 뒤로 오면 누적 목록·스크롤 위치가 그대로고 다시 요청하지 않는다', async ({ page }) => {
  await page.setViewportSize(MOBILE);
  const { searchGets } = await setUp(page);
  await page.goto('/search?keyword=%EB%B3%B5%EC%9B%90');
  await expect(cards(page)).toHaveCount(SIZE);

  await expect(async () => {
    await page.mouse.wheel(0, 4000);
    await expect(cards(page)).toHaveCount(TOTAL, { timeout: 500 });
  }).toPass();
  expect([...new Set(searchGets)]).toEqual([0, 1, 2]);
  const getsBefore = searchGets.length;

  await page.evaluate(() => window.scrollTo(0, 2400));
  await expect.poll(() => scrollY(page)).toBe(2400);
  const point = { x: 195, y: 420 };
  const target = await cardAt(page, point);
  expect(target).not.toBeNull();

  await page.mouse.click(point.x, point.y);
  await expect(page).toHaveURL(new RegExp(`${target}$`));

  await page.goBack();
  await expect(page).toHaveURL(/\/search\?/);
  await expect(cards(page)).toHaveCount(TOTAL);
  await expect.poll(() => scrollY(page)).toBe(2400);
  expect(await cardAt(page, point)).toBe(target);
  expect(searchGets.length).toBe(getsBefore);
});
