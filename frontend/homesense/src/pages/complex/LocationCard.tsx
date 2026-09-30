import { MapFoldIcon } from '../../components/icons/MapFoldIcon';
import { MapPinIcon } from '../../components/icons/MapPinIcon';
import { CARD_CLASS } from './detailFormat';

interface LocationCardProps {
  address: string | null;
  locationPrecision?: string | null;
  loading: boolean;
}

/**
 * DTL-01 구성요소 8 — 위치. 지오코딩(BAT-GEO-01)과 지도 화면(MAP-01)이 없어 좌표가 없으므로(로컬 DB 좌표 0건)
 * 주소와 중립 자리표시만 보이고 "지도에서 크게 보기"는 비활성으로 둔다. 카카오 SDK는 불러오지 않는다(결정 2).
 * 근사 좌표 문구는 UI정의서 7.2절 — MVP 데이터에선 나오지 않지만 조건부로 넣어 둔다.
 */
export function LocationCard({ address, locationPrecision, loading }: LocationCardProps) {
  const approximate = Boolean(locationPrecision) && locationPrecision !== 'PRECISE';
  return (
    <section aria-labelledby="complex-location-title" className={CARD_CLASS}>
      <h2 id="complex-location-title" className="text-[15px] font-bold text-[#101828]">
        위치
      </h2>
      {loading ? (
        <div className="mt-2 h-4 w-2/3 animate-pulse rounded bg-[#f3f4f6]" />
      ) : (
        <p className="mt-1.5 flex items-start gap-1 text-[12.5px] leading-[1.5] text-[#4a5565]">
          <MapPinIcon className="mt-0.5 size-3.5 shrink-0 text-[#99a1af]" />
          <span>{address || '주소 정보 없음'}</span>
        </p>
      )}
      {approximate && <p className="mt-1 text-[11.5px] text-[#973c00]">근사 위치입니다</p>}
      <div className="mt-3 flex h-[160px] flex-col items-center justify-center gap-2 rounded-[12px] border border-dashed border-[#d1d5dc] bg-[#f7f8fa] text-[#99a1af]">
        <MapFoldIcon className="size-6" />
        <p className="text-[12px]">지도 위치는 준비 중입니다</p>
      </div>
      <button
        type="button"
        disabled
        aria-describedby="complex-location-map-note"
        className="mt-3 w-full rounded-[10px] border border-[#e5e7eb] bg-white py-2.5 text-[13px] font-semibold text-[#99a1af] disabled:cursor-not-allowed"
      >
        지도에서 크게 보기
      </button>
      <p id="complex-location-map-note" className="mt-1.5 text-center text-[11px] text-[#99a1af]">
        지도 화면 준비 중
      </p>
    </section>
  );
}
