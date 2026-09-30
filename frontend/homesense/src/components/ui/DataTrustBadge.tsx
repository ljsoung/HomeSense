import type { HousingType, MatchMethod } from '../../features/complex/types';

const HOUSING_TYPE_LABEL: Record<HousingType, string> = {
  APT: '아파트',
  VILLA: '연립다세대',
};

interface DataTrustBadgeProps {
  /** 단지 상세(DTL-01)는 complex_type이 비어 유형을 정할 수 없는 단지가 있다 — 없으면 유형 배지를 그리지 않는다. */
  housingType?: HousingType | null;
  /**
   * UI정의서 4.9절의 정밀(EXACT)/근사(SIMILAR) 배지 — matchMethod가 null(매칭 실패)이면 렌더링하지
   * 않는다. `undefined`도 함께 받아들이는 이유는 소비자가 `complex.matchMethod ?? undefined`처럼
   * null을 옵셔널 prop 관례로 넘기기 편하게 하기 위함이다(둘 다 동일하게 "배지 없음"으로 처리).
   */
  matchMethod?: MatchMethod | null;
  /** sm: 카드(HOME-01·SRCH-01). md: 단지 상세 헤더(DTL-01 Figma 4:3048 — 11px 굵게, 좌우 10px). */
  size?: 'sm' | 'md';
}

/** UIC-09. */
export function DataTrustBadge({ housingType, matchMethod, size = 'sm' }: DataTrustBadgeProps) {
  const md = size === 'md';
  const pill = md ? 'px-2.5 py-1 text-[11px] font-bold leading-[16.5px]' : 'px-2 py-0.5 text-[10px] font-semibold';
  return (
    <div className={`flex items-center ${md ? 'gap-1.5' : 'gap-1'}`}>
      {housingType && (
        <span className={`rounded-full bg-[#d1eae6] text-[#0b4a43] ${pill} ${md ? '' : 'tracking-[0.25px]'}`}>
          {HOUSING_TYPE_LABEL[housingType]}
        </span>
      )}
      {matchMethod && (
        <span
          className={`flex items-center gap-1 rounded-full ${pill} ${
            matchMethod === 'EXACT' ? 'bg-[#dcfce7] text-[#016630]' : 'bg-[#fef3c6] text-[#973c00]'
          }`}
        >
          <span
            className={`size-1.5 rounded-full ${
              matchMethod === 'EXACT' ? 'bg-[#00c950]' : md ? 'bg-[#ffb900]' : 'bg-[#fe9a00]'
            }`}
          />
          {matchMethod === 'EXACT' ? '정밀' : '근사'}
        </span>
      )}
    </div>
  );
}
