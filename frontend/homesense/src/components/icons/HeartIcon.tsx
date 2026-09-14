import type { SVGProps } from 'react';

interface HeartIconProps extends SVGProps<SVGSVGElement> {
  filled?: boolean;
}

/** 찜(관심 매물) 토글 아이콘 — 매물 카드 오버레이 버튼, 최근 조회 행 모두 이 컴포넌트를 공유한다. */
export function HeartIcon({ filled = false, ...props }: HeartIconProps) {
  return (
    <svg width="15" height="15" viewBox="0 0 15 15" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M11.875 8.75C12.8063 7.8375 13.75 6.74375 13.75 5.3125C13.75 4.40082 13.3878 3.52648 12.7432 2.88182C12.0985 2.23716 11.2242 1.875 10.3125 1.875C9.2125 1.875 8.4375 2.1875 7.5 3.125C6.5625 2.1875 5.7875 1.875 4.6875 1.875C3.77582 1.875 2.90148 2.23716 2.25682 2.88182C1.61216 3.52648 1.25 4.40082 1.25 5.3125C1.25 6.75 2.1875 7.84375 3.125 8.75L7.5 13.125L11.875 8.75Z"
        fill={filled ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="1.25"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
