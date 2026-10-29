import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertCircleIcon } from '../icons/AlertCircleIcon';

interface AppErrorBoundaryProps {
  children: ReactNode;
  /** 이 값이 바뀌면(라우트 이동) 오류 상태를 풀고 다시 그린다. */
  resetKey?: string;
}

interface AppErrorBoundaryState {
  error: Error | null;
}

/** 운영에서 보이는 고정 문구. 오류 내용(메시지·스택)은 개발 모드에서만 보인다. */
export const APP_ERROR_TITLE = '화면을 표시하는 중 문제가 발생했어요';
export const APP_ERROR_DESCRIPTION = '잠시 후 다시 시도하거나 홈으로 이동해 주세요.';

/**
 * 앱 최상위 에러 바운더리 — 렌더 중 예외가 나도 흰 화면 대신 안내 화면(다시 시도, 홈으로)을 보인다. 라우터는 데이터 라우터가 아니라
 * (`<BrowserRouter>`) `errorElement`가 없어 역할이 겹치지 않는다. 이벤트 핸들러·비동기 오류는 잡지 않는다(React 규칙) — 그쪽은
 * 각 화면이 토스트·배너로 알린다. 외부 오류 수집 서비스는 붙이지 않는다(개발 모드에서만 콘솔에 남긴다).
 *
 * "홈으로"는 전체 새로고침 링크다 — 예외가 남긴 모듈 상태와 무관하게 깨끗하게 다시 시작한다. 바운더리 밖 Provider에서도 쓰므로
 * 라우터 링크를 쓰지 않는다.
 */
export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: unknown): AppErrorBoundaryState {
    return { error: error instanceof Error ? error : new Error(String(error)) };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) console.error('[AppErrorBoundary]', error, info.componentStack);
  }

  componentDidUpdate(prevProps: AppErrorBoundaryProps) {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) this.setState({ error: null });
  }

  private retry = () => this.setState({ error: null });

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;
    return (
      <main className="flex min-h-screen items-center justify-center bg-[#f7f8fa] px-4 py-16">
        <div role="alert" className="w-full max-w-[420px] rounded-[16px] border border-[#f3f4f6] bg-white p-6 text-center shadow-[0_1px_4px_rgba(0,0,0,0.05)]">
          <span className="mx-auto mb-4 flex size-12 items-center justify-center rounded-full bg-[#fef2f2] text-[#e7000b]">
            <AlertCircleIcon aria-hidden="true" className="size-6" />
          </span>
          <h1 className="text-[17px] leading-6 font-bold text-[#101828]">{APP_ERROR_TITLE}</h1>
          <p className="mt-2 text-[13px] leading-5 text-[#4a5565]">{APP_ERROR_DESCRIPTION}</p>
          {import.meta.env.DEV && (
            <pre
              data-testid="app-error-detail"
              className="mt-4 max-h-48 overflow-auto rounded-[10px] bg-[#f9fafb] p-3 text-left text-[11px] leading-4 whitespace-pre-wrap text-[#7f1d1d]"
            >
              {error.message}
              {error.stack ? `\n\n${error.stack}` : ''}
            </pre>
          )}
          <div className="mt-5 flex gap-2">
            <button
              type="button"
              onClick={this.retry}
              className="flex-1 rounded-[12px] border border-[#e5e7eb] bg-white py-2.5 text-[13.5px] font-semibold text-[#364153] hover:bg-[#f9fafb]"
            >
              다시 시도
            </button>
            <a
              href="/"
              className="flex-1 rounded-[12px] bg-brand py-2.5 text-[13.5px] font-semibold text-white hover:bg-[#0c4a44]"
            >
              홈으로
            </a>
          </div>
        </div>
      </main>
    );
  }
}
