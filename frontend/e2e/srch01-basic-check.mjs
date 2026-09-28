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

// 3) URL로 복원한 검색어도 요청 전에 서버(SearchKeywordPolicy)와 같은 규칙으로 검사한다 — 제출 시점
//    검사만 있을 때는 /search?keyword=래 같은 URL이 그대로 서버로 가서 400 → 일반 오류 화면이 떴다.
async function openSearch(page, query) {
  const searchCalls = [];
  const logCalls = [];
  page.on('request', (r) => {
    if (r.url().includes('/api/complexes/search')) searchCalls.push(r.url());
    if (r.url().includes('/api/search/logs')) logCalls.push(r.postData());
  });
  await page.goto(`${BASE}/search?${query}`);
  await page.waitForLoadState('networkidle');
  return { searchCalls, logCalls };
}
const alertText = async (page) => (await page.getByRole('alert').allTextContents()).join(' ');
const errorBannerShown = (page) => page.getByRole('button', { name: '다시 시도' }).isVisible();

await withPage(async (page) => {
  const { searchCalls } = await openSearch(page, `keyword=${encodeURIComponent('래')}`);
  ok('URL 1글자 keyword: 검색 API 호출 없음', searchCalls.length === 0);
  ok('URL 1글자 keyword: 입력창 아래 2자 안내 표시', (await alertText(page)).includes('2자 이상'));
  ok('URL 1글자 keyword: 본문에 2자 안내(오류 화면 아님)', await page.getByText('검색어는 2자 이상 입력해주세요.').last().isVisible());
  ok('URL 1글자 keyword: 일반 오류 배너 없음', !(await errorBannerShown(page)));
  ok('URL 1글자 keyword: 입력창에 검색어 복원', (await page.locator('input[role="combobox"]').inputValue()) === '래');
});

await withPage(async (page) => {
  const { searchCalls } = await openSearch(page, `keyword=${encodeURIComponent('😀')}`);
  ok('URL 이모지 1개(코드포인트 1자): 서버처럼 2자 미만 처리, API 호출 없음', searchCalls.length === 0 && (await alertText(page)).includes('2자 이상'));
});

await withPage(async (page) => {
  const { searchCalls } = await openSearch(page, `keyword=${encodeURIComponent('가'.repeat(51))}`);
  ok('URL 51자 keyword: 50자 안내, API 호출 없음', searchCalls.length === 0 && (await alertText(page)).includes('50자 이하'));
});

await withPage(async (page) => {
  const { searchCalls } = await openSearch(page, `keyword=${encodeURIComponent('가'.repeat(50))}`);
  ok('URL 50자 keyword: 경계값은 검색 실행(API 호출)', searchCalls.length === 1 && !(await errorBannerShown(page)));
});

await withPage(async (page) => {
  const { searchCalls } = await openSearch(page, 'keyword=%20%20');
  ok('URL 공백만 keyword: 조건 없음 안내, API 호출 없음', searchCalls.length === 0 && (await page.getByText('검색어를 입력하세요').isVisible()));
});

await withPage(async (page) => {
  const { searchCalls } = await openSearch(page, `keyword=${encodeURIComponent(' 래미 ')}`);
  ok('URL 2글자(앞뒤 공백) keyword: trim 후 검색 실행', searchCalls.length === 1 && new URL(searchCalls[0]).searchParams.get('keyword') === '래미');
});

await withPage(async (page) => {
  const { searchCalls } = await openSearch(page, `regionCode=4111100000&regionLabel=test&keyword=${encodeURIComponent('래')}`);
  ok('regionCode와 1글자 keyword가 함께면 regionCode로 검색(keyword 미전송)', searchCalls.length === 1 && !new URL(searchCalls[0]).searchParams.has('keyword'));
});

// 3-1) 홈 히어로에서 1글자로 검색 — 결과 화면이 요청 없이 안내하고, 검색 기록도 남기지 않는다.
await withPage(async (page) => {
  const searchCalls = [];
  const logCalls = [];
  page.on('request', (r) => {
    if (r.url().includes('/api/complexes/search')) searchCalls.push(r.url());
    if (r.url().includes('/api/search/logs')) logCalls.push(r.postData());
  });
  await page.goto(`${BASE}/`);
  await page.waitForLoadState('networkidle');
  const input = page.locator('input[role="combobox"]').first();
  await input.fill('래');
  await input.press('Enter');
  await page.waitForURL(/\/search\?/, { timeout: 5000 });
  await page.waitForLoadState('networkidle');
  ok('홈에서 1글자 검색: 결과 화면에서 검색 API 호출 없음', searchCalls.length === 0);
  // 홈에서 이동한 직후엔 networkidle이 검색 화면 렌더보다 먼저 끝날 수 있어 안내가 뜰 때까지 기다린다.
  const alertShown = await page.getByRole('alert').filter({ hasText: '2자 이상' }).waitFor({ timeout: 5000 }).then(() => true, () => false);
  ok('홈에서 1글자 검색: 2자 안내 표시', alertShown);
  ok('홈에서 1글자 검색: 안내가 뜬 뒤에도 검색 API 호출 없음', searchCalls.length === 0);
  ok('홈에서 1글자 검색: 검색 기록 요청 없음', logCalls.length === 0);
});

// 3-2) 재검색 바에서 1글자 제출은 이동 자체를 막고, 고치면 안내가 사라진다.
await withPage(async (page) => {
  const { searchCalls } = await openSearch(page, 'regionCode=4111100000&regionLabel=test');
  const before = page.url();
  const input = page.locator('input[role="combobox"]').first();
  await input.fill('래');
  await input.press('Enter');
  await page.waitForTimeout(300);
  ok('재검색 1글자 제출: URL 그대로', page.url() === before);
  ok('재검색 1글자 제출: 2자 안내 표시', (await alertText(page)).includes('2자 이상'));
  await input.fill('래미');
  ok('재검색 입력을 고치면 안내가 사라짐', (await page.getByRole('alert').count()) === 0);
  void searchCalls;
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
