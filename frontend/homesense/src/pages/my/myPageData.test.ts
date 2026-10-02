import { describe, expect, it } from 'vitest';
import type { FavoritePropertySummaryResponse } from '../../features/favorite/types';
import { pickRecentFavorites } from './myPageData';

function favorite(favoritePropertyId: number): FavoritePropertySummaryResponse {
  return {
    favoritePropertyId,
    complexId: favoritePropertyId * 10,
    complexName: `단지${favoritePropertyId}`,
    sido: '서울특별시',
    sigungu: '종로구',
    dongRi: '숭인동',
    housingType: 'APT',
    recentDealCategory: null,
    recentDealDate: null,
    recentAmount: null,
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
