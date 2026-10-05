import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import type { RegionAutocompleteResponse } from '../../features/region/types';
import { useRegionAutocomplete } from '../../features/region/useRegionAutocomplete';
import { KEYWORD_MAX_LENGTH } from '../../features/search/searchParams';
import { SearchIcon } from '../icons/SearchIcon';
import { XIcon } from '../icons/XIcon';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  onSubmitKeyword: (value: string) => void;
  onSelectRegion: (region: RegionAutocompleteResponse) => void;
  placeholder?: string;
  /**
   * 'hero' = HOME-01 히어로(Figma 4:1302 — 2px 테두리 입력창 안 오른쪽에 "검색" 버튼),
   * 'inline' = SRCH-01 재검색 바(입력창 옆에 별도 "재검색" 버튼).
   */
  variant?: 'hero' | 'inline';
  autoFocus?: boolean;
}

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
  variant = 'hero',
  autoFocus,
}: SearchBarProps) {
  const listboxId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [focused, setFocused] = useState(false);

  // `focused`가 아니면 자동완성을 아예 조회·오픈하지 않는다 — 재검색 바가 URL의 기존 keyword/
  // regionLabel로 미리 채워진 채 마운트될 때(SRCH-01 재검색 입력창), 사용자가 손대지 않았는데도
  // 그 초기값으로 자동완성이 떠서 바로 아래 "필터" 버튼 등 다른 요소의 클릭을 가로채는 버그가
  // 실측(Playwright)으로 확인됐다 — 포커스 여부로 "사용자가 실제로 이 입력을 조작 중인지"를
  // 구분해서 막는다.
  //
  // 디바운스·요청 취소와 "보이는 후보 = 지금 입력의 응답"은 useRegionAutocomplete(MY-02 관심 지역 추가와 공유)가
  // 맡는다 — 입력을 바꾸면 이전 검색어의 후보·활성 후보가 곧바로 사라져, 그 사이 클릭이나 Enter로 지금 입력과
  // 무관한 지역이 선택되지 않는다(Codex P2). 포커스를 되찾으면 지금 입력의 응답이 남아 있을 때만 목록을 다시 연다.
  const autocomplete = useRegionAutocomplete(value, focused);
  const { items: suggestions, open, activeIndex: highlighted } = autocomplete;

  const selectRegion = (region: RegionAutocompleteResponse) => {
    if (!autocomplete.isVisible(region)) return;
    autocomplete.close();
    onSelectRegion(region);
  };

  const submit = () => {
    if (autocomplete.activeItem) {
      selectRegion(autocomplete.activeItem);
      return;
    }
    autocomplete.close();
    onSubmitKeyword(value);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      if (!open) return;
      event.preventDefault();
      autocomplete.moveActive(1);
    } else if (event.key === 'ArrowUp') {
      if (!open) return;
      event.preventDefault();
      autocomplete.moveActive(-1);
    } else if (event.key === 'Escape') {
      if (open) {
        event.preventDefault();
        autocomplete.close();
      }
    } else if (event.key === 'Enter') {
      event.preventDefault();
      submit();
    }
  };

  const renderInputBox = (boxClassName: string, trailing?: ReactNode) => (
    <div className="relative flex-1">
      <div className={`flex items-center gap-3 rounded-[14px] border-[#e5e7eb] px-3.5 focus-within:border-brand ${boxClassName}`}>
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
            autocomplete.reopen();
          }}
          onBlur={() => {
            setFocused(false);
            // 옵션 클릭(mousedown)이 blur보다 먼저 처리되도록 살짝 지연한다.
            setTimeout(autocomplete.close, 120);
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
        {trailing}
      </div>
      {open && (
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
              onMouseEnter={() => autocomplete.setActiveIndex(index)}
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
        {renderInputBox('border py-2.5')}
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
      className="px-4 py-3"
    >
      {renderInputBox(
        'border-2 py-2.5',
        <button
          type="submit"
          className="flex shrink-0 items-center gap-1.5 rounded-[10px] bg-brand px-5 py-2 text-[13.5px] font-semibold whitespace-nowrap text-white hover:bg-[#0d4f48]"
        >
          <SearchIcon className="size-3.5" />
          검색
        </button>,
      )}
    </form>
  );
}
