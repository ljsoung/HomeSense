export type HousingType = 'APT' | 'VILLA';
export type DealCategory = 'SALE' | 'RENT';
export type MatchMethod = 'EXACT' | 'SIMILAR';

/**
 * ComplexSummaryResponse.java 실제 필드 그대로. 대표 거래 금액은 dealCategory가 SALE이면
 * 매매금액, RENT면 보증금이다(representativeAmount 자체가 이미 그 의미로 내려온다). 이미지 URL
 * 필드는 백엔드에 아예 없다(Complex 엔티티 확인 완료, ComplexCard의 자리표시 썸네일 참고).
 *
 * matchMethod/floor는 대표 거래(Trade) 엔티티에서 그대로 옮겨오는 nullable 필드다(2026-09-17,
 * CPX-RCV-RGN 카드 표시 필드 보강 작업으로 백엔드에 추가됨 — 이전엔 이 두 필드 자체가 DTO에
 * 없어 CLAUDE.md SCR-HOME-01 절이 "프론트에서 생략"으로 확정했던 갭이다). match_method는 매칭
 * 실패 시, floor는 원본 xlsx 미기재 시 각각 NULL로 내려온다 — 두 경우 모두 UI는 해당 배지/세그먼트를
 * 렌더링하지 않는다(DataTrustBadge/ComplexCard 참고).
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
  matchMethod: MatchMethod | null;
  floor: number | null;
}
