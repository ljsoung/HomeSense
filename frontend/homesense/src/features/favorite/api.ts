import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { FavoritePropertyResponse, FavoritePropertySummaryResponse } from './types';

export async function getFavoriteProperties(): Promise<FavoritePropertySummaryResponse[]> {
  const { data } = await httpClient.get<ApiResponse<FavoritePropertySummaryResponse[]>>('/api/favorites/properties');
  return (data as Extract<ApiResponse<FavoritePropertySummaryResponse[]>, { success: true }>).data;
}

/** AddFavoritePropertyRequest.java 실제 필드 그대로 — complexId 하나뿐이다(housingType 불필요). */
export async function addFavoriteProperty(complexId: number): Promise<FavoritePropertyResponse> {
  const { data } = await httpClient.post<ApiResponse<FavoritePropertyResponse>>('/api/favorites/properties', { complexId });
  return (data as Extract<ApiResponse<FavoritePropertyResponse>, { success: true }>).data;
}

/** DELETE 경로의 {id}는 complexId가 아니라 favoritePropertyId다(FavoriteController.removeFavoriteProperty()). */
export async function removeFavoriteProperty(favoritePropertyId: number): Promise<void> {
  await httpClient.delete<ApiResponse<void>>(`/api/favorites/properties/${favoritePropertyId}`);
}
