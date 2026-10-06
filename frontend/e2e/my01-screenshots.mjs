// MY-01 마이페이지 홈을 3개 뷰포트에서 스크린샷으로 저장 — Figma 7:5373(데스크톱)/26:15193(태블릿)/26:14966(모바일)과
// 육안 대조·PR 첨부용. 테스트 아님(단정문 없음). 백엔드 불필요(route로 흉내). 결과: ./out/my01-{desktop,tablet,mobile}.png
import { chromium } from 'playwright';
import { mkdirSync } from 'fs';
import { BASE } from './base.mjs';
import { okBody } from './mockApi.mjs';

mkdirSync('./out', { recursive: true });
const kstAgo = (msAgo) => new Date(Date.now() - msAgo + 9 * 3600_000).toISOString().slice(0, 19);
const favorite = (id, name, amount) => ({
  favoritePropertyId: id, complexId: id * 10, complexName: name, sido: '서울특별시', sigungu: '서초구', dongRi: '반포동',
  housingType: 'APT', recentDealCategory: 'SALE', recentDealDate: '2026-09-01', recentAmount: amount, changeRate: null, hasNotificationSetting: false,
});
// BAT-NTF-01 응답 모양: createdAt(발생 시각), 발송 전이라 sentAt 키 없음. 행에는 title이 보인다.
const notification = (id, title, msAgo, isRead) => ({
  notificationId: id, notificationType: 'PRICE_CHANGE', title, message: '최근 3개월 평균 3.3㎡당 상세', complexId: 10, legalDongCd: null, tradeId: null, isRead, createdAt: kstAgo(msAgo),
});
const USER = { userId: 7, email: 'jiseong@homesense.kr', nickname: '지성', createdAt: '2026-09-15T10:20:30' };
const FAVORITES = [favorite(9, '래미안 원베일리', 425000), favorite(5, '아크로리버파크', 389000)];
const NOTIFICATIONS = [
  notification(31, '래미안 원베일리 실거래가 3.2% 상승', 3 * 3600_000, false),
  notification(30, '서울특별시 서초구 반포동 신규 실거래 2건', 26 * 3600_000, true),
  notification(29, '아크로리버파크 실거래가 1.1% 하락', 3 * 86_400_000, true),
];

const browser = await chromium.launch();
const viewports = [
  { name: 'desktop', width: 1280, height: 1100 },
  { name: 'tablet', width: 768, height: 1100 },
  { name: 'mobile', width: 390, height: 844 },
];
for (const vp of viewports) {
  const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
  await context.addInitScript(() => {
    localStorage.setItem('homesense.accessToken', 'A');
    localStorage.setItem('homesense.refreshToken', 'R');
  });
  await context.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    const json = (body) => route.fulfill({ status: 200, contentType: 'application/json', body });
    if (path === '/api/users/me') return json(okBody(USER));
    if (path === '/api/favorites/properties') return json(okBody(FAVORITES));
    if (path === '/api/notifications') return json(okBody(NOTIFICATIONS, { page: 0, size: 3, totalElements: 3, totalPages: 1 }));
    return json(okBody([]));
  });
  const page = await context.newPage();
  await page.goto(`${BASE}/my`);
  await page.getByText('2026.09.15 가입').waitFor({ timeout: 10000 });
  await page.getByText('래미안 원베일리 매매가가 3.2% 올랐어요').waitFor({ timeout: 10000 });
  await page.waitForTimeout(300);
  await page.screenshot({ path: `./out/my01-${vp.name}.png`, fullPage: true });
  console.log(`saved ./out/my01-${vp.name}.png`);
  await context.close();
}
await browser.close();
