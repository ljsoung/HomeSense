// SCR-SRCH-01 회귀 테스트 — 필터 패널에서 값을 바꾸고 "필터 적용"을 누르지 않은 채 재검색바(키워드
// 제출/지역 자동완성 선택)로 검색하면, 그 미적용 draft 값이 조용히 버려지고 이전 URL 값으로
// 검색되던 버그(사용자 실보고: "거래유형을 선택하고 재검색하면 반영이 안 됨")의 재발을 막는다.
// 원인: handleSubmitKeyword/handleSelectRegion이 executeSearch의 base로 filters(URL 커밋값)를
// 넘겨 draft(패널의 현재 선택, 아직 미적용)를 무시했다 — base를 draft로 교체해 수정.
import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0, fail = 0;
function ok(name, cond, detail = '') { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); } }

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const searchReqs = [];
page.on('request', (r) => { if (r.url().includes('/api/complexes/search')) searchReqs.push(r.url()); });

await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=%EA%B2%BD%EA%B8%B0%EB%8F%84%20%EC%88%98%EC%9B%90%EC%8B%9C%20%EC%9E%A5%EC%95%88%EA%B5%AC`);
await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });

// 1) 필터 패널에서 월세로 바꾸고 "필터 적용"을 눌러 커밋 — baseline.
await page.getByRole('radio', { name: '월세' }).check();
await page.getByRole('button', { name: '필터 적용' }).click();
await page.waitForTimeout(500);
ok('필터 적용 직후 URL에 dealType=월세가 반영됨', page.url().includes('dealType=%EC%9B%94%EC%84%B8'));

// 2) 이제 매매로 바꾸되 "필터 적용"을 누르지 않고, 재검색바에서 같은 텍스트로 Enter.
searchReqs.length = 0;
await page.getByRole('radio', { name: '매매' }).check();
const input = page.locator('input[role="combobox"]');
await input.press('End');
await input.press('Enter');
await page.waitForTimeout(600);

const lastReq = searchReqs[searchReqs.length - 1] ?? '';
ok('재검색(키워드 제출) 요청에 매매(dealCategory=SALE)가 반영됨', lastReq.includes('dealCategory=SALE'), lastReq);
ok('재검색 요청에 이전 rentType(WOLSE)이 남아있지 않음', !lastReq.includes('rentType=WOLSE'), lastReq);
ok('재검색 후 URL에는 dealType(매매=기본값)이 생략됨', !page.url().includes('dealType='));

// 3) 필터 패널이 여전히 "매매"를 선택된 상태로 보여주는지(재검색 후 draft가 새 filters와 동기화).
const saleChecked = await page.getByRole('radio', { name: '매매' }).isChecked();
ok('재검색 후 필터 패널이 매매를 선택된 상태로 보여줌(draft와 filters 동기화)', saleChecked);

// 4) 지역 자동완성 선택 경로도 같은 방식으로 확인 — 전세로 바꾸고 적용 없이 자동완성으로 지역 재선택.
searchReqs.length = 0;
await page.getByRole('radio', { name: '전세' }).check();
await input.fill('');
await input.fill('수원시');
await page.waitForTimeout(500);
const firstOption = page.locator('[role="option"]').first();
await firstOption.waitFor({ state: 'visible', timeout: 5000 });
await firstOption.click();
await page.waitForTimeout(600);
const lastReq2 = searchReqs[searchReqs.length - 1] ?? '';
ok('지역 자동완성 재검색 요청에 전세(rentType=JEONSE)가 반영됨', lastReq2.includes('rentType=JEONSE'), lastReq2);

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
