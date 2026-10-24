// [AUTH-02] 단지 상세에서 비로그인으로 관심 등록 → 로그인 화면 → 회원가입을 거치면, 가입 후 그 상세로 돌아와
// 관심 등록이 이어서 실행된다(예전엔 회원가입으로 넘어갈 때 복귀 목적지를 잃어 항상 홈으로 갔다).
import { expect, test } from '@playwright/test';

import { fakeJwt, json } from './helpers.mjs';

const COMPLEX_ID = 900001;
const TOKEN = fakeJwt(9, 'new');

const detail = {
  complexId: COMPLEX_ID,
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
  locationPrecision: 'PRECISE',
  precisionBadge: 'PRECISE',
  basicInfo: { householdCount: 1000, buildingCount: 10, approvalDate: '2001-05-01' },
  extendedInfo: {},
};

for (const viewport of [{ width: 390, height: 844 }, { width: 1280, height: 900 }]) {
  test(`상세 → 관심 등록 → 로그인 → 회원가입 → 상세로 복귀하고 관심 등록됨 (${viewport.width}px)`, async ({ page }) => {
    await page.setViewportSize(viewport);
    const favorites = [];
    const posts = [];

    await page.route('**/api/**', async (route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      const method = request.method();
      if (path === `/api/complexes/${COMPLEX_ID}`) return route.fulfill(json(detail));
      if (path === '/api/auth/check-email') return route.fulfill(json({ duplicate: false }));
      if (path === '/api/auth/signup') {
        return route.fulfill(json({ accessToken: TOKEN, refreshToken: 'RN', expiresIn: 1800, userId: 9, email: 'new@test.com', nickname: '새회원' }));
      }
      if (path === '/api/users/me') return route.fulfill(json({ userId: 9, email: 'new@test.com', nickname: '새회원', createdAt: '2026-10-23T00:00:00' }));
      if (path === '/api/favorites/properties' && method === 'POST') {
        posts.push(request.postDataJSON());
        const created = { favoritePropertyId: 501, complexId: COMPLEX_ID, housingType: 'APT', createdAt: '2026-10-23T00:00:00' };
        favorites.push(created);
        return route.fulfill(json(created));
      }
      if (path === '/api/favorites/properties') return route.fulfill(json(favorites));
      return route.fulfill(json([]));
    });

    await page.goto(`/complexes/${COMPLEX_ID}`);
    await page.getByRole('button', { name: '관심 매물 등록' }).click();
    await expect(page).toHaveURL(/\/login$/);

    await page.getByRole('link', { name: '회원가입' }).click();
    await expect(page).toHaveURL(/\/signup$/);
    await page.locator('#email').fill('new@test.com');
    await page.getByRole('button', { name: '중복확인' }).click();
    await expect(page.getByText('사용 가능한 이메일입니다')).toBeVisible();
    await page.locator('#password').fill('Passw0rd!');
    await page.locator('#passwordConfirm').fill('Passw0rd!');
    await page.locator('#nickname').fill('새회원');
    // 체크박스 상자를 누른다(약관 행 가운데에는 개인정보처리방침 링크가 있다).
    await page.locator('label[for="confirmedAge14"]').click({ position: { x: 6, y: 6 } });
    await page.locator('label[for="agreeToTerms"]').click({ position: { x: 6, y: 6 } });
    await expect(page.getByRole('checkbox', { name: /만 14세/ })).toBeChecked();
    await expect(page.getByRole('checkbox', { name: /동의합니다/ })).toBeChecked();
    await page.getByRole('button', { name: '가입하기' }).click();

    await expect(page).toHaveURL(new RegExp(`/complexes/${COMPLEX_ID}$`));
    await expect(page.getByRole('button', { name: '관심 매물 해제' })).toBeVisible();
    expect(posts).toEqual([{ complexId: COMPLEX_ID }]);
  });
}
