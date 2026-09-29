// SCR-SRCH-01 모바일 필터 바텀시트 — 시트 안에서 값을 바꿔도 키보드 포커스가 그 컨트롤에 머무는지.
// 수정 전에는 호출부의 인라인 onClose가 매 렌더 새 함수라 BottomSheet effect가 draft 변경마다
// cleanup(트리거로 포커스 복귀) → 재초기화(첫 요소 = 닫기 버튼으로 포커스)를 반복했다.
// 실 백엔드(8080) 필요. BASE로 dev 서버 지정.
import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 392, height: 800 } });
const page = await context.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(String(e)));
await page.goto(`${BASE}/search?regionCode=4111100000`);
await page.waitForLoadState('networkidle');

const trigger = page.getByRole('button', { name: /필터/ }).first();
await trigger.click();
const sheet = page.locator('[role="dialog"]');
await sheet.waitFor();
const focusedLabel = () => page.evaluate(() => {
  const el = document.activeElement;
  return el?.getAttribute('aria-label') ?? el?.closest('label')?.textContent?.trim() ?? el?.tagName;
});
const focusInsideSheet = () => sheet.evaluate((d) => d.contains(document.activeElement));

// 0) 모바일은 숨겨진 데스크톱 사이드바와 시트에 FilterPanel이 동시에 렌더된다 — id 중복·id 참조 깨짐이
//    없어야 하고, 라디오 그룹 이름도 패널마다 달라야 한다.
const idAudit = await page.evaluate(() => {
  const ids = [...document.querySelectorAll('[id]')].map((e) => e.id);
  const duplicates = [...new Set(ids.filter((id, i) => ids.indexOf(id) !== i))];
  const brokenRefs = [];
  for (const attr of ['for', 'aria-labelledby', 'aria-describedby', 'aria-controls', 'aria-activedescendant']) {
    for (const el of document.querySelector('[role="dialog"]').querySelectorAll(`[${attr}]`)) {
      for (const ref of el.getAttribute(attr).split(/\s+/)) if (!document.getElementById(ref)) brokenRefs.push(`${attr}=${ref}`);
    }
  }
  const groupsIn = (root) => [...new Set([...root.querySelectorAll('input[type="radio"]')].map((r) => r.name))];
  const sheetGroups = groupsIn(document.querySelector('[role="dialog"]'));
  const sidebarGroups = groupsIn(document.querySelector('aside'));
  return { idCount: ids.length, duplicates, brokenRefs, sheetGroups, sidebarGroups };
});
ok(`중복 id 0개(전체 ${idAudit.idCount}개)`, idAudit.duplicates.length === 0);
ok('시트 안의 id 참조 속성이 모두 존재하는 요소를 가리킴', idAudit.brokenRefs.length === 0);
ok('시트와 사이드바의 라디오 그룹 이름이 서로 다름', idAudit.sheetGroups.length === 1 && idAudit.sidebarGroups.length === 1 && idAudit.sheetGroups[0] !== idAudit.sidebarGroups[0]);

// 0-1) 시트 안에서 라벨을 클릭하면 시트 쪽 컨트롤이 바뀐다(숨겨진 사이드바 컨트롤이 아니라).
const sheetVilla = sheet.getByRole('checkbox', { name: '연립다세대' });
const sidebarVilla = page.locator('aside').getByRole('checkbox', { name: '연립다세대', includeHidden: true });
const villaBefore = await sheetVilla.isChecked();
await sheet.getByText('연립다세대', { exact: true }).click();
ok('시트의 "연립다세대" 라벨 클릭 시 시트 체크박스가 토글됨', (await sheetVilla.isChecked()) !== villaBefore);
await sheet.getByText('연립다세대', { exact: true }).click();
ok('다시 클릭 시 원래 상태로 복귀', (await sheetVilla.isChecked()) === villaBefore);
ok('사이드바 체크박스도 같은 draft 값을 보임', (await sidebarVilla.isChecked()) === villaBefore);
await sheet.getByText('월세', { exact: true }).click();
const sheetRadios = sheet.getByRole('radiogroup', { name: '거래유형' }).getByRole('radio');
ok('시트의 "월세" 라벨 클릭 시 시트 라디오가 선택됨', await sheetRadios.nth(2).isChecked());
await sheet.getByText('매매', { exact: true }).click();
ok('시트의 "매매" 라벨 클릭 시 다시 매매 선택', await sheetRadios.nth(0).isChecked());

