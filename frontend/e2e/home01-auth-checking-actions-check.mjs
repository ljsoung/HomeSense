// 세션 확인(authChecking) 중에 인증이 필요한 동작을 해도, 로그인 사용자를 비로그인으로 오판하지 않는지.
// 확인 중에는 로그인 사용자도 isAuthenticated가 false라, 예전에는 하트 클릭이 곧바로 /login으로 보냈고
// 복원이 성공해도 로그인 화면에 남았다(Codex P2). 재발급 응답을 지연시켜 확인 구간을 확정적으로 만든다.
// 실 백엔드(8080)+Redis 필요 — 가입 API로 테스트 계정을 만든다. BASE로 dev 서버 지정.
import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const ACCESS_KEY = 'homesense.accessToken';
const REFRESH_KEY = 'homesense.refreshToken';
const PENDING_FAVORITE_KEY = 'homesense.pendingFavoriteComplexId';
const REFRESH_DELAY_MS = 2000;

async function createAccount(tag) {
  const suffix = `${Date.now().toString(36).slice(-5)}${tag}`;
  const res = await fetch(`${BASE}/api/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `checking-${suffix}@test.com`, password: 'Passw0rd!', nickname: `확인${suffix}`.slice(0, 12), ageConfirmed: true }),
  });
  const data = (await res.json()).data;
  if (!data?.refreshToken) throw new Error(`테스트 계정 가입 실패 ${res.status}`);
  return data;
}

const api = (path, accessToken, init = {}) =>
  fetch(`${BASE}${path}`, { ...init, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}`, ...(init.headers ?? {}) } });

const popular = (await (await fetch(`${BASE}/api/complexes/popular?limit=8`)).json()).data;
const complexIdByName = new Map(popular.map((c) => [c.complexName, c.complexId]));

const browser = await chromium.launch();

