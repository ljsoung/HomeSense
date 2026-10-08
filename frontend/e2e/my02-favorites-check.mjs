// MY-02 관심 매물·지역 관리 — 모바일(390)·태블릿(768)·데스크톱(1280). 백엔드 불필요(route로 상태 있는 가짜 서버).
// 1. 렌더: 데스크톱은 세로 섹션 + 정렬 chip, 태블릿·모바일은 탭 + 정렬 드롭다운(탭 전환·URL ?tab=regions)
//    카드(면적·층, 최근 매매가, 변동률 문구, 알림조건 배지 4종), 정렬(변동률순 → URL ?sort=rate, null 맨 뒤)
// 2. 빈 상태: 안내 카드 + "매물 둘러보기"(→ /) + 관심 지역 추가 입력은 남는다
// 3. 삭제: 취소 → 그대로 / 확인 → 숨김·토스트 → 실행취소 → DELETE 없음 / 확인 → 5초 뒤 DELETE 1회
//    대기 중 다른 항목 삭제 → 앞 건 즉시 DELETE, 대기 중 화면 이동 → 즉시 DELETE, 실패 → 되살림 + 서버 문구
// 4. 지역 추가: 리 단위 후보 제외, 성공(맨 위·정렬 등록순 복귀·POST 본문), 중복(요청 없음), 후보 없음
//    4-1. 입력을 바꾼 직후(다음 응답 붙잡음) 이전 후보 없음·활성 후보 초기화·Enter로 선택 안 됨
// 5. 이동: 매물 → DTL-01, 지역 → SRCH-01(regionCode), 배지 → MY-03 ?favoritePropertyId=, 모바일 하단 탭 "찜" 활성
// 6. 키보드만으로 삭제·실행취소·지역 추가, 삭제 뒤 포커스가 다음 항목으로
// 7. 가로 스크롤 없음
// 8. 알림 설정 조회만 실패 → 목록은 표시, 배지만 숨김
// 1에 Figma 대조 항목 포함: 썸네일 크기(60×46·90×70·64×64), 모바일 라벨 열·삭제 터치 영역·삭제 글자색
import { chromium } from 'playwright';
import { BASE } from './base.mjs';
import { errBody, okBody } from './mockApi.mjs';

let pass = 0;
let fail = 0;
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); }
}
// 목업 본문은 mockApi.mjs로 만든다 — 실 서버처럼 null 필드를 JSON에서 뺀다(non_null).

const USER = { userId: 7, email: 'jiseong@homesense.kr', nickname: '지성', createdAt: '2026-09-15T10:20:30' };

function property(id, name, { amount = 90000, rate = null, area = 84.98, floor = 9, registeredAt }) {
  return {
    favoritePropertyId: id, complexId: id * 10, complexName: name, sido: '서울특별시', sigungu: '서초구', dongRi: '반포동',
    housingType: 'APT', registeredAt, recentDealCategory: amount === null ? null : 'SALE',
    recentDealDate: amount === null ? null : '2026-09-20', recentAmount: amount, recentArea: amount === null ? null : area,
    recentFloor: amount === null ? null : floor, changeRate: rate, hasNotificationSetting: false,
  };
}
function region(id, code, sigungu, name, { rate = null, registeredAt, perPyeong = 5123.4, count = 4 }) {
  return {
    favoriteRegionId: id, legalDongCd: code, fullPath: `경기도 ${sigungu} ${name}`, sidoName: '경기도', sigunguName: sigungu,
    eupmyeondongName: name, registeredAt, avgPrice: 90000, changeRate: rate, pricePerPyeong: perPyeong, newTradeCount: count,
  };
}
const initialProperties = () => [
  property(1, '래미안 원베일리', { amount: 425000, rate: 3.2, registeredAt: '2026-09-01T10:00:00' }),
  property(2, '아크로리버파크', { amount: 389000, rate: -1.1, area: 59.9, floor: 3, registeredAt: '2026-09-03T10:00:00' }),
  property(3, '반포자이', { amount: null, registeredAt: '2026-09-02T10:00:00' }),
  property(4, '신반포센트럴자이', { amount: 280000, rate: 0, registeredAt: '2026-08-30T10:00:00' }),
];
const initialRegions = () => [
  region(11, '4155036000', '안성시', '금광면', { rate: 1.5, registeredAt: '2026-09-02T09:00:00' }),
  region(12, '4111113500', '수원시 장안구', '파장동', { rate: null, registeredAt: '2026-09-04T09:00:00', perPyeong: null, count: 0 }),
];
const SETTINGS = [
  { notificationSettingId: 1, favoritePropertyId: 1, favoriteRegionId: null, priceChangeThresholdPct: 5, newTradeAlertYn: true, emailAlertYn: true },
  { notificationSettingId: 2, favoritePropertyId: 2, favoriteRegionId: null, priceChangeThresholdPct: 2.5, newTradeAlertYn: false, emailAlertYn: true },
  { notificationSettingId: 3, favoritePropertyId: 4, favoriteRegionId: null, priceChangeThresholdPct: 5, newTradeAlertYn: true, emailAlertYn: false },
];
const AUTOCOMPLETE = {
  금광: [
    { legalDongCd: '4155036000', fullPath: '경기도 안성시 금광면' },
    { legalDongCd: '4155036021', fullPath: '경기도 안성시 금광면 개산리' },
    { legalDongCd: '4155036022', fullPath: '경기도 안성시 금광면 금광리' },
  ],
  역삼: [{ legalDongCd: '1168010100', fullPath: '서울특별시 강남구 역삼동' }],
  기장: [
    { legalDongCd: '2671025021', fullPath: '부산광역시 기장군 기장읍 동부리' },
    { legalDongCd: '2671025022', fullPath: '부산광역시 기장군 기장읍 서부리' },
  ],
};

