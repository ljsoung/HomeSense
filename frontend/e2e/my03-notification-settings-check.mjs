// MY-03 알림 설정 — 모바일(390)·태블릿(768)·데스크톱(1280). 백엔드 불필요(route로 상태 있는 가짜 서버).
// 1. 렌더: 제목, 대상 6행(매물 4 + 지역 2)과 행 보조 텍스트(MY-02 배지 문구·미설정·이메일 꺼짐), 미선택 시 입력 비활성·저장 비활성·안내
//    데스크톱은 임계치 값 텍스트, 태블릿·모바일은 숫자 입력. 이메일 주소 표시, 웹 푸시 비활성. 모바일 하단 탭 "마이" 활성. 가로 스크롤 없음
// 2. 진입 쿼리: ?favoritePropertyId= 미리 선택·화면 안으로 스크롤, 정수 아닌 저장값 그대로, 모르는 id 무시
// 3. 0%: 배너(role=status)·요약 문구
// 4. 다중 선택: 대상마다 다름 표시, "선택한 대상 n곳에 같은 설정이 적용됩니다", 입력값 유지, 저장 요청 본문, 저장 후 변경 없음 상태·선택 유지
// 5. 저장 실패: role=alert + 서버 문구, 입력 유지, 다시 시도 → 성공
// 6. 빈 상태 → "관심 목록으로 가기"(/favorites)
// 7. 숫자 입력 보정(태블릿·모바일): 25 → 저장 비활성 → blur 20, 빈 값 → 직전 값
// 8. 페이지 오류: 설정 조회 실패 → 오류 + 다시 시도. 내 정보만 실패 → 이메일 주소만 숨김
// 9. MY-02 배지 → MY-03 미리 선택, MY-01 메뉴 링크
// 10. 키보드만으로 대상 선택 → 임계치 → 저장(데스크톱)
// 11. 비로그인 → 로그인 → 쿼리 그대로 복귀
// 12. 저장 중(PUT 응답 전) 입력 잠금 → 응답 뒤 다시 열림
import { chromium } from 'playwright';
import { BASE } from './base.mjs';
import { errBody, okBody } from './mockApi.mjs';

let pass = 0;
let fail = 0;
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); }
}

const USER = { userId: 7, email: 'jiseong@homesense.kr', nickname: '지성', createdAt: '2026-09-15T10:20:30' };

function property(id, name, registeredAt, hasNotificationSetting = false) {
  return {
    favoritePropertyId: id, complexId: id * 10, complexName: name, sido: '서울특별시', sigungu: '서초구', dongRi: '반포동',
    housingType: 'APT', registeredAt, recentDealCategory: 'SALE', recentDealDate: '2026-09-20', recentAmount: 300000,
    recentArea: 84.98, recentFloor: 9, changeRate: 1.2, hasNotificationSetting,
  };
}
function region(id, code, sigungu, name, registeredAt) {
  return {
    favoriteRegionId: id, legalDongCd: code, fullPath: `경기도 ${sigungu} ${name}`, sidoName: '경기도', sigunguName: sigungu,
    eupmyeondongName: name, registeredAt, avgPrice: 90000, changeRate: null, pricePerPyeong: 5000, newTradeCount: 2,
  };
}
// 등록순(최근 먼저): 매물 4 → 2 → 3 → 1, 지역 12 → 11.
const PROPERTIES = [
  property(1, '래미안 원베일리', '2026-09-01T10:00:00', true),
  property(2, '아크로리버파크', '2026-09-03T10:00:00', true),
  property(3, '반포자이', '2026-09-02T10:00:00'),
  property(4, '신반포센트럴자이', '2026-09-04T10:00:00', true),
];
const REGIONS = [
  region(11, '4155036000', '안성시', '금광면', '2026-09-02T09:00:00'),
  region(12, '4111113500', '수원시 장안구', '파장동', '2026-09-04T09:00:00'),
];
// 매물 1: 5%·신규거래·이메일 / 매물 2: 2.5%(옛 API로 저장된 정수 아닌 값)·신규거래 끔 / 매물 4: 이메일 꺼짐 / 매물 3·지역: 미설정
const initialSettings = () => [
  { notificationSettingId: 1, favoritePropertyId: 1, priceChangeThresholdPct: 5, newTradeAlertYn: true, emailAlertYn: true },
  { notificationSettingId: 2, favoritePropertyId: 2, priceChangeThresholdPct: 2.5, newTradeAlertYn: false, emailAlertYn: true },
  { notificationSettingId: 3, favoritePropertyId: 4, priceChangeThresholdPct: 5, newTradeAlertYn: true, emailAlertYn: false },
];

