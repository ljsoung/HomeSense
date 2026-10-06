// 인증 상태 3종(checking/authenticated/anonymous) — 세션 확인 중(checking)에 화면이 로그인·비로그인 어느
// 쪽으로도 확정하지 않는지 검증한다. 세션 확인(GET /api/users/me) 응답을 붙잡아 확인 구간을 만든다.
// - 헤더(데스크톱 Gnb·모바일 헤더): 로그인 링크도 계정 메뉴도 없이 자리 표시만, 레이아웃 이동 없음
// - HOME-01 개인화: 관심 지역 요약·관심 매물 API를 부르지 않고, 비로그인 가입 유도 CTA도 띄우지 않음
// 응답을 풀면 계정 메뉴로 확정된다. 토큰이 없으면 처음부터 비로그인(자리 표시 없음).
// 보호 라우트(RequireAuth) 시나리오는 첫 보호 화면을 만들 때 추가한다(지금은 vitest로만 검증).
// 서버는 route로 흉내 낸다(백엔드 불필요).
import { chromium } from 'playwright';
import { BASE } from './base.mjs';
import { okBody } from './mockApi.mjs';

let pass = 0;
let fail = 0;
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); }
}
const PERSONALIZED = ['/api/regions/interest-summary', '/api/favorites'];

const browser = await chromium.launch();

async function scenario(label, width) {
  const context = await browser.newContext({ viewport: { width, height: 900 } });
  await context.addInitScript(() => {
    localStorage.setItem('homesense.accessToken', 'A');
    localStorage.setItem('homesense.refreshToken', 'R');
  });
  let release;
  const released = new Promise((r) => { release = r; });
  const personalizedDuringCheck = [];
  let checking = true;
  await context.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (checking && PERSONALIZED.some((p) => path.startsWith(p))) personalizedDuringCheck.push(path);
    if (path === '/api/users/me') {
      await released;
      return route.fulfill({ status: 200, contentType: 'application/json', body: okBody({ userId: 1, email: 'a@test.com', nickname: '확인중', createdAt: '' }) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: okBody([]) });
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto(`${BASE}/`);
  await page.waitForTimeout(1500);

  const header = page.locator('header:visible').first();
  const placeholder = header.getByTestId('account-placeholder');
  ok(`${label}: 확인 중 헤더에 자리 표시가 있음`, await placeholder.isVisible());
  ok(`${label}: 확인 중 헤더에 로그인 링크가 없음`, (await header.getByRole('link', { name: '로그인', exact: true }).count()) === 0);
  ok(`${label}: 확인 중 헤더에 계정 메뉴가 없음`, (await header.getByRole('button', { name: /계정 메뉴/ }).count()) === 0);
  const boxBefore = await header.boundingBox();
  ok(`${label}: 확인 중 개인화 API 호출 없음`, personalizedDuringCheck.length === 0, JSON.stringify(personalizedDuringCheck));
  ok(`${label}: 확인 중 비로그인 가입 유도 CTA 없음`, (await page.getByRole('link', { name: '무료 회원가입' }).count()) === 0);

  checking = false;
  release();
  const menu = header.getByRole('button', { name: /계정 메뉴/ });
  ok(`${label}: 확인이 끝나면 계정 메뉴로 확정`, await menu.waitFor({ timeout: 5000 }).then(() => true, () => false));
  ok(`${label}: 확정 후 자리 표시 사라짐`, (await placeholder.count()) === 0);
  const boxAfter = await header.boundingBox();
  ok(`${label}: 헤더 높이 변화 없음(레이아웃 이동 없음)`, boxBefore?.height === boxAfter?.height, `${boxBefore?.height} → ${boxAfter?.height}`);
  ok(`${label}: pageerror 없음`, errors.length === 0, errors.join(' | '));
  await context.close();
}

async function anonymousScenario(label, width) {
  const context = await browser.newContext({ viewport: { width, height: 900 } });
  await context.route('**/api/**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: okBody([]) }));
  const page = await context.newPage();
  await page.goto(`${BASE}/`);
  const header = page.locator('header:visible').first();
  ok(`${label}: 토큰이 없으면 곧바로 로그인 링크`, await header.getByRole('link', { name: '로그인', exact: true }).waitFor({ timeout: 3000 }).then(() => true, () => false));
  ok(`${label}: 토큰이 없으면 자리 표시 없음`, (await header.getByTestId('account-placeholder').count()) === 0);
  await context.close();
}

await scenario('데스크톱', 1280);
await scenario('모바일', 390);
await anonymousScenario('데스크톱 비로그인', 1280);
await anonymousScenario('모바일 비로그인', 390);
await browser.close();

console.log(`\n${pass}/${pass + fail} passed`);
process.exit(fail > 0 ? 1 : 0);
