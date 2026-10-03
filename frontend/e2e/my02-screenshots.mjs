// MY-02 관심 매물·지역 관리를 3개 뷰포트에서 스크린샷으로 저장 — Figma 6-4695/6-4995/6-5286(데스크톱),
// 28-16161/29-16508/29-16879(태블릿), 27-15395/28-15713/28-16055(모바일)과 육안 대조·PR 첨부용. 테스트 아님(단정문 없음).
// 백엔드 불필요(route로 흉내). 결과: ./out/my02-{desktop,tablet,mobile}-{list,regions,dialog,dialog-region,empty}.png
import { chromium } from 'playwright';
import { mkdirSync } from 'fs';
import { BASE } from './base.mjs';

mkdirSync('./out', { recursive: true });
const dropNulls = (_key, value) => (value === null ? undefined : value);
const okBody = (data) => JSON.stringify({ success: true, data, timestamp: '' }, dropNulls);
const USER = { userId: 7, email: 'jiseong@homesense.kr', nickname: '지성', createdAt: '2026-09-15T10:20:30' };
const property = (id, name, amount, rate, registeredAt, area = 84.98, floor = 9) => ({
  favoritePropertyId: id, complexId: id * 10, complexName: name, sido: '서울특별시', sigungu: '서초구', dongRi: '반포동',
  housingType: 'APT', registeredAt, recentDealCategory: amount === null ? null : 'SALE', recentDealDate: '2026-09-20',
  recentAmount: amount, recentArea: amount === null ? null : area, recentFloor: amount === null ? null : floor, changeRate: rate,
  hasNotificationSetting: id !== 3,
});
const region = (id, code, sigungu, name, rate, perPyeong, count, registeredAt) => ({
  favoriteRegionId: id, legalDongCd: code, fullPath: `경기도 ${sigungu} ${name}`, sidoName: '경기도', sigunguName: sigungu,
  eupmyeondongName: name, registeredAt, avgPrice: 90000, changeRate: rate, pricePerPyeong: perPyeong, newTradeCount: count,
});
const PROPERTIES = [
  property(1, '래미안 원베일리', 425000, 3.2, '2026-09-01T10:00:00'),
  property(2, '아크로리버파크', 389000, -1.1, '2026-09-03T10:00:00', 59.9, 3),
  property(3, '반포자이', null, null, '2026-09-02T10:00:00'),
  property(4, '신반포센트럴자이', 280000, 0, '2026-08-30T10:00:00'),
];
const REGIONS = [
  region(11, '4155036000', '안성시', '금광면', 1.5, 5123.4, 4, '2026-09-02T09:00:00'),
  region(12, '4111113500', '수원시 장안구', '파장동', -0.8, 6210, 12, '2026-09-04T09:00:00'),
];
const SETTINGS = [
  { notificationSettingId: 1, favoritePropertyId: 1, priceChangeThresholdPct: 5, newTradeAlertYn: true, emailAlertYn: true },
  { notificationSettingId: 2, favoritePropertyId: 2, priceChangeThresholdPct: 2.5, newTradeAlertYn: false, emailAlertYn: true },
  { notificationSettingId: 3, favoritePropertyId: 4, priceChangeThresholdPct: 5, newTradeAlertYn: true, emailAlertYn: false },
];

const browser = await chromium.launch();
const viewports = [
  { name: 'desktop', width: 1280, height: 1100 },
  { name: 'tablet', width: 768, height: 1100 },
  { name: 'mobile', width: 390, height: 844 },
];

async function open(vp, empty) {
  const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
  await context.addInitScript(() => {
    localStorage.setItem('homesense.accessToken', 'A');
    localStorage.setItem('homesense.refreshToken', 'R');
  });
  await context.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    const json = (body) => route.fulfill({ status: 200, contentType: 'application/json', body });
    if (path === '/api/users/me') return json(okBody(USER));
    if (path === '/api/favorites/properties') return json(okBody(empty ? [] : PROPERTIES));
    if (path === '/api/favorites/regions') return json(okBody(empty ? [] : REGIONS));
    if (path === '/api/notifications/settings') return json(okBody(SETTINGS));
    return json(okBody([]));
  });
  const page = await context.newPage();
  await page.goto(`${BASE}/favorites`);
  await page.getByRole('heading', { level: 1 }).waitFor({ timeout: 10000 });
  return { context, page };
}

for (const vp of viewports) {
  const { context, page } = await open(vp, false);
  await page.getByRole('link', { name: /알림 조건/ }).first().waitFor({ timeout: 5000 });
  await page.screenshot({ path: `./out/my02-${vp.name}-list.png`, fullPage: true });
  if (vp.name !== 'desktop') {
    await page.getByRole('tab', { name: /관심 지역/ }).click();
    await page.getByText('금광면').waitFor();
    await page.screenshot({ path: `./out/my02-${vp.name}-regions.png`, fullPage: true });
    await page.getByRole('tab', { name: /관심 매물/ }).click();
  }
  await page.getByRole('button', { name: '래미안 원베일리 관심 매물 삭제' }).click();
  await page.getByRole('alertdialog').waitFor();
  await page.screenshot({ path: `./out/my02-${vp.name}-dialog.png` });
  await page.keyboard.press('Escape');
  await page.getByRole('alertdialog').waitFor({ state: 'detached' });
  if (vp.name !== 'desktop') await page.getByRole('tab', { name: /관심 지역/ }).click();
  await page.getByRole('button', { name: '금광면 관심 지역 삭제' }).click();
  await page.getByRole('alertdialog').waitFor();
  await page.screenshot({ path: `./out/my02-${vp.name}-dialog-region.png` });
  await context.close();

  const empty = await open(vp, true);
  await empty.page.getByText('아직 등록한 관심 매물·지역이 없어요').waitFor();
  await empty.page.screenshot({ path: `./out/my02-${vp.name}-empty.png`, fullPage: true });
  await empty.context.close();
  console.log(`saved my02-${vp.name}-*`);
}
await browser.close();
