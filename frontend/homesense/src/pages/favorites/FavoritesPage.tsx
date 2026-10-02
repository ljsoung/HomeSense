import { useCallback, useEffect, useId, useMemo, useRef, useState, type KeyboardEvent, type ReactNode, type RefObject } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeftIcon } from '../../components/icons/ArrowLeftIcon';
import { ArrowRightIcon } from '../../components/icons/ArrowRightIcon';
import { ChevronDownIcon } from '../../components/icons/ChevronDownIcon';
import { FilterIcon } from '../../components/icons/FilterIcon';
import { HeartIcon } from '../../components/icons/HeartIcon';
import { TrashIcon } from '../../components/icons/TrashIcon';
import { MainLayout } from '../../components/layout/MainLayout';
import { MODAL_DANGER_BUTTON_CLASS, MODAL_SECONDARY_BUTTON_CLASS, Modal } from '../../components/ui/Modal';
import { useToast } from '../../components/ui/useToast';
import {
  addFavoriteRegion,
  getFavoriteProperties,
  getFavoriteRegions,
  removeFavoriteProperty,
  removeFavoriteRegion,
} from '../../features/favorite/api';
import type { FavoritePropertySummaryResponse, FavoriteRegionSummaryResponse } from '../../features/favorite/types';
import { getNotificationSettings } from '../../features/notification/api';
import type { NotificationSettingResponse } from '../../features/notification/types';
import type { RegionAutocompleteResponse } from '../../features/region/types';
import { MEDIA_MD_DOWN, MEDIA_XL_UP, useMediaQuery } from '../../lib/useMediaQuery';
import { MY_ROUTES } from '../../routes/paths';
import { RowSkeleton, WidgetError } from '../my/SectionCard';
import { useLoadable } from '../my/useLoadable';
import { PropertyCard, RegionCard } from './FavoriteCards';
import {
  SORT_QUERY_VALUE,
  TAB_QUERY_VALUE,
  parseSort,
  parseTab,
  resolveNotificationBadge,
  sortFavorites,
  type FavoritesLayout,
  type FavoritesSort,
  type FavoritesTab,
} from './favoritesModel';
import { RegionAdder } from './RegionAdder';
import { usePendingDeletion } from './usePendingDeletion';

const SORT_LABEL: Record<FavoritesSort, { chip: string; select: string }> = {
  registered: { chip: '등록순', select: '최근 등록순' },
  rate: { chip: '변동률순', select: '변동률순' },
};
const SORTS: FavoritesSort[] = ['registered', 'rate'];
const TABS: { key: FavoritesTab; label: string }[] = [
  { key: 'properties', label: '관심 매물' },
  { key: 'regions', label: '관심 지역' },
];

// useLoadable은 렌더마다 바뀌지 않는 fetcher를 받는다 — 모듈 수준 함수로 둔다.
const loadProperties = (): Promise<FavoritePropertySummaryResponse[]> => getFavoriteProperties();
const loadRegions = (): Promise<FavoriteRegionSummaryResponse[]> => getFavoriteRegions();
const loadSettings = (): Promise<NotificationSettingResponse[]> => getNotificationSettings();

const propertyKey = (id: number) => `property:${id}`;
const regionKey = (id: number) => `region:${id}`;

interface DeleteTarget {
  key: string;
  name: string;
  kind: 'property' | 'region';
  id: number;
}

/** 삭제 뒤 포커스를 옮길 곳 — 다음 항목의 제목 링크, 없으면 섹션 제목. */
type FocusRequest = { kind: 'item'; key: string } | { kind: 'heading'; section: FavoritesTab };

/**
 * SCR-MY-02 관심 매물·지역 관리(UI정의서 v2.1 5.5절, FR-5.1~5.3). 보호 라우트(RequireAuth) 안에서만 그려진다.
 * - 1280px 이상은 관심 매물 → 관심 지역 → 관심 지역 추가를 세로로 쌓고 정렬은 chip, 그 아래는 "관심 매물 / 관심 지역" 탭과
 *   정렬 드롭다운(D1). 화면 크기별로 트리를 두 벌 렌더하지 않고 useMediaQuery로 한 벌만 그린다(모바일·태블릿·데스크톱 세
 *   배치, Figma 수치는 CLAUDE.md SCR-MY-02 절의 Figma 대조 표).
 * - 정렬은 화면에서 한다. 하나의 정렬이 두 목록에 같이 적용되고, 정렬·탭은 URL(`?sort=rate`, `?tab=regions`)에 replace로
 *   남겨 단지 상세·검색 결과에서 돌아오면 그대로다(D5).
 * - 삭제는 확인 → 숨김 → 5초 뒤 DELETE, 실행취소 가능(D6, usePendingDeletion).
 * - 관심 매물 목록, 관심 지역 목록, 알림 설정(배지 문구용)을 따로 불러 한 쪽 실패가 다른 쪽을 막지 않는다. 알림 설정을
 *   아직 받지 못했거나 조회가 실패하면 배지만 숨긴다(설정이 있는 사용자에게 "알림 설정"을 보이지 않게).
 */
