import { describe, expect, it } from 'vitest';
import { defaultFilters, parseSearchParams, serializeSearchParams } from '../search/searchParams';
import {
  approximateBounds,
  buildComplexMapHref,
  buildMapApiQuery,
  buildMapHref,
  buildSearchHref,
  DEFAULT_VIEWPORT,
  filterKeyOfMapQuery,
  formatBounds,
  formatClusterCount,
  formatPinPrice,
  mapFilterKey,
  levelForRegionCode,
  parseMapUrl,
  resolveInitialView,
  serializeMapUrl,
} from './mapParams';

const params = (query: string) => new URLSearchParams(query);

describe('지도 URL 파싱·직렬화', () => {
  it('뷰포트와 선택한 단지를 읽고 그대로 다시 쓴다', () => {
    const state = parseMapUrl(params('dealType=전세&lat=37.5&lng=127.03&level=4&complexId=12'));
    expect(state.viewport).toEqual({ lat: 37.5, lng: 127.03, level: 4 });
    expect(state.complexId).toBe(12);
    expect(state.filters.dealType).toBe('전세');
    expect(serializeMapUrl(state).toString()).toBe(params('dealType=전세&lat=37.5&lng=127.03&level=4&complexId=12').toString());
  });

  it('좌표는 소수 6자리로 줄이고 끝의 0은 뗀다', () => {
    const query = serializeMapUrl({ filters: parseMapUrl(params('')).filters, viewport: { lat: 37.123456789, lng: 127.5, level: 5 } });
    expect(query.get('lat')).toBe('37.123457');
    expect(query.get('lng')).toBe('127.5');
  });

  it('범위를 벗어나거나 숫자가 아닌 뷰포트는 버린다', () => {
    expect(parseMapUrl(params('lat=91&lng=127&level=5')).viewport).toBeUndefined();
    expect(parseMapUrl(params('lat=37&lng=181&level=5')).viewport).toBeUndefined();
    expect(parseMapUrl(params('lat=37&lng=127&level=15')).viewport).toBeUndefined();
    expect(parseMapUrl(params('lat=37&lng=127&level=4.5')).viewport).toBeUndefined();
    expect(parseMapUrl(params('lat=abc&lng=127&level=5')).viewport).toBeUndefined();
  });

  it('level 없이 좌표만 있으면 뷰포트가 아니다 — 단지 링크(complexId + 좌표)만 단지 레벨로 받는다', () => {
    expect(parseMapUrl(params('lat=37.5&lng=127')).viewport).toBeUndefined();
    expect(parseMapUrl(params('lat=37.5&lng=127&complexId=3')).viewport).toEqual({ lat: 37.5, lng: 127, level: 3 });
  });

  it('잘못된 complexId는 버린다', () => {
    expect(parseMapUrl(params('complexId=0')).complexId).toBeUndefined();
    expect(parseMapUrl(params('complexId=-2')).complexId).toBeUndefined();
    expect(parseMapUrl(params('complexId=1.5')).complexId).toBeUndefined();
  });

  it('page는 지도 URL에서 버린다', () => {
    const state = parseMapUrl(params('regionCode=4111100000&page=3'));
    expect(state.filters.page).toBe(1);
    expect(serializeMapUrl(state).has('page')).toBe(false);
  });
});

