import { lazy, Suspense, type ReactNode } from 'react';
import { BrowserRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AppErrorBoundary } from '../components/ui/AppErrorBoundary';
import { ComplexDetailRoute } from '../pages/complex/ComplexDetailPage';
import { LoginPage } from '../pages/auth/LoginPage';
import { MapPage } from '../pages/map/MapPage';
import { PasswordResetPage } from '../pages/auth/PasswordResetPage';
import { SignupPage } from '../pages/auth/SignupPage';
import { FavoritesPage } from '../pages/favorites/FavoritesPage';
import { HomePage } from '../pages/home/HomePage';
import { PrivacyPolicyPage } from '../pages/legal/PrivacyPolicyPage';
import { MyPage, MyPageSkeleton } from '../pages/my/MyPage';
import { NotificationHistoryPage } from '../pages/notifications/NotificationHistoryPage';
import { ProfileEditPage } from '../pages/profileEdit/ProfileEditPage';
import { NotificationSettingsPage } from '../pages/notificationSettings/NotificationSettingsPage';
import { SearchResultsPage } from '../pages/search/SearchResultsPage';
import { AUTH_ROUTES, MY_ROUTES } from './paths';
import { RequireAuth } from './RequireAuth';

// e2e 전용 렌더 오류 화면 — 개발 서버에서만 등록한다. 운영 빌드는 import.meta.env.DEV가 false라 이 동적 import가 빠진다.
const E2eCrash = import.meta.env.DEV ? lazy(() => import('./devOnly/E2eCrash')) : null;

/** 화면 오류 바운더리 — 라우트가 바뀌면(뒤로 가기 등) 오류 상태를 푼다. */
function RouteErrorBoundary({ children }: { children: ReactNode }) {
  const location = useLocation();
  return <AppErrorBoundary resetKey={location.key}>{children}</AppErrorBoundary>;
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <RouteErrorBoundary>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path={AUTH_ROUTES.login} element={<LoginPage />} />
          <Route path={AUTH_ROUTES.signup} element={<SignupPage />} />
          <Route path="/privacy" element={<PrivacyPolicyPage />} />
          {/* 백엔드가 발송하는 재설정 링크가 {baseUrl}/password-reset?token=... 형식으로 고정돼 있다
              (AUTH-03 프론트 프롬프트 1절) — 이 경로를 바꾸면 이미 발송된 메일의 링크가 깨진다. */}
          <Route path={AUTH_ROUTES.passwordReset} element={<PasswordResetPage />} />
          <Route path="/search" element={<SearchResultsPage />} />
          <Route path="/complexes/:id" element={<ComplexDetailRoute />} />
          <Route path="/map" element={<MapPage />} />
          <Route
            path={MY_ROUTES.home}
            element={
              <RequireAuth checkingFallback={<MyPageSkeleton />}>
                <MyPage />
              </RequireAuth>
            }
          />
          <Route
            path={MY_ROUTES.favorites}
            element={
              <RequireAuth>
                <FavoritesPage />
              </RequireAuth>
            }
          />
          {/* MY-03 알림 설정 — MY-02 알림조건 배지가 `?favoritePropertyId={id}`로 들어와 그 대상을 미리 선택한다(`?favoriteRegionId=`도
              받는다). MY-04·05는 아직 준비 중 — MY-01 링크가 끊기지 않게 보호 라우트 아래 자리 표시를 둔다. */}
          <Route
            path={MY_ROUTES.notificationSettings}
            element={
              <RequireAuth>
                <NotificationSettingsPage />
              </RequireAuth>
            }
          />
          {/* NotificationController.getNotifications()/NotificationResponse Javadoc이 "MY-04 알림
              이력"이라고 명시한다 — MY-03은 별개 화면(알림 설정, GET/PUT /api/notifications/settings).
              처음엔 이 구분을 확인하지 않고 MY-03으로 잘못 연결했었다(CLAUDE.md SCR-HOME-01 절 참고). */}
          <Route
            path={MY_ROUTES.notifications}
            element={
              <RequireAuth>
                <NotificationHistoryPage />
              </RequireAuth>
            }
          />
          <Route
            path={MY_ROUTES.profileEdit}
            element={
              <RequireAuth>
                <ProfileEditPage />
              </RequireAuth>
            }
          />
          {E2eCrash && (
            <Route
              path="/__e2e/crash"
              element={
                <Suspense fallback={null}>
                  <E2eCrash />
                </Suspense>
              }
            />
          )}
        </Routes>
      </RouteErrorBoundary>
    </BrowserRouter>
  );
}
