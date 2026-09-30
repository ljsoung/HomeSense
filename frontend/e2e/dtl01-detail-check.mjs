// SCR-DTL-01 단지 상세 — 모킹 시나리오(page.route)와 실제 백엔드 스모크(360/768/1280).
// 전제: dev 서버(BASE), 실제 백엔드(8080, /api는 vite 프록시)·Redis·MariaDB. 비로그인 상태로 실행한다.
// 실제 백엔드 스모크의 단지 id는 REAL_ID(기본 10059, 로컬 DB의 수원한일타운아파트)로 바꿀 수 있다.
import { chromium } from 'playwright';
import { BASE } from './base.mjs';

const REAL_ID = Number(process.env.REAL_ID ?? 10059);
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

const MOCK_ID = 900001;

function ymd(monthsAgo, day) {
  const now = new Date();
  const d = new Date(now.getFullYear(), now.getMonth() - monthsAgo, day);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function detail(overrides = {}) {
  return {
    complexId: MOCK_ID,
    complexName: '모킹테스트아파트',
    complexType: '아파트',
    housingType: 'APT',
    sido: '경기도',
    sigungu: '수원장안구',
    dongRi: '조원동',
    legalDongCd: '4111112900',
    legalDongAddress: '경기도 수원장안구 조원동 881 모킹테스트아파트',
    matchPending: false,
    matchMethod: 'EXACT',
    basicInfo: { householdCount: 1000, buildingCount: 10, approvalDate: '2001-05-01', totalParkingCount: 1200, highestFloor: 25 },
    extendedInfo: {
      managementType: '위탁관리',
      groundParkingCount: 200,
      undergroundParkingCount: 1000,
      elevatorPassengerCount: 20,
      // 승강기 그룹 외 나머지는 비워 둔다 — 값이 모두 없는 그룹은 숨는다.
    },
    ...overrides,
  };
}

// 매매 45건: 최신이 index 0. 월별로 흩어 추이가 그려지게 한다. 두 번째 행은 해제, 세 번째는 직거래, 네 번째는
// 거래유형 없음, 첫 행은 등기 완료.
function saleTrades() {
  const list = [];
  for (let i = 0; i < 45; i++) {
    list.push({
      tradeId: 5000 + i,
      dealDate: ymd(Math.floor(i / 8), 20 - (i % 8)),
      dealCategory: 'SALE',
      excluUseArea: 84.98,
      floor: 5 + (i % 10),
      dealAmount: 50000 + (i % 5) * 1000,
      dealingType: i === 2 ? 'DIRECT' : i === 3 ? undefined : 'AGENT',
      isCancelled: i === 1,
      ...(i === 1 ? { cancelDate: ymd(0, 25) } : {}),
      isRegistered: i === 0,
    });
  }
  return list;
}

const JEONSE = [
  { tradeId: 7001, dealDate: ymd(0, 3), dealCategory: 'RENT', rentType: 'JEONSE', excluUseArea: 59.84, floor: 3, depositAmount: 30000, monthlyRentAmount: 0, isCancelled: false, isRegistered: false },
];
const WOLSE = [
  { tradeId: 8001, dealDate: ymd(0, 4), dealCategory: 'RENT', rentType: 'WOLSE', excluUseArea: 59.84, floor: 7, depositAmount: 5000, monthlyRentAmount: 110, isCancelled: false, isRegistered: false },
];

function envelope(data) {
  return { status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data, timestamp: '' }) };
}
function failure(status, code, message) {
  return {
    status,
    contentType: 'application/json',
    body: JSON.stringify({ success: false, data: null, error: { code, message }, timestamp: '' }),
  };
}

