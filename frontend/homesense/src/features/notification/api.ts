import { httpClient } from '../../lib/httpClient';
import type { ApiResponse, PageMeta } from '../../types/api';
import type { NotificationResponse, NotificationSettingItem, NotificationSettingResponse, NotificationType } from './types';

/**
 * 알림 이력(GET /api/notifications). 서버가 발생 시각(created_at) 최신순, 같으면 ID 역순으로 고정해 돌려주고 요청의 정렬은
 * 버린다. 발생 후 6개월이 지난 알림은 빠진다. 페이지는 0부터 센다(ApiResponse.success(Page<T>) 관례), 크기는 서버가 50건까지로
 * 줄인다. MY-01 미리보기가 쓴다(읽음 처리는 하지 않는다).
 */
export async function getNotifications(page: number, size: number): Promise<NotificationResponse[]> {
  return (await getNotificationPage({ page, size })).items;
}

export interface NotificationPage {
  items: NotificationResponse[];
  pageMeta: PageMeta;
}

/** MY-04 알림 이력 한 페이지. type을 비우면 전체. 페이지 메타가 없으면(서버 계약 위반) 받은 항목만으로 채운다. */
export async function getNotificationPage(
  query: { type?: NotificationType; page: number; size: number },
  signal?: AbortSignal,
): Promise<NotificationPage> {
  const { data } = await httpClient.get<ApiResponse<NotificationResponse[]>>('/api/notifications', {
    params: { type: query.type, page: query.page, size: query.size },
    signal,
  });
  const success = data as Extract<ApiResponse<NotificationResponse[]>, { success: true }>;
  return {
    items: success.data,
    pageMeta: success.pageMeta ?? { page: query.page, size: query.size, totalElements: success.data.length, totalPages: 1 },
  };
}

/** 읽음 처리(PATCH /api/notifications/{id}/read) — 본인 알림만, 이미 읽었어도 그대로 성공(멱등). */
export async function markNotificationRead(notificationId: number): Promise<void> {
  await httpClient.patch<ApiResponse<void>>(`/api/notifications/${notificationId}/read`);
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
