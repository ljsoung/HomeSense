// MY-01 마이페이지 홈 — 모바일(390)·태블릿(768)·데스크톱(1280)에서 검증한다. 백엔드 불필요(route로 흉내).
// 1. 비로그인으로 /my 직접 진입 → 로그인 → /my로 자동 복귀
// 2. 세션 확인(checking) 중에는 로그인 화면으로 튕기지 않고 스켈레톤
// 3. 프로필(닉네임·이메일·가입일), 모바일은 이메일과 가입일이 두 줄(이메일만 말줄임)
// 4. 관심 매물·최근 알림 위젯의 정상/빈/오류+재시도, 프로필 오류 배너+재시도
// 5. 로그아웃 → HOME-01(로그인 화면 아님)
// 6. 탈퇴: 체크 전 버튼 비활성 → 실패 시 다이얼로그 안 메시지 → 성공 시 세션 정리 + HOME-01(/logout 미호출)
// 7. 다이얼로그 키보드(초기 포커스, Tab 순환, Esc, 트리거로 포커스 복귀)
// 8. 가로 스크롤 없음
import { chromium } from 'playwright';
import { BASE } from './base.mjs';

let pass = 0;
let fail = 0;
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); }
}
const okBody = (data, pageMeta) => JSON.stringify({ success: true, data, error: null, ...(pageMeta ? { pageMeta } : {}), timestamp: '' });
const errBody = (code, message) => JSON.stringify({ success: false, data: null, error: { code, message }, timestamp: '' });

const ACCESS_KEY = 'homesense.accessToken';
const REFRESH_KEY = 'homesense.refreshToken';
const LONG_EMAIL = 'very.long.email.address.for.ellipsis.check@homesense-example-domain.com';
const USER = { userId: 7, email: LONG_EMAIL, nickname: '지성', createdAt: '2026-09-15T10:20:30' };

/** 지금으로부터 msAgo 전의 KST LocalDateTime 문자열(서버 형식, 타임존 없음). */
function kstAgo(msAgo) {
  return new Date(Date.now() - msAgo + 9 * 3600_000).toISOString().slice(0, 19);
}
function favorite(id, name, amount, category) {
  return {
    favoritePropertyId: id, complexId: id * 10, complexName: name, sido: '서울특별시', sigungu: '종로구', dongRi: '숭인동',
    housingType: 'APT', recentDealCategory: category, recentDealDate: amount ? '2026-09-01' : null, recentAmount: amount,
    changeRate: null, hasNotificationSetting: false,
  };
}
// 응답 순서와 무관하게 최근 등록(ID 큰) 2건 — 9(자이), 5(힐스테이트).
const FAVORITES = [favorite(3, '래미안', 90000, 'SALE'), favorite(9, '자이', 125000, 'SALE'), favorite(5, '힐스테이트', null, null)];
const NOTIFICATIONS = [
  { notificationId: 31, notificationType: 'PRICE_CHANGE', title: '자이 시세 변동', message: '최근 거래가가 5% 올랐어요', complexId: 90, legalDongCd: null, tradeId: null, isRead: false, sentAt: kstAgo(3 * 3600_000 + 60_000) },
  { notificationId: 30, notificationType: 'NEW_TRADE', title: '종로구 신규 거래', message: '새 거래가 등록됐어요', complexId: null, legalDongCd: '1111017400', tradeId: 5, isRead: true, sentAt: kstAgo(2 * 86_400_000 + 60_000) },
  { notificationId: 29, notificationType: 'NEW_TRADE', title: '숭인동 신규 거래', message: '새 거래가 등록됐어요', complexId: null, legalDongCd: '1111017400', tradeId: 4, isRead: true, sentAt: kstAgo(10 * 86_400_000) },
];

const browser = await chromium.launch();

/**
 * api: 응답 모드를 바꿀 수 있는 객체. me: 'ok'|'error'|'hold'|'errorAfterFirst', favorites/notifications: 'ok'|'empty'|'error',
 * withdraw: 'ok'|'fail'. calls에 요청을 기록한다.
 */
