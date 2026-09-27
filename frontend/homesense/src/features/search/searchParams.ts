import type { DealCategory, HousingType, RentType } from '../complex/types';

export type DealTypeUi = '매매' | '전세' | '월세';
export type SortUi = 'LATEST' | 'AMOUNT' | 'AREA';

export const HOUSING_TYPE_OPTIONS: { value: HousingType; label: string }[] = [
  { value: 'APT', label: '아파트' },
  { value: 'VILLA', label: '연립다세대' },
];

export const DEAL_TYPE_OPTIONS: { value: DealTypeUi; label: string }[] = [
  { value: '매매', label: '매매' },
  { value: '전세', label: '전세' },
  { value: '월세', label: '월세' },
];

export const SORT_OPTIONS: { value: SortUi; label: string }[] = [
  { value: 'LATEST', label: '최신순' },
  { value: 'AMOUNT', label: '금액순' },
  { value: 'AREA', label: '면적순' },
];

/**
 * 슬라이더 범위(확정 사항 #7) — 손잡이가 상한에 있으면 "이상" 의미로 그 파라미터 자체를 생략한다.
 * 건축년도 하한은 0단계 실측(로컬 DB `MIN(YEAR(approval_date))`가 1968, 1968/1970년 준공이 각 2/1건
 * 뿐인 꼬리값이라 더 둥근 1970으로 잡았다 — CLAUDE.md에 근거와 함께 기록).
 */
export const AREA_RANGE = { min: 10, max: 200 } as const;
export const AMOUNT_RANGE = { min: 0, max: 200000 } as const; // 만원 단위, 20억
export const BUILD_YEAR_MIN = 1970;
export const buildYearMax = () => new Date().getFullYear();

export interface SearchFilters {
  regionCode?: string;
  regionLabel?: string;
  keyword?: string;
  housingTypes: HousingType[];
  dealType: DealTypeUi;
  areaMin: number;
  areaMax: number;
  amountMin: number;
  amountMax: number;
  buildYearMin: number;
  buildYearMax: number;
  sort: SortUi;
  page: number; // 1-base(UI 표시용) — API 호출 직전에만 0-base로 변환한다.
}

export function defaultFilters(): SearchFilters {
  return {
    housingTypes: ['APT', 'VILLA'],
    dealType: '매매',
    areaMin: AREA_RANGE.min,
    areaMax: AREA_RANGE.max,
    amountMin: AMOUNT_RANGE.min,
    amountMax: AMOUNT_RANGE.max,
    buildYearMin: BUILD_YEAR_MIN,
    buildYearMax: buildYearMax(),
    sort: 'LATEST',
    page: 1,
  };
}

function dealTypeFromUrl(raw: string | null): DealTypeUi {
  return raw === '전세' || raw === '월세' ? raw : '매매';
}

/** DealTypeUi → 백엔드 파라미터(확정 사항 #6): 매매=dealCategory=SALE, 전세=rentType=JEONSE, 월세=rentType=WOLSE. */
export function dealTypeToApiParams(dealType: DealTypeUi): { dealCategory?: DealCategory; rentType?: RentType } {
  if (dealType === '매매') return { dealCategory: 'SALE' };
  if (dealType === '전세') return { rentType: 'JEONSE' };
  return { rentType: 'WOLSE' };
}

/** URL 쿼리스트링(재검색·새로고침·직접 진입 시 URL이 유일한 진실 소스)에서 필터를 복원한다. */
export function parseSearchParams(params: URLSearchParams): SearchFilters {
  const defaults = defaultFilters();
  const housingTypesRaw = params.getAll('housingTypes').length
    ? params.getAll('housingTypes')
    : (params.get('housingTypes')?.split(',') ?? []);
  const housingTypes = housingTypesRaw.filter((v): v is HousingType => v === 'APT' || v === 'VILLA');

  const sortRaw = params.get('sort');
  const sort: SortUi = sortRaw === 'AMOUNT' || sortRaw === 'AREA' ? sortRaw : 'LATEST';

  const num = (key: string, fallback: number) => {
    const raw = params.get(key);
    if (raw === null || raw === '') return fallback;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : fallback;
  };

  const page = Math.max(1, num('page', 1));

  return {
    regionCode: params.get('regionCode') ?? undefined,
    regionLabel: params.get('regionLabel') ?? undefined,
    keyword: params.get('keyword') ?? undefined,
    housingTypes: housingTypes.length > 0 ? housingTypes : defaults.housingTypes,
    dealType: dealTypeFromUrl(params.get('dealType')),
    areaMin: num('areaMin', defaults.areaMin),
    areaMax: num('areaMax', defaults.areaMax),
    amountMin: num('amountMin', defaults.amountMin),
    amountMax: num('amountMax', defaults.amountMax),
    buildYearMin: num('buildYearMin', defaults.buildYearMin),
    buildYearMax: num('buildYearMax', defaults.buildYearMax),
    sort,
    page,
  };
}

