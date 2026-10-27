// 반응형 렌더 규칙 — 화면 크기별 두 벌 렌더를 없앤 세 곳(헤더, HOME-01 추천 단지, SRCH-01 필터 패널)이 DOM에 한 벌만
// 있고, 폭이 바뀌어도 다시 만들어지지 않으며(같은 DOM 노드), API를 다시 부르지 않고, 고르던 필터 값이 남는지 본다.
// 백엔드 불필요(/api/** 는 page.route 목업).
import { expect, test } from '@playwright/test';
import { errBody, okBody } from '../mockApi.mjs';
import { ACCESS_KEY, REFRESH_KEY, fakeJwt } from './helpers.mjs';

const MOBILE = { width: 390, height: 844 };
const DESKTOP = { width: 1280, height: 900 };

const COMPLEXES = [1, 2, 3, 4].map((id) => ({
  complexId: id,
  complexName: `한벌단지${id}`,
  sido: '경기도',
  sigungu: '수원장안구',
  dongRi: '정자동',
  householdCount: 500,
  approvalDate: '2005-06-01',
  representativeHousingType: 'APT',
  representativeDealCategory: 'SALE',
  representativeDealDate: '2026-09-10',
  representativeAmount: 50000 + id * 1000,
  representativeArea: 84.9,
  matchMethod: 'EXACT',
  precisionBadge: 'PRECISE',
  floor: 7,
}));

/** /api/** 목업. calls[path]에 호출 수를 센다. loggedIn이면 계정 정보를 준다. */
async function mockApis(page, { loggedIn = false } = {}) {
  const calls = {};
  await page.route('**/api/**', (route) => {
    const url = new URL(route.request().url());
    calls[url.pathname] = (calls[url.pathname] ?? 0) + 1;
    const ok = (data, pageMeta) => route.fulfill({ status: 200, contentType: 'application/json', body: okBody(data, pageMeta) });
    switch (url.pathname) {
      case '/api/complexes/popular':
        return ok(COMPLEXES);
      case '/api/complexes/search':
        return ok(COMPLEXES, { page: 0, size: 20, totalElements: COMPLEXES.length, totalPages: 1 });
      case '/api/users/me':
        return loggedIn
          ? ok({ userId: 7, email: 'one@test.com', nickname: '한벌', createdAt: '2026-09-01T00:00:00' })
          : route.fulfill({ status: 401, contentType: 'application/json', body: errBody('UNAUTHORIZED', '인증이 필요합니다') });
      case '/api/favorites/properties':
      case '/api/regions/interest-summary':
      case '/api/recent-views':
      case '/api/search/popular':
        return ok([]);
      default:
        return route.fulfill({ status: 404, contentType: 'application/json', body: errBody('NOT_FOUND', 'mock 없음') });
    }
  });
  if (loggedIn) {
    await page.addInitScript(
      ([accessKey, refreshKey, access]) => {
        if (!sessionStorage.getItem('e2e-seeded')) {
          localStorage.setItem(accessKey, access);
          localStorage.setItem(refreshKey, 'R7');
          sessionStorage.setItem('e2e-seeded', '1');
        }
      },
      [ACCESS_KEY, REFRESH_KEY, fakeJwt(7, 'A')],
    );
  }
  return calls;
}

/** 요소에 표식을 달고, 나중에 같은 노드인지 확인한다(다시 만들어지면 표식이 사라진다). */
const tag = (locator, value) => locator.evaluate((el, v) => (el.dataset.e2eTag = v), value);

test('헤더: DOM에 한 벌, 폭이 바뀌어도 같은 노드이고 열린 계정 메뉴가 그대로', async ({ page }) => {
  await mockApis(page, { loggedIn: true });
  await page.setViewportSize(MOBILE);
  await page.goto('/');

  await expect(page.locator('header')).toHaveCount(1);
  const banner = page.getByRole('banner');
  const menuButton = banner.getByRole('button', { name: '한벌 계정 메뉴' });
  await expect(menuButton).toBeVisible();
  await expect(page.locator('button[aria-haspopup="menu"]')).toHaveCount(1);
  // 모바일: 중앙 메뉴·닉네임은 보이지 않는다(한쪽에만 있는 요소).
  await expect(page.getByRole('navigation', { name: '주 메뉴' })).toBeHidden();
  await expect(banner.getByText('한벌', { exact: true })).toBeHidden();

  await tag(page.locator('header'), 'header');
  await menuButton.click();
  await expect(page.getByRole('menu', { name: '계정 메뉴' })).toBeVisible();

  await page.setViewportSize(DESKTOP);
  await expect(page.locator('header')).toHaveCount(1);
  await expect(page.locator('header')).toHaveAttribute('data-e2e-tag', 'header');
  await expect(page.getByRole('navigation', { name: '주 메뉴' })).toBeVisible();
  await expect(banner.getByText('한벌', { exact: true })).toBeVisible();
  // 같은 메뉴 버튼이라 열린 메뉴가 닫히지 않는다.
  await expect(page.getByRole('menu', { name: '계정 메뉴' })).toBeVisible();
  await expect(menuButton).toHaveAttribute('aria-expanded', 'true');
});

test('헤더(비로그인): 로그인 링크 한 벌, 회원가입은 md 이상에서만', async ({ page }) => {
  await mockApis(page);
  await page.setViewportSize(MOBILE);
  await page.goto('/');
  const banner = page.getByRole('banner');
  await expect(banner.getByRole('link', { name: '로그인', exact: true })).toBeVisible();
  await expect(page.locator('header a[href="/login"]')).toHaveCount(1);
  await expect(banner.getByRole('link', { name: '회원가입' })).toBeHidden();

  await page.setViewportSize(DESKTOP);
  await expect(banner.getByRole('link', { name: '로그인', exact: true })).toBeVisible();
  await expect(page.locator('header a[href="/login"]')).toHaveCount(1);
  await expect(banner.getByRole('link', { name: '회원가입' })).toBeVisible();
});

