import { useEffect } from 'react';
import { MainLayout } from '../../components/layout/MainLayout';
import { privacySections } from './privacyPolicySections';

/**
 * SCR-LEGAL-01(신규 제안) 개인정보처리방침. `privacyPolicySections.tsx`에 실제 법정 기재사항
 * 13개 항목의 문구를 담아 두고, 이 페이지는 목차(앵커 링크)와 섹션 레이아웃만 책임진다 — 문구만
 * 바꿀 때 이 파일을 건드릴 필요가 없게 분리했다.
 */
export function PrivacyPolicyPage() {
  // 앵커 클릭 시 부드럽게 스크롤되도록 이 페이지에 머무는 동안만 켠다 — 전역 CSS에 걸면 다른
  // 화면의 의도치 않은 스크롤까지 부드럽게 바뀌어 버린다.
  useEffect(() => {
    const root = document.documentElement;
    const previous = root.style.scrollBehavior;
    root.style.scrollBehavior = 'smooth';
    return () => {
      root.style.scrollBehavior = previous;
    };
  }, []);

  return (
    <MainLayout>
      <div className="mx-auto max-w-[840px] px-4 py-10 md:px-8 md:py-14">
        <header className="pb-8">
          <p className="text-[11px] font-semibold tracking-[1.2px] text-brand uppercase">HomeSense</p>
          <h1 className="pt-1.5 text-[26px] font-extrabold tracking-[-0.5px] text-[#101828]">개인정보처리방침</h1>
          <p className="pt-2 text-[13px] text-[#6a7282]">
            HomeSense(이하 &lsquo;회사&rsquo;)는 이용자의 개인정보를 중요시하며, 「개인정보 보호법」 등 관련
            법령을 준수하고 있습니다. 회사는 개인정보처리방침을 통하여 이용자가 제공하는 개인정보가 어떠한
            목적과 방식으로 이용되고 있으며, 개인정보 보호를 위해 어떠한 조치가 취해지고 있는지 알려드립니다.
          </p>
        </header>

        <nav aria-label="목차" className="mb-10 rounded-[16px] border border-[#f3f4f6] bg-white p-5 shadow-[0_1px_1.5px_rgba(0,0,0,0.1),0_1px_1px_rgba(0,0,0,0.1)]">
          <p className="pb-3 text-[13px] font-bold text-[#101828]">목차</p>
          <ul className="flex flex-wrap gap-x-5 gap-y-2">
            {privacySections.map((section) => (
              <li key={section.id}>
                <a href={`#${section.id}`} className="text-[12.5px] text-[#4a5565] hover:text-brand hover:underline">
                  {section.navLabel}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <div className="flex flex-col gap-10">
          {privacySections.map((section) => (
            <section key={section.id} id={section.id} className="scroll-mt-6">
              <h2 className="pb-3 text-[18px] font-extrabold tracking-[-0.3px] text-[#101828]">{section.heading}</h2>
              <div className="flex flex-col gap-3">{section.content}</div>
            </section>
          ))}
        </div>
      </div>
    </MainLayout>
  );
}
