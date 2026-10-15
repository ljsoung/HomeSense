import { useEffect, useRef, useState, type MouseEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircleIcon } from '../../components/icons/AlertCircleIcon';
import { BellIcon } from '../../components/icons/BellIcon';
import { BuildingIcon } from '../../components/icons/BuildingIcon';
import { ChevronLeftIcon } from '../../components/icons/ChevronLeftIcon';
import { PlusIcon } from '../../components/icons/PlusIcon';
import { TrendingDownIcon } from '../../components/icons/TrendingDownIcon';
import { TrendingUpIcon } from '../../components/icons/TrendingUpIcon';
import { MainLayout } from '../../components/layout/MainLayout';
import { MobileAppBar } from '../../components/layout/MobileAppBar';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/useToast';
import { markNotificationRead } from '../../features/notification/api';
import type { NotificationResponse } from '../../features/notification/types';
import { formatRelativeTime } from '../../lib/relativeTime';
import { MEDIA_MD_DOWN, MEDIA_XL_UP, useMediaQuery } from '../../lib/useMediaQuery';
import { MY_ROUTES } from '../../routes/paths';
import {
  clampPage,
  FILTER_EMPTY_MESSAGE,
  FILTER_OPTIONS,
  formatAbsoluteDateTime,
  MISSING_TARGET_FALLBACK,
  MISSING_TARGET_MESSAGE,
  needsNormalization,
  parseHistoryParams,
  priceDirectionOf,
  resolveNotificationLink,
  RETENTION_NOTICE,
  serializeHistoryParams,
  showsRelativeTime,
  type HistoryFilter,
  type NotificationLink,
} from './notificationHistoryModel';
import { forgetLocallyRead, rememberLocallyRead, useNotificationHistory } from './useNotificationHistory';

const TITLE = '알림 이력';
/** D11 — Figma "발송된 알림을 시간순으로 확인합니다." 대신. 이메일을 끈 알림은 발송되지 않지만 목록에는 나온다. */
const SUBTITLE = '받은 알림을 최신순으로 확인합니다.';
/**
 * D9 — Figma 문구("관심 매물이나 지역을 등록하면 가격 변동 시 알림을 받을 수 있어요.")는 사실과 다르다: 관심 등록이 알림 설정을
 * 만들지 않는다(FavoriteService는 notification_setting을 읽기만 한다). 알림은 MY-03에서 설정해야 생긴다.
 */
const EMPTY_DESCRIPTION = ['관심 매물·지역에 알림을 설정하면', '가격 변동이나 신규 거래를 알려드려요.'];
/** MY-02 빈 상태 CTA "매물 둘러보기"와 같은 목적지. */
const BROWSE_HREF = '/';

type Layout = 'desktop' | 'tablet' | 'mobile';

const CARD_CLASS = 'rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_2px_12px_rgba(0,0,0,0.06)]';
const MUTED_TEXT = 'text-[12px] leading-[18px] text-[#99a1af]';

/**
 * SCR-MY-04 알림 이력(UI정의서 5.5절, FR-6.4). 트리는 한 벌이다 — 머리(브레드크럼/앱 바)와 행 배치만 화면 크기로 고르고, 목록
 * 모드는 데이터 훅이 바꾼다(768px 미만 무한 스크롤, 그 이상 번호형 — SRCH-01과 같은 경계). 머리 경계는 MY-03과 같은 1280px이다.
 * URL에 type(필터)과 page(번호형, 1-base)를 둬 DTL-01에서 뒤로 오면 같은 탭·페이지로 돌아온다(D7).
 */
