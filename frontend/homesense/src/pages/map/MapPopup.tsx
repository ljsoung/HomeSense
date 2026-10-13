import { useEffect, useEffectEvent, useRef } from 'react';
import { Link } from 'react-router-dom';
import { XIcon } from '../../components/icons/XIcon';
import { DataTrustBadge } from '../../components/ui/DataTrustBadge';
import type { ComplexMapPointResponse } from '../../features/complex/types';
import { describeDealAmount, formatArea } from '../../lib/format';

interface MapPopupProps {
  item: ComplexMapPointResponse;
  /** 사용자가 핀·목록을 눌러 연 팝업이면 true — 열리면 팝업으로 포커스를 옮긴다(뒤로 와서 복원된 팝업은 옮기지 않는다). */
  autoFocus: boolean;
  onClose: () => void;
}

/**
 * MAP-01 구성요소 4 — 마커 상세 팝업(Figma 6:4659). 단지명·주택유형·정밀/근사 배지, 최근 거래가(SRCH-01 카드와 같은 금액
 * 규칙 — describeDealAmount), "상세보기"(DTL-01), 닫기(X)·Esc.
 *
 * 근사 안내(UI정의서 4.9·7.2절)는 근사의 이유마다 따로 보인다: 좌표가 근사(locationPrecision = APPROXIMATE)면 "근사
 * 위치입니다", 대표 거래가 유사 매칭(SIMILAR)이면 "일부 정보는 유사 매칭 결과입니다"(UI정의서 5.3절 문구). 배지는 서버
 * precisionBadge 하나로 정하지만, 위치가 정확한 유사 매칭 단지에 "근사 위치"라고 쓰면 사실과 달라 문구는 나눈다.
 */
export function MapPopup({ item, autoFocus, onClose }: MapPopupProps) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const titleId = `map-popup-title-${item.complexId}`;
  const approximateLocation = item.locationPrecision === 'APPROXIMATE';
  const similarMatch = item.matchMethod === 'SIMILAR';
  const meta = [formatArea(item.representativeArea), item.floor != null ? `${item.floor}층` : null].filter(Boolean).join(' · ');

  // 팝업은 지도 오버레이 컨테이너에 포털로 그려지는데, 그 컨테이너는 부모(KakaoMapView)의 effect가 지도에 붙인다 —
  // 자식 effect가 먼저 돌아 그 시점엔 아직 문서 밖이라, 다음 프레임에 포커스를 옮긴다.
  useEffect(() => {
    if (!autoFocus) return;
    const frame = requestAnimationFrame(() => closeRef.current?.focus());
    return () => cancelAnimationFrame(frame);
  }, [autoFocus, item.complexId]);

  const closeOnEscape = useEffectEvent(() => onClose());
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      // 필터 팝오버·시트가 열려 있으면 Esc는 그쪽을 닫는다 — 팝업까지 함께 닫지 않는다.
      const active = document.activeElement;
      if (active instanceof HTMLElement && active.closest('[role="dialog"]:not([data-testid="map-popup"])')) return;
      event.preventDefault();
      closeOnEscape();
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, []);

  return (
    <div role="dialog" aria-labelledby={titleId} className="flex flex-col items-center" data-testid="map-popup">
      <div className="relative w-[212px] rounded-[14px] border border-[#e5e7eb] bg-white p-4 shadow-[0_8px_16px_rgba(0,0,0,0.18)]">
        <DataTrustBadge housingType={item.representativeHousingType} precisionBadge={item.precisionBadge} />
        <p id={titleId} className="pt-2 pr-5 text-[13.5px] font-bold leading-[18.5px] text-[#101828]">
          {item.complexName}
        </p>
        {meta && <p className="pt-0.5 text-[11px] leading-[16.5px] text-[#99a1af]">{meta}</p>}
        <p className="pt-2 text-[20px] font-extrabold leading-[30px] tracking-[-0.4px] text-[#1c1c1e]">
          {describeDealAmount(item.representativeDealCategory, item.rentType, item.representativeAmount, item.monthlyRentAmount)}
        </p>
        {(approximateLocation || similarMatch) && (
          <div className="pt-1 text-[11px] leading-[16.5px] text-[#bb4d00]">
            {approximateLocation && <p>근사 위치입니다</p>}
            {similarMatch && <p>일부 정보는 유사 매칭 결과입니다</p>}
          </div>
        )}
        <Link
          to={`/complexes/${item.complexId}`}
          className="mt-3 flex h-9 w-full items-center justify-center rounded-[14px] bg-brand text-[13px] font-bold text-white hover:bg-[#0d4f48]"
        >
          상세보기
        </Link>
        <button
          ref={closeRef}
          type="button"
          onClick={onClose}
          aria-label="팝업 닫기"
          className="absolute top-2.5 right-2.5 flex size-6 items-center justify-center rounded-full text-[#99a1af] hover:bg-[#f3f4f6]"
        >
          <XIcon className="size-3.5" />
        </button>
      </div>
      {/* 말풍선 꼬리 — Figma 6:4688(16×8). */}
      <div className="size-0 border-x-8 border-t-8 border-x-transparent border-t-white drop-shadow-[0_2px_2px_rgba(0,0,0,0.08)]" aria-hidden="true" />
    </div>
  );
}
