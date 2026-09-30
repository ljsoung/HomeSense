import type { SVGProps } from 'react';

/**
 * 펼침/접힘 토글 표시 — DTL-01 Figma(4:3174)의 실제 SVG. 접기 상태의 위쪽 화살표(Figma 6:3756)는 이 도형을
 * 180° 돌린 것과 같아 rotate-180으로 쓴다.
 */
export function ChevronDownIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" {...props}>
      <path d="M4 6L8 10L12 6" stroke="currentColor" strokeWidth="1.33333" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
