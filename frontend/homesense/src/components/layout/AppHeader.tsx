import { Link, useLocation } from 'react-router-dom';
import { HomeIcon } from '../icons/HomeIcon';
import type { AuthStatus } from '../../features/auth/authContext';
import { useAuth } from '../../features/auth/useAuth';
import { assertNever } from '../../lib/assertNever';
import { UserMenu } from './UserMenu';

// `/`(HOME-01)에서도 "지역·단지 검색"을 활성으로 표시한다 — Figma 데스크톱 로그인(3:2)/비로그인
// (4:1232) 두 프레임 모두 홈 화면에서 이 탭을 활성 상태로 그린다(홈 히어로 자체가 검색 진입점).
// 단지 상세(`/complexes/*`, DTL-01)도 검색에서 이어지는 화면이라 같은 탭을 활성으로 둔다(UI정의서 4.1절).
const NAV_LINKS: { to: string; label: string; alsoActiveOn?: string[]; alsoActivePrefix?: string }[] = [
  { to: '/search', label: '지역·단지 검색', alsoActiveOn: ['/'], alsoActivePrefix: '/complexes/' },
  { to: '/map', label: '지도로 보기' },
  { to: '/favorites', label: '관심목록' },
  { to: '/notifications', label: '알림' },
];

/**
 * UIC-01 GNB와 모바일 헤더 — 트리 하나다(CLAUDE.md "반응형 렌더 규칙"). 예전엔 데스크톱 GNB와 모바일 헤더를 둘 다
 * 렌더하고 CSS로 한쪽을 숨겨, 헤더·계정 메뉴가 DOM에 두 벌 있었다. 화면 크기에 따라 달라지는 것은 클래스뿐이고,
 * 한쪽에만 있는 요소(중앙 메뉴·회원가입 버튼·닉네임)는 그쪽에서만 보인다 — 같은 내용의 두 번째 사본은 없다.
 *
 * 중앙 메뉴는 데스크톱·태블릿(md 이상)에만 있고 모바일은 하단 탭(UIC-02)이 대신한다. 우측 계정 영역은 로그인이면
 * 아바타(+md 이상 닉네임) 메뉴(UserMenu), 비로그인이면 로그인(+md 이상 회원가입), 확인 중이면 자리 표시다.
 *
 * 알림 진입점은 중앙 메뉴의 "알림" 링크(`/notifications` → MY-04 알림 이력) 하나다. 헤더 벨 아이콘은 두지 않는다 —
 * UI정의서 2.3/4.1절 GNB 구성(로고 / 주메뉴 / 우측 계정 영역)에 없고, 모바일 알림 진입점은 하단 탭 "마이"의 배지로
 * 정의돼 있다(4.2절, 배지는 미읽음 개수 API가 없어 미구현 — CLAUDE.md SCR-HOME-01 절).
 */
export function AppHeader() {
  const { status } = useAuth();
  const location = useLocation();

  return (
    <header className="border-b border-[#e5e7eb] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.06)]">
      <div className="mx-auto flex h-[60px] max-w-[1280px] items-center justify-between gap-4 px-4 md:px-8">
        <Link to="/" className="flex shrink-0 items-center gap-2">
          <div className="flex size-[30px] items-center justify-center rounded-[10px] bg-brand">
            <HomeIcon className="size-[15px]" />
          </div>
          <p className="text-[17px] font-extrabold tracking-[-0.4px] text-brand">HomeSense</p>
        </Link>

        <nav aria-label="주 메뉴" className="hidden flex-1 items-center justify-center gap-0.5 md:flex">
          {NAV_LINKS.map((link) => {
            const active =
              location.pathname === link.to ||
              (link.alsoActiveOn?.includes(location.pathname) ?? false) ||
              (link.alsoActivePrefix !== undefined && location.pathname.startsWith(link.alsoActivePrefix));
            return (
              <Link
                key={link.to}
                to={link.to}
                className={`rounded-[10px] px-3.5 py-2 text-[13.5px] font-medium transition-colors ${
                  active ? 'bg-[#e8f2f0] text-brand' : 'text-[#4a5565] hover:bg-[#f7f8fa]'
                }`}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>

        <div className="flex shrink-0 items-center gap-1.5" aria-busy={status === 'checking'}>
          <AccountArea status={status} />
        </div>
      </div>
    </header>
  );
}

/**
 * 우측 계정 영역. 세션 확인(getMe/refresh) 중에는 로그인 버튼도 계정 메뉴도 아닌 자리 표시만 둔다 — 예전엔 토큰이 있다는
 * 것만으로 빈 이니셜 아바타를 그려, 만료된 토큰만 남은 비로그인 사용자가 로그인된 것처럼 보였다. 자리 표시 크기는 비로그인
 * 영역에 맞춘다 — md 이상은 로그인·회원가입(실측 144×32px, 중앙 메뉴가 밀리지 않게), 모바일은 로그인 링크 35×20px과
 * 아바타 28px 중 큰 쪽.
 */
function AccountArea({ status }: { status: AuthStatus }) {
  switch (status) {
    case 'checking':
      return <span data-testid="account-placeholder" aria-hidden="true" className="block h-7 w-[35px] md:h-8 md:w-[144px]" />;
    case 'authenticated':
      return <UserMenu />;
    case 'anonymous':
      return (
        <>
          <Link
            to="/login"
            className="text-[13.5px] font-semibold text-brand md:rounded-[10px] md:px-3 md:py-1.5 md:font-medium md:text-[#364153] md:hover:bg-[#f7f8fa]"
          >
            로그인
          </Link>
          <Link
            to="/signup"
            className="hidden rounded-[10px] bg-brand px-4 py-1.5 text-[13.5px] font-semibold text-white hover:bg-[#0d4f48] md:inline-block"
          >
            회원가입
          </Link>
        </>
      );
    default:
      return assertNever(status);
  }
}
