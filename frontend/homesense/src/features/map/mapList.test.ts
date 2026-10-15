import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ComplexMapPointResponse } from '../complex/types';
import { readLastViewport, writeLastViewport } from './lastViewport';
import { formatMapCount, sortMapItems } from './mapList';

function item(complexId: number, amount: number, area: number, dealDate: string): ComplexMapPointResponse {
  return {
    complexId,
    complexName: `단지${complexId}`,
    representativeHousingType: 'APT',
    representativeDealCategory: 'SALE',
    representativeDealDate: dealDate,
    representativeAmount: amount,
    representativeArea: area,
    latitude: 37.5,
    longitude: 127,
  };
}

describe('목록 정렬(SRCH-01과 같은 sort 값)', () => {
  const items = [item(1, 50_000, 84, '2026-08-01'), item(2, 30_000, 59, '2026-09-10'), item(3, 30_000, 120, '2026-09-10')];

  it('최신순은 계약일 내림차순, 같으면 complexId', () => {
    expect(sortMapItems(items, 'LATEST').map((i) => i.complexId)).toEqual([2, 3, 1]);
  });
  it('금액순은 오름차순, 면적순은 내림차순', () => {
    expect(sortMapItems(items, 'AMOUNT').map((i) => i.complexId)).toEqual([2, 3, 1]);
    expect(sortMapItems(items, 'AREA').map((i) => i.complexId)).toEqual([3, 1, 2]);
  });
  it('원본 배열을 바꾸지 않는다', () => {
    const copy = [...items];
    sortMapItems(items, 'AREA');
    expect(items).toEqual(copy);
  });
});

describe('건수 표기', () => {
  it('잘렸으면 +를 붙인다', () => {
    expect(formatMapCount(6, false)).toBe('6건');
    expect(formatMapCount(500, true)).toBe('500+건');
    expect(formatMapCount(1200, false)).toBe('1,200건');
  });
});

describe('마지막으로 본 위치(localStorage)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it('쓰고 읽는다', () => {
    writeLastViewport({ lat: 37.1, lng: 127.2, level: 5 });
    expect(readLastViewport()).toEqual({ lat: 37.1, lng: 127.2, level: 5 });
  });

  it('깨진 값·범위 밖 값은 없음으로 본다', () => {
    window.localStorage.setItem('homesense.map.lastViewport', '{oops');
    expect(readLastViewport()).toBeNull();
    window.localStorage.setItem('homesense.map.lastViewport', JSON.stringify({ lat: 99, lng: 127, level: 5 }));
    expect(readLastViewport()).toBeNull();
  });

  it('저장소 접근이 예외를 던져도 지도 동작을 막지 않는다', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(readLastViewport()).toBeNull();
    expect(() => writeLastViewport({ lat: 37, lng: 127, level: 5 })).not.toThrow();
  });
});
