package com.jiseong.homesense.batch.notifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jiseong.homesense.batch.notifier.NotificationTriggerResult.SkipReason;
import com.jiseong.homesense.batch.notifier.NotificationTriggerResult.Tally;
import com.jiseong.homesense.common.config.NotifierProperties;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.notification.entity.Notification;
import com.jiseong.homesense.notification.entity.NotificationType;
import com.jiseong.homesense.notification.repository.NotificationRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.service.RegionCodePrefixResolver;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.user.entity.User;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

/**
 * BAT-NTF-01. BAT-SCH-01 조합 순회가 끝난 뒤 이번 런과 연관된 알림 설정만 평가해 notification을 만든다
 * (FR-6.1, FR-6.2). 이메일 발송(BAT-MAIL-01)은 하지 않는다 — 알림은 {@code sent_at=NULL}로 만들어지고 그것이 곧
 * 발송 대기열이다(D1·D8). 결정 근거는 CLAUDE.md "BAT-NTF-01 구현 결정 사항".
 *
 * <ul>
 *   <li>NEW_TRADE: 이번 런에 신규 INSERT된 해제되지 않은 거래(매매·전월세)가 있으면 설정당 1건으로 묶어 만든다.
 *       대표 거래(최신 계약일, 같으면 큰 trade_id)를 trade_id로 건다(D3).</li>
 *   <li>PRICE_CHANGE: 신규 매매의 3.3㎡당 평균을 직전 N개월(런 이전 적재분) 평균과 비교해 반올림 변동률이
 *       임계치 이상이면 만든다. 기준 표본이 최소보다 적으면 평가하지 않는다(D4).</li>
 *   <li>"신규" = {@code trade.created_at >= 런 시작 시각} — 같은 날 다시 돌려도 이전 런 적재분은 신규가 아니라
 *       중복 알림이 생기지 않는다(D2).</li>
 *   <li>ACTIVE 회원만 대상이고(D6), email_alert_yn과 무관하게 만든다(D8).</li>
 * </ul>
 *
 * <p>알림 저장은 설정 {@code chunkSize}건씩 한 트랜잭션으로 커밋한다. 청크 안에서 저장이 하나라도 실패하면 그
 * 트랜잭션은 rollback-only가 돼 청크 전체가 날아가므로(CLAUDE.md "REQUIRES_NEW 격리" 절과 같은 함정), 그
 * 청크만 설정 1건씩 개별 트랜잭션으로 다시 저장해 실패한 설정만 빼고 나머지를 살린다(D9).
 */
@Slf4j
@Component
public class WatchConditionEvaluator {

    private final WatchConditionQuery query;
    private final RegionCodePrefixResolver regionCodePrefixResolver;
    private final NotificationRepository notificationRepository;
    private final EntityManager entityManager;
    private final NotifierProperties properties;
    private final TransactionTemplate transactionTemplate;