const browser = await chromium.launch();

/** options.empty: 두 목록을 비운다. deleteMode: 'ok'|'fail'|'notFound'. clock: page.clock 설치. */
/** holdQuery: 그 검색어의 자동완성 응답을 releaseHold()까지 붙잡는다(요청 중 상태를 만든다). */
async function open(viewport, { empty = false, deleteMode = 'ok', clock = false, query = '', settingsMode = 'ok', holdQuery = null } = {}) {
  let releaseHold = () => {};
  const hold = new Promise((resolve) => { releaseHold = resolve; });
  const context = await browser.newContext({ viewport });
  await context.addInitScript(() => {
    if (!sessionStorage.getItem('__seeded')) {
      localStorage.setItem('homesense.accessToken', 'A');
      localStorage.setItem('homesense.refreshToken', 'R');
      sessionStorage.setItem('__seeded', '1');
    }
  });
  const db = { properties: empty ? [] : initialProperties(), regions: empty ? [] : initialRegions(), nextRegionId: 100 };
  const calls = [];
  await context.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    calls.push({ method, path, body: request.postData() });
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body });
    if (path === '/api/users/me') return json(200, okBody(USER));
    if (path === '/api/favorites/properties' && method === 'GET') return json(200, okBody(db.properties));
    if (path === '/api/favorites/regions' && method === 'GET') return json(200, okBody(db.regions));
    if (path === '/api/notifications/settings') {
      if (settingsMode === 'fail') return json(500, errBody('INTERNAL_SERVER_ERROR', '일시적인 오류가 발생했습니다'));
      return json(200, okBody(SETTINGS));
    }
    if (path === '/api/regions') {
      const q = url.searchParams.get('query') ?? '';
      if (q === holdQuery) await hold;
      const key = Object.keys(AUTOCOMPLETE).find((k) => q.startsWith(k));
      return json(200, okBody(key ? AUTOCOMPLETE[key] : []));
    }
    const del = path.match(/^\/api\/favorites\/(properties|regions)\/(\d+)$/);
    if (del && method === 'DELETE') {
      if (deleteMode === 'fail') return json(500, errBody('INTERNAL_SERVER_ERROR', '삭제하지 못했습니다. 잠시 후 다시 시도해주세요'));
      if (deleteMode === 'notFound') return json(404, errBody('FAVORITE_NOT_FOUND', '존재하지 않는 관심 등록입니다'));
      const id = Number(del[2]);
      if (del[1] === 'properties') db.properties = db.properties.filter((p) => p.favoritePropertyId !== id);
      else db.regions = db.regions.filter((r) => r.favoriteRegionId !== id);
      return json(200, okBody(null));
    }
    if (path === '/api/favorites/regions' && method === 'POST') {
      const { legalDongCd } = JSON.parse(request.postData() ?? '{}');
      if (db.regions.some((r) => r.legalDongCd === legalDongCd)) return json(409, errBody('DUPLICATE_FAVORITE_REGION', '이미 등록된 지역입니다'));
      const id = db.nextRegionId++;
      const fullPath = Object.values(AUTOCOMPLETE).flat().find((r) => r.legalDongCd === legalDongCd)?.fullPath ?? legalDongCd;
      const parts = fullPath.split(' ');
      db.regions.push({
        favoriteRegionId: id, legalDongCd, fullPath, sidoName: parts[0], sigunguName: parts.slice(1, -1).join(' '),
        eupmyeondongName: parts.at(-1), registeredAt: '2026-10-02T12:00:00', avgPrice: null, changeRate: null, pricePerPyeong: null, newTradeCount: 0,
      });
      return json(200, okBody({ favoriteRegionId: id, legalDongCd, fullPath, registeredAt: '2026-10-02T12:00:00' }));
    }
    if (path.startsWith('/api/complexes/') || path.startsWith('/api/trades')) return json(404, errBody('COMPLEX_NOT_FOUND', '존재하지 않는 단지입니다'));
    return json(200, okBody([]));
  });
  const page = await context.newPage();
  if (clock) await page.clock.install();
  await page.goto(`${BASE}/favorites${query}`);
  await page.getByRole('heading', { level: 1, name: /관심 매물·지역/ }).waitFor({ timeout: 10000 });
  return { context, page, calls, db, releaseHold };
}

