export type NotificationType = 'PRICE_CHANGE' | 'NEW_TRADE';

/**
 * GET /api/notifications 항목 — NotificationResponse.java 실제 필드 그대로. record 컴포넌트 이름이 isRead라
 * JSON 키도 isRead다(TradeResponse.isCancelled와 같다). sentAt은 타임존 없는 LocalDateTime 문자열이다.
 * complexId/legalDongCd/tradeId는 알림 유형에 맞는 딥링크 대상만 채워진다(상호 배타 아님).
 */
export interface NotificationResponse {
  notificationId: number;
  notificationType: NotificationType;
  title: string;
  message: string;
  complexId: number | null;
  legalDongCd: string | null;
  tradeId: number | null;
  isRead: boolean;
  sentAt: string;
}
