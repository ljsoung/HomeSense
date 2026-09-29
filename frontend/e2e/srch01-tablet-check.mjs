import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 768, height: 1024 } });
const page = await context.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));

await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=%EA%B2%BD%EA%B8%B0%EB%8F%84%20%EC%88%98%EC%9B%90%EC%8B%9C%20%EC%9E%A5%EC%95%88%EA%B5%AC`);
await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });

ok('768px에서 사이드바 필터 패널이 보임(모바일 바텀시트 버튼 아님)', await page.locator('aside').isVisible());

// "필터 적용"(사이드바 안, 데스크톱/태블릿에서도 항상 보임)과 혼동하지 않도록 정확히 "필터"만
// 텍스트인 모바일 전용 pill 버튼만 골라 화면에 보이는지 확인한다.
const anyVisibleMobileFilterPill = await page.evaluate(() => {
  const btns = Array.from(document.querySelectorAll('button'));
  return btns.some((b) => b.textContent?.trim().startsWith('필터') && !b.textContent?.includes('적용') && b.offsetParent !== null);
});
ok('768px에서 모바일 전용 필터 pill 버튼은 화면에 보이지 않음(사이드바로 대체)', !anyVisibleMobileFilterPill);

ok('768px에서 데스크톱 정렬 바의 "지도로 보기" 링크가 보임', await page.locator('main a[href^="/map"]').last().isVisible());

// 필터 적용 왕복
await page.getByRole('radio', { name: '전세' }).check();
await page.getByRole('button', { name: '필터 적용' }).click();
await page.waitForTimeout(700);
ok('768px에서 필터 적용 시 URL에 dealType=전세 반영', decodeURIComponent(page.url()).includes('전세'));
ok('768px에서 필터 적용 후 pageerror 없음', errors.length === 0);

// 페이지네이션 동작
const hasPagination = (await page.locator('nav[aria-label="페이지네이션"]').count()) > 0;
if (hasPagination) {
  await page.getByRole('button', { name: '2' }).click();
  await page.waitForTimeout(600);
  ok('768px에서 페이지네이션 클릭 시 page=2 반영', page.url().includes('page=2'));
} else {
  console.log('SKIP 768px 페이지네이션(전세 결과가 1페이지 이하)');
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
