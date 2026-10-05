import { createContext } from 'react';

export type ToastVariant = 'success' | 'error';

export interface ToastAction {
  label: string;
  onClick: () => void;
}

export interface ToastOptions {
  /** 토스트 안의 버튼(예: "실행취소", "목록 보기"). 누르면 onClick 뒤 토스트를 닫고 onExpire는 부르지 않는다. */
  action?: ToastAction;
  /** 표시 시간. 기본 3초. */
  durationMs?: number;
  /** 표시 시간이 다 지나 스스로 닫힐 때만 부른다(버튼·dismissToast로 닫히면 부르지 않는다). */
  onExpire?: () => void;
}

export interface ToastContextValue {
  /** 토스트를 띄우고 그 id를 돌려준다. */
  showToast: (message: string, variant?: ToastVariant, options?: ToastOptions) => number;
  /** 토스트를 바로 닫는다. onExpire는 부르지 않는다. 이미 닫혔으면 아무 일도 없다. */
  dismissToast: (id: number) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);
