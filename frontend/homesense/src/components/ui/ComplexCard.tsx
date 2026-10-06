import { Link } from 'react-router-dom';
import { HomeIcon } from '../icons/HomeIcon';
import { HeartIcon } from '../icons/HeartIcon';
import { DataTrustBadge } from './DataTrustBadge';
import { favoritePendingClass } from './favoritePending';
import { describeDealAmount, formatAddress, formatArea, formatDottedDate, formatKoreanPrice, formatPricePerArea } from '../../lib/format';
import type { ComplexSummaryResponse } from '../../features/complex/types';

interface ComplexCardProps {
  complex: ComplexSummaryResponse;
  isFavorited: boolean;
  onToggleFavorite: () => void;
  /** 세션 확인 중에 누른 하트가 판정을 기다리는 중이면 true — 하트에 aria-busy와 대기 표시를 단다. */
  favoritePending?: boolean;
  className?: string;
  /** 'grid' = HOME-01 인기 단지(기존 동작 그대로), 'list' = SRCH-01 검색 결과 목록형 카드. */
  variant?: 'grid' | 'list';
}

/**
 * SIMILAR(근사 매칭) 안내 문구 — Figma 원문("검색 조건과 정확히 일치하지 않는 유사 매물입니다")은
 * SIMILAR가 실제로 의미하는 바(지번 등 일부 정보가 정확히 일치하지 않아 유사도 기준으로 추정
 * 매칭됐다는 것, BAT-MAT-02)를 잘못 설명하고 있어(SIMILAR는 "검색 조건 불일치"가 아니라 "단지
 * 마스터 매칭 신뢰도"의 문제다) 정정한 문구를 쓴다.
 */
const SIMILAR_CAPTION = '지번 등 일부 정보가 정확히 일치하지 않아 유사도 기준으로 추정 매칭된 결과입니다.';

/**
 * UIC-05. 백엔드에 단지 이미지 URL 필드가 아예 없어(Complex 엔티티 확인 완료) 실제 사진 대신
 * 브랜드 톤 그라디언트 위에 옅은 집 아이콘을 올린 자리표시 썸네일을 쓴다 — Figma 목업의 스톡
 * 사진은 가상의 단지명에 맞춰진 것이라 실제 데이터와 매칭될 수 없어 그대로 옮기지 않았다.
 * matchMethod/floor는 둘 다 nullable이라(매칭 실패/원본 미기재) 값이 없으면 각각 배지·층수
 * 세그먼트를 렌더링하지 않는다(2026-09-17, CPX-RCV-RGN 카드 표시 필드 보강으로 필드 자체는
 * 이미 채워짐 — CLAUDE.md SCR-HOME-01 절 참고). 사용승인일이 없으면 "건축 …년"을 뺀다.
 * 서버는 null 필드를 키째 빼므로 값 유무는 `!= null`로 본다 — `!== null`이면 빠진 키(undefined)가 통과해
 * "· undefined층"이 됐다.
 */
