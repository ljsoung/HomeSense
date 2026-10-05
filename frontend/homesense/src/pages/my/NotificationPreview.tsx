import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BellIcon } from '../../components/icons/BellIcon';
import { ChevronRightIcon } from '../../components/icons/ChevronRightIcon';
import { EmptyState } from '../../components/ui/EmptyState';
import type { NotificationResponse } from '../../features/notification/types';
import { formatRelativeTime } from '../../lib/relativeTime';
import { MY_ROUTES } from '../../routes/paths';
import { NOTIFICATION_PREVIEW_COUNT } from './myPageData';
import { CardBody, RowSkeleton, SectionCard, WidgetError } from './SectionCard';
import type { Loadable } from './useLoadable';

/**
 * 점은 Figma(MY-01·MY-04)에서 알림 대상의 종류를 뜻한다 — 관심 매물 = Primary, 관심 지역 = amber. 하지만 지금은
 * 응답만으로 대상을 가를 수 없어 한 색(Primary)으로 통일한다: NotificationResponse의 complexId·legalDongCd·tradeId는
 * 상호 배타가 아니고(관심 지역의 신규 거래 알림에도 complexId가 실릴 수 있다), 알림을 만드는 BAT-NTF-01이 아직 없어
 * 어떤 조합이 오는지 정해진 계약도 없다(CLAUDE.md SCR-MY-01 절). 읽음 여부는 MY-01에서 표시하지 않는다.
 */
function TargetDot() {
  return <span aria-hidden="true" className="mt-1.5 size-2 shrink-0 rounded-full bg-brand" />;
}

/**
 * MY-01 최근 알림 미리보기 — 최근 3건. 행과 "전체 보기"는 MY-04로 간다. 이 화면에서는 읽음 처리(PATCH)를 하지 않는다
 * (UI정의서 MY-01에 읽음 처리 정의 없음 — 읽음은 MY-04의 책임).
 * 행 모양은 Figma(7:5373): 점 → 알림 문구(13/18 Medium) 아래 상대 시간(11/17) → 오른쪽 chevron. Figma 행에는 글자가 한
 * 줄뿐이라 `message`만 보이고 `title`은 쓰지 않는다 — 알림을 만드는 BAT-NTF-01이 아직 없어 두 필드에 무엇이 담길지
 * 정해지지 않았다. 그 배치가 생기면 다시 본다(CLAUDE.md SCR-MY-01 절).
 */
export function NotificationPreview({ state, onRetry }: { state: Loadable<NotificationResponse[]>; onRetry: () => void }) {
  // 상대 시간의 기준 시각 — 렌더마다 바뀌지 않게 처음 그릴 때 한 번 정한다(화면을 다시 열면 새로 정해진다).
  const [now] = useState(() => Date.now());
  return (
    <SectionCard title="최근 알림" viewAll={{ to: MY_ROUTES.notifications, ariaLabel: '알림 전체 보기' }}>
      {state.status === 'loading' && (
        <div aria-busy="true" className="divide-y divide-[#f3f4f6]">
          <span className="sr-only">알림을 불러오는 중</span>
          {Array.from({ length: NOTIFICATION_PREVIEW_COUNT }, (_, index) => (
            <RowSkeleton key={index} />
          ))}
        </div>
      )}
      {state.status === 'error' && <WidgetError message={state.message} onRetry={onRetry} />}
      {state.status === 'success' && state.data.length === 0 && (
        <CardBody>
          <EmptyState icon={<BellIcon className="size-5" />} description="아직 받은 알림이 없어요" />
        </CardBody>
      )}
      {state.status === 'success' && state.data.length > 0 && (
        <ul className="flex flex-col divide-y divide-[#f3f4f6]">
          {state.data.map((notification) => (
            <li key={notification.notificationId}>
              <Link to={MY_ROUTES.notifications} className="flex items-start gap-3.5 px-5 py-4 hover:bg-[#f7f8fa]">
                <TargetDot />
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="line-clamp-2 text-[13px] leading-[18px] font-medium text-[#1e2939]">
                    {/* message는 NULL 허용 컬럼이라 키째 빠질 수 있다 — 빈 행 대신 NOT NULL인 title을 보인다. */}
                    {notification.message ?? notification.title}
                  </span>
                  <time dateTime={notification.sentAt} className="mt-0.5 text-[11px] leading-[17px] text-[#99a1af]">
                    {formatRelativeTime(notification.sentAt, now)}
                  </time>
                </span>
                <ChevronRightIcon aria-hidden="true" className="size-4 shrink-0 self-center text-[#99a1af]" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}
