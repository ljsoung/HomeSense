import { describe, expect, it } from 'vitest';
import type { NotificationResponse } from '../../features/notification/types';
import {
  appendUnique,
  clampPage,
  formatAbsoluteDateTime,
  needsNormalization,
  parseHistoryParams,
  priceDirectionOf,
  resolveNotificationLink,
  serializeHistoryParams,
  showsRelativeTime,
} from './notificationHistoryModel';
import { formatRelativeTime } from '../../lib/relativeTime';

const params = (query: string) => new URLSearchParams(query);

describe('resolveNotificationLink(D1)', () => {
  it('관심 매물 알림 → DTL-01', () => {
    expect(resolveNotificationLink({ complexId: 10 })).toEqual({ kind: 'complex', href: '/complexes/10' });
  });

  it('관심 매물 + 거래 → DTL-01까지만(거래 모달을 URL로 여는 방법이 없다)', () => {
    expect(resolveNotificationLink({ complexId: 10, legalDongCd: '1111017400' })).toEqual({ kind: 'complex', href: '/complexes/10' });
  });

  it('관심 지역 알림 → SRCH-01 지역 결과(MY-02 관심 지역 링크와 같은 빌더)', () => {
    expect(resolveNotificationLink({ legalDongCd: '1111017400' })).toEqual({ kind: 'region', href: '/search?regionCode=1111017400' });
  });

  it('관심 지역 + 거래(complexId 없음) → 여전히 SRCH-01', () => {
    expect(resolveNotificationLink({ complexId: null, legalDongCd: '1111017400' }).kind).toBe('region');
  });

  it('대상이 모두 없으면(키가 빠졌거나 null) missing', () => {
    expect(resolveNotificationLink({})).toEqual({ kind: 'missing' });
    expect(resolveNotificationLink({ complexId: null, legalDongCd: null })).toEqual({ kind: 'missing' });
  });
});

describe('priceDirectionOf(D4)', () => {
  it('제목 끝 단어로 방향을 정한다', () => {
    expect(priceDirectionOf('숭인 힐스테이트 실거래가 2.1% 상승')).toBe('up');
    expect(priceDirectionOf('서울특별시 종로구 창신동 실거래가 3.4% 하락 ')).toBe('down');
  });

  it('형식 밖이면 unknown — 방향을 지어내지 않는다', () => {
    expect(priceDirectionOf('숭인 힐스테이트 실거래가 변동')).toBe('unknown');
    expect(priceDirectionOf('상승세 단지 신규 실거래 3건')).toBe('unknown');
  });
});

describe('URL 상태(D7)', () => {
  it('모르는 type은 전체, 숫자가 아니거나 1 미만인 page는 1', () => {
    expect(parseHistoryParams(params('type=EVERYTHING&page=abc'))).toEqual({ filter: 'ALL', page: 1 });
    expect(parseHistoryParams(params('type=NEW_TRADE&page=0'))).toEqual({ filter: 'NEW_TRADE', page: 1 });
    expect(parseHistoryParams(params('type=PRICE_CHANGE&page=3'))).toEqual({ filter: 'PRICE_CHANGE', page: 3 });
    expect(parseHistoryParams(params('page=2.5')).page).toBe(1);
  });

  it('기본값(전체, 1페이지)은 URL에서 생략', () => {
    expect(serializeHistoryParams({ filter: 'ALL', page: 1 }).toString()).toBe('');
    expect(serializeHistoryParams({ filter: 'NEW_TRADE', page: 2 }).toString()).toBe('type=NEW_TRADE&page=2');
  });

  it('정규화가 필요한 URL만 true', () => {
    expect(needsNormalization(params('type=EVERYTHING'))).toBe(true);
    expect(needsNormalization(params('page=0'))).toBe(true);
    expect(needsNormalization(params('type=ALL'))).toBe(true);
    expect(needsNormalization(params('type=NEW_TRADE&page=2'))).toBe(false);
    expect(needsNormalization(params(''))).toBe(false);
  });

  it('범위를 넘는 page는 마지막 페이지(0건이면 1)', () => {
    expect(clampPage(9, 3)).toBe(3);
    expect(clampPage(2, 3)).toBe(2);
    expect(clampPage(4, 0)).toBe(1);
  });
});

describe('시간 표시(D10)', () => {
  it('서버 LocalDateTime을 문자열 그대로 "YYYY-MM-DD HH:mm"으로', () => {
    expect(formatAbsoluteDateTime('2026-10-06T03:10:00')).toBe('2026-10-06 03:10');
    expect(formatAbsoluteDateTime('2026-10-06T23:59:59.123')).toBe('2026-10-06 23:59');
    expect(formatAbsoluteDateTime('형식 밖')).toBe('형식 밖');
  });
});

describe('showsRelativeTime(D10) — formatRelativeTime과 같은 7일 경계', () => {
  const created = '2026-10-01T12:00:00'; // KST
  const base = Date.parse('2026-10-01T12:00:00+09:00');
  const HOUR = 3_600_000;
  const DAY = 24 * HOUR;

  it('6일 23시간 → 상대 시각을 보인다("6일 전")', () => {
    const now = base + 6 * DAY + 23 * HOUR;
    expect(showsRelativeTime(created, now)).toBe(true);
    expect(formatRelativeTime(created, now)).toBe('6일 전');
  });

  it('정확히 7일 → 보이지 않는다(formatRelativeTime도 날짜로 바뀐다)', () => {
    const now = base + 7 * DAY;
    expect(showsRelativeTime(created, now)).toBe(false);
    expect(formatRelativeTime(created, now)).toBe('2026.10.01');
  });

  it('8일 → 보이지 않는다', () => {
    expect(showsRelativeTime(created, base + 8 * DAY)).toBe(false);
  });

  it('읽을 수 없는 값은 false', () => {
    expect(showsRelativeTime('형식 밖', base)).toBe(false);
  });
});

describe('appendUnique(D8)', () => {
  const n = (id: number): NotificationResponse => ({
    notificationId: id,
    notificationType: 'NEW_TRADE',
    title: `알림 ${id}`,
    isRead: false,
    createdAt: '2026-10-06T03:10:00',
  });

  it('notification_id가 겹치면 먼저 받은 것을 남긴다', () => {
    expect(appendUnique([n(3), n(2)], [n(2), n(1)]).map((x) => x.notificationId)).toEqual([3, 2, 1]);
  });
});
