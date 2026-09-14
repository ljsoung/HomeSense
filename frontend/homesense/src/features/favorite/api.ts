import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';

/** AddFavoritePropertyRequest.java 실제 필드 그대로 — complexId 하나뿐이다(housingType 불필요). */
export async function addFavoriteProperty(complexId: number): Promise<void> {
  await httpClient.post<ApiResponse<unknown>>('/api/favorites/properties', { complexId });
}
