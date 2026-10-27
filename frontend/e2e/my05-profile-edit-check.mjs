// MY-05 회원정보 수정 — 모바일(390)·태블릿(768)·데스크톱(1280). 백엔드 불필요(route로 상태 있는 가짜 서버).
// 1. 프리필·이메일 읽기 전용(readonly·aria-readonly·안내 문구)·저장 비활성·섹션 접힘·머리(브레드크럼/앱 바)·필드 배치·가로 스크롤 없음
// 2. MY-01 → 닉네임 변경 저장: 요청 본문은 nickname만, 토스트, MY-01로 이동, 새 닉네임(카드·헤더), 이탈 다이얼로그 없음
// 3. 비밀번호 변경: 펼치면 현재 비밀번호 포커스, 체크리스트·충족 안내·확인 일치/불일치, 본문에 확인값 없음,
//    성공 → 토큰 삭제·로그아웃 API 미호출·/login + 안내 토스트, 로그인하면 HOME-01(복귀 목적지 없음)
// 4. 현재 비밀번호 불일치(400) → 필드 아래 문구·aria-invalid·포커스·입력값 유지, 고치면 해제 / 잠금(429) 문구 / 닉네임 서버 검증(400) 문구
// 5. 그 밖의 실패(500) → 배너 + 다시 시도, 입력값 유지
// 6. 이탈 방지: dirty에서 취소·뒤로가기(앱 바/브레드크럼) → 다이얼로그 → 계속 수정은 머묾, 나가기는 MY-01. 변경 없으면 바로 나감. beforeunload
// 7. 섹션을 접으면 비밀번호 입력 초기화(D6)
// 8. 비로그인 → 로그인 → MY-05 복귀
// 9. 닉네임 저장 응답이 오기 전에 GNB·하단 탭으로 떠나면, 늦은 응답이 화면을 MY-01로 끌어오지 않는다(토스트는 보인다)
// 10. 비밀번호 변경 응답이 화면을 떠난 뒤 도착해도 안내 토스트·로컬 토큰 삭제 — 공개 화면(홈)은 그대로 비로그인, 보호 화면(관심목록)은 로그인 화면
// 11. 비밀번호 변경 응답 전에 로그아웃하고 다른 계정(B)으로 로그인하면, 늦은 응답(성공·401·400·500)은 B의 토큰·로그인 상태를
//     건드리지 않고 토스트·오류도 없다. 401이면 인터셉터가 B 토큰으로 재시도·재발급하지 않는다
// 12. 비밀번호 변경 응답 전에 로그아웃하고 비로그인으로 남으면 늦은 성공 응답에 안내 토스트만 보인다
import { chromium } from 'playwright';
import { BASE } from './base.mjs';
import { errBody, okBody } from './mockApi.mjs';

