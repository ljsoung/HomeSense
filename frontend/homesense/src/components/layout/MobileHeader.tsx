import { Link } from 'react-router-dom';
import { HomeIcon } from '../icons/HomeIcon';
import { useAuth } from '../../features/auth/useAuth';

/**
 * 모바일 헤더(md 미만) — Figma 모바일 로그인(24:6736) 헤더 우측에는 아바타 옆에 아이콘이 하나 더
 * 있었지만 그 글리프를 원본 SVG로 확인한 적이 없었다(추정으로 벨 아이콘을 그려 넣었었다). 이후
 * UI정의서 2.3/4.1/4.2절(모바일 알림 진입점은 GNB 벨이 아니라 하단 탭 "마이" 아이콘의 배지여야
 * 하고, 하단 탭 5개를 넘기지 않기 위해 알림을 별도 탭으로 두지 않는다는 명시적 설계)을 근거로
 * 이 헤더 벨을 완전히 제거했다 — 확인 안 된 아이콘을 추측으로 유지하는 것과, 스펙에 없는 진입점을
 * 만드는 것 두 가지 문제를 한 번에 해소한다(CLAUDE.md SCR-HOME-01 절 판단 기록 참고). "마이" 탭
 * 배지 자체는 API-NTF-01에 미읽음 카운트 전용 엔드포인트가 없어 이번 범위에서 구현하지 않았다 —
 * 완결 필요.
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
          <Link to="/my" className="flex size-7 items-center justify-center rounded-full bg-brand text-[11px] font-bold text-white">
            {(user?.nickname ?? ' ').charAt(0)}
          </Link>
        ) : (
          <Link to="/login" className="text-[13.5px] font-semibold text-brand">
            로그인
          </Link>
        )}
      </div>
    </header>
  );
}
