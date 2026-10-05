import { useId, useRef, type ReactNode } from 'react';
import { useDialogBehavior } from './useDialogBehavior';

/**
 * - 'confirm'(확인형): 아이콘·제목·짧은 본문·버튼만 있는 확인 다이얼로그(MY-02 삭제, MY-01 로그아웃·회원탈퇴 선택).
 *   근거는 Figma MY-02 삭제 다이얼로그 프레임(데스크톱 6-4995, 태블릿 29-16508, 모바일 28-15713) — Figma 파일 안의
 *   다이얼로그는 이 세 프레임뿐이다. 경계 1280px.
 * - 'content'(내용형): 목록·표·폼처럼 내용을 담는 모달(DTL-01 거래상세, MY-01 회원탈퇴 폼). Figma 근거가 없어 2026-10-02까지
 *   쓰던 값(34bbba3 이전)을 유지한다 — 768px 이상에서 폭 400이라 내용이 좁아지지 않는다. 경계 768px.
 */
export type ModalVariant = 'confirm' | 'content';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  /** 하단 버튼 영역(선택). 없으면 "닫기" 버튼 하나를 둔다. */
  footer?: ReactNode;
  /** 되돌릴 수 없는 동작을 확인받는 대화상자(회원탈퇴 등)는 'alertdialog'. 기본 'dialog'. */
  role?: 'dialog' | 'alertdialog';
  /** 본문 중 대화상자 설명으로 읽힐 요소의 id(aria-describedby). alertdialog에 권장된다. */
  describedBy?: string;
  /**
   * 제목 위 아이콘(선택) — MY-02 삭제 다이얼로그의 휴지통 상자. 상자 모양(1280px 이상 56px 원, 그 아래 48px radius 16)은
   * 호출부가 그리고, 아래 간격은 이 컴포넌트가 둔다.
   */
  icon?: ReactNode;
  /** 확인형/내용형(ModalVariant). 기본 'content'. */
  variant?: ModalVariant;
}

const VARIANT_CLASS: Record<ModalVariant, { overlay: string; card: string; icon: string; title: string; body: string; footer: string }> = {
  // 1280px 이상: 오버레이 40%, 폭 400, 안쪽 32, 그림자 0 16px 48px 20%, 아이콘 아래 20, 제목 18px ExtraBold, 제목→본문 10,
  // 버튼 영역 위 28. 그 아래: 45%, 320, 24, 0 20px 60px 25%, 16, 16px Bold, 8, 24. radius는 모두 16.
  confirm: {
    overlay: 'bg-black/45 xl:bg-black/40',
    card: 'max-w-[320px] p-6 shadow-[0_20px_60px_rgba(0,0,0,0.25)] xl:max-w-[400px] xl:p-8 xl:shadow-[0_16px_48px_rgba(0,0,0,0.2)]',
    icon: 'mb-4 xl:mb-5',
    title: 'text-[16px] font-bold leading-6 xl:text-[18px] xl:font-extrabold xl:leading-[27px]',
    body: 'mt-2 xl:mt-2.5',
    footer: 'pt-6 xl:pt-7',
  },
  // 34bbba3 이전 값 그대로(경계 768px).
  content: {
    overlay: 'bg-black/45 md:bg-black/40',
    card: 'max-w-[320px] p-6 shadow-[0_20px_30px_rgba(0,0,0,0.25)] md:max-w-[400px] md:p-8 md:shadow-[0_16px_24px_rgba(0,0,0,0.2)]',
    icon: 'mb-5',
    title: 'text-[16px] font-bold leading-6 md:text-[18px] md:font-extrabold md:leading-[27px]',
    body: 'mt-4',
    footer: 'pt-6 md:pt-7',
  },
};

/** 내용형 보조 버튼(닫기·취소) — 테두리 #e5e7eb, radius 14, 14px 반굵게. 768px 이상 47px·#4a5565, 그 아래 43px·#364153. */
export const MODAL_SECONDARY_BUTTON_CLASS =
  'flex h-[43px] w-full items-center justify-center rounded-[14px] border border-[#e5e7eb] bg-white text-[14px] font-semibold leading-[21px] text-[#364153] hover:bg-[#f7f8fa] md:h-[47px] md:text-[#4a5565]';

/** 내용형의 되돌릴 수 없는 동작 버튼(회원탈퇴 폼). #e7000b — 흰 글자 대비 4.77:1(NFR-8). */
export const MODAL_DANGER_BUTTON_CLASS =
  'flex h-[43px] w-full items-center justify-center rounded-[14px] bg-[#e7000b] text-[14px] font-semibold text-white hover:bg-[#c10007] disabled:cursor-not-allowed disabled:opacity-50 md:h-[47px]';

