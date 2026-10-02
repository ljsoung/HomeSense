import type { SVGProps } from 'react';

/**
 * 위치 핀 — MY-02 관심 지역 행의 아이콘(Figma 6:4695, 36px 상자 안 16px). Figma MCP가 연결되지 않아 원본 SVG를 받지
 * 못했다 — lucide map-pin 도형(24 격자, 선 굵기 2)으로 직접 그렸다. 원본을 받으면 교체한다.
 */
export function MapPinIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="10" r="3" stroke="currentColor" strokeWidth="2" />
    </svg>
  );
}