const deletes = (calls) => calls.filter((c) => c.method === 'DELETE');
const cardNames = async (page) => page.locator('li[data-favorite-key^="property:"] h3').allInnerTexts();
async function waitFor(fn, timeout = 5000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    if (await fn()) return true;
    await new Promise((r) => setTimeout(r, 50));
  }
  return false;
}
async function advance(page, ms) {
  for (let t = 0; t < ms; t += 1000) await page.clock.runFor(Math.min(1000, ms - t));
}
async function showRegions(page, isDesktop) {
  if (!isDesktop) await page.getByRole('tab', { name: /관심 지역/ }).click();
}

async function scenarioRender(label, viewport, isDesktop) {
  const { context, page } = await open(viewport);
  await page.getByText('래미안 원베일리').waitFor({ timeout: 5000 });
  // 배지는 알림 설정 목록을 받은 뒤에 나타난다.
  await page.getByRole('link', { name: /알림 조건/ }).first().waitFor({ timeout: 5000 });
  // MY-02는 모바일에서만 공용 앱 바를 쓴다(태블릿은 페이지 제목).
  if (label === '모바일') {
    ok(`${label} 1: 상단 앱 바 높이 52px`, await page.getByRole('heading', { level: 1 }).evaluate((h) => h.parentElement.getBoundingClientRect().height) === 52);
  }
  if (isDesktop) {
    ok(`${label} 1: 세로 섹션(관심 매물·관심 지역·추가)`, (await page.getByRole('heading', { level: 2, name: /관심 매물/ }).isVisible())
      && (await page.getByRole('heading', { level: 2, name: /관심 지역$|관심 지역\s*\d/ }).first().isVisible())
      && (await page.getByRole('heading', { level: 2, name: '관심 지역 추가' }).isVisible()));
    ok(`${label} 1: 정렬 chip(radiogroup)`, (await page.getByRole('radio', { name: '변동률순' }).isVisible()) && (await page.getByRole('tablist').count()) === 0);
    ok(`${label} 1: 섹션 개수`, (await page.getByRole('heading', { level: 2, name: /관심 매물/ }).innerText()).replace(/\s/g, '') === '관심매물4');
  } else {
    ok(`${label} 1: 탭 + 정렬 드롭다운`, (await page.getByRole('tab', { name: /관심 매물/ }).getAttribute('aria-selected')) === 'true'
      && (await page.getByRole('combobox', { name: '정렬' }).isVisible()) && (await page.getByRole('radio').count()) === 0);
    ok(`${label} 1: 지역 목록은 탭 전환 전엔 렌더되지 않음`, (await page.getByText('금광면').count()) === 0);
  }
  const first = page.locator('li[data-favorite-key="property:1"]');
  const text = (await first.innerText()).replace(/\s+/g, ' ');
  ok(`${label} 1: 매물 카드(면적·층·가격·변동률·배지·등록일)`, text.includes('85㎡ · 9층') && text.includes('42억 5,000만원')
    && text.includes('▲ 3.2%') && text.includes('±5% · 신규거래') && text.includes('등록일 2026.09.01'), text);
  ok(`${label} 1: 변동률 스크린리더 문장`, (await first.locator('.sr-only').allInnerTexts()).some((t) => t.includes('직전 1개월 대비 3.2% 상승')));
  const t2 = await page.locator('li[data-favorite-key="property:2"]').innerText();
  const t3 = await page.locator('li[data-favorite-key="property:3"]').innerText();
  const t4 = await page.locator('li[data-favorite-key="property:4"]').innerText();
  ok(`${label} 1: 배지 문구(임계치만·설정 없음·꺼짐)`, t2.includes('±2.5% 알림') && t3.includes('알림 설정') && t4.includes('이메일 꺼짐'));
  const bodyText = await page.locator('body').innerText();
  ok(`${label} 1: 빠진 필드(null 생략)가 NaN·undefined로 보이지 않음`, !/NaN|undefined/.test(bodyText), bodyText.match(/.{0,20}(NaN|undefined).{0,20}/)?.[0]);
  ok(`${label} 1: 하락·변동없음·거래 없음`, t2.includes('▼ 1.1%') && t4.includes('변동없음') && t3.includes('거래 없음') && t3.includes('—'));
  const registeredOrder = await cardNames(page);
  ok(`${label} 1: 등록순(기본) 정렬`, JSON.stringify(registeredOrder) === JSON.stringify(['아크로리버파크', '반포자이', '래미안 원베일리', '신반포센트럴자이']), JSON.stringify(registeredOrder));
  // Figma: 데스크톱 60×46, 태블릿 90×70, 모바일 64×64 — 모든 크기에 있다.
  const thumb = await first.getByTestId('favorite-thumbnail').boundingBox();
  const expectedThumb = isDesktop ? [60, 46] : viewport.width >= 768 ? [90, 70] : [64, 64];
  ok(`${label} 1: 썸네일 크기 ${expectedThumb.join('×')}`, thumb && Math.round(thumb.width) === expectedThumb[0] && Math.round(thumb.height) === expectedThumb[1],
    JSON.stringify(thumb));
  if (viewport.width < 768) {
    // 라벨 열: "직전 1개월 대비"가 한 줄에 들어가고 두 줄의 값 시작 위치가 같다.
    const labelBox = await first.getByText('직전 1개월 대비', { exact: true }).boundingBox();
    const priceBox = await first.getByText('42억 5,000만원').boundingBox();
    const rateBox = await first.getByText('▲ 3.2%').boundingBox();
    ok(`${label} 1: 모바일 라벨 한 줄, 값 시작 위치 정렬`, labelBox.height < 20 && Math.abs(priceBox.x - rateBox.x) < 1,
      `label h=${labelBox.height} price x=${priceBox.x} rate x=${rateBox.x}`);
    // 삭제 버튼: 보이는 크기는 작지만 위아래 20px 지점도 같은 버튼이 받는다(터치 영역 44px 이상).
    const hit = await first.getByRole('button', { name: '래미안 원베일리 관심 매물 삭제' }).evaluate((button) => {
      const rect = button.getBoundingClientRect();
      const cx = rect.left + rect.width / 2;
      const cy = rect.top + rect.height / 2;
      const at = (x, y) => document.elementFromPoint(x, y)?.closest('button') === button;
      return { h: rect.height, ok: at(cx, cy - 20) && at(cx, cy + 20) && at(rect.left - 4, cy) && at(rect.right + 4, cy) };
    });
    ok(`${label} 1: 모바일 삭제 터치 영역 44px 이상(보이는 높이 ${hit.h}px)`, hit.ok);
    const deleteColor = await first.getByRole('button', { name: '래미안 원베일리 관심 매물 삭제' }).evaluate((b) => getComputedStyle(b).color);
    ok(`${label} 1: 모바일 삭제 글자색 #6a7282`, deleteColor === 'rgb(106, 114, 130)', deleteColor);
  }

  if (isDesktop) await page.getByRole('radio', { name: '변동률순' }).click();
  else await page.getByRole('combobox', { name: '정렬' }).selectOption('rate');
  const expectedRate = JSON.stringify(['래미안 원베일리', '신반포센트럴자이', '아크로리버파크', '반포자이']);
  await waitFor(async () => JSON.stringify(await cardNames(page)) === expectedRate);
  const rateOrder = await cardNames(page);
  ok(`${label} 1: 변동률순(null 맨 뒤) + URL sort=rate`, JSON.stringify(rateOrder) === JSON.stringify(['래미안 원베일리', '신반포센트럴자이', '아크로리버파크', '반포자이'])
    && new URL(page.url()).searchParams.get('sort') === 'rate', `${JSON.stringify(rateOrder)} ${page.url()}`);

  await showRegions(page, isDesktop);
  await page.getByText('금광면').waitFor({ timeout: 5000 });
  const r = (await page.locator('li[data-favorite-key="region:11"]').innerText()).replace(/\s+/g, ' ');
  ok(`${label} 1: 지역 카드(시군구·3.3㎡당·신규거래·변동률)`, r.includes('경기도 안성시') && r.includes('5,123만원') && r.includes('최근 1개월 신규거래 4건') && r.includes('▲ 1.5%'), r);
  if (!isDesktop) {
    ok(`${label} 1: 탭 URL ?tab=regions, 추가 입력이 탭 맨 위`, new URL(page.url()).searchParams.get('tab') === 'regions'
      && (await page.getByRole('combobox', { name: /관심 지역 추가/ }).boundingBox()).y < (await page.locator('li[data-favorite-key]').first().boundingBox()).y);
    await page.getByRole('tab', { name: /관심 매물/ }).click();
    await waitFor(async () => (await page.locator('li[data-favorite-key^="region:"]').count()) === 0);
    const leftover = await page.locator('li[data-favorite-key^="region:"]').count();
    ok(`${label} 1: 관심 매물 탭으로 돌아가면 지역 목록이 사라짐`, leftover === 0 && (await page.locator('li[data-favorite-key^="property:"]').count()) === 4, `regions=${leftover}`);
  }
  const sw = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  ok(`${label} 7: 가로 스크롤 없음`, sw <= 0, `+${sw}px`);
  await context.close();
}

