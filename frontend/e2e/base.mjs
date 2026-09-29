// e2e 스크립트가 공유하는 dev 서버 주소. 기본값은 `npm run dev`의 기본 포트(5173)다.
// 다른 포트로 띄웠다면 BASE 환경변수로 덮는다: BASE=http://localhost:5183 node srch01-basic-check.mjs
// (2026-09-29까지는 스크립트마다 기본값을 따로 적어, SRCH-01 계열 11개만 5183을 가리키고 있었다.)
export const BASE = process.env.BASE ?? 'http://localhost:5173';
