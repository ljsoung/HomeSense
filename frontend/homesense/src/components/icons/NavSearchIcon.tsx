import type { SVGProps } from 'react';

export function NavSearchIcon({ strokeWidth = 1.64918, ...props }: SVGProps<SVGSVGElement>) {
  return (
    <svg width="22" height="22" viewBox="0 0 22 22" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M10.0783 17.408C14.1264 17.408 17.408 14.1264 17.408 10.0783C17.408 6.03026 14.1264 2.74864 10.0783 2.74864C6.03026 2.74864 2.74864 6.03026 2.74864 10.0783C2.74864 14.1264 6.03026 17.408 10.0783 17.408Z"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M19.2405 19.2405L15.3007 15.3007" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
