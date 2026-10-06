import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { InterestRegionSummaryResponse } from '../../features/region/types';
import { RegionCard } from './InterestRegionSummary';

// 서버는 null 필드를 키째 뺀다(non_null). 거래가 없으면 avgPrice·changeRate 키가 아예 없다 — 예전엔 `!== null`이라
// 빠진 키가 통과해 "−NaN% / NaN만원"이 그려졌다.

afterEach(cleanup);

const base = { favoriteRegionId: 1, legalDongCd: '1168010100', fullPath: '서울특별시 강남구 역삼동' };

function textOf(region: InterestRegionSummaryResponse): string {
  return render(<RegionCard region={region} />).container.textContent ?? '';
}

describe('RegionCard(HOME-01 관심 지역 요약) — 서버가 뺀 필드', () => {
  it('평균가·변동률 키가 없으면 "거래 없음"과 "—"를 보인다', () => {
    const text = textOf({ ...base, tradeCount: 0 });
    expect(text).not.toMatch(/NaN|undefined/);
    expect(text).toContain('거래 없음');
    expect(text).toContain('—');
    expect(text).toContain('변동 정보 없음');
  });

  it('변동률 키만 없으면 평균가는 보이고 변동률은 "—"다', () => {
    const text = textOf({ ...base, avgPrice: 42000, tradeCount: 2 });
    expect(text).not.toMatch(/NaN|undefined/);
    expect(text).toContain('4억 2,000만원');
    expect(text).toContain('—');
  });

  it('값이 다 있으면 평균가와 변동률을 보인다', () => {
    const text = textOf({ ...base, avgPrice: 50786, changeRate: -5.59, tradeCount: 7 });
    expect(text).toContain('5억 786만원');
    expect(text).toContain('−5.6%');
    expect(text).not.toContain('거래 없음');
  });
});
