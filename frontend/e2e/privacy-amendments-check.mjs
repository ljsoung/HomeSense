import { chromium } from 'playwright';
const BASE = process.env.BASE ?? 'http://localhost:5173';
const results = [];
function log(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} - ${name}${detail ? ' :: ' + detail : ''}`);
}
const browser = await chromium.launch();
const page = await (await browser.newContext()).newPage();
await page.goto(BASE + '/privacy', { waitUntil: 'networkidle' });
const section = await page.locator('#amendments').innerText();

// 표를 행 단위(셀 배열)로 읽는다 — v1.1이 자기 날짜·비고를 유지하는지 행별로 확인하기 위함.
const rows = await page.locator('#amendments tbody tr').evaluateAll((trs) =>
  trs.map((tr) => [...tr.querySelectorAll('td')].map((td) => td.innerText.trim())),
);
const row = (v) => rows.find((r) => r[0] === v);

// v1.3(2항·8항) 추가 후: 적용일은 v1.3 행을 따르고, 앞선 세 행은 그대로여야 한다. v1.3 내용은
// privacy-token-rotation-check가 본다.
log('applies-from date follows the latest row (v1.3)', !!row('v1.3') && section.includes(`${row('v1.3')[1]}부터 적용`), row('v1.3')?.[1]);
log('exactly four revision rows: v1.0, v1.1, v1.2, v1.3 in order', rows.map((r) => r[0]).join(',') === 'v1.0,v1.1,v1.2,v1.3', rows.map((r) => r[0]).join(','));
log('v1.0 row keeps 2026-09-15 and 최초 제정', row('v1.0')?.[1] === '2026-09-15' && row('v1.0')?.[2] === '최초 제정');
log('v1.1 row keeps its own date 2026-09-21', row('v1.1')?.[1] === '2026-09-21');
log('v1.1 note unchanged (자기 확인 항목 도입 반영)', row('v1.1')?.[2] === '6항 연령 확인 관련 문구 수정: 회원가입 시 만 14세 이상 자기 확인 항목 도입 반영', row('v1.1')?.[2]);
log('v1.2 row keeps its own date 2026-09-21', row('v1.2')?.[1] === '2026-09-21');
log('v1.2 note reads server-side verification', row('v1.2')?.[2] === '6항 연령 확인 관련 문구 수정: 가입 시 서버에서도 연령 확인 항목을 검증하도록 변경 반영', row('v1.2')?.[2]);
log('7-day notice sentence still present', section.includes('최소 7일'));
await browser.close();
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length > 0 ? 1 : 0);
