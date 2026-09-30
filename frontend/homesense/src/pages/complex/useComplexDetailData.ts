import axios from 'axios';
import { useCallback, useEffect, useRef, useState } from 'react';
import { getComplexDetail } from '../../features/complex/api';
import type { ComplexDetailResponse } from '../../features/complex/types';
import { getTradeHistory } from '../../features/trade/api';
import type { TradeDealType, TradeResponse } from '../../features/trade/types';
import { getErrorMessage } from '../../lib/apiError';

export type DetailState =
  | { status: 'loading' }
  | { status: 'success'; data: ComplexDetailResponse }
  | { status: 'notFound' }
  | { status: 'error'; message: string };

export type HistoryState =
  | { status: 'loading' }
  | { status: 'success'; data: TradeResponse[] }
  | { status: 'error'; message: string };

const LOADING: HistoryState = { status: 'loading' };

/** 경로의 id — 양의 정수가 아니면 null(요청 없이 "찾을 수 없음"). */
export function parseComplexId(raw: string | undefined): number | null {
  if (!raw || !/^[1-9]\d*$/.test(raw)) return null;
  const id = Number(raw);
  return Number.isSafeInteger(id) ? id : null;
}

/**
 * DTL-01 데이터 로딩(6.1절). 단지 상세와 거래 이력은 서로 독립이라 한쪽이 실패해도 다른 쪽은 그린다. 재시도는
 * 실패한 쪽만 다시 부른다. 호출부는 단지 id가 바뀌면 컴포넌트를 새로 마운트한다(`key`) — 이 훅은 id가 고정이라고
 * 본다.
 *
 * 거래 이력은 거래유형별로 한 번씩만 받아 페이지에 보관한다. 매매 이력은 탭과 무관하게 항상 받는다 — 가격 추이
 * 카드와 매매 탭이 같은 데이터를 쓴다(TanStack Query가 없어 페이지 수준에서 공유, 결정 8). 전세·월세 탭은 처음 열
 * 때 받고, 다시 돌아와도 재요청하지 않는다. 진행 중인 요청은 탭을 바꿔도 취소하지 않는다 — 취소한 요청의 뒤처리가
 * 새 요청의 상태를 덮는 경합을 만들지 않기 위해서다. 같은 유형을 두 번 부르지 않도록 진행 중인 유형을 ref로 든다.
 */
export function useComplexDetailData(complexId: number | null, selectedDealType: TradeDealType) {
  const [detail, setDetail] = useState<DetailState>(() =>
    complexId === null ? { status: 'notFound' } : { status: 'loading' },
  );
  const [detailRound, setDetailRound] = useState(0);
  const [histories, setHistories] = useState<Partial<Record<TradeDealType, HistoryState>>>({});
  const inFlightRef = useRef<Set<TradeDealType>>(new Set());

  useEffect(() => {
    if (complexId === null) return;
    const controller = new AbortController();
    const load = async () => {
      setDetail({ status: 'loading' });
      try {
        const data = await getComplexDetail(complexId, controller.signal);
        setDetail({ status: 'success', data });
      } catch (error) {
        if (controller.signal.aborted) return;
        if (axios.isAxiosError(error) && error.response?.status === 404) {
          setDetail({ status: 'notFound' });
        } else {
          setDetail({ status: 'error', message: getErrorMessage(error) });
        }
      }
    };
    void load();
    return () => controller.abort();
  }, [complexId, detailRound]);

  useEffect(() => {
    if (complexId === null) return;
    const wanted: TradeDealType[] = selectedDealType === 'SALE' ? ['SALE'] : ['SALE', selectedDealType];
    const load = async (type: TradeDealType) => {
      inFlightRef.current.add(type);
      setHistories((prev) => ({ ...prev, [type]: LOADING }));
      try {
        const data = await getTradeHistory(complexId, type);
        setHistories((prev) => ({ ...prev, [type]: { status: 'success', data } }));
      } catch (error) {
        setHistories((prev) => ({ ...prev, [type]: { status: 'error', message: getErrorMessage(error) } }));
      } finally {
        inFlightRef.current.delete(type);
      }
    };
    for (const type of wanted) {
      if (histories[type] === undefined && !inFlightRef.current.has(type)) void load(type);
    }
  }, [complexId, selectedDealType, histories]);

  const retryDetail = useCallback(() => setDetailRound((round) => round + 1), []);
  const retryHistory = useCallback((type: TradeDealType) => {
    // 비우면 위 effect가 그 유형만 다시 받는다.
    setHistories((prev) => ({ ...prev, [type]: undefined }));
  }, []);

  return {
    detail,
    saleHistory: histories.SALE ?? LOADING,
    selectedHistory: histories[selectedDealType] ?? LOADING,
    retryDetail,
    retryHistory,
  };
}
