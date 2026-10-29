// [MY-04] 모바일 무한 스크롤 — 알림을 눌러 이동했다가 뒤로 오면 받은 페이지들과 스크롤 위치가 그대로고, 이미 받은 페이지를 다시
// 요청하지 않는다. 메뉴로 새로 들어오면 처음부터다. 백엔드 불필요(/api/** 는 page.route 목업).
import { expect, test } from '@playwright/test';
import { errBody, okBody } from '../mockApi.mjs';
import { ACCESS_KEY, REFRESH_KEY, fakeJwt } from './helpers.mjs';

const MOBILE = { width: 390, height: 844 };
const TOTAL = 60;

const NOTIFICATIONS = Array.from({ length: TOTAL }, (_, index) => {
  const id = TOTAL - index;
  return {
    notificationId: id,
    notificationType: 'NEW_TRADE',
    title: `복원단지${id} 신규 실거래 1건`,
    complexId: 2000 + id,
    isRead: true,
    createdAt: `2026-09-${String(1 + (id % 28)).padStart(2, '0')}T03:00:00`,
  };
}).sort((a, b) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : b.notificationId - a.notificationId));

async function setUp(page) {
  const listGets = [];
  await page.addInitScript(
    ([accessKey, refreshKey, access]) => {
      if (!sessionStorage.getItem('e2e-seeded')) {
        localStorage.setItem(accessKey, access);
        localStorage.setItem(refreshKey, 'R7');
        sessionStorage.setItem('e2e-seeded', '1');
      }
    },
    [ACCESS_KEY, REFRESH_KEY, fakeJwt(7, 'a')],
  );
  await page.route('**/api/**', (route) => {
    const url = new URL(route.request().url());
    const ok = (data, pageMeta) => route.fulfill({ status: 200, contentType: 'application/json', body: okBody(data, pageMeta) });
    if (url.pathname === '/api/users/me') {
      return ok({ userId: 7, email: 'restore@test.com', nickname: '복원', createdAt: '2026-09-01T00:00:00' });
    }
    if (url.pathname === '/api/notifications' && route.request().method() === 'GET') {
      const pageNo = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      if (size === 20) listGets.push(pageNo); // MY-01 미리보기(size=3)는 세지 않는다
      return ok(NOTIFICATIONS.slice(pageNo * size, pageNo * size + size), {
        page: pageNo,
        size,
        totalElements: TOTAL,
        totalPages: Math.ceil(TOTAL / size),
      });
    }
    if (url.pathname === '/api/favorites/properties') return ok([]);
    return route.fulfill({ status: 404, contentType: 'application/json', body: errBody('NOT_FOUND', 'mock 없음') });
  });
  return { listGets };
}

const rows = (page) => page.locator('ul[aria-label="알림 목록"] > li');
const scrollY = (page) => page.evaluate(() => window.scrollY);

test('알림을 눌러 이동했다가 뒤로 오면 받은 페이지·스크롤 위치가 그대로고 다시 요청하지 않는다', async ({ page }) => {
  await page.setViewportSize(MOBILE);
  const { listGets } = await setUp(page);
  await page.goto('/notifications');
  await expect(rows(page)).toHaveCount(20);

  // 끝까지 내려 세 페이지를 모두 받는다.
  await expect(async () => {
    await page.mouse.wheel(0, 4000);
    await expect(rows(page)).toHaveCount(TOTAL, { timeout: 500 });
  }).toPass();
  expect(listGets).toEqual([0, 1, 2]);

  // 가운데쯤으로 스크롤하고(스크롤 이벤트로 저장된다), 화면 가운데의 행을 누른다.
  await page.evaluate(() => window.scrollTo(0, 2400));
  await expect.poll(() => scrollY(page)).toBe(2400);
  const point = { x: 195, y: 420 };
  const targetText = await page.evaluate(({ x, y }) => document.elementFromPoint(x, y).closest('li').textContent, point);

  await page.mouse.click(point.x, point.y);
  await expect(page).toHaveURL(/\/complexes\/\d+$/);

  await page.goBack();
  await expect(page).toHaveURL(/\/notifications$/);
  await expect(rows(page)).toHaveCount(TOTAL);
  await expect.poll(() => scrollY(page)).toBe(2400);
  const restoredText = await page.evaluate(({ x, y }) => document.elementFromPoint(x, y).closest('li').textContent, point);
  expect(restoredText).toBe(targetText);
  expect(listGets).toEqual([0, 1, 2]);
});

test('메뉴로 새로 들어오면 처음부터 받는다', async ({ page }) => {
  await page.setViewportSize(MOBILE);
  const { listGets } = await setUp(page);
  await page.goto('/notifications');
  await expect(rows(page)).toHaveCount(20);
  await expect(async () => {
    await page.mouse.wheel(0, 4000);
    expect(await rows(page).count()).toBeGreaterThanOrEqual(40);
  }).toPass();

  await page.goto('/my');
  await page.getByRole('link', { name: '알림 이력' }).click();
  await expect(page).toHaveURL(/\/notifications$/);
  await expect(rows(page)).toHaveCount(20);
  expect(await scrollY(page)).toBe(0);
  expect(listGets.filter((p) => p === 0)).toHaveLength(2);
});
