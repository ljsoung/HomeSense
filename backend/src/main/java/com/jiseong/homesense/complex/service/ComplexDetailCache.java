package com.jiseong.homesense.complex.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.exception.ComplexNotFoundException;
import com.jiseong.homesense.complex.repository.ComplexRepository;

import lombok.RequiredArgsConstructor;

/**
 * complexDetail::{complexId} 캐시(TTL 24h)만 떼어낸 별도 빈이다. ComplexService.getDetail()이 매
 * 호출마다 SVC-RCV-01.record()를 실행해야 하는데(설계서 3.6절), 그 메서드 자체에
 * {@code @Cacheable}을 걸어두면 record() 호출까지 캐시 히트마다 스킵된다. 그렇다고 같은 클래스
 * 안에 캐시 전용 메서드를 따로 둬도 self-invocation(자기 자신 호출은 프록시를 거치지 않는 Spring
 * AOP의 알려진 제약)에 걸려 캐싱 자체가 무력화되므로, 이 로직만 별도 빈으로 분리해 ComplexService가
 * 외부 빈 호출로 이 메서드를 부르게 한다(CLAUDE.md SVC-RCV-01 절 참고).
 */
@Component
@RequiredArgsConstructor
public class ComplexDetailCache {

    private final ComplexRepository complexRepository;

    @Cacheable(cacheNames = "complexDetail", key = "#complexId")
    public ComplexDetailResponse get(Long complexId) {
        Complex complex = complexRepository.findById(complexId).orElseThrow(ComplexNotFoundException::new);
        return ComplexDetailResponse.from(complex);
    }
}
