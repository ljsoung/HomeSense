import { Link } from 'react-router-dom';
import axios from 'axios';
import { AlertCircleIcon } from '../../components/icons/AlertCircleIcon';
import { PencilIcon } from '../../components/icons/PencilIcon';
import type { UserResponse } from '../../features/user/types';
import { formatDottedDate } from '../../lib/format';
import { MY_ROUTES } from '../../routes/paths';
import type { Loadable } from './useLoadable';

/**
 * UserResponse.createdAt은 타임존 없는 LocalDateTime 문자열이고 서버가 KST로 기록한다(배포 JVM 타임존 KST 고정이 전제,
 * CLAUDE.md "날짜/시간 처리") — 날짜 부분을 그대로 쓴다. 브라우저 타임존으로 다시 계산하지 않는다.
 */
function formatJoinedDate(createdAt: string): string {
  return `${formatDottedDate(createdAt.slice(0, 10))} 가입`;
}

/**
 * 401은 세션이 끝났다는 뜻이라 보호 라우트 가드가 곧 로그인 화면으로 보낸다 — 그 사이 배너를 띄우지 않는다.
 * 그 밖의 실패만 화면 상단 배너로 알린다(UI정의서 MY-01 예외 처리).
 */
function isSessionEnded(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401;
}

export function ProfileErrorBanner({ state, onRetry }: { state: Loadable<UserResponse>; onRetry: () => void }) {
  if (state.status !== 'error' || isSessionEnded(state.error)) return null;
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 rounded-[12px] border border-[#fee2e2] bg-[#fef2f2] px-4 py-3"
    >
      <p className="flex items-center gap-2 text-[13px] text-[#7f1d1d]">
        <AlertCircleIcon className="size-4 shrink-0 text-[#e7000b]" />
        <span>{state.message}</span>
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="rounded-[10px] bg-white px-3.5 py-1.5 text-[13px] font-semibold text-[#e7000b] shadow-[0_1px_2px_rgba(0,0,0,0.1)]"
      >
        다시 시도
      </button>
    </div>
  );
}

// 프로필 카드만 그림자가 6%다(다른 카드는 5%, Figma 7:5373).
const PROFILE_CARD_CLASS = 'rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_1px_4px_rgba(0,0,0,0.06)]';

/** MY-01 프로필 요약 카드 — 아바타(닉네임 첫 글자, 헤더 UserMenu와 같은 모양), 닉네임, 이메일, 가입일. */
export function ProfileCard({ state }: { state: Loadable<UserResponse> }) {
  const user = state.status === 'success' ? state.data : null;

  return (
    <section
      aria-label="내 프로필"
      aria-busy={state.status === 'loading'}
      className={`flex items-center gap-5 p-5 md:p-7 ${PROFILE_CARD_CLASS}`}
    >
      {user ? (
        <>
          <span
            aria-hidden="true"
            className="flex size-[52px] shrink-0 items-center justify-center rounded-full bg-brand text-[20px] leading-[30px] font-extrabold text-white md:size-16 md:text-[24px] md:leading-9"
          >
            {user.nickname.charAt(0)}
          </span>
          <div className="flex min-w-0 flex-1 flex-col">
            <p className="truncate text-[16px] leading-6 font-extrabold text-[#101828] md:text-[20px] md:leading-[30px]">
              {user.nickname}
            </p>
            {/* 모바일은 이메일과 가입일을 두 줄로 나눠 이메일만 말줄임한다(긴 이메일이 가입일을 밀어내지 않게). Figma 모바일은
                가입일을 숨기지만 UI정의서 MY-01의 필수 항목이라 그대로 보인다. 말줄임은 CSS라 전체 이메일은 DOM과
                스크린리더에 그대로 남고, title로 마우스 오버에서도 보인다. */}
            <p className="mt-0.5 flex min-w-0 flex-col text-[13px] leading-5 text-[#99a1af] md:flex-row md:items-center md:gap-1.5">
              <span className="truncate" title={user.email}>
                {user.email}
              </span>
              <span aria-hidden="true" className="hidden md:inline">
                ·
              </span>
              <span className="shrink-0">{formatJoinedDate(user.createdAt)}</span>
            </p>
          </div>
          {/* 프로그램설계서 3.2절 하단 메모는 "MY-01 회원정보 수정은 인라인/모달"이라 적지만, UI정의서 v2.1이 MY-05
              화면으로 이동하는 방식으로 바꿨다 — UI정의서를 따른다(문서 동기화 필요). */}
          <Link
            to={MY_ROUTES.profileEdit}
            className="flex shrink-0 items-center gap-1.5 rounded-[14px] border border-[#e5e7eb] bg-white px-4 py-2 text-[13px] leading-5 font-semibold text-[#4a5565] hover:bg-[#f7f8fa]"
          >
            <PencilIcon className="size-3.5" />
            회원정보 수정
          </Link>
        </>
      ) : (
        <>
          <span aria-hidden="true" className="size-[52px] shrink-0 animate-pulse rounded-full bg-[#f3f4f6] md:size-16" />
          <div aria-hidden="true" className="flex flex-1 flex-col gap-2">
            <div className="h-4 w-28 animate-pulse rounded bg-[#f3f4f6]" />
            <div className="h-3.5 w-48 max-w-full animate-pulse rounded bg-[#f3f4f6]" />
          </div>
          {state.status === 'loading' && <span className="sr-only">프로필을 불러오는 중</span>}
        </>
      )}
    </section>
  );
}