async function scenarioEmpty(label, viewport) {
  const { context, page } = await open(viewport, { empty: true });
  await page.getByText('아직 등록한 관심 매물·지역이 없어요').waitFor({ timeout: 5000 });
  ok(`${label} 2: 빈 상태 + 매물 둘러보기(→ /)`, (await page.getByRole('link', { name: '매물 둘러보기' }).getAttribute('href')) === '/');
  ok(`${label} 2: 빈 상태에서도 지역 추가 입력`, await page.getByRole('combobox', { name: /관심 지역 추가/ }).isVisible());
  await context.close();
}

// 알림 설정 조회만 실패 → 목록은 그대로, 배지만 숨긴다("알림 설정"으로 보이면 설정이 있는 사용자에게 틀린 정보).
async function scenarioSettingsFail(label, viewport) {
  const { context, page, calls } = await open(viewport, { settingsMode: 'fail' });
  await page.getByText('래미안 원베일리').waitFor({ timeout: 5000 });
  await waitFor(async () => calls.some((c) => c.path === '/api/notifications/settings'));
  await page.waitForTimeout(300);
  const items = await page.locator('li[data-favorite-key^="property:"]').count();
  const badges = await page.getByRole('link', { name: /알림 조건/ }).count();
  const listText = await page.locator('li[data-favorite-key^="property:"]').allInnerTexts();
  ok(`${label} 8: 알림 설정 조회 실패 → 목록 4건 표시, 배지 없음, "알림 설정" 문구 없음`, items === 4 && badges === 0
    && !listText.some((t) => t.includes('알림 설정') || t.includes('이메일 꺼짐') || t.includes('알림 꺼짐')), `items=${items} badges=${badges}`);
  ok(`${label} 8: 매물 정보는 그대로(가격·변동률)`, listText.join(' ').includes('42억 5,000만원') && listText.join(' ').includes('3.2%'));
  await context.close();
}

