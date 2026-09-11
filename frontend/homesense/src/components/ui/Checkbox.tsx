import type { ReactNode } from 'react';
import { CheckIcon } from '../icons/CheckIcon';

interface CheckboxProps {
  id?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: ReactNode;
}

/** 실제 체크 의미는 sr-only 네이티브 input이 갖고, 시각적 박스는 aria-hidden 장식일 뿐이다(표준 커스텀 체크박스 패턴). */
export function Checkbox({ id, checked, onChange, label }: CheckboxProps) {
  return (
    <label htmlFor={id} className="flex w-full cursor-pointer items-start gap-2.5">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="sr-only"
      />
      <span
        aria-hidden="true"
        className={`flex size-5 shrink-0 items-center justify-center rounded-lg border-2 transition-colors ${
          checked ? 'border-brand bg-brand' : 'border-[#d1d5db] bg-white'
        }`}
      >
        {checked && <CheckIcon strokeWidth={1.5} className="size-3 text-white" />}
      </span>
      <span className="text-[13px] leading-[17.875px] text-[#364153]">{label}</span>
    </label>
  );
}
