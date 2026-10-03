import type { ReactNode, Ref } from 'react';
import { Link } from 'react-router-dom';
import { BellIcon } from '../../components/icons/BellIcon';
import { HomeIcon } from '../../components/icons/HomeIcon';
import { MapPinIcon } from '../../components/icons/MapPinIcon';
import { TrashSmallIcon } from '../../components/icons/TrashSmallIcon';
import { DataTrustBadge } from '../../components/ui/DataTrustBadge';
import type { HousingType } from '../../features/complex/types';
import type { FavoritePropertySummaryResponse, FavoriteRegionSummaryResponse } from '../../features/favorite/types';
import { formatArea, formatDottedDate, formatKoreanPrice } from '../../lib/format';
import { MY_ROUTES } from '../../routes/paths';
import { regionSearchHref, type FavoritesLayout, type NotificationBadge } from './favoritesModel';

/*
 * MY-02 카드 — Figma 세 프레임(데스크톱 6-4695, 태블릿 28-16161, 모바일 27-15395)의 구성이 서로 달라 화면이 넘겨 준
 * layout으로 한 벌만 그린다. 데스크톱은 카드 하나 안의 행(구분선), 태블릿은 행마다 카드, 모바일은 흰 목록의 행(구분선).
 * 카드 전체가 링크다 — 링크 안에 버튼을 넣지 않으려고 제목 링크의 ::after가 행 전체를 덮고, 삭제 버튼·알림 배지는 그
 * 위 레이어(relative z-10)에 둔다.
 */

/** 행 전체를 덮는 링크. 태블릿은 카드가 둥글어 포커스 링도 같은 radius로 그린다. */
function stretchedLinkClass(layout: FavoritesLayout): string {
  return `outline-none after:absolute after:inset-0 after:content-[""] focus-visible:after:ring-2 focus-visible:after:ring-inset focus-visible:after:ring-brand ${
    layout === 'tablet' ? 'after:rounded-[16px]' : ''
  }`;
}

const ROW_CLASS: Record<FavoritesLayout, string> = {
  desktop: 'relative flex items-center gap-4 px-5 py-4 hover:bg-[#f9fafb]',
  tablet: 'relative flex gap-4 rounded-[16px] border border-[#f3f4f6] bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,0.06)] hover:border-[#d1eae6]',
  mobile: 'relative flex gap-3 bg-white p-4',
};

const HOUSING_TYPE_LABEL: Record<HousingType, string> = { APT: '아파트', VILLA: '연립다세대' };

// 상승·하락 색은 HOME-01 관심 지역 카드와 같은 값을 쓴다(Figma는 데스크톱 #ef4444·태블릿 #fb2c36·하락 #2b7fff로 화면마다
// 달랐다 — 같은 뜻의 색을 하나로 맞췄다).
const RATE_UP = 'text-[#e7000b]';
const RATE_DOWN = 'text-[#155dfc]';
const RATE_LABEL = '직전 1개월 대비';

/** "2026-09-01T10:00:00" → "2026.09.01" */
function formatRegisteredDate(registeredAt: string): string {
  return formatDottedDate(registeredAt.slice(0, 10));
}

function areaFloorText(favorite: FavoritePropertySummaryResponse): string {
  return [
    favorite.recentArea != null ? formatArea(favorite.recentArea) : null,
    favorite.recentFloor != null ? `${favorite.recentFloor}층` : null,
  ]
    .filter(Boolean)
    .join(' · ');
}

/**
 * 변동률 값(D10). 계산은 최근 1개월 매매 평균과 그 직전 1개월 매매 평균의 비교라 라벨은 "전월 대비"가 아니라
 * "직전 1개월 대비"다(라벨은 호출부가 aria-hidden으로 그린다). 0 → "변동없음", 없음 → "—". 스크린리더에는 라벨을 포함한
 * 문장 하나로 읽힌다.
 */
