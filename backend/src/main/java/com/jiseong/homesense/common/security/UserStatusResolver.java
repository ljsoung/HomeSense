package com.jiseong.homesense.common.security;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.entity.UserStatus;
import com.jiseong.homesense.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * COM-SEC-01 캐시미스 복구(코드리뷰 P1 지적, 2026-09-22) — {@link UserStatusCacheService}의
 * Redis 키가 사라지면(재시작, evict 등) {@code JwtAuthenticationFilter}가 캐시미스를 곧바로
 * 미인증으로 처리했는데, 프론트가 이 401을 자동 복구할 인터셉터를 아직 갖추지 않았다
 * (`httpClient.ts`에 401→refresh 핸들러 없음, `AuthProvider.tsx`가 `getMe()` 실패를 조용히
 * 무시) — CLAUDE.md가 전제했던 "401을 받으면 클라이언트가 /api/auth/refresh를 호출해 캐시가
 * 다시 채워진다"는 자연 치유 경로가 실제로는 없어, Redis 하나의 일시적 장애만으로 이미
 * 로그인한 모든 사용자가 프론트 재배포나 강제 재로그인 없이는 복구되지 않는 전면 로그아웃을
 * 일으킬 수 있었다.
 *
 * <p>캐시미스일 때만 DB로 폴백해 최신 상태를 읽고 캐시를 다시 채운다 — 캐시 히트(정상 경로)의
 * 성능에는 영향이 없다. 탈퇴 후 유예기간이 지나 파기(BAT-USR-01)된 계정처럼 DB에도 행이 없는
 * 경우는 그대로 미인증 처리한다 — 이건 캐시 문제가 아니라 실제로 더 이상 존재하지 않는
 * 계정이므로 옳은 동작이다.
 *
 * <p><b>이 복구용 쓰기는 반드시 {@code setIfAbsent}(SET-NX)여야 한다, 무조건 덮어쓰기가 아니라</b>
 * (코드리뷰 P1 지적, 2026-09-22 — 위 캐시미스 복구를 도입한 바로 그 리뷰가 지적한 후속 문제).
 * 여기서 실행하는 {@code userRepository.findById()}도 결국 "DB 읽기 → 그 값으로 캐시 SET"이라,
 * 이 읽기가 지연되는 사이(GC 정지 등, 위에서 이미 인정한 것과 같은 종류의 지연) 다른 트랜잭션이
 * 이 사용자를 탈퇴시켜 캐시에 WITHDRAWN을 먼저 써 놓았다면, 뒤늦게 도착하는 이 메서드의 쓰기가
 * 무조건 덮어쓰기였다면 그 WITHDRAWN을 다시 ACTIVE로 되돌려 버린다 — 창이 훨씬 좁아졌을 뿐
 * ({@code AuthService.loginInternal()}/{@code refreshAccessToken()}이 겪었던 것과 정확히 같은
 * 모양의 race다. "창이 좁으니 확률적으로 괜찮다"는 근거로 남겨두지 않고, SET-NX로 구조적으로
 * 막는다 — 이미 캐시에 값이 있다면(그게 무슨 값이든) 이 메서드는 절대 덮어쓰지 않으므로, 순서와
 * 무관하게 "더 먼저 명확히 확정된 값"이 항상 보존된다.
 */
@Component
@RequiredArgsConstructor
public class UserStatusResolver {

    private final UserStatusCacheService userStatusCacheService;
    private final UserRepository userRepository;

    public boolean isActive(Long userId) {
        return resolveStatus(userId).filter(UserStatus.ACTIVE::equals).isPresent();
    }

    private Optional<UserStatus> resolveStatus(Long userId) {
        return userStatusCacheService.getStatus(userId).or(() -> refreshFromDatabase(userId));
    }

    private Optional<UserStatus> refreshFromDatabase(Long userId) {
        Optional<UserStatus> status = userRepository.findById(userId).map(User::getStatus);
        status.ifPresent(s -> userStatusCacheService.setIfAbsent(userId, s));
        return status;
    }
}
