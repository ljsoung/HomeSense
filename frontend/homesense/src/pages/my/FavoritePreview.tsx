import { Link } from 'react-router-dom';
import { HeartIcon } from '../../components/icons/HeartIcon';
import { HomeIcon } from '../../components/icons/HomeIcon';
import { EmptyState } from '../../components/ui/EmptyState';
import type { FavoritePropertySummaryResponse } from '../../features/favorite/types';
import { formatArea, formatKoreanPrice } from '../../lib/format';
import { MY_ROUTES } from '../../routes/paths';
import type { FavoritePreview as FavoritePreviewData } from './myPageData';
import { FAVORITE_PREVIEW_COUNT } from './myPageData';
import { CardBody, CardFooterLink, RowSkeleton, SectionCard, WidgetError } from './SectionCard';
import type { Loadable } from './useLoadable';

/**
 * 행의 "가격 · 면적" — 최근 매매 거래의 가격과 전용면적(MY-02 카드와 같은 값). 응답에 있는 값만 쓴다 — 매매 거래가 없으면
 * 둘 다 없어 줄을 생략한다. 최근 거래는 매매만이라(MY-02 백엔드 보강) "보증금" 표기는 옛 응답 대비로만 남겼다.
 */
function describeFavorite(favorite: FavoritePropertySummaryResponse): string {
  // 서버가 null 필드를 빼므로(non_null) 값이 없으면 undefined로 온다 — === null이면 "NaN만원"이 된다.
  const parts: string[] = [];
  if (favorite.recentAmount != null) {
    const price = formatKoreanPrice(favorite.recentAmount);
    parts.push(favorite.recentDealCategory === 'RENT' ? `보증금 ${price}` : price);
  }
  if (favorite.recentArea != null) parts.push(formatArea(favorite.recentArea));
  return parts.join(' · ');
}

/**
 * MY-01 관심 매물 미리보기 — 최근 등록 2건. 행을 누르면 그 단지의 DTL-01. 하트는 표시 전용(aria-hidden)이고
 * 해제는 MY-02(삭제 확인 다이얼로그·토스트)의 책임이다. 행 모양은 Figma(7:5373): 여백 16/20, 간격 14, 썸네일 56×42.
 */
export function FavoritePreview({ state, onRetry }: { state: Loadable<FavoritePreviewData>; onRetry: () => void }) {
  return (
    <SectionCard title="관심 매물" viewAll={{ to: MY_ROUTES.favorites, ariaLabel: '관심 매물 전체 보기' }}>
      {state.status === 'loading' && (
        <div aria-busy="true" className="divide-y divide-[#f3f4f6]">
          <span className="sr-only">관심 매물을 불러오는 중</span>
          {Array.from({ length: FAVORITE_PREVIEW_COUNT }, (_, index) => (
            <RowSkeleton key={index} thumbnail />
          ))}
        </div>
      )}
      {state.status === 'error' && <WidgetError message={state.message} onRetry={onRetry} />}
      {state.status === 'success' && state.data.items.length === 0 && (
        <CardBody>
          <EmptyState
            icon={<HeartIcon className="size-5" />}
            description="아직 등록한 관심 매물이 없어요"
            actions={
              <Link
                to="/search"
                className="rounded-[10px] bg-brand px-3.5 py-2 text-[13px] font-semibold text-white hover:bg-[#0c4a44]"
              >
                매물 둘러보기
              </Link>
            }
          />
        </CardBody>
      )}
      {state.status === 'success' && state.data.items.length > 0 && (
        <>
          <ul className="flex flex-col divide-y divide-[#f3f4f6]">
            {state.data.items.map((favorite) => {
              const summary = describeFavorite(favorite);
              return (
                <li key={favorite.favoritePropertyId}>
                  <Link
                    to={`/complexes/${favorite.complexId}`}
                    className="flex items-center gap-3.5 px-5 py-4 hover:bg-[#f7f8fa]"
                  >
                    <div className="flex h-[42px] w-14 shrink-0 items-center justify-center rounded-[14px] bg-[#f3f4f6]">
                      <HomeIcon aria-hidden="true" className="size-5 opacity-40 [&_path]:stroke-[#6a7282]" />
                    </div>
                    <div className="flex min-w-0 flex-1 flex-col">
                      <span className="truncate text-[13px] leading-5 font-bold text-[#101828]">{favorite.complexName}</span>
                      {summary && <span className="text-[12px] leading-[18px] text-muted">{summary}</span>}
                    </div>
                    <HeartIcon filled aria-hidden="true" className="size-4 shrink-0 text-[#ff2056]" />
                  </Link>
                </li>
              );
            })}
          </ul>
          <CardFooterLink to={MY_ROUTES.favorites}>전체 관심 매물 보기</CardFooterLink>
        </>
      )}
    </SectionCard>
  );
}
