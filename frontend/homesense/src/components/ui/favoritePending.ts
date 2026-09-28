/**
 * 세션 확인 중에 누른 하트의 대기 표시 — 판정(로그인 여부)이 나기 전이라 아직 등록/해제하지 않았다는 것을
 * 알린다. 브랜드색 링과 깜빡임으로 표시하고, 판정 후 처리되면 사라진다. 하트를 그리는 곳(ComplexCard의
 * 그리드·목록형, RecentViews)이 같은 표시를 쓴다. 접근성 쪽 표시는 각 버튼의 aria-busy가 맡는다.
 */
export function favoritePendingClass(pending: boolean): string {
  return pending ? 'animate-pulse cursor-wait ring-2 ring-brand/60' : '';
}
