import { MapFoldIcon } from '../../components/icons/MapFoldIcon';
import { SIDE_CARD_CLASS } from './detailFormat';

interface LocationCardProps {
  address: string | null;
  locationPrecision?: string | null;
  loading: boolean;
}

/**
 * DTL-01 구성요소 8 — 위치(Figma 4:3478). 좌표는 BAT-GEO-01이 채웠지만 지도 화면(MAP-01)과 카카오 SDK 연동이 아직 없어,
 * Figma의 지도 그림 자리에 "준비 중" 자리표시를 두고 "지도에서 크게 보기"는 비활성으로 둔다 — 이 카드는 좌표를 쓰지 않는다.
 * Figma 그림은 특정 위치에 핀이 꽂힌 가짜 지도라 실제 위치처럼 읽혀 쓰지 않는다(결정 2). 근사 좌표 문구는 UI정의서 7.2절 — BAT-GEO-01 키워드 보완으로 찾은 단지
 * (locationPrecision = APPROXIMATE)에서 나온다. 헤더 배지(precisionBadge)와 달리 위치에 관한 문구라 locationPrecision만 본다 —
 * 좌표가 없으면(NULL) 근사라고 하지 않는다.
 */
export function LocationCard({ address, locationPrecision, loading }: LocationCardProps) {
  const approximate = Boolean(locationPrecision) && locationPrecision !== 'PRECISE';
  return (
    <section aria-labelledby="complex-location-title" className={SIDE_CARD_CLASS}>
      <h2 id="complex-location-title" className="text-[14px] font-extrabold leading-[21px] text-[#101828]">
        위치
      </h2>
      <div className="pt-3">
        <div className="flex h-[160px] flex-col items-center justify-center gap-2 rounded-[14px] border border-[#f3f4f6] bg-[#f7f8fa] text-[#99a1af]">
          <MapFoldIcon className="size-6" />
          <p className="text-[12px]">지도 위치는 준비 중입니다</p>
        </div>
      </div>
      {loading ? (
        <div className="mt-2.5 h-4 w-2/3 animate-pulse rounded bg-[#f3f4f6]" />
      ) : (
        <p className="pt-2.5 text-[11px] leading-[16.5px] text-[#99a1af]">{address || '주소 정보 없음'}</p>
      )}
      {approximate && <p className="pt-1 text-[11px] leading-[16.5px] text-[#973c00]">근사 위치입니다</p>}
      <div className="pt-3">
        <button
          type="button"
          disabled
          aria-describedby="complex-location-map-note"
          className="flex h-[41.5px] w-full items-center justify-center gap-2 rounded-[14px] border border-brand text-[13px] font-semibold leading-[19.5px] text-brand disabled:cursor-not-allowed disabled:opacity-50"
        >
          <MapFoldIcon className="size-4" />
          지도에서 크게 보기
        </button>
      </div>
      <p id="complex-location-map-note" className="pt-1.5 text-center text-[11px] text-[#99a1af]">
        지도 화면 준비 중
      </p>
    </section>
  );
}
