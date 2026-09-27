import type { SVGProps } from 'react';

/** 검색창 입력 지우기 버튼용 X — ChevronRightIcon과 같은 획 두께(1.33333) 관례를 따른다. */
export function XIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M12 4L4 12M4 4L12 12" stroke="currentColor" strokeWidth="1.33333" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
