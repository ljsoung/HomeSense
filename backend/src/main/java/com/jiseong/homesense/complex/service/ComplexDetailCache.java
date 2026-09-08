package com.jiseong.homesense.complex.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.exception.ComplexNotFoundException;
import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;

import lombok.RequiredArgsConstructor;

/**
 * complexDetailV2::{complexId} 캐시(TTL 24h)만 떼어낸 별도 빈이다. ComplexService.getDetail()이 매
 * 호출마다 SVC-RCV-01.record()를 실행해야 하는데(설계서 3.6절), 그 메서드 자체에
 * {@code @Cacheable}을 걸어두면 record() 호출까지 캐시 히트마다 스킵된다. 그렇다고 같은 클래스
 * 안에 캐시 전용 메서드를 따로 둬도 self-invocation(자기 자신 호출은 프록시를 거치지 않는 Spring
 * AOP의 알려진 제약)에 걸려 캐싱 자체가 무력화되므로, 이 로직만 별도 빈으로 분리해 ComplexService가
 * 외부 빈 호출로 이 메서드를 부르게 한다(CLAUDE.md SVC-RCV-01 절 참고).
 *
 * <p>캐시 이름은 {@code complexDetail}이 아니라 {@code complexDetailV2}다 — {@link ComplexDetailResponse}에
 * housingType 필드가 추가되며(SVC-RCV-01) 캐시 이름을 버전업했다. Redis가 배포 사이에도 살아남는
 * 환경에서 옛 이름으로 저장된 엔트리를 그대로 뒀다면, housingType 없이 직렬화된 캐시가 역직렬화 시
 * 조용히 null로 채워지고(Jackson record 역직렬화는 누락 필드를 예외 없이 null로 채운다) 그 결과
 * RecentViewService.record()가 매번 조용히 기록을 스킵하는(target.housingType() == null) 채로 TTL
 * 24h가 지날 때까지 유지된다 — 이름을 바꿔 옛 엔트리를 애초에 다시 읽지 않게 했다(Codex 코드리뷰
 * 지적). 새 이름의 옛 엔트리는 아무도 참조하지 않아 각자의 TTL로 자연 만료된다. 이 DTO 필드가 다시
 * 바뀌면 이 이름을 또 한 번 올려라.
 */
@Component
@RequiredArgsConstructor
public class ComplexDetailCache {

    private final ComplexRepository complexRepository;

    @Cacheable(cacheNames = "complexDetailV2", key = "#complexId")
    public ComplexDetailResponse get(Long complexId) {
        Complex complex = complexRepository.findById(complexId).orElseThrow(ComplexNotFoundException::new);
        return ComplexDetailResponse.from(complex);
    }
}
