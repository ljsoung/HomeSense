import type { DealCategory, RentType } from '../complex/types';
import { appendFilterApiParams, parseSearchParams, serializeSearchParams, type SearchFilters } from '../search/searchParams';

/**
 * MAP-01 URL 상태. 필터는 SRCH-01과 같은 파라미터 이름·같은 모듈(features/search/searchParams.ts)로 파싱·직렬화해 두 화면을
 * 오가도 필터가 유지된다(UI정의서 4.4절). 지도 전용 상태는 뷰포트(lat·lng·level)와 선택한 단지(complexId)뿐이다.
 * page는 지도에 없으므로 지도 URL에서는 버리고, SRCH-01로 돌아갈 때 첫 페이지로 간다.
 */

export interface MapViewport {
  lat: number;
  lng: number;
  /** 카카오맵 지도 레벨(1~14, 작을수록 확대). */
  level: number;
}

export interface MapBounds {
  swLat: number;
  swLng: number;
  neLat: number;
  neLng: number;
}

export interface MapUrlState {
  filters: SearchFilters;
  viewport?: MapViewport;
  complexId?: number;
}

export const MIN_LEVEL = 1;
export const MAX_LEVEL = 14;

/**
 * 기본 위치 — 서울시청 부근, 동네 몇 개가 보이는 레벨. UI정의서의 "접속 지역 인근"은 IP 위치 조회가 필요해 MVP에서는 이
 * 고정값으로 대신한다(CLAUDE.md "SCR-MAP-01" 절).
 */
export const DEFAULT_VIEWPORT: MapViewport = { lat: 37.5666, lng: 126.9784, level: 6 };

/** 단지로 들어올 때(DTL-01 "지도에서 크게 보기", 목록 항목 선택)의 레벨 — 단지 주변 몇 블록이 보인다. */
export const COMPLEX_LEVEL = 3;

function parseNumber(raw: string | null): number | undefined {
  if (raw === null || raw.trim() === '') return undefined;
  const value = Number(raw);
  return Number.isFinite(value) ? value : undefined;
}

export function isValidViewport(v: Partial<MapViewport> | null | undefined): v is MapViewport {
  return (
    !!v &&
    typeof v.lat === 'number' &&
    typeof v.lng === 'number' &&
    typeof v.level === 'number' &&
    Number.isFinite(v.lat) &&
    Number.isFinite(v.lng) &&
    v.lat >= -90 &&
    v.lat <= 90 &&
    v.lng >= -180 &&
    v.lng <= 180 &&
    Number.isInteger(v.level) &&
    v.level >= MIN_LEVEL &&
    v.level <= MAX_LEVEL
  );
}

export function parseMapUrl(params: URLSearchParams): MapUrlState {
  const filters = { ...parseSearchParams(params), page: 1 };
  const lat = parseNumber(params.get('lat'));
  const lng = parseNumber(params.get('lng'));
  const level = parseNumber(params.get('level'));
  const candidate = { lat, lng, level: level ?? COMPLEX_LEVEL };
  // level만 빠진 단지 링크(complexId + 좌표)도 받는다 — 레벨은 단지 레벨로 둔다.
  const viewport = isValidViewport(candidate) && (level !== undefined || params.has('complexId')) ? candidate : undefined;
  const complexIdRaw = parseNumber(params.get('complexId'));
  const complexId = complexIdRaw !== undefined && Number.isInteger(complexIdRaw) && complexIdRaw > 0 ? complexIdRaw : undefined;
  return { filters, viewport, complexId };
}

/** 좌표는 소수 6자리(약 0.1m)로 줄인다 — 지도를 조금만 움직여도 긴 소수가 URL에 쌓이지 않게 한다. */
function roundCoord(value: number): string {
  return value.toFixed(6).replace(/\.?0+$/, '');
}

export function serializeMapUrl({ filters, viewport, complexId }: MapUrlState): URLSearchParams {
  const params = serializeSearchParams({ ...filters, page: 1 });
  if (viewport) {
    params.set('lat', roundCoord(viewport.lat));
    params.set('lng', roundCoord(viewport.lng));
    params.set('level', String(viewport.level));
  }
  if (complexId !== undefined) params.set('complexId', String(complexId));
  return params;
}

