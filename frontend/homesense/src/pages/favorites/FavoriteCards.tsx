import type { Ref } from 'react';
import { Link } from 'react-router-dom';
import { BellIcon } from '../../components/icons/BellIcon';
import { HomeIcon } from '../../components/icons/HomeIcon';
import { DataTrustBadge } from '../../components/ui/DataTrustBadge';
import type { FavoritePropertySummaryResponse, FavoriteRegionSummaryResponse } from '../../features/favorite/types';
import { formatAddress, formatArea, formatDottedDate, formatKoreanPrice } from '../../lib/format';
import { MY_ROUTES } from '../../routes/paths';
import { regionSearchHref, type NotificationBadge } from './favoritesModel';

/** 카드 공통 틀 — MY-01 카드와 같은 테두리·그림자(SectionCard CARD_CLASS)에 radius 16. */
const CARD_CLASS =
  'relative rounded-[16px] border border-[#f3f4f6] bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,0.05)] hover:border-[#d1eae6] md:p-5';

/**
 * 카드 전체를 누르면 이동하는 링크. 링크 안에 버튼을 넣지 않으려고(D9) 제목 링크의 ::after가 카드 전체를 덮고,
 * 삭제 버튼·알림 배지는 그 위 레이어(relative z-10)에 둔다. 키보드 포커스 링도 ::after에 그린다.
 */
const STRETCHED_LINK_CLASS =
  'outline-none after:absolute after:inset-0 after:rounded-[16px] after:content-[""] focus-visible:after:ring-2 focus-visible:after:ring-brand';

const DELETE_BUTTON_CLASS =
  'relative z-10 shrink-0 rounded-[10px] border border-[#e5e7eb] bg-white px-3 py-1.5 text-[12.5px] font-semibold text-[#4a5565] hover:border-[#fecaca] hover:bg-[#fef2f2] hover:text-[#c10007] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand';

/** "2026-09-01T10:00:00" → "2026.09.01" */
function formatRegisteredDate(registeredAt: string): string {
  return formatDottedDate(registeredAt.slice(0, 10));
}

/**
 * 변동률(D10). 계산은 최근 1개월 매매 평균과 그 직전 1개월 매매 평균의 비교라(RegionStatsCalculator·FavoriteService) 라벨을
 * "전월 대비"가 아니라 "직전 1개월 대비"로 쓴다 — 달력상 지난달과의 비교로 읽히지 않게(HOME-01이 "이번 달" 대신 "최근 1개월"을
 * 쓴 것과 같은 이유). 0 → "변동없음", null → "—". 스크린리더에는 기호 대신 문장으로 읽힌다.
 */
export function ChangeRate({ value }: { value: number | null | undefined }) {
  if (value == null) {
    return (
      <span className="text-[12.5px] text-[#99a1af]">
        <span aria-hidden="true">—</span>
        <span className="sr-only">직전 1개월 대비 변동 정보 없음</span>
      </span>
    );
  }
  const rounded = Math.abs(value).toFixed(1);
  if (Number(rounded) === 0) {
    return <span className="text-[12.5px] font-semibold text-[#6a7282]">변동없음</span>;
  }
  const up = value > 0;
  return (
    <span className={`text-[12.5px] font-bold ${up ? 'text-[#e7000b]' : 'text-[#155dfc]'}`}>
      <span aria-hidden="true">
        <span className="mr-1 font-medium text-[#99a1af]">직전 1개월</span>
        {up ? '▲' : '▼'} {rounded}%
      </span>
      <span className="sr-only">
        직전 1개월 대비 {rounded}% {up ? '상승' : '하락'}
      </span>
    </span>
  );
}

const BADGE_TONE_CLASS: Record<NotificationBadge['tone'], string> = {
  unset: 'border border-dashed border-[#b8d9d4] bg-white text-brand',
  off: 'border border-[#e5e7eb] bg-[#f3f4f6] text-[#6a7282]',
  on: 'border border-[#d1eae6] bg-[#e8f2f0] text-brand',
};

/** 알림조건 배지 — MY-03으로 이동한다. 사전 선택 대상은 URL 계약 `?favoritePropertyId={id}`(CLAUDE.md SCR-MY-02 절). */
function NotificationBadgeLink({ favoritePropertyId, badge, complexName }: { favoritePropertyId: number; badge: NotificationBadge; complexName: string }) {
  return (
    <Link
      to={`${MY_ROUTES.notificationSettings}?favoritePropertyId=${favoritePropertyId}`}
      aria-label={`${complexName} 알림 조건: ${badge.label}`}
      className={`relative z-10 inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11.5px] font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand ${BADGE_TONE_CLASS[badge.tone]}`}
    >
      <BellIcon aria-hidden="true" className="size-3" />
      {badge.label}
    </Link>
  );
}

interface PropertyCardProps {
  favorite: FavoritePropertySummaryResponse;
  badge: NotificationBadge;
  onDelete: () => void;
  linkRef?: Ref<HTMLAnchorElement>;
}

/**
 * MY-02 관심 매물 카드(D2) — 화면 크기마다 Figma 카드 구성이 달라 그 합집합을 한 컴포넌트에 담는다: 주택유형 배지,
 * 단지명, 주소, 최근 매매의 면적·층, 최근 거래가, 변동률, 등록일, 알림조건 배지, 삭제. 썸네일은 데이터가 없어
 * UIC-05와 같은 자리표시이고 모바일에서는 숨긴다(요소 하나의 표시 여부라 두 벌 렌더가 아니다).
 */
