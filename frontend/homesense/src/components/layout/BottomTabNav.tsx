import { Link, useLocation } from 'react-router-dom';
import { NavFavoritesIcon } from '../icons/NavFavoritesIcon';
import { NavHomeIcon } from '../icons/NavHomeIcon';
import { NavMapIcon } from '../icons/NavMapIcon';
import { NavMyIcon } from '../icons/NavMyIcon';
import { NavSearchIcon } from '../icons/NavSearchIcon';

const TABS = [
  { to: '/', label: '홈', Icon: NavHomeIcon },
  { to: '/search', label: '검색', Icon: NavSearchIcon },
  { to: '/map', label: '지도', Icon: NavMapIcon },
  { to: '/favorites', label: '찜', Icon: NavFavoritesIcon },
  { to: '/my', label: '마이', Icon: NavMyIcon },
] as const;

/**
 * UIC-02. 모바일 전용(md 이상은 MainLayout이 숨기고 Gnb로 대체). Figma가 활성 탭(홈)만
 * strokeWidth 2.29/브랜드색, 비활성 탭은 1.65/회색으로 캡처해뒀다 — 실제로는 라우트에 따라
 * 어느 탭이든 활성화될 수 있어 strokeWidth·색상을 currentColor/prop으로 동적으로 바꾼다.
 */
export function BottomTabNav() {
  const location = useLocation();

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 flex border-t border-[#e5e7eb] bg-white pb-[env(safe-area-inset-bottom)] md:hidden">
      {TABS.map(({ to, label, Icon }) => {
        const active = to === '/' ? location.pathname === '/' : location.pathname.startsWith(to);
        return (
          <Link
            key={to}
            to={to}
            className={`flex flex-1 flex-col items-center gap-0.5 py-2 text-[10.5px] font-medium ${
              active ? 'text-brand' : 'text-[#9ca3af]'
            }`}
          >
            <Icon strokeWidth={active ? 2.3 : 1.65} className="size-[22px]" />
            {label}
          </Link>
        );
      })}
    </nav>
  );
}
