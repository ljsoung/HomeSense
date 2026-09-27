import { httpClient } from '../../lib/httpClient';
import type { ApiResponse, PageMeta } from '../../types/api';
import type { ComplexSummaryResponse } from './types';

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
