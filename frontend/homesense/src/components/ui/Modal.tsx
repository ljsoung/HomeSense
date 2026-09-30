import { useId, useRef, type ReactNode } from 'react';
import { useDialogBehavior } from './useDialogBehavior';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  /** 하단 버튼 영역(선택). 없으면 "닫기" 버튼 하나를 둔다. */
  footer?: ReactNode;
}

/** Figma 다이얼로그(6:5277, 모바일 28:16012)의 보조 버튼 — 테두리 #e5e7eb, radius 14, 14px 반굵게. */
const MODAL_SECONDARY_BUTTON_CLASS =
  'flex h-[43px] w-full items-center justify-center rounded-[14px] border border-[#e5e7eb] bg-white text-[14px] font-semibold leading-[21px] text-[#364153] hover:bg-[#f7f8fa] md:h-[47px] md:text-[#4a5565]';

/**
 * 공용 모달 대화상자 — DTL-01 거래상세 모달이 처음 쓰고, MY-02 삭제 다이얼로그 등이 이어서 쓴다.
 * role="dialog" + aria-modal, 제목으로 aria-labelledby, 포커스 트랩·Esc·배경 클릭 닫기·본문 스크롤 잠금·닫을 때
 * 연 요소로 포커스 복귀(useDialogBehavior, BottomSheet와 공유). 높이는 85vh까지이고 넘치면 본문만 스크롤한다.
 *
 * 모양은 Figma MY-02 삭제 다이얼로그를 따른다 — 데스크톱(6:5261): 오버레이 검정 40%, 폭 400px, 안쪽 32px,
 * radius 16, 그림자 0 16px 24px 20%, 제목 18px 가장 굵게. 모바일(28:15995): 오버레이 45%, 폭 320px, 안쪽 24px,
 * 그림자 0 20px 30px 25%, 제목 16px 굵게. 닫기는 X 아이콘 대신 Figma처럼 아래쪽 버튼으로 둔다.
 */
export function Modal({ open, onClose, title, children, footer }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  useDialogBehavior(open, dialogRef, onClose);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/45 md:bg-black/40" onClick={onClose} aria-hidden="true" />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="relative z-10 flex max-h-[85vh] w-full max-w-[320px] flex-col rounded-[16px] bg-white p-6 shadow-[0_20px_30px_rgba(0,0,0,0.25)] md:max-w-[400px] md:p-8 md:shadow-[0_16px_24px_rgba(0,0,0,0.2)]"
      >
        <h2
          id={titleId}
          className="shrink-0 text-[16px] font-bold leading-6 text-[#101828] md:text-[18px] md:font-extrabold md:leading-[27px]"
        >
          {title}
        </h2>
        <div className="mt-4 min-h-0 overflow-y-auto">{children}</div>
        <div className="shrink-0 pt-6 md:pt-7">
          {footer ?? (
            <button type="button" onClick={onClose} className={MODAL_SECONDARY_BUTTON_CLASS}>
              닫기
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
