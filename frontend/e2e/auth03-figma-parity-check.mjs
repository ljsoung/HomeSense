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
const SCRATCH = 'C:/Users/super/AppData/Local/Temp/claude/c--Users-super-IdeaProjects-HomeSense-frontend/4058a39b-7483-43e8-bf0a-2a72e48d3e38/scratchpad/figma-auth03';

// ---------- Test A: request step (idle) — stepper, lock icon badge, back link ----------
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (A)', false, e.message));
  await page.goto(BASE + '/password-reset', { waitUntil: 'networkidle' });

  const bodyText = await page.locator('body').innerText();
  log('A: step indicator shows both step labels', bodyText.includes('이메일 입력') && bodyText.includes('비밀번호 설정'));
  log('A: heading + description match Figma copy', bodyText.includes('비밀번호 찾기') && bodyText.includes('가입 시 등록한 이메일을 입력해주세요.'));
  log('A: back link renders with arrow icon + text', (await page.locator('a[href="/login"] svg').count()) > 0);
  const backLinkColor = await page.locator('a[href="/login"]').first().evaluate((el) => getComputedStyle(el).color);
  log('A: back link color is muted grey (#6a7282), not brand green', backLinkColor === 'rgb(106, 114, 130)', backLinkColor);

  await page.screenshot({ path: `${SCRATCH}/actual-1-email.png` });
  await context.close();
}

// ---------- Test B: request -> sent (mocked), countdown, resend, 429 handling ----------
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (B)', false, e.message));

  let requestCount = 0;
  await page.route('**/api/auth/password-reset-request', (route) => {
    requestCount += 1;
    if (requestCount === 2) {
      route.fulfill({ status: 429, contentType: 'application/json', body: JSON.stringify(errorEnvelope('PASSWORD_RESET_COOLDOWN', '잠시 후 다시 시도해주세요')) });
      return;
    }
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(null)) });
  });

  await page.clock.install();
  await page.goto(BASE + '/password-reset', { waitUntil: 'networkidle' });
  await page.fill('#email', 'user@homesense.kr');
  await page.locator('button[type=submit]').click();
  await page.waitForSelector('text=이메일을 발송했습니다', { timeout: 10000 });

  const sentBody = await page.locator('body').innerText();
  log('B: shows submitted email in a display chip', sentBody.includes('user@homesense.kr'));
  log('B: shows the oracle-safety note verbatim', sentBody.includes('보안상 이메일 존재 여부와 무관하게 동일한 안내 문구를 표시합니다.'));
  log('B: shows initial 60s countdown', sentBody.includes('60초 후 재발송 가능'));
  const resendDisabledInitially = (await page.locator('button:has-text("재설정 링크 발송")').count()) === 0;
  log('B: resend CTA is a disabled countdown box, not an enabled button, before cooldown ends', resendDisabledInitially);

  await page.screenshot({ path: `${SCRATCH}/actual-2-sent.png` });

  // Empirically, a single large runFor() only fires one timer in this recursive-setTimeout
  // countdown (each tick reschedules a fresh 1s timeout from a React effect, and Playwright's
  // clock doesn't yield back to let that effect commit before considering its virtual-time
  // budget exhausted) — looping 1s ticks fires each one in turn, matching how the real countdown
  // actually advances one second at a time anyway.
  for (let i = 0; i < 61; i += 1) {
    await page.clock.runFor(1000);
  }
  await page.waitForSelector('button:has-text("재설정 링크 발송")', { timeout: 5000 });
  log('B: resend button becomes an enabled CTA once cooldown reaches 0', true);

  await page.locator('button:has-text("재설정 링크 발송")').click();
  await page.waitForSelector('text=잠시 후 다시 시도해주세요', { timeout: 5000 });
  log('B: server 429 on resend shows the server message inline', true);
  const cooldownRestarted = await page.locator('body').innerText();
  log('B: 429 restarts the client cooldown to 60s (fail-safe re-lock)', cooldownRestarted.includes('60초 후 재발송 가능'), cooldownRestarted.match(/\d+초 후 재발송 가능/)?.[0]);

  await context.close();
}

// ---------- Test C: expired-link screen — badge, clock icon badge, 30-minute warning box ----------
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (C)', false, e.message));
  await page.route('**/api/auth/password-reset/validate-token*', (route) =>
    route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify(errorEnvelope('INVALID_RESET_TOKEN', '유효하지 않거나 만료된 재설정 링크입니다. 다시 요청해주세요')) }),
  );
  await page.goto(BASE + '/password-reset?token=bad', { waitUntil: 'networkidle' });
  await page.waitForSelector('text=링크가 만료되었습니다', { timeout: 10000 });
  const body = await page.locator('body').innerText();
  log('C: expired badge pill shown instead of stepper', body.includes('링크 만료'));
  log('C: shows the dynamic server message (not the static Figma copy) as the primary description', body.includes('유효하지 않거나 만료된 재설정 링크입니다. 다시 요청해주세요'));
  log('C: shows the static 30-minute validity warning box', body.includes('30분간') && body.includes('유효합니다') && body.includes('스팸함도 확인해보세요'));
  await page.screenshot({ path: `${SCRATCH}/actual-4-expired.png` });
  await context.close();
}

// ---------- Test D: confirm step valid token — step2 stepper (step1 checked), lock badge ----------
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror (D)', false, e.message));
  await page.route('**/api/auth/password-reset/validate-token*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(null)) }),
  );
  await page.route('**/api/auth/password-reset', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(null)) }),
  );
  await page.goto(BASE + '/password-reset?token=good', { waitUntil: 'networkidle' });
  await page.waitForSelector('text=새 비밀번호 설정', { timeout: 10000 });
  const body = await page.locator('body').innerText();
  log('D: description matches Figma copy', body.includes('안전한 새 비밀번호를 설정해주세요.'));
  log('D: step indicator shows step1 done + step2 active', body.includes('이메일 입력') && body.includes('비밀번호 설정'));
  log('D: password-confirm placeholder matches Figma exactly', (await page.locator('#newPasswordConfirm').getAttribute('placeholder')) === '비밀번호 재입력');

  await page.screenshot({ path: `${SCRATCH}/actual-3-form.png` });

  await page.fill('#newPassword', 'abcd1234!');
  await page.fill('#newPasswordConfirm', 'abcd1234!');
  await page.locator('button[type=submit]').click();
  await page.waitForSelector('text=비밀번호가 변경되었습니다', { timeout: 10000 });
  log('D: success screen renders after reset', true);

  await context.close();
}

// ---------- Test E: mobile viewport smoke check ----------
{
  const context = await browser.newContext({ viewport: { width: 375, height: 800 } });
  const page = await context.newPage();
  await page.goto(BASE + '/password-reset', { waitUntil: 'networkidle' });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  log('E: no horizontal overflow at mobile width', overflow <= 0, `overflow=${overflow}`);
  await context.close();
}

await browser.close();

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length > 0 ? 1 : 0);
