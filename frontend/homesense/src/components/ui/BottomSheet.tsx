import { useEffect, useEffectEvent, useRef, type ReactNode } from 'react';

interface BottomSheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}

const FOCUSABLE_SELECTOR = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * 모바일 "필터 N" 진입용 범용 바텀시트 — Figma에 이 화면 전용 프레임이 없어 기존 토큰(카드
 * radius/그림자)만으로 직접 구성했다(확정 사항). 포커스 트랩(Tab 순환)·Esc 닫기·백드롭 클릭
 * 닫기·본문 스크롤 잠금을 갖춘다. 닫힐 때 열기 전 포커스를 갖고 있던 요소로 되돌린다.
 */
export function BottomSheet({ open, onClose, title, children }: BottomSheetProps) {
  const sheetRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLElement | null>(null);
  // Esc 처리는 항상 최신 onClose를 부르되 effect 의존성에는 넣지 않는다 — 호출부가 인라인 화살표
  // 함수(`onClose={() => setSheetOpen(false)}`)를 넘기면 시트 안에서 필터를 바꿀 때마다 onClose가
  // 새 함수가 되고, 그걸 의존성에 두면 effect가 cleanup(트리거로 포커스 복귀) → 재초기화(첫 요소로
  // 포커스)를 반복해 라디오·슬라이더를 조작할 때마다 포커스가 닫기 버튼으로 튀었다. 포커스 트랩과
  // 스크롤 잠금은 `open`이 바뀔 때만 시작·종료한다.
  const closeOnEscape = useEffectEvent(() => onClose());

  useEffect(() => {
    if (!open) return;
    triggerRef.current = document.activeElement as HTMLElement;
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const focusables = sheetRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
    focusables?.[0]?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeOnEscape();
        return;
      }
      if (event.key !== 'Tab' || !sheetRef.current) return;
      const nodes = Array.from(sheetRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
      if (nodes.length === 0) return;
      const first = nodes[0];
      const last = nodes[nodes.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = originalOverflow;
      triggerRef.current?.focus();
    };
  }, [open]);

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
