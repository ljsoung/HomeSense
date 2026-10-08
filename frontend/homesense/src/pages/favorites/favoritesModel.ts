import type { FavoriteRegionSummaryResponse } from '../../features/favorite/types';
import type { NotificationSettingResponse } from '../../features/notification/types';
import { defaultFilters, serializeSearchParams } from '../../features/search/searchParams';

/** 정렬 — URL `?sort=rate`. 없거나 모르는 값은 등록순. */
export type FavoritesSort = 'registered' | 'rate';
/** 태블릿·모바일 탭 — URL `?tab=regions`. 없거나 모르는 값은 관심 매물. 데스크톱(세로 섹션)에서는 쓰지 않는다. */
export type FavoritesTab = 'properties' | 'regions';

/**
 * 화면 크기별 배치 — 모바일(767px 이하)·태블릿(768~1279px)·데스크톱(1280px 이상). Figma 세 프레임의 카드 구성이 서로
 * 달라 화면이 이 값으로 한 벌만 그린다(반응형 렌더 규칙).
 */
export type FavoritesLayout = 'mobile' | 'tablet' | 'desktop';

export const SORT_QUERY_VALUE: Record<FavoritesSort, string | null> = { registered: null, rate: 'rate' };
export const TAB_QUERY_VALUE: Record<FavoritesTab, string | null> = { properties: null, regions: 'regions' };

export function parseSort(raw: string | null): FavoritesSort {
  return raw === 'rate' ? 'rate' : 'registered';
}

export function parseTab(raw: string | null): FavoritesTab {
  return raw === 'regions' ? 'regions' : 'properties';
}

interface Sortable {
  id: number;
  registeredAt: string;
  changeRate: number | null;
}

/** 등록순 = 최근 등록이 먼저. registeredAt이 같으면 ID가 큰(나중에 저장된) 쪽이 먼저. */
export function compareRegistered(a: Sortable, b: Sortable): number {
  if (a.registeredAt !== b.registeredAt) return a.registeredAt < b.registeredAt ? 1 : -1;
  return b.id - a.id;
}

/** 변동률순 = 변동률 내림차순, null은 맨 뒤, 같으면 등록순. */
export function compareRate(a: Sortable, b: Sortable): number {
  if (a.changeRate === null && b.changeRate === null) return compareRegistered(a, b);
  if (a.changeRate === null) return 1;
  if (b.changeRate === null) return -1;
  if (a.changeRate !== b.changeRate) return b.changeRate - a.changeRate;
  return compareRegistered(a, b);
}

/** 원본을 바꾸지 않고 정렬한 새 배열을 돌려준다. */
export function sortFavorites<T>(items: readonly T[], sort: FavoritesSort, toSortable: (item: T) => Sortable): T[] {
  const compare = sort === 'rate' ? compareRate : compareRegistered;
  return [...items].sort((a, b) => compare(toSortable(a), toSortable(b)));
}

export type NotificationBadgeTone = 'unset' | 'off' | 'on';

export interface NotificationBadge {
  label: string;
  tone: NotificationBadgeTone;
}

function formatThreshold(pct: number): string {
  return Number.isInteger(pct) ? String(pct) : pct.toFixed(1);
}

/**
 * 관심 매물 알림조건 배지 문구(MY-02 결정 D4) — Figma의 더미 문구("지역변동 9%")를 쓰지 않고 실제 설정 값에서 만든다.
 * - 설정 없음 → "알림 설정"(설정 유도)
 * - 이메일 수신 꺼짐 → "이메일 꺼짐" — 2026-10-07 "알림 꺼짐"에서 바꿨다(MY-03 D8). BAT-NTF-01은 email_alert_yn과 무관하게 알림을
 *   만들어 알림 이력(MY-04)에는 남고 이메일만 가지 않으므로 "알림 꺼짐"은 사실과 달랐다. MY-03 대상 행 보조 텍스트도 이 함수를 쓴다.
 * - 임계치 + 신규거래 → "±{n}% · 신규거래", 임계치만 → "±{n}% 알림"
 * - 임계치 0 → "모든 변동 · 신규거래" / "모든 변동 알림" — 2026-10-08 "±0%"에서 바꿨다. "±0%"는 "변동 없음"·"꺼짐"으로 읽힐 수
 *   있는데 0은 "조금이라도 오르거나 내리면 알림"이다(MY-03 D7).
 * 알림 설정 목록을 아직 받지 못했거나 조회가 실패하면 배지를 그리지 않는다(호출부) — 그때 "알림 설정"을 보이면 설정이
 * 있는 사용자에게 틀린 정보가 되고, 관심 매물 응답의 hasNotificationSetting만으로는 "이메일 꺼짐"을 가릴 수 없다.
 */
export function describeNotificationBadge(setting: NotificationSettingResponse | undefined): NotificationBadge {
  if (!setting) return { label: '알림 설정', tone: 'unset' };
  if (!setting.emailAlertYn) return { label: '이메일 꺼짐', tone: 'off' };
  const threshold =
    setting.priceChangeThresholdPct === 0 ? '모든 변동' : `±${formatThreshold(setting.priceChangeThresholdPct)}%`;
  return setting.newTradeAlertYn
    ? { label: `${threshold} · 신규거래`, tone: 'on' }
    : { label: `${threshold} 알림`, tone: 'on' };
}

/**
 * 카드에 보일 배지. 알림 설정 목록이 없으면(로딩 중·조회 실패 — `settingsByProperty`가 null) null을 돌려 배지를 숨긴다.
 */
export function resolveNotificationBadge(
  settingsByProperty: ReadonlyMap<number, NotificationSettingResponse> | null,
  favoritePropertyId: number,
): NotificationBadge | null {
  if (settingsByProperty === null) return null;
  return describeNotificationBadge(settingsByProperty.get(favoritePropertyId));
}

/** SRCH-01이 읽는 파라미터(regionCode·regionLabel)로 그 지역 검색 결과 링크를 만든다. 나머지 필터는 기본값. */
export function regionSearchHref(region: Pick<FavoriteRegionSummaryResponse, 'legalDongCd' | 'fullPath'>): string {
  const params = serializeSearchParams({ ...defaultFilters(), regionCode: region.legalDongCd, regionLabel: region.fullPath });
  return `/search?${params.toString()}`;
}