export function ComplexCard({ complex, isFavorited, onToggleFavorite, favoritePending = false, className = '', variant = 'grid' }: ComplexCardProps) {
  const isSimilar = complex.matchMethod === 'SIMILAR';
  const favoriteButton = (
    <button
      type="button"
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
        onToggleFavorite();
      }}
      aria-label={isFavorited ? '관심 매물 해제' : '관심 매물 등록'}
      aria-pressed={isFavorited}
      aria-busy={favoritePending || undefined}
      className={`flex size-8 shrink-0 items-center justify-center rounded-full bg-white/80 text-[#4a5565] shadow-[0_1px_3px_rgba(0,0,0,0.1),0_1px_2px_rgba(0,0,0,0.1)] backdrop-blur-sm transition-colors hover:text-[#e7000b] ${favoritePendingClass(favoritePending)}`}
    >
      <HeartIcon filled={isFavorited} className={isFavorited ? 'text-[#ff2056]' : ''} />
    </button>
  );

  if (variant === 'list') {
    // 목록형 카드 레이아웃(썸네일 | 배지·이름·주소·메타 | 가격·평단가+하트)은 Figma 노드
    // 4:1704/24:9405/24:10388(데스크톱·모바일·태블릿 검색결과) 실물 스크린샷으로 확인한 실제
    // 구조다 — 처음 구현은 그리드 카드처럼 가격을 주소 아래 왼쪽 컬럼에 세로로 쌓았는데, 실제
    // Figma는 가격·평단가를 오른쪽 컬럼에 배치하고 메타 줄에는 대신 건축년도를 넣는다(평단가는
    // 메타 줄이 아니라 가격 바로 아래).
    const favoriteButtonList = (
      <button
        type="button"
        onClick={(event) => {
          event.preventDefault();
          event.stopPropagation();
          onToggleFavorite();
        }}
        aria-label={isFavorited ? '관심 매물 해제' : '관심 매물 등록'}
        aria-pressed={isFavorited}
        aria-busy={favoritePending || undefined}
        className={`rounded-full p-0.5 text-[#99a1af] transition-colors hover:text-[#e7000b] ${favoritePendingClass(favoritePending)}`}
      >
        <HeartIcon filled={isFavorited} className={isFavorited ? 'text-[#ff2056]' : ''} />
      </button>
    );

    return (
      <Link
        to={`/complexes/${complex.complexId}`}
        className={`flex flex-col gap-2 rounded-[16px] border bg-white p-3 shadow-[0_1px_3px_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] transition-colors hover:border-brand/40 ${
          isSimilar ? 'border-[#fee685]' : 'border-[#f3f4f6]'
        } ${className}`}
      >
        <div className="flex gap-3">
          <div
            className={`relative size-[92px] shrink-0 overflow-hidden rounded-[12px] bg-gradient-to-br from-[#e8f5f2] to-[#d1eae6] ${
              isSimilar ? 'after:absolute after:inset-0 after:bg-[rgba(255,185,0,0.1)]' : ''
            }`}
          >
            <div className="flex h-full w-full items-center justify-center">
              <HomeIcon className="size-9 opacity-40 [&_path]:stroke-brand" />
            </div>
          </div>

          <div className="flex min-w-0 flex-1 items-start justify-between gap-2">
            <div className="min-w-0">
              <DataTrustBadge housingType={complex.representativeHousingType} matchMethod={complex.matchMethod} />
              <p className="mt-1 truncate text-[14px] font-bold text-[#101828]">{complex.complexName}</p>
              <p className="truncate text-[11.5px] text-[#99a1af]">{formatAddress(complex.sigungu, complex.dongRi)}</p>
              <p className="mt-1 text-[11px] text-[#99a1af]">
                전용 {formatArea(complex.representativeArea)}
                {complex.floor != null ? ` · ${complex.floor}층` : ''} · {formatDottedDate(complex.representativeDealDate)}
                {complex.approvalDate != null ? ` · 건축 ${complex.approvalDate.slice(0, 4)}년` : ''}
              </p>
            </div>

            <div className="flex shrink-0 flex-col items-end gap-1">
              {favoriteButtonList}
              <p className="text-right text-[15px] font-extrabold tracking-[-0.3px] text-[#1c1c1e]">
                {describeDealAmount(complex.representativeDealCategory, complex.rentType, complex.representativeAmount, complex.monthlyRentAmount)}
              </p>
              {/* 월세는 ㎡당 가격을 보여주지 않는다 — representativeAmount가 보증금뿐이라 월세금액을
                  빼고 계산한 "㎡당 가격"은 실제 비용을 왜곡해서 보여준다. */}
              {complex.rentType !== 'WOLSE' && (
                <p className="text-[11px] text-[#99a1af]">{formatPricePerArea(complex.representativeAmount, complex.representativeArea)}</p>
              )}
            </div>
          </div>
        </div>

        {isSimilar && <p className="rounded-[8px] bg-[#fef9c2] px-2.5 py-1.5 text-[11px] leading-[1.5] text-[#973c00]">{SIMILAR_CAPTION}</p>}
      </Link>
    );
  }

  return (
    <div className={`flex h-full flex-col overflow-hidden rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_1px_3px_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] ${className}`}>
      <div className="relative h-[228px] w-full shrink-0 overflow-hidden bg-[#f3f4f6]">
        <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-[#e8f5f2] to-[#d1eae6]">
          <HomeIcon className="size-14 opacity-40 [&_path]:stroke-brand" />
        </div>
        <div className="absolute top-2.5 right-2.5">{favoriteButton}</div>
        <div className="absolute bottom-2.5 left-2.5">
          <DataTrustBadge housingType={complex.representativeHousingType} matchMethod={complex.matchMethod} />
        </div>
      </div>
      <div className="flex flex-col p-4">
        <p className="truncate text-[14px] font-bold text-[#101828]">{complex.complexName}</p>
        <p className="mt-0.5 truncate text-[11.5px] text-[#99a1af]">
          {formatAddress(complex.sigungu, complex.dongRi)}
        </p>
        <p className="mt-2 text-[17px] font-extrabold tracking-[-0.3px] text-[#1c1c1e]">
          {formatKoreanPrice(complex.representativeAmount)}
        </p>
        <p className="mt-1.5 text-[11px] text-[#99a1af]">
          전용 {formatArea(complex.representativeArea)}
          {complex.floor != null ? ` · ${complex.floor}층` : ''} · {complex.representativeDealDate}
        </p>
      </div>
    </div>
  );
}
