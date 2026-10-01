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

function MyPageFrame({ children }: { children: ReactNode }) {
  return (
    <MainLayout>
      <div className="mx-auto flex max-w-[1280px] flex-col gap-4 px-4 py-5 md:gap-5 md:px-8 md:py-8">
        <h1 className="sr-only">마이페이지</h1>
        {children}
      </div>
    </MainLayout>
  );
}

/** 위젯 두 개 — 데스크톱(1280 이상)은 나란히, 그 아래는 세로로 쌓는다(CSS로만, 한 벌 렌더). */
function WidgetRow({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-1 gap-4 md:gap-5 xl:grid-cols-2">{children}</div>;
}

/** 로그인 상태 확인 중(`checking`)에 보호 라우트 가드가 보여 주는 자리 표시 — 리다이렉트하지 않는다. */
export function MyPageSkeleton() {
  return (
    <MyPageFrame>
      <div role="status" aria-label="로그인 상태 확인 중" className="flex flex-col gap-4 md:gap-5">
        <ProfileCard state={LOADING} />
        <div aria-hidden="true" className="h-[220px] animate-pulse rounded-[16px] bg-[#eef0f3] md:h-[112px]" />
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
