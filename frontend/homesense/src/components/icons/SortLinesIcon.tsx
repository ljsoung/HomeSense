import type { SVGProps } from 'react';

/**
 * 정렬 14px — MY-02 정렬 드롭다운 앞 아이콘(Figma 28-16161·27-15395, 선 1.17, 길이가 줄어드는 세 줄).
 * SRCH-01 필터 버튼의 FilterIcon(손잡이 달린 슬라이더)과 도형이 다르다.
 */
export function SortLinesIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M1.74951 3.49902H12.248" stroke="currentColor" strokeWidth="1.1665" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M4.08252 6.99902H9.91502" stroke="currentColor" strokeWidth="1.1665" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M5.83252 10.498H8.16552" stroke="currentColor" strokeWidth="1.1665" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
