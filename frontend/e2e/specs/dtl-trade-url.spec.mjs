// [DTL-01] 거래상세 모달 URL — 행을 누르면 `?trade={id}`가 push되고 뒤로 가기로 닫힌다. 그 URL로 직접 들어오면 상세를 받아
// 이 단지의 거래인지 확인한 뒤 연다(다시 받지 않음). 직접 진입 후 닫으면 replace로 파라미터만 지운다. 없는 거래·다른 단지의
// 거래·형식이 틀린 값은 열지 않고 파라미터를 지운 뒤 안내한다. 공용 Modal의 포커스(행으로 복귀)가 유지된다.
// 백엔드 불필요(/api/** 는 page.route 목업).
import { expect, test } from '@playwright/test';
import { errBody, okBody } from '../mockApi.mjs';

const COMPLEX_ID = 900001;
const OTHER_COMPLEX = 900002;

const detail = {
  complexId: COMPLEX_ID,
  complexName: '모달주소아파트',
  complexType: '아파트',
  housingType: 'APT',
  sido: '경기도',
  sigungu: '수원장안구',
  dongRi: '조원동',
  legalDongCd: '4111112900',
  legalDongAddress: '경기도 수원장안구 조원동 881 모달주소아파트',
  matchPending: false,
  matchMethod: 'EXACT',
  locationPrecision: 'PRECISE',
  precisionBadge: 'PRECISE',
  basicInfo: { householdCount: 1000, buildingCount: 10, approvalDate: '2001-05-01' },
  extendedInfo: {},
};

const TRADES = [0, 1, 2].map((i) => ({
  tradeId: 5000 + i,
  dealDate: `2026-09-${String(20 - i).padStart(2, '0')}`,
  dealCategory: 'SALE',
  excluUseArea: 84.98,
  floor: 5 + i,
  dealAmount: 50000 + i * 1000,
  dealingType: 'AGENT',
  isCancelled: false,
  isRegistered: false,
}));

function tradeDetail(id, complexId) {
  return {
    tradeId: id,
    dealDate: '2026-09-20',
    excluUseArea: 84.98,
    floor: 5,
    dealCategory: 'SALE',
    dealAmount: 50000,
    aptDongPending: true,
    dealingType: 'AGENT',
    sellerType: '개인',
    buyerType: '법인',
    isCancelled: false,
    landLeaseYn: false,
    ...(complexId == null ? {} : { complexId }),
  };
}

async function setUp(page) {
  const tradeDetailCalls = [];
  await page.route('**/api/**', (route) => {
    const url = new URL(route.request().url());
    const ok = (data) => route.fulfill({ status: 200, contentType: 'application/json', body: okBody(data) });
    const notFound = (code, message) => route.fulfill({ status: 404, contentType: 'application/json', body: errBody(code, message) });
    if (url.pathname === `/api/complexes/${COMPLEX_ID}`) return ok(detail);
    if (url.pathname === '/api/trades') return ok(url.searchParams.get('dealType') === 'SALE' ? TRADES : []);
    const trade = url.pathname.match(/^\/api\/trades\/(\d+)$/);
    if (trade) {
      const id = Number(trade[1]);
      tradeDetailCalls.push(id);
      if (id === 9999) return notFound('TRADE_NOT_FOUND', '존재하지 않는 거래입니다');
      if (id === 7777) return ok(tradeDetail(id, OTHER_COMPLEX));
      if (id === 6666) return ok(tradeDetail(id, null)); // 단지 매칭 실패 거래
      return ok(tradeDetail(id, COMPLEX_ID));
    }
    if (url.pathname === '/api/users/me') {
      return route.fulfill({ status: 401, contentType: 'application/json', body: errBody('UNAUTHORIZED', '인증이 필요합니다') });
    }
    return notFound('NOT_FOUND', 'mock 없음');
  });
  return { tradeDetailCalls };
}

