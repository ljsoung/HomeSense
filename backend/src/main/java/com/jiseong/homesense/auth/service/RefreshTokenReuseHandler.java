package com.jiseong.homesense.auth.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.logging.AuditLogger;

import lombok.RequiredArgsConstructor;

/**
 * SVC-AUTH-01 재사용 탐지 전용 — 정확한 안전 조건은 "호출자에게 열린 트랜잭션이 전혀 없어야 한다"가
 * 아니라 "호출자가 이 사용자의 refresh_token 행 중 어느 것에도 아직 락을 쥐고 있지 않아야 한다"다.
 * 이 요구사항은 실제 동시성 IT({@code AuthServiceRefreshRotationMariaDbIT})로 두 단계에 걸쳐
 * 발견·확정됐다 — 처음엔 아래 이유들을 모른 채 {@link com.jiseong.homesense.auth.service.RefreshTokenRotator}의
 * 트랜잭션이 아직 열려 있는 도중(자기 자신이 방금 실패한 조건부 UPDATE로 잠가 둔 그 행이 살아있는
 * 채로) 이 클래스를 호출했다가 자기 자신과 교착(self-deadlock)해 IT가 타임아웃으로 실패했다.
 *
 * <p>현재 호출부는 둘이다. {@link AuthService#refreshAccessToken}은 검증+회전 시도 전체를
 * {@link RefreshTokenRotator}(자기완결 트랜잭션)에 위임하고 자신은 {@code NOT_SUPPORTED}로 트랜잭션
 * 자체를 열지 않는다 — 아래 (3)의 교착을 실제로 겪었던 경로라 가장 보수적으로 "트랜잭션 자체가 없음"을
 * 보장한다. {@link AuthService#logout}은 이 메서드를 부르기 전까지 {@code findByTokenValue()}(비잠금
 * SELECT)만 수행하므로 — {@code revokeIfUnrevoked()} 같은 조건부 UPDATE를 거치지 않는다 — 클래스
 * 레벨 {@code @Transactional}(REQUIRED)이 열려 있어도 안전하다: 애초에 어떤 행도 잠근 적이 없기
 * 때문이다. 새 호출부를 추가할 때는 이 조건(그 시점까지 잠근 행이 있는가)만 확인하면 되고, "트랜잭션이
 * 아예 없어야 한다"는 더 강한 요구로 오해해 불필요하게 NOT_SUPPORTED 패턴을 복제하지 않아도 된다.
 *
 * <p>(1) <b>가시성</b> — 같은 Refresh Token으로 두 요청이 경쟁해 진 쪽(loser)의 트랜잭션은 REPEATABLE
 * READ 스냅샷을 이긴 쪽(winner)이 커밋하기 훨씬 전에 이미 열어 뒀다. winner가 새로 INSERT한 행은
 * loser의 스냅샷 기준으로는 여전히 "존재한 적 없는" phantom이라, loser의 트랜잭션 안에서
 * {@code revokeAllByUserId()}를 그대로 불러도 그 새 토큰은 조건절에 걸리지 않고 살아남는다 — 탈취
 * 공격자가 이 경쟁에서 이기면 방금 발급받은 새 세션이 재사용 탐지의 전체 폐기를 피해 가는 구멍이
 * 된다. {@code REQUIRES_NEW}로 새 트랜잭션을 열면 그 시점의 최신 커밋 상태(winner의 INSERT 포함)를
 * 보는 새 스냅샷에서 출발하므로 이 문제가 사라진다 — 단, 이것만으로는 아래 (3)의 교착까지 막지
 * 못한다는 게 이 클래스 역사의 핵심이다.
 *
 * <p>(2) <b>독립성</b> — 재사용 탐지는 "요청을 거부하기 전에 남기는 부작용"이다. loser의 바깥쪽
 * {@code refreshAccessToken()}은 이 처리 직후 {@link com.jiseong.homesense.auth.exception.
 * InvalidRefreshTokenException}(RuntimeException)을 던져 그 트랜잭션을 rollback-only로 만든다 —
 * 대량 폐기와 감사 로그가 바깥쪽 트랜잭션에 같이 묶여 있었다면 이 예외 하나로 통째로 롤백돼 버렸을
 * 것이다. REQUIRES_NEW는 독립적으로 커밋되므로 바깥쪽이 예외로 끝나도 안전하게 남는다.
 *
 * <p>(3) <b>자기 교착 회피(가장 늦게 발견됨)</b> — {@code REQUIRES_NEW}가 "새 스냅샷에서 시작"함을
 * 보장하는 것과, 그 새 트랜잭션이 필요한 락을 "즉시 획득할 수 있음"을 보장하는 것은 별개다. loser의
 * {@code revokeIfUnrevoked()}는 UPDATE 대상 행을 0건 갱신했더라도(조건절이 안 맞아서) InnoDB가
 * WHERE 절을 평가하려 그 행을 조회하는 과정에서 이미 그 행에 배타 락을 걸어 둔다 — 이 락은 loser의
 * 바깥쪽 트랜잭션이 끝날 때까지(커밋이든 롤백이든) 풀리지 않는다. 그 바깥쪽 트랜잭션이 여전히 열려
 * 있는 채로 이 클래스를 {@code REQUIRES_NEW}로 호출하면, 새로 연 트랜잭션의 {@code
 * revokeAllByUserId()}가 정확히 그 같은 행을 다시 잠그려다가 — 같은 애플리케이션 스레드가 소유한 다른
 * 커넥션이 이미 쥐고 있는 락을 기다리며 — 데드락 감지기가 잡을 수 없는 자기 자신과의 교착에 빠진다
 * (두 트랜잭션 다 DB 관점에서는 "락을 기다리는 중"이지 "다른 락을 요청하며 대기 중"이 아니라서 InnoDB의
 * wait-for 사이클 탐지에 걸리지 않는다 — 그래서 즉시 에러가 아니라 innodb_lock_wait_timeout까지
 * 그냥 멈춰 있는다). 이 문제는 REQUIRES_NEW 자체로는 풀 수 없다 — 호출자가 이 메서드를 부르기
 * *전에* 자신의 트랜잭션을 완전히 끝내야 한다. 그래서 {@link AuthService#refreshAccessToken}은
 * {@code NOT_SUPPORTED}로 자신은 트랜잭션을 열지 않고, 검증+회전 시도 전체를 {@link RefreshTokenRotator}
 * (자기완결 트랜잭션)에 위임해 그 트랜잭션이 완전히 커밋된 뒤에만 이 클래스를 부른다 — 그 시점엔
 * loser 쪽에서 쥐고 있던 락이 전부 반납된 상태라 이 클래스의 REQUIRES_NEW가 그 즉시 락을 잡을 수 있다.
 *
 * <p>이전에 이 프로젝트가 REQUIRES_NEW 게이트웨이 패턴(TradeInsertGateway 등)을 원자적 upsert로
 * 교체한 선례와 모순되지 않는다 — 그때는 "UNIQUE 위반을 피해 INSERT 하나만 격리"하려다 스냅샷 문제를
 * 새로 만든 것이 문제였고(재시도 로직이 필요 없어져야 했는데 남아 있었다), 여기는 재시도가 전혀
 * 없고 "호출자가 이미 트랜잭션을 끝낸 뒤, 최신 커밋을 보는 새 트랜잭션에서 한 번만 실행하고
 * 독립적으로 커밋한다"는 REQUIRES_NEW 본연의 용도다.
 */
@Component
@RequiredArgsConstructor
class RefreshTokenReuseHandler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogger auditLogger;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void handle(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        auditLogger.logRefreshTokenReuseDetected(userId);
    }
}
