import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AlertTriangleIcon } from '../../components/icons/AlertTriangleIcon';
import { ArrowLeftIcon } from '../../components/icons/ArrowLeftIcon';
import { HeartIcon } from '../../components/icons/HeartIcon';
import { ShareIcon } from '../../components/icons/ShareIcon';
import { DataTrustBadge } from '../../components/ui/DataTrustBadge';
import { favoritePendingClass } from '../../components/ui/favoritePending';
import { useToast } from '../../components/ui/useToast';
import type { ComplexDetailResponse } from '../../features/complex/types';
import { defaultFilters, serializeSearchParams } from '../../features/search/searchParams';
import { CARD_CLASS, displayAddress } from './detailFormat';

/** 법정동코드 앞 n자리를 10자리로 채운 계층 코드(SRCH-01 regionCode). */
function regionCodeOf(legalDongCd: string, digits: number): string {
  return legalDongCd.slice(0, digits).padEnd(10, '0');
}

/** 지역 링크는 지역 조건만 싣고 나머지는 SRCH-01 기본값을 쓴다(기본값은 URL에서 생략된다). */
function searchHref(regionCode: string, regionLabel: string): string {
  return `/search?${serializeSearchParams({ ...defaultFilters(), regionCode, regionLabel }).toString()}`;
}

/**
 * 뒤로가기 — 앱 안에서 들어왔으면 이전 화면으로, 링크로 바로 들어왔으면(이 탭의 첫 화면) 홈으로 간다.
 * react-router는 첫 진입 위치의 key를 'default'로 둔다. 모양은 Figma 4:3018(화살표 14px + "뒤로").
 */
function BackButton() {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <button
      type="button"
      aria-label="뒤로 가기"
      onClick={() => (location.key === 'default' ? navigate('/') : navigate(-1))}
      className="flex shrink-0 items-center gap-1 rounded-[6px] text-[16px] font-medium leading-6 text-[#6a7282] hover:text-[#101828]"
    >
      <ArrowLeftIcon className="size-3.5" aria-hidden="true" />
      뒤로
    </button>
  );
}

interface BreadcrumbProps {
  detail: ComplexDetailResponse | null;
}

/** 뒤로 | 시도 › 시군구 › 단지명(Figma 4:3017). 법정동코드가 있으면 시도·시군구를 SRCH-01 지역 검색으로 잇는다. */
export function ComplexBreadcrumb({ detail }: BreadcrumbProps) {
  const crumbs: { label: string; href?: string }[] = [];
  if (detail) {
    const code = detail.legalDongCd ?? null;
    if (detail.sido) crumbs.push({ label: detail.sido, href: code ? searchHref(regionCodeOf(code, 2), detail.sido) : undefined });
    if (detail.sigungu) {
      const label = [detail.sido, detail.sigungu].filter(Boolean).join(' ');
      crumbs.push({ label: detail.sigungu, href: code ? searchHref(regionCodeOf(code, 5), label) : undefined });
    }
    crumbs.push({ label: detail.complexName });
  }

  return (
    <div className="flex min-w-0 items-center gap-2">
      <BackButton />
      <span aria-hidden="true" className="text-[12.5px] leading-[18.75px] text-[#d1d5dc]">
        |
      </span>
      {detail ? (
        <nav aria-label="위치 경로" className="min-w-0">
          <ol className="flex min-w-0 items-center gap-1.5 text-[12.5px] leading-[18.75px] text-[#6a7282]">
            {crumbs.map((crumb, index) => {
              const last = index === crumbs.length - 1;
              return (
                <li key={`${crumb.label}-${index}`} className={`flex min-w-0 items-center gap-1.5 ${last ? '' : 'shrink-0'}`}>
                  {crumb.href ? (
                    <Link to={crumb.href} className="hover:text-brand hover:underline">
                      {crumb.label}
                    </Link>
                  ) : (
                    <span className={last ? 'truncate font-semibold text-[#1e2939]' : ''} aria-current={last ? 'page' : undefined}>
                      {crumb.label}
                    </span>
                  )}
                  {!last && (
                    <span aria-hidden="true" className="text-[#d1d5dc]">
                      ›
                    </span>
                  )}
                </li>
              );
            })}
          </ol>
        </nav>
      ) : (
        <div className="h-4 w-48 animate-pulse rounded bg-[#e5e7eb]" />
      )}
    </div>
  );
}

interface ComplexHeaderProps {
  detail: ComplexDetailResponse | null;
  favorited: boolean;
  favoritePending: boolean;
  favoriteProcessing: boolean;
  onToggleFavorite: () => void;
}

