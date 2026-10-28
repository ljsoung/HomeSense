import { useCallback, useEffect, useEffectEvent, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AlertTriangleIcon } from '../../components/icons/AlertTriangleIcon';
import { ChevronLeftIcon } from '../../components/icons/ChevronLeftIcon';
import { LocateIcon } from '../../components/icons/LocateIcon';
import { MinusIcon } from '../../components/icons/MinusIcon';
import { PlusIcon } from '../../components/icons/PlusIcon';
import { MainLayout } from '../../components/layout/MainLayout';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/useToast';
import { getComplexDetail } from '../../features/complex/api';
import type { ComplexDetailResponse, ComplexMapPointResponse } from '../../features/complex/types';
import { readLastViewport, writeLastViewport } from '../../features/map/lastViewport';
import {
  buildMapApiQuery,
  buildSearchHref,
  COMPLEX_LEVEL,
  DEFAULT_VIEWPORT,
  filterKeyOfMapQuery,
  KEYWORD_LEVEL,
  levelForRegionCode,
  mapFilterKey,
  parseMapUrl,
  resolveInitialView,
  type MapBounds,
  type MapViewport,
  type PointView,
  type RegionView,
} from '../../features/map/mapParams';
import { fetchSelectedComplex, type MapPinItem } from '../../features/map/selection';
import { useMapPoints } from '../../features/map/useMapPoints';
import { useReplaceMapUrl } from '../../features/map/useReplaceMapUrl';
import type { RegionAutocompleteResponse } from '../../features/region/types';
import {
  defaultFilters,
  hasSearchCondition,
  KEYWORD_ISSUE_MESSAGE,
  keywordIssue,
  urlKeywordIssue,
  type SearchFilters,
  type SortUi,
} from '../../features/search/searchParams';
import { MEDIA_MD_DOWN, useMediaQuery } from '../../lib/useMediaQuery';
import { useFavoriteToggle } from '../home/useFavoriteToggle';
import { KakaoMapView, type KakaoMapHandle, type MapStatus, type StartView } from './KakaoMapView';
import { MapFilterBar } from './MapFilterBar';
import { formatMapCount } from '../../features/map/mapList';
import { MapListPanel } from './MapListPanel';
import { MapListSheet, type SheetSnap } from './MapListSheet';
import { MapPopup } from './MapPopup';

/** 시작 위치 중 단지 좌표 조회가 필요 없는 것만 StartView로 바꾼다. */
function toStartView(view: PointView | RegionView): StartView {
  return view.kind === 'point'
    ? { kind: 'point', viewport: view.viewport }
    : { kind: 'region', query: view.query, level: view.level, fallback: view.fallback.viewport, notifyNotFound: view.notifyNotFound };
}

/** 지역·검색어로 위치를 찾지 못했을 때 — 지도 검색창 제출과 검색어 진입이 같은 문구를 쓴다. */
const NOT_FOUND_MESSAGE = '위치를 찾을 수 없습니다. 지역명을 확인해 주세요.';

const CONTROL_BUTTON_CLASS =
  'flex size-9 items-center justify-center rounded-[14px] border border-[#e5e7eb] bg-white text-[#364153] shadow-[0_4px_3px_rgba(0,0,0,0.1),0_2px_2px_rgba(0,0,0,0.1)] hover:bg-[#f7f8fa] disabled:cursor-wait disabled:opacity-60';

/**
 * SCR-MAP-01 — 지도 기반 매물 조회(UI정의서 5.4절, Figma 6:4306·26:14574·26:14318). URL이 필터·뷰포트·선택의 유일한
 * 소스다: 필터는 SRCH-01과 같은 파라미터, 지도 위치(lat·lng·level)와 선택한 단지(complexId)는 지도를 움직일 때마다
 * replace로 갱신해 히스토리를 쌓지 않는다. 그래서 상세에서 뒤로 오면 같은 뷰포트·필터·선택(팝업)으로 돌아온다.
 *
 * 지도와 목록은 지도 범위 조회 응답 하나를 함께 쓴다. 지도 SDK를 불러오지 못해도 목록은 시작 위치로 근사한 범위로 계속
 * 채운다.
 */