let pass = 0;
let fail = 0;
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}${detail ? ' :: ' + detail : ''}`); }
}

const CURRENT = 'Abcd1234!';
const NEXT = 'Efgh5678!';

// 실제 백엔드처럼 JWT 모양(서명은 검증하지 않는다) — 401 인터셉터가 sub로 계정을 비교하므로 문자열 토큰이면 그 비교가 빠진다.
const fakeJwt = (sub, tag) => `${Buffer.from('{"alg":"HS256"}').toString('base64url')}.${Buffer.from(JSON.stringify({ sub: String(sub), tag })).toString('base64url')}.sig`;
const TOKEN_A = fakeJwt(7, 'a');
const TOKEN_B = fakeJwt(8, 'b');

const OTHER_USER = { userId: 8, email: 'other@homesense.kr', nickname: '다른계정', createdAt: '2026-09-20T09:00:00' };

const browser = await chromium.launch();

/**
 * putMode: 'ok' | 'wrongPassword' | 'locked' | 'nicknameInvalid' | 'failOnce' | 'hold'(db.release()를 부를 때까지 응답 보류).
 * db.release(kind): kind 'ok'(기본) | 'unauthorized'(401 UNAUTHORIZED) | 'wrongPassword'(400) | 'serverError'(500).
 */
async function open(viewport, { path = '/my/profile', putMode = 'ok', loggedIn = true } = {}) {
  const context = await browser.newContext({ viewport });
  if (loggedIn) {
    await context.addInitScript((tokenA) => {
      if (!sessionStorage.getItem('__seeded')) {
        localStorage.setItem('homesense.accessToken', tokenA);
        localStorage.setItem('homesense.refreshToken', 'R');
        sessionStorage.setItem('__seeded', '1');
      }
    }, TOKEN_A);
  }
  const db = { user: { userId: 7, email: 'jiseong@homesense.kr', nickname: '지성', createdAt: '2026-09-15T10:20:30' }, putFailures: putMode === 'failOnce' ? 1 : 0 };
  let releasePut;
  const putHeld = new Promise((resolve) => { releasePut = resolve; });
  db.release = (kind = 'ok') => releasePut(kind);
  const calls = [];
  await context.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const p = url.pathname;
    const method = request.method();
    const body = request.postData();
    calls.push({ method, path: p, body: body ? JSON.parse(body) : null });
    const json = (status, payload) => route.fulfill({ status, contentType: 'application/json', body: payload }).catch(() => {});
    if (p === '/api/auth/login') return json(200, okBody({ accessToken: TOKEN_B, refreshToken: 'R2', expiresIn: 1800 }));
    if (p === '/api/auth/logout') return json(200, okBody(null));
    if (p === '/api/users/me' && method === 'GET') {
      // 로그인 응답의 토큰은 다른 계정(B)이다 — 시나리오 11
      if (request.headers().authorization === `Bearer ${TOKEN_B}`) return json(200, okBody(OTHER_USER));
      return json(200, okBody(db.user));
    }
    if (p === '/api/users/me' && method === 'PUT') {
      const req = JSON.parse(body);
      if (putMode === 'wrongPassword') return json(400, errBody('INVALID_CURRENT_PASSWORD', '현재 비밀번호가 일치하지 않습니다'));
      if (putMode === 'locked') return json(429, errBody('PASSWORD_CONFIRM_LOCKED', '비밀번호 확인에 여러 번 실패했습니다. 잠시 후 다시 시도해주세요'));
      if (putMode === 'nicknameInvalid') {
        return route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({ success: false, error: { code: 'VALIDATION_FAILED', message: '입력값이 유효하지 않습니다', fieldErrors: [{ field: 'nickname', message: '서버가 거부한 닉네임입니다' }] }, timestamp: '2026-10-19T00:00:00' }),
        }).catch(() => {});
      }
      if (putMode === 'hold') {
        const kind = await putHeld;
        if (kind === 'unauthorized') return json(401, errBody('UNAUTHORIZED', '인증이 필요합니다'));
        if (kind === 'wrongPassword') return json(400, errBody('INVALID_CURRENT_PASSWORD', '현재 비밀번호가 일치하지 않습니다'));
        if (kind === 'serverError') return json(500, errBody('INTERNAL_SERVER_ERROR', '서버 오류가 발생했습니다'));
      }
      if (db.putFailures > 0) { db.putFailures--; return json(500, errBody('INTERNAL_SERVER_ERROR', '서버 오류가 발생했습니다')); }
      if (req.nickname !== undefined) db.user = { ...db.user, nickname: req.nickname };
      return json(200, okBody(db.user));
    }
    if (p === '/api/favorites/properties') return json(200, okBody([]));
    if (p === '/api/notifications') return json(200, okBody([], { page: 0, size: 3, totalElements: 0, totalPages: 0 }));
    return json(404, errBody('NOT_FOUND', 'mock 없음'));
  });
  const page = await context.newPage();
  await page.goto(`${BASE}${path}`);
  return { context, page, calls, db };
}

const puts = (calls) => calls.filter((c) => c.method === 'PUT' && c.path === '/api/users/me');
const field = (page, label) => page.getByLabel(label, { exact: true });
const saveButton = (page) => page.getByRole('button', { name: /^저장/ });
const toggle = (page) => page.getByRole('button', { name: /비밀번호 변경/ });
const ready = (page) => field(page, '닉네임').waitFor({ timeout: 8000 });
const waitPath = (page, path) => page.waitForURL((u) => new URL(u).pathname === path, { timeout: 5000 }).then(() => true, () => false);
const leaveDialog = (page) => page.getByRole('alertdialog', { name: '변경 사항을 저장하지 않고 나갈까요?' });
const focusedId = (page) => page.evaluate(() => document.activeElement?.id ?? '');

async function fillPasswords(page, current, next, confirm) {
  await toggle(page).click();
  await field(page, '현재 비밀번호').fill(current);
  await field(page, '새 비밀번호').fill(next);
  await field(page, '새 비밀번호 확인').fill(confirm);
}

async function scenarioRender(label, viewport, layout) {
  const { context, page } = await open(viewport);
  await ready(page);
  ok(`${label} 1: 닉네임 프리필`, (await field(page, '닉네임').inputValue()) === '지성');
  const email = field(page, '이메일');
  ok(`${label} 1: 이메일 프리필·readonly·aria-readonly`, (await email.inputValue()) === 'jiseong@homesense.kr'
    && (await email.getAttribute('readonly')) !== null && (await email.getAttribute('aria-readonly')) === 'true');
  ok(`${label} 1/D1: 이메일 안내 문구가 설명으로 연결`, (await email.evaluate((el) => (el.getAttribute('aria-describedby') ?? '').split(' ').map((id) => document.getElementById(id)?.textContent).join(' ')))
    .includes('이메일(로그인 ID)은 현재 변경할 수 없습니다'));
  await email.fill('other@x.kr').catch(() => {});
  ok(`${label} 1/D1: 이메일은 입력해도 바뀌지 않음`, (await email.inputValue()) === 'jiseong@homesense.kr');
  ok(`${label} 1: 변경 없으면 저장 비활성`, await saveButton(page).isDisabled());
  ok(`${label} 1: 섹션 접힘(aria-expanded=false, "펼치기")`, (await toggle(page).getAttribute('aria-expanded')) === 'false' && (await toggle(page).textContent()).includes('펼치기'));
  ok(`${label} 1: 비밀번호 필드 없음(접힘)`, (await field(page, '현재 비밀번호').count()) === 0);
  ok(`${label} 1: 문서 제목`, (await page.title()) === '회원정보 수정 | HomeSense');
  const h1 = page.getByRole('heading', { name: '회원정보 수정', level: 1 });
  ok(`${label} 1: H1 하나`, (await h1.count()) === 1);
  const breadcrumb = page.getByRole('navigation', { name: '위치 경로' });
  const appBarBack = page.getByRole('button', { name: '뒤로 가기' });
  const nickBox = await field(page, '닉네임').boundingBox();
  const emailBox = await email.boundingBox();
  const notice = page.getByText('변경 사항이 없으면 저장이 비활성화됩니다.');
  if (layout === 'desktop') {
    ok(`${label} 1: 브레드크럼 있음, 앱 바 없음`, (await breadcrumb.count()) === 1 && (await appBarBack.count()) === 0);
    ok(`${label} 1: 기본 정보 2열(같은 줄)`, Math.abs(nickBox.y - emailBox.y) < 2 && emailBox.x > nickBox.x + nickBox.width);
    ok(`${label} 1: 안내 문구 보임`, await notice.isVisible());
    const card = await page.locator('form').boundingBox();
    ok(`${label} 1: 콘텐츠 폭 612`, Math.abs(card.width - 612) <= 1, String(card.width));
  } else {
    ok(`${label} 1: 앱 바 있음, 브레드크럼 없음`, (await breadcrumb.count()) === 0 && (await appBarBack.count()) === 1);
    ok(`${label} 1: 기본 정보 1열(세로)`, emailBox.y > nickBox.y + nickBox.height);
    ok(`${label} 1: 안내 문구 숨김`, !(await notice.isVisible()));
    const cancel = await page.getByRole('button', { name: '취소' }).boundingBox();
    const save = await saveButton(page).boundingBox();
    ok(`${label} 1: 취소·저장 가로 균등`, Math.abs(cancel.width - save.width) <= 1 && Math.abs(cancel.y - save.y) < 2, `${cancel.width}/${save.width}`);
    if (layout === 'mobile') {
      await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
      await page.waitForTimeout(150);
      const tab = page.locator('nav.fixed');
      ok(`${label} 1: 맨 아래에서 저장 버튼이 하단 탭에 가리지 않음`, (await saveButton(page).boundingBox()).y + save.height <= (await tab.boundingBox()).y + 0.5);
      ok(`${label} 1: 하단 탭 "마이" 활성`, (await tab.getByRole('link', { name: '마이' }).getAttribute('class')).includes('text-brand'));
    }
  }
  const input = await field(page, '닉네임').evaluate((el) => ({ h: el.getBoundingClientRect().height, bg: getComputedStyle(el).backgroundColor, r: getComputedStyle(el).borderTopLeftRadius }));
  ok(`${label} 1: 입력 47px·#f9fafb·radius 14`, Math.round(input.h) === 47 && input.bg === 'rgb(249, 250, 251)' && input.r === '14px', JSON.stringify(input));
  ok(`${label} 1: 가로 스크롤 없음`, await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
  await context.close();
}