/**
 * 필터/정렬/페이지를 URL 쿼리스트링으로 직렬화한다 — 기본값과 같은 항목은 생략해(확정 사항 #7·URL
 * 단일 소스 원칙) URL을 짧고 읽기 쉽게 유지한다. regionCode/keyword는 상호 배타적으로 하나만 싣는다
 * (확정 사항 #3).
 */
export function serializeSearchParams(filters: SearchFilters): URLSearchParams {
  const params = new URLSearchParams();
  const defaults = defaultFilters();

  if (filters.regionCode) {
    params.set('regionCode', filters.regionCode);
    if (filters.regionLabel) params.set('regionLabel', filters.regionLabel);
  } else if (filters.keyword) {
    params.set('keyword', filters.keyword);
  }

  const housingTypes = [...filters.housingTypes].sort();
  const isDefaultHousingTypes = housingTypes.length === 2 && housingTypes.join(',') === 'APT,VILLA';
  if (!isDefaultHousingTypes) {
    filters.housingTypes.forEach((type) => params.append('housingTypes', type));
  }

  if (filters.dealType !== '매매') {
    params.set('dealType', filters.dealType);
  }

  if (filters.areaMin !== defaults.areaMin) params.set('areaMin', String(filters.areaMin));
  if (filters.areaMax !== defaults.areaMax) params.set('areaMax', String(filters.areaMax));
  if (filters.amountMin !== defaults.amountMin) params.set('amountMin', String(filters.amountMin));
  if (filters.amountMax !== defaults.amountMax) params.set('amountMax', String(filters.amountMax));
  if (filters.buildYearMin !== defaults.buildYearMin) params.set('buildYearMin', String(filters.buildYearMin));
  if (filters.buildYearMax !== defaults.buildYearMax) params.set('buildYearMax', String(filters.buildYearMax));

  if (filters.sort !== 'LATEST') params.set('sort', filters.sort);
  if (filters.page !== 1) params.set('page', String(filters.page));

  return params;
}

/**
 * 실제 `GET /api/complexes/search` 호출에 쓸 파라미터. UI 1-base page를 API 0-base로 바꾸고,
 * 슬라이더가 전체범위 그대로면(확정 사항 #7 "이상" 의미) 해당 min/max를 아예 생략한다.
 */
export function buildApiQuery(filters: SearchFilters, pageSizeOverride?: number): URLSearchParams {
  const params = new URLSearchParams();
  const defaults = defaultFilters();

  if (filters.regionCode) {
    params.set('regionCode', filters.regionCode);
  } else if (filters.keyword) {
    params.set('keyword', filters.keyword);
  }

  const isDefaultHousingTypes = filters.housingTypes.length === 2;
  if (!isDefaultHousingTypes) {
    filters.housingTypes.forEach((type) => params.append('housingTypes', type));
  }

  const { dealCategory, rentType } = dealTypeToApiParams(filters.dealType);
  if (dealCategory) params.set('dealCategory', dealCategory);
  if (rentType) params.set('rentType', rentType);

  if (filters.areaMin !== defaults.areaMin) params.set('areaMin', String(filters.areaMin));
  if (filters.areaMax !== defaults.areaMax) params.set('areaMax', String(filters.areaMax));
  if (filters.amountMin !== defaults.amountMin) params.set('amountMin', String(filters.amountMin));
  if (filters.amountMax !== defaults.amountMax) params.set('amountMax', String(filters.amountMax));
  if (filters.buildYearMin !== defaults.buildYearMin) params.set('buildYearMin', String(filters.buildYearMin));
  if (filters.buildYearMax !== defaults.buildYearMax) params.set('buildYearMax', String(filters.buildYearMax));

  params.set('sort', filters.sort);
  params.set('page', String(filters.page - 1));
  params.set('size', String(pageSizeOverride ?? 20));

  return params;
}

/** 클라이언트 쪽 최소 검증 — 서버(2~50자)와 동일한 규칙을 미리 걸러 왕복 없이 즉시 피드백한다. */
export function isKeywordTooShort(keyword: string): boolean {
  const trimmed = keyword.trim();
  return trimmed.length > 0 && trimmed.length < 2;
}

export const KEYWORD_MAX_LENGTH = 50;

export function hasSearchCondition(filters: SearchFilters): boolean {
  return Boolean(filters.regionCode || filters.keyword);
}
