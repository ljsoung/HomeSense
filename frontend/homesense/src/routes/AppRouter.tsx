import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { PlaceholderPage } from '../pages/PlaceholderPage';
import { LoginPage } from '../pages/auth/LoginPage';

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<PlaceholderPage programId="HOME-01" title="홈" />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<PlaceholderPage programId="AUTH-02" title="회원가입" />} />
        <Route path="/forgot-password" element={<PlaceholderPage programId="AUTH-03" title="비밀번호 찾기" />} />
      </Routes>
    </BrowserRouter>
  );
}
