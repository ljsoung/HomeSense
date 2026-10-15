// MY-04 알림 이력 — 모바일(390, 무한 스크롤)·태블릿(768, 번호형)·데스크톱(1280, 번호형). 백엔드 불필요(route로 상태 있는 가짜 서버).
// 1. 렌더: 행 수(번호형 10·무한 20), 안 읽은 행의 점·스크린리더 "읽지 않음", 읽은 행 없음, 유형 아이콘, 머리(브레드크럼/앱 바), 보관 안내 위치,
//    GNB "알림"(데스크톱)·하단 탭 "마이"(모바일) 활성, 가로 스크롤 없음
// 2. 안 읽은 매물 알림 클릭 → PATCH + DTL-01, 뒤로 오면 점 없음. 지역 알림 → SRCH-01 지역. 대상 없음 → 토스트 + SRCH-01. 읽은 알림은 PATCH 없음
// 3. PATCH 500이어도 이동한다(D2) — 응답을 붙잡아도 이동이 먼저
// 4. chip 전환 → type 요청·페이지 리셋, 느린 이전 응답이 새 탭 결과를 덮지 않음
// 5. 번호형: 2페이지 → DTL-01 → 뒤로 → 2페이지. 범위 밖 page → 마지막 페이지, 모르는 type → 전체(replace)
// 6. 무한 스크롤: 다음 페이지(page=1, size=20), 끝 푸터 "총 N건 · 모두 표시됨", 다음 페이지 실패 → 재시도
// 7. 전체 빈 상태(chip 숨김·CTA) / 유형 필터 빈 상태(chip 유지)
// 8. 목록 조회 실패 → 오류 배너 + 다시 시도
// 9. 비로그인 → 로그인 → 쿼리 포함 복귀
import { chromium } from 'playwright';
import { BASE } from './base.mjs';
import { errBody, okBody } from './mockApi.mjs';

let pass = 0;
let fail = 0;
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); }
}

const USER = { userId: 7, email: 'jiseong@homesense.kr', nickname: '지성', createdAt: '2026-09-15T10:20:30' };

/**
 * 45건. id가 클수록 최신. 같은 배치 런(같은 created_at)이 여러 건 섞인다.
 * 45: 안 읽은 매물(complexId 10, 가격 상승) / 44: 안 읽은 지역(legalDongCd, 신규거래) / 43: 안 읽은 대상 없음(가격 하락)
 * 42: 읽은 매물 / 나머지: 홀수 PRICE_CHANGE 상승·짝수 NEW_TRADE, 3건마다 읽음
 */
function makeNotifications(count = 45) {
  const list = [];
  for (let id = 1; id <= count; id++) {
    const type = id % 2 === 1 ? 'PRICE_CHANGE' : 'NEW_TRADE';
    const day = String(1 + Math.floor(id / 4)).padStart(2, '0');
    list.push({
      notificationId: id,
      notificationType: type,
      title: type === 'PRICE_CHANGE' ? `단지${id} 실거래가 2.${id % 10}% 상승` : `단지${id} 신규 실거래 ${id % 5 + 1}건`,
      complexId: 1000 + id,
      isRead: id % 3 === 0,
      createdAt: `2026-09-${day}T03:00:00`,
    });
  }
  const byId = (id) => list.find((n) => n.notificationId === id);
  if (count < 45) return list;
  Object.assign(byId(45), { notificationType: 'PRICE_CHANGE', title: '숭인 힐스테이트 실거래가 2.1% 상승', complexId: 10, isRead: false, createdAt: '2026-10-15T09:00:00' });
  Object.assign(byId(44), { notificationType: 'NEW_TRADE', title: '서울특별시 종로구 창신동 신규 실거래 3건', complexId: undefined, legalDongCd: '1111017500', tradeId: 99, isRead: false, createdAt: '2026-10-15T09:00:00' });
  Object.assign(byId(43), { notificationType: 'PRICE_CHANGE', title: '사라진 단지 실거래가 1.2% 하락', complexId: undefined, isRead: false, createdAt: '2026-10-15T09:00:00' });
  Object.assign(byId(42), { notificationType: 'PRICE_CHANGE', title: '읽은 단지 실거래가 0.5% 상승', complexId: 42, isRead: true, createdAt: '2026-10-14T09:00:00' });
  return list;
}

