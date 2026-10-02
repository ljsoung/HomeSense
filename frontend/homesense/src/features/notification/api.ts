import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { NotificationResponse } from './types';

/**
 * 알림 이력(GET /api/notifications). 서버가 발송 시각 내림차순(OrderBySentAtDesc)으로 돌려준다. type을 비우면
 * 전체다. 페이지는 0부터 센다(ApiResponse.success(Page<T>) 관례). MY-01은 읽음 처리(PATCH)를 하지 않는다.
 */
export async function getNotifications(page: number, size: number): Promise<NotificationResponse[]> {
  const { data } = await httpClient.get<ApiResponse<NotificationResponse[]>>('/api/notifications', {
    params: { page, size },
  });
  return (data as Extract<ApiResponse<NotificationResponse[]>, { success: true }>).data;
}