/** 만료된 Access Token + 주어진 Refresh Token으로 시작하고, 재발급 응답을 지연시킨다. */
async function openChecking(refreshToken) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript(([r, keys]) => {
    if (!localStorage.getItem('__seeded')) {
      localStorage.setItem(keys[0], 'expired.access.token');
      localStorage.setItem(keys[1], r);
      localStorage.setItem('__seeded', '1');
    }
  }, [refreshToken, [ACCESS_KEY, REFRESH_KEY]]);
  let refreshDoneAt = 0;
  await context.route('**/api/auth/refresh', async (route) => {
    await new Promise((r) => setTimeout(r, REFRESH_DELAY_MS));
    const response = await route.fetch();
    refreshDoneAt = Date.now();
    await route.fulfill({ response });
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const favoriteCalls = [];
  page.on('request', (r) => {
    if (r.url().includes('/api/favorites/properties') && r.method() !== 'GET') favoriteCalls.push({ method: r.method(), at: Date.now() });
  });
  return { context, page, errors, favoriteCalls, refreshDone: () => refreshDoneAt };
}

/** 추천 단지 그리드에서 보이는 n번째 카드의 하트와 그 단지 id. */
async function firstVisibleHeart(page, index = 0) {
  const heart = page.locator('button[aria-pressed]:visible').nth(index);
  await heart.waitFor({ timeout: 10000 });
  const name = await heart.evaluate((el) => {
    let n = el;
    while (n && !(n.querySelector && n.querySelector('p.font-bold'))) n = n.parentElement;
    return n?.querySelector('p.font-bold')?.textContent ?? null;
  });
  return { heart, complexId: complexIdByName.get(name), name };
}

// 대기 표시: aria-busy="true"와 시각적 표시(브랜드색 링).
const isBusy = async (heart) =>
  (await heart.getAttribute('aria-busy')) === 'true' && ((await heart.getAttribute('class')) ?? '').split(' ').includes('ring-2');
// 추천 단지는 모바일·데스크톱용 카드를 둘 다 렌더하므로(CSS로 하나만 보임) 보이는 하트만 센다.
const busyCount = (page) => page.locator('button[aria-pressed][aria-busy="true"]:visible').count();

const checkingHeaderEmpty = async (page) =>
  (await page.locator('header').first().getByRole('link', { name: '로그인' }).count()) === 0 &&
  (await page.locator('header').first().getByRole('button', { name: /계정 메뉴/ }).count()) === 0;

// 1) 로그인 사용자 — 확인 중 클릭은 로그인 화면으로 보내지 않고, 판정 뒤 등록한다.
{
  const account = await createAccount('a');
  const { context, page, errors, favoriteCalls, refreshDone } = await openChecking(account.refreshToken);
  await page.goto(`${BASE}/`);
  const { heart, complexId, name } = await firstVisibleHeart(page);
  ok(`로그인 사용자: 카드와 단지 id 확인(${name})`, Boolean(complexId));
  ok('로그인 사용자: 클릭 시점에 세션 확인 중(헤더 우측 비어 있음)', await checkingHeaderEmpty(page));
  ok('로그인 사용자: 확인 중 최근 조회 영역에 "로그인하면" 문구 없음', (await page.getByText('로그인하면 최근 조회한').count()) === 0);
  ok('로그인 사용자: 클릭 전에는 대기 표시 없음', !(await isBusy(heart)));
  await heart.click();
  const clickedAt = Date.now();
  ok('로그인 사용자: 확인 중 클릭 직후 대기 표시(aria-busy·링)', await isBusy(heart));
  await page.waitForTimeout(REFRESH_DELAY_MS + 1500);
  ok('로그인 사용자: 처리 후 대기 표시 사라짐', !(await isBusy(heart)) && (await busyCount(page)) === 0);
  ok('로그인 사용자: 로그인 화면으로 이동하지 않음', new URL(page.url()).pathname === '/');
  ok('로그인 사용자: 대기 중인 하트 재생 항목을 남기지 않음', (await page.evaluate((k) => sessionStorage.getItem(k), PENDING_FAVORITE_KEY)) === null);
  ok('로그인 사용자: 복원 뒤 로그인 상태(계정 메뉴)', await page.locator('header').first().getByRole('button', { name: /계정 메뉴/ }).isVisible());
  ok('로그인 사용자: 등록 요청이 정확히 1회(POST)', JSON.stringify(favoriteCalls.map((c) => c.method)) === '["POST"]');
  ok('로그인 사용자: 등록 요청은 복원(재발급) 뒤에 나감', favoriteCalls[0]?.at >= refreshDone() && refreshDone() > clickedAt);
  const [access] = await page.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  const favorites = (await (await api('/api/favorites/properties', access)).json()).data ?? [];
  ok('로그인 사용자: 서버에 관심 매물로 등록됨', favorites.some((f) => f.complexId === complexId));
  ok('로그인 사용자: 하트가 채워짐', (await heart.getAttribute('aria-pressed')) === 'true');
  ok('pageerror 없음(로그인 사용자)', errors.length === 0);
  await context.close();
}

// 2) 이미 찜한 단지 — 확인 중에는 하트가 비어 보이므로 그 클릭은 "등록" 의도다. 하트 상태를 불러온 뒤 이미
//    등록돼 있으면 요청을 보내지 않는다(토글로 재생하면 사용자가 본 적 없는 해제 요청이 나갔다).
{
  const account = await createAccount('b');
  const complexId = popular[0].complexId;
  const added = await api('/api/favorites/properties', account.accessToken, { method: 'POST', body: JSON.stringify({ complexId }) });
  ok('이미 찜함: 사전 등록 성공', added.ok);
  const { context, page, errors, favoriteCalls } = await openChecking(account.refreshToken);
  await page.goto(`${BASE}/`);
  const target = page.locator('button[aria-pressed]:visible').first();
  await target.waitFor({ timeout: 10000 });
  const { complexId: shownId } = await firstVisibleHeart(page);
  ok('이미 찜함: 첫 카드가 사전 등록한 단지', shownId === complexId);
  ok('이미 찜함: 클릭 시점에 세션 확인 중', await checkingHeaderEmpty(page));
  ok('이미 찜함: 클릭 순간 하트는 비어 보임(aria-pressed=false)', (await target.getAttribute('aria-pressed')) === 'false');
  await target.click();
  ok('이미 찜함: 확인 중 클릭 직후 대기 표시', await isBusy(target));
  await page.waitForTimeout(REFRESH_DELAY_MS + 1500);
  ok('이미 찜함: 처리 후 대기 표시 사라짐', (await busyCount(page)) === 0);
  ok('이미 찜함: 등록·해제 요청 모두 없음(이미 의도한 상태)', favoriteCalls.length === 0);
  const [access] = await page.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  const favorites = (await (await api('/api/favorites/properties', access)).json()).data ?? [];
  ok('이미 찜함: 서버에 관심 매물이 그대로 등록돼 있음', favorites.some((f) => f.complexId === complexId));
  ok('이미 찜함: 하트가 채워짐', (await target.getAttribute('aria-pressed')) === 'true');
  ok('이미 찜함: 오류 토스트 없음', (await page.getByRole('status').filter({ hasText: '이미' }).count()) === 0);
  ok('pageerror 없음(이미 찜함)', errors.length === 0);
  await context.close();
}

// 3) 세션이 무효인 사용자 — 확인 중 클릭은 판정이 비로그인으로 끝난 뒤 로그인 화면으로 보내고, 재생 항목을 남긴다.
{
  const { context, page, errors, favoriteCalls } = await openChecking('invalid.refresh.token');
  await page.goto(`${BASE}/`);
  const { heart, complexId } = await firstVisibleHeart(page);
  ok('무효 세션: 클릭 시점에 세션 확인 중', await checkingHeaderEmpty(page));
  await heart.click();
  ok('무효 세션: 확인 중 클릭 직후 대기 표시', await isBusy(heart));
  await page.waitForTimeout(300);
  ok('무효 세션: 판정 전에는 이동하지 않음', new URL(page.url()).pathname === '/');
  const toLogin = await page.waitForURL(/\/login$/, { timeout: REFRESH_DELAY_MS + 5000 }).then(() => true, () => false);
  ok('무효 세션: 판정 뒤 로그인 화면으로 이동', toLogin);
  ok('무효 세션: 로그인 후 재생할 하트 클릭이 남음', (await page.evaluate((k) => sessionStorage.getItem(k), PENDING_FAVORITE_KEY)) === String(complexId));
  ok('무효 세션: 관심 매물 요청 없음', favoriteCalls.length === 0);
  await page.goBack();
  await page.locator('button[aria-pressed]:visible').first().waitFor({ timeout: 10000 });
  ok('무효 세션: 홈으로 돌아와도 대기 표시가 남아 있지 않음', (await busyCount(page)) === 0);
  ok('pageerror 없음(무효 세션)', errors.length === 0);
  await context.close();
}

// 4) 확인 중 서로 다른 카드 X, Y를 연달아 클릭 — 전역으로 마지막 하나(Y)만 처리하고, X는 버리며 대기 표시도 푼다.
{
  const account = await createAccount('d');
  const { context, page, errors, favoriteCalls } = await openChecking(account.refreshToken);
  await page.goto(`${BASE}/`);
  const x = await firstVisibleHeart(page, 0);
  const y = await firstVisibleHeart(page, 1);
  ok(`X·Y: 서로 다른 두 단지(${x.name} / ${y.name})`, Boolean(x.complexId && y.complexId) && x.complexId !== y.complexId);
  ok('X·Y: 클릭 시점에 세션 확인 중', await checkingHeaderEmpty(page));
  await x.heart.click();
  ok('X·Y: X 클릭 직후 X에 대기 표시', await isBusy(x.heart));
  await y.heart.click();
  ok('X·Y: Y 클릭 직후 Y에 대기 표시', await isBusy(y.heart));
  ok('X·Y: Y를 누르면 X의 대기 표시는 풀림(대기 표시는 하나뿐)', !(await isBusy(x.heart)) && (await busyCount(page)) === 1);
  await page.waitForTimeout(REFRESH_DELAY_MS + 1500);
  ok('X·Y: 처리 후 대기 표시 없음', (await busyCount(page)) === 0);
  ok('X·Y: 등록 요청은 Y 하나(POST 1회)', JSON.stringify(favoriteCalls.map((c) => c.method)) === '["POST"]');
  const [access] = await page.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  const favorites = (await (await api('/api/favorites/properties', access)).json()).data ?? [];
  ok('X·Y: 서버에는 Y만 등록되고 X는 등록되지 않음', favorites.some((f) => f.complexId === y.complexId) && !favorites.some((f) => f.complexId === x.complexId));
  ok('X·Y: 하트도 Y만 채워짐', (await y.heart.getAttribute('aria-pressed')) === 'true' && (await x.heart.getAttribute('aria-pressed')) === 'false');
  ok('pageerror 없음(X·Y)', errors.length === 0);
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed (total: ${pass + fail})`);
process.exit(fail ? 1 : 0);
