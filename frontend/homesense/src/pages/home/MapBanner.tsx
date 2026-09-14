import { Link } from 'react-router-dom';
import { ArrowRightIcon } from '../../components/icons/ArrowRightIcon';
import { MapFoldIcon } from '../../components/icons/MapFoldIcon';

/** HOME-01 구성요소 3 — 지도 진입 배너. MAP-01은 자리표시 화면이라 링크만 연결한다. */
export function MapBanner() {
  return (
    <Link
      to="/map"
      className="mx-auto flex max-w-[1280px] items-center justify-between border-b border-[#e5e7eb] bg-white px-4 py-3.5 md:px-8"
    >
      <div className="flex items-center gap-3">
        <div className="flex size-9 items-center justify-center rounded-full bg-[#e8f2f0] text-brand">
          <MapFoldIcon className="size-[17px]" />
        </div>
        <p className="text-[13.5px] font-semibold text-[#101828]">지도로 보기</p>
      </div>
      <div className="flex items-center gap-1.5 rounded-[10px] border border-brand px-4 py-1.5">
        <span className="text-[13px] font-semibold text-brand">지도 열기</span>
        <ArrowRightIcon className="size-3.5 text-brand" />
      </div>
    </Link>
  );
}
