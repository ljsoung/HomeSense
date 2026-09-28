import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { InterestRegionSummaryResponse, RegionAutocompleteResponse } from './types';

export async function getInterestSummary(): Promise<InterestRegionSummaryResponse[]> {
  const { data } = await httpClient.get<ApiResponse<InterestRegionSummaryResponse[]>>('/api/regions/interest-summary');
  return (data as Extract<ApiResponse<InterestRegionSummaryResponse[]>, { success: true }>).data;
}

/**
 * UIC-03 자동완성이 쓴다. `signal`로 디바운스 중 더 최신 입력이 들어오면 이전 요청을 취소한다 —
 * 실패해도(네트워크 오류·취소 등) 호출부가 빈 배열로 우아하게 폴백해 자유 텍스트 검색 자체를
 * 막지 않는다(확정 사항 참고).
 */
export async function autocompleteRegions(query: string, signal?: AbortSignal): Promise<RegionAutocompleteResponse[]> {
  const { data } = await httpClient.get<ApiResponse<RegionAutocompleteResponse[]>>('/api/regions', {
    params: { query },
    signal,
  });
  return (data as Extract<ApiResponse<RegionAutocompleteResponse[]>, { success: true }>).data;
}

/**
 * fullPath("시도 시군구 읍면동", RegionAutocompleteResponse.from() 참고)에서 시군구/읍면동만
 * 뽑아 Figma 카드의 2줄 표시(작은 시군구 위 / 굵은 읍면동 아래)에 맞춘다. 시도는 항상 한 토큰이고
 * 읍면동도 항상 한 토큰이지만, 시군구는 "수원시 팔달구"처럼 두 토큰을 가질 수 있어 첫/마지막
 * 토큰만 고정으로 떼어내고 나머지 전부를 시군구로 합친다 — 공백 기준 균등 분할은 이 경우 깨진다.
 */
export function splitRegionPath(fullPath: string): { sigungu: string; eupmyeondong: string } {
  const parts = fullPath.trim().split(/\s+/).filter(Boolean);
  if (parts.length <= 1) {
    return { sigungu: '', eupmyeondong: parts[0] ?? '' };
  }
  const [, ...rest] = parts;
  const eupmyeondong = rest[rest.length - 1];
  const sigungu = rest.slice(0, -1).join(' ');
  return { sigungu, eupmyeondong };
}
