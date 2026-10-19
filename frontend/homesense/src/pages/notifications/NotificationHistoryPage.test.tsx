import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/ToastProvider';
import type { NotificationResponse } from '../../features/notification/types';
import { NotificationHistoryPage } from './NotificationHistoryPage';
import { resetLocallyRead } from './useNotificationHistory';

const getNotificationPage = vi.fn();
vi.mock('../../features/notification/api', () => ({
  getNotificationPage: (...args: unknown[]) => getNotificationPage(...args),
  markNotificationRead: vi.fn(() => Promise.resolve()),
}));
// MainLayout의 헤더는 인증 상태를 읽는다 — 이 테스트는 목록만 본다.
vi.mock('../../components/layout/MainLayout', () => ({
  MainLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

const NOW = Date.parse('2026-10-06T12:00:00+09:00');

function stubViewport(width: number) {
  window.matchMedia = ((query: string) => {
    const max = /max-width:\s*(\d+)px/.exec(query);
    const min = /min-width:\s*(\d+)px/.exec(query);
    const matches = (max ? width <= Number(max[1]) : true) && (min ? width >= Number(min[1]) : true);
    return { matches, media: query, addEventListener: () => {}, removeEventListener: () => {} } as unknown as MediaQueryList;
  }) as typeof window.matchMedia;
}

beforeEach(() => {
  resetLocallyRead();
  vi.useFakeTimers({ now: NOW, toFake: ['Date'] });
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  getNotificationPage.mockReset();
});

function renderAt(width: number, items: NotificationResponse[], path = '/notifications') {
  stubViewport(width);
  getNotificationPage.mockResolvedValue({
    items,
    pageMeta: { page: 0, size: 10, totalElements: items.length, totalPages: items.length === 0 ? 0 : 1 },
  });
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <NotificationHistoryPage />
      </MemoryRouter>
    </ToastProvider>,
  );
}

// sentAt은 이메일 발송 전에는 서버가 키째 뺀다 — 시각은 발생 시각(createdAt)으로 그린다(D10).
const unsent: NotificationResponse = {
  notificationId: 1,
  notificationType: 'PRICE_CHANGE',
  title: '숭인 힐스테이트 실거래가 2.1% 상승',
  complexId: 10,
  isRead: false,
  createdAt: '2026-10-06T09:00:00',
};

describe('NotificationHistoryPage 시간 표시(D10)', () => {
  it('데스크톱: sentAt이 없어도 createdAt의 절대 시각과 상대 시각', async () => {
    renderAt(1280, [unsent]);
    const row = await screen.findByRole('link', { name: /숭인 힐스테이트/ });
    expect(row.textContent).toContain('2026-10-06 09:00');
    expect(row.textContent).toContain('3시간 전');
  });

  it('모바일: "{상대} · {절대}" 한 줄', async () => {
    renderAt(390, [unsent]);
    const row = await screen.findByRole('link', { name: /숭인 힐스테이트/ });
    expect(row.textContent).toContain('3시간 전 · 2026-10-06 09:00');
  });

  it('7일이 지난 알림은 상대 시각 없이 절대 시각만(데스크톱·모바일)', async () => {
    const old = { ...unsent, notificationId: 3, title: '오래된 단지 실거래가 1.0% 상승', createdAt: '2026-09-28T09:00:00' };
    renderAt(1280, [old]);
    const desktopRow = await screen.findByRole('link', { name: /오래된 단지/ });
    expect(desktopRow.textContent).toContain('2026-09-28 09:00');
    expect(desktopRow.textContent).not.toContain('2026.09.28');
    cleanup();
    renderAt(390, [old]);
    const mobileRow = await screen.findByRole('link', { name: /오래된 단지/ });
    expect(mobileRow.querySelector('time')?.textContent).toBe('2026-09-28 09:00');
  });

  it('안 읽은 행에는 스크린리더 "읽지 않음"과 점, 읽은 행에는 없다', async () => {
    renderAt(1280, [unsent, { ...unsent, notificationId: 2, title: '창신동 신규 실거래 3건', notificationType: 'NEW_TRADE', isRead: true }]);
    const unreadRow = await screen.findByRole('link', { name: /숭인 힐스테이트.*읽지 않음/ });
    expect(unreadRow.querySelector('[data-testid="unread-dot"]')).not.toBeNull();
    const readRow = screen.getByRole('link', { name: /창신동/ });
    expect(readRow.textContent).not.toContain('읽지 않음');
    expect(readRow.querySelector('[data-testid="unread-dot"]')).toBeNull();
  });
});

describe('NotificationHistoryPage 빈 상태(D9)', () => {
  it('모바일: 번호형 URL(?page=2)로 들어와도 0건이면 전체 빈 상태 — 무한 스크롤은 page를 쓰지 않는다', async () => {
    renderAt(390, [], '/notifications?page=2');
    expect(await screen.findByText('아직 받은 알림이 없어요')).toBeTruthy();
    expect(screen.queryByRole('radiogroup', { name: '알림 유형' })).toBeNull();
    expect(screen.queryByText('이 페이지에 알림이 없어요')).toBeNull();
    expect(getNotificationPage).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }), expect.anything());
  });
});
