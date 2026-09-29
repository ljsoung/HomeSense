import { chromium } from 'playwright';

// 서버가 ageConfirmed 필드 오류만 담은 400을 돌려주는 경우(체크박스 게이트를 우회한 요청에서만 발생)에
// 알 수 없는 필드 키가 조용히 버려지지 않고 기존 폼 단위 에러 경로(formError)로 message가 그대로 보이는지,
// 그리고 알려진 필드 매핑(email 등)이 그대로 동작하는지 검증한다.
import { BASE } from './base.mjs';
const VIEWPORTS = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'mobile', width: 392, height: 852 },
];
const envelope = (data) => ({ success: true, data, error: null, timestamp: new Date().toISOString() });
const AGE_MSG = '만 14세 이상 확인이 필요합니다';
let pass = 0, fail = 0;
const browser = await chromium.launch();

for (const vp of VIEWPORTS) {
  const log = (name, ok, d = '') => { ok ? pass++ : fail++; console.log(`${ok ? 'PASS' : 'FAIL'} [${vp.name}] ${name}${d ? ' :: ' + d : ''}`); };
  const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
  const page = await context.newPage();
  page.on('pageerror', (e) => log('no pageerror', false, e.message));

  let mode = 'age-only';
  await page.route('**/api/auth/check-email*', (r) => r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope({ duplicate: false })) }));
  await page.route('**/api/auth/signup', (r) => {
    const MODES = {
      'age-only': [{ field: 'ageConfirmed', message: AGE_MSG }],
      mixed: [{ field: 'nickname', message: '닉네임 서버 에러' }, { field: 'ageConfirmed', message: AGE_MSG }],
      'mapped-only': [{ field: 'nickname', message: '닉네임 서버 에러' }],
      'two-unmapped-same-message': [{ field: 'ageConfirmed', message: AGE_MSG }, { field: 'someOtherKey', message: AGE_MSG }],
      'mapped-and-unmapped-same-message': [{ field: 'nickname', message: AGE_MSG }, { field: 'ageConfirmed', message: AGE_MSG }],
      'two-unmapped-diff-message': [{ field: 'ageConfirmed', message: AGE_MSG }, { field: 'someOtherKey', message: '다른 미매핑 에러' }],
    };
    const fieldErrors = MODES[mode];
    r.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ success: false, data: null, error: { code: 'VALIDATION_FAILED', message: '입력값이 유효하지 않습니다', fieldErrors }, timestamp: new Date().toISOString() }) });
  });

  await page.goto(BASE + '/signup', { waitUntil: 'networkidle' });
  const clickBox = (id) => page.locator('label[for="' + id + '"]').click({ position: { x: 10, y: 9 } });
  await page.fill('#email', 'ok@example.com');
  await page.click('button:has-text("중복확인")');
  await page.waitForSelector('text=사용 가능한 이메일입니다.');
  await page.fill('#password', 'abcd1234!');
  await page.fill('#passwordConfirm', 'abcd1234!');
  await page.fill('#nickname', '지성');
  await clickBox('confirmedAge14');
  await clickBox('agreeToTerms');

  // 1) ageConfirmed-only 400 -> message must be visible (not silently dropped)
  await page.locator('button[type=submit]').click();
  await page.waitForSelector(`text=${AGE_MSG}`, { timeout: 3000 }).catch(() => {});
  log('ageConfirmed-only 400 shows the server message via the form-level error', (await page.locator(`text=${AGE_MSG}`).count()) === 1);
  log('does NOT fall back to the generic error message', (await page.locator('text=일시적인 오류가 발생했습니다').count()) === 0);
  log('stays on /signup (no navigation)', page.url().endsWith('/signup'));

  // 2) known + unknown field together -> both surface
  mode = 'mixed';
  await page.locator('button[type=submit]').click();
  await page.waitForSelector('text=닉네임 서버 에러', { timeout: 3000 }).catch(() => {});
  log('known field error (nickname) still rendered inline', (await page.locator('text=닉네임 서버 에러').count()) === 1);
  log('unknown field error (ageConfirmed) also rendered', (await page.locator(`text=${AGE_MSG}`).count()) === 1);
  const submitAndWait = async (m, waitText) => {
    mode = m;
    await page.locator('button[type=submit]').click();
    await page.waitForSelector(`text=${waitText}`, { timeout: 3000 }).catch(() => {});
  };
// 요소 수가 아니라 화면에 실제로 보이는 문구의 등장 횟수를 센다 — 한 요소 안에 같은 문구가 두 번 들어간 경우를 놓치지 않는다.
  const count = async (t) => ((await page.locator('form').innerText()).split(t).length - 1);

  // 3) 매핑된 오류만 오는 기존 경로 — 인라인 1회, 폼 단위 에러(미매핑 message)는 나타나지 않는다(기존 동작 불변)
  await submitAndWait('mapped-only', '닉네임 서버 에러');
  log('mapped-only 400: nickname error inline exactly once (existing behaviour unchanged)', (await count('닉네임 서버 에러')) === 1);
  log('mapped-only 400: no form-level error appears (previous unmapped message cleared on resubmit)', (await count(AGE_MSG)) === 0);
  log('mapped-only 400: no generic fallback message', (await count('일시적인 오류가 발생했습니다')) === 0);

  // 4) 미매핑 2건이 같은 message — 중복 표시 여부(현 동작 기록)
  await submitAndWait('two-unmapped-same-message', AGE_MSG);
  const sameCount = await count(AGE_MSG);
  log('two unmapped errors with IDENTICAL message: shown once (no duplicate)', sameCount === 1, `rendered ${sameCount}x`);

  // 4b) 매핑된 오류(인라인)와 미매핑 오류(폼 단위)의 message가 같은 경우 — 화면에 한 번만
  await submitAndWait('mapped-and-unmapped-same-message', AGE_MSG);
  const mixedSame = await count(AGE_MSG);
  log('mapped + unmapped errors with the SAME message: shown once (inline only)', mixedSame === 1, `rendered ${mixedSame}x`);

  // 5) 미매핑 2건이 서로 다른 message — 둘 다 보이되 각 1회
  await submitAndWait('two-unmapped-diff-message', '다른 미매핑 에러');
  log('two unmapped errors with DIFFERENT messages: each shown once', (await count(AGE_MSG)) === 1 && (await count('다른 미매핑 에러')) === 1);
  await context.close();
}
await browser.close();
console.log(`\ntotal: ${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
