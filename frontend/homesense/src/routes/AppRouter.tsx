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
        <Route path="/notifications" element={<PlaceholderPage programId="MY-03" title="알림" />} />
      </Routes>
    </BrowserRouter>
  );
}