async function open(viewport, { loggedIn = true, api: apiOverrides = {} } = {}) {
  const context = await browser.newContext({ viewport });
  if (loggedIn) {
    await context.addInitScript(([keys]) => {
      if (!sessionStorage.getItem('__seeded')) {
        localStorage.setItem(keys[0], 'A');
        localStorage.setItem(keys[1], 'R');
        sessionStorage.setItem('__seeded', '1');
      }
    }, [[ACCESS_KEY, REFRESH_KEY]]);
  }
  const api = { me: 'ok', favorites: 'ok', notifications: 'ok', withdraw: 'ok', ...apiOverrides };
  const calls = [];
  let releaseMe;
  const meReleased = new Promise((r) => { releaseMe = r; });
  await context.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    calls.push({ method: request.method(), path, search: url.search, body: request.postData() });
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body });
    if (path === '/api/users/me' && request.method() === 'GET') {
      if (api.me === 'hold') await meReleased;
      // 세션 확인(첫 호출)은 통과시키고 MY-01 화면의 프로필 요청부터 실패시킨다. 개발 모드 StrictMode가 화면을
      // 두 번 마운트해 요청 횟수로는 구분할 수 없다.
      if (api.me === 'errorAfterFirst') {
        if (api.meServed) return json(500, errBody('INTERNAL_SERVER_ERROR', '프로필 서버 오류입니다'));
        api.meServed = true;
      }
      if (api.me === 'error') return json(500, errBody('INTERNAL_SERVER_ERROR', '프로필 서버 오류입니다'));
      return json(200, okBody(USER));
    }
    if (path === '/api/users/me' && request.method() === 'DELETE') {
      if (api.withdraw === 'fail') return json(401, errBody('INVALID_CREDENTIALS', '비밀번호가 일치하지 않습니다'));
      return json(200, okBody(null));
    }
    if (path === '/api/favorites/properties') {
      if (api.favorites === 'error') return json(500, errBody('INTERNAL_SERVER_ERROR', '관심 매물을 불러오지 못했습니다'));
      return json(200, okBody(api.favorites === 'empty' ? [] : FAVORITES));
    }
    if (path === '/api/notifications') {
      if (api.notifications === 'error') return json(503, errBody('SERVICE_UNAVAILABLE', '알림 서버 점검 중입니다'));
      const data = api.notifications === 'empty' ? [] : NOTIFICATIONS;
      return json(200, okBody(data, { page: 0, size: 3, totalElements: data.length, totalPages: 1 }));
    }
    if (path === '/api/auth/login') return json(200, okBody({ accessToken: 'A2', refreshToken: 'R2', expiresIn: 1800 }));
    if (path === '/api/auth/logout') return json(200, okBody(null));
    return json(200, okBody([]));
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  return { context, page, api, calls, errors, releaseMe };
}

const tokens = (page) => page.evaluate(([a, r]) => [localStorage.getItem(a), localStorage.getItem(r)], [ACCESS_KEY, REFRESH_KEY]);
const path = (page) => new URL(page.url()).pathname;
const waitPath = (page, expected, timeout = 5000) =>
  page.waitForURL((u) => new URL(u).pathname === expected, { timeout }).then(() => true, () => false);
const profileReady = (page) => page.getByText('2026.09.15 가입').waitFor({ timeout: 5000 }).then(() => true, () => false);
const accountTile = (page) => page.getByRole('button', { name: '로그아웃·회원탈퇴' });

async function scenarioAnonymousRedirect(label, viewport) {
  const { context, page, errors } = await open(viewport, { loggedIn: false });
  await page.goto(`${BASE}/my`);
  ok(`${label} 1: 비로그인 /my → 로그인 화면`, await waitPath(page, '/login'));
  await page.getByLabel('이메일').fill('user@test.com');
  await page.getByLabel('비밀번호', { exact: true }).fill('Passw0rd!');
  await page.locator('button[type="submit"]').click();
  ok(`${label} 1: 로그인 후 /my로 자동 복귀`, await waitPath(page, '/my'));
  ok(`${label} 1: 복귀 후 프로필 표시`, await profileReady(page));
  ok(`${label} 1: pageerror 없음`, errors.length === 0, errors.join(' | '));
  await context.close();
}

async function scenarioChecking(label, viewport) {
  const { context, page, releaseMe } = await open(viewport, { api: { me: 'hold' } });
  await page.goto(`${BASE}/my`);
  await page.waitForTimeout(1200);
  ok(`${label} 2: 확인 중에 /my에 머묾(로그인 화면으로 튕기지 않음)`, path(page) === '/my', path(page));
  ok(`${label} 2: 확인 중 스켈레톤 표시`, await page.getByRole('status', { name: '로그인 상태 확인 중' }).isVisible());
  releaseMe();
  ok(`${label} 2: 확인이 끝나면 프로필`, await profileReady(page));
  ok(`${label} 2: 끝까지 /my`, path(page) === '/my');
  await context.close();
}