async function scenarioNickname(label, viewport, layout) {
  const { context, page, calls } = await open(viewport, { path: '/my' });
  await page.getByRole('link', { name: '회원정보 수정' }).click();
  await ready(page);
  ok(`${label} 2: MY-01 → MY-05`, new URL(page.url()).pathname === '/my/profile');
  await field(page, '닉네임').fill('가');
  ok(`${label} 2: 1자 닉네임 → 필드 오류·저장 비활성`, (await field(page, '닉네임').getAttribute('aria-invalid')) === 'true' && await saveButton(page).isDisabled());
  await field(page, '닉네임').fill('  새닉네임 ');
  ok(`${label} 2: 닉네임 변경 → 저장 활성`, await saveButton(page).isEnabled());
  if (layout === 'desktop') ok(`${label} 2: 안내 "저장하지 않은 변경 사항이 있습니다."`, await page.getByText('저장하지 않은 변경 사항이 있습니다.').isVisible());
  await saveButton(page).click();
  ok(`${label} 2: MY-01로 이동`, await waitPath(page, '/my'));
  ok(`${label} 2: 토스트`, await page.getByText('회원정보가 저장되었습니다').isVisible());
  const body = puts(calls).at(-1)?.body;
  ok(`${label} 2: 요청 본문은 nickname만(trim)`, JSON.stringify(body) === JSON.stringify({ nickname: '새닉네임' }), JSON.stringify(body));
  ok(`${label} 2: 이탈 다이얼로그 없음`, (await leaveDialog(page).count()) === 0);
  ok(`${label} 2: MY-01 프로필에 새 닉네임`, await page.locator(':text-is("새닉네임"):visible').first().waitFor({ timeout: 5000 }).then(() => true, () => false));
  ok(`${label} 2: 헤더 계정 메뉴에 새 닉네임`, (await page.getByRole('button', { name: '새닉네임 계정 메뉴' }).count()) >= 1);
  await context.close();
}

