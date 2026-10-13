import { useEffect, useRef, useState, type ReactNode } from 'react';
import { ChevronDownIcon } from '../../components/icons/ChevronDownIcon';
import { SearchIcon } from '../../components/icons/SearchIcon';
import { BottomSheet } from '../../components/ui/BottomSheet';
import { FilterPanel } from '../../components/ui/FilterPanel';
import { SearchBar } from '../../components/ui/SearchBar';
import type { RegionAutocompleteResponse } from '../../features/region/types';
import { defaultFilters, type SearchFilters } from '../../features/search/searchParams';

type ChipKey = 'housing' | 'deal' | 'amount';

const CHIPS: { key: ChipKey; label: string }[] = [
  { key: 'housing', label: '유형' },
  { key: 'deal', label: '거래유형' },
  { key: 'amount', label: '가격대' },
];

function chipActive(key: ChipKey, filters: SearchFilters): boolean {
  const d = defaultFilters();
  switch (key) {
    case 'housing':
      return filters.housingTypes.length !== d.housingTypes.length;
    case 'deal':
      return filters.dealType !== d.dealType;
    case 'amount':
      return filters.amountMin !== d.amountMin || filters.amountMax !== d.amountMax;
  }
}

interface MapFilterBarProps {
  mobile: boolean;
  filters: SearchFilters;
  draft: SearchFilters;
  onChangeDraft: (updater: (prev: SearchFilters) => SearchFilters) => void;
  onApply: () => void;
  onReset: () => void;
  /** 필터 패널을 열 때 draft를 현재 URL 필터로 맞춘다. */
  onOpenFilters: () => void;
  searchValue: string;
  onSearchValueChange: (value: string) => void;
  onSubmitKeyword: (value: string) => void;
  onSelectRegion: (region: RegionAutocompleteResponse) => void;
  /** 데스크톱 막대 오른쪽 건수(Figma 6:4373 "6건"). */
  countText: ReactNode;
}

/**
 * MAP-01 구성요소 1 — 검색·필터. 데스크톱·태블릿(Figma 6:4346·26:14614)은 지도 위쪽 막대(지역 검색 + 유형·거래유형·가격대
 * 칩 + 건수), 모바일(26:14549)은 지도 위에 뜬 칩 줄이다. 화면 크기로 한쪽만 그린다(반응형 렌더 규칙 — 두 벌을 숨기지 않는다).
 *
 * 칩은 필드마다 다른 드롭다운이 아니라 같은 필터 패널(UIC-04 FilterPanel — SRCH-01과 같은 컴포넌트·같은 draft)을 연다.
 * 데스크톱은 막대 아래 팝오버, 모바일은 SRCH-01의 필터 하단 시트(BottomSheet)다. 필터를 바꾼 칩은 브랜드색으로 표시한다.
 */
