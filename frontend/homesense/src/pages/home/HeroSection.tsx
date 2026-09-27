import { useEffect, useState } from 'react';
import { SearchBar } from '../../components/ui/SearchBar';
import { SegmentedToggle } from '../../components/ui/SegmentedToggle';
import { getPopularKeywords } from '../../features/search/api';
import { DEAL_TYPE_OPTIONS, HOUSING_TYPE_OPTIONS, type DealTypeUi } from '../../features/search/searchParams';
import { useExecuteSearch } from '../../features/search/useExecuteSearch';
import type { HousingType } from '../../features/complex/types';

/**
 * HOME-01 구성요소 2 — 히어로 검색. UIC-03(SearchBar)을 매물유형/거래유형 토글, 인기 검색어 칩과
 * 함께 구성한다. [SCR-HOME-01] 검색 실행은 SRCH-01과 공유하는 useExecuteSearch 하나로 통일했다
 * (확정 사항 #1) — 예전엔 이 컴포넌트가 직접 `housingType`(단수)/`dealType`(SALE/JEONSE/WOLSE)
 * 파라미터로 `/search`에 navigate했는데, 실제 백엔드 검색 계약은 `housingTypes`(복수, 반복
 * 파라미터)와 `dealCategory`/`rentType`(거래유형)이라 그대로는 SRCH-01이 못 읽는 URL을 만들고
 * 있었다. 매물유형은 여기서는 여전히 단일 선택(히어로 UX 자체를 바꾸지 않음)이라 배열로 감싸
 * overrides로 넘긴다.
 */
export function HeroSection() {
  const executeSearch = useExecuteSearch();
  const [housingType, setHousingType] = useState<HousingType>('APT');
  const [dealType, setDealType] = useState<DealTypeUi>('매매');
  const [keyword, setKeyword] = useState('');
  const [popularKeywords, setPopularKeywords] = useState<string[]>([]);

  useEffect(() => {
    let cancelled = false;
    getPopularKeywords(5)
      .then((results) => {
        if (!cancelled) {
          setPopularKeywords(results.map((r) => r.keyword));
        }
      })
      .catch(() => {
        // 인기 검색어는 부가 정보라 실패해도 조용히 빈 배열(영역 숨김)로 둔다.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const overrides = { housingTypes: [housingType], dealType };

  const runKeywordSearch = (searchKeyword: string) => {
    const trimmed = searchKeyword.trim();
    if (!trimmed) return;
    executeSearch({ mode: 'keyword', keyword: trimmed }, undefined, overrides);
  };

  const runChipSearch = (chipKeyword: string) => {
    executeSearch({ mode: 'chip', keyword: chipKeyword }, undefined, overrides);
  };

  return (
    <section className="relative overflow-hidden bg-[#0f5c54] py-10 md:py-14">
      <div
        className="absolute inset-0"
        style={{
          backgroundImage:
            'linear-gradient(145deg, rgba(11,74,67,0.92) 8%, rgba(15,92,84,0.8) 50%, rgba(8,55,50,0.88) 92%)',
        }}
      />
      <div className="relative mx-auto flex max-w-[720px] flex-col items-center px-4">
        <p className="pb-3 text-[11px] font-semibold tracking-[1.3px] text-white/60 uppercase">
          국토교통부 실거래가 공개 데이터
        </p>
        <h1 className="pb-8 text-center text-[24px] font-bold tracking-[-0.5px] text-white md:text-[32px]">
          실거래가로 보는 우리 동네 아파트
        </h1>

        <div className="w-full max-w-[688px] rounded-[16px] bg-white shadow-[0_25px_25px_rgba(0,0,0,0.25)]">
          <div className="flex flex-wrap items-center gap-3 border-b border-[#f3f4f6] px-4 pt-4 pb-3">
            <SegmentedToggle options={HOUSING_TYPE_OPTIONS} value={housingType} onChange={setHousingType} />
            <div className="hidden h-5 w-px bg-[#e5e7eb] md:block" />
            <SegmentedToggle options={DEAL_TYPE_OPTIONS} value={dealType} onChange={setDealType} />
          </div>
          <SearchBar
            value={keyword}
            onChange={setKeyword}
            onSubmitKeyword={runKeywordSearch}
            onSelectRegion={(region) =>
              executeSearch({ mode: 'region', regionCode: region.legalDongCd, regionLabel: region.fullPath }, undefined, overrides)
            }
          />
        </div>

        {popularKeywords.length > 0 && (
          <div className="flex flex-wrap items-center justify-center gap-2 pt-4">
            <span className="text-[11px] font-medium text-white/50">인기 검색</span>
            {popularKeywords.map((popularKeyword) => (
              <button
                key={popularKeyword}
                type="button"
                onClick={() => {
                  setKeyword(popularKeyword);
                  runChipSearch(popularKeyword);
                }}
                className="rounded-full border border-white/20 px-2.5 py-0.5 text-[12px] text-white/75 hover:bg-white/10"
              >
                {popularKeyword}
              </button>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
