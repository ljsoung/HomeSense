import axios from 'axios';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { BottomSheet } from '../../components/ui/BottomSheet';
import { ComplexCard } from '../../components/ui/ComplexCard';
import { EmptyState } from '../../components/ui/EmptyState';
import { FilterPanel } from '../../components/ui/FilterPanel';
import { Pagination } from '../../components/ui/Pagination';
import { SearchBar } from '../../components/ui/SearchBar';
import { Spinner } from '../../components/ui/Spinner';
import { AlertTriangleIcon } from '../../components/icons/AlertTriangleIcon';
import { FilterIcon } from '../../components/icons/FilterIcon';
import { MapFoldIcon } from '../../components/icons/MapFoldIcon';
import { SearchIcon } from '../../components/icons/SearchIcon';
import { MainLayout } from '../../components/layout/MainLayout';
import { searchComplexes } from '../../features/complex/api';
import type { ComplexSummaryResponse } from '../../features/complex/types';
import {
  buildApiQuery,
  defaultFilters,
  hasSearchCondition,
  KEYWORD_ISSUE_MESSAGE,
  keywordIssue,
  parseSearchParams,
  serializeSearchParams,
  SORT_OPTIONS,
  urlKeywordIssue,
  type KeywordIssue,
  type SearchFilters,
} from '../../features/search/searchParams';
import { useExecuteSearch } from '../../features/search/useExecuteSearch';
import type { PageMeta } from '../../types/api';
import { getErrorMessage } from '../../lib/apiError';
import { useFavoriteToggle } from '../home/useFavoriteToggle';

const extractErrorMessage = getErrorMessage;

/**
 * 뒤로가기 복원용 세션 캐시 — SPA 내비게이션(전체 새로고침 없음) 동안만 살아있는 모듈 스코프
 * Map이다. `location.key`(react-router가 히스토리 엔트리마다 부여하는 안정 키)로 색인해, 카드
 * 클릭(DTL-01로 push) 후 브라우저 뒤로가기로 돌아오면 이 페이지가 리마운트되며 같은 key로 저장된
 * 누적 목록·스크롤 위치를 그대로 복원한다 — 데스크톱/태블릿은 필터가 페이지 이동마다 URL을
 * `replace`해 히스토리 엔트리가 하나뿐이라 "같은 페이지"가 자연히 유지되고, 모바일은 여러 페이지를
 * 누적한 리스트 자체를 이 캐시가 들고 있어야 "누적된 채로 복원"이 된다(확정 사항).
 */
const scrollCache = new Map<
  string,
  { accumulated: ComplexSummaryResponse[]; pageMeta: PageMeta | null; scrollY: number; url: string }
>();

function useIsMobile() {
  const query = '(max-width: 767px)';
  const [isMobile, setIsMobile] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const mql = window.matchMedia(query);
    const handler = () => setIsMobile(mql.matches);
    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, []);
  return isMobile;
}

/**
 * SCR-SRCH-01 — 검색결과 목록 화면. HOME-01의 MainLayout/ComplexCard/EmptyState/Spinner/SearchBar와
 * useFavoriteToggle을 그대로 재사용하고, 이 화면에서 새로 만든 UIC-04(FilterPanel)/UIC-06
 * (Pagination)/BottomSheet를 조합한다. URL 쿼리스트링이 필터·정렬·페이지의 유일한 소스다 — 이
 * 컴포넌트는 그 값을 파생시켜 렌더링할 뿐, 별도 state로 복제하지 않는다(필터 패널의 draft 제외).
 */