async function scenarioContent(label, viewport, mobile) {
  const { context, page, calls, errors } = await open(viewport);
  await page.goto(`${BASE}/my`);
  ok(`${label} 3: 가입일 표시`, await profileReady(page));
  ok(`${label} 3: 닉네임 표시`, await page.getByRole('region', { name: '내 프로필' }).getByText('지성', { exact: true }).isVisible());
  const email = page.getByText(LONG_EMAIL, { exact: true });
  ok(`${label} 3: 이메일 전체 값이 DOM에 있음`, (await email.count()) === 1);
  const emailBox = await email.boundingBox();
  const joinedBox = await page.getByText('2026.09.15 가입').boundingBox();
  if (mobile) {
    ok(`${label} 3: 모바일 이메일·가입일이 두 줄`, emailBox && joinedBox && joinedBox.y >= emailBox.y + emailBox.height - 1, JSON.stringify([emailBox, joinedBox]));
    const truncated = await email.evaluate((el) => el.scrollWidth > el.clientWidth);
    ok(`${label} 3: 모바일 긴 이메일은 말줄임`, truncated);
  }
  ok(`${label} 3: 회원정보 수정 → MY-05`, (await page.getByRole('link', { name: '회원정보 수정' }).getAttribute('href')) === '/my/profile');

  // 4. 정상 위젯
  const favSection = page.getByRole('region', { name: '관심 매물' });
  const favLinks = favSection.locator('ul a');
  ok(`${label} 4: 관심 매물 최근 등록 2건`, (await favLinks.count()) === 2);
  ok(`${label} 4: 관심 매물 순서(ID 내림차순)`, (await favLinks.allInnerTexts()).map((t) => t.split('\n')[0]).join(',') === '자이,힐스테이트', (await favLinks.allInnerTexts()).join('|'));
  ok(`${label} 4: 행 → DTL-01`, (await favLinks.first().getAttribute('href')) === '/complexes/90');
  ok(`${label} 4: 가격 표시, 없는 값은 생략`, (await favLinks.first().innerText()).includes('12억 5,000만원') && !(await favLinks.nth(1).innerText()).includes('null'));
  ok(`${label} 4: 하트는 aria-hidden`, (await favLinks.first().locator('svg').last().getAttribute('aria-hidden')) === 'true');
  ok(`${label} 4: 전체 관심 매물 보기 → MY-02`, (await favSection.getByRole('link', { name: /전체 관심 매물 보기/ }).getAttribute('href')) === '/favorites');
  ok(`${label} 4: 관심 매물 전체 보기 → MY-02`, (await page.getByRole('link', { name: '관심 매물 전체 보기' }).getAttribute('href')) === '/favorites');
  const notiSection = page.getByRole('region', { name: '최근 알림' });
  const notiLinks = notiSection.locator('ul a');
  ok(`${label} 4: 최근 알림 3건`, (await notiLinks.count()) === 3);
  const notiTexts = await notiLinks.allInnerTexts();
  ok(`${label} 4: 상대 시간(3시간 전·2일 전·날짜)`, notiTexts[0].includes('3시간 전') && notiTexts[1].includes('2일 전') && /\d{4}\.\d{2}\.\d{2}/.test(notiTexts[2]), notiTexts.join('|'));
  ok(`${label} 4: 읽음 여부 문구(색만으로 구분하지 않음)`, notiTexts[0].includes('읽지 않음') && notiTexts[1].includes('읽음'));
  ok(`${label} 4: 알림 행·전체 보기 → MY-04`, (await notiLinks.first().getAttribute('href')) === '/notifications' && (await page.getByRole('link', { name: '알림 전체 보기' }).getAttribute('href')) === '/notifications');
  ok(`${label} 4: 알림 요청 page=0&size=3`, calls.some((c) => c.path === '/api/notifications' && c.search.includes('page=0') && c.search.includes('size=3')));
  ok(`${label} 4: 읽음 처리(PATCH) 없음`, !calls.some((c) => c.method === 'PATCH'));

  // 메뉴 타일
  ok(`${label} 메뉴: MY-02/03/04 링크`, (await page.getByRole('link', { name: '관심 매물·지역 관리' }).getAttribute('href')) === '/favorites'
    && (await page.getByRole('link', { name: '알림 설정' }).getAttribute('href')) === '/notifications/settings'
    && (await page.getByRole('link', { name: '알림 이력' }).getAttribute('href')) === '/notifications');
  const tileColor = await accountTile(page).evaluate((el) => getComputedStyle(el).color);
  ok(`${label} 메뉴: 계정 타일이 활성 버튼(흐린 색 아님)`, (await accountTile(page).isEnabled()) && tileColor === 'rgb(54, 65, 83)', tileColor);

  // 8. 가로 스크롤 없음
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  ok(`${label} 8: 가로 스크롤 없음`, overflow <= 0, String(overflow));
  // 모바일 하단 탭에 가리지 않음 — 마지막 위젯 아래가 탭 위로 스크롤된다.
  if (mobile) {
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    const notiBox = await notiSection.boundingBox();
    const tabBox = await page.locator('nav').filter({ hasText: '마이' }).last().boundingBox();
    ok(`${label} 8: 맨 아래에서 위젯이 하단 탭에 가리지 않음`, notiBox && tabBox && notiBox.y + notiBox.height <= tabBox.y + 1, JSON.stringify([notiBox, tabBox]));
  }
  ok(`${label} pageerror 없음`, errors.length === 0, errors.join(' | '));
  await context.close();
}