const browser = await chromium.launch();

/** putMode: 'ok'|'fail'|'failOnce'. settingsMode: 'ok'|'fail'. meMode: 'ok'|'fail'(세션 복원 뒤 화면의 내 정보 조회만 실패). */
async function open(viewport, { query = '', empty = false, putMode = 'ok', settingsMode = 'ok', meMode = 'ok', loggedIn = true, path = '/notifications/settings' } = {}) {
  const context = await browser.newContext({ viewport });
  if (loggedIn) {
    await context.addInitScript(() => {
      if (!sessionStorage.getItem('__seeded')) {
        localStorage.setItem('homesense.accessToken', 'A');
        localStorage.setItem('homesense.refreshToken', 'R');
        sessionStorage.setItem('__seeded', '1');
      }
    });
  }
  const db = { settings: initialSettings(), nextId: 100, putFailures: putMode === 'failOnce' ? 1 : putMode === 'fail' ? Infinity : 0 };
  // putMode 'hold': PUT 응답을 releasePut()까지 붙잡는다(저장 중 상태를 만든다).
  let releasePut = () => {};
  const putHeld = new Promise((resolve) => { releasePut = resolve; });
  const calls = [];
  await context.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const p = url.pathname;
    const method = request.method();
    calls.push({ method, path: p, body: request.postData() });
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body });
    if (p === '/api/auth/login') return json(200, okBody({ accessToken: 'A2', refreshToken: 'R2', expiresIn: 1800 }));
    if (p === '/api/users/me') {
      // 세션 복원도 /me를 부른다 — 첫 호출(복원)은 성공시키고 화면의 내 정보 조회만 실패시킨다.
      db.meCalls = (db.meCalls ?? 0) + 1;
      if (meMode === 'fail' && db.meCalls > 1) return json(500, errBody('INTERNAL_SERVER_ERROR', '프로필 서버 오류입니다'));
      return json(200, okBody(USER));
    }
    if (p === '/api/favorites/properties') return json(200, okBody(empty ? [] : PROPERTIES));
    if (p === '/api/favorites/regions') return json(200, okBody(empty ? [] : REGIONS));
    if (p === '/api/notifications/settings' && method === 'GET') {
      if (settingsMode === 'fail') return json(500, errBody('INTERNAL_SERVER_ERROR', '알림 설정을 불러오지 못했습니다'));
      return json(200, okBody(empty ? [] : db.settings));
    }
    if (p === '/api/notifications/settings' && method === 'PUT') {
      if (putMode === 'hold') await putHeld;
      if (db.putFailures > 0) {
        db.putFailures--;
        return json(500, errBody('INTERNAL_SERVER_ERROR', '알림 설정을 저장하지 못했습니다. 잠시 후 다시 시도해주세요'));
      }
      const { settings } = JSON.parse(request.postData() ?? '{}');
      for (const item of settings) {
        const existing = db.settings.find((s) =>
          item.favoritePropertyId != null ? s.favoritePropertyId === item.favoritePropertyId : s.favoriteRegionId === item.favoriteRegionId);
        const values = { priceChangeThresholdPct: item.priceChangeThresholdPct, newTradeAlertYn: item.newTradeAlertYn, emailAlertYn: item.emailAlertYn };
        if (existing) Object.assign(existing, values);
        else db.settings.push({ notificationSettingId: db.nextId++, favoritePropertyId: item.favoritePropertyId, favoriteRegionId: item.favoriteRegionId, ...values });
      }
      return json(200, okBody(null));
    }
    if (p === '/api/notifications') return json(200, okBody([], { page: 0, size: 3, totalElements: 0, totalPages: 0 }));
    return json(200, okBody([]));
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto(`${BASE}${path}${query}`);
  return { context, page, calls, db, errors, releasePut };
}