async function scenarioDelete(label, viewport, isDesktop) {
  // 취소 / 실행취소 / 5초 뒤 DELETE
  const { context, page, calls } = await open(viewport, { clock: true });
  await page.getByText('래미안 원베일리').waitFor({ timeout: 5000 });
  await page.getByRole('button', { name: '래미안 원베일리 관심 매물 삭제' }).click();
  const dialog = page.getByRole('alertdialog', { name: '삭제하시겠어요?' });
  ok(`${label} 3: 확인 다이얼로그 문구`, (await dialog.innerText()).includes('래미안 원베일리을(를) 삭제하면 해당 항목의 알림도 함께 중지됩니다.'));
  // Figma 6-4995(1280px 이상) / 29-16508·28-15713(그 아래): 폭·안쪽 여백·그림자·아이콘 상자·제목 크기가 다르다.
  const look = await dialog.evaluate((el) => {
    const style = getComputedStyle(el);
    const icon = el.querySelector('[data-testid="dialog-icon"]');
    const title = el.querySelector('h2');
    return {
      width: el.getBoundingClientRect().width, padding: style.paddingTop, shadow: style.boxShadow,
      iconSize: icon?.getBoundingClientRect().width, iconRadius: icon ? getComputedStyle(icon).borderRadius : null,
      titleSize: title ? getComputedStyle(title).fontSize : null,
    };
  });
  const expectLook = isDesktop
    ? { width: 400, padding: '32px', shadow: '0px 16px 48px', iconSize: 56, titleSize: '18px' }
    : { width: 320, padding: '24px', shadow: '0px 20px 60px', iconSize: 48, titleSize: '16px' };
  ok(`${label} 3: 다이얼로그 크기(폭 ${expectLook.width}, 여백 ${expectLook.padding}, 그림자, 아이콘 ${expectLook.iconSize}, 제목 ${expectLook.titleSize})`,
    Math.round(look.width) === expectLook.width && look.padding === expectLook.padding && look.shadow.includes(expectLook.shadow)
    && Math.round(look.iconSize) === expectLook.iconSize && look.titleSize === expectLook.titleSize, JSON.stringify(look));
  // 확인 다이얼로그 버튼은 모든 크기(390 포함)에서 가로 — 같은 줄, 취소 왼쪽·삭제 오른쪽, 폭을 나눠 가짐(Figma 28-15713).
  const cancelBox = await dialog.getByRole('button', { name: '취소' }).boundingBox();
  const confirmBox = await dialog.getByRole('button', { name: '삭제' }).boundingBox();
  ok(`${label} 3: 확인 다이얼로그 버튼 가로 배치(${viewport.width}px)`, Math.abs(cancelBox.y - confirmBox.y) < 1 && cancelBox.x < confirmBox.x
    && Math.abs(cancelBox.width - confirmBox.width) < 1, JSON.stringify({ cancelBox, confirmBox }));
  await dialog.getByRole('button', { name: '취소' }).click();
  ok(`${label} 3: 취소 → 그대로, 요청 없음`, (await page.locator('li[data-favorite-key="property:1"]').count()) === 1 && deletes(calls).length === 0);

  await page.getByRole('button', { name: '래미안 원베일리 관심 매물 삭제' }).click();
  await dialog.getByRole('button', { name: '삭제' }).click();
  ok(`${label} 3: 확인 → 곧바로 숨김 + 토스트, 개수 갱신`, (await page.locator('li[data-favorite-key="property:1"]').count()) === 0
    && (await page.getByText('삭제했어요').isVisible())
    && (!isDesktop ? (await page.getByRole('tab', { name: /관심 매물/ }).innerText()).includes('3') : (await page.getByRole('heading', { level: 2, name: /관심 매물/ }).innerText()).includes('3')));
  await page.getByRole('button', { name: '실행취소' }).click();
  await advance(page, 7000);
  ok(`${label} 3: 실행취소 → 되살림, DELETE 없음`, (await page.locator('li[data-favorite-key="property:1"]').count()) === 1 && deletes(calls).length === 0);

  await page.getByRole('button', { name: '래미안 원베일리 관심 매물 삭제' }).click();
  await dialog.getByRole('button', { name: '삭제' }).click();
  await advance(page, 4000);
  ok(`${label} 3: 4초까지는 DELETE 없음`, deletes(calls).length === 0);
  await advance(page, 1000);
  await waitFor(async () => deletes(calls).length > 0);
  ok(`${label} 3: 5초 뒤 DELETE 1회(/properties/1)`, deletes(calls).length === 1 && deletes(calls)[0].path === '/api/favorites/properties/1');
  await advance(page, 6000);
  ok(`${label} 3: 확정 뒤 다시 나타나지 않음`, (await page.locator('li[data-favorite-key="property:1"]').count()) === 0 && deletes(calls).length === 1);

  // 대기 중 다른 항목 삭제 → 앞 건 즉시 확정, 토스트 하나
  await page.getByRole('button', { name: '아크로리버파크 관심 매물 삭제' }).click();
  await dialog.getByRole('button', { name: '삭제' }).click();
  await page.getByRole('button', { name: '반포자이 관심 매물 삭제' }).click();
  await dialog.getByRole('button', { name: '삭제' }).click();
  await waitFor(async () => deletes(calls).length === 2);
  ok(`${label} 3: 연속 삭제 → 앞 건 즉시 DELETE, 토스트 1개`, deletes(calls).length === 2 && deletes(calls)[1].path === '/api/favorites/properties/2'
    && (await page.getByText('삭제했어요').count()) === 1);

  // 대기 중 화면 이동 → 즉시 DELETE
  await page.locator('li[data-favorite-key="property:4"] h3 a').click();
  await waitFor(async () => deletes(calls).length === 3);
  ok(`${label} 3: 대기 중 다른 화면 이동 → 즉시 DELETE`, deletes(calls).length === 3 && deletes(calls)[2].path === '/api/favorites/properties/3'
    && new URL(page.url()).pathname === '/complexes/40');
  await context.close();

  // 실패 → 되살림 + 서버 문구 / 404 → 성공
  const failing = await open(viewport, { clock: true, deleteMode: 'fail' });
  await failing.page.getByText('래미안 원베일리').waitFor({ timeout: 5000 });
  await failing.page.getByRole('button', { name: '래미안 원베일리 관심 매물 삭제' }).click();
  await failing.page.getByRole('alertdialog').getByRole('button', { name: '삭제' }).click();
  await advance(failing.page, 5000);
  ok(`${label} 3: DELETE 실패 → 되살림 + 서버 문구`, await waitFor(async () => (await failing.page.locator('li[data-favorite-key="property:1"]').count()) === 1
    && (await failing.page.getByText('삭제하지 못했습니다. 잠시 후 다시 시도해주세요').count()) === 1));
  await failing.context.close();

  const gone = await open(viewport, { clock: true, deleteMode: 'notFound' });
  await gone.page.getByText('래미안 원베일리').waitFor({ timeout: 5000 });
  await gone.page.getByRole('button', { name: '래미안 원베일리 관심 매물 삭제' }).click();
  await gone.page.getByRole('alertdialog').getByRole('button', { name: '삭제' }).click();
  await advance(gone.page, 5000);
  await waitFor(async () => deletes(gone.calls).length === 1);
  await gone.page.waitForTimeout(200);
  ok(`${label} 3: 404(이미 삭제) → 성공 처리`, (await gone.page.locator('li[data-favorite-key="property:1"]').count()) === 0
    && (await gone.page.getByText('존재하지 않는 관심 등록입니다').count()) === 0);
  await gone.context.close();
}

