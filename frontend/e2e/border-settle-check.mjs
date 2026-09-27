import { chromium } from 'playwright';
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
await page.route('**/api/auth/check-email*', (route) =>
  route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { duplicate: false }, error: null, timestamp: new Date().toISOString() }) }),
);
await page.route('**/api/auth/signup', (route) =>
  route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ success: false, data: null, error: { code: 'DUPLICATE_EMAIL', message: '이미 사용 중인 이메일입니다' }, timestamp: new Date().toISOString() }) }),
);
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
await page.waitForTimeout(400);
const border = await page.locator('#email').evaluate((el) => getComputedStyle(el).borderColor);
console.log('email border after 409 (settled, expect red rgb(255, 100, 103)):', border);
await browser.close();