export function RateValue({ value, className }: { value: number | null | undefined; className: string }) {
  if (value == null) {
    return (
      <span className={`${className} text-[#99a1af]`}>
        <span aria-hidden="true">—</span>
        <span className="sr-only">{RATE_LABEL} 변동 정보 없음</span>
      </span>
    );
  }
  const rounded = Math.abs(value).toFixed(1);
  if (Number(rounded) === 0) {
    return (
      <span className={`${className} text-[#6a7282]`}>
        <span aria-hidden="true">변동없음</span>
        <span className="sr-only">{RATE_LABEL} 변동 없음</span>
      </span>
    );
  }
  const up = value > 0;
  return (
    <span className={`${className} ${up ? RATE_UP : RATE_DOWN}`}>
      <span aria-hidden="true">
        {up ? '▲' : '▼'} {rounded}%
      </span>
      <span className="sr-only">
        {RATE_LABEL} {rounded}% {up ? '상승' : '하락'}
      </span>
    </span>
  );
}

/** 썸네일 자리표시 — 단지 사진 데이터가 없다. Figma 크기: 데스크톱 60×46, 태블릿 90×70, 모바일 64×64(모두 radius 14). */
function Thumbnail({ layout }: { layout: FavoritesLayout }) {
  const size = { desktop: 'h-[46px] w-[60px]', tablet: 'h-[70px] w-[90px]', mobile: 'size-16' }[layout];
  return (
    <div
      aria-hidden="true"
      data-testid="favorite-thumbnail"
      className={`flex shrink-0 items-center justify-center rounded-[14px] bg-[#f3f4f6] text-[#c4c9d1] ${size}`}
    >
      <HomeIcon className={layout === 'tablet' ? 'size-6' : 'size-5'} />
    </div>
  );
}

/** 관심 지역 아이콘 — 36px, #e8f2f0, radius 14, 핀 16px(Figma 6-4695). */
function PinBox() {
  return (
    <div aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-[14px] bg-[#e8f2f0] text-brand">
      <MapPinIcon className="size-4" />
    </div>
  );
}

const BADGE_TONE_CLASS: Record<NotificationBadge['tone'], string> = {
  // Figma에는 설정된 상태(브랜드색 바탕)만 있다. 미설정·꺼짐은 같은 모양에 색만 바꿨다.
  on: 'bg-brand text-white',
  unset: 'bg-white text-brand ring-1 ring-inset ring-brand',
  off: 'bg-[#f3f4f6] text-[#4a5565]',
};

/**
 * 알림조건 배지 — MY-03으로 이동한다(URL 계약 `?favoritePropertyId={id}`). Figma 6-4695: pill, 좌우 8px, 종 10px + 간격 2,
 * 10px 굵게. 모바일은 보이는 크기는 그대로 두고 터치 영역만 세로 44px로 넓힌다(UI정의서 6.3절).
 */
function AlertBadge({
  favoritePropertyId,
  badge,
  complexName,
  layout,
}: {
  favoritePropertyId: number;
  badge: NotificationBadge;
  complexName: string;
  layout: FavoritesLayout;
}) {
  return (
    <Link
      to={`${MY_ROUTES.notificationSettings}?favoritePropertyId=${favoritePropertyId}`}
      aria-label={`${complexName} 알림 조건: ${badge.label}`}
      className={`relative z-10 inline-flex shrink-0 items-center gap-0.5 rounded-full px-2 py-0.5 text-[10px] leading-[15px] font-bold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand ${
        layout === 'mobile' ? 'before:absolute before:-inset-y-[15px] before:inset-x-0 before:content-[""]' : ''
      } ${BADGE_TONE_CLASS[badge.tone]}`}
    >
      <BellIcon aria-hidden="true" className="size-2.5" />
      {badge.label}
    </Link>
  );
}

/**
 * 삭제 버튼(D3) — 데스크톱·태블릿은 테두리 텍스트 버튼(태블릿 Figma의 "상세보기" 자리·모양, radius 10, 6/12).
 * 모바일은 휴지통 12px + "삭제" 10px(Figma 27-15395). 글자색은 Figma #d1d5dc(흰 바탕 약 1.5:1) 대신 #6a7282(약 4.8:1),
 * 터치 영역은 ::before로 44px 이상(보이는 크기는 그대로).
 */
function DeleteButton({ layout, label, onClick }: { layout: FavoritesLayout; label: string; onClick: () => void }) {
  if (layout === 'mobile') {
    return (
      <button
        type="button"
        onClick={onClick}
        aria-label={label}
        className="relative z-10 flex shrink-0 items-center gap-1 text-[10px] leading-[15px] font-medium text-[#6a7282] before:absolute before:-inset-x-2 before:-inset-y-[15px] before:content-[''] hover:text-[#c10007] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
      >
        <TrashSmallIcon aria-hidden="true" className="size-3" />
        삭제
      </button>
    );
  }
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className="relative z-10 shrink-0 rounded-[10px] border border-[#e5e7eb] bg-white px-3 py-1.5 text-[12px] leading-[18px] font-semibold text-[#4a5565] hover:border-[#fecaca] hover:bg-[#fef2f2] hover:text-[#c10007] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
    >
      삭제
    </button>
  );
}

