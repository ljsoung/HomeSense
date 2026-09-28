// SCR-SRCH-01 모바일(392px) — 바텀시트, 무한스크롤, 뒤로가기 복원.
import { chromium } from 'playwright';

const BASE = process.env.BASE ?? 'http://localhost:5183';
let pass = 0;
let fail = 0;
function ok(name, cond) {
  if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); }
}

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 392, height: 800 } });
const page = await context.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));

// 경기도 전체(3,597+ 단지) — 페이지 여러 장 누적 검증에 충분한 데이터量
await page.goto(`${BASE}/search?regionCode=4100000000&regionLabel=%EA%B2%BD%EA%B8%B0%EB%8F%84`);
await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });

// 1) 필터 버튼 → 바텀시트 열림 → Esc 닫힘
await page.getByRole('button', { name: /필터/ }).click();
await page.waitForSelector('[role="dialog"]');
ok('필터 버튼 클릭 시 바텀시트 열림', await page.locator('[role="dialog"]').isVisible());
await page.keyboard.press('Escape');
await page.waitForTimeout(200);
ok('Esc로 바텀시트 닫힘', (await page.locator('[role="dialog"]').count()) === 0);

// 2) 백드롭 클릭 닫힘 + 스크롤 잠금
await page.getByRole('button', { name: /필터/ }).click();
await page.waitForSelector('[role="dialog"]');
const bodyOverflow = await page.evaluate(() => document.body.style.overflow);
ok('바텀시트 열려있는 동안 body 스크롤 잠김', bodyOverflow === 'hidden');
await page.mouse.click(10, 10); // 백드롭
await page.waitForTimeout(200);
ok('백드롭 클릭으로 바텀시트 닫힘', (await page.locator('[role="dialog"]').count()) === 0);
const bodyOverflowAfter = await page.evaluate(() => document.body.style.overflow);
ok('닫힌 후 스크롤 잠금 해제', bodyOverflowAfter !== 'hidden');

// 3) 무한스크롤로 여러 페이지 누적(30+ 아이템)
let count = await page.locator('a[href^="/complexes/"]').count();
let attempts = 0;
while (count < 30 && attempts < 15) {
  await page.mouse.wheel(0, 3000);
  await page.waitForTimeout(500);
  count = await page.locator('a[href^="/complexes/"]').count();
  attempts++;
}
ok('무한스크롤로 30개 이상 누적', count >= 30);
ok('무한스크롤 중 pageerror 없음', errors.length === 0);

const scrollYBeforeNav = await page.evaluate(() => window.scrollY);
ok('스크롤이 실제로 내려간 상태', scrollYBeforeNav > 500);

// 4) 카드 클릭 → DTL-01(placeholder) → 뒤로가기 → 누적 목록·스크롤 복원
// 주의: Playwright locator.click()은 대상이 화면 밖이면 자동으로 스크롤해서 보이게 만든다 —
// .first()/.last() 둘 다 현재 스크롤 위치와 무관한 카드라 클릭 직전 스크롤이 그 카드 위치로
// 바뀌어버려 "그 자리에서 클릭"을 재현하지 못하는 거짓 실패를 냈다. 현재 뷰포트 안에 실제로
// 보이는 카드를 브라우저 컨텍스트에서 직접 찾아 순수 DOM .click()으로 눌러 스크롤 이동 없이
// 내비게이션만 일으킨다.
await page.evaluate(() => {
  const anchors = Array.from(document.querySelectorAll('a[href^="/complexes/"]'));
  const viewportH = window.innerHeight;
  const visible = anchors.find((a) => {
    const r = a.getBoundingClientRect();
    return r.top >= 0 && r.bottom <= viewportH;
  });
  (visible ?? anchors[anchors.length - 1]).click();
});
await page.waitForURL(/\/complexes\//);
await page.goBack();
await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
await page.waitForTimeout(300);
const countAfterBack = await page.locator('a[href^="/complexes/"]').count();
ok('뒤로가기 후 누적 목록이 유지됨(30개 이상)', countAfterBack >= 30);
const scrollYAfterBack = await page.evaluate(() => window.scrollY);
console.log('DEBUG scrollYBeforeNav=', scrollYBeforeNav, 'scrollYAfterBack=', scrollYAfterBack);
ok('뒤로가기 후 스크롤 위치 복원(비슷한 위치)', Math.abs(scrollYAfterBack - scrollYBeforeNav) < 400);

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
