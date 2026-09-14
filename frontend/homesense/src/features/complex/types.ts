export type HousingType = 'APT' | 'VILLA';
export type DealCategory = 'SALE' | 'RENT';

/**
 * ComplexSummaryResponse.java 실제 필드 그대로 — matchMethod/floor/이미지 URL 필드는 백엔드에
 * 아예 없다(CLAUDE.md SCR-HOME-01 절 "정밀/근사 배지, 층수 생략" 판단 참고). 대표 거래 금액은
 * dealCategory가 SALE이면 매매금액, RENT면 보증금이다(representativeAmount 자체가 이미 그
 * 의미로 내려온다).
 */
export interface ComplexSummaryResponse {
  complexId: number;
  complexName: string;
  sido: string;
  sigungu: string;
  dongRi: string;
  householdCount: number;
  buildingCount: number;
  approvalDate: string;
  representativeHousingType: HousingType;
  representativeDealCategory: DealCategory;
  representativeDealDate: string;
  representativeAmount: number;
  representativeArea: number;
}
