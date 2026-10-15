import type { ReactNode } from 'react';
import { BottomTabNav } from './BottomTabNav';
import { Footer } from './Footer';
import { Gnb } from './Gnb';
import { MobileHeader } from './MobileHeader';

interface MainLayoutProps {
  children: ReactNode;
  /**
   * 화면 높이를 꽉 채우고 공통 푸터를 그리지 않는다 — MAP-01(지도)용. Figma 6:4306·26:14574·26:14318에 푸터가 없다.
   * 이 레이아웃을 쓰는 화면은 UI정의서 7.4절 데이터 출처 문구를 화면 안 다른 곳에 직접 보여야 한다.
   */
  fullHeight?: boolean;
}

/**
 * HOME-01이 이 저장소 최초로 도입하는 레이아웃 — AUTH-01/02의 AuthLayout(중앙 카드, GNB 없음)과
 * 달리 GNB(데스크톱/태블릿)+본문+Footer+하단 탭(모바일)을 갖춘다. 이후 콘텐츠 화면(SRCH-01,
 * DTL-01, MAP-01, MY-01 등)이 이 레이아웃을 그대로 공유한다.
 */
export function MainLayout({ children, fullHeight = false }: MainLayoutProps) {
  if (fullHeight) {
    return (
      <div className="flex h-dvh w-full flex-col overflow-hidden bg-[#f7f8fa]">
        <div className="hidden md:block">
          <Gnb />
        </div>
        <div className="md:hidden">
          <MobileHeader />
        </div>
        <main className="relative min-h-0 flex-1 pb-16 md:pb-0">{children}</main>
        <BottomTabNav />
      </div>
    );
  }

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
