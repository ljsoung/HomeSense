import { chromium } from 'playwright';
import { BASE } from './base.mjs';

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
await page.route('**/api/auth/check-email*', (route) =>
  route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { duplicate: false }, error: null, timestamp: new Date().toISOString() }) }),
);

// Helper to fill everything except nickname to a valid state, then test nickname scenarios.
async function setupValidBase() {
  await page.goto(`${BASE}/signup`, { waitUntil: 'networkidle' });
  await page.fill('#email', 'ok@example.com');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=사용 가능한 이메일입니다.');
  await page.fill('#password', 'abcd1234!');
  await page.fill('#passwordConfirm', 'abcd1234!');
  await page.locator('label[for="confirmedAge14"]').click({ position: { x: 10, y: 9 } }); await page.locator('label[for="agreeToTerms"]').click({ position: { x: 10, y: 9 } });
}

// Scenario 1: " a" -> raw length 2 (previously valid), trimmed "a" length 1 (should now be INVALID)
{
  await setupValidBase();
  await page.fill('#nickname', ' a');
  await page.waitForTimeout(200);
  const nicknameBorder = await page.locator('#nickname').evaluate((el) => getComputedStyle(el).borderColor);
  const submitDisabled = await page.locator('button[type=submit]').isDisabled();
  console.log('Scenario 1 (" a", trims to 1 char):');
  console.log('  nickname border (expect red rgb(255, 100, 103)):', nicknameBorder);
  console.log('  submit disabled (expect true):', submitDisabled);
}

// Scenario 2: 12-char valid nickname + trailing space -> raw length 13 (previously invalid), trimmed 12 (should now be VALID)
{
  await setupValidBase();
  await page.fill('#nickname', 'abcdefghijkl '); // 12 chars + trailing space = 13 raw
  await page.waitForTimeout(200);
  const nicknameBorder = await page.locator('#nickname').evaluate((el) => getComputedStyle(el).borderColor);
  const submitDisabled = await page.locator('button[type=submit]').isDisabled();
  console.log('Scenario 2 ("abcdefghijkl ", trims to 12 chars):');
  console.log('  nickname border (expect green rgb(5, 223, 114)):', nicknameBorder);
  console.log('  submit disabled (expect false):', submitDisabled);
}

// Scenario 3: verify the actual submitted payload is trimmed
{
  await setupValidBase();
  let capturedBody = null;
  await page.route('**/api/auth/signup', async (route) => {
    capturedBody = route.request().postDataJSON();
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: { accessToken: 'a', refreshToken: 'b', expiresIn: 1800, userId: 1, email: 'ok@example.com', nickname: 'abcdefghijkl' }, error: null, timestamp: new Date().toISOString() }),
    });
  });
  await page.fill('#nickname', 'abcdefghijkl ');
  await page.waitForFunction(() => !document.querySelector('button[type=submit]').disabled);
  await page.click('button[type=submit]');
  await page.waitForURL(`${BASE}/`);
  console.log('Scenario 3: submitted nickname payload:', JSON.stringify(capturedBody?.nickname), '(expect "abcdefghijkl", no trailing space)');
}

await browser.close();
