import type { HousingType, PrecisionBadge } from '../../features/complex/types';

const HOUSING_TYPE_LABEL: Record<HousingType, string> = {
  APT: '아파트',
  VILLA: '연립다세대',
};

interface DataTrustBadgeProps {
  /** 단지 상세(DTL-01)는 complex_type이 비어 유형을 정할 수 없는 단지가 있다 — 없으면 유형 배지를 그리지 않는다. */
  housingType?: HousingType | null;
  /**
   * UI정의서 4.9절의 정밀/근사 배지 — 서버가 계산한 precisionBadge 그대로(유사 매칭이거나 근사 좌표면 APPROXIMATE).
   * 없으면(null·키 생략) 렌더링하지 않는다.
   */
  precisionBadge?: PrecisionBadge | null;
  /** sm: 카드(HOME-01·SRCH-01). md: 단지 상세 헤더(DTL-01 Figma 4:3048 — 11px 굵게, 좌우 10px). */
  size?: 'sm' | 'md';
}

/** UIC-09. */
export function DataTrustBadge({ housingType, precisionBadge, size = 'sm' }: DataTrustBadgeProps) {
  const md = size === 'md';
  const pill = md ? 'px-2.5 py-1 text-[11px] font-bold leading-[16.5px]' : 'px-2 py-0.5 text-[10px] font-semibold';
  return (
    <div className={`flex items-center ${md ? 'gap-1.5' : 'gap-1'}`}>
      {housingType && (
        <span className={`rounded-full bg-[#d1eae6] text-[#0b4a43] ${pill} ${md ? '' : 'tracking-[0.25px]'}`}>
          {HOUSING_TYPE_LABEL[housingType]}
        </span>
      )}
      {precisionBadge && (
        <span
          className={`flex items-center gap-1 rounded-full ${pill} ${
            precisionBadge === 'PRECISE' ? 'bg-[#dcfce7] text-[#016630]' : 'bg-[#fef3c6] text-[#973c00]'
          }`}
        >
          <span
            className={`size-1.5 rounded-full ${
              precisionBadge === 'PRECISE' ? 'bg-[#00c950]' : md ? 'bg-[#ffb900]' : 'bg-[#fe9a00]'
            }`}
          />
          {precisionBadge === 'PRECISE' ? '정밀' : '근사'}
        </span>
      )}
    </div>
  );
}
