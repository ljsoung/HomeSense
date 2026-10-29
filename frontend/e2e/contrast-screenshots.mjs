// 글자 대비 수정 전후 비교 스크린숏 — 목업(specs/screenMocks.mjs)으로 주요 화면을 찍는다. 백엔드 불필요.
// 실행: BASE=http://localhost:5173 LABEL=after node contrast-screenshots.mjs → out/contrast-{LABEL}-{화면}-{폭}.png
// 수정 전은 develop을 다른 포트로 띄워 LABEL=before로 찍는다.
import { mkdirSync } from 'node:fs';
import { chromium } from 'playwright';
import { BASE } from './base.mjs';
import { SCREENS, mockApis } from './specs/screenMocks.mjs';

const LABEL = process.env.LABEL ?? 'after';
const IDS = (process.env.SCREENS ?? 'HOME-01,SRCH-01,DTL-01,AUTH-01,MY-01,MY-02,MY-04').split(',');
const VIEWPORTS = [
  { name: '390', width: 390, height: 844 },
  { name: '1280', width: 1280, height: 900 },
];

mkdirSync('out', { recursive: true });
const browser = await chromium.launch();
for (const viewport of VIEWPORTS) {
  for (const screen of SCREENS.filter((s) => IDS.includes(s.id))) {
    const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height } });
    const page = await context.newPage();
    await mockApis(page, screen.loggedIn, screen.options);
    await page.goto(`${BASE}${screen.path}`);
    await screen.ready(page).waitFor({ timeout: 10_000 });
    await page.evaluate(() =>
      Promise.all(
        document
          .getAnimations()
          .filter((a) => a.effect?.getComputedTiming().iterations !== Infinity)
          .map((a) => a.finished.catch(() => {})),
      ),
    );
    const file = `out/contrast-${LABEL}-${screen.id}-${viewport.name}.png`;
    await page.screenshot({ path: file, fullPage: true });
    console.log(file);
    await context.close();
  }
}
await browser.close();
