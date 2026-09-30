import type { TradeResponse } from '../../features/trade/types';

/** 가격 추이 카드가 보는 기간 — 이번 달 포함 최근 12개월. */
export const TREND_WINDOW_MONTHS = 12;

export interface TrendPoint {
  /** 'YYYY-MM' */
  month: string;
  /** 그 달 평균 거래가(만원). */
  average: number;
}

export type PriceTrend =
  | { kind: 'insufficient'; months: string[] }
  | {
      kind: 'ready';
      /** 창 안의 12개월('YYYY-MM', 오래된 달부터) — 차트 x축. */
      months: string[];
      /** 대표 면적 구간(전용면적 소수점 버림, ㎡). */
      areaBucket: number;
      /** 거래가 있는 달만, 오래된 달부터. */
      points: TrendPoint[];
      firstAverage: number;
      latestAverage: number;
      /** (마지막 − 첫) ÷ 첫 × 100, 소수 첫째 자리 반올림. */
      changeRate: number;
      /** 첫 데이터 월~마지막 데이터 월을 포함한 개월 수. */
      periodMonths: number;
    };

function monthKey(year: number, monthIndex: number): string {
  return `${year}-${String(monthIndex + 1).padStart(2, '0')}`;
}

/** today가 속한 달을 마지막으로 하는 최근 12개월('YYYY-MM'), 오래된 달부터. */
export function trendWindow(today: Date): string[] {
  const months: string[] = [];
  for (let offset = TREND_WINDOW_MONTHS - 1; offset >= 0; offset -= 1) {
    const d = new Date(today.getFullYear(), today.getMonth() - offset, 1);
    months.push(monthKey(d.getFullYear(), d.getMonth()));
  }
  return months;
}

function monthOrdinal(month: string): number {
  const [year, m] = month.split('-').map(Number);
  return year * 12 + (m - 1);
}

interface BucketStats {
  count: number;
  latestDate: string;
  latestTradeId: number;
}

function isBetterBucket(candidate: BucketStats, best: BucketStats): boolean {
  if (candidate.count !== best.count) return candidate.count > best.count;
  if (candidate.latestDate !== best.latestDate) return candidate.latestDate > best.latestDate;
  return candidate.latestTradeId > best.latestTradeId;
}

/**
 * DTL-01 가격 추이(구성요소 7) 집계. 통계 API(API-STT-01)가 없어 이 화면이 받는 매매 이력으로 클라이언트에서 계산한다.
 * - 대상: 매매, 해제되지 않았고 거래금액이 있는 거래 중 최근 12개월(이번 달 포함).
 * - 대표 면적: 대상 거래가 가장 많은 전용면적 구간(Math.floor). 동률이면 가장 최근 거래가 있는 구간, 그것도
 *   같으면 거래 id가 큰 쪽(같은 날 나중에 적재된 거래)이 있는 구간.
 * - 월별 평균 거래가. 거래가 없는 달은 점을 두지 않는다. 데이터 월이 2개 미만이면 추이를 만들지 않는다.
 */
export function buildPriceTrend(trades: TradeResponse[], today: Date): PriceTrend {
  const months = trendWindow(today);
  const inWindow = new Set(months);
  const eligible = trades.filter(
    (t) => t.dealCategory === 'SALE' && !t.isCancelled && t.dealAmount != null && inWindow.has(t.dealDate.slice(0, 7)),
  );

  const buckets = new Map<number, BucketStats>();
  for (const t of eligible) {
    const bucket = Math.floor(t.excluUseArea);
    const current = buckets.get(bucket);
    if (!current) {
      buckets.set(bucket, { count: 1, latestDate: t.dealDate, latestTradeId: t.tradeId });
      continue;
    }
    current.count += 1;
    if (t.dealDate > current.latestDate || (t.dealDate === current.latestDate && t.tradeId > current.latestTradeId)) {
      current.latestDate = t.dealDate;
      current.latestTradeId = t.tradeId;
    }
  }

  let areaBucket: number | null = null;
  let bestStats: BucketStats | null = null;
  for (const [bucket, stats] of buckets) {
    if (bestStats === null || isBetterBucket(stats, bestStats)) {
      areaBucket = bucket;
      bestStats = stats;
    }
  }
  if (areaBucket === null) return { kind: 'insufficient', months };

  const sums = new Map<string, { total: number; count: number }>();
  for (const t of eligible) {
    if (Math.floor(t.excluUseArea) !== areaBucket) continue;
    const month = t.dealDate.slice(0, 7);
    const entry = sums.get(month) ?? { total: 0, count: 0 };
    entry.total += t.dealAmount as number;
    entry.count += 1;
    sums.set(month, entry);
  }
  const points: TrendPoint[] = [];
  for (const month of months) {
    const entry = sums.get(month);
    if (entry) points.push({ month, average: entry.total / entry.count });
  }
  if (points.length < 2) return { kind: 'insufficient', months };

  const first = points[0];
  const latest = points[points.length - 1];
  return {
    kind: 'ready',
    months,
    areaBucket,
    points,
    firstAverage: first.average,
    latestAverage: latest.average,
    changeRate: Math.round(((latest.average - first.average) / first.average) * 1000) / 10,
    periodMonths: monthOrdinal(latest.month) - monthOrdinal(first.month) + 1,
  };
}

/** 변동률 옆 기간 문구 — 12개월 이상이면 "1년", 아니면 실제 개월 수(첫 데이터 월~마지막 데이터 월). */
export function periodLabel(periodMonths: number): string {
  return periodMonths >= TREND_WINDOW_MONTHS ? '1년' : `${periodMonths}개월`;
}

/** 만원 → "5.10억". */
export function formatEok(amountInManwon: number): string {
  return `${(amountInManwon / 10000).toFixed(2)}억`;
}
