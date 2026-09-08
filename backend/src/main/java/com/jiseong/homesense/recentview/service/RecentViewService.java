package com.jiseong.homesense.recentview.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.recentview.dto.RecentViewResponse;
import com.jiseong.homesense.recentview.dto.RecentViewTarget;
import com.jiseong.homesense.recentview.entity.RecentView;
import com.jiseong.homesense.recentview.repository.RecentViewRepository;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * SVC-RCV-01. Controller에 노출되는 조회(getRecent())와, SVC-CPX-01(단지 상세 조회)이 내부적으로
 * 호출하는 협력 메서드(record())를 담당한다. 캐시는 적용하지 않는다(CLAUDE.md 캐싱 절 — 거래
 * 검색/이력처럼 배치 직후 변경 가능성이 있는 성격은 아니지만, 이 도메인은 애초에 캐시 대상 표에
 * 없다).
 *
 * <p>record()는 설계서 3.6절("RCV 도메인과 협력") 그대로 ComplexService.getDetail() 내부에서
 * 호출된다(캐시 자체는 별도 빈 {@link com.jiseong.homesense.complex.service.ComplexDetailCache}로
 * 분리해 두어 record()가 캐시 히트에 영향받지 않는다 — CLAUDE.md SVC-RCV-01 절 참고). record()는
 * 설계서 3.3절이 명시한 대로 {@code @Async}로 실행한다 — 조회 응답 경로에 조회 이력 write(조회+갱신
 * 또는 INSERT, 상한 초과 시 삭제까지)가 얹히지 않도록 하기 위함이다. 예외가 나도 호출자에게
 * 전파되지 않고 기본 {@code SimpleAsyncUncaughtExceptionHandler}가 로그만 남긴다 — 조회 이력은
 * 참고 정보일 뿐 상세조회 성공 여부에 영향을 줘서는 안 된다는 판단이다(AsyncConfig 참고).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RecentViewService {

    /** 주체(회원/세션)별 보관 건수 상한 — 이를 넘기면 viewed_at이 가장 오래된 레코드부터 삭제한다. */
    private static final int MAX_PER_ACTOR = 20;

    private final RecentViewRepository recentViewRepository;
    private final UserRepository userRepository;
    private final ComplexRepository complexRepository;

    @Transactional(readOnly = true)
    public List<RecentViewResponse> getRecent(Long userId, String sessionId, int limit) {
        if (userId == null && sessionId == null) {
            return List.of();
        }

        PageRequest pageRequest = PageRequest.of(0, Math.max(limit, 1));
        List<RecentView> views = userId != null
                ? recentViewRepository.findByUser_UserIdOrderByViewedAtDesc(userId, pageRequest)
                : recentViewRepository.findBySessionIdOrderByViewedAtDesc(sessionId, pageRequest);

        return views.stream().map(RecentViewResponse::from).toList();
    }

    @Async
    public void record(Long userId, String sessionId, RecentViewTarget target) {
        if ((userId == null && sessionId == null) || target.housingType() == null) {
            return;
        }

        Optional<RecentView> existing = userId != null
                ? recentViewRepository.findByUser_UserIdAndComplex_ComplexId(userId, target.complexId())
                : recentViewRepository.findBySessionIdAndComplex_ComplexId(sessionId, target.complexId());

        if (existing.isPresent()) {
            existing.get().touch();
            return;
        }

        User user = userId != null ? userRepository.getReferenceById(userId) : null;
        String actorSessionId = userId != null ? null : sessionId;
        Complex complex = complexRepository.getReferenceById(target.complexId());
        recentViewRepository.save(RecentView.record(user, actorSessionId, complex, target.housingType()));
        evictOverflow(userId, sessionId);
    }

    private void evictOverflow(Long userId, String sessionId) {
        long count = userId != null
                ? recentViewRepository.countByUser_UserId(userId)
                : recentViewRepository.countBySessionId(sessionId);
        if (count <= MAX_PER_ACTOR) {
            return;
        }

        PageRequest overflowPage = PageRequest.of(0, (int) (count - MAX_PER_ACTOR));
        List<RecentView> oldest = userId != null
                ? recentViewRepository.findByUser_UserIdOrderByViewedAtAsc(userId, overflowPage)
                : recentViewRepository.findBySessionIdOrderByViewedAtAsc(sessionId, overflowPage);
        recentViewRepository.deleteAll(oldest);
    }
}
