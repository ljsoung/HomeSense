import { useEffect, useImperativeHandle, useRef, useState, type ReactNode, type Ref } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangleIcon } from '../../components/icons/AlertTriangleIcon';
import { loadKakaoMaps } from '../../features/map/kakaoLoader';
import type {
  KakaoCluster,
  KakaoCustomOverlay,
  KakaoLatLng,
  KakaoMap,
  KakaoMapsNamespace,
  KakaoMarkerClusterer,
} from '../../features/map/kakaoTypes';
import {
  approximateBounds,
  formatClusterCount,
  formatPinPrice,
  MAX_LEVEL,
  MIN_LEVEL,
  type MapBounds,
  type MapViewport,
} from '../../features/map/mapParams';
import { hasRepresentativeTrade, type MapPinItem } from '../../features/map/selection';
import { describeDealAmount } from '../../lib/format';

/** 시작 위치 — MapPage가 complexId 조회까지 끝낸 결과. 지역 라벨은 SDK Geocoder가 필요해 여기서 처리한다. */
export type StartView =
  | { kind: 'point'; viewport: MapViewport }
  /** notifyNotFound: 못 찾아 fallback으로 열면 onStartNotFound를 부른다(검색어로 들어온 경우). */
  | { kind: 'region'; query: string; level: number; fallback: MapViewport; notifyNotFound?: boolean };

export type MapStatus = 'loading' | 'ready' | 'error';

export interface KakaoMapHandle {
  panTo(lat: number, lng: number): void;
  zoomBy(delta: number): void;
  moveTo(lat: number, lng: number): void;
  /** 핀이 화면에 있으면(클러스터에 묶이지 않았으면) 포커스하고 true. */
  focusPin(complexId: number): boolean;
  /**
   * 지역명·주소를 SDK로 찾아 그 중심으로 옮긴다. 찾는 동안 `isCurrent()`가 거짓이 됐거나(더 새로운 이동·선택) 지도가
   * 사라졌으면(언마운트·재시도) 옮기지 않고 'stale'. 지도가 아직 만들어지는 중이면 이 검색어를 새 시작 위치로 넘기고
   * 'deferred'. 못 찾거나 지도를 쓸 수 없으면 'not-found'.
   */
  moveToQuery(query: string, level: number, isCurrent: () => boolean): Promise<MoveToQueryResult>;
}

export type MoveToQueryResult = 'moved' | 'not-found' | 'stale' | 'deferred';

interface KakaoMapViewProps {
  ref?: Ref<KakaoMapHandle>;
  startView: StartView;
  /** 응답 항목 + 응답에 없는 선택 단지(MapPage가 마지막으로 알던 값으로 더한다). */
  items: MapPinItem[];
  selectedId: number | null;
  /** 지도가 멈출 때(idle). SDK를 못 불러오면 화면 크기로 근사한 bounds로 한 번 부른다 — 목록 패널은 계속 동작한다. */
  onIdle: (viewport: MapViewport, bounds: MapBounds) => void;
  onSelectPin: (complexId: number) => void;
  onMapClick: () => void;
  /** SDK 상태가 바뀌면 알린다 — 지도가 없으면 목록 항목 선택이 팝업 대신 상세로 간다. */
  onStatusChange?: (status: MapStatus) => void;
  /** 사용자가 지도를 직접 만졌다(누르기·끌기·휠). 진행 중인 위치 이동(검색·현재 위치)의 결과를 버리는 데 쓴다. 팝업 안은 제외. */
  onUserGesture?: () => void;
  /**
   * 시작 위치(notifyNotFound가 켜진 검색어)를 못 찾아 대체 위치로 열었다. 지도가 만들어진 뒤 한 번 — 그사이 시작 위치가
   * 바뀌었거나(지도가 뜨기 전 다른 검색) 지도가 사라졌으면(언마운트·재마운트) 부르지 않는다.
   */
  onStartNotFound?: () => void;
  /** 선택된 단지의 팝업 내용. 핀 바로 위에 띄운다. */
  popup: ReactNode;
  /** 지도 위에 겹쳐 그릴 요소(컨트롤·범례·안내). SDK 오류 상태에서도 그린다. */
  children?: ReactNode;
}