async function scenarioPassword(label, viewport) {
  const { context, page, calls } = await open(viewport);
  await ready(page);
  await toggle(page).click();
  ok(`${label} 3: 펼치면 "접기"·aria-expanded=true`, (await toggle(page).getAttribute('aria-expanded')) === 'true' && (await toggle(page).textContent()).includes('접기'));
  ok(`${label} 3: 펼치면 현재 비밀번호로 포커스`, (await focusedId(page)) === (await field(page, '현재 비밀번호').getAttribute('id')));
  await field(page, '현재 비밀번호').fill(CURRENT);
  ok(`${label} 3: 현재 비밀번호만 → 저장 비활성`, await saveButton(page).isDisabled());
  await field(page, '새 비밀번호').fill('Efgh5678');
  ok(`${label} 3: 입력 시작부터 체크리스트`, await page.getByText('특수문자 포함').isVisible());
  ok(`${label} 3: 정책 미충족 → 충족 안내 없음`, (await page.getByText('새 비밀번호 조건을 모두 충족합니다.').count()) === 0);
  await field(page, '새 비밀번호').fill(NEXT);
  ok(`${label} 3: 정책 충족 → 충족 안내`, await page.getByText('새 비밀번호 조건을 모두 충족합니다.').isVisible());
  await field(page, '새 비밀번호 확인').fill('Efgh5678?');
  ok(`${label} 3: 확인 불일치 문구·저장 비활성`, await page.getByText('비밀번호가 일치하지 않습니다').isVisible() && await saveButton(page).isDisabled());
  await field(page, '새 비밀번호 확인').fill(NEXT);
  ok(`${label} 3: 확인 일치 문구·저장 활성`, await page.getByText('비밀번호가 일치합니다').isVisible() && await saveButton(page).isEnabled());
  const eye = page.getByRole('button', { name: '비밀번호 표시' }).first();
  await eye.click();
  ok(`${label} 3: 표시 토글 → type=text·aria-pressed=true·라벨 "숨기기"`, (await field(page, '현재 비밀번호').getAttribute('type')) === 'text'
    && (await page.getByRole('button', { name: '비밀번호 숨기기' }).first().getAttribute('aria-pressed')) === 'true');
  await saveButton(page).click();
  ok(`${label} 3: 로그인 화면으로`, await waitPath(page, '/login'));
  ok(`${label} 3: 안내 토스트`, await page.getByText('비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.').isVisible());
  const body = puts(calls).at(-1)?.body;
  ok(`${label} 3: 요청 본문은 비밀번호 두 값만(확인값·닉네임 없음)`, JSON.stringify(body) === JSON.stringify({ currentPassword: CURRENT, newPassword: NEXT }), JSON.stringify(body));
  ok(`${label} 3: 로그아웃 API 미호출`, !calls.some((c) => c.path === '/api/auth/logout'));
  ok(`${label} 3: 로컬 토큰 삭제`, await page.evaluate(() => localStorage.getItem('homesense.accessToken') === null && localStorage.getItem('homesense.refreshToken') === null));
  await page.getByLabel('이메일').fill('jiseong@homesense.kr');
  await page.getByLabel('비밀번호', { exact: true }).fill(NEXT);
  await page.locator('button[type="submit"]').click();
  ok(`${label} 3: 다시 로그인하면 HOME-01(복귀 목적지 없음)`, await waitPath(page, '/'));
  await context.close();
}

