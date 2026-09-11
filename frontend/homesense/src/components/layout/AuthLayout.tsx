import type { ReactNode } from 'react';
import loginBackground from '../../assets/auth/login-background.jpg';

/**
 * AUTH-01/AUTH-02가 공유하는 배경(항공뷰 사진 + Ken Burns 애니메이션 + 그라디언트)과 카드 셸.
 * 카드 padding(28px 모바일 / 40px 태블릿·데스크탑)은 AUTH-02 Figma(mobile 30:17405, tablet
 * 30:17903, desktop 20:5892)로 확정된 값이다 — AUTH-01 최초 구현 당시엔 이 수치가 없어 24px/640px
 * 브레이크포인트로 추정했었는데, 이번에 확인된 값으로 두 화면 모두 소급 정정했다(md: 768px 기준
 * 28px→40px 전환).
 *
 * 내부 콘텐츠 간 gap/padding은 화면마다 다르므로(로그인은 섹션별 pt-*, 회원가입은 필드 그룹
 * 전체에 균일한 gap) 이 컴포넌트는 카드 자체의 여백까지만 책임지고 내부 레이아웃은 각 페이지에 맡긴다.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative flex min-h-screen w-full items-center justify-center overflow-hidden px-4 py-10">
      <img
        src={loginBackground}
        alt=""
        className="animate-aerial-pan absolute inset-0 h-full w-full object-cover will-change-transform"
      />
      <div className="absolute inset-0 bg-gradient-to-b from-black/55 via-black/38 to-black/60" />

      <div className="relative z-10 flex w-full max-w-[440px] flex-col items-start rounded-[24px] bg-white p-7 shadow-[0_24px_64px_rgba(0,0,0,0.22)] md:p-10">
        {children}
      </div>
    </div>
  );
}