/** SRCH-01 → MAP-01. page를 버리고 필터만 넘긴다(지도 뷰포트는 지역 라벨이나 마지막 위치로 정한다). */
export function buildMapHref(filters: SearchFilters, extra?: Omit<MapUrlState, 'filters'>): string {
  const query = serializeMapUrl({ filters, ...extra }).toString();
  return query ? `/map?${query}` : '/map';
}

/** MAP-01 → SRCH-01. 지도 상태(뷰포트·선택)를 버리고 첫 페이지로 간다. */
export function buildSearchHref(filters: SearchFilters): string {
  const query = serializeSearchParams({ ...filters, page: 1 }).toString();
  return query ? `/search?${query}` : '/search';
}

/** DTL-01 "지도에서 크게 보기" — 그 단지로 이동해 핀을 강조하고 팝업을 연다. */
export function buildComplexMapHref(complexId: number, lat: number, lng: number): string {
  const params = new URLSearchParams();
  params.set('lat', roundCoord(lat));
  params.set('lng', roundCoord(lng));
  params.set('level', String(COMPLEX_LEVEL));
  params.set('complexId', String(complexId));
  return `/map?${params.toString()}`;
}

/** 백엔드 BoundsCondition 형식 "swLat,swLng,neLat,neLng". 경계를 포함하므로 소수 7자리(DB 정밀도)로 보낸다. */
export function formatBounds(b: MapBounds): string {
  return [b.swLat, b.swLng, b.neLat, b.neLng].map((v) => v.toFixed(7)).join(',');
}

export function buildMapApiQuery(filters: SearchFilters, bounds: MapBounds): URLSearchParams {
  const params = new URLSearchParams();
  params.set('bounds', formatBounds(bounds));
  appendFilterApiParams(params, filters);
  return params;
}

/**
 * 지도 조회의 필터 부분(bounds 제외, 정렬된 문자열) — 지도 API에 실리는 필터만이라 정렬·페이지·지역·검색어는 들어가지 않는다.
 * 선택 단지의 값(응답 항목·마지막으로 알던 값)이 어느 필터 기준인지 비교하는 데 쓴다.
 */
export function mapFilterKey(filters: SearchFilters): string {
  const params = new URLSearchParams();
  appendFilterApiParams(params, filters);
  params.sort();
  return params.toString();
}

/** 지도 조회 문자열(buildMapApiQuery 결과)의 필터 부분 — mapFilterKey와 같은 형태. */
export function filterKeyOfMapQuery(query: string): string {
  const params = new URLSearchParams(query);
  params.delete('bounds');
  params.sort();
  return params.toString();
}

/**
 * SDK 없이 화면 크기와 레벨로 bounds를 근사한다 — 지도 SDK를 불러오지 못해도 목록 패널은 동작해야 하기 때문이다(UI정의서
 * 예외 처리). 카카오맵은 레벨이 1 오를 때마다 축척이 2배가 되고, 레벨 3에서 1px이 약 0.9m다(2026-10-13 실제 SDK 화면의 축척
 * 막대로 잰 값 — 레벨 4에서 100m ≈ 60px, 레벨 8에서 2km ≈ 58px). 정확한 축척이 아니라 목록이 화면에 보이는 지도와 대략
 * 같은 범위를 갖게 하는 것이 목적이다.
 */
export function approximateBounds(center: { lat: number; lng: number }, level: number, widthPx: number, heightPx: number): MapBounds {
  const metersPerPx = 0.9 * 2 ** (level - 3);
  const halfHeightM = (heightPx * metersPerPx) / 2;
  const halfWidthM = (widthPx * metersPerPx) / 2;
  const dLat = halfHeightM / 111_320;
  const dLng = halfWidthM / (111_320 * Math.cos((center.lat * Math.PI) / 180));
  const clampLat = (v: number) => Math.max(-90, Math.min(90, v));
  const clampLng = (v: number) => Math.max(-180, Math.min(180, v));
  return {
    swLat: clampLat(center.lat - dLat),
    swLng: clampLng(center.lng - dLng),
    neLat: clampLat(center.lat + dLat),
    neLng: clampLng(center.lng + dLng),
  };
}

