import { useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { InfoCircleIcon } from '../../components/icons/InfoCircleIcon';
import { SIDE_CARD_CLASS } from './detailFormat';
import type { HistoryState } from './useComplexDetailData';
import { buildPriceTrend, formatEok, periodLabel, type PriceTrend } from './priceTrend';

interface PriceTrendCardProps {
  saleHistory: HistoryState;
  onRetry: () => void;
  today?: Date;
}

const DEFAULT_CHART_WIDTH = 258;
/** Figma 4:3430 — 차트 영역 140px, 그림 영역 위 8px~110px, 그 아래 축 글자. */
const CHART_HEIGHT = 140;
const PAD_X = 4;
const PAD_TOP = 8;
const PAD_BOTTOM = 30;

/** "2026-03" → "26.03" */
function shortMonth(month: string): string {
  return `${month.slice(2, 4)}.${month.slice(5, 7)}`;
}

function describe(trend: Extract<PriceTrend, { kind: 'ready' }>): string {
  const direction = trend.changeRate > 0 ? '상승' : trend.changeRate < 0 ? '하락' : '변동 없음';
  const rate = trend.changeRate === 0 ? '' : ` ${Math.abs(trend.changeRate).toFixed(1)}%`;
  return `최근 1년 ${trend.areaBucket}㎡ 평균 거래가 ${formatEok(trend.firstAverage)}에서 ${formatEok(trend.latestAverage)}으로${rate} ${direction}`;
}

/** 점들을 가로 중간점을 제어점으로 하는 3차 곡선으로 잇는다(Figma 차트의 부드러운 선). */
function smoothPath(points: { x: number; y: number }[]): string {
  return points
    .map((p, i) => {
      if (i === 0) return `M${p.x.toFixed(1)} ${p.y.toFixed(1)}`;
      const prev = points[i - 1];
      const mid = (prev.x + p.x) / 2;
      return `C${mid.toFixed(1)} ${prev.y.toFixed(1)} ${mid.toFixed(1)} ${p.y.toFixed(1)} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`;
    })
    .join(' ');
}

/**
 * DTL-01 구성요소 7 — 가격 추이(Figma 4:3410). 통계 API(API-STT-01)가 없어 매매 이력으로 클라이언트에서
 * 집계한다(결정 1). 차트는 의존성 없이 SVG로 그린다: 12개월 축 위에 거래가 있는 달만 점을 찍고 잇는다(빈 달은 건너뜀).
 */
export function PriceTrendCard({ saleHistory, onRetry, today }: PriceTrendCardProps) {
  const gradientId = useId();
  const trend = useMemo<PriceTrend | null>(
    () => (saleHistory.status === 'success' ? buildPriceTrend(saleHistory.data, today ?? new Date()) : null),
    [saleHistory, today],
  );

  return (
    <section aria-labelledby={`${gradientId}-title`} className={SIDE_CARD_CLASS}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 id={`${gradientId}-title`} className="text-[14px] font-extrabold leading-[21px] text-[#101828]">
            가격 추이
          </h2>
          <p className="pt-0.5 text-[11px] leading-[16.5px] text-[#99a1af]">
            {trend?.kind === 'ready' ? `최근 1년 · ${trend.areaBucket}㎡ 평균` : '최근 1년 매매'}
          </p>
        </div>
        {trend?.kind === 'ready' && (
          <div className="shrink-0 text-right">
            <p className="text-[17px] font-extrabold leading-[25.5px] tracking-[-0.3px] text-brand">{formatEok(trend.latestAverage)}</p>
            <p
              className={`text-[10.5px] font-semibold leading-[15.75px] ${
                trend.changeRate > 0 ? 'text-[#fb2c36]' : trend.changeRate < 0 ? 'text-[#155dfc]' : 'text-[#6a7282]'
              }`}
            >
              {trend.changeRate > 0 ? '▲ +' : trend.changeRate < 0 ? '▼ −' : ''}
              {Math.abs(trend.changeRate).toFixed(1)}% ({periodLabel(trend.periodMonths)})
            </p>
          </div>
        )}
      </div>

      <div className="pt-3">
        {saleHistory.status === 'loading' ? (
          <div className="h-[140px] animate-pulse rounded-[10px] bg-[#f3f4f6]" data-testid="price-trend-skeleton" />
        ) : saleHistory.status === 'error' ? (
          <div className="flex h-[140px] flex-col items-center justify-center gap-2 text-center">
            <p className="text-[12px] text-[#6a7282]">가격 추이를 불러오지 못했습니다.</p>
            <button type="button" onClick={onRetry} className="text-[12px] font-semibold text-brand">
              다시 시도
            </button>
          </div>
        ) : trend?.kind === 'ready' ? (
          <TrendChart trend={trend} gradientId={gradientId} />
        ) : (
          <p className="flex h-[140px] items-center justify-center text-center text-[12px] leading-[1.6] text-[#6a7282]">
            최근 1년 매매 거래가 적어 추이를 표시할 수 없습니다
          </p>
        )}
      </div>

      <p className="flex items-center gap-1 pt-2 text-[10.5px] leading-[15.75px] text-[#99a1af]">
        <InfoCircleIcon className="size-3 shrink-0" aria-hidden="true" />
        최근 1년 평균 거래가 기준 · 국토교통부 실거래
      </p>
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
  const baseline = CHART_HEIGHT - PAD_BOTTOM;
  const plotHeight = baseline - PAD_TOP;
  // 선이 위아래 끝에 붙지 않게 값 범위의 위쪽 20%·아래쪽 30%를 비워 둔다(Figma: 최저점이 그림 영역 아래쪽 1/4).
  const top = PAD_TOP + plotHeight * 0.2;
  const bottom = PAD_TOP + plotHeight * 0.7;
  const step = (width - PAD_X * 2) / (trend.months.length - 1);
  const x = (month: string) => PAD_X + trend.months.indexOf(month) * step;
  const y = (value: number) => (max === min ? (top + bottom) / 2 : bottom - ((value - min) / (max - min)) * (bottom - top));

  const coords = trend.points.map((p) => ({ x: x(p.month), y: y(p.average) }));
  const line = smoothPath(coords);
  const area = `${line} L${coords[coords.length - 1].x.toFixed(1)} ${baseline} L${coords[0].x.toFixed(1)} ${baseline} Z`;
  const labelIndexes = [0, 4, 8, trend.months.length - 1];
  const gridLines = [PAD_TOP, PAD_TOP + plotHeight / 2, baseline];

  return (
    <div ref={wrapperRef} className="w-full">
      <svg role="img" aria-label={describe(trend)} viewBox={`0 0 ${width} ${CHART_HEIGHT}`} width={width} height={CHART_HEIGHT} className="block">
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#0f5c54" stopOpacity="0.18" />
            <stop offset="100%" stopColor="#0f5c54" stopOpacity="0" />
          </linearGradient>
        </defs>
        {gridLines.map((gy) => (
          <line key={gy} x1={0} x2={width} y1={gy + 0.5} y2={gy + 0.5} stroke="#f2f3f5" strokeDasharray="2 4" />
        ))}
        <path d={area} fill={`url(#${gradientId})`} fillOpacity="0.6" />
        <path d={line} fill="none" stroke="#0f5c54" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
        {coords.map((c, i) => (
          <circle key={trend.points[i].month} cx={c.x} cy={c.y} r="2.5" fill="#0f5c54" fillOpacity="0.6" />
        ))}
        {labelIndexes.map((index) => (
          <text
            key={index}
            x={PAD_X + index * step}
            y={baseline + 16}
            fontSize="9"
            fill="#9ca3af"
            textAnchor={index === 0 ? 'start' : index === trend.months.length - 1 ? 'end' : 'middle'}
          >
            {shortMonth(trend.months[index])}
          </text>
        ))}
      </svg>
    </div>
  );
}
