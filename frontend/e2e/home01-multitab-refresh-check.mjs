// 여러 탭이 동시에 세션을 복원할 때 Refresh Token 재발급이 브라우저 전체에서 한 번만 나가는지 검증한다.
// 탭마다 따로 있는 모듈 변수만으로 single-flight를 하면, Access Token이 만료된 채 두 탭을 동시에 열었을 때
// 두 탭이 같은 Refresh Token으로 재발급을 보낸다 — 백엔드 Rotation 재사용 탐지가 두 번째 요청을 탈취로
// 보고 그 사용자의 Refresh Token을 전부 폐기하고, 거절당한 탭이 공유 localStorage까지 지워 세션 전체가 끝났다.
// 재발급 응답을 지연시켜 두 탭의 재발급이 반드시 겹치게 만든다.
// 실 백엔드(8080)+Redis 필요 — 가입 API로 테스트 계정을 만든다. BASE로 dev 서버 지정.
import { chromium } from 'playwright';

const BASE = process.env.BASE ?? 'http://localhost:5173';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const ACCESS_KEY = 'homesense.accessToken';
const REFRESH_KEY = 'homesense.refreshToken';
const REFRESH_DELAY_MS = 800;

async function createAccount(tag) {
  const suffix = `${Date.now().toString(36).slice(-5)}${tag}`;
  const res = await fetch(`${BASE}/api/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `multitab-${suffix}@test.com`, password: 'Passw0rd!', nickname: `탭${suffix}`.slice(0, 12), ageConfirmed: true }),
  });
  const data = (await res.json()).data;
  if (!data?.refreshToken) throw new Error(`테스트 계정 가입 실패 ${res.status}`);
  return data;
}

const browser = await chromium.launch();

async function runScenario(tabCount) {
  const account = await createAccount(String(tabCount));
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  // 두 탭이 localStorage를 공유한다(같은 컨텍스트·같은 오리진). 처음 한 번만 만료된 Access Token을 심는다.
  await context.addInitScript(([r, keys]) => {
    if (!localStorage.getItem('__seeded')) {
      localStorage.setItem(keys[0], 'expired.access.token');
      localStorage.setItem(keys[1], r);
      localStorage.setItem('__seeded', '1');
    }
  }, [account.refreshToken, [ACCESS_KEY, REFRESH_KEY]]);

  const refreshStatuses = [];
  await context.route('**/api/auth/refresh', async (route) => {
    await new Promise((r) => setTimeout(r, REFRESH_DELAY_MS));
    const response = await route.fetch();
    refreshStatuses.push(response.status());
    await route.fulfill({ response });
  });

  const pages = await Promise.all(Array.from({ length: tabCount }, () => context.newPage()));
  const errors = [];
  pages.forEach((p) => p.on('pageerror', (e) => errors.push(String(e))));
  await Promise.all(pages.map((p) => p.goto(`${BASE}/`)));
  await Promise.all(pages.map((p) => p.waitForLoadState('networkidle')));
  await pages[0].waitForTimeout(REFRESH_DELAY_MS);

  const label = `${tabCount}개 탭`;
  ok(`${label}: 재발급 요청이 브라우저 전체에서 정확히 1회`, refreshStatuses.length === 1);
  ok(`${label}: 재발급 응답 200`, JSON.stringify(refreshStatuses) === '[200]');
  for (const [i, p] of pages.entries()) {
    const menuShown = await p.locator('header').first().getByRole('button', { name: /계정 메뉴/ }).waitFor({ timeout: 5000 }).then(() => true, () => false);
    ok(`${label}: 탭 ${i + 1} 로그인 유지(계정 메뉴 표시)`, menuShown);
  }
  const [access, refresh] = await pages[0].evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  ok(`${label}: 공유 저장소에 새 토큰이 남아 있음(지워지지 않음)`, Boolean(access && refresh) && access !== 'expired.access.token' && refresh !== account.refreshToken);

  // 남은 토큰이 서버에서 실제로 유효한지 — 재사용 탐지로 전부 폐기됐다면 여기서 401이 난다.
  const meStatus = await fetch(`${BASE}/api/users/me`, { headers: { Authorization: `Bearer ${access}` } }).then((r) => r.status);
  ok(`${label}: 남은 Access Token으로 GET /api/users/me 200`, meStatus === 200);
  const refreshStillValid = await fetch(`${BASE}/api/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: refresh }),
  }).then((r) => r.ok);
  ok(`${label}: 남은 Refresh Token이 서버에서 폐기되지 않음`, refreshStillValid);
  ok(`${label}: pageerror 없음`, errors.length === 0);
  await context.close();
}

await runScenario(2);
await runScenario(3);

