package com.jiseong.homesense.batch.collector;

/**
 * 스로틀·재시도 백오프 대기(Thread.sleep) 중 스레드가 인터럽트됐을 때 던진다.
 * 애플리케이션 종료·작업 취소로 인한 인터럽트를 조용히 무시하고 다음 조합으로 넘어가면
 * 스로틀/백오프 없이 남은 조합을 몰아서 호출하며 종료가 지연될 수 있으므로,
 * 잡지 않고 그대로 전파시켜 BatchExecutionOrchestrator.orchestrate()의 순회를 즉시 중단시킨다.
 */
public class BatchInterruptedException extends RuntimeException {

    public BatchInterruptedException(String message, InterruptedException cause) {
        super(message, cause);
    }
}
