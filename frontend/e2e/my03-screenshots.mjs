// MY-03 알림 설정을 3개 뷰포트에서 스크린샷으로 저장 — Figma 35-2/35-247(데스크톱), 36-1017/36-1264(태블릿),
// 36-508/36-755(모바일)와 육안 대조·PR 첨부용. 테스트 아님(단정문 없음). 백엔드 불필요(route로 흉내).
// 결과: ./out/my03-{desktop,tablet,mobile}-{default,zero,mixed,empty,save-failure}.png
import { chromium } from 'playwright';
import { mkdirSync } from 'fs';
import { BASE } from './base.mjs';
import { errBody, okBody } from './mockApi.mjs';

mkdirSync('./out', { recursive: true });
const USER = { userId: 7, email: 'jiseong@homesense.kr', nickname: '지성', createdAt: '2026-09-15T10:20:30' };
const property = (id, name, registeredAt) => ({
  favoritePropertyId: id, complexId: id * 10, complexName: name, sido: '서울특별시', sigungu: '서초구', dongRi: '반포동',
  housingType: 'APT', registeredAt, recentDealCategory: 'SALE', recentDealDate: '2026-09-20', recentAmount: 300000,
  recentArea: 84.98, recentFloor: 9, changeRate: 1.2, hasNotificationSetting: id !== 3,
});
const region = (id, code, sigungu, name, registeredAt) => ({
  favoriteRegionId: id, legalDongCd: code, fullPath: `경기도 ${sigungu} ${name}`, sidoName: '경기도', sigunguName: sigungu,
  eupmyeondongName: name, registeredAt, avgPrice: 90000, changeRate: null, pricePerPyeong: 5000, newTradeCount: 2,
});
const PROPERTIES = [
  property(1, '래미안 원베일리', '2026-09-01T10:00:00'),
  property(2, '아크로리버파크', '2026-09-03T10:00:00'),
  property(3, '반포자이', '2026-09-02T10:00:00'),
];
const REGIONS = [region(11, '4155036000', '안성시', '금광면', '2026-09-02T09:00:00')];
const SETTINGS = [
  { notificationSettingId: 1, favoritePropertyId: 1, priceChangeThresholdPct: 5, newTradeAlertYn: true, emailAlertYn: true },
  { notificationSettingId: 2, favoritePropertyId: 2, priceChangeThresholdPct: 10, newTradeAlertYn: false, emailAlertYn: false },
];

const browser = await chromium.launch();
async function open(viewport, query, { empty = false, putFail = false } = {}) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript(() => {
    localStorage.setItem('homesense.accessToken', 'A');
    localStorage.setItem('homesense.refreshToken', 'R');
  });
  await context.route('**/api/**', (route) => {
    const request = route.request();
    const p = new URL(request.url()).pathname;
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body });
    if (p === '/api/users/me') return json(200, okBody(USER));
    if (p === '/api/favorites/properties') return json(200, okBody(empty ? [] : PROPERTIES));
    if (p === '/api/favorites/regions') return json(200, okBody(empty ? [] : REGIONS));
    if (p === '/api/notifications/settings' && request.method() === 'PUT' && putFail) {
      return json(500, errBody('INTERNAL_SERVER_ERROR', '알림 설정을 저장하지 못했습니다. 잠시 후 다시 시도해주세요'));
    }
    if (p === '/api/notifications/settings') return json(200, okBody(empty ? [] : SETTINGS));
    return json(200, okBody([]));
  });
  const page = await context.newPage();
  await page.goto(`${BASE}/notifications/settings${query}`);
  return { context, page };
}
const ready = (page) => page.getByRole('heading', { name: '대상 선택' }).waitFor({ timeout: 10000 });
async function setSlider(page, value) {
  await page.getByRole('slider', { name: '변동 임계치' }).evaluate((el, v) => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, String(v));
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }, value);
}

for (const vp of [
  { name: 'mobile', viewport: { width: 390, height: 844 } },
  { name: 'tablet', viewport: { width: 768, height: 1024 } },
  { name: 'desktop', viewport: { width: 1280, height: 900 } },
]) {
  const base = await open(vp.viewport, '?favoritePropertyId=1');
  await ready(base.page);
  await base.page.evaluate(() => window.scrollTo(0, 0));
  await base.page.screenshot({ path: `./out/my03-${vp.name}-default.png`, fullPage: true });
  await setSlider(base.page, 0);
  await base.page.waitForTimeout(350);
  await base.page.evaluate(() => window.scrollTo(0, 0));
  await base.page.screenshot({ path: `./out/my03-${vp.name}-zero.png`, fullPage: true });
  await base.context.close();

  const mixed = await open(vp.viewport, '?favoritePropertyId=1');
  await ready(mixed.page);
  await mixed.page.locator('label[data-target-key="property:2"]').click();
  await mixed.page.locator('label[data-target-key="region:11"]').click();
  await mixed.page.getByText(/같은 설정이 적용됩니다/).waitFor({ timeout: 5000 });
  await mixed.page.waitForTimeout(300);
  await mixed.page.evaluate(() => window.scrollTo(0, 0));
  await mixed.page.screenshot({ path: `./out/my03-${vp.name}-mixed.png`, fullPage: true });
  await mixed.context.close();

  const empty = await open(vp.viewport, '', { empty: true });
  await empty.page.getByText('알림을 받을 관심 매물·지역이 없어요').waitFor({ timeout: 10000 });
  await empty.page.screenshot({ path: `./out/my03-${vp.name}-empty.png`, fullPage: true });
  await empty.context.close();

  const failure = await open(vp.viewport, '?favoritePropertyId=3', { putFail: true });
  await ready(failure.page);
  await failure.page.getByRole('button', { name: '저장', exact: true }).click();
  await failure.page.getByRole('alert').filter({ hasText: '저장하지 못했습니다' }).waitFor({ timeout: 5000 });
  await failure.page.screenshot({ path: `./out/my03-${vp.name}-save-failure.png`, fullPage: true });
  await failure.context.close();
}
await browser.close();
console.log('saved ./out/my03-*.png');
