import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import type { HousingType } from '../complex/types';
import { logSearch } from './api';
import { defaultFilters, serializeSearchParams, type DealTypeUi, type SearchFilters } from './searchParams';

export type SearchExecuteInput =
  | { mode: 'region'; regionCode: string; regionLabel: string }
  | { mode: 'keyword'; keyword: string }
  | { mode: 'chip'; keyword: string };

/**
 * HOME-01 히어로 검색, 인기 검색어 칩, SRCH-01 안의 재검색 바가 전부 공유하는 단일 실행 경로
 * (확정 사항 #1). 지역 선택/자유 텍스트 실행 시에만 검색 기록을 남기고(확정 사항 #2), 인기
 * 검색어 칩 클릭은 이미 알려진 검색어라 다시 기록하지 않는다 — 로그 호출은 항상 fire-and-forget이라
 * 실패해도 화면 이동을 막지 않는다(logSearch() 자체가 이미 예외를 삼킨다).
 *
 * `base`를 넘기면(SRCH-01 안의 재검색) 그 기존 필터(매물유형/거래유형/슬라이더/정렬)를 유지한 채
 * regionCode/keyword만 교체하고 페이지를 1로 되돌린다(확정 사항 #3) — 넘기지 않으면(HOME-01) 항상
 * 기본 필터에서 새로 시작한다.
 */
export function useExecuteSearch() {
  const navigate = useNavigate();

  return useCallback(
    (input: SearchExecuteInput, base?: SearchFilters, overrides?: { housingTypes?: HousingType[]; dealType?: DealTypeUi }) => {
      const start = base ?? defaultFilters();
      const filters: SearchFilters = {
        ...start,
        housingTypes: overrides?.housingTypes ?? start.housingTypes,
        dealType: overrides?.dealType ?? start.dealType,
        page: 1,
        regionCode: input.mode === 'region' ? input.regionCode : undefined,
        regionLabel: input.mode === 'region' ? input.regionLabel : undefined,
        keyword: input.mode === 'region' ? undefined : input.keyword.trim(),
      };

      navigate(`/search?${serializeSearchParams(filters).toString()}`);

      if (input.mode === 'chip') {
        return;
      }
      const loggedKeyword = input.mode === 'region' ? input.regionLabel : input.keyword.trim();
      if (loggedKeyword) {
        void logSearch(loggedKeyword);
      }
    },
    [navigate],
  );
}
