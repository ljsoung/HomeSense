import { ChevronLeftIcon } from '../icons/ChevronLeftIcon';
import { ChevronRightIcon } from '../icons/ChevronRightIcon';

interface PaginationProps {
  currentPage: number; // 1-base
  totalPages: number;
  onPageChange: (page: number) => void;
}

const GROUP_SIZE = 5;

/** UIC-06 — 데스크톱/태블릿 번호 페이지네이션(5개 묶음 + 이전/다음). 모바일 무한스크롤은 SRCH-01 페이지가 별도로 구현한다. */
export function Pagination({ currentPage, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) return null;

  const group = Math.ceil(currentPage / GROUP_SIZE);
  const start = (group - 1) * GROUP_SIZE + 1;
  const end = Math.min(start + GROUP_SIZE - 1, totalPages);
  const pages = Array.from({ length: end - start + 1 }, (_, i) => start + i);

  const buttonBase = 'flex size-9 items-center justify-center rounded-full text-[13px] font-semibold transition-colors';

  return (
    <nav aria-label="페이지네이션" className="flex items-center justify-center gap-1.5">
      <button
        type="button"
        aria-label="이전 페이지"
        disabled={start === 1}
        onClick={() => onPageChange(start - 1)}
        className={`${buttonBase} text-[#6a7282] hover:bg-[#f3f4f6] disabled:cursor-not-allowed disabled:opacity-30`}
      >
        <ChevronLeftIcon className="size-4" />
      </button>
      {pages.map((page) => (
        <button
          key={page}
          type="button"
          aria-current={page === currentPage ? 'page' : undefined}
          onClick={() => onPageChange(page)}
          className={`${buttonBase} ${page === currentPage ? 'bg-brand text-white' : 'text-[#364153] hover:bg-[#f3f4f6]'}`}
        >
          {page}
        </button>
      ))}
      <button
        type="button"
        aria-label="다음 페이지"
        disabled={end === totalPages}
        onClick={() => onPageChange(end + 1)}
        className={`${buttonBase} text-[#6a7282] hover:bg-[#f3f4f6] disabled:cursor-not-allowed disabled:opacity-30`}
      >
        <ChevronRightIcon className="size-4" />
      </button>
    </nav>
  );
}
