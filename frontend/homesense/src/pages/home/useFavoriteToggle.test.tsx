import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { AuthContext, type AuthContextValue, type AuthStatus } from '../../features/auth/authContext';
import type { FavoritePropertySummaryResponse } from '../../features/favorite/types';
import { useFavoriteToggle } from './useFavoriteToggle';

// 다른 탭의 계정 변경으로 authenticated(A) → checking → authenticated(B)가 될 때, 확인 중에 미룬 하트 클릭이
// A의 하트 상태가 아니라 B의 관심 목록을 받은 뒤에 처리되는지 검증한다.

const api = vi.hoisted(() => ({
  getFavoriteProperties: vi.fn(),
  addFavoriteProperty: vi.fn(),
  removeFavoriteProperty: vi.fn(),
}));
vi.mock('../../features/favorite/api', () => api);

function favorite(complexId: number, favoritePropertyId: number): FavoritePropertySummaryResponse {
  return {
    favoritePropertyId,
    complexId,
    complexName: `단지${complexId}`,
    sido: '서울특별시',
    sigungu: '종로구',
    dongRi: '숭인동',
    housingType: 'APT',
    recentDealCategory: null,
    recentDealDate: null,
    recentAmount: null,
    changeRate: null,
    hasNotificationSetting: false,
  } as FavoritePropertySummaryResponse;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

function renderWithStatus(initial: AuthStatus) {
  let status = initial;
  const auth = (): AuthContextValue => ({
    status,
    user: null,
    login: async () => {},
    signup: async () => {},
    logout: async () => {},
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter>
      <ToastProvider>
        <AuthContext.Provider value={auth()}>{children}</AuthContext.Provider>
      </ToastProvider>
    </MemoryRouter>
  );
  const hook = renderHook(() => useFavoriteToggle(), { wrapper });
  return {
    ...hook,
    setStatus: (next: AuthStatus) => {
      status = next;
      hook.rerender();
    },
  };
}

beforeEach(() => {
  api.getFavoriteProperties.mockReset();
  api.addFavoriteProperty.mockReset();
  api.removeFavoriteProperty.mockReset();
  api.addFavoriteProperty.mockResolvedValue({ favoritePropertyId: 200 });
  api.removeFavoriteProperty.mockResolvedValue(undefined);
});

afterEach(() => {
  cleanup();
});

describe('useFavoriteToggle — 다른 계정으로 다시 확인', () => {
  it('확인 중에 미룬 클릭은 A의 하트 상태가 아니라 B의 목록을 받은 뒤 처리된다', async () => {
    // 계정 A: 단지 10을 찜해 둔 상태(favoritePropertyId 100).
    api.getFavoriteProperties.mockResolvedValueOnce([favorite(10, 100)]);
    const view = renderWithStatus('authenticated');
    await waitFor(() => expect(view.result.current.favoritedIds.has(10)).toBe(true));

    // 다른 탭이 B로 로그인 → 다시 확인. 확인 중에는 A의 하트 상태를 버린다.
    act(() => view.setStatus('checking'));
    expect(view.result.current.favoritedIds.size).toBe(0);

    // 확인 중 단지 10의 하트를 누른다 — 판정까지 미뤄진다.
    act(() => view.result.current.toggleFavorite(10));
    expect(view.result.current.pendingFavoriteId).toBe(10);

    // B로 확정. B의 관심 목록 응답은 아직 붙잡혀 있다.
    const listB = deferred<FavoritePropertySummaryResponse[]>();
    api.getFavoriteProperties.mockReturnValueOnce(listB.promise);
    act(() => view.setStatus('authenticated'));
    await act(async () => {
      await Promise.resolve();
    });
    // 수정 전: A의 하트 상태(10→100)로 곧바로 DELETE /favorites/properties/100을 B 토큰으로 보냈다.
    expect(api.removeFavoriteProperty).not.toHaveBeenCalled();
    expect(api.addFavoriteProperty).not.toHaveBeenCalled();

    // B의 목록(단지 10 없음)을 받은 뒤에야 처리된다 — B 기준으로는 등록이다.
    await act(async () => {
      listB.resolve([]);
    });
    await waitFor(() => expect(api.addFavoriteProperty).toHaveBeenCalledWith(10));
    expect(api.removeFavoriteProperty).not.toHaveBeenCalled();
    expect(view.result.current.pendingFavoriteId).toBeNull();
  });
});

// 확인 중 클릭은 "토글"이 아니라 클릭 순간 보이던 하트 기준의 의도로 재생한다. 빈 하트를 눌렀는데 목록을 받아
// 보니 이미 등록돼 있으면 요청을 보내지 않는다(토글이었다면 해제 요청이 나갔다).
describe('useFavoriteToggle — 확인 중 클릭은 의도로 재생', () => {
  async function clickDuringCheckingThenConfirm(
    view: ReturnType<typeof renderWithStatus>,
    listB: FavoritePropertySummaryResponse[],
  ) {
    expect(view.result.current.favoritedIds.has(10)).toBe(false); // 클릭 순간 빈 하트
    act(() => view.result.current.toggleFavorite(10));
    expect(view.result.current.pendingFavoriteId).toBe(10);
    api.getFavoriteProperties.mockResolvedValueOnce(listB);
    act(() => view.setStatus('authenticated'));
    await waitFor(() => expect(view.result.current.favoritedIds.has(10)).toBe(true));
    await waitFor(() => expect(view.result.current.pendingFavoriteId).toBeNull());
    await act(async () => {
      await Promise.resolve();
    });
  }

  it('계정 전환 뒤 확인 중: B가 이미 등록한 단지를 빈 하트로 누르면 목록 도착 후 요청 0건', async () => {
    api.getFavoriteProperties.mockResolvedValueOnce([favorite(10, 100)]); // 계정 A
    const view = renderWithStatus('authenticated');
    await waitFor(() => expect(view.result.current.favoritedIds.has(10)).toBe(true));
    act(() => view.setStatus('checking'));

    await clickDuringCheckingThenConfirm(view, [favorite(10, 300)]); // 계정 B도 단지 10을 등록해 둠

    expect(api.addFavoriteProperty).not.toHaveBeenCalled();
    expect(api.removeFavoriteProperty).not.toHaveBeenCalled();
  });

  it('첫 로딩 확인 중: 이미 등록한 단지를 빈 하트로 누르면 목록 도착 후 요청 0건', async () => {
    const view = renderWithStatus('checking');

    await clickDuringCheckingThenConfirm(view, [favorite(10, 300)]);

    expect(api.addFavoriteProperty).not.toHaveBeenCalled();
    expect(api.removeFavoriteProperty).not.toHaveBeenCalled();
  });

  it('첫 로딩 확인 중: 등록되지 않은 단지를 빈 하트로 누르면 목록 도착 후 등록 1회', async () => {
    const view = renderWithStatus('checking');

    await clickDuringCheckingThenConfirm(view, []);

    expect(api.addFavoriteProperty).toHaveBeenCalledTimes(1);
    expect(api.addFavoriteProperty).toHaveBeenCalledWith(10);
    expect(api.removeFavoriteProperty).not.toHaveBeenCalled();
  });
});
