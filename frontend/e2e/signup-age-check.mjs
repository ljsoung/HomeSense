import { chromium } from 'playwright';

// 세 "프로젝트"는 실제 @playwright/test project가 아니라(저장소에 커밋된 스위트가 없다) 뷰포트 에뮬레이션이다.
// Figma 프레임 폭 기준: desktop 1440 / tablet 768 / mobile 392.
import { BASE } from './base.mjs';
const VIEWPORTS = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'mobile', width: 392, height: 852 },
];

const envelope = (data) => ({ success: true, data, error: null, timestamp: new Date().toISOString() });

let totalPass = 0;
let totalFail = 0;
const perProject = {};

const browser = await chromium.launch();

for (const vp of VIEWPORTS) {
  perProject[vp.name] = { pass: 0, fail: 0 };
  const log = (name, ok, detail = '') => {
    perProject[vp.name][ok ? 'pass' : 'fail']++;
    ok ? totalPass++ : totalFail++;
    console.log(`${ok ? 'PASS' : 'FAIL'} [${vp.name}] ${name}${detail ? ' :: ' + detail : ''}`);
  };

  const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror', false, e.message));

  let signupPosted = false;
  let signupBody = null;
  await page.route('**/api/auth/check-email*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope({ duplicate: false })) }),
  );
  await page.route('**/api/auth/signup', (route) => {
    signupPosted = true;
    signupBody = route.request().postDataJSON();
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope({ accessToken: 'at', refreshToken: 'rt', expiresIn: 1800, userId: 1, email: 'ok@example.com', nickname: '지성' })),
    });
  });
  await page.route('**/api/users/me', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope({ userId: 1, email: 'ok@example.com', nickname: '지성', createdAt: '2025-01-01T00:00:00' })) }),
  );

  await page.goto(BASE + '/signup', { waitUntil: 'networkidle' });

  // 라벨 정중앙은 약관 라벨 안의 '개인정보처리방침' 링크 위에 떨어질 수 있어(링크 클릭은 토글하지 않는다) 체크박스 박스 위치를 직접 클릭한다.
  const clickBox = (id) => page.locator('label[for="' + id + '"]').click({ position: { x: 10, y: 9 } });
  const submit = page.locator('button[type=submit]');
  const age = page.locator('#confirmedAge14');
  const terms = page.locator('#agreeToTerms');

  // --- presence, label, order (age row directly above terms row) ---
  log('age checkbox exists', (await age.count()) === 1);
  const ageLabelText = (await page.locator('label[for="confirmedAge14"]').innerText()).replace(/\s+/g, ' ').trim();
  log('label reads "만 14세 이상입니다 (필수)"', ageLabelText === '만 14세 이상입니다 (필수)', ageLabelText);
  const ageBox = await page.locator('label[for="confirmedAge14"]').boundingBox();
  const termsBox = await page.locator('label[for="agreeToTerms"]').boundingBox();
  log('age row is directly above the terms row', ageBox.y < termsBox.y, `ageY=${ageBox.y.toFixed(0)} termsY=${termsBox.y.toFixed(0)}`);
  const gap = termsBox.y - (ageBox.y + ageBox.height);
  log('rows do not overlap (gap >= 0)', gap >= 0, `gap=${gap.toFixed(1)}px`);
  log('starts unchecked', !(await age.isChecked()));

  // --- fill the other five conditions ---
  await page.fill('#email', 'ok@example.com');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=사용 가능한 이메일입니다.');
  await page.fill('#password', 'abcd1234!');
  await page.fill('#passwordConfirm', 'abcd1234!');
  await page.fill('#nickname', '지성');

  // 1) five of six satisfied (terms checked, age NOT) -> disabled
  await clickBox('agreeToTerms');
  log('age unchecked + all other conditions met -> submit disabled', await submit.isDisabled());

  // 2) age checked, terms unchecked -> disabled (each is independently required)
  await clickBox('agreeToTerms'); // uncheck terms
  await clickBox('confirmedAge14');
  log('age checked but terms unchecked -> submit disabled', await submit.isDisabled());

  // 3) both checked -> enabled
  await clickBox('agreeToTerms');
  log('age + terms checked with the rest valid -> submit enabled', await submit.isEnabled());

  // 4) uncheck age -> disabled again
  await clickBox('confirmedAge14');
  log('unchecking age re-disables submit', await submit.isDisabled());
  log('age box reflects unchecked state', !(await age.isChecked()));

  // 5) label click toggles (already used above, assert state explicitly)
  await clickBox('confirmedAge14');
  log('label click toggles ON', await age.isChecked());
  log('submit enabled again after re-check', await submit.isEnabled());

  // 6) label text click (not just the box) toggles
  await page.locator('label[for="confirmedAge14"] >> text=만 14세 이상입니다').click();
  log('clicking the label text toggles OFF', !(await age.isChecked()));
  await page.locator('label[for="confirmedAge14"] >> text=만 14세 이상입니다').click();
  log('clicking the label text toggles ON', await age.isChecked());

  // 7) keyboard: focus the input directly, Space toggles
  await age.focus();
  log('age input is focusable', await page.evaluate(() => document.activeElement?.id === 'confirmedAge14'));
  await page.keyboard.press('Space');
  log('Space toggles OFF', !(await age.isChecked()));
  await page.keyboard.press('Space');
  log('Space toggles ON', await age.isChecked());

  // 8) Tab order: age -> terms (sr-only inputs are still tab stops)
  await page.keyboard.press('Tab');
  log('Tab from age moves focus to the terms checkbox', await page.evaluate(() => document.activeElement?.id === 'agreeToTerms'));
  await page.keyboard.press('Space');
  log('Space on terms toggles it (existing behaviour unchanged)', !(await terms.isChecked()));
  await page.keyboard.press('Space');
  log('terms checked again', await terms.isChecked());

  // 9) layout: no horizontal overflow, submit button still reachable on screen
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  log('no horizontal scroll', overflow <= 0, `overflow=${overflow}`);
  const submitBox = await submit.boundingBox();
  log('submit button below both checkbox rows', submitBox.y > termsBox.y, `submitY=${submitBox.y.toFixed(0)}`);

  // 10) ageConfirmed is sent (server validates it); agreeToTerms is still client-only
  await submit.click();
  await page.waitForURL(BASE + '/');
  log('signup posted once', signupPosted === true);
  const keys = Object.keys(signupBody ?? {}).sort().join(',');
  log('request body has exactly ageConfirmed,email,nickname,password (no terms field)', keys === 'ageConfirmed,email,nickname,password', keys);
  log('ageConfirmed is boolean true (mirrors the checked box)', signupBody?.ageConfirmed === true, String(signupBody?.ageConfirmed));

  // 11) policy link still opens in new tab and does not toggle the age box (regression for stopPropagation)
  await page.goto(BASE + '/signup', { waitUntil: 'networkidle' });
  const popupPromise = context.waitForEvent('page');
  await page.locator('a:has-text("개인정보처리방침")').click();
  const popup = await popupPromise;
  await popup.waitForLoadState();
  log('policy link opens /privacy in a new tab', popup.url().endsWith('/privacy'));
  await popup.close();
  log('policy link click did not toggle the age checkbox', !(await page.locator('#confirmedAge14').isChecked()));
  log('policy link click did not toggle the terms checkbox', !(await page.locator('#agreeToTerms').isChecked()));

  await page.screenshot({ path: `${process.env.OUT_DIR ?? "./out"}/signup-age-${vp.name}.png`, fullPage: true });
  await context.close();
}

await browser.close();

console.log('\n=== per-project ===');
for (const [name, r] of Object.entries(perProject)) {
  console.log(`${name}: ${r.pass} passed, ${r.fail} failed`);
}
console.log(`total: ${totalPass} passed, ${totalFail} failed`);
process.exit(totalFail > 0 ? 1 : 0);
