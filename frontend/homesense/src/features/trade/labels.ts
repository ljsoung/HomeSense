/**
 * 거래유형 코드 → 화면 문구. 코드는 BAT-PRS-01(TradeFieldMapper)이 원천 dealingGbn("중개거래"/"직거래")을 정규화한
 * 값이다. 이력 테이블과 거래상세 모달이 이 함수 하나를 쓴다. 값이 없거나(전월세) 모르는 코드면 "-".
 */
const DEALING_TYPE_LABEL: Record<string, string> = {
  AGENT: '중개거래',
  DIRECT: '직거래',
};

export function dealingTypeLabel(code: string | null | undefined): string {
  return (code && DEALING_TYPE_LABEL[code]) || '-';
}
