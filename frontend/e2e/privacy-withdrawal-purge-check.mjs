import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';

// BAT-USR-01(탈퇴 계정 자동 파기) 반영 후 방침 2항(보유 기간)·6항 문구를 검증한다.
// 기존 privacy-retention-fix-check.mjs("7일 유예기간 문구가 없다")는 그 배치가 생기면서 전제가 뒤집혀 대체됐다.
const BASE = process.env.BASE ?? 'http://localhost:5173';
const REPO = process.env.REPO ?? 'C:/Users/super/IdeaProjects/HomeSense';
const results = [];
function log(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} - ${name}${detail ? ' :: ' + detail : ''}`);
}

const props = readFileSync(`${REPO}/backend/src/main/resources/application.properties`, 'utf8');
const backendDefault = props.match(/homesense\.withdrawal\.grace-days=\$\{WITHDRAWAL_GRACE_DAYS:(\d+)\}/)?.[1];

const browser = await chromium.launch();
for (const vp of [
  { name: 'desktop', width: 1200, height: 900 },
  { name: 'mobile', width: 392, height: 852 },
]) {
  const tag = (n) => `[${vp.name}] ${n}`;
  const page = await (await browser.newContext({ viewport: { width: vp.width, height: vp.height } })).newPage();
  page.on('pageerror', (e) => log(tag('no pageerror'), false, e.message));
  await page.goto(BASE + '/privacy', { waitUntil: 'networkidle' });
  await page.waitForTimeout(300);

  const t = await page.locator('#retention').innerText();
  const rights = await page.locator('#rights').innerText();

  // --- 새 동작(자동 파기)이 실제로 서술돼 있다 ---
  log(tag('timeline: 탈퇴 즉시 → 로그인 불가·토큰 즉시 폐기'), t.includes('탈퇴 즉시') && t.includes('더 이상 로그인할 수 없으며') && t.includes('인증 토큰(Refresh Token)은 즉시 폐기됩니다'));
  const within = t.match(/탈퇴 후 (\d+)일 이내/)?.[1];
  const after = t.match(/탈퇴 후 (\d+)일 경과/)?.[1];
  log(tag('timeline: 보관 기간(N일 이내)과 자동 파기(N일 경과) 두 행이 같은 N'), !!within && within === after, `${within}/${after}`);
  log(tag('policy N equals backend default grace-days (application.properties)'), !!backendDefault && within === backendDefault, `policy=${within} backend=${backendDefault}`);
  log(tag('states retention window: 삭제하지 않고 보관'), t.includes('로그인할 수 없는 상태로 개인정보를 보관하며, 이 기간에는 삭제하지 않습니다'));
  log(tag('states automatic daily purge after N days'), t.includes('매일 새벽 정기 처리에서 자동으로 파기됩니다'));
  log(tag('discloses purge can lag up to about a day'), t.includes('최대 하루 정도 늦을 수 있습니다'));
  for (const item of ['계정정보', '관심 매물', '관심 지역', '알림 설정·이력', '최근 조회 이력', '인증 토큰']) {
    log(tag(`purge scope lists ${item}`), t.includes(item));
  }
  log(tag('still says login does not restore the account'), t.includes('재로그인을 통해 복구되지 않으며'));
  log(tag('same-email re-signup only after purge (actual behaviour)'), t.includes('같은 이메일로 다시 가입하려면') && t.includes('파기된 뒤에 가능합니다'));
  log(tag('officer immediate-deletion path kept, described as manual'), t.includes('10. 개인정보 보호책임자') && t.includes('즉시 삭제를 원하시는 경우') && t.includes('요청을 받아 수동으로 처리합니다'));

  // --- 옛 문구(미자동화 전제)와 허위 안내가 없다 ---
  log(tag('old "완전 삭제 절차는 아직 자동화되어 있지 않습니다" is gone'), !t.includes('아직 자동화되어 있지 않습니다') && !t.includes('자동 파기 절차가 마련되는 대로'));
  log(tag('NO claim that users can withdraw-cancel themselves (no UI exists)'), !/직접 철회|철회할 수|탈퇴를 철회|철회 가능|탈퇴 철회/.test(t + rights));
  log(tag('no fabricated login-to-cancel claim'), !t.includes('재로그인을 통해 탈퇴를 철회'));
  log(tag('no fabricated separate-DB storage claim'), !t.includes('별도의 DB로 옮겨져'));

  // --- 6항: 2항을 참조하는 문장이 새 동작과 모순되지 않는다 ---
  log(tag('6항 under-14 deletion sentence no longer points at a non-existent "not automated" 2항'), !rights.includes('안내된 것과 같이 자동화되어'));
  log(tag('6항 under-14 deletion is still described as manual on request'), rights.includes('안내된 즉시 삭제 요청과 같이 자동화되어') && rights.includes('요청을 받아 수동으로 처리합니다'));

  // --- 레이아웃: 새 표가 모바일에서 넘치지 않는다 ---
  const table = page.locator('#retention table');
  log(tag('retention table renders'), (await table.count()) === 1 && (await table.isVisible()));
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  log(tag('no horizontal page scroll'), overflow <= 0, `overflow=${overflow}`);
  await page.screenshot({ path: `${process.env.OUT_DIR ?? './out'}/privacy-withdrawal-${vp.name}.png`, fullPage: false });
  await page.close();
}
await browser.close();

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length > 0 ? 1 : 0);
