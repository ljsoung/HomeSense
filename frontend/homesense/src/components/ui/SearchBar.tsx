import type { FormEvent } from 'react';
import { SearchIcon } from '../icons/SearchIcon';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  placeholder?: string;
}

/** UIC-03. 검색 입력 한 단위(아이콘+입력창+검색 버튼) — 히어로 검색, SRCH-01 등에서 재사용된다. */
export function SearchBar({ value, onChange, onSubmit, placeholder = '지역명·단지명(건물명)으로 검색' }: SearchBarProps) {
  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit();
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 p-4">
      <div className="flex items-center gap-3 rounded-[14px] border border-[#e5e7eb] px-3.5 py-2.5">
        <SearchIcon className="size-[18px] shrink-0 text-[#99a1af]" />
        <input
          type="text"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          className="w-full min-w-0 text-[14px] text-[#101828] placeholder:text-[#99a1af] focus:outline-none"
        />
      </div>
      <button type="submit" className="h-[45px] w-full rounded-[14px] bg-brand text-[14px] font-semibold text-white hover:bg-[#0d4f48]">
        검색
      </button>
    </form>
  );
}