const browser = await chromium.launch();

/**
 * patchMode: 'ok'|'fail'|'hold'. listMode: 'ok'|'failOnce'|'fail'. delays: { [type|'ALL']: ms }. failMorePageOnce: 무한 스크롤 그 페이지(0-base) 첫 요청만 500.
 */
async function open(viewport, { path = '/notifications', count = 45, patchMode = 'ok', listMode = 'ok', delays = {}, failMorePageOnce = null, loggedIn = true } = {}) {
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
  const db = { notifications: makeNotifications(count), listFailures: listMode === 'failOnce' ? 1 : listMode === 'fail' ? Infinity : 0, moreFailed: false };
  let releasePatch = () => {};
  const patchHeld = new Promise((resolve) => { releasePatch = resolve; });
  const calls = [];
  await context.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const p = url.pathname;
    const method = request.method();
    calls.push({ method, path: p, query: url.searchParams.toString() });
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body }).catch(() => {});
    if (p === '/api/auth/login') return json(200, okBody({ accessToken: 'A2', refreshToken: 'R2', expiresIn: 1800 }));
    if (p === '/api/users/me') return json(200, okBody(USER));
    if (p === '/api/notifications' && method === 'GET') {
      const type = url.searchParams.get('type');
      const page = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      const delay = delays[type ?? 'ALL'];
      if (delay) await new Promise((r) => setTimeout(r, delay));
      if (db.listFailures > 0) { db.listFailures--; return json(500, errBody('INTERNAL_SERVER_ERROR', '알림 서버 오류입니다')); }
      if (failMorePageOnce !== null && page === failMorePageOnce && !db.moreFailed) {
        db.moreFailed = true;
        return json(500, errBody('INTERNAL_SERVER_ERROR', '다음 페이지 오류'));
      }
      const filtered = db.notifications
        .filter((n) => !type || n.notificationType === type)
        .sort((a, b) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : b.notificationId - a.notificationId));
      const items = filtered.slice(page * size, page * size + size);
      return json(200, okBody(items, { page, size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size) }));
    }
    const read = p.match(/^\/api\/notifications\/(\d+)\/read$/);
    if (read && method === 'PATCH') {
      if (patchMode === 'hold') await patchHeld;
      if (patchMode === 'fail') return json(500, errBody('INTERNAL_SERVER_ERROR', '읽음 처리 오류'));
      const n = db.notifications.find((x) => x.notificationId === Number(read[1]));
      if (n) n.isRead = true;
      return json(200, okBody(null));
    }
    if (p === '/api/complexes/search') return json(200, okBody([], { page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    return json(404, errBody('NOT_FOUND', 'mock 없음'));
  });
  const page = await context.newPage();
  await page.goto(`${BASE}${path}`);
  return { context, page, calls, db, releasePatch };
}

const rows = (page) => page.locator('ul[aria-label="알림 목록"] > li');
const row = (page, id) => page.locator(`[data-notification-id="${id}"]`);
const listGets = (calls) => calls.filter((c) => c.method === 'GET' && c.path === '/api/notifications');
const patches = (calls) => calls.filter((c) => c.method === 'PATCH');
const ready = (page) => rows(page).first().waitFor({ timeout: 8000 });
const chip = (page, name) => page.getByRole('radio', { name });

