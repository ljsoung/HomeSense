import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { AlertCircleIcon } from '../../components/icons/AlertCircleIcon';
import { BuildingIcon } from '../../components/icons/BuildingIcon';
import { MainLayout } from '../../components/layout/MainLayout';
import { EmptyState } from '../../components/ui/EmptyState';
import { useToast } from '../../components/ui/useToast';
import { getTradeDetail } from '../../features/trade/api';
import type { TradeDealType, TradeDetailResponse, TradeResponse } from '../../features/trade/types';
import { useFavoriteToggle } from '../home/useFavoriteToggle';
import { ComplexBreadcrumb, ComplexHeader } from './ComplexHeader';
import { BasicInfoSummary, DetailInfoSection, MatchPendingNotice } from './ComplexInfoSections';
import { CARD_CLASS, displayAddress } from './detailFormat';
import { buildComplexMapHref } from '../../features/map/mapParams';
import { LocationCard } from './LocationCard';
import { PriceTrendCard } from './PriceTrendCard';
import { TradeDetailModal } from './TradeDetailModal';
import { TradeHistorySection } from './TradeHistorySection';
import { parseComplexId, useComplexDetailData } from './useComplexDetailData';

const DEAL_TYPES: readonly TradeDealType[] = ['SALE', 'JEONSE', 'WOLSE'];

/** `?deal=` — 없거나 알 수 없는 값이면 매매. */
function dealTypeFromParam(raw: string | null): TradeDealType {
  return DEAL_TYPES.find((type) => type === raw) ?? 'SALE';
}

/** `?trade=` — 양의 정수가 아니면 null. */
function tradeIdFromParam(raw: string | null): number | null {
  if (raw === null || !/^[1-9][0-9]{0,15}$/.test(raw)) return null;
  return Number(raw);
}

/** 화면 안에서 거래 행을 눌러 연 모달의 히스토리 항목 표시 — 닫을 때 뒤로 가기(그 항목을 꺼냄)로 닫는다. */
interface TradeModalState {
  tradeModal?: boolean;
}

export const TRADE_NOT_FOUND_TOAST = '해당 거래를 찾을 수 없습니다.';

/** 단지가 바뀌면(다른 단지 링크로 이동) 화면 상태를 새로 시작한다 — 아래 훅·모달이 id를 고정값으로 본다. */
export function ComplexDetailRoute() {
  const { id } = useParams();
  return <ComplexDetailPage key={id ?? ''} rawId={id} />;
}

/**
 * SCR-DTL-01 단지 상세. 단지 상세(GET /api/complexes/{id})와 실거래 이력(GET /api/trades)을 따로 받아 한쪽이
 * 실패해도 나머지를 그린다.
 *
 * 배치는 컴포넌트 트리 하나에 grid-template-areas만 바꾼다(CLAUDE.md "반응형 렌더 규칙" — 두 벌 렌더 금지).
 * 1280px 이상: 왼쪽 본문 + 오른쪽 300px 사이드바(가격 추이·위치, 안쪽이 sticky). 그 아래: 경로 → 헤더 → 요약 →
 * 상세정보 → 가격 추이 → 위치 → 탭·이력 순서로 한 줄. 데이터 출처 두 줄(구성요소 9)은 공용 Footer가 같은 문구로
 * 모든 화면에 이미 보여 주므로 본문에 다시 넣지 않는다(같은 문구가 연달아 두 번 보였다).
 */
