// 공용 Modal을 쓰는 다이얼로그 5개(MY-02 매물·지역 삭제, DTL-01 거래상세, MY-01 로그아웃·회원탈퇴 선택, MY-01 회원탈퇴)를
// 390/768/1024/1280에서 열어 너비·버튼 배치를 출력하고 스크린샷을 남긴다. 테스트 아님(단정문 없음) — Modal 확인형/내용형
// 구분을 바꿀 때 전후 비교용. 백엔드 불필요(route로 흉내). 결과: 표준 출력 표 + ./out/modal-{이름}-{너비}.png
import { chromium } from 'playwright';
import { mkdirSync } from 'fs';
import { BASE } from './base.mjs';
import { okBody } from './mockApi.mjs';

mkdirSync('./out', { recursive: true });
const USER = { userId: 7, email: 'jiseong@homesense.kr', nickname: '지성', createdAt: '2026-09-15T10:20:30' };
const COMPLEX_ID = 900001;
const DETAIL = {
  complexId: COMPLEX_ID, complexName: '모킹테스트아파트', complexType: '아파트', housingType: 'APT', sido: '경기도', sigungu: '수원장안구',
  dongRi: '조원동', legalDongCd: '4111112900', legalDongAddress: '경기도 수원장안구 조원동 881', matchPending: false, matchMethod: 'EXACT',
  basicInfo: { householdCount: 1000, buildingCount: 10, approvalDate: '2001-05-01', totalParkingCount: 1200, highestFloor: 25 }, extendedInfo: {},
};
const SALE = [{ tradeId: 5000, dealDate: '2026-09-20', dealCategory: 'SALE', excluUseArea: 84.98, floor: 5, dealAmount: 50000, dealingType: 'AGENT', isCancelled: false, isRegistered: true }];
const TRADE = {
  tradeId: 5000, dealDate: '2026-09-20', excluUseArea: 84.98, floor: 5, dealCategory: 'SALE', dealAmount: 50000, aptDong: '101',
  registrationDate: '2026-09-28', dealingType: 'AGENT', sellerType: '개인', buyerType: '법인', isCancelled: false, landLeaseYn: false,
};
const PROPERTIES = [{
  favoritePropertyId: 1, complexId: 10, complexName: '래미안 원베일리', sido: '서울특별시', sigungu: '서초구', dongRi: '반포동', housingType: 'APT',
  registeredAt: '2026-09-01T10:00:00', recentDealCategory: 'SALE', recentDealDate: '2026-09-20', recentAmount: 425000, recentArea: 84.98, recentFloor: 9,
  changeRate: 3.2, hasNotificationSetting: false,
}];
const REGIONS = [{
  favoriteRegionId: 11, legalDongCd: '4155036000', fullPath: '경기도 안성시 금광면', sidoName: '경기도', sigunguName: '안성시', eupmyeondongName: '금광면',
  registeredAt: '2026-09-02T09:00:00', avgPrice: 90000, changeRate: 1.5, pricePerPyeong: 5123.4, newTradeCount: 4,
}];

const browser = await chromium.launch();

async function open(width, path) {
  const context = await browser.newContext({ viewport: { width, height: 900 } });
  await context.addInitScript(() => {
    localStorage.setItem('homesense.accessToken', 'A');
    localStorage.setItem('homesense.refreshToken', 'R');
  });
  await context.route('**/api/**', (route) => {
    const url = new URL(route.request().url());
    const p = url.pathname;
    const json = (body) => route.fulfill({ status: 200, contentType: 'application/json', body });
    if (p === '/api/users/me') return json(okBody(USER));
    if (p === `/api/complexes/${COMPLEX_ID}`) return json(okBody(DETAIL));
    if (p === '/api/trades') return json(okBody(url.searchParams.get('dealType') === 'SALE' ? SALE : []));
    if (/^\/api\/trades\/\d+$/.test(p)) return json(okBody(TRADE));
    if (p === '/api/favorites/properties') return json(okBody(PROPERTIES));
    if (p === '/api/favorites/regions') return json(okBody(REGIONS));
    return json(okBody([]));
  });
  const page = await context.newPage();
  await page.goto(`${BASE}${path}`);
  return { context, page };
}

/** 열린 다이얼로그의 너비와 버튼 배치(두 버튼 이상이면 첫 두 버튼의 y가 같으면 가로). */
async function measure(dialog) {
  return dialog.evaluate((el) => {
    const buttons = [...el.querySelectorAll('button')].filter((b) => b.offsetParent !== null);
    const boxes = buttons.slice(-2).map((b) => b.getBoundingClientRect());
    const row = boxes.length === 2 && Math.abs(boxes[0].top - boxes[1].top) < 1;
    return { width: Math.round(el.getBoundingClientRect().width), buttons: boxes.length < 2 ? '-' : row ? '가로' : '세로' };
  });
}

const DIALOGS = [
  {
    name: 'my02-property', path: '/favorites', run: async (page) => {
      await page.getByRole('button', { name: '래미안 원베일리 관심 매물 삭제' }).click();
      return page.getByRole('alertdialog');
    },
  },
  {
    name: 'my02-region', path: '/favorites', run: async (page, width) => {
      if (width < 1280) await page.getByRole('tab', { name: /관심 지역/ }).click();
      await page.getByRole('button', { name: '금광면 관심 지역 삭제' }).click();
      return page.getByRole('alertdialog');
    },
  },
  {
    name: 'dtl01-trade', path: `/complexes/${COMPLEX_ID}`, run: async (page) => {
      const row = page.locator('tbody tr').first();
      await row.waitFor({ timeout: 10000 });
      await row.click();
      const dialog = page.getByRole('dialog', { name: '거래 상세' });
      await dialog.getByText('매도자').waitFor();
      return dialog;
    },
  },
  {
    name: 'my01-choice', path: '/my', run: async (page) => {
      await page.getByRole('button', { name: '로그아웃·회원탈퇴' }).click();
      return page.getByRole('dialog', { name: '로그아웃·회원탈퇴' });
    },
  },
  {
    name: 'my01-withdraw', path: '/my', run: async (page) => {
      await page.getByRole('button', { name: '로그아웃·회원탈퇴' }).click();
      await page.getByRole('dialog', { name: '로그아웃·회원탈퇴' }).getByRole('button', { name: '회원탈퇴' }).click();
      return page.getByRole('alertdialog', { name: '회원탈퇴' });
    },
  },
];

const rows = [];
for (const d of DIALOGS) {
  for (const width of [390, 768, 1024, 1280]) {
    const { context, page } = await open(width, d.path);
    const dialog = await d.run(page, width);
    await dialog.waitFor();
    const m = await measure(dialog);
    await page.screenshot({ path: `./out/modal-${d.name}-${width}.png` });
    rows.push(`${d.name.padEnd(14)} ${String(width).padStart(4)}px  너비 ${m.width}  버튼 ${m.buttons}`);
    await context.close();
  }
}
console.log(rows.join('\n'));
await browser.close();