/** 데스크톱 오른쪽 지표 — 값 위, 라벨 아래, 오른쪽 정렬(Figma 6-4695). 행마다 열이 맞도록 너비를 고정한다. */
function DesktopMetric({ value, label, width }: { value: ReactNode; label: string; width: string }) {
  return (
    <div className={`flex shrink-0 flex-col items-end text-right ${width}`}>
      {value}
      <span aria-hidden={label === RATE_LABEL ? 'true' : undefined} className="text-[11px] leading-[16.5px] text-[#99a1af]">
        {label}
      </span>
    </div>
  );
}

/** 태블릿 지표 — 라벨 10px 위, 값 아래(Figma 28-16161). 지표 사이에는 1×32 구분선을 둔다(호출부). */
function TabletMetric({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col">
      <span aria-hidden={label === RATE_LABEL ? 'true' : undefined} className="text-[10px] leading-[15px] text-[#99a1af]">
        {label}
      </span>
      {children}
    </div>
  );
}

function MetricDivider() {
  return <span aria-hidden="true" className="h-8 w-px shrink-0 bg-[#f3f4f6]" />;
}

/**
 * 모바일 지표 — 라벨 열과 값 열(Figma 27-15395). Figma는 라벨 열을 60px로 고정했지만 "직전 1개월 대비"(11px)가 들어가지
 * 않아, 가장 긴 라벨에 맞춘 열(grid auto)로 두 줄의 값 시작 위치를 맞춘다.
 */
function MobileMetrics({ rows }: { rows: { label: string; value: ReactNode }[] }) {
  return (
    <div className="mt-2 grid grid-cols-[auto_1fr] items-baseline gap-x-2 gap-y-0.5">
      {rows.map((row) => (
        <MobileMetricRow key={row.label} label={row.label} value={row.value} />
      ))}
    </div>
  );
}

function MobileMetricRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <>
      <span aria-hidden={label === RATE_LABEL ? 'true' : undefined} className="text-[11px] leading-[16.5px] whitespace-nowrap text-[#99a1af]">
        {label}
      </span>
      <span className="min-w-0">{value}</span>
    </>
  );
}

interface PropertyCardProps {
  favorite: FavoritePropertySummaryResponse;
  /** null이면 배지를 숨긴다(알림 설정 목록을 아직 받지 못했거나 조회 실패). */
  badge: NotificationBadge | null;
  layout: FavoritesLayout;
  onDelete: () => void;
  linkRef?: Ref<HTMLAnchorElement>;
}

/**
 * 관심 매물 카드(D2) — 화면 크기별 Figma 카드의 합집합을 담는다: 썸네일, 주택유형, 단지명, 최근 매매의 면적·층,
 * 최근 매매가, 변동률, 등록일, 알림조건 배지(명세 필수 — Figma에는 데스크톱에만 있다), 삭제(D3).
 */
