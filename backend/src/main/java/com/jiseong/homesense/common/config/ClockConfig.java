package com.jiseong.homesense.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 공용 KST {@link Clock}. 탈퇴 유예기간처럼 "저장 시점"과 "비교 시점"이 반드시 같은 시간 소스를 써야 하는
 * 로직(User.withdrawnAt 기록 ↔ 파기·철회 threshold 계산)이 JVM 기본 타임존에 흔들리지 않게 한다 —
 * DATETIME 컬럼은 타임존 정보가 없어, 기록은 UTC로 하고 비교는 KST로 하면 유예기간이 9시간 어긋난다.
 *
 * <p>이 프로젝트의 다른 클래스는 아직 각자 자체 KST 상수를 쓴다(CLAUDE.md "날짜/시간 처리") — 이 빈은 그것을
 * 대체하지 않고, 테스트에서 시간을 고정해야 하는 탈퇴 도메인부터 도입한다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
