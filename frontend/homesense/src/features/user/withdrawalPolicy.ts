/**
 * 탈퇴 계정 보관(유예) 기간(일). 백엔드 homesense.withdrawal.grace-days(환경변수 WITHDRAWAL_GRACE_DAYS)의 기본값
 * 7과 반드시 같아야 한다 — 이 값을 바꾸거나 배포 환경에서 WITHDRAWAL_GRACE_DAYS를 7이 아닌 값으로 설정하면
 * 개인정보처리방침 2항과 MY-01 탈퇴 안내가 실제 파기 시점과 어긋나므로 이 상수도 함께 바꿔라(CLAUDE.md BAT-USR-01 절).
 * 방침과 탈퇴 안내가 같은 값을 쓰도록 한 곳에 둔다.
 */
export const WITHDRAWAL_GRACE_DAYS = 7;
