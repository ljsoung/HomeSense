import { useState, type ReactNode } from 'react';
import { MainLayout } from '../../components/layout/MainLayout';
import { AccountDialogs, type AccountDialog } from './AccountDialogs';
import { FavoritePreview } from './FavoritePreview';
import { MenuGrid } from './MenuGrid';
import { loadFavoritePreview, loadProfile, loadRecentNotifications } from './myPageData';
import { NotificationPreview } from './NotificationPreview';
import { ProfileCard, ProfileErrorBanner } from './ProfileCard';
import { useLoadable } from './useLoadable';

const LOADING = { status: 'loading' } as const;

/**
 * 본문 틀(Figma 7:5373·26:15193·26:14966) — 최대 폭 1000(패딩 포함), 패딩 48/32·섹션 간격 28, 모바일 24/16·20.
 * 제목 26/39(모바일 22/33) ExtraBold. Figma 데스크톱·태블릿의 부제(14/21 #99a1af)는 문구를 받지 못해 넣지 않았다.
 */
function MyPageFrame({ children }: { children: ReactNode }) {
  return (
    <MainLayout>
      <div className="mx-auto flex max-w-[1000px] flex-col gap-5 px-4 py-6 md:gap-7 md:px-8 md:py-12">
        <h1 className="text-[22px] leading-[33px] font-extrabold text-[#101828] md:text-[26px] md:leading-[39px]">마이페이지</h1>
        {children}
      </div>
    </MainLayout>
  );
}

/** 위젯 두 개 — 768 이상은 나란히(간격 24), 그 아래는 세로로 쌓는다(CSS로만, 한 벌 렌더). */
function WidgetRow({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-1 gap-5 md:grid-cols-2 md:gap-6">{children}</div>;
}

/** 로그인 상태 확인 중(`checking`)에 보호 라우트 가드가 보여 주는 자리 표시 — 리다이렉트하지 않는다. */
export function MyPageSkeleton() {
  return (
    <MyPageFrame>
      <div role="status" aria-label="로그인 상태 확인 중" className="flex flex-col gap-5 md:gap-7">
        <ProfileCard state={LOADING} />
        <div aria-hidden="true" className="h-[250px] animate-pulse rounded-[16px] bg-[#eef0f3] md:h-[190px]" />
        <WidgetRow>
          <div aria-hidden="true" className="h-[200px] animate-pulse rounded-[16px] bg-[#eef0f3]" />
          <div aria-hidden="true" className="h-[200px] animate-pulse rounded-[16px] bg-[#eef0f3]" />
        </WidgetRow>
      </div>
    </MyPageFrame>
  );
}

/**
 * SCR-MY-01 마이페이지 홈(UI정의서 v2.1 5.5절, FR-1.4). 보호 라우트(RequireAuth) 안에서만 그려진다.
 * 프로필·관심 매물·최근 알림을 동시에 따로 불러, 한 영역의 실패가 다른 영역을 막지 않는다.
 */
export function MyPage() {
  const profile = useLoadable(loadProfile);
  const favorites = useLoadable(loadFavoritePreview);
  const notifications = useLoadable(loadRecentNotifications);
  const [dialog, setDialog] = useState<AccountDialog>(null);

  return (
    <MyPageFrame>
      <ProfileErrorBanner state={profile.state} onRetry={profile.retry} />
      <ProfileCard state={profile.state} />
      <MenuGrid onOpenAccount={() => setDialog('choice')} />
      <WidgetRow>
        <FavoritePreview state={favorites.state} onRetry={favorites.retry} />
        <NotificationPreview state={notifications.state} onRetry={notifications.retry} />
      </WidgetRow>
      <AccountDialogs dialog={dialog} onChange={setDialog} />
    </MyPageFrame>
  );
}
