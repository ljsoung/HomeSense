import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BellIcon } from '../../components/icons/BellIcon';
import { EmptyState } from '../../components/ui/EmptyState';
import type { NotificationResponse } from '../../features/notification/types';
import { formatRelativeTime } from '../../lib/relativeTime';
import { MY_ROUTES } from '../../routes/paths';
import { NOTIFICATION_PREVIEW_COUNT } from './myPageData';
import { RowSkeleton, SectionCard, WidgetError } from './SectionCard';
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
 */
export function NotificationPreview({ state, onRetry }: { state: Loadable<NotificationResponse[]>; onRetry: () => void }) {
  // 상대 시간의 기준 시각 — 렌더마다 바뀌지 않게 처음 그릴 때 한 번 정한다(화면을 다시 열면 새로 정해진다).
  const [now] = useState(() => Date.now());
  return (
    <SectionCard
      title="최근 알림"
      icon={<BellIcon className="size-4 text-[#101828]" />}
      viewAll={{ to: MY_ROUTES.notifications, ariaLabel: '알림 전체 보기' }}
    >
      {state.status === 'loading' && (
        <div aria-busy="true">
          <span className="sr-only">알림을 불러오는 중</span>
          {Array.from({ length: NOTIFICATION_PREVIEW_COUNT }, (_, index) => (
            <RowSkeleton key={index} />
          ))}
        </div>
      )}
      {state.status === 'error' && <WidgetError message={state.message} onRetry={onRetry} />}
      {state.status === 'success' && state.data.length === 0 && (
        <EmptyState icon={<BellIcon className="size-5" />} description="아직 받은 알림이 없어요" />
      )}
      {state.status === 'success' && state.data.length > 0 && (
        <ul className="flex flex-col divide-y divide-[#f3f4f6]">
          {state.data.map((notification) => (
            <li key={notification.notificationId}>
              <Link to={MY_ROUTES.notifications} className="flex items-start gap-3 rounded-[10px] py-3 hover:bg-[#f7f8fa]">
                <TargetDot />
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="truncate text-[14px] font-semibold text-[#101828]">{notification.title}</span>
                  <span className="line-clamp-2 text-[13px] text-[#4a5565]">{notification.message}</span>
                </span>
                <time dateTime={notification.sentAt} className="shrink-0 text-[12px] text-[#6a7282]">
                  {formatRelativeTime(notification.sentAt, now)}
                </time>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}