/** 모킹 라우트 설치. counts로 요청 수를 센다. opts로 실패·데이터를 바꾼다. */
async function mock(page, opts = {}) {
  const counts = { detail: 0, trades: { SALE: 0, JEONSE: 0, WOLSE: 0 }, tradeDetail: 0 };
  await page.route((url) => url.pathname === `/api/complexes/${MOCK_ID}`, async (route) => {
    counts.detail++;
    const r = opts.detail ? opts.detail(counts.detail) : envelope(detail(opts.detailOverrides));
    await route.fulfill(r);
  });
  await page.route((url) => url.pathname === '/api/trades', async (route) => {
    const type = new URL(route.request().url()).searchParams.get('dealType');
    counts.trades[type]++;
    if (opts.trades) {
      const r = opts.trades(type, counts.trades[type]);
      if (r) return route.fulfill(r);
    }
    const data = type === 'SALE' ? (opts.sale ?? saleTrades()) : type === 'JEONSE' ? JEONSE : WOLSE;
    await route.fulfill(envelope(data));
  });
  await page.route((url) => /^\/api\/trades\/\d+$/.test(url.pathname), async (route) => {
    counts.tradeDetail++;
    const id = Number(new URL(route.request().url()).pathname.split('/').pop());
    if (opts.tradeDetail) {
      const r = opts.tradeDetail(id, counts.tradeDetail);
      if (r) return route.fulfill(r);
    }
    await route.fulfill(
      envelope({
        tradeId: id,
        dealDate: ymd(0, 20),
        excluUseArea: 84.98,
        floor: 5,
        dealCategory: 'SALE',
        dealAmount: 50000,
        aptDongPending: true,
        dealingType: 'AGENT',
        sellerType: '개인',
        buyerType: '법인',
        isCancelled: false,
        landLeaseYn: false,
      }),
    );
  });
  return counts;
}

const browser = await chromium.launch();

async function newPage(width = 1280, height = 900, contextOptions = {}) {
  const context = await browser.newContext({ viewport: { width, height }, ...contextOptions });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  return { context, page, errors };
}

// 1) 잘못된 id — 요청 없이 "존재하지 않는 단지".
{
  const { context, page } = await newPage();
  const apiCalls = [];
  page.on('request', (r) => {
    const p = new URL(r.url()).pathname;
    if (p.startsWith('/api/complexes/') || p.startsWith('/api/trades')) apiCalls.push(p);
  });
  await page.goto(`${BASE}/complexes/abc`);
  await page.getByText('존재하지 않는 단지입니다').waitFor();
  ok('잘못된 id: 존재하지 않는 단지 안내', true);
  ok('잘못된 id: 단지·이력 요청 0건', apiCalls.length === 0);
  ok('잘못된 id: 홈으로 링크', (await page.getByRole('link', { name: '홈으로' }).getAttribute('href')) === '/');
  await context.close();
}

// 2) 404 — 빈 상태 + 홈으로.
{
  const { context, page } = await newPage(360, 780);
  await mock(page, { detail: () => failure(404, 'COMPLEX_NOT_FOUND', '존재하지 않는 단지입니다') });
  await page.goto(`${BASE}/complexes/${MOCK_ID}`);
  await page.getByText('존재하지 않는 단지입니다').waitFor();
  await page.getByRole('link', { name: '홈으로' }).click();
  await page.waitForURL(`${BASE}/`);
  ok('404: 홈으로 누르면 홈', true);
  await context.close();
}

// 3) 상세만 500 → 상단 오류 배너, 이력은 그대로. 다시 시도하면 상세만 다시 부른다.
{
  const { context, page } = await newPage();
  // StrictMode 개발 모드는 첫 요청을 한 번 취소하고 다시 보낸다 — 호출 횟수가 아니라 "다시 시도를 눌렀는지"로 가른다.
  let healed = false;
  const counts = await mock(page, {
    detail: () => (healed ? envelope(detail()) : failure(500, 'INTERNAL_SERVER_ERROR', '상세 서버 오류')),
  });
  await page.goto(`${BASE}/complexes/${MOCK_ID}`);
  await page.getByRole('alert').getByText('상세 서버 오류').waitFor();
  ok('상세 500: 서버 문구 배너', true);
  ok('상세 500: 이력 표는 그려짐', await page.locator('tbody tr').first().isVisible());
  const saleBefore = counts.trades.SALE;
  const detailBefore = counts.detail;
  healed = true;
  await page.getByRole('alert').getByRole('button', { name: '다시 시도' }).click();
  await page.locator('h1#complex-name').waitFor();
  ok('상세 500: 다시 시도 후 헤더 표시', (await page.locator('h1#complex-name').textContent()) === '모킹테스트아파트');
  ok('상세 500: 다시 시도는 상세만 다시 부름', counts.detail === detailBefore + 1 && counts.trades.SALE === saleBefore);
  await context.close();
}

