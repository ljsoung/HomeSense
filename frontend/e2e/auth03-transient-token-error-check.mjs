import { chromium } from 'playwright';

const BASE = process.env.BASE ?? 'http://localhost:5173';
const results = [];
function log(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} - ${name}${detail ? ' :: ' + detail : ''}`);
}
const envelope = (data) => ({ success: true, data, error: null, timestamp: new Date().toISOString() });
const errorEnvelope = (code, message) => ({ success: false, data: null, error: { code, message }, timestamp: new Date().toISOString() });

const browser = await chromium.launch();

// ---------- Test A: genuine 400 INVALID_RESET_TOKEN -> still shows the expired-link screen ----------
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (A)', false, e.message));
  await page.route('**/api/auth/password-reset/validate-token*', (route) =>
    route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify(errorEnvelope('INVALID_RESET_TOKEN', '유효하지 않거나 만료된 재설정 링크입니다. 다시 요청해주세요')) }),
  );
  await page.goto(BASE + '/password-reset?token=bad', { waitUntil: 'networkidle' });
  await page.waitForSelector('text=링크가 만료되었습니다', { timeout: 10000 });
  const body = await page.locator('body').innerText();
  log('A: genuine 400 still shows "링크가 만료되었습니다"', body.includes('링크가 만료되었습니다'));
  log('A: shows the server message verbatim', body.includes('유효하지 않거나 만료된 재설정 링크입니다'));
  log('A: offers "재설정 링크 다시 요청" (request a new link), not a bare retry', body.includes('재설정 링크 다시 요청'));
  await context.close();
}

// ---------- Test B: network failure (offline) during validate-token -> transient error state, NOT expired ----------
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (B)', false, e.message));
  await page.route('**/api/auth/password-reset/validate-token*', (route) => route.abort('failed'));
  await page.goto(BASE + '/password-reset?token=good', { waitUntil: 'networkidle' });
  await page.waitForSelector('text=링크를 확인할 수 없습니다', { timeout: 10000 });
  const body = await page.locator('body').innerText();
  log('B: network failure shows the transient-error screen, not "링크가 만료되었습니다"', body.includes('링크를 확인할 수 없습니다') && !body.includes('링크가 만료되었습니다'));
  log('B: shows the generic retryable message', body.includes('일시적인 오류가 발생했습니다'));
  log('B: offers "다시 시도" (retry), not "재설정 링크 다시 요청"', body.includes('다시 시도') && !body.includes('재설정 링크 다시 요청'));
  await context.close();
}

// ---------- Test C: 500 from validate-token -> transient error state; retry re-validates and can succeed ----------
// Note: React StrictMode (enabled in this app's main.tsx) double-invokes effects in dev, so the
// initial mount alone can trigger 2 requests — a naive "fail only the first call" mock would let
// the second StrictMode-driven call already succeed before the user ever clicks retry. Instead,
// keep failing until the test explicitly flips a flag right before clicking retry.
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (C)', false, e.message));
  let attempt = 0;
  let shouldSucceed = false;
  await page.route('**/api/auth/password-reset/validate-token*', (route) => {
    attempt += 1;
    if (!shouldSucceed) {
      route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify(errorEnvelope('INTERNAL_SERVER_ERROR', 'boom')) });
      return;
    }
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(null)) });
  });
  await page.goto(BASE + '/password-reset?token=good', { waitUntil: 'networkidle' });
  await page.waitForSelector('text=링크를 확인할 수 없습니다', { timeout: 10000 });
  log('C: 5xx shows the transient-error screen (survives StrictMode double-invoke)', true);
  const attemptsBeforeRetry = attempt;

  shouldSucceed = true;
  await page.locator('button:has-text("다시 시도")').click();
  await page.waitForSelector('text=새 비밀번호 설정', { timeout: 10000 });
  log('C: clicking 다시 시도 re-validates and reaches the password form on success', true);
  log('C: retry actually issued a new validate-token call', attempt > attemptsBeforeRetry, `before=${attemptsBeforeRetry} after=${attempt}`);
  await context.close();
}

await browser.close();

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length > 0 ? 1 : 0);
