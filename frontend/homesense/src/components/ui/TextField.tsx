import { forwardRef, type InputHTMLAttributes, type ReactNode } from 'react';
import { Input, type FieldStatus } from './Input';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  labelAction?: ReactNode;
  status?: FieldStatus;
  endAdornment?: ReactNode;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, labelAction, status = 'default', endAdornment, id, ...props },
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
      <Input ref={ref} id={id} status={status} endAdornment={endAdornment} {...props} />
    </div>
  );
});
