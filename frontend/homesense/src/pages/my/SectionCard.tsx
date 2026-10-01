import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

export const CARD_CLASS =
  'rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_1px_1.5px_rgba(0,0,0,0.1),0_1px_1px_rgba(0,0,0,0.1)]';

interface SectionCardProps {
  title: string;
  icon: ReactNode;
  /** 헤더 오른쪽 "전체 보기" 링크. 같은 화면에 여러 개라 aria-label로 대상을 구분한다. */
  viewAll: { to: string; ariaLabel: string };
  children: ReactNode;
}

/** MY-01 미리보기 위젯(관심 매물·최근 알림)의 공통 틀 — HOME-01 카드(RecentViews)와 같은 카드 모양. */
export function SectionCard({ title, icon, viewAll, children }: SectionCardProps) {
  return (
    <section className={`flex flex-col p-5 ${CARD_CLASS}`} aria-label={title}>
      <div className="flex items-center justify-between pb-3">
        <h2 className="flex items-center gap-1.5 text-[15px] font-bold text-[#101828]">
          {icon}
          {title}
        </h2>
        <Link
          to={viewAll.to}
          aria-label={viewAll.ariaLabel}
          className="rounded-[8px] px-1.5 py-0.5 text-[12.5px] font-medium text-[#6a7282] hover:text-brand"
        >
          전체 보기
        </Link>
      </div>
      {children}
    </section>
  );
}

/** 위젯 안에서만 보이는 오류 안내 — 페이지 전체를 깨지 않는다. 문구는 서버 error.message 그대로. */
export function WidgetError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 py-6 text-center">
      <p className="text-[13px] text-[#7f1d1d]">{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="rounded-[10px] border border-[#e5e7eb] bg-white px-3.5 py-1.5 text-[13px] font-semibold text-[#364153] hover:bg-[#f7f8fa]"
      >
        다시 시도
      </button>
    </div>
  );
}

/** 목록 행 자리 표시(스켈레톤). */
export function RowSkeleton({ thumbnail = false }: { thumbnail?: boolean }) {
  return (
    <div className="flex items-center gap-3 py-3" aria-hidden="true">
      {thumbnail && <div className="size-[52px] shrink-0 animate-pulse rounded-[10px] bg-[#f3f4f6]" />}
      <div className="flex flex-1 flex-col gap-2">
        <div className="h-3.5 w-2/3 animate-pulse rounded bg-[#f3f4f6]" />
        <div className="h-3 w-1/3 animate-pulse rounded bg-[#f3f4f6]" />
      </div>
    </div>
  );
}
