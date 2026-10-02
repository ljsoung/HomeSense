import type { SVGProps } from 'react';

/** MY-01 메뉴 "관심 매물·지역 관리" 하트 — Figma(7:5373)의 20px SVG. 카드·하트 토글용 HeartIcon(15px)과 획 두께가 달라 따로 둔다. */
export function MenuHeartIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" {...props}>
      <path d="M15.8334 11.6667C17.075 10.45 18.3334 8.99167 18.3334 7.08333C18.3334 5.86776 17.8505 4.70197 16.9909 3.84243C16.1314 2.98289 14.9656 2.5 13.75 2.5C12.2834 2.5 11.25 2.91667 10 4.16667C8.75002 2.91667 7.71669 2.5 6.25002 2.5C5.03444 2.5 3.86866 2.98289 3.00911 3.84243C2.14957 4.70197 1.66669 5.86776 1.66669 7.08333C1.66669 9 2.91669 10.4583 4.16669 11.6667L10 17.5L15.8334 11.6667Z" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
