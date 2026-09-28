import { chromium } from 'playwright';

const BASE = process.env.BASE ?? 'http://localhost:5173';
const results = [];
function log(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} - ${name}${detail ? ' :: ' + detail : ''}`);
}

const browser = await chromium.launch();

// ---------- Test A: real backend, step 1 (request) + cooldown ----------
// The "다른 이메일로 다시 시도" button from the first implementation no longer exists — the
// Figma-parity rewrite replaced it with a disabled 60s countdown box on the sent screen itself
// (see CLAUDE.md SCR-AUTH-03), so there is no in-UI way to resubmit within the cooldown window
// without actually waiting out the real 60s. Instead, trigger the 429 from a *second*, fresh
// page (no prior client state) submitting the same email — that hits the real backend cooldown
// immediately and surfaces the error on the plain request-form screen, which is what the
// PASSWORD_RESET_COOLDOWN path actually renders in that scenario.
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (A)', false, e.message));
  await page.goto(BASE + '/password-reset', { waitUntil: 'networkidle' });

  const email = `auth03-check-${Date.now()}@example.com`;
  const submit = page.locator('button[type=submit]');

  log('A: submit disabled with empty email', await submit.isDisabled());
  await page.fill('#email', 'not-an-email');
  log('A: submit disabled with invalid email format', await submit.isDisabled());
  await page.fill('#email', email);
  log('A: submit enabled with valid email format', await submit.isEnabled());

  await submit.click();
  await page.waitForSelector('text=이메일을 발송했습니다', { timeout: 10000 });
  const sentBody = await page.locator('body').innerText();
  log('A: shows "발송했습니다" confirmation after submit', sentBody.includes('발송했습니다'));
  log('A: shows the submitted email in a display chip', sentBody.includes(email));
  log('A: shows a disabled 60s countdown (not an immediately-clickable resend)', /\d+초 후 재발송 가능/.test(sentBody));

  const page2 = await context.newPage();
  await page2.goto(BASE + '/password-reset', { waitUntil: 'networkidle' });
  await page2.fill('#email', email);
  await page2.locator('button[type=submit]').click();
  await page2.waitForSelector('#request-error', { timeout: 10000 });
  const cooldownText = await page2.locator('#request-error').innerText();
  log('A: a second, independent request for the same email hits the real 60s cooldown (429)', cooldownText.includes('잠시 후 다시 시도해주세요'), cooldownText);
  const ariaInvalid = await page2.locator('#email').getAttribute('aria-invalid');
  log('A: email field aria-invalid=true on cooldown error', ariaInvalid === 'true');
  const alertRole = await page2.locator('#request-error').getAttribute('role');
  log('A: cooldown message has role=alert', alertRole === 'alert');
  await page2.close();

  await context.close();
}

// ---------- Test B: real backend, invalid token ----------
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (B)', false, e.message));
  await page.goto(BASE + '/password-reset?token=not-a-real-token', { waitUntil: 'networkidle' });
  await page.waitForSelector('text=링크가 만료되었습니다', { timeout: 10000 });
  const invalidBody = await page.locator('body').innerText();
  log('B: shows expired-link message from server', invalidBody.includes('유효하지 않거나 만료된 재설정 링크입니다'));

  // 2026-09-23 Figma 대조 재작업으로 버튼 문구가 "재설정 다시 요청"에서 "재설정 링크 다시 요청"으로
  // 바뀌었다(PasswordResetPage.tsx) — 이 스크립트는 그 시점에 Docker가 꺼져 있어 실제 백엔드로
  // 재실행되지 못한 채 옛 문구로 남아 있었다. 이번에 실 백엔드로 처음 재실행하며 발견해 맞췄다.
  await page.locator('button:has-text("재설정 링크 다시 요청")').click();
  await page.waitForURL((url) => url.pathname === '/password-reset' && !url.search, { timeout: 10000 });
  log('B: retry button navigates back to /password-reset without token', true);
  await page.waitForSelector('#email');
  log('B: request form renders after retry', await page.locator('#email').isVisible());

  await context.close();
}

// ---------- Test C: mocked validate-token + reset (full step-2 form + success) ----------
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (C)', false, e.message));

  const envelope = (data) => ({ success: true, data, error: null, timestamp: new Date().toISOString() });
  let resetCalledWith = null;

  await page.route('**/api/auth/password-reset/validate-token*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(null)) }),
  );
  await page.route('**/api/auth/password-reset', (route) => {
    resetCalledWith = route.request().postDataJSON();
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(null)) });
  });

  await page.goto(BASE + '/password-reset?token=mock-token-abc', { waitUntil: 'networkidle' });
  await page.waitForSelector('text=새 비밀번호 설정', { timeout: 10000 });
  log('C: valid token renders the new-password form', true);

  const submit = page.locator('button[type=submit]');
  await page.fill('#newPassword', 'weak');
  log('C: submit disabled for a policy-failing password', await submit.isDisabled());
  const checklistText = await page.locator('body').innerText();
  log('C: checklist text present (8자 이상/영문 포함/숫자 포함/특수문자 포함)', ['8자 이상', '영문 포함', '숫자 포함', '특수문자 포함'].every((s) => checklistText.includes(s)));

  await page.fill('#newPassword', 'abcd1234!');
  await page.fill('#newPasswordConfirm', 'mismatch1!');
  log('C: submit disabled when confirm does not match', await submit.isDisabled());
  const mismatchText = await page.locator('#password-confirm-hint').innerText();
  log('C: shows mismatch message', mismatchText.includes('비밀번호가 일치하지 않습니다'));

  await page.fill('#newPasswordConfirm', 'abcd1234!');
  log('C: submit enabled once confirm matches a valid password', await submit.isEnabled());

  await submit.click();
  await page.waitForSelector('text=비밀번호가 변경되었습니다', { timeout: 10000 });
  log('C: success screen shown after reset', true);
  log('C: POST /password-reset sent correct token', resetCalledWith?.token === 'mock-token-abc', JSON.stringify(resetCalledWith));
  log('C: POST /password-reset sent correct newPassword', resetCalledWith?.newPassword === 'abcd1234!');

  await page.locator('button:has-text("로그인하러 가기")').click();
  await page.waitForURL((url) => url.pathname === '/login', { timeout: 10000 });
  log('C: "로그인하러 가기" navigates to /login', true);

  await context.close();
}

// ---------- Test D: mobile viewport smoke check ----------
{
  const context = await browser.newContext({ viewport: { width: 375, height: 800 } });
  const page = await context.newPage();
  await page.goto(BASE + '/password-reset', { waitUntil: 'networkidle' });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  log('D: no horizontal overflow at mobile width', overflow <= 0, `overflow=${overflow}`);
  await context.close();
}

await browser.close();

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length > 0 ? 1 : 0);
