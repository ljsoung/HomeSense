import { useRef, useState, type KeyboardEvent } from 'react';
import { ClockIcon } from '../../components/icons/ClockIcon';
import { EmptyState } from '../../components/ui/EmptyState';
import type { TradeDealType, TradeResponse } from '../../features/trade/types';
import { dealingTypeLabel } from '../../features/trade/labels';
import { formatKoreanPrice } from '../../lib/format';
import { CARD_SHELL_CLASS, formatExactArea, formatFloor, formatFullDate } from './detailFormat';
import type { HistoryState } from './useComplexDetailData';

export const HISTORY_PAGE_SIZE = 20;

const TABS: { type: TradeDealType; label: string }[] = [
  { type: 'SALE', label: '매매' },
  { type: 'JEONSE', label: '전세' },
  { type: 'WOLSE', label: '월세' },
];

interface TradeHistorySectionProps {
  dealType: TradeDealType;
  onChangeDealType: (type: TradeDealType) => void;
  history: HistoryState;
  onRetry: () => void;
  onOpenTrade: (trade: TradeResponse) => void;
}

function amount(value: number | null | undefined): string {
  return value == null ? '-' : formatKoreanPrice(value);
}

const TH = 'px-4 py-3 text-[11px] font-bold leading-[16.5px] tracking-[0.275px] text-[#99a1af]';

/**
 * DTL-01 구성요소 5·6 — 거래유형 탭(Figma 4:3182, 가운데 정렬한 별도 카드)과 실거래 이력 카드(Figma 4:3192).
 * 이력 API는 목록 전체를 주므로 20행씩 화면에서 늘린다(결정 5). 전월세 원천 데이터에는 거래유형·등기일자가 없어
 * 전세·월세 탭은 그 열과 범례를 뺀다(결정 4) — 전월세 행에 "등기 전"을 띄우지 않는다.
 */
