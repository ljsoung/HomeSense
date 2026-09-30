import { useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { CARD_CLASS } from './detailFormat';
import type { HistoryState } from './useComplexDetailData';
import { buildPriceTrend, formatEok, periodLabel, type PriceTrend } from './priceTrend';

interface PriceTrendCardProps {
  saleHistory: HistoryState;
  onRetry: () => void;
  today?: Date;
}

const DEFAULT_CHART_WIDTH = 260;
const CHART_HEIGHT = 120;
const PAD_X = 8;
const PAD_TOP = 10;
const PAD_BOTTOM = 22;

/** "2026-03" → "26.03" */
function shortMonth(month: string): string {
  return `${month.slice(2, 4)}.${month.slice(5, 7)}`;
}

function describe(trend: Extract<PriceTrend, { kind: 'ready' }>): string {
  const direction = trend.changeRate > 0 ? '상승' : trend.changeRate < 0 ? '하락' : '변동 없음';
  const rate = trend.changeRate === 0 ? '' : ` ${Math.abs(trend.changeRate).toFixed(1)}%`;
  return `최근 1년 ${trend.areaBucket}㎡ 평균 거래가 ${formatEok(trend.firstAverage)}에서 ${formatEok(trend.latestAverage)}으로${rate} ${direction}`;
}

/**
 * DTL-01 구성요소 7 — 가격 추이. 통계 API(API-STT-01)가 없어 매매 이력으로 클라이언트에서 집계한다(결정 1).
 * 차트는 의존성 없이 SVG로 그린다: 12개월 축 위에 거래가 있는 달만 점을 찍고 선으로 잇는다(빈 달은 건너뜀).
 */
export function PriceTrendCard({ saleHistory, onRetry, today }: PriceTrendCardProps) {
  const gradientId = useId();
  const trend = useMemo<PriceTrend | null>(
    () => (saleHistory.status === 'success' ? buildPriceTrend(saleHistory.data, today ?? new Date()) : null),
    [saleHistory, today],
  );

  return (
    <section aria-labelledby={`${gradientId}-title`} className={CARD_CLASS}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 id={`${gradientId}-title`} className="text-[15px] font-bold text-[#101828]">
            가격 추이
          </h2>
          <p className="mt-0.5 text-[12px] text-[#6a7282]">
            {trend?.kind === 'ready' ? `최근 1년 · ${trend.areaBucket}㎡ 평균` : '최근 1년 매매'}
          </p>
        </div>
        {trend?.kind === 'ready' && (
          <div className="shrink-0 text-right">
            <p className="text-[20px] font-extrabold leading-tight text-[#101828]">{formatEok(trend.latestAverage)}</p>
            <p
              className={`text-[12px] font-semibold ${
                trend.changeRate > 0 ? 'text-[#e7000b]' : trend.changeRate < 0 ? 'text-[#155dfc]' : 'text-[#6a7282]'
              }`}
            >
              {trend.changeRate > 0 ? '▲ +' : trend.changeRate < 0 ? '▼ −' : ''}
              {Math.abs(trend.changeRate).toFixed(1)}% ({periodLabel(trend.periodMonths)})
            </p>
          </div>
        )}
      </div>

      <div className="mt-4">
        {saleHistory.status === 'loading' ? (
          <div className="h-[120px] animate-pulse rounded-[10px] bg-[#f3f4f6]" data-testid="price-trend-skeleton" />
        ) : saleHistory.status === 'error' ? (
          <div className="flex h-[120px] flex-col items-center justify-center gap-2 text-center">
            <p className="text-[12px] text-[#6a7282]">가격 추이를 불러오지 못했습니다.</p>
            <button type="button" onClick={onRetry} className="text-[12px] font-semibold text-brand">
              다시 시도
            </button>
          </div>
        ) : trend?.kind === 'ready' ? (
          <TrendChart trend={trend} gradientId={gradientId} />
        ) : (
          <p className="flex h-[120px] items-center justify-center text-center text-[12px] leading-[1.6] text-[#6a7282]">
            최근 1년 매매 거래가 적어 추이를 표시할 수 없습니다
          </p>
        )}
      </div>

      <p className="mt-3 text-[11px] text-[#99a1af]">최근 1년 평균 거래가 기준 · 국토교통부 실거래</p>
    </section>
  );
}

/**
 * 차트 폭은 카드 폭을 따라간다. viewBox를 고정하고 늘리면(preserveAspectRatio="none") 태블릿처럼 넓은 카드에서
 * 글자·점이 가로로 늘어나 보여, 실제 폭을 재서 viewBox 폭으로 쓴다.
 */
function TrendChart({ trend, gradientId }: { trend: Extract<PriceTrend, { kind: 'ready' }>; gradientId: string }) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(DEFAULT_CHART_WIDTH);
  useLayoutEffect(() => {
    const node = wrapperRef.current;
    if (!node) return;
    const measure = () => setWidth(Math.max(Math.round(node.clientWidth), 120));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const values = trend.points.map((p) => p.average);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const plotHeight = CHART_HEIGHT - PAD_TOP - PAD_BOTTOM;
  const step = (width - PAD_X * 2) / (trend.months.length - 1);
  const x = (month: string) => PAD_X + trend.months.indexOf(month) * step;
  const y = (value: number) => (max === min ? PAD_TOP + plotHeight / 2 : PAD_TOP + (1 - (value - min) / (max - min)) * plotHeight);

  const coords = trend.points.map((p) => ({ x: x(p.month), y: y(p.average) }));
  const line = coords.map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x.toFixed(1)} ${c.y.toFixed(1)}`).join(' ');
  const baseline = PAD_TOP + plotHeight;
  const area = `${line} L${coords[coords.length - 1].x.toFixed(1)} ${baseline} L${coords[0].x.toFixed(1)} ${baseline} Z`;
  const labelIndexes = [0, 4, 8, trend.months.length - 1];

  return (
    <div ref={wrapperRef} className="w-full">
      <svg role="img" aria-label={describe(trend)} viewBox={`0 0 ${width} ${CHART_HEIGHT}`} width={width} height={CHART_HEIGHT} className="block">
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#0f5c54" stopOpacity="0.22" />
            <stop offset="100%" stopColor="#0f5c54" stopOpacity="0" />
          </linearGradient>
        </defs>
        <line x1={PAD_X} x2={width - PAD_X} y1={baseline} y2={baseline} stroke="#e5e7eb" strokeWidth="1" />
        <path d={area} fill={`url(#${gradientId})`} />
        <path d={line} fill="none" stroke="#0f5c54" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
        {coords.map((c, i) => (
          <circle key={trend.points[i].month} cx={c.x} cy={c.y} r="3" fill="#ffffff" stroke="#0f5c54" strokeWidth="2" />
        ))}
        {labelIndexes.map((index) => (
          <text
            key={index}
            x={PAD_X + index * step}
            y={CHART_HEIGHT - 6}
            fontSize="10"
            fill="#99a1af"
            textAnchor={index === 0 ? 'start' : index === trend.months.length - 1 ? 'end' : 'middle'}
          >
            {shortMonth(trend.months[index])}
          </text>
        ))}
      </svg>
    </div>
  );
}
