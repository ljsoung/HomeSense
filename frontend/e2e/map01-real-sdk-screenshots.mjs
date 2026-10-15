// SCR-MAP-01 실제 카카오맵 SDK 스크린샷 — 목업 없음. 전제: dev 서버(BASE, localhost:5173), 실제 백엔드(8080)·좌표가
// 채워진 DB, frontend/homesense/.env.local의 VITE_KAKAO_JS_KEY, 카카오 콘솔 JavaScript SDK 도메인에 http://localhost:5173 등록.
// 결과는 out/map01-real-*.png. 지도가 실제로 그려졌는지(SDK 타일·핀·클러스터)는 사람이 이미지를 본다 — 스크립트는 SDK
// 오류 상태가 아닌지, 핀이나 클러스터가 하나 이상 있는지만 확인한다.
import { mkdirSync } from 'node:fs';
import { chromium } from 'playwright';
import { BASE } from './base.mjs';

mkdirSync('out', { recursive: true });
const browser = await chromium.launch();
let failed = 0;
const cases = [
  { name: 'desktop-seoul-wide', width: 1280, height: 800, query: 'lat=37.5400&lng=127.0000&level=8' },
  { name: 'desktop-gangnam', width: 1280, height: 800, query: 'lat=37.5050&lng=127.0450&level=4' },
  { name: 'mobile-gangnam', width: 390, height: 844, query: 'lat=37.5050&lng=127.0450&level=5' },
];
for (const c of cases) {
  const page = await (await browser.newContext({ viewport: { width: c.width, height: c.height } })).newPage();
  await page.goto(`${BASE}/map?${c.query}`);
  await page.waitForTimeout(6000);
  const sdkError = await page.getByTestId('map-error').count();
  const pins = await page.locator('[data-testid="map-canvas"] button[data-complex-id]').count();
  const clusters = await page.evaluate(() =>
    [...document.querySelectorAll('[data-testid="map-canvas"] div')].filter((d) => /^(\d+|99\+)$/.test(d.textContent?.trim() ?? '') && d.style.borderRadius).length,
  );
  await page.screenshot({ path: `out/map01-real-${c.name}.png` });
  const okCase = sdkError === 0 && pins + clusters > 0;
  if (!okCase) failed++;
  console.log(`${okCase ? 'PASS' : 'FAIL'} ${c.name}: sdkError=${sdkError} pins=${pins} clusters=${clusters}`);
  await page.close();
}
await browser.close();
process.exit(failed ? 1 : 0);
