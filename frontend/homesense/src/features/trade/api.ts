import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { TradeDealType, TradeDetailResponse, TradeResponse } from './types';

/**
 * GET /api/trades?complexId=&dealType= — 단지의 실거래 이력 전체(List, 페이지 없음, 계약일·거래 id 내림차순,
 * 해제 건 포함). 캐시 미적용이라 항상 최신이다.
 */
export async function getTradeHistory(complexId: number, dealType: TradeDealType, signal?: AbortSignal): Promise<TradeResponse[]> {
  const { data } = await httpClient.get<ApiResponse<TradeResponse[]>>('/api/trades', {
    params: { complexId, dealType },
    signal,
  });
  return (data as Extract<ApiResponse<TradeResponse[]>, { success: true }>).data;
}

export async function getTradeDetail(tradeId: number, signal?: AbortSignal): Promise<TradeDetailResponse> {
  const { data } = await httpClient.get<ApiResponse<TradeDetailResponse>>(`/api/trades/${tradeId}`, { signal });
  return (data as Extract<ApiResponse<TradeDetailResponse>, { success: true }>).data;
}
