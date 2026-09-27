import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { autocompleteRegions } from '../../features/region/api';
import type { RegionAutocompleteResponse } from '../../features/region/types';
import { KEYWORD_MAX_LENGTH } from '../../features/search/searchParams';
import { SearchIcon } from '../icons/SearchIcon';
import { XIcon } from '../icons/XIcon';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  onSubmitKeyword: (value: string) => void;
  onSelectRegion: (region: RegionAutocompleteResponse) => void;
  placeholder?: string;
  /** 'stacked' = HOME-01 히어로(검색창 아래 큰 버튼), 'inline' = SRCH-01 재검색 바(한 줄, 버튼 없음). */
  variant?: 'stacked' | 'inline';
  autoFocus?: boolean;
}

const DEBOUNCE_MS = 300;
const MIN_QUERY_LENGTH = 2;

/**
 * UIC-03 — 자유 텍스트 검색 + 지역 자동완성을 겸하는 검색창. ARIA combobox 패턴(입력에
 * role=combobox/aria-expanded/aria-controls/aria-activedescendant, 목록에 role=listbox/option)을
 * 그대로 따른다. 자동완성 실패(네트워크 오류 등)는 목록을 비우기만 할 뿐 자유 텍스트 검색 자체를
 * 막지 않는다(확정 사항).
 */
export function SearchBar({
  value,
  onChange,
  onSubmitKeyword,
  onSelectRegion,
  placeholder = '지역명·단지명(건물명)으로 검색',
  variant = 'stacked',
  autoFocus,
}: SearchBarProps) {
  const listboxId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [suggestions, setSuggestions] = useState<RegionAutocompleteResponse[]>([]);
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(-1);
  const [focused, setFocused] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  // `focused`가 아니면 자동완성을 아예 조회·오픈하지 않는다 — 재검색 바가 URL의 기존 keyword/
  // regionLabel로 미리 채워진 채 마운트될 때(SRCH-01 재검색 입력창), 사용자가 손대지 않았는데도
  // 그 초기값으로 자동완성이 떠서 바로 아래 "필터" 버튼 등 다른 요소의 클릭을 가로채는 버그가
  // 실측(Playwright)으로 확인됐다 — 포커스 여부로 "사용자가 실제로 이 입력을 조작 중인지"를
  // 구분해서 막는다.
  useEffect(() => {
    if (!focused) return;
    const trimmed = value.trim();
    const timer = setTimeout(() => {
      if (trimmed.length < MIN_QUERY_LENGTH) {
        setSuggestions([]);
        setOpen(false);
        return;
      }
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      autocompleteRegions(trimmed, controller.signal)
        .then((results) => {
          setSuggestions(results);
          setOpen(results.length > 0);
          setHighlighted(-1);
        })
        .catch(() => {
          setSuggestions([]);
          setOpen(false);
        });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [value, focused]);

  const closeSuggestions = () => {
    setOpen(false);
    setHighlighted(-1);
  };

  const selectRegion = (region: RegionAutocompleteResponse) => {
    closeSuggestions();
    onSelectRegion(region);
  };

  const submit = () => {
    if (open && highlighted >= 0 && suggestions[highlighted]) {
      selectRegion(suggestions[highlighted]);
      return;
    }
    closeSuggestions();
    onSubmitKeyword(value);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      if (!open) return;
      event.preventDefault();
      setHighlighted((prev) => (prev + 1) % suggestions.length);
    } else if (event.key === 'ArrowUp') {
      if (!open) return;
      event.preventDefault();
      setHighlighted((prev) => (prev - 1 + suggestions.length) % suggestions.length);
    } else if (event.key === 'Escape') {
      if (open) {
        event.preventDefault();
        closeSuggestions();
      }
    } else if (event.key === 'Enter') {
      event.preventDefault();
      submit();
    }
  };

  const inputBox = (
    <div className="relative flex-1">
      <div className="flex items-center gap-3 rounded-[14px] border border-[#e5e7eb] px-3.5 py-2.5 focus-within:border-brand">
        <SearchIcon className="size-[18px] shrink-0 text-[#99a1af]" />
        <input
          ref={inputRef}
          type="text"
          role="combobox"
          aria-expanded={open}
          aria-controls={listboxId}
          aria-activedescendant={highlighted >= 0 ? `${listboxId}-option-${highlighted}` : undefined}
          aria-autocomplete="list"
          autoFocus={autoFocus}
          value={value}
          maxLength={KEYWORD_MAX_LENGTH}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={handleKeyDown}
          onFocus={() => {
            setFocused(true);
            if (suggestions.length > 0) setOpen(true);
          }}
          onBlur={() => {
            setFocused(false);
            // 옵션 클릭(mousedown)이 blur보다 먼저 처리되도록 살짝 지연한다.
            setTimeout(closeSuggestions, 120);
          }}
          placeholder={placeholder}
          className="w-full min-w-0 text-[14px] text-[#101828] placeholder:text-[#99a1af] focus:outline-none"
        />
        {value && (
          <button
            type="button"
            aria-label="검색어 지우기"
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => {
              onChange('');
              inputRef.current?.focus();
            }}
            className="shrink-0 text-[#99a1af] hover:text-[#6a7282]"
          >
            <XIcon className="size-4" />
          </button>
        )}
      </div>
      {open && suggestions.length > 0 && (
        <ul
          id={listboxId}
          role="listbox"
          className="absolute inset-x-0 top-[calc(100%+6px)] z-30 max-h-64 overflow-y-auto rounded-[14px] border border-[#e5e7eb] bg-white py-1.5 shadow-[0_10px_20px_rgba(0,0,0,0.12)]"
        >
          {suggestions.map((region, index) => (
            <li
              key={region.legalDongCd}
              id={`${listboxId}-option-${index}`}
              role="option"
              aria-selected={index === highlighted}
              onMouseDown={(event) => event.preventDefault()}
              onMouseEnter={() => setHighlighted(index)}
              onClick={() => selectRegion(region)}
              className={`cursor-pointer px-3.5 py-2 text-[13px] ${index === highlighted ? 'bg-[#f0f9f7] text-brand' : 'text-[#364153]'}`}
            >
              {region.fullPath}
            </li>
          ))}
        </ul>
      )}
    </div>
  );

  if (variant === 'inline') {
    return (
      <form
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
        className="flex items-center gap-2"
      >
        {inputBox}
        {/* Figma 재검색 바(4:1704/24:9405/24:10388)는 입력창 옆에 "재검색" 버튼을 별도로 둔다 —
            Enter 제출만 지원하던 이전 구현은 이 버튼이 없어 마우스만 쓰는 사용자에게 제출 수단이
            안 보였다. */}
        <button
          type="submit"
          className="flex h-full shrink-0 items-center gap-1.5 rounded-[14px] bg-brand px-4 py-2.5 text-[13.5px] font-semibold whitespace-nowrap text-white hover:bg-[#0d4f48]"
        >
          <SearchIcon className="size-4" />
          재검색
        </button>
      </form>
    );
  }

  return (
    <form
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
      className="flex flex-col gap-3 p-4"
    >
      {inputBox}
      <button type="submit" className="h-[45px] w-full rounded-[14px] bg-brand text-[14px] font-semibold text-white hover:bg-[#0d4f48]">
        검색
      </button>
    </form>
  );
}
