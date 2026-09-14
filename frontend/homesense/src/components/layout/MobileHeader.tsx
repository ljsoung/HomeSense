import { Link } from 'react-router-dom';
import { BellIcon } from '../icons/BellIcon';
import { HomeIcon } from '../icons/HomeIcon';
import { useAuth } from '../../features/auth/useAuth';

/**
 * 모바일 헤더(md 미만) — Figma 모바일 로그인(24:6736) 헤더는 우측에 아바타(이름 없이 원형만)+벨
 * 아이콘 두 개를 보여주지만, 모바일 비로그인(24:7370) 헤더 우측은 아이콘 하나뿐이라 정확한
 * 글리프를 원본 SVG로 확인하지 못했다(해당 프레임은 download_assets 호출 대상이 아니었음) —
 * 데스크톱 비로그인 GNB와 기능적으로 동일하도록 "로그인" 텍스트 링크로 대체했다(추측 아이콘을
 * 임의로 그리지 않기 위한 판단, CLAUDE.md SCR-HOME-01 절 참고).
 */
export function MobileHeader() {
  const { isAuthenticated, user } = useAuth();

  return (
    <header className="border-b border-[#e5e7eb] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.06)]">
      <div className="flex h-[60px] items-center justify-between px-4">
        <Link to="/" className="flex items-center gap-2">
          <div className="flex size-[30px] items-center justify-center rounded-[10px] bg-brand">
            <HomeIcon className="size-[15px]" />
          </div>
          <p className="text-[17px] font-extrabold tracking-[-0.4px] text-brand">HomeSense</p>
        </Link>

        {isAuthenticated ? (
          <div className="flex items-center gap-1.5">
            <Link to="/my" className="flex size-7 items-center justify-center rounded-full bg-brand text-[11px] font-bold text-white">
              {(user?.nickname ?? ' ').charAt(0)}
            </Link>
            <Link to="/notifications" aria-label="알림" className="flex size-9 items-center justify-center text-[#4a5565]">
              <BellIcon className="size-5" />
            </Link>
          </div>
        ) : (
          <Link to="/login" className="text-[13.5px] font-semibold text-brand">
            로그인
          </Link>
        )}
      </div>
    </header>
  );
}
