import type { ComplexDetailResponse } from '../../features/complex/types';

/** 홈 카드와 같은 카드 외곽(radius 16, 옅은 테두리·그림자). 안쪽 여백은 CARD_CLASS에만 있다. */
export const CARD_SHELL_CLASS =
  'rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_1px_1.5px_rgba(0,0,0,0.1),0_1px_1px_rgba(0,0,0,0.1)]';
export const CARD_CLASS = `${CARD_SHELL_CLASS} p-5`;

/** "2026-07-12" → "2026.07.12". */
export function formatFullDate(isoDate: string): string {
  return isoDate.slice(0, 10).replaceAll('-', '.');
}

/** "2010-01-15" → "2010.01" (사용승인일). */
export function formatYearMonth(isoDate: string): string {
  return isoDate.slice(0, 7).replace('-', '.');
}

/** 전용면적 원값 그대로(소수 둘째 자리) — "84.98㎡". */
export function formatExactArea(area: number): string {
  return `${Number(area).toFixed(2)}㎡`;
}

export function formatFloor(floor: number | null | undefined): string {
  return floor == null ? '-' : `${floor}층`;
}

/**
 * 헤더·위치 카드의 주소. legal_dong_address는 K-apt 원본이라 "시도 시군구 동리 지번 단지명"처럼 끝에 단지명이
 * 붙어 있는 경우가 많아(CLAUDE.md BAT-MAT-02 버그 B), 끝의 단지명은 떼고 보여 준다. 없으면 시도·시군구·동리를 잇는다.
 */
export function displayAddress(detail: ComplexDetailResponse): string {
  const raw = detail.legalDongAddress?.trim();
  if (raw) {
    const name = detail.complexName.trim();
    return raw.endsWith(name) && raw.length > name.length ? raw.slice(0, raw.length - name.length).trim() : raw;
  }
  return [detail.sido, detail.sigungu, detail.dongRi].filter(Boolean).join(' ');
}
