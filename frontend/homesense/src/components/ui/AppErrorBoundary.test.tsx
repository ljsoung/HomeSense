import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { APP_ERROR_DESCRIPTION, APP_ERROR_TITLE, AppErrorBoundary } from './AppErrorBoundary';

// 운영 빌드에는 크래시 경로(/__e2e/crash)가 없어 e2e로 운영 화면을 볼 수 없다. DEV를 false로 바꿔 같은 컴포넌트를 그린다.

const SECRET_MESSAGE = '내부 오류: user@example.com 토큰 abc.def.ghi';

function Thrower(): never {
  throw new Error(SECRET_MESSAGE);
}

beforeEach(() => {
  // React가 잡은 렌더 오류를 콘솔에 남긴다 — 테스트 출력만 조용히 한다.
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

describe('AppErrorBoundary', () => {
  it('운영 모드에서는 고정 문구만 보이고 오류 메시지·스택은 화면에 없다', () => {
    vi.stubEnv('DEV', false);
    const { container } = render(
      <AppErrorBoundary>
        <Thrower />
      </AppErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toBeTruthy();
    expect(screen.getByRole('heading', { name: APP_ERROR_TITLE })).toBeTruthy();
    expect(container.textContent).toContain(APP_ERROR_DESCRIPTION);
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeTruthy();
    expect(screen.getByRole('link', { name: '홈으로' }).getAttribute('href')).toBe('/');

    expect(container.textContent).not.toContain(SECRET_MESSAGE);
    expect(container.textContent).not.toContain('Thrower');
    expect(container.querySelector('[data-testid="app-error-detail"]')).toBeNull();
    expect(container.querySelector('pre')).toBeNull();
  });

  it('개발 모드에서는 오류 메시지를 함께 보인다(대조군)', () => {
    vi.stubEnv('DEV', true);
    const { container } = render(
      <AppErrorBoundary>
        <Thrower />
      </AppErrorBoundary>,
    );

    expect(screen.getByRole('heading', { name: APP_ERROR_TITLE })).toBeTruthy();
    expect(container.querySelector('[data-testid="app-error-detail"]')?.textContent).toContain(SECRET_MESSAGE);
  });
});
