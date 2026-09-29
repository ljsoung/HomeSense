import { chromium } from 'playwright';

// Checkbox 소비처는 SignupPage의 2곳(#confirmedAge14, #agreeToTerms)뿐이다(grep 확인). 3개 뷰포트에서
// 키보드(Tab) 포커스 시 링 표시 / 마우스 클릭 시 링 미표시 / 레이아웃 불변을 검증한다.
import { BASE } from './base.mjs';
const VIEWPORTS = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'mobile', width: 392, height: 852 },
];
const BRAND = 'rgb(15, 92, 84)';
let pass = 0, fail = 0;
const per = {};

const browser = await chromium.launch();
for (const vp of VIEWPORTS) {
  per[vp.name] = { pass: 0, fail: 0 };
  const log = (n, ok, d = '') => {
    per[vp.name][ok ? 'pass' : 'fail']++; ok ? pass++ : fail++;
    console.log(`${ok ? 'PASS' : 'FAIL'} [${vp.name}] ${n}${d ? ' :: ' + d : ''}`);
  };
  const ctx = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
  const page = await ctx.newPage();
  await page.goto(BASE + '/signup', { waitUntil: 'networkidle' });

  const boxShadow = (id) => page.evaluate((id) => {
    const box = document.getElementById(id).nextElementSibling; // 시각 박스(aria-hidden span)
    return getComputedStyle(box).boxShadow;
  }, id);
  const rect = (id) => page.evaluate((id) => {
    const r = document.getElementById(id).nextElementSibling.getBoundingClientRect();
    return [r.x, r.y, r.width, r.height].map((n) => Math.round(n * 10) / 10).join(',');
  }, id);
  const hasRing = (s) => s !== 'none' && s.includes(BRAND);

  for (const id of ['confirmedAge14', 'agreeToTerms']) {
    log(`${id}: no ring initially`, !hasRing(await boxShadow(id)));
  }

  // 1) 키보드: 첫 체크박스까지 Tab -> 링 표시
  const before = await rect('confirmedAge14');
  await page.locator('#nickname').focus();
  await page.keyboard.press('Tab');
  log('Tab reaches confirmedAge14', await page.evaluate(() => document.activeElement?.id === 'confirmedAge14'));
  const s1 = await boxShadow('confirmedAge14');
  log('keyboard focus shows brand ring on confirmedAge14', hasRing(s1), s1);
  log('ring does not shift layout (box rect unchanged)', (await rect('confirmedAge14')) === before);
  log('terms box has no ring while age is focused', !hasRing(await boxShadow('agreeToTerms')));

  // 2) Space 토글해도 링 유지(키보드 조작 중)
  await page.keyboard.press('Space');
  log('ring persists while toggling with Space', hasRing(await boxShadow('confirmedAge14')));
  await page.keyboard.press('Space'); // 원상복구

  // 3) Tab -> terms: 링 이동
  await page.keyboard.press('Tab');
  log('Tab moves focus to agreeToTerms', await page.evaluate(() => document.activeElement?.id === 'agreeToTerms'));
  log('ring moves to terms box', hasRing(await boxShadow('agreeToTerms')));
  log('age box ring removed after focus leaves', !hasRing(await boxShadow('confirmedAge14')));

  // 4) 마우스: 다른 곳으로 포커스를 뺐다가 박스/라벨 텍스트 클릭 -> 링 없음
  await page.locator('#nickname').focus();
  await page.locator('label[for="confirmedAge14"]').click({ position: { x: 10, y: 9 } });
  log('mouse click on box: focus lands on input', await page.evaluate(() => document.activeElement?.id === 'confirmedAge14'));
  log('mouse click on box does NOT show ring', !hasRing(await boxShadow('confirmedAge14')));
  await page.locator('#nickname').focus();
  await page.locator('label[for="confirmedAge14"] >> text=만 14세 이상입니다').click();
  log('mouse click on label text does NOT show ring', !hasRing(await boxShadow('confirmedAge14')));
  await page.locator('#nickname').focus();
  await page.locator('label[for="agreeToTerms"]').click({ position: { x: 10, y: 9 } });
  log('mouse click on terms box does NOT show ring', !hasRing(await boxShadow('agreeToTerms')));

  // 5) 체크 상태(브랜드 배경)에서도 링이 구분되도록 오프셋이 있는지: 체크된 채 Tab 포커스
  await page.locator('#nickname').focus();
  await page.keyboard.press('Tab');
  if (!(await page.locator('#confirmedAge14').isChecked())) await page.keyboard.press('Space');
  const checkedNow = await page.locator('#confirmedAge14').isChecked();
  log('checked+keyboard-focused box still shows ring', checkedNow && hasRing(await boxShadow('confirmedAge14')), `checked=${checkedNow}`);

  // 6) 가로 스크롤 없음 / 링이 카드에서 잘리지 않음(박스 좌측에서 링 바깥까지 화면 안)
  const ov = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  log('no horizontal scroll', ov <= 0);
  const r = await page.locator('#confirmedAge14').evaluate((el) => el.nextElementSibling.getBoundingClientRect().left);
  log('ring (4px out) stays inside viewport', r - 4 >= 0, `left=${r}`);

  await page.locator('#nickname').focus();
  await page.keyboard.press('Tab');
  await page.screenshot({ path: `${process.env.OUT_DIR ?? "./out"}/checkbox-focus-${vp.name}.png`, clip: { x: 0, y: Math.max(0, (await page.locator('label[for="confirmedAge14"]').boundingBox()).y - 30), width: vp.width, height: 130 } });
  await ctx.close();
}
await browser.close();
console.log('\n=== per-viewport ===');
for (const [n, r] of Object.entries(per)) console.log(`${n}: ${r.pass} passed, ${r.fail} failed`);
console.log(`total: ${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
