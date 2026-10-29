import type { DealCategory, RentType } from '../complex/types';

/** 이력 탭·API의 거래유형 필터(GET /api/trades의 dealType). */
export type TradeDealType = 'SALE' | 'JEONSE' | 'WOLSE';

/**
 * GET /api/trades?complexId=&dealType= 항목 — TradeResponse.java 실제 필드 그대로. 금액은 만원 단위.
 * cancelDate(해제사유발생일)와 dealingType(AGENT/DIRECT)은 해제되지 않았거나 전월세면 null이고, `non_null`
 * 직렬화라 JSON에서 키가 빠진다 — 선택 프로퍼티로 둔다.
 */
export interface TradeResponse {
  tradeId: number;
  dealDate: string;
  dealCategory: DealCategory;
  rentType?: RentType | null;
  excluUseArea: number;
  floor?: number | null;
  dealAmount?: number | null;
  depositAmount?: number | null;
  monthlyRentAmount?: number | null;
  dealingType?: string | null;
  isCancelled: boolean;
  cancelDate?: string | null;
  isRegistered: boolean;
}

/** GET /api/trades/{tradeId} — TradeDetailResponse.java 실제 필드 그대로. */
export interface TradeDetailResponse {
  /** 이 거래가 매칭된 단지. 매칭되지 않은 거래는 서버가 키를 뺀다. DTL-01 `?trade=` 진입 시 지금 단지의 거래인지 확인한다. */
  complexId?: number | null;
  tradeId: number;
  dealDate: string;
  excluUseArea: number;
  floor?: number | null;
  dealCategory: DealCategory;
  rentType?: RentType | null;
  dealAmount?: number | null;
  depositAmount?: number | null;
  monthlyRentAmount?: number | null;
  aptDong?: string | null;
  aptDongPending: boolean;
  dealingType?: string | null;
  sellerType?: string | null;
  buyerType?: string | null;
  registrationDate?: string | null;
  isCancelled: boolean;
  cancelDate?: string | null;
  landLeaseYn?: boolean | null;
}