export function MapPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const isMobile = useMediaQuery(MEDIA_MD_DOWN);
  const { favoritedIds, toggleFavorite, pendingFavoriteId } = useFavoriteToggle();
  const mapRef = useRef<KakaoMapHandle>(null);

  const urlState = useMemo(() => parseMapUrl(searchParams), [searchParams]);
  const { filters } = urlState;
  const selectedId = urlState.complexId ?? null;

  // 비동기 결과 적용 규칙 — 위치를 옮기거나 단지를 고르는 사용자 동작(지역 검색·현재 위치·목록/핀 선택·확대/축소·지도 직접
  // 조작)마다 번호를 올리고, 언마운트하면 한 번 더 올린다. 늦게 끝난 작업은 시작할 때의 번호가 그대로일 때만 결과를 적용하고
  // 오류를 알린다 — 그사이 더 새로운 동작이 있었거나 화면을 떠났으면 조용히 버린다.
  const intentRef = useRef(0);
  const beginIntent = useCallback(() => ++intentRef.current, []);
  useEffect(
    () => () => {
      intentRef.current += 1;
    },
    [],
  );

  // 진입 시 한 번 정한다 — 이후 지도를 움직여 URL이 바뀌어도 시작 위치는 다시 계산하지 않는다.
  const [initialView] = useState(() => resolveInitialView(urlState, readLastViewport()));
  const [startView, setStartView] = useState<StartView | null>(() =>
    initialView.kind === 'complex' ? null : toStartView(initialView),
  );

  // complexId만 있고 좌표가 없는 링크 — 단지 좌표를 조회해 그 위치에서 시작한다. 실패하면 다음 순위. 받은 상세는 선택 단지
  // 조회(fetchSelectedComplex)가 다시 부르지 않게 기억한다.
  const entryDetailRef = useRef<ComplexDetailResponse | undefined>(undefined);
  useEffect(() => {
    if (initialView.kind !== 'complex') return;
    let cancelled = false;
    const intent = intentRef.current;
    const fallback = () => toStartView(initialView.fallback);
    getComplexDetail(initialView.complexId)
      .then((detail) => {
        if (cancelled) return;
        entryDetailRef.current = detail;
        // 좌표를 받는 사이 다른 지역을 검색했으면 그 지역이 시작 위치다(검색이 이미 startView를 정했다). KakaoMapView는
        // 마운트할 때만 startView를 읽어 덮어써도 지도는 그대로지만, 상태가 실제 시작 위치와 어긋나지 않게 둔다.
        if (intentRef.current !== intent) return;
        if (detail.latitude != null && detail.longitude != null) {
          setStartView({ kind: 'point', viewport: { lat: detail.latitude, lng: detail.longitude, level: COMPLEX_LEVEL } });
        } else {
          setStartView(fallback());
        }
      })
      .catch(() => {
        if (!cancelled && intentRef.current === intent) setStartView(fallback());
      });
    return () => {
      cancelled = true;
    };
  }, [initialView]);

  const [bounds, setBounds] = useState<MapBounds | null>(null);
  const [mapStatus, setMapStatus] = useState<MapStatus>('loading');
  const apiQuery = useMemo(() => (bounds ? buildMapApiQuery(filters, bounds) : null), [bounds, filters]);

  const points = useMapPoints(apiQuery);
  const replaceUrl = useReplaceMapUrl();

  const handleIdle = useCallback(
    (viewport: MapViewport, next: MapBounds) => {
      setBounds(next);
      writeLastViewport(viewport);
      replaceUrl((prev) => ({ ...prev, viewport }));
    },
    [replaceUrl],
  );

  // 선택(팝업). popupAutoFocus: 사용자가 연 팝업만 포커스를 옮긴다. selectionSource: 핀에서 고르면 목록을 그 행으로 스크롤.
  const [popupAutoFocus, setPopupAutoFocus] = useState(false);
  const [selectionFromMap, setSelectionFromMap] = useState(false);
  const [sheetSnap, setSheetSnap] = useState<SheetSnap>('half');

  const select = useCallback(
    (complexId: number, from: 'map' | 'list') => {
      setPopupAutoFocus(true);
      setSelectionFromMap(from === 'map');
      replaceUrl((prev) => ({ ...prev, complexId }));
    },
    [replaceUrl],
  );

  const closePopup = useCallback(
    (returnFocus: boolean) => {
      const closingId = selectedId;
      replaceUrl((prev) => ({ ...prev, complexId: undefined }));
      setPopupAutoFocus(false);
      if (!returnFocus || closingId === null) return;
      // 닫으면 그 단지의 핀으로 포커스를 돌린다. 핀이 클러스터에 묶여 화면에 없으면 목록 행으로 돌린다.
      requestAnimationFrame(() => {
        if (mapRef.current?.focusPin(closingId)) return;
        document.querySelector<HTMLElement>(`[data-complex-id="${closingId}"] [data-map-select]`)?.focus();
      });
    },
    [replaceUrl, selectedId],
  );

  const handleListSelect = (item: ComplexMapPointResponse) => {
    if (mapStatus !== 'ready') {
      // 지도가 없으면 팝업을 띄울 곳이 없다 — 곧바로 단지 상세로 간다.
      navigate(`/complexes/${item.complexId}`);
      return;
    }
    beginIntent();
    mapRef.current?.panTo(item.latitude, item.longitude);
    if (isMobile) setSheetSnap('collapsed');
    select(item.complexId, 'list');
  };

  // 필터 — SRCH-01과 같은 draft → 적용 흐름. 적용하면 지도와 목록이 같은 응답으로 함께 바뀐다.
  const [draft, setDraft] = useState<SearchFilters>(filters);
  const applyFilters = () => replaceUrl((prev) => ({ ...prev, filters: { ...draft, page: 1 } }));
  const resetDraft = () =>
    setDraft({ ...defaultFilters(), regionCode: filters.regionCode, regionLabel: filters.regionLabel, keyword: filters.keyword });
  const resetFilters = () =>
    replaceUrl((prev) => ({
      ...prev,
      filters: { ...defaultFilters(), regionCode: filters.regionCode, regionLabel: filters.regionLabel, keyword: filters.keyword },
    }));
  const changeSort = (sort: SortUi) => replaceUrl((prev) => ({ ...prev, filters: { ...prev.filters, sort } }));

  // 지역 검색 — 고른 지역(또는 입력한 지역명)으로 지도를 옮기고, SRCH-01로 돌아갈 때 쓰도록 검색 조건도 URL에 남긴다.
  const urlSearchText = filters.regionLabel ?? filters.keyword ?? '';
  const [searchValue, setSearchValue] = useState(urlSearchText);
  // 같은 라우트 안에서 URL의 검색 조건이 바뀌면(GNB "지도로 보기"로 조건 없는 /map, 뒤로가기) 검색창을 맞춘다. 바뀔 때만
  // 맞추고 매 렌더 덮어쓰지 않는다 — 지도를 움직일 때마다 URL(뷰포트)이 바뀌어도 사용자가 입력 중인 값은 그대로 둔다.
  const [syncedSearchText, setSyncedSearchText] = useState(urlSearchText);
  // 이 화면이 검색·지역 선택으로 직접 URL에 쓴 검색 조건(아직 URL 렌더에 반영되지 않은 것, 쓴 순서대로). URL 갱신은 전환
  // 렌더라 늦게 반영된다 — 느린 기기에서는 Enter 뒤 250ms쯤. 그사이 사용자가 다음 검색어를 입력하면, 자기 검색의 반영이
  // 검색창을 앞 검색어로 되돌려 다음 Enter가 앞 검색을 다시 보냈다. 자기가 쓴 값이 반영될 때는 검색창을 건드리지 않는다.
  const [ownSearchTexts, setOwnSearchTexts] = useState<string[]>([]);
  if (syncedSearchText !== urlSearchText) {
    setSyncedSearchText(urlSearchText);
    const own = ownSearchTexts.indexOf(urlSearchText);
    if (own === -1) {
      setSearchValue(urlSearchText);
      if (ownSearchTexts.length > 0) setOwnSearchTexts([]);
    } else {
      setOwnSearchTexts(ownSearchTexts.slice(own + 1));
    }
  }
  const rememberOwnSearchText = (text: string) =>
    setOwnSearchTexts((prev) => {
      // URL이 바뀌지 않을 값(지금 URL과 같거나 바로 앞에 쓴 값과 같음)은 반영될 일이 없으니 남기지 않는다.
      const last = prev.length > 0 ? prev[prev.length - 1] : syncedSearchText;
      return text === last ? prev : [...prev, text];
    });
  const moveToQuery = async (query: string, level: number) => {
    const intent = beginIntent();
    const map = mapRef.current;
    if (!map) {
      // 지도 컴포넌트가 아직 없다(진입 단지 좌표 조회 중) — 이 검색어를 시작 위치로 삼는다.
      setStartView({ kind: 'region', query, level, fallback: DEFAULT_VIEWPORT });
      return;
    }
    // 지도가 아직 만들어지는 중이면(deferred) 검색어가 그 지도의 시작 위치로 넘어간다. 지도는 화면 폭이 바뀌어도 다시
    // 마운트되지 않아(아래 레이아웃) 여기에 따로 남길 필요가 없다.
    const result = await map.moveToQuery(query, level, () => intentRef.current === intent);
    // 밀린 검색(stale)은 알리지 않는다 — 앞 지역을 못 찾았다는 토스트가 뒤 지역 화면이나 다른 화면에 뜨면 안 된다.
    if (result === 'not-found' && intentRef.current === intent) {
      showToast(NOT_FOUND_MESSAGE, 'error');
    }
  };
  const handleSelectRegion = (region: RegionAutocompleteResponse) => {
    setSearchValue(region.fullPath);
    rememberOwnSearchText(region.fullPath);
    replaceUrl((prev) => ({
      ...prev,
      complexId: undefined,
      filters: { ...prev.filters, regionCode: region.legalDongCd, regionLabel: region.fullPath, keyword: undefined },
    }));
    void moveToQuery(region.fullPath, levelForRegionCode(region.legalDongCd));
  };
  /** 검색어를 받아들였으면 true. SRCH-01과 같은 규칙(keywordIssue, 2~50자)을 어긴 검색어는 URL에 남기지 않고 지도도 옮기지
   *  않는다 — 남기면 "목록으로" 링크가 SRCH-01의 검색어 오류 화면으로 간다. */
  const handleSubmitKeyword = (value: string): boolean => {
    const issue = keywordIssue(value);
    if (issue) {
      showToast(KEYWORD_ISSUE_MESSAGE[issue], 'error');
      return false;
    }
    const query = value.trim();
    if (!query) return false;
    rememberOwnSearchText(query);
    replaceUrl((prev) => ({
      ...prev,
      complexId: undefined,
      filters: { ...prev.filters, keyword: query, regionCode: undefined, regionLabel: undefined },
    }));
    void moveToQuery(query, KEYWORD_LEVEL);
    return true;
  };

  // 현재 위치 — 조회 중에는 버튼을 잠근다. 권한 거부·실패는 토스트로 알리고 지도는 그대로 둔다. 조회 중에 다른 이동·선택을
  // 했거나 화면을 떠났으면 결과(이동·토스트)를 버리고 버튼 잠금만 푼다.
  const [locating, setLocating] = useState(false);
  const goToCurrentLocation = () => {
    if (locating) return;
    if (!('geolocation' in navigator)) {
      showToast('이 브라우저에서는 현재 위치를 쓸 수 없습니다.', 'error');
      return;
    }
    const intent = beginIntent();
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        if (intentRef.current !== intent) return;
        mapRef.current?.moveTo(position.coords.latitude, position.coords.longitude);
      },
      (error) => {
        setLocating(false);
        if (intentRef.current !== intent) return;
        showToast(
          error.code === error.PERMISSION_DENIED ? '위치 권한이 없어 현재 위치로 이동할 수 없습니다.' : '현재 위치를 가져오지 못했습니다.',
          'error',
        );
      },
      { timeout: 10_000, maximumAge: 60_000 },
    );
  };

  const [listCollapsed, setListCollapsed] = useState(false);

  // 선택한 단지 — 최신 응답에 있으면 그 항목을 쓰고 마지막으로 알던 값으로 기억한다. 응답에 없으면(상한을 넘어 잘림, 지도를
  // 옮겨 범위 밖) 마지막으로 알던 값으로 핀과 팝업을 유지한다. 처음부터 없으면(DTL-01 딥링크로 넓은 레벨 진입) 지금 지도
  // 중심(딥링크면 단지 좌표) 주변의 좁은 범위 조회로 먼저 찾고, 없을 때만 상세 API로 받는다(features/map/selection.ts).
  // 마지막으로 알던 값은 그 값을 받은 필터 키와 함께 기억하고, 지금 필터와 키가 다르면 쓰지 않는다 — 필터를 바꾼 뒤 새 응답에
  // 선택 단지가 없으면 옛 필터의 대표 거래(예: 매매 가격)를 팝업에 남기지 않고 새 필터로 다시 조회한다.
  const filterKey = useMemo(() => mapFilterKey(filters), [filters]);
  const responseSelected = selectedId === null ? undefined : points.items.find((item) => item.complexId === selectedId);
  const responseFilterKey = points.itemsQuery === null ? null : filterKeyOfMapQuery(points.itemsQuery);
  const [knownSelected, setKnownSelected] = useState<{ item: MapPinItem; filterKey: string } | null>(null);
  if (responseSelected && responseFilterKey !== null && knownSelected?.item !== responseSelected) {
    setKnownSelected({ item: responseSelected, filterKey: responseFilterKey });
  }
  const selectedItem: MapPinItem | undefined =
    responseSelected ??
    (knownSelected !== null && knownSelected.item.complexId === selectedId && knownSelected.filterKey === filterKey
      ? knownSelected.item
      : undefined);
  const needsSelectedLookup = selectedId !== null && !points.initialLoading && selectedItem === undefined;
  const lookupSelected = useEffectEvent((complexId: number, lookupFilterKey: string, signal: AbortSignal) =>
    fetchSelectedComplex(
      complexId,
      filters,
      {
        near: urlState.viewport ? { lat: urlState.viewport.lat, lng: urlState.viewport.lng } : undefined,
        knownDetail: entryDetailRef.current?.complexId === complexId ? entryDetailRef.current : undefined,
      },
      signal,
    ).then((found) => {
      if (!signal.aborted && found) setKnownSelected({ item: found, filterKey: lookupFilterKey });
    }),
  );
  // 조회 중에 다른 단지를 고르거나 팝업을 닫거나 화면을 떠나면 끊고 결과를 버린다. 필터를 바꿔도 끊고 새 필터로 다시
  // 찾는다(옛 필터의 대표 거래를 팝업에 넣지 않게). 키는 지도 API에 실리는 필터만이라 정렬·페이지는 들어가지 않는다. 동작
  // 번호(intentRef)는 보지 않는다 — 확대·축소·지도 터치는 선택을 바꾸지 않으니 팝업 가격 조회를 끊지 않는다.
  useEffect(() => {
    if (!needsSelectedLookup || selectedId === null) return;
    const controller = new AbortController();
    // 실패(상세 404·네트워크)하면 팝업 없이 둔다 — 지도와 목록은 그대로 쓸 수 있다.
    lookupSelected(selectedId, filterKey, controller.signal).catch(() => {});
    return () => controller.abort();
  }, [needsSelectedLookup, selectedId, filterKey]);
  // 지도에 그리는 핀 — 응답 항목 + 응답에 없는 선택 단지.
  const mapItems = useMemo<MapPinItem[]>(
    () => (selectedItem && !responseSelected ? [...points.items, selectedItem] : points.items),
    [points.items, selectedItem, responseSelected],
  );
  const areaLabel = filters.regionLabel ?? filters.keyword ?? '지도 영역';
  const countText = points.initialLoading ? '…' : formatMapCount(points.items.length, points.truncated);
  // URL로 들어온 검색어가 SRCH-01 규칙을 어기면(공유 링크 등) 목록 링크를 두지 않는다 — SRCH-01이 결과 대신 검색어 오류만 보인다.
  const searchHref = hasSearchCondition(filters) && !urlKeywordIssue(filters) ? buildSearchHref(filters) : undefined;

  const listPanel = (
    <MapListPanel
      items={points.items}
      truncated={points.truncated}
      initialLoading={points.initialLoading}
      areaLabel={areaLabel}
      sort={filters.sort}
      onSortChange={changeSort}
      selectedId={selectedId}
      onSelect={handleListSelect}
      favoritedIds={favoritedIds}
      pendingFavoriteId={pendingFavoriteId}
      onToggleFavorite={toggleFavorite}
      onResetFilters={resetFilters}
      searchHref={searchHref}
      scrollSelectedIntoView={selectionFromMap}
      hideHeader={isMobile}
    />
  );

  const mapView = startView ? (
    <KakaoMapView
      ref={mapRef}
      startView={startView}
      items={mapItems}
      selectedId={selectedId}
      onIdle={handleIdle}
      onSelectPin={(id) => {
        beginIntent();
        // 핀을 지도 가운데로 옮긴다 — 가장자리 핀의 팝업이 필터 막대·시트 아래로 잘리지 않게 한다(목록 선택과 같다).
        const target = mapItems.find((item) => item.complexId === id);
        if (target) mapRef.current?.panTo(target.latitude, target.longitude);
        select(id, 'map');
      }}
      onMapClick={() => {
        if (selectedId !== null) closePopup(false);
      }}
      onStatusChange={setMapStatus}
      onUserGesture={beginIntent}
      onStartNotFound={() => showToast(NOT_FOUND_MESSAGE, 'error')}
      popup={selectedItem ? <MapPopup item={selectedItem} autoFocus={popupAutoFocus} onClose={() => closePopup(true)} /> : null}
    >
      {/* 지도 위 안내 — 범위 안 결과가 상한을 넘어 잘렸을 때. */}
      {points.truncated && (
        <div
          role="status"
          className={`pointer-events-none absolute left-1/2 z-10 -translate-x-1/2 rounded-full border border-[#e5e7eb] bg-white/90 px-3 py-1 text-[12px] font-medium text-[#364153] shadow-[0_2px_4px_rgba(0,0,0,0.1)] backdrop-blur-[8px] ${
            isMobile ? 'top-[100px]' : 'top-3'
          }`}
        >
          지도를 더 확대해보세요
        </div>
      )}
      {/* 지도 컨트롤(Figma 6:4623) — 확대·축소·현재 위치. */}
      <div className={`absolute right-4 z-10 flex flex-col gap-1 ${isMobile ? 'top-14' : 'bottom-6'}`}>
        <button
          type="button"
          onClick={() => {
            beginIntent();
            mapRef.current?.zoomBy(-1);
          }}
          aria-label="지도 확대"
          className={CONTROL_BUTTON_CLASS}
        >
          <PlusIcon className="size-4" />
        </button>
        <button
          type="button"
          onClick={() => {
            beginIntent();
            mapRef.current?.zoomBy(1);
          }}
          aria-label="지도 축소"
          className={CONTROL_BUTTON_CLASS}
        >
          <MinusIcon className="size-4" />
        </button>
        <button
          type="button"
          onClick={goToCurrentLocation}
          disabled={locating}
          aria-busy={locating}
          aria-label={locating ? '현재 위치 찾는 중' : '현재 위치로 이동'}
          className={`mt-1 ${CONTROL_BUTTON_CLASS}`}
        >
          {locating ? <Spinner className="size-4" /> : <LocateIcon className="size-4" />}
        </button>
      </div>
      {/* 정밀·근사 범례(Figma 6:4639) — 좌하단 고정. 모바일은 시트 위로 올린다. */}
      <div
        className={`absolute left-4 z-10 flex items-center gap-3 rounded-[14px] border border-[#f3f4f6] bg-white/90 px-3 py-2 shadow-[0_4px_6px_rgba(0,0,0,0.1),0_2px_4px_rgba(0,0,0,0.1)] backdrop-blur-[8px] ${
          isMobile ? 'top-14' : 'bottom-6'
        }`}
        aria-label="핀 색상 범례"
        role="note"
      >
        <span className="flex items-center gap-1.5 text-[11px] font-medium text-[#4a5565]">
          <span className="size-2.5 rounded-full bg-brand" aria-hidden="true" />
          정밀
        </span>
        <span className="h-3 w-px bg-[#e5e7eb]" aria-hidden="true" />
        <span className="flex items-center gap-1.5 text-[11px] font-medium text-[#4a5565]">
          <span className="size-2.5 rounded-full bg-[#bb4d00]" aria-hidden="true" />
          근사
        </span>
      </div>
    </KakaoMapView>
  ) : (
    <div className="flex h-full items-center justify-center bg-[#eef3f8] text-[12px] text-[#99a1af]">
      <Spinner className="size-6" />
    </div>
  );

  // API 실패 배너 — 서버 error.message 그대로(UIC-07과 같은 오류 톤). 기존 마커·목록은 지우지 않는다.
  const errorBanner = points.errorMessage ? (
    <div
      role="alert"
      className={`absolute inset-x-3 z-30 mx-auto flex max-w-[480px] items-center gap-2.5 rounded-[14px] border border-[#fee2e2] bg-[#fef2f2] px-3.5 py-2.5 shadow-[0_4px_6px_rgba(0,0,0,0.08)] ${
        isMobile ? 'top-12' : 'top-3'
      }`}
    >
      <AlertTriangleIcon className="size-4 shrink-0 text-[#e7000b]" />
      <p className="min-w-0 flex-1 text-[12.5px] text-[#7f1d1d]">{points.errorMessage}</p>
      <button
        type="button"
        onClick={points.retry}
        className="shrink-0 rounded-[10px] bg-white px-3 py-1.5 text-[12px] font-semibold text-[#e7000b] shadow-[0_1px_2px_rgba(0,0,0,0.1)]"
      >
        다시 시도
      </button>
    </div>
  ) : null;

  const filterBar = (
    <MapFilterBar
      mobile={isMobile}
      filters={filters}
      draft={draft}
      onChangeDraft={setDraft}
      onApply={applyFilters}
      onReset={resetDraft}
      onOpenFilters={() => setDraft(filters)}
      searchValue={searchValue}
      onSearchValueChange={setSearchValue}
      onSubmitKeyword={handleSubmitKeyword}
      onSelectRegion={handleSelectRegion}
      countText={
        points.initialLoading ? (
          '…'
        ) : (
          <>
            {formatMapCount(points.items.length, points.truncated).replace(/건$/, '')}
            <span className="font-medium text-muted">건</span>
          </>
        )
      }
    />
  );

  // 레이아웃은 트리 하나다(반응형 렌더 규칙). 화면 폭이 768px 경계를 넘어도 지도 컨테이너와 SDK 지도 객체가 같은 자리에
  // 남는다 — 위치·확대 레벨·핀·진행 중인 지역 검색이 그대로이고, 크기 변화는 KakaoMapView의 ResizeObserver가 relayout으로
  // 맞춘다. 화면 크기마다 달라지는 것은 클래스와, 한쪽에만 있는 목록 자리(데스크톱 사이드바 / 모바일 하단 시트)뿐이다.
  // 조건부 자식은 자리를 비워 둔 채(false) 렌더해 지도 래퍼의 위치가 바뀌지 않게 한다.
  return (
    <MainLayout fullHeight>
      <div className={`relative flex h-full flex-col ${isMobile ? 'overflow-hidden' : ''}`}>
        {filterBar}
        <div className={isMobile ? 'absolute inset-0' : 'relative flex min-h-0 flex-1'}>
          {!isMobile && (
            <aside
              aria-label="지도 단지 목록"
              className={`h-full w-[380px] shrink-0 border-r border-[#e5e7eb] bg-white ${listCollapsed ? 'hidden' : ''}`}
            >
              {listPanel}
            </aside>
          )}
          <div className={isMobile ? 'absolute inset-0' : 'relative min-w-0 flex-1'} data-testid="map-area">
            {mapView}
            {errorBanner}
            {/* 목록 접기·펼치기(Figma 26:14831) — 지도 왼쪽 가장자리. */}
            {!isMobile && (
              <button
                type="button"
                onClick={() => setListCollapsed((v) => !v)}
                aria-label={listCollapsed ? '목록 펼치기' : '목록 접기'}
                aria-expanded={!listCollapsed}
                className="absolute top-1/2 left-0 z-20 flex h-[52px] w-5 -translate-y-1/2 items-center justify-center rounded-r-[14px] border border-l-0 border-[#e5e7eb] bg-white text-[#6a7282] shadow-[0_1px_1.5px_rgba(0,0,0,0.1),0_1px_1px_rgba(0,0,0,0.1)]"
              >
                <ChevronLeftIcon className={`size-3.5 ${listCollapsed ? 'rotate-180' : ''}`} />
              </button>
            )}
          </div>
        </div>
        {isMobile && (
          <MapListSheet
            snap={sheetSnap}
            onSnapChange={setSheetSnap}
            title={
              <span aria-live="polite">
                주변 단지 <span className="text-brand">{countText}</span>
              </span>
            }
          >
            {listPanel}
          </MapListSheet>
        )}
      </div>
    </MainLayout>
  );
}

