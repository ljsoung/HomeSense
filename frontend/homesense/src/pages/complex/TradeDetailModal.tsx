import { useEffect, useState } from 'react';
import { Modal } from '../../components/ui/Modal';
import { getTradeDetail } from '../../features/trade/api';
import { dealingTypeLabel } from '../../features/trade/labels';
import type { TradeDetailResponse } from '../../features/trade/types';
import { getErrorMessage } from '../../lib/apiError';
import { formatKoreanPrice } from '../../lib/format';
import { formatExactArea, formatFloor, formatFullDate } from './detailFormat';

type DetailLoad =
  | { status: 'loading' }
  | { status: 'success'; data: TradeDetailResponse }
  | { status: 'error'; message: string };

interface TradeDetailModalProps {
  /** 열 거래 id. null이면 닫힌 상태. */
  tradeId: number | null;
  onClose: () => void;
}

/**
 * DTL-01 거래상세 모달(UI정의서 5.3절 구성요소 8). 열 때마다 GET /api/trades/{tradeId}를 부르고 자체 로딩·오류·
 * 재시도를 갖는다. 공용 Modal이 포커스 트랩·Esc·배경 클릭·포커스 복귀(행으로)·스크롤 잠금을 맡는다.
 * 호출부는 거래가 바뀔 때 `key`로 새로 마운트한다 — 이 컴포넌트는 tradeId가 고정이라고 본다.
 */
export function TradeDetailModal({ tradeId, onClose }: TradeDetailModalProps) {
  const [load, setLoad] = useState<DetailLoad>({ status: 'loading' });
  const [round, setRound] = useState(0);

  useEffect(() => {
    if (tradeId === null) return;
    const controller = new AbortController();
    const run = async () => {
      setLoad({ status: 'loading' });
      try {
        const data = await getTradeDetail(tradeId, controller.signal);
        setLoad({ status: 'success', data });
      } catch (error) {
        if (!controller.signal.aborted) setLoad({ status: 'error', message: getErrorMessage(error) });
      }
    };
    void run();
    return () => controller.abort();
  }, [tradeId, round]);

  return (
    <Modal open={tradeId !== null} onClose={onClose} title="거래 상세">
      {load.status === 'loading' ? (
        <div className="space-y-3" data-testid="trade-detail-skeleton">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-5 animate-pulse rounded bg-[#f3f4f6]" />
          ))}
        </div>
      ) : load.status === 'error' ? (
        <div className="flex flex-col items-center gap-3 py-4 text-center">
          <p className="text-[13px] text-[#7f1d1d]">{load.message}</p>
          <button
            type="button"
            onClick={() => setRound((value) => value + 1)}
            className="rounded-[10px] border border-[#e5e7eb] px-4 py-2 text-[13px] font-semibold text-[#4a5565]"
          >
            다시 시도
          </button>
        </div>
      ) : (
        <TradeDetailBody trade={load.data} />
      )}
    </Modal>
  );
}

function TradeDetailBody({ trade }: { trade: TradeDetailResponse }) {
  const isSale = trade.dealCategory === 'SALE';
  const rows: { label: string; value: string; tone?: 'muted' | 'pending' | 'cancel' }[] = [];
  const push = (label: string, value: string | null | undefined, tone?: 'muted' | 'pending' | 'cancel') => {
    if (value != null && value !== '') rows.push({ label, value, tone });
  };

  push('계약일', formatFullDate(trade.dealDate));
  push('전용면적', formatExactArea(trade.excluUseArea));
  push('층', trade.floor == null ? null : formatFloor(trade.floor));

  if (isSale) {
    // 매매: 동은 등기 후에야 공개된다(원천 aptDong 공란) — 빈칸 대신 안내한다.
    push('동', trade.aptDong ?? (trade.aptDongPending ? '등기 완료 후 제공' : null), trade.aptDong ? undefined : 'muted');
    push('거래유형', dealingTypeLabel(trade.dealingType));
    // 중개사소재지는 원천에서 수집하지 않아(BAT-PRS-01 매핑 확인 필요) 행을 두지 않는다(결정 10).
    push('매도자', trade.sellerType);
    push('매수자', trade.buyerType);
    push(
      '등기일자',
      trade.registrationDate ? formatFullDate(trade.registrationDate) : '등기 전',
      trade.registrationDate ? undefined : 'pending',
    );
    if (trade.isCancelled) {
      push('해제', trade.cancelDate ? `${formatFullDate(trade.cancelDate)} 해제` : '해제된 거래', 'cancel');
    }
  } else {
    // 전월세 원천에는 거래유형·등기·해제·동 정보가 없다 — 값이 있는 항목만 보인다.
    push('동', trade.aptDong);
    push('거래유형', trade.dealingType ? dealingTypeLabel(trade.dealingType) : null);
  }

  const mainAmount = isSale ? trade.dealAmount : trade.depositAmount;
  const amountLabel = isSale ? '거래금액' : '보증금';

  return (
    <div>
      <div className="rounded-[12px] bg-[#f7f8fa] px-4 py-3">
        <p className="text-[12px] text-[#6a7282]">
          {isSale ? '매매' : trade.rentType === 'WOLSE' ? '월세' : '전세'} · {amountLabel}
        </p>
        <p
          className={`mt-0.5 text-[20px] font-extrabold text-[#101828] ${
            trade.isCancelled ? 'text-[#99a1af] line-through decoration-[#e7000b]/70' : ''
          }`}
          data-testid="trade-detail-amount"
        >
          {mainAmount == null ? '-' : formatKoreanPrice(mainAmount)}
        </p>
        {trade.rentType === 'WOLSE' && trade.monthlyRentAmount != null && (
          <p className="mt-0.5 text-[13px] font-semibold text-[#4a5565]">
            월세 {trade.monthlyRentAmount.toLocaleString('ko-KR')}만원
          </p>
        )}
        {trade.landLeaseYn === true && (
          <span className="mt-2 inline-block rounded-full bg-[#fef3c6] px-2 py-0.5 text-[11px] font-semibold text-[#973c00]">
            토지임대부
          </span>
        )}
      </div>
      <dl className="mt-3 divide-y divide-[#f3f4f6]">
        {rows.map((row) => (
          <div key={row.label} className="flex items-start justify-between gap-3 py-2.5 text-[13px]">
            <dt className="shrink-0 text-[#6a7282]">{row.label}</dt>
            <dd
              className={`text-right ${
                row.tone === 'muted'
                  ? 'text-[#99a1af]'
                  : row.tone === 'pending'
                    ? 'font-semibold text-[#973c00]'
                    : row.tone === 'cancel'
                      ? 'font-semibold text-[#e7000b]'
                      : 'font-medium text-[#101828]'
              }`}
            >
              {row.value}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
