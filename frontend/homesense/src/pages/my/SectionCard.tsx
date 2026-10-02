import { useId, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRightIcon } from '../../components/icons/ArrowRightIcon';

/** MY-01 카드 공통(Figma 7:5373) — 흰 배경, radius 16, 테두리 #f3f4f6, 그림자 0 1px 4px 5%. 프로필 카드만 그림자 6%. */
export const CARD_CLASS = 'rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_1px_4px_rgba(0,0,0,0.05)]';

/** 섹션 제목 — 카드 밖 위쪽에 놓는 14/21 Bold 회색 글자(메뉴·관심 매물·최근 알림 공통). */
export const SECTION_TITLE_CLASS = 'text-[14px] leading-[21px] font-bold text-[#99a1af]';

interface SectionCardProps {
  title: string;
  /** 제목 줄 오른쪽 "전체 보기" 링크. 같은 화면에 여러 개라 aria-label로 대상을 구분한다. */
  viewAll: { to: string; ariaLabel: string };
  children: ReactNode;
}

/**
 * MY-01 미리보기 위젯(관심 매물·최근 알림)의 틀 — 제목과 "전체 보기"는 카드 밖 위에, 목록은 카드 안에 둔다(Figma).
 * 카드 자체에는 안쪽 여백이 없고 각 행이 16/20 여백을 갖는다. 행이 아닌 내용(빈 상태·오류·스켈레톤)은 CardBody로 감싼다.
 */
export function SectionCard({ title, viewAll, children }: SectionCardProps) {
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className="flex flex-col">
      <div className="mb-3 flex items-center justify-between">
        <h2 id={headingId} className={SECTION_TITLE_CLASS}>
          {title}
        </h2>
        <Link
          to={viewAll.to}
          aria-label={viewAll.ariaLabel}
          className="flex items-center gap-1 rounded-[8px] text-[13px] leading-5 font-semibold text-brand hover:underline"
        >
          전체 보기
          <ArrowRightIcon aria-hidden="true" className="size-3.5" />
        </Link>
      </div>
      <div className={`flex flex-1 flex-col overflow-hidden ${CARD_CLASS}`}>{children}</div>
    </section>
  );
}

/** 카드 안의 행이 아닌 내용(빈 상태·오류) — 행과 같은 좌우 여백을 준다. */
export function CardBody({ children }: { children: ReactNode }) {
  return <div className="px-5 py-4">{children}</div>;
}

/** 카드 맨 아래 "전체 … 보기" 행 — 13/20 SemiBold Primary, 화살표와 간격 6, 위아래 14. */
export function CardFooterLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link
      to={to}
      className="flex items-center justify-center gap-1.5 border-t border-[#f3f4f6] py-3.5 text-[13px] leading-5 font-semibold text-brand hover:bg-[#f7f8fa]"
    >
      {children}
      <ArrowRightIcon aria-hidden="true" className="size-3.5" />
    </Link>
  );
}

/** 위젯 안에서만 보이는 오류 안내 — 페이지 전체를 깨지 않는다. 문구는 서버 error.message 그대로. */
export function WidgetError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <CardBody>
      <div role="alert" className="flex flex-col items-center gap-3 py-2 text-center">
        <p className="text-[13px] text-[#7f1d1d]">{message}</p>
        <button
          type="button"
          onClick={onRetry}
          className="rounded-[10px] border border-[#e5e7eb] bg-white px-3.5 py-1.5 text-[13px] font-semibold text-[#364153] hover:bg-[#f7f8fa]"
        >
          다시 시도
        </button>
      </div>
    </CardBody>
  );
}

/** 목록 행 자리 표시(스켈레톤) — 실제 행과 같은 여백·썸네일 크기. */
export function RowSkeleton({ thumbnail = false }: { thumbnail?: boolean }) {
  return (
    <div className="flex items-center gap-3.5 px-5 py-4" aria-hidden="true">
      {thumbnail && <div className="h-[42px] w-14 shrink-0 animate-pulse rounded-[14px] bg-[#f3f4f6]" />}
      <div className="flex flex-1 flex-col gap-2">
        <div className="h-3.5 w-2/3 animate-pulse rounded bg-[#f3f4f6]" />
        <div className="h-3 w-1/3 animate-pulse rounded bg-[#f3f4f6]" />
      </div>
    </div>
  );
}
