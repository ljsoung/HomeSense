import axios from 'axios';
import { useCallback, useEffect, useRef, useState } from 'react';
import { getNotificationPage } from '../../features/notification/api';
import type { NotificationResponse } from '../../features/notification/types';
import { getErrorMessage } from '../../lib/apiError';
import type { PageMeta } from '../../types/api';
import { appendUnique, INFINITE_SIZE, PAGED_SIZE, type HistoryFilter } from './notificationHistoryModel';

export type HistoryMode = 'paged' | 'infinite';

export interface HistoryState {
  items: NotificationResponse[];
  pageMeta: PageMeta | null;
  /** 첫 페이지(번호형은 그 페이지) 상태. */
  status: 'loading' | 'success' | 'error';
  errorMessage: string | null;
  /** 무한 스크롤 다음 페이지 상태 — 실패해도 이미 불러온 항목은 그대로 둔다(D8). */
  moreStatus: 'idle' | 'loading' | 'error';
  /** 무한 스크롤에서 지금까지 받은 페이지 수(다음에 받을 0-base 페이지). 중복 제거로 항목 수와 어긋날 수 있어 따로 센다. */
  loadedPages: number;
}

const INITIAL: HistoryState = { items: [], pageMeta: null, status: 'loading', errorMessage: null, moreStatus: 'idle', loadedPages: 0 };

/**
 * 읽음으로 바꾼 알림 ID — 화면을 떠나도(DTL-01로 이동) 남는다. 읽음 PATCH가 끝나기 전에 뒤로 와 목록을 다시 받아도 점이 되살아나지
 * 않게 받은 항목에 덮어 쓴다. PATCH가 실패하면 지운다 — 다음 조회부터 서버 상태가 기준이다(D2).
 */
const locallyRead = new Set<number>();

export function rememberLocallyRead(id: number): void {
  locallyRead.add(id);
}

export function forgetLocallyRead(id: number): void {
  locallyRead.delete(id);
}

/** 테스트용 — 모듈 상태를 비운다. */
export function resetLocallyRead(): void {
  locallyRead.clear();
}

function withLocalReads(items: NotificationResponse[]): NotificationResponse[] {
  return items.map((n) => (!n.isRead && locallyRead.has(n.notificationId) ? { ...n, isRead: true } : n));
}

/**
 * 무한 스크롤 뒤로가기 복원 — 히스토리 항목(location.key)마다 받은 페이지들과 스크롤 위치를 기억한다. DTL-01 등에서 뒤로 오면 같은
 * key로 돌아오므로 다시 요청하지 않고 그대로 그린 뒤 스크롤을 되돌린다. 메뉴로 새로 들어오면(새 key) 처음부터다. 항목은 서버가 준
 * 그대로(읽음 표시를 덮기 전) 둔다 — 떠난 뒤 읽음 PATCH가 실패하면(forgetLocallyRead) 복원할 때 다시 안 읽음으로 보이게.
 * 번호형은 URL page가 있어 이 캐시를 쓰지 않는다.
 */
interface RestoreEntry {
  filter: HistoryFilter;
  serverItems: NotificationResponse[];
  pageMeta: PageMeta | null;
  loadedPages: number;
  scrollY: number;
}
const restoreCache = new Map<string, RestoreEntry>();

/** 테스트용 — 복원 캐시를 비운다. */
export function resetHistoryRestoreCache(): void {
  restoreCache.clear();
}

function restoredState(entry: RestoreEntry): HistoryState {
  return {
    items: withLocalReads(entry.serverItems),
    pageMeta: entry.pageMeta,
    status: 'success',
    errorMessage: null,
    moreStatus: 'idle',
    loadedPages: entry.loadedPages,
  };
}

/**
 * MY-04 목록 데이터. 리스트 컴포넌트는 하나이고 이 훅이 모드만 바꾼다(D8): 번호형은 (필터, 페이지)마다 그 페이지를, 무한 스크롤은
 * 필터마다 처음부터 20건씩 이어 붙인다. 필터·페이지·모드가 바뀌면 진행 중 요청을 끊고 번호를 올려, 늦게 온 이전 응답이 화면을
 * 덮지 않게 한다(D7).
 */