async function scenarioErrors(label, viewport) {
  let { context, page } = await open(viewport, { putMode: 'wrongPassword' });
  await ready(page);
  await field(page, '닉네임').fill('새닉네임');
  await fillPasswords(page, 'Wrong123!', NEXT, NEXT);
  await saveButton(page).click();
  const current = field(page, '현재 비밀번호');
  const error = page.getByRole('alert').filter({ hasText: '현재 비밀번호가 일치하지 않습니다' });
  ok(`${label} 4: 불일치 → 필드 아래 문구(role=alert)`, await error.waitFor({ timeout: 5000 }).then(() => true, () => false));
  ok(`${label} 4: aria-invalid·aria-describedby`, (await current.getAttribute('aria-invalid')) === 'true'
    && (await current.getAttribute('aria-describedby')).includes(await error.getAttribute('id')));
  ok(`${label} 4: 현재 비밀번호로 포커스`, (await focusedId(page)) === (await current.getAttribute('id')));
  ok(`${label} 4: 입력값 유지`, (await current.inputValue()) === 'Wrong123!' && (await field(page, '새 비밀번호').inputValue()) === NEXT && (await field(page, '닉네임').inputValue()) === '새닉네임');
  ok(`${label} 4: 머문다(이동 없음)`, new URL(page.url()).pathname === '/my/profile');
  await current.press('End');
  await current.type('x');
  ok(`${label} 4: 고치면 오류 해제`, (await current.getAttribute('aria-invalid')) === null && (await error.count()) === 0);
  await context.close();

  ({ context, page } = await open(viewport, { putMode: 'locked' }));
  await ready(page);
  await fillPasswords(page, CURRENT, NEXT, NEXT);
  await saveButton(page).click();
  ok(`${label} 4: 잠금(429) → 현재 비밀번호 아래 서버 문구`, await page.getByRole('alert').filter({ hasText: '비밀번호 확인에 여러 번 실패했습니다. 잠시 후 다시 시도해주세요' })
    .waitFor({ timeout: 5000 }).then(() => true, () => false) && (await field(page, '현재 비밀번호').getAttribute('aria-invalid')) === 'true');
  await context.close();

  ({ context, page } = await open(viewport, { putMode: 'nicknameInvalid' }));
  await ready(page);
  await field(page, '닉네임').fill('새닉네임');
  await saveButton(page).click();
  ok(`${label} 4: 닉네임 서버 검증 → 닉네임 아래 서버 문구`, await page.getByRole('alert').filter({ hasText: '서버가 거부한 닉네임입니다' })
    .waitFor({ timeout: 5000 }).then(() => true, () => false) && (await field(page, '닉네임').getAttribute('aria-invalid')) === 'true');
  await context.close();

  ({ context, page } = await open(viewport, { putMode: 'failOnce' }));
  await ready(page);
  await field(page, '닉네임').fill('새닉네임');
  await saveButton(page).click();
  const banner = page.getByRole('alert').filter({ hasText: '서버 오류가 발생했습니다' });
  ok(`${label} 5: 500 → 배너(서버 문구)`, await banner.waitFor({ timeout: 5000 }).then(() => true, () => false));
  ok(`${label} 5: 입력값 유지·머묾`, (await field(page, '닉네임').inputValue()) === '새닉네임' && new URL(page.url()).pathname === '/my/profile');
  await banner.getByRole('button', { name: '다시 시도' }).click();
  ok(`${label} 5: 다시 시도 → 저장되어 MY-01`, await waitPath(page, '/my'));
  await context.close();
}