// 4) 이력만 500 → 상세는 그대로, 이력 영역에 오류와 다시 시도(이력만 다시 부름).
{
  const { context, page } = await newPage();
  let healed = false;
  const counts = await mock(page, {
    trades: (type) => (type === 'SALE' && !healed ? failure(500, 'INTERNAL_SERVER_ERROR', '이력 서버 오류') : null),
  });
  await page.goto(`${BASE}/complexes/${MOCK_ID}`);
  await page.locator('h1#complex-name').waitFor();
  await page.getByText('이력 서버 오류').waitFor();
  ok('이력 500: 헤더는 표시', true);
  ok('이력 500: 가격 추이도 오류 표시', await page.getByText('가격 추이를 불러오지 못했습니다.').isVisible());
  const detailBefore = counts.detail;
  const saleBefore = counts.trades.SALE;
  healed = true;
  await page.locator('section[aria-labelledby="trade-history-title"]').getByRole('button', { name: '다시 시도' }).click();
  await page.locator('tbody tr').first().waitFor();
  ok('이력 500: 다시 시도는 이력만 다시 부름', counts.trades.SALE === saleBefore + 1 && counts.detail === detailBefore);
  await context.close();
}

// 5) 정상(모킹) — 헤더·요약·토글·표·더보기·모달·추이·공유.
{
  const { context, page, errors } = await newPage(1280, 900, { permissions: ['clipboard-read', 'clipboard-write'] });
  const counts = await mock(page);
  await page.goto(`${BASE}/complexes/${MOCK_ID}`);
  await page.locator('h1#complex-name').waitFor();
  ok('제목: {단지명} | HomeSense', (await page.title()) === '모킹테스트아파트 | HomeSense');
  ok('정밀 배지', await page.getByText('정밀', { exact: true }).isVisible());
  ok('EXACT면 근사 안내 없음', (await page.getByTestId('similar-match-notice').count()) === 0);
  ok(
    '경로: 시도 링크 regionCode 앞 2자리',
    (await page.getByRole('link', { name: '경기도' }).getAttribute('href'))?.includes('regionCode=4100000000'),
  );
  ok(
    '경로: 시군구 링크 regionCode 앞 5자리',
    (await page.getByRole('link', { name: '수원장안구' }).getAttribute('href'))?.includes('regionCode=4111100000'),
  );
  ok('주소는 끝의 단지명을 뗀다', await page.getByText('경기도 수원장안구 조원동 881', { exact: true }).first().isVisible());
  ok('요약: 시공사 없음 → 정보 없음', await page.getByText('정보 없음').first().isVisible());

  // 상세정보 토글
  const toggle = page.getByRole('button', { name: /상세정보 보기/ });
  ok('토글: 기본 접힘', (await toggle.getAttribute('aria-expanded')) === 'false');
  await toggle.click();
  const toggleOpen = page.getByRole('button', { name: /상세정보 접기/ });
  ok('토글: 펼침 aria-expanded', (await toggleOpen.getAttribute('aria-expanded')) === 'true');
  ok('토글: 값 있는 그룹(승강기) 표시', await page.getByRole('heading', { name: '승강기' }).isVisible());
  ok('토글: 값 없는 그룹(보안/편의시설) 숨김', (await page.getByRole('heading', { name: '보안/편의시설' }).count()) === 0);
  ok('토글: 세대당 주차대수 1.20대', await page.getByText('1.20대').isVisible());
  await toggleOpen.click();

  // 이력 표
  const rows = page.locator('tbody tr');
  ok('표: 처음 20행', (await rows.count()) === 20);
  ok('표: 범례(매매 탭)', await page.getByLabel('범례').isVisible());
  ok('표: 첫 행 등기 완료', (await rows.nth(0).textContent()).includes('완료'));
  ok('표: 해제 행 표시·해제일', (await rows.nth(1).getAttribute('data-cancelled')) === 'true' && (await rows.nth(1).textContent()).includes('해제'));
  ok('표: 직거래 매핑', (await rows.nth(2).textContent()).includes('직거래'));
  ok('표: 거래유형 없으면 -', (await rows.nth(3).locator('td').nth(4).textContent()).trim() === '-');
  ok('표: 중개거래 매핑·등기 전', (await rows.nth(4).textContent()).includes('중개거래') && (await rows.nth(4).textContent()).includes('등기 전'));
  ok('표: 계약일 YYYY.MM.DD와 행 이름', /^\d{4}\.\d{2}\.\d{2} 거래 상세 보기$/.test(await rows.nth(0).getAttribute('aria-label')));
  await page.getByRole('button', { name: /더보기/ }).click();
  ok('더보기: 40행', (await rows.count()) === 40);
  await page.getByRole('button', { name: /더보기/ }).click();
  ok('더보기: 45행 후 버튼 사라짐', (await rows.count()) === 45 && (await page.getByRole('button', { name: /더보기/ }).count()) === 0);

  // 모달(키보드로 열기)
  await rows.nth(0).focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: '거래 상세' });
  await dialog.getByText('매도자').waitFor();
  ok('모달: aria-modal', (await dialog.getAttribute('aria-modal')) === 'true');
  ok('모달: 동 없으면 등기 완료 후 제공', await dialog.getByText('등기 완료 후 제공').isVisible());
  ok('모달: 거래유형 같은 매핑(중개거래)', await dialog.getByText('중개거래').isVisible());
  ok('모달: 등기일자 없으면 등기 전', await dialog.getByText('등기 전').isVisible());
  ok('모달: 중개사소재지 행 없음', (await dialog.getByText('중개사').count()) === 0);
  await page.keyboard.press('Escape');
  await dialog.waitFor({ state: 'detached' });
  ok('모달: Esc 후 행으로 포커스 복귀', (await page.evaluate(() => document.activeElement?.getAttribute('aria-label'))) === (await rows.nth(0).getAttribute('aria-label')));
  ok('모달: 스크롤 잠금 해제', (await page.evaluate(() => document.body.style.overflow)) === '');

  // 추이
  ok('추이: 차트 role=img', await page.getByRole('img', { name: /평균 거래가/ }).isVisible());
  ok('추이: 변동률 기간 표기', /\(\d+개월\)|\(1년\)/.test(await page.locator('section', { has: page.getByRole('heading', { name: '가격 추이' }) }).textContent()));

  // 탭 — 전세로 바꿔도 매매 이력은 다시 받지 않는다.
  const saleCalls = counts.trades.SALE;
  await page.getByRole('tab', { name: '매매' }).focus();
  await page.keyboard.press('ArrowRight');
  await page.getByRole('tab', { name: '전세', selected: true }).waitFor();
  ok('탭: 방향키로 전세, URL ?deal=JEONSE', page.url().endsWith('?deal=JEONSE'));
  await page.locator('tbody tr').first().waitFor();
  const jeonseHeaders = await page.locator('thead').textContent();
  ok('전세: 등기·거래유형 열 없음, 범례 없음', !jeonseHeaders.includes('등기') && !jeonseHeaders.includes('거래유형') && (await page.getByLabel('범례').count()) === 0);
  ok('전세: 등기 전 표시 없음', !(await page.locator('tbody').textContent()).includes('등기 전'));
  await page.getByRole('tab', { name: '월세' }).click();
  await page.locator('thead').getByText('월세').waitFor();
  ok('월세: 월세 열', (await page.locator('tbody').textContent()).includes('110만원'));
  await page.getByRole('tab', { name: '매매' }).click();
  ok('탭: 매매로 돌아와도 매매 이력 재요청 없음', counts.trades.SALE === saleCalls);
  ok('탭: 매매면 URL에서 deal 생략', !page.url().includes('deal='));

  // 공유
  await page.getByRole('button', { name: '링크 공유' }).click();
  await page.getByText('링크를 복사했습니다').waitFor();
  ok('공유: 클립보드에 현재 주소', (await page.evaluate(() => navigator.clipboard.readText())) === page.url());
  ok('정상: pageerror 없음', errors.length === 0);
  await context.close();
}

