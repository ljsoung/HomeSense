import { act, cleanup, renderHook, screen, waitFor } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/ToastProvider';
import { AuthContext, type AuthContextValue, type AuthStatus } from '../../features/auth/authContext';
import type { FavoritePropertySummaryResponse } from '../../features/favorite/types';
import { advanceSessionGeneration, __resetSessionStateForTests } from '../../features/auth/session';
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

function serverError(status: number, code: string, message: string) {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, {
    status,
    statusText: '',
    headers: {},
    config,
    data: { success: false, data: null, error: { code, message }, timestamp: '' },
  });
}

// DTL-01 결정 4 — 등록/해제가 실패하면 상태 코드와 무관하게 서버 문구를 보이고, 서버의 관심 목록으로 하트를
// 다시 맞춘다(409를 따로 분기하지 않는다). 요청이 진행 중인 단지는 두 번째 클릭을 보내지 않는다.
describe('useFavoriteToggle — 실패 시 서버 문구와 재동기화', () => {
  it('등록이 409로 실패하면 서버 문구를 보이고 목록을 다시 받아 채워진 하트로 맞춘다', async () => {
    api.getFavoriteProperties.mockResolvedValueOnce([]);
    const view = renderWithStatus('authenticated');
    await waitFor(() => expect(api.getFavoriteProperties).toHaveBeenCalledTimes(1));
    api.addFavoriteProperty.mockRejectedValueOnce(serverError(409, 'DUPLICATE_FAVORITE', '이미 관심 매물로 등록된 단지입니다'));
    api.getFavoriteProperties.mockResolvedValueOnce([favorite(10, 100)]);

    await act(async () => view.result.current.toggleFavorite(10));

    expect(await screen.findByText('이미 관심 매물로 등록된 단지입니다')).toBeTruthy();
    await waitFor(() => expect(view.result.current.favoritedIds.has(10)).toBe(true));
    expect(api.getFavoriteProperties).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(view.result.current.processingIds.size).toBe(0));
  });

  it('해제가 404로 실패하면 서버 문구를 보이고 목록을 다시 받아 빈 하트로 맞춘다', async () => {
    api.getFavoriteProperties.mockResolvedValueOnce([favorite(10, 100)]);
    const view = renderWithStatus('authenticated');
    await waitFor(() => expect(view.result.current.favoritedIds.has(10)).toBe(true));
    api.removeFavoriteProperty.mockRejectedValueOnce(serverError(404, 'FAVORITE_NOT_FOUND', '존재하지 않는 관심 등록입니다'));
    api.getFavoriteProperties.mockResolvedValueOnce([]);

    await act(async () => view.result.current.toggleFavorite(10));

    expect(await screen.findByText('존재하지 않는 관심 등록입니다')).toBeTruthy();
    await waitFor(() => expect(view.result.current.favoritedIds.has(10)).toBe(false));
  });

  it('요청이 진행 중인 단지를 다시 눌러도 두 번째 요청을 보내지 않는다', async () => {
    api.getFavoriteProperties.mockResolvedValueOnce([]);
    const view = renderWithStatus('authenticated');
    await waitFor(() => expect(api.getFavoriteProperties).toHaveBeenCalledTimes(1));
    const pending = deferred<{ favoritePropertyId: number }>();
    api.addFavoriteProperty.mockReturnValueOnce(pending.promise);

    act(() => view.result.current.toggleFavorite(10));
    await waitFor(() => expect(view.result.current.processingIds.has(10)).toBe(true));
    act(() => view.result.current.toggleFavorite(10));
    expect(api.addFavoriteProperty).toHaveBeenCalledTimes(1);

    await act(async () => pending.resolve({ favoritePropertyId: 100 }));
    await waitFor(() => expect(view.result.current.processingIds.has(10)).toBe(false));
    expect(view.result.current.favoritedIds.has(10)).toBe(true);
  });
});

