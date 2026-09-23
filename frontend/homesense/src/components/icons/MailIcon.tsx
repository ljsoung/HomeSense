import type { SVGProps } from 'react';

/** AUTH-03 Figma(31:18222)의 실제 봉투 아이콘 — 원본 stroke(#99A1AF)를 currentColor로 교체해 재사용한다. */
export function MailIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M13.3333 2.66667H2.66667C1.93029 2.66667 1.33333 3.26362 1.33333 4V12C1.33333 12.7364 1.93029 13.3333 2.66667 13.3333H13.3333C14.0697 13.3333 14.6667 12.7364 14.6667 12V4C14.6667 3.26362 14.0697 2.66667 13.3333 2.66667Z"
        stroke="currentColor"
        strokeWidth="1.33333"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M14.6667 4.66667L8.68667 8.46667C8.48085 8.59562 8.24288 8.66401 8 8.66401C7.75712 8.66401 7.51915 8.59562 7.31333 8.46667L1.33333 4.66667"
        stroke="currentColor"
        strokeWidth="1.33333"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
