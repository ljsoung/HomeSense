// 헤더 계정 메뉴(UserMenu)와 로그아웃을 검증한다.
// - 아바타 클릭 → "마이페이지 / 로그아웃" 메뉴, 첫 항목 포커스, ↑/↓ 이동, Esc(버튼으로 포커스 복귀)·바깥 클릭으로 닫힘
// - 로그아웃: 서버 POST /api/auth/logout 200, 로컬 토큰 삭제, 로그인 버튼 복귀, 토스트, 서버에서 Refresh Token 폐기 확인
// - Access Token이 만료된 채 로그아웃: 401 → refresh 1회 → logout 재시도 200, 원래 Refresh Token도 폐기
// - 모바일(392px) 헤더 아바타에서도 로그아웃
// 실 백엔드(8080)+Redis 필요 — 가입 API로 테스트 계정을 만든다. BASE로 dev 서버 지정.
import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const ACCESS_KEY = 'homesense.accessToken';
const REFRESH_KEY = 'homesense.refreshToken';

async function createAccount(tag) {
  const suffix = `${Date.now().toString(36).slice(-5)}${tag}`;
  const res = await fetch(`${BASE}/api/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `logout-${suffix}@test.com`, password: 'Passw0rd!', nickname: `로그${suffix}`.slice(0, 12), ageConfirmed: true }),
  });
  const data = (await res.json()).data;
  if (!data?.refreshToken) throw new Error(`테스트 계정 가입 실패 ${res.status}`);
  return data;
}

/** 서버가 이 Refresh Token을 아직 받아주는지 — 로그아웃으로 폐기됐다면 거부(4xx)해야 한다. */
async function refreshAccepted(refreshToken) {
  const res = await fetch(`${BASE}/api/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  return res.ok;
}

const browser = await chromium.launch();

async function openLoggedIn(account, viewport) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript(([a, r, keys]) => {
    if (!sessionStorage.getItem('__seeded')) {
      localStorage.setItem(keys[0], a);
      localStorage.setItem(keys[1], r);
      sessionStorage.setItem('__seeded', '1');
    }
  }, [account.accessToken, account.refreshToken, [ACCESS_KEY, REFRESH_KEY]]);
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const logoutResponses = [];
  page.on('response', (r) => { if (r.url().includes('/api/auth/logout')) logoutResponses.push(r.status()); });
  const refreshCalls = [];
  page.on('request', (r) => { if (r.url().includes('/api/auth/refresh')) refreshCalls.push(r); });
  await page.goto(`${BASE}/`);
  await page.waitForLoadState('networkidle');
  return { context, page, errors, logoutResponses, refreshCalls };
}

const stored = (page) => page.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);

// 1) 데스크톱 — 메뉴 동작과 정상 로그아웃
{
  const account = await createAccount('d');
  const { context, page, errors, logoutResponses } = await openLoggedIn(account, { width: 1280, height: 900 });
  const header = page.locator('header').first();
  const trigger = header.getByRole('button', { name: /계정 메뉴/ });
  ok('로그인 상태: 계정 메뉴 버튼 표시', await trigger.isVisible());
  ok('메뉴 닫힘 상태: aria-expanded=false', (await trigger.getAttribute('aria-expanded')) === 'false');

  await trigger.click();
  const menu = page.getByRole('menu', { name: '계정 메뉴' });
  ok('아바타 클릭 시 메뉴 열림', await menu.isVisible());
  const items = menu.getByRole('menuitem');
  ok('메뉴 항목: 마이페이지·로그아웃', JSON.stringify(await items.allTextContents()) === JSON.stringify(['마이페이지', '로그아웃']));
  ok('열리면 첫 항목에 포커스', await items.nth(0).evaluate((el) => el === document.activeElement));
  await page.keyboard.press('ArrowDown');
  ok('ArrowDown으로 로그아웃 항목 이동', await items.nth(1).evaluate((el) => el === document.activeElement));
  await page.keyboard.press('ArrowDown');
  ok('ArrowDown 순환(첫 항목으로)', await items.nth(0).evaluate((el) => el === document.activeElement));
  await page.keyboard.press('Escape');
  ok('Esc로 메뉴 닫힘', (await page.getByRole('menu').count()) === 0);
  ok('Esc 후 버튼으로 포커스 복귀', await trigger.evaluate((el) => el === document.activeElement));

  await trigger.click();
  await page.locator('h1').first().click();
  ok('바깥 클릭으로 메뉴 닫힘', (await page.getByRole('menu').count()) === 0);

  const refreshBefore = (await stored(page))[1];
  await trigger.click();
  await menu.getByRole('menuitem', { name: '로그아웃' }).click();
  await header.getByRole('link', { name: '로그인' }).waitFor({ timeout: 5000 });
  ok('로그아웃 후 로그인·회원가입 버튼 복귀', await header.getByRole('link', { name: '회원가입' }).isVisible());
  ok('로그아웃 토스트 표시', await page.getByRole('status').filter({ hasText: '로그아웃되었습니다' }).isVisible());
  ok('서버 로그아웃 200 (1회)', JSON.stringify(logoutResponses) === '[200]');
  ok('로컬 토큰 삭제', (await stored(page)).every((v) => v === null));
  ok('서버에서 Refresh Token 폐기됨(재발급 거부)', !(await refreshAccepted(refreshBefore)));

  await page.reload();
  await page.waitForLoadState('networkidle');
  ok('새로고침 후에도 비로그인 유지', await page.locator('header').first().getByRole('link', { name: '로그인' }).isVisible());
  ok('pageerror 없음(데스크톱)', errors.length === 0);
  await context.close();
}

