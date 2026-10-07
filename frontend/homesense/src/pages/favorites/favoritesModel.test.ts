import { describe, expect, it } from 'vitest';
import type { NotificationSettingResponse } from '../../features/notification/types';
import { isEupmyeondongCode } from '../../features/region/regionLevel';
import {
  compareRate,
  compareRegistered,
  describeNotificationBadge,
  parseSort,
  parseTab,
  regionSearchHref,
  resolveNotificationBadge,
  sortFavorites,
} from './favoritesModel';

const item = (id: number, registeredAt: string, changeRate: number | null) => ({ id, registeredAt, changeRate });

describe('정렬', () => {
  it('등록순은 최근 등록이 먼저이고, 같은 시각이면 ID가 큰 쪽이 먼저다', () => {
    const items = [item(1, '2026-09-01T10:00:00', null), item(3, '2026-09-03T10:00:00', null), item(2, '2026-09-03T10:00:00', null)];
    expect([...items].sort(compareRegistered).map((i) => i.id)).toEqual([3, 2, 1]);
  });

  it('변동률순은 내림차순이고 null은 맨 뒤, 같으면 등록순이다', () => {
    const items = [
      item(1, '2026-09-01T10:00:00', null),
      item(2, '2026-09-02T10:00:00', -3.5),
      item(3, '2026-09-03T10:00:00', 2.1),
      item(4, '2026-09-04T10:00:00', 2.1),
      item(5, '2026-09-05T10:00:00', null),
      item(6, '2026-09-06T10:00:00', 0),
    ];
    expect([...items].sort(compareRate).map((i) => i.id)).toEqual([4, 3, 6, 2, 5, 1]);
  });

  it('sortFavorites는 원본을 바꾸지 않는다', () => {
    const items = [item(1, '2026-09-01T10:00:00', 1), item(2, '2026-09-02T10:00:00', 5)];
    const sorted = sortFavorites(items, 'rate', (i) => i);
    expect(sorted.map((i) => i.id)).toEqual([2, 1]);
    expect(items.map((i) => i.id)).toEqual([1, 2]);
  });

  it('URL 값을 읽는다 — 모르는 값은 기본값', () => {
    expect(parseSort('rate')).toBe('rate');
    expect(parseSort('weird')).toBe('registered');
    expect(parseSort(null)).toBe('registered');
    expect(parseTab('regions')).toBe('regions');
    expect(parseTab('x')).toBe('properties');
  });
});

describe('알림조건 배지 문구(D4)', () => {
  const setting = (overrides: Partial<NotificationSettingResponse>): NotificationSettingResponse => ({
    notificationSettingId: 1,
    favoritePropertyId: 10,
    favoriteRegionId: null,
    priceChangeThresholdPct: 5,
    newTradeAlertYn: false,
    emailAlertYn: true,
    ...overrides,
  });
  it('설정 없음 → "알림 설정"', () => {
    expect(describeNotificationBadge(undefined)).toEqual({ label: '알림 설정', tone: 'unset' });
  });

  it('이메일 수신 꺼짐 → "이메일 꺼짐"(알림 이력에는 남는다)', () => {
    expect(describeNotificationBadge(setting({ emailAlertYn: false, newTradeAlertYn: true }))).toEqual({
      label: '이메일 꺼짐',
      tone: 'off',
    });
  });

  it('임계치만 → "±n% 알림", 소수 임계치는 한 자리', () => {
    expect(describeNotificationBadge(setting({})).label).toBe('±5% 알림');
    expect(describeNotificationBadge(setting({ priceChangeThresholdPct: 2.5 })).label).toBe('±2.5% 알림');
  });

  it('임계치 + 신규거래 → "±n% · 신규거래"', () => {
    expect(describeNotificationBadge(setting({ newTradeAlertYn: true }))).toEqual({ label: '±5% · 신규거래', tone: 'on' });
  });

  it('알림 설정 목록이 없으면(로딩 중·조회 실패) 배지를 숨긴다 — "알림 설정"으로 보이지 않는다', () => {
    expect(resolveNotificationBadge(null, 10)).toBeNull();
  });

  it('목록이 있으면 그 항목의 설정으로, 없는 항목은 "알림 설정"', () => {
    const map = new Map([[10, setting({ newTradeAlertYn: true })]]);
    expect(resolveNotificationBadge(map, 10)?.label).toBe('±5% · 신규거래');
    expect(resolveNotificationBadge(map, 11)).toEqual({ label: '알림 설정', tone: 'unset' });
  });
});

describe('읍면동 코드 판정', () => {
  it.each([
    ['1100000000', false, '시도'],
    ['1168000000', false, '시군구 대표행'],
    ['4111100000', false, '구(시+구 도시)'],
    ['2671025021', false, '리'],
    ['1168010100', true, '동'],
    ['2671025000', true, '읍'],
    ['116801010', false, '9자리'],
    ['11680A0100', false, '숫자 아님'],
  ])('%s → %s (%s)', (code, expected) => {
    expect(isEupmyeondongCode(code)).toBe(expected);
  });
});

describe('관심 지역 → SRCH-01 링크', () => {
  it('regionCode와 regionLabel만 싣는다(나머지 필터는 기본값이라 생략)', () => {
    const href = regionSearchHref({ legalDongCd: '4155036000', fullPath: '경기도 안성시 금광면' });
    const params = new URLSearchParams(href.split('?')[1]);
    expect(href.startsWith('/search?')).toBe(true);
    expect(params.get('regionCode')).toBe('4155036000');
    expect(params.get('regionLabel')).toBe('경기도 안성시 금광면');
    expect([...params.keys()].sort()).toEqual(['regionCode', 'regionLabel']);
  });
});
