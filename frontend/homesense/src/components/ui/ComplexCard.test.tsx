import { cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import type { ComplexSummaryResponse } from '../../features/complex/types';
import { ComplexCard } from './ComplexCard';

// 서버는 null 필드를 키째 뺀다(non_null). 층·시군구·사용승인일·매칭 방식이 없는 대표 거래가 그대로 오면 예전엔
// `floor !== null`이 빠진 키를 통과시켜 "· undefined층"이 그려졌다.

afterEach(cleanup);

// 대표 거래의 필수 값만 있고 nullable 필드는 모두 빠진 응답.
const OMITTED: ComplexSummaryResponse = {
  complexId: 900001,
  complexName: '층없는단지',
  sido: '세종특별자치시',
  dongRi: '어진동',
  representativeHousingType: 'APT',
  representativeDealCategory: 'SALE',
  representativeDealDate: '2026-09-20',
  representativeAmount: 61000,
  representativeArea: 84.9,
};

function textOf(variant: 'grid' | 'list', complex: ComplexSummaryResponse = OMITTED): string {
  const { container } = render(
    <MemoryRouter>
      <ComplexCard complex={complex} isFavorited={false} onToggleFavorite={() => {}} variant={variant} />
    </MemoryRouter>,
  );
  return container.textContent ?? '';
}

describe('ComplexCard(UIC-05) — 서버가 뺀 필드', () => {
  for (const variant of ['grid', 'list'] as const) {
    it(`${variant}: 층·시군구가 없으면 "undefined" 없이 해당 부분을 뺀다`, () => {
      const text = textOf(variant);
      expect(text).not.toMatch(/undefined|null|NaN/);
      expect(text.replace('층없는단지', '')).not.toContain('층');
      expect(text).toContain('어진동');
    });
  }

  it('list: 사용승인일이 없으면 "건축 …년"을 뺀다', () => {
    expect(textOf('list')).not.toContain('건축');
  });

  it('값이 있으면 층과 건축년도를 보인다', () => {
    const text = textOf('list', { ...OMITTED, floor: 9, approvalDate: '2001-05-01', sigungu: '세종시' });
    expect(text).toContain('· 9층');
    expect(text).toContain('건축 2001년');
  });
});
