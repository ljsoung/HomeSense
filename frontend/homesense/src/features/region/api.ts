import { httpClient } from '../../lib/httpClient';
import type { ApiResponse } from '../../types/api';
import type { InterestRegionSummaryResponse } from './types';

export async function getInterestSummary(): Promise<InterestRegionSummaryResponse[]> {
  const { data } = await httpClient.get<ApiResponse<InterestRegionSummaryResponse[]>>('/api/regions/interest-summary');
  return (data as Extract<ApiResponse<InterestRegionSummaryResponse[]>, { success: true }>).data;
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
