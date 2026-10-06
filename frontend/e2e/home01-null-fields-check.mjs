// HOME-01 — 서버가 null 필드를 키째 빼는(non_null) 응답에서 NaN·undefined가 그려지지 않는지(2026-10-05).
// - 관심 지역 요약: 거래 없는 지역(평균가·변동률 없음) → "거래 없음"과 "—", 변동률만 없는 지역 → 평균가는 보이고 "—"
// - 인기 단지 카드(UIC-05 grid): 층·시군구·사용승인일·매칭 방식이 빠진 대표 거래 → "· undefined층" 없음, 층 세그먼트 생략
// 목업 본문은 mockApi.mjs(null 키 제거)로 만든다. 백엔드 불필요(route로 흉내). 데스크톱·모바일.
import { chromium } from 'playwright';
import { BASE } from './base.mjs';
import { okBody } from './mockApi.mjs';

let pass = 0;
let fail = 0;
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); }
}

const USER = { userId: 1, email: 'a@test.com', nickname: '지성', createdAt: '2026-09-15T10:20:30' };
const REGIONS = [
  // 최근 1개월 매매 없음 → 서버가 avgPrice·changeRate를 뺀다
  { favoriteRegionId: 1, legalDongCd: '1168010100', fullPath: '서울특별시 강남구 역삼동', avgPrice: null, changeRate: null, tradeCount: 0 },
  // 이번 달은 있고 직전 1개월이 없음 → changeRate만 빠진다
  { favoriteRegionId: 2, legalDongCd: '4111113500', fullPath: '경기도 수원시 장안구 파장동', avgPrice: 42000, changeRate: null, tradeCount: 2 },
  { favoriteRegionId: 3, legalDongCd: '4111113600', fullPath: '경기도 수원시 장안구 조원동', avgPrice: 50786, changeRate: -5.59, tradeCount: 7 },
];
const POPULAR = [
  {
    complexId: 900001, complexName: '층없는단지', sido: '세종특별자치시', sigungu: null, dongRi: '어진동', householdCount: 500,
    buildingCount: null, approvalDate: null, representativeHousingType: 'APT', representativeDealCategory: 'SALE',
    representativeDealDate: '2026-09-20', representativeAmount: 61000, representativeArea: 84.9, matchMethod: null, floor: null,
    rentType: null, monthlyRentAmount: null,
  },
];

const browser = await chromium.launch();

async function scenario(label, width) {
  const context = await browser.newContext({ viewport: { width, height: 1400 } });
  await context.addInitScript(() => {
    localStorage.setItem('homesense.accessToken', 'A');
    localStorage.setItem('homesense.refreshToken', 'R');
  });
  await context.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    const json = (body) => route.fulfill({ status: 200, contentType: 'application/json', body });
    if (path === '/api/users/me') return json(okBody(USER));
    if (path === '/api/regions/interest-summary') return json(okBody(REGIONS));
    if (path === '/api/complexes/popular') return json(okBody(POPULAR));
    return json(okBody([]));
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto(`${BASE}/`);
  await page.getByText('관심 지역 요약').waitFor({ timeout: 5000 });
  await page.getByText('역삼동', { exact: true }).waitFor({ timeout: 5000 });

  const card = (name) => page.getByText(name, { exact: true }).locator('xpath=ancestor::div[contains(@class,"rounded-[14px]")][1]');
  const empty = (await card('역삼동').innerText()).replace(/\s+/g, ' ');
  const partial = (await card('파장동').innerText()).replace(/\s+/g, ' ');
  const full = (await card('조원동').innerText()).replace(/\s+/g, ' ');
  ok(`${label}: 거래 없는 지역 — "거래 없음"과 "—"`, empty.includes('거래 없음') && empty.includes('—'), empty);
  ok(`${label}: 거래 없는 지역 — 스크린리더용 "변동 정보 없음"`, empty.includes('변동 정보 없음'), empty);
  ok(`${label}: 변동률만 없는 지역 — 평균가 표시, 변동률 "—"`, partial.includes('4억 2,000만원') && partial.includes('—'), partial);
  ok(`${label}: 값이 다 있는 지역 — 평균가·변동률`, full.includes('5억 786만원') && full.includes('5.6%'), full);

  const summaryText = await page.getByText('관심 지역 요약').locator('xpath=ancestor::div[contains(@class,"rounded-[16px]")][1]').innerText();
  ok(`${label}: 관심 지역 요약에 NaN·undefined 없음`, !/NaN|undefined/.test(summaryText), summaryText.match(/.{0,20}(NaN|undefined).{0,20}/)?.[0]);

  // 추천 단지는 모바일·데스크톱 목록을 둘 다 렌더한다(기술 부채) — 보이는 쪽 카드만 본다.
  const name = page.locator('p:visible', { hasText: '층없는단지' }).first();
  await name.waitFor({ timeout: 5000 });
  const cardText = (await name.locator('xpath=ancestor::div[contains(@class,"rounded-[16px]")][1]').innerText()).replace(/\s+/g, ' ');
  ok(`${label}: 층 없는 인기 단지 카드 — "층" 세그먼트 생략`, !/층/.test(cardText.replace('층없는단지', '')), cardText);
  ok(`${label}: 시군구 없는 단지 — 동리만 주소로`, cardText.includes('어진동') && !cardText.includes('null'), cardText);
  const bodyText = await page.locator('body').innerText();
  ok(`${label}: 화면 전체에 NaN·undefined·null 문자열 없음`, !/NaN|undefined|\bnull\b/.test(bodyText), bodyText.match(/.{0,20}(NaN|undefined|null).{0,20}/)?.[0]);
  ok(`${label}: pageerror 없음`, errors.length === 0, errors.join(' | '));
  await context.close();
}

await scenario('데스크톱', 1280);
await scenario('모바일', 390);
await browser.close();
console.log(`\n${pass} passed, ${fail} failed (total: ${pass + fail})`);
process.exit(fail ? 1 : 0);