export function FavoritesPage() {
  const isDesktop = useMediaQuery(MEDIA_XL_UP);
  const isMobile = useMediaQuery(MEDIA_MD_DOWN);
  const layout: FavoritesLayout = isDesktop ? 'desktop' : isMobile ? 'mobile' : 'tablet';
  const [searchParams, setSearchParams] = useSearchParams();
  const sort = parseSort(searchParams.get('sort'));
  const tab = parseTab(searchParams.get('tab'));
  const { showToast } = useToast();

  const properties = useLoadable(loadProperties);
  const regions = useLoadable(loadRegions);
  const settings = useLoadable(loadSettings);
  const { hiddenKeys, schedule, flush } = usePendingDeletion();

  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  // 다음 커밋 뒤 옮길 포커스. 렌더에 쓰이지 않아 state가 아니라 ref로 둔다.
  const focusRequestRef = useRef<FocusRequest | null>(null);
  const linkRefs = useRef(new Map<string, HTMLAnchorElement>());
  const propertiesHeadingRef = useRef<HTMLHeadingElement>(null);
  const regionsHeadingRef = useRef<HTMLHeadingElement>(null);

  const updateQuery = useCallback(
    (key: 'sort' | 'tab', value: string | null) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (value === null) next.delete(key);
          else next.set(key, value);
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  const visibleProperties = useMemo(() => {
    if (properties.state.status !== 'success') return [];
    const shown = properties.state.data.filter((item) => !hiddenKeys.has(propertyKey(item.favoritePropertyId)));
    return sortFavorites(shown, sort, (item) => ({
      id: item.favoritePropertyId,
      registeredAt: item.registeredAt,
      changeRate: item.changeRate ?? null,
    }));
  }, [properties.state, hiddenKeys, sort]);

  const visibleRegions = useMemo(() => {
    if (regions.state.status !== 'success') return [];
    const shown = regions.state.data.filter((item) => !hiddenKeys.has(regionKey(item.favoriteRegionId)));
    return sortFavorites(shown, sort, (item) => ({
      id: item.favoriteRegionId,
      registeredAt: item.registeredAt,
      changeRate: item.changeRate ?? null,
    }));
  }, [regions.state, hiddenKeys, sort]);

  // 알림 설정 목록을 아직 받지 못했거나 조회가 실패하면 null — 배지를 숨긴다(resolveNotificationBadge).
  const settingsByProperty = useMemo(() => {
    if (settings.state.status !== 'success') return null;
    const map = new Map<number, NotificationSettingResponse>();
    for (const setting of settings.state.data) {
      if (setting.favoritePropertyId != null) map.set(setting.favoritePropertyId, setting);
    }
    return map;
  }, [settings.state]);

  // 숨김(삭제 대기) 항목도 아직 서버에 있으니 중복 판정에 넣지 않는다 — 추가 전에 대기 삭제를 먼저 확정한다(handleAddRegion).
  const registeredCodes = useMemo(
    () => new Set(visibleRegions.map((region) => region.legalDongCd)),
    [visibleRegions],
  );

  // 삭제 확정·실행취소 뒤 포커스 이동. 대화상자가 닫히며 원래 버튼으로 돌려주려던 포커스는 그 버튼이 사라져 아무 데도
  // 가지 않으므로, 대화상자의 정리(cleanup)가 끝난 뒤 이 effect가 다음 항목(또는 섹션 제목)으로 옮긴다.
  useEffect(() => {
    const request = focusRequestRef.current;
    if (!request) return;
    focusRequestRef.current = null;
    if (request.kind === 'item') {
      linkRefs.current.get(request.key)?.focus();
    } else {
      (request.section === 'properties' ? propertiesHeadingRef : regionsHeadingRef).current?.focus();
    }
  });

  const setLinkRef = (key: string) => (element: HTMLAnchorElement | null) => {
    if (element) linkRefs.current.set(key, element);
    else linkRefs.current.delete(key);
  };

  const nextFocusAfterRemoving = (key: string, section: FavoritesTab): FocusRequest => {
    const keys =
      section === 'properties'
        ? visibleProperties.map((item) => propertyKey(item.favoritePropertyId))
        : visibleRegions.map((item) => regionKey(item.favoriteRegionId));
    const index = keys.indexOf(key);
    const next = keys[index + 1] ?? keys[index - 1];
    return next ? { kind: 'item', key: next } : { kind: 'heading', section };
  };

  const confirmDelete = () => {
    const target = deleteTarget;
    if (!target) return;
    setDeleteTarget(null);
    const section: FavoritesTab = target.kind === 'property' ? 'properties' : 'regions';
    focusRequestRef.current = nextFocusAfterRemoving(target.key, section);
    schedule({
      key: target.key,
      commit: () => (target.kind === 'property' ? removeFavoriteProperty(target.id) : removeFavoriteRegion(target.id)),
      onCommitted: () => {
        if (target.kind === 'property') {
          properties.setData((prev) => prev.filter((item) => item.favoritePropertyId !== target.id));
        } else {
          regions.setData((prev) => prev.filter((item) => item.favoriteRegionId !== target.id));
        }
      },
      onRestored: () => {
        focusRequestRef.current = { kind: 'item', key: target.key };
      },
    });
  };

  // 추가 성공 → 정렬을 등록순으로 되돌리고 목록을 새로 받아 새 항목이 맨 위에 보이게 한다(명세 "목록 상단에 즉시 반영"을
  // 변동률순에서도 지키려고). 같은 지역이 삭제 대기 중이면 그 삭제를 먼저 확정한다 — 그러지 않으면 서버에 아직 남아 있어 409가 난다.
  const handleAddRegion = async (region: RegionAutocompleteResponse) => {
    await flush();
    await addFavoriteRegion(region.legalDongCd);
    updateQuery('sort', SORT_QUERY_VALUE.registered);
    try {
      regions.setData(await getFavoriteRegions());
    } catch {
      regions.retry();
    }
    showToast('관심 지역에 추가했어요', 'success');
  };

  const openDelete = (target: DeleteTarget) => setDeleteTarget(target);

  const propertiesLoaded = properties.state.status === 'success';
  const regionsLoaded = regions.state.status === 'success';
  const bothEmpty = propertiesLoaded && regionsLoaded && visibleProperties.length === 0 && visibleRegions.length === 0;

  const propertyCount = propertiesLoaded ? visibleProperties.length : null;
  const regionCount = regionsLoaded ? visibleRegions.length : null;

  const propertyList = (
    <ListArea
      layout={layout}
      state={properties.state}
      onRetry={properties.retry}
      emptyText="등록한 관심 매물이 없어요. 단지 상세에서 하트를 눌러 추가할 수 있어요."
      isEmpty={visibleProperties.length === 0}
    >
      {visibleProperties.map((favorite) => {
        const key = propertyKey(favorite.favoritePropertyId);
        return (
          <PropertyCard
            key={key}
            favorite={favorite}
            layout={layout}
            linkRef={setLinkRef(key)}
            badge={resolveNotificationBadge(settingsByProperty, favorite.favoritePropertyId)}
            onDelete={() => openDelete({ key, name: favorite.complexName, kind: 'property', id: favorite.favoritePropertyId })}
          />
        );
      })}
    </ListArea>
  );

  const regionList = (
    <ListArea
      layout={layout}
      state={regions.state}
      onRetry={regions.retry}
      emptyText="등록한 관심 지역이 없어요. 읍·면·동 이름으로 추가해 보세요."
      isEmpty={visibleRegions.length === 0}
    >
      {visibleRegions.map((region) => {
        const key = regionKey(region.favoriteRegionId);
        return (
          <RegionCard
            key={key}
            region={region}
            layout={layout}
            linkRef={setLinkRef(key)}
            onDelete={() => openDelete({ key, name: region.eupmyeondongName, kind: 'region', id: region.favoriteRegionId })}
          />
        );
      })}
    </ListArea>
  );

  const adder = <RegionAdder registeredCodes={registeredCodes} onAdd={handleAddRegion} />;
  // 제목이 보이는 추가 영역 — 데스크톱 세 번째 섹션, 그리고 두 목록이 모두 비었을 때(D8) 모든 크기.
  const adderSection = (
    <section aria-labelledby="favorites-adder-heading" className="flex flex-col gap-3">
      <h2 id="favorites-adder-heading" className="text-[15px] leading-[22.5px] font-bold text-[#101828]">
        관심 지역 추가
      </h2>
      {adder}
    </section>
  );
  const onSortChange = (next: FavoritesSort) => updateQuery('sort', SORT_QUERY_VALUE[next]);

  let content: ReactNode;
  if (layout === 'desktop') {
    content = (
      <div className="mx-auto flex max-w-[1000px] flex-col gap-7 px-8 py-12">
        <div className="flex items-end justify-between gap-4">
          <PageHeading layout="desktop" />
          <SortChips value={sort} onChange={onSortChange} />
        </div>
        {bothEmpty ? (
          <>
            <BothEmpty />
            {adderSection}
          </>
        ) : (
          <>
            <Section title="관심 매물" count={propertyCount} headingRef={propertiesHeadingRef}>
              {propertyList}
            </Section>
            <Section title="관심 지역" count={regionCount} headingRef={regionsHeadingRef}>
              {regionList}
            </Section>
            {adderSection}
          </>
        )}
      </div>
    );
  } else {
    const tabs = (
      <TabbedLists
        layout={layout}
        tab={tab}
        onChangeTab={(next) => updateQuery('tab', TAB_QUERY_VALUE[next])}
        sortControl={<SortSelect layout={layout} value={sort} onChange={onSortChange} />}
        counts={{ properties: propertyCount, regions: regionCount }}
        headingRefs={{ properties: propertiesHeadingRef, regions: regionsHeadingRef }}
        panels={{
          properties: propertyList,
          // 태블릿·모바일은 추가 입력을 관심 지역 탭 맨 위에 둔다 — 목록이 길어도 바로 쓸 수 있게(D7).
          regions:
            layout === 'tablet' ? (
              <div className="flex flex-col gap-4">
                {adder}
                {regionList}
              </div>
            ) : (
              <>
                <div className="border-b border-[#f3f4f6] bg-white px-4 pt-4 pb-1">{adder}</div>
                {regionList}
              </>
            ),
        }}
      />
    );
    content =
      layout === 'tablet' ? (
        <div className="mx-auto flex max-w-[1000px] flex-col px-6 py-8">
          <PageHeading layout="tablet" />
          {bothEmpty ? (
            <div className="mt-6 flex flex-col gap-7">
              <BothEmpty />
              {adderSection}
            </div>
          ) : (
            <div className="mt-6 overflow-hidden rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_2px_12px_rgba(0,0,0,0.06)]">
              {tabs}
            </div>
          )}
        </div>
      ) : (
        <div className="flex flex-col">
          <MobileAppBar />
          {bothEmpty ? (
            <div className="flex flex-col gap-6 px-4 py-6">
              <BothEmpty />
              {adderSection}
            </div>
          ) : (
            tabs
          )}
        </div>
      );
  }

  return (
    <MainLayout>
      {content}

      <Modal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        role="alertdialog"
        describedBy="favorites-delete-description"
        title="삭제하시겠어요?"
        icon={
          <div className="flex size-14 items-center justify-center rounded-full bg-[#fef2f2] text-[#fb2c36]">
            <TrashIcon aria-hidden="true" className="size-6" />
          </div>
        }
        footer={
          <div className="flex flex-col gap-2 md:flex-row-reverse md:gap-3">
            <button type="button" onClick={confirmDelete} className={MODAL_DANGER_BUTTON_CLASS}>
              삭제
            </button>
            <button type="button" onClick={() => setDeleteTarget(null)} className={MODAL_SECONDARY_BUTTON_CLASS}>
              취소
            </button>
          </div>
        }
      >
        <p id="favorites-delete-description" className="text-[14px] leading-[22.75px] text-[#4a5565]">
          {deleteTarget?.name}을(를) 삭제하면 해당 항목의 알림도 함께 중지됩니다.
        </p>
      </Modal>
    </MainLayout>
  );
}

const SUBTITLE = '등록한 매물과 지역의 시세 변동을 한눈에 확인하세요.';

/** 데스크톱 24/36, 태블릿 22/33 ExtraBold + 부제 13px(두 크기 모두 데스크톱 문구로 통일). 모바일은 MobileAppBar가 제목이다. */
function PageHeading({ layout }: { layout: 'desktop' | 'tablet' }) {
  return (
    <div>
      <h1
        className={`font-extrabold text-[#101828] ${
          layout === 'desktop' ? 'text-[24px] leading-[36px]' : 'text-[22px] leading-[33px]'
        }`}
      >
        관심 매물·지역 관리
      </h1>
      <p className="mt-1 text-[13px] leading-[19.5px] text-[#99a1af]">{SUBTITLE}</p>
    </div>
  );
}

/**
 * 모바일 앱 바(Figma 27-15395) — 48px, 흰색, 아래 구분선, 뒤로가기 32×32 + 제목 16px Bold. 공용 모바일 헤더(MainLayout)는
 * 그대로 두고 그 아래에 둔다(DTL-01·MY-01도 공용 헤더를 유지했다). Figma 오른쪽 종 아이콘은 넣지 않는다 — UI정의서에 헤더
 * 알림 진입점이 없어 HOME-01에서 뺀 것과 같은 판단(모바일 알림은 하단 탭 "마이").
 * 뒤로가기는 앱 안에서 들어왔으면 이전 화면, 링크로 바로 들어왔으면 마이페이지(DTL-01 뒤로가기와 같은 규칙).
 */
function MobileAppBar() {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <div className="flex h-12 items-center gap-1 border-b border-[#f3f4f6] bg-white px-4">
      <button
        type="button"
        aria-label="뒤로 가기"
        onClick={() => (location.key === 'default' ? navigate(MY_ROUTES.home) : navigate(-1))}
        className="-ml-1.5 flex size-8 items-center justify-center rounded-[8px] text-[#4a5565] hover:bg-[#f3f4f6] focus-visible:outline-2 focus-visible:outline-brand"
      >
        <ArrowLeftIcon aria-hidden="true" className="size-5" />
      </button>
      <h1 className="text-[16px] leading-6 font-bold text-[#101828]">관심 매물·지역</h1>
    </div>
  );
}