describe('SRCH-01 ↔ MAP-01 필터 보존', () => {
  const searchQuery =
    'regionCode=4111100000&regionLabel=경기도 수원시 장안구&housingTypes=APT&dealType=월세&areaMin=60&amountMax=50000&buildYearMin=2000&sort=AMOUNT&page=4';

  it('검색 → 지도 → 검색으로 오가도 필터가 그대로이고 검색은 첫 페이지로 간다', () => {
    const fromSearch = parseSearchParams(params(searchQuery));
    const mapHref = buildMapHref(fromSearch);
    expect(mapHref).not.toContain('page=');

    const onMap = parseMapUrl(params(mapHref.split('?')[1]));
    // 지도에서 움직여 뷰포트·선택이 붙어도
    const mapUrl = serializeMapUrl({ ...onMap, viewport: { lat: 37.3, lng: 127, level: 6 }, complexId: 9 });
    const back = buildSearchHref(parseMapUrl(mapUrl).filters);
    const restored = parseSearchParams(params(back.split('?')[1]));

    expect(restored).toEqual({ ...fromSearch, page: 1 });
    expect(back).not.toMatch(/lat=|lng=|level=|complexId=|page=/);
  });

  it('필터가 기본값이면 쿼리 없이 간다', () => {
    expect(buildMapHref(parseSearchParams(params('')))).toBe('/map');
    expect(buildSearchHref(parseSearchParams(params('')))).toBe('/search');
  });

  it('DTL-01 링크는 단지 좌표·단지 레벨·complexId를 싣는다', () => {
    const href = buildComplexMapHref(42, 37.5012345, 127.0398765);
    const state = parseMapUrl(params(href.split('?')[1]));
    expect(state.complexId).toBe(42);
    expect(state.viewport).toEqual({ lat: 37.501235, lng: 127.039877, level: 3 });
  });
});

describe('bounds 직렬화', () => {
  it('백엔드 형식 "swLat,swLng,neLat,neLng"(소수 7자리)', () => {
    expect(formatBounds({ swLat: 37.4, swLng: 126.9, neLat: 37.6, neLng: 127.1 })).toBe('37.4000000,126.9000000,37.6000000,127.1000000');
  });

  it('지도 API 쿼리는 bounds와 /search와 같은 이름의 필터만 싣는다(지역·키워드·정렬·페이지 없음)', () => {
    const filters = parseSearchParams(params('regionCode=4111100000&keyword=래미안&dealType=전세&amountMin=10000&sort=AREA&page=2'));
    const query = buildMapApiQuery(filters, { swLat: 1, swLng: 2, neLat: 3, neLng: 4 });
    expect(query.get('bounds')).toBe('1.0000000,2.0000000,3.0000000,4.0000000');
    expect(query.get('rentType')).toBe('JEONSE');
    expect(query.get('amountMin')).toBe('10000');
    for (const key of ['regionCode', 'keyword', 'sort', 'page', 'size']) expect(query.has(key)).toBe(false);
  });

  it('SDK 없이 근사한 bounds는 중심을 둘러싸고 레벨이 1 오르면 두 배로 넓어진다', () => {
    const center = { lat: 37.5, lng: 127 };
    const b5 = approximateBounds(center, 5, 800, 600);
    const b6 = approximateBounds(center, 6, 800, 600);
    expect(b5.swLat).toBeLessThan(37.5);
    expect(b5.neLng).toBeGreaterThan(127);
    expect((b6.neLat - b6.swLat) / (b5.neLat - b5.swLat)).toBeCloseTo(2, 5);
  });
});

