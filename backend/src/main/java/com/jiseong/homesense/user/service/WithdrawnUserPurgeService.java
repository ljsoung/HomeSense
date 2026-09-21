package com.jiseong.homesense.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * BAT-USR-01의 트랜잭션 경계. user 도메인이 자기 Repository를 소유한다(프로그램설계서 2.2절 계층 협력 원칙).
 *
 * <p>{@link #purgeOne}이 이 클래스에 있고 반복문은 {@code WithdrawnUserPurgeScheduler}에 있는 이유: 같은 클래스
 * 안에서 {@code this.purgeOne(...)}을 부르면 Spring AOP 프록시를 거치지 않아 {@code @Transactional}이 무력화된다
 * (self-invocation). 서로 다른 빈으로 나눠야 사용자 1명당 별도 트랜잭션이 실제로 적용된다.
 */
@Service
@RequiredArgsConstructor
public class WithdrawnUserPurgeService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Long> findTargetIds(LocalDateTime threshold, long afterId, int limit) {
        return userRepository.findPurgeTargetIds(threshold, afterId, PageRequest.ofSize(limit));
    }

    /**
     * 사용자 1명을 파기한다(자식은 DB CASCADE). 조건부 DELETE라 그 사이 철회됐거나 이미 삭제된 경우 0을 반환한다.
     */
    @Transactional
    public int purgeOne(Long userId, LocalDateTime threshold) {
        return userRepository.deleteIfPurgeable(userId, threshold);
    }

    @Transactional(readOnly = true)
    public long countWithdrawnWithoutTimestamp() {
        return userRepository.countWithdrawnWithoutTimestamp();
    }
}
