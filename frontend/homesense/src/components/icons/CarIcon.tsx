import type { SVGProps } from 'react';

/** 기본정보 요약 — 총주차대수. Figma 자산을 받지 못해(DTL-01 작업 시 Figma 커넥터 미인가) 선 두께 2의 24px 선 아이콘으로 직접 그렸다. */
export function CarIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      {...props}
    >
      <path d="M5 17h14M5 17a2 2 0 1 0 4 0M15 17a2 2 0 1 0 4 0M3 17v-5l2-5h14l2 5v5M3 12h18" />
    </svg>
  );
}
