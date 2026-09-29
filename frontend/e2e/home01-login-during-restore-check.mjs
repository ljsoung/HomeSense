// 세션 복원(재발급)이 진행 중일 때 다른 계정으로 로그인해도, 늦게 도착한 재발급 결과가 새 로그인의
// 토큰을 덮어쓰지 않는지 검증한다. 수정 전에는 refreshTokens()가 응답 후 저장소를 조건 없이 덮어써,
// 화면은 B로 보이는데 이후 인증 요청은 A로 나가는 상태가 됐다(Codex P1).
// 재발급 응답을 붙잡아 "A 재발급 대기 중 → B 로그인 → A 재발급 응답 도착" 순서를 확정적으로 만든다.
// 같은 시나리오를 Web Locks가 있을 때(로그인 저장이 재발급 락을 기다림)와 없을 때(재발급 결과 저장의
// compare-and-set만으로 막음) 두 번 돌린다.
// 실 백엔드(8080)+Redis 필요 — 가입 API로 테스트 계정을 만든다. BASE로 dev 서버 지정.
import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const ACCESS_KEY = 'homesense.accessToken';
const REFRESH_KEY = 'homesense.refreshToken';
const PASSWORD = 'Passw0rd!';

async function createAccount(tag) {
  const suffix = `${Date.now().toString(36).slice(-5)}${tag}`;
  const email = `restore-${suffix}@test.com`;
  const nickname = `복원${suffix}`.slice(0, 12);
  const res = await fetch(`${BASE}/api/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD, nickname, ageConfirmed: true }),
  });
  const data = (await res.json()).data;
  if (!data?.refreshToken) throw new Error(`테스트 계정 가입 실패 ${res.status}`);
  return { ...data, email, nickname };
}

const whoAmI = (accessToken) =>
  fetch(`${BASE}/api/users/me`, { headers: { Authorization: `Bearer ${accessToken}` } })
    .then((r) => (r.ok ? r.json() : null))
    .then((body) => body?.data?.email ?? null);

const browser = await chromium.launch();

/** A의 만료된 세션으로 시작하는 컨텍스트 + A 재발급 응답을 붙잡는 route. */
async function openWithExpiredSession(accountA, { noLocks }) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript(([r, keys, disableLocks]) => {
    if (disableLocks) Object.defineProperty(Navigator.prototype, 'locks', { get: () => undefined, configurable: true });
    if (!localStorage.getItem('__seeded')) {
      localStorage.setItem(keys[0], 'expired.access.token');
      localStorage.setItem(keys[1], r);
      localStorage.setItem('__seeded', '1');
    }
  }, [accountA.refreshToken, [ACCESS_KEY, REFRESH_KEY], noLocks]);
  let release;
  const released = new Promise((r) => { release = r; });
  let arrived;
  const seen = new Promise((r) => { arrived = r; });
  const refreshStatuses = [];
  await context.route('**/api/auth/refresh', async (route) => {
    arrived();
    await released;
    const response = await route.fetch();
    refreshStatuses.push(response.status());
    await route.fulfill({ response });
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  return { context, page, errors, release, seen, refreshStatuses };
}

async function submitLogin(page, email, password) {
  await page.getByLabel('이메일').fill(email);
  await page.getByLabel('비밀번호', { exact: true }).fill(password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
}

// 1) 복원 중 B로 로그인 — A의 재발급 결과가 B의 토큰을 덮지 않는다.
async function loginDuringRestore(label, noLocks) {
  const accountA = await createAccount(`a${noLocks ? 'n' : 'l'}`);
  const accountB = await createAccount(`b${noLocks ? 'n' : 'l'}`);
  const { context, page, errors, release, seen, refreshStatuses } = await openWithExpiredSession(accountA, { noLocks });
  let loginTokensB = null;
  page.on('response', async (r) => {
    if (r.url().includes('/api/auth/login') && r.ok()) loginTokensB = (await r.json()).data;
  });
  await page.goto(`${BASE}/login`);
  await seen;
  if (noLocks) ok(`${label}: navigator.locks 없음 확인`, await page.evaluate(() => navigator.locks === undefined));
  ok(`${label}: A의 재발급 요청이 진행 중(응답 대기)`, refreshStatuses.length === 0);

  await submitLogin(page, accountB.email, PASSWORD);
  // B 로그인 응답이 먼저 도착하게 잠시 기다린 뒤 A의 재발급 응답을 풀어 준다.
  await page.waitForTimeout(1000);
  release();
  await page.waitForURL(`${BASE}/`, { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(500);

  ok(`${label}: A의 재발급 응답이 도착함(200)`, JSON.stringify(refreshStatuses) === '[200]');
  const [access, refresh] = await page.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  ok(`${label}: B 로그인 응답을 받음`, Boolean(loginTokensB?.refreshToken));
  ok(`${label}: 저장된 토큰이 B 로그인 응답 그대로(A 재발급 결과로 덮이지 않음)`, Boolean(loginTokensB) && access === loginTokensB.accessToken && refresh === loginTokensB.refreshToken);
  ok(`${label}: 저장된 Access Token이 B 계정`, (await whoAmI(access)) === accountB.email);
  const menu = page.locator('header').first().getByRole('button', { name: /계정 메뉴/ });
  const menuShown = await menu.waitFor({ timeout: 5000 }).then(() => true, () => false);
  ok(`${label}: 화면에 B 닉네임 표시`, menuShown && (await menu.textContent()).includes(accountB.nickname));

  await page.reload();
  await page.waitForLoadState('networkidle');
  const menuAfterReload = page.locator('header').first().getByRole('button', { name: /계정 메뉴/ });
  const shownAfterReload = await menuAfterReload.waitFor({ timeout: 5000 }).then(() => true, () => false);
  ok(`${label}: 새로고침 후에도 B로 로그인 유지`, shownAfterReload && (await menuAfterReload.textContent()).includes(accountB.nickname));
  const [accessAfterReload] = await page.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  ok(`${label}: 새로고침 후 저장된 토큰도 B 계정`, (await whoAmI(accessAfterReload)) === accountB.email);
  ok(`${label}: pageerror 없음`, errors.length === 0);
  await context.close();
}

await loginDuringRestore('Web Locks', false);
await loginDuringRestore('Web Locks 없음(compare-and-set만)', true);

// 3) 로그인 저장이 재발급 락을 기다리는 구간 — "화면은 B, API 요청은 A 토큰"이 되지 않는지.
//    holdMs 동안 A 재발급을 붙잡는다. 1500ms면 로그인 저장이 락 안에서 끝나고, 7000ms면 락 대기 상한(5초)을
//    넘겨 storeTokens의 대체 경로(락 없이 저장)로 끝난다 — 그때 A 재발급은 아직 진행 중이다.
async function authRequestsDuringLockWait(label, holdMs) {
  const accountA = await createAccount(`w${holdMs}`);
  const accountB = await createAccount(`x${holdMs}`);
  const { context, page, errors, release, seen, refreshStatuses } = await openWithExpiredSession(accountA, { noLocks: false });
  let loginTokensB = null;
  let loginResponseAt = 0;
  page.on('response', async (r) => {
    if (r.url().includes('/api/auth/login') && r.ok()) {
      // 토큰을 채운 뒤에 도착 시각을 기록한다 — 반대 순서면 대기 루프가 JSON 파싱 전에 끝나 토큰 단언이 간헐 실패했다.
      const at = Date.now();
      loginTokensB = (await r.json()).data;
      loginResponseAt = at;
    }
  });
  // 인증 헤더가 붙은 앱 요청을, 요청 시점의 화면 URL과 함께 기록한다(/api/auth/*는 제외).
  const authedRequests = [];
  page.on('request', (r) => {
    const url = r.url();
    if (!url.includes('/api/') || url.includes('/api/auth/')) return;
    const auth = r.headers().authorization;
    if (auth) authedRequests.push({ url, token: auth.replace(/^Bearer /, ''), at: Date.now(), screen: new URL(page.url()).pathname });
  });
  await page.goto(`${BASE}/login`);
  await seen;
  await submitLogin(page, accountB.email, PASSWORD);
  const startedAt = Date.now();

  // 재발급을 풀기 전: 로그인 응답은 왔지만 저장이 락을 기다리는 중이다.
  for (let i = 0; i < 50 && !loginResponseAt; i++) await page.waitForTimeout(100);
  ok(`${label}: B 로그인 응답을 받음`, Boolean(loginTokensB?.accessToken));
  await page.waitForTimeout(500);
  const stillWaiting = holdMs > 5000 ? Date.now() - startedAt < 4500 : true;
  if (stillWaiting) {
    ok(`${label}: 저장 전에는 화면이 로그인 화면 그대로(B로 전환 안 됨)`, new URL(page.url()).pathname === '/login');
    const storedBefore = await page.evaluate((k) => localStorage.getItem(k), ACCESS_KEY);
    ok(`${label}: 저장 전 저장소에는 아직 B 토큰이 없음`, storedBefore !== loginTokensB?.accessToken);
    // 앱 자신의 httpClient(같은 모듈 인스턴스·같은 인터셉터)로 인증 요청을 보낸다.
    const probe = await page.evaluate(async () => {
      const { httpClient } = await import('/src/lib/httpClient.ts');
      return httpClient.get('/api/users/me').then((r) => ({ status: r.status, email: r.data?.data?.email }), (e) => ({ status: e.response?.status ?? 0 }));
    });
    const probeReq = authedRequests.filter((r) => r.url.endsWith('/api/users/me')).at(-1);
    ok(`${label}: 저장 전 인증 요청은 B 토큰이 아님(B로 인증되지 않음, ${probe.status})`, probeReq?.token !== loginTokensB?.accessToken && probe.email !== accountB.email);
    ok(`${label}: 그 시점 화면도 B가 아님(로그인 화면)`, probeReq?.screen === '/login');
  }

  if (holdMs > 5000) {
    // 락 대기 상한을 넘겨 대체 경로로 저장되고 화면이 B로 넘어간다 — A 재발급은 아직 붙잡혀 있다.
    const navigated = await page.waitForURL(`${BASE}/`, { timeout: 8000 }).then(() => true, () => false);
    ok(`${label}: 락 대기 상한 뒤 대체 경로로 저장되어 홈으로 이동(${Date.now() - startedAt}ms)`, navigated);
    ok(`${label}: 그 시점 A 재발급은 아직 진행 중`, refreshStatuses.length === 0);
    await page.waitForLoadState('networkidle');
    const menu = page.locator('header').first().getByRole('button', { name: /계정 메뉴/ });
    const shown = await menu.waitFor({ timeout: 5000 }).then(() => true, () => false);
    ok(`${label}: 재발급 진행 중에도 화면은 B`, shown && (await menu.textContent()).includes(accountB.nickname));
    const whileHeld = authedRequests.filter((r) => r.screen !== '/login');
    ok(`${label}: 화면이 B로 바뀐 뒤 인증 요청이 있음(${whileHeld.length}건)`, whileHeld.length > 0);
    ok(`${label}: 그 요청은 전부 B 토큰`, whileHeld.every((r) => r.token === loginTokensB.accessToken));
  }

  const releasedAt = Date.now();
  await page.waitForTimeout(Math.max(0, holdMs - (releasedAt - startedAt)));
  release();
  await page.waitForURL(`${BASE}/`, { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(800);

  ok(`${label}: A 재발급 응답 도착(200)`, JSON.stringify(refreshStatuses) === '[200]');
  const afterSwitch = authedRequests.filter((r) => r.screen !== '/login');
  ok(`${label}: 화면이 B로 바뀐 뒤의 인증 요청이 있음(${afterSwitch.length}건)`, afterSwitch.length > 0);
  ok(`${label}: 화면이 B로 바뀐 뒤의 인증 요청은 전부 B 토큰`, afterSwitch.every((r) => r.token === loginTokensB.accessToken));
  const [access, refresh] = await page.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  ok(`${label}: 재발급 응답 후에도 저장소는 B 로그인 결과 그대로`, access === loginTokensB.accessToken && refresh === loginTokensB.refreshToken);
  ok(`${label}: pageerror 없음`, errors.length === 0);
  await context.close();
}

await authRequestsDuringLockWait('락 대기 중(1.5초)', 1500);
await authRequestsDuringLockWait('락 대기 상한 초과(7초)', 7000);

// 2) 복원 중 로그인이 실패(비밀번호 틀림)해도 복원 결과가 버려지지 않고 헤더가 확정된다.
//    (로그인 "시작"에 세대 값을 올리면 실패한 로그인이 복원 결과까지 버려, 헤더가 확인 중에 머문다.)
{
  const accountA = await createAccount('c');
  const { context, page, errors, release, seen } = await openWithExpiredSession(accountA, { noLocks: false });
  await page.goto(`${BASE}/login`);
  await seen;
  await submitLogin(page, accountA.email, 'WrongPass1!');
  const loginFailedShown = await page.getByText('비밀번호가 일치하지 않습니다').waitFor({ timeout: 5000 }).then(() => true, () => false);
  ok('복원 중 로그인 실패: 실패 문구 표시', loginFailedShown);
  release();
  // 로그인 화면에는 헤더가 없으므로, 복원이 끝난 뒤 페이지를 새로 불러오지 않고(= 복원을 다시 시작하지
  // 않고) SPA 안에서 홈으로 이동해 같은 AuthProvider 상태의 헤더를 확인한다.
  await page.waitForTimeout(1000);
  await page.evaluate(() => { history.pushState({}, '', '/'); dispatchEvent(new PopStateEvent('popstate')); });
  const restoreRequestsAfterNav = [];
  page.on('request', (r) => { if (r.url().includes('/api/users/me') || r.url().includes('/api/auth/refresh')) restoreRequestsAfterNav.push(r.url()); });
  const menu = page.locator('header').first().getByRole('button', { name: /계정 메뉴/ });
  const settled = await menu.waitFor({ timeout: 5000 }).then(() => true, () => false);
  ok('복원 중 로그인 실패: 복원된 A 세션으로 헤더가 확정됨', settled && (await menu.textContent()).includes(accountA.nickname));
  ok('복원 중 로그인 실패: 페이지를 다시 불러오지 않고 확인(복원 재시작 없음)', restoreRequestsAfterNav.length === 0);
  ok('pageerror 없음(로그인 실패)', errors.length === 0);
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed (total: ${pass + fail})`);
process.exit(fail ? 1 : 0);
