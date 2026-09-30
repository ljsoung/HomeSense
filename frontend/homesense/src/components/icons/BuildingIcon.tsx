import type { SVGProps } from 'react';

/** 동수(건물) — DTL-01 Figma(4:3103)의 실제 SVG. 존재하지 않는 단지 안내에도 쓴다. */
export function BuildingIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="17" height="17" viewBox="0 0 17 17" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" {...props}>
      <path
        d="M4.25 15.5833V2.83333C4.25 2.45761 4.39926 2.09728 4.66493 1.8316C4.93061 1.56592 5.29094 1.41667 5.66667 1.41667H11.3333C11.7091 1.41667 12.0694 1.56592 12.3351 1.8316C12.6007 2.09728 12.75 2.45761 12.75 2.83333V15.5833H4.25Z"
        stroke="currentColor"
        strokeWidth="1.41667"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M4.25 8.5H2.83333C2.45761 8.5 2.09728 8.64926 1.8316 8.91493C1.56592 9.18061 1.41667 9.54094 1.41667 9.91667V14.1667C1.41667 14.5424 1.56592 14.9027 1.8316 15.1684C2.09728 15.4341 2.45761 15.5833 2.83333 15.5833H4.25"
        stroke="currentColor"
        strokeWidth="1.41667"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M12.75 6.375H14.1667C14.5424 6.375 14.9027 6.52426 15.1684 6.78993C15.4341 7.05561 15.5833 7.41594 15.5833 7.79167V14.1667C15.5833 14.5424 15.4341 14.9027 15.1684 15.1684C14.9027 15.4341 14.5424 15.5833 14.1667 15.5833H12.75"
        stroke="currentColor"
        strokeWidth="1.41667"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M7.08333 4.25H9.91667" stroke="currentColor" strokeWidth="1.41667" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M7.08333 7.08333H9.91667" stroke="currentColor" strokeWidth="1.41667" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M7.08333 9.91667H9.91667" stroke="currentColor" strokeWidth="1.41667" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M7.08333 12.75H9.91667" stroke="currentColor" strokeWidth="1.41667" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
