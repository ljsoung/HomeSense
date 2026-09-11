import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { PlaceholderPage } from '../pages/PlaceholderPage';
import { LoginPage } from '../pages/auth/LoginPage';
import { SignupPage } from '../pages/auth/SignupPage';

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<PlaceholderPage programId="HOME-01" title="홈" />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/forgot-password" element={<PlaceholderPage programId="AUTH-03" title="비밀번호 찾기" />} />
      </Routes>
    </BrowserRouter>
  );
}
