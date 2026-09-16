package com.jiseong.homesense.batch.loader;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@code TradeChunkLoader.upsertOne()}이 건 하나를 저장할 때 이 게이트웨이를 거치도록 해, 그 건의
 * {@code TradeRepository#upsert}(원자적 {@code INSERT ... ON DUPLICATE KEY UPDATE}) 호출을 청크
 * 트랜잭션과 별개의 트랜잭션(REQUIRES_NEW)으로 격리한다(Codex 코드리뷰 P1 지적).
 *
 * <p><b>왜 필요한가 — 원자적 upsert가 UNIQUE 경쟁 문제를 없앴다고 해서 이 격리 자체가 불필요해진
 * 것은 아니다.</b> {@code loadChunk()}는 최대 500건을 한 트랜잭션으로 처리하며, 건마다
 * {@code try/catch}로 개별 실패를 스킵하고 계속 진행한다 — 그 의도는 "한 건 실패가 나머지 499건을
 * 막지 않게" 하려는 것이다. 하지만 오버사이즈 문자열(VARCHAR 길이 초과), UNSIGNED 컬럼에 대한 음수
 * 값, 유효하지 않은 FK 같은 **진짜 제약 위반**이 발생하면, 그 원인이 UNIQUE 경쟁이든 다른
 * 무엇이든과 무관하게 JPA 스펙상 해당 예외가 현재 트랜잭션을 rollback-only로 표시한다 — 애플리케이션
 * 코드가 그 예외를 잡아서 넘어가도 마킹 자체는 지워지지 않는다. 그 결과 {@code loadChunk()}가 정상
 * 종료된 것처럼 보여도 트랜잭션 커밋 시점에 {@code UnexpectedRollbackException}이 터지고, 그 청크가
 * 처리한 나머지 499건까지 전부 롤백된다 — 딱 한 건의 나쁜 데이터가 청크 전체를 무효화하는 것이다.
 * 이 프로젝트가 이미 같은 클래스의 함정을 두 번 겪었다(SVC-NTF-01
 * {@code NotificationSettingRepository} 1차 구현, 그리고 삭제된 {@code TradeInsertGateway} 자체의
 * 존재 이유) — CLAUDE.md "UNIQUE 제약 동시성 회귀 테스트 원칙" 절 참고.
 *
 * <p>이번엔 격리 대상이 단일 원자적 SQL 문장 하나뿐이라(옛 게이트웨이처럼 INSERT 실패 후 재조회·
 * UPDATE 재시도를 별도로 구현할 필요가 없다), REQUIRES_NEW를 다시 들여와도 그 옛 구조가 겪었던
 * REPEATABLE READ 스냅샷 문제(재조회가 격리된 트랜잭션의 커밋을 못 보는 문제)가 재현되지 않는다 —
 * 이 게이트웨이는 재조회를 전혀 하지 않고 결과(영향받은 행 수 또는 예외)를 그대로 반환/전파할 뿐이다.
 *
 * <p><b>커넥션 풀 고갈 위험도 별도로 확인했다</b> — 스냅샷 가시성이 안전하다는 것과 커넥션 풀 관점이
 * 안전하다는 것은 서로 다른 질문이라 하나를 확인했다고 다른 하나도 안전하다고 볼 수 없다(SVC-NTF-01
 * `NotificationSettingInsertGateway`가 커넥션 풀은 검토했지만 스냅샷 문제를 놓쳤던 것과 정반대 방향
 * — 이번엔 스냅샷은 확인했으니 풀 쪽도 별도로 봐야 한다). {@code TradeChunkLoader.loadChunk()}는
 * 청크(최대 500건)를 단일 for문으로 순회하며 건마다 이 게이트웨이를 순차 호출하므로, 한 청크 안에
 * 제약 위반 건이 여러 개 있어도 동시에 열리는 REQUIRES_NEW 커넥션은 항상 최대 1개뿐이다(직전 건의
 * 트랜잭션이 커밋/롤백돼 커넥션을 반환한 뒤에야 다음 건이 새로 획득한다). 여기에 BAT-SCH-01
 * (BatchExecutionOrchestrator)이 조합 자체도 항상 단일 스레드로 순회하는 것까지 더하면, 이 배치
 * 파이프라인 전체에서 동시에 열리는 커넥션은 "청크 트랜잭션 1개 + 이 REQUIRES_NEW 1개" = 최대
 * 2개뿐이다(CLAUDE.md "REQUIRES_NEW 격리 INSERT 게이트웨이 패턴" 절 참고).
 *
 * <p><b>무효화 조건</b>: 이 안전성은 전적으로 "배치가 항상 단일 스레드로 순차 실행된다"는 전제에
 * 기댄다 — 나중에 청크 처리나 조합 순회를 병렬화(예: {@code ExecutorService}/{@code parallelStream})
 * 하게 되면 동시에 열리는 REQUIRES_NEW 커넥션 수가 스레드 수만큼 늘어나므로, 그 시점에 커넥션 풀
 * 크기(`spring.datasource.hikari.maximum-pool-size`) 대비 이 분석을 반드시 재검증해야 한다.
 */
@Component
@RequiredArgsConstructor
class TradeUpsertGateway {

    private final TradeRepository tradeRepository;

    /**
     * public인 이유: Spring 프록시 기반 @Transactional은 public 메서드에만 보장된 동작이다
     * ({@code TradeChunkLoader.loadChunk()}의 같은 이유 참고). 클래스 자체는 여전히 package-private이라
     * 패키지 밖 노출과는 무관하다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int upsert(
            String housingType,
            String dealCategory,
            String rentType,
            String datasetId,
            String sggCd,
            String legalDongCd,
            String umdNm,
            Long complexId,
            String buildingName,
            String jibun,
            BigDecimal excluUseArea,
            Short floor,
            Short buildYear,
            LocalDate dealDate,
            Long dealAmount,
            Long depositAmount,
            Long monthlyRentAmount,
            String aptDong,
            String dealingType,
            String agentSggNm,
            LocalDate registrationDate,
            String sellerType,
            String buyerType,
            Boolean landLeaseYn,
            boolean cancelYn,
            LocalDate cancelDate,
            String matchMethod,
            BigDecimal matchConfidence,
            String dedupHash,
            LocalDateTime now) {
        return tradeRepository.upsert(housingType, dealCategory, rentType, datasetId, sggCd, legalDongCd, umdNm,
                complexId, buildingName, jibun, excluUseArea, floor, buildYear, dealDate, dealAmount, depositAmount,
                monthlyRentAmount, aptDong, dealingType, agentSggNm, registrationDate, sellerType, buyerType,
                landLeaseYn, cancelYn, cancelDate, matchMethod, matchConfidence, dedupHash, now);
    }
}