/** 법정동코드 단위로 지역 검색 후 레벨: 시도 → 10, 시군구 → 7, 읍면동·리 → 5. */
export function levelForRegionCode(regionCode: string | undefined): number {
  if (!regionCode || !/^\d{10}$/.test(regionCode)) return 6;
  if (regionCode.endsWith('00000000')) return 10;
  if (regionCode.endsWith('00000')) return 7;
  return 5;
}

export interface PointView {
  kind: 'point';
  viewport: MapViewport;
  source: 'complex' | 'viewport' | 'stored' | 'default';
}

/** 지역 라벨을 지오코딩해 그 중심으로 간다. 실패하면 fallback. */
export interface RegionView {
  kind: 'region';
  query: string;
  level: number;
  fallback: PointView;
}

/** complexId만 있고 좌표가 없다 — 단지 좌표를 조회한 뒤 그 위치로 간다. 실패하면 fallback(다음 순위). */
export interface ComplexView {
  kind: 'complex';
  complexId: number;
  fallback: PointView | RegionView;
}

export type InitialView = PointView | RegionView | ComplexView;

/**
 * 진입 시 초기 중심 결정 순서(MAP-01):
 * 1) complexId(+좌표) — DTL-01 "지도에서 크게 보기"
 * 2) URL의 lat·lng·level — 새로고침·상세에서 뒤로
 * 3) regionLabel — SRCH-01 "지도로 보기"(SDK Geocoder로 지역명 중심)
 * 4) 마지막으로 본 위치(localStorage)
 * 5) 기본 위치(서울시청 부근)
 */
export function resolveInitialView(state: MapUrlState, stored: MapViewport | null): InitialView {
  const lastResort: PointView = stored
    ? { kind: 'point', viewport: stored, source: 'stored' }
    : { kind: 'point', viewport: DEFAULT_VIEWPORT, source: 'default' };

  const withoutComplex = (): PointView | RegionView => {
    if (state.viewport) return { kind: 'point', viewport: state.viewport, source: 'viewport' };
    const label = state.filters.regionLabel?.trim();
    if (label) return { kind: 'region', query: label, level: levelForRegionCode(state.filters.regionCode), fallback: lastResort };
    return lastResort;
  };

  if (state.complexId !== undefined) {
    if (state.viewport) return { kind: 'point', viewport: state.viewport, source: 'complex' };
    return { kind: 'complex', complexId: state.complexId, fallback: withoutComplex() };
  }
  return withoutComplex();
}

/** 클러스터 배지 — 100건 이상은 "99+". */
export function formatClusterCount(count: number): string {
  return count >= 100 ? '99+' : String(count);
}

/** 1억 이상은 소수 첫째 자리 억(".0"은 뗀다), 그 아래는 만 단위. */
function shortManwon(amount: number): string {
  if (amount >= 10_000) {
    const eok = Math.round(amount / 1_000) / 10;
    return `${Number.isInteger(eok) ? eok : eok.toFixed(1)}억`;
  }
  return `${amount.toLocaleString('ko-KR')}만`;
}

/**
 * 지도 핀(가격 말풍선)의 짧은 가격 — Figma 6:4594 등("3억", "4.1억"). 금액 기준은 카드와 같다(매매 거래금액, 전월세
 * 보증금). 월세는 보증금/월세를 함께 보인다("5,000만/50") — FR-3.3. Figma가 "3억"과 "3.0억"을 섞어 써 ".0"은 떼는 쪽으로
 * 통일했다.
 */
export function formatPinPrice(
  dealCategory: DealCategory,
  rentType: RentType | null | undefined,
  amount: number,
  monthlyRentAmount: number | null | undefined,
): string {
  if (dealCategory === 'RENT' && rentType === 'WOLSE') {
    return `${shortManwon(amount)}/${(monthlyRentAmount ?? 0).toLocaleString('ko-KR')}`;
  }
  return shortManwon(amount);
}
