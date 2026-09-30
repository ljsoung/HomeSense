import { useEffect, useEffectEvent, type RefObject } from 'react';

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * 모달성 대화상자(BottomSheet, Modal)의 공통 동작 — 열릴 때 첫 포커스 가능 요소로 포커스, Tab 순환(포커스 트랩),
 * Esc 닫기, 본문 스크롤 잠금, 닫힐 때 열기 전 포커스(연 버튼·행)로 복귀. BottomSheet에 있던 것을 그대로 옮겼다.
 *
 * Esc 처리는 항상 최신 onClose를 부르되 effect 의존성에는 넣지 않는다 — 호출부가 인라인 화살표 함수를 넘기면
 * 렌더마다 onClose가 새 함수가 되고, 그걸 의존성에 두면 effect가 cleanup(트리거로 포커스 복귀) → 재초기화(첫
 * 요소로 포커스)를 반복해 대화상자 안에서 값을 바꿀 때마다 포커스가 닫기 버튼으로 튀었다(SRCH-01 바텀시트
 * 버그, e2e srch01-bottomsheet-focus-check). 포커스 트랩과 스크롤 잠금은 `open`이 바뀔 때만 시작·종료한다.
 */
export function useDialogBehavior(open: boolean, containerRef: RefObject<HTMLElement | null>, onClose: () => void) {
  const closeOnEscape = useEffectEvent(() => onClose());

  useEffect(() => {
    if (!open) return;
    const trigger = document.activeElement as HTMLElement | null;
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const focusables = containerRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
    focusables?.[0]?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeOnEscape();
        return;
      }
      if (event.key !== 'Tab' || !containerRef.current) return;
      const nodes = Array.from(containerRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
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
      trigger?.focus();
    };
  }, [open, containerRef]);
}