describe('초기 중심 결정 순서', () => {
  const stored = { lat: 35.1, lng: 129.0, level: 7 };

  it('1) complexId + 좌표가 있으면 그 단지 위치', () => {
    const view = resolveInitialView(parseMapUrl(params('complexId=5&lat=37.5&lng=127&regionLabel=부산')), stored);
    expect(view).toEqual({ kind: 'point', viewport: { lat: 37.5, lng: 127, level: 3 }, source: 'complex' });
  });

  it('1) complexId만 있으면 단지 좌표를 조회하고, 실패하면 다음 순위로', () => {
    const view = resolveInitialView(parseMapUrl(params('complexId=5&regionLabel=부산광역시')), stored);
    expect(view.kind).toBe('complex');
    if (view.kind === 'complex') expect(view.fallback.kind).toBe('region');
  });

  it('2) lat·lng·level이 지역 라벨보다 먼저다', () => {
    const view = resolveInitialView(parseMapUrl(params('lat=37&lng=127&level=8&regionLabel=부산')), stored);
    expect(view).toEqual({ kind: 'point', viewport: { lat: 37, lng: 127, level: 8 }, source: 'viewport' });
  });

  it('3) 지역 라벨은 법정동코드 단위로 레벨을 정하고, 지오코딩 실패 시 마지막 위치로', () => {
    const view = resolveInitialView(parseMapUrl(params('regionCode=4111100000&regionLabel=경기도 수원시 장안구')), stored);
    expect(view).toEqual({
      kind: 'region',
      query: '경기도 수원시 장안구',
      level: 7,
      fallback: { kind: 'point', viewport: stored, source: 'stored' },
    });
  });

  it('4) 마지막으로 본 위치, 5) 기본 위치', () => {
    expect(resolveInitialView(parseMapUrl(params('')), stored)).toEqual({ kind: 'point', viewport: stored, source: 'stored' });
    expect(resolveInitialView(parseMapUrl(params('')), null)).toEqual({ kind: 'point', viewport: DEFAULT_VIEWPORT, source: 'default' });
  });

  it('지역 레벨: 시도 10, 시군구 7, 읍면동 5, 형식 오류 6', () => {
    expect(levelForRegionCode('1100000000')).toBe(10);
    expect(levelForRegionCode('1168000000')).toBe(7);
    expect(levelForRegionCode('1168010100')).toBe(5);
    expect(levelForRegionCode('abc')).toBe(6);
    expect(levelForRegionCode(undefined)).toBe(6);
  });
});

describe('표기', () => {
  it('클러스터는 100건 이상이면 99+', () => {
    expect(formatClusterCount(1)).toBe('1');
    expect(formatClusterCount(99)).toBe('99');
    expect(formatClusterCount(100)).toBe('99+');
    expect(formatClusterCount(1234)).toBe('99+');
  });

  it('핀 가격 — 억은 소수 첫째 자리(.0은 뗌), 1억 미만은 만, 월세는 보증금/월세', () => {
    expect(formatPinPrice('SALE', null, 30_000, null)).toBe('3억');
    expect(formatPinPrice('SALE', null, 41_200, null)).toBe('4.1억');
    expect(formatPinPrice('SALE', null, 39_960, null)).toBe('4억');
    expect(formatPinPrice('RENT', 'JEONSE', 9_500, 0)).toBe('9,500만');
    expect(formatPinPrice('RENT', 'WOLSE', 5_000, 50)).toBe('5,000만/50');
  });

  it('직렬화 결과가 SRCH-01 serializeSearchParams와 같은 필터 키를 쓴다', () => {
    const filters = parseSearchParams(params('housingTypes=VILLA&dealType=매매&areaMax=120'));
    const mapQuery = serializeMapUrl({ filters });
    expect(mapQuery.toString()).toBe(serializeSearchParams(filters).toString());
  });
});

describe('필터 키 — 선택 단지 값이 어느 필터 기준인지 비교', () => {
  const bounds = { swLat: 37.5, swLng: 126.9, neLat: 37.6, neLng: 127.0 };

  it('조회 문자열의 필터 부분은 bounds와 무관하게 mapFilterKey와 같다', () => {
    const filters = { ...defaultFilters(), dealType: '전세' as const, areaMin: 60 };
    const other = { swLat: 35, swLng: 129, neLat: 35.1, neLng: 129.1 };
    expect(filterKeyOfMapQuery(buildMapApiQuery(filters, bounds).toString())).toBe(mapFilterKey(filters));
    expect(filterKeyOfMapQuery(buildMapApiQuery(filters, other).toString())).toBe(mapFilterKey(filters));
  });

  it('거래유형이 바뀌면 키가 달라지고, 정렬·페이지·검색어는 키에 들어가지 않는다', () => {
    const base = defaultFilters();
    expect(mapFilterKey({ ...base, dealType: '전세' })).not.toBe(mapFilterKey(base));
    expect(mapFilterKey({ ...base, sort: 'AMOUNT', page: 3, keyword: '수원' })).toBe(mapFilterKey(base));
  });
});
