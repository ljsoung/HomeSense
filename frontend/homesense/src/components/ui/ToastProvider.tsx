import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react';
import { AlertCircleIcon } from '../icons/AlertCircleIcon';
import { CheckIcon } from '../icons/CheckIcon';
import { ToastContext, type ToastVariant } from './toastContext';

interface ToastItem {
  id: number;
  message: string;
  variant: ToastVariant;
}

const AUTO_DISMISS_MS = 3000;

/**
 * UIC-07. Figma에 별도 토스트 프레임이 없어(이번 HOME-01 조회 범위 밖) 기존 AUTH 화면의 톤(brand
 * 색상, rounded-14px, 그림자)만 재사용해 최소 구성으로 만들었다 — 화면 우상단(데스크톱)/하단
 * 중앙(모바일, 바텀탭과 겹치지 않도록 위쪽에 띄움)에 스택으로 쌓인다.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(0);

  const showToast = useCallback((message: string, variant: ToastVariant = 'success') => {
    const id = nextId.current++;
    setToasts((prev) => [...prev, { id, message, variant }]);
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((toast) => toast.id !== id));
    }, AUTO_DISMISS_MS);
  }, []);

  const value = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex flex-col items-center gap-2 px-4 md:inset-x-auto md:top-5 md:right-5 md:bottom-auto md:items-end">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            className={`pointer-events-auto flex w-full max-w-[360px] items-center gap-2 rounded-[14px] px-4 py-3 text-[13.5px] font-semibold text-white shadow-[0_8px_20px_rgba(0,0,0,0.18)] ${
              toast.variant === 'success' ? 'bg-brand' : 'bg-[#e7000b]'
            }`}
          >
            {toast.variant === 'success' ? (
              <CheckIcon className="size-4 shrink-0" strokeWidth={2} />
            ) : (
              <AlertCircleIcon className="size-4 shrink-0" />
            )}
            <span>{toast.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
