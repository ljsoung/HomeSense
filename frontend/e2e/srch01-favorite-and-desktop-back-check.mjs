import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const browser = await chromium.launch();

// 1) 비로그인 하트 클릭 → /login으로 이동, 검색 쿼리가 state.from에 보존됨 확인(재로그인 흐름은
// useFavoriteToggle 자체가 이미 HOME-01에서 검증됐으므로 여기선 SRCH-01 리스트 카드에서도 같은
// 진입점이 정상 연결되는지만 확인한다).
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=test`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  await page.getByRole('button', { name: /관심 매물 등록/ }).first().click();
  await page.waitForURL(/\/login/);
  ok('비로그인 하트 클릭 시 /login으로 이동', page.url().includes('/login'));
  await context.close();
}

// 2) 데스크톱 — 페이지 2로 이동 후 카드 클릭 → 뒤로가기 → 같은 페이지(page=2) 그대로 유지
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/search?regionCode=4100000000&regionLabel=%EA%B2%BD%EA%B8%B0%EB%8F%84`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  // 2페이지 응답이 온 뒤에 목록을 찍는다. 고정 600ms 대기는 경기도 전체 검색(수백 ms)보다 짧아, 1페이지 목록을
  // "2페이지"로 찍어 뒤로가기 뒤 목록과 달라지는 간헐 실패가 있었다(2026-09-29 확인).
  const page2Response = page.waitForResponse((r) => r.url().includes('/api/complexes/search') && r.url().includes('page=1'));
  await page.getByRole('button', { name: '2' }).click();
  await page2Response;
  await page.waitForTimeout(300);
  ok('데스크톱 2페이지 이동', page.url().includes('page=2'));
  const namesPage2 = await page.locator('a[href^="/complexes/"] p').allInnerTexts();

  await page.locator('a[href^="/complexes/"]').first().click();
  await page.waitForURL(/\/complexes\//);
  await page.goBack();
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  await page.waitForTimeout(300);
  ok('뒤로가기 후 URL이 여전히 page=2', page.url().includes('page=2'));
  const namesAfterBack = await page.locator('a[href^="/complexes/"] p').allInnerTexts();
  ok('뒤로가기 후 같은 목록(2페이지) 재현', JSON.stringify(namesPage2) === JSON.stringify(namesAfterBack));
  await context.close();
}

// 3) HOME-01 회귀 — 히어로 검색/인기 검색어 칩이 여전히 /search로 정상 이동
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(`${BASE}/`);
  await page.waitForTimeout(1000);
  const searchInput = page.locator('input[role="combobox"]').first();
  await searchInput.fill('래미안');
  await page.keyboard.press('Enter');
  await page.waitForURL(/\/search\?/);
  ok('HOME-01 히어로 검색 실행 시 /search로 이동', page.url().includes('/search'));
  ok('HOME-01 히어로 검색 URL에 keyword 포함', decodeURIComponent(page.url()).includes('래미안'));
  ok('HOME-01 회귀 — pageerror 없음', errors.length === 0);
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
