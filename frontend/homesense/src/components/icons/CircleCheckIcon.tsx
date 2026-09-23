import type { SVGProps } from 'react';

/**
 * AUTH-03 Figma(31:18291, "이메일을 발송했습니다" 화면)의 실제 원형-체크 아이콘 — 단순 체크마크인
 * CheckIcon과는 다른 별도 도형(열린 원호 + 체크)이다.
 */
export function CircleCheckIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="28" height="28" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M25.4345 11.6667C25.9673 14.2815 25.5876 17 24.3587 19.3688C23.1297 21.7375 21.1259 23.6134 18.6813 24.6835C16.2367 25.7537 13.4991 25.9534 10.925 25.2494C8.35097 24.5454 6.09606 22.9803 4.53632 20.815C2.97659 18.6497 2.20631 16.0151 2.35394 13.3506C2.50157 10.6861 3.55819 8.15277 5.34759 6.17303C7.13699 4.19329 9.55101 2.88684 12.1871 2.47153C14.8231 2.05622 17.5219 2.55717 19.8333 3.89083"
        stroke="currentColor"
        strokeWidth="2.33333"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M10.5 12.8333L14 16.3333L25.6667 4.66667" stroke="currentColor" strokeWidth="2.33333" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
