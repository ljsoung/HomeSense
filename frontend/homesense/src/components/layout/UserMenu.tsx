import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../features/auth/useAuth';
import { useToast } from '../ui/useToast';

interface UserMenuProps {
  /** 'desktop' = 아바타+닉네임 알약(Figma 3:38), 'mobile' = 아바타 원형만. */
  variant: 'desktop' | 'mobile';
}

/**
 * 로그인 상태의 헤더 우측 아바타 — 누르면 "마이페이지 / 로그아웃" 메뉴가 열린다. Figma(3:2/24:6736)는
 * 아바타만 그리고 로그아웃 위치를 정의하지 않았고, 마이페이지(MY-01)는 아직 자리표시라 그 안에 둘 수도
 * 없어 여기 둔다. ARIA 메뉴 버튼 패턴(aria-haspopup/aria-expanded, role=menu/menuitem)을 따르고,
 * 열리면 첫 항목에 포커스, ↑/↓로 이동, Esc·바깥 클릭으로 닫힌다(Esc는 버튼으로 포커스 복귀).
 */
export function UserMenu({ variant }: UserMenuProps) {
  const { user, logout } = useAuth();
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const menuId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const nickname = user?.nickname ?? '';
  const initial = nickname.charAt(0);

  useEffect(() => {
    if (!open) return;
    menuRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, [open]);

  const closeAndFocusButton = () => {
    setOpen(false);
    buttonRef.current?.focus();
  };

  const handleMenuKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const items = [...(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])];
    const index = items.indexOf(document.activeElement as HTMLElement);
    if (event.key === 'Escape') {
      event.preventDefault();
      closeAndFocusButton();
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      items[(index + 1) % items.length]?.focus();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      items[(index - 1 + items.length) % items.length]?.focus();
    } else if (event.key === 'Tab') {
      setOpen(false);
    }
  };

  const handleLogout = async () => {
    setLoggingOut(true);
    await logout();
    setLoggingOut(false);
    setOpen(false);
    showToast('로그아웃되었습니다');
  };

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label={`${nickname || '내'} 계정 메뉴`}
        onClick={() => setOpen((prev) => !prev)}
        className={
          variant === 'desktop'
            ? 'flex items-center gap-2 rounded-full py-1 pr-3 pl-1 hover:bg-[#f7f8fa]'
            : 'flex size-7 items-center justify-center rounded-full bg-brand text-[11px] font-bold text-white'
        }
      >
        {variant === 'desktop' ? (
          <>
            <span className="flex size-7 items-center justify-center rounded-full bg-brand text-[11px] font-bold text-white">
              {initial}
            </span>
            <span className="text-[13px] font-medium text-[#364153]">{nickname}</span>
          </>
        ) : (
          initial
        )}
      </button>

      {open && (
        <div
          ref={menuRef}
          id={menuId}
          role="menu"
          aria-label="계정 메뉴"
          onKeyDown={handleMenuKeyDown}
          className="absolute top-[calc(100%+8px)] right-0 z-40 w-40 rounded-[14px] border border-[#e5e7eb] bg-white py-1.5 shadow-[0_10px_20px_rgba(0,0,0,0.12)]"
        >
          <Link
            to="/my"
            role="menuitem"
            onClick={() => setOpen(false)}
            className="block px-3.5 py-2 text-[13px] text-[#364153] outline-none hover:bg-[#f7f8fa] focus-visible:bg-[#f0f9f7] focus-visible:text-brand"
          >
            마이페이지
          </Link>
          <button
            type="button"
            role="menuitem"
            disabled={loggingOut}
            onClick={handleLogout}
            className="block w-full px-3.5 py-2 text-left text-[13px] text-[#364153] outline-none hover:bg-[#f7f8fa] focus-visible:bg-[#f0f9f7] focus-visible:text-brand disabled:opacity-60"
          >
            로그아웃
          </button>
        </div>
      )}
    </div>
  );
}
