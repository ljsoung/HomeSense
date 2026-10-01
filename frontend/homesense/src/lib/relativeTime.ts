/**
 * 서버의 LocalDateTime 문자열(타임존 없음, 예: "2026-09-30T14:05:00")을 KST 시각으로 읽는다. 배포 JVM 기본
 * 타임존을 Asia/Seoul로 고정하는 것이 전제다(CLAUDE.md BAT-USR-01 체크리스트, "날짜/시간 처리") — 브라우저의
 * 타임존으로 읽으면 해외에서 접속한 사용자에게 시간이 어긋난다. 이미 오프셋이나 Z가 붙어 있으면 그대로 쓴다.
 * 읽을 수 없으면 NaN.
 */
export function parseServerDateTime(value: string): number {
  const hasZone = /(Z|[+-]\d{2}:?\d{2})$/.test(value);
  return Date.parse(hasZone ? value : `${value}+09:00`);
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/**
 * "방금 전 / N분 전 / N시간 전 / N일 전", 7일 이상 지나면 "YYYY.MM.DD"(서버 문자열의 날짜 부분 그대로).
 * 시계 차이로 미래 시각이 오면 "방금 전"으로 본다. 읽을 수 없는 값은 원문의 날짜 부분을 돌려준다.
 */
export function formatRelativeTime(value: string, now: number = Date.now()): string {
  const time = parseServerDateTime(value);
  const datePart = value.slice(0, 10).replaceAll('-', '.');
  if (Number.isNaN(time)) return datePart;
  const diff = now - time;
  if (diff < MINUTE) return '방금 전';
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}분 전`;
  if (diff < DAY) return `${Math.floor(diff / HOUR)}시간 전`;
  if (diff < 7 * DAY) return `${Math.floor(diff / DAY)}일 전`;
  return datePart;
}
