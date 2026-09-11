import { forwardRef, type InputHTMLAttributes, type ReactNode } from 'react';

export type FieldStatus = 'default' | 'success' | 'error';

function statusBorderClass(status: FieldStatus): string {
  switch (status) {
    case 'success':
      return 'border-[#05df72] focus:border-[#05df72]';
    case 'error':
      return 'border-[#ff6467] focus:border-[#ff6467]';
    default:
      return 'border-[#e5e7eb] focus:border-brand';
  }
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  status?: FieldStatus;
  endAdornment?: ReactNode;
}

/** TextField의 label 없이 입력 상자만 필요한 경우(예: AUTH-02 이메일 + 중복확인 버튼 가로 배치)를 위한 하위 컴포넌트. */
export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { status = 'default', endAdornment, className = '', ...props },
  ref,
) {
  return (
    <div className="relative w-full">
      <input
        ref={ref}
        className={`h-11 w-full rounded-[14px] border bg-white px-4 text-[14px] text-[#101828] outline-none transition-colors placeholder:text-[#99a1af] ${statusBorderClass(status)} ${
          endAdornment ? 'pr-11' : ''
        } ${className}`}
        {...props}
      />
      {endAdornment && <div className="absolute inset-y-0 right-3.5 flex items-center">{endAdornment}</div>}
    </div>
  );
});
