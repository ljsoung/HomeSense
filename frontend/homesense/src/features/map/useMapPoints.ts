import axios from 'axios';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ComplexMapPointResponse } from '../complex/types';
import { getErrorMessage } from '../../lib/apiError';
import { searchComplexesInBounds } from './api';

export const MAP_FETCH_DEBOUNCE_MS = 300;

export interface MapPointsState {
  items: ComplexMapPointResponse[];
  truncated: boolean;
  /** 아직 한 번도 응답을 받지 못했다 — 목록 스켈레톤을 보인다. */
  initialLoading: boolean;
  loading: boolean;
  /** 마지막 요청이 실패했으면 서버 error.message(없으면 공통 문구). 이전 결과는 그대로 둔다. */
  errorMessage: string | null;
}

/**
 * 지도 범위 조회. `query`(bounds + 필터의 직렬화 문자열)가 바뀌면 300ms 뒤에 부른다 — 지도 idle과 필터 적용이 같은
 * 경로를 타고, 지도와 목록 패널이 이 응답 하나를 함께 쓴다(목록용 API를 따로 부르지 않는다). 새 요청을 보내면 이전
 * 요청은 AbortController로 끊고, 요청 번호가 최신이 아닌 늦은 응답은 버린다. 실패하면 이전 결과(마커·목록)를 지우지
 * 않는다.
 */
export function useMapPoints(query: URLSearchParams | null) {
  const queryKey = query?.toString() ?? null;
  const [state, setState] = useState<MapPointsState>({
    items: [],
    truncated: false,
    initialLoading: true,
    loading: false,
    errorMessage: null,
  });
  const [retryToken, setRetryToken] = useState(0);
  const requestSeq = useRef(0);
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (queryKey === null) return;
    const timer = window.setTimeout(() => {
      controllerRef.current?.abort();
      const controller = new AbortController();
      controllerRef.current = controller;
      const seq = ++requestSeq.current;
      setState((prev) => ({ ...prev, loading: true }));
      searchComplexesInBounds(new URLSearchParams(queryKey), controller.signal)
        .then((result) => {
          if (seq !== requestSeq.current) return;
          setState({ items: result.items, truncated: result.truncated, initialLoading: false, loading: false, errorMessage: null });
        })
        .catch((error: unknown) => {
          if (seq !== requestSeq.current || axios.isCancel(error)) return;
          setState((prev) => ({ ...prev, initialLoading: false, loading: false, errorMessage: getErrorMessage(error) }));
        });
    }, MAP_FETCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [queryKey, retryToken]);

  useEffect(() => () => controllerRef.current?.abort(), []);

  const retry = useCallback(() => setRetryToken((n) => n + 1), []);

  return { ...state, retry };
}