export function PropertyCard({ favorite, badge, onDelete, linkRef }: PropertyCardProps) {
  const address = formatAddress(favorite.sigungu, favorite.dongRi);
  const tradeMeta = [
    favorite.recentArea != null ? `전용 ${formatArea(favorite.recentArea)}` : null,
    favorite.recentFloor != null ? `${favorite.recentFloor}층` : null,
    favorite.recentDealDate != null ? `${formatDottedDate(favorite.recentDealDate)} 거래` : null,
  ].filter(Boolean);

  return (
    <li className={CARD_CLASS} data-favorite-key={`property:${favorite.favoritePropertyId}`}>
      <div className="flex gap-3.5">
        <div
          aria-hidden="true"
          className="hidden h-[46px] w-[60px] shrink-0 items-center justify-center rounded-[14px] bg-[#f3f4f6] text-[#c4c9d1] md:flex"
        >
          <HomeIcon className="size-5" />
        </div>
        <div className="min-w-0 flex-1">
          <DataTrustBadge housingType={favorite.housingType} />
          <h3 className="mt-1.5 truncate text-[15px] leading-[22px] font-bold text-[#101828]">
            <Link ref={linkRef} to={`/complexes/${favorite.complexId}`} className={STRETCHED_LINK_CLASS}>
              {favorite.complexName}
            </Link>
          </h3>
          {address && <p className="truncate text-[12.5px] leading-[18px] text-[#6a7282]">{address}</p>}
          {tradeMeta.length > 0 && <p className="mt-0.5 text-[12px] leading-[18px] text-[#99a1af]">{tradeMeta.join(' · ')}</p>}
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1 text-right">
          <span className="text-[11px] text-[#99a1af]">최근 매매가</span>
          <span className="text-[16px] leading-[22px] font-extrabold text-[#101828] md:text-[17px]">
            {favorite.recentAmount != null ? formatKoreanPrice(favorite.recentAmount) : '거래 없음'}
          </span>
          <ChangeRate value={favorite.changeRate} />
        </div>
      </div>
      <div className="mt-3 flex items-center justify-between gap-2 border-t border-[#f3f4f6] pt-3">
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <NotificationBadgeLink favoritePropertyId={favorite.favoritePropertyId} badge={badge} complexName={favorite.complexName} />
          <span className="text-[11.5px] text-[#99a1af]">등록 {formatRegisteredDate(favorite.registeredAt)}</span>
        </div>
        <button type="button" onClick={onDelete} aria-label={`${favorite.complexName} 관심 매물 삭제`} className={DELETE_BUTTON_CLASS}>
          삭제
        </button>
      </div>
    </li>
  );
}

interface RegionCardProps {
  region: FavoriteRegionSummaryResponse;
  onDelete: () => void;
  linkRef?: Ref<HTMLAnchorElement>;
}

/**
 * MY-02 관심 지역 카드 — 읍면동명, 그 위 시도·시군구(같은 이름의 동을 구분하려고 넣었다 — Figma에는 읍면동명만 있다),
 * 3.3㎡당 평균가, 최근 1개월 신규거래 건수, 변동률, 삭제. 알림조건 배지는 명세·Figma 모두 없다.
 */
export function RegionCard({ region, onDelete, linkRef }: RegionCardProps) {
  const parent = [region.sidoName, region.sigunguName].filter(Boolean).join(' ');
  return (
    <li className={CARD_CLASS} data-favorite-key={`region:${region.favoriteRegionId}`}>
      <div className="flex gap-3.5">
        <div className="min-w-0 flex-1">
          {parent && <p className="truncate text-[12px] leading-[18px] text-[#6a7282]">{parent}</p>}
          <h3 className="truncate text-[15px] leading-[22px] font-bold text-[#101828]">
            <Link ref={linkRef} to={regionSearchHref(region)} aria-label={`${region.fullPath} 매물 검색`} className={STRETCHED_LINK_CLASS}>
              {region.eupmyeondongName}
            </Link>
          </h3>
          <p className="mt-0.5 text-[12px] leading-[18px] text-[#99a1af]">최근 1개월 신규거래 {region.newTradeCount.toLocaleString('ko-KR')}건</p>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1 text-right">
          <span className="text-[11px] text-[#99a1af]">3.3㎡당 평균가</span>
          <span className="text-[16px] leading-[22px] font-extrabold text-[#101828] md:text-[17px]">
            {region.pricePerPyeong != null ? formatKoreanPrice(Math.round(region.pricePerPyeong)) : '거래 없음'}
          </span>
          <ChangeRate value={region.changeRate} />
        </div>
      </div>
      <div className="mt-3 flex items-center justify-between gap-2 border-t border-[#f3f4f6] pt-3">
        <span className="text-[11.5px] text-[#99a1af]">등록 {formatRegisteredDate(region.registeredAt)}</span>
        <button type="button" onClick={onDelete} aria-label={`${region.eupmyeondongName} 관심 지역 삭제`} className={DELETE_BUTTON_CLASS}>
          삭제
        </button>
      </div>
    </li>
  );
}
