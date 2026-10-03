import { useId, useRef, type ReactNode } from 'react';
import { useDialogBehavior } from './useDialogBehavior';

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
   * 호출부가 그리고, 아래 간격(20 / 16)은 이 컴포넌트가 둔다.
   */
  icon?: ReactNode;
}

/** 보조 버튼(취소·닫기) — 테두리 #e5e7eb, radius 14, 14px 반굵게. 높이·글자색: 1280px 이상 47px·#4a5565, 그 아래 43px·#364153. */
export const MODAL_SECONDARY_BUTTON_CLASS =
  'flex h-[43px] w-full items-center justify-center rounded-[14px] border border-[#e5e7eb] bg-white text-[14px] font-semibold leading-[21px] text-[#364153] hover:bg-[#f7f8fa] xl:h-[47px] xl:text-[#4a5565]';

/**
 * 되돌릴 수 없는 동작의 확인 버튼(회원탈퇴, MY-02 삭제). 색은 Figma(1280px 이상 #ef4444, 그 아래 #fb2c36) 대신 #e7000b —
 * 흰 글자 대비가 Figma 두 색은 3.76:1·3.81:1로 4.5:1에 못 미치고 #e7000b는 4.77:1(NFR-8). 굵기는 Figma대로 1280px 이상 Bold.
 */
export const MODAL_DANGER_BUTTON_CLASS =
  'flex h-[43px] w-full items-center justify-center rounded-[14px] bg-[#e7000b] text-[14px] font-semibold text-white hover:bg-[#c10007] disabled:cursor-not-allowed disabled:opacity-50 xl:h-[47px] xl:font-bold';

/** 확인·취소 두 버튼을 담는 하단 영역 — 간격 1280px 이상 12px, 그 아래 10px. 768px 미만은 세로로 쌓는다. */
export const MODAL_FOOTER_ROW_CLASS = 'flex flex-col gap-2.5 md:flex-row-reverse xl:gap-3';

/**
 * 공용 모달 대화상자 — DTL-01 거래상세 모달이 처음 쓰고, MY-01 계정 다이얼로그·MY-02 삭제 다이얼로그가 이어서 쓴다.
 * role="dialog" + aria-modal, 제목으로 aria-labelledby, 포커스 트랩·Esc·배경 클릭 닫기·본문 스크롤 잠금·닫을 때
 * 연 요소로 포커스 복귀(useDialogBehavior, BottomSheet와 공유). 높이는 85vh까지이고 넘치면 본문만 스크롤한다.
 *
 * 모양의 근거는 Figma 파일 안의 유일한 다이얼로그인 MY-02 삭제 다이얼로그다(데스크톱 6-4995, 태블릿 29-16508, 모바일
 * 28-15713 — 다른 화면에는 다이얼로그 프레임이 없다). 1280px 이상: 오버레이 검정 40%, 폭 400, 안쪽 32, 그림자
 * 0 16px 48px 20%, 제목 18px ExtraBold, 제목→본문 10, 버튼 영역 위 28. 그 아래: 오버레이 45%, 폭 320, 안쪽 24,
 * 그림자 0 20px 60px 25%, 제목 16px Bold, 제목→본문 8, 버튼 영역 위 24. radius는 모두 16. 닫기는 X 아이콘 대신 아래쪽
 * 버튼으로 둔다. 렌더는 한 벌이고 크기 차이는 CSS 미디어쿼리로만 나눈다.
 */
export function Modal({ open, onClose, title, children, footer, role = 'dialog', describedBy, icon }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  useDialogBehavior(open, dialogRef, onClose);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/45 xl:bg-black/40" onClick={onClose} aria-hidden="true" />
      <div
        ref={dialogRef}
        role={role}
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={describedBy}
        className="relative z-10 flex max-h-[85vh] w-full max-w-[320px] flex-col rounded-[16px] bg-white p-6 shadow-[0_20px_60px_rgba(0,0,0,0.25)] xl:max-w-[400px] xl:p-8 xl:shadow-[0_16px_48px_rgba(0,0,0,0.2)]"
      >
        {icon && <div className="mb-4 shrink-0 xl:mb-5">{icon}</div>}
        <h2
          id={titleId}
          className="shrink-0 text-[16px] font-bold leading-6 text-[#101828] xl:text-[18px] xl:font-extrabold xl:leading-[27px]"
        >
          {title}
        </h2>
        <div className="mt-2 min-h-0 overflow-y-auto xl:mt-2.5">{children}</div>
        <div className="shrink-0 pt-6 xl:pt-7">
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
