import type { SVGProps } from 'react';

/** 기본정보 요약 — 시공사. Figma 자산을 받지 못해(DTL-01 작업 시 Figma 커넥터 미인가) 선 두께 2의 24px 선 아이콘으로 직접 그렸다. */
export function HammerIcon(props: SVGProps<SVGSVGElement>) {
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
      <path d="M15 12l-8.5 8.5a2.12 2.12 0 0 1-3-3L12 9" /><path d="M17.64 15L22 10.64M20.91 11.7l-1.25-1.25a2 2 0 0 1-.59-1.41V7.66L15.36 3.95a2 2 0 0 0-2.82 0L11 5.5l2 2 1.5 1.5L19 13.5" />
    </svg>
  );
}
