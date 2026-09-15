import { Link } from 'react-router-dom';
import { HomeIcon } from '../icons/HomeIcon';

export function Footer() {
  return (
    <footer className="border-t border-[#e5e7eb] bg-white">
      <div className="mx-auto max-w-[1280px] px-4 py-6 md:px-8">
        <div className="flex items-center gap-2">
          <div className="flex size-6 items-center justify-center rounded-[8px] bg-brand">
            <HomeIcon className="size-3.5" />
          </div>
          <p className="text-[14px] font-extrabold text-brand">HomeSense</p>
        </div>
        <p className="mt-4 text-[11.5px] leading-[1.5] text-[#99a1af]">
          데이터 출처: 국토교통부 실거래가 공개시스템 (data.go.kr) · 갱신주기: 일 1회
        </p>
        <p className="mt-1 text-[11px] leading-[1.5] text-[#99a1af] md:pl-5">
          본 서비스는 참고용 데이터를 제공하며, 실제 매매·임대차 계약에는 공인중개사 확인을 권장합니다.
        </p>
        <div className="mt-5 flex flex-wrap gap-x-6 gap-y-2 border-t border-[#f3f4f6] pt-4 text-[11.5px] text-[#99a1af]">
          {/* "이용약관"은 실제 화면이 아직 없어 링크로 만들지 않았다(완결 필요, CLAUDE.md 참고) —
              "개인정보처리방침"만 실제 /privacy 페이지가 생겨 링크로 바꿨다. */}
          <span>이용약관</span>
          <Link to="/privacy" className="hover:text-brand hover:underline">
            개인정보처리방침
          </Link>
          <span>서비스 소개</span>
          <span>고객센터</span>
        </div>
        <p className="mt-3 text-[11px] text-[#d1d5dc]">© 2025 HomeSense. All rights reserved.</p>
      </div>
    </footer>
  );
}
