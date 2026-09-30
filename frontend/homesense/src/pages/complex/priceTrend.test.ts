import { describe, expect, it } from 'vitest';
import type { TradeResponse } from '../../features/trade/types';
import { buildPriceTrend, formatEok, periodLabel, trendWindow } from './priceTrend';

const TODAY = new Date(2026, 8, 30); // 2026-09-30

let nextId = 1;
function sale(dealDate: string, area: number, amount: number | null, extra: Partial<TradeResponse> = {}): TradeResponse {
  return {
    tradeId: nextId++,
    dealDate,
    dealCategory: 'SALE',
    excluUseArea: area,
    dealAmount: amount,
    isCancelled: false,
    isRegistered: true,
    ...extra,
  };
}

describe('buildPriceTrend', () => {
  it('최근 12개월 창은 이번 달을 포함한다', () => {
    const months = trendWindow(TODAY);
    expect(months).toHaveLength(12);
    expect(months[0]).toBe('2025-10');
    expect(months[11]).toBe('2026-09');
  });

  it('거래가 가장 많은 면적 구간(소수점 버림)을 대표 면적으로 고르고 그 구간의 월 평균을 낸다', () => {
    const result = buildPriceTrend(
      [
        sale('2026-03-10', 84.98, 50000),
        sale('2026-03-20', 84.12, 52000),
        sale('2026-08-05', 84.5, 55000),
        sale('2026-08-06', 59.9, 40000),
      ],
      TODAY,
    );
    expect(result.kind).toBe('ready');
    if (result.kind !== 'ready') return;
    expect(result.areaBucket).toBe(84);
    expect(result.points).toEqual([
      { month: '2026-03', average: 51000 },
      { month: '2026-08', average: 55000 },
    ]);
    expect(result.firstAverage).toBe(51000);
    expect(result.latestAverage).toBe(55000);
    expect(result.changeRate).toBe(7.8); // (55000 − 51000) ÷ 51000 = 7.84%
    expect(result.periodMonths).toBe(6); // 3월~8월
  });

  it('해제 건·전월세·금액 없음·창 밖 거래는 뺀다', () => {
    const result = buildPriceTrend(
      [
        sale('2026-04-01', 84, 50000),
        sale('2026-05-01', 84, 60000),
        sale('2026-05-02', 84, 99999, { isCancelled: true }),
        sale('2026-05-03', 84, 99999, { dealCategory: 'RENT' }),
        sale('2026-05-04', 84, null),
        sale('2025-09-30', 84, 1),
      ],
      TODAY,
    );
    expect(result.kind).toBe('ready');
    if (result.kind !== 'ready') return;
    expect(result.points).toEqual([
      { month: '2026-04', average: 50000 },
      { month: '2026-05', average: 60000 },
    ]);
  });

  it('해제 건만 많은 구간은 대표 면적이 되지 않는다', () => {
    const result = buildPriceTrend(
      [
        sale('2026-04-01', 59, 40000, { isCancelled: true }),
        sale('2026-05-01', 59, 40000, { isCancelled: true }),
        sale('2026-06-01', 59, 40000, { isCancelled: true }),
        sale('2026-04-10', 84, 50000),
        sale('2026-06-10', 84, 51000),
      ],
      TODAY,
    );
    expect(result.kind === 'ready' && result.areaBucket).toBe(84);
  });

  it('건수가 같으면 가장 최근 거래가 있는 구간을 고른다', () => {
    const result = buildPriceTrend(
      [
        sale('2026-02-01', 59.1, 40000),
        sale('2026-06-01', 59.2, 42000),
        sale('2026-03-01', 84.1, 50000),
        sale('2026-07-01', 84.2, 52000),
      ],
      TODAY,
    );
    expect(result.kind === 'ready' && result.areaBucket).toBe(84);
  });

  it('건수와 최근 거래일이 같으면 거래 id가 큰 구간을 고른다', () => {
    const result = buildPriceTrend(
      [
        sale('2026-02-01', 84, 50000, { tradeId: 10 }),
        sale('2026-06-01', 84, 52000, { tradeId: 11 }),
        sale('2026-03-01', 59, 40000, { tradeId: 20 }),
        sale('2026-06-01', 59, 42000, { tradeId: 21 }),
      ],
      TODAY,
    );
    expect(result.kind === 'ready' && result.areaBucket).toBe(59);
  });

  it('데이터 월이 2개 미만이면 insufficient', () => {
    expect(buildPriceTrend([sale('2026-05-01', 84, 50000), sale('2026-05-20', 84, 51000)], TODAY).kind).toBe('insufficient');
    expect(buildPriceTrend([], TODAY).kind).toBe('insufficient');
  });

  it('하락은 음수 변동률', () => {
    const result = buildPriceTrend([sale('2026-01-10', 84, 50000), sale('2026-02-10', 84, 48400)], TODAY);
    expect(result.kind === 'ready' && result.changeRate).toBe(-3.2);
  });

  it('기간 문구는 12개월 이상이면 1년, 아니면 실제 개월 수', () => {
    expect(periodLabel(7)).toBe('7개월');
    expect(periodLabel(12)).toBe('1년');
    const full = buildPriceTrend([sale('2025-10-01', 84, 50000), sale('2026-09-01', 84, 51000)], TODAY);
    expect(full.kind === 'ready' && full.periodMonths).toBe(12);
  });

  it('억 단위 소수 둘째 자리', () => {
    expect(formatEok(51000)).toBe('5.10억');
    expect(formatEok(45980)).toBe('4.60억');
  });
});
