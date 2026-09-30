// 토큰 저장소(localStorage)는 탭끼리 공유된다. 탭 2가 계정 B로 로그인하면, 아직 계정 A를 보여 주는 탭 1의
// 새 요청이 처음부터 B 토큰으로 나갈 수 있었다(CLAUDE.md "탭 계정 동기화" 절). 검증하는 것:
// 1) 요청 방어 — 탭 1이 저장소 변경을 아직 모르는 상태(storage 이벤트를 막음)에서 관심 단지 추가를 누르면,
//    요청이 B 토큰으로 서버에 도달하지 않고, 탭 1은 다시 확인을 거쳐 B를 보여 준다.
// 2) 화면 동기화 — storage 이벤트를 받으면 탭 1이 확인 중(자리 표시)을 거쳐 B로 바뀐다.
// 3) 다른 탭의 로그아웃 — 탭 1이 비로그인으로 바뀐다.
// 실 백엔드(8080)+Redis 필요 — 가입 API로 테스트 계정 A·B를 만든다(실제 JWT, sub = 회원 ID). page.route는
// 확인 중 구간을 붙잡아 두는 응답 지연(2)에만 쓴다. BASE로 dev 서버 지정.
import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); }
}

const ACCESS_KEY = 'homesense.accessToken';
const REFRESH_KEY = 'homesense.refreshToken';
const PASSWORD = 'Passw0rd!';

