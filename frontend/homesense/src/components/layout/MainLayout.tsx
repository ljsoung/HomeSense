import type { ReactNode } from 'react';
import { BottomTabNav } from './BottomTabNav';
import { Footer } from './Footer';
import { Gnb } from './Gnb';
import { MobileHeader } from './MobileHeader';

/**
 * HOME-01이 이 저장소 최초로 도입하는 레이아웃 — AUTH-01/02의 AuthLayout(중앙 카드, GNB 없음)과
 * 달리 GNB(데스크톱/태블릿)+본문+Footer+하단 탭(모바일)을 갖춘다. 이후 콘텐츠 화면(SRCH-01,
 * DTL-01, MAP-01, MY-01 등)이 이 레이아웃을 그대로 공유한다.
 */
export function MainLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen w-full flex-col bg-[#f7f8fa]">
      <div className="hidden md:block">
        <Gnb />
      </div>
      <div className="md:hidden">
        <MobileHeader />
      </div>
      <div className="flex flex-1 flex-col pb-16 md:pb-0">
        <main className="flex-1">{children}</main>
        <Footer />
      </div>
      <BottomTabNav />
    </div>
  );
}