async function scenarioLeave(label, viewport, layout) {
  let { context, page } = await open(viewport, { path: '/my' });
  await page.getByRole('link', { name: '회원정보 수정' }).click();
  await ready(page);
  await page.getByRole('button', { name: '취소' }).click();
  ok(`${label} 6: 변경 없으면 취소 → 바로 MY-01`, await waitPath(page, '/my') && (await leaveDialog(page).count()) === 0);
  await page.getByRole('link', { name: '회원정보 수정' }).click();
  await ready(page);
  await field(page, '닉네임').fill('새닉네임');
  await page.getByRole('button', { name: '취소' }).click();
  const dialog = leaveDialog(page);
  ok(`${label} 6: dirty에서 취소 → 다이얼로그(본문)`, await dialog.isVisible() && (await dialog.textContent()).includes('저장하지 않은 변경사항이 있습니다.'));
  await dialog.getByRole('button', { name: '계속 수정' }).click();
  ok(`${label} 6: 계속 수정 → 머묾·값 유지`, (await dialog.count()) === 0 && new URL(page.url()).pathname === '/my/profile' && (await field(page, '닉네임').inputValue()) === '새닉네임');
  const back = layout === 'desktop'
    ? page.getByRole('navigation', { name: '위치 경로' }).getByRole('button', { name: '마이페이지' })
    : page.getByRole('button', { name: '뒤로 가기' });
  await back.click();
  ok(`${label} 6: dirty에서 ${layout === 'desktop' ? '브레드크럼' : '앱 바 뒤로가기'} → 다이얼로그`, await dialog.isVisible());
  await dialog.getByRole('button', { name: '나가기' }).click();
  ok(`${label} 6: 나가기 → MY-01`, await waitPath(page, '/my'));
  await context.close();

  // beforeunload — dirty일 때만 묻는다.
  ({ context, page } = await open(viewport));
  await ready(page);
  let asked = false;
  page.on('dialog', async (d) => { if (d.type() === 'beforeunload') asked = true; await d.dismiss().catch(() => {}); });
  await page.close({ runBeforeUnload: true });
  await new Promise((r) => setTimeout(r, 300));
  ok(`${label} 6: 변경 없으면 beforeunload 묻지 않음`, !asked);
  page = await context.newPage();
  await page.goto(`${BASE}/my/profile`);
  await ready(page);
  await field(page, '닉네임').fill('새닉네임');
  asked = false;
  page.on('dialog', async (d) => { if (d.type() === 'beforeunload') asked = true; await d.dismiss().catch(() => {}); });
  await page.close({ runBeforeUnload: true });
  for (let i = 0; i < 20 && !asked; i++) await new Promise((r) => setTimeout(r, 100));
  ok(`${label} 6: dirty면 beforeunload 확인`, asked);
  await context.close();
}

async function scenarioCollapse(label, viewport) {
  const { context, page } = await open(viewport);
  await ready(page);
  await fillPasswords(page, CURRENT, NEXT, NEXT);
  ok(`${label} 7: 입력 후 저장 활성`, await saveButton(page).isEnabled());
  await toggle(page).click();
  ok(`${label} 7: 접으면 필드 사라지고 저장 비활성(변경 없음)`, (await field(page, '현재 비밀번호').count()) === 0 && await saveButton(page).isDisabled());
  await toggle(page).click();
  ok(`${label} 7: 다시 펼치면 세 필드 비어 있음`, (await field(page, '현재 비밀번호').inputValue()) === '' && (await field(page, '새 비밀번호').inputValue()) === ''
    && (await field(page, '새 비밀번호 확인').inputValue()) === '');
  await context.close();
}

async function scenarioLogin(label, viewport) {
  const { context, page } = await open(viewport, { loggedIn: false });
  ok(`${label} 8: 비로그인 → 로그인 화면`, await waitPath(page, '/login'));
  await page.getByLabel('이메일').fill('jiseong@homesense.kr');
  await page.getByLabel('비밀번호', { exact: true }).fill(CURRENT);
  await page.locator('button[type="submit"]').click();
  ok(`${label} 8: 로그인 후 MY-05 복귀`, await waitPath(page, '/my/profile'));
  await context.close();
}