/**
 * 핀 클러스터링 시작 레벨. 이보다 확대된 화면(레벨 4 이하)에서는 모든 핀을 따로 보인다 — 단지 가격을 읽을 수 있는 거리다.
 * 넓은 화면에서는 클러스터 원형 배지(건수)로 묶는다.
 */
const CLUSTER_MIN_LEVEL = 5;

/** idle의 중심이 기억한 중심과 이만큼(px) 안이면 움직이지 않은 것으로 본다 — SDK 격자 맞춤 오차(최대 1px)보다 크게. */
const SNAP_TOLERANCE_PX = 1.5;

const PIN_BUTTON_CLASS =
  'group flex min-h-11 min-w-11 cursor-pointer items-end justify-center bg-transparent p-0 focus-visible:outline-none';
const BUBBLE_BASE_CLASS =
  'flex h-[26px] items-center whitespace-nowrap rounded-[13px] border-2 px-2.5 text-[12px] font-bold leading-[18px] group-focus-visible:ring-2 group-focus-visible:ring-offset-2 group-focus-visible:ring-[#101828]';
// Figma 6:4595(정밀) / 6:4619(근사) / 6:4690(선택). 근사 색은 Figma의 #d97706 대신 #bb4d00을 쓴다 — 12px 굵은 글자라
// 4.5:1이 필요한데 #d97706은 흰 바탕 대비 3.19:1이다. #bb4d00은 5.03:1(선택 시 흰 글자도 같다)이고 이 프로젝트의 기존
// 근사·주의 문구 색(DTL-01 근사 안내, "등기 전" 배지)과 같다. 정밀(#0f5c54)은 7.84:1. 범례 견본도 같은 값(MapPage).
const BUBBLE_TONE = {
  precise: { idle: 'border-brand bg-white text-brand', selected: 'border-brand bg-brand text-white' },
  approximate: { idle: 'border-[#bb4d00] bg-white text-[#bb4d00]', selected: 'border-[#bb4d00] bg-[#bb4d00] text-white' },
} as const;
const BUBBLE_SHADOW = { idle: 'shadow-[0_2px_3px_rgba(0,0,0,0.16)]', selected: 'shadow-[0_4px_7px_rgba(0,0,0,0.28)]' } as const;

const CLUSTER_STYLE = {
  width: '44px',
  height: '44px',
  lineHeight: '40px',
  borderRadius: '9999px',
  background: 'rgba(15, 92, 84, 0.92)',
  border: '2px solid #ffffff',
  color: '#ffffff',
  textAlign: 'center',
  fontWeight: '700',
  fontSize: '13px',
  boxShadow: '0 2px 6px rgba(0, 0, 0, 0.25)',
};

interface PinEntry {
  overlay: KakaoCustomOverlay;
  button: HTMLButtonElement;
  bubble: HTMLSpanElement;
  signature: string;
  approximate: boolean;
  /** 클러스터러에 들어 있는지. 선택한 핀은 클러스터에 묶이지 않게 지도에 직접 붙인다. */
  clustered: boolean;
}

/** 시작 위치를 찾지 못할 때 쓰는 위치. */
function startFallback(view: StartView): MapViewport {
  return view.kind === 'point' ? view.viewport : view.fallback;
}

/** 주소 검색(Geocoder) → 실패하면 장소 키워드 검색(Places)으로 한 번 더 찾는다. */
function geocode(maps: KakaoMapsNamespace, query: string): Promise<{ lat: number; lng: number } | null> {
  return new Promise((resolve) => {
    new maps.services.Geocoder().addressSearch(query, (result, status) => {
      if (status === maps.services.Status.OK && result[0]) {
        resolve({ lat: Number(result[0].y), lng: Number(result[0].x) });
        return;
      }
      new maps.services.Places().keywordSearch(query, (places, placeStatus) => {
        resolve(placeStatus === maps.services.Status.OK && places[0] ? { lat: Number(places[0].y), lng: Number(places[0].x) } : null);
      });
    });
  });
}

function pinLabel(item: MapPinItem): string {
  const price = hasRepresentativeTrade(item)
    ? describeDealAmount(item.representativeDealCategory, item.rentType, item.representativeAmount, item.monthlyRentAmount)
    : '거래 정보 없음';
  return `${item.complexName}, ${price}${item.precisionBadge === 'APPROXIMATE' ? ', 근사' : ''}`;
}

