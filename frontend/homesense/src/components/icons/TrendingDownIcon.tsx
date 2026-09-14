import type { SVGProps } from 'react';

export function TrendingDownIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M11 8.5L6.75 4.25L4.25 6.75L1 3.5" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M8 8.5H11V5.5" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
