import { getFavoriteProperties } from '../../features/favorite/api';
import type { FavoritePropertySummaryResponse } from '../../features/favorite/types';
import { getNotifications } from '../../features/notification/api';
import type { NotificationResponse } from '../../features/notification/types';
import { getMe } from '../../features/user/api';
import type { UserResponse } from '../../features/user/types';

export const FAVORITE_PREVIEW_COUNT = 2;
export const NOTIFICATION_PREVIEW_COUNT = 3;

/**
 * 관심 매물 미리보기 — 최근 등록순 최대 2건. 백엔드 목록(FavoritePropertyRepository.findByUser_UserId)에는 ORDER BY가
 * 없고 응답에 등록일시도 없어 favoritePropertyId 내림차순으로 정렬한다. ID는 AUTO_INCREMENT이고 registered_at은
 * 저장 시점의 현재 시각이라(FavoriteProperty.register) ID 순서가 등록 순서와 같다 — 인기 단지 대체 정렬이 complex_id를
 * 등록 순서로 쓴 것과 같은 근거(CLAUDE.md SVC-CPX-01 절).
 */
export function pickRecentFavorites(
  favorites: FavoritePropertySummaryResponse[],
  count: number = FAVORITE_PREVIEW_COUNT,
): FavoritePropertySummaryResponse[] {
  return [...favorites].sort((a, b) => b.favoritePropertyId - a.favoritePropertyId).slice(0, count);
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

/** 서버가 발송 시각 내림차순으로 준다. 유형 필터 없음(전체). */
export const loadRecentNotifications = (): Promise<NotificationResponse[]> =>
  getNotifications(0, NOTIFICATION_PREVIEW_COUNT);
