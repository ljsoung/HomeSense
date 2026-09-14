import type { SVGProps } from 'react';

export function NavMyIcon({ strokeWidth = 1.64918, ...props }: SVGProps<SVGSVGElement>) {
  return (
    <svg width="22" height="22" viewBox="0 0 22 22" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M17.408 19.2405V17.408C17.408 16.4361 17.0219 15.5039 16.3346 14.8166C15.6473 14.1293 14.7152 13.7432 13.7432 13.7432H8.24591C7.27393 13.7432 6.34176 14.1293 5.65447 14.8166C4.96718 15.5039 4.58106 16.4361 4.58106 17.408V19.2405"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M10.9945 10.0783C13.0186 10.0783 14.6594 8.43753 14.6594 6.41349C14.6594 4.38945 13.0186 2.74864 10.9945 2.74864C8.97051 2.74864 7.3297 4.38945 7.3297 6.41349C7.3297 8.43753 8.97051 10.0783 10.9945 10.0783Z"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
