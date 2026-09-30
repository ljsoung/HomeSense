import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { PopularKeywordResponse } from './types';

export async function getPopularKeywords(limit: number): Promise<PopularKeywordResponse[]> {
  const { data } = await httpClient.get<ApiResponse<PopularKeywordResponse[]>>('/api/search/popular', {
    params: { limit },
  });
  return (data as Extract<ApiResponse<PopularKeywordResponse[]>, { success: true }>).data;
}

/**
 * POST /api/search/logs — 검색 실행(자유 텍스트 제출, 자동완성 지역 선택) 딱 그 순간에만 호출한다.
 * 인기 검색어 칩 클릭, SRCH-01 안에서의 필터/정렬/페이지/새로고침/뒤로가기는 절대 호출하지 않는다
 * (확정 사항 #2). fire-and-forget이라 실패해도 예외를 던지지 않고 조용히 삼킨다 — 검색 자체나
 * 화면 이동을 절대 막으면 안 되는 부가 기능이기 때문이다(SVC-RCV-01.record()와 같은 원칙).
 */
export async function logSearch(keyword: string): Promise<void> {
  try {
    // 검색 기록은 회원과 연결되지 않아(search_log에 회원 컬럼 없음) 어느 계정의 토큰이든 결과가 같다 — 탭이 로그인
    // 계정을 확정하기 전(확인 중)에 검색해도 요청 방어가 막지 않게 표시한다.
    await httpClient.post('/api/search/logs', { keyword }, { _accountIndependent: true });
  } catch {
    // 로깅 실패는 검색 자체에 영향을 주면 안 된다 — 조용히 무시한다.
  }
}