const ready = (page) => page.getByRole('heading', { level: 2, name: '대상 선택' }).waitFor({ timeout: 10000 }).then(() => true, () => false);
const row = (page, key) => page.locator(`label[data-target-key="${key}"]`);
const rowBox = (page, key) => row(page, key).locator('input[type="checkbox"]');
const saveButton = (page) => page.getByRole('button', { name: /^(저장|저장 중…)$/ });
const slider = (page) => page.getByRole('slider', { name: '변동 임계치' });
const puts = (calls) => calls.filter((c) => c.method === 'PUT' && c.path === '/api/notifications/settings');
const hintText = (page) => page.locator('p', { hasText: /먼저 선택하세요|변경 사항이 없으면|저장하지 않은 변경/ }).first().innerText();
async function setSlider(page, value) {
  await slider(page).evaluate((el, v) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(el, String(v));
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, value);
}
async function waitFor(fn, timeout = 5000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    if (await fn()) return true;
    await new Promise((r) => setTimeout(r, 50));
  }
  return false;
}
const noHorizontalScroll = (page) => page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth);

async function scenarioRender(label, viewport, layout) {
  const { context, page, errors } = await open(viewport);
  ok(`${label} 1: 화면 표시`, await ready(page));
  for (const text of ['이 비율 이상 변동 시 알림을 발송합니다. 기본값 5%', '대상에 신규 실거래가 확인되면 알림을 발송합니다', '알림을 받을 수단을 선택합니다']) {
    ok(`${label} 1: 카드 설명 "${text}"`, await page.getByText(text, { exact: true }).isVisible());
  }
  ok(`${label} 1: 제목 h1 "알림 설정"`, await page.getByRole('heading', { level: 1, name: '알림 설정' }).isVisible());
  const keys = await page.locator('label[data-target-key]').evaluateAll((els) => els.map((e) => e.dataset.targetKey));
  ok(`${label} 1: 대상 순서(매물 등록순 → 지역 등록순)`, JSON.stringify(keys) === JSON.stringify(
    ['property:4', 'property:2', 'property:3', 'property:1', 'region:12', 'region:11']), keys.join(','));
  ok(`${label} 1: 행 보조 텍스트 — 설정됨 ±5% · 신규거래`, (await row(page, 'property:1').innerText()).includes('±5% · 신규거래'));
  ok(`${label} 1: 행 보조 텍스트 — 이메일 꺼짐`, (await row(page, 'property:4').innerText()).includes('이메일 꺼짐'));
  ok(`${label} 1: 행 보조 텍스트 — 미설정`, (await row(page, 'property:3').innerText()).includes('미설정'));
  ok(`${label} 1: 지역 이름 "시군구 읍면동"`, (await row(page, 'region:12').innerText()).includes('수원시 장안구 파장동'));
  ok(`${label} 1: 미선택 → 저장 비활성`, await saveButton(page).isDisabled());
  ok(`${label} 1: 미선택 안내`, (await hintText(page)) === '알림을 받을 대상을 먼저 선택하세요');
  ok(`${label} 1: 미선택 → 임계치 입력 비활성(섹션은 보임)`, (await slider(page).isDisabled()) && (await slider(page).isVisible()));
  ok(`${label} 1: 미선택 → 스위치 비활성`, await page.getByRole('switch', { name: '신규거래 알림 수신' }).isDisabled());
  const numberInput = page.getByRole('textbox', { name: '변동 임계치(%)' });
  if (layout !== 'desktop') {
    ok(`${label} 1: 상단 앱 바 높이 52px`, await page.getByRole('heading', { level: 1 }).evaluate((h) => h.parentElement.getBoundingClientRect().height) === 52);
  }
  if (layout === 'desktop') ok(`${label} 1: 데스크톱은 값 텍스트(숫자 입력 없음)`, (await numberInput.count()) === 0);
  else {
    const box = await numberInput.boundingBox();
    ok(`${label} 1: 숫자 입력 52×35`, box && Math.round(box.width) === 52 && Math.round(box.height) === 35, JSON.stringify(box));
  }
  ok(`${label} 1: 이메일 주소 표시`, await page.getByText(USER.email).isVisible());
  ok(`${label} 1: 웹 푸시 비활성 + 2차 확장 예정`,
    (await page.getByText('2차 확장 예정').isVisible()) && (await page.getByRole('checkbox', { name: /웹 푸시/ }).isDisabled()));
  ok(`${label} 1: 기본 요약 문구(5%)`, (await page.getByTestId('threshold-summary').innerText()).includes('상승 5% 이상 또는 하락 5% 이상 시 알림'));
  if (layout === 'mobile') {
    const tab = page.locator('nav a', { hasText: '마이' });
    ok(`${label} 1: 하단 탭 "마이" 활성`, (await tab.getAttribute('class')).includes('text-brand'));
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    const saveBox = await saveButton(page).boundingBox();
    const navBox = await page.locator('nav.fixed').boundingBox();
    ok(`${label} 1: 맨 아래에서 저장 버튼이 하단 탭에 가리지 않음`, saveBox && navBox && saveBox.y + saveBox.height <= navBox.y,
      `${saveBox?.y + saveBox?.height} vs ${navBox?.y}`);
  }
  ok(`${label} 1: 가로 스크롤 없음`, await noHorizontalScroll(page));
  ok(`${label} 1: pageerror 없음`, errors.length === 0, errors.join(' | '));
  await context.close();
}

