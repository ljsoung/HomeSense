import { useCallback, useState } from 'react';
import { useToast } from '../../components/ui/useToast';
import { useAuth } from './useAuth';

/**
 * 사용자 로그아웃 동작 — 헤더 계정 메뉴(UserMenu)와 MY-01 계정 다이얼로그가 같은 경로를 쓴다(로그아웃 경로를 두
 * 벌로 만들지 않는다). 화면 이동은 하지 않는다: 보호 라우트 위에서 로그아웃하면 RequireAuth가 HOME-01로 보내고
 * (`signedOutByUser`), 공개 화면에서는 그 자리에 비로그인으로 남는다.
 */
export function useLogoutAction() {
  const { logout } = useAuth();
  const { showToast } = useToast();
  const [loggingOut, setLoggingOut] = useState(false);

  const logoutWithNotice = useCallback(async () => {
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      setLoggingOut(false);
    }
    showToast('로그아웃되었습니다');
  }, [logout, showToast]);

  return { logoutWithNotice, loggingOut };
}
