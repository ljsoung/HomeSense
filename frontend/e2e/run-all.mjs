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
  'privacy-withdrawal-purge-check', 'privacy-token-rotation-check', 'auth03-password-reset-check',
  // SRCH-01 — 실 백엔드(8080) 필요, dev 서버 주소는 base.mjs(기본 5173), 다른 포트면 BASE 환경변수로 덮는다.
  'srch01-basic-check', 'srch01-mobile-check', 'srch01-favorite-and-desktop-back-check',
  'srch01-slider-boundary-and-wolse-check', 'srch01-keyboard-and-error-check', 'srch01-tablet-check',
  'srch01-draft-carryover-check',
  // 모바일 필터 바텀시트 — 값 변경 시 포커스 유지, 시트 라디오 선택 표시. 실 백엔드 필요.
  'srch01-bottomsheet-focus-check',
  // UIC-03 — 자동완성 늦은 응답 경합(덮어쓰기·blur 후 재오픈). 실 백엔드 필요.
  'uic03-autocomplete-race-check',
  // HOME-01 — SRCH-01이 공유 컴포넌트(ComplexCard)를 재작업한 뒤 그리드 variant(HOME-01)에 영향이
  // 없는지 확인하는 회귀 테스트. 실 백엔드 필요.
  'home01-card-check',
  // HOME-01 최상단(GNB·히어로 Figma 대조)과 새로고침 시 세션 복원(무효 토큰 정리, refresh 1회). 실 백엔드+Redis 필요.
  'home01-header-session-check',
  // 헤더 계정 메뉴와 로그아웃(서버 폐기 포함). 실 백엔드+Redis 필요.
  'home01-logout-check',
  // 여러 탭이 동시에 세션을 복원할 때 재발급이 브라우저 전체에서 1회인지(Web Locks). 실 백엔드+Redis 필요.
  'home01-multitab-refresh-check',
  // 세션 복원 중 다른 계정으로 로그인해도 늦은 재발급 결과가 덮어쓰지 않는지. 실 백엔드+Redis 필요.
  'home01-login-during-restore-check',
  // 세션 확인 중 하트 클릭 등 인증 의존 동작이 판정까지 미뤄지는지. 실 백엔드+Redis 필요.
  'home01-auth-checking-actions-check',
  // 401 인터셉터 — 사용 중 만료 시 두 탭 동시 재발급이 1회인지. 백엔드 불필요(route로 흉내).
  'auth-interceptor-multitab-check',
  // 인증 상태 3종 — 확인 중 헤더 자리 표시·개인화 API 미호출. 백엔드 불필요.
  'auth-status-checking-check',
  // 탭 계정 동기화 — 다른 탭이 계정을 바꿨을 때 요청 방어·화면 동기화. 실 백엔드+Redis 필요.
  'auth-tab-account-sync-check',
  // 서버 로그아웃이 멈춰도 로컬 로그아웃이 제한 시간 안에 끝나는지. 실 백엔드+Redis 필요.
  'home01-logout-hang-check',
  // DTL-01 단지 상세 — 모킹 시나리오 + 실 백엔드 스모크(360/768/1280). 실 백엔드 필요(REAL_ID, 기본 10059).
  'dtl01-detail-check',
  // MY-01 마이페이지 홈 — 보호 라우트, 위젯 상태, 로그아웃·탈퇴 다이얼로그(390/768/1280). 백엔드 불필요.
  'my01-mypage-check',
  // MY-02 관심 매물·지역 관리 — 세로 섹션/탭, 지연 삭제·실행취소, 지역 추가, 이동, 키보드(390/768/1280). 백엔드 불필요.
  'my02-favorites-check',
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
