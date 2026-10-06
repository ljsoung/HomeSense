import { describe, expect, it } from 'vitest';
import { formatRelativeTime, parseServerDateTime } from './relativeTime';

// 기준 시각: KST 2026-10-01 12:00:00 = UTC 03:00:00
const NOW = Date.UTC(2026, 9, 1, 3, 0, 0);

describe('parseServerDateTime', () => {
  it('타임존 없는 문자열을 KST로 읽는다', () => {
    expect(parseServerDateTime('2026-10-01T12:00:00')).toBe(NOW);
  });

  it('오프셋이 붙어 있으면 그대로 쓴다', () => {
    expect(parseServerDateTime('2026-10-01T03:00:00Z')).toBe(NOW);
    expect(parseServerDateTime('2026-10-01T12:00:00+09:00')).toBe(NOW);
  });
});

describe('formatRelativeTime', () => {
  it.each([
    ['2026-10-01T11:59:30', '방금 전'],
    ['2026-10-01T12:00:30', '방금 전'], // 시계 차이로 미래
    ['2026-10-01T11:55:00', '5분 전'],
    ['2026-10-01T09:00:00', '3시간 전'],
    ['2026-09-30T12:00:01', '23시간 전'],
    ['2026-09-29T12:00:00', '2일 전'],
    ['2026-09-24T12:00:01', '6일 전'],
    ['2026-09-24T12:00:00', '2026.09.24'],
    ['2026-08-01T08:00:00.123', '2026.08.01'],
  ])('%s → %s', (value, expected) => {
    expect(formatRelativeTime(value, NOW)).toBe(expected);
  });

  it('읽을 수 없는 값은 날짜 부분만 돌려준다', () => {
    expect(formatRelativeTime('2026-09-01Tbad', NOW)).toBe('2026.09.01');
  });

  it('값이 없으면 예외 대신 빈 문자열이다(서버가 뺀 null 필드)', () => {
    expect(formatRelativeTime(undefined, NOW)).toBe('');
    expect(formatRelativeTime(null, NOW)).toBe('');
  });
});
