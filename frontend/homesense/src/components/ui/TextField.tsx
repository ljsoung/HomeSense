import { forwardRef, type InputHTMLAttributes, type ReactNode } from 'react';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  labelAction?: ReactNode;
  error?: boolean;
  endAdornment?: ReactNode;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, labelAction, error = false, endAdornment, className = '', id, ...props },
  ref,
) {
  return (
    <div className="flex w-full flex-col gap-1.5">
      <div className="flex items-center justify-between">
        <label htmlFor={id} className="text-[13px] font-semibold text-[#364153]">
          {label}
        </label>
        {labelAction}
      </div>
      <div className="relative">
        <input
          ref={ref}
          id={id}
          className={`h-11 w-full rounded-[14px] border bg-white px-4 text-[14px] text-[#101828] outline-none transition-colors placeholder:text-[#99a1af] focus:border-brand ${
            error ? 'border-[#ff6467]' : 'border-[#e5e7eb]'
          } ${endAdornment ? 'pr-11' : ''} ${className}`}
          {...props}
        />
        {endAdornment && <div className="absolute inset-y-0 right-3.5 flex items-center">{endAdornment}</div>}
      </div>
    </div>
  );
});
