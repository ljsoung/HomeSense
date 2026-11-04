import { act, cleanup, renderHook } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { lookupRegion } from './api';
import type { RegionAutocompleteResponse } from './types';
import { clearRegionNameCache, useRegionName } from './useRegionName';

vi.mock('./api', () => ({ lookupRegion: vi.fn() }));

interface Pending {
  code: string;
  signal?: AbortSignal;
  resolve: (value: RegionAutocompleteResponse) => void;
  reject: (error: unknown) => void;
}
let pending: Pending[] = [];

beforeEach(() => {
  pending = [];
  clearRegionNameCache();
  vi.mocked(lookupRegion).mockImplementation(
    (code, signal) => new Promise((resolve, reject) => pending.push({ code, signal, resolve, reject })),
  );
});

afterEach(() => {
  cleanup();
  vi.mocked(lookupRegion).mockReset();
});

function notFound() {
  const headers = new AxiosHeaders();
  return new AxiosError('404', 'ERR_BAD_REQUEST', undefined, undefined, {
    status: 404,
    statusText: 'Not Found',
    headers,
    config: { headers },
    data: {},
  });
}

async function settle(fn: () => void) {
  await act(async () => {
    fn();
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe('useRegionName', () => {
  it('이름을 이미 알거나 코드가 없으면 조회하지 않는다', () => {
    renderHook(() => useRegionName('1168010100', '서울특별시 강남구 역삼동'));
    renderHook(() => useRegionName(undefined, undefined));
    expect(lookupRegion).not.toHaveBeenCalled();
  });

  it('코드만 있으면 조회해 이름을 돌려주고, 같은 코드는 다시 묻지 않는다', async () => {
    const first = renderHook(() => useRegionName('1168010100', undefined));
    expect(first.result.current).toBeNull();
    await settle(() => pending[0].resolve({ legalDongCd: '1168010100', fullPath: '서울특별시 강남구 역삼동' }));
    expect(first.result.current).toBe('서울특별시 강남구 역삼동');

    const second = renderHook(() => useRegionName('1168010100', undefined));
    expect(second.result.current).toBe('서울특별시 강남구 역삼동');
    expect(lookupRegion).toHaveBeenCalledTimes(1);
  });

  it('404면 null이고 다시 묻지 않는다, 일시 실패는 null이지만 다음에 다시 묻는다', async () => {
    const gone = renderHook(() => useRegionName('2811000000', undefined));
    await settle(() => pending[0].reject(notFound()));
    expect(gone.result.current).toBeNull();
    renderHook(() => useRegionName('2811000000', undefined));
    expect(lookupRegion).toHaveBeenCalledTimes(1);

    const flaky = renderHook(() => useRegionName('4111112900', undefined));
    await settle(() => pending[1].reject(new Error('network')));
    expect(flaky.result.current).toBeNull();
    renderHook(() => useRegionName('4111112900', undefined));
    expect(lookupRegion).toHaveBeenCalledTimes(3);
  });

  it('코드가 바뀌면 이전 요청을 끊고, 늦게 온 이전 결과를 돌려주지 않는다', async () => {
    const hook = renderHook(({ code }) => useRegionName(code, undefined), { initialProps: { code: '1168010100' } });
    hook.rerender({ code: '4111112900' });
    expect(pending[0].signal?.aborted).toBe(true);
    expect(hook.result.current).toBeNull();

    await settle(() => pending[1].resolve({ legalDongCd: '4111112900', fullPath: '경기도 수원시 장안구 파장동' }));
    expect(hook.result.current).toBe('경기도 수원시 장안구 파장동');
  });
});
