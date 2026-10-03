import { act, cleanup, fireEvent, render, renderHook, screen } from '@testing-library/react';
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { useToast } from '../../components/ui/useToast';
import { UNDO_WINDOW_MS, usePendingDeletion, type PendingDeletion } from './usePendingDeletion';

function serverError(status: number, code: string, message: string): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig;
  const response = {
    status,
    statusText: String(status),
    headers: {},
    config,
    data: { success: false, data: null, error: { code, message }, timestamp: '' },
  } as AxiosResponse;
  return new AxiosError(`status ${status}`, 'ERR_BAD_REQUEST', config, null, response);
}

const wrapper = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;

function entry(key: string, overrides: Partial<PendingDeletion> = {}) {
  return {
    key,
    commit: vi.fn(async () => {}),
    onCommitted: vi.fn(),
    onRestored: vi.fn(),
    ...overrides,
  } satisfies PendingDeletion;
}

/** 비동기 commit의 then 체인까지 흘려보낸다. */
async function settle() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('usePendingDeletion — 삭제 확인 뒤 5초 지연 DELETE와 실행취소(D6)', () => {
  it('곧바로 숨기고 토스트를 띄우며, 5초가 지나야 DELETE를 보낸다', async () => {
    const { result } = renderHook(() => usePendingDeletion(), { wrapper });
    const target = entry('property:1');
    act(() => result.current.schedule(target));

    expect(result.current.hiddenKeys.has('property:1')).toBe(true);
    expect(screen.getByText('삭제했어요')).toBeTruthy();
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS - 1));
    expect(target.commit).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(1));
    await settle();
    expect(target.commit).toHaveBeenCalledTimes(1);
    expect(target.onCommitted).toHaveBeenCalledTimes(1);
    expect(result.current.hiddenKeys.has('property:1')).toBe(false);
    expect(screen.queryByText('삭제했어요')).toBeNull();
  });

  it('실행취소하면 DELETE를 보내지 않고 숨김을 푼다', async () => {
    const { result } = renderHook(() => usePendingDeletion(), { wrapper });
    const target = entry('property:1');
    act(() => result.current.schedule(target));

    act(() => screen.getByRole('button', { name: '실행취소' }).click());
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS * 2));
    await settle();

    expect(target.commit).not.toHaveBeenCalled();
    expect(target.onRestored).toHaveBeenCalledTimes(1);
    expect(result.current.hiddenKeys.size).toBe(0);
    expect(screen.queryByText('삭제했어요')).toBeNull();
  });

  it('대기 중에 다른 항목을 지우면 앞 건은 즉시 확정하고 토스트는 마지막 하나만 남는다', async () => {
    const { result } = renderHook(() => usePendingDeletion(), { wrapper });
    const first = entry('property:1');
    const second = entry('region:2');
    act(() => result.current.schedule(first));
    act(() => vi.advanceTimersByTime(1000));
    act(() => result.current.schedule(second));
    await settle();

    expect(first.commit).toHaveBeenCalledTimes(1);
    expect(second.commit).not.toHaveBeenCalled();
    expect(screen.getAllByText('삭제했어요')).toHaveLength(1);

    // 두 번째 건은 자기 5초를 새로 센다.
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS - 1));
    expect(second.commit).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(1));
    await settle();
    expect(second.commit).toHaveBeenCalledTimes(1);
    expect(first.commit).toHaveBeenCalledTimes(1);
  });

  it('언마운트되면 대기 건을 즉시 확정한다', async () => {
    const { result, unmount } = renderHook(() => usePendingDeletion(), { wrapper });
    const target = entry('property:1');
    act(() => result.current.schedule(target));

    unmount();
    await settle();
    expect(target.commit).toHaveBeenCalledTimes(1);
  });

  it('이미 지워진 경우(FAVORITE_NOT_FOUND)는 성공으로 본다', async () => {
    const { result } = renderHook(() => usePendingDeletion(), { wrapper });
    const target = entry('property:1', {
      commit: vi.fn(async () => {
        throw serverError(404, 'FAVORITE_NOT_FOUND', '존재하지 않는 관심 등록입니다');
      }),
    });
    act(() => result.current.schedule(target));
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS));
    await settle();

    expect(target.onCommitted).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('존재하지 않는 관심 등록입니다')).toBeNull();
  });

  it('그 밖의 실패는 항목을 되살리고 서버 문구로 오류 토스트를 띄운다', async () => {
    const { result } = renderHook(() => usePendingDeletion(), { wrapper });
    const target = entry('property:1', {
      commit: vi.fn(async () => {
        throw serverError(403, 'ACCESS_DENIED', '본인의 관심 등록만 삭제할 수 있습니다');
      }),
    });
    act(() => result.current.schedule(target));
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS));
    await settle();

    expect(target.onCommitted).not.toHaveBeenCalled();
    expect(result.current.hiddenKeys.has('property:1')).toBe(false);
    expect(screen.getByText('본인의 관심 등록만 삭제할 수 있습니다')).toBeTruthy();
  });

  it('토스트에 마우스를 올리거나 포커스가 있으면 시간이 멈춘다(WCAG 2.2.1)', async () => {
    const { result } = renderHook(() => usePendingDeletion(), { wrapper });
    const target = entry('property:1');
    act(() => result.current.schedule(target));
    const toast = screen.getByRole('status');

    act(() => vi.advanceTimersByTime(2000));
    fireEvent.mouseEnter(toast);
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS * 3));
    expect(target.commit).not.toHaveBeenCalled();
    fireEvent.mouseLeave(toast);

    act(() => screen.getByRole('button', { name: '실행취소' }).focus());
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS * 3));
    expect(target.commit).not.toHaveBeenCalled();
    act(() => screen.getByRole('button', { name: '실행취소' }).blur());

    // 멈추기 전에 2초가 흘렀으니 남은 3초 뒤 확정된다.
    act(() => vi.advanceTimersByTime(2999));
    expect(target.commit).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(1));
    await settle();
    expect(target.commit).toHaveBeenCalledTimes(1);
  });
});

describe('ToastProvider — 버튼 없는 토스트는 예전 동작 그대로', () => {
  it('3초 뒤 닫히고 마우스를 올려도 멈추지 않는다', () => {
    function Shower() {
      const { showToast } = useToast();
      return (
        <button type="button" onClick={() => showToast('저장했어요')}>
          show
        </button>
      );
    }
    render(
      <ToastProvider>
        <Shower />
      </ToastProvider>,
    );
    act(() => screen.getByRole('button', { name: 'show' }).click());
    fireEvent.mouseEnter(screen.getByRole('status'));
    act(() => vi.advanceTimersByTime(3000));
    expect(screen.queryByText('저장했어요')).toBeNull();
  });
});
