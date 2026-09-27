import type { SVGProps } from 'react';

/** 모바일 "필터 N" 버튼 아이콘 — Figma 노드 24:9456(모바일 검색결과 필터 버튼)의 실제 SVG를 그대로 옮겼다. */
export function FilterIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M14 2.66H9.33" stroke="currentColor" strokeWidth="1.33" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6.66 2.66H2" stroke="currentColor" strokeWidth="1.33" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M14 8H8" stroke="currentColor" strokeWidth="1.33" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M5.33 8H2" stroke="currentColor" strokeWidth="1.33" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M14 13.32H10.66" stroke="currentColor" strokeWidth="1.33" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M8 13.32H2" stroke="currentColor" strokeWidth="1.33" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M9.33 1.33V4" stroke="currentColor" strokeWidth="1.33" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M5.33 6.66V9.33" stroke="currentColor" strokeWidth="1.33" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10.66 11.99V14.65" stroke="currentColor" strokeWidth="1.33" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
