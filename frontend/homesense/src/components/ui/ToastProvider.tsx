import { useCallback, useEffect, useMemo, useRef, useState, type FocusEvent, type ReactNode } from 'react';
import { AlertCircleIcon } from '../icons/AlertCircleIcon';
import { CheckIcon } from '../icons/CheckIcon';
import { ToastContext, type ToastAction, type ToastOptions, type ToastVariant } from './toastContext';

interface ToastItem {
  id: number;
  message: string;
  variant: ToastVariant;
  durationMs: number;
  action?: ToastAction;
  onExpire?: () => void;
}

const AUTO_DISMISS_MS = 3000;

/**
 * UIC-07. Figma에 별도 토스트 프레임이 없어(이번 HOME-01 조회 범위 밖) 기존 AUTH 화면의 톤(brand
 * 색상, rounded-14px, 그림자)만 재사용해 최소 구성으로 만들었다 — 화면 우상단(데스크톱)/하단
 * 중앙(모바일, 바텀탭과 겹치지 않도록 위쪽에 띄움)에 스택으로 쌓인다.
 *
 * 버튼이 있는 토스트(MY-02 "실행취소", DTL-01 "목록 보기")는 마우스를 올리거나 키보드 포커스가 안에 있는 동안
 * 시간이 멈춘다(WCAG 2.2.1 시간 조절). 버튼이 없는 토스트는 예전처럼 3초 뒤 닫힌다.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(0);

  const remove = useCallback((id: number) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback((message: string, variant: ToastVariant = 'success', options: ToastOptions = {}) => {
    const id = nextId.current++;
    const item: ToastItem = {
      id,
      message,
      variant,
      durationMs: options.durationMs ?? AUTO_DISMISS_MS,
      action: options.action,
      onExpire: options.onExpire,
    };
    setToasts((prev) => [...prev, item]);
    return id;
  }, []);

  const value = useMemo(() => ({ showToast, dismissToast: remove }), [showToast, remove]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex flex-col items-center gap-2 px-4 md:inset-x-auto md:top-5 md:right-5 md:bottom-auto md:items-end">
        {toasts.map((toast) => (
          <ToastView key={toast.id} toast={toast} onRemove={remove} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

function ToastView({ toast, onRemove }: { toast: ToastItem; onRemove: (id: number) => void }) {
  const pausable = toast.action !== undefined;
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const paused = pausable && (hovered || focused);
  const remainingRef = useRef(toast.durationMs);
  // 타이머 콜백이 최신 값을 쓰도록 — 토스트 항목은 바뀌지 않지만 onRemove는 바뀔 수 있다.
  const latest = useRef({ toast, onRemove });
  useEffect(() => {
    latest.current = { toast, onRemove };
  });

  useEffect(() => {
    if (paused) return;
    const startedAt = Date.now();
    const timer = window.setTimeout(() => {
      const { toast: current, onRemove: removeToast } = latest.current;
      removeToast(current.id);
      current.onExpire?.();
    }, remainingRef.current);
    return () => {
      window.clearTimeout(timer);
      remainingRef.current = Math.max(0, remainingRef.current - (Date.now() - startedAt));
    };
  }, [paused]);

  const handleBlur = (event: FocusEvent<HTMLDivElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocused(false);
  };

  return (
    <div
      role="status"
      onMouseEnter={pausable ? () => setHovered(true) : undefined}
      onMouseLeave={pausable ? () => setHovered(false) : undefined}
      onFocus={pausable ? () => setFocused(true) : undefined}
      onBlur={pausable ? handleBlur : undefined}
      className={`pointer-events-auto flex w-full max-w-[360px] items-center gap-2 rounded-[14px] px-4 py-3 text-[13.5px] font-semibold text-white shadow-[0_8px_20px_rgba(0,0,0,0.18)] ${
        toast.variant === 'success' ? 'bg-brand' : 'bg-[#e7000b]'
      }`}
    >
      {toast.variant === 'success' ? (
        <CheckIcon className="size-4 shrink-0" strokeWidth={2} />
      ) : (
        <AlertCircleIcon className="size-4 shrink-0" />
      )}
      <span className="flex-1">{toast.message}</span>
      {toast.action && (
        <button
          type="button"
          onClick={() => {
            onRemove(toast.id);
            toast.action?.onClick();
          }}
          className="shrink-0 rounded-[8px] px-2 py-1 text-[13px] font-bold text-white underline underline-offset-2 hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-white"
        >
          {toast.action.label}
        </button>
      )}
    </div>
  );
}
