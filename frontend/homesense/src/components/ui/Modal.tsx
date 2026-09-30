import { useId, useRef, type ReactNode } from 'react';
import { XIcon } from '../icons/XIcon';
import { useDialogBehavior } from './useDialogBehavior';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  /** 하단 버튼 영역(선택). */
  footer?: ReactNode;
}

/**
 * 공용 모달 대화상자 — DTL-01 거래상세 모달이 처음 쓰고, MY-02 삭제 다이얼로그 등이 이어서 쓴다.
 * role="dialog" + aria-modal, 제목으로 aria-labelledby, 포커스 트랩·Esc·배경 클릭 닫기·본문 스크롤 잠금·닫을 때
 * 연 요소로 포커스 복귀(useDialogBehavior, BottomSheet와 공유). 모바일은 좌우 여백을 둔 전체 폭, 높이는 85vh까지이고
 * 넘치면 본문만 스크롤한다.
 */
export function Modal({ open, onClose, title, children, footer }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  useDialogBehavior(open, dialogRef, onClose);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} aria-hidden="true" />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="relative z-10 flex max-h-[85vh] w-full max-w-[440px] flex-col rounded-[16px] bg-white shadow-[0_20px_40px_rgba(0,0,0,0.18)]"
      >
        <div className="flex shrink-0 items-center justify-between gap-3 border-b border-[#f3f4f6] px-5 py-4">
          <h2 id={titleId} className="text-[16px] font-bold text-[#101828]">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="rounded-full p-1 text-[#6a7282] hover:bg-[#f3f4f6]"
          >
            <XIcon className="size-5" />
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="shrink-0 border-t border-[#f3f4f6] px-5 py-3">{footer}</div>}
      </div>
    </div>
  );
}
