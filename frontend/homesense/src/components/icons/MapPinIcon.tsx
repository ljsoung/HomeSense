import type { SVGProps } from 'react';

/** 위치 핀 16px — MY-02 관심 지역 행 아이콘(Figma 6-4695, 36px 상자 안, 선 1.33). */
export function MapPinIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M8.40063 14.5328C9.64063 13.4622 13.3333 9.9955 13.3333 6.66683C13.3333 5.25234 12.7714 3.89579 11.7712 2.89559C10.771 1.8954 9.41445 1.3335 7.99996 1.3335C6.58547 1.3335 5.22892 1.8954 4.22872 2.89559C3.22853 3.89579 2.66663 5.25234 2.66663 6.66683C2.66663 9.9955 6.35929 13.4622 7.59929 14.5328C7.71481 14.6197 7.85543 14.6667 7.99996 14.6667C8.14449 14.6667 8.28511 14.6197 8.40063 14.5328Z"
        stroke="currentColor"
        strokeWidth="1.33333"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M8 8.6665C9.10457 8.6665 10 7.77107 10 6.6665C10 5.56193 9.10457 4.6665 8 4.6665C6.89543 4.6665 6 5.56193 6 6.6665C6 7.77107 6.89543 8.6665 8 8.6665Z"
        stroke="currentColor"
        strokeWidth="1.33333"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
