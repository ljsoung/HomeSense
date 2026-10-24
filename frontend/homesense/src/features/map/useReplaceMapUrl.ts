import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { parseMapUrl, serializeMapUrl, type MapUrlState } from './mapParams';

/**
 * MAP-01 URL 갱신. 항상 replace로 바꿔 히스토리를 쌓지 않고, 다음 URL은 호출 시점의 `window.location.search`로 만든다.
 * react-router의 `setSearchParams(fn)`은 마지막 렌더의 URL을 넘겨줘, 다시 렌더되기 전에 두 번 부르면 앞의 갱신이 사라진다.
 * 이동(replaceState)은 동기라 지금 주소를 읽으면 앞의 갱신이 이미 들어 있다. 결과가 지금 주소와 같으면 이동하지 않는다.
 */
export function useReplaceMapUrl() {
  const [, setSearchParams] = useSearchParams();
  return useCallback(
    (update: (prev: MapUrlState) => MapUrlState) => {
      const current = new URLSearchParams(window.location.search);
      const next = serializeMapUrl(update(parseMapUrl(current)));
      if (next.toString() === current.toString()) return;
      setSearchParams(next, { replace: true });
    },
    [setSearchParams],
  );
}
