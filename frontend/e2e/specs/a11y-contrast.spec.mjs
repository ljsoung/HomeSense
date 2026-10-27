// 접근성 — 주요 화면에서 axe color-contrast 위반 0건(NFR-8, WCAG 1.4.3). 다른 규칙 위반은 실패로 치지 않고 목록으로만 남긴다
// (out/axe/{화면 뷰포트}.json — 테스트마다 따로 쓴다. 실패하면 워커가 다시 떠 모듈 상태가 사라지므로 모아 두지 않는다).
// 백엔드 불필요(/api/** 목업, 지도는 가짜 SDK).
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { SCREENS, mockApis } from './screenMocks.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const AXE_OUT_DIR = join(HERE, '..', 'out', 'axe');

const VIEWPORTS = [
  { name: '모바일', width: 390, height: 844 },
  { name: '데스크톱', width: 1280, height: 900 },
];

for (const viewport of VIEWPORTS) {
  for (const screen of SCREENS) {
    test(`${screen.id} ${viewport.name}: color-contrast 위반 0건`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await mockApis(page, screen.loggedIn);
      await page.goto(screen.path);
      await expect(screen.ready(page)).toBeVisible({ timeout: 10_000 });
      // 전환이 끝난 화면을 본다(색 전환 중이면 중간 색으로 잴 수 있다). 무한 반복(배경 Ken Burns 등)은 기다리지 않는다.
      await page.evaluate(() =>
        Promise.all(
          document
            .getAnimations()
            .filter((a) => a.effect?.getComputedTiming().iterations !== Infinity)
            .map((a) => a.finished.catch(() => {})),
        ),
      );

      const results = await new AxeBuilder({ page }).analyze();
      const contrast = results.violations.find((v) => v.id === 'color-contrast');
      const failures = (contrast?.nodes ?? []).map((n) => `${n.target.join(' ')} — ${n.any[0]?.message ?? n.failureSummary}`);
      mkdirSync(AXE_OUT_DIR, { recursive: true });
      writeFileSync(
        join(AXE_OUT_DIR, `${screen.id} ${viewport.name}.json`),
        JSON.stringify(
          {
            contrast: (contrast?.nodes ?? []).map((n) => ({ target: n.target.join(' '), html: n.html.slice(0, 160), ...n.any[0]?.data })),
            other: results.violations
              .filter((v) => v.id !== 'color-contrast')
              .map((v) => ({ id: v.id, impact: v.impact, help: v.help, count: v.nodes.length, targets: v.nodes.slice(0, 5).map((n) => n.target.join(' ')) })),
          },
          null,
          2,
        ),
      );
      expect(failures, failures.join('\n')).toEqual([]);
    });
  }
}
