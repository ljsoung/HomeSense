import { useEffect, useImperativeHandle, useRef, useState, type ReactNode, type Ref } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangleIcon } from '../../components/icons/AlertTriangleIcon';
import type { ComplexMapPointResponse } from '../../features/complex/types';
import { loadKakaoMaps } from '../../features/map/kakaoLoader';
import type { KakaoCluster, KakaoCustomOverlay, KakaoMap, KakaoMapsNamespace, KakaoMarkerClusterer } from '../../features/map/kakaoTypes';
import {
  approximateBounds,
  formatClusterCount,
  formatPinPrice,
  MAX_LEVEL,
  MIN_LEVEL,
  type MapBounds,
  type MapViewport,
} from '../../features/map/mapParams';
import { describeDealAmount } from '../../lib/format';

/** 시작 위치 — MapPage가 complexId 조회까지 끝낸 결과. 지역 라벨은 SDK Geocoder가 필요해 여기서 처리한다. */
export type StartView = { kind: 'point'; viewport: MapViewport } | { kind: 'region'; query: string; level: number; fallback: MapViewport };

export type MapStatus = 'loading' | 'ready' | 'error';

export interface KakaoMapHandle {
  panTo(lat: number, lng: number): void;
  zoomBy(delta: number): void;
  moveTo(lat: number, lng: number): void;
  /** 핀이 화면에 있으면(클러스터에 묶이지 않았으면) 포커스하고 true. */
  focusPin(complexId: number): boolean;
  /** 지역명·주소를 SDK로 찾아 그 중심으로 옮긴다. 못 찾거나 지도가 없으면 false. */
  moveToQuery(query: string, level: number): Promise<boolean>;
}

interface KakaoMapViewProps {
  ref?: Ref<KakaoMapHandle>;
  startView: StartView;
  items: ComplexMapPointResponse[];
  selectedId: number | null;
  /** 지도가 멈출 때(idle). SDK를 못 불러오면 화면 크기로 근사한 bounds로 한 번 부른다 — 목록 패널은 계속 동작한다. */
  onIdle: (viewport: MapViewport, bounds: MapBounds) => void;
  onSelectPin: (complexId: number) => void;
  onMapClick: () => void;
  /** SDK 상태가 바뀌면 알린다 — 지도가 없으면 목록 항목 선택이 팝업 대신 상세로 간다. */
  onStatusChange?: (status: MapStatus) => void;
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

const PIN_BUTTON_CLASS =
  'group flex min-h-11 min-w-11 cursor-pointer items-end justify-center bg-transparent p-0 focus-visible:outline-none';
const BUBBLE_BASE_CLASS =
  'flex h-[26px] items-center whitespace-nowrap rounded-[13px] border-2 px-2.5 text-[12px] font-bold leading-[18px] group-focus-visible:ring-2 group-focus-visible:ring-offset-2 group-focus-visible:ring-[#101828]';
// Figma 6:4595(정밀) / 6:4619(근사) / 6:4690(선택). 근사 색 #d97706은 Figma 핀·범례 값이다(카드 배지 amber와 다른 토큰).
const BUBBLE_TONE = {
  precise: { idle: 'border-brand bg-white text-brand', selected: 'border-brand bg-brand text-white' },
  approximate: { idle: 'border-[#d97706] bg-white text-[#d97706]', selected: 'border-[#d97706] bg-[#d97706] text-white' },
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

function pinLabel(item: ComplexMapPointResponse): string {
  const price = describeDealAmount(item.representativeDealCategory, item.rentType, item.representativeAmount, item.monthlyRentAmount);
  return `${item.complexName}, ${price}${item.precisionBadge === 'APPROXIMATE' ? ', 근사' : ''}`;
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
  popup,
  children,
}: KakaoMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapsRef = useRef<KakaoMapsNamespace | null>(null);
  const mapRef = useRef<KakaoMap | null>(null);
  const clustererRef = useRef<KakaoMarkerClusterer | null>(null);
  const pinsRef = useRef(new Map<number, PinEntry>());
  const popupOverlayRef = useRef<KakaoCustomOverlay | null>(null);
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
  useEffect(() => {
    onStatusChangeRef.current = onStatusChange;
  });
  useEffect(() => {
    onStatusChangeRef.current?.(status);
  }, [status]);
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

    const resolveStart = async (maps: KakaoMapsNamespace): Promise<MapViewport> => {
      const view = startViewRef.current;
      if (view.kind === 'point') return view.viewport;
      const found = await geocode(maps, view.query);
      return found ? { ...found, level: view.level } : view.fallback;
    };

    loadKakaoMaps()
      .then(async (maps) => {
        if (cancelled) return;
        const start = await resolveStart(maps);
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

        const handleIdle = () => {
          const center = map.getCenter();
          const b = map.getBounds();
          const sw = b.getSouthWest();
          const ne = b.getNorthEast();
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
    };
  }, [attempt, popupContainer]);

  // 패널 접기·창 크기 변경 등으로 지도 컨테이너 크기가 바뀌면 relayout한다.
  useEffect(() => {
    const container = containerRef.current;
    if (!container || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(() => mapRef.current?.relayout());
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

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
        removed.push(entry.overlay);
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
      bubble.textContent = formatPinPrice(item.representativeDealCategory, item.rentType, item.representativeAmount, item.monthlyRentAmount);
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
      };
      applyPinState(entry, selectedIdRef.current === id);
      pins.set(id, entry);
      added.push(overlay);
    });
    if (added.length) clusterer.addMarkers(added, true);
    if (removed.length || added.length) clusterer.redraw();
  }, [items, status]);

  // 선택 상태: 핀 강조와 z-index, 팝업 위치.
  const selectedItem = selectedId === null ? undefined : items.find((item) => item.complexId === selectedId);
  const hasPopup = popup != null;
  useEffect(() => {
    if (status !== 'ready') return;
    pinsRef.current.forEach((entry, id) => applyPinState(entry, id === selectedId));
    const maps = mapsRef.current;
    const map = mapRef.current;
    const overlay = popupOverlayRef.current;
    if (!maps || !map || !overlay) return;
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
      async moveToQuery(query, level) {
        const maps = mapsRef.current;
        const map = mapRef.current;
        if (!maps || !map) return false;
        const found = await geocode(maps, query);
        if (!found || mapRef.current !== map) return false;
        map.setLevel(level);
        map.setCenter(new maps.LatLng(found.lat, found.lng));
        return true;
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
