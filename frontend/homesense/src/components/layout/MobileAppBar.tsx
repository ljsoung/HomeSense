import { ArrowLeftIcon } from '../icons/ArrowLeftIcon';
import { MY_ROUTES } from '../../routes/paths';
import { useGoBack } from './useGoBack';

/**
 * 마이페이지 하위 화면의 모바일·태블릿 앱 바(MY-02·MY-03이 같이 쓴다) — 52px, 흰색, 아래 구분선,
 * 뒤로가기 32×32 + 제목 16px Bold(h1). 공용 모바일 헤더(MainLayout)는 그대로 두고 그 아래에 둔다.
 * 높이 52px은 MY-03·MY-04·MY-05 Figma 값이다. MY-02 모바일 Figma(27-15395)만 48px인데 Figma 안의 불일치로 보고 52로 통일했다
 * (2026-10-08, CLAUDE.md "SCR-MY-03" 절).
 * 뒤로가기는 앱 안에서 들어왔으면 이전 화면, 링크로 바로 들어왔으면 `fallback`(기본 마이페이지 — DTL-01 뒤로가기와 같은 규칙).
 */
export function MobileAppBar({
  title,
  fallback = MY_ROUTES.home,
  onBack,
}: {
  title: string;
  fallback?: string;
  /** 주면 뒤로가기를 바로 하지 않고 이 함수에 기본 동작(goBack)을 넘긴다 — MY-05가 저장하지 않은 변경을 확인할 때 쓴다. */
  onBack?: (goBack: () => void) => void;
}) {
  const goBack = useGoBack(fallback);
  return (
    <div className="flex h-[52px] items-center gap-1 border-b border-[#f3f4f6] bg-white px-4">
      <button
        type="button"
        aria-label="뒤로 가기"
        onClick={() => (onBack ? onBack(goBack) : goBack())}
        className="-ml-1.5 flex size-8 items-center justify-center rounded-[8px] text-[#4a5565] hover:bg-[#f3f4f6] focus-visible:outline-2 focus-visible:outline-brand"
      >
        <ArrowLeftIcon aria-hidden="true" className="size-5" />
      </button>
      <h1 className="text-[16px] leading-6 font-bold text-[#101828]">{title}</h1>
    </div>
  );
}

