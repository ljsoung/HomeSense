import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { BrowserRouter, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { useReplaceMapUrl } from './useReplaceMapUrl';

function mount() {
  const { result } = renderHook(() => ({ replace: useReplaceMapUrl(), search: useLocation().search }), {
    wrapper: ({ children }: { children: ReactNode }) => <BrowserRouter>{children}</BrowserRouter>,
  });
  return result;
}

describe('useReplaceMapUrl', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/map?lat=37.5&lng=127&level=6');
  });

  it('다시 렌더되기 전에 두 번 불러도 두 갱신이 모두 남는다(호출 시점의 주소로 다음 URL을 만든다)', () => {
    const result = mount();
    act(() => {
      result.current.replace((prev) => ({ ...prev, complexId: 7 }));
      result.current.replace((prev) => ({ ...prev, viewport: { lat: 37.3, lng: 127.01, level: 5 } }));
    });
    const params = new URLSearchParams(window.location.search);
    expect(params.get('complexId')).toBe('7');
    expect(params.get('lat')).toBe('37.3');
    expect(params.get('level')).toBe('5');
    expect(result.current.search).toBe(window.location.search);
  });

  it('replace로 바꿔 히스토리를 쌓지 않고, 바뀌는 것이 없으면 이동하지 않는다', () => {
    const result = mount();
    const length = window.history.length;
    act(() => result.current.replace((prev) => ({ ...prev, complexId: 3 })));
    expect(window.history.length).toBe(length);
    const state = window.history.state;
    act(() => result.current.replace((prev) => ({ ...prev })));
    // 이동했다면 react-router가 새 history state(key)를 쓴다.
    expect(window.history.state).toBe(state);
  });
});
