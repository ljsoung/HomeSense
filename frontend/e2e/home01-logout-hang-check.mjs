// 서버 로그아웃 요청이 응답 없이 멈춰도 로컬 로그아웃은 제한 시간 안에 끝나는지 검증한다.
// 수정 전에는 서버 폐기(await)가 끝나야 로컬 토큰을 지웠는데 로그아웃 요청에 timeout이 없어, 서버가 응답하지
// 않으면 로컬 로그아웃이 영원히 끝나지 않고 로그아웃 버튼도 비활성으로 남았다(Codex P2).
// 실 백엔드(8080)+Redis 필요 — 가입 API로 테스트 계정을 만든다. BASE로 dev 서버 지정.
import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond, detail = '') { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); } }

const ACCESS_KEY = 'homesense.accessToken';
const REFRESH_KEY = 'homesense.refreshToken';
// 로그아웃 상한(session.ts LOGOUT_REVOKE_DEADLINE_MS=5초)보다 넉넉하게 기다린다.
const SETTLE_LIMIT_MS = 9000;
const PASSWORD = 'Passw0rd!';

async function createAccount(tag) {
  const suffix = `${Date.now().toString(36).slice(-5)}${tag}`;
  const email = `hang-${suffix}@test.com`;
  const nickname = `멈춤${suffix}`.slice(0, 12);
  const res = await fetch(`${BASE}/api/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD, nickname, ageConfirmed: true }),
  });
  const data = (await res.json()).data;
  if (!data?.refreshToken) throw new Error(`테스트 계정 가입 실패 ${res.status}`);
  return { ...data, email, nickname };
}

const browser = await chromium.launch();

/**
 * 로그인 상태로 홈을 연다. `hang`에 든 경로(logout/refresh)는 응답하지 않고 붙잡아 둔다.
 * `expireAccessAfterLoad`면 로드 뒤 Access Token을 무효로 바꿔, 로그아웃이 401 → 재발급 경로를 타게 한다.
 */
async function openLoggedIn(account, { hang, expireAccessAfterLoad = false, noLocks = false }) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript(([a, r, keys, disableLocks]) => {
    if (disableLocks) Object.defineProperty(Navigator.prototype, 'locks', { get: () => undefined, configurable: true });
    if (!sessionStorage.getItem('__seeded')) {
      localStorage.setItem(keys[0], a);
      localStorage.setItem(keys[1], r);
      sessionStorage.setItem('__seeded', '1');
    }
  }, [account.accessToken, account.refreshToken, [ACCESS_KEY, REFRESH_KEY], noLocks]);
  const held = [];
  let holding = true;
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto(`${BASE}/`);
  await page.waitForLoadState('networkidle');
  if (expireAccessAfterLoad) await page.evaluate((k) => localStorage.setItem(k, 'expired.access.token'), ACCESS_KEY);
  for (const path of hang) {
    await context.route(`**/api/auth/${path}`, async (route) => {
      if (holding) { held.push({ path, route }); return; } // 응답하지 않고 붙잡는다
      await route.continue();
    });
  }
  return { context, page, errors, held, stopHolding: () => { holding = false; } };
}

const stored = (page) => page.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);

async function clickLogout(page) {
  const header = page.locator('header').first();
  await header.getByRole('button', { name: /계정 메뉴/ }).click();
  await page.getByRole('menuitem', { name: '로그아웃' }).click();
  const startedAt = Date.now();
  const settled = await header.getByRole('link', { name: '로그인' }).waitFor({ timeout: SETTLE_LIMIT_MS }).then(() => true, () => false);
  return { settled, elapsed: Date.now() - startedAt };
}

// 1) 로그아웃 요청이 멈춤
{
  const account = await createAccount('l');
  const { context, page, errors, held } = await openLoggedIn(account, { hang: ['logout'] });
  const { settled, elapsed } = await clickLogout(page);
  ok('로그아웃 요청이 붙잡힘(서버 응답 없음)', held.some((h) => h.path === 'logout'));
  ok(`로그아웃 요청 멈춤: 제한 시간 안에 로컬 로그아웃 완료(${elapsed}ms)`, settled && elapsed < SETTLE_LIMIT_MS);
  ok('로그아웃 요청 멈춤: 로컬 토큰 삭제', (await stored(page)).every((v) => v === null));
  ok('로그아웃 요청 멈춤: 로그아웃 토스트', await page.getByRole('status').filter({ hasText: '로그아웃되었습니다' }).isVisible());
  ok('pageerror 없음(로그아웃 멈춤)', errors.length === 0);
  await context.close();
}

// 2) Access Token 만료 → 401 → 재발급이 멈춤. 제한 시간 뒤 로컬 로그아웃이 끝나고, 나중에 재발급 응답이
//    도착해도 지운 토큰을 되살리지 않는다(조건부 저장).
{
  const account = await createAccount('r');
  const { context, page, errors, held, stopHolding } = await openLoggedIn(account, { hang: ['refresh'], expireAccessAfterLoad: true });
  // 클라이언트가 재발급 요청을 스스로 끊었는지(재발급 timeout) 기록한다.
  const refreshFailedOnClient = [];
  page.on('requestfailed', (r) => { if (r.url().includes('/api/auth/refresh')) refreshFailedOnClient.push(r.failure()?.errorText ?? ''); });
  const { settled, elapsed } = await clickLogout(page);
  ok('재발급이 붙잡힘(로그아웃 401 뒤)', held.some((h) => h.path === 'refresh'));
  ok(`재발급 멈춤: 제한 시간 안에 로컬 로그아웃 완료(${elapsed}ms)`, settled && elapsed < SETTLE_LIMIT_MS);
  ok('재발급 멈춤: 로컬 토큰 삭제', (await stored(page)).every((v) => v === null));
  // 붙잡았던 재발급을 이제 서버로 보내 응답을 돌려준다 — 저장소가 비어 있으므로 결과는 버려져야 한다.
  stopHolding();
  const refreshResponses = [];
  page.on('response', (r) => { if (r.url().includes('/api/auth/refresh')) refreshResponses.push(r.status()); });
  // 재발급 timeout(5초)이 로그아웃 상한(5초)과 비슷해, 붙잡힌 요청은 여기서 풀기 전에 클라이언트 timeout으로
  // 이미 끊겼을 수 있다 — 그때는 늦은 응답이 페이지에 도착할 수 없으니 되살릴 위험도 없다.
  await Promise.all(held.filter((h) => h.path === 'refresh').map(async (h) =>
    h.route.fulfill({ response: await h.route.fetch() }).catch(() => {})));
  await page.waitForTimeout(1500);
  ok('재발급 멈춤: 늦게 온 재발급 응답(200)이 도착했거나, 클라이언트가 재발급 요청을 timeout으로 끊음',
    refreshResponses.includes(200) || refreshFailedOnClient.length > 0,
    `responses=${JSON.stringify(refreshResponses)} failed=${JSON.stringify(refreshFailedOnClient)}`);
  ok('재발급 멈춤: 늦은 응답이 지운 토큰을 되살리지 않음', (await stored(page)).every((v) => v === null));
  ok('재발급 멈춤: 헤더는 계속 비로그인', await page.locator('header').first().getByRole('link', { name: '로그인' }).isVisible());
  ok('pageerror 없음(재발급 멈춤)', errors.length === 0);
  await context.close();
}

// 3) 상한 뒤 뒤늦게 이어진 서버 폐기가 새로 로그인한 계정(B)을 로그아웃시키지 않는다.
//    재발급이 멈춘 채 로그아웃 → 상한 뒤 로컬 로그아웃 → B로 로그인 → A 재발급의 timeout(10초) 전에 응답을 풀어 준다.
//    중단 신호가 없으면 폐기 작업이 재발급 뒤 저장소를 다시 읽어 B의 토큰으로 로그아웃을 보낸다.
//    Web Locks를 끈다 — 락이 있으면 B의 토큰 저장이 A 재발급(락 보유)을 기다려, A 재발급이 먼저 timeout으로
//    끝나 버리므로 "B가 먼저 저장되고 A 재발급 응답이 뒤에 도착"하는 순서가 만들어지지 않는다.
{
  const accountA = await createAccount('a');
  const accountB = await createAccount('b');
  const { context, page, errors, held, stopHolding } = await openLoggedIn(accountA, { hang: ['refresh'], expireAccessAfterLoad: true, noLocks: true });
  ok('B 재로그인: navigator.locks 없음 확인', await page.evaluate(() => navigator.locks === undefined));
  const logoutClickedAt = Date.now();
  const { settled } = await clickLogout(page);
  ok('B 재로그인: A 로그아웃이 상한 뒤 로컬에서 완료', settled);
  const logoutRequests = [];
  page.on('request', (r) => {
    if (r.url().includes('/api/auth/logout')) logoutRequests.push({ auth: (r.headers().authorization ?? '').replace(/^Bearer /, ''), body: r.postData() ?? '' });
  });
  let loginTokensB = null;
  page.on('response', async (r) => { if (r.url().includes('/api/auth/login') && r.ok()) loginTokensB = (await r.json()).data; });
  // A의 재발급은 아직 붙잡혀 있다. SPA 안에서 로그인 화면으로 가 B로 로그인한다(페이지를 새로 불러오면
  // 뒤에서 이어지던 A의 폐기 작업이 사라져 이 경우를 재현하지 못한다).
  await page.evaluate(() => { history.pushState({}, '', '/login'); dispatchEvent(new PopStateEvent('popstate')); });
  await page.getByLabel('이메일').fill(accountB.email);
  await page.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await page.waitForURL(`${BASE}/`, { timeout: 10000 });
  await page.waitForLoadState('networkidle');
  ok('B 재로그인: B 로그인 응답을 받음', Boolean(loginTokensB?.accessToken));

  // 붙잡았던 A의 재발급을 이제 서버로 보내 응답을 돌려준다 — A 재발급의 10초 timeout 전이어야 받는 쪽이 살아 있다.
  ok(`B 재로그인: A 재발급 timeout 전에 응답을 풀어 줌(${Date.now() - logoutClickedAt}ms)`, Date.now() - logoutClickedAt < 9500);
  stopHolding();
  await Promise.all(held.filter((h) => h.path === 'refresh').map(async (h) => h.route.fulfill({ response: await h.route.fetch() })));
  await page.waitForTimeout(2000);

  ok('B 재로그인: B의 토큰으로 로그아웃 요청이 나가지 않음',
    loginTokensB && logoutRequests.every((r) => r.auth !== loginTokensB.accessToken && !r.body.includes(loginTokensB.refreshToken)));
  const [access, refresh] = await stored(page);
  ok('B 재로그인: 저장소는 B 로그인 결과 그대로', loginTokensB && access === loginTokensB.accessToken && refresh === loginTokensB.refreshToken);
  const meStatus = await fetch(`${BASE}/api/users/me`, { headers: { Authorization: `Bearer ${access}` } }).then((r) => r.status);
  ok('B 재로그인: B 세션이 서버에서 유효(GET /me 200)', meStatus === 200);
  const refreshStillValid = await fetch(`${BASE}/api/auth/refresh`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: refresh }),
  }).then((r) => r.ok);
  ok('B 재로그인: B의 Refresh Token이 서버에서 폐기되지 않음', refreshStillValid);
  const menu = page.locator('header').first().getByRole('button', { name: /계정 메뉴/ });
  ok('B 재로그인: 화면은 B로 로그인 상태', (await menu.isVisible()) && (await menu.textContent()).includes(accountB.nickname));
  ok('pageerror 없음(B 재로그인)', errors.length === 0);
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed (total: ${pass + fail})`);
process.exit(fail ? 1 : 0);