async function scenarioPreselect(label, viewport, layout) {
  const { context, page } = await open(viewport, { query: '?favoritePropertyId=2' });
  await ready(page);
  ok(`${label} 2: ?favoritePropertyId=2 → 미리 선택`, await rowBox(page, 'property:2').isChecked());
  ok(`${label} 2: 다른 행은 선택 안 됨`, !(await rowBox(page, 'property:1').isChecked()));
  const inView = await row(page, 'property:2').evaluate((el) => {
    const r = el.getBoundingClientRect();
    return r.top >= 0 && r.bottom <= window.innerHeight;
  });
  ok(`${label} 2: 미리 선택한 행이 화면 안`, inView);
  const shown = layout === 'desktop'
    ? await page.getByText('2.5%', { exact: true }).isVisible()
    : (await page.getByRole('textbox', { name: '변동 임계치(%)' }).inputValue()) === '2.5';
  ok(`${label} 2: 정수 아닌 저장값 2.5 그대로 표시`, shown);
  ok(`${label} 2: 저장값 그대로면 저장 비활성·변경 없음 안내`,
    (await saveButton(page).isDisabled()) && (await hintText(page)) === '변경 사항이 없으면 저장이 비활성화됩니다.');
  ok(`${label} 2: 신규거래 꺼짐 반영`, (await page.getByRole('switch', { name: '신규거래 알림 수신' }).getAttribute('aria-checked')) === 'false');
  await context.close();

  const unknown = await open(viewport, { query: '?favoritePropertyId=404&favoriteRegionId=abc' });
  await ready(unknown.page);
  ok(`${label} 2: 모르는 id·숫자 아님 → 무시(선택 없음)`,
    (await unknown.page.locator('label[data-target-key] input:checked').count()) === 0);
  ok(`${label} 2: 모르는 id → pageerror 없음`, unknown.errors.length === 0);
  await unknown.context.close();

  const regionPick = await open(viewport, { query: '?favoriteRegionId=11' });
  await ready(regionPick.page);
  ok(`${label} 2: ?favoriteRegionId=11 → 미리 선택`, await rowBox(regionPick.page, 'region:11').isChecked());
  await regionPick.context.close();
}

async function scenarioZero(label, viewport) {
  const { context, page } = await open(viewport, { query: '?favoritePropertyId=1' });
  await ready(page);
  ok(`${label} 3: 5%에서는 배너 없음`, (await page.getByText('임계치 0% 설정 확인').count()) === 0);
  await setSlider(page, 0);
  const banner = page.getByRole('status').filter({ hasText: '임계치 0% 설정 확인' });
  ok(`${label} 3: 0% → 배너(role=status)`, await banner.isVisible());
  ok(`${label} 3: 배너 본문`, (await banner.innerText()).includes('알림 빈도가 매우 높아질 수 있으니 확인 후 저장해주세요.'));
  ok(`${label} 3: 0% 요약 문구`, (await page.getByTestId('threshold-summary').innerText()).includes('가격이 조금이라도 오르거나 내리면 알림'));
  ok(`${label} 3: 슬라이더 aria-valuetext 0%`, (await slider(page).getAttribute('aria-valuetext')) === '0%');
  ok(`${label} 3: 0%도 저장 가능`, await saveButton(page).isEnabled());
  await saveButton(page).click();
  ok(`${label} 3: 0% 저장 후 행 문구 "모든 변동 · 신규거래"("±0%" 아님)`,
    await waitFor(async () => (await row(page, 'property:1').innerText()).includes('모든 변동 · 신규거래')),
    await row(page, 'property:1').innerText());
  ok(`${label} 3: 0% 저장 후에도 배너 유지(선택 유지·0%)`, await page.getByText('임계치 0% 설정 확인').isVisible());
  await rowBox(page, 'property:1').evaluate((el) => el.click());
  ok(`${label} 3: 선택을 모두 풀면 배너 숨김`, (await page.getByText('임계치 0% 설정 확인').count()) === 0);
  await context.close();
}

