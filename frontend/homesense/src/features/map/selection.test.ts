import { describe, expect, it } from 'vitest';
import type { ComplexDetailResponse } from '../complex/types';
import { boundsAround, hasRepresentativeTrade, pointFromDetail } from './selection';

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
