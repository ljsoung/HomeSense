import type { SVGProps } from 'react';

export function NavFavoritesIcon({ strokeWidth = 1.64918, ...props }: SVGProps<SVGSVGElement>) {
  return (
    <svg width="22" height="22" viewBox="0 0 22 22" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M17.408 19.2405L10.9945 15.5756L4.58106 19.2405V4.58106C4.58106 4.09507 4.77412 3.62899 5.11777 3.28534C5.46141 2.9417 5.9275 2.74864 6.41349 2.74864H15.5756C16.0616 2.74864 16.5277 2.9417 16.8713 3.28534C17.215 3.62899 17.408 4.09507 17.408 4.58106V19.2405Z"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