async function scenarioLeaveWhileSaving(label, viewport) {
  const { context, page, calls, db } = await open(viewport, { putMode: 'hold' });
  await ready(page);
  await field(page, '닉네임').fill('늦은닉네임');
  await saveButton(page).click();
  await page.waitForFunction(() => document.querySelector('button[aria-busy="true"]') !== null);
  // GNB "관심목록"(md 이상)·하단 탭 "찜"(모바일) — 이 화면이 막지 못하는 이동(D7)
  await page.locator('a[href="/favorites"]:visible').first().click();
  ok(`${label} 9: 저장 중 관심목록으로 이동`, await waitPath(page, '/favorites'));
  db.release();
  ok(`${label} 9: 늦은 응답 뒤 토스트`, await page.getByText('회원정보가 저장되었습니다').waitFor({ timeout: 5000 }).then(() => true, () => false));
  await page.waitForTimeout(500);
  ok(`${label} 9: 늦은 응답이 MY-01로 끌어오지 않음`, new URL(page.url()).pathname === '/favorites', page.url());
  ok(`${label} 9: PUT 1회`, puts(calls).length === 1);
  await context.close();
}

async function scenarioPasswordAfterLeave(label, viewport) {
  for (const { name, link, expectedPath } of [
    { name: '공개 화면(홈)', link: 'header a[href="/"]', expectedPath: '/' },
    { name: '보호 화면(관심목록)', link: 'a[href="/favorites"]:visible', expectedPath: '/login' },
  ]) {
    const { context, page, calls, db } = await open(viewport, { putMode: 'hold' });
    await ready(page);
    await fillPasswords(page, CURRENT, NEXT, NEXT);
    await saveButton(page).click();
    await page.waitForFunction(() => document.querySelector('button[aria-busy="true"]') !== null);
    await page.locator(link).first().click();
    const leftPath = name.startsWith('공개') ? '/' : '/favorites';
    ok(`${label} 10 ${name}: 저장 중 이동`, await waitPath(page, leftPath));
    db.release();
    ok(`${label} 10 ${name}: 늦은 응답 뒤 안내 토스트`,
      await page.getByText('비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.').waitFor({ timeout: 5000 }).then(() => true, () => false));
    ok(`${label} 10 ${name}: 로컬 토큰 삭제`,
      await page.waitForFunction(() => localStorage.getItem('homesense.accessToken') === null && localStorage.getItem('homesense.refreshToken') === null, null, { timeout: 5000 }).then(() => true, () => false));
    ok(`${label} 10 ${name}: 경로 ${expectedPath}`, await waitPath(page, expectedPath), page.url());
    if (expectedPath === '/') {
      await page.waitForTimeout(500);
      ok(`${label} 10 ${name}: 홈에 머묾(MY-01·로그인 화면으로 끌려가지 않음)`, new URL(page.url()).pathname === '/', page.url());
      ok(`${label} 10 ${name}: 헤더가 비로그인(로그인 링크)`, (await page.getByRole('banner').locator('a[href="/login"]').count()) >= 1);
    }
    ok(`${label} 10 ${name}: PUT 1회·로그아웃 API 미호출`, puts(calls).length === 1 && !calls.some((c) => c.path === '/api/auth/logout'));
    await context.close();
  }
}

async function logOutFromHome(page) {
  await page.getByRole('banner').locator('a[href="/"]').click();
  await waitPath(page, '/');
  await page.getByRole('banner').getByRole('button', { name: /계정 메뉴/ }).click();
  await page.getByRole('menuitem', { name: '로그아웃' }).click();
  await page.getByRole('banner').locator('a[href="/login"]').first().waitFor({ timeout: 5000 });
}

