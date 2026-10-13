import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getComplexDetail } from '../complex/api';
import type { ComplexDetailResponse, ComplexMapPointResponse } from '../complex/types';
import { defaultFilters } from '../search/searchParams';
import { searchComplexesInBounds } from './api';
import { boundsAround, fetchSelectedComplex, hasRepresentativeTrade, pointFromDetail } from './selection';

vi.mock('../complex/api', () => ({ getComplexDetail: vi.fn() }));
vi.mock('./api', () => ({ searchComplexesInBounds: vi.fn() }));
const mockDetail = vi.mocked(getComplexDetail);
const mockSearch = vi.mocked(searchComplexesInBounds);

const detail = (overrides: Partial<ComplexDetailResponse> = {}): ComplexDetailResponse => ({
  complexId: 7,
  complexName: '선택단지',
  housingType: 'APT',
  sido: '서울특별시',
  sigungu: '중구',
  dongRi: '태평로1가',
  latitude: 37.5,
  longitude: 127.0,
  locationPrecision: 'APPROXIMATE',
  matchPending: false,
  matchMethod: 'EXACT',
  precisionBadge: 'APPROXIMATE',
  basicInfo: { householdCount: 100, constructor: null },
  extendedInfo: {},
  ...overrides,
});

describe('pointFromDetail', () => {
  it('상세의 이름·좌표·배지로 핀 항목을 만들고 대표 거래 필드는 비운다', () => {
    const item = pointFromDetail(detail());
    expect(item).toMatchObject({
      complexId: 7,
      complexName: '선택단지',
      latitude: 37.5,
      longitude: 127.0,
      precisionBadge: 'APPROXIMATE',
      locationPrecision: 'APPROXIMATE',
      representativeHousingType: 'APT',
      householdCount: 100,
    });
    expect(item && hasRepresentativeTrade(item)).toBe(false);
  });

  it('좌표가 없으면(키 생략 포함) null', () => {
    expect(pointFromDetail(detail({ latitude: null }))).toBeNull();
    const noLng = detail();
    delete noLng.longitude;
    expect(pointFromDetail(noLng)).toBeNull();
  });

  it('유형을 정할 수 없는 단지는 유형 없이 만든다', () => {
    expect(pointFromDetail(detail({ housingType: null }))?.representativeHousingType).toBeUndefined();
  });
});

describe('hasRepresentativeTrade', () => {
  it('대표 거래 필드가 모두 있어야 true', () => {
    const base = pointFromDetail(detail())!;
    const full = {
      ...base,
      representativeHousingType: 'APT' as const,
      representativeDealCategory: 'SALE' as const,
      representativeDealDate: '2026-09-01',
      representativeAmount: 50000,
      representativeArea: 84.9,
    };
    expect(hasRepresentativeTrade(full)).toBe(true);
    expect(hasRepresentativeTrade({ ...full, representativeAmount: undefined })).toBe(false);
  });
});

describe('boundsAround', () => {
  it('좌표를 가운데 둔 좁은 범위(남서 ≤ 북동)', () => {
    const b = boundsAround(37.5, 127.0);
    expect(b.swLat).toBeLessThan(37.5);
    expect(b.neLat).toBeGreaterThan(37.5);
    expect(b.swLng).toBeLessThan(127.0);
    expect(b.neLng).toBeGreaterThan(127.0);
    expect(b.neLat - b.swLat).toBeCloseTo(0.001, 6);
  });
});

describe('fetchSelectedComplex — 딥링크 선택 단지 조회 순서', () => {
  const point = (id: number, lat = 37.5, lng = 127.0): ComplexMapPointResponse => ({
    complexId: id,
    complexName: `단지${id}`,
    representativeHousingType: 'APT',
    representativeDealCategory: 'SALE',
    representativeDealDate: '2026-09-01',
    representativeAmount: 50000 + id,
    representativeArea: 84.9,
    latitude: lat,
    longitude: lng,
  });
  const nearbyResponse = (items: ComplexMapPointResponse[]) => ({ items, truncated: false, limit: 500 });
  const boundsOf = (call: number) => mockSearch.mock.calls[call][0].get('bounds')!.split(',').map(Number);

  beforeEach(() => {
    mockDetail.mockReset();
    mockSearch.mockReset();
  });

  it('좁은 범위 응답에 다른 단지가 섞여도 complexId로만 고르고, 상세 API는 부르지 않는다', async () => {
    mockSearch.mockResolvedValue(nearbyResponse([point(8), point(7), point(9)]));
    const found = await fetchSelectedComplex(7, defaultFilters(), { near: { lat: 37.5, lng: 127.0 } });
    expect(found?.complexId).toBe(7);
    expect(found?.representativeAmount).toBe(50007);
    expect(mockDetail).not.toHaveBeenCalled();
    const [swLat, swLng, neLat, neLng] = boundsOf(0);
    expect(swLat < 37.5 && neLat > 37.5 && swLng < 127.0 && neLng > 127.0).toBe(true);
  });

  it('좁은 범위에 없으면(다른 단지만) 그때 상세 API — 상세 좌표가 이미 본 범위 안이면 지도 API를 다시 부르지 않는다', async () => {
    mockSearch.mockResolvedValue(nearbyResponse([point(8)]));
    mockDetail.mockResolvedValue(detail());
    const found = await fetchSelectedComplex(7, defaultFilters(), { near: { lat: 37.5, lng: 127.0 } });
    expect(found?.complexId).toBe(7);
    expect(found && hasRepresentativeTrade(found)).toBe(false);
    expect(mockDetail).toHaveBeenCalledTimes(1);
    expect(mockSearch).toHaveBeenCalledTimes(1);
  });

  it('상세 좌표가 처음 범위 밖이면 그 좌표 주변으로 한 번 더 찾는다', async () => {
    mockSearch.mockResolvedValueOnce(nearbyResponse([])).mockResolvedValueOnce(nearbyResponse([point(8, 36.0, 128.0), point(7, 36.0, 128.0)]));
    mockDetail.mockResolvedValue(detail({ latitude: 36.0, longitude: 128.0 }));
    const found = await fetchSelectedComplex(7, defaultFilters(), { near: { lat: 37.5, lng: 127.0 } });
    expect(found?.complexId).toBe(7);
    expect(found && hasRepresentativeTrade(found)).toBe(true);
    const [swLat, , neLat] = boundsOf(1);
    expect(swLat < 36.0 && neLat > 36.0).toBe(true);
  });

  it('진입 때 받은 상세가 있으면 상세 API를 다시 부르지 않는다', async () => {
    mockSearch.mockResolvedValue(nearbyResponse([]));
    const found = await fetchSelectedComplex(7, defaultFilters(), { near: { lat: 37.5, lng: 127.0 }, knownDetail: detail() });
    expect(found?.complexName).toBe('선택단지');
    expect(mockDetail).not.toHaveBeenCalled();
  });

  it('좁은 범위 조회가 실패해도 상세로 대신한다, 상세에 좌표가 없으면 null', async () => {
    mockSearch.mockRejectedValue(new Error('network'));
    mockDetail.mockResolvedValue(detail({ latitude: null }));
    expect(await fetchSelectedComplex(7, defaultFilters(), { near: { lat: 37.5, lng: 127.0 } })).toBeNull();
    expect(mockDetail).toHaveBeenCalledTimes(1);
  });
});
