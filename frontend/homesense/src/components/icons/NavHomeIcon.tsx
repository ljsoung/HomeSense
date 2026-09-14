import type { SVGProps } from 'react';

export function NavHomeIcon({ strokeWidth = 1.64918, ...props }: SVGProps<SVGSVGElement>) {
  return (
    <svg width="22" height="22" viewBox="0 0 22 22" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M13.7432 19.2405V11.9108C13.7432 11.6678 13.6467 11.4347 13.4748 11.2629C13.303 11.0911 13.07 10.9945 12.827 10.9945H9.16213C8.91913 10.9945 8.68609 11.0911 8.51427 11.2629C8.34244 11.4347 8.24591 11.6678 8.24591 11.9108V19.2405"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M2.74864 9.16212C2.74857 8.89557 2.80666 8.63221 2.91886 8.39041C3.03105 8.14862 3.19465 7.93421 3.39823 7.76215L9.81172 2.26579C10.1425 1.98627 10.5615 1.8329 10.9945 1.8329C11.4276 1.8329 11.8466 1.98627 12.1774 2.26579L18.5909 7.76215C18.7945 7.93421 18.958 8.14862 19.0702 8.39041C19.1824 8.63221 19.2405 8.89557 19.2405 9.16212V17.408C19.2405 17.894 19.0474 18.3601 18.7038 18.7038C18.3601 19.0474 17.894 19.2405 17.408 19.2405H4.58106C4.09507 19.2405 3.62899 19.0474 3.28534 18.7038C2.9417 18.3601 2.74864 17.894 2.74864 17.408V9.16212Z"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
