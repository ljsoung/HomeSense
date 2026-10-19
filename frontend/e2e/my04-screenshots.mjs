// MY-04 알림 이력 스크린샷(테스트 아님) — 390/768/1280의 안 읽음·읽음 혼재 목록, 유형 필터 빈 상태, 전체 빈 상태,
// 모바일 끝 푸터, 목록 조회 실패를 ./out/my04-{desktop,tablet,mobile}-*.png에 저장한다. 백엔드 불필요(모킹).
import { mkdirSync } from 'node:fs';
import { chromium } from 'playwright';
import { BASE } from './base.mjs';
import { errBody, okBody } from './mockApi.mjs';

mkdirSync('out', { recursive: true });

const NOTIFICATIONS = [
  { notificationId: 9, notificationType: 'PRICE_CHANGE', title: '힐스테이트 안성 실거래가 2.1% 상승', complexId: 10, isRead: false, createdAt: '2026-10-15T09:00:00' },
  { notificationId: 8, notificationType: 'NEW_TRADE', title: '경기도 안성시 금광면 신규 실거래 3건', legalDongCd: '4155036000', isRead: false, createdAt: '2026-10-15T09:00:00' },
  { notificationId: 7, notificationType: 'PRICE_CHANGE', title: '래미안 원베일리 실거래가 1.4% 하락', complexId: 11, isRead: false, createdAt: '2026-10-14T03:00:00' },
  { notificationId: 6, notificationType: 'NEW_TRADE', title: '아크로리버파크 신규 실거래 1건', complexId: 12, isRead: true, createdAt: '2026-10-12T03:00:00' },
  { notificationId: 5, notificationType: 'PRICE_CHANGE', title: '반포자이 실거래가 0.8% 상승', complexId: 13, isRead: true, createdAt: '2026-10-05T03:00:00' },
  { notificationId: 4, notificationType: 'NEW_TRADE', title: '서울특별시 종로구 창신동 신규 실거래 2건', legalDongCd: '1111017500', isRead: true, createdAt: '2026-09-28T03:00:00' },
  { notificationId: 3, notificationType: 'PRICE_CHANGE', title: '신반포센트럴자이 실거래가 3.0% 상승', complexId: 14, isRead: true, createdAt: '2026-09-20T03:00:00' },
];
const USER = { userId: 7, email: 'jiseong@homesense.kr', nickname: '지성', createdAt: '2026-09-15T10:20:30' };

const browser = await chromium.launch();

async function shot(viewport, name, { path = '/notifications', items = NOTIFICATIONS, fail = false, fullPage = true, scrollBottom = false } = {}) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript(() => {
    localStorage.setItem('homesense.accessToken', 'A');
    localStorage.setItem('homesense.refreshToken', 'R');
  });
  await context.route('**/api/**', (route) => {
    const url = new URL(route.request().url());
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body });
    if (url.pathname === '/api/users/me') return json(200, okBody(USER));
    if (url.pathname === '/api/notifications') {
      if (fail) return json(500, errBody('INTERNAL_SERVER_ERROR', '알림을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'));
      const type = url.searchParams.get('type');
      const list = items.filter((n) => !type || n.notificationType === type);
      return json(200, okBody(list, { page: 0, size: 20, totalElements: list.length, totalPages: list.length ? 1 : 0 }));
    }
    return json(404, errBody('NOT_FOUND', 'mock 없음'));
  });
  const page = await context.newPage();
  await page.goto(`${BASE}${path}`);
  await page.waitForTimeout(800);
  if (scrollBottom) {
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await page.waitForTimeout(300);
  }
  await page.screenshot({ path: `out/my04-${name}.png`, fullPage: fullPage && !scrollBottom });
  console.log(`out/my04-${name}.png`);
  await context.close();
}

for (const [label, viewport] of [
  ['desktop', { width: 1280, height: 900 }],
  ['tablet', { width: 768, height: 1024 }],
  ['mobile', { width: 390, height: 844 }],
]) {
  await shot(viewport, `${label}-list`);
  await shot(viewport, `${label}-filter-empty`, { path: '/notifications?type=NEW_TRADE', items: NOTIFICATIONS.filter((n) => n.notificationType === 'PRICE_CHANGE') });
  await shot(viewport, `${label}-empty`, { items: [] });
  await shot(viewport, `${label}-error`, { fail: true });
}
await shot({ width: 390, height: 844 }, 'mobile-end-footer', { scrollBottom: true });

await browser.close();
