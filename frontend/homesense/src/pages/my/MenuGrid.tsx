import { useId, type ReactNode } from 'react';
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

// 한 벌로 렌더하고 배치만 CSS로 바꾼다(반응형 렌더 규칙). 값은 Figma(7:5373·26:15193·26:14966).
// - 모바일: 카드 하나 안의 1열 목록, 행 여백 16/20·간격 14, 아이콘 칸 36/radius 14, 라벨 14/21 SemiBold + chevron.
// - md 이상: 타일 4열(간격 16), 타일마다 카드, 여백 32/16·세로 간격 14, 아이콘 칸 48/radius 16, 라벨 13/18 SemiBold.
const TILE_CLASS =
  'flex w-full items-center gap-3.5 px-5 py-4 text-left hover:bg-[#f7f8fa] md:flex-col md:justify-center md:rounded-[16px] md:border md:border-[#f3f4f6] md:bg-white md:px-4 md:py-8 md:text-center md:shadow-[0_1px_4px_rgba(0,0,0,0.05)]';

type TileTone = 'default' | 'muted';

function TileContent({ icon, label, tone = 'default' }: { icon: ReactNode; label: string; tone?: TileTone }) {
  return (
    <>
      <span
        aria-hidden="true"
        className={`flex size-9 shrink-0 items-center justify-center rounded-[14px] text-brand md:size-12 md:rounded-[16px] ${
          tone === 'muted' ? 'bg-[#f3f4f6]' : 'bg-[#e8f2f0]'
        }`}
      >
        {icon}
      </span>
      {/* 라벨 #1c1c1e는 흰 배경 대비 약 17:1. 로그아웃 타일만 Figma는 #9ca3af인데 흰 배경 대비 약 2.5:1이라 NFR-8(4.5:1)에
          못 미쳐 #6a7282(약 4.8:1)로 올렸다(지성 결정). 아이콘은 장식이라 Figma 색(#9ca3af) 그대로 둔다. */}
      <span
        className={`flex-1 text-[14px] leading-[21px] font-semibold md:flex-none md:text-[13px] md:leading-[18px] ${
          tone === 'muted' ? 'text-[#6a7282]' : 'text-[#1c1c1e]'
        }`}
      >
        {label}
      </span>
      <ChevronRightIcon aria-hidden="true" className="size-4 shrink-0 text-[#99a1af] md:hidden" />
    </>
  );
}

/**
 * MY-01 메뉴 — MY-02/03/04는 이동 링크, "로그아웃·회원탈퇴"는 선택 다이얼로그를 여는 버튼이다.
 * UI정의서 6.2절은 "2열→1열"이라 적지만 이 프로젝트의 "Figma 우선" 규칙에 따라 4열로 둔다(문서와 다른 점, 문서 동기화 필요).
 */
export function MenuGrid({ onOpenAccount }: { onOpenAccount: () => void }) {
  const headingId = useId();
  return (
    <nav aria-labelledby={headingId}>
      <h2
        id={headingId}
        className="mb-2 text-[13px] leading-5 font-bold text-[#99a1af] md:mb-3 md:text-[14px] md:leading-[21px]"
      >
        메뉴
      </h2>
      <ul
        className={`flex flex-col divide-y divide-[#f3f4f6] overflow-hidden md:grid md:grid-cols-4 md:gap-4 md:divide-y-0 md:overflow-visible md:rounded-none md:border-0 md:bg-transparent md:shadow-none ${CARD_CLASS}`}
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
            <TileContent icon={<LogOutIcon className="size-5 text-[#9ca3af]" />} label="로그아웃·회원탈퇴" tone="muted" />
          </button>
        </li>
      </ul>
    </nav>
  );
}
