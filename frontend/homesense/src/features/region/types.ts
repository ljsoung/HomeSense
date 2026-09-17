/**
 * InterestRegionSummaryResponse.java 실제 필드 그대로. fullPath는 "시도 시군구 읍면동" 형태의
 * 전체 경로 문자열이라, Figma 카드가 요구하는 "시군구 / 읍면동" 2줄 표시는 프론트에서
 * splitRegionPath()로 직접 분리한다(백엔드가 이미 분리된 필드를 주지 않는다).
 *
 * tradeCount는 avgPrice/changeRate와 같은 호출(RegionStatsCalculator.calculate())의 부산물이라
 * 항상 같은 기간을 가리킨다 — 그 기간은 달력월이 아니라 "최근 1개월" 롤링 윈도우(KST,
 * `LocalDate.now().minusMonths(1)`)다. 거래가 0건 있는 지역과 0건인 지역을 구분해야 하므로 항상
 * 노출한다(avgPrice/changeRate가 null이어도 tradeCount는 0으로 내려온다).
 */
export interface InterestRegionSummaryResponse {
  favoriteRegionId: number;
  legalDongCd: string;
  fullPath: string;
  avgPrice: number | null;
  changeRate: number | null;
  tradeCount: number;
}
