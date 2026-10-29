import { useCallback, useState } from 'react';
import { parseMapUrl } from '../../features/map/mapParams';

/** 주소창에 지금 들어 있는 검색 조건(regionLabel·keyword). 렌더된 URL보다 앞설 수 있다(전환 렌더 대기 중). */
function searchTextOfLocation(): string {
  const { filters } = parseMapUrl(new URLSearchParams(window.location.search));
  return filters.regionLabel ?? filters.keyword ?? '';
}

/**
 * MAP-01 검색창 값. 같은 라우트 안에서 URL의 검색 조건이 바뀌면(GNB "지도로 보기"로 조건 없는 /map, 뒤로가기) 검색창을
 * 맞춘다. 바뀔 때만 맞추고 매 렌더 덮어쓰지 않는다 — 지도를 움직일 때마다 URL(뷰포트)이 바뀌어도 입력 중인 값은 그대로 둔다.
 *
 * 이 화면이 검색·지역 선택으로 직접 URL에 쓴 검색 조건은 쓴 순서대로 기억해 두고(`rememberOwn`), 그 값이 렌더에 반영될 때는
 * 검색창을 건드리지 않는다. URL 갱신은 전환 렌더라 늦게 반영된다(느린 기기에서 Enter 뒤 약 250ms) — 그사이 입력한 다음
 * 검색어를 앞 검색어로 되돌리면 다음 Enter가 앞 검색을 다시 보냈다(백로그 17).
 *
 * 기억한 값은 렌더된 URL이 주소창과 같아지면(대기 중인 반영이 없으면) 비운다. 전환 렌더가 건너뛰어져 URL 조건이 그대로인 채
 * 끝나면(T를 썼다가 곧바로 원래 S로 되돌림) 반영되지 않은 값이 남아, 나중에 바깥에서 그 값으로 바뀐 URL을 자기 것으로
 * 착각하지 않게 한다.
 */
export function useMapSearchText(urlSearchText: string) {
  const [value, setValue] = useState(urlSearchText);
  const [synced, setSynced] = useState(urlSearchText);
  const [ownTexts, setOwnTexts] = useState<string[]>([]);
  if (synced !== urlSearchText) {
    setSynced(urlSearchText);
    const own = ownTexts.indexOf(urlSearchText);
    if (own === -1) {
      setValue(urlSearchText);
      if (ownTexts.length > 0) setOwnTexts([]);
    } else {
      setOwnTexts(ownTexts.slice(own + 1));
    }
  } else if (ownTexts.length > 0 && searchTextOfLocation() === urlSearchText) {
    setOwnTexts([]);
  }

  /** 이 화면이 URL에 쓸 검색 조건을 기억한다 — URL을 바꾸기 직전에 부른다. */
  const rememberOwn = useCallback(
    (text: string) =>
      setOwnTexts((prev) => {
        // URL이 바뀌지 않을 값(지금 URL과 같거나 바로 앞에 쓴 값과 같음)은 반영될 일이 없으니 남기지 않는다.
        const last = prev.length > 0 ? prev[prev.length - 1] : synced;
        return text === last ? prev : [...prev, text];
      }),
    [synced],
  );

  return { value, setValue, rememberOwn };
}
