import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Spinner } from '../components/ui/Spinner';
import { useAuth } from '../features/auth/useAuth';
import { assertNever } from '../lib/assertNever';

/**
 * 로그인이 필요한 화면을 감싸는 가드.
 * - `checking`: 리다이렉트하지 않고 기다린다 — 확인 중인 로그인 사용자를 로그인 화면으로 보내지 않는다.
 * - `anonymous`: AUTH-01로 보내고 `location.state.from`에 원래 위치를 남긴다(로그인 후 `getRedirectPath`로 복귀).
 *   사용 중 재발급이 세션 종료로 끝나 비로그인으로 바뀐 경우도 여기서 로그인 화면으로 간다 — 인터셉터는
 *   화면 이동을 하지 않는다.
 * - `authenticated`: 화면을 그린다.
 *
 * 관리자 권한 확인은 이 가드의 범위 밖이다. 2026-09-29 기준 인증이 필요한 화면(MY-01·02·04 등)은 아직
 * 플레이스홀더라 라우트에 적용하지 않았다 — 첫 보호 화면을 만들 때 그 라우트를 감싼다(CLAUDE.md "인증 상태 3종" 절).
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();
  switch (status) {
    case 'checking':
      return (
        <div role="status" aria-label="로그인 상태 확인 중" className="flex min-h-[50vh] items-center justify-center">
          <Spinner />
        </div>
      );
    case 'anonymous':
      return <Navigate to="/login" replace state={{ from: location }} />;
    case 'authenticated':
      return <>{children}</>;
    default:
      return assertNever(status);
  }
}
