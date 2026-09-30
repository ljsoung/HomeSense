import { useRef, useState, type KeyboardEvent } from 'react';
import { ClockIcon } from '../../components/icons/ClockIcon';
import { EmptyState } from '../../components/ui/EmptyState';
import type { TradeDealType, TradeResponse } from '../../features/trade/types';
import { dealingTypeLabel } from '../../features/trade/labels';
import { formatKoreanPrice } from '../../lib/format';
import { CARD_CLASS, formatExactArea, formatFloor, formatFullDate } from './detailFormat';
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

/**
 * DTL-01 구성요소 5·6 — 거래유형 탭과 실거래 이력 테이블. 이력 API는 목록 전체를 주므로 20행씩 화면에서 늘린다
 * (결정 5). 전월세 원천 데이터에는 거래유형·등기일자가 없어 전세·월세 탭은 그 열과 범례를 뺀다(결정 4) — 전월세
 * 행에 "등기 전"을 띄우지 않는다.
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

  return (
    <section aria-labelledby="trade-history-title" className={CARD_CLASS}>
      <div role="tablist" aria-label="거래유형" className="flex gap-1 rounded-[12px] bg-[#f3f4f6] p-1">
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
              className={`flex-1 rounded-[10px] py-2 text-[13px] font-semibold transition-colors ${
                selected ? 'bg-white text-brand shadow-[0_1px_2px_rgba(0,0,0,0.08)]' : 'text-[#6a7282]'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      <div id={panelId} role="tabpanel" aria-labelledby={`trade-tab-${dealType}`} className="mt-4">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <div>
            <h2 id="trade-history-title" className="text-[15px] font-bold text-[#101828]">
              실거래 이력
            </h2>
            <p className="mt-0.5 text-[12px] text-[#6a7282]">계약일 기준 · 최근 거래 순</p>
          </div>
          {dealType === 'SALE' && (
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-[#6a7282]" aria-label="범례">
              <span className="flex items-center gap-1">
                <RegistrationPendingBadge /> 소유권 이전등기가 아직 확인되지 않은 거래
              </span>
              <span className="flex items-center gap-1">
                <CancelledBadge /> 계약 해제
              </span>
            </div>
          )}
        </div>

        <div className="mt-3">
          {history.status === 'loading' ? (
            <div className="space-y-2" data-testid="trade-history-skeleton">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="h-10 animate-pulse rounded-[8px] bg-[#f3f4f6]" />
              ))}
            </div>
          ) : history.status === 'error' ? (
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
          ) : rows.length === 0 ? (
            <EmptyState icon={<ClockIcon className="size-6" />} description="해당 거래유형의 실거래 이력이 없습니다" />
          ) : (
            <>
              <div className="overflow-x-auto" data-testid="trade-history-scroll">
                <table className={`w-full text-[13px] ${dealType === 'SALE' ? 'min-w-[600px]' : 'min-w-[360px]'}`}>
                  <thead>
                    <tr className="border-b border-[#f3f4f6] text-left text-[11.5px] font-medium text-[#6a7282]">
                      <th scope="col" className="py-2 pr-3 font-medium">
                        계약일
                      </th>
                      <th scope="col" className="py-2 pr-3 font-medium">
                        전용면적
                      </th>
                      <th scope="col" className="py-2 pr-3 font-medium">
                        층
                      </th>
                      <th scope="col" className="py-2 pr-3 text-right font-medium">
                        {dealType === 'SALE' ? '거래금액' : '보증금'}
                      </th>
                      {dealType === 'WOLSE' && (
                        <th scope="col" className="py-2 pr-3 text-right font-medium">
                          월세
                        </th>
                      )}
                      {dealType === 'SALE' && (
                        <>
                          <th scope="col" className="py-2 pr-3 font-medium">
                            거래유형
                          </th>
                          <th scope="col" className="py-2 font-medium">
                            등기여부
                          </th>
                        </>
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {rows.slice(0, visible).map((trade) => (
                      <HistoryRow key={trade.tradeId} trade={trade} dealType={dealType} onOpen={onOpenTrade} />
                    ))}
                  </tbody>
                </table>
              </div>
              {rows.length > visible && (
                <button
                  type="button"
                  onClick={() => setVisibleCounts((prev) => ({ ...prev, [dealType]: prev[dealType] + HISTORY_PAGE_SIZE }))}
                  className="mt-3 w-full rounded-[10px] border border-[#e5e7eb] bg-white py-2.5 text-[13px] font-semibold text-[#4a5565] hover:bg-[#f7f8fa]"
                >
                  더보기 ({Math.min(visible, rows.length)}/{rows.length})
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  );
}

function HistoryRow({
  trade,
  dealType,
  onOpen,
}: {
  trade: TradeResponse;
  dealType: TradeDealType;
  onOpen: (trade: TradeResponse) => void;
}) {
  const cancelled = trade.isCancelled;
  const strike = cancelled ? 'line-through decoration-[#e7000b]/60' : '';
  const date = formatFullDate(trade.dealDate);
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
      className={`cursor-pointer border-b border-[#f3f4f6] last:border-b-0 hover:bg-[#f7f8fa] focus:outline-none focus-visible:bg-[#e8f2f0] ${
        cancelled ? 'bg-[#fef2f2] text-[#99a1af] hover:bg-[#fee2e2]' : 'text-[#101828]'
      }`}
      data-cancelled={cancelled || undefined}
    >
      <td className={`whitespace-nowrap py-2.5 pr-3 ${strike}`}>{date}</td>
      <td className={`whitespace-nowrap py-2.5 pr-3 ${strike}`}>{formatExactArea(trade.excluUseArea)}</td>
      <td className={`whitespace-nowrap py-2.5 pr-3 ${strike}`}>{formatFloor(trade.floor)}</td>
      <td className={`whitespace-nowrap py-2.5 pr-3 text-right font-semibold ${strike}`}>
        {amount(dealType === 'SALE' ? trade.dealAmount : trade.depositAmount)}
      </td>
      {dealType === 'WOLSE' && (
        <td className={`whitespace-nowrap py-2.5 pr-3 text-right font-semibold ${strike}`}>
          {trade.monthlyRentAmount == null ? '-' : `${trade.monthlyRentAmount.toLocaleString('ko-KR')}만원`}
        </td>
      )}
      {dealType === 'SALE' && (
        <>
          <td className={`whitespace-nowrap py-2.5 pr-3 ${strike}`}>
            <DealingTypeBadge code={trade.dealingType} />
          </td>
          <td className="whitespace-nowrap py-2.5">
            {cancelled ? (
              <span className="flex flex-col items-start gap-0.5">
                <CancelledBadge />
                {trade.cancelDate && <span className="text-[11px] text-[#e7000b]">{formatFullDate(trade.cancelDate)}</span>}
              </span>
            ) : trade.isRegistered ? (
              <span className="text-[12px] text-[#4a5565]">완료</span>
            ) : (
              <RegistrationPendingBadge />
            )}
          </td>
        </>
      )}
    </tr>
  );
}

function DealingTypeBadge({ code }: { code: string | null | undefined }) {
  const label = dealingTypeLabel(code);
  if (label === '-') return <span className="text-[#99a1af]">-</span>;
  return <span className="rounded-full bg-[#f3f4f6] px-2 py-0.5 text-[11px] font-medium text-[#4a5565]">{label}</span>;
}

function RegistrationPendingBadge() {
  return (
    <span className="rounded-full bg-[#fef3c6] px-2 py-0.5 text-[11px] font-semibold text-[#973c00] no-underline">등기 전</span>
  );
}

function CancelledBadge() {
  return <span className="rounded-full bg-[#fee2e2] px-2 py-0.5 text-[11px] font-semibold text-[#e7000b]">해제</span>;
}
