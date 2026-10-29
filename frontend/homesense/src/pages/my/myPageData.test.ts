import { describe, expect, it } from 'vitest';
import type { FavoritePropertySummaryResponse } from '../../features/favorite/types';
import { notificationTargetOf, pickRecentFavorites } from './myPageData';

function favorite(favoritePropertyId: number, registeredAt = '2026-09-01T10:00:00'): FavoritePropertySummaryResponse {
  return {
    favoritePropertyId,
    complexId: favoritePropertyId * 10,
    complexName: `단지${favoritePropertyId}`,
    sido: '서울특별시',
    sigungu: '종로구',
    dongRi: '숭인동',
    housingType: 'APT',
    registeredAt,
    recentDealCategory: null,
    recentDealDate: null,
    recentAmount: null,
    recentArea: null,
    recentFloor: null,
    changeRate: null,
    hasNotificationSetting: false,
  };
}

describe('pickRecentFavorites', () => {
  it('응답 순서와 무관하게 최근 등록(ID 큰) 순으로 최대 2건을 고른다', () => {
    const result = pickRecentFavorites([favorite(3), favorite(9), favorite(1), favorite(7)]);
    expect(result.map((f) => f.favoritePropertyId)).toEqual([9, 7]);
  });

  it('2건 미만이면 있는 만큼만 돌려주고 원본 배열은 바꾸지 않는다', () => {
    const input = [favorite(1)];
    expect(pickRecentFavorites(input).map((f) => f.favoritePropertyId)).toEqual([1]);
    expect(pickRecentFavorites([])).toEqual([]);
    const many = [favorite(1), favorite(2)];
    pickRecentFavorites(many);
    expect(many.map((f) => f.favoritePropertyId)).toEqual([1, 2]);
  });
});

describe('pickRecentFavorites — 등록일시 기준', () => {
  it('ID 순서와 달라도 registeredAt이 최신인 것이 먼저다', () => {
    const result = pickRecentFavorites([
      favorite(9, '2026-09-01T10:00:00'),
      favorite(2, '2026-10-01T10:00:00'),
      favorite(5, '2026-09-15T10:00:00'),
    ]);
    expect(result.map((f) => f.favoritePropertyId)).toEqual([2, 5]);
  });
});

describe('notificationTargetOf', () => {
  it('complexId가 있으면 관심 매물(legalDongCd가 함께 있어도), legalDongCd만 있으면 관심 지역, 둘 다 없으면 알 수 없음', () => {
    expect(notificationTargetOf({ complexId: 1, legalDongCd: '1111017400' })).toBe('property');
    expect(notificationTargetOf({ complexId: 1 })).toBe('property');
    expect(notificationTargetOf({ legalDongCd: '1111017400' })).toBe('region');
    expect(notificationTargetOf({ complexId: null, legalDongCd: null })).toBe('unknown');
    expect(notificationTargetOf({})).toBe('unknown');
  });
});
