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
 * HOME-01은 favoritePropertyId/complexId만 써서 하트 상태를 하이드레이트한다(나머지는 MY-01·MY-02).
 *
 * recent*(거래가·거래일·전용면적·층)는 취소되지 않은 매매 거래 중 최신 1건이다(2026-10-02부터, 그 전에는 전월세도
 * 섞였다). 매매 거래가 없으면 모두 null이다. recentDealCategory는 이제 'SALE' 또는 null만 오지만, MY-01 미리보기가
 * 'RENT'를 비교하는 옛 분기를 그대로 두고 있어 타입은 넓게 둔다. changeRate는 최근 1개월 매매 평균과 그 직전 1개월
 * 매매 평균의 변동률(%)이고 어느 한쪽이 없으면 null이다. registeredAt은 타임존 없는 LocalDateTime 문자열이다.
 *
 * 서버는 null 필드를 JSON에서 아예 뺀다(`non_null` 직렬화) — 값이 없는 필드는 null이 아니라 키 자체가 없다. 그래서
 * 선택 필드(?)로 두고 `== null`로 검사한다(`=== null`이면 undefined가 통과해 "NaN만원"이 된다).
 */
export interface FavoritePropertySummaryResponse {
  favoritePropertyId: number;
  complexId: number;
  complexName: string;
  sido: string;
  sigungu: string;
  dongRi: string;
  housingType: HousingType;
  registeredAt: string;
  recentDealCategory?: 'SALE' | 'RENT' | null;
  recentDealDate?: string | null;
  recentAmount?: number | null;
  recentArea?: number | null;
  recentFloor?: number | null;
  changeRate?: number | null;
  hasNotificationSetting: boolean;
}

/**
 * GET /api/favorites/regions 목록 항목 — FavoriteRegionSummaryResponse.java 실제 필드 그대로. 통계 4종(avgPrice·
 * changeRate·pricePerPyeong·newTradeCount)은 RegionStatsCalculator의 "최근 1개월(직전 1개월 대비), 매매·미취소"
 * 기준이다. pricePerPyeong은 3.3㎡당 평균가(만원). sigunguName은 세종처럼 시군구 계층이 없으면 null이다.
 */
export interface FavoriteRegionSummaryResponse {
  favoriteRegionId: number;
  legalDongCd: string;
  fullPath: string;
  sidoName?: string | null;
  sigunguName?: string | null;
  eupmyeondongName: string;
  registeredAt: string;
  avgPrice?: number | null;
  changeRate?: number | null;
  pricePerPyeong?: number | null;
  newTradeCount: number;
}

/** POST /api/favorites/regions 응답 — FavoriteRegionResponse.java. */
export interface FavoriteRegionResponse {
  favoriteRegionId: number;
  legalDongCd: string;
  fullPath: string;
  registeredAt: string;
}
