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
 * MY-04 목록 데이터. 리스트 컴포넌트는 하나이고 이 훅이 모드만 바꾼다(D8): 번호형은 (필터, 페이지)마다 그 페이지를, 무한 스크롤은
 * 필터마다 처음부터 20건씩 이어 붙인다. 필터·페이지·모드가 바뀌면 진행 중 요청을 끊고 번호를 올려, 늦게 온 이전 응답이 화면을
 * 덮지 않게 한다(D7).
 */
export function useNotificationHistory({ filter, page, mode }: { filter: HistoryFilter; page: number; mode: HistoryMode }) {
  const [state, setState] = useState<HistoryState>(INITIAL);
  const [attempt, setAttempt] = useState(0);
  const generation = useRef(0);
  const moreController = useRef<AbortController | null>(null);
  // 번호형에서만 페이지가 조회 키다 — 무한 스크롤은 URL page를 쓰지 않는다.
  const pageKey = mode === 'paged' ? page : 1;
  const type = filter === 'ALL' ? undefined : filter;
  // 조회 키가 바뀌면(필터·페이지·모드·다시 시도) 렌더 중에 처음 상태로 되돌린다 — 이전 키의 항목을 새 키의 결과처럼 보이지 않게.
  const requestKey = `${filter}|${pageKey}|${mode}|${attempt}`;
  const [stateKey, setStateKey] = useState(requestKey);
  if (stateKey !== requestKey) {
    setStateKey(requestKey);
    setState(INITIAL);
  }

  useEffect(() => {
    const gen = ++generation.current;
    const controller = new AbortController();
    moreController.current?.abort();
    const size = mode === 'paged' ? PAGED_SIZE : INFINITE_SIZE;
    getNotificationPage({ type, page: pageKey - 1, size }, controller.signal)
      .then(({ items, pageMeta }) => {
        if (gen !== generation.current) return;
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
  }, [type, pageKey, mode, attempt]);

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
