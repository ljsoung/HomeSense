import { HomeIcon } from '../icons/HomeIcon';
import { HeartIcon } from '../icons/HeartIcon';
import { DataTrustBadge } from './DataTrustBadge';
import { formatArea, formatKoreanPrice } from '../../lib/format';
import type { ComplexSummaryResponse } from '../../features/complex/types';

interface ComplexCardProps {
  complex: ComplexSummaryResponse;
  isFavorited: boolean;
  onToggleFavorite: () => void;
  className?: string;
}

/**
 * UIC-05. 백엔드에 단지 이미지 URL 필드가 아예 없어(Complex 엔티티 확인 완료) 실제 사진 대신
 * 브랜드 톤 그라디언트 위에 옅은 집 아이콘을 올린 자리표시 썸네일을 쓴다 — Figma 목업의 스톡
 * 사진은 가상의 단지명에 맞춰진 것이라 실제 데이터와 매칭될 수 없어 그대로 옮기지 않았다.
 * matchMethod/floor는 ComplexSummaryResponse에 없어 배지·층수 표시를 생략한다(사용자 확인,
 * CLAUDE.md SCR-HOME-01 절 참고).
 */
export function ComplexCard({ complex, isFavorited, onToggleFavorite, className = '' }: ComplexCardProps) {
  return (
    <div className={`flex h-full flex-col overflow-hidden rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_1px_3px_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] ${className}`}>
      <div className="relative h-[228px] w-full shrink-0 overflow-hidden bg-[#f3f4f6]">
        <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-[#e8f5f2] to-[#d1eae6]">
          <HomeIcon className="size-14 opacity-40 [&_path]:stroke-brand" />
        </div>
        <button
          type="button"
          onClick={onToggleFavorite}
          aria-label={isFavorited ? '관심 매물 해제' : '관심 매물 등록'}
          aria-pressed={isFavorited}
          className="absolute top-2.5 right-2.5 flex size-8 items-center justify-center rounded-full bg-white/80 text-[#4a5565] shadow-[0_1px_3px_rgba(0,0,0,0.1),0_1px_2px_rgba(0,0,0,0.1)] backdrop-blur-sm transition-colors hover:text-[#e7000b]"
        >
          <HeartIcon filled={isFavorited} className={isFavorited ? 'text-[#ff2056]' : ''} />
        </button>
        <div className="absolute bottom-2.5 left-2.5">
          <DataTrustBadge housingType={complex.representativeHousingType} />
        </div>
      </div>
      <div className="flex flex-col p-4">
        <p className="truncate text-[14px] font-bold text-[#101828]">{complex.complexName}</p>
        <p className="mt-0.5 truncate text-[11.5px] text-[#99a1af]">
          {complex.sigungu} {complex.dongRi}
        </p>
        <p className="mt-2 text-[17px] font-extrabold tracking-[-0.3px] text-[#1c1c1e]">
          {formatKoreanPrice(complex.representativeAmount)}
        </p>
        <p className="mt-1.5 text-[11px] text-[#99a1af]">
          전용 {formatArea(complex.representativeArea)} · {complex.representativeDealDate}
        </p>
      </div>
    </div>
  );
}
