import type { SVGProps } from 'react';

/** 지도 축소 버튼 — Figma 6:4629(MAP-01 지도 컨트롤 "−")의 SVG 그대로. */
export function MinusIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" {...props}>
      <path d="M3.33333 8H12.6667" stroke="currentColor" strokeWidth="1.33333" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
