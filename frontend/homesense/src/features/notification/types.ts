export type NotificationType = 'PRICE_CHANGE' | 'NEW_TRADE';

/**
 * GET /api/notifications 항목 — NotificationResponse.java 실제 필드 그대로. record 컴포넌트 이름이 isRead라
 * JSON 키도 isRead다(TradeResponse.isCancelled와 같다). sentAt은 타임존 없는 LocalDateTime 문자열이다.
 * complexId/legalDongCd/tradeId는 알림 유형에 맞는 딥링크 대상만 채워진다(상호 배타 아님). message와 세 대상은
 * NULL 허용 컬럼이고 서버가 null 필드를 키째 빼므로(`non_null`) `?: T | null`로 둔다.
 */
export interface NotificationResponse {
  notificationId: number;
  notificationType: NotificationType;
  title: string;
  message?: string | null;
  complexId?: number | null;
  legalDongCd?: string | null;
  tradeId?: number | null;
  isRead: boolean;
  sentAt: string;
}

/**
 * GET /api/notifications/settings 항목 — NotificationSettingResponse.java 실제 필드 그대로. favoritePropertyId와
 * favoriteRegionId 중 정확히 하나만 채워진다. 대상 이름은 없어 관심 매물·지역 목록과 ID로 조인한다(SVC-NTF-01 설계).
 * priceChangeThresholdPct는 0.0~100.0(%)이다.
 */
export interface NotificationSettingResponse {
  notificationSettingId: number;
  /** 대상이 아닌 쪽은 서버가 키를 빼서(non_null) undefined로 온다. */
  favoritePropertyId?: number | null;
  favoriteRegionId?: number | null;
  priceChangeThresholdPct: number;
  newTradeAlertYn: boolean;
  emailAlertYn: boolean;
}