export function MapFilterBar(props: MapFilterBarProps) {
  const { mobile, filters, draft, onChangeDraft, onApply, onReset, onOpenFilters } = props;
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);

  const openFilters = () => {
    onOpenFilters();
    setFiltersOpen(true);
  };
  const apply = () => {
    onApply();
    setFiltersOpen(false);
  };

  const chip = (key: ChipKey, label: string, chipClass: string, iconClass: string) => {
    const active = chipActive(key, filters);
    return (
      <button
        key={key}
        type="button"
        onClick={openFilters}
        aria-haspopup="dialog"
        aria-expanded={filtersOpen}
        className={`${chipClass} ${active ? 'border-brand text-brand' : ''}`}
      >
        {label}
        {active && <span className="sr-only">(적용됨)</span>}
        <ChevronDownIcon className={iconClass} />
      </button>
    );
  };

  if (mobile) {
    const regionLabel = filters.regionLabel ?? filters.keyword ?? '지역 검색';
    return (
      <>
        <div className="absolute inset-x-0 top-0 z-20 flex gap-2 overflow-x-auto px-3 py-2 [scrollbar-width:none]" data-testid="map-filter-chips">
          <button
            type="button"
            onClick={() => setSearchOpen(true)}
            className="flex h-8 shrink-0 items-center gap-1.5 rounded-full border border-[#e5e7eb] bg-white/95 px-3 text-[12px] font-semibold text-[#364153] shadow-[0_4px_6px_rgba(0,0,0,0.1),0_2px_4px_rgba(0,0,0,0.1)] backdrop-blur-[8px]"
          >
            <SearchIcon className="size-3.5 text-[#6a7282]" />
            <span className="max-w-[140px] truncate">{regionLabel}</span>
          </button>
          {CHIPS.map(({ key, label }) =>
            chip(
              key,
              label,
              'flex h-8 shrink-0 items-center gap-1 rounded-full border border-[#e5e7eb] bg-white/95 px-3 text-[12px] font-semibold text-[#364153] shadow-[0_4px_6px_rgba(0,0,0,0.1),0_2px_4px_rgba(0,0,0,0.1)] backdrop-blur-[8px]',
              'size-3 opacity-70',
            ),
          )}
        </div>
        <BottomSheet open={searchOpen} onClose={() => setSearchOpen(false)} title="지역 검색">
          <SearchBar
            variant="inline"
            value={props.searchValue}
            onChange={props.onSearchValueChange}
            onSubmitKeyword={(value) => {
              setSearchOpen(false);
              props.onSubmitKeyword(value);
            }}
            onSelectRegion={(region) => {
              setSearchOpen(false);
              props.onSelectRegion(region);
            }}
            placeholder="지역명으로 지도 이동"
            ariaLabel="지역 검색"
          />
        </BottomSheet>
        <BottomSheet open={filtersOpen} onClose={() => setFiltersOpen(false)} title="필터">
          <FilterPanel draft={draft} onChangeDraft={onChangeDraft} onApply={apply} onReset={onReset} showHeader={false} />
        </BottomSheet>
      </>
    );
  }

  return (
    <div className="relative z-30 flex h-14 shrink-0 items-center gap-2.5 border-b border-[#e5e7eb] bg-white px-5">
      <div className="w-[240px] shrink-0">
        <SearchBar
          variant="compact"
          value={props.searchValue}
          onChange={props.onSearchValueChange}
          onSubmitKeyword={props.onSubmitKeyword}
          onSelectRegion={props.onSelectRegion}
          placeholder="지역명으로 지도 이동"
          ariaLabel="지역 검색"
        />
      </div>
      <div className="px-1" aria-hidden="true">
        <div className="h-5 w-px bg-[#e5e7eb]" />
      </div>
      {CHIPS.map(({ key, label }) =>
        chip(
          key,
          label,
          'flex h-[37.5px] shrink-0 items-center gap-1.5 rounded-[14px] border border-[#e5e7eb] bg-white px-3.5 text-[13px] font-semibold text-[#4a5565] hover:bg-[#f7f8fa]',
          'size-3.5',
        ),
      )}
      <p className="ml-auto shrink-0 text-[13px] font-bold text-[#1e2939]">{props.countText}</p>
      {filtersOpen && (
        <FilterPopover onClose={() => setFiltersOpen(false)}>
          <FilterPanel draft={draft} onChangeDraft={onChangeDraft} onApply={apply} onReset={onReset} />
        </FilterPopover>
      )}
    </div>
  );
}

/**
 * 데스크톱 필터 팝오버 — 지도를 가리지 않게 모달로 만들지 않는다(포커스를 가두지 않음). 열리면 첫 입력으로 포커스, Esc·바깥
 * 클릭으로 닫히고 닫힐 때 연 칩으로 포커스를 돌려준다.
 */
function FilterPopover({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    const trigger = document.activeElement as HTMLElement | null;
    ref.current?.querySelector<HTMLElement>('input, button')?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCloseRef.current();
      }
    };
    const onPointer = (event: PointerEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node) && !(event.target as HTMLElement).closest('[aria-haspopup="dialog"]')) {
        onCloseRef.current();
      }
    };
    document.addEventListener('keydown', onKey);
    document.addEventListener('pointerdown', onPointer);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('pointerdown', onPointer);
      trigger?.focus();
    };
  }, []);

  return (
    <div
      ref={ref}
      role="dialog"
      aria-label="필터"
      className="absolute top-[calc(100%+8px)] left-[290px] z-40 max-h-[calc(100dvh-160px)] w-[320px] overflow-y-auto rounded-[16px] border border-[#e5e7eb] bg-white p-5 shadow-[0_10px_20px_rgba(0,0,0,0.12)]"
    >
      {children}
    </div>
  );
}
