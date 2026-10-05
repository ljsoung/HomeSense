// UIC-03 SearchBar 자동완성 — 이미 보낸 요청의 늦은 응답이 최신 상태를 덮어쓰지 않는지, 입력을 바꾼 직후
// 이전 검색어의 후보를 고를 수 없는지(4번) 검증한다.
// 로컬 백엔드는 응답이 빨라 경합이 저절로 재현되지 않으므로, page.route로 특정 query의 응답을
// "게이트"에 묶어 두고 원하는 시점에 풀어 순서를 확정적으로 만든다(고정 지연보다 결정적이다 —
// 디바운스 300ms 창 안에 늦은 응답이 도착하도록 정확히 맞출 수 있다).
// 실 백엔드(8080) 필요. BASE로 dev 서버 지정.
import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

function deferred() {
  let resolve;
  const promise = new Promise((r) => { resolve = r; });
  return { promise, resolve };
}

/**
 * `heldQuery` 요청을 가로채 게이트가 풀릴 때까지 응답을 붙잡는다. 페이지가 그 사이 요청을
 * abort했는지는 route.fulfill의 성패가 아니라(Playwright는 이미 취소된 요청에 fulfill해도 예외를
 * 던지지 않는다) 브라우저의 requestfailed 이벤트로 판정한다.
 */
async function holdQuery(page, heldQuery) {
  const arrived = deferred();
  const gate = deferred();
  const outcome = { aborted: false };
  const isHeld = (url) => new URL(url).searchParams.get('query') === heldQuery;
  page.on('requestfailed', (request) => {
    if (request.url().includes('/api/regions') && isHeld(request.url())) outcome.aborted = true;
  });
  await page.route('**/api/regions?query=*', async (route) => {
    if (!isHeld(route.request().url())) {
      await route.continue();
      return;
    }
    arrived.resolve();
    await gate.promise;
    try {
      const response = await route.fetch();
      await route.fulfill({ response });
    } catch {
      // 페이지가 닫히는 등으로 fulfill할 수 없으면 무시한다.
    }
  });
  return { arrived: arrived.promise, release: gate.resolve, outcome };
}

/** 옵션이 렌더될 때마다 "그 순간의 입력값 + 옵션 텍스트"를 기록한다(잠깐 스쳐 간 오염도 잡는다). */
async function recordOptionSnapshots(page) {
  await page.evaluate(() => {
    window.__optionSnapshots = [];
    const take = () => {
      const input = document.querySelector('input[role="combobox"]');
      const options = [...document.querySelectorAll('[role="option"]')].map((o) => o.textContent ?? '');
      if (options.length) window.__optionSnapshots.push({ value: input?.value ?? '', options });
    };
    new MutationObserver(take).observe(document.body, { childList: true, subtree: true, characterData: true });
  });
}

async function newHomePage(browser) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto(`${BASE}/`);
  await page.waitForTimeout(500);
  return { context, page, errors, input: page.locator('input[role="combobox"]').first() };
}

const browser = await chromium.launch();

// 1) 덮어쓰기 + abort 부작용 — "수원" 응답을 붙잡아 둔 채 "안성"으로 바꾸고, 안성의 디바운스
//    창(300ms) 안에서 수원 응답을 풀어 준다. 수정 전에는 이 응답이 입력값 "안성" 아래에 수원
//    후보를 띄웠다(다음 요청을 보낼 때에야 이전 요청을 abort했기 때문).
{
  const { context, page, errors, input } = await newHomePage(browser);
  const held = await holdQuery(page, '수원');
  await recordOptionSnapshots(page);

  await input.click();
  await input.fill('수원');
  await held.arrived; // 디바운스가 끝나 수원 요청이 실제로 나감
  await input.fill('안성');
  held.release(); // 안성 요청이 나가기 전(디바운스 창 안)에 수원 응답을 풀어 줌

  await page.waitForSelector('[role="option"]', { timeout: 5000 });
  await page.waitForTimeout(1200); // 늦은 응답이 있다면 반영될 시간을 충분히 준다

  const snapshots = await page.evaluate(() => window.__optionSnapshots);
  const contaminated = snapshots.filter((s) => s.value === '안성' && s.options.some((o) => o.includes('수원')));
  ok('입력이 "안성"인 동안 수원 후보가 한 번도 표시되지 않음', contaminated.length === 0);

  const finalOptions = await page.locator('[role="option"]').allTextContents();
  ok('최종 목록이 비어 있지 않음(취소된 요청이 목록을 비우지 않음)', finalOptions.length > 0);
  ok('최종 목록에 안성 후보만 남음', finalOptions.length > 0 && finalOptions.every((t) => t.includes('안성')));
  ok('값이 바뀌는 즉시 수원 요청이 취소됨(requestfailed)', held.outcome.aborted);
  ok('콘솔 pageerror 없음', errors.length === 0);
  await context.close();
}