function ComplexDetailPage({ rawId }: { rawId: string | undefined }) {
  const complexId = parseComplexId(rawId);
  const [searchParams, setSearchParams] = useSearchParams();
  const rawDeal = searchParams.get('deal');
  const dealType = dealTypeFromParam(rawDeal);
  const { detail, saleHistory, selectedHistory, retryDetail, retryHistory } = useComplexDetailData(complexId, dealType);
  const { favoritedIds, toggleFavorite, pendingFavoriteId, processingIds } = useFavoriteToggle({ listLinkInToast: true });
  const location = useLocation();
  const navigate = useNavigate();
  const { showToast } = useToast();

  // 거래상세 모달은 URL `?trade={tradeId}`가 연다(UI정의서 6.3). 화면 안에서 행을 누르면 push로 붙이고(state 표시) 닫을 때 뒤로
  // 가기로 그 항목을 꺼낸다 — 브라우저 뒤로 가기도 같은 결과(모달 닫힘)다. 직접 진입(공유·새로고침 아닌 새 탭 등)은 state 표시가
  // 없어 먼저 상세를 받아 이 단지의 거래인지 확인한 뒤 연다. 없는 거래(404)·다른 단지의 거래·형식이 틀린 값은 열지 않고 파라미터를
  // replace로 지운 뒤 안내한다. 직접 진입 후 닫으면 replace로 파라미터만 지운다(이전 항목이 앱 밖일 수 있다).
  const rawTrade = searchParams.get('trade');
  const tradeParam = tradeIdFromParam(rawTrade);
  const openedInApp = (location.state as TradeModalState | null)?.tradeModal === true;
  const [verified, setVerified] = useState<{ tradeId: number; data?: TradeDetailResponse } | null>(null);
  const verifiedHere = verified !== null && verified.tradeId === tradeParam;

  const removeTradeParam = useCallback(() => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete('trade');
        return next;
      },
      { replace: true },
    );
  }, [setSearchParams]);

  useEffect(() => {
    if (rawTrade === null || openedInApp || verifiedHere) return;
    const reject = () => {
      showToast(TRADE_NOT_FOUND_TOAST, 'error');
      removeTradeParam();
    };
    const controller = new AbortController();
    if (tradeParam === null || complexId === null) {
      // 렌더가 끝난 뒤 한 번만 — StrictMode가 effect를 두 번 돌려도 앞 실행은 정리(abort)돼 안내가 한 번이다.
      void Promise.resolve().then(() => {
        if (!controller.signal.aborted) reject();
      });
      return () => controller.abort();
    }
    getTradeDetail(tradeParam, controller.signal)
      .then((data) => {
        if (data.complexId === complexId) setVerified({ tradeId: tradeParam, data });
        else reject();
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (axios.isAxiosError(error) && error.response?.status === 404) reject();
        // 일시 오류는 모달을 열고 모달의 오류·다시 시도에 맡긴다(이 단지의 거래인지는 다시 받을 때도 모달이 보이는 값으로 알 수 있다).
        else setVerified({ tradeId: tradeParam });
      });
    return () => controller.abort();
  }, [rawTrade, tradeParam, openedInApp, verifiedHere, complexId, showToast, removeTradeParam]);

  const openTradeId = tradeParam !== null && (openedInApp || verifiedHere) ? tradeParam : null;
  const closeTrade = () => {
    if (openedInApp) navigate(-1);
    else removeTradeParam();
  };

  // 알 수 없는 ?deal= 값은 매매로 보고 URL도 정리한다(기록을 남기지 않는 replace).
  useEffect(() => {
    if (rawDeal !== null && (rawDeal === 'SALE' || !DEAL_TYPES.some((type) => type === rawDeal))) {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.delete('deal');
          return next;
        },
        { replace: true },
      );
    }
  }, [rawDeal, setSearchParams]);

  const changeDealType = useCallback(
    (type: TradeDealType) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (type === 'SALE') next.delete('deal');
          else next.set('deal', type);
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  const data = detail.status === 'success' ? detail.data : null;

  useEffect(() => {
    const previous = document.title;
    if (data) document.title = `${data.complexName} | HomeSense`;
    else if (detail.status === 'notFound') document.title = '단지를 찾을 수 없습니다 | HomeSense';
    return () => {
      document.title = previous;
    };
  }, [data, detail.status]);

  if (detail.status === 'notFound') {
    return (
      <MainLayout>
        <div className="mx-auto max-w-[1280px] px-4 py-16 md:px-8">
          <div className={CARD_CLASS}>
            <EmptyState
              icon={<BuildingIcon className="size-6" />}
              title="존재하지 않는 단지입니다"
              description="주소가 바뀌었거나 삭제된 단지일 수 있습니다."
              actions={
                <Link to="/" className="rounded-[10px] bg-brand px-4 py-2 text-[13px] font-semibold text-white">
                  홈으로
                </Link>
              }
            />
          </div>
        </div>
      </MainLayout>
    );
  }

  const openTrade = (trade: TradeResponse) =>
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set('trade', String(trade.tradeId));
        return next;
      },
      { state: { tradeModal: true } satisfies TradeModalState },
    );
  const detailLoading = detail.status === 'loading';

  return (
    <MainLayout>
      <div
        className="mx-auto grid max-w-[1280px] grid-cols-1 gap-4 px-4 pb-10 pt-5 [grid-template-areas:'crumb'_'header'_'info'_'side'_'history'] xl:grid-cols-[minmax(0,1fr)_300px] xl:grid-rows-[auto_auto_auto_1fr] xl:gap-x-5 xl:px-8 xl:[grid-template-areas:'crumb_crumb'_'header_side'_'info_side'_'history_side']"
        data-testid="complex-detail-grid"
      >
        {/* Figma: 경로 줄과 첫 카드 사이 20px(카드끼리는 16px). */}
        <div className="mb-1 min-w-0 [grid-area:crumb]">
          <ComplexBreadcrumb detail={data} />
        </div>

        <div className="min-w-0 space-y-4 self-start [grid-area:header]">
          {detail.status === 'error' && (
            <div
              role="alert"
              className="flex flex-wrap items-center justify-between gap-3 rounded-[12px] border border-[#fee2e2] bg-[#fef2f2] px-4 py-3"
            >
              <p className="flex items-center gap-2 text-[13px] text-[#7f1d1d]">
                <AlertCircleIcon className="size-4 shrink-0 text-[#e7000b]" />
                <span>{detail.message}</span>
              </p>
              <button
                type="button"
                onClick={retryDetail}
                className="rounded-[10px] bg-white px-3.5 py-1.5 text-[13px] font-semibold text-[#e7000b] shadow-[0_1px_2px_rgba(0,0,0,0.1)]"
              >
                다시 시도
              </button>
            </div>
          )}
          {detail.status !== 'error' && (
            <ComplexHeader
              detail={data}
              favorited={complexId !== null && favoritedIds.has(complexId)}
              favoritePending={complexId !== null && pendingFavoriteId === complexId}
              favoriteProcessing={complexId !== null && processingIds.has(complexId)}
              onToggleFavorite={() => {
                if (complexId !== null) toggleFavorite(complexId);
              }}
            />
          )}
        </div>

        <div className="min-w-0 space-y-4 self-start [grid-area:info]">
          {detailLoading ? (
            <div className={`${CARD_CLASS} space-y-3`} data-testid="basic-info-skeleton">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="h-10 animate-pulse rounded bg-[#f3f4f6]" />
              ))}
            </div>
          ) : data ? (
            data.matchPending ? (
              <MatchPendingNotice />
            ) : (
              <>
                <BasicInfoSummary basic={data.basicInfo} />
                <DetailInfoSection extended={data.extendedInfo} basic={data.basicInfo} />
              </>
            )
          ) : null}
        </div>

        <aside className="min-w-0 [grid-area:side]" aria-label="가격 추이와 위치">
          <div className="space-y-4 xl:sticky xl:top-6">
            <PriceTrendCard saleHistory={saleHistory} onRetry={() => retryHistory('SALE')} />
            <LocationCard
              address={data ? displayAddress(data) : null}
              locationPrecision={data?.locationPrecision}
              loading={detailLoading}
              mapHref={
                data && data.latitude != null && data.longitude != null
                  ? buildComplexMapHref(data.complexId, data.latitude, data.longitude)
                  : undefined
              }
            />
          </div>
        </aside>

        <div className="min-w-0 self-start [grid-area:history]">
          <TradeHistorySection
            dealType={dealType}
            onChangeDealType={changeDealType}
            history={selectedHistory}
            onRetry={() => retryHistory(dealType)}
            onOpenTrade={openTrade}
          />
        </div>

      </div>

      {openTradeId !== null && (
        <TradeDetailModal
          key={openTradeId}
          tradeId={openTradeId}
          onClose={closeTrade}
          initialData={verifiedHere ? verified?.data : undefined}
        />
      )}
    </MainLayout>
  );
}