test('HOME-01 추천 단지: 목록 한 벌, 폭이 바뀌어도 같은 노드이고 인기 단지 API를 다시 부르지 않음', async ({ page }) => {
  const calls = await mockApis(page);
  await page.setViewportSize(MOBILE);
  await page.goto('/');

  const list = page.getByRole('list', { name: '추천 단지' });
  await expect(list.getByRole('listitem')).toHaveCount(COMPLEXES.length);
  // 하트 버튼은 카드 수만큼만 DOM에 있다(예전엔 두 배).
  await expect(page.locator('button[aria-label$="관심 매물 등록"]')).toHaveCount(COMPLEXES.length);
  await expect(list).toHaveCSS('display', 'flex');
  await tag(list, 'list');
  // 개발 서버는 StrictMode라 마운트 때 effect가 두 번 돌아 호출이 2회일 수 있다 — 횟수 자체가 아니라 폭을 바꿔도 늘지
  // 않는지 본다(목록이 다시 마운트되면 늘어난다).
  const popularCalls = calls['/api/complexes/popular'];
  expect(popularCalls).toBeGreaterThan(0);

  for (const [size, display] of [
    [DESKTOP, 'grid'],
    [MOBILE, 'flex'],
  ]) {
    await page.setViewportSize(size);
    await expect(list).toHaveCSS('display', display);
    await expect(list).toHaveAttribute('data-e2e-tag', 'list');
    await expect(list.getByRole('listitem')).toHaveCount(COMPLEXES.length);
    await expect(page.locator('button[aria-label$="관심 매물 등록"]')).toHaveCount(COMPLEXES.length);
  }
  expect(calls['/api/complexes/popular']).toBe(popularCalls);
});

test('SRCH-01 필터 패널: 한쪽에만 렌더, 시트에서 적용하지 않은 값은 닫히면(폭 변경 포함) 버린다', async ({ page }) => {
  const calls = await mockApis(page);
  await page.setViewportSize(MOBILE);
  await page.goto('/search?regionCode=4111100000&regionLabel=%EC%88%98%EC%9B%90%EC%8B%9C');
  await expect(page.getByRole('main').getByRole('link', { name: /한벌단지1/ })).toBeVisible();
  expect(calls['/api/complexes/search']).toBe(1);

  // 모바일: 사이드바 없음, 시트는 닫혀 있다 — 필터 패널이 DOM에 없다.
  await expect(page.locator('aside')).toHaveCount(0);
  await expect(page.locator('input[type="radio"]')).toHaveCount(0);

  const openSheet = async () => {
    await page.getByRole('button', { name: /^필터/ }).click();
    const sheet = page.getByRole('dialog', { name: '필터' });
    await expect(sheet).toBeVisible();
    await expect(page.locator('input[type="radio"]')).toHaveCount(3);
    return sheet;
  };

  // 1) 시트에서 월세를 고르고(적용 안 함) 폭을 넓히면 시트가 사라지며 그 값은 버려진다 — 사이드바는 적용된 매매.
  let sheet = await openSheet();
  await sheet.getByText('월세', { exact: true }).click();
  await expect(sheet.getByRole('radio', { name: '월세' })).toBeChecked();
  await page.setViewportSize(DESKTOP);
  const sidebar = page.getByRole('complementary', { name: '필터' });
  await expect(sidebar).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('input[type="radio"]')).toHaveCount(3);
  await expect(sidebar.getByRole('radio', { name: '매매' })).toBeChecked();
  expect(page.url()).not.toContain('dealType');

  // 다시 모바일 — 시트는 저절로 열리지 않고, 열면 매매.
  await page.setViewportSize(MOBILE);
  await expect(page.locator('aside')).toHaveCount(0);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  sheet = await openSheet();
  await expect(sheet.getByRole('radio', { name: '매매' })).toBeChecked();

  // 2) 닫기 버튼으로 닫아도 버려진다.
  await sheet.getByText('전세', { exact: true }).click();
  await sheet.getByRole('button', { name: '닫기' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  sheet = await openSheet();
  await expect(sheet.getByRole('radio', { name: '매매' })).toBeChecked();

  // 3) 적용하면 남는다 — URL이 바뀌고 넓힌 사이드바도 월세.
  await sheet.getByText('월세', { exact: true }).click();
  await sheet.getByRole('button', { name: '필터 적용' }).click();
  await expect(page).toHaveURL(/dealType=/);
  await page.setViewportSize(DESKTOP);
  await expect(sidebar.getByRole('radio', { name: '월세' })).toBeChecked();

  // 4) 사이드바에서 고르던 값(적용 안 함)은 패널이 계속 보이므로 폭이 바뀌어도 남는다 — 모바일에서 시트를 열면 그 값.
  await sidebar.getByText('전세', { exact: true }).click();
  await page.setViewportSize(MOBILE);
  sheet = await openSheet();
  await expect(sheet.getByRole('radio', { name: '전세' })).toBeChecked();
  // 검색 API는 768px 경계를 넘을 때마다 다시 부른다 — 모바일(누적)과 데스크톱(페이지)의 목록 방식이 달라 isMobile이 조회
  // 조건에 들어 있다(이번 변경 전부터의 동작, CLAUDE.md 백로그 16).
});
