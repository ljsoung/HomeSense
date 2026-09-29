// HOME-01 카드 영역(ComplexCard grid variant) 라이브 렌더링을 스크린샷으로 저장 — Figma 노드
// 4:1232(데스크톱)/24:7860(태블릿)/24:7370(모바일)와 육안 대조용. 테스트 아님(단정문 없음).
import { chromium } from 'playwright';
import { mkdirSync } from 'fs';

import { BASE } from './base.mjs';
mkdirSync('./out', { recursive: true });

const browser = await chromium.launch();
const viewports = [
  { name: 'desktop', width: 1280, height: 1400 },
  { name: 'tablet', width: 768, height: 1400 },
  { name: 'mobile', width: 392, height: 1400 },
];

for (const vp of viewports) {
  const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
  const page = await context.newPage();
  await page.goto(`${BASE}/`);
  await page.waitForSelector('text=인기 검색 지역', { timeout: 10000 });
  await page.waitForTimeout(500);
  await page.screenshot({ path: `./out/home01-${vp.name}.png`, fullPage: true });
  await context.close();
}
await browser.close();
console.log('done');
