import type { HousingType } from '../complex/types';

/**
 * RecentViewResponse.java 실제 필드 그대로. sido/sigungu/dongRi는 ComplexSummaryResponse가
 * 이미 노출하는 것과 정확히 같은 원시 필드 3개다(2026-09-17, CPX-RCV-RGN 카드 표시 필드 보강 —
 * 백엔드가 하나의 "address" 문자열로 조합하지 않고 원시값을 그대로 내려주므로 프론트가
 * `formatAddress()`(lib/format.ts)로 ComplexCard와 동일하게 조합한다). 셋 다 nullable(단지
 * 기본정보 xlsx 원본 미기재 가능)이다. 가격/전용면적/층수는 여전히 백엔드에 없다(recent_view
 * 테이블 자체에 대응 데이터가 없어 이번 작업 범위 밖 — CLAUDE.md SCR-HOME-01 절 참고) — Figma
 * 카드가 요구하는 가격/면적·층 표시는 계속 생략한다.
 */
export interface RecentViewResponse {
  complexId: number;
  complexName: string;
  housingType: HousingType | null;
  sido: string | null;
  sigungu: string | null;
  dongRi: string | null;
  viewedAt: string;
}