/**
 * 두 목록이 모두 빈 상태(Figma 6-5286) — 목록 카드와 같은 외곽, 안쪽 80/24, 가운데 정렬. 아이콘 상자 80px #e8f2f0 radius 24,
 * 제목 18px ExtraBold, 설명 14px, CTA는 2px 외곽선 버튼 + 화살표.
 */
function BothEmpty() {
  return (
    <div className="flex flex-col items-center rounded-[16px] border border-[#f3f4f6] bg-white px-6 py-20 text-center shadow-[0_1px_4px_rgba(0,0,0,0.05)]">
      <div aria-hidden="true" className="flex size-20 items-center justify-center rounded-[24px] bg-[#e8f2f0] text-brand">
        <HeartIcon className="size-9" />
      </div>
      <p className="mt-6 text-[18px] leading-[27px] font-extrabold text-[#101828]">아직 등록한 관심 매물·지역이 없어요</p>
      <p className="mt-2 text-[14px] leading-[22.75px] text-[#99a1af]">
        매물을 탐색하고 관심 목록에 추가하면 시세 변동 알림을 받을 수 있어요.
      </p>
      <Link
        to="/"
        className="mt-7 inline-flex items-center gap-2 rounded-[14px] border-2 border-brand px-6 py-3 text-[14px] leading-[21px] font-bold text-brand hover:bg-[#f0f9f7] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
      >
        매물 둘러보기
        <ArrowRightIcon aria-hidden="true" className="size-4" />
      </Link>
    </div>
  );
}

