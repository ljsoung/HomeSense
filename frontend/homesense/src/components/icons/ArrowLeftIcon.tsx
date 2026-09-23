import type { SVGProps } from 'react';

/** AUTH-03 Figma(31:18233)의 실제 화살표 아이콘 — ArrowRightIcon과 같은 도형을 좌우 반전한 것과 동일하다. */
export function ArrowLeftIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M7 11.0833L2.91667 7L7 2.91667" stroke="currentColor" strokeWidth="1.16667" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M11.0833 7H2.91667" stroke="currentColor" strokeWidth="1.16667" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
