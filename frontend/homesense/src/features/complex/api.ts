import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { ComplexSummaryResponse } from './types';

export async function getPopularComplexes(limit: number): Promise<ComplexSummaryResponse[]> {
  const { data } = await httpClient.get<ApiResponse<ComplexSummaryResponse[]>>('/api/complexes/popular', {
    params: { limit },
  });
  return (data as Extract<ApiResponse<ComplexSummaryResponse[]>, { success: true }>).data;
}
