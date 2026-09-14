import type { HousingType } from '../../features/complex/types';

const HOUSING_TYPE_LABEL: Record<HousingType, string> = {
  APT: '아파트',
  VILLA: '연립다세대',
};

type MatchMethod = 'EXACT' | 'SIMILAR';

interface DataTrustBadgeProps {
  housingType: HousingType;
  /**
   * UI정의서 4.9절은 정밀(EXACT)/근사(SIMILAR) 배지도 함께 정의하지만, ComplexSummaryResponse에는
   * matchMethod 필드 자체가 없다(백엔드 소스 확인 완료, 사용자 확인: "프론트에서 배지/층수를
   * 생략" — CLAUDE.md SCR-HOME-01 절 참고). 값이 없으면 이 배지는 렌더링하지 않는다 — 백엔드가
   * 이 필드를 노출하는 시점에 그대로 넘기기만 하면 된다.
   */
  matchMethod?: MatchMethod;
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