/** 데스크톱 섹션 — 제목 15px Bold + 개수 배지(pill, 11px Bold 흰색), 아래 12px 뒤 목록 카드(Figma 6-4695). */
function Section({
  title,
  count,
  headingRef,
  children,
}: {
  title: string;
  count: number | null;
  headingRef: RefObject<HTMLHeadingElement | null>;
  children: ReactNode;
}) {
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3">
      <h2
        id={headingId}
        ref={headingRef}
        tabIndex={-1}
        className="flex items-center gap-2 text-[15px] leading-[22.5px] font-bold text-[#101828] outline-none"
      >
        {title}
        {count !== null && (
          <span className="rounded-full bg-brand px-2 py-0.5 text-[11px] leading-[16.5px] font-bold text-white">{count}</span>
        )}
      </h2>
      {children}
    </section>
  );
}

/** 데스크톱 목록 카드 — 흰색, 테두리 #f3f4f6, radius 16, 그림자 0 1px 4px 5%, 행 사이 구분선. */
const DESKTOP_LIST_CARD_CLASS =
  'overflow-hidden rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_1px_4px_rgba(0,0,0,0.05)]';

function ListArea({
  layout,
  state,
  onRetry,
  emptyText,
  isEmpty,
  children,
}: {
  layout: FavoritesLayout;
  state: { status: 'loading' } | { status: 'success' } | { status: 'error'; message: string };
  onRetry: () => void;
  emptyText: string;
  isEmpty: boolean;
  children: ReactNode;
}) {
  // 상태 표시(로딩·오류·빈 목록)를 감싸는 틀 — 데스크톱은 목록 카드, 태블릿은 카드 한 장, 모바일은 흰 목록.
  const frame = (body: ReactNode) =>
    layout === 'desktop' ? (
      <div className={DESKTOP_LIST_CARD_CLASS}>{body}</div>
    ) : layout === 'tablet' ? (
      <div className="rounded-[16px] border border-[#f3f4f6] bg-white">{body}</div>
    ) : (
      <div className="bg-white">{body}</div>
    );

  if (state.status === 'loading') {
    return (
      <div aria-busy="true">
        <span className="sr-only">불러오는 중</span>
        {frame(
          <div className="divide-y divide-[#f3f4f6]">
            {[0, 1].map((index) => (
              <RowSkeleton key={index} thumbnail />
            ))}
          </div>,
        )}
      </div>
    );
  }
  if (state.status === 'error') {
    return frame(
      <div className="px-5 py-4">
        <WidgetError message={state.message} onRetry={onRetry} />
      </div>,
    );
  }
  if (isEmpty) {
    return frame(<p className="px-5 py-10 text-center text-[13px] leading-[19.5px] text-[#6a7282]">{emptyText}</p>);
  }
  if (layout === 'desktop') {
    return (
      <div className={DESKTOP_LIST_CARD_CLASS}>
        <ul className="divide-y divide-[#f3f4f6]">{children}</ul>
      </div>
    );
  }
  if (layout === 'tablet') return <ul className="flex flex-col gap-3">{children}</ul>;
  return <ul className="divide-y divide-[#f3f4f6] bg-white">{children}</ul>;
}