export function PropertyCard({ favorite, badge, layout, onDelete, linkRef }: PropertyCardProps) {
  const areaFloor = areaFloorText(favorite);
  const registered = formatRegisteredDate(favorite.registeredAt);
  const price = favorite.recentAmount != null ? formatKoreanPrice(favorite.recentAmount) : '거래 없음';
  const deleteLabel = `${favorite.complexName} 관심 매물 삭제`;
  const title = (
    <Link ref={linkRef} to={`/complexes/${favorite.complexId}`} className={stretchedLinkClass(layout)}>
      {favorite.complexName}
    </Link>
  );
  const alertBadge = badge && (
    <AlertBadge favoritePropertyId={favorite.favoritePropertyId} badge={badge} complexName={favorite.complexName} layout={layout} />
  );

  if (layout === 'desktop') {
    const meta = [HOUSING_TYPE_LABEL[favorite.housingType], areaFloor, `등록일 ${registered}`].filter(Boolean).join(' · ');
    return (
      <li className={ROW_CLASS.desktop} data-favorite-key={`property:${favorite.favoritePropertyId}`}>
        <Thumbnail layout="desktop" />
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-2">
            <h3 className="truncate text-[14px] leading-[21px] font-bold text-[#101828]">{title}</h3>
            {alertBadge}
          </div>
          <p className="mt-0.5 truncate text-[12px] leading-[18px] text-[#99a1af]">{meta}</p>
        </div>
        <DesktopMetric
          width="w-[124px]"
          label="최근 매매가"
          value={<span className="text-[15px] leading-[22.5px] font-extrabold text-[#1c1c1e]">{price}</span>}
        />
        <DesktopMetric width="w-[96px]" label={RATE_LABEL} value={<RateValue value={favorite.changeRate} className="text-[13px] leading-[19.5px] font-bold" />} />
        <DeleteButton layout="desktop" label={deleteLabel} onClick={onDelete} />
      </li>
    );
  }

  if (layout === 'tablet') {
    return (
      <li className={ROW_CLASS.tablet} data-favorite-key={`property:${favorite.favoritePropertyId}`}>
        <Thumbnail layout="tablet" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <DataTrustBadge housingType={favorite.housingType} />
            {alertBadge}
          </div>
          <h3 className="mt-1 truncate text-[15px] leading-[22px] font-bold text-[#101828]">{title}</h3>
          {areaFloor && <p className="mt-0.5 text-[12px] leading-[18px] text-[#99a1af]">{areaFloor}</p>}
          <div className="mt-3 flex items-center gap-4">
            <TabletMetric label="최근 매매가">
              <span className="text-[16px] leading-[24px] font-extrabold whitespace-nowrap text-[#1c1c1e]">{price}</span>
            </TabletMetric>
            <MetricDivider />
            <TabletMetric label={RATE_LABEL}>
              <RateValue value={favorite.changeRate} className="text-[11px] leading-[16.5px] font-bold whitespace-nowrap" />
            </TabletMetric>
            <MetricDivider />
            <TabletMetric label="등록일">
              <span className="text-[12px] leading-[18px] font-medium text-[#4a5565]">{registered}</span>
            </TabletMetric>
          </div>
        </div>
        <div className="shrink-0 self-start">
          <DeleteButton layout="tablet" label={deleteLabel} onClick={onDelete} />
        </div>
      </li>
    );
  }

  return (
    <li className={ROW_CLASS.mobile} data-favorite-key={`property:${favorite.favoritePropertyId}`}>
      <Thumbnail layout="mobile" />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1">
          <DataTrustBadge housingType={favorite.housingType} />
          {areaFloor && <span className="text-[10px] leading-[15px] text-[#99a1af]">{areaFloor}</span>}
        </div>
        <h3 className="mt-0.5 truncate text-[13.5px] leading-[20px] font-bold text-[#101828]">{title}</h3>
        <MobileMetrics
          rows={[
            { label: '최근 매매가', value: <span className="text-[13px] leading-[19.5px] font-extrabold text-[#101828]">{price}</span> },
            { label: RATE_LABEL, value: <RateValue value={favorite.changeRate} className="text-[11px] leading-[16.5px] font-bold" /> },
          ]}
        />
        <div className="mt-2 flex items-center justify-between gap-4">
          <span className="text-[10px] leading-[15px] text-[#99a1af]">등록일 {registered}</span>
          <div className="flex items-center gap-4">
            {alertBadge}
            <DeleteButton layout="mobile" label={deleteLabel} onClick={onDelete} />
          </div>
        </div>
      </div>
    </li>
  );
}

interface RegionCardProps {
  region: FavoriteRegionSummaryResponse;
  layout: FavoritesLayout;
  onDelete: () => void;
  linkRef?: Ref<HTMLAnchorElement>;
}

/**
 * 관심 지역 카드 — 핀 아이콘, 읍면동명과 시도·시군구(같은 이름의 동을 구분하려고 넣었다 — Figma에는 읍면동명만 있다),
 * 3.3㎡당 평균가, 최근 1개월 신규거래 건수, 변동률, 삭제. 알림조건 배지는 명세·Figma 모두 없다.
 */
