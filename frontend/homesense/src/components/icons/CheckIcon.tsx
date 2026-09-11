import type { SVGProps } from 'react';

interface CheckIconProps extends SVGProps<SVGSVGElement> {
  strokeWidth?: number;
}

/**
 * AUTH-02 Figma가 내려준 체크마크는 픽셀 크기(12px/14px)와 색상만 다를 뿐 동일한 도형을
 * 스케일한 것이다(12x12 viewBox 기준 좌표를 14/12로 곱하면 14x14 버전과 정확히 일치) — 세 곳
 * (FieldHint 성공 아이콘 14px, 비밀번호 체크리스트 12px, 약관 체크박스 12px)에서 각각 별도
 * 아이콘 파일을 두지 않고 이 컴포넌트 하나를 strokeWidth/색상만 바꿔 재사용한다.
 */
export function CheckIcon({ strokeWidth = 1, ...props }: CheckIconProps) {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M10 3L4.5 8.5L2 6"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
