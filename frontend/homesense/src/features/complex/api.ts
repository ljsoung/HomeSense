import { httpClient } from '../../lib/httpClient';
import { SESSION_ID_HEADER, getOrCreateSessionId } from '../../lib/sessionId';
import type { ApiResponse, PageMeta } from '../../types/api';
import type { ComplexDetailResponse, ComplexSummaryResponse } from './types';

export async function getPopularComplexes(limit: number): Promise<ComplexSummaryResponse[]> {
  const { data } = await httpClient.get<ApiResponse<ComplexSummaryResponse[]>>('/api/complexes/popular', {
    params: { limit },
  });
  return (data as Extract<ApiResponse<ComplexSummaryResponse[]>, { success: true }>).data;
}

export interface ComplexSearchResult {
  data: ComplexSummaryResponse[];
  pageMeta: PageMeta;
}

/**
 * `query`는 features/search/searchParams.ts의 `buildApiQuery()`가 만든 URLSearchParams를 그대로
 * 받는다 — axios는 URLSearchParams를 params로 주면 가공 없이 그대로 직렬화해(반복 키 포함) 백엔드가
 * `housingTypes=APT&housingTypes=VILLA`처럼 리스트를 반복 파라미터로 받는 `@ModelAttribute` 바인딩과
 * 정확히 맞는다(라이브 curl로 확인, CLAUDE.md "단지 검색 지역코드·키워드·거래유형" 절).
 */
export async function searchComplexes(query: URLSearchParams, signal?: AbortSignal): Promise<ComplexSearchResult> {
  const response = await httpClient.get<ApiResponse<ComplexSummaryResponse[]>>('/api/complexes/search', {
    params: query,
    signal,
  });
  const body = response.data as Extract<ApiResponse<ComplexSummaryResponse[]>, { success: true }>;
  return { data: body.data, pageMeta: body.pageMeta as PageMeta };
}

/**
 * GET /api/complexes/{id} (DTL-01). 조회 이력(SVC-RCV-01)을 남기도록 X-Session-Id를 항상 싣는다 — 비로그인은 이
 * 헤더로 기록되고, 로그인 사용자는 백엔드가 userId를 우선한다(features/recentview/api.ts와 같은 규칙).
 */
export async function getComplexDetail(complexId: number, signal?: AbortSignal): Promise<ComplexDetailResponse> {
  const { data } = await httpClient.get<ApiResponse<ComplexDetailResponse>>(`/api/complexes/${complexId}`, {
    headers: { [SESSION_ID_HEADER]: getOrCreateSessionId() },
    signal,
  });
  return (data as Extract<ApiResponse<ComplexDetailResponse>, { success: true }>).data;
}
