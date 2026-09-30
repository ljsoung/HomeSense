import type { SVGProps } from 'react';

/** 공유 버튼 아이콘(세 점을 잇는 공유 기호). Figma 자산을 받지 못해(DTL-01 작업 시 Figma 커넥터 미인가) 선 두께 2의 24px 선 아이콘으로 직접 그렸다. */
export function ShareIcon(props: SVGProps<SVGSVGElement>) {
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
      <circle cx="18" cy="5" r="3" /><circle cx="6" cy="12" r="3" /><circle cx="18" cy="19" r="3" /><path d="M8.59 13.51l6.83 3.98M15.41 6.51l-6.82 3.98" />
    </svg>
  );
}
