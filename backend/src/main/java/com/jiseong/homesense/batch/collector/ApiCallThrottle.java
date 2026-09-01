package com.jiseong.homesense.batch.collector;

import org.springframework.stereotype.Component;

/**
 * 국토부 Open API로 나가는 모든 실제 HTTP 요청 직전에 호출해 30 TPS 제한을 지킨다.
 * BatchExecutionOrchestrator가 조합(시군구×계약월×주택유형×거래유형) 단위로만 호출을 절제하면,
 * RealEstateApiCollector.collect() 내부에서 한 조합이 데이터셋(APT+SALE은 2개)×페이지 수만큼
 * 연달아 요청을 보내는 구간은 무방비 상태가 된다 — 실제 요청이 나가는 지점(requestPage) 바로
 * 앞에서 이 컴포넌트로 간격을 강제해야 한다. 싱글턴 빈이라 이 애플리케이션의 모든 호출자가
 * 같은 마지막 호출 시각을 공유한다.
 */
@Component
public class ApiCallThrottle {

    /** 30 TPS(평균 33ms/건) 대비 안전마진을 둔 호출 간 최소 지연. */
    private static final long MIN_INTERVAL_MILLIS = 40;

    private long lastCallAtMillis = 0;

    public synchronized void throttle() {
        long remaining = MIN_INTERVAL_MILLIS - (System.currentTimeMillis() - lastCallAtMillis);
        if (remaining > 0) {
            sleep(remaining);
        }
        lastCallAtMillis = System.currentTimeMillis();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
