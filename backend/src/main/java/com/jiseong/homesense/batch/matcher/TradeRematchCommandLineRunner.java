package com.jiseong.homesense.batch.matcher;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * {@link TradeRematchRunner}의 수동 실행 진입점. {@code rematch} 프로필로만 활성화되므로 평소
 * 부팅(local/prod)에는 전혀 관여하지 않는다 — 매처 로직 수정 후 필요할 때만
 * {@code --spring.profiles.active=local,rematch}로 실행한다.
 *
 * <p>기본값은 1차 패스({@code rematchUnmatched()}, complex_id IS NULL 행만). 인자에
 * {@code --mode=full}을 추가하면 2차 패스({@code rematchUpdatedBefore()})를 실행한다 — complex_id
 * 유무와 무관하게 실행 시점 이전에 매칭된 행 전부를 다시 돈다(버그 A/B 시절 SIMILAR로 오배정된 행을
 * 포함해야 하므로 1차 패스만으로는 부족하다, {@link TradeRematchRunner} 클래스 javadoc 참고). cutoff를
 * "실행 시점"으로 잡아도 안전하다 — 1차 패스가 방금 고친 행은 재검사해도 결과가 같아 unchanged로만
 * 잡히고(applyRematch는 멱등), 아직 옛 로직으로 매칭된 행만 실제로 재배정된다.
 */
@Component
@Profile("rematch")
@RequiredArgsConstructor
public class TradeRematchCommandLineRunner implements CommandLineRunner {

    private static final String FULL_MODE_ARG = "--mode=full";

    private final TradeRematchRunner tradeRematchRunner;

    @Override
    public void run(String... args) {
        List<String> argList = Arrays.asList(args);
        if (argList.contains(FULL_MODE_ARG)) {
            tradeRematchRunner.rematchUpdatedBefore(LocalDateTime.now());
        } else {
            tradeRematchRunner.rematchUnmatched();
        }
    }
}
