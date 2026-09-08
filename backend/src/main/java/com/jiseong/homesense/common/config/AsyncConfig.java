package com.jiseong.homesense.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SVC-RCV-01.record()의 {@code @Async} 진입점을 활성화한다 — 프로그램 설계서 3.3절 SVC-CPX-01
 * 처리 로직 원문("SVC-RCV-01.record()를 호출해 조회 이력을 비동기적으로 남긴다")이 요구하는
 * 비동기 실행이다. 동기로 두면 캐시 히트 경로에서도 매 상세조회 요청마다 DB write(조회+갱신 또는
 * INSERT, 상한 초과 시 삭제까지)가 응답 경로에 얹혀 NFR-1(평균 200ms 이내)을 해칠 수 있다.
 * 기본 SimpleAsyncTaskExecutor로 충분한 저빈도 백그라운드 write라 별도 스레드풀 커스터마이징은
 * 하지 않는다 — 필요해지면 그때 Executor 빈을 추가한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
