// 401 인터셉터(사용 중 Access Token 만료 → 재발급 → 재시도)가 여러 탭에서 동시에 일어나도 재발급이 브라우저
// 전체에서 한 번만 나가는지 검증한다. 같은 Refresh Token으로 재발급이 두 번 나가면 백엔드 Rotation의 재사용
// 탐지가 그 사용자의 세션을 전부 폐기한다(CLAUDE.md "401 자동 재발급과 요청 timeout" 절).
//
// 서버는 page.route로 흉내 낸다(백엔드 불필요). /api/users/me는 옛 Access Token(A1)도 통과시켜 세션 복원에서는
// 재발급이 일어나지 않게 하고, 보호 API(/api/favorites/**, /api/regions/interest-summary)만 A1을 401(UNAUTHORIZED)로
// 거부한다 — 로드 직후 두 탭이 각자 보호 API를 부르며 "사용 중 401"을 동시에 받는다. 이어서 두 탭에서 앱의
// httpClient로 보호 API를 동시에 여러 번 호출해 같은 상황을 한 번 더 만든다.
// 재발급 응답을 지연시켜 두 탭의 재발급 시도가 반드시 겹치게 한다. BASE로 dev 서버 지정.
import { chromium } from 'playwright';

import { BASE } from './base.mjs';
import { errBody, okBody } from './mockApi.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); }
}

const ACCESS_KEY = 'homesense.accessToken';
const REFRESH_KEY = 'homesense.refreshToken';
const REFRESH_DELAY_MS = 800;


const browser = await chromium.launch();

async function runScenario(label, { noLocks }) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript(([keys, disableLocks]) => {
    if (disableLocks) {
      try { Object.defineProperty(Navigator.prototype, 'locks', { get: () => undefined, configurable: true }); } catch { /* noop */ }
    }
    if (!localStorage.getItem('__seeded')) {
      localStorage.setItem(keys[0], 'A1');
      localStorage.setItem(keys[1], 'R1');
      localStorage.setItem('__seeded', '1');
    }
  }, [[ACCESS_KEY, REFRESH_KEY], noLocks]);

  const refreshBodies = [];
  const protectedAuth = [];
  let valid = { access: 'A2', refresh: 'R2' };
  let currentRefresh = 'R1';

  await context.route('**/api/**', async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const auth = (req.headers()['authorization'] ?? '').replace('Bearer ', '');
    if (url.pathname === '/api/auth/refresh') {
      const { refreshToken } = JSON.parse(req.postData() ?? '{}');
      refreshBodies.push(refreshToken);
      await new Promise((r) => setTimeout(r, REFRESH_DELAY_MS));
      if (refreshToken === currentRefresh) {
        currentRefresh = valid.refresh;
        return route.fulfill({ status: 200, contentType: 'application/json', body: okBody({ accessToken: valid.access, refreshToken: valid.refresh, expiresIn: 1800 }) });
      }
      // 이미 교체된 토큰의 재사용 — 실제 백엔드는 이때 이 회원의 모든 토큰을 폐기한다.
      return route.fulfill({ status: 401, contentType: 'application/json', body: errBody('INVALID_REFRESH_TOKEN', '유효하지 않은 Refresh Token입니다') });
    }
    if (url.pathname === '/api/users/me') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: okBody({ userId: 1, email: 'tab@test.com', nickname: '탭사용자', createdAt: '' }) });
    }
    if (url.pathname.startsWith('/api/favorites') || url.pathname === '/api/regions/interest-summary') {
      protectedAuth.push(auth);
      if (auth === valid.access) return route.fulfill({ status: 200, contentType: 'application/json', body: okBody([]) });
      return route.fulfill({ status: 401, contentType: 'application/json', body: errBody('UNAUTHORIZED', '인증이 필요합니다') });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: okBody([]) });
  });

  const pages = [await context.newPage(), await context.newPage()];
  const errors = [];
  pages.forEach((p) => p.on('pageerror', (e) => errors.push(String(e))));

  // 1) 로드 직후 두 탭이 동시에 보호 API를 부르며 401을 받는다.
  await Promise.all(pages.map((p) => p.goto(`${BASE}/`)));
  await Promise.all(pages.map((p) => p.waitForLoadState('networkidle')));
  await pages[0].waitForTimeout(REFRESH_DELAY_MS + 500);

  ok(`${label}: 로드 직후 동시 401 → 재발급 요청이 브라우저 전체에서 정확히 1회`, refreshBodies.length === 1, JSON.stringify(refreshBodies));
  ok(`${label}: 재발급에 쓴 Refresh Token은 R1 하나뿐(재사용 없음)`, refreshBodies.every((t) => t === 'R1'));
  const stored = await pages[1].evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  ok(`${label}: 저장소가 새 토큰(A2, R2)`, stored[0] === 'A2' && stored[1] === 'R2', JSON.stringify(stored));
  ok(`${label}: 보호 API의 마지막 요청들은 새 토큰으로 성공`, protectedAuth.filter((a) => a === 'A2').length >= 2, JSON.stringify(protectedAuth));

  // 2) 서버가 A2도 만료시킨 뒤, 두 탭에서 앱의 httpClient로 보호 API를 동시에 3번씩 부른다.
  valid = { access: 'A3', refresh: 'R3' };
  refreshBodies.length = 0;
  // 앱이 실제로 불러온 모듈 URL(dev 서버가 변경 후 ?t= 쿼리를 붙인다)을 그대로 import해야 같은 인스턴스
  // (같은 인터셉터·같은 재발급 상태)를 쓴다.
  const probe = () => import(
    performance.getEntriesByType('resource').map((e) => e.name).find((n) => n.includes('/src/lib/httpClient.ts')) ?? '/src/lib/httpClient.ts'
  ).then(({ httpClient }) =>
    Promise.all([1, 2, 3].map(() => httpClient.get('/api/favorites/properties').then((r) => r.status, (e) => e.response?.status ?? 0))));
  const results = await Promise.all(pages.map((p) => p.evaluate(probe)));
  ok(`${label}: 동시 요청 6건 모두 재시도 후 200`, results.flat().every((s) => s === 200), JSON.stringify(results));
  ok(`${label}: 재발급은 이번에도 정확히 1회(R2)`, refreshBodies.length === 1 && refreshBodies[0] === 'R2', JSON.stringify(refreshBodies));

  // 두 탭 모두 로그인 상태 유지(헤더 아바타 = 계정 메뉴 버튼, 로그인 링크 없음).
  for (const [i, p] of pages.entries()) {
    const loginLinks = await p.getByRole('link', { name: '로그인', exact: true }).count();
    ok(`${label}: 탭 ${i + 1} 로그인 상태 유지(로그인 링크 없음)`, loginLinks === 0);
  }
  ok(`${label}: pageerror 없음`, errors.length === 0, errors.join(' | '));
  await context.close();
}

