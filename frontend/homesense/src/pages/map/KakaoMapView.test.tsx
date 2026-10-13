import { render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KakaoMapView } from './KakaoMapView';

// SDK는 불러오지 않는다 — 크기 감시(ResizeObserver)는 SDK 상태와 무관하게 마운트 때 붙는다.
vi.mock('../../features/map/kakaoLoader', () => ({ loadKakaoMaps: () => new Promise(() => {}) }));

class FakeResizeObserver {
  static instances: FakeResizeObserver[] = [];
  callback: ResizeObserverCallback;
  observed: Element[] = [];
  disconnected = false;
  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
    FakeResizeObserver.instances.push(this);
  }
  observe(target: Element) {
    this.observed.push(target);
  }
  unobserve() {}
  disconnect() {
    this.disconnected = true;
  }
  fire() {
    this.callback([], this as unknown as ResizeObserver);
  }
}

describe('KakaoMapView — 크기 감시 정리', () => {
  let nextFrame = 1;
  const pendingFrames = new Set<number>();
  const cancelled: number[] = [];

  beforeEach(() => {
    FakeResizeObserver.instances = [];
    pendingFrames.clear();
    cancelled.length = 0;
    vi.stubGlobal('ResizeObserver', FakeResizeObserver);
    vi.stubGlobal('requestAnimationFrame', () => {
      const id = nextFrame++;
      pendingFrames.add(id);
      return id;
    });
    vi.stubGlobal('cancelAnimationFrame', (id: number) => {
      cancelled.push(id);
      pendingFrames.delete(id);
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('언마운트하면 ResizeObserver를 끊고 예약된 프레임을 취소한다', () => {
    const { getByTestId, unmount } = render(
      <KakaoMapView
        startView={{ kind: 'point', viewport: { lat: 37.5, lng: 127, level: 6 } }}
        items={[]}
        selectedId={null}
        onIdle={() => {}}
        onSelectPin={() => {}}
        onMapClick={() => {}}
        popup={null}
      />,
    );
    const observer = FakeResizeObserver.instances.find((o) => o.observed.includes(getByTestId('map-canvas')));
    expect(observer).toBeDefined();

    // 크기 변경 → relayout·범위 알림 프레임이 예약된 채로 언마운트한다.
    observer!.fire();
    const scheduled = [...pendingFrames];
    expect(scheduled).toHaveLength(1);

    unmount();
    expect(observer!.disconnected).toBe(true);
    expect(cancelled).toContain(scheduled[0]);
    expect(pendingFrames.size).toBe(0);
  });

  it('크기 변경이 연달아 오면 앞 프레임을 취소하고 하나만 남긴다', () => {
    const { getByTestId, unmount } = render(
      <KakaoMapView
        startView={{ kind: 'point', viewport: { lat: 37.5, lng: 127, level: 6 } }}
        items={[]}
        selectedId={null}
        onIdle={() => {}}
        onSelectPin={() => {}}
        onMapClick={() => {}}
        popup={null}
      />,
    );
    const observer = FakeResizeObserver.instances.find((o) => o.observed.includes(getByTestId('map-canvas')))!;
    observer.fire();
    observer.fire();
    observer.fire();
    expect(pendingFrames.size).toBe(1);
    unmount();
  });
});
