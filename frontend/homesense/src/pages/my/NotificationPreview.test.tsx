import { cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { NotificationResponse } from '../../features/notification/types';
import { NotificationPreview } from './NotificationPreview';

// BAT-NTF-01이 만든 알림은 BAT-MAIL-01이 보내기 전까지 sentAt이 null이라 서버가 키째 뺀다(non_null). 예전 미리보기는
// sentAt으로 시각을 그려 formatRelativeTime(undefined)가 value.slice에서 예외를 던졌다 — 마이페이지 전체가 깨졌다.

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

const NOW = Date.parse('2026-10-06T12:00:00+09:00');

function renderPreview(data: NotificationResponse[]) {
  vi.useFakeTimers({ now: NOW });
  return render(
    <MemoryRouter>
      <NotificationPreview state={{ status: 'success', data }} onRetry={() => {}} />
    </MemoryRouter>,
  );
}

describe('NotificationPreview(MY-01 최근 알림)', () => {
  it('sentAt 키가 없는 미발송 알림도 발생 시각(createdAt)으로 그린다', () => {
    const { container } = renderPreview([
      {
        notificationId: 1,
        notificationType: 'PRICE_CHANGE',
        title: '숭인 힐스테이트 실거래가 2.1% 상승',
        message: '최근 3개월 평균 3.3㎡당 3,306만원 → 신규 매매 2건 평균 3.3㎡당 3,375만원',
        complexId: 10,
        isRead: false,
        createdAt: '2026-10-06T09:00:00',
      },
    ]);

    const text = container.textContent ?? '';
    expect(text).toContain('숭인 힐스테이트 실거래가 2.1% 상승');
    expect(text).toContain('3시간 전');
    expect(text).not.toContain('최근 3개월 평균'); // 한 줄 행에는 상세(message)가 아니라 요약(title)을 보인다
    expect(container.querySelector('time')?.getAttribute('dateTime')).toBe('2026-10-06T09:00:00');
  });

  it('message가 없어도 title을 보인다', () => {
    const { container } = renderPreview([
      {
        notificationId: 2,
        notificationType: 'NEW_TRADE',
        title: '서울특별시 종로구 숭인동 신규 실거래 1건',
        legalDongCd: '1111017400',
        tradeId: 5,
        isRead: true,
        createdAt: '2026-10-04T12:00:00',
      },
    ]);

    expect(container.textContent).toContain('서울특별시 종로구 숭인동 신규 실거래 1건');
    expect(container.textContent).toContain('2일 전');
  });

  it('점 색과 글자로 관심 매물·관심 지역을 구분하고, 대상을 모르면 회색 점만 둔다', () => {
    const base = { notificationType: 'NEW_TRADE' as const, isRead: false, createdAt: '2026-10-06T09:00:00' };
    const { container } = renderPreview([
      { ...base, notificationId: 1, title: '매물 알림', complexId: 10, tradeId: 3 },
      { ...base, notificationId: 2, title: '지역 알림', legalDongCd: '1111017400', tradeId: 4 },
      { ...base, notificationId: 3, title: '대상 없음 알림' },
    ]);

    const rows = [...container.querySelectorAll('li')];
    const dot = (row: Element) => row.querySelector('[data-target]');
    const label = (row: Element) => row.querySelector('[data-testid="notification-target"]')?.textContent;
    expect(dot(rows[0])?.className).toContain('bg-brand');
    expect(label(rows[0])).toBe('관심 매물 · ');
    expect(dot(rows[1])?.className).toContain('bg-[#e17100]');
    expect(label(rows[1])).toBe('관심 지역 · ');
    expect(dot(rows[2])?.className).toContain('bg-[#99a1af]');
    expect(label(rows[2])).toBeUndefined();
    expect(rows[2].textContent).toContain('3시간 전');
  });
});
