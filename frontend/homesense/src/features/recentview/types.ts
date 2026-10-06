import type { HousingType } from '../complex/types';

/**
 * RecentViewResponse.java 실제 필드 그대로. sido/sigungu/dongRi는 ComplexSummaryResponse가
 * 이미 노출하는 것과 정확히 같은 원시 필드 3개다(2026-09-17, CPX-RCV-RGN 카드 표시 필드 보강 —
 * 백엔드가 하나의 "address" 문자열로 조합하지 않고 원시값을 그대로 내려주므로 프론트가
 * `formatAddress()`(lib/format.ts)로 ComplexCard와 동일하게 조합한다). 셋 다 nullable(단지
 * 기본정보 xlsx 원본 미기재 가능)이고, 서버가 null 필드를 키째 빼므로(`non_null`) `?: T | null`로 둔다.
 * 백엔드는 대표 거래의 price/area/floor도 내려주지만(2026-09-17) 화면이 쓰지 않아 타입에 두지 않았다.
 */
export interface RecentViewResponse {
  complexId: number;
  complexName: string;
  housingType: HousingType | null;
  sido?: string | null;
  sigungu?: string | null;
  dongRi?: string | null;
  viewedAt: string;
}
