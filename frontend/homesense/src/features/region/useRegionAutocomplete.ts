import { useEffect, useRef } from 'react';
import { autocompleteRegions } from './api';
import type { RegionAutocompleteResponse } from './types';

export const AUTOCOMPLETE_DEBOUNCE_MS = 300;
export const AUTOCOMPLETE_MIN_QUERY_LENGTH = 2;

/**
 * 자동완성 요청에 보내는 값과 결과를 기록·비교하는 값이 모두 같은 정규화를 거치도록 한 곳에 둔다 — 한쪽만
 * 바뀌면 재포커스 비교가 조용히 어긋난다.
 */
export function normalizeAutocompleteQuery(raw: string): string {
  return raw.trim();
}

export type RegionAutocompleteUpdate =
  | { kind: 'results'; query: string; results: RegionAutocompleteResponse[] }
  /** 2자 미만이 됐거나 요청이 실패했다 — 목록을 비운다. */
  | { kind: 'cleared'; reason: 'short' | 'error' };

/**
 * 지역 자동완성 조회(UIC-03 검색창과 MY-02 관심 지역 추가가 같이 쓴다). `enabled`가 false면(입력에 포커스가 없으면)
 * 조회하지 않는다. 입력이 바뀌면 300ms 디바운스 뒤 조회하고, 값이 바뀌거나 비활성화되면 타이머와 이미 보낸 요청을
 * 함께 취소한다 — 이전 값의 느린 응답이 최신 결과를 덮지 않는다. 결과는 `onUpdate`로 알린다(최신 콜백을 쓴다).
 */
export function useRegionAutocomplete(
  value: string,
  enabled: boolean,
  onUpdate: (update: RegionAutocompleteUpdate) => void,
): void {
  const onUpdateRef = useRef(onUpdate);
  useEffect(() => {
    onUpdateRef.current = onUpdate;
  });
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!enabled) return;
    const query = normalizeAutocompleteQuery(value);
    let controller: AbortController | null = null;
    const timer = setTimeout(() => {
      if (query.length < AUTOCOMPLETE_MIN_QUERY_LENGTH) {
        onUpdateRef.current({ kind: 'cleared', reason: 'short' });
        return;
      }
      abortRef.current?.abort();
      const current = new AbortController();
      controller = current;
      abortRef.current = current;
      autocompleteRegions(query, current.signal)
        .then((results) => {
          if (current.signal.aborted) return;
          onUpdateRef.current({ kind: 'results', query, results });
        })
        .catch(() => {
          if (current.signal.aborted) return;
          onUpdateRef.current({ kind: 'cleared', reason: 'error' });
        });
    }, AUTOCOMPLETE_DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller?.abort();
    };
  }, [value, enabled]);
}