async function scenarioMixed(label, viewport) {
  const { context, page, calls } = await open(viewport);
  await ready(page);
  await row(page, 'property:1').click();
  await row(page, 'property:2').click();
  const mixedTags = await page.getByText('대상마다 다름', { exact: true }).count();
  ok(`${label} 4: 임계치·신규거래 "대상마다 다름"`, mixedTags === 2, String(mixedTags));
  ok(`${label} 4: 적용 안내 "선택한 대상 2곳에…"`, await page.getByText('선택한 대상 2곳에 같은 설정이 적용됩니다').isVisible());
  ok(`${label} 4: 저장 버튼이 적용 안내를 설명으로 연결`, await saveButton(page).evaluate((el) =>
    (el.getAttribute('aria-describedby') ?? '').split(' ').map((id) => document.getElementById(id)?.textContent ?? '').join('|').includes('2곳')));
  await setSlider(page, 7);
  ok(`${label} 4: 바꾼 임계치는 대상마다 다름 해제`, (await page.getByText('대상마다 다름', { exact: true }).count()) === 1);
  // 대상을 더 골라도 입력한 값이 남는다(D3).
  await row(page, 'region:11').click();
  ok(`${label} 4: 대상을 더 골라도 입력값 7% 유지`, (await slider(page).getAttribute('aria-valuetext')) === '7%');
  ok(`${label} 4: 3곳 안내`, await page.getByText('선택한 대상 3곳에 같은 설정이 적용됩니다').isVisible());
  ok(`${label} 4: 변경 안내`, (await hintText(page)) === '저장하지 않은 변경 사항이 있습니다.');
  await saveButton(page).click();
  ok(`${label} 4: 토스트 "알림 설정을 저장했어요"`, await page.getByText('알림 설정을 저장했어요').waitFor({ timeout: 5000 }).then(() => true, () => false));
  const body = JSON.parse(puts(calls)[0]?.body ?? '{}');
  const expected = [
    { favoritePropertyId: 2, favoriteRegionId: null, priceChangeThresholdPct: 7, newTradeAlertYn: true, emailAlertYn: true },
    { favoritePropertyId: 1, favoriteRegionId: null, priceChangeThresholdPct: 7, newTradeAlertYn: true, emailAlertYn: true },
    { favoritePropertyId: null, favoriteRegionId: 11, priceChangeThresholdPct: 7, newTradeAlertYn: true, emailAlertYn: true },
  ];
  ok(`${label} 4: PUT 1회, 본문은 선택한 3곳 같은 값`, puts(calls).length === 1 && JSON.stringify(body.settings) === JSON.stringify(expected),
    JSON.stringify(body));
  ok(`${label} 4: 저장 후 다시 조회`, await waitFor(() => calls.filter((c) => c.method === 'GET' && c.path === '/api/notifications/settings').length >= 2));
  ok(`${label} 4: 저장 후 선택 유지`, (await rowBox(page, 'property:1').isChecked()) && (await rowBox(page, 'region:11').isChecked()));
  ok(`${label} 4: 저장 후 변경 없음 상태`, await waitFor(async () => (await saveButton(page).isDisabled()) && (await hintText(page)).startsWith('변경 사항이 없으면')));
  ok(`${label} 4: 저장 후 대상마다 다름·적용 안내 없음`,
    (await page.getByText('대상마다 다름', { exact: true }).count()) === 0 && (await page.getByText(/같은 설정이 적용됩니다/).count()) === 0);
  ok(`${label} 4: 저장 후 행 보조 텍스트 갱신(지역 11 ±7%)`, (await row(page, 'region:11').innerText()).includes('±7% · 신규거래'));
  await context.close();
}

