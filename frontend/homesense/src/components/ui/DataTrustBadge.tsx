import type { HousingType, MatchMethod } from '../../features/complex/types';

const HOUSING_TYPE_LABEL: Record<HousingType, string> = {
  APT: '아파트',
  VILLA: '연립다세대',
};

interface DataTrustBadgeProps {
  housingType: HousingType;
  /**
   * UI정의서 4.9절의 정밀(EXACT)/근사(SIMILAR) 배지 — matchMethod가 null(매칭 실패)이면 렌더링하지
   * 않는다. `undefined`도 함께 받아들이는 이유는 소비자가 `complex.matchMethod ?? undefined`처럼
   * null을 옵셔널 prop 관례로 넘기기 편하게 하기 위함이다(둘 다 동일하게 "배지 없음"으로 처리).
   */
  matchMethod?: MatchMethod | null;
}

/** UIC-09. */
export function DataTrustBadge({ housingType, matchMethod }: DataTrustBadgeProps) {
  return (
    <div className="flex items-center gap-1">
      <span className="rounded-full bg-[#d1eae6] px-2 py-0.5 text-[10px] font-semibold tracking-[0.25px] text-[#0b4a43]">
        {HOUSING_TYPE_LABEL[housingType]}
      </span>
      {matchMethod && (
        <span
          className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold ${
            matchMethod === 'EXACT' ? 'bg-[#dcfce7] text-[#016630]' : 'bg-[#fef3c6] text-[#973c00]'
          }`}
        >
          <span className={`size-1.5 rounded-full ${matchMethod === 'EXACT' ? 'bg-[#00c950]' : 'bg-[#fe9a00]'}`} />
          {matchMethod === 'EXACT' ? '정밀' : '근사'}
        </span>
      )}
    </div>
  );
}
