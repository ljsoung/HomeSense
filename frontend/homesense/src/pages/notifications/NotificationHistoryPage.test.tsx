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

function renderAt(width: number, items: NotificationResponse[]) {
  stubViewport(width);
  getNotificationPage.mockResolvedValue({ items, pageMeta: { page: 0, size: 10, totalElements: items.length, totalPages: 1 } });
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/notifications']}>
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

  it('안 읽은 행에는 스크린리더 "읽지 않음"과 점, 읽은 행에는 없다', async () => {
    renderAt(1280, [unsent, { ...unsent, notificationId: 2, title: '창신동 신규 실거래 3건', notificationType: 'NEW_TRADE', isRead: true }]);
    const unreadRow = await screen.findByRole('link', { name: /숭인 힐스테이트.*읽지 않음/ });
    expect(unreadRow.querySelector('[data-testid="unread-dot"]')).not.toBeNull();
    const readRow = screen.getByRole('link', { name: /창신동/ });
    expect(readRow.textContent).not.toContain('읽지 않음');
    expect(readRow.querySelector('[data-testid="unread-dot"]')).toBeNull();
  });
});
