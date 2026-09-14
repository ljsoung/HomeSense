const SESSION_ID_KEY = 'homesense.sessionId';

/** RecentViewController.SESSION_ID_HEADER와 반드시 같은 값을 써야 한다(CLAUDE.md SVC-RCV-01 절). */
export const SESSION_ID_HEADER = 'X-Session-Id';

/**
 * 비로그인 사용자의 SVC-RCV-01 조회 이력을 식별하는 브라우저 단위 세션 ID.
 * 탭이 아니라 브라우저에 귀속되도록 sessionStorage가 아니라 localStorage에 저장한다(새로고침·새
 * 탭에서도 같은 값을 유지해야 "최근 조회한 단지"가 방금 본 걸 그대로 보여준다). crypto.randomUUID()는
 * 모든 최신 브라우저의 secure context(https 또는 localhost)에서 지원된다 — 이 값은 인증 토큰이
 * 아니라 단순 식별자라 폴백을 두지 않는다.
 */
export function getOrCreateSessionId(): string {
  const existing = localStorage.getItem(SESSION_ID_KEY);
  if (existing) {
    return existing;
  }
  const created = crypto.randomUUID();
  localStorage.setItem(SESSION_ID_KEY, created);
  return created;
}