/** 내용형 두 버튼 영역 — 768px 미만은 세로로 쌓는다. */
export const MODAL_FOOTER_ROW_CLASS = 'flex flex-col gap-2 md:flex-row-reverse';

/** 확인형 보조 버튼(취소) — Figma MY-02: 1280px 이상 47px·#4a5565, 그 아래 43px·#364153. */
export const CONFIRM_SECONDARY_BUTTON_CLASS =
  'flex h-[43px] w-full items-center justify-center rounded-[14px] border border-[#e5e7eb] bg-white text-[14px] font-semibold leading-[21px] text-[#364153] hover:bg-[#f7f8fa] xl:h-[47px] xl:text-[#4a5565]';

/**
 * 확인형 위험 버튼(MY-02 삭제). 색은 Figma(1280px 이상 #ef4444, 그 아래 #fb2c36) 대신 #e7000b — 흰 글자 대비가 Figma 두 색은
 * 3.76:1·3.81:1로 4.5:1에 못 미치고 #e7000b는 4.77:1(NFR-8). 굵기는 Figma대로 1280px 이상 Bold.
 * 투명 테두리 1px는 가로 배치에서 취소 버튼(테두리 1px)과 폭을 똑같이 나누려고 둔다 — 없으면 2px 좁아진다.
 */
export const CONFIRM_DANGER_BUTTON_CLASS =
  'flex h-[43px] w-full items-center justify-center rounded-[14px] border border-transparent bg-[#e7000b] text-[14px] font-semibold text-white hover:bg-[#c10007] disabled:cursor-not-allowed disabled:opacity-50 xl:h-[47px] xl:font-bold';

/**
 * 확인형 두 버튼 영역 — 모든 크기에서 가로(Figma 28-15713·29-16508도 가로 auto-layout으로 두 버튼이 폭을 나눠 가진다).
 * 간격 1280px 이상 12px, 그 아래 10px. 확인 버튼이 오른쪽이다(DOM은 확인 → 취소 순서라 초기 포커스가 확인 버튼).
 */
export const CONFIRM_FOOTER_ROW_CLASS = 'flex flex-row-reverse gap-2.5 xl:gap-3 [&>*]:min-w-0 [&>*]:flex-1';

/**
 * 공용 모달 대화상자 — DTL-01 거래상세 모달이 처음 쓰고, MY-01 계정 다이얼로그·MY-02 삭제 다이얼로그가 이어서 쓴다.
 * role="dialog" + aria-modal, 제목으로 aria-labelledby, 포커스 트랩·Esc·배경 클릭 닫기·본문 스크롤 잠금·닫을 때
 * 연 요소로 포커스 복귀(useDialogBehavior, BottomSheet와 공유). 높이는 85vh까지이고 넘치면 본문만 스크롤한다.
 * 모양은 variant(확인형/내용형)에 따른다 — ModalVariant 설명 참고. 닫기는 X 아이콘 대신 아래쪽 버튼으로 둔다.
 * 렌더는 한 벌이고 크기 차이는 CSS 미디어쿼리로만 나눈다.
 */
export function Modal({ open, onClose, title, children, footer, role = 'dialog', describedBy, icon, variant = 'content' }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  useDialogBehavior(open, dialogRef, onClose);

  if (!open) return null;

  const style = VARIANT_CLASS[variant];
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className={`absolute inset-0 ${style.overlay}`} onClick={onClose} aria-hidden="true" />
      <div
        ref={dialogRef}
        role={role}
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={describedBy}
        data-variant={variant}
        className={`relative z-10 flex max-h-[85vh] w-full flex-col rounded-[16px] bg-white ${style.card}`}
      >
        {icon && <div className={`shrink-0 ${style.icon}`}>{icon}</div>}
        <h2 id={titleId} className={`shrink-0 text-[#101828] ${style.title}`}>
          {title}
        </h2>
        <div className={`min-h-0 overflow-y-auto ${style.body}`}>{children}</div>
        <div className={`shrink-0 ${style.footer}`}>
          {footer ?? (
            <button
              type="button"
              onClick={onClose}
              className={variant === 'confirm' ? CONFIRM_SECONDARY_BUTTON_CLASS : MODAL_SECONDARY_BUTTON_CLASS}
            >
              닫기
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
