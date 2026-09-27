import { chromium } from 'playwright';

const outDir = (process.env.OUT_DIR ?? './out');
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });

// First email: check-email says available, but signup returns 409 (race with another user).
let checkedEmail = null;
await page.route('**/api/auth/check-email*', (route) => {
  const url = new URL(route.request().url());
  checkedEmail = url.searchParams.get('email');
  route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data: { duplicate: false }, error: null, timestamp: new Date().toISOString() }),
  });
});
let signupAttempts = 0;
await page.route('**/api/auth/signup', (route) => {
  signupAttempts += 1;
  route.fulfill({
    status: 409,
    contentType: 'application/json',
    body: JSON.stringify({ success: false, data: null, error: { code: 'DUPLICATE_EMAIL', message: '이미 사용 중인 이메일입니다' }, timestamp: new Date().toISOString() }),
  });
});

await page.goto('http://localhost:5173/signup', { waitUntil: 'networkidle' });
await page.fill('#email', 'taken@example.com');
await page.click('button:has-text("중복확인")');
await page.waitForSelector('text=사용 가능한 이메일입니다.');
await page.fill('#password', 'abcd1234!');
await page.fill('#passwordConfirm', 'abcd1234!');
await page.fill('#nickname', '지성');
await page.locator('label[for="confirmedAge14"]').click({ position: { x: 10, y: 9 } }); await page.locator('label[for="agreeToTerms"]').click({ position: { x: 10, y: 9 } });
await page.waitForFunction(() => !document.querySelector('button[type=submit]').disabled);
await page.click('button[type=submit]');
await page.waitForSelector('text=이미 사용 중인 이메일입니다', { timeout: 5000 });
console.log('Step 1: 409 duplicate error shown for taken@example.com (expected)');
const borderAfter409 = await page.locator('#email').evaluate((el) => getComputedStyle(el).borderColor);
console.log('  email border after 409 (expect red):', borderAfter409);

// Now change to a genuinely different, available email and re-check it.
await page.fill('#email', 'fresh@example.com');
await page.waitForTimeout(100);
const staleErrorGoneImmediately = await page.locator('text=이미 사용 중인 이메일입니다').count();
console.log('Step 2: stale 409 error gone immediately after editing (expect 0):', staleErrorGoneImmediately);

await page.click('button:has-text("중복확인")');
await page.waitForSelector('text=사용 가능한 이메일입니다.', { timeout: 5000 });
await page.waitForTimeout(300);
const finalBorder = await page.locator('#email').evaluate((el) => getComputedStyle(el).borderColor);
const staleErrorStillGone = await page.locator('text=이미 사용 중인 이메일입니다').count();
const submitDisabled = await page.locator('button[type=submit]').isDisabled();
console.log('Step 3: after checking fresh@example.com as available:');
console.log('  email border (expect green rgb(5, 223, 114)):', finalBorder);
console.log('  stale 409 message still absent (expect 0):', staleErrorStillGone);
console.log('  submit disabled (expect false):', submitDisabled);
await page.screenshot({ path: `${outDir}/server-email-error-cleared.png` });

await browser.close();
