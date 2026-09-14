import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { PopularKeywordResponse } from './types';

export async function getPopularKeywords(limit: number): Promise<PopularKeywordResponse[]> {
  const { data } = await httpClient.get<ApiResponse<PopularKeywordResponse[]>>('/api/search/popular', {
    params: { limit },
  });
  return (data as Extract<ApiResponse<PopularKeywordResponse[]>, { success: true }>).data;
}
