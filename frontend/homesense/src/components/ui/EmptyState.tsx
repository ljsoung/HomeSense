import type { ReactNode } from 'react';

interface EmptyStateProps {
  icon: ReactNode;
  title?: string;
  description: ReactNode;
  actions?: ReactNode;
  /**
   * UIC-08 — Figma가 두 가지 다른 빈 상태 톤을 쓴다: 'inducement'(관심 지역 등록 유도 카드,
   * 점선 테두리+민트 그라디언트+버튼)와 'plain'(최근 조회 로그인 유도, 밋밋한 회색 아이콘+텍스트만).
   */
  variant?: 'plain' | 'inducement';
}

export function EmptyState({ icon, title, description, actions, variant = 'plain' }: EmptyStateProps) {
  if (variant === 'inducement') {
    return (
      <div
        className="flex h-full w-full flex-col items-center justify-center rounded-[16px] border border-dashed border-[#b8d9d4] p-8 text-center shadow-[0_1px_1.5px_rgba(0,0,0,0.1),0_1px_1px_rgba(0,0,0,0.1)]"
        style={{ backgroundImage: 'linear-gradient(143deg, #f0f9f7 0%, #e8f5f2 100%)' }}
      >
        <div className="mb-4 flex size-14 items-center justify-center rounded-[16px] bg-[#d1eae6] text-brand">{icon}</div>
        {title && <p className="mb-1.5 text-[16px] font-bold text-[#101828]">{title}</p>}
        <div className="mb-5 text-[13px] leading-[1.625] text-[#6a7282]">{description}</div>
        {actions && <div className="flex items-stretch gap-2">{actions}</div>}
      </div>
    );
  }

  return (
    <div className="flex w-full flex-col items-center justify-center py-4 text-center">
      <div className="mb-3 flex size-12 items-center justify-center rounded-full bg-[#f3f4f6] text-[#99a1af]">{icon}</div>
      {title && <p className="mb-1 text-[13px] font-semibold text-[#101828]">{title}</p>}
      <div className="text-[13px] leading-[1.625] text-[#99a1af]">{description}</div>
      {actions && <div className="mt-3 flex items-stretch gap-2">{actions}</div>}
    </div>
  );
}