// 2) blur 후 재오픈 — 수원 응답을 붙잡은 채 입력 밖을 클릭해 목록을 닫고(120ms 지연 닫힘 이후),
//    그 뒤 응답을 풀어 준다. 수정 전에는 blur가 디바운스 타이머만 지워, 이미 나간 요청의 응답이
//    포커스 없는 상태에서 목록을 다시 열었다.
{
  const { context, page, errors, input } = await newHomePage(browser);
  const held = await holdQuery(page, '수원');

  await input.click();
  await input.fill('수원');
  await held.arrived;
  await page.locator('h1').first().click(); // 입력 밖 클릭 → blur
  await page.waitForTimeout(300); // 120ms 닫힘 타이머가 끝난 뒤
  ok('blur 후 입력에 포커스가 없음', !(await input.evaluate((el) => el === document.activeElement)));
  held.release();
  await page.waitForTimeout(1200);

  ok('지연 응답 도착 후에도 목록이 닫혀 있음', (await page.locator('[role="listbox"]').count()) === 0);
  ok('blur로 수원 요청이 취소됨(requestfailed)', held.outcome.aborted);
  ok('콘솔 pageerror 없음(blur)', errors.length === 0);
  await context.close();
}

// 3) 정상 경로 회귀 — 지연 없이 입력 → 후보 표시 → 마우스 클릭으로 선택. 옵션의 mousedown이
//    preventDefault라 blur(120ms 지연 닫힘)보다 click이 먼저 처리돼야 한다.
{
  const { context, page, errors, input } = await newHomePage(browser);
  await input.click();
  await input.fill('수원시 장안구');
  await page.waitForSelector('[role="option"]', { timeout: 5000 });
  const first = page.locator('[role="option"]').first();
  const label = (await first.textContent()) ?? '';
  ok('정상 입력 시 후보가 표시됨', label.includes('장안구'));
  await first.click();
  await page.waitForURL(/\/search\?/, { timeout: 5000 });
  ok('마우스 클릭으로 선택 시 regionCode로 이동', page.url().includes('regionCode='));
  ok('콘솔 pageerror 없음(정상 경로)', errors.length === 0);
  await context.close();
}

// 4) 입력을 바꾼 직후(다음 응답 전) 이전 검색어의 후보를 고를 수 없다(Codex P2). "수원시" 응답을 붙잡아
//    요청 중 상태를 만든다. 수정 전에는 "수원" 후보가 그대로 떠 있고 활성 후보도 남아, Enter가 수원 후보의
//    regionCode로 검색했다.
{
  const { context, page, errors, input } = await newHomePage(browser);
  const held = await holdQuery(page, '수원시');
  await input.click();
  await input.fill('수원');
  await page.waitForSelector('[role="option"]', { timeout: 5000 });
  await input.press('ArrowDown');
  ok('바꾸기 전 활성 후보 있음', Boolean(await input.getAttribute('aria-activedescendant')));

  await input.fill('수원시');
  ok('입력을 바꾼 직후 이전 후보가 보이지 않음(클릭 불가)', (await page.locator('[role="option"]').count()) === 0);
  ok('입력을 바꾼 직후 활성 후보 초기화', (await input.getAttribute('aria-activedescendant')) === null);
  await input.press('Enter');
  await page.waitForURL(/\/search\?/, { timeout: 5000 });
  const params = new URL(page.url()).searchParams;
  ok('Enter는 이전 후보(regionCode)가 아니라 지금 입력(keyword)으로 검색', !params.has('regionCode') && params.get('keyword') === '수원시');
  held.release();
  ok('콘솔 pageerror 없음(이전 후보)', errors.length === 0);
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed (total: ${pass + fail})`);
process.exit(fail ? 1 : 0);