async function createAccount(tag) {
  const suffix = `${Date.now().toString(36).slice(-5)}${tag}`;
  const email = `tabsync-${suffix}@test.com`;
  const nickname = `탭${tag}${suffix}`.slice(0, 12);
  const res = await fetch(`${BASE}/api/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD, nickname, ageConfirmed: true }),
  });
  const data = (await res.json()).data;
  if (!data?.refreshToken) throw new Error(`테스트 계정 가입 실패 ${res.status}`);
  return { ...data, email, nickname };
}

const jwtSub = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()).sub;
const favoritesOf = async (accessToken) =>
  (await (await fetch(`${BASE}/api/favorites/properties`, { headers: { Authorization: `Bearer ${accessToken}` } })).json()).data ?? [];

const browser = await chromium.launch();

/** 계정 A로 로그인된 탭 1을 연다. `blockStorageEvents`면 탭 1이 storage 이벤트를 받지 못하게 한다. */
async function openScenario(accountA, { blockStorageEvents = false } = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript(([keys, access, refresh]) => {
    if (!localStorage.getItem('__seeded')) {
      localStorage.setItem(keys[0], access);
      localStorage.setItem(keys[1], refresh);
      localStorage.setItem('__seeded', '1');
    }
  }, [[ACCESS_KEY, REFRESH_KEY], accountA.accessToken, accountA.refreshToken]);

  const tab1 = await context.newPage();
  // 탭 1이 서버로 보낸 관심 매물 등록·해제 요청(실제로 네트워크에 나간 것만 잡힌다 — 인터셉터가 거절한 요청은 없다).
  const favoriteWrites = [];
  tab1.on('request', (r) => {
    if (r.url().includes('/api/favorites/properties') && r.method() !== 'GET') {
      favoriteWrites.push((r.headers()['authorization'] ?? '').replace('Bearer ', ''));
    }
  });
  if (blockStorageEvents) {
    // 앱보다 먼저 등록한 리스너가 전파를 멈춰, 앱의 storage 리스너가 이벤트를 받지 못하게 한다.
    await tab1.addInitScript(() => window.addEventListener('storage', (e) => e.stopImmediatePropagation(), true));
  }
  const errors = [];
  tab1.on('pageerror', (e) => errors.push(String(e)));
  await tab1.goto(`${BASE}/`);
  await tab1.waitForLoadState('networkidle');
  return { context, tab1, errors, favoriteWrites };
}

const header = (page) => page.locator('header:visible').first();
const accountMenu = (page) => header(page).getByRole('button', { name: /계정 메뉴/ });
const showsAccount = async (page, nickname, timeout = 8000) =>
  accountMenu(page).filter({ hasText: nickname }).waitFor({ timeout }).then(() => true, () => false);

async function loginInNewTab(context, account) {
  const tab2 = await context.newPage();
  await tab2.goto(`${BASE}/login`);
  await tab2.getByLabel('이메일').fill(account.email);
  await tab2.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
  await tab2.getByRole('button', { name: '로그인', exact: true }).click();
  await tab2.waitForURL(`${BASE}/`);
  return tab2;
}

// 실제 JWT의 sub 형식(회원 ID 문자열) 확인 — 클라이언트가 비교하는 값이다.
{
  const probe = await createAccount('p');
  ok(`실제 JWT: sub가 회원 ID 문자열(${jwtSub(probe.accessToken)} = ${probe.userId})`, jwtSub(probe.accessToken) === String(probe.userId));
}

// 1) 요청 방어
{
  const accountA = await createAccount('a');
  const accountB = await createAccount('b');
  const { context, tab1, errors, favoriteWrites } = await openScenario(accountA, { blockStorageEvents: true });
  ok('방어: 탭 1이 계정 A로 로그인 상태', await showsAccount(tab1, accountA.nickname));
  const heart = tab1.locator('button[aria-pressed]:visible').first();
  ok('방어: 관심 단지 하트가 보임', await heart.waitFor({ timeout: 8000 }).then(() => true, () => false));

  await loginInNewTab(context, accountB);
  const stored = await tab1.evaluate((k) => localStorage.getItem(k), ACCESS_KEY);
  ok('방어: 공유 저장소가 계정 B의 토큰으로 바뀜', stored !== null && jwtSub(stored) === String(accountB.userId));
  ok('방어: storage 이벤트를 막아 탭 1은 아직 계정 A를 보여 줌', await showsAccount(tab1, accountA.nickname, 1000));

  await heart.click();
  ok('방어: 계정이 바뀌었다는 안내 토스트', await tab1.getByRole('status').filter({ hasText: '로그인 계정이 바뀌어' }).waitFor({ timeout: 3000 }).then(() => true, () => false));
  ok('방어: 탭 1은 다시 확인을 거쳐 계정 B를 보여 줌', await showsAccount(tab1, accountB.nickname));
  await tab1.waitForTimeout(800);
  ok('방어: 탭 1의 관심 단지 요청이 서버에 가지 않음', favoriteWrites.length === 0, `${favoriteWrites.length}건`);
  ok('방어: 서버에서 계정 B의 관심 매물이 비어 있음', (await favoritesOf(accountB.accessToken)).length === 0);
  ok('방어: 서버에서 계정 A의 관심 매물도 비어 있음', (await favoritesOf(accountA.accessToken)).length === 0);
  ok('방어: pageerror 없음', errors.length === 0, errors.join(' | '));
  await context.close();
}

// 2) 화면 동기화 — storage 이벤트로 확인 중을 거쳐 B로 바뀐다. 확인 중 구간을 보려고 탭 1이 B 토큰으로 보낸
//    GET /api/users/me 응답만 붙잡아 둔다(실제 서버 응답을 받아 두었다가 풀어 준다).
{
  const accountA = await createAccount('c');
  const accountB = await createAccount('d');
  const { context, tab1, errors, favoriteWrites } = await openScenario(accountA);
  ok('동기화: 탭 1이 계정 A로 로그인 상태', await showsAccount(tab1, accountA.nickname));
  let releaseMe;
  const meGate = new Promise((r) => { releaseMe = r; });
  await tab1.route('**/api/users/me', async (route) => {
    const auth = (route.request().headers()['authorization'] ?? '').replace('Bearer ', '');
    const response = await route.fetch();
    if (auth && jwtSub(auth) === String(accountB.userId)) await meGate;
    await route.fulfill({ response });
  });

  await loginInNewTab(context, accountB);
  const placeholder = header(tab1).getByTestId('account-placeholder');
  ok('동기화: 탭 1이 확인 중(자리 표시)으로 바뀜', await placeholder.waitFor({ timeout: 3000 }).then(() => true, () => false));
  ok('동기화: 확인 중에는 계정 A를 보여 주지 않음', (await accountMenu(tab1).count()) === 0);
  releaseMe();
  ok('동기화: 탭 1이 계정 B로 확정', await showsAccount(tab1, accountB.nickname));
  ok('동기화: 탭 1이 보낸 관심 단지 요청 없음', favoriteWrites.length === 0);
  ok('동기화: pageerror 없음', errors.length === 0, errors.join(' | '));
  await context.close();
}

// 3) 다른 탭의 로그아웃 — 탭 1이 비로그인으로 바뀐다.
{
  const accountA = await createAccount('e');
  const { context, tab1, errors } = await openScenario(accountA);
  ok('로그아웃: 탭 1이 계정 A로 로그인 상태', await showsAccount(tab1, accountA.nickname));
  const tab2 = await context.newPage();
  await tab2.goto(`${BASE}/`);
  await accountMenu(tab2).click();
  await tab2.getByRole('menuitem', { name: '로그아웃' }).click();
  await header(tab2).getByRole('link', { name: '로그인' }).waitFor({ timeout: 8000 });
  ok('로그아웃: 탭 1이 비로그인(로그인 링크)으로 바뀜', await header(tab1).getByRole('link', { name: '로그인' }).waitFor({ timeout: 3000 }).then(() => true, () => false));
  ok('로그아웃: 탭 1에 계정 메뉴 없음', (await accountMenu(tab1).count()) === 0);
  ok('로그아웃: pageerror 없음', errors.length === 0, errors.join(' | '));
  await context.close();
}

await browser.close();
console.log(`\n${pass}/${pass + fail} passed`);
process.exit(fail > 0 ? 1 : 0);