async function scenarioSaveFailure(label, viewport) {
  const { context, page, calls } = await open(viewport, { query: '?favoritePropertyId=3', putMode: 'failOnce' });
  await ready(page);
  ok(`${label} 5: 미설정 대상만 골라도 저장 가능`, await saveButton(page).isEnabled());
  await page.getByRole('switch', { name: '신규거래 알림 수신' }).click();
  await saveButton(page).click();
  const alert = page.getByRole('alert').filter({ hasText: '저장하지 못했습니다' });
  ok(`${label} 5: 실패 → role=alert + 서버 문구`, await alert.waitFor({ timeout: 5000 }).then(() => true, () => false));
  ok(`${label} 5: 입력 유지(신규거래 꺼짐)`, (await page.getByRole('switch', { name: '신규거래 알림 수신' }).getAttribute('aria-checked')) === 'false');
  ok(`${label} 5: 선택 유지`, await rowBox(page, 'property:3').isChecked());
  ok(`${label} 5: 성공 토스트 없음`, (await page.getByText('알림 설정을 저장했어요').count()) === 0);
  await alert.getByRole('button', { name: '다시 시도' }).click();
  ok(`${label} 5: 다시 시도 → 성공 토스트`, await page.getByText('알림 설정을 저장했어요').waitFor({ timeout: 5000 }).then(() => true, () => false));
  ok(`${label} 5: 다시 시도 후 오류 배너 사라짐`, await waitFor(async () => (await page.getByRole('alert').filter({ hasText: '저장하지 못했습니다' }).count()) === 0));
  const bodies = puts(calls).map((c) => c.body);
  ok(`${label} 5: 같은 본문으로 두 번 PUT`, bodies.length === 2 && bodies[0] === bodies[1], bodies.join(' / '));
  ok(`${label} 5: 행 보조 텍스트 미설정 → 설정됨`, (await row(page, 'property:3').innerText()).includes('±5% 알림'));
  await context.close();
}

// 저장 중(PUT 응답 전)에는 입력이 잠겨 그사이 바꾼 값이 응답 시점에 조용히 사라지지 않는다(코드리뷰 P2).
async function scenarioLockedWhileSaving(label, viewport, layout) {
  const { context, page, calls, releasePut } = await open(viewport, { query: '?favoritePropertyId=1', putMode: 'hold' });
  await ready(page);
  await setSlider(page, 7);
  await saveButton(page).click();
  ok(`${label} 12: 저장 중 PUT 1회 나감`, await waitFor(() => puts(calls).length === 1));
  ok(`${label} 12: 저장 중 대상 체크박스 잠김`, await rowBox(page, 'property:2').isDisabled());
  ok(`${label} 12: 저장 중 슬라이더 잠김`, await slider(page).isDisabled());
  ok(`${label} 12: 저장 중 스위치 잠김`, await page.getByRole('switch', { name: '신규거래 알림 수신' }).isDisabled());
  ok(`${label} 12: 저장 중 이메일 체크박스 잠김`, await page.getByRole('checkbox', { name: /이메일 수신/ }).isDisabled());
  if (layout !== 'desktop') ok(`${label} 12: 저장 중 숫자 입력 잠김`, await page.getByRole('textbox', { name: '변동 임계치(%)' }).isDisabled());
  ok(`${label} 12: 저장 중 폼 aria-busy`, (await page.locator('[aria-busy="true"]').count()) > 0);
  // 잠긴 행을 눌러도 선택이 바뀌지 않는다.
  await row(page, 'property:2').click({ force: true });
  ok(`${label} 12: 저장 중 행 클릭은 선택을 바꾸지 않음`, !(await rowBox(page, 'property:2').isChecked()));
  releasePut();
  ok(`${label} 12: 응답 뒤 토스트`, await page.getByText('알림 설정을 저장했어요').waitFor({ timeout: 5000 }).then(() => true, () => false));
  ok(`${label} 12: 응답 뒤 입력 다시 열림`, await waitFor(async () => (await rowBox(page, 'property:2').isEnabled()) && (await slider(page).isEnabled())));
  ok(`${label} 12: 저장한 값(7%) 유지`, (await slider(page).getAttribute('aria-valuetext')) === '7%');
  ok(`${label} 12: PUT 본문은 저장 버튼을 누른 순간의 값`, JSON.parse(puts(calls)[0].body).settings[0].priceChangeThresholdPct === 7);
  await context.close();
}

