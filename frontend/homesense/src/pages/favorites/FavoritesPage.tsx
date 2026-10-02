import { useCallback, useEffect, useId, useMemo, useRef, useState, type KeyboardEvent, type ReactNode, type RefObject } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { HeartIcon } from '../../components/icons/HeartIcon';
import { MainLayout } from '../../components/layout/MainLayout';
import { EmptyState } from '../../components/ui/EmptyState';
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
import { MEDIA_XL_UP, useMediaQuery } from '../../lib/useMediaQuery';
import { RowSkeleton, WidgetError } from '../my/SectionCard';
import { useLoadable } from '../my/useLoadable';
import { PropertyCard, RegionCard } from './FavoriteCards';
import {
  SORT_QUERY_VALUE,
  TAB_QUERY_VALUE,
  describeNotificationBadge,
  parseSort,
  parseTab,
  sortFavorites,
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
 *   정렬 드롭다운(D1). 화면 크기별로 트리를 두 벌 렌더하지 않고 useMediaQuery로 한 벌만 그린다. 카드 컴포넌트는 공유한다.
 * - 정렬은 화면에서 한다. 하나의 정렬이 두 목록에 같이 적용되고, 정렬·탭은 URL(`?sort=rate`, `?tab=regions`)에 replace로
 *   남겨 단지 상세·검색 결과에서 돌아오면 그대로다(D5).
 * - 삭제는 확인 → 숨김 → 5초 뒤 DELETE, 실행취소 가능(D6, usePendingDeletion).
 * - 관심 매물 목록, 관심 지역 목록, 알림 설정(배지 문구용)을 따로 불러 한 쪽 실패가 다른 쪽을 막지 않는다. 알림 설정 조회가
 *   실패하면 배지는 관심 매물 응답의 hasNotificationSetting만으로 그린다.
 */
export function FavoritesPage() {
  const isDesktop = useMediaQuery(MEDIA_XL_UP);
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

  const settingsByProperty = useMemo(() => {
    const map = new Map<number, NotificationSettingResponse>();
    if (settings.state.status === 'success') {
      for (const setting of settings.state.data) {
        if (setting.favoritePropertyId != null) map.set(setting.favoritePropertyId, setting);
      }
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
            linkRef={setLinkRef(key)}
            badge={describeNotificationBadge(settingsByProperty.get(favorite.favoritePropertyId), {
              settingsAvailable: settings.state.status === 'success',
              hasNotificationSetting: favorite.hasNotificationSetting,
            })}
            onDelete={() => openDelete({ key, name: favorite.complexName, kind: 'property', id: favorite.favoritePropertyId })}
          />
        );
      })}
    </ListArea>
  );

  const regionList = (
    <ListArea
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
            linkRef={setLinkRef(key)}
            onDelete={() => openDelete({ key, name: region.eupmyeondongName, kind: 'region', id: region.favoriteRegionId })}
          />
        );
      })}
    </ListArea>
  );

  const adder = <RegionAdder registeredCodes={registeredCodes} onAdd={handleAddRegion} />;

  return (
    <MainLayout>
      <div className="mx-auto flex max-w-[1000px] flex-col gap-5 px-4 py-6 md:gap-7 md:px-8 md:py-12">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-[22px] leading-[33px] font-extrabold text-[#101828] md:text-[26px] md:leading-[39px]">
            관심 매물·지역 관리
          </h1>
          {isDesktop ? (
            <SortChips value={sort} onChange={(next) => updateQuery('sort', SORT_QUERY_VALUE[next])} />
          ) : (
            <SortSelect value={sort} onChange={(next) => updateQuery('sort', SORT_QUERY_VALUE[next])} />
          )}
        </div>

        {bothEmpty ? (
          <>
            <EmptyState
              variant="inducement"
              icon={<HeartIcon className="size-6" />}
              title="아직 등록한 관심 매물·지역이 없어요"
              description="매물을 탐색하고 관심 목록에 추가하면 시세 변동 알림을 받을 수 있어요."
              actions={
                <Link
                  to="/"
                  className="flex h-[42px] items-center rounded-[14px] bg-brand px-5 text-[14px] font-semibold text-white hover:bg-[#0c4a44]"
                >
                  매물 둘러보기
                </Link>
              }
            />
            {/* 빈 상태에서도 관심 지역 추가는 남긴다(D8) — 명세의 빈 상태는 "리스트 영역"이고, 이 입력이 MY-02에서 지역을
                등록하는 유일한 경로다. */}
            <section aria-labelledby="favorites-adder-heading" className="rounded-[16px] border border-[#f3f4f6] bg-white p-5">
              <h2 id="favorites-adder-heading" className="sr-only">
                관심 지역 추가
              </h2>
              {adder}
            </section>
          </>
        ) : isDesktop ? (
          <>
            <Section title="관심 매물" count={propertyCount} headingRef={propertiesHeadingRef}>
              {propertyList}
            </Section>
            <Section title="관심 지역" count={regionCount} headingRef={regionsHeadingRef}>
              {regionList}
            </Section>
            <section aria-labelledby="favorites-adder-heading" className="rounded-[16px] border border-[#f3f4f6] bg-white p-5">
              <h2 id="favorites-adder-heading" className="mb-1 text-[16px] font-bold text-[#101828]">
                관심 지역 추가
              </h2>
              {adder}
            </section>
          </>
        ) : (
          <TabbedLists
            tab={tab}
            onChangeTab={(next) => updateQuery('tab', TAB_QUERY_VALUE[next])}
            counts={{ properties: propertyCount, regions: regionCount }}
            headingRefs={{ properties: propertiesHeadingRef, regions: regionsHeadingRef }}
            panels={{
              properties: propertyList,
              // 태블릿·모바일은 추가 입력을 탭 맨 위에 둔다 — 목록이 길어도 바로 쓸 수 있게(D7).
              regions: (
                <div className="flex flex-col gap-4">
                  <div className="rounded-[16px] border border-[#f3f4f6] bg-white p-4">{adder}</div>
                  {regionList}
                </div>
              ),
            }}
          />
        )}
      </div>

      <Modal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        role="alertdialog"
        describedBy="favorites-delete-description"
        title="삭제하시겠어요?"
        footer={
          <div className="flex flex-col gap-2 md:flex-row-reverse">
            <button type="button" onClick={confirmDelete} className={MODAL_DANGER_BUTTON_CLASS}>
              삭제
            </button>
            <button type="button" onClick={() => setDeleteTarget(null)} className={MODAL_SECONDARY_BUTTON_CLASS}>
              취소
            </button>
          </div>
        }
      >
        <p id="favorites-delete-description" className="text-[14px] leading-[22px] text-[#4a5565]">
          {deleteTarget?.name}을(를) 삭제하면 해당 항목의 알림도 함께 중지됩니다.
        </p>
      </Modal>
    </MainLayout>
  );
}

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
      <h2 id={headingId} ref={headingRef} tabIndex={-1} className="text-[16px] font-bold text-[#101828] outline-none">
        {title}
        {count !== null && <span className="ml-1.5 text-brand">{count}</span>}
      </h2>
      {children}
    </section>
  );
}

