package com.jiseong.homesense.batch.collector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiCallThrottleTest {

    private final ApiCallThrottle throttle = new ApiCallThrottle();

    @Test
    void 연속_호출_간_최소_40ms_간격을_보장한다() {
        long start = System.currentTimeMillis();
        throttle.throttle();
        throttle.throttle();
        throttle.throttle();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(80);
    }

    @Test
    void 이전_호출로부터_이미_충분한_시간이_지났으면_기다리지_않는다() throws InterruptedException {
        throttle.throttle();
        Thread.sleep(50);

        long start = System.currentTimeMillis();
        throttle.throttle();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(40);
    }
}
