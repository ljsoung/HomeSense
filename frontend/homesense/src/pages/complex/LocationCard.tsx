import { MapFoldIcon } from '../../components/icons/MapFoldIcon';
import { SIDE_CARD_CLASS } from './detailFormat';

interface LocationCardProps {
  address: string | null;
  locationPrecision?: string | null;
  loading: boolean;
}

/**
 * DTL-01 구성요소 8 — 위치(Figma 4:3478). 지오코딩(BAT-GEO-01)과 지도 화면(MAP-01)이 없어 좌표가 없으므로(로컬 DB
 * 좌표 0건) Figma의 지도 그림 자리에 "준비 중" 자리표시를 두고 "지도에서 크게 보기"는 비활성으로 둔다. Figma 그림은
 * 특정 위치에 핀이 꽂힌 가짜 지도라 좌표 없이 쓰면 실제 위치처럼 읽혀 쓰지 않는다. 카카오 SDK는 불러오지 않는다(결정 2).
 * 근사 좌표 문구는 UI정의서 7.2절 — MVP 데이터에선 나오지 않지만 조건부로 넣어 둔다.
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