export function TradeHistorySection({ dealType, onChangeDealType, history, onRetry, onOpenTrade }: TradeHistorySectionProps) {
  const [visibleCounts, setVisibleCounts] = useState<Record<TradeDealType, number>>({
    SALE: HISTORY_PAGE_SIZE,
    JEONSE: HISTORY_PAGE_SIZE,
    WOLSE: HISTORY_PAGE_SIZE,
  });
  const tabRefs = useRef<Partial<Record<TradeDealType, HTMLButtonElement | null>>>({});
  const panelId = 'trade-history-panel';

  const onTabKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    event.preventDefault();
    const next = TABS[(index + (event.key === 'ArrowRight' ? 1 : TABS.length - 1)) % TABS.length].type;
    onChangeDealType(next);
    tabRefs.current[next]?.focus();
  };

  const visible = visibleCounts[dealType];
  const rows = history.status === 'success' ? history.data : [];
  const isSale = dealType === 'SALE';

  return (
    <div className="space-y-4">
      <div className="flex justify-center">
        <div role="tablist" aria-label="거래유형" className={`${CARD_SHELL_CLASS} flex items-center p-1.5`}>
          {TABS.map((tab, index) => {
            const selected = tab.type === dealType;
            return (
              <button
                key={tab.type}
                ref={(el) => {
                  tabRefs.current[tab.type] = el;
                }}
                type="button"
                role="tab"
                id={`trade-tab-${tab.type}`}
                aria-selected={selected}
                aria-controls={panelId}
                tabIndex={selected ? 0 : -1}
                onClick={() => onChangeDealType(tab.type)}
                onKeyDown={(event) => onTabKeyDown(event, index)}
                className={`rounded-[14px] px-6 py-2 text-[13.5px] font-semibold leading-[20.25px] transition-colors ${
                  selected ? 'bg-brand text-white' : 'text-[#6b7280] hover:text-[#101828]'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      <section
        id={panelId}
        role="tabpanel"
        aria-labelledby={`trade-tab-${dealType}`}
        className={`${CARD_SHELL_CLASS} overflow-hidden`}
      >
        <div className="px-5 pb-3 pt-5 md:px-6">
          <h2 id="trade-history-title" className="text-[15px] font-extrabold leading-[22.5px] text-[#101828]">
            실거래 이력
          </h2>
          <p className="pt-0.5 text-[11.5px] leading-[17.25px] text-[#99a1af]">계약일 기준 · 최근 거래 순</p>
        </div>

        {history.status === 'loading' ? (
          <div className="space-y-2 px-5 pb-5 md:px-6" data-testid="trade-history-skeleton">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-10 animate-pulse rounded-[8px] bg-[#f3f4f6]" />
            ))}
          </div>
        ) : history.status === 'error' ? (
          <div className="px-5 pb-5 md:px-6">
            <div className="flex flex-col items-center gap-2 rounded-[12px] border border-[#fee2e2] bg-[#fef2f2] p-5 text-center">
              <p className="text-[13px] text-[#7f1d1d]">{history.message}</p>
              <button
                type="button"
                onClick={onRetry}
                className="rounded-[10px] bg-white px-4 py-2 text-[13px] font-semibold text-[#e7000b] shadow-[0_1px_2px_rgba(0,0,0,0.1)]"
              >
                다시 시도
              </button>
            </div>
          </div>
        ) : rows.length === 0 ? (
          <div className="px-5 pb-5 md:px-6">
            <EmptyState icon={<ClockIcon className="size-6" />} description="해당 거래유형의 실거래 이력이 없습니다" />
          </div>
        ) : (
          <>
            <div className="overflow-x-auto" data-testid="trade-history-scroll">
              <table className={`w-full text-[13px] leading-[19.5px] ${isSale ? 'min-w-[600px]' : 'min-w-[360px]'}`}>
                {isSale && (
                  <colgroup>
                    <col className="w-[28%]" />
                    <col className="w-[10%]" />
                    <col className="w-[11%]" />
                    <col className="w-[21%]" />
                    <col className="w-[16%]" />
                    <col className="w-[14%]" />
                  </colgroup>
                )}
                <thead>
                  <tr className="border-y border-[#f3f4f6] bg-[#f9fafb]">
                    <th scope="col" className={`${TH} text-left`}>
                      계약일
                    </th>
                    <th scope="col" className={`${TH} text-right`}>
                      전용면적
                    </th>
                    <th scope="col" className={`${TH} text-right`}>
                      층
                    </th>
                    <th scope="col" className={`${TH} text-right`}>
                      {isSale ? '거래금액' : '보증금'}
                    </th>
                    {dealType === 'WOLSE' && (
                      <th scope="col" className={`${TH} text-right`}>
                        월세
                      </th>
                    )}
                    {isSale && (
                      <>
                        <th scope="col" className={`${TH} text-left`}>
                          거래유형
                        </th>
                        <th scope="col" className={`${TH} text-left`}>
                          등기여부
                        </th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {rows.slice(0, visible).map((trade, index) => (
                    <HistoryRow key={trade.tradeId} trade={trade} dealType={dealType} striped={index % 2 === 1} onOpen={onOpenTrade} />
                  ))}
                </tbody>
              </table>
            </div>
            {rows.length > visible && (
              <div className="border-t border-[#f3f4f6] px-5 py-3 md:px-6">
                <button
                  type="button"
                  onClick={() => setVisibleCounts((prev) => ({ ...prev, [dealType]: prev[dealType] + HISTORY_PAGE_SIZE }))}
                  className="w-full rounded-[14px] border border-[#e5e7eb] bg-white py-2.5 text-[13px] font-semibold text-[#4a5565] hover:bg-[#f7f8fa]"
                >
                  더보기 ({Math.min(visible, rows.length)}/{rows.length})
                </button>
              </div>
            )}
            {isSale && (
              <div
                className="flex flex-col gap-4 border-t border-[#f3f4f6] px-5 py-3 text-[11px] leading-[16.5px] text-[#99a1af] md:flex-row md:px-6"
                aria-label="범례"
              >
                <span className="flex items-center gap-1.5">
                  <span aria-hidden="true" className="size-2 shrink-0 rounded-full bg-[#ffb900]" />
                  등기 전 — 소유권 이전등기가 아직 확인되지 않은 거래
                </span>
                <span className="flex items-center gap-1.5">
                  <span aria-hidden="true" className="size-2 shrink-0 rounded-full bg-[#ff6467]" />
                  해제 — 계약 해제
                </span>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}

function HistoryRow({
  trade,
  dealType,
  striped,
  onOpen,
}: {
  trade: TradeResponse;
  dealType: TradeDealType;
  striped: boolean;
  onOpen: (trade: TradeResponse) => void;
}) {
  const cancelled = trade.isCancelled;
  const muted = cancelled ? 'text-[#99a1af] line-through' : '';
  const date = formatFullDate(trade.dealDate);
  const background = cancelled ? 'bg-[rgba(254,242,242,0.5)]' : striped ? 'bg-[#fafafa]' : 'bg-white';
  const cell = 'whitespace-nowrap px-4 py-[15px]';
  return (
    <tr
      tabIndex={0}
      aria-label={`${date} 거래 상세 보기`}
      onClick={() => onOpen(trade)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onOpen(trade);
        }
      }}
      className={`cursor-pointer border-b border-[#f9fafb] text-[#1e2939] last:border-b-0 hover:bg-[#f3f4f6] focus:outline-none focus-visible:bg-[#e8f2f0] ${background}`}
      data-cancelled={cancelled || undefined}
    >
      <td className={`${cell} font-medium ${muted}`}>{date}</td>
      <td className={`${cell} text-right ${muted}`}>{formatExactArea(trade.excluUseArea)}</td>
      <td className={`${cell} text-right ${muted}`}>{formatFloor(trade.floor)}</td>
      <td className={`${cell} text-right font-bold ${cancelled ? muted : 'text-[#101828]'}`}>
        {amount(dealType === 'SALE' ? trade.dealAmount : trade.depositAmount)}
      </td>
      {dealType === 'WOLSE' && (
        <td className={`${cell} text-right font-bold ${cancelled ? muted : 'text-[#101828]'}`}>
          {trade.monthlyRentAmount == null ? '-' : `${trade.monthlyRentAmount.toLocaleString('ko-KR')}만원`}
        </td>
      )}
      {dealType === 'SALE' && (
        <>
          <td className={cell}>
            <DealingTypeBadge code={trade.dealingType} dimmed={cancelled} />
          </td>
          <td className={cell}>
            {cancelled ? (
              <span className="flex flex-col items-start gap-0.5">
                <CancelledBadge />
                {trade.cancelDate && <span className="text-[11px] leading-[16.5px] text-[#e7000b]">{formatFullDate(trade.cancelDate)}</span>}
              </span>
            ) : trade.isRegistered ? (
              <span className="text-[12px] leading-[18px] text-[#99a1af]">완료</span>
            ) : (
              <RegistrationPendingBadge />
            )}
          </td>
        </>
      )}
    </tr>
  );
}

function DealingTypeBadge({ code, dimmed }: { code: string | null | undefined; dimmed: boolean }) {
  const label = dealingTypeLabel(code);
  if (label === '-') return <span className="text-[#99a1af]">-</span>;
  const tone = code === 'DIRECT' ? 'bg-[#eff6ff] text-[#1447e6]' : 'bg-[#f3f4f6] text-[#4a5565]';
  return (
    <span className={`rounded-full px-2 py-0.5 text-[10.5px] font-semibold leading-[15.75px] ${tone} ${dimmed ? 'opacity-50' : ''}`}>
      {label}
    </span>
  );
}

function RegistrationPendingBadge() {
  return (
    <span className="rounded-full bg-[#fef3c6] px-2 py-0.5 text-[10px] font-bold leading-[15px] text-[#bb4d00]">등기 전</span>
  );
}

function CancelledBadge() {
  return <span className="rounded-full bg-[#ffe2e2] px-2 py-0.5 text-[10px] font-bold leading-[15px] text-[#e7000b]">해제</span>;
}