async function scenarioEmptyAndErrors(label, viewport) {
  // 빈 상태
  {
    const { context, page } = await open(viewport, { api: { favorites: 'empty', notifications: 'empty' } });
    await page.goto(`${BASE}/my`);
    await profileReady(page);
    ok(`${label} 4: 관심 매물 빈 상태`, await page.getByText('아직 등록한 관심 매물이 없어요').waitFor({ timeout: 5000 }).then(() => true, () => false));
    ok(`${label} 4: 빈 상태 CTA → 검색`, (await page.getByRole('link', { name: '매물 둘러보기' }).getAttribute('href')) === '/search');
    ok(`${label} 4: 알림 빈 상태`, await page.getByText('아직 받은 알림이 없어요').isVisible());
    await context.close();
  }
  // 오류 + 재시도
  {
    const { context, page, api } = await open(viewport, { api: { me: 'ok', favorites: 'error', notifications: 'error' } });
    await page.goto(`${BASE}/my`);
    await profileReady(page);
    const favSection = page.getByRole('region', { name: '관심 매물' });
    const notiSection = page.getByRole('region', { name: '최근 알림' });
    ok(`${label} 4: 관심 매물 오류는 위젯 안에 서버 메시지`, await favSection.getByRole('alert').filter({ hasText: '관심 매물을 불러오지 못했습니다' }).waitFor({ timeout: 5000 }).then(() => true, () => false));
    ok(`${label} 4: 알림 오류는 위젯 안에 서버 메시지`, await notiSection.getByRole('alert').filter({ hasText: '알림 서버 점검 중입니다' }).isVisible());
    ok(`${label} 4: 위젯 오류여도 프로필은 정상`, await page.getByRole('region', { name: '내 프로필' }).getByText('지성', { exact: true }).isVisible());
    api.favorites = 'ok';
    await favSection.getByRole('button', { name: '다시 시도' }).click();
    ok(`${label} 4: 관심 매물 재시도 성공`, await favSection.locator('ul a').first().waitFor({ timeout: 5000 }).then(() => true, () => false));
    ok(`${label} 4: 알림 위젯은 여전히 오류(독립)`, await notiSection.getByRole('alert').isVisible());
    api.notifications = 'ok';
    await notiSection.getByRole('button', { name: '다시 시도' }).click();
    ok(`${label} 4: 알림 재시도 성공`, await notiSection.locator('ul a').first().waitFor({ timeout: 5000 }).then(() => true, () => false));
    await context.close();
  }
  // 프로필 오류 배너 — 세션 확인(첫 getMe)은 통과시키고 MY-01의 getMe만 실패시킨다.
  {
    const { context, page, api } = await open(viewport, { api: { me: 'errorAfterFirst' } });
    await page.goto(`${BASE}/my`);
    const banner = page.getByRole('alert').filter({ hasText: '프로필 서버 오류입니다' });
    ok(`${label} 4: 프로필 오류 배너(서버 메시지)`, await banner.waitFor({ timeout: 5000 }).then(() => true, () => false));
    ok(`${label} 4: 프로필 오류여도 위젯은 정상`, (await page.getByRole('region', { name: '관심 매물' }).locator('ul a').count()) === 2);
    api.me = 'ok';
    await banner.getByRole('button', { name: '다시 시도' }).click();
    ok(`${label} 4: 프로필 재시도 성공`, await profileReady(page));
    ok(`${label} 4: 배너 사라짐`, (await banner.count()) === 0);
    await context.close();
  }
}

