import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { ComplexMapSearchResponse } from '../complex/types';

/**
 * GET /api/complexes/map(API-CPX-01) — `query`는 mapParams.buildMapApiQuery()가 만든 bounds + 필터. 비로그인 조회가
 * 허용되는 공개 API다.
 */
export async function searchComplexesInBounds(query: URLSearchParams, signal?: AbortSignal): Promise<ComplexMapSearchResponse> {
  const { data } = await httpClient.get<ApiResponse<ComplexMapSearchResponse>>('/api/complexes/map', {
    params: query,
    signal,
  });
  return (data as Extract<ApiResponse<ComplexMapSearchResponse>, { success: true }>).data;
}
