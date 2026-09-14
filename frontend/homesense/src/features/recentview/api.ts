import { httpClient } from '../../lib/httpClient';
import { SESSION_ID_HEADER, getOrCreateSessionId } from '../../lib/sessionId';
import type { ApiResponse } from '../../types/api';
import type { RecentViewResponse } from './types';

/**
 * 로그인 여부와 무관하게 항상 X-Session-Id를 실어 보낸다 — 로그인 사용자는 백엔드가 userId를
 * 우선시해 이 헤더를 무시하므로(RecentViewService.getRecent()) 해가 없고, 비로그인 사용자는
 * 이 헤더가 없으면 자신의 이전 조회 이력을 영영 다시 볼 수 없다.
 */
export async function getRecentViews(limit: number): Promise<RecentViewResponse[]> {
  const { data } = await httpClient.get<ApiResponse<RecentViewResponse[]>>('/api/recent-views', {
    params: { limit },
    headers: { [SESSION_ID_HEADER]: getOrCreateSessionId() },
  });
  return (data as Extract<ApiResponse<RecentViewResponse[]>, { success: true }>).data;
}