// 6) 공유 실패
{
  const { context, page } = await newPage();
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: () => Promise.reject(new Error('denied')) } });
  });
  await mock(page);
  await page.goto(`${BASE}/complexes/${MOCK_ID}`);
  await page.locator('h1#complex-name').waitFor();
  await page.getByRole('button', { name: '링크 공유' }).click();
  await page.getByText('링크를 복사하지 못했습니다').waitFor();
  ok('공유 실패: 안내 토스트', true);
  await context.close();
}

// 7) SIMILAR / matchMethod null / matchPending / ?deal= 잘못된 값 / 추이 부족
{
  const { context, page } = await newPage(360, 780);
  await mock(page, { detailOverrides: { matchMethod: 'SIMILAR' } });
  await page.goto(`${BASE}/complexes/${MOCK_ID}?deal=xx`);
  await page.locator('h1#complex-name').waitFor();
  ok('SIMILAR: 근사 배지·유사 매칭 결과 안내', (await page.getByText('근사', { exact: true }).isVisible()) && (await page.getByText('유사 매칭 결과').isVisible()));
  ok('잘못된 deal: 매매 탭 선택·URL 정리', (await page.getByRole('tab', { name: '매매', selected: true }).count()) === 1 && !page.url().includes('deal='));
  ok('모바일: 관심·공유는 아이콘만(글자 숨김)', !(await page.getByText('관심 등록', { exact: true }).isVisible()) && (await page.getByRole('button', { name: '관심 매물 등록' }).isVisible()));
  await context.close();
}
{
  const { context, page } = await newPage();
  await mock(page, { detailOverrides: { matchMethod: undefined } });
  await page.goto(`${BASE}/complexes/${MOCK_ID}`);
  await page.locator('h1#complex-name').waitFor();
  ok('matchMethod 없음: 정밀·근사 배지 없음', (await page.getByText('정밀', { exact: true }).count()) === 0 && (await page.getByText('근사', { exact: true }).count()) === 0);
  ok('matchMethod 없음: 유사 안내 없음', (await page.getByTestId('similar-match-notice').count()) === 0);
  await context.close();
}
{
  const { context, page } = await newPage(768, 1024);
  await mock(page, { detailOverrides: { matchPending: true }, sale: saleTrades().slice(0, 3).map((t) => ({ ...t, dealDate: ymd(0, 10) })) });
  await page.goto(`${BASE}/complexes/${MOCK_ID}?deal=JEONSE`);
  await page.getByText('단지 정보를 준비 중입니다').waitFor();
  ok('matchPending: 준비 중 안내, 요약·토글 없음', (await page.getByRole('button', { name: /상세정보/ }).count()) === 0 && (await page.getByText('세대수').count()) === 0);
  ok('deal=JEONSE 직접 진입: 전세 탭', (await page.getByRole('tab', { name: '전세', selected: true }).count()) === 1);
  ok('추이: 한 달뿐이면 부족 안내', await page.getByText('최근 1년 매매 거래가 적어 추이를 표시할 수 없습니다').isVisible());
  await context.close();
}

