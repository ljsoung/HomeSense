// HOME-01 최상단(GNB + 히어로)과 새로고침 시 세션 복원을 검증한다.
// - 비로그인 GNB: 로그인/회원가입 버튼, 홈에서 "지역·단지 검색" 활성(Figma 3:2/4:1232)
// - 히어로: 배경 사진, 입력창 안 "검색" 버튼(Figma 4:1302)으로 검색 실행
// - 무효 토큰만 남은 경우: 로그인된 것처럼 보이지 않고 토큰이 지워짐(사용자 리포트 재현)
// - Access Token 무효 + Refresh Token 유효: refresh가 정확히 1회(StrictMode 이중 effect에도)만 나가고 로그인 유지
// 실 백엔드(8080)+Redis 필요 — 테스트 계정을 가입 API로 만든다. BASE로 dev 서버 지정.
import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const ACCESS_KEY = 'homesense.accessToken';
const REFRESH_KEY = 'homesense.refreshToken';

// 매 실행마다 새 계정 — 닉네임은 2~12자.
const suffix = Date.now().toString(36).slice(-6);
const account = { email: `hdr-${suffix}@test.com`, password: 'Passw0rd!', nickname: `헤더${suffix}`.slice(0, 12) };
const signupRes = await fetch(`${BASE}/api/auth/signup`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ ...account, ageConfirmed: true }),
});
const signup = (await signupRes.json()).data;
if (!signup?.refreshToken) {
  console.log('FAIL 테스트 계정 가입 실패', signupRes.status);
  process.exit(1);
}

const browser = await chromium.launch();

async function openHome({ tokens } = {}) {
  const context = await browser.newContext({ viewport: { width: 1551, height: 900 } });
  if (tokens) {
    await context.addInitScript(([a, r, keys]) => {
      if (!sessionStorage.getItem('__seeded')) {
        localStorage.setItem(keys[0], a);
        localStorage.setItem(keys[1], r);
        sessionStorage.setItem('__seeded', '1');
      }
    }, [tokens.access, tokens.refresh, [ACCESS_KEY, REFRESH_KEY]]);
  }
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const refreshCalls = [];
  page.on('request', (r) => { if (r.url().includes('/api/auth/refresh')) refreshCalls.push(r); });
  await page.goto(`${BASE}/`);
  await page.waitForLoadState('networkidle');
  return { context, page, errors, refreshCalls };
}

const header = (page) => page.locator('header').first();

// 1) 비로그인 — GNB와 히어로
{
  const { context, page, errors } = await openHome();
  ok('비로그인: 로그인 버튼 표시', await header(page).getByRole('link', { name: '로그인' }).isVisible());
  ok('비로그인: 회원가입 버튼 표시', await header(page).getByRole('link', { name: '회원가입' }).isVisible());
  const searchTab = header(page).getByRole('link', { name: '지역·단지 검색' });
  ok('홈에서 "지역·단지 검색" 탭 활성(배경 #e8f2f0)', (await searchTab.evaluate((el) => getComputedStyle(el).backgroundColor)) === 'rgb(232, 242, 240)');
  const mapTab = header(page).getByRole('link', { name: '지도로 보기' });
  ok('다른 탭은 비활성', (await mapTab.evaluate((el) => getComputedStyle(el).backgroundColor)) === 'rgba(0, 0, 0, 0)');

  const heroImg = page.locator('section img').first();
  ok('히어로 배경 사진이 로드됨', await heroImg.evaluate((img) => img.complete && img.naturalWidth > 0));

  const input = page.locator('input[role="combobox"]').first();
  const submit = page.locator('section form[role="search"] button[type="submit"]');
  ok('히어로 "검색" 버튼이 하나', (await submit.count()) === 1);
  const [inputBox, buttonBox] = [await input.boundingBox(), await submit.boundingBox()];
  const wrapperBox = await submit.evaluate((b) => b.parentElement.getBoundingClientRect().toJSON());
  ok('"검색" 버튼이 입력창과 같은 줄(입력창 오른쪽)', buttonBox.x > inputBox.x + inputBox.width - 1 && Math.abs(buttonBox.y + buttonBox.height / 2 - (inputBox.y + inputBox.height / 2)) < 4);
  ok('"검색" 버튼이 입력 테두리 안에 있음', buttonBox.x + buttonBox.width <= wrapperBox.x + wrapperBox.width && buttonBox.y >= wrapperBox.y);
  ok('입력 테두리 2px', (await submit.evaluate((b) => getComputedStyle(b.parentElement).borderTopWidth)) === '2px');

  await input.fill('래미안');
  await page.waitForTimeout(400);
  await submit.click();
  await page.waitForURL(/\/search\?/, { timeout: 5000 });
  ok('"검색" 버튼 클릭 시 검색 결과로 이동', decodeURIComponent(page.url()).includes('래미안'));
  ok('pageerror 없음(비로그인)', errors.length === 0);
  await context.close();
}

// 2) 무효 토큰만 남은 경우 — 사용자 리포트("로그인 안 했는데 로그인된 것처럼 보임") 재현
{
  const { context, page, errors, refreshCalls } = await openHome({ tokens: { access: 'stale.access.token', refresh: 'stale.refresh.token' } });
  ok('무효 토큰: 로그인 버튼 표시(로그인된 것처럼 보이지 않음)', await header(page).getByRole('link', { name: '로그인' }).isVisible());
  ok('무효 토큰: 계정 메뉴(아바타) 없음', (await header(page).getByRole('button', { name: /계정 메뉴/ }).count()) === 0);
  ok('무효 토큰: 관심 지역 요약 대신 가입 유도 카드', await page.getByText('관심 지역을 등록하고').isVisible());
  const stored = await page.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  ok('무효 토큰: localStorage 토큰이 지워짐', stored.every((v) => v === null));
  ok('무효 토큰: refresh 시도는 1회', refreshCalls.length === 1);
  ok('pageerror 없음(무효 토큰)', errors.length === 0);
  await context.close();
}

// 3) Access Token 무효 + Refresh Token 유효 — 한 번만 재발급하고 로그인 유지
{
  const { context, page, errors, refreshCalls } = await openHome({ tokens: { access: 'expired.access.token', refresh: signup.refreshToken } });
  const avatar = header(page).getByRole('button', { name: /계정 메뉴/ });
  ok('재발급: 아바타 표시(로그인 유지)', await avatar.isVisible());
  ok('재발급: 닉네임 표시', (await avatar.textContent()).includes(account.nickname));
  ok('재발급: refresh 요청이 정확히 1회(StrictMode 이중 effect에도)', refreshCalls.length === 1);
  const stored = await page.evaluate((keys) => keys.map((k) => localStorage.getItem(k)), [ACCESS_KEY, REFRESH_KEY]);
  ok('재발급: 새 토큰으로 교체됨', stored[0] !== 'expired.access.token' && stored[1] !== signup.refreshToken && stored.every(Boolean));

  await page.reload();
  await page.waitForLoadState('networkidle');
  ok('새로고침 후에도 로그인 유지', await header(page).getByRole('button', { name: /계정 메뉴/ }).isVisible());
  ok('pageerror 없음(재발급)', errors.length === 0);
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed (total: ${pass + fail})`);
process.exit(fail ? 1 : 0);
