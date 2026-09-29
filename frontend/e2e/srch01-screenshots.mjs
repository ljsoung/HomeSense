import { chromium } from 'playwright';
import { mkdirSync } from 'fs';

import { BASE } from './base.mjs';
mkdirSync('./out', { recursive: true });

const browser = await chromium.launch();
const viewports = [
  { name: 'desktop', width: 1280, height: 900 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'mobile', width: 392, height: 800 },
];

for (const vp of viewports) {
  const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
  const page = await context.newPage();
  await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=%EA%B2%BD%EA%B8%B0%EB%8F%84%20%EC%88%98%EC%9B%90%EC%8B%9C%20%EC%9E%A5%EC%95%88%EA%B5%AC`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  await page.waitForTimeout(300);
  await page.screenshot({ path: `./out/srch01-${vp.name}.png`, fullPage: false });
  await context.close();
}
await browser.close();
console.log('done');