async function scenarioEmpty(label, viewport) {
  const { context, page } = await open(viewport, { empty: true });
  ok(`${label} 6: 빈 상태 제목`, await page.getByText('알림을 받을 관심 매물·지역이 없어요').waitFor({ timeout: 10000 }).then(() => true, () => false));
  ok(`${label} 6: 빈 상태 설명`, await page.getByText('관심 목록에 매물이나 지역을 추가하면 알림을 설정할 수 있어요').isVisible());
  ok(`${label} 6: 저장 버튼 없음`, (await saveButton(page).count()) === 0);
  await page.getByRole('link', { name: '관심 목록으로 가기' }).click();
  ok(`${label} 6: "관심 목록으로 가기" → /favorites`, await page.waitForURL((u) => new URL(u).pathname === '/favorites', { timeout: 5000 }).then(() => true, () => false));
  await context.close();
}

async function scenarioNumberInput(label, viewport) {
  const { context, page } = await open(viewport, { query: '?favoritePropertyId=1' });
  await ready(page);
  const input = page.getByRole('textbox', { name: '변동 임계치(%)' });
  await input.fill('25');
  ok(`${label} 7: 25 입력 중 → aria-invalid`, (await input.getAttribute('aria-invalid')) === 'true');
  ok(`${label} 7: 25 입력 중 → 저장 비활성`, await saveButton(page).isDisabled());
  await input.press('Enter');
  ok(`${label} 7: Enter → 20으로 보정`, (await input.inputValue()) === '20');
  ok(`${label} 7: 보정 후 슬라이더 20%`, (await slider(page).getAttribute('aria-valuetext')) === '20%');
  ok(`${label} 7: 보정 후 저장 가능`, await saveButton(page).isEnabled());
  await input.fill('12');
  ok(`${label} 7: 유효한 입력은 바로 슬라이더 반영`, (await slider(page).getAttribute('aria-valuetext')) === '12%');
  await input.fill('');
  await input.blur();
  ok(`${label} 7: 빈 값 blur → 직전 값 12`, (await input.inputValue()) === '12');
  await context.close();
}

async function scenarioPageErrors(label, viewport) {
  const failed = await open(viewport, { settingsMode: 'fail' });
  const alert = failed.page.getByRole('alert').filter({ hasText: '알림 설정을 불러오지 못했습니다' });
  ok(`${label} 8: 설정 조회 실패 → 페이지 오류`, await alert.waitFor({ timeout: 10000 }).then(() => true, () => false));
  ok(`${label} 8: 페이지 오류 시 폼 없음`, (await failed.page.getByRole('heading', { name: '대상 선택' }).count()) === 0);
  ok(`${label} 8: 다시 시도 버튼`, await alert.getByRole('button', { name: '다시 시도' }).isVisible());
  await failed.context.close();

  const meFail = await open(viewport, { meMode: 'fail', query: '?favoritePropertyId=1' });
  ok(`${label} 8: 내 정보 실패 → 화면은 표시`, await ready(meFail.page));
  ok(`${label} 8: 내 정보 실패 → 이메일 주소만 숨김`, (await meFail.page.getByText(USER.email).count()) === 0
    && (await meFail.page.getByText('이메일 수신').isVisible()));
  await meFail.page.getByRole('checkbox', { name: /이메일 수신/ }).evaluate((el) => el.click());
  ok(`${label} 8: 이메일 끔 → 안내 문구`, await meFail.page.getByText('이메일은 보내지 않고 알림 이력에만 기록해요').isVisible());
  await meFail.context.close();
}

async function scenarioEntryLinks(label, viewport) {
  const { context, page } = await open(viewport, { path: '/favorites' });
  await page.getByRole('heading', { level: 1, name: /관심 매물·지역/ }).waitFor({ timeout: 10000 });
  await page.getByRole('link', { name: /^래미안 원베일리 알림 조건/ }).click();
  ok(`${label} 9: MY-02 배지 → MY-03`, await page.waitForURL((u) => new URL(u).pathname === '/notifications/settings', { timeout: 5000 }).then(() => true, () => false));
  await ready(page);
  ok(`${label} 9: 배지에서 온 대상 미리 선택`, await rowBox(page, 'property:1').isChecked());
  await page.goto(`${BASE}/my`);
  const menu = page.getByRole('link', { name: /알림 설정/ }).first();
  ok(`${label} 9: MY-01 메뉴 "알림 설정" → /notifications/settings`,
    await menu.waitFor({ timeout: 10000 }).then(async () => (await menu.getAttribute('href')) === '/notifications/settings', () => false));
  await context.close();
}

