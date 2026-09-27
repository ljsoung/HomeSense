import { chromium } from 'playwright';

const BASE = 'http://localhost:5173';
const results = [];
function log(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} - ${name}${detail ? ' :: ' + detail : ''}`);
}

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1200, height: 900 } });
const page = await context.newPage();
page.on('pageerror', (err) => log('no console pageerror', false, err.message));

await page.goto(BASE + '/signup', { waitUntil: 'networkidle' });
await page.waitForTimeout(400);

// Confirm the link is set up to open in a new tab (no unmount risk) before testing state preservation.
const policyLink = page.getByRole('link', { name: /개인정보처리방침/ });
const target = await policyLink.getAttribute('target');
log('개인정보처리방침 link has target="_blank"', target === '_blank', `target=${target}`);
const rel = await policyLink.getAttribute('rel');
log('rel="noopener noreferrer" present for security', rel === 'noopener noreferrer', `rel=${rel}`);

// Fill out the whole form as a real user would.
await page.locator('#email').fill('test@example.com');
await page.locator('#password').fill('Password123!');
await page.locator('#passwordConfirm').fill('Password123!');
await page.locator('#nickname').fill('테스트유저');
// Checkbox.tsx renders the native input as sr-only; check it via its id directly.
await page.locator('#agreeToTerms').check({ force: true });
await page.waitForTimeout(200);

const emailBefore = await page.locator('#email').inputValue();
const passwordBefore = await page.locator('#password').inputValue();
const nicknameBefore = await page.locator('#nickname').inputValue();
const checkedBefore = await page.locator('#agreeToTerms').isChecked();
log('form filled before opening policy', emailBefore === 'test@example.com' && passwordBefore.length > 0 && checkedBefore);

// Click the link and confirm a NEW tab/page opens rather than navigating the current one away.
const [newPage] = await Promise.all([context.waitForEvent('page'), policyLink.click()]);
// 새 탭은 about:blank로 시작해 곧바로 로드 완료로 보일 수 있다 — networkidle만 기다리면 아직 /privacy로 이동하기
// 전에 아래 url() 단언이 실행되는 경합이 생기므로, 목적지 URL에 도달할 때까지 먼저 기다린다.
await newPage.waitForURL(/\/privacy$/, { timeout: 15000 });
await newPage.waitForLoadState('networkidle');
log('clicking policy link opens a new tab', newPage.url().endsWith('/privacy'), newPage.url());
log('original tab URL unchanged (still on /signup)', page.url().endsWith('/signup'), page.url());

// Confirm the original tab's form state is fully intact — no unmount occurred.
const emailAfter = await page.locator('#email').inputValue();
const passwordAfter = await page.locator('#password').inputValue();
const passwordConfirmAfter = await page.locator('#passwordConfirm').inputValue();
const nicknameAfter = await page.locator('#nickname').inputValue();
const checkedAfter = await page.locator('#agreeToTerms').isChecked();

log('email preserved after opening policy in new tab', emailAfter === emailBefore, emailAfter);
log('password preserved after opening policy in new tab', passwordAfter === passwordBefore);
log('passwordConfirm preserved', passwordConfirmAfter === 'Password123!');
log('nickname preserved', nicknameAfter === nicknameBefore, nicknameAfter);
log('agreeToTerms checkbox still checked', checkedAfter === true);

await newPage.close();
await browser.close();
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length > 0 ? 1 : 0);
