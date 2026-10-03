import type { SVGProps } from 'react';

/**
 * 하트 36px — MY-02 빈 상태 아이콘(Figma 6-5286, 선 2.25). 카드 찜 버튼의 HeartIcon(15px, 선 1.25)과 같은 도형이지만
 * 36px로 키우면 선이 3이 돼 Figma(2.25)와 달라 따로 둔다.
 */
export function EmptyHeartIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="36" height="36" viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M28.5 21C30.735 18.81 33 16.185 33 12.75C33 10.562 32.1308 8.46354 30.5836 6.91637C29.0365 5.36919 26.938 4.5 24.75 4.5C22.11 4.5 20.25 5.25 18 7.5C15.75 5.25 13.89 4.5 11.25 4.5C9.06196 4.5 6.96354 5.36919 5.41637 6.91637C3.86919 8.46354 3 10.562 3 12.75C3 16.2 5.25 18.825 7.5 21L18 31.5L28.5 21Z"
        stroke="currentColor"
        strokeWidth="2.25"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