/** 데스크톱 정렬 chip(Figma 6-4695) — pill, 간격 8, 6/16, 13px SemiBold. 미선택은 흰 바탕·테두리 #e5e7eb·글자 #6a7282. */
function SortChips({ value, onChange }: { value: FavoritesSort; onChange: (next: FavoritesSort) => void }) {
  return (
    <div role="radiogroup" aria-label="정렬" className="flex shrink-0 gap-2">
      {SORTS.map((option) => (
        <button
          key={option}
          type="button"
          role="radio"
          aria-checked={value === option}
          onClick={() => onChange(option)}
          className={`rounded-full border px-4 py-1.5 text-[13px] leading-[19.5px] font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand ${
            value === option ? 'border-brand bg-brand text-white' : 'border-[#e5e7eb] bg-white text-[#6a7282] hover:bg-[#f7f8fa]'
          }`}
        >
          {SORT_LABEL[option].chip}
        </button>
      ))}
    </div>
  );
}

/**
 * 태블릿·모바일 정렬(Figma 28-16161·27-15395) — radius 14, 테두리 #e5e7eb, 6/12, 필터 아이콘 14 + 13px SemiBold + 화살표 14.
 * 네이티브 select에 아이콘만 겹쳐 그려 키보드·스크린리더 동작은 그대로 얻는다. 높이 태블릿 35, 모바일 34.
 */
