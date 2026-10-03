import type { SVGProps } from 'react';

/**
 * 휴지통 12px — MY-02 모바일 카드 삭제 버튼(Figma 27-15395, 선 1). TrashIcon(24px, 선 2)과 같은 도형이지만 축소하면 선이
 * 0.5가 돼 따로 둔다(MY-01 메뉴 아이콘을 크기별로 나눈 것과 같은 이유).
 */
export function TrashSmallIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M1.49951 2.99902H10.4963" stroke="currentColor" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" />
      <path
        d="M9.49652 2.99902V9.99652C9.49652 10.4963 8.99669 10.9962 8.49687 10.9962H3.49867C2.99884 10.9962 2.49902 10.4963 2.49902 9.99652V2.99902"
        stroke="currentColor"
        strokeWidth="1"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M3.99854 2.99928V1.99964C3.99854 1.49982 4.49836 1 4.99818 1H6.99746C7.49728 1 7.9971 1.49982 7.9971 1.99964V2.99928"
        stroke="currentColor"
        strokeWidth="1"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M4.99805 5.49805V8.49697" stroke="currentColor" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6.99756 5.49805V8.49697" stroke="currentColor" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
