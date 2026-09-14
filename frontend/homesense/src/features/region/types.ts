/**
 * InterestRegionSummaryResponse.java 실제 필드 그대로. fullPath는 "시도 시군구 읍면동" 형태의
 * 전체 경로 문자열이라, Figma 카드가 요구하는 "시군구 / 읍면동" 2줄 표시는 프론트에서
 * splitRegionPath()로 직접 분리한다(백엔드가 이미 분리된 필드를 주지 않는다).
 */
export interface InterestRegionSummaryResponse {
  favoriteRegionId: number;
  legalDongCd: string;
  fullPath: string;
  avgPrice: number | null;
  changeRate: number | null;
}