// release: 늦게 도착하는 응답 — 'ok'(성공), 'unauthorized'(401 — 인터셉터가 B 토큰으로 재시도·재발급하지 않아야 한다),
// 'wrongPassword'(400), 'serverError'(500)
async function scenarioPasswordAfterAccountSwitch(label, viewport, release = 'ok') {
  label = `${label} 11[${release}]`;
  const { context, page, calls, db } = await open(viewport, { putMode: 'hold' });
  await ready(page);
  await fillPasswords(page, CURRENT, NEXT, NEXT);
  await saveButton(page).click();
  await page.waitForFunction(() => document.querySelector('button[aria-busy="true"]') !== null);
  await page.evaluate(() => { window.__sameDocument = true; });
  await page.getByRole('banner').locator('a[href="/"]').click();
  await waitPath(page, '/');
  await page.getByRole('banner').getByRole('button', { name: /계정 메뉴/ }).click();
  await page.getByRole('menuitem', { name: '로그아웃' }).click();
  // 앱 안 링크로 이동한다 — page.goto는 새로고침이라 보류 중인 PUT과 그 뒤처리가 함께 사라진다.
  await page.getByRole('banner').locator('a[href="/login"]').first().click();
  await waitPath(page, '/login');
  await page.getByLabel('이메일').fill(OTHER_USER.email);
  await page.getByLabel('비밀번호', { exact: true }).fill('Other123!');
  await page.locator('button[type="submit"]').click();
  ok(`${label}: B로 로그인 → 홈`, await waitPath(page, '/'));
  await page.getByRole('banner').getByRole('button', { name: /다른계정 계정 메뉴/ }).waitFor({ timeout: 5000 }).catch(() => {});
  ok(`${label}: 응답 보류 중(새로고침 없이 같은 문서)`, puts(calls).length === 1 && await page.evaluate(() => window.__sameDocument === true));
  db.release(release);
  await page.waitForTimeout(800);
  ok(`${label}: 늦은 응답 뒤에도 B의 토큰 유지`,
    await page.evaluate((tokenB) => localStorage.getItem('homesense.accessToken') === tokenB && localStorage.getItem('homesense.refreshToken') === 'R2', TOKEN_B));
  ok(`${label}: 헤더가 B로 로그인된 채`, (await page.getByRole('banner').getByRole('button', { name: /다른계정 계정 메뉴/ }).count()) === 1);
  ok(`${label}: 비밀번호 변경 안내 토스트 없음`, (await page.getByText('비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.').count()) === 0);
  ok(`${label}: 홈에 머묾`, new URL(page.url()).pathname === '/', page.url());
  ok(`${label}: PUT 1회(B 토큰으로 재시도 없음)`, puts(calls).length === 1);
  ok(`${label}: 토큰 재발급 요청 없음`, calls.every((c) => c.path !== '/api/auth/refresh'));
  ok(`${label}: 오류 토스트·배너 없음`, (await page.getByText(/일치하지 않습니다|서버 오류|일시적인 오류|인증이 필요합니다/).count()) === 0);
  await context.close();
}

// 12. 비밀번호 변경 응답 전에 로그아웃하고 비로그인으로 남으면, 늦은 성공 응답에 안내 토스트만 보인다(세션 정리·이동 없음)
async function scenarioPasswordAfterLogout(label, viewport) {
  const { context, page, calls, db } = await open(viewport, { putMode: 'hold' });
  await ready(page);
  await fillPasswords(page, CURRENT, NEXT, NEXT);
  await saveButton(page).click();
  await page.waitForFunction(() => document.querySelector('button[aria-busy="true"]') !== null);
  await logOutFromHome(page);
  const logoutCalls = calls.filter((c) => c.path === '/api/auth/logout').length;
  db.release();
  const toast = page.getByText('비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.');
  ok(`${label} 12: 로그아웃 상태로 남으면 안내 토스트 표시`, await toast.waitFor({ timeout: 3000 }).then(() => true, () => false));
  ok(`${label} 12: 홈에 머묾`, new URL(page.url()).pathname === '/', page.url());
  ok(`${label} 12: 토큰 없음`, await page.evaluate(() => localStorage.getItem('homesense.accessToken') === null));
  ok(`${label} 12: 로그아웃 API 추가 호출 없음`, calls.filter((c) => c.path === '/api/auth/logout').length === logoutCalls);
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
  await scenarioNickname(label, viewport, layout);
  await scenarioPassword(label, viewport);
  await scenarioErrors(label, viewport);
  await scenarioLeave(label, viewport, layout);
  await scenarioCollapse(label, viewport);
  await scenarioLogin(label, viewport);
  await scenarioLeaveWhileSaving(label, viewport);
  await scenarioPasswordAfterLeave(label, viewport);
  for (const release of ['ok', 'unauthorized', 'wrongPassword', 'serverError']) {
    await scenarioPasswordAfterAccountSwitch(label, viewport, release);
  }
  await scenarioPasswordAfterLogout(label, viewport);
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
