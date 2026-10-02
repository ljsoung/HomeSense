import { useEffect, useState } from 'react';

/**
 * 미디어 쿼리 일치 여부. 화면 크기에 따라 구조가 달라야 할 때 컴포넌트를 두 벌 렌더하고 CSS로 숨기지 않고
 * 이 값으로 한 벌만 렌더한다(CLAUDE.md "반응형 렌더 규칙").
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const mql = window.matchMedia(query);
    const handler = () => setMatches(mql.matches);
    handler();
    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, [query]);
  return matches;
}

/** Tailwind 기본 브레이크포인트와 같은 값. */
export const MEDIA_MD_DOWN = '(max-width: 767px)';
export const MEDIA_XL_UP = '(min-width: 1280px)';
