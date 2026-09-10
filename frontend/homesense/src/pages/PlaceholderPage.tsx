interface PlaceholderPageProps {
  title: string;
  programId: string;
}

/** AUTH-02/AUTH-03/HOME-01처럼 아직 설계·구현되지 않은 화면의 라우트를 끊지 않기 위한 최소 자리표시. */
export function PlaceholderPage({ title, programId }: PlaceholderPageProps) {
  return (
    <div className="flex min-h-screen w-full flex-col items-center justify-center gap-2 bg-[#f7f8fa] px-4 text-center">
      <p className="text-[13px] font-semibold text-[#99a1af]">{programId}</p>
      <h1 className="text-[22px] font-extrabold text-[#101828]">{title}</h1>
      <p className="text-[14px] text-[#6a7282]">추후 구현 예정입니다.</p>
    </div>
  );
}
