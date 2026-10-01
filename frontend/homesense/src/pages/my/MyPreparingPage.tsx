import { Link } from 'react-router-dom';
import { ArrowLeftIcon } from '../../components/icons/ArrowLeftIcon';
import { MainLayout } from '../../components/layout/MainLayout';
import { MY_ROUTES } from '../../routes/paths';

interface MyPreparingPageProps {
  title: string;
  programId: string;
}

/**
 * MY-02~05의 "준비 중" 자리 표시. MY-01 메뉴 링크가 404나 빈 화면으로 끝나지 않게 보호 라우트 아래에 둔다.
 * 각 화면을 구현하면 이 자리 표시를 실제 화면으로 바꾼다.
 */
export function MyPreparingPage({ title, programId }: MyPreparingPageProps) {
  return (
    <MainLayout>
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-2 px-4 py-16 text-center md:px-8">
        <p className="text-[13px] font-semibold text-[#99a1af]">{programId}</p>
        <h1 className="text-[22px] font-extrabold text-[#101828]">{title}</h1>
        <p className="text-[14px] text-[#6a7282]">준비 중인 화면입니다.</p>
        <Link
          to={MY_ROUTES.home}
          className="mt-4 flex items-center gap-1.5 rounded-[10px] border border-[#e5e7eb] bg-white px-3.5 py-2 text-[13px] font-semibold text-[#364153] hover:bg-[#f7f8fa]"
        >
          <ArrowLeftIcon aria-hidden="true" className="size-3.5" />
          마이페이지로 돌아가기
        </Link>
      </div>
    </MainLayout>
  );
}