/** 핀 말풍선 글자 — 대표 거래가 없으면(지금 필터에 맞는 거래 없는 선택 단지) 짧게 줄인 단지명. */
function pinText(item: MapPinItem): string {
  if (hasRepresentativeTrade(item)) {
    return formatPinPrice(item.representativeDealCategory, item.rentType, item.representativeAmount, item.monthlyRentAmount);
  }
  return item.complexName.length > 8 ? `${item.complexName.slice(0, 8)}…` : item.complexName;
}

function applyPinState(entry: PinEntry, selected: boolean) {
  const tone = entry.approximate ? BUBBLE_TONE.approximate : BUBBLE_TONE.precise;
  entry.bubble.className = `${BUBBLE_BASE_CLASS} ${selected ? tone.selected : tone.idle} ${selected ? BUBBLE_SHADOW.selected : BUBBLE_SHADOW.idle}`;
  entry.button.setAttribute('aria-pressed', String(selected));
  entry.overlay.setZIndex(selected ? 100 : 1);
}

/**
 * MAP-01 지도. 카카오맵 JS SDK를 불러와(features/map/kakaoLoader) 지도를 만들고, 단지마다 가격 말풍선 핀(CustomOverlay +
 * button)을 MarkerClusterer에 넣는다. SDK 객체는 React 상태가 아니라 ref로 들고, 응답이 바뀌면 complexId로 비교해 바뀐
 * 핀만 다시 만든다(최대 500개).
 */
