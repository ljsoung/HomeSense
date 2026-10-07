import { useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeftIcon } from '../icons/ArrowLeftIcon';
import { MY_ROUTES } from '../../routes/paths';

/**
 * 마이페이지 하위 화면의 모바일·태블릿 앱 바(MY-02 Figma 27-15395에서 뽑아 MY-03과 같이 쓴다) — 48px, 흰색, 아래 구분선,
 * 뒤로가기 32×32 + 제목 16px Bold(h1). 공용 모바일 헤더(MainLayout)는 그대로 두고 그 아래에 둔다.
 * 뒤로가기는 앱 안에서 들어왔으면 이전 화면, 링크로 바로 들어왔으면 `fallback`(기본 마이페이지 — DTL-01 뒤로가기와 같은 규칙).
 */
export function MobileAppBar({ title, fallback = MY_ROUTES.home }: { title: string; fallback?: string }) {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <div className="flex h-12 items-center gap-1 border-b border-[#f3f4f6] bg-white px-4">
      <button
        type="button"
        aria-label="뒤로 가기"
        onClick={() => (location.key === 'default' ? navigate(fallback) : navigate(-1))}
        className="-ml-1.5 flex size-8 items-center justify-center rounded-[8px] text-[#4a5565] hover:bg-[#f3f4f6] focus-visible:outline-2 focus-visible:outline-brand"
      >
        <ArrowLeftIcon aria-hidden="true" className="size-5" />
      </button>
      <h1 className="text-[16px] leading-6 font-bold text-[#101828]">{title}</h1>
    </div>
  );
}
