import type { SVGProps } from 'react';

/**
 * 휴지통 — MY-02 삭제 다이얼로그(Figma 6:4995 아이콘 원 안 24px)와 모바일 카드 삭제 버튼(12px).
 * Figma MCP가 연결되지 않아 원본 SVG를 받지 못했다 — 다른 아이콘과 같은 선 스타일(lucide trash-2 도형, 24 격자,
 * 선 굵기 2, 둥근 끝)로 직접 그렸다. 원본을 받으면 교체한다.
 */
export function TrashIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M3 6h18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10 11v6M14 11v6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
