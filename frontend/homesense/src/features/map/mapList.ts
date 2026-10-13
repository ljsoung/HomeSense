import type { ComplexMapPointResponse } from '../complex/types';
import type { SortUi } from '../search/searchParams';

/**
 * UI정의서 7.4절. 지도 화면은 공통 푸터를 그리지 않아(Figma) 목록 패널 아래에 둔다. Figma 6:4561의 "출처: … · 정밀(실거래가
 * 기반) / 근사(시세 추정)"는 쓰지 않는다 — 근사는 시세 추정이 아니라 유사 매칭이거나 근사 좌표라는 뜻이라(UI정의서 4.9절,
 * PrecisionBadge.java) 명세 오류로 보고, 출처 문구는 UI정의서 7.4절 원문, 정밀·근사 뜻은 지도 범례가 맡는다.
 */
export const DATA_SOURCE_TEXT = '데이터 출처: 국토교통부 실거래가 공개시스템 (data.go.kr) · 갱신주기: 일 1회';

/** 카드 정렬 — SRCH-01과 같은 sort 파라미터(최신순·금액순·면적순)를 받은 응답 안에서 적용한다(지도 API는 정렬하지 않는다). */
export function sortMapItems(items: ComplexMapPointResponse[], sort: SortUi): ComplexMapPointResponse[] {
  const sorted = [...items];
  switch (sort) {
    case 'AMOUNT':
      return sorted.sort((a, b) => a.representativeAmount - b.representativeAmount || a.complexId - b.complexId);
    case 'AREA':
      return sorted.sort((a, b) => b.representativeArea - a.representativeArea || a.complexId - b.complexId);
    case 'LATEST':
      return sorted.sort((a, b) => b.representativeDealDate.localeCompare(a.representativeDealDate) || a.complexId - b.complexId);
  }
}

export function formatMapCount(count: number, truncated: boolean): string {
  return `${count.toLocaleString('ko-KR')}${truncated ? '+' : ''}건`;
}