/**
 * DTL-01 구성요소 2 — 단지 헤더(Figma 4:3045, 근사 6:3612). 근사 매칭이면 카드 맨 위에 주황 안내 상자, 그 아래
 * 유형 배지와 정밀/근사 배지(matchMethod가 null이면 그리지 않는다, 결정 5), 단지명(h1)·주소, 관심·공유 버튼.
 * 버튼은 태블릿·데스크톱에서 아이콘+글자, 모바일에서 아이콘만 보인다(이름은 aria-label로 준다).
 */
export function ComplexHeader({ detail, favorited, favoritePending, favoriteProcessing, onToggleFavorite }: ComplexHeaderProps) {
  const { showToast } = useToast();

  const share = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      showToast('링크를 복사했습니다', 'success');
    } catch {
      showToast('링크를 복사하지 못했습니다', 'error');
    }
  };

  if (!detail) {
    return (
      <section className={CARD_CLASS} aria-busy="true" data-testid="complex-header-skeleton">
        <div className="h-4 w-24 animate-pulse rounded bg-[#f3f4f6]" />
        <div className="mt-3 h-7 w-2/3 animate-pulse rounded bg-[#f3f4f6]" />
        <div className="mt-2 h-4 w-1/2 animate-pulse rounded bg-[#f3f4f6]" />
      </section>
    );
  }

  const address = displayAddress(detail);
  const similar = detail.matchMethod === 'SIMILAR';
  const buttonBase =
    'flex items-center justify-center gap-1.5 rounded-[14px] border px-[15px] py-[9px] text-[12.5px] font-semibold leading-[18.75px] transition-colors disabled:cursor-not-allowed disabled:opacity-60 md:px-3.5 md:py-2';

  return (
    <section className={CARD_CLASS} aria-labelledby="complex-name">
      {similar && (
        <div
          role="note"
          className="flex items-start gap-2.5 rounded-[14px] border border-[#fee685] bg-[#fffbeb] px-4 py-3"
          data-testid="similar-match-notice"
        >
          <AlertTriangleIcon className="mt-0.5 size-4 shrink-0 text-[#fe9a00]" aria-hidden="true" />
          <div className="min-w-0">
            <p className="text-[12.5px] font-semibold leading-[18.75px] text-[#973c00]">유사 매칭 결과</p>
            <p className="pt-0.5 text-[11.5px] leading-[17.25px] text-[#bb4d00]">
              지번 등 일부 정보가 정확히 일치하지 않아 유사도 기준으로 추정 매칭된 단지입니다. 참고용 정보로 확인해 주세요.
            </p>
          </div>
        </div>
      )}

      <div className={`flex items-start gap-4 ${similar ? 'pt-4' : ''}`}>
        <div className="min-w-0 flex-1">
          {(detail.housingType || detail.matchMethod) && (
            <DataTrustBadge housingType={detail.housingType} matchMethod={detail.matchMethod} size="md" />
          )}
          <h1
            id="complex-name"
            className="break-keep pt-2.5 text-[24px] font-extrabold leading-[30px] tracking-[-0.5px] text-[#101828] md:text-[28px] md:leading-[35px]"
          >
            {detail.complexName}
          </h1>
          {address && <p className="break-words pt-1.5 text-[13px] leading-[19.5px] text-[#99a1af]">{address}</p>}
        </div>
        <div className="flex shrink-0 items-center gap-2 pt-1">
          <button
            type="button"
            onClick={onToggleFavorite}
            disabled={favoriteProcessing}
            aria-pressed={favorited}
            aria-busy={favoritePending || favoriteProcessing}
            aria-label={favorited ? '관심 매물 해제' : '관심 매물 등록'}
            className={`${buttonBase} ${
              favorited ? 'border-[#fecaca] bg-[#fef2f2] text-[#e7000b]' : 'border-[#e5e7eb] bg-white text-[#4a5565] hover:bg-[#f7f8fa]'
            } ${favoritePendingClass(favoritePending)}`}
          >
            <HeartIcon filled={favorited} className="size-4" aria-hidden="true" />
            <span className="hidden md:inline">{favorited ? '관심등록됨' : '관심등록'}</span>
          </button>
          <button
            type="button"
            onClick={() => void share()}
            aria-label="링크 공유"
            className={`${buttonBase} border-[#e5e7eb] bg-white text-[#4a5565] hover:bg-[#f7f8fa]`}
          >
            <ShareIcon className="size-4" />
            <span className="hidden md:inline">공유</span>
          </button>
        </div>
      </div>
    </section>
  );
}
