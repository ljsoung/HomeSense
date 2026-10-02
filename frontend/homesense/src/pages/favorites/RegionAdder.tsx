import { useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { isEupmyeondongCode } from '../../features/region/regionLevel';
import type { RegionAutocompleteResponse } from '../../features/region/types';
import { useRegionAutocomplete } from '../../features/region/useRegionAutocomplete';
import { getErrorMessage } from '../../lib/apiError';

export const REGION_NOT_FOUND_MESSAGE = '지역을 찾을 수 없습니다';
export const REGION_DUPLICATE_MESSAGE = '이미 등록된 지역입니다';

interface RegionAdderProps {
  /** 이미 목록에 있는 법정동코드 — 고르면 요청 없이 중복 안내를 띄운다. */
  registeredCodes: ReadonlySet<string>;
  /** 등록. 실패하면 예외를 던진다(서버 문구를 입력 아래에 그대로 보인다). */
  onAdd: (region: RegionAutocompleteResponse) => Promise<void>;
}

/**
 * MY-02 관심 지역 추가(D7). 자동완성 후보를 고른 경우에만 추가할 수 있고, 후보는 읍·면·동 단위만 남긴다
 * (서버도 같은 규칙으로 400을 낸다). 조회·디바운스·취소는 UIC-03 검색창과 같은 useRegionAutocomplete를 쓴다.
 * 고른 뒤 입력을 고치면 선택이 풀린다. 후보 위에서 Enter는 선택, 선택된 상태에서 Enter는 추가.
 * 안내 문구(후보 없음·중복·서버 오류)는 aria-live 영역으로 읽힌다.
 */
export function RegionAdder({ registeredCodes, onAdd }: RegionAdderProps) {
  const inputId = useId();
  const listboxId = useId();
  const messageId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [value, setValue] = useState('');
  const [selected, setSelected] = useState<RegionAutocompleteResponse | null>(null);
  const [suggestions, setSuggestions] = useState<RegionAutocompleteResponse[]>([]);
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(-1);
  const [focused, setFocused] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // 고른 뒤에는 조회하지 않는다 — 입력값이 고른 지역의 전체 경로로 바뀌어도 목록이 다시 열리지 않게.
  useRegionAutocomplete(value, focused && selected === null, (update) => {
    if (update.kind === 'cleared') {
      setSuggestions([]);
      setOpen(false);
      setHighlighted(-1);
      setMessage((prev) => (prev === REGION_NOT_FOUND_MESSAGE ? null : prev));
      return;
    }
    const candidates = update.results.filter((region) => isEupmyeondongCode(region.legalDongCd));
    setSuggestions(candidates);
    setOpen(candidates.length > 0);
    setHighlighted(-1);
    setMessage(candidates.length === 0 ? REGION_NOT_FOUND_MESSAGE : null);
  });

  const close = () => {
    setOpen(false);
    setHighlighted(-1);
  };

  const choose = (region: RegionAutocompleteResponse) => {
    close();
    setSelected(region);
    setValue(region.fullPath);
    setMessage(registeredCodes.has(region.legalDongCd) ? REGION_DUPLICATE_MESSAGE : null);
  };

  const submit = async () => {
    if (!selected || submitting) return;
    if (registeredCodes.has(selected.legalDongCd)) {
      setMessage(REGION_DUPLICATE_MESSAGE);
      return;
    }
    setSubmitting(true);
    setMessage(null);
    try {
      await onAdd(selected);
      setSelected(null);
      setValue('');
      setSuggestions([]);
    } catch (error) {
      setMessage(getErrorMessage(error));
      inputRef.current?.focus();
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    void submit();
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
        close();
      }
    } else if (event.key === 'Enter' && open && highlighted >= 0 && suggestions[highlighted]) {
      event.preventDefault();
      choose(suggestions[highlighted]);
    }
  };

  const isDuplicate = selected !== null && registeredCodes.has(selected.legalDongCd);
  const canAdd = selected !== null && !isDuplicate && !submitting;

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-2">
      {/* 보이는 제목은 데스크톱 섹션 제목("관심 지역 추가")이 맡는다(Figma 6-4695). 탭 안에서는 제목이 없어 이름만 둔다. */}
      <label htmlFor={inputId} className="sr-only">
        관심 지역 추가 — 읍·면·동 이름
      </label>
      <div className="flex gap-3">
        <div className="relative min-w-0 flex-1">
          <div className="flex h-11 items-center rounded-[14px] border border-[#e5e7eb] bg-white px-4 focus-within:border-brand">
            <input
              ref={inputRef}
              id={inputId}
              type="text"
              role="combobox"
              aria-expanded={open}
              aria-controls={listboxId}
              aria-autocomplete="list"
              aria-activedescendant={highlighted >= 0 ? `${listboxId}-option-${highlighted}` : undefined}
              aria-describedby={messageId}
              autoComplete="off"
              value={value}
              placeholder="예: 금광면, 역삼동"
              onChange={(event) => {
                setValue(event.target.value);
                if (selected && event.target.value !== selected.fullPath) setSelected(null);
                setMessage(null);
              }}
              onKeyDown={handleKeyDown}
              onFocus={() => setFocused(true)}
              onBlur={() => {
                setFocused(false);
                // 후보 클릭(mousedown)이 blur보다 먼저 처리되도록 살짝 늦춘다(UIC-03과 같다).
                setTimeout(close, 120);
              }}
              className="w-full min-w-0 text-[14px] text-[#101828] placeholder:font-medium placeholder:text-[#99a1af] focus:outline-none"
            />
          </div>
          {open && suggestions.length > 0 && (
            <ul
              id={listboxId}
              role="listbox"
              aria-label="관심 지역 후보"
              className="absolute inset-x-0 top-[calc(100%+6px)] z-30 max-h-64 overflow-y-auto rounded-[14px] border border-[#e5e7eb] bg-white py-1.5 shadow-[0_10px_20px_rgba(0,0,0,0.12)]"
            >
              {suggestions.map((region, index) => (
                <li
                  key={region.legalDongCd}
                  id={`${listboxId}-option-${index}`}
                  role="option"
                  aria-selected={index === highlighted}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => choose(region)}
                  className={`cursor-pointer px-3.5 py-2.5 text-[13.5px] text-[#101828] ${index === highlighted ? 'bg-[#f0f9f7]' : 'hover:bg-[#f7f8fa]'}`}
                >
                  {region.fullPath}
                </li>
              ))}
            </ul>
          )}
        </div>
        <button
          type="submit"
          disabled={!canAdd}
          className="flex h-11 w-[66px] shrink-0 items-center justify-center rounded-[14px] bg-brand text-[14px] font-bold text-white hover:bg-[#0c4a44] disabled:cursor-not-allowed disabled:opacity-50"
        >
          추가
        </button>
      </div>
      <p id={messageId} aria-live="polite" className="min-h-[18px] text-[12.5px] leading-[18px] text-[#c10007]">
        {message}
      </p>
    </form>
  );
}
