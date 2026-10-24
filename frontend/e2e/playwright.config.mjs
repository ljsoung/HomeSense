// @playwright/test 기반 스펙(specs/). 기존 *.mjs 스크립트(node로 직접 실행)와는 따로 돈다.
// 실행: npx playwright test  — dev 서버(BASE, 기본 5173)가 없으면 ../homesense에서 띄운다.
import { defineConfig } from '@playwright/test';

import { BASE } from './base.mjs';

export default defineConfig({
  testDir: './specs',
  outputDir: './out/test-results',
  fullyParallel: false,
  workers: 1,
  timeout: 30_000,
  expect: { timeout: 5_000 },
  reporter: [['list']],
  use: { baseURL: BASE, trace: 'retain-on-failure' },
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    cwd: '../homesense',
    url: BASE,
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
