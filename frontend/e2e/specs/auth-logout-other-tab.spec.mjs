// [AUTH-01] 로그아웃 응답을 기다리는 동안 다른 탭이 다른 계정으로 로그인하면, 이 탭의 로그아웃이 끝나도
// 그 새 로그인(B)은 지워지지 않는다. 로그아웃을 시작한 탭은 B를 따라간다(CLAUDE.md "로그아웃 조건부 삭제" 절).
import { expect, test } from '@playwright/test';

import { ACCESS_KEY, REFRESH_KEY, fakeJwt, gate, json, subjectOf } from './helpers.mjs';

const TOKEN_A = fakeJwt(7, 'a');
const TOKEN_B = fakeJwt(8, 'b');
const USERS = {
  7: { userId: 7, email: 'a@test.com', nickname: '계정A', createdAt: '2026-01-01T00:00:00' },
  8: { userId: 8, email: 'b@test.com', nickname: '계정B', createdAt: '2026-01-01T00:00:00' },
};

for (const logoutStatus of [200, 503]) {
  test(`로그아웃 응답(${logoutStatus}) 전에 다른 탭이 B로 로그인하면 B 토큰이 남고 두 탭 모두 B`, async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const logoutGate = gate();
    const logoutBodies = [];
    const meResults = [];

    await context.route('**/api/**', async (route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      if (path === '/api/users/me') {
        const sub = subjectOf(request);
        const user = USERS[sub];
        meResults.push({ sub, status: user ? 200 : 401 });
        return route.fulfill(user ? json(user) : { status: 401, contentType: 'application/json', body: '{"success":false,"error":{"code":"UNAUTHORIZED","message":"x"}}' });
      }
      if (path === '/api/auth/logout') {
        logoutBodies.push(request.postDataJSON().refreshToken);
        await logoutGate.promise;
        return route.fulfill(logoutStatus === 200 ? json(null) : { status: 503, contentType: 'application/json', body: '{"success":false,"error":{"code":"ERROR","message":"x"}}' });
      }
      if (path === '/api/auth/login') return route.fulfill(json({ accessToken: TOKEN_B, refreshToken: 'RB', expiresIn: 1800 }));
      if (path === '/api/auth/refresh') return route.fulfill({ status: 500, body: '' }); // 이 시나리오에서는 불리면 안 된다
      return route.fulfill(json([]));
    });

    // 탭 A: 계정 A로 로그인된 상태.
    const tabA = await context.newPage();
    await tabA.goto('/');
    await tabA.evaluate(([keys, a]) => {
      localStorage.setItem(keys[0], a);
      localStorage.setItem(keys[1], 'RA');
      localStorage.setItem('homesense.authSessionId', 'session-A');
    }, [[ACCESS_KEY, REFRESH_KEY], TOKEN_A]);
    await tabA.reload();
    const headerA = tabA.locator('header:visible');
    await headerA.getByRole('button', { name: /계정 메뉴/ }).click();
    await tabA.getByRole('menuitem', { name: '로그아웃' }).click();
    await expect.poll(() => logoutBodies).toEqual(['RA']);

    // 탭 B: 로그아웃 응답이 붙잡힌 동안 계정 B로 로그인한다.
    const tabB = await context.newPage();
    await tabB.goto('/login');
    await tabB.getByLabel('이메일', { exact: true }).fill('b@test.com');
    await tabB.getByLabel('비밀번호', { exact: true }).fill('Passw0rd!');
    await tabB.getByRole('button', { name: '로그인', exact: true }).click();
    await expect(tabB).toHaveURL(/\/$/);
    await expect(tabB.locator('header:visible').getByRole('button', { name: /계정 메뉴/ })).toBeVisible();

    // 탭 A의 로그아웃 응답이 이제 온다.
    logoutGate.release();

    // B 토큰은 그대로 남는다.
    await expect.poll(() => tabB.evaluate((key) => localStorage.getItem(key), ACCESS_KEY)).toBe(TOKEN_B);
    await expect(tabA).toHaveURL(/\/$/);
    // 탭 A는 B를 따라간다 — 비로그인 헤더(로그인 링크)가 아니라 계정 메뉴.
    await expect(headerA.getByRole('button', { name: /계정 메뉴/ })).toBeVisible();
    await expect(headerA.getByRole('link', { name: '로그인' })).toHaveCount(0);
    expect(logoutBodies).toEqual(['RA']); // 두 번째 로그아웃(B 토큰)은 보내지 않는다

    // 탭 B는 새로고침해도 B로 확인된다.
    meResults.length = 0;
    await tabB.reload();
    await expect(tabB.locator('header:visible').getByRole('button', { name: /계정 메뉴/ })).toBeVisible();
    expect(meResults).toContainEqual({ sub: '8', status: 200 });
    expect(await tabB.evaluate((key) => localStorage.getItem(key), REFRESH_KEY)).toBe('RB');

    await context.close();
  });
}