await runScenario('Web Locks 있음', { noLocks: false });

// 다른 계정 — 탭 2가 계정 B로 로그인한 직후 탭 1(계정 A 화면)의 요청이 401을 받아도 B 토큰으로 재시도하지 않는다.
// 저장소(localStorage)는 탭끼리 공유되므로, 재시도가 저장소 값을 그대로 쓰면 A 화면의 요청이 B 계정으로 실행된다.
// 토큰은 서명 없는 JWT 모양(payload에 sub)으로 흉내 낸다 — 클라이언트는 sub만 읽는다.
{
  const jwt = (sub, nonce) => {
    const b64 = (v) => Buffer.from(JSON.stringify(v)).toString('base64url');
    return `${b64({ alg: 'HS256' })}.${b64({ sub, jti: nonce })}.sig`;
  };
  const A1 = jwt('1', 'a1');
  const B1 = jwt('2', 'b1');
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript(([keys, access]) => {
    if (!localStorage.getItem('__seeded')) {
      localStorage.setItem(keys[0], access);
      localStorage.setItem(keys[1], 'RA1');
      localStorage.setItem('__seeded', '1');
    }
  }, [[ACCESS_KEY, REFRESH_KEY], A1]);

  let aExpired = false;
  // 탭 1의 A 토큰 요청 응답을 붙잡아, 그 사이 탭 2가 로그인하게 한다(요청은 A로 나갔고 401은 로그인 뒤에 도착).
  let holdTab1 = false;
  let releaseTab1;
  const tab1Released = new Promise((r) => { releaseTab1 = r; });
  let tab1Held;
  const tab1HeldSeen = new Promise((r) => { tab1Held = r; });
  const refreshCalls = [];
  const tab1Protected = [];
  let tab1;
  await context.route('**/api/**', async (route) => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    const auth = (req.headers()['authorization'] ?? '').replace('Bearer ', '');
    const fromTab1 = tab1 && req.frame()?.page() === tab1;
    if (path === '/api/auth/login') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: okBody({ accessToken: B1, refreshToken: 'RB1', expiresIn: 1800 }) });
    }
    if (path === '/api/auth/refresh') {
      refreshCalls.push(JSON.parse(req.postData() ?? '{}').refreshToken);
      return route.fulfill({ status: 401, contentType: 'application/json', body: errBody('INVALID_REFRESH_TOKEN', 'x') });
    }
    if (path === '/api/users/me') {
      const who = auth === B1 ? { userId: 2, nickname: '계정B' } : { userId: 1, nickname: '계정A' };
      return route.fulfill({ status: 200, contentType: 'application/json', body: okBody({ ...who, email: `${who.userId}@test.com`, createdAt: '' }) });
    }
    if (path.startsWith('/api/favorites')) {
      if (fromTab1) tab1Protected.push(auth);
      if (fromTab1 && holdTab1 && auth === A1) {
        tab1Held();
        await tab1Released;
      }
      const valid = auth === B1 || (auth === A1 && !aExpired);
      if (valid) return route.fulfill({ status: 200, contentType: 'application/json', body: okBody([]) });
      return route.fulfill({ status: 401, contentType: 'application/json', body: errBody('UNAUTHORIZED', '인증이 필요합니다') });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: okBody([]) });
  });

  tab1 = await context.newPage();
  const errors = [];
  tab1.on('pageerror', (e) => errors.push(String(e)));
  await tab1.goto(`${BASE}/`);
  await tab1.waitForLoadState('networkidle');
  const menuA = tab1.locator('header').first().getByRole('button', { name: /계정 메뉴/ });
  ok('다른 계정: 탭 1이 계정 A로 로그인 상태', (await menuA.waitFor({ timeout: 5000 }).then(() => true, () => false)) && (await menuA.textContent()).includes('계정A'));

  // 서버가 계정 A의 토큰을 만료시킨다. 탭 1이 보호 API를 부르고(A 토큰), 응답이 오기 전에 탭 2가 계정 B로 로그인한다.
  aExpired = true;
  holdTab1 = true;
  tab1Protected.length = 0;
  const probe = () => import(
    performance.getEntriesByType('resource').map((e) => e.name).find((n) => n.includes('/src/lib/httpClient.ts')) ?? '/src/lib/httpClient.ts'
  ).then(({ httpClient }) => httpClient.get('/api/favorites/properties').then((r) => r.status, (e) => e.response?.status ?? 0));
  const statusPromise = tab1.evaluate(probe);
  await tab1HeldSeen;

  const tab2 = await context.newPage();
  await tab2.goto(`${BASE}/login`);
  await tab2.getByLabel('이메일').fill('b@test.com');
  await tab2.getByLabel('비밀번호', { exact: true }).fill('Passw0rd!');
  await tab2.getByRole('button', { name: '로그인', exact: true }).click();
  await tab2.waitForURL(`${BASE}/`);
  const storedAfterLogin = await tab1.evaluate((k) => localStorage.getItem(k), ACCESS_KEY);
  ok('다른 계정: 탭 1 요청 대기 중 공유 저장소가 계정 B의 토큰으로 바뀜', storedAfterLogin === B1);

  releaseTab1(); // 이제 탭 1의 A 토큰 요청에 401이 도착한다.
  const status = await statusPromise;
  ok('다른 계정: 탭 1의 원 요청은 A 토큰으로 나갔음', tab1Protected[0] === A1);
  ok('다른 계정: 탭 1의 요청은 401로 실패(재시도 안 함)', status === 401, `status=${status}`);
  ok('다른 계정: 탭 1이 계정 B의 토큰으로 보낸 보호 요청이 없음', !tab1Protected.includes(B1), JSON.stringify(tab1Protected.map((t) => (t === A1 ? 'A1' : t === B1 ? 'B1' : t))));
  ok('다른 계정: 재발급을 보내지 않음(저장소가 이미 다른 값)', refreshCalls.length === 0, JSON.stringify(refreshCalls));
  ok('다른 계정: 저장소는 계정 B 그대로', (await tab1.evaluate((k) => localStorage.getItem(k), ACCESS_KEY)) === B1);
  ok('다른 계정: pageerror 없음', errors.length === 0, errors.join(' | '));
  await context.close();
}

await browser.close();

console.log(`\n${pass}/${pass + fail} passed`);
process.exit(fail > 0 ? 1 : 0);