    public WatchConditionEvaluator(WatchConditionQuery query, RegionCodePrefixResolver regionCodePrefixResolver,
                                   NotificationRepository notificationRepository, EntityManager entityManager,
                                   NotifierProperties properties, PlatformTransactionManager transactionManager) {
        this.query = query;
        this.regionCodePrefixResolver = regionCodePrefixResolver;
        this.notificationRepository = notificationRepository;
        this.entityManager = entityManager;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public NotificationTriggerResult evaluateAfterLoad(NotificationTriggerContext ctx) {
        if (ctx.hasNoAffectedTargets()) {
            return NotificationTriggerResult.empty();
        }
        // 마이그레이션(schema/notification_sent_at_nullable.sql)이 빠진 DB에서는 알림 INSERT가 전부 거부된다 — 설정마다
        // 실패를 쌓는 대신 원인을 바로 알리고 평가를 건너뛴다. 오케스트레이터가 이 예외를 ERROR로 남기고 흡수한다.
        if (!query.isNotificationSentAtNullable()) {
            throw new IllegalStateException("notification.sent_at이 NOT NULL이라 알림을 만들 수 없다 — "
                    + "backend/src/main/resources/schema/notification_sent_at_nullable.sql을 이 DB에 적용해야 한다");
        }

        List<WatchTarget> targets = new ArrayList<>(query.findPropertyTargets(ctx.complexIds()));
        targets.addAll(query.findRegionTargets(LegalDongHierarchy.ancestorsAndSelf(ctx.legalDongCds())));
        if (targets.isEmpty()) {
            return logged(NotificationTriggerResult.empty());
        }

        Tally tally = new Tally();
        Map<WatchTarget, String> regionPrefixes = resolveRegionPrefixes(targets);
        List<NewTradeRow> newTrades = query.findNewTrades(ctx.runStartedAt(), ctx.legalDongCds());
        Map<WatchTarget, List<NewTradeRow>> newTradesByTarget = groupByTarget(targets, regionPrefixes, newTrades);
        Baselines baselines = loadBaselines(ctx, newTradesByTarget, regionPrefixes);

        List<Planned> plans = new ArrayList<>();
        for (WatchTarget target : targets) {
            tally.evaluated++;
            try {
                if (target.isRegion() && !regionPrefixes.containsKey(target)) {
                    tally.skip(SkipReason.REGION_UNRESOLVED);
                    continue;
                }
                List<NewTradeRow> trades = newTradesByTarget.getOrDefault(target, List.of());
                List<NotificationDraft> drafts = new ArrayList<>(2);
                planNewTrade(target, trades, tally).ifPresent(drafts::add);
                planPriceChange(target, trades, baselines.of(target, regionPrefixes), tally).ifPresent(drafts::add);
                if (!drafts.isEmpty()) {
                    plans.add(new Planned(target, drafts));
                }
            } catch (RuntimeException e) {
                tally.failed++;
                logTargetFailure(target, e);
            }
        }

        for (List<Planned> chunk : WatchConditionQuery.partition(plans, properties.chunkSize())) {
            save(chunk, tally);
        }
        return logged(tally.toResult());
    }

    private Optional<NotificationDraft> planNewTrade(WatchTarget target, List<NewTradeRow> trades, Tally tally) {
        if (!target.newTradeAlert()) {
            tally.skip(SkipReason.NEW_TRADE_ALERT_OFF);
            return Optional.empty();
        }
        if (trades.isEmpty()) {
            tally.skip(SkipReason.NO_NEW_TRADE);
            return Optional.empty();
        }
        NewTradeRow representative = trades.get(0);
        for (NewTradeRow trade : trades) {
            if (trade.isMoreRecentThan(representative)) {
                representative = trade;
            }
        }
        return Optional.of(new NotificationDraft(NotificationType.NEW_TRADE,
                NotificationTextFormatter.newTradeTitle(target.targetName(), trades.size()),
                NotificationTextFormatter.newTradeMessage(representative, trades.size()),
                representative.tradeId()));
    }

    private Optional<NotificationDraft> planPriceChange(WatchTarget target, List<NewTradeRow> trades,
                                                        PriceAggregate baseline, Tally tally) {
        PriceAggregate fresh = PriceAggregate.EMPTY;
        for (NewTradeRow trade : trades) {
            if (trade.isPricedSale()) {
                fresh = fresh.plus(trade);
            }
        }
        if (fresh.count() == 0) {
            tally.skip(SkipReason.NO_NEW_SALE);
            return Optional.empty();
        }
        if (baseline.count() < properties.minBaselineSamples()) {
            tally.skip(SkipReason.BASELINE_TOO_SMALL);
            return Optional.empty();
        }
        BigDecimal baselineAvg = baseline.average();
        BigDecimal freshAvg = fresh.average();
        Optional<BigDecimal> rate = PriceChangeCalculator.changeRate(baselineAvg, freshAvg);
        if (rate.isEmpty() || !PriceChangeCalculator.exceedsThreshold(rate.get(), target.thresholdPct())) {
            tally.skip(SkipReason.BELOW_THRESHOLD);
            return Optional.empty();
        }
        return Optional.of(new NotificationDraft(NotificationType.PRICE_CHANGE,
                NotificationTextFormatter.priceChangeTitle(target.targetName(), rate.get()),
                NotificationTextFormatter.priceChangeMessage(properties.baselineMonths(), baselineAvg,
                        (int) fresh.count(), freshAvg),
                null));
    }

    /** 관심 지역 코드 → 집계 prefix. 활성 법정동이 아니면 빠지고 그 설정은 REGION_UNRESOLVED로 스킵된다. */
    private Map<WatchTarget, String> resolveRegionPrefixes(List<WatchTarget> targets) {
        Map<WatchTarget, String> prefixes = new HashMap<>();
        for (WatchTarget target : targets) {
            if (target.isRegion()) {
                regionCodePrefixResolver.prefixOf(target.legalDongCd())
                        .ifPresent(prefix -> prefixes.put(target, prefix));
            }
        }
        return prefixes;
    }

    private Map<WatchTarget, List<NewTradeRow>> groupByTarget(List<WatchTarget> targets,
                                                              Map<WatchTarget, String> regionPrefixes,
                                                              List<NewTradeRow> newTrades) {
        Map<Long, List<NewTradeRow>> byComplex = new HashMap<>();
        for (NewTradeRow trade : newTrades) {
            if (trade.complexId() != null) {
                byComplex.computeIfAbsent(trade.complexId(), k -> new ArrayList<>()).add(trade);
            }
        }
        Map<String, List<NewTradeRow>> byPrefix = new HashMap<>();
        for (String prefix : new LinkedHashSet<>(regionPrefixes.values())) {
            List<NewTradeRow> matched = new ArrayList<>();
            for (NewTradeRow trade : newTrades) {
                if (trade.legalDongCd() != null && trade.legalDongCd().startsWith(prefix)) {
                    matched.add(trade);
                }
            }
            byPrefix.put(prefix, matched);
        }

        Map<WatchTarget, List<NewTradeRow>> result = new LinkedHashMap<>();
        for (WatchTarget target : targets) {
            List<NewTradeRow> trades = target.isRegion()
                    ? byPrefix.getOrDefault(regionPrefixes.get(target), List.of())
                    : byComplex.getOrDefault(target.complexId(), List.of());
            result.put(target, trades);
        }
        return result;
    }

    /** 신규 매매가 있는 대상만 기준 평균을 조회한다. */
    private Baselines loadBaselines(NotificationTriggerContext ctx, Map<WatchTarget, List<NewTradeRow>> tradesByTarget,
                                    Map<WatchTarget, String> regionPrefixes) {
        Set<Long> complexIds = new LinkedHashSet<>();
        Set<String> prefixes = new LinkedHashSet<>();
        tradesByTarget.forEach((target, trades) -> {
            if (trades.stream().noneMatch(NewTradeRow::isPricedSale)) {
                return;
            }
            if (target.isRegion()) {
                prefixes.add(regionPrefixes.get(target));
            } else {
                complexIds.add(target.complexId());
            }
        });
        LocalDate from = ctx.runDate().minusMonths(properties.baselineMonths());
        LocalDate to = ctx.runDate().plusDays(1);
        return new Baselines(
                complexIds.isEmpty() ? Map.of() : query.baselineByComplex(complexIds, from, to, ctx.runStartedAt()),
                prefixes.isEmpty() ? Map.of() : query.baselineByPrefix(prefixes, from, to, ctx.runStartedAt()));
    }

    private void save(List<Planned> chunk, Tally tally) {
        try {
            transactionTemplate.executeWithoutResult(status -> chunk.forEach(this::persist));
            chunk.forEach(planned -> count(planned, tally));
        } catch (RuntimeException chunkFailure) {
            log.atWarn()
                    .addKeyValue("programId", "BAT-NTF-01")
                    .addKeyValue("chunkSize", chunk.size())
                    .setCause(chunkFailure)
                    .log("BAT-NTF-01 알림 청크 저장 실패, 설정 1건씩 다시 저장한다");
            for (Planned planned : chunk) {
                try {
                    transactionTemplate.executeWithoutResult(status -> persist(planned));
                    count(planned, tally);
                } catch (RuntimeException e) {
                    tally.failed++;
                    logTargetFailure(planned.target(), e);
                }
            }
        }
    }

    private void persist(Planned planned) {
        WatchTarget target = planned.target();
        for (NotificationDraft draft : planned.drafts()) {
            notificationRepository.save(Notification.builder()
                    .user(entityManager.getReference(User.class, target.userId()))
                    .notificationType(draft.type())
                    .title(draft.title())
                    .message(draft.message())
                    .complex(target.isRegion() ? null : entityManager.getReference(Complex.class, target.complexId()))
                    .legalDistrictCode(target.isRegion()
                            ? entityManager.getReference(LegalDistrictCode.class, target.legalDongCd()) : null)
                    .trade(draft.tradeId() != null ? entityManager.getReference(Trade.class, draft.tradeId()) : null)
                    .build());
        }
    }

    private static void count(Planned planned, Tally tally) {
        for (NotificationDraft draft : planned.drafts()) {
            if (draft.type() == NotificationType.NEW_TRADE) {
                tally.newTrade++;
            } else {
                tally.priceChange++;
            }
        }
    }

    private void logTargetFailure(WatchTarget target, RuntimeException e) {
        log.atWarn()
                .addKeyValue("programId", "BAT-NTF-01")
                .addKeyValue("notificationSettingId", target.settingId())
                .setCause(e)
                .log("BAT-NTF-01 알림 설정 평가 실패, 다음 설정으로 진행한다");
    }

    private NotificationTriggerResult logged(NotificationTriggerResult result) {
        log.atInfo()
                .addKeyValue("programId", "BAT-NTF-01")
                .addKeyValue("evaluatedSettings", result.evaluatedSettings())
                .addKeyValue("newTradeCreated", result.newTradeCreated())
                .addKeyValue("priceChangeCreated", result.priceChangeCreated())
                .addKeyValue("skipped", result.skipped())
                .addKeyValue("failed", result.failed())
                .log("BAT-NTF-01 알림 평가 완료");
        return result;
    }

    private record NotificationDraft(NotificationType type, String title, String message, Long tradeId) {
    }

    private record Planned(WatchTarget target, List<NotificationDraft> drafts) {
    }

    private record Baselines(Map<Long, PriceAggregate> byComplex, Map<String, PriceAggregate> byPrefix) {

        PriceAggregate of(WatchTarget target, Map<WatchTarget, String> regionPrefixes) {
            PriceAggregate aggregate = target.isRegion()
                    ? byPrefix.get(regionPrefixes.get(target))
                    : byComplex.get(target.complexId());
            return aggregate != null ? aggregate : PriceAggregate.EMPTY;
        }
    }
}
