import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const browser = await chromium.launch();

// 1) 자동완성 키보드 네비게이션 — ArrowDown/Up으로 하이라이트 이동, Enter로 선택, Esc로 닫기,
//    아무 것도 고르지 않고 Enter 시 자유 텍스트 검색으로 폴백
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/`);
  await page.waitForTimeout(500);
  const input = page.locator('input[role="combobox"]').first();
  await input.click();
  await input.fill('수원');
  await page.waitForSelector('[role="listbox"]', { timeout: 5000 });
  const options = page.locator('[role="option"]');
  const count = await options.count();
  ok('자동완성 목록이 뜸(2자 이상 입력)', count > 0);

  await input.press('ArrowDown');
  await page.waitForTimeout(50);
  ok('ArrowDown 시 첫 옵션이 aria-selected=true', (await options.nth(0).getAttribute('aria-selected')) === 'true');
  await input.press('ArrowDown');
  await page.waitForTimeout(50);
  ok('ArrowDown 반복 시 두 번째 옵션으로 하이라이트 이동', (await options.nth(1).getAttribute('aria-selected')) === 'true');
  await input.press('ArrowUp');
  await page.waitForTimeout(50);
  ok('ArrowUp 시 다시 첫 옵션으로 하이라이트 이동', (await options.nth(0).getAttribute('aria-selected')) === 'true');

  await input.press('Escape');
  await page.waitForTimeout(100);
  ok('Esc로 자동완성 목록이 닫힘', (await page.locator('[role="listbox"]').count()) === 0);

  // 다시 열고 하이라이트된 옵션을 Enter로 선택 → /search로 이동하며 regionCode 실림
  await input.fill('수원시 장안구');
  await page.waitForSelector('[role="listbox"]', { timeout: 5000 });
  await input.press('ArrowDown');
  await input.press('Enter');
  await page.waitForURL(/\/search\?/, { timeout: 5000 });
  ok('하이라이트된 옵션에서 Enter 시 regionCode로 이동', page.url().includes('regionCode='));

  await context.close();
}

// 2) 아무 것도 고르지 않고 Enter 시 자유 텍스트 검색으로 폴백
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/`);
  await page.waitForTimeout(500);
  const input = page.locator('input[role="combobox"]').first();
  await input.click();
  await input.fill('아이파크제로존');
  await page.waitForTimeout(500); // 디바운스 대기(자동완성 결과가 없을 가능성이 높은 키워드)
  await input.press('Enter');
  await page.waitForURL(/\/search\?/, { timeout: 5000 });
  ok('자동완성 미선택 상태로 Enter 시 자유 텍스트 keyword로 이동', decodeURIComponent(page.url()).includes('아이파크제로존'));
  await context.close();
}

// 3) 자동완성 API 실패 시에도 자유 텍스트 검색은 계속 가능
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.route('**/api/regions?query=*', (route) => route.abort());
  await page.goto(`${BASE}/`);
  await page.waitForTimeout(500);
  const input = page.locator('input[role="combobox"]').first();
  await input.click();
  await input.fill('래미안');
  await page.waitForTimeout(600);
  ok('자동완성 API 실패 시 목록이 뜨지 않음(우아한 실패)', (await page.locator('[role="listbox"]').count()) === 0);
  await input.press('Enter');
  await page.waitForURL(/\/search\?/, { timeout: 5000 });
  ok('자동완성 실패해도 자유 텍스트 검색은 정상 동작', decodeURIComponent(page.url()).includes('래미안'));
  await context.close();
}

// 4) 슬라이더 키보드 조작 — 방향키로 값 변경, aria-label 확인(aria-valuetext는 네이티브 range에
//    자동으로 붙지 않으므로 aria-label로 대체 문서화한 사실을 재확인)
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=test`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  const areaMinSlider = page.getByLabel('전용면적 최소값', { exact: true });
  await areaMinSlider.focus();
  const before = await areaMinSlider.inputValue();
  await areaMinSlider.press('ArrowRight');
  const after = await areaMinSlider.inputValue();
  ok('슬라이더가 방향키로 값이 실제로 바뀜', Number(after) > Number(before));
  const numberInput = page.getByLabel('전용면적 최소값 직접 입력');
  ok('슬라이더 값 변경이 옆 숫자 입력창에도 동기화됨', (await numberInput.inputValue()) === after);
  await context.close();
}

// 5) 바텀시트 포커스 트랩 — Tab을 반복해도 시트 밖으로 포커스가 나가지 않음
{
  const context = await browser.newContext({ viewport: { width: 392, height: 800 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=test`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  await page.getByRole('button', { name: /필터/ }).click();
  await page.waitForSelector('[role="dialog"]');
  const dialog = page.locator('[role="dialog"]');
  let allInside = true;
  for (let i = 0; i < 30; i++) {
    await page.keyboard.press('Tab');
    const isInside = await page.evaluate(() => {
      const dlg = document.querySelector('[role="dialog"]');
      return dlg ? dlg.contains(document.activeElement) : false;
    });
    if (!isInside) {
      allInside = false;
      break;
    }
  }
  ok('바텀시트에서 Tab을 30회 반복해도 포커스가 시트 밖으로 나가지 않음', allInside);
  ok('바텀시트가 여전히 열려있음', await dialog.isVisible());
  await context.close();
}

// 6) 에러 배너 → 재시도 성공 (route mock으로 5xx 후 200)
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  let callCount = 0;
  await page.route('**/api/complexes/search*', async (route) => {
    callCount++;
    if (callCount === 1) {
      await route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false,"data":null,"error":{"code":"INTERNAL_SERVER_ERROR","message":"일시적인 서버 오류가 발생했습니다."},"timestamp":""}' });
    } else {
      await route.continue();
    }
  });
  await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=test`);
  // 대기 결과를 반환값으로 받아 단언한다 — 실패하면 시간 초과 예외가 아니라 어느 조건이 틀렸는지 FAIL로 드러난다.
  const bannerMessageShown = await page.getByText('일시적인 서버 오류가 발생했습니다.').waitFor({ timeout: 10000 }).then(() => true, () => false);
  ok('서버 5xx 시 에러 배너에 서버 메시지 그대로 표시', bannerMessageShown);
  const retryButton = page.getByRole('button', { name: '다시 시도' });
  ok('서버 5xx 시 "다시 시도" 버튼 표시', await retryButton.isVisible());
  ok('서버 5xx 시 결과 카드 없음', (await page.locator('a[href^="/complexes/"]').count()) === 0);
  const callsBeforeRetry = callCount;
  if (await retryButton.isVisible()) await retryButton.click();
  const cardsRendered = await page.locator('a[href^="/complexes/"]').first().waitFor({ timeout: 10000 }).then(() => true, () => false);
  ok('다시 시도 클릭 시 검색을 한 번 더 요청', callCount === callsBeforeRetry + 1);
  ok('다시 시도 클릭 시 재요청 성공하여 결과 렌더링', cardsRendered);
  ok('재요청 성공 후 에러 배너가 사라짐', (await page.getByText('일시적인 서버 오류가 발생했습니다.').count()) === 0);
  await context.close();
}

// 7) 잘못된 regionCode의 서버 메시지 표시
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/search?regionCode=abc123`);
  await page.waitForSelector('text=지역 코드가 올바르지 않습니다', { timeout: 10000 });
  ok('잘못된 regionCode 형식 시 서버 메시지(지역 코드가 올바르지 않습니다) 그대로 표시', true);
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