function SortSelect({
  layout,
  value,
  onChange,
}: {
  layout: FavoritesLayout;
  value: FavoritesSort;
  onChange: (next: FavoritesSort) => void;
}) {
  return (
    <div className="relative inline-flex shrink-0 items-center self-center">
      <FilterIcon aria-hidden="true" className="pointer-events-none absolute left-3 size-3.5 text-[#6a7282]" />
      <select
        aria-label="정렬"
        value={value}
        onChange={(event) => onChange(event.target.value as FavoritesSort)}
        className={`appearance-none rounded-[14px] border border-[#e5e7eb] bg-white pr-8 pl-8 text-[13px] font-semibold text-[#364153] focus:border-brand focus:outline-none ${
          layout === 'tablet' ? 'h-[35px]' : 'h-[34px]'
        }`}
      >
        {SORTS.map((option) => (
          <option key={option} value={option}>
            {SORT_LABEL[option].select}
          </option>
        ))}
      </select>
      <ChevronDownIcon aria-hidden="true" className="pointer-events-none absolute right-3 size-3.5 text-[#99a1af]" />
    </div>
  );
}

/**
 * 태블릿·모바일 탭(Figma 28-16161·27-15395) — 선택 탭은 브랜드색 글자 + 밑줄, 미선택 #99a1af, 오른쪽에 정렬 드롭다운.
 * 태블릿: 바 높이 53·좌우 16, 탭 14/20·14px. 모바일: 흰 바 46·좌우 8, 탭 12/16·13px. 목록은 태블릿 안쪽 16(카드 간격 12),
 * 모바일은 흰 목록(구분선).
 */
