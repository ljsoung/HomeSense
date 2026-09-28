// 15개 검증 스크립트를 순서대로 실행하고 종료코드를 요약한다. 사용법은 README.md 참고.
// auth03-password-reset-check는 다른 14개와 달리 실제 백엔드+Redis가 떠 있어야 한다(README 참고) —
// run-all.mjs에는 그대로 포함하되, 백엔드 없이 돌리는 세션은 `node run-all.mjs <14개 이름...>`처럼
// 이 스크립트만 빼고 지정하라.
import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';

const OUT_DIR = process.env.OUT_DIR ?? './out';

const SCRIPTS = [
  'signup-age-check', 'checkbox-focus-check', 'signup-age-server-error-check', 'signup-test',
  'signup-functional', 'dup-409-check', 'server-email-error-clear-test',
  'password-nickname-server-error-clear-test', 'nickname-trim-test', 'border-settle-check',
  'signup-policy-newtab-check', 'privacy-age-fix-check', 'privacy-amendments-check',
  'privacy-withdrawal-purge-check', 'auth03-password-reset-check',
  // SRCH-01 — 실 백엔드(8080) 필요, dev 서버 기본 포트(5173/5183 등)는 BASE 환경변수로 지정.
  'srch01-basic-check', 'srch01-mobile-check', 'srch01-favorite-and-desktop-back-check',
  'srch01-slider-boundary-and-wolse-check', 'srch01-keyboard-and-error-check', 'srch01-tablet-check',
  'srch01-draft-carryover-check',
  // UIC-03 — 자동완성 늦은 응답 경합(덮어쓰기·blur 후 재오픈). 실 백엔드 필요.
  'uic03-autocomplete-race-check',
  // HOME-01 — SRCH-01이 공유 컴포넌트(ComplexCard)를 재작업한 뒤 그리드 variant(HOME-01)에 영향이
  // 없는지 확인하는 회귀 테스트. 실 백엔드 필요.
  'home01-card-check',
];
const only = process.argv.slice(2);
const targets = only.length ? SCRIPTS.filter((s) => only.includes(s)) : SCRIPTS;

let failed = 0;
for (const name of targets) {
  const r = spawnSync(process.execPath, [`${name}.mjs`], { encoding: 'utf8' });
  const lines = (r.stdout + r.stderr).trim().split('\n');
  const summary = lines.filter((l) => /passed|total:/.test(l)).slice(-1)[0] ?? lines.slice(-1)[0];
  console.log(`${r.status === 0 ? 'OK  ' : 'FAIL'} ${name.padEnd(44)} ${summary}`);
  if (r.status !== 0) {
    failed++;
    // 실패 원인을 잃지 않도록 전체 출력을 파일로 남긴다(요약 줄만으로는 스택트레이스의 끝만 보인다).
    mkdirSync(OUT_DIR, { recursive: true });
    const logPath = `${OUT_DIR}/failed-${name}-${Date.now()}.log`;
    writeFileSync(logPath, `exit=${r.status}
${r.stdout}
${r.stderr}`);
    const firstError = lines.find((l) => /Error|Timeout|FAIL/.test(l)) ?? '(오류 줄 없음)';
    console.log(`     ↳ ${firstError.trim()}
     ↳ full output: ${logPath}`);
  }
}
console.log(`\n${targets.length - failed}/${targets.length} scripts exited 0`);
process.exit(failed ? 1 : 0);
