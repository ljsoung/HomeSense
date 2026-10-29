// 주요 화면 목업(/api/** 응답과 가짜 지도 SDK) — 접근성 검사(a11y-contrast.spec)와 스크린숏 스크립트가 같이 쓴다.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { errBody, okBody } from '../mockApi.mjs';
import { ACCESS_KEY, REFRESH_KEY, fakeJwt } from './helpers.mjs';

const FAKE_SDK = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'fakeKakaoSdk.js'), 'utf8');

const daysAgo = (days) => {
  const kst = new Date(Date.now() + 9 * 3600_000 - days * 86400_000);
  return `${kst.toISOString().slice(0, 10)}T09:00:00`;
};

const SUMMARY = (id, overrides = {}) => ({
  complexId: id,
  complexName: `대비단지${id}`,
  sido: '경기도',
  sigungu: '수원장안구',
  dongRi: '정자동',
  householdCount: 500,
  buildingCount: 8,
  approvalDate: '2005-06-01',
  representativeHousingType: 'APT',
  representativeDealCategory: 'SALE',
  representativeDealDate: '2026-09-10',
  representativeAmount: 50000 + id * 1000,
  representativeArea: 84.9,
  matchMethod: id === 2 ? 'SIMILAR' : 'EXACT',
  precisionBadge: id === 2 ? 'APPROXIMATE' : 'PRECISE',
  floor: 7,
  latitude: 37.5666 + id * 0.002,
  longitude: 126.9784 + id * 0.003,
  locationPrecision: 'PRECISE',
  ...overrides,
});
const COMPLEXES = [1, 2, 3, 4].map((id) => SUMMARY(id));

const DETAIL = {
  complexId: 1,
  complexName: '대비단지1',
  complexType: '아파트',
  housingType: 'APT',
  sido: '경기도',
  sigungu: '수원장안구',
  dongRi: '정자동',
  legalDongCd: '4111113000',
  legalDongAddress: '경기도 수원장안구 정자동 123',
  latitude: 37.3,
  longitude: 127.01,
  locationPrecision: 'PRECISE',
  matchPending: false,
  matchMethod: 'SIMILAR',
  precisionBadge: 'APPROXIMATE',
  basicInfo: { householdCount: 500, buildingCount: 8, approvalDate: '2005-06-01', constructor: '대비건설', totalParkingCount: 600, highestFloor: 20 },
  extendedInfo: { supplyType: '분양', managementType: '위탁관리', heatingType: '개별난방', groundParkingCount: 100 },
};
const TRADES = [
  { tradeId: 11, dealDate: daysAgo(10).slice(0, 10), dealCategory: 'SALE', excluUseArea: 84.9, floor: 7, dealAmount: 52000, dealingType: 'AGENT', isCancelled: false, isRegistered: true },
  { tradeId: 12, dealDate: daysAgo(40).slice(0, 10), dealCategory: 'SALE', excluUseArea: 59.9, floor: 3, dealAmount: 41000, dealingType: 'DIRECT', isCancelled: true, cancelDate: daysAgo(30).slice(0, 10), isRegistered: false },
  { tradeId: 13, dealDate: daysAgo(80).slice(0, 10), dealCategory: 'SALE', excluUseArea: 84.9, floor: 12, dealAmount: 50500, dealingType: 'AGENT', isCancelled: false, isRegistered: false },
];

const FAV_PROPERTIES = [
  { favoritePropertyId: 31, complexId: 1, complexName: '대비단지1', sido: '경기도', sigungu: '수원장안구', dongRi: '정자동', housingType: 'APT', registeredAt: daysAgo(3), recentDealCategory: 'SALE', recentDealDate: daysAgo(10).slice(0, 10), recentAmount: 52000, recentArea: 84.9, recentFloor: 7, changeRate: 2.1, hasNotificationSetting: true },
  { favoritePropertyId: 32, complexId: 3, complexName: '대비단지3', sido: '경기도', sigungu: '수원장안구', dongRi: '정자동', housingType: 'VILLA', registeredAt: daysAgo(5), hasNotificationSetting: false },
];
const FAV_REGIONS = [
  { favoriteRegionId: 41, legalDongCd: '1111017500', fullPath: '서울특별시 종로구 창신동', sidoName: '서울특별시', sigunguName: '종로구', eupmyeondongName: '창신동', registeredAt: daysAgo(4), avgPrice: 61000, changeRate: -1.2, pricePerPyeong: 2400, newTradeCount: 3 },
  { favoriteRegionId: 42, legalDongCd: '1168010100', fullPath: '서울특별시 강남구 역삼동', sidoName: '서울특별시', sigunguName: '강남구', eupmyeondongName: '역삼동', registeredAt: daysAgo(6), newTradeCount: 0 },
];
const NOTIFICATIONS = [
  { notificationId: 91, notificationType: 'PRICE_CHANGE', title: '대비단지1 실거래가 2.1% 상승', message: '최근 3개월 평균 → 신규 매매', complexId: 1, isRead: false, createdAt: daysAgo(1) },
  { notificationId: 92, notificationType: 'NEW_TRADE', title: '서울특별시 종로구 창신동 신규 실거래 3건', legalDongCd: '1111017500', tradeId: 99, isRead: true, createdAt: daysAgo(20) },
];
const SETTINGS = [{ notificationSettingId: 51, favoritePropertyId: 31, priceChangeThresholdPct: 5, newTradeAlertYn: true, emailAlertYn: true }];
const USER = { userId: 7, email: 'contrast@test.com', nickname: '대비', createdAt: '2026-09-01T00:00:00' };

