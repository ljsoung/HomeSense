import { chromium } from 'playwright';

import { BASE } from './base.mjs';
const results = [];
function log(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} - ${name}${detail ? ' :: ' + detail : ''}`);
}
const envelope = (data) => ({ success: true, data, error: null, timestamp: new Date().toISOString() });
const errorEnvelope = (code, message) => ({ success: false, data: null, error: { code, message }, timestamp: new Date().toISOString() });

const browser = await chromium.launch();

// ---------- Test A: reported bug — 429 on the idle form no longer leaves the submit button
// clickable for the rest of the cooldown. Reproduces "page opened/reloaded while this email is
// already under cooldown": the form starts idle (not the "발송 완료" screen), submit hits 429
// immediately, and the button must lock for the same email. ----------
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (A)', false, e.message));

  let requestCount = 0;
  await page.route('**/api/auth/password-reset-request', (route) => {
    requestCount += 1;
    route.fulfill({ status: 429, contentType: 'application/json', body: JSON.stringify(errorEnvelope('PASSWORD_RESET_COOLDOWN', '잠시 후 다시 시도해주세요')) });
  });

  await page.clock.install();
  await page.goto(BASE + '/password-reset', { waitUntil: 'networkidle' });
  await page.fill('#email', 'cooldown@homesense.kr');
  await page.locator('button[type=submit]').click();
  await page.waitForSelector('text=잠시 후 다시 시도해주세요', { timeout: 10000 });
  log('A: still shows the server 429 message on the idle form', true);

  const submitVisible = await page.locator('button[type=submit]:has-text("재설정 링크 발송")').isVisible().catch(() => false);
  log('A: the submit button is replaced by a locked countdown (bug fix)', !submitVisible);
  const countdownVisible = await page.locator('text=/\\d+초 후 다시 시도 가능/').isVisible();
  log('A: shows a countdown affordance, not a silently-disabled button', countdownVisible);

  // Repeated clicks/Enter during the cooldown must not re-hit the endpoint.
  await page.locator('#email').press('Enter');
  await page.waitForTimeout(300);
  log('A: pressing Enter during cooldown does not resubmit (native single-field submit guard)', requestCount === 1, `requestCount=${requestCount}`);

  // Advance past the 60s client-side cooldown; the real button must come back for this email.
  for (let i = 0; i < 61; i += 1) {
    await page.clock.runFor(1000);
  }
  await page.waitForSelector('button[type=submit]:has-text("재설정 링크 발송")', { timeout: 5000 });
  log('A: submit button returns once the cooldown reaches 0', true);

  await context.close();
}

// ---------- Test B: switching to a different email must not stay locked by the old cooldown ----------
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (B)', false, e.message));

  let call = 0;
  await page.route('**/api/auth/password-reset-request', (route) => {
    call += 1;
    if (call === 1) {
      route.fulfill({ status: 429, contentType: 'application/json', body: JSON.stringify(errorEnvelope('PASSWORD_RESET_COOLDOWN', '잠시 후 다시 시도해주세요')) });
      return;
    }
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(null)) });
  });

  await page.goto(BASE + '/password-reset', { waitUntil: 'networkidle' });
  await page.fill('#email', 'first@homesense.kr');
  await page.locator('button[type=submit]').click();
  await page.waitForSelector('text=/\\d+초 후 다시 시도 가능/', { timeout: 10000 });
  log('B: first email locks after 429', true);

  await page.fill('#email', 'different@homesense.kr');
  await page.waitForSelector('button[type=submit]:has-text("재설정 링크 발송")', { timeout: 5000 });
  log('B: switching to a different (not-cooling-down) email re-enables the real submit button', true);

  await page.locator('button[type=submit]').click();
  await page.waitForSelector('text=이메일을 발송했습니다', { timeout: 10000 });
  log('B: the different email actually submits successfully (not silently blocked)', true);
  log('B: exactly 2 requestPasswordReset calls total (no extra/duplicate calls)', call === 2, `call=${call}`);

  await context.close();
}

await browser.close();

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length > 0 ? 1 : 0);
