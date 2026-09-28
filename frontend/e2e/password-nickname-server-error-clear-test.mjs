import { chromium } from 'playwright';

const browser = await chromium.launch();

async function setupAndSubmitWith400(page, fieldErrors) {
  await page.route('**/api/auth/check-email*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { duplicate: false }, error: null, timestamp: new Date().toISOString() }) }),
  );
  await page.route('**/api/auth/signup', (route) =>
    route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: JSON.stringify({ success: false, data: null, error: { code: 'VALIDATION_FAILED', message: '입력값이 유효하지 않습니다', fieldErrors }, timestamp: new Date().toISOString() }),
    }),
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
}

// ---- Password server error clears on edit ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await setupAndSubmitWith400(page, [{ field: 'password', message: '서버 방어 규칙 위반' }]);
  await page.waitForSelector('text=서버 방어 규칙 위반', { timeout: 5000 });
  console.log('Password: server error shown after 400 (expected)');
  await page.fill('#password', 'newpass1234!');
  await page.waitForTimeout(100);
  const gone = await page.locator('text=서버 방어 규칙 위반').count();
  console.log('Password: server error cleared after editing password (expect 0):', gone);
  await page.close();
}

// ---- Nickname server error clears on edit ----
{
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await setupAndSubmitWith400(page, [{ field: 'nickname', message: '닉네임에 사용할 수 없는 문자가 포함되어 있습니다' }]);
  await page.waitForSelector('text=닉네임에 사용할 수 없는 문자가 포함되어 있습니다', { timeout: 5000 });
  console.log('Nickname: server error shown after 400 (expected)');
  await page.fill('#nickname', '새닉네임');
  await page.waitForTimeout(100);
  const gone = await page.locator('text=닉네임에 사용할 수 없는 문자가 포함되어 있습니다').count();
  console.log('Nickname: server error cleared after editing nickname (expect 0):', gone);
  await page.close();
}

await browser.close();
