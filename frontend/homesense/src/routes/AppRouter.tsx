import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { PlaceholderPage } from '../pages/PlaceholderPage';
import { LoginPage } from '../pages/auth/LoginPage';
import { SignupPage } from '../pages/auth/SignupPage';
import { HomePage } from '../pages/home/HomePage';

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/forgot-password" element={<PlaceholderPage programId="AUTH-03" title="비밀번호 찾기" />} />
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
