import { useEffect, useMemo, useRef, useState } from 'react';
import { autocompleteRegions } from './api';
import type { RegionAutocompleteResponse } from './types';

export const AUTOCOMPLETE_DEBOUNCE_MS = 300;
export const AUTOCOMPLETE_MIN_QUERY_LENGTH = 2;

const NO_ITEMS: RegionAutocompleteResponse[] = [];

/**
 * 자동완성 요청에 보내는 값과 결과를 기록·비교하는 값이 모두 같은 정규화를 거치도록 한 곳에 둔다 — 한쪽만
 * 바뀌면 결과가 지금 입력의 것인지 비교가 조용히 어긋난다.
 */
export function normalizeAutocompleteQuery(raw: string): string {
  return raw.trim();
}

/** 한 검색어에 대한 응답. 요청이 실패하면 `failed`이고 후보는 비어 있다. */
export interface RegionAutocompleteResult {
  query: string;
  items: RegionAutocompleteResponse[];
  failed: boolean;
}

export interface RegionAutocomplete {
  /**
   * 지금 입력(정규화 값)에 대한 응답. 요청 중이거나, 2자 미만이거나, 가진 응답이 이전 검색어의 것이면 null이다 —
   * 이때는 후보도 "결과 없음"도 보이지 않는다.
   */
  result: RegionAutocompleteResult | null;
  /** 지금 입력의 후보(`select`를 거친 것). 응답이 없으면 빈 배열. */
  items: RegionAutocompleteResponse[];
  /** 목록을 열지 — 후보가 있고 사용자가 닫지 않았을 때. */
  open: boolean;
  /** 활성 후보 위치(aria-activedescendant). 목록이 닫혔거나 응답이 바뀌면 -1. */
  activeIndex: number;
  activeItem: RegionAutocompleteResponse | null;
  setActiveIndex: (index: number) => void;
  /** 활성 후보를 한 칸 옮긴다(목록 끝에서 처음으로 돈다). 목록이 닫혀 있으면 아무것도 하지 않는다. */
  moveActive: (delta: 1 | -1) => void;
  /** 지금 응답의 목록을 닫는다(Esc·blur·선택). 같은 검색어의 새 응답이 오거나 `reopen`하면 다시 열린다. */
  close: () => void;
  reopen: () => void;
  /** 지금 보이는 후보인지 — 선택 직전 방어용. 이전 검색어의 후보는 false. */
  isVisible: (region: RegionAutocompleteResponse) => boolean;
}

interface Options {
  /** 응답에서 후보로 남길 것만 고른다(예: MY-02는 읍·면·동만). 렌더마다 같은 함수여야 한다. */
  select?: (items: RegionAutocompleteResponse[]) => RegionAutocompleteResponse[];
}

/**
 * 지역 자동완성(UIC-03 검색창과 MY-02 관심 지역 추가가 같이 쓴다). `enabled`가 false면(입력에 포커스가 없으면)
 * 조회하지 않는다. 검색어가 바뀌면 300ms 디바운스 뒤 조회하고, 바뀌거나 비활성화되면 타이머와 보낸 요청을 함께
 * 취소한다.
 *
 * 응답은 검색어와 함께 보관하고, 화면에 보이는 후보는 그 검색어가 지금 입력과 같을 때만 계산해 낸다(Codex P2).
 * 예전에는 응답을 받을 때 후보를 화면 상태에 옮겨 두어, 입력을 바꾼 뒤 디바운스·요청 동안 이전 검색어의 후보가
 * 그대로 떠 있었다 — 그 사이 클릭하거나 남아 있던 활성 후보에서 Enter를 누르면 지금 입력과 무관한 지역이
 * 선택됐다. 입력을 바꿀 때 지우는 대신 계산하는 이유: 늦게 도착한 응답이 목록을 다시 채우는 경우까지 막는다.
 * 늦은 응답은 요청 취소에 더해 저장할 때도 검색어를 비교해 버린다.
 */
export function useRegionAutocomplete(value: string, enabled: boolean, options: Options = {}): RegionAutocomplete {
  const { select } = options;
  const query = normalizeAutocompleteQuery(value);
  const [stored, setStored] = useState<RegionAutocompleteResult | null>(null);
  const [dismissed, setDismissed] = useState<RegionAutocompleteResult | null>(null);
  const [active, setActive] = useState<{ result: RegionAutocompleteResult | null; index: number }>({
    result: null,
    index: -1,
  });

  // 검색어가 바뀌면 활성 후보를 지운다(렌더 중 상태 조정). 같은 검색어로 되돌아와 그 응답이 다시 보여도 이전에
  // 골라 둔 위치는 이어받지 않는다.
  const [activeQuery, setActiveQuery] = useState(query);
  if (activeQuery !== query) {
    setActiveQuery(query);
    setActive({ result: null, index: -1 });
  }

  const latestQueryRef = useRef(query);
  useEffect(() => {
    latestQueryRef.current = query;
  }, [query]);

  useEffect(() => {
    if (!enabled || query.length < AUTOCOMPLETE_MIN_QUERY_LENGTH) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      // 응답이 지금 검색어의 것이 아니면 버린다 — 취소가 늦거나 빠진 경우에도 이전 검색어의 결과를 저장하지 않는다.
      const accept = () => !controller.signal.aborted && latestQueryRef.current === query;
      autocompleteRegions(query, controller.signal)
        .then((items) => {
          if (accept()) setStored({ query, items, failed: false });
        })
        .catch(() => {
          if (accept()) setStored({ query, items: NO_ITEMS, failed: true });
        });
    }, AUTOCOMPLETE_DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query, enabled]);

  const result = stored !== null && stored.query === query && query.length >= AUTOCOMPLETE_MIN_QUERY_LENGTH ? stored : null;
  const items = useMemo(
    () => (result === null ? NO_ITEMS : select ? select(result.items) : result.items),
    [result, select],
  );
  const open = result !== null && items.length > 0 && dismissed !== result;
  const activeIndex = open && active.result === result && active.index < items.length ? active.index : -1;

  return {
    result,
    items,
    open,
    activeIndex,
    activeItem: activeIndex >= 0 ? items[activeIndex] : null,
    setActiveIndex: (index) => setActive({ result, index }),
    moveActive: (delta) => {
      if (!open) return;
      setActive({ result, index: (activeIndex + delta + items.length) % items.length });
    },
    close: () => {
      setDismissed(result);
      setActive({ result: null, index: -1 });
    },
    reopen: () => setDismissed(null),
    isVisible: (region) => open && items.some((item) => item.legalDongCd === region.legalDongCd),
  };
}
