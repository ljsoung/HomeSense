import { useEffect, useMemo, useRef } from 'react';
import { Link } from 'react-router-dom';
import { FilterIcon } from '../../components/icons/FilterIcon';
import { MapPinIcon } from '../../components/icons/MapPinIcon';
import { ComplexCard } from '../../components/ui/ComplexCard';
import { EmptyState } from '../../components/ui/EmptyState';
import type { ComplexMapPointResponse } from '../../features/complex/types';
import { DATA_SOURCE_TEXT, formatMapCount, sortMapItems } from '../../features/map/mapList';
import { SORT_OPTIONS, type SortUi } from '../../features/search/searchParams';

interface MapListPanelProps {
  items: ComplexMapPointResponse[];
  truncated: boolean;
  initialLoading: boolean;
  /** 헤더 앞부분(지역 라벨 등). 없으면 "지도 영역". */
  areaLabel: string;
  /** 목록 위 제목 대신 쓰는 접두어 — 모바일 시트는 "주변 단지"(Figma 26:14329). */
  countPrefix?: 'area' | 'nearby';
  sort: SortUi;
  onSortChange: (sort: SortUi) => void;
  selectedId: number | null;
  onSelect: (item: ComplexMapPointResponse) => void;
  favoritedIds: ReadonlySet<number>;
  pendingFavoriteId: number | null;
  onToggleFavorite: (complexId: number) => void;
  onResetFilters: () => void;
  /** SRCH-01 목록으로 가는 주소 — 검색 조건(지역·키워드)이 있을 때만. */
  searchHref?: string;
  /** 선택이 지도(핀)에서 왔으면 그 행을 목록에서 보이게 스크롤한다. */
  scrollSelectedIntoView: boolean;
  /** 헤더를 그리지 않는다(모바일 시트는 자기 헤더가 건수를 보인다). */
  hideHeader?: boolean;
}

/**
 * MAP-01 구성요소 2 — 목록 패널(FR-4.5). 지도는 키보드·스크린리더로 쓰기 어려워 이 목록이 접근 가능한 대체 수단이다:
 * 건수는 aria-live로 알리고, 행을 누르면 지도에서 그 단지로 이동해 팝업을 연다. 행은 SRCH-01 카드(UIC-05)의 'compact'
 * 변형이다.
 */
export function MapListPanel({
  items,
  truncated,
  initialLoading,
  areaLabel,
  countPrefix = 'area',
  sort,
  onSortChange,
  selectedId,
  onSelect,
  favoritedIds,
  pendingFavoriteId,
  onToggleFavorite,
  onResetFilters,
  searchHref,
  scrollSelectedIntoView,
  hideHeader = false,
}: MapListPanelProps) {
  const sorted = useMemo(() => sortMapItems(items, sort), [items, sort]);
  const listRef = useRef<HTMLDivElement>(null);
  const countText = formatMapCount(items.length, truncated);

  useEffect(() => {
    if (!scrollSelectedIntoView || selectedId === null) return;
    listRef.current?.querySelector(`[data-complex-id="${selectedId}"]`)?.scrollIntoView({ block: 'nearest' });
  }, [selectedId, scrollSelectedIntoView]);

  return (
    <div className="flex h-full min-h-0 flex-col bg-white">
      {!hideHeader && (
        <div className="flex shrink-0 items-center justify-between gap-2 border-b border-[#f3f4f6] px-4 py-3">
          <p className="min-w-0 truncate text-[13px] font-semibold leading-[19.5px] text-[#6a7282]" aria-live="polite">
            {countPrefix === 'nearby' ? '주변 단지 ' : `${areaLabel} · `}
            <span className="font-bold text-[#101828]">{initialLoading ? '…' : countText}</span>
          </p>
          <div className="flex shrink-0 items-center gap-3">
            {searchHref && (
              <Link to={searchHref} className="text-[12px] font-medium text-brand hover:underline">
                목록으로
              </Link>
            )}
            <label className="relative flex items-center gap-1.5 text-[12px] font-medium text-[#6a7282]">
              <FilterIcon className="size-3.5" aria-hidden="true" />
              <span>{SORT_OPTIONS.find((o) => o.value === sort)?.label ?? '정렬'}</span>
              <select
                aria-label="정렬"
                value={sort}
                onChange={(event) => onSortChange(event.target.value as SortUi)}
                className="absolute inset-0 cursor-pointer opacity-0"
              >
                {SORT_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>
      )}

      <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto">
        {initialLoading ? (
          <div aria-busy="true" aria-label="목록 불러오는 중">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex gap-3 px-4 py-3.5" data-testid="map-list-skeleton">
                <div className="h-14 w-[72px] animate-pulse rounded-[14px] bg-[#f3f4f6]" />
                <div className="flex flex-1 flex-col gap-1.5">
                  <div className="h-3 w-20 animate-pulse rounded bg-[#f3f4f6]" />
                  <div className="h-3.5 w-2/3 animate-pulse rounded bg-[#f3f4f6]" />
                  <div className="h-4 w-1/3 animate-pulse rounded bg-[#f3f4f6]" />
                </div>
              </div>
            ))}
          </div>
        ) : sorted.length === 0 ? (
          <div className="px-4 py-8">
            <EmptyState
              icon={<MapPinIcon className="size-5" />}
              title="이 지역에 조건에 맞는 단지가 없습니다"
              description="지도를 옮기거나 필터를 초기화해보세요."
              actions={
                <button
                  type="button"
                  onClick={onResetFilters}
                  className="rounded-[10px] bg-brand px-4 py-2 text-[13px] font-semibold text-white"
                >
                  필터 초기화
                </button>
              }
            />
          </div>
        ) : (
          <ul aria-label="지도 단지 목록">
            {sorted.map((item) => (
              <li key={item.complexId}>
                <ComplexCard
                  complex={item}
                  variant="compact"
                  selected={item.complexId === selectedId}
                  onSelect={() => onSelect(item)}
                  isFavorited={favoritedIds.has(item.complexId)}
                  favoritePending={pendingFavoriteId === item.complexId}
                  onToggleFavorite={() => onToggleFavorite(item.complexId)}
                />
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="shrink-0 border-t border-[#f3f4f6] bg-[#f9fafb] px-4 py-2">
        <p className="text-[10px] leading-[16.25px] text-[#99a1af]">{DATA_SOURCE_TEXT}</p>
      </div>
    </div>
  );
}
