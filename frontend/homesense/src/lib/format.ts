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

/**
 * ComplexCard가 원래 `{sigungu} {dongRi}`로 직접 이어붙이던 것과 같은 조합 규칙(구분자 없이 공백
 * 하나)을 그대로 재사용한다 — RecentViewResponse도 같은 세 원시 필드(sido/sigungu/dongRi)를
 * 노출하도록 백엔드가 맞춰졌으므로(CPX-RCV-RGN 카드 표시 필드 보강) 새 조합 로직을 만들지 않고
 * 이 함수 하나를 두 컴포넌트가 공유한다. 두 필드 모두 nullable(단지 기본정보 xlsx 원본 미기재
 * 가능)이라 없는 쪽은 건너뛰어 어색한 홑공백이 남지 않게 한다.
 */
export function formatAddress(sigungu: string | null, dongRi: string | null): string {
  return [sigungu, dongRi].filter((part): part is string => Boolean(part)).join(' ');
}
