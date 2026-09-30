import { act, cleanup, render, screen } from '@testing-library/react';
import { useLayoutEffect, type ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext, type AuthContextValue, type AuthStatus } from '../../features/auth/authContext';
import type { RecentViewResponse } from '../../features/recentview/types';
import type { InterestRegionSummaryResponse } from '../../features/region/types';
import { InterestRegionSummary } from './InterestRegionSummary';
import { RecentViews } from './RecentViews';

// 계정별 데이터를 들고 있는 홈 컴포넌트가 다른 탭의 계정 변경(authenticated(A) → checking → authenticated(B))에서
// A의 데이터를 B로 확정된 뒤에 보여 주지 않는지 검증한다. B의 응답은 붙잡아 두어 "B 확정 직후" 구간을 만든다.
// act()는 useEffect까지 끝낸 뒤 돌려주므로, 커밋 직후(브라우저라면 칠해지는) DOM은 형제 레이아웃 effect로
// 커밋마다 기록해 확인한다 — 수정 전에는 B 확정 첫 커밋에 A의 데이터가 있었고, useEffect가 로딩으로 바꾸기 전에
// 한 프레임 칠해졌다.

const regionApi = vi.hoisted(() => ({ getInterestSummary: vi.fn() }));
vi.mock('../../features/region/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../features/region/api')>()),
  getInterestSummary: regionApi.getInterestSummary,
}));
const recentApi = vi.hoisted(() => ({ getRecentViews: vi.fn() }));
vi.mock('../../features/recentview/api', () => recentApi);

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

let commits: { status: AuthStatus; text: string }[] = [];

/** 커밋마다 그 시점의 DOM 텍스트를 기록한다(레이아웃 effect는 DOM 반영 뒤, useEffect 전에 돈다). */
function CommitProbe({ status }: { status: AuthStatus }) {
  useLayoutEffect(() => {
    commits.push({ status, text: document.body.textContent ?? '' });
  });
  return null;
}

/** 다시 authenticated가 된 뒤의 커밋 중 text가 들어 있는 커밋이 있었는지. */
function shownAfterReconfirm(text: string): boolean {
  const checkingAt = commits.findIndex((c) => c.status === 'checking');
  return commits.slice(checkingAt).some((c) => c.status === 'authenticated' && c.text.includes(text));
}

function renderWithStatus(initial: AuthStatus, ui: () => ReactNode) {
  let status = initial;
  const auth = (): AuthContextValue => ({
    status,
    user: null,
    login: async () => {},
    signup: async () => {},
    logout: async () => {},
  });
  const tree = () => (
    <MemoryRouter>
      <AuthContext.Provider value={auth()}>
        {ui()}
        <CommitProbe status={status} />
      </AuthContext.Provider>
    </MemoryRouter>
  );
  const view = render(tree());
  return {
    setStatus: (next: AuthStatus) => {
      status = next;
      view.rerender(tree());
    },
  };
}

const flush = () =>
  act(async () => {
    await Promise.resolve();
  });

beforeEach(() => {
  commits = [];
  regionApi.getInterestSummary.mockReset();
  recentApi.getRecentViews.mockReset();
});

afterEach(() => {
  cleanup();
});

describe('계정별 홈 데이터 — 다른 계정으로 다시 확인', () => {
  it('관심 지역 요약: B로 확정된 뒤 B 응답 전에 A의 관심 지역을 보여 주지 않는다', async () => {
    const regionA: InterestRegionSummaryResponse = {
      favoriteRegionId: 1,
      legalDongCd: '1111010100',
      fullPath: '서울특별시 종로구 계정A동',
      avgPrice: 100000,
      changeRate: 1.2,
      tradeCount: 3,
    };
    regionApi.getInterestSummary.mockResolvedValueOnce([regionA]);
    const view = renderWithStatus('authenticated', () => <InterestRegionSummary />);
    expect(await screen.findByText('계정A동')).toBeTruthy();

    act(() => view.setStatus('checking'));
    const listB = deferred<InterestRegionSummaryResponse[]>();
    regionApi.getInterestSummary.mockReturnValueOnce(listB.promise);
    act(() => view.setStatus('authenticated'));
    await flush();
    expect(shownAfterReconfirm('계정A동')).toBe(false);
    expect(screen.queryByText('계정A동')).toBeNull();

    await act(async () => {
      listB.resolve([]);
    });
    expect(screen.queryByText('계정A동')).toBeNull();
  });

  it('최근 조회: B로 확정된 뒤 B 응답 전에 A의 최근 조회 단지를 보여 주지 않는다', async () => {
    const viewA: RecentViewResponse = {
      complexId: 10,
      complexName: '계정A단지',
      housingType: 'APT',
      sido: '서울특별시',
      sigungu: '종로구',
      dongRi: '숭인동',
      viewedAt: '2026-09-30T10:00:00',
    };
    recentApi.getRecentViews.mockResolvedValueOnce([viewA]);
    const view = renderWithStatus('authenticated', () => (
      <RecentViews favoritedIds={new Set()} onToggleFavorite={() => {}} pendingFavoriteId={null} />
    ));
    expect(await screen.findByText('계정A단지')).toBeTruthy();

    act(() => view.setStatus('checking'));
    const listB = deferred<RecentViewResponse[]>();
    recentApi.getRecentViews.mockReturnValueOnce(listB.promise);
    act(() => view.setStatus('authenticated'));
    await flush();
    expect(shownAfterReconfirm('계정A단지')).toBe(false);
    expect(screen.queryByText('계정A단지')).toBeNull();

    await act(async () => {
      listB.resolve([]);
    });
    expect(screen.queryByText('계정A단지')).toBeNull();
  });
});
