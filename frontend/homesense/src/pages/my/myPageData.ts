import { getFavoriteProperties } from '../../features/favorite/api';
import type { FavoritePropertySummaryResponse } from '../../features/favorite/types';
import { getNotifications } from '../../features/notification/api';
import type { NotificationResponse } from '../../features/notification/types';
import { getMe } from '../../features/user/api';
import { compareRegistered } from '../favorites/favoritesModel';
import type { UserResponse } from '../../features/user/types';

export const FAVORITE_PREVIEW_COUNT = 2;
export const NOTIFICATION_PREVIEW_COUNT = 3;

/**
 * 관심 매물 미리보기 — 최근 등록순 최대 2건. MY-02 "등록순"과 같은 비교(registeredAt 내림차순, 같으면 ID 내림차순)를
 * 쓴다 — UI정의서 MY-01 "최근 등록 2건", MY-02 기본 정렬이 최신 먼저. 서버 목록에는 ORDER BY가 없다.
 */
export function pickRecentFavorites(
  favorites: FavoritePropertySummaryResponse[],
  count: number = FAVORITE_PREVIEW_COUNT,
): FavoritePropertySummaryResponse[] {
  const sortable = (f: FavoritePropertySummaryResponse) => ({
    id: f.favoritePropertyId,
    registeredAt: f.registeredAt,
    changeRate: null,
  });
  return [...favorites].sort((a, b) => compareRegistered(sortable(a), sortable(b))).slice(0, count);
}

export interface FavoritePreview {
  items: FavoritePropertySummaryResponse[];
}

// useLoadable은 렌더마다 바뀌지 않는 fetcher를 받는다 — 모듈 수준 함수로 둔다.
export const loadProfile = (): Promise<UserResponse> => getMe();

export const loadFavoritePreview = async (): Promise<FavoritePreview> => {
  const favorites = await getFavoriteProperties();
  return { items: pickRecentFavorites(favorites) };
};

/** 서버가 발생 시각(createdAt) 내림차순으로 준다. 유형 필터 없음(전체). */
export const loadRecentNotifications = (): Promise<NotificationResponse[]> =>
  getNotifications(0, NOTIFICATION_PREVIEW_COUNT);

export type NotificationTarget = 'property' | 'region' | 'unknown';

/**
 * 알림 대상의 종류 — BAT-NTF-01 D7: 관심 매물 알림은 complexId가 있고, 관심 지역 알림은 complexId 없이 legalDongCd만 있다.
 * 둘 다 없으면(대상이 지워짐) 알 수 없음.
 */
export function notificationTargetOf(notification: Pick<NotificationResponse, 'complexId' | 'legalDongCd'>): NotificationTarget {
  if (notification.complexId != null) return 'property';
  if (notification.legalDongCd != null) return 'region';
  return 'unknown';
}