async function scenarioAddRegion(label, viewport, isDesktop) {
  const { context, page, calls } = await open(viewport, { query: '?sort=rate' });
  await page.getByText('래미안 원베일리').waitFor({ timeout: 5000 });
  await showRegions(page, isDesktop);
  const input = page.getByRole('combobox', { name: /관심 지역 추가/ });
  const addButton = page.getByRole('button', { name: '추가' });

  await input.fill('기장');
  ok(`${label} 4: 리 단위만 있으면 "지역을 찾을 수 없습니다"`, await page.getByText('지역을 찾을 수 없습니다').waitFor({ timeout: 3000 }).then(() => true, () => false)
    && (await page.getByRole('listbox').count()) === 0);

  await input.fill('금광');
  await page.getByRole('listbox', { name: '관심 지역 후보' }).waitFor({ timeout: 3000 });
  const options = await page.getByRole('listbox', { name: '관심 지역 후보' }).getByRole('option').allInnerTexts();
  ok(`${label} 4: 후보는 읍면동만(리 제외)`, JSON.stringify(options) === JSON.stringify(['경기도 안성시 금광면']), JSON.stringify(options));
  ok(`${label} 4: 선택 전 "추가" 비활성`, await addButton.isDisabled());
  await page.getByRole('option', { name: '경기도 안성시 금광면' }).click();
  ok(`${label} 4: 이미 등록된 지역 → 안내, 추가 비활성, 요청 없음`, (await page.getByText('이미 등록된 지역입니다').isVisible()) && (await addButton.isDisabled())
    && calls.filter((c) => c.method === 'POST').length === 0);

  await input.fill('역삼');
  await page.getByRole('option', { name: '서울특별시 강남구 역삼동' }).click();
  ok(`${label} 4: 선택하면 "추가" 활성`, !(await addButton.isDisabled()));
  await addButton.click();
  await page.getByText('관심 지역에 추가했어요').waitFor({ timeout: 5000 });
  const post = calls.find((c) => c.method === 'POST' && c.path === '/api/favorites/regions');
  ok(`${label} 4: POST 본문 legalDongCd`, post && JSON.parse(post.body).legalDongCd === '1168010100');
  const regionNames = await page.locator('li[data-favorite-key^="region:"] h3').allInnerTexts();
  ok(`${label} 4: 새 지역이 맨 위, 정렬 등록순 복귀`, regionNames[0] === '역삼동' && new URL(page.url()).searchParams.get('sort') === null, JSON.stringify(regionNames));
  ok(`${label} 4: 입력 초기화`, (await input.inputValue()) === '');
  await context.close();
}

