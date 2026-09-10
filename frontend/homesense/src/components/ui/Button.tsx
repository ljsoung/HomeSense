import type { ButtonHTMLAttributes } from 'react';

export function Button({ className = '', type = 'button', ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type={type}
      className={`h-12 w-full rounded-[14px] text-[15px] font-bold transition-colors disabled:cursor-not-allowed disabled:bg-[#e5e7eb] disabled:text-[#9ca3af] disabled:shadow-none enabled:bg-brand enabled:text-white enabled:shadow-[0_4px_7px_rgba(15,92,84,0.35)] enabled:hover:bg-[#0d4f48] ${className}`}
      {...props}
    />
  );
}
