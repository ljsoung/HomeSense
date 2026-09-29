import { chromium } from 'playwright';

// SCR-LEGAL-01 8항 — Refresh Token Rotation·재사용 탐지 반영 문구와 13항 v1.3 행을 검증한다.
// 재사용 탐지는 2026-09-29부터 Access Token도 즉시 무효화하므로(RefreshTokenReuseHandler) "즉시 해제"를 쓰고
// 옛 "최대 30분" 단서는 없어야 한다. 옛 문구("재발급 시 교체·폐기하지 않는다")가 다시 나타나지 않는지도 본다.
// v1.3 시행일이 아직 플레이스홀더('YYYY-MM-DD')면 PASS로 세지 않고 SKIP으로 알린다(머지 전 기입 필요).
// 백엔드 불필요.
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
  log(tag('재사용 탐지: 폐기된 토큰 재사용 → 탈취 가능성 → 모든 Refresh Token 폐기 + Access Token 무효화'), t.includes('이미 폐기된 Refresh Token이 다시 사용되면 토큰이 탈취되었을 가능성이 있다고 보고, 해당 계정의 모든 Refresh Token을 폐기하고 이미 발급된 Access Token도 무효화합니다'));
  log(tag('재사용 탐지 결과: 모든 기기에서 즉시 해제, 다시 로그인'), t.includes('모든 기기에서 즉시 로그인 상태가 해제되며, 다시 로그인해야 합니다'));
  log(tag('옛 "최대 30분"·"더 이상 연장되지 않" 단서 없음'), !t.includes('최대 30분') && !t.includes('더 이상 연장되지 않'));

  // --- 옛 문구·과장·내부 용어가 없다(페이지 전체) ---
  log(tag('옛 "교체하거나 폐기하지 않" 문구 없음'), !all.includes('교체하거나 폐기하지 않') && !all.includes('교체·폐기하지 않'));
  log(tag('옛 "계속 재사용될 수 있습니다" 문구 없음'), !all.includes('계속 재사용될'));
  log(tag('"완전히 막/차단/방지" 과장 없음'), !/완전히 (막|차단|방지)/.test(t));
  log(tag('내부 용어(revoked_yn/rotated_yn/HIGH) 노출 없음'), !/revoked_yn|rotated_yn|HIGH/.test(all));

  // --- 13항 v1.3 행 ---
  const rows = await page.locator('#amendments tbody tr').evaluateAll((trs) =>
    trs.map((tr) => [...tr.querySelectorAll('td')].map((td) => td.innerText.trim())),
  );
  const v13 = rows.find((r) => r[0] === 'v1.3');
  log(tag('v1.3 행이 마지막 행으로 존재'), rows.at(-1)?.[0] === 'v1.3', rows.map((r) => r[0]).join(','));
  log(tag('v1.3 비고: 2항·8항 변경 내용'), v13?.[2] === '2항·8항 관련 문구 수정: 탈퇴 계정 자동 파기 및 유예 기간, 토큰 재발급 시 교체와 재사용 탐지 반영', v13?.[2]);
  const amend = norm(await page.locator('#amendments').innerText());
  log(tag('방침 적용일 = v1.3 시행일'), !!v13 && amend.includes(`${v13[1]}부터 적용`), v13?.[1]);
  if (v13?.[1] === 'YYYY-MM-DD') {
    console.log(`SKIP - ${tag('v1.3 시행일이 아직 플레이스홀더(YYYY-MM-DD) — 머지 전 실제 날짜 기입 필요')}`);
  } else {
    log(tag('v1.3 시행일이 YYYY-MM-DD 형식의 실제 날짜'), /^\d{4}-\d{2}-\d{2}$/.test(v13?.[1] ?? ''), v13?.[1]);
  }

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  log(tag('가로 스크롤 없음'), overflow <= 0, `overflow=${overflow}`);
  log(tag('pageerror 없음'), errors.length === 0, errors.join(' | '));
  await page.close();
}
await browser.close();

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length > 0 ? 1 : 0);