// 8) 비로그인 관심 등록 → 로그인 화면, 직접 진입 후 뒤로 → 홈
{
  const { context, page } = await newPage();
  await mock(page);
  await page.goto(`${BASE}/complexes/${MOCK_ID}`);
  await page.locator('h1#complex-name').waitFor();
  await page.getByRole('button', { name: '관심 매물 등록' }).click();
  await page.waitForURL(/\/login$/);
  ok('비로그인 관심 등록: 로그인 화면', true);
  ok('비로그인 관심 등록: 재생 대기(sessionStorage)', (await page.evaluate(() => sessionStorage.getItem('homesense.pendingFavoriteComplexId'))) === String(MOCK_ID));
  await context.close();
}
{
  const { context, page } = await newPage();
  await mock(page);
  await page.goto(`${BASE}/complexes/${MOCK_ID}`);
  await page.locator('h1#complex-name').waitFor();
  await page.getByRole('button', { name: '뒤로 가기' }).click();
  await page.waitForURL(`${BASE}/`);
  ok('직접 진입 후 뒤로: 홈', true);
  await context.close();
}

// 9) 실제 백엔드 스모크 — 3개 폭. 가로 스크롤 없음, 순서, 사이드바 sticky(1280만), 내비 활성.
for (const [width, height] of [[360, 780], [768, 1024], [1280, 900]]) {
  const { context, page, errors } = await newPage(width, height);
  const responses = [];
  page.on('response', (r) => {
    const p = new URL(r.url()).pathname;
    if (p.startsWith('/api/complexes/') || p === '/api/trades') responses.push(r.status());
  });
  await page.goto(`${BASE}/complexes/${REAL_ID}`);
  await page.locator('h1#complex-name').waitFor({ timeout: 15000 });
  await page.locator('tbody tr').first().waitFor({ timeout: 15000 });
  ok(`실백엔드 ${width}: 단지·이력 200`, responses.length >= 2 && responses.every((s) => s === 200));
  ok(`실백엔드 ${width}: 가로 스크롤 없음`, (await page.evaluate(() => document.documentElement.scrollWidth)) <= width);
  const y = async (loc) => (await loc.boundingBox())?.y ?? -1;
  const header = await y(page.locator('h1#complex-name'));
  const summary = await y(page.getByLabel('기본정보 요약'));
  const toggleY = await y(page.getByRole('button', { name: /상세정보 보기/ }));
  const trend = await y(page.getByRole('heading', { name: '가격 추이' }));
  const location = await y(page.getByRole('heading', { name: '위치' }));
  const history = await y(page.getByRole('tablist', { name: '거래유형' }));
  const sticky = await page.locator('aside[aria-label="가격 추이와 위치"] > div').evaluate((el) => getComputedStyle(el).position);
  if (width >= 1280) {
    ok(`실백엔드 ${width}: 본문 순서(헤더<요약<토글<이력)`, header < summary && summary < toggleY && toggleY < history);
    ok(`실백엔드 ${width}: 사이드바 sticky·추이가 이력보다 위`, sticky === 'sticky' && trend < history && trend < location);
  } else {
    ok(`실백엔드 ${width}: 쌓임 순서`, header < summary && summary < toggleY && toggleY < trend && trend < location && location < history);
    ok(`실백엔드 ${width}: sticky 아님`, sticky !== 'sticky');
  }
  if (width >= 768) {
    const cls = await page.getByRole('link', { name: '지역·단지 검색' }).getAttribute('class');
    ok(`실백엔드 ${width}: GNB 지역·단지 검색 활성`, cls.includes('text-brand'));
  } else {
    const cls = await page.getByRole('link', { name: '검색' }).getAttribute('class');
    ok(`실백엔드 ${width}: 하단 탭 검색 활성`, cls.includes('text-brand'));
  }
  // 표는 자기 영역 안에서만 가로 스크롤한다.
  const tableFits = await page.getByTestId('trade-history-scroll').evaluate((el) => el.getBoundingClientRect().right <= window.innerWidth);
  ok(`실백엔드 ${width}: 표 스크롤 영역이 화면 안`, tableFits);
  await page.locator('tbody tr').first().click();
  await page.getByRole('dialog', { name: '거래 상세' }).getByText('계약일').waitFor({ timeout: 10000 });
  ok(`실백엔드 ${width}: 거래 상세 모달`, true);
  await page.keyboard.press('Escape');
  ok(`실백엔드 ${width}: pageerror 없음`, errors.length === 0);
  await context.close();
}

// 10) 실제 백엔드: 검색 결과에서 들어왔다가 뒤로 → 검색 결과
{
  const { context, page } = await newPage();
  await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=${encodeURIComponent('경기도 수원시 장안구')}`);
  const card = page.locator('a[href^="/complexes/"]').first();
  await card.waitFor({ timeout: 15000 });
  await card.click();
  await page.locator('h1#complex-name').waitFor({ timeout: 15000 });
  await page.getByRole('button', { name: '뒤로 가기' }).click();
  await page.waitForURL(/\/search\?/);
  ok('실백엔드: 검색 결과 → 상세 → 뒤로 = 검색 결과', true);
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail === 0 ? 0 : 1);