function TabbedLists({
  layout,
  tab,
  onChangeTab,
  sortControl,
  counts,
  headingRefs,
  panels,
}: {
  layout: FavoritesLayout;
  tab: FavoritesTab;
  onChangeTab: (next: FavoritesTab) => void;
  sortControl: ReactNode;
  counts: Record<FavoritesTab, number | null>;
  headingRefs: Record<FavoritesTab, RefObject<HTMLHeadingElement | null>>;
  panels: Record<FavoritesTab, ReactNode>;
}) {
  const baseId = useId();
  const tabRefs = useRef(new Map<FavoritesTab, HTMLButtonElement>());
  const tablet = layout === 'tablet';

  // 화살표·Home·End로 탭 이동(WAI-ARIA 탭 패턴, 이동과 동시에 선택).
  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    const index = TABS.findIndex((item) => item.key === tab);
    let nextIndex: number | null = null;
    if (event.key === 'ArrowRight') nextIndex = (index + 1) % TABS.length;
    else if (event.key === 'ArrowLeft') nextIndex = (index - 1 + TABS.length) % TABS.length;
    else if (event.key === 'Home') nextIndex = 0;
    else if (event.key === 'End') nextIndex = TABS.length - 1;
    if (nextIndex === null) return;
    event.preventDefault();
    const next = TABS[nextIndex].key;
    onChangeTab(next);
    tabRefs.current.get(next)?.focus();
  };

  return (
    <div className="flex flex-col">
      <div
        className={`flex items-stretch justify-between gap-2 border-b border-[#f3f4f6] bg-white ${
          tablet ? 'h-[53px] px-4' : 'h-[46px] px-2'
        }`}
      >
        <div role="tablist" aria-label="관심 목록" className="flex items-stretch">
          {TABS.map((item) => {
            const selected = item.key === tab;
            const count = counts[item.key];
            return (
              <button
                key={item.key}
                ref={(element) => {
                  if (element) tabRefs.current.set(item.key, element);
                }}
                type="button"
                role="tab"
                id={`${baseId}-tab-${item.key}`}
                aria-selected={selected}
                aria-controls={`${baseId}-panel-${item.key}`}
                tabIndex={selected ? 0 : -1}
                onClick={() => onChangeTab(item.key)}
                onKeyDown={handleKeyDown}
                className={`relative flex items-center font-bold focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand ${
                  tablet ? 'px-5 text-[14px] leading-[21px]' : 'px-4 text-[13px] leading-[19.5px]'
                } ${
                  selected
                    ? 'text-brand after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-brand after:content-[""]'
                    : 'text-[#99a1af] hover:text-[#6a7282]'
                }`}
              >
                {item.label}
                {count !== null && <span className="ml-1">{count}</span>}
              </button>
            );
          })}
        </div>
        {sortControl}
      </div>
      <div role="tabpanel" id={`${baseId}-panel-${tab}`} aria-labelledby={`${baseId}-tab-${tab}`} className={tablet ? 'p-4' : ''}>
        <h2 ref={headingRefs[tab]} tabIndex={-1} className="sr-only">
          {TABS.find((item) => item.key === tab)?.label}
        </h2>
        {panels[tab]}
      </div>
    </div>
  );
}