export function RegionCard({ region, layout, onDelete, linkRef }: RegionCardProps) {
  const parent = [region.sidoName, region.sigunguName].filter(Boolean).join(' ');
  const perPyeong = region.pricePerPyeong != null ? formatKoreanPrice(Math.round(region.pricePerPyeong)) : '거래 없음';
  const tradeCount = `${region.newTradeCount.toLocaleString('ko-KR')}건`;
  const deleteLabel = `${region.eupmyeondongName} 관심 지역 삭제`;
  const title = (
    <Link ref={linkRef} to={regionSearchHref(region)} aria-label={`${region.fullPath} 매물 검색`} className={stretchedLinkClass(layout)}>
      {region.eupmyeondongName}
    </Link>
  );

  if (layout === 'desktop') {
    return (
      <li className={ROW_CLASS.desktop} data-favorite-key={`region:${region.favoriteRegionId}`}>
        <PinBox />
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-baseline gap-2">
            <h3 className="shrink-0 text-[14px] leading-[21px] font-bold text-[#101828]">{title}</h3>
            {parent && <span className="truncate text-[12px] leading-[18px] text-[#99a1af]">{parent}</span>}
          </div>
          <p className="mt-0.5 truncate text-[13px] leading-[19.5px] text-[#6a7282]">
            3.3㎡당 평균가 <span className="font-bold text-[#364153]">{perPyeong}</span> · 최근 1개월 신규거래{' '}
            <span className="font-bold text-[#364153]">{tradeCount}</span>
          </p>
        </div>
        <DesktopMetric width="w-[96px]" label={RATE_LABEL} value={<RateValue value={region.changeRate} className="text-[13px] leading-[19.5px] font-bold" />} />
        <DeleteButton layout="desktop" label={deleteLabel} onClick={onDelete} />
      </li>
    );
  }

  if (layout === 'tablet') {
    return (
      <li className={ROW_CLASS.tablet} data-favorite-key={`region:${region.favoriteRegionId}`}>
        <PinBox />
        <div className="min-w-0 flex-1">
          {parent && <p className="truncate text-[12px] leading-[18px] text-[#99a1af]">{parent}</p>}
          <h3 className="truncate text-[15px] leading-[22px] font-bold text-[#101828]">{title}</h3>
          <div className="mt-3 flex items-center gap-4">
            <TabletMetric label="3.3㎡당 평균가">
              <span className="text-[16px] leading-[24px] font-extrabold whitespace-nowrap text-[#1c1c1e]">{perPyeong}</span>
            </TabletMetric>
            <MetricDivider />
            <TabletMetric label="최근 1개월 신규거래">
              <span className="text-[12px] leading-[18px] font-medium text-[#4a5565]">{tradeCount}</span>
            </TabletMetric>
            <MetricDivider />
            <TabletMetric label={RATE_LABEL}>
              <RateValue value={region.changeRate} className="text-[11px] leading-[16.5px] font-bold whitespace-nowrap" />
            </TabletMetric>
          </div>
        </div>
        <div className="shrink-0 self-start">
          <DeleteButton layout="tablet" label={deleteLabel} onClick={onDelete} />
        </div>
      </li>
    );
  }

  return (
    <li className={ROW_CLASS.mobile} data-favorite-key={`region:${region.favoriteRegionId}`}>
      <PinBox />
      <div className="min-w-0 flex-1">
        {parent && <p className="truncate text-[10px] leading-[15px] text-[#99a1af]">{parent}</p>}
        <h3 className="mt-0.5 truncate text-[13.5px] leading-[20px] font-bold text-[#101828]">{title}</h3>
        <MobileMetrics
          rows={[
            { label: '3.3㎡당 평균가', value: <span className="text-[13px] leading-[19.5px] font-extrabold text-[#101828]">{perPyeong}</span> },
            { label: '최근 1개월 신규거래', value: <span className="text-[11px] leading-[16.5px] font-bold text-[#364153]">{tradeCount}</span> },
            { label: RATE_LABEL, value: <RateValue value={region.changeRate} className="text-[11px] leading-[16.5px] font-bold" /> },
          ]}
        />
        <div className="mt-2 flex justify-end">
          <DeleteButton layout="mobile" label={deleteLabel} onClick={onDelete} />
        </div>
      </div>
    </li>
  );
}
