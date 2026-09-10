import type { SVGProps } from 'react';

/**
 * Not part of the Figma export (AUTH-01 only shows the "hidden" state) — hand-authored to match
 * EyeIcon's stroke weight/viewBox/palette for the password field's "revealed" toggle state.
 */
export function EyeOffIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      {...props}
    >
      <path
        d="M9.53333 9.53333C9.15242 9.914 8.62889 10.1334 8.08 10.1334C7.53112 10.1334 7.00759 9.914 6.62667 9.53333C6.24575 9.15267 6.02667 8.62888 6.02667 8.08C6.02667 7.53112 6.24575 7.00733 6.62667 6.62667"
        stroke="currentColor"
        strokeWidth="1.33333"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M11.7267 11.6467C10.6183 12.4652 9.31958 12.9224 8 12.9600C4.66667 12.9600 2.09333 10.9067 1.10667 8C1.42973 7.04906 1.94693 6.17654 2.62667 5.44"
        stroke="currentColor"
        strokeWidth="1.33333"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M6.16 3.44C6.75042 3.24957 7.36685 3.15139 8 3.15C11.3333 3.15 13.9067 5.20333 14.8933 8.08C14.5804 8.98924 14.1057 9.83552 13.4933 10.5733"
        stroke="currentColor"
        strokeWidth="1.33333"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M1.33333 1.33333L14.6667 14.6667" stroke="currentColor" strokeWidth="1.33333" strokeLinecap="round" />
    </svg>
  );
}
