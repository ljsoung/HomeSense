import { useCallback, useEffect, useState } from 'react';
import { getErrorMessage } from '../../lib/apiError';

export type Loadable<T> =
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; message: string; error: unknown };

/**
 * MY-01 영역 하나의 데이터 로딩. 영역마다 따로 불러 한 영역이 실패해도 나머지는 그린다(UI정의서 MY-01 예외 처리).
 * `fetcher`는 렌더마다 바뀌지 않는 함수(모듈 수준)여야 한다. 재시도는 같은 fetcher를 다시 부른다.
 * 계정이 바뀌면 보호 라우트 가드가 확인 중 상태에서 화면을 내렸다가 다시 마운트하므로 이 훅은 계정 변경을
 * 따로 다루지 않는다 — 언마운트 뒤 도착한 응답은 버린다.
 */
export function useLoadable<T>(fetcher: () => Promise<T>) {
  const [state, setState] = useState<Loadable<T>>({ status: 'loading' });
  const [round, setRound] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setState({ status: 'loading' });
      try {
        const data = await fetcher();
        if (!cancelled) setState({ status: 'success', data });
      } catch (error) {
        if (!cancelled) setState({ status: 'error', message: getErrorMessage(error), error });
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [fetcher, round]);

  const retry = useCallback(() => setRound((value) => value + 1), []);
  return { state, retry };
}
