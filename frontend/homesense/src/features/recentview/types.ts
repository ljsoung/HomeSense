import type { HousingType } from '../complex/types';

/**
 * RecentViewResponse.java 실제 필드 그대로 — 주소/가격/면적/층수는 백엔드에 없다(CLAUDE.md
 * SCR-HOME-01 절 참고, ComplexSummaryResponse의 matchMethod/floor 생략과 같은 종류의 데이터
 * 갭). Figma 카드가 요구하는 주소/가격/면적·층 표시는 이번 범위에서 함께 생략한다.
 */
export interface RecentViewResponse {
  complexId: number;
  complexName: string;
  housingType: HousingType | null;
  viewedAt: string;
}
