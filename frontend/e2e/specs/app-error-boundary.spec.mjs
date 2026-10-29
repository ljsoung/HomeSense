// [COM] 앱 최상위 에러 바운더리 — 렌더 중 예외가 나면 흰 화면 대신 안내 화면(다시 시도, 홈으로)을 보이고, 홈으로 가면 정상으로
// 그린다. 라우트가 바뀌면 오류 상태가 풀린다. 개발 서버 전용 경로 /__e2e/crash로 예외를 일으킨다(운영 빌드에는 없다 — PR에 빌드
// 산출물 확인 기록). 백엔드 불필요(/api/** 는 page.route 목업).
import { expect, test } from '@playwright/test';
import { errBody, okBody } from '../mockApi.mjs';

async function mockApis(page) {
  await page.route('**/api/**', (route) => {
    const url = new URL(route.request().url());
    const ok = (data) => route.fulfill({ status: 200, contentType: 'application/json', body: okBody(data) });
    if (url.pathname === '/api/users/me') {
      return route.fulfill({ status: 401, contentType: 'application/json', body: errBody('UNAUTHORIZED', '인증이 필요합니다') });
    }
    if (url.pathname === '/api/complexes/popular' || url.pathname === '/api/search/popular' || url.pathname === '/api/recent-views') {
      return ok([]);
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: errBody('NOT_FOUND', 'mock 없음') });
  });
}

const alert = (page) => page.getByRole('alert').filter({ hasText: '화면을 표시하는 중 문제가 발생했어요' });

for (const viewport of [{ width: 390, height: 844 }, { width: 1280, height: 900 }]) {
  test(`렌더 오류면 안내 화면, 홈으로 가면 정상 (${viewport.width}px)`, async ({ page }) => {
    await page.setViewportSize(viewport);
    await mockApis(page);
    await page.goto('/__e2e/crash');

    await expect(alert(page)).toBeVisible();
    await expect(page.getByRole('button', { name: '다시 시도' })).toBeVisible();
    // 개발 모드에서는 오류 내용이 보인다(운영은 고정 문구만).
    await expect(page.getByTestId('app-error-detail')).toContainText('e2e 테스트용 렌더 오류');

    await page.getByRole('link', { name: '홈으로' }).click();
    await expect(page).toHaveURL(/\/$/);
    await expect(alert(page)).toHaveCount(0);
    await expect(page.getByRole('banner')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
  });
}

test('다시 시도하면 다시 그리고, 라우트가 바뀌면 오류 상태가 풀린다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await mockApis(page);
  await page.goto('/privacy');
  await expect(page.getByRole('banner')).toBeVisible();

  // 앱 안 이동으로 오류 화면에 들어간다(전체 새로고침 없이).
  await page.evaluate(() => {
    history.pushState({ idx: 1, key: 'crash' }, '', '/__e2e/crash');
    dispatchEvent(new PopStateEvent('popstate', { state: history.state }));
  });
  await expect(alert(page)).toBeVisible();

  // 뒤로 가기(라우트 변경) → 오류가 풀리고 이전 화면이 그려진다.
  await page.goBack();
  await expect(page).toHaveURL(/\/privacy$/);
  await expect(alert(page)).toHaveCount(0);
  await expect(page.getByRole('banner')).toBeVisible();

  // 다시 오류 화면으로 간 뒤, 원인이 사라진 상태에서 "다시 시도"하면 다시 그린다.
  await page.goForward();
  await expect(alert(page)).toBeVisible();
  await page.evaluate(() => {
    window.__homesenseE2eCrash = false;
  });
  await page.getByRole('button', { name: '다시 시도' }).click();
  await expect(page.getByTestId('e2e-crash-recovered')).toBeVisible();
  await expect(alert(page)).toHaveCount(0);
});
