import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BellIcon } from '../../components/icons/BellIcon';
import { ChevronRightIcon } from '../../components/icons/ChevronRightIcon';
import { EmptyState } from '../../components/ui/EmptyState';
import type { NotificationResponse } from '../../features/notification/types';
import { formatRelativeTime } from '../../lib/relativeTime';
import { MY_ROUTES } from '../../routes/paths';
import { NOTIFICATION_PREVIEW_COUNT, notificationTargetOf, type NotificationTarget } from './myPageData';
import { CardBody, RowSkeleton, SectionCard, WidgetError } from './SectionCard';
import type { Loadable } from './useLoadable';

/**
 * 점 색은 Figma(MY-01·MY-04)에서 알림 대상의 종류다 — 관심 매물 = Primary(#0f5c54), 관심 지역 = amber. Figma의 amber 값을
 * 직접 받지 못해 기존 토큰 #e17100(MY-03 0% 경고 아이콘)을 썼다 — 흰 배경 대비 약 3.8:1(그래픽 3:1 기준 충족). 대상을 모르면
 * 회색 #99a1af. 색만으로 구분하지 않도록 시각 줄 앞에 "관심 매물"/"관심 지역" 글자를 함께 둔다(아래 TARGET_LABEL).
 * 읽음 여부는 MY-01에서 표시하지 않는다.
 */
const TARGET_DOT_CLASS: Record<NotificationTarget, string> = {
  property: 'bg-brand',
  region: 'bg-[#e17100]',
  unknown: 'bg-[#99a1af]',
};

const TARGET_LABEL: Record<NotificationTarget, string | null> = {
  property: '관심 매물',
  region: '관심 지역',
  unknown: null,
};

function TargetDot({ target }: { target: NotificationTarget }) {
  return (
    <span
      aria-hidden="true"
      data-target={target}
      className={`mt-1.5 size-2 shrink-0 rounded-full ${TARGET_DOT_CLASS[target]}`}
    />
  );
}

/**
 * MY-01 최근 알림 미리보기 — 최근 3건. 행과 "전체 보기"는 MY-04로 간다. 이 화면에서는 읽음 처리(PATCH)를 하지 않는다
 * (UI정의서 MY-01에 읽음 처리 정의 없음 — 읽음은 MY-04의 책임).
 * 행 모양은 Figma(7:5373): 점 → 알림 문구(13/18 Medium) 아래 상대 시간(11/17) → 오른쪽 chevron. Figma 행에는 글자가 한
 * 줄뿐이라 `title`을 보인다 — BAT-NTF-01이 title을 "○○ 실거래가 2.1% 상승"·"○○ 신규 실거래 3건" 같은 한 줄 요약으로,
 * message를 거래·평균가 상세로 만든다. 시각은 발생 시각(createdAt)이다 — sentAt은 이메일 발송 전에는 없다.
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
          {state.data.map((notification) => {
            const target = notificationTargetOf(notification);
            const label = TARGET_LABEL[target];
            return (
              <li key={notification.notificationId}>
                <Link to={MY_ROUTES.notifications} className="flex items-start gap-3.5 px-5 py-4 hover:bg-[#f7f8fa]">
                  <TargetDot target={target} />
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="line-clamp-2 text-[13px] leading-[18px] font-medium text-[#1e2939]">
                      {notification.title}
                    </span>
                    <span className="mt-0.5 text-[11px] leading-[17px] text-muted">
                      {label && <span data-testid="notification-target">{label} · </span>}
                      <time dateTime={notification.createdAt}>{formatRelativeTime(notification.createdAt, now)}</time>
                    </span>
                  </span>
                  <ChevronRightIcon aria-hidden="true" className="size-4 shrink-0 self-center text-[#99a1af]" />
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </SectionCard>
  );
}
