import { chromium } from 'playwright';

const outDir = (process.env.OUT_DIR ?? './out');
const browser = await chromium.launch();

// ---- 1. Duplicate email via check-email endpoint ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.route('**/api/auth/check-email*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { duplicate: true }, error: null, timestamp: new Date().toISOString() }) }),
  );
  await page.goto('http://localhost:5173/signup', { waitUntil: 'networkidle' });
  await page.fill('#email', 'taken@example.com');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=이미 사용 중인 이메일입니다');
  const border = await page.locator('#email').evaluate((el) => getComputedStyle(el).borderColor);
  console.log('1. duplicate email border (expect red rgb(255,100,103)):', border);
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${outDir}/08-duplicate-email.png` });

  // 2. edit email -> hint should reset (blur triggers check again on new value, but before blur, hint gone)
  await page.fill('#email', 'taken2@example.com');
  const hintGoneCount = await page.locator('text=이미 사용 중인 이메일입니다').count();
  console.log('2. hint cleared immediately after edit (expect 0):', hintGoneCount);
  await page.close();
}

// ---- 3. Invalid email format on check-email click (no network call should fire) ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  let networkCalled = false;
  await page.route('**/api/auth/check-email*', (route) => {
    networkCalled = true;
    route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });
  await page.goto('http://localhost:5173/signup', { waitUntil: 'networkidle' });
  await page.fill('#email', 'not-an-email');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=올바른 이메일 형식이 아닙니다');
  console.log('3. invalid format shown, network called? (expect false):', networkCalled);
  await page.close();
}

// ---- 4. Password mismatch hint ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.goto('http://localhost:5173/signup', { waitUntil: 'networkidle' });
  await page.fill('#password', 'abcd1234!');
  await page.fill('#passwordConfirm', 'different1!');
  await page.waitForSelector('text=비밀번호가 일치하지 않습니다');
  console.log('4. password mismatch hint shown: OK');
  await page.close();
}

// ---- 5. Nickname invalid (too short) ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.goto('http://localhost:5173/signup', { waitUntil: 'networkidle' });
  await page.fill('#nickname', 'a');
  await page.waitForSelector('text=닉네임은 2자 이상 12자 이하여야 합니다');
  console.log('5. nickname too-short hint shown: OK');
  await page.close();
}

// ---- 6. Submit -> 409 duplicate mapped to email field + focus ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.route('**/api/auth/check-email*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { duplicate: false }, error: null, timestamp: new Date().toISOString() }) }),
  );
  await page.route('**/api/auth/signup', (route) =>
    route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ success: false, data: null, error: { code: 'DUPLICATE_EMAIL', message: '이미 사용 중인 이메일입니다' }, timestamp: new Date().toISOString() }) }),
  );
  await page.goto('http://localhost:5173/signup', { waitUntil: 'networkidle' });
  await page.fill('#email', 'race@example.com');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=사용 가능한 이메일입니다.');
  await page.fill('#password', 'abcd1234!');
  await page.fill('#passwordConfirm', 'abcd1234!');
  await page.fill('#nickname', '지성');
  await page.locator('label[for="confirmedAge14"]').click({ position: { x: 10, y: 9 } }); await page.locator('label[for="agreeToTerms"]').click({ position: { x: 10, y: 9 } });
  await page.waitForFunction(() => !document.querySelector('button[type=submit]').disabled);
  await page.click('button[type=submit]');
  await page.waitForSelector('#email-hint');
  const hintText = await page.locator('#email-hint').innerText();
  const focused = await page.evaluate(() => document.activeElement.id);
  console.log('6. after 409: hint text =', JSON.stringify(hintText), ' focused element =', focused);
  await page.close();
}

// ---- 7. Submit -> 400 field errors mapped ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.route('**/api/auth/check-email*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { duplicate: false }, error: null, timestamp: new Date().toISOString() }) }),
  );
  await page.route('**/api/auth/signup', (route) =>
    route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ success: false, data: null, error: { code: 'VALIDATION_FAILED', message: '입력값이 유효하지 않습니다', fieldErrors: [{ field: 'nickname', message: '닉네임에 사용할 수 없는 문자가 포함되어 있습니다' }] }, timestamp: new Date().toISOString() }) }),
  );
  await page.goto('http://localhost:5173/signup', { waitUntil: 'networkidle' });
  await page.fill('#email', 'ok@example.com');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=사용 가능한 이메일입니다.');
  await page.fill('#password', 'abcd1234!');
  await page.fill('#passwordConfirm', 'abcd1234!');
  await page.fill('#nickname', '지성');
  await page.locator('label[for="confirmedAge14"]').click({ position: { x: 10, y: 9 } }); await page.locator('label[for="agreeToTerms"]').click({ position: { x: 10, y: 9 } });
  await page.waitForFunction(() => !document.querySelector('button[type=submit]').disabled);
  await page.click('button[type=submit]');
  await page.waitForSelector('text=닉네임에 사용할 수 없는 문자가 포함되어 있습니다');
  console.log('7. 400 fieldErrors mapped to nickname: OK');
  await page.close();
}

// ---- 8. Successful signup -> token storage + redirect ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.route('**/api/auth/check-email*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { duplicate: false }, error: null, timestamp: new Date().toISOString() }) }),
  );
  await page.route('**/api/auth/signup', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { accessToken: 'at', refreshToken: 'rt', expiresIn: 1800, userId: 1, email: 'ok@example.com', nickname: '지성' }, error: null, timestamp: new Date().toISOString() }) }),
  );
  await page.goto('http://localhost:5173/signup', { waitUntil: 'networkidle' });
  await page.fill('#email', 'ok@example.com');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=사용 가능한 이메일입니다.');
  await page.fill('#password', 'abcd1234!');
  await page.fill('#passwordConfirm', 'abcd1234!');
  await page.fill('#nickname', '지성');
  await page.locator('label[for="confirmedAge14"]').click({ position: { x: 10, y: 9 } }); await page.locator('label[for="agreeToTerms"]').click({ position: { x: 10, y: 9 } });
  await page.waitForFunction(() => !document.querySelector('button[type=submit]').disabled);
  await page.click('button[type=submit]');
  await page.waitForURL('http://localhost:5173/');
  const stored = await page.evaluate(() => ({ access: localStorage.getItem('homesense.accessToken'), refresh: localStorage.getItem('homesense.refreshToken') }));
  console.log('8. redirected to:', page.url(), ' tokens:', stored);
  await page.close();
}

// ---- 9. Login link navigates to /login ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.goto('http://localhost:5173/signup', { waitUntil: 'networkidle' });
  await page.click('text=로그인');
  await page.waitForURL('http://localhost:5173/login');
  console.log('9. login link -> ', page.url());
  await page.close();
}

await browser.close();
console.log('done');