const dialog = (page) => page.getByRole('dialog', { name: '거래 상세' });
const rows = (page) => page.locator('tbody tr');
const tradeParam = (page) => new URL(page.url()).searchParams.get('trade');

test('행을 누르면 ?trade=가 push되고, 뒤로 가기로 닫히며 포커스가 행으로 돌아온다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await setUp(page);
  await page.goto(`/complexes/${COMPLEX_ID}`);
  await expect(rows(page)).toHaveCount(3);
  const historyLength = await page.evaluate(() => history.length);

  await rows(page).nth(1).focus();
  await page.keyboard.press('Enter');
  await expect(dialog(page).getByText('매도자')).toBeVisible();
  expect(tradeParam(page)).toBe('5001');
  expect(await page.evaluate(() => history.length)).toBe(historyLength + 1);

  await page.goBack();
  await expect(dialog(page)).toHaveCount(0);
  expect(tradeParam(page)).toBeNull();
  await expect(rows(page).nth(1)).toBeFocused();
});

test('화면 안에서 연 모달의 닫기·Esc는 뒤로 가기로 닫아 히스토리가 쌓이지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await setUp(page);
  await page.goto(`/complexes/${COMPLEX_ID}`);
  await expect(rows(page)).toHaveCount(3);
  const historyLength = await page.evaluate(() => history.length);

  await rows(page).first().click();
  await expect(dialog(page).getByText('매도자')).toBeVisible();
  await dialog(page).getByRole('button', { name: '닫기' }).click();
  await expect(dialog(page)).toHaveCount(0);
  expect(tradeParam(page)).toBeNull();

  await rows(page).first().focus();
  await page.keyboard.press('Enter');
  await expect(dialog(page).getByText('매도자')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialog(page)).toHaveCount(0);
  await expect(rows(page).first()).toBeFocused();

  // 두 번 열고 닫았지만 뒤로 가기로 꺼냈으니 길이는 처음과 같고, 앞으로 가기에 남은 모달 항목은 다음 열기가 덮는다.
  expect(await page.evaluate(() => history.length)).toBe(historyLength + 1);
  expect(tradeParam(page)).toBeNull();
});

test('?trade=로 직접 들어오면 확인한 뒤 열고(다시 받지 않음), 닫으면 replace로 파라미터만 지운다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  const { tradeDetailCalls } = await setUp(page);
  await page.goto('/privacy');
  await page.goto(`/complexes/${COMPLEX_ID}?trade=5002`);
  await expect(dialog(page).getByText('매도자')).toBeVisible();
  expect(tradeDetailCalls).toEqual([5002]);
  const historyLength = await page.evaluate(() => history.length);

  await dialog(page).getByRole('button', { name: '닫기' }).click();
  await expect(dialog(page)).toHaveCount(0);
  expect(new URL(page.url()).pathname).toBe(`/complexes/${COMPLEX_ID}`);
  expect(tradeParam(page)).toBeNull();
  expect(await page.evaluate(() => history.length)).toBe(historyLength);

  // replace였으므로 뒤로 가면 단지 상세가 아니라 그 전 화면이다.
  await page.goBack();
  await expect(page).toHaveURL(/\/privacy$/);
});

for (const [label, value, toastExpected] of [
  ['없는 거래(404)', '9999', true],
  ['다른 단지의 거래', '7777', true],
  ['단지 매칭이 없는 거래', '6666', true],
  ['형식이 틀린 값', 'abc', true],
]) {
  test(`${label} — 모달을 열지 않고 파라미터를 지운 뒤 안내한다`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await setUp(page);
    await page.goto(`/complexes/${COMPLEX_ID}?deal=JEONSE&trade=${value}`);
    await expect.poll(() => tradeParam(page)).toBeNull();
    expect(new URL(page.url()).searchParams.get('deal')).toBe('JEONSE');
    if (toastExpected) await expect(page.getByText('해당 거래를 찾을 수 없습니다.')).toHaveCount(1);
    await expect(dialog(page)).toHaveCount(0);
  });
}
