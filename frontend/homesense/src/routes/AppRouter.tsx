import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { PlaceholderPage } from '../pages/PlaceholderPage';
import { ComplexDetailRoute } from '../pages/complex/ComplexDetailPage';
import { LoginPage } from '../pages/auth/LoginPage';
import { PasswordResetPage } from '../pages/auth/PasswordResetPage';
import { SignupPage } from '../pages/auth/SignupPage';
import { HomePage } from '../pages/home/HomePage';
import { PrivacyPolicyPage } from '../pages/legal/PrivacyPolicyPage';
import { MyPage, MyPageSkeleton } from '../pages/my/MyPage';
import { MyPreparingPage } from '../pages/my/MyPreparingPage';
import { SearchResultsPage } from '../pages/search/SearchResultsPage';
import { MY_ROUTES } from './paths';
import { RequireAuth } from './RequireAuth';

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/privacy" element={<PrivacyPolicyPage />} />
        {/* 백엔드가 발송하는 재설정 링크가 {baseUrl}/password-reset?token=... 형식으로 고정돼 있다
            (AUTH-03 프론트 프롬프트 1절) — 이 경로를 바꾸면 이미 발송된 메일의 링크가 깨진다. */}
        <Route path="/password-reset" element={<PasswordResetPage />} />
        <Route path="/search" element={<SearchResultsPage />} />
        <Route path="/complexes/:id" element={<ComplexDetailRoute />} />
        <Route path="/map" element={<PlaceholderPage programId="MAP-01" title="지도로 보기" />} />
        <Route
          path={MY_ROUTES.home}
          element={
            <RequireAuth checkingFallback={<MyPageSkeleton />}>
              <MyPage />
            </RequireAuth>
          }
        />
        {/* MY-02~05는 아직 준비 중 — MY-01 메뉴 링크가 끊기지 않게 보호 라우트 아래 자리 표시를 둔다. */}
        <Route
          path={MY_ROUTES.favorites}
          element={
            <RequireAuth>
              <MyPreparingPage programId="MY-02" title="관심 매물·지역 관리" />
            </RequireAuth>
          }
        />
        <Route
          path={MY_ROUTES.notificationSettings}
          element={
            <RequireAuth>
              <MyPreparingPage programId="MY-03" title="알림 설정" />
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
              <MyPreparingPage programId="MY-04" title="알림 이력" />
            </RequireAuth>
          }
        />
        <Route
          path={MY_ROUTES.profileEdit}
          element={
            <RequireAuth>
              <MyPreparingPage programId="MY-05" title="회원정보 수정" />
            </RequireAuth>
          }
        />
      </Routes>
    </BrowserRouter>
  );
}
