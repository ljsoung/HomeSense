import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { NotificationResponse, NotificationSettingItem, NotificationSettingResponse } from './types';

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

/** 내 알림 설정 전체(GET /api/notifications/settings). MY-02 알림조건 배지가 관심 매물과 ID로 조인한다. */
export async function getNotificationSettings(): Promise<NotificationSettingResponse[]> {
  const { data } = await httpClient.get<ApiResponse<NotificationSettingResponse[]>>('/api/notifications/settings');
  return (data as Extract<ApiResponse<NotificationSettingResponse[]>, { success: true }>).data;
}

/**
 * MY-03 알림 설정 저장(PUT /api/notifications/settings) — 선택한 대상 전부를 한 번에 보낸다. 서버가 한 트랜잭션으로 저장해
 * 하나라도 실패하면(남의 대상 403, 없는 대상 404, 검증 400) 아무것도 저장되지 않는다.
 */
export async function updateNotificationSettings(settings: NotificationSettingItem[]): Promise<void> {
  await httpClient.put<ApiResponse<void>>('/api/notifications/settings', { settings });
}
