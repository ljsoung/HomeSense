import { chromium } from 'playwright';

// SCR-LEGAL-01 8항 — Refresh Token Rotation·재사용 탐지(2026-09-22 백엔드 도입) 반영 문구를 검증한다.
// 옛 문구("재발급 시 교체·폐기하지 않는다")가 다시 나타나지 않는지, 재사용 탐지 시 "즉시 모든 기기에서
// 로그아웃" 같은 과장(이미 발급된 Access Token은 만료까지 유효하다)이 없는지도 함께 본다. 백엔드 불필요.
const BASE = process.env.BASE ?? 'http://localhost:5173';
const results = [];
function log(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} - ${name}${detail ? ' :: ' + detail : ''}`);
}

const browser = await chromium.launch();
for (const vp of [
  { name: 'desktop', width: 1200, height: 900 },
  { name: 'mobile', width: 392, height: 852 },
]) {
  const tag = (n) => `[${vp.name}] ${n}`;
  const page = await (await browser.newContext({ viewport: { width: vp.width, height: vp.height } })).newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(BASE + '/privacy', { waitUntil: 'networkidle' });

  const sec = page.locator('#security');
  log(tag('8항 섹션 렌더링'), (await sec.count()) === 1 && (await sec.isVisible()));
  // innerText는 줄바꿈 위치가 뷰포트마다 달라 공백을 하나로 접어 비교한다.
  const norm = (s) => s.replace(/\s+/g, ' ');
  const t = norm(await sec.innerText());
  const all = norm(await page.locator('body').innerText());

  // --- 새 동작 ---
  log(tag('재발급마다 새 Refresh Token 발급 + 기존 토큰 즉시 폐기'), t.includes('재발급할 때마다 새 Refresh Token을 발급하고, 기존 Refresh Token은 즉시 폐기합니다'));
  log(tag('로그아웃은 해당 기기 토큰 폐기'), t.includes('로그아웃 시에는 해당 기기의 Refresh Token'));
  log(tag('비밀번호 재설정은 계정의 모든 토큰 폐기'), t.includes('비밀번호를 재설정하면 해당 계정의 모든 Refresh Token을 즉시 폐기합니다'));
  log(tag('재사용 탐지: 폐기된 토큰 재사용 → 탈취 가능성 → 모든 토큰 폐기'), t.includes('이미 폐기된 Refresh Token이 다시 사용되면 토큰이 탈취되었을 가능성이 있다고 보고, 해당 계정의 모든 Refresh Token을 폐기합니다'));
  log(tag('재사용 탐지 결과를 이용자 관점으로 설명(다시 로그인)'), t.includes('모든 기기에서 로그인 상태가 더 이상 연장되지 않으며') && t.includes('다시 로그인해야 합니다'));
  log(tag('Access Token 잔여 유효기간을 숨기지 않음'), t.includes('이미 발급된 Access Token의 유효기간(최대 30분)이 지나면'));

  // --- 옛 문구·과장·내부 용어가 없다(페이지 전체) ---
  log(tag('옛 "교체하거나 폐기하지 않" 문구 없음'), !all.includes('교체하거나 폐기하지 않') && !all.includes('교체·폐기하지 않'));
  log(tag('옛 "계속 재사용될 수 있습니다" 문구 없음'), !all.includes('계속 재사용될'));
  log(tag('"즉시 모든 기기에서 로그아웃" 과장 없음'), !/즉시 모든 기기에서 로그아웃/.test(all));
  log(tag('"완전히 막/차단/방지" 과장 없음'), !/완전히 (막|차단|방지)/.test(t));
  log(tag('내부 용어(revoked_yn/rotated_yn/HIGH) 노출 없음'), !/revoked_yn|rotated_yn|HIGH/.test(all));

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  log(tag('가로 스크롤 없음'), overflow <= 0, `overflow=${overflow}`);
  log(tag('pageerror 없음'), errors.length === 0, errors.join(' | '));
  await page.close();
}
await browser.close();

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length > 0 ? 1 : 0);