// 4-1. 입력을 바꾼 직후(다음 응답 전) 이전 검색어의 후보를 고를 수 없다(Codex P2). "역삼동" 응답을 붙잡아 요청 중 상태를 만든다.
async function scenarioStaleCandidate(label, viewport, isDesktop) {
  const { context, page, calls, releaseHold } = await open(viewport, { holdQuery: '역삼동' });
  await page.getByText('래미안 원베일리').waitFor({ timeout: 5000 });
  await showRegions(page, isDesktop);
  const input = page.getByRole('combobox', { name: /관심 지역 추가/ });
  await input.fill('역삼');
  await page.getByRole('option', { name: '서울특별시 강남구 역삼동' }).waitFor({ timeout: 3000 });
  await input.press('ArrowDown');
  ok(`${label} 4-1: 바꾸기 전 활성 후보 있음`, Boolean(await input.getAttribute('aria-activedescendant')));

  await input.fill('역삼동');
  // 후보 목록으로 범위를 좁힌다 — 태블릿·모바일의 정렬 <select>의 <option>도 option 역할이라 페이지 전체로 세면 안 된다.
  ok(`${label} 4-1: 입력을 바꾼 직후 이전 후보가 보이지 않음(클릭 불가)`,
    (await page.getByRole('listbox', { name: '관심 지역 후보' }).count()) === 0
    && (await page.getByRole('option', { name: '서울특별시 강남구 역삼동' }).count()) === 0);
  ok(`${label} 4-1: 활성 후보 초기화`, (await input.getAttribute('aria-activedescendant')) === null);
  ok(`${label} 4-1: 요청 중에는 "지역을 찾을 수 없습니다" 없음`, (await page.getByText('지역을 찾을 수 없습니다').count()) === 0);
  await input.press('Enter');
  ok(`${label} 4-1: Enter로 이전 후보가 선택되지 않음`, (await input.inputValue()) === '역삼동'
    && (await page.getByRole('button', { name: '추가' }).isDisabled()) && calls.every((c) => c.method !== 'POST'));

  releaseHold();
  ok(`${label} 4-1: 응답이 오면 지금 검색어의 후보가 보임`, await page.getByRole('option', { name: '서울특별시 강남구 역삼동' })
    .waitFor({ timeout: 3000 }).then(() => true, () => false));
  await context.close();
}

