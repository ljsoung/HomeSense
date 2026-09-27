import type { SVGProps } from 'react';

/** 모바일 "필터 N" 바텀시트 진입 버튼용 슬라이더(필터) 픽토그램 — Figma 확인 없이 기존 획 두께 관례로 직접 작성. */
export function FilterIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M2 4h12M2 8h12M2 12h12" stroke="currentColor" strokeWidth="1.33333" strokeLinecap="round" />
      <circle cx="6" cy="4" r="1.5" fill="white" stroke="currentColor" strokeWidth="1.33333" />
      <circle cx="11" cy="8" r="1.5" fill="white" stroke="currentColor" strokeWidth="1.33333" />
      <circle cx="5" cy="12" r="1.5" fill="white" stroke="currentColor" strokeWidth="1.33333" />
    </svg>
  );
}
