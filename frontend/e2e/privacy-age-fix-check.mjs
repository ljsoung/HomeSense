import { chromium } from 'playwright';

const BASE = process.env.BASE ?? 'http://localhost:5173';
const results = [];
function log(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} - ${name}${detail ? ' :: ' + detail : ''}`);
}

const browser = await chromium.launch();
const page = await (await browser.newContext({ viewport: { width: 1200, height: 900 } })).newPage();
await page.goto(BASE + '/privacy', { waitUntil: 'networkidle' });
await page.waitForTimeout(400);

const t = await page.locator('#rights').innerText();

// 5차 갱신(2026-09-19): 자기 확인 체크박스가 생겼으므로 "연령 확인 절차 없음" 문구는 사라져야 한다.
log('old "no age-check procedure" wording is gone', !t.includes('연령을 확인하는 별도 절차를 두고 있지 않아'));
log('old very-first false claim still absent', !t.includes('만 14세 미만 아동의 개인정보를 수집하지 않습니다'));
log('states 만 14세 이상 target audience', t.includes('만 14세 이상을 대상으로 서비스를 제공합니다'));
log('states the server also rejects signup without the tick', t.includes('만 14세 이상입니다') && t.includes('체크하지 않으면 서버에서도 가입을 거부합니다'));
log('old "screen-only / not sent or verified by server" wording is gone', !t.includes('서버로 전송되거나 서버에서 검증되지는 않습니다') && !t.includes('가입 버튼이 활성화되나'));
log('states the tick is not stored, used for request validation only', t.includes('체크 여부는 저장하지 않고 가입 요청 검증에만 사용'));
log('server enforcement is scoped to the tick, not actual age', t.includes('서버가 확인하는 것은 이 항목의 체크 여부일 뿐 실제 연령이 아니며'));
log('discloses reliance on self-attestation (limitation kept)', t.includes('자기 확인에 의존'));
log('states no separate verification (생년월일/본인인증)', t.includes('생년월일 확인이나 본인인증 등 별도의 연령 검증 수단도 두고 있지 않아'));
log('still admits it cannot fully block under-14 signup', t.includes('완전히 차단하지는 못합니다'));
log('cites 제22조의2 and admits no guardian-consent procedure', t.includes('제22조의2') && t.includes('이 동의 절차를 갖추고 있지 않습니다'));
log('reactive deletion commitment present', t.includes('가입 사실을 확인한 경우 지체 없이 해당 계정과 개인정보를'));
log('points to 10항 contact for manual deletion request', t.includes('10. 개인정보 보호책임자') && t.includes('삭제를 요청해 주시기 바랍니다'));
log('says auto-detect/auto-delete does NOT exist', t.includes('가입 사실을 자동으로 탐지하지 않으므로'));

log('deletion described as manual per 2항 (not automated)', t.includes('자동화되어 있지 않고 요청을 받아 수동으로 처리합니다'));
log('no unconfirmed forward promise to build more age checks', !t.includes('마련하는 대로') && !t.includes('마련하여 고지'));

// Forbidden claims (must NOT appear)
log('does NOT claim checkbox result is stored/verified on server', !/서버에 (저장|기록)|체크 이력|동의 이력/.test(t));
log('does NOT claim identity verification / 본인인증 procedure exists', !t.includes('본인인증을 통해') && !t.includes('본인 인증을 통해'));
log('does NOT claim guardian consent is checked', !t.includes('법정대리인의 동의를 확인'));

await browser.close();
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length > 0 ? 1 : 0);
