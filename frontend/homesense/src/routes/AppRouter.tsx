import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { PlaceholderPage } from '../pages/PlaceholderPage';
import { LoginPage } from '../pages/auth/LoginPage';
import { PasswordResetPage } from '../pages/auth/PasswordResetPage';
import { SignupPage } from '../pages/auth/SignupPage';
import { HomePage } from '../pages/home/HomePage';
import { PrivacyPolicyPage } from '../pages/legal/PrivacyPolicyPage';

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
        <Route path="/search" element={<PlaceholderPage programId="SRCH-01" title="지역·단지 검색" />} />
        <Route path="/complexes/:id" element={<PlaceholderPage programId="DTL-01" title="단지 상세" />} />
        <Route path="/map" element={<PlaceholderPage programId="MAP-01" title="지도로 보기" />} />
        <Route path="/my" element={<PlaceholderPage programId="MY-01" title="마이페이지" />} />
        <Route path="/favorites" element={<PlaceholderPage programId="MY-02" title="관심목록" />} />
        {/* NotificationController.getNotifications()/NotificationResponse Javadoc이 "MY-04 알림
            이력"이라고 명시한다 — MY-03은 별개 화면(알림 설정, GET/PUT /api/notifications/settings).
            처음엔 이 구분을 확인하지 않고 MY-03으로 잘못 연결했었다(CLAUDE.md SCR-HOME-01 절 참고). */}
        <Route path="/notifications" element={<PlaceholderPage programId="MY-04" title="알림 이력" />} />
      </Routes>
    </BrowserRouter>
  );
}
