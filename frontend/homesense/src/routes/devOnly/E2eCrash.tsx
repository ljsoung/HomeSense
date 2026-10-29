/**
 * 개발 서버 전용(운영 빌드에 들어가지 않는다 — AppRouter가 `import.meta.env.DEV`일 때만 lazy로 부른다). e2e가 렌더 중 예외를
 * 일으켜 앱 에러 바운더리를 확인한다. `window.__homesenseE2eCrash = false`면 던지지 않아 "다시 시도"를 확인할 수 있다.
 */
declare global {
  interface Window {
    __homesenseE2eCrash?: boolean;
  }
}

export default function E2eCrash() {
  if (window.__homesenseE2eCrash !== false) throw new Error('e2e 테스트용 렌더 오류');
  return <p data-testid="e2e-crash-recovered">다시 그렸습니다</p>;
}