export function SearchResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  const isMobile = useIsMobile();
  const executeSearch = useExecuteSearch();
  const { favoritedIds, toggleFavorite, pendingFavoriteId } = useFavoriteToggle();

  const filters = useMemo(() => parseSearchParams(searchParams), [searchParams]);
  const [keywordInput, setKeywordInput] = useState(filters.keyword ?? filters.regionLabel ?? '');
  // 재검색 바에서 제출한 검색어의 규칙 위반(제출 시점 검사). URL에서 복원한 검색어의 위반은
  // 아래 invalidUrlKeyword로 따로 파생한다.
  const [submitIssue, setSubmitIssue] = useState<KeywordIssue | null>(null);

  const [accumulated, setAccumulated] = useState<ComplexSummaryResponse[]>([]);
  const [pageMeta, setPageMeta] = useState<PageMeta | null>(null);
  const [status, setStatus] = useState<'idle' | 'loading' | 'loadingMore' | 'error'>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [draft, setDraft] = useState<SearchFilters>(filters);
  const [sheetOpen, setSheetOpen] = useState(false);

  // hasFetchedRef가 true가 되기 전(=아직 한 번도 실제 응답을 받지 못한 상태)에는 절대 캐시에
  // 쓰지 않는다 — React StrictMode의 개발 모드 이중 마운트(mount→cleanup→mount)가 데이터 조회
  // effect보다 이 정리 로직을 먼저 훑고 지나가며 "아직 비어있는 초기 상태"를 이 URL의 캐시로
  // 잘못 저장해, 뒤이은 진짜 실행이 그 텅 빈 캐시를 "히트"로 오인해 실제 API 호출 자체를 건너뛰는
  // 버그가 실측으로 확인됐다(대상 URL은 그대로인데 accumulated=[]/pageMeta=null인 엔트리가
  // 조건 없음이 아닌 화면에서도 "조건에 맞는 단지가 없습니다"로 잘못 렌더링됨).
  const dataRef = useRef({ accumulated, pageMeta });
  const hasFetchedRef = useRef(false);
  useEffect(() => {
    dataRef.current = { accumulated, pageMeta };
  }, [accumulated, pageMeta]);

  // 필터 패널이 닫혀 있을 때는(적용 직후 등) draft가 항상 최신 URL 필터를 그대로 반영해야 한다 —
  // "prop이 바뀌면 state를 리셋한다"는 React 공식 패턴대로 effect가 아니라 렌더 중에 조정한다
  // (react-hooks/set-state-in-effect가 effect 본문의 동기 setState를 금지한다). filters는
  // useMemo(searchParams)라 URL이 실제로 바뀔 때만 참조가 바뀐다.
  const [filtersSnapshot, setFiltersSnapshot] = useState(filters);
  if (filters !== filtersSnapshot) {
    setFiltersSnapshot(filters);
    setDraft(filters);
    setKeywordInput(filters.keyword ?? filters.regionLabel ?? '');
  }

  // 뒤로가기 복원 + 실제 데이터 조회. setState 호출은 전부 비동기 콜백(마이크로태스크/프라미스
  // 콜백) 안에서만 일어나게 해 effect 본문에서의 동기 setState를 피한다(react-hooks/set-state-in-effect).
  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    const run = async () => {
      await Promise.resolve();
      if (cancelled) return;

      const cached = scrollCache.get(location.key);
      if (cached && cached.url === searchParams.toString()) {
        setAccumulated(cached.accumulated);
        setPageMeta(cached.pageMeta);
        setStatus('idle');
        setErrorMessage(null);
        hasFetchedRef.current = true;
        requestAnimationFrame(() => window.scrollTo(0, cached.scrollY));
        return;
      }

      // URL로 들어온 검색어(직접 입력·새로고침·뒤로가기·홈 히어로에서 1글자로 검색)도 제출 시점과 같은
      // 규칙으로 요청 전에 막는다 — 서버가 400을 돌려줘 일반 오류 화면이 뜨는 대신 안내 문구를 보인다.
      if (!hasSearchCondition(filters) || urlKeywordIssue(filters)) {
        setAccumulated([]);
        setPageMeta(null);
        setStatus('idle');
        setErrorMessage(null);
        return;
      }

      const isFreshPage = filters.page === 1;
      setStatus(isFreshPage ? 'loading' : 'loadingMore');
      setErrorMessage(null);

      try {
        const { data, pageMeta: meta } = await searchComplexes(buildApiQuery(filters), controller.signal);
        if (cancelled) return;
        setAccumulated((prev) => {
          const base = isMobile && !isFreshPage ? prev : [];
          const merged = [...base, ...data];
          return Array.from(new Map(merged.map((c) => [c.complexId, c])).values());
        });
        setPageMeta(meta);
        setStatus('idle');
        hasFetchedRef.current = true;
      } catch (error) {
        if (cancelled || axios.isCancel(error)) return;
        setStatus('error');
        setErrorMessage(extractErrorMessage(error));
      }
    };

    void run();
    return () => {
      cancelled = true;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams.toString(), location.key, isMobile]);

  // 스크롤 위치+누적 목록을 이 히스토리 엔트리 캐시에 계속 저장해 뒤로가기 복원에 대비한다.
  // hasFetchedRef 가드(위 주석 참고) — 실제 응답을 한 번도 못 받은 채로(StrictMode 이중 마운트의
  // 첫 번째 클린업 등) 저장을 시도하면 아무 것도 하지 않는다.
  //
  // 언마운트 시점에 window.scrollY를 다시 읽어 저장하는 방식은 실제로 틀린 값을 기록한다는 게
  // Playwright로 실측 확인됐다 — 카드 클릭으로 짧은 DTL-01 자리표시 페이지가 마운트되면 브라우저가
  // "문서 높이가 스크롤 위치보다 짧아졌다"는 이유로 스크롤을 즉시 0으로 clamp하는데, React가 이
  // 컴포넌트의 cleanup(및 그 안의 저장 호출)을 실행하는 시점엔 이미 그 clamp가 끝난 뒤라 0이 캐시를
  // 덮어써 버린다. 그래서 언마운트 시 별도로 저장하지 않고, 스크롤이 실제로 일어나는 동안의 'scroll'
  // 이벤트 리스너만으로 캐시를 계속 최신 상태로 유지한다 — 페이지를 떠나기 직전의 마지막 'scroll'
  // 이벤트가 이미 올바른 값을 기록해 두므로 언마운트 시점에 다시 읽을 필요가 없다.
  useEffect(() => {
    const save = () => {
      if (!hasFetchedRef.current) return;
      scrollCache.set(location.key, { ...dataRef.current, scrollY: window.scrollY, url: searchParams.toString() });
    };
    window.addEventListener('scroll', save, { passive: true });
    return () => window.removeEventListener('scroll', save);
  }, [location.key, searchParams]);

  const navigateFilters = (next: SearchFilters, options: { replace?: boolean } = { replace: true }) => {
    setSearchParams(serializeSearchParams(next), options);
  };

  // base로 filters(URL에 이미 커밋된 값)가 아니라 draft(필터 패널의 현재 선택 상태, "필터 적용"을
  // 누르지 않았어도 화면에는 이미 반영돼 보이는 값)를 넘긴다 — 그렇지 않으면 사용자가 거래유형 등을
  // 바꾸고 "적용" 없이 재검색바로 검색할 때 그 변경이 조용히 무시되고 이전 URL의 값으로 검색되는
  // 버그가 있었다(실측 확인: 월세 적용 후 매매로 바꾸고 Enter → 요청이 여전히 rentType=WOLSE로 나감).
  const handleSubmitKeyword = (value: string) => {
    const issue = keywordIssue(value);
    if (issue) {
      setSubmitIssue(issue);
      return;
    }
    setSubmitIssue(null);
    const trimmed = value.trim();
    if (!trimmed) return;
    executeSearch({ mode: 'keyword', keyword: trimmed }, draft);
  };

  const handleSelectRegion = (region: { legalDongCd: string; fullPath: string }) => {
    setSubmitIssue(null);
    executeSearch({ mode: 'region', regionCode: region.legalDongCd, regionLabel: region.fullPath }, draft);
  };

  const handleApplyFilter = () => {
    navigateFilters({ ...draft, page: 1 });
    setSheetOpen(false);
  };

  const handleResetFilter = () => {
    const reset = defaultFilters();
    setDraft({ ...reset, regionCode: filters.regionCode, regionLabel: filters.regionLabel, keyword: filters.keyword });
  };

  const handleSortChange = (sort: SearchFilters['sort']) => {
    navigateFilters({ ...filters, sort, page: 1 });
  };

  const handlePageChange = (page: number) => {
    navigateFilters({ ...filters, page });
    window.scrollTo(0, 0);
  };

  const handleLoadMore = () => {
    if (status === 'loadingMore' || !pageMeta || filters.page >= pageMeta.totalPages) return;
    navigateFilters({ ...filters, page: filters.page + 1 });
  };

  // 모바일 무한스크롤 — sentinel이 보이면 다음 페이지를 자동으로 불러온다.
  const sentinelRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!isMobile || !sentinelRef.current) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) handleLoadMore();
      },
      { rootMargin: '200px' },
    );
    observer.observe(sentinelRef.current);
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isMobile, filters.page, pageMeta, status]);

  const conditionLabel = filters.regionLabel ?? filters.keyword ?? '선택한 지역';
  const noCondition = !hasSearchCondition(filters);
  const invalidUrlKeyword = urlKeywordIssue(filters);
  // 입력창의 안내 문구: 방금 제출한 검색어의 위반이 우선이고, 없으면 URL 검색어의 위반을 보인다.
  const inlineIssue = submitIssue ?? invalidUrlKeyword;
  const isFirstLoad = status === 'loading' && accumulated.length === 0;
  const hasMore = !!pageMeta && filters.page < pageMeta.totalPages;
  const activeFilterCount = [
    filters.dealType !== '매매',
    filters.housingTypes.length !== 2,
    filters.areaMin !== defaultFilters().areaMin || filters.areaMax !== defaultFilters().areaMax,
    filters.amountMin !== defaultFilters().amountMin || filters.amountMax !== defaultFilters().amountMax,
    filters.buildYearMin !== defaultFilters().buildYearMin || filters.buildYearMax !== defaultFilters().buildYearMax,
  ].filter(Boolean).length;

  return (
    <MainLayout>
      <div className="mx-auto flex max-w-[1280px] flex-col gap-5 px-4 py-5 md:flex-row md:items-start md:px-8 md:py-8">
        <aside className="hidden w-[280px] shrink-0 rounded-[16px] border border-[#e5e7eb] bg-white p-5 md:block">
          <FilterPanel draft={draft} onChangeDraft={setDraft} onApply={handleApplyFilter} onReset={handleResetFilter} />
        </aside>

        <div className="min-w-0 flex-1">
          <div className="mb-4 flex flex-col gap-3 rounded-[16px] border border-[#e5e7eb] bg-white p-4">
            <SearchBar
              variant="inline"
              value={keywordInput}
              onChange={(value) => {
                setKeywordInput(value);
                setSubmitIssue(null);
              }}
              onSubmitKeyword={handleSubmitKeyword}
              onSelectRegion={handleSelectRegion}
              placeholder="지역명·단지명(건물명)으로 재검색"
            />
            {inlineIssue && (
              <p role="alert" className="text-[12px] text-[#e7000b]">
                {KEYWORD_ISSUE_MESSAGE[inlineIssue]}
              </p>
            )}
            <p className="text-[13px] text-[#6a7282]">
              <span className="font-semibold text-[#101828]">&lsquo;{conditionLabel}&rsquo;</span> 검색 결과
            </p>
          </div>

          {/* 모바일 전용 — Figma(24:9405)는 "필터"/"지도" 두 알약 버튼을 한 행에 나란히 둔다(지도
              링크가 이 행에 있어 아래 정렬 바에는 모바일에서 지도 링크를 다시 넣지 않는다). */}
          <div className="mb-4 flex gap-2 md:hidden">
            <button
              type="button"
              onClick={() => setSheetOpen(true)}
              className="flex flex-1 items-center justify-center gap-1.5 rounded-[12px] border border-[#e5e7eb] bg-white py-2.5 text-[13.5px] font-semibold text-[#364153]"
            >
              <FilterIcon className="size-4" />
              필터
              {activeFilterCount > 0 && (
                <span className="flex size-5 items-center justify-center rounded-full bg-brand text-[11px] font-semibold text-white">
                  {activeFilterCount}
                </span>
              )}
            </button>
            <a
              href={`/map?${(() => {
                const p = serializeSearchParams(filters);
                p.delete('page');
                return p.toString();
              })()}`}
              className="flex flex-1 items-center justify-center gap-1.5 rounded-[12px] border border-[#e5e7eb] bg-white py-2.5 text-[13.5px] font-semibold text-[#364153]"
            >
              <MapFoldIcon className="size-4" />
              지도
            </a>
          </div>

          {noCondition ? (
            <div className="flex min-h-[320px] items-center justify-center rounded-[16px] border border-[#e5e7eb] bg-white">
              <EmptyState
                icon={<SearchIcon className="size-5" />}
                title="검색어를 입력하세요"
                description="지역명이나 단지명으로 검색해보세요."
              />
            </div>
          ) : invalidUrlKeyword ? (
            <div className="flex min-h-[320px] items-center justify-center rounded-[16px] border border-[#e5e7eb] bg-white">
              <EmptyState
                icon={<SearchIcon className="size-5" />}
                title={KEYWORD_ISSUE_MESSAGE[invalidUrlKeyword]}
                description="검색어를 고쳐 다시 검색해보세요."
              />
            </div>
          ) : status === 'error' && accumulated.length === 0 ? (
            <div className="flex min-h-[320px] flex-col items-center justify-center gap-3 rounded-[16px] border border-[#fee2e2] bg-[#fef2f2] p-6 text-center">
              <AlertTriangleIcon className="size-6 text-[#e7000b]" />
              <p className="text-[13px] text-[#7f1d1d]">{errorMessage}</p>
              <button
                type="button"
                onClick={() => navigateFilters(filters)}
                className="rounded-[10px] bg-white px-4 py-2 text-[13px] font-semibold text-[#e7000b] shadow-[0_1px_2px_rgba(0,0,0,0.1)]"
              >
                다시 시도
              </button>
            </div>
          ) : isFirstLoad ? (
            <div className="grid gap-3">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="h-[130px] animate-pulse rounded-[16px] bg-[#f3f4f6]" />
              ))}
            </div>
          ) : accumulated.length === 0 ? (
            <EmptyState
              variant="inducement"
              icon={<SearchIcon className="size-6" />}
              title="조건에 맞는 단지가 없습니다"
              description="검색 조건을 조정하거나 필터를 초기화해보세요."
              actions={
                <button
                  type="button"
                  onClick={() => navigateFilters({ ...defaultFilters(), regionCode: filters.regionCode, regionLabel: filters.regionLabel, keyword: filters.keyword })}
                  className="rounded-[10px] bg-brand px-4 py-2 text-[13px] font-semibold text-white"
                >
                  필터 초기화
                </button>
              }
            />
          ) : (
            <>
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <p className="text-[13px] text-[#6a7282]">
                  총 <span className="font-bold text-[#101828]">{pageMeta?.totalElements.toLocaleString('ko-KR') ?? 0}</span>건
                </p>
                <div className="flex items-center gap-2">
                  <div role="radiogroup" aria-label="정렬" className="flex gap-1">
                    {SORT_OPTIONS.map((option) => (
                      <button
                        key={option.value}
                        type="button"
                        role="radio"
                        aria-checked={filters.sort === option.value}
                        aria-pressed={filters.sort === option.value}
                        onClick={() => handleSortChange(option.value)}
                        className={`rounded-full px-3 py-1.5 text-[12.5px] font-medium ${
                          filters.sort === option.value ? 'bg-brand text-white' : 'bg-[#f3f4f6] text-[#4a5565]'
                        }`}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                  <a
                    href={`/map?${(() => {
                      const p = serializeSearchParams(filters);
                      p.delete('page');
                      return p.toString();
                    })()}`}
                    className="hidden items-center gap-1 rounded-full border border-[#e5e7eb] px-3 py-1.5 text-[12.5px] font-medium text-[#364153] hover:bg-[#f7f8fa] md:flex"
                  >
                    <MapFoldIcon className="size-3.5" />
                    지도로 보기
                  </a>
                </div>
              </div>

              <div className="flex flex-col gap-3">
                {accumulated.map((complex) => (
                  <ComplexCard
                    key={complex.complexId}
                    complex={complex}
                    variant="list"
                    isFavorited={favoritedIds.has(complex.complexId)}
                    onToggleFavorite={() => toggleFavorite(complex.complexId)}
                    favoritePending={pendingFavoriteId === complex.complexId}
                  />
                ))}
              </div>

              {isMobile ? (
                <div ref={sentinelRef} className="flex items-center justify-center py-6">
                  {status === 'loadingMore' && <Spinner className="size-6" />}
                  {!hasMore && accumulated.length > 0 && <p className="text-[12px] text-[#99a1af]">마지막 결과입니다</p>}
                  {hasMore && status !== 'loadingMore' && (
                    <button
                      type="button"
                      onClick={handleLoadMore}
                      className="rounded-[10px] border border-[#e5e7eb] px-4 py-2 text-[13px] font-semibold text-[#364153]"
                    >
                      더 불러오기
                    </button>
                  )}
                </div>
              ) : (
                <div className="pt-6">
                  <Pagination currentPage={filters.page} totalPages={pageMeta?.totalPages ?? 1} onPageChange={handlePageChange} />
                </div>
              )}
            </>
          )}
        </div>
      </div>

      <BottomSheet open={sheetOpen} onClose={() => setSheetOpen(false)} title="필터">
        <FilterPanel draft={draft} onChangeDraft={setDraft} onApply={handleApplyFilter} onReset={handleResetFilter} showHeader={false} />
      </BottomSheet>
    </MainLayout>
  );
}