async function scenarioLogout(label, viewport) {
  const { context, page, calls, errors } = await open(viewport);
  await page.goto(`${BASE}/my`);
  await profileReady(page);
  await accountTile(page).click();
  const dialog = page.getByRole('dialog', { name: '로그아웃·회원탈퇴' });
  ok(`${label} 5: 선택 다이얼로그`, await dialog.isVisible());
  await dialog.getByRole('button', { name: '로그아웃' }).click();
  ok(`${label} 5: 로그아웃 → HOME-01`, await waitPath(page, '/'));
  await page.waitForTimeout(500);
  ok(`${label} 5: 로그인 화면으로 가지 않음`, path(page) === '/', path(page));
  ok(`${label} 5: 서버 로그아웃 호출`, calls.some((c) => c.path === '/api/auth/logout'));
  ok(`${label} 5: 로컬 토큰 삭제`, (await tokens(page)).every((t) => t === null));
  ok(`${label} 5: 토스트`, await page.getByText('로그아웃되었습니다').isVisible());
  ok(`${label} 5: pageerror 없음`, errors.length === 0, errors.join(' | '));
  await context.close();
}

async function scenarioWithdraw(label, viewport) {
  const { context, page, api, calls, errors } = await open(viewport, { api: { withdraw: 'fail' } });
  await page.goto(`${BASE}/my`);
  await profileReady(page);
  await accountTile(page).click();
  await page.getByRole('dialog', { name: '로그아웃·회원탈퇴' }).getByRole('button', { name: '회원탈퇴' }).click();
  const dialog = page.getByRole('alertdialog', { name: '회원탈퇴' });
  ok(`${label} 6: 탈퇴 확인 alertdialog`, await dialog.isVisible());
  const describedBy = await dialog.getAttribute('aria-describedby');
  ok(`${label} 6: 안내가 aria-describedby로 연결`, Boolean(describedBy) && (await page.locator(`[id="${describedBy}"]`).innerText()).includes('7일'));
  const notice = await dialog.innerText();
  ok(`${label} 6: 철회 안내 없음`, !notice.includes('철회') && !notice.includes('복구할 수 있'));
  ok(`${label} 6: 사유 입력 없음`, (await dialog.getByLabel(/사유/).count()) === 0);
  const submit = dialog.getByRole('button', { name: '탈퇴하기' });
  ok(`${label} 6: 체크 전 탈퇴하기 비활성`, await submit.isDisabled());
  await dialog.getByLabel('비밀번호 확인').fill('wrong-pass');
  ok(`${label} 6: 비밀번호만 입력해도 체크 전에는 비활성`, await submit.isDisabled());
  await dialog.getByText('안내 사항을 확인했습니다').click();
  ok(`${label} 6: 체크하면 활성`, await submit.isEnabled());
  await submit.click();
  ok(`${label} 6: 실패 시 다이얼로그 안 서버 메시지`, await dialog.getByText('비밀번호가 일치하지 않습니다').waitFor({ timeout: 5000 }).then(() => true, () => false));
  ok(`${label} 6: 실패 시 /my에 머묾·토큰 유지`, path(page) === '/my' && (await tokens(page)).every((t) => t !== null));
  ok(`${label} 6: 실패 후 비밀번호 칸으로 포커스`, await dialog.getByLabel('비밀번호 확인').evaluate((el) => el === document.activeElement));

  api.withdraw = 'ok';
  await dialog.getByLabel('비밀번호 확인').fill('Passw0rd!');
  await submit.click();
  ok(`${label} 6: 성공 → HOME-01`, await waitPath(page, '/'));
  await page.waitForTimeout(400);
  ok(`${label} 6: 로그인 화면으로 가지 않음`, path(page) === '/');
  ok(`${label} 6: 로컬 세션 정리`, (await tokens(page)).every((t) => t === null));
  ok(`${label} 6: /logout 미호출`, !calls.some((c) => c.path === '/api/auth/logout'));
  const deleteCall = calls.filter((c) => c.method === 'DELETE' && c.path === '/api/users/me').at(-1);
  ok(`${label} 6: 요청 본문은 password만`, deleteCall && JSON.stringify(Object.keys(JSON.parse(deleteCall.body))) === '["password"]', deleteCall?.body);
  ok(`${label} 6: 완료 토스트`, await page.getByText('회원 탈퇴가 완료되었습니다').isVisible());
  ok(`${label} 6: 헤더가 비로그인`, await page.locator('header:visible').first().getByRole('link', { name: '로그인', exact: true }).isVisible());
  ok(`${label} 6: pageerror 없음`, errors.length === 0, errors.join(' | '));
  await context.close();
}

