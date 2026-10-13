import { isValidViewport, type MapViewport } from './mapParams';

const STORAGE_KEY = 'homesense.map.lastViewport';

/**
 * 마지막으로 본 지도 위치(초기 중심 결정 4순위). 기기별 편의 기능이라 localStorage에 두고, 읽기·쓰기 모두 실패해도
 * (사생활 보호 모드, 저장소 차단) 지도는 다음 순위(기본 위치)로 정상 동작한다.
 */
export function readLastViewport(): MapViewport | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    return isValidViewport(parsed as Partial<MapViewport>) ? (parsed as MapViewport) : null;
  } catch {
    return null;
  }
}

export function writeLastViewport(viewport: MapViewport): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(viewport));
  } catch {
    // 저장소를 쓸 수 없으면 다음 진입이 기본 위치로 간다 — 지도 동작에는 영향이 없다.
  }
}
