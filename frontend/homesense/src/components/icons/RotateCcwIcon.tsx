import type { SVGProps } from 'react';

/** 필터 패널 "초기화" 링크 아이콘 — Figma 노드 4:1777(데스크톱 검색결과 사이드바 초기화 버튼) 실제 SVG. */
export function RotateCcwIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M1.5 6C1.5 6.89 1.76 7.76 2.26 8.5C2.75 9.24 3.46 9.82 4.28 10.16C5.1 10.5 6 10.59 6.88 10.41C7.75 10.24 8.55 9.81 9.18 9.18C9.81 8.55 10.24 7.75 10.41 6.88C10.59 6 10.5 5.1 10.16 4.28C9.82 3.46 9.24 2.75 8.5 2.26C7.76 1.76 6.89 1.5 6 1.5C4.74 1.5 3.53 2 2.63 2.87L1.5 4"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M1.5 1.5V4H4" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