const activeIn = (locator) => locator.evaluate((el) => el.contains(document.activeElement));
const tileFocused = (page) => accountTile(page).evaluate((el) => el === document.activeElement);

async function scenarioKeyboard(label, viewport) {
  const { context, page } = await open(viewport);
  await page.goto(`${BASE}/my`);
  await profileReady(page);
  await accountTile(page).focus();
  await page.keyboard.press('Enter');
  const choice = page.getByRole('dialog', { name: '로그아웃·회원탈퇴' });
  ok(`${label} 7: Enter로 선택 다이얼로그`, await choice.isVisible());
  ok(`${label} 7: 열리면 포커스가 다이얼로그 안`, await activeIn(choice));
  let trapped = true;
  for (let i = 0; i < 8; i++) {
    await page.keyboard.press(i % 2 ? 'Shift+Tab' : 'Tab');
    await page.keyboard.press('Tab');
    if (!(await activeIn(choice))) trapped = false;
  }
  ok(`${label} 7: Tab 순환(포커스 트랩)`, trapped);
  await page.keyboard.press('Escape');
  ok(`${label} 7: Esc로 닫힘`, (await choice.count()) === 0);
  ok(`${label} 7: 닫히면 타일로 포커스 복귀`, await tileFocused(page));

  await page.keyboard.press('Enter');
  await choice.getByRole('button', { name: '회원탈퇴' }).focus();
  await page.keyboard.press('Enter');
  const alert = page.getByRole('alertdialog', { name: '회원탈퇴' });
  ok(`${label} 7: 키보드로 탈퇴 다이얼로그`, await alert.isVisible());
  ok(`${label} 7: 첫 포커스는 확인 체크박스`, await alert.getByRole('checkbox', { name: '안내 사항을 확인했습니다' }).evaluate((el) => el === document.activeElement));
  await page.keyboard.press('Space');
  ok(`${label} 7: Space로 체크`, await alert.getByRole('checkbox').isChecked());
  let alertTrapped = true;
  for (let i = 0; i < 8; i++) {
    await page.keyboard.press('Tab');
    if (!(await activeIn(alert))) alertTrapped = false;
  }
  ok(`${label} 7: 탈퇴 다이얼로그 Tab 순환`, alertTrapped);
  await page.keyboard.press('Escape');
  ok(`${label} 7: Esc로 탈퇴 다이얼로그 닫힘`, (await alert.count()) === 0);
  ok(`${label} 7: 탈퇴 다이얼로그 닫히면 타일로 포커스 복귀`, await tileFocused(page));
  await page.keyboard.press('Enter');
  await choice.getByRole('button', { name: '회원탈퇴' }).click();
  ok(`${label} 7: 다시 열면 체크가 초기화`, !(await alert.getByRole('checkbox').isChecked()));
  await context.close();
}

async function scenarioPlaceholders(label, viewport) {
  const { context, page } = await open(viewport);
  for (const [to, id] of [['/favorites', 'MY-02'], ['/notifications/settings', 'MY-03'], ['/notifications', 'MY-04'], ['/my/profile', 'MY-05']]) {
    await page.goto(`${BASE}${to}`);
    ok(`${label} 5절: ${to} 준비 중(${id})`, await page.getByText(id, { exact: true }).waitFor({ timeout: 5000 }).then(() => true, () => false)
      && (await page.getByText('준비 중인 화면입니다.').isVisible()));
  }
  await context.close();
  const anon = await open(viewport, { loggedIn: false });
  await anon.page.goto(`${BASE}/my/profile`);
  ok(`${label} 5절: 비로그인 MY-05 → 로그인 화면`, await waitPath(anon.page, '/login'));
  await anon.context.close();
}

const VIEWPORTS = [
  ['모바일', { width: 390, height: 844 }, true],
  ['태블릿', { width: 768, height: 1024 }, false],
  ['데스크톱', { width: 1280, height: 900 }, false],
];
for (const [label, viewport, mobile] of VIEWPORTS) {
  await scenarioAnonymousRedirect(label, viewport);
  await scenarioChecking(label, viewport);
  await scenarioContent(label, viewport, mobile);
  await scenarioEmptyAndErrors(label, viewport);
  await scenarioLogout(label, viewport);
  await scenarioWithdraw(label, viewport);
  await scenarioKeyboard(label, viewport);
  await scenarioPlaceholders(label, viewport);
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