// 재발급 응답이 무기한 멈춘 경우 — 락을 쥔 탭이 멈춰도 다른 탭의 인증이 영원히 막히지 않아야 한다.
// 정해진 처리: 기다리던 탭은 락 대기 상한(5초) 뒤, 락을 쥔 탭은 재발급 timeout(10초) 뒤 "판정 불가"로 끝난다
// (화면은 비로그인, 토큰은 지우지 않음, 같은 토큰으로 재발급을 다시 보내지 않음). 응답이 풀리면 새로고침으로 복구된다.
{
  const account = await createAccount('h');
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript(([r, keys]) => {
    if (!localStorage.getItem('__seeded')) {
      localStorage.setItem(keys[0], 'expired.access.token');
      localStorage.setItem(keys[1], r);
      localStorage.setItem('__seeded', '1');
    }
  }, [account.refreshToken, [ACCESS_KEY, REFRESH_KEY]]);
  const held = [];
  let holding = true;
  await context.route('**/api/auth/refresh', async (route) => {
    if (holding) { held.push(route); return; } // 응답하지 않고 붙잡아 둔다(서버에는 도달하지 않음)
    await route.continue();
  });
  const refreshRequests = [];
  context.on('request', (r) => { if (r.url().includes('/api/auth/refresh')) refreshRequests.push(Date.now()); });
  const readTokens = (p) => p.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  const loginShown = (p) => p.locator('header').first().getByRole('link', { name: '로그인' }).isVisible();
  const menuShown = (p) => p.locator('header').first().getByRole('button', { name: /계정 메뉴/ }).isVisible();

  const tab1 = await context.newPage();
  await tab1.goto(`${BASE}/`);
  const heldArrived = await (async () => { for (let i = 0; i < 50 && held.length === 0; i++) await tab1.waitForTimeout(100); return held.length === 1; })();
  ok('멈춤: 탭 1의 재발급 요청이 붙잡힘(락 보유)', heldArrived);

  const tab2 = await context.newPage();
  const tab2Start = Date.now();
  await tab2.goto(`${BASE}/`);
  const tab2Settled = await tab2.locator('header').first().getByRole('link', { name: '로그인' }).waitFor({ timeout: 9000 }).then(() => true, () => false);
  const tab2Elapsed = Date.now() - tab2Start;
  ok(`멈춤: 탭 2가 락 대기 상한 뒤 비로그인으로 끝남(${tab2Elapsed}ms)`, tab2Settled && tab2Elapsed >= 4500 && tab2Elapsed < 9000);
  ok('멈춤: 탭 2가 확인 중 상태에 남지 않음(로그인·계정 메뉴 중 하나로 확정)', (await loginShown(tab2)) || (await menuShown(tab2)));
  ok('멈춤: 탭 2는 같은 토큰으로 재발급을 다시 보내지 않음', refreshRequests.length === 1);
  const [a2, r2] = await readTokens(tab2);
  ok('멈춤: 탭 2 종료 후에도 토큰 유지(지우지 않음)', a2 === 'expired.access.token' && r2 === account.refreshToken);

  const tab1Settled = await tab1.locator('header').first().getByRole('link', { name: '로그인' }).waitFor({ timeout: 8000 }).then(() => true, () => false);
  ok('멈춤: 탭 1도 재발급 timeout 뒤 비로그인으로 끝남(락 해제)', tab1Settled);
  const [a1, r1] = await readTokens(tab1);
  ok('멈춤: 탭 1 종료 후에도 토큰 유지', a1 === 'expired.access.token' && r1 === account.refreshToken);
  ok('멈춤: 전체 재발급 요청은 여전히 1회', refreshRequests.length === 1);

  // 응답이 풀리면 다음 로드에서 정상 복구된다(붙잡힌 요청은 서버에 도달하지 않았으므로 원래 토큰이 유효).
  holding = false;
  await Promise.all(held.map((r) => r.abort().catch(() => {})));
  await tab2.reload();
  await tab2.waitForLoadState('networkidle');
  const recovered = await tab2.locator('header').first().getByRole('button', { name: /계정 메뉴/ }).waitFor({ timeout: 5000 }).then(() => true, () => false);
  ok('멈춤 해소 후 새로고침: 로그인 복구', recovered);
  await context.close();
}

// Web Locks API가 없는 브라우저 폴백 — 탭 안의 직렬화만 남으므로 단일 탭 재발급이 그대로 동작해야 한다.
{
  const account = await createAccount('f');
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript(([r, keys]) => {
    Object.defineProperty(Navigator.prototype, 'locks', { get: () => undefined, configurable: true });
    if (!localStorage.getItem('__seeded')) {
      localStorage.setItem(keys[0], 'expired.access.token');
      localStorage.setItem(keys[1], r);
      localStorage.setItem('__seeded', '1');
    }
  }, [account.refreshToken, [ACCESS_KEY, REFRESH_KEY]]);
  const refreshCalls = [];
  context.on('request', (r) => { if (r.url().includes('/api/auth/refresh')) refreshCalls.push(r); });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto(`${BASE}/`);
  await page.waitForLoadState('networkidle');
  ok('폴백: navigator.locks 없음 확인', await page.evaluate(() => navigator.locks === undefined));
  const menuShown = await page.locator('header').first().getByRole('button', { name: /계정 메뉴/ }).waitFor({ timeout: 5000 }).then(() => true, () => false);
  ok('폴백: 단일 탭 로그인 유지', menuShown);
  ok('폴백: 재발급 1회(StrictMode 이중 effect에도)', refreshCalls.length === 1);
  ok('폴백: pageerror 없음', errors.length === 0);
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed (total: ${pass + fail})`);
process.exit(fail ? 1 : 0);