// 2) Access Token이 만료된 채 로그아웃 — 401 → refresh 1회 → logout 재시도
{
  const account = await createAccount('e');
  const { context, page, errors, logoutResponses, refreshCalls } = await openLoggedIn(account, { width: 1280, height: 900 });
  const refreshAtLoad = refreshCalls.length;
  const [, refreshBefore] = await stored(page);
  await page.evaluate((key) => localStorage.setItem(key, 'expired.access.token'), ACCESS_KEY);

  await page.locator('header').first().getByRole('button', { name: /계정 메뉴/ }).click();
  await page.getByRole('menuitem', { name: '로그아웃' }).click();
  await page.locator('header').first().getByRole('link', { name: '로그인' }).waitFor({ timeout: 5000 });
  ok('만료 토큰: logout 401 후 재시도 200', JSON.stringify(logoutResponses) === '[401,200]');
  ok('만료 토큰: refresh 1회', refreshCalls.length - refreshAtLoad === 1);
  ok('만료 토큰: 로컬 토큰 삭제', (await stored(page)).every((v) => v === null));
  ok('만료 토큰: 원래 Refresh Token도 서버에서 폐기', !(await refreshAccepted(refreshBefore)));
  ok('pageerror 없음(만료 토큰)', errors.length === 0);
  await context.close();
}

// 3) 모바일 헤더
{
  const account = await createAccount('m');
  const { context, page, errors, logoutResponses } = await openLoggedIn(account, { width: 392, height: 800 });
  // 모바일에서는 DOM의 첫 header가 숨겨진 데스크톱 GNB라 실제로 보이는 모바일 헤더를 고른다.
  const header = page.locator('header:visible').first();
  const trigger = header.getByRole('button', { name: /계정 메뉴/ });
  ok('모바일: 아바타(계정 메뉴) 표시', await trigger.isVisible());
  await trigger.click();
  const menu = page.getByRole('menu', { name: '계정 메뉴' });
  // 메뉴가 안 열리면 boundingBox()가 null이라, 예외로 죽지 않고 FAIL로 드러나게 null을 먼저 거른다.
  const box = await menu.boundingBox();
  ok('모바일: 메뉴가 화면 안에 들어옴', box !== null && box.x >= 0 && box.x + box.width <= 392);
  await menu.getByRole('menuitem', { name: '로그아웃' }).click();
  // 대기 결과를 반환값으로 받아 단언한다 — 실패하면 시간 초과 예외가 아니라 이 항목의 FAIL로 드러난다.
  const loginLinkShown = await header.getByRole('link', { name: '로그인' }).waitFor({ timeout: 5000 }).then(() => true, () => false);
  ok('모바일: 로그아웃 후 로그인 링크 복귀', loginLinkShown);
  ok('모바일: 로그아웃 후 계정 메뉴(아바타) 사라짐', (await trigger.count()) === 0);
  ok('모바일: 로컬 토큰 삭제', (await stored(page)).every((v) => v === null));
  ok('모바일: 서버 로그아웃 200', JSON.stringify(logoutResponses) === '[200]');
  ok('pageerror 없음(모바일)', errors.length === 0);
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed (total: ${pass + fail})`);
process.exit(fail ? 1 : 0);
