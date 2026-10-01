/**
 * 마이페이지 영역 경로. MY-02(/favorites)와 MY-04(/notifications)는 GNB·하단 탭이 이미 쓰던 경로를 그대로 확정했고,
 * MY-03·MY-05는 MY-01을 만들며 새로 정했다. 아직 구현되지 않은 화면은 보호 라우트 아래 "준비 중" 페이지로 둔다
 * (CLAUDE.md "SCR-MY-01" 절).
 */
export const MY_ROUTES = {
  home: '/my',
  favorites: '/favorites',
  notificationSettings: '/notifications/settings',
  notifications: '/notifications',
  profileEdit: '/my/profile',
} as const;
