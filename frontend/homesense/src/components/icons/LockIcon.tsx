import type { SVGProps } from 'react';

/** AUTH-03 Figma(31:18203)의 실제 자물쇠 아이콘 — 원본 stroke(#0F5C54)를 currentColor로 교체해 재사용한다. */
export function LockIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="28" height="28" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M22.1667 12.8333H5.83333C4.54467 12.8333 3.5 13.878 3.5 15.1667V23.3333C3.5 24.622 4.54467 25.6667 5.83333 25.6667H22.1667C23.4553 25.6667 24.5 24.622 24.5 23.3333V15.1667C24.5 13.878 23.4553 12.8333 22.1667 12.8333Z"
        stroke="currentColor"
        strokeWidth="2.33333"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M8.16667 12.8333V8.16667C8.16667 6.61957 8.78125 5.13584 9.87521 4.04188C10.9692 2.94792 12.4529 2.33333 14 2.33333C15.5471 2.33333 17.0308 2.94792 18.1248 4.04188C19.2188 5.13584 19.8333 6.61957 19.8333 8.16667V12.8333"
        stroke="currentColor"
        strokeWidth="2.33333"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