export function KakaoMapView({
  ref,
  startView,
  items,
  selectedId,
  onIdle,
  onSelectPin,
  onMapClick,
  onStatusChange,
  onUserGesture,
  onStartNotFound,
  popup,
  children,
}: KakaoMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapsRef = useRef<KakaoMapsNamespace | null>(null);
  const mapRef = useRef<KakaoMap | null>(null);
  const clustererRef = useRef<KakaoMarkerClusterer | null>(null);
  const pinsRef = useRef(new Map<number, PinEntry>());
  const popupOverlayRef = useRef<KakaoCustomOverlay | null>(null);
  /** 지금 지도의 중심·레벨·bounds를 onIdle로 알린다 — 지도가 만들어질 때 채운다. */
  const publishViewRef = useRef<(() => void) | null>(null);
  /**
   * 크기 변경 보정용. layoutSizeRef: 이 컴포넌트가 마지막으로 배치한(지도 생성·relayout) 컨테이너 크기. lastCenterRef: 그 크기에서
   * 지도가 마지막으로 멈췄을 때의 중심 — 크기가 바뀐 뒤 되돌릴 기준이다. SDK의 getCenter()는 기준이 될 수 없다: 실제 SDK는 창
   * resize 이벤트에 스스로 relayout하면서 왼쪽 위를 고정해 중심을 어긋나게 바꿔 놓는다(2026-10-27 탐침).
   */
  const layoutSizeRef = useRef<{ w: number; h: number } | null>(null);
  const lastCenterRef = useRef<KakaoLatLng | null>(null);
  /** 팝업 오버레이가 지금 지도에 붙어 있는지. */
  const popupShownRef = useRef(false);
  const [popupContainer] = useState(() => {
    const el = document.createElement('div');
    el.className = 'pb-[30px]';
    // 팝업 안의 클릭·드래그가 지도 클릭(팝업 닫기)·지도 이동으로 번지지 않게 한다.
    for (const type of ['mousedown', 'touchstart', 'click', 'dblclick', 'wheel', 'pointerdown']) {
      el.addEventListener(type, (event) => event.stopPropagation());
    }
    return el;
  });
  const [status, setStatus] = useState<MapStatus>('loading');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  // 콜백은 최신 것을 부르되 지도 생성 effect를 다시 돌리지 않는다.
  const callbacks = useRef({ onIdle, onSelectPin, onMapClick });
  useEffect(() => {
    callbacks.current = { onIdle, onSelectPin, onMapClick };
  });
  const onStatusChangeRef = useRef(onStatusChange);
  const onUserGestureRef = useRef(onUserGesture);
  const onStartNotFoundRef = useRef(onStartNotFound);
  useEffect(() => {
    onStatusChangeRef.current = onStatusChange;
    onUserGestureRef.current = onUserGesture;
    onStartNotFoundRef.current = onStartNotFound;
  });
  const statusRef = useRef(status);
  useEffect(() => {
    statusRef.current = status;
    onStatusChangeRef.current?.(status);
  }, [status]);
  /** 시작 위치. 지도가 만들어지기 전에 지역을 검색하면 그것으로 바뀐다(moveToQuery). */
  const startViewRef = useRef(startView);
  const selectedIdRef = useRef(selectedId);
  useEffect(() => {
    selectedIdRef.current = selectedId;
  });

  // SDK 로드 + 지도 생성. 재시도(attempt)마다 다시 돈다.
  useEffect(() => {
    let cancelled = false;
    const container = containerRef.current;
    if (!container) return;
    const listeners: Array<() => void> = [];
    const pins = pinsRef.current;

    const fallbackToApproximate = (message: string) => {
      setStatus('error');
      setErrorMessage(message);
      const view = startViewRef.current;
      const viewport = view.kind === 'point' ? view.viewport : view.fallback;
      const rect = container.getBoundingClientRect();
      callbacks.current.onIdle(viewport, approximateBounds(viewport, viewport.level, rect.width || 800, rect.height || 600));
    };

    // 찾는 사이 시작 위치가 바뀌었으면(지도가 뜨기 전에 다른 지역을 검색) 앞 결과는 버리고 새 시작
    // 위치로 다시 정한다 — 늦게 끝난 첫 지오코딩이 지도를 처음 지역에서 열지 않게 한다.
    const resolveStart = async (maps: KakaoMapsNamespace): Promise<{ viewport: MapViewport; notFound: boolean }> => {
      for (;;) {
        const view = startViewRef.current;
        if (view.kind === 'point') return { viewport: view.viewport, notFound: false };
        const found = await geocode(maps, view.query);
        if (cancelled || startViewRef.current === view) {
          return found
            ? { viewport: { ...found, level: view.level }, notFound: false }
            : { viewport: view.fallback, notFound: view.notifyNotFound === true };
        }
      }
    };

    loadKakaoMaps()
      .then(async (maps) => {
        if (cancelled) return;
        const { viewport: start, notFound: startNotFound } = await resolveStart(maps);
        if (cancelled) return;
        mapsRef.current = maps;
        container.innerHTML = '';
        const map = new maps.Map(container, { center: new maps.LatLng(start.lat, start.lng), level: start.level });
        mapRef.current = map;

        const clusterer = new maps.MarkerClusterer({
          map,
          averageCenter: true,
          minLevel: CLUSTER_MIN_LEVEL,
          disableClickZoom: true,
          styles: [CLUSTER_STYLE],
          texts: formatClusterCount,
        });
        clustererRef.current = clusterer;

        layoutSizeRef.current = { w: container.clientWidth, h: container.clientHeight };
        lastCenterRef.current = map.getCenter();
        const handleIdle = () => {
          // 컨테이너 크기가 마지막 배치와 다르면 크기 변경 도중이다(창 resize에 SDK가 스스로 relayout해 중심이 어긋난 상태). 이
          // 중심은 기억하지도 알리지도 않는다 — 아래 ResizeObserver가 원래 중심으로 되돌린 뒤 알린다.
          const size = layoutSizeRef.current;
          if (typeof ResizeObserver !== 'undefined' && size && (size.w !== container.clientWidth || size.h !== container.clientHeight)) {
            return;
          }
          const b = map.getBounds();
          const sw = b.getSouthWest();
          const ne = b.getNorthEast();
          // SDK는 중심을 픽셀 격자에 맞춘다 — 크기 변경 뒤 기억한 중심으로 되돌려도 getCenter()는 최대 1px 어긋난 값을
          // 돌려준다(실제 SDK 2026-10-27: 폭 767에서 setCenter(126.9783807) → 126.9783354, 1.0px). 이 값을 기억하면 경계를 넘을
          // 때마다 한 방향으로 쌓이고 URL도 바뀐다. 그래서 기억한 중심과 1.5px 안이면 움직이지 않은 것으로 보고 기억한 중심을
          // 그대로 쓴다(복원 뒤 idle이 몇 번 오든, 동기든 비동기든 같다). 사용자가 옮겼으면(1.5px 넘게) 새 중심을 기억한다.
          const snapped = map.getCenter();
          const remembered = lastCenterRef.current;
          const latPerPx = (ne.getLat() - sw.getLat()) / (container.clientHeight || 1);
          const lngPerPx = (ne.getLng() - sw.getLng()) / (container.clientWidth || 1);
          const unmoved =
            remembered !== null &&
            Math.abs(snapped.getLat() - remembered.getLat()) < SNAP_TOLERANCE_PX * latPerPx &&
            Math.abs(snapped.getLng() - remembered.getLng()) < SNAP_TOLERANCE_PX * lngPerPx;
          const center = unmoved ? remembered : snapped;
          lastCenterRef.current = center;
          callbacks.current.onIdle(
            { lat: center.getLat(), lng: center.getLng(), level: map.getLevel() },
            { swLat: sw.getLat(), swLng: sw.getLng(), neLat: ne.getLat(), neLng: ne.getLng() },
          );
        };
        // 핀·팝업은 clickable 오버레이라 그 클릭은 지도 클릭으로 번지지 않는다(카카오 CustomOverlay clickable 옵션).
        const handleClick = () => callbacks.current.onMapClick();
        // 클러스터를 누르면 그 클러스터가 덮는 영역으로 확대한다.
        const handleClusterClick = (cluster: KakaoCluster) => {
          map.setBounds(cluster.getBounds());
        };
        publishViewRef.current = handleIdle;
        maps.event.addListener(map, 'idle', handleIdle);
        maps.event.addListener(map, 'click', handleClick);
        maps.event.addListener(clusterer, 'clusterclick', handleClusterClick);
        listeners.push(
          () => maps.event.removeListener(map, 'idle', handleIdle),
          () => maps.event.removeListener(map, 'click', handleClick),
          () => maps.event.removeListener(clusterer, 'clusterclick', handleClusterClick),
        );

        popupOverlayRef.current = new maps.CustomOverlay({
          position: map.getCenter(),
          content: popupContainer,
          xAnchor: 0.5,
          yAnchor: 1,
          zIndex: 1000,
          clickable: true,
        });

        setStatus('ready');
        setErrorMessage(null);
        handleIdle();
        if (startNotFound) onStartNotFoundRef.current?.();
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        fallbackToApproximate(error instanceof Error ? error.message : '지도를 불러오지 못했습니다.');
      });

    return () => {
      cancelled = true;
      listeners.forEach((remove) => remove());
      clustererRef.current?.clear();
      pins.forEach((entry) => entry.overlay.setMap(null));
      pins.clear();
      popupOverlayRef.current?.setMap(null);
      popupOverlayRef.current = null;
      popupShownRef.current = false;
      clustererRef.current = null;
      mapRef.current = null;
      publishViewRef.current = null;
      layoutSizeRef.current = null;
      lastCenterRef.current = null;
    };
  }, [attempt, popupContainer]);

  // 패널 접기·창 크기 변경 등으로 지도 컨테이너 크기가 바뀌면 relayout하고, 바뀐 bounds를 onIdle로 다시 알린다 — relayout은
  // idle을 내지 않을 수 있어, 알리지 않으면 새로 드러난 영역엔 핀이 없고 목록엔 화면 밖 단지가 남는다. 창을 끄는 동안 연달아
  // 오는 크기 변경은 프레임당 한 번으로 묶는다(조회 자체는 useMapPoints가 300ms 디바운스한다).
  useEffect(() => {
    const container = containerRef.current;
    if (!container || typeof ResizeObserver === 'undefined') return;
    let frame = 0;
    const observer = new ResizeObserver(() => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const map = mapRef.current;
        if (!map) return;
        // 크기가 바뀌어도 화면 가운데가 같은 곳을 보게 한다 — 768px 경계를 넘어 레이아웃이 바뀌어도(지도는 다시 마운트되지
        // 않는다) 사용자가 보던 위치가 그대로 가운데 남는다. relayout은 왼쪽 위를 고정해 중심을 옮기므로, 크기가 바뀌기 전
        // 마지막으로 멈춘 중심(lastCenterRef)으로 되돌린다. getCenter()를 쓰면 안 된다 — 창 resize에 SDK가 이미 스스로
        // relayout해 어긋난 중심을 돌려준다.
        const center = lastCenterRef.current ?? map.getCenter();
        map.relayout();
        map.setCenter(center);
        layoutSizeRef.current = { w: container.clientWidth, h: container.clientHeight };
        publishViewRef.current?.();
      });
    });
    observer.observe(container);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, []);

  // 사용자가 지도를 직접 만지면(누르기·끌기·휠) 알린다. SDK가 이벤트를 먼저 가로채지 않게 캡처 단계에서 받고, 팝업 안의
  // 조작은 지도 이동이 아니라 제외한다.
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const handle = (event: Event) => {
      if (event.target instanceof Node && popupContainer.contains(event.target)) return;
      onUserGestureRef.current?.();
    };
    container.addEventListener('pointerdown', handle, true);
    container.addEventListener('wheel', handle, { capture: true, passive: true });
    return () => {
      container.removeEventListener('pointerdown', handle, true);
      container.removeEventListener('wheel', handle, true);
    };
  }, [popupContainer]);

  // 응답이 바뀌면 핀을 complexId로 비교해 바뀐 것만 다시 만든다.
  useEffect(() => {
    const maps = mapsRef.current;
    const clusterer = clustererRef.current;
    if (status !== 'ready' || !maps || !clusterer) return;
    const pins = pinsRef.current;
    const next = new Map(items.map((item) => [item.complexId, item]));

    const removed: KakaoCustomOverlay[] = [];
    pins.forEach((entry, id) => {
      const item = next.get(id);
      const signature = item ? `${item.latitude},${item.longitude},${pinLabel(item)}` : '';
      if (!item || signature !== entry.signature) {
        if (entry.clustered) removed.push(entry.overlay);
        else entry.overlay.setMap(null);
        pins.delete(id);
      }
    });
    if (removed.length) clusterer.removeMarkers(removed, true);

    const added: KakaoCustomOverlay[] = [];
    next.forEach((item, id) => {
      if (pins.has(id)) return;
      const button = document.createElement('button');
      button.type = 'button';
      button.className = PIN_BUTTON_CLASS;
      button.setAttribute('aria-label', pinLabel(item));
      button.dataset.complexId = String(id);
      const bubble = document.createElement('span');
      bubble.textContent = pinText(item);
      button.appendChild(bubble);
      button.addEventListener('click', () => callbacks.current.onSelectPin(id));
      const overlay = new maps.CustomOverlay({
        position: new maps.LatLng(item.latitude, item.longitude),
        content: button,
        xAnchor: 0.5,
        yAnchor: 1,
        clickable: true,
      });
      const entry: PinEntry = {
        overlay,
        button,
        bubble,
        signature: `${item.latitude},${item.longitude},${pinLabel(item)}`,
        approximate: item.precisionBadge === 'APPROXIMATE',
        clustered: selectedIdRef.current !== id,
      };
      applyPinState(entry, selectedIdRef.current === id);
      pins.set(id, entry);
      if (entry.clustered) added.push(overlay);
      else overlay.setMap(mapRef.current);
    });
    if (added.length) clusterer.addMarkers(added, true);
    if (removed.length || added.length) clusterer.redraw();
  }, [items, status]);

  // 선택 상태: 핀 강조와 z-index, 팝업 위치.
  const selectedItem = selectedId === null ? undefined : items.find((item) => item.complexId === selectedId);
  const hasPopup = popup != null;
  useEffect(() => {
    if (status !== 'ready') return;
    const maps = mapsRef.current;
    const map = mapRef.current;
    const clusterer = clustererRef.current;
    const overlay = popupOverlayRef.current;
    if (!maps || !map || !clusterer || !overlay) return;
    // 선택한 핀은 클러스터러에서 빼 지도에 직접 붙인다 — 넓은 레벨에서도 클러스터에 묶여 사라지지 않는다. 선택이 풀리면 되돌린다.
    let moved = false;
    let refocus: HTMLButtonElement | null = null;
    pinsRef.current.forEach((entry, id) => {
      const selected = id === selectedId;
      if (entry.clustered === selected) {
        // 옮기는 동안 핀 DOM이 잠깐 문서에서 빠진다 — 그 핀에 포커스가 있었으면(팝업을 닫고 핀으로 돌린 직후) 되돌린다.
        const hadFocus = entry.button.contains(document.activeElement);
        if (selected) {
          clusterer.removeMarkers([entry.overlay], true);
          entry.overlay.setMap(map);
        } else {
          entry.overlay.setMap(null);
          clusterer.addMarkers([entry.overlay], true);
        }
        entry.clustered = !selected;
        moved = true;
        if (hadFocus) refocus = entry.button;
      }
      applyPinState(entry, selected);
    });
    if (moved) clusterer.redraw();
    const focusTarget = refocus as HTMLButtonElement | null;
    if (focusTarget?.isConnected) focusTarget.focus();
    // 이미 지도에 붙어 있으면 위치만 바꾼다 — setMap을 다시 부르면 오버레이 DOM이 빠졌다 붙으며 팝업 안의 포커스를 잃는다.
    if (selectedItem && hasPopup) {
      overlay.setPosition(new maps.LatLng(selectedItem.latitude, selectedItem.longitude));
      if (!popupShownRef.current) {
        overlay.setMap(map);
        popupShownRef.current = true;
      }
    } else if (popupShownRef.current) {
      overlay.setMap(null);
      popupShownRef.current = false;
    }
  }, [selectedId, selectedItem, hasPopup, status]);

  useImperativeHandle(
    ref,
    () => ({
      panTo(lat, lng) {
        const maps = mapsRef.current;
        if (!maps) return;
        mapRef.current?.panTo(new maps.LatLng(lat, lng));
      },
      zoomBy(delta) {
        const map = mapRef.current;
        if (!map) return;
        const level = Math.min(MAX_LEVEL, Math.max(MIN_LEVEL, map.getLevel() + delta));
        map.setLevel(level, { animate: true });
      },
      moveTo(lat, lng) {
        const maps = mapsRef.current;
        if (!maps) return;
        mapRef.current?.setCenter(new maps.LatLng(lat, lng));
      },
      focusPin(complexId) {
        const button = pinsRef.current.get(complexId)?.button;
        if (!button || !button.isConnected) return false;
        button.focus();
        return true;
      },
      async moveToQuery(query, level, isCurrent) {
        const maps = mapsRef.current;
        const map = mapRef.current;
        if (!maps || !map) {
          if (statusRef.current !== 'loading') return 'not-found';
          startViewRef.current = { kind: 'region', query, level, fallback: startFallback(startViewRef.current) };
          return 'deferred';
        }
        const found = await geocode(maps, query);
        if (!isCurrent() || mapRef.current !== map) return 'stale';
        if (!found) return 'not-found';
        map.setLevel(level);
        map.setCenter(new maps.LatLng(found.lat, found.lng));
        return 'moved';
      },
    }),
    [],
  );

  return (
    <div className="relative h-full w-full overflow-hidden bg-[#eef3f8]">
      <div ref={containerRef} className="absolute inset-0" data-testid="map-canvas" aria-hidden={status !== 'ready'} />
      {status === 'loading' && (
        <div className="absolute inset-0 flex items-center justify-center text-[12px] text-[#99a1af]">지도를 불러오는 중…</div>
      )}
      {status === 'error' && (
        <div role="alert" className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-6 text-center" data-testid="map-error">
          <AlertTriangleIcon className="size-6 text-[#e7000b]" />
          <p className="text-[13px] font-semibold text-[#101828]">{errorMessage ?? '지도를 불러오지 못했습니다.'}</p>
          <p className="text-[12px] text-[#6a7282]">목록에서 단지를 계속 볼 수 있습니다.</p>
          <button
            type="button"
            onClick={() => {
              setStatus('loading');
              setAttempt((n) => n + 1);
            }}
            className="rounded-[10px] bg-white px-4 py-2 text-[13px] font-semibold text-brand shadow-[0_1px_2px_rgba(0,0,0,0.1)]"
          >
            다시 시도
          </button>
        </div>
      )}
      {status === 'ready' && children}
      {status === 'ready' && selectedItem && popup ? createPortal(popup, popupContainer) : null}
    </div>
  );
}
