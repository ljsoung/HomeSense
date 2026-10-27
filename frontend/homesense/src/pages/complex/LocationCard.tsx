import { Link } from 'react-router-dom';
import { MapFoldIcon } from '../../components/icons/MapFoldIcon';
import { SIDE_CARD_CLASS } from './detailFormat';

interface LocationCardProps {
  address: string | null;
  locationPrecision?: string | null;
  loading: boolean;
  /** 좌표가 있으면 MAP-01에서 이 단지를 여는 주소(buildComplexMapHref). 없으면 버튼을 비활성으로 둔다. */
  mapHref?: string;
}

/**
 * DTL-01 구성요소 8 — 위치(Figma 4:3478). 카드 안의 지도 그림 자리는 여전히 자리표시다 — 상세 화면에서는 카카오 SDK를
 * 불러오지 않는다(결정 2, Figma 그림은 특정 위치에 핀이 꽂힌 가짜 지도라 실제 위치처럼 읽혀 쓰지 않는다). 좌표가 있으면
 * "지도에서 크게 보기"가 MAP-01에서 이 단지를 연다(complexId + 좌표 — 지도가 그 위치로 가서 핀을 강조하고 팝업을 연다).
 * 좌표가 없으면(지오코딩 못 찾음·지번 없음) 버튼을 비활성으로 두고 이유를 적는다. 근사 좌표 문구는 UI정의서 7.2절 — BAT-GEO-01 키워드 보완으로 찾은 단지
 * (locationPrecision = APPROXIMATE)에서 나온다. 헤더 배지(precisionBadge)와 달리 위치에 관한 문구라 locationPrecision만 본다 —
 * 좌표가 없으면(NULL) 근사라고 하지 않는다.
 */
export function LocationCard({ address, locationPrecision, loading, mapHref }: LocationCardProps) {
  const approximate = Boolean(locationPrecision) && locationPrecision !== 'PRECISE';
  return (
    <section aria-labelledby="complex-location-title" className={SIDE_CARD_CLASS}>
      <h2 id="complex-location-title" className="text-[14px] font-extrabold leading-[21px] text-[#101828]">
        위치
      </h2>
      <div className="pt-3">
        <div className="flex h-[160px] flex-col items-center justify-center gap-2 rounded-[14px] border border-[#f3f4f6] bg-[#f7f8fa] text-muted">
          <MapFoldIcon className="size-6" />
          <p className="text-[12px]">{mapHref ? '아래 버튼으로 지도에서 위치를 확인하세요' : '지도 위치 정보가 없습니다'}</p>
        </div>
      </div>
      {loading ? (
        <div className="mt-2.5 h-4 w-2/3 animate-pulse rounded bg-[#f3f4f6]" />
      ) : (
        <p className="pt-2.5 text-[11px] leading-[16.5px] text-muted">{address || '주소 정보 없음'}</p>
      )}
      {approximate && <p className="pt-1 text-[11px] leading-[16.5px] text-[#973c00]">근사 위치입니다</p>}
      <div className="pt-3">
        {mapHref ? (
          <Link
            to={mapHref}
            className="flex h-[41.5px] w-full items-center justify-center gap-2 rounded-[14px] border border-brand text-[13px] font-semibold leading-[19.5px] text-brand hover:bg-[#f0f9f7]"
          >
            <MapFoldIcon className="size-4" />
            지도에서 크게 보기
          </Link>
        ) : (
          <button
            type="button"
            disabled
            aria-describedby="complex-location-map-note"
            className="flex h-[41.5px] w-full items-center justify-center gap-2 rounded-[14px] border border-brand text-[13px] font-semibold leading-[19.5px] text-brand disabled:cursor-not-allowed disabled:opacity-50"
          >
            <MapFoldIcon className="size-4" />
            지도에서 크게 보기
          </button>
        )}
      </div>
      {!mapHref && !loading && (
        <p id="complex-location-map-note" className="pt-1.5 text-center text-[11px] text-muted">
          위치 정보가 없어 지도에서 볼 수 없습니다
        </p>
      )}
    </section>
  );
}
