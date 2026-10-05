import type { DealCategory, RentType } from '../features/complex/types';

/**
 * 국토부 실거래가 API의 거래금액은 "만원" 단위 정수로 내려온다(TradeFieldMapper.requiredAmount()가
 * 콤마만 제거하고 그대로 파싱 — CLAUDE.md에는 명시돼 있지 않아 배치 파서 소스로 직접 확인했다).
 * representativeAmount/avgPrice 등 이 값을 그대로 노출하는 모든 응답 필드가 같은 단위를 쓴다.
 */
export function formatKoreanPrice(amountInManwon: number): string {
  const eok = Math.floor(amountInManwon / 10000);
  const remainder = amountInManwon % 10000;
  if (eok > 0 && remainder > 0) {
    return `${eok}억 ${remainder.toLocaleString('ko-KR')}만원`;
  }
  if (eok > 0) {
    return `${eok}억원`;
  }
  return `${remainder.toLocaleString('ko-KR')}만원`;
}

/** UI정의서 4.9절 관례 — 상승은 +, 하락은 유니코드 마이너스(−)로 표기한다. */
export function formatChangeRate(changeRate: number): string {
  const rounded = Math.abs(changeRate).toFixed(1);
  return changeRate >= 0 ? `+${rounded}%` : `−${rounded}%`;
}

export function formatArea(area: number): string {
  return `${Math.round(area)}㎡`;
}

/** SRCH-01 리스트 카드의 "평당가" 대신 표기 — ㎡당 단가(만원/㎡)를 반올림해 보여준다. */
export function formatPricePerArea(amountInManwon: number, area: number): string {
  if (!area) {
    return '';
  }
  return `${Math.round(amountInManwon / area).toLocaleString('ko-KR')}만원/㎡`;
}

/** "2026-09-11" → "2026.09.11". API가 아직 ISO(하이픈) 형식을 그대로 주는 필드(대표 거래일 등)용. */
export function formatDottedDate(isoDate: string): string {
  return isoDate.replaceAll('-', '.');
}

/**
 * SRCH-01 리스트 카드 전용 — 매매/전세/월세에 따라 대표 거래 금액 표기를 분기한다(확정 사항 #6).
 * dealCategory=SALE이면 매매금액 그대로, RENT+JEONSE면 "전세" 접두, RENT+WOLSE면 보증금과
 * 월세금액을 함께("보증금 {} · 월세 {}만원") 보여준다 — FR-3.3이 요구하는 "보증금과 월세금액을
 * 함께 제공"을 만족한다. HOME-01의 그리드 ComplexCard는 이 분기 없이 금액만 노출하던 기존 동작을
 * 그대로 유지한다(회귀 방지 — 이 헬퍼는 새 list variant에서만 쓴다).
 */
export function describeDealAmount(
  dealCategory: DealCategory,
  rentType: RentType | null | undefined,
  amount: number,
  monthlyRentAmount: number | null | undefined,
): string {
  if (dealCategory === 'SALE') {
    return formatKoreanPrice(amount);
  }
  if (rentType === 'WOLSE') {
    return `보증금 ${formatKoreanPrice(amount)} · 월세 ${(monthlyRentAmount ?? 0).toLocaleString('ko-KR')}만원`;
  }
  return `전세 ${formatKoreanPrice(amount)}`;
}

/**
 * ComplexCard가 원래 `{sigungu} {dongRi}`로 직접 이어붙이던 것과 같은 조합 규칙(구분자 없이 공백
 * 하나)을 그대로 재사용한다 — RecentViewResponse도 같은 세 원시 필드(sido/sigungu/dongRi)를
 * 노출하도록 백엔드가 맞춰졌으므로(CPX-RCV-RGN 카드 표시 필드 보강) 새 조합 로직을 만들지 않고
 * 이 함수 하나를 두 컴포넌트가 공유한다. 두 필드 모두 nullable(단지 기본정보 xlsx 원본 미기재
 * 가능)이라 없는 쪽은 건너뛰어 어색한 홑공백이 남지 않게 한다. 서버가 null 필드를 키째 빼므로 없는 부분은
 * undefined로도 온다 — 둘 다 같은 "부분 없음"으로 받는다(값 하나를 그리는 포매터와 달리 이 함수의 목적이 있는
 * 부분만 잇는 것이라 잘못된 호출을 감추지 않는다).
 */
export function formatAddress(sigungu: string | null | undefined, dongRi: string | null | undefined): string {
  return [sigungu, dongRi].filter((part): part is string => Boolean(part)).join(' ');
}
