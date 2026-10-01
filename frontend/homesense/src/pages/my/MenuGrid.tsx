import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { BellRingIcon } from '../../components/icons/BellRingIcon';
import { ChevronRightIcon } from '../../components/icons/ChevronRightIcon';
import { LogOutIcon } from '../../components/icons/LogOutIcon';
import { MenuBellIcon } from '../../components/icons/MenuBellIcon';
import { MenuHeartIcon } from '../../components/icons/MenuHeartIcon';
import { MY_ROUTES } from '../../routes/paths';
import { CARD_CLASS } from './SectionCard';

// 아이콘은 Figma(7:5373) SVG. 색은 currentColor라 타일 아이콘 칸의 text-brand(Primary)를 따른다.
const MENU_ITEMS = [
  { to: MY_ROUTES.favorites, label: '관심 매물·지역 관리', icon: <MenuHeartIcon className="size-5" /> },
  { to: MY_ROUTES.notificationSettings, label: '알림 설정', icon: <MenuBellIcon className="size-5" /> },
  { to: MY_ROUTES.notifications, label: '알림 이력', icon: <BellRingIcon className="size-5" /> },
] as const;

// 한 벌로 렌더하고 배치만 CSS로 바꾼다(반응형 렌더 규칙) — 모바일: 아이콘+라벨+chevron 가로 행, md 이상: 아이콘 위·라벨
// 아래 세로 타일. 텍스트 색 #364153은 흰 배경 대비 약 10:1(NFR-8 4.5:1 이상).
const TILE_CLASS =
  'flex w-full items-center gap-3 px-4 py-3.5 text-left text-[14px] font-semibold text-[#364153] hover:bg-[#f7f8fa] md:flex-col md:justify-center md:gap-2.5 md:rounded-[16px] md:border md:border-[#f3f4f6] md:bg-white md:px-3 md:py-5 md:text-center md:shadow-[0_1px_1.5px_rgba(0,0,0,0.1),0_1px_1px_rgba(0,0,0,0.1)]';

function TileContent({ icon, label }: { icon: ReactNode; label: string }) {
  return (
    <>
      <span
        aria-hidden="true"
        className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-[#f0f9f7] text-brand md:size-11 md:rounded-[12px]"
      >
        {icon}
      </span>
      <span className="flex-1 md:flex-none">{label}</span>
      <ChevronRightIcon aria-hidden="true" className="size-4 shrink-0 text-[#99a1af] md:hidden" />
    </>
  );
}

/**
 * MY-01 메뉴 — MY-02/03/04는 이동 링크, "로그아웃·회원탈퇴"는 선택 다이얼로그를 여는 버튼이다.
 * 데스크톱·태블릿 4열 타일, 모바일 1열 목록은 Figma(7:5373, 26:15193, 26:14966)를 따른다. UI정의서 6.2절은
 * "2열→1열"이라 적지만 이 프로젝트의 "Figma 우선" 규칙에 따라 4열로 둔다(문서와 다른 점, 문서 동기화 필요).
 */
export function MenuGrid({ onOpenAccount }: { onOpenAccount: () => void }) {
  return (
    <nav aria-label="마이페이지 메뉴">
      <ul
        className={`flex flex-col divide-y divide-[#f3f4f6] overflow-hidden md:grid md:grid-cols-4 md:gap-3 md:divide-y-0 md:overflow-visible md:rounded-none md:border-0 md:bg-transparent md:shadow-none ${CARD_CLASS}`}
      >
        {MENU_ITEMS.map((item) => (
          <li key={item.to}>
            <Link to={item.to} className={TILE_CLASS}>
              <TileContent icon={item.icon} label={item.label} />
            </Link>
          </li>
        ))}
        <li>
          <button type="button" aria-haspopup="dialog" onClick={onOpenAccount} className={TILE_CLASS}>
            {/* Figma는 계정 타일 아이콘만 흐린 회색(#9ca3af — 색 토큰이 없어 하단 탭 비활성과 같은 값을 쓴다).
                아이콘은 장식이고 라벨 글자는 다른 타일과 같은 #364153이라 대비 기준(NFR-8)과 무관하다. */}
            <TileContent icon={<LogOutIcon className="size-5 text-[#9ca3af]" />} label="로그아웃·회원탈퇴" />
          </button>
        </li>
      </ul>
    </nav>
  );
}
