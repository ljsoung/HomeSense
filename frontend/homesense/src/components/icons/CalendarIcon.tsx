import type { SVGProps } from 'react';

/** 기본정보 요약 — 사용승인일. Figma 자산을 받지 못해(DTL-01 작업 시 Figma 커넥터 미인가) 선 두께 2의 24px 선 아이콘으로 직접 그렸다. */
export function CalendarIcon(props: SVGProps<SVGSVGElement>) {
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
      <rect x="3" y="4" width="18" height="18" rx="2" /><path d="M16 2v4M8 2v4M3 10h18" />
    </svg>
  );
}