// 계정 A에서 시작한 등록/해제·재동기화의 늦은 응답이 계정 B의 하트 상태를 덮어쓰지 않는지(Codex P2).
describe('useFavoriteToggle — 다른 계정의 늦은 응답은 버린다', () => {
  afterEach(() => {
    __resetSessionStateForTests();
  });

  it('A에서 실패한 등록의 재동기화 응답이 B로 바뀐 뒤 오면 B의 하트 상태를 덮어쓰지 않는다', async () => {
    api.getFavoriteProperties.mockResolvedValueOnce([]); // A 하이드레이션
    const view = renderWithStatus('authenticated');
    await waitFor(() => expect(api.getFavoriteProperties).toHaveBeenCalledTimes(1));

    api.addFavoriteProperty.mockRejectedValueOnce(serverError(409, 'DUPLICATE_FAVORITE', '이미 관심 매물로 등록된 단지입니다'));
    const resyncA = deferred<FavoritePropertySummaryResponse[]>();
    api.getFavoriteProperties.mockReturnValueOnce(resyncA.promise); // A 재동기화(붙잡음)
    act(() => view.result.current.toggleFavorite(10));
    await waitFor(() => expect(api.getFavoriteProperties).toHaveBeenCalledTimes(2));

    // 다른 탭이 B로 로그인 → 다시 확인 → B로 확정, B의 목록은 단지 20(favoritePropertyId 300).
    api.getFavoriteProperties.mockResolvedValueOnce([favorite(20, 300)]);
    act(() => view.setStatus('checking'));
    act(() => view.setStatus('authenticated'));
    await waitFor(() => expect(view.result.current.favoritedIds.has(20)).toBe(true));

    // A의 재동기화 응답이 이제 도착한다.
    await act(async () => resyncA.resolve([favorite(10, 100)]));

    expect(view.result.current.favoritedIds.has(20)).toBe(true);
    expect(view.result.current.favoritedIds.has(10)).toBe(false);
  });

  it('A에서 보낸 등록의 성공 응답이 B로 바뀐 뒤 오면 B의 하트 상태에 넣지 않는다', async () => {
    api.getFavoriteProperties.mockResolvedValueOnce([]);
    const view = renderWithStatus('authenticated');
    await waitFor(() => expect(api.getFavoriteProperties).toHaveBeenCalledTimes(1));

    const addA = deferred<{ favoritePropertyId: number }>();
    api.addFavoriteProperty.mockReturnValueOnce(addA.promise);
    act(() => view.result.current.toggleFavorite(10));

    api.getFavoriteProperties.mockResolvedValueOnce([]);
    act(() => view.setStatus('checking'));
    act(() => view.setStatus('authenticated'));
    await waitFor(() => expect(api.getFavoriteProperties).toHaveBeenCalledTimes(2));

    await act(async () => addA.resolve({ favoritePropertyId: 100 }));

    expect(view.result.current.favoritedIds.has(10)).toBe(false);
  });

  it('상태 값이 그대로여도 세션 세대가 바뀌면 재동기화 응답을 버린다', async () => {
    api.getFavoriteProperties.mockResolvedValueOnce([]);
    const view = renderWithStatus('authenticated');
    await waitFor(() => expect(api.getFavoriteProperties).toHaveBeenCalledTimes(1));

    api.removeFavoriteProperty.mockRejectedValueOnce(serverError(404, 'FAVORITE_NOT_FOUND', '존재하지 않는 관심 등록입니다'));
    api.getFavoriteProperties.mockResolvedValueOnce([favorite(10, 100)]); // 첫 클릭(등록 409)의 재동기화로 하트를 채운다
    api.addFavoriteProperty.mockRejectedValueOnce(serverError(409, 'DUPLICATE_FAVORITE', '이미 관심 매물로 등록된 단지입니다'));
    await act(async () => view.result.current.toggleFavorite(10));
    await waitFor(() => expect(view.result.current.favoritedIds.has(10)).toBe(true));

    const resync = deferred<FavoritePropertySummaryResponse[]>();
    api.getFavoriteProperties.mockReturnValueOnce(resync.promise);
    act(() => view.result.current.toggleFavorite(10)); // 해제 실패 → 재동기화(붙잡음)
    await waitFor(() => expect(api.getFavoriteProperties).toHaveBeenCalledTimes(3));

    advanceSessionGeneration(); // 로그인·로그아웃·다시 확인과 같은 세션 전환
    await act(async () => resync.resolve([]));

    expect(view.result.current.favoritedIds.has(10)).toBe(true);
  });
});
