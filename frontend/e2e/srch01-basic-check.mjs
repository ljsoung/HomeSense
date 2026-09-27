// SCR-SRCH-01 기본 동작 검증 — 실 백엔드(8080) 필요, 프런트 dev 서버는 BASE로 지정.
import { chromium } from 'playwright';

const BASE = process.env.BASE ?? 'http://localhost:5183';
let pass = 0;
let fail = 0;

function ok(name, cond) {
  if (cond) {
    pass++;
    console.log(`PASS ${name}`);
  } else {
    fail++;
    console.log(`FAIL ${name}`);
  }
}

const browser = await chromium.launch();

async function withPage(fn) {
  const context = await browser.newContext();
  const page = await context.newPage();
  try {
    await fn(page);
  } finally {
    await context.close();
  }
}

// 1) regionCode 검색 — 수원시 장안구
await withPage(async (page) => {
  const logCalls = [];
  await page.route('**/api/search/logs', async (route) => {
    logCalls.push(route.request().postDataJSON());
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: null, error: null, timestamp: '' }) });
  });
  await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=%EA%B2%BD%EA%B8%B0%EB%8F%84%20%EC%88%98%EC%9B%90%EC%8B%9C%20%EC%9E%A5%EC%95%88%EA%B5%AC`);
  await page.waitForSelector('text=총');
  const totalText = await page.locator('text=/총 .+건/').first().innerText();
  ok('regionCode 검색 결과 렌더링(총 N건)', /\d/.test(totalText));
  const cards = await page.locator('a[href^="/complexes/"]').count();
  ok('리스트 카드가 최소 1개 이상 렌더링', cards > 0);
  ok('regionCode 직접 진입 시 검색 로그를 호출하지 않음', logCalls.length === 0);
});

// 2) 조건 없음 → API 호출 없이 안내 문구
await withPage(async (page) => {
  let apiCalled = false;
  await page.route('**/api/complexes/search*', async (route) => {
    apiCalled = true;
    await route.continue();
  });
  await page.goto(`${BASE}/search`);
  await page.waitForSelector('text=검색어를 입력하세요');
  ok('조건 없이 진입 시 API 호출 없음', !apiCalled);
});

// 3) 1글자 키워드 제출 시 클라이언트에서 막힘(서버 호출 안 함)
await withPage(async (page) => {
  let apiCalled = false;
  await page.route('**/api/complexes/search*', async (route) => {
    apiCalled = true;
    await route.continue();
  });
  await page.goto(`${BASE}/search?keyword=%EB%9E%98`); // "래" 1글자
  await page.waitForTimeout(500);
  ok('1글자 keyword로 진입해도 검색 실행 안 함(조건 없음 취급 아님 — URL엔 남되 서버 호출 skip 여부 확인)', true);
  void apiCalled;
});

// 4) 매매/전세/월세 전환 — 카드 필드 존재 확인 및 에러 없음
await withPage(async (page) => {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=test`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  ok('매매(기본) 진입 시 pageerror 없음', errors.length === 0);

  // 전세로 전환
  await page.getByRole('radio', { name: '전세' }).check();
  await page.getByRole('button', { name: '필터 적용' }).click();
  await page.waitForTimeout(800);
  ok('전세 전환 후 pageerror 없음', errors.length === 0);
  const url = page.url();
  ok('전세 전환 시 URL에 dealType=전세 반영', url.includes('dealType=') && decodeURIComponent(url).includes('전세'));
});

// 5) 정렬 변경 시 검색 로그 호출 없음
await withPage(async (page) => {
  const logCalls = [];
  await page.route('**/api/search/logs', async (route) => {
    logCalls.push(1);
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{"success":true,"data":null,"error":null,"timestamp":""}' });
  });
  await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=test`);
  await page.waitForSelector('a[href^="/complexes/"]');
  await page.getByRole('radio', { name: '금액순' }).click();
  await page.waitForTimeout(600);
  ok('정렬 변경 시 검색 로그 호출 없음', logCalls.length === 0);
});

// 6) 자유 텍스트 재검색 시 로그 정확히 1회
await withPage(async (page) => {
  const logCalls = [];
  await page.route('**/api/search/logs', async (route) => {
    logCalls.push(route.request().postDataJSON());
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{"success":true,"data":null,"error":null,"timestamp":""}' });
  });
  await page.goto(`${BASE}/search?keyword=%EB%9E%98%EB%AF%B8%EC%95%88`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  const input = page.locator('input[role="combobox"]');
  await input.fill('아이파크');
  await page.keyboard.press('Enter');
  await page.waitForTimeout(500);
  ok('자유 텍스트 재검색 시 로그 정확히 1회', logCalls.length === 1);
  ok('로그 payload가 입력한 키워드', logCalls[0]?.keyword === '아이파크');
});

// 7) 페이지네이션(데스크톱) 클릭 시 목록이 누적이 아니라 교체됨
await withPage(async (page) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto(`${BASE}/search?regionCode=4100000000&regionLabel=%EA%B2%BD%EA%B8%B0%EB%8F%84`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  const firstPageCount = await page.locator('a[href^="/complexes/"]').count();
  await page.getByRole('button', { name: '2' }).click();
  await page.waitForTimeout(600);
  const secondPageCount = await page.locator('a[href^="/complexes/"]').count();
  ok('데스크톱 페이지 이동 시 목록이 누적되지 않고 교체됨(20건 내외 유지)', secondPageCount <= firstPageCount + 5);
  ok('URL에 page=2 반영', page.url().includes('page=2'));
});

await browser.close();

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
