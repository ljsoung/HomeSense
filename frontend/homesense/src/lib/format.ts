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
