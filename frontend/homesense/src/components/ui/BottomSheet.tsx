import { useRef, type ReactNode } from 'react';
import { useDialogBehavior } from './useDialogBehavior';

interface BottomSheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}

/**
 * 모바일 "필터 N" 진입용 범용 바텀시트 — Figma에 이 화면 전용 프레임이 없어 기존 토큰(카드
 * radius/그림자)만으로 직접 구성했다(확정 사항). 포커스 트랩(Tab 순환)·Esc 닫기·백드롭 클릭
 * 닫기·본문 스크롤 잠금을 갖춘다(useDialogBehavior — 공용 Modal과 공유). 닫힐 때 열기 전 포커스를
 * 갖고 있던 요소로 되돌린다.
 */
export function BottomSheet({ open, onClose, title, children }: BottomSheetProps) {
  const sheetRef = useRef<HTMLDivElement>(null);
  useDialogBehavior(open, sheetRef, onClose);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} aria-hidden="true" />
      <div
        ref={sheetRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="relative z-10 flex max-h-[85vh] w-full flex-col rounded-t-[20px] bg-white shadow-[0_-4px_20px_rgba(0,0,0,0.15)]"
      >
        <div className="flex shrink-0 items-center justify-between border-b border-[#f3f4f6] px-4 py-3.5">
          <p className="text-[15px] font-bold text-[#101828]">{title}</p>
          <button type="button" onClick={onClose} aria-label="닫기" className="p-1 text-[#6a7282]">
            ✕
          </button>
        </div>
        <div className="overflow-y-auto px-4 py-4">{children}</div>
      </div>
    </div>
  );
}
