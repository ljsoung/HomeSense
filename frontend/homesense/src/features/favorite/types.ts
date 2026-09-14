import type { HousingType } from '../complex/types';

/** POST /api/favorites/properties 응답 — FavoritePropertyResponse.java 실제 필드 그대로. */
export interface FavoritePropertyResponse {
  favoritePropertyId: number;
  complexId: number;
  complexName: string;
  housingType: HousingType;
  registeredAt: string;
}

/**
 * GET /api/favorites/properties 목록 항목 — FavoritePropertySummaryResponse.java 실제 필드 그대로.
 * HOME-01은 favoritePropertyId/complexId만 써서 하트 상태를 하이드레이트한다(나머지는 MY-02 전용).
 */
export interface FavoritePropertySummaryResponse {
  favoritePropertyId: number;
  complexId: number;
  complexName: string;
  sido: string;
  sigungu: string;
  dongRi: string;
  housingType: HousingType;
  recentDealCategory: 'SALE' | 'RENT' | null;
  recentDealDate: string | null;
  recentAmount: number | null;
  changeRate: number | null;
  hasNotificationSetting: boolean;
}