async function scenarioRender(label, viewport, layout) {
  const { context, page } = await open(viewport);
  await ready(page);
  const mode = layout === 'mobile' ? 'infinite' : 'paged';
  ok(`${label} 1: 행 수 ${mode === 'paged' ? 10 : 20}`, (await rows(page).count()) === (mode === 'paged' ? 10 : 20), String(await rows(page).count()));
  const unread = row(page, 45);
  ok(`${label} 1: 안 읽은 행 점`, (await unread.locator('[data-testid="unread-dot"]').count()) === 1);
  ok(`${label} 1: 안 읽은 행 스크린리더 "읽지 않음"`, (await unread.getAttribute('href')) === '/complexes/10' && (await unread.textContent()).includes('(읽지 않음)') && (await unread.locator('.sr-only').count()) === 1);
  ok(`${label} 1: 점은 aria-hidden`, (await unread.locator('[data-testid="unread-dot"]').getAttribute('aria-hidden')) === 'true');
  const readRow = row(page, 42);
  ok(`${label} 1: 읽은 행은 점·"읽지 않음" 없음`, (await readRow.locator('[data-testid="unread-dot"]').count()) === 0 && !(await readRow.textContent()).includes('읽지 않음'));
  const titleColor = async (id) => row(page, id).locator('span.flex-col > span').first().evaluate((el) => getComputedStyle(el).color);
  ok(`${label} 1: 안 읽음 제목 #101828, 읽음 #4a5565`, (await titleColor(45)) === 'rgb(16, 24, 40)' && (await titleColor(42)) === 'rgb(74, 85, 101)');
  const iconColor = async (id) => row(page, id).locator('[data-testid="notification-icon"]').evaluate((el) => getComputedStyle(el).color);
  ok(`${label} 1: 상승 아이콘 빨강, 하락 파랑, 신규거래 브랜드`,
    (await iconColor(45)) === 'rgb(239, 68, 68)' && (await iconColor(43)) === 'rgb(59, 130, 246)' && (await iconColor(44)) === 'rgb(15, 92, 84)');
  if (layout === 'desktop') {
    ok(`${label} 1: 브레드크럼`, await page.getByRole('navigation', { name: '위치 경로' }).isVisible());
    ok(`${label} 1: GNB "알림" 활성`, (await page.locator('header').getByRole('link', { name: '알림' }).getAttribute('class')).includes('text-brand'));
    const card = page.locator('section[aria-label="알림 이력"]');
    const notice = page.getByText('발생 후 6개월이 지난 알림은 자동 보관됩니다');
    const [cardBox, noticeBox] = [await card.boundingBox(), await notice.boundingBox()];
    ok(`${label} 1/D6: 보관 안내는 카드 아래 12px`, noticeBox && cardBox && Math.abs(noticeBox.y - (cardBox.y + cardBox.height) - 12) <= 1, `${noticeBox?.y} vs ${cardBox?.y + cardBox?.height}`);
  } else {
    ok(`${label} 1: 앱 바 제목`, await page.getByRole('heading', { name: '알림 이력' }).isVisible());
  }
  if (layout === 'mobile') {
    ok(`${label} 1: 하단 탭 "마이" 활성`, (await page.locator('nav').filter({ hasText: '마이' }).getByRole('link', { name: '마이' }).getAttribute('class')).includes('text-brand'));
  }
  if (layout === 'tablet') {
    const pagination = page.getByRole('navigation', { name: '페이지네이션' });
    const notice = page.getByText('발생 후 6개월이 지난 알림은 자동 보관됩니다');
    ok(`${label} 1/D6: 보관 안내는 페이지네이션 아래`, (await notice.boundingBox()).y > (await pagination.boundingBox()).y);
  }
  ok(`${label} 1: 가로 스크롤 없음`, await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
  await context.close();
}

async function scenarioOpen(label, viewport) {
  // 매물 → DTL-01, 뒤로 오면 점 없음
  let { context, page, calls } = await open(viewport);
  await ready(page);
  await row(page, 45).click();
  ok(`${label} 2: 매물 알림 → DTL-01`, await page.waitForURL(/\/complexes\/10$/, { timeout: 5000 }).then(() => true, () => false), page.url());
  ok(`${label} 2: PATCH 1회(45)`, patches(calls).length === 1 && patches(calls)[0].path === '/api/notifications/45/read');
  await page.goBack();
  await ready(page);
  ok(`${label} 2: 뒤로 오면 점 없음`, (await row(page, 45).locator('[data-testid="unread-dot"]').count()) === 0);
  // 읽은 알림 → PATCH 없음
  await row(page, 42).click();
  await page.waitForURL(/\/complexes\/42$/);
  ok(`${label} 2: 읽은 알림은 PATCH 없음`, patches(calls).length === 1);
  await page.goBack();
  await ready(page);
  // 지역 → SRCH-01 지역
  await row(page, 44).click();
  ok(`${label} 2: 지역 알림 → SRCH-01 지역`, await page.waitForURL((u) => new URL(u).pathname === '/search' && new URL(u).searchParams.get('regionCode') === '1111017500', { timeout: 5000 }).then(() => true, () => false), page.url());
  await page.goBack();
  await ready(page);
  // 대상 없음 → 토스트 + SRCH-01
  await row(page, 43).click();
  ok(`${label} 2: 대상 없음 → "현재 확인할 수 없는 매물입니다"`, await page.getByText('현재 확인할 수 없는 매물입니다').waitFor({ timeout: 3000 }).then(() => true, () => false));
  ok(`${label} 2: 대상 없음 → SRCH-01 기본`, new URL(page.url()).pathname === '/search' && new URL(page.url()).search === '');
  ok(`${label} 2: 대상 없음도 읽음 처리`, patches(calls).some((c) => c.path === '/api/notifications/43/read'));
  await context.close();

  // D2 — PATCH 응답을 붙잡아도 이동이 먼저, 500이어도 이동
  for (const patchMode of ['hold', 'fail']) {
    ({ context, page, calls } = await open(viewport, { patchMode }));
    await ready(page);
    await row(page, 45).click();
    ok(`${label} 3: PATCH ${patchMode === 'hold' ? '응답 전' : '500'}에도 DTL-01로 이동`, await page.waitForURL(/\/complexes\/10$/, { timeout: 3000 }).then(() => true, () => false), page.url());
    await context.close();
  }
  // PATCH 500 + 새 탭 열기(화면에 남음) → 점을 되돌린다
  ({ context, page, calls } = await open(viewport, { patchMode: 'fail' }));
  await ready(page);
  await row(page, 45).click({ modifiers: ['Control'] });
  await page.waitForTimeout(600);
  ok(`${label} 3: 새 탭 열기 + PATCH 500 → 이 화면의 점 되돌림`, new URL(page.url()).pathname === '/notifications' && (await row(page, 45).locator('[data-testid="unread-dot"]').count()) === 1);
  await context.close();
}

async function scenarioFilter(label, viewport) {
  const { context, page, calls } = await open(viewport, { path: '/notifications?page=2', delays: { PRICE_CHANGE: 1500 } });
  await ready(page);
  await chip(page, '가격변동').click();
  await chip(page, '신규거래').click();
  await page.waitForTimeout(2200);
  const types = await rows(page).locator('a, button').evaluateAll((els) => els.map((el) => el.querySelector('[data-testid="notification-icon"]')?.className ?? ''));
  const texts = await rows(page).allTextContents();
  ok(`${label} 4: 느린 가격변동 응답이 신규거래 결과를 덮지 않음`, texts.length > 0 && texts.every((t) => t.includes('신규')), texts.slice(0, 3).join(' | '));
  ok(`${label} 4: 아이콘도 신규거래`, types.every((c) => c.includes('e8f2f0')));
  const last = listGets(calls).at(-1);
  ok(`${label} 4: 요청 type=NEW_TRADE, page=0`, new URLSearchParams(last.query).get('type') === 'NEW_TRADE' && new URLSearchParams(last.query).get('page') === '0', last.query);
  ok(`${label} 4: URL type=NEW_TRADE, page 리셋`, new URL(page.url()).search === '?type=NEW_TRADE', page.url());
  ok(`${label} 4: 선택 chip aria-checked`, (await chip(page, '신규거래').getAttribute('aria-checked')) === 'true');
  await chip(page, '전체').click();
  await page.waitForTimeout(400);
  ok(`${label} 4: 전체 → type 없음`, !new URLSearchParams(listGets(calls).at(-1).query).has('type') && new URL(page.url()).search === '');
  await context.close();
}

async function scenarioPaged(label, viewport) {
  let { context, page, calls } = await open(viewport);
  await ready(page);
  await page.getByRole('button', { name: '2', exact: true }).click();
  await page.waitForTimeout(400);
  ok(`${label} 5: 2페이지 → URL page=2, 요청 page=1·size=10`, new URL(page.url()).searchParams.get('page') === '2' && new URLSearchParams(listGets(calls).at(-1).query).get('page') === '1' && new URLSearchParams(listGets(calls).at(-1).query).get('size') === '10');
  const firstId = await rows(page).first().locator('[data-notification-id]').getAttribute('data-notification-id');
  await rows(page).first().locator('a').click();
  await page.waitForURL(/\/complexes\//);
  await page.goBack();
  await ready(page);
  ok(`${label} 5: DTL-01 → 뒤로 → 2페이지`, new URL(page.url()).searchParams.get('page') === '2' && (await page.getByRole('button', { name: '2', exact: true }).getAttribute('aria-current')) === 'page');
  ok(`${label} 5: 같은 행이 맨 위`, (await rows(page).first().locator('[data-notification-id]').getAttribute('data-notification-id')) === firstId);
  await context.close();

  ({ context, page } = await open(viewport, { path: '/notifications?page=9' }));
  await ready(page);
  await page.waitForTimeout(400);
  ok(`${label} 5: 범위 밖 page=9 → 마지막(5)`, new URL(page.url()).searchParams.get('page') === '5', page.url());
  await context.close();

  ({ context, page } = await open(viewport, { path: '/notifications?type=EVERYTHING&page=0' }));
  await ready(page);
  await page.waitForTimeout(300);
  ok(`${label} 5: 모르는 type·page=0 → /notifications`, new URL(page.url()).search === '', page.url());
  await context.close();
}

async function scenarioInfinite(label, viewport) {
  let { context, page, calls } = await open(viewport);
  await ready(page);
  ok(`${label} 6: 첫 요청 page=0·size=20`, new URLSearchParams(listGets(calls)[0].query).get('size') === '20');
  for (let i = 0; i < 6 && (await rows(page).count()) < 45; i++) {
    await page.mouse.wheel(0, 4000);
    await page.waitForTimeout(500);
  }
  ok(`${label} 6: 끝까지 45행`, (await rows(page).count()) === 45, String(await rows(page).count()));
  ok(`${label} 6: 두 번째 요청 page=1`, listGets(calls).some((c) => new URLSearchParams(c.query).get('page') === '1'));
  ok(`${label} 6: 끝 푸터 "총 45건 · 모두 표시됨"`, await page.getByText('총 45건 · 모두 표시됨').isVisible());
  const footer = page.getByText('총 45건 · 모두 표시됨');
  const notice = page.getByText('발생 후 6개월이 지난 알림은 자동 보관됩니다');
  ok(`${label} 6/D6: 보관 안내는 푸터 아래 줄`, (await notice.boundingBox()).y > (await footer.boundingBox()).y);
  const ids = await page.locator('[data-notification-id]').evaluateAll((els) => els.map((el) => el.getAttribute('data-notification-id')));
  ok(`${label} 6: 중복 없음`, new Set(ids).size === ids.length);
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  await page.waitForTimeout(200);
  const tab = page.locator('nav').filter({ hasText: '마이' });
  const tabTop = (await tab.boundingBox()).y;
  ok(`${label} 6: 끝까지 내렸을 때 보관 안내가 하단 탭에 가리지 않음`, (await notice.boundingBox()).y + (await notice.boundingBox()).height <= tabTop, `${(await notice.boundingBox()).y} / ${tabTop}`);
  await context.close();

  ({ context, page, calls } = await open(viewport, { failMorePageOnce: 1 }));
  await ready(page);
  await page.mouse.wheel(0, 4000);
  const retry = page.getByRole('alert').getByRole('button', { name: '다시 시도' });
  ok(`${label} 6: 다음 페이지 실패 → 재시도 버튼`, await retry.waitFor({ timeout: 4000 }).then(() => true, () => false));
  ok(`${label} 6: 실패해도 이미 불러온 20행 유지`, (await rows(page).count()) === 20);
  await retry.click();
  await page.waitForTimeout(600);
  ok(`${label} 6: 재시도 → 40행`, (await rows(page).count()) === 40, String(await rows(page).count()));
  await context.close();
}

async function scenarioEmpty(label, viewport) {
  let { context, page } = await open(viewport, { count: 0 });
  await page.getByText('아직 받은 알림이 없어요').waitFor({ timeout: 5000 });
  ok(`${label} 7: 전체 빈 상태 → chip 숨김`, (await page.getByRole('radiogroup', { name: '알림 유형' }).count()) === 0);
  ok(`${label} 7: 설명 문구(D9)`, await page.getByText('관심 매물·지역에 알림을 설정하면').isVisible());
  const cta = page.getByRole('link', { name: '매물 둘러보기' });
  ok(`${label} 7: CTA → /`, (await cta.getAttribute('href')) === '/');
  await context.close();

  ({ context, page } = await open(viewport, { count: 1, path: '/notifications?type=NEW_TRADE' }));
  await page.getByText('신규거래 알림이 없어요').waitFor({ timeout: 5000 });
  ok(`${label} 7: 유형 필터 빈 상태 → chip 유지`, (await page.getByRole('radiogroup', { name: '알림 유형' }).count()) === 1);
  ok(`${label} 7: 유형 필터 빈 상태 → CTA 없음`, (await page.getByRole('link', { name: '매물 둘러보기' }).count()) === 0);
  await context.close();
}

async function scenarioError(label, viewport) {
  const { context, page } = await open(viewport, { listMode: 'failOnce' });
  const alert = page.getByRole('alert').filter({ hasText: '알림 서버 오류입니다' });
  ok(`${label} 8: 목록 조회 실패 → 서버 문구 배너`, await alert.waitFor({ timeout: 5000 }).then(() => true, () => false));
  await alert.getByRole('button', { name: '다시 시도' }).click();
  ok(`${label} 8: 다시 시도 → 목록`, await ready(page).then(() => true, () => false));
  await context.close();
}

async function scenarioLogin(label, viewport) {
  const { context, page } = await open(viewport, { loggedIn: false, path: '/notifications?type=NEW_TRADE&page=2' });
  ok(`${label} 9: 비로그인 → 로그인 화면`, await page.waitForURL((u) => new URL(u).pathname === '/login', { timeout: 5000 }).then(() => true, () => false));
  await page.getByLabel('이메일').fill('user@test.com');
  await page.getByLabel('비밀번호', { exact: true }).fill('Passw0rd!');
  await page.locator('button[type="submit"]').click();
  ok(`${label} 9: 로그인 후 쿼리 그대로 복귀`, await page.waitForURL((u) => {
    const url = new URL(u);
    return url.pathname === '/notifications' && url.searchParams.get('type') === 'NEW_TRADE';
  }, { timeout: 5000 }).then(() => true, () => false), page.url());
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
  await scenarioOpen(label, viewport);
  await scenarioFilter(label, viewport);
  if (layout === 'mobile') await scenarioInfinite(label, viewport);
  else await scenarioPaged(label, viewport);
  await scenarioEmpty(label, viewport);
  await scenarioError(label, viewport);
  await scenarioLogin(label, viewport);
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
