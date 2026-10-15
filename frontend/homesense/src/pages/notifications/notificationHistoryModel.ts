import type { NotificationResponse, NotificationType } from '../../features/notification/types';
import { regionSearchHref } from '../favorites/favoritesModel';

/** 목록에 보이는 기간(개월) — 서버 homesense.notification.history.retention-months 기본값과 같아야 한다(CLAUDE.md "SCR-MY-04" D6). */
export const HISTORY_RETENTION_MONTHS = 6;
export const RETENTION_NOTICE = `발생 후 ${HISTORY_RETENTION_MONTHS}개월이 지난 알림은 자동 보관됩니다`;

/** 데스크톱·태블릿 번호형 한 페이지, 모바일 무한 스크롤 한 번에 불러오는 건수(D8). 서버 상한은 50. */
export const PAGED_SIZE = 10;
export const INFINITE_SIZE = 20;

/** SRCH-01 기본 화면 — 대상이 없는 알림의 대체 이동 위치(D1). */
export const MISSING_TARGET_FALLBACK = '/search';
export const MISSING_TARGET_MESSAGE = '현재 확인할 수 없는 매물입니다';

export type NotificationLink = { kind: 'complex'; href: string } | { kind: 'region'; href: string } | { kind: 'missing' };

/**
 * D1 — 알림 → 이동 위치. 관심 매물 알림은 complexId(DTL-01), 관심 지역 알림은 complexId 없이 legalDongCd(SRCH-01 지역 결과,
 * MY-02의 관심 지역 링크와 같은 빌더)다(BAT-NTF-01 D7). 둘 다 없으면(대상 단지·법정동이 지워져 FK가 NULL) missing.
 * tradeId가 있어도 DTL-01 진입까지만 한다 — DTL-01은 거래 상세 모달을 URL로 여는 방법이 없다(?deal=만 있다).
 * 알림에는 지역 이름이 없어 SRCH-01은 "선택한 지역"으로 표시한다.
 */
export function resolveNotificationLink(n: Pick<NotificationResponse, 'complexId' | 'legalDongCd'>): NotificationLink {
  if (n.complexId != null) return { kind: 'complex', href: `/complexes/${n.complexId}` };
  if (n.legalDongCd != null && n.legalDongCd !== '') {
    return { kind: 'region', href: regionSearchHref({ legalDongCd: n.legalDongCd, fullPath: '' }) };
  }
  return { kind: 'missing' };
}

export type PriceDirection = 'up' | 'down' | 'unknown';

/**
 * D4 — 가격변동 알림의 방향. 응답에 방향 필드가 없어 BAT-NTF-01 제목 형식("{대상명} 실거래가 2.1% 상승|하락")의 끝 단어로
 * 정한다. 형식 밖이면 unknown(방향을 지어내지 않는다). 무효화: 제목 형식이 바뀌거나 응답에 방향·변동률 필드가 생기면.
 */
export function priceDirectionOf(title: string): PriceDirection {
  const trimmed = title.trim();
  if (trimmed.endsWith('상승')) return 'up';
  if (trimmed.endsWith('하락')) return 'down';
  return 'unknown';
}

export type HistoryFilter = 'ALL' | NotificationType;

export const FILTER_OPTIONS: { value: HistoryFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'PRICE_CHANGE', label: '가격변동' },
  { value: 'NEW_TRADE', label: '신규거래' },
];

export interface HistoryParams {
  filter: HistoryFilter;
  /** 1-base(SRCH-01 관례). 모바일 무한 스크롤에서는 쓰지 않는다. */
  page: number;
}

/** URL → 화면 상태. 모르는 type은 전체, 숫자가 아니거나 1보다 작은 page는 1(범위를 넘는 page는 응답을 받은 뒤 고친다). */
export function parseHistoryParams(params: URLSearchParams): HistoryParams {
  const type = params.get('type');
  const filter: HistoryFilter = type === 'PRICE_CHANGE' || type === 'NEW_TRADE' ? type : 'ALL';
  const rawPage = Number(params.get('page'));
  const page = Number.isInteger(rawPage) && rawPage >= 1 ? rawPage : 1;
  return { filter, page };
}

/** 화면 상태 → URL. 기본값(전체, 1페이지)은 생략한다. */
export function serializeHistoryParams({ filter, page }: HistoryParams): URLSearchParams {
  const params = new URLSearchParams();
  if (filter !== 'ALL') params.set('type', filter);
  if (page > 1) params.set('page', String(page));
  return params;
}

/** URL이 정규화된 값과 다르면(모르는 type, 0·음수·숫자 아닌 page) true — replace로 고친다. */
export function needsNormalization(params: URLSearchParams): boolean {
  return serializeHistoryParams(parseHistoryParams(params)).toString() !== normalizedOrder(params);
}

function normalizedOrder(params: URLSearchParams): string {
  const known = new URLSearchParams();
  const type = params.get('type');
  const page = params.get('page');
  if (type !== null) known.set('type', type);
  if (page !== null) known.set('page', page);
  return known.toString();
}

/** 범위를 넘는 page → 마지막 페이지(0건이면 1). 범위 안이면 그대로. */
export function clampPage(page: number, totalPages: number): number {
  return Math.min(page, Math.max(1, totalPages));
}

/**
 * D10 — 절대 시각 "YYYY-MM-DD HH:mm". 서버 LocalDateTime(타임존 없음, KST 기준 — CLAUDE.md 날짜/시간 처리)을 문자열 그대로
 * 잘라 쓴다(브라우저 타임존으로 다시 계산하지 않는다 — formatRelativeTime의 날짜 부분과 같은 방식). 형식이 다르면 원문.
 */
export function formatAbsoluteDateTime(value: string): string {
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(value);
  return match ? `${match[1]} ${match[2]}` : value;
}

/** 유형 필터 빈 상태 문구(D9). 전체 빈 상태는 별도 화면. */
export const FILTER_EMPTY_MESSAGE: Record<NotificationType, string> = {
  PRICE_CHANGE: '가격변동 알림이 없어요',
  NEW_TRADE: '신규거래 알림이 없어요',
};

/** 무한 스크롤로 이어 붙일 때 notification_id 기준 중복 제거(D8) — 앞쪽(먼저 받은 것)을 남긴다. */
export function appendUnique(prev: NotificationResponse[], next: NotificationResponse[]): NotificationResponse[] {
  const seen = new Set(prev.map((n) => n.notificationId));
  return [...prev, ...next.filter((n) => !seen.has(n.notificationId))];
}