export function NotificationHistoryPage() {
  const isDesktop = useMediaQuery(MEDIA_XL_UP);
  const isMobile = useMediaQuery(MEDIA_MD_DOWN);
  const layout: Layout = isDesktop ? 'desktop' : isMobile ? 'mobile' : 'tablet';
  const mode = isMobile ? 'infinite' : 'paged';

  const [searchParams, setSearchParams] = useSearchParams();
  const params = parseHistoryParams(searchParams);
  const history = useNotificationHistory({ filter: params.filter, page: params.page, mode });
  const navigate = useNavigate();
  const { showToast } = useToast();

  // 모르는 type, 0·음수·숫자 아닌 page → 정규화한 값으로 replace(D7).
  useEffect(() => {
    if (needsNormalization(searchParams)) setSearchParams(serializeHistoryParams(params), { replace: true });
  });

  // 범위를 넘는 page → 마지막 페이지로 replace(번호형만).
  const totalPages = history.pageMeta?.totalPages;
  useEffect(() => {
    if (mode !== 'paged' || history.status !== 'success' || totalPages === undefined) return;
    const clamped = clampPage(params.page, totalPages);
    if (clamped !== params.page) setSearchParams(serializeHistoryParams({ ...params, page: clamped }), { replace: true });
  });

  const changeFilter = (filter: HistoryFilter) => {
    if (filter === params.filter) return;
    setSearchParams(serializeHistoryParams({ filter, page: 1 }), { replace: true });
    window.scrollTo(0, 0);
  };
  const changePage = (page: number) => {
    setSearchParams(serializeHistoryParams({ ...params, page }), { replace: true });
    window.scrollTo(0, 0);
  };

  // D2 — 낙관적 읽음 표시 + PATCH를 기다리지 않고 이동. 실패는 알리지 않고, 화면에 남아 있을 때(새 탭으로 열기)만 점을 되돌린다.
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const markRead = (notification: NotificationResponse) => {
    if (notification.isRead) return;
    const id = notification.notificationId;
    history.setRead(id, true);
    rememberLocallyRead(id);
    markNotificationRead(id).catch(() => {
      forgetLocallyRead(id);
      if (mounted.current) history.setRead(id, false);
    });
  };
  const openMissing = (notification: NotificationResponse) => {
    markRead(notification);
    showToast(MISSING_TARGET_MESSAGE, 'error');
    navigate(MISSING_TARGET_FALLBACK);
  };

  const isAllEmpty = history.status === 'success' && params.filter === 'ALL' && history.items.length === 0 && params.page === 1;
  const filterBar = isAllEmpty ? null : <FilterChips layout={layout} value={params.filter} onChange={changeFilter} />;

  const listBody = (
    <ListBody
      layout={layout}
      mode={mode}
      filter={params.filter}
      history={history}
      onOpen={markRead}
      onOpenMissing={openMissing}
      currentPage={params.page}
      onPageChange={changePage}
    />
  );

  const emptyAll = <AllEmpty />;

  if (layout === 'desktop') {
    return (
      <MainLayout>
        <div className="mx-auto flex w-full max-w-[800px] flex-col px-6 py-8">
          <nav aria-label="위치 경로" className="text-[13px] leading-[19.5px]">
            <ol className="flex items-center gap-1.5">
              <li>
                <Link to={MY_ROUTES.home} className="flex items-center gap-1.5 font-semibold text-[#6a7282] hover:underline">
                  <ChevronLeftIcon aria-hidden="true" className="size-4" />
                  마이페이지
                </Link>
              </li>
              <li aria-hidden="true" className="text-[16px] leading-none text-[#d1d5dc]">
                /
              </li>
              <li aria-current="page" className="font-semibold text-[#101828]">
                {TITLE}
              </li>
            </ol>
          </nav>
          <div className="pt-5">
            <h1 className="text-[22px] leading-[33px] font-extrabold tracking-[-0.5px] text-[#101828]">{TITLE}</h1>
            <p className="mt-0.5 text-[13px] leading-[19.5px] text-[#99a1af]">{SUBTITLE}</p>
          </div>
          <section aria-label={TITLE} className={`mt-6 overflow-hidden ${CARD_CLASS}`}>
            {isAllEmpty ? (
              emptyAll
            ) : (
              <>
                {filterBar}
                {listBody}
              </>
            )}
          </section>
          {!isAllEmpty && history.status === 'success' && (
            <p className={`mt-3 text-center ${MUTED_TEXT}`}>{RETENTION_NOTICE}</p>
          )}
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout>
      <div className="flex flex-col">
        <MobileAppBar title={TITLE} />
        <p className="px-4 pt-4 pb-1 text-[13px] leading-[19.5px] text-[#99a1af]">{SUBTITLE}</p>
        <section aria-label={TITLE} className="mt-3 bg-white">
          {isAllEmpty ? (
            emptyAll
          ) : (
            <>
              {filterBar}
              {listBody}
            </>
          )}
        </section>
      </div>
    </MainLayout>
  );
}

/** 필터 chip(D7) — 전체/가격변동/신규거래. 데스크톱은 필터 바 안에, 태블릿·모바일은 가로 스크롤(6.2절). */
function FilterChips({ layout, value, onChange }: { layout: Layout; value: HistoryFilter; onChange: (next: HistoryFilter) => void }) {
  return (
    <div className={`border-b border-[#f3f4f6] bg-white ${layout === 'desktop' ? 'px-6 py-4' : 'px-4 py-3'}`}>
      <div role="radiogroup" aria-label="알림 유형" className="flex gap-2 overflow-x-auto [scrollbar-width:none]">
        {FILTER_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={value === option.value}
            onClick={() => onChange(option.value)}
            className={`flex h-[34px] shrink-0 items-center rounded-full border px-4 text-[13px] font-bold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand ${
              value === option.value ? 'border-brand bg-brand text-white' : 'border-[#e5e7eb] bg-white text-[#6b7280] hover:bg-[#f7f8fa]'
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function ListBody({
  layout,
  mode,
  filter,
  history,
  onOpen,
  onOpenMissing,
  currentPage,
  onPageChange,
}: {
  layout: Layout;
  mode: 'paged' | 'infinite';
  filter: HistoryFilter;
  history: ReturnType<typeof useNotificationHistory>;
  onOpen: (n: NotificationResponse) => void;
  onOpenMissing: (n: NotificationResponse) => void;
  currentPage: number;
  onPageChange: (page: number) => void;
}) {
  // 상대 시각의 기준 — 렌더마다 바뀌지 않게 처음 그릴 때 한 번 정한다(MY-01 미리보기와 같다).
  const [now] = useState(() => Date.now());
  const sentinelRef = useRef<HTMLDivElement>(null);
  const { loadMore, hasMore, moreStatus, status } = history;

  // 모바일 무한 스크롤 — 센티널이 보이면 다음 페이지. 진행 중이거나 실패했으면 부르지 않는다(실패는 재시도 버튼으로).
  useEffect(() => {
    if (mode !== 'infinite' || !hasMore || moreStatus !== 'idle' || status !== 'success' || !sentinelRef.current) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting) loadMore();
    }, { rootMargin: '200px' });
    observer.observe(sentinelRef.current);
    return () => observer.disconnect();
  }, [mode, hasMore, moreStatus, status, loadMore]);

  if (history.status === 'loading') {
    return (
      <div aria-busy="true">
        <span className="sr-only">알림을 불러오는 중</span>
        {Array.from({ length: 5 }, (_, index) => (
          <RowSkeleton key={index} layout={layout} />
        ))}
      </div>
    );
  }

  if (history.status === 'error') {
    return (
      <div className={layout === 'desktop' ? 'p-6' : 'p-4'}>
        <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-[12px] border border-[#fee2e2] bg-[#fef2f2] px-4 py-3">
          <p className="flex items-center gap-2 text-[13px] text-[#7f1d1d]">
            <AlertCircleIcon aria-hidden="true" className="size-4 shrink-0 text-[#e7000b]" />
            <span>{history.errorMessage}</span>
          </p>
          <button
            type="button"
            onClick={history.retry}
            className="rounded-[10px] bg-white px-3.5 py-1.5 text-[13px] font-semibold text-[#e7000b] shadow-[0_1px_2px_rgba(0,0,0,0.1)]"
          >
            다시 시도
          </button>
        </div>
      </div>
    );
  }

  if (history.items.length === 0) {
    // 유형 필터 빈 상태(D9) — chip은 유지하고 짧게. 전체 빈 상태는 페이지가 따로 그린다.
    const message = filter === 'ALL' ? '이 페이지에 알림이 없어요' : FILTER_EMPTY_MESSAGE[filter];
    return <p className="px-6 py-14 text-center text-[13px] leading-[19.5px] text-[#99a1af]">{message}</p>;
  }

  const total = history.pageMeta?.totalElements ?? history.items.length;
  const totalPages = history.pageMeta?.totalPages ?? 1;
  return (
    <>
      <ul aria-label="알림 목록">
        {history.items.map((notification) => (
          <li key={notification.notificationId} className="border-b border-[#f3f4f6] last:border-b-0">
            <NotificationRow
              layout={layout}
              notification={notification}
              now={now}
              onOpen={onOpen}
              onOpenMissing={onOpenMissing}
            />
          </li>
        ))}
      </ul>
      {mode === 'paged' ? (
        // 한 페이지뿐이면 페이지네이션이 없다 — 데스크톱은 빈 영역을 두지 않고, 태블릿은 보관 안내만 둔다(데스크톱 안내는 카드 아래).
        (totalPages > 1 || layout !== 'desktop') && (
          <div className="border-t border-[#f3f4f6] py-6">
            <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={onPageChange} />
            {layout !== 'desktop' && <p className={`text-center ${MUTED_TEXT} ${totalPages > 1 ? 'mt-4' : ''}`}>{RETENTION_NOTICE}</p>}
          </div>
        )
      ) : (
        <div className="border-t border-[#f3f4f6]">
          {history.moreStatus === 'loading' && (
            <div className="flex justify-center py-5" aria-busy="true">
              <Spinner className="size-5" />
              <span className="sr-only">알림을 더 불러오는 중</span>
            </div>
          )}
          {history.moreStatus === 'error' && (
            <div className="flex flex-col items-center gap-2 py-5" role="alert">
              <p className={MUTED_TEXT}>알림을 더 불러오지 못했어요</p>
              <button
                type="button"
                onClick={history.loadMore}
                className="rounded-[10px] border border-[#e5e7eb] bg-white px-3.5 py-1.5 text-[13px] font-semibold text-[#364153]"
              >
                다시 시도
              </button>
            </div>
          )}
          {history.hasMore && history.moreStatus === 'idle' && <div ref={sentinelRef} aria-hidden="true" className="h-px" />}
          {!history.hasMore && (
            <div className="flex flex-col items-center gap-1 py-5">
              <p className={MUTED_TEXT}>총 {total}건 · 모두 표시됨</p>
              <p className={MUTED_TEXT}>{RETENTION_NOTICE}</p>
            </div>
          )}
        </div>
      )}
    </>
  );
}

/** D4 — 유형·방향별 아이콘과 색. 아이콘은 lucide 계열 기존 아이콘을 크기만 바꿔 쓴다(20px에서 선 1.67, 18px에서 1.5). */
function TypeIcon({ notification, layout }: { notification: NotificationResponse; layout: Layout }) {
  const circle = layout === 'desktop' ? 'size-10' : 'mt-0.5 size-9';
  const icon = layout === 'desktop' ? 'size-5' : 'size-[18px]';
  let tone = 'bg-[#f3f4f6] text-[#6b7280]';
  let glyph = <BellIcon aria-hidden="true" className={icon} />;
  if (notification.notificationType === 'NEW_TRADE') {
    tone = 'bg-[#e8f2f0] text-[#0f5c54]';
    glyph = <BuildingIcon aria-hidden="true" className={icon} />;
  } else if (notification.notificationType === 'PRICE_CHANGE') {
    const direction = priceDirectionOf(notification.title);
    if (direction === 'down') {
      tone = 'bg-[#eff6ff] text-[#3b82f6]';
      glyph = <TrendingDownIcon aria-hidden="true" className={icon} />;
    } else {
      tone = direction === 'up' ? 'bg-[#fef2f2] text-[#ef4444]' : 'bg-[#f3f4f6] text-[#6b7280]';
      glyph = <TrendingUpIcon aria-hidden="true" className={icon} />;
    }
  }
  return (
    <span aria-hidden="true" data-testid="notification-icon" className={`flex shrink-0 items-center justify-center rounded-full ${circle} ${tone}`}>
      {glyph}
    </span>
  );
}

/**
 * 한 행. 대상이 있으면 링크(키보드 Enter·새 탭 열기), 없으면 버튼(D2). 안 읽음은 8px 점 + 제목 진하게 + 스크린리더 "읽지 않음"(D3).
 */
function NotificationRow({
  layout,
  notification,
  now,
  onOpen,
  onOpenMissing,
}: {
  layout: Layout;
  notification: NotificationResponse;
  now: number;
  onOpen: (n: NotificationResponse) => void;
  onOpenMissing: (n: NotificationResponse) => void;
}) {
  const link: NotificationLink = resolveNotificationLink(notification);
  const unread = !notification.isRead;
  // 7일이 지나면 formatRelativeTime이 날짜를 돌려줘 절대 시각과 겹친다 — 그때는 상대 시각을 보이지 않는다(D10).
  const relative = showsRelativeTime(notification.createdAt, now) ? formatRelativeTime(notification.createdAt, now) : null;
  const absolute = formatAbsoluteDateTime(notification.createdAt);
  const desktop = layout === 'desktop';

  const rowClass = `flex w-full text-left hover:bg-[#f7f8fa] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand ${
    desktop ? 'min-h-[73px] items-center gap-4 px-6 py-4' : 'items-start gap-3 p-4'
  }`;
  const titleClass = `${desktop ? 'text-[14px] leading-[19.25px]' : 'text-[13.5px] leading-[18.56px]'} ${
    unread ? 'font-semibold text-[#101828]' : 'font-medium text-[#4a5565]'
  }`;

  const content = (
    <>
      <TypeIcon notification={notification} layout={layout} />
      <span className="flex min-w-0 flex-1 flex-col">
        <span className={titleClass}>
          {notification.title}
          {unread && <span className="sr-only"> (읽지 않음)</span>}
        </span>
        {desktop ? (
          <time dateTime={notification.createdAt} className="mt-0.5 text-[11.5px] leading-[17.25px] text-[#99a1af]">
            {absolute}
          </time>
        ) : (
          <span className="pt-1 text-[11px] leading-[16.5px] text-[#99a1af]">
            <time dateTime={notification.createdAt}>{relative ? `${relative} · ${absolute}` : absolute}</time>
          </span>
        )}
      </span>
      {desktop && relative && <span className="shrink-0 text-[12.5px] leading-[18.75px] text-[#99a1af]">{relative}</span>}
      <span className={`flex w-6 shrink-0 justify-center ${desktop ? '' : 'pt-1.5'}`}>
        {unread && <span aria-hidden="true" data-testid="unread-dot" className="size-2 rounded-full bg-[#0f5c54]" />}
      </span>
    </>
  );

  if (link.kind === 'missing') {
    return (
      <button type="button" className={rowClass} onClick={() => onOpenMissing(notification)} data-notification-id={notification.notificationId}>
        {content}
      </button>
    );
  }
  const handleOpen = (event: MouseEvent<HTMLAnchorElement>) => {
    // 왼쪽 클릭(수정 키 포함 — 새 탭)과 가운데 클릭 모두 읽음 처리한다. 이동은 링크 기본 동작에 맡긴다(PATCH를 기다리지 않는다).
    if (event.type === 'auxclick' && event.button !== 1) return;
    onOpen(notification);
  };
  return (
    <Link
      to={link.href}
      className={rowClass}
      onClick={handleOpen}
      onAuxClick={handleOpen}
      data-notification-id={notification.notificationId}
    >
      {content}
    </Link>
  );
}

function RowSkeleton({ layout }: { layout: Layout }) {
  const desktop = layout === 'desktop';
  return (
    <div className={`flex border-b border-[#f3f4f6] ${desktop ? 'items-center gap-4 px-6 py-4' : 'items-start gap-3 p-4'}`}>
      <div className={`shrink-0 animate-pulse rounded-full bg-[#f3f4f6] ${desktop ? 'size-10' : 'size-9'}`} />
      <div className="flex flex-1 flex-col gap-2">
        <div className="h-3.5 w-2/3 animate-pulse rounded bg-[#f3f4f6]" />
        <div className="h-3 w-1/3 animate-pulse rounded bg-[#f3f4f6]" />
      </div>
    </div>
  );
}

/** 전체 빈 상태(Figma 38-1717·38-2048) — 필터 chip은 숨긴다. 공용 EmptyState(UIC-08 plain)를 쓰고 CTA만 Figma 모양. */
function AllEmpty() {
  return (
    <div className="px-6 py-20">
      <EmptyState
        icon={<BellIcon aria-hidden="true" className="size-7" />}
        title="아직 받은 알림이 없어요"
        description={
          <>
            {EMPTY_DESCRIPTION[0]}
            <br />
            {EMPTY_DESCRIPTION[1]}
          </>
        }
        actions={
          <Link
            to={BROWSE_HREF}
            className="mt-3 flex h-10 items-center gap-2 rounded-[14px] bg-brand px-5 text-[13px] font-bold text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
          >
            <PlusIcon aria-hidden="true" className="size-4" />
            매물 둘러보기
          </Link>
        }
      />
    </div>
  );
}
