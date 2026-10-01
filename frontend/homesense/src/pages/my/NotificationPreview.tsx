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
 * 점 색의 의미: 읽지 않은 알림 = 브랜드색, 읽은 알림 = 회색(NotificationResponse.isRead). MY-04 알림 이력과 같은
 * 의미여야 한다 — MY-04 Figma(38:1526)를 이번 작업에서 대조하지 못해(Figma 연결 끊김) 확인이 필요하다.
 * 색만으로 구분하지 않도록 스크린리더용 문구를 함께 둔다.
 */
function ReadDot({ isRead }: { isRead: boolean }) {
  return (
    <>
      <span
        aria-hidden="true"
        className={`mt-1.5 size-2 shrink-0 rounded-full ${isRead ? 'bg-[#d1d5dc]' : 'bg-brand'}`}
      />
      <span className="sr-only">{isRead ? '읽음' : '읽지 않음'}</span>
    </>
  );
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
                <ReadDot isRead={notification.isRead} />
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
