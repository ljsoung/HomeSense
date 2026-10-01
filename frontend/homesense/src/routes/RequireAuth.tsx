import { useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Spinner } from '../components/ui/Spinner';
import { useAuth } from '../features/auth/useAuth';
import { assertNever } from '../lib/assertNever';

/**
 * 로그인이 필요한 화면을 감싸는 가드.
 * - `checking`: 리다이렉트하지 않고 기다린다 — 확인 중인 로그인 사용자를 로그인 화면으로 보내지 않는다. 화면이
 *   자기 모양의 자리 표시(스켈레톤)를 `checkingFallback`으로 넘기면 그것을, 없으면 스피너를 보인다.
 * - `anonymous`: AUTH-01로 보내고 `location.state.from`에 원래 위치를 남긴다(로그인 후 `getRedirectPath`로 복귀).
 *   사용 중 재발급이 세션 종료로 끝나 비로그인으로 바뀐 경우도 여기서 로그인 화면으로 간다 — 인터셉터는
 *   화면 이동을 하지 않는다.
 *   예외: 이 화면을 보던 중 사용자가 직접 로그아웃·탈퇴했으면(`signedOutByUser`) HOME-01로 보낸다. 로그아웃한
 *   사람을 "다시 로그인하면 돌아온다"는 로그인 화면으로 보내지 않기 위해서다(MY-01, 헤더 계정 메뉴 모두 해당).
 *   이미 비로그인인 채로 들어온 경우(예: 예전에 로그아웃한 뒤 하단 탭 "마이")는 로그인 화면으로 보낸다 — 이
 *   가드가 로그인 상태를 본 적이 있는지로 구분한다.
 * - `authenticated`: 화면을 그린다.
 *
 * 관리자 권한 확인은 이 가드의 범위 밖이다.
 */
export function RequireAuth({ children, checkingFallback }: { children: ReactNode; checkingFallback?: ReactNode }) {
  const { status, signedOutByUser } = useAuth();
  const location = useLocation();
  const [sawAuthenticated, setSawAuthenticated] = useState(status === 'authenticated');
  if (status === 'authenticated' && !sawAuthenticated) {
    setSawAuthenticated(true);
  }

  switch (status) {
    case 'checking':
      if (checkingFallback !== undefined) return <>{checkingFallback}</>;
      return (
        <div role="status" aria-label="로그인 상태 확인 중" className="flex min-h-[50vh] items-center justify-center">
          <Spinner />
        </div>
      );
    case 'anonymous':
      if (signedOutByUser && sawAuthenticated) {
        return <Navigate to="/" replace />;
      }
      return <Navigate to="/login" replace state={{ from: location }} />;
    case 'authenticated':
      return <>{children}</>;
    default:
      return assertNever(status);
  }
}