async function scenarioNavigation(label, viewport, isDesktop) {
  const { context, page } = await open(viewport);
  await page.getByText('래미안 원베일리').waitFor({ timeout: 5000 });
  ok(`${label} 5: 매물 → DTL-01`, (await page.locator('li[data-favorite-key="property:1"] h3 a').getAttribute('href')) === '/complexes/10');
  ok(`${label} 5: 배지 → MY-03 ?favoritePropertyId=`, (await page.getByRole('link', { name: '래미안 원베일리 알림 조건: ±5% · 신규거래' }).getAttribute('href'))
    === '/notifications/settings?favoritePropertyId=1');
  await showRegions(page, isDesktop);
  const href = await page.getByRole('link', { name: '경기도 안성시 금광면 매물 검색' }).getAttribute('href');
  const params = new URLSearchParams(href.split('?')[1]);
  ok(`${label} 5: 지역 → SRCH-01 regionCode`, href.startsWith('/search?') && params.get('regionCode') === '4155036000' && params.get('regionLabel') === '경기도 안성시 금광면', href);
  // 카드 빈 곳을 눌러도 이동(::after가 카드 전체를 덮는다), 삭제 버튼은 이동하지 않음
  await page.getByRole('button', { name: '금광면 관심 지역 삭제' }).click();
  ok(`${label} 5: 삭제 버튼은 카드 링크로 이동하지 않음`, (await page.getByRole('alertdialog').isVisible()) && new URL(page.url()).pathname === '/favorites');
  await page.keyboard.press('Escape');
  const box = await page.locator('li[data-favorite-key="region:11"]').boundingBox();
  await page.mouse.click(box.x + box.width / 2, box.y + 12);
  ok(`${label} 5: 카드 빈 곳 클릭 → SRCH-01`, await waitFor(async () => new URL(page.url()).pathname === '/search'));
  if (!isDesktop && viewport.width < 768) {
    await page.goto(`${BASE}/favorites`);
    await page.getByRole('heading', { level: 1, name: /관심 매물·지역/ }).waitFor();
    const tab = page.locator('nav').getByRole('link', { name: '찜' });
    ok(`${label} 5: 하단 탭 "찜" 활성`, (await tab.getAttribute('class')).includes('text-brand'));
  }
  await context.close();
}

async function scenarioKeyboard(label, viewport, isDesktop) {
  const { context, page, calls } = await open(viewport);
  await page.getByText('래미안 원베일리').waitFor({ timeout: 5000 });
  // 등록순: 아크로리버파크, 반포자이, 래미안 원베일리, 신반포센트럴자이 → 반포자이를 지우면 포커스는 래미안 원베일리로
  await page.getByRole('button', { name: '반포자이 관심 매물 삭제' }).focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('alertdialog');
  await dialog.waitFor();
  ok(`${label} 6: 다이얼로그 초기 포커스 = 삭제`, await page.evaluate(() => document.activeElement?.textContent?.trim() === '삭제'));
  await page.keyboard.press('Enter');
  ok(`${label} 6: 삭제 뒤 포커스 → 다음 항목`, await waitFor(async () => page.evaluate(() => document.activeElement?.textContent?.trim() === '래미안 원베일리')));
  let reached = false;
  for (let i = 0; i < 80 && !reached; i++) {
    await page.keyboard.press('Tab');
    reached = await page.evaluate(() => document.activeElement?.textContent?.trim() === '실행취소');
  }
  ok(`${label} 6: Tab으로 실행취소 도달`, reached);
  await page.keyboard.press('Enter');
  ok(`${label} 6: 키보드 실행취소 → 되살림, 포커스 복귀`, await waitFor(async () => (await page.locator('li[data-favorite-key="property:3"]').count()) === 1
    && page.evaluate(() => document.activeElement?.textContent?.trim() === '반포자이')));
  ok(`${label} 6: DELETE 없음`, deletes(calls).length === 0);

  await showRegions(page, isDesktop);
  const input = page.getByRole('combobox', { name: /관심 지역 추가/ });
  await input.focus();
  await page.keyboard.type('역삼');
  await page.getByRole('listbox').waitFor({ timeout: 3000 });
  await page.keyboard.press('ArrowDown');
  ok(`${label} 6: 화살표 → aria-activedescendant`, Boolean(await input.getAttribute('aria-activedescendant')));
  await page.keyboard.press('Enter');
  ok(`${label} 6: Enter로 후보 선택`, (await input.inputValue()) === '서울특별시 강남구 역삼동');
  await page.keyboard.press('Enter');
  ok(`${label} 6: Enter로 추가`, await waitFor(async () => calls.some((c) => c.method === 'POST')));
  await context.close();
}

// VIEWPORTS=데스크톱,모바일 처럼 일부만 돌릴 수 있다(브라우저가 불안정한 환경에서 뷰포트별로 나눠 실행할 때).
const only = process.env.VIEWPORTS?.split(',');
for (const [label, viewport, isDesktop] of [
  ['데스크톱', { width: 1280, height: 900 }, true],
  ['태블릿', { width: 768, height: 1000 }, false],
  ['모바일', { width: 390, height: 844 }, false],
].filter(([name]) => !only || only.includes(name))) {
  await scenarioRender(label, viewport, isDesktop);
  await scenarioEmpty(label, viewport);
  await scenarioSettingsFail(label, viewport);
  await scenarioDelete(label, viewport, isDesktop);
  await scenarioAddRegion(label, viewport, isDesktop);
  await scenarioStaleCandidate(label, viewport, isDesktop);
  await scenarioNavigation(label, viewport, isDesktop);
  await scenarioKeyboard(label, viewport, isDesktop);
}

await browser.close();
console.log(`\n${pass}/${pass + fail} passed`);
process.exit(fail === 0 ? 0 : 1);
