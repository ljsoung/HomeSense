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
  pendingFavoriteId: number | null;
}

/**
 * HOME-01 구성요소 5 — GET /api/complexes/popular 연동. 완료 조건: 모바일은 1.2장 보이는 가로
 * 스크롤 캐러셀(Figma 모바일 프레임 확인), 데스크톱/태블릿은 그리드 — 같은 목록 한 벌의 배치만 바뀐다.
 */
export function RecommendedComplexes({ favoritedIds, onToggleFavorite, pendingFavoriteId }: RecommendedComplexesProps) {
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
        <span className="flex items-center gap-0.5 text-[13px] font-semibold text-muted">
          전체보기
          <ChevronRightIcon className="size-4" />
        </span>
      </div>

      {loading ? (
        <div className="flex justify-center py-10">
          <Spinner />
        </div>
      ) : (
        // 목록은 한 벌이다(반응형 렌더 규칙) — 모바일은 가로 스크롤(카드 83% 폭), md 이상은 같은 목록이 그리드가 된다.
        // 예전엔 두 벌을 렌더하고 CSS로 한쪽을 숨겨 하트 버튼이 카드 수의 두 배였다.
        <ul
          aria-label="추천 단지"
          className="-mx-4 flex gap-3 overflow-x-auto px-4 pb-3 md:mx-0 md:grid md:grid-cols-2 md:gap-5 md:overflow-visible md:px-0 md:pb-0 lg:grid-cols-4"
        >
          {complexes.map((complex) => (
            <li key={complex.complexId} className="flex w-[83%] shrink-0 md:w-auto">
              <ComplexCard
                complex={complex}
                isFavorited={favoritedIds.has(complex.complexId)}
                onToggleFavorite={() => onToggleFavorite(complex.complexId)}
                favoritePending={pendingFavoriteId === complex.complexId}
                className="w-full"
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
