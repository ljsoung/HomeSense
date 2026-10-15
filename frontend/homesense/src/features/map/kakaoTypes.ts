/**
 * 카카오맵 JS SDK(dapi.kakao.com/v2/maps/sdk.js, libraries=clusterer,services) 중 MAP-01이 실제로 쓰는 범위만 선언한다.
 * SDK는 타입을 배포하지 않는다. any로 열어 두지 않고 호출하는 메서드만 적어, 잘못된 호출이 컴파일 에러로 드러나게 한다.
 * 새 메서드를 쓰게 되면 여기에 추가한다.
 */

export interface KakaoLatLng {
  getLat(): number;
  getLng(): number;
}

export interface KakaoLatLngBounds {
  getSouthWest(): KakaoLatLng;
  getNorthEast(): KakaoLatLng;
  extend(latlng: KakaoLatLng): void;
}

export interface KakaoMapOptions {
  center: KakaoLatLng;
  level: number;
}

export interface KakaoMap {
  getCenter(): KakaoLatLng;
  setCenter(latlng: KakaoLatLng): void;
  getLevel(): number;
  setLevel(level: number, options?: { animate?: boolean; anchor?: KakaoLatLng }): void;
  getBounds(): KakaoLatLngBounds;
  setBounds(bounds: KakaoLatLngBounds): void;
  panTo(latlng: KakaoLatLng): void;
  relayout(): void;
}

export interface KakaoCustomOverlayOptions {
  position: KakaoLatLng;
  content: HTMLElement;
  xAnchor?: number;
  yAnchor?: number;
  zIndex?: number;
  clickable?: boolean;
  map?: KakaoMap | null;
}

export interface KakaoCustomOverlay {
  setMap(map: KakaoMap | null): void;
  setPosition(latlng: KakaoLatLng): void;
  setZIndex(zIndex: number): void;
  getPosition(): KakaoLatLng;
}

export interface KakaoCluster {
  getBounds(): KakaoLatLngBounds;
  getCenter(): KakaoLatLng;
}

export interface KakaoMarkerClustererOptions {
  map: KakaoMap;
  averageCenter?: boolean;
  minLevel?: number;
  gridSize?: number;
  disableClickZoom?: boolean;
  styles?: Partial<CSSStyleDeclaration>[] | Record<string, string>[];
  texts?: (count: number) => string;
  calculator?: number[];
}

export interface KakaoMarkerClusterer {
  addMarkers(markers: KakaoCustomOverlay[], nodraw?: boolean): void;
  removeMarkers(markers: KakaoCustomOverlay[], nodraw?: boolean): void;
  clear(): void;
  redraw(): void;
}

export interface KakaoGeocoderResult {
  x: string; // 경도
  y: string; // 위도
}

export type KakaoServiceStatus = 'OK' | 'ZERO_RESULT' | 'ERROR';

export interface KakaoGeocoder {
  addressSearch(query: string, callback: (result: KakaoGeocoderResult[], status: KakaoServiceStatus) => void): void;
}

export interface KakaoPlaces {
  keywordSearch(query: string, callback: (result: KakaoGeocoderResult[], status: KakaoServiceStatus) => void): void;
}

export interface KakaoMapsNamespace {
  load(callback: () => void): void;
  LatLng: new (lat: number, lng: number) => KakaoLatLng;
  LatLngBounds: new (sw?: KakaoLatLng, ne?: KakaoLatLng) => KakaoLatLngBounds;
  Map: new (container: HTMLElement, options: KakaoMapOptions) => KakaoMap;
  CustomOverlay: new (options: KakaoCustomOverlayOptions) => KakaoCustomOverlay;
  MarkerClusterer: new (options: KakaoMarkerClustererOptions) => KakaoMarkerClusterer;
  event: {
    addListener(target: KakaoMap | KakaoMarkerClusterer, type: string, handler: (...args: never[]) => void): void;
    removeListener(target: KakaoMap | KakaoMarkerClusterer, type: string, handler: (...args: never[]) => void): void;
  };
  services: {
    Geocoder: new () => KakaoGeocoder;
    Places: new () => KakaoPlaces;
    Status: { OK: KakaoServiceStatus; ZERO_RESULT: KakaoServiceStatus; ERROR: KakaoServiceStatus };
  };
}

export interface KakaoNamespace {
  maps: KakaoMapsNamespace;
}

declare global {
  interface Window {
    kakao?: KakaoNamespace;
  }
}
