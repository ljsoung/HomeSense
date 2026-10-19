// MY-05: 390/768/1280에서 Figma 세 상태(기본·비밀번호 섹션 펼침·현재 비밀번호 오류)를 ./out/my05-{mobile,tablet,desktop}-*.png로 저장하고,
// Figma 수치표(작업 지시 6장)와 대조할 계산값을 출력한다(모킹, 테스트 아님) — PR 첨부·Figma 대조용.
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { BASE } from './base.mjs';
import { errBody, okBody } from './mockApi.mjs';

mkdirSync('./out', { recursive: true });
const browser = await chromium.launch();

async function open(viewport) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript(() => {
    localStorage.setItem('homesense.accessToken', 'A');
    localStorage.setItem('homesense.refreshToken', 'R');
  });
  await context.route('**/api/**', (route) => {
    const p = new URL(route.request().url()).pathname;
    const method = route.request().method();
    const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body });
    if (p === '/api/users/me' && method === 'GET') return json(200, okBody({ userId: 7, email: 'jiseong@homesense.kr', nickname: '지성', createdAt: '2026-09-15T10:20:30' }));
    if (p === '/api/users/me' && method === 'PUT') return json(400, errBody('INVALID_CURRENT_PASSWORD', '현재 비밀번호가 일치하지 않습니다'));
    return json(404, errBody('NOT_FOUND', 'mock 없음'));
  });
  const page = await context.newPage();
  await page.goto(`${BASE}/my/profile`);
  await page.getByLabel('닉네임', { exact: true }).waitFor();
  return { context, page };
}

const style = (locator, props) =>
  locator.evaluate((el, keys) => {
    const cs = getComputedStyle(el);
    const r = el.getBoundingClientRect();
    return { w: Math.round(r.width * 10) / 10, h: Math.round(r.height * 10) / 10, ...Object.fromEntries(keys.map((k) => [k, cs.getPropertyValue(k)])) };
  }, props);

for (const [name, viewport] of [['mobile', { width: 390, height: 844 }], ['tablet', { width: 768, height: 1024 }], ['desktop', { width: 1280, height: 900 }]]) {
  const { context, page } = await open(viewport);
  await page.screenshot({ path: `./out/my05-${name}-default.png`, fullPage: true });
  const out = {};
  if (name === 'desktop') {
    out.h1 = await style(page.getByRole('heading', { level: 1 }), ['font-size', 'font-weight', 'line-height', 'color']);
    out.breadcrumbCurrent = await style(page.locator('[aria-current="page"]'), ['font-size', 'font-weight', 'color']);
  }
  out.card = await style(page.locator('form > div').first(), ['border-radius', 'box-shadow', 'border-top-color']);
  out.sectionTitle = await style(page.getByRole('heading', { name: '기본 정보' }), ['font-size', 'font-weight', 'color']);
  out.label = await style(page.locator('label').first(), ['font-size', 'font-weight', 'color', 'margin-bottom']);
  out.input = await style(page.getByLabel('닉네임', { exact: true }), ['height', 'background-color', 'border-top-color', 'border-radius', 'font-size', 'color']);
  out.emailHelp = await style(page.getByText('이메일(로그인 ID)은 현재 변경할 수 없습니다'), ['font-size', 'font-weight', 'color', 'margin-top']);
  out.toggleCollapsed = await style(page.getByText('펼치기'), ['font-size', 'font-weight', 'color']);
  out.cancel = await style(page.getByRole('button', { name: '취소' }), ['background-color', 'border-top-color', 'border-radius', 'font-size', 'font-weight', 'color']);
  out.saveDisabled = await style(page.getByRole('button', { name: '저장' }), ['background-color', 'color', 'box-shadow']);

  await page.getByRole('button', { name: /비밀번호 변경/ }).click();
  await page.getByLabel('현재 비밀번호', { exact: true }).fill('Abcd1234!');
  await page.getByLabel('새 비밀번호', { exact: true }).fill('Efgh5678!');
  await page.getByLabel('새 비밀번호 확인', { exact: true }).fill('Efgh5678!');
  out.toggleExpanded = await style(page.getByText('접기'), ['color']);
  out.successBox = await style(page.getByText('새 비밀번호 조건을 모두 충족합니다.'), ['background-color', 'border-top-color', 'border-radius', 'font-size', 'font-weight', 'color']);
  out.saveEnabled = await style(page.getByRole('button', { name: '저장' }), ['background-color', 'color', 'box-shadow']);
  const next = await page.getByLabel('새 비밀번호', { exact: true }).boundingBox();
  const confirm = await page.getByLabel('새 비밀번호 확인', { exact: true }).boundingBox();
  out.newConfirmLayout = { sameRow: Math.abs(next.y - confirm.y) < 2, nextW: next.width, confirmW: confirm.width };
  await page.mouse.click(1, 1);
  await page.screenshot({ path: `./out/my05-${name}-expanded.png`, fullPage: true });

  await page.getByRole('button', { name: '저장' }).click();
  await page.getByRole('alert').filter({ hasText: '현재 비밀번호가 일치하지 않습니다' }).waitFor();
  await page.waitForTimeout(400); // 입력의 transition-colors(150ms)가 끝난 뒤 잰다
  out.errorInput = await style(page.getByLabel('현재 비밀번호', { exact: true }), ['border-top-color', 'box-shadow']);
  out.errorText = await style(page.getByRole('alert').filter({ hasText: '현재 비밀번호가 일치하지 않습니다' }), ['font-size', 'font-weight', 'color', 'margin-top']);
  await page.screenshot({ path: `./out/my05-${name}-error.png`, fullPage: true });
  console.log(`== ${name}\n${JSON.stringify(out, null, 1)}`);
  await context.close();
}

await browser.close();