// 1) 라디오(거래유형) — 방향키로 선택을 바꿔도 포커스가 라디오에 머물고, 시트에 정확히 하나가 체크된다.
//    (모바일은 숨겨진 데스크톱 사이드바에도 FilterPanel이 있어, 두 패널이 같은 라디오 name을 쓰면
//    체크가 사이드바로 넘어가 시트에는 아무것도 선택되지 않은 것처럼 보였다.)
const radios = sheet.getByRole('radiogroup', { name: '거래유형' }).getByRole('radio');
const checkedValues = () => radios.evaluateAll((els) => els.filter((e) => e.checked).map((e) => e.value));
ok('시트 초기 상태: 매매 하나만 체크', JSON.stringify(await checkedValues()) === '["매매"]');
await radios.nth(0).focus();
await page.keyboard.press('ArrowDown');
await page.waitForTimeout(150);
ok('라디오 방향키로 전세 하나만 체크됨', JSON.stringify(await checkedValues()) === '["전세"]');
ok('라디오 변경 후 포커스가 선택된 라디오에 머묾', await radios.nth(1).evaluate((el) => el === document.activeElement));
ok('라디오 변경 후 닫기 버튼으로 튀지 않음', (await focusedLabel()) !== '닫기');

// 2) 범위 슬라이더 — 방향키로 값을 여러 번 바꿔도 포커스 유지
const slider = sheet.locator('input[type="range"]').first();
const sliderLabel = await slider.getAttribute('aria-label');
await slider.focus();
const before = await slider.inputValue();
for (let i = 0; i < 3; i++) await page.keyboard.press('ArrowRight');
await page.waitForTimeout(150);
ok('슬라이더 방향키로 값 변경됨', (await slider.inputValue()) !== before);
ok('슬라이더 변경 후 포커스 유지', (await focusedLabel()) === sliderLabel);

// 3) 숫자 입력 — 타이핑 중 포커스 유지
const numberInput = sheet.locator('input[type="number"]').first();
await numberInput.focus();
await page.keyboard.press('Control+A');
await page.keyboard.type('30');
await page.waitForTimeout(150);
ok('숫자 입력 중 포커스 유지', await numberInput.evaluate((el) => el === document.activeElement));
ok('포커스가 시트 안에 있음', await focusInsideSheet());

// 4) 트랩·Esc·포커스 복귀 회귀 — open 변화에서만 시작/종료
ok('시트가 열린 채 유지', await sheet.isVisible());
ok('숫자 입력 후에도 거래유형 선택 유지(전세)', JSON.stringify(await checkedValues()) === '["전세"]');
await page.keyboard.press('Escape');
await page.waitForTimeout(150);
ok('Esc로 시트 닫힘', (await page.locator('[role="dialog"]').count()) === 0);
ok('닫힌 뒤 필터 버튼으로 포커스 복귀', await trigger.evaluate((el) => el === document.activeElement));
ok('본문 스크롤 잠금 해제', (await page.evaluate(() => document.body.style.overflow)) === '');

// 5) 시트에서 고른 거래유형이 실제로 적용되는지
await trigger.click();
await sheet.waitFor();
const radios2 = sheet.getByRole('radiogroup', { name: '거래유형' }).getByRole('radio');
await radios2.nth(2).check();
await sheet.getByRole('button', { name: '필터 적용' }).click();
await page.waitForURL(/dealType=/, { timeout: 5000 });
ok('시트에서 월세 선택 후 적용 시 URL에 반영', decodeURIComponent(page.url()).includes('dealType=월세'));
ok('pageerror 없음', errors.length === 0);

await browser.close();
console.log(`\n${pass} passed, ${fail} failed (total: ${pass + fail})`);
process.exit(fail ? 1 : 0);
