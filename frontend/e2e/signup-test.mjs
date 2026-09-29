import { chromium } from 'playwright';
import { BASE } from './base.mjs';

const outDir = (process.env.OUT_DIR ?? './out');
const browser = await chromium.launch();

async function shot(page, name) {
  await page.screenshot({ path: `${outDir}/${name}.png` });
}

// ---- Desktop base state ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.goto(`${BASE}/signup`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(300); await shot(page, '01-desktop-base');
  const submitDisabled = await page.locator('button[type=submit]').isDisabled();
  console.log('desktop base: submit disabled?', submitDisabled);
  await page.close();
}

// ---- Desktop validation-hint-ish state: fill everything valid except special char ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.route('**/api/auth/check-email*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { duplicate: false }, error: null, timestamp: new Date().toISOString() }) }),
  );
  await page.goto(`${BASE}/signup`, { waitUntil: 'networkidle' });
  await page.fill('#email', 'test@example.com');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=사용 가능한 이메일입니다.');
  await page.fill('#password', 'abcd1234'); // letter+digit+8+, no special -> matches Figma demo
  await page.fill('#passwordConfirm', 'abcd1234');
  await page.waitForSelector('text=비밀번호가 일치합니다.');
  await page.fill('#nickname', '지성');
  await page.locator('label[for="confirmedAge14"]').click({ position: { x: 10, y: 9 } }); await page.locator('label[for="agreeToTerms"]').click({ position: { x: 10, y: 9 } });
  await page.waitForTimeout(300); await shot(page, '02-desktop-hint-partial-password');
  const submitDisabled = await page.locator('button[type=submit]').isDisabled();
  console.log('desktop hint (special char unmet): submit disabled? (should be true)', submitDisabled);

  // now complete the password fully -> should enable
  await page.fill('#password', 'abcd1234!');
  await page.fill('#passwordConfirm', 'abcd1234!');
  await page.waitForTimeout(200);
  await page.waitForTimeout(300); await shot(page, '03-desktop-hint-all-valid');
  const submitDisabled2 = await page.locator('button[type=submit]').isDisabled();
  console.log('desktop hint (all valid): submit disabled? (should be false)', submitDisabled2);
  await page.close();
}

// ---- Mobile base + hint ----
{
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await page.goto(`${BASE}/signup`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(300); await shot(page, '04-mobile-base');
  await page.close();
}
{
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await page.route('**/api/auth/check-email*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { duplicate: false }, error: null, timestamp: new Date().toISOString() }) }),
  );
  await page.goto(`${BASE}/signup`, { waitUntil: 'networkidle' });
  await page.fill('#email', 'test@example.com');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=사용 가능한 이메일입니다.');
  await page.fill('#password', 'abcd1234!');
  await page.fill('#passwordConfirm', 'abcd1234!');
  await page.fill('#nickname', '지성');
  await page.locator('label[for="confirmedAge14"]').click({ position: { x: 10, y: 9 } }); await page.locator('label[for="agreeToTerms"]').click({ position: { x: 10, y: 9 } });
  await page.waitForTimeout(200);
  await page.waitForTimeout(300); await shot(page, '05-mobile-hint-all-valid');
  await page.close();
}

// ---- Tablet base + hint ----
{
  const page = await browser.newPage({ viewport: { width: 820, height: 1180 } });
  await page.goto(`${BASE}/signup`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(300); await shot(page, '06-tablet-base');
  await page.close();
}
{
  const page = await browser.newPage({ viewport: { width: 820, height: 1180 } });
  await page.route('**/api/auth/check-email*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { duplicate: false }, error: null, timestamp: new Date().toISOString() }) }),
  );
  await page.goto(`${BASE}/signup`, { waitUntil: 'networkidle' });
  await page.fill('#email', 'test@example.com');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=사용 가능한 이메일입니다.');
  await page.fill('#password', 'abcd1234!');
  await page.fill('#passwordConfirm', 'abcd1234!');
  await page.fill('#nickname', '지성');
  await page.locator('label[for="confirmedAge14"]').click({ position: { x: 10, y: 9 } }); await page.locator('label[for="agreeToTerms"]').click({ position: { x: 10, y: 9 } });
  await page.waitForTimeout(200);
  await page.waitForTimeout(300); await shot(page, '07-tablet-hint-all-valid');
  await page.close();
}

await browser.close();
console.log('done');
