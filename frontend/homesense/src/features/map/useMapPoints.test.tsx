import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ComplexMapPointResponse, ComplexMapSearchResponse } from '../complex/types';
import { searchComplexesInBounds } from './api';
import { MAP_FETCH_DEBOUNCE_MS, useMapPoints } from './useMapPoints';

vi.mock('./api', () => ({ searchComplexesInBounds: vi.fn() }));

const mockedSearch = vi.mocked(searchComplexesInBounds);

function response(complexId: number): ComplexMapSearchResponse {
  return { items: [{ complexId } as ComplexMapPointResponse], truncated: false, limit: 500 };
}

interface Pending {
  signal: AbortSignal | undefined;
  resolve: (value: ComplexMapSearchResponse) => void;
}

describe('useMapPoints', () => {
  let pending: Pending[];

  beforeEach(() => {
    vi.useFakeTimers();
    pending = [];
    // 끊겨도 응답이 오는 경우(이미 도착한 응답)를 흉내 내려고 signal을 무시하고 직접 resolve한다.
    mockedSearch.mockImplementation(
      (_query, signal) =>
        new Promise<ComplexMapSearchResponse>((resolve) => {
          pending.push({ signal, resolve });
        }),
    );
  });

  afterEach(() => {
    vi.useRealTimers();
    mockedSearch.mockReset();
  });

  it('조회가 바뀌면 디바운스 중이라도 옛 요청을 끊고, 그 사이 도착한 옛 응답을 버린다', async () => {
    const { result, rerender } = renderHook(({ q }) => useMapPoints(new URLSearchParams(q)), {
      initialProps: { q: 'bounds=a' },
    });
    act(() => vi.advanceTimersByTime(MAP_FETCH_DEBOUNCE_MS));
    expect(pending).toHaveLength(1);

    rerender({ q: 'bounds=b' });
    expect(pending[0].signal?.aborted).toBe(true);

    // 새 디바운스가 끝나기 전에 옛 응답이 도착한다.
    await act(async () => pending[0].resolve(response(1)));
    expect(result.current.items).toEqual([]);
    expect(result.current.initialLoading).toBe(true);

    act(() => vi.advanceTimersByTime(MAP_FETCH_DEBOUNCE_MS));
    expect(pending).toHaveLength(2);
    await act(async () => pending[1].resolve(response(2)));
    expect(result.current.items.map((item) => item.complexId)).toEqual([2]);
    expect(result.current.initialLoading).toBe(false);
  });

  it('조건 변경으로 끊긴 요청의 오류는 오류 상태·배너로 이어지지 않는다', async () => {
    // 실제 axios처럼 abort되면 reject한다. 취소 판별(isCancel)이 아니라 요청 번호로 걸러지는지 보려고 일반 Error로 던진다.
    mockedSearch.mockImplementation(
      (_query, signal) =>
        new Promise<ComplexMapSearchResponse>((resolve, reject) => {
          pending.push({ signal, resolve });
          signal?.addEventListener('abort', () => reject(new Error('aborted')));
        }),
    );
    const { result, rerender } = renderHook(({ q }) => useMapPoints(new URLSearchParams(q)), {
      initialProps: { q: 'bounds=a' },
    });
    act(() => vi.advanceTimersByTime(MAP_FETCH_DEBOUNCE_MS));
    expect(pending).toHaveLength(1);

    await act(async () => rerender({ q: 'bounds=b' }));
    expect(pending[0].signal?.aborted).toBe(true);
    expect(result.current.errorMessage).toBeNull();
    expect(result.current.initialLoading).toBe(true);
  });

  it('첫 응답 뒤 조건이 바뀌면 새 응답이 올 때까지 기존 항목을 유지하고, 도착하면 바꾼다', async () => {
    const { result, rerender } = renderHook(({ q }) => useMapPoints(new URLSearchParams(q)), {
      initialProps: { q: 'bounds=a' },
    });
    act(() => vi.advanceTimersByTime(MAP_FETCH_DEBOUNCE_MS));
    await act(async () => pending[0].resolve(response(1)));
    expect(result.current.items.map((item) => item.complexId)).toEqual([1]);

    rerender({ q: 'bounds=b' });
    expect(result.current.items.map((item) => item.complexId)).toEqual([1]);
    act(() => vi.advanceTimersByTime(MAP_FETCH_DEBOUNCE_MS - 1));
    expect(result.current.items.map((item) => item.complexId)).toEqual([1]);
    act(() => vi.advanceTimersByTime(1));
    expect(pending).toHaveLength(2);
    // 요청 중에도 비우지 않는다.
    expect(result.current.items.map((item) => item.complexId)).toEqual([1]);
    expect(result.current.initialLoading).toBe(false);

    await act(async () => pending[1].resolve(response(2)));
    expect(result.current.items.map((item) => item.complexId)).toEqual([2]);
  });

  it('언마운트하면 진행 중 요청을 끊는다', () => {
    const { unmount } = renderHook(() => useMapPoints(new URLSearchParams('bounds=a')));
    act(() => vi.advanceTimersByTime(MAP_FETCH_DEBOUNCE_MS));
    unmount();
    expect(pending[0].signal?.aborted).toBe(true);
  });
});