function ListArea({
  state,
  onRetry,
  emptyText,
  isEmpty,
  children,
}: {
  state: { status: 'loading' } | { status: 'success' } | { status: 'error'; message: string };
  onRetry: () => void;
  emptyText: string;
  isEmpty: boolean;
  children: ReactNode;
}) {
  if (state.status === 'loading') {
    return (
      <div aria-busy="true" className="flex flex-col gap-3">
        <span className="sr-only">불러오는 중</span>
        {[0, 1].map((index) => (
          <div key={index} className="rounded-[16px] border border-[#f3f4f6] bg-white">
            <RowSkeleton thumbnail />
          </div>
        ))}
      </div>
    );
  }
  if (state.status === 'error') {
    return (
      <div className="rounded-[16px] border border-[#f3f4f6] bg-white px-5 py-4">
        <WidgetError message={state.message} onRetry={onRetry} />
      </div>
    );
  }
  if (isEmpty) {
    return <p className="rounded-[16px] border border-dashed border-[#e5e7eb] bg-white px-5 py-6 text-center text-[13px] text-[#6a7282]">{emptyText}</p>;
  }
  return <ul className="flex flex-col gap-3">{children}</ul>;
}

function SortChips({ value, onChange }: { value: FavoritesSort; onChange: (next: FavoritesSort) => void }) {
  return (
    <div role="radiogroup" aria-label="정렬" className="flex gap-1">
      {SORTS.map((option) => (
        <button
          key={option}
          type="button"
          role="radio"
          aria-checked={value === option}
          onClick={() => onChange(option)}
          className={`rounded-full px-3.5 py-1.5 text-[13px] font-semibold ${
            value === option ? 'bg-brand text-white' : 'bg-white text-[#4a5565] ring-1 ring-[#e5e7eb] hover:bg-[#f7f8fa]'
          }`}
        >
          {SORT_LABEL[option].chip}
        </button>
      ))}
    </div>
  );
}

/** 태블릿·모바일 정렬 — 네이티브 select(키보드·스크린리더 동작을 그대로 얻는다). */
function SortSelect({ value, onChange }: { value: FavoritesSort; onChange: (next: FavoritesSort) => void }) {
  return (
    <select
      aria-label="정렬"
      value={value}
      onChange={(event) => onChange(event.target.value as FavoritesSort)}
      className="h-9 rounded-[10px] border border-[#e5e7eb] bg-white pr-8 pl-3 text-[13px] font-semibold text-[#364153] focus:border-brand focus:outline-none"
    >
      {SORTS.map((option) => (
        <option key={option} value={option}>
          {SORT_LABEL[option].select}
        </option>
      ))}
    </select>
  );
}

function TabbedLists({
  tab,
  onChangeTab,
  counts,
  headingRefs,
  panels,
}: {
  tab: FavoritesTab;
  onChangeTab: (next: FavoritesTab) => void;
  counts: Record<FavoritesTab, number | null>;
  headingRefs: Record<FavoritesTab, RefObject<HTMLHeadingElement | null>>;
  panels: Record<FavoritesTab, ReactNode>;
}) {
  const baseId = useId();
  const tabRefs = useRef(new Map<FavoritesTab, HTMLButtonElement>());

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
    <div className="flex flex-col gap-4">
      <div role="tablist" aria-label="관심 목록" className="flex rounded-[14px] bg-[#eef0f3] p-1">
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
              className={`flex-1 rounded-[10px] py-2 text-[14px] font-semibold ${
                selected ? 'bg-white text-[#101828] shadow-[0_1px_3px_rgba(0,0,0,0.08)]' : 'text-[#6a7282]'
              }`}
            >
              {item.label}
              {count !== null && <span className={`ml-1 ${selected ? 'text-brand' : ''}`}>{count}</span>}
            </button>
          );
        })}
      </div>
      <div role="tabpanel" id={`${baseId}-panel-${tab}`} aria-labelledby={`${baseId}-tab-${tab}`}>
        <h2 ref={headingRefs[tab]} tabIndex={-1} className="sr-only">
          {TABS.find((item) => item.key === tab)?.label}
        </h2>
        {panels[tab]}
      </div>
    </div>
  );
}