async function scenarioKeyboard(label, viewport) {
  const { context, page, calls } = await open(viewport);
  await ready(page);
  // 첫 대상 체크박스로 포커스를 옮긴다(앞쪽 헤더 링크는 건너뛴다).
  await rowBox(page, 'property:4').focus();
  await page.keyboard.press('Space');
  ok(`${label} 10: Space로 대상 선택`, await rowBox(page, 'property:4').isChecked());
  // 남은 대상 5개를 지나(Tab 5번) 한 번 더 누르면 슬라이더.
  for (let i = 0; i < 6; i++) await page.keyboard.press('Tab');
  const onSlider = await page.evaluate(() => document.activeElement?.getAttribute('aria-label'));
  ok(`${label} 10: Tab으로 슬라이더 도달`, onSlider === '변동 임계치', String(onSlider));
  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('ArrowRight');
  ok(`${label} 10: 방향키 → 7%`, (await slider(page).getAttribute('aria-valuetext')) === '7%');
  await page.keyboard.press('Tab');
  ok(`${label} 10: Tab → 신규거래 스위치`, (await page.evaluate(() => document.activeElement?.getAttribute('role'))) === 'switch');
  await page.keyboard.press('Space');
  ok(`${label} 10: Space로 스위치 끔`, (await page.getByRole('switch', { name: '신규거래 알림 수신' }).getAttribute('aria-checked')) === 'false');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  const onSave = await page.evaluate(() => document.activeElement?.textContent);
  ok(`${label} 10: Tab → 저장 버튼(웹 푸시는 건너뜀)`, onSave === '저장', String(onSave));
  await page.keyboard.press('Enter');
  ok(`${label} 10: Enter로 저장`, await waitFor(() => puts(calls).length === 1));
  const body = JSON.parse(puts(calls)[0]?.body ?? '{}');
  ok(`${label} 10: 본문 7%·신규거래 끔·이메일 끔 유지(매물 4)`, JSON.stringify(body.settings) === JSON.stringify([
    { favoritePropertyId: 4, favoriteRegionId: null, priceChangeThresholdPct: 7, newTradeAlertYn: false, emailAlertYn: false },
  ]), JSON.stringify(body));
  await context.close();
}

async function scenarioAnonymous(label, viewport) {
  const { context, page } = await open(viewport, { loggedIn: false, query: '?favoritePropertyId=2' });
  ok(`${label} 11: 비로그인 → 로그인 화면`, await page.waitForURL((u) => new URL(u).pathname === '/login', { timeout: 5000 }).then(() => true, () => false));
  await page.getByLabel('이메일').fill('user@test.com');
  await page.getByLabel('비밀번호', { exact: true }).fill('Passw0rd!');
  await page.locator('button[type="submit"]').click();
  ok(`${label} 11: 로그인 후 쿼리 그대로 복귀`, await page.waitForURL((u) => {
    const url = new URL(u);
    return url.pathname === '/notifications/settings' && url.search === '?favoritePropertyId=2';
  }, { timeout: 5000 }).then(() => true, () => false), page.url());
  await ready(page);
  ok(`${label} 11: 복귀 후 미리 선택`, await rowBox(page, 'property:2').isChecked());
  await context.close();
}

const VIEWPORTS = [
  { label: '모바일', viewport: { width: 390, height: 844 }, layout: 'mobile' },
  { label: '태블릿', viewport: { width: 768, height: 1024 }, layout: 'tablet' },
  { label: '데스크톱', viewport: { width: 1280, height: 900 }, layout: 'desktop' },
];
const only = (process.env.VIEWPORTS ?? '').split(',').filter(Boolean);
for (const { label, viewport, layout } of VIEWPORTS.filter((v) => only.length === 0 || only.includes(v.label))) {
  await scenarioRender(label, viewport, layout);
  await scenarioPreselect(label, viewport, layout);
  await scenarioZero(label, viewport);
  await scenarioMixed(label, viewport);
  await scenarioSaveFailure(label, viewport);
  await scenarioLockedWhileSaving(label, viewport, layout);
  await scenarioEmpty(label, viewport);
  if (layout !== 'desktop') await scenarioNumberInput(label, viewport);
  await scenarioPageErrors(label, viewport);
  await scenarioEntryLinks(label, viewport);
  if (layout === 'desktop') await scenarioKeyboard(label, viewport);
  await scenarioAnonymous(label, viewport);
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