/** options.zeroThreshold: 관심 매물 31의 알림 임계치를 0%로(MY-03 0% 경고 상태). */
export async function mockApis(page, loggedIn, options = {}) {
  await page.route('https://dapi.kakao.com/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/javascript', body: FAKE_SDK }),
  );
  await page.route('**/api/**', (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const ok = (data, pageMeta) => route.fulfill({ status: 200, contentType: 'application/json', body: okBody(data, pageMeta) });
    if (path === '/api/complexes/popular') return ok(COMPLEXES);
    if (path === '/api/complexes/search') return ok(COMPLEXES, { page: 0, size: 20, totalElements: 4, totalPages: 1 });
    if (path === '/api/complexes/map') return ok({ items: COMPLEXES, truncated: false, limit: 500 });
    if (path === '/api/complexes/1') return ok(DETAIL);
    if (path === '/api/trades') return ok(TRADES);
    if (path === '/api/search/popular') return ok([{ keyword: '수원시' }, { keyword: '래미안' }]);
    if (path === '/api/recent-views') return ok([{ complexId: 3, complexName: '대비단지3', housingType: 'APT', sigungu: '수원장안구', dongRi: '정자동', viewedAt: daysAgo(1) }]);
    if (!loggedIn) {
      if (path === '/api/users/me') return route.fulfill({ status: 401, contentType: 'application/json', body: errBody('UNAUTHORIZED', '인증이 필요합니다') });
      return route.fulfill({ status: 404, contentType: 'application/json', body: errBody('NOT_FOUND', 'mock 없음') });
    }
    if (path === '/api/users/me') return ok(USER);
    if (path === '/api/regions/interest-summary') return ok(FAV_REGIONS.map(({ favoriteRegionId, legalDongCd, fullPath, avgPrice, changeRate, newTradeCount }) => ({ favoriteRegionId, legalDongCd, fullPath, avgPrice, changeRate, tradeCount: newTradeCount })));
    if (path === '/api/favorites/properties') return ok(FAV_PROPERTIES);
    if (path === '/api/favorites/regions') return ok(FAV_REGIONS);
    if (path === '/api/notifications/settings') return ok(options.zeroThreshold ? SETTINGS.map((x) => ({ ...x, priceChangeThresholdPct: 0 })) : SETTINGS);
    if (path === '/api/notifications') return ok(NOTIFICATIONS, { page: 0, size: 10, totalElements: 2, totalPages: 1 });
    return route.fulfill({ status: 404, contentType: 'application/json', body: errBody('NOT_FOUND', 'mock 없음') });
  });
  if (loggedIn) {
    await page.addInitScript(
      ([accessKey, refreshKey, access]) => {
        localStorage.setItem(accessKey, access);
        localStorage.setItem(refreshKey, 'R7');
      },
      [ACCESS_KEY, REFRESH_KEY, fakeJwt(7, 'A')],
    );
  }
}

/** 화면, 로그인 여부, 화면이 다 그려졌다고 볼 요소. */
export const SCREENS = [
  { id: 'HOME-01', path: '/', loggedIn: false, ready: (p) => p.getByRole('list', { name: '추천 단지' }).getByRole('listitem').first() },
  { id: 'HOME-01(로그인)', path: '/', loggedIn: true, ready: (p) => p.getByText('창신동').first() },
  { id: 'SRCH-01', path: '/search?regionCode=4111100000&regionLabel=%EC%88%98%EC%9B%90%EC%8B%9C', loggedIn: false, ready: (p) => p.getByText('대비단지4').first() },
  { id: 'DTL-01', path: '/complexes/1', loggedIn: false, ready: (p) => p.getByRole('table').first() },
  { id: 'MAP-01', path: '/map?lat=37.5666&lng=126.9784&level=5', loggedIn: false, ready: (p) => p.locator('[data-testid="map-canvas"] button[data-complex-id="1"]') },
  { id: 'AUTH-01', path: '/login', loggedIn: false, ready: (p) => p.getByRole('button', { name: '로그인', exact: true }) },
  { id: 'AUTH-02', path: '/signup', loggedIn: false, ready: (p) => p.getByRole('button', { name: '가입하기' }) },
  { id: 'AUTH-03', path: '/password-reset', loggedIn: false, ready: (p) => p.getByRole('button', { name: /재설정 링크/ }) },
  { id: 'LEGAL-01', path: '/privacy', loggedIn: false, ready: (p) => p.getByRole('heading', { level: 1 }) },
  { id: 'MY-01', path: '/my', loggedIn: true, ready: (p) => p.getByText('contrast@test.com') },
  { id: 'MY-02', path: '/favorites', loggedIn: true, ready: (p) => p.getByText('대비단지1').first() },
  { id: 'MY-03', path: '/notifications/settings', loggedIn: true, ready: (p) => p.getByText('대비단지3').first() },
  {
    id: 'MY-03(0%)',
    path: '/notifications/settings?favoritePropertyId=31',
    loggedIn: true,
    options: { zeroThreshold: true },
    ready: (p) => p.getByText('임계치 0% 설정 확인'),
  },
  { id: 'MY-04', path: '/notifications', loggedIn: true, ready: (p) => p.getByText('대비단지1 실거래가 2.1% 상승').first() },
  { id: 'MY-05', path: '/my/profile', loggedIn: true, ready: (p) => p.getByRole('button', { name: /저장/ }) },
];