export function useNotificationHistory({
  filter,
  page,
  mode,
  restoreKey,
}: {
  filter: HistoryFilter;
  page: number;
  mode: HistoryMode;
  /** 히스토리 항목 key(location.key) — 무한 스크롤 복원 캐시의 키. */
  restoreKey: string;
}) {
  const [attempt, setAttempt] = useState(0);
  // 번호형에서만 페이지가 조회 키다 — 무한 스크롤은 URL page를 쓰지 않는다.
  const pageKey = mode === 'paged' ? page : 1;
  const type = filter === 'ALL' ? undefined : filter;
  const requestKey = `${filter}|${pageKey}|${mode}|${attempt}`;
  // 마운트할 때만 복원한다 — 같은 히스토리 항목으로 돌아온 무한 스크롤이고 필터가 같을 때. 이 조회 키로는 요청하지 않는다
  // (StrictMode가 effect를 두 번 돌려도 같다). 키가 바뀌면(필터·모드·다시 시도) 평소처럼 새로 받는다.
  const [restored, setRestored] = useState(() => {
    const entry = mode === 'infinite' ? restoreCache.get(restoreKey) : undefined;
    return entry && entry.filter === filter ? { requestKey, entry } : null;
  });
  const [state, setState] = useState<HistoryState>(() => (restored ? restoredState(restored.entry) : INITIAL));
  const serverItems = useRef<NotificationResponse[]>(restored?.entry.serverItems ?? []);
  const generation = useRef(0);
  const moreController = useRef<AbortController | null>(null);
  // 조회 키가 바뀌면(필터·페이지·모드·다시 시도) 렌더 중에 처음 상태로 되돌린다 — 이전 키의 항목을 새 키의 결과처럼 보이지 않게.
  const [stateKey, setStateKey] = useState(requestKey);
  if (stateKey !== requestKey) {
    setStateKey(requestKey);
    setState(INITIAL);
    // 복원은 처음 조회 키에서만 — 다른 키로 바뀐 뒤 같은 키로 돌아와도 새로 받는다.
    if (restored) setRestored(null);
  }

  useEffect(() => {
    if (restored?.requestKey === requestKey) return;
    const gen = ++generation.current;
    const controller = new AbortController();
    moreController.current?.abort();
    const size = mode === 'paged' ? PAGED_SIZE : INFINITE_SIZE;
    getNotificationPage({ type, page: pageKey - 1, size }, controller.signal)
      .then(({ items, pageMeta }) => {
        if (gen !== generation.current) return;
        serverItems.current = items;
        setState({ items: withLocalReads(items), pageMeta, status: 'success', errorMessage: null, moreStatus: 'idle', loadedPages: 1 });
      })
      .catch((error: unknown) => {
        if (gen !== generation.current || axios.isCancel(error)) return;
        setState((prev) => ({ ...prev, status: 'error', errorMessage: getErrorMessage(error) }));
      });
    return () => {
      controller.abort();
      generation.current += 1;
    };
    // requestKey는 type·pageKey·mode·attempt로만 만들어진다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, pageKey, mode, attempt]);

  // 무한 스크롤 결과를 이 히스토리 항목에 기억한다(받은 뒤에만 — 아직 받지 못한 빈 상태를 기억하지 않는다). 스크롤 위치는 실제
  // 스크롤 이벤트로만 갱신한다 — 떠날 때 다시 읽으면 짧은 다음 화면 때문에 0으로 줄어든 값을 읽는다(SRCH-01과 같은 이유).
  const { items: currentItems, pageMeta: currentMeta, status: currentStatus, loadedPages: currentPages } = state;
  useEffect(() => {
    if (mode !== 'infinite' || currentStatus !== 'success') return;
    restoreCache.set(restoreKey, {
      filter,
      serverItems: serverItems.current,
      pageMeta: currentMeta,
      loadedPages: currentPages,
      scrollY: restoreCache.get(restoreKey)?.scrollY ?? 0,
    });
  }, [mode, currentStatus, currentItems, currentMeta, currentPages, filter, restoreKey]);

  useEffect(() => {
    if (mode !== 'infinite') return;
    const save = () => {
      // 다른 화면으로 이동하면 URL(history.state.key)이 먼저 바뀌고, 짧은 다음 화면 때문에 브라우저가 스크롤을 줄이며 낸 이벤트가 이
      // 리스너가 떼어지기 전에 올 수 있다 — 그 값을 이 항목의 위치로 저장하지 않는다.
      // 첫 항목(새로 연 페이지)은 react-router가 key 없이 둬 location.key가 'default'다.
      if (((window.history.state as { key?: string } | null)?.key ?? 'default') !== restoreKey) return;
      const entry = restoreCache.get(restoreKey);
      if (entry && entry.filter === filter) entry.scrollY = window.scrollY;
    };
    window.addEventListener('scroll', save, { passive: true });
    return () => window.removeEventListener('scroll', save);
  }, [mode, restoreKey, filter]);

  // 복원했으면 그린 뒤 스크롤을 되돌린다(한 번).
  const scrolledBack = useRef(false);
  useEffect(() => {
    if (!restored || scrolledBack.current) return;
    scrolledBack.current = true;
    const { scrollY } = restored.entry;
    requestAnimationFrame(() => window.scrollTo(0, scrollY));
  }, [restored]);

  const { loadedPages } = state;
  const hasMore = mode === 'infinite' && state.pageMeta !== null && loadedPages < state.pageMeta.totalPages;

  const loadMore = useCallback(() => {
    if (mode !== 'infinite' || state.status !== 'success' || state.moreStatus === 'loading' || !hasMore) return;
    const gen = generation.current;
    const controller = new AbortController();
    moreController.current = controller;
    setState((prev) => ({ ...prev, moreStatus: 'loading' }));
    getNotificationPage({ type, page: loadedPages, size: INFINITE_SIZE }, controller.signal)
      .then(({ items, pageMeta }) => {
        if (gen !== generation.current) return;
        serverItems.current = appendUnique(serverItems.current, items);
        setState((prev) => ({
          ...prev,
          items: appendUnique(prev.items, withLocalReads(items)),
          pageMeta,
          moreStatus: 'idle',
          loadedPages: prev.loadedPages + 1,
        }));
      })
      .catch((error: unknown) => {
        if (gen !== generation.current || axios.isCancel(error)) return;
        setState((prev) => ({ ...prev, moreStatus: 'error' }));
      });
  }, [mode, state.status, state.moreStatus, hasMore, type, loadedPages]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  /** 낙관적 읽음 표시(D2) — 되돌리면 안 읽음으로. */
  const setRead = useCallback((id: number, read: boolean) => {
    setState((prev) => ({ ...prev, items: prev.items.map((n) => (n.notificationId === id ? { ...n, isRead: read } : n)) }));
  }, []);

  return { ...state, hasMore, loadMore, retry, setRead };
}
