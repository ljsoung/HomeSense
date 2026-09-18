import type { ReactNode } from 'react';
import { CheckIcon } from '../icons/CheckIcon';

interface CheckboxProps {
  id?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: ReactNode;
}

/**
 * 실제 체크 의미는 sr-only 네이티브 input이 갖고, 시각적 박스는 aria-hidden 장식일 뿐이다(표준 커스텀 체크박스 패턴).
 *
 * 키보드 포커스 표시(WCAG 2.4.7): 진짜 포커스는 화면에 안 보이는 sr-only input에 있으므로, 형제인 시각 박스에
 * `peer-focus-visible`로 링을 그린다. `:focus-visible`이라 Tab 등 키보드로 포커스가 올 때만 나타나고 마우스
 * 클릭에는 나타나지 않는다. 링 색은 기존 토큰(brand)을, 두께·오프셋은 Tailwind 기본 스케일(ring-2/offset-2)을
 * 쓰며 box-shadow라 레이아웃을 밀지 않는다. Figma에는 체크박스 포커스 상태가 정의돼 있지 않다(CLAUDE.md
 * SCR-AUTH-02 절 "Checkbox 포커스 표시" 판단 기록 참고).
 */
export function Checkbox({ id, checked, onChange, label }: CheckboxProps) {
  return (
    <label htmlFor={id} className="flex w-full cursor-pointer items-start gap-2.5">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="peer sr-only"
      />
      <span
        aria-hidden="true"
        className={`flex size-5 shrink-0 items-center justify-center rounded-lg border-2 transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-brand peer-focus-visible:ring-offset-2 ${
          checked ? 'border-brand bg-brand' : 'border-[#d1d5db] bg-white'
        }`}
      >
        {checked && <CheckIcon strokeWidth={1.5} className="size-3 text-white" />}
      </span>
      <span className="text-[13px] leading-[17.875px] text-[#364153]">{label}</span>
    </label>
  );
}
