import { useEffect, useState } from 'react';
import { ChevronRightIcon } from '../../components/icons/ChevronRightIcon';
import { ComplexCard } from '../../components/ui/ComplexCard';
import { Spinner } from '../../components/ui/Spinner';
import { getPopularComplexes } from '../../features/complex/api';
import type { ComplexSummaryResponse } from '../../features/complex/types';

const POPULAR_LIMIT = 8;

interface RecommendedComplexesProps {
  favoritedIds: Set<number>;
  onToggleFavorite: (complexId: number) => void;
}

/**
 * HOME-01 구성요소 5 — GET /api/complexes/popular 연동. 완료 조건: 모바일은 1.2장 보이는 가로
 * 스크롤 캐러셀(Figma 모바일 프레임 확인), 데스크톱/태블릿은 그리드.
 */
export function RecommendedComplexes({ favoritedIds, onToggleFavorite }: RecommendedComplexesProps) {
  const [complexes, setComplexes] = useState<ComplexSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    getPopularComplexes(POPULAR_LIMIT)
      .then((result) => {
        if (!cancelled) {
          setComplexes(result);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setComplexes([]);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!loading && complexes.length === 0) {
    return null;
  }

  return (
    <section className="mx-auto max-w-[1280px] px-4 pt-8 pb-4 md:px-8">
      <div className="flex items-center justify-between pb-5">
        <h2 className="text-[18px] font-extrabold tracking-[-0.3px] text-[#101828] md:text-[20px]">인기 검색 지역 · 추천 단지</h2>
        <span className="flex items-center gap-0.5 text-[13px] font-semibold text-[#99a1af]">
          전체보기
          <ChevronRightIcon className="size-4" />
        </span>
      </div>

      {loading ? (
        <div className="flex justify-center py-10">
          <Spinner />
        </div>
      ) : (
        <>
          <div className="-mx-4 flex gap-3 overflow-x-auto px-4 pb-3 md:hidden">
            {complexes.map((complex) => (
              <ComplexCard
                key={complex.complexId}
                complex={complex}
                isFavorited={favoritedIds.has(complex.complexId)}
                onToggleFavorite={() => onToggleFavorite(complex.complexId)}
                className="w-[83%] shrink-0"
              />
            ))}
          </div>
          <div className="hidden grid-cols-2 gap-5 md:grid lg:grid-cols-4">
            {complexes.map((complex) => (
              <ComplexCard
                key={complex.complexId}
                complex={complex}
                isFavorited={favoritedIds.has(complex.complexId)}
                onToggleFavorite={() => onToggleFavorite(complex.complexId)}
              />
            ))}
          </div>
        </>
      )}
    </section>
  );
}
