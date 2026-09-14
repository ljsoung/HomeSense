import { Link, useLocation } from 'react-router-dom';
import { HomeIcon } from '../icons/HomeIcon';
import { useAuth } from '../../features/auth/useAuth';

const NAV_LINKS = [
  { to: '/search', label: '지역·단지 검색' },
  { to: '/map', label: '지도로 보기' },
  { to: '/favorites', label: '관심목록' },
  { to: '/notifications', label: '알림' },
];

/**
 * UIC-01. 데스크톱/태블릿에서 노출되고, 모바일은 BottomTabNav(UIC-02)로 대체된다(md:flex 이하는
 * MainLayout이 숨긴다). 완료 조건 — 로그인/비로그인에 따라 우측 영역이 아바타+닉네임+알림벨
 * (로그인) 또는 로그인/회원가입 버튼(비로그인)으로 갈린다(Figma 데스크톱 로그인 3:2 / 로그아웃
 * 4:1232 노드로 각각 확인). 아바타는 로그아웃 버튼이 아니라 MY-01(마이페이지, 자리표시)로
 * 이동한다 — Figma 정적 목업엔 드롭다운/로그아웃 어포던스가 없어 임의로 만들지 않았다.
 *
 * 알림 진입점은 중앙 네비의 "알림" 텍스트 링크 하나뿐이다(`/notifications` → MY-04 알림 이력 —
 * `NotificationController.getNotifications()`/`NotificationResponse` Javadoc이 명시적으로
 * "MY-04 알림 이력"이라 적어 뒀다, MY-03은 별개 화면인 알림 설정). 한때 이 옆에 별도 벨 아이콘도
 * 있었다 — Figma 데스크톱 로그인 프레임(3:2)이 중앙 네비 텍스트 링크와 별개로 빨간 점 배지가 붙은
 * 벨을 그려 둬서, "픽셀 증거는 있다"는 이유로 완결 필요로 남겨둔 채 유지했었다. 이후 UI정의서
 * 원문(2.3절/4.1절)을 대조한 결과 GNB 구성은 "로고 / 주메뉴(지역·단지 검색·지도로 보기·관심목록·
 * 알림) / 우측 영역(비로그인: 로그인·회원가입, 로그인: 프로필 아이콘)"으로만 정의돼 있고 벨은 전혀
 * 언급되지 않는다는 게 확인돼 제거했다 — CLAUDE.md가 스스로 못박은 "코드와 문서가 어긋나면 문서가
 * 맞다" 원칙대로, Figma 픽셀은 6개 근거 문서(요구사항/엔티티/테이블/UI/프로그램목록/프로그램설계
 * 정의서) 밖의 참고 자료일 뿐이라 문서 쪽을 따랐다(CLAUDE.md SCR-HOME-01 절 판단 기록 참고).
 */
export function Gnb() {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  return (
    <header className="border-b border-[#e5e7eb] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.06)]">
      <div className="mx-auto flex h-[60px] max-w-[1280px] items-center gap-4 px-8">
        <Link to="/" className="flex shrink-0 items-center gap-2">
          <div className="flex size-[30px] items-center justify-center rounded-[10px] bg-brand">
            <HomeIcon className="size-[15px]" />
          </div>
          <p className="text-[17px] font-extrabold tracking-[-0.4px] text-brand">HomeSense</p>
        </Link>

        <nav className="flex flex-1 items-center justify-center gap-0.5">
          {NAV_LINKS.map((link) => {
            const active = location.pathname === link.to;
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

        <div className="flex shrink-0 items-center gap-1.5">
          {isAuthenticated ? (
            <Link to="/my" className="flex items-center gap-2 rounded-full py-1 pr-3 pl-1 hover:bg-[#f7f8fa]">
              <span className="flex size-7 items-center justify-center rounded-full bg-brand text-[11px] font-bold text-white">
                {(user?.nickname ?? ' ').charAt(0)}
              </span>
              <span className="text-[13px] font-medium text-[#364153]">{user?.nickname ?? ''}</span>
            </Link>
          ) : (
            <>
              <Link to="/login" className="rounded-[10px] px-3 py-1.5 text-[13.5px] font-medium text-[#364153] hover:bg-[#f7f8fa]">
                로그인
              </Link>
              <Link
                to="/signup"
                className="rounded-[10px] bg-brand px-4 py-1.5 text-[13.5px] font-semibold text-white hover:bg-[#0d4f48]"
              >
                회원가입
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
