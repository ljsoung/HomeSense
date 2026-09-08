package com.jiseong.homesense.favorite.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.common.exception.ComplexNotFoundException;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.favorite.dto.AddFavoritePropertyCommand;
import com.jiseong.homesense.favorite.dto.AddFavoriteRegionCommand;
import com.jiseong.homesense.favorite.dto.FavoritePropertyResponse;
import com.jiseong.homesense.favorite.dto.FavoritePropertySummaryResponse;
import com.jiseong.homesense.favorite.dto.FavoriteRegionResponse;
import com.jiseong.homesense.favorite.dto.FavoriteRegionSummaryResponse;
import com.jiseong.homesense.favorite.entity.FavoriteProperty;
import com.jiseong.homesense.favorite.entity.FavoriteRegion;
import com.jiseong.homesense.favorite.exception.AccessDeniedException;
import com.jiseong.homesense.favorite.exception.DuplicateFavoriteException;
import com.jiseong.homesense.favorite.exception.DuplicateFavoriteRegionException;
import com.jiseong.homesense.favorite.exception.FavoriteNotFoundException;
import com.jiseong.homesense.favorite.exception.HousingTypeUndeterminedException;
import com.jiseong.homesense.favorite.exception.MissingComplexIdException;
import com.jiseong.homesense.favorite.repository.FavoritePropertyRepository;
import com.jiseong.homesense.favorite.repository.FavoriteRegionRepository;
import com.jiseong.homesense.notification.repository.NotificationSettingRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.exception.RegionNotFoundException;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.region.service.RegionStatsCalculator;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * SVC-FAV-01. 관심 매물(복합 참조 없이 항상 complex_id, CLAUDE.md 최우선 규칙)과 관심 지역의
 * 등록·조회·삭제를 담당한다. 캐시는 적용하지 않는다(회원별 개인화 데이터, 설계서 명시).
 *
 * <p>getFavoriteRegions()는 설계서 Service 표가 지정한 대로 RGN 도메인의
 * {@link RegionStatsCalculator}를 그대로 재사용한다("RGN 도메인과 협력", SVC-CPX-01이
 * SVC-RCV-01.record()를 직접 호출하는 것과 같은 Service-to-Service 협력 패턴, CLAUDE.md 참고) — FAV가
 * RegionService 전체가 아니라 계산 컴포넌트 하나만 가져다 쓰는 이유는 RegionService.getInterestSummary()가
 * 이미 FavoriteRegionRepository를 직접 참조하는 반대 방향 의존을 갖고 있어(HOME-01이 FAV 서비스 계층이
 * 없던 RGN-01 시점에 먼저 구현됨), 두 서비스가 서로를 호출하게 하면 순환이 생기기 때문이다.
 *
 * <p>getFavoriteProperties()의 changeRate(전월 대비 변동률) 계산은 RegionStatsCalculator와 같은
 * 창(최근 1개월 vs 그 이전 1개월, 매매·미취소만) 로직을 legal_dong_cd 대신 complex_id로 계산한다 —
 * WHERE 절이 달라 RegionStatsCalculator를 그대로 재사용할 수 없고("도메인별 수직 패키지" 원칙,
 * SVC-TRD-01이 TradeSortCondition을 분리한 것과 같은 이유), 계산 자체는 몇 줄 되지 않아 별도
 * 컴포넌트로 추출하지 않고 이 클래스 안에 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FavoriteService {

    private static final int WINDOW_MONTHS = 1;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final FavoritePropertyRepository favoritePropertyRepository;
    private final FavoriteRegionRepository favoriteRegionRepository;
    private final ComplexRepository complexRepository;
    private final LegalDistrictCodeRepository legalDistrictCodeRepository;
    private final TradeRepository tradeRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;
    private final RegionStatsCalculator regionStatsCalculator;

    @Transactional(readOnly = true)
    public List<FavoritePropertySummaryResponse> getFavoriteProperties(Long userId) {
        return favoritePropertyRepository.findByUser_UserId(userId).stream()
                .map(favorite -> buildPropertySummary(userId, favorite))
                .toList();
    }

    public FavoritePropertyResponse addFavoriteProperty(Long userId, AddFavoritePropertyCommand cmd) {
        if (cmd.complexId() == null) {
            throw new MissingComplexIdException();
        }
        if (favoritePropertyRepository.existsByUser_UserIdAndComplex_ComplexId(userId, cmd.complexId())) {
            throw new DuplicateFavoriteException();
        }

        Complex complex = complexRepository.findById(cmd.complexId()).orElseThrow(ComplexNotFoundException::new);
        HousingType housingType = complex.inferHousingType();
        if (housingType == null) {
            throw new HousingTypeUndeterminedException();
        }

        User user = userRepository.getReferenceById(userId);
        FavoriteProperty favorite = FavoriteProperty.register(user, complex, housingType);
        try {
            // GenerationType.IDENTITY라 save()가 이 시점에 곧바로 INSERT를 실행한다 — UNIQUE 위반이
            // 있다면 여기서 즉시 터진다(AuthService.signup()과 같은 전제, CLAUDE.md "UNIQUE 제약
            // 동시성 회귀 테스트 원칙" 참고). existsBy() 조회 이후 이 INSERT 이전에 같은 조합으로
            // 동시에 들어온 다른 요청이 먼저 커밋을 끝낸 race condition을 이 catch가 잡아 번역한다.
            favoritePropertyRepository.save(favorite);
        } catch (DataIntegrityViolationException raceCondition) {
            throw new DuplicateFavoriteException();
        }
        return FavoritePropertyResponse.from(favorite);
    }

    public void removeFavoriteProperty(Long userId, Long favoritePropertyId) {
        FavoriteProperty favorite = favoritePropertyRepository.findById(favoritePropertyId)
                .orElseThrow(FavoriteNotFoundException::new);
        if (!favorite.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException();
        }
        favoritePropertyRepository.delete(favorite);
    }

    @Transactional(readOnly = true)
    public List<FavoriteRegionSummaryResponse> getFavoriteRegions(Long userId) {
        return favoriteRegionRepository.findByUser_UserId(userId).stream()
                .map(favorite -> FavoriteRegionSummaryResponse.of(favorite,
                        regionStatsCalculator.calculate(favorite.getLegalDistrictCode().getLegalDongCd())))
                .toList();
    }

    public FavoriteRegionResponse addFavoriteRegion(Long userId, AddFavoriteRegionCommand cmd) {
        if (favoriteRegionRepository.existsByUser_UserIdAndLegalDistrictCode_LegalDongCd(userId, cmd.legalDongCd())) {
            throw new DuplicateFavoriteRegionException();
        }

        // findById()가 아니라 이 predicate를 쓴다 — 비활성 코드나 시도/시군구 대표행(계층 상위 행,
        // eupmyeondongName=null)은 어떤 거래에도 매칭되지 않아 통계가 항상 빈 관심 지역이 등록되는
        // 문제를 막는다(searchByNameContaining()이 자동완성에서 거는 조건과 동일, Codex 코드리뷰 P2).
        LegalDistrictCode region = legalDistrictCodeRepository
                .findByLegalDongCdAndIsActiveTrueAndEupmyeondongNameIsNotNull(cmd.legalDongCd())
                .orElseThrow(RegionNotFoundException::new);

        User user = userRepository.getReferenceById(userId);
        FavoriteRegion favorite = FavoriteRegion.register(user, region);
        try {
            // addFavoriteProperty()와 같은 전제(GenerationType.IDENTITY) — save() 시점에 곧바로
            // UNIQUE 위반이 터진다.
            favoriteRegionRepository.save(favorite);
        } catch (DataIntegrityViolationException raceCondition) {
            throw new DuplicateFavoriteRegionException();
        }
        return FavoriteRegionResponse.from(favorite);
    }

    public void removeFavoriteRegion(Long userId, Long favoriteRegionId) {
        FavoriteRegion favorite = favoriteRegionRepository.findById(favoriteRegionId)
                .orElseThrow(FavoriteNotFoundException::new);
        if (!favorite.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException();
        }
        favoriteRegionRepository.delete(favorite);
    }

    private FavoritePropertySummaryResponse buildPropertySummary(Long userId, FavoriteProperty favorite) {
        Long complexId = favorite.getComplex().getComplexId();
        Trade recentTrade = tradeRepository
                .findFirstByComplex_ComplexIdAndCancelYnFalseOrderByDealDateDesc(complexId)
                .orElse(null);
        BigDecimal changeRate = calculatePropertyChangeRate(complexId);
        boolean hasNotificationSetting = notificationSettingRepository
                .existsByUser_UserIdAndFavoriteProperty_FavoritePropertyId(userId, favorite.getFavoritePropertyId());
        return FavoritePropertySummaryResponse.of(favorite, recentTrade, changeRate, hasNotificationSetting);
    }

    /** RegionStatsCalculator.calculate()와 같은 창·모집단(매매·미취소·최근 1개월 vs 그 이전 1개월)을 complex_id 기준으로 계산한다. */
    private BigDecimal calculatePropertyChangeRate(Long complexId) {
        LocalDate now = LocalDate.now(KST);
        LocalDate to = now.plusDays(1);
        LocalDate currentFrom = now.minusMonths(WINDOW_MONTHS);
        LocalDate previousFrom = now.minusMonths((long) WINDOW_MONTHS * 2);

        BigDecimal currentAvg = averageSaleAmount(complexId, currentFrom, to);
        BigDecimal previousAvg = averageSaleAmount(complexId, previousFrom, currentFrom);
        return changeRate(currentAvg, previousAvg);
    }

    private BigDecimal averageSaleAmount(Long complexId, LocalDate from, LocalDate to) {
        Optional<Double> avg = tradeRepository.findAverageSaleAmountByComplex(complexId, from, to);
        return avg.map(value -> BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP)).orElse(null);
    }

    private BigDecimal changeRate(BigDecimal currentAvg, BigDecimal previousAvg) {
        if (currentAvg == null || previousAvg == null || previousAvg.signum() == 0) {
            return null;
        }
        return currentAvg.subtract(previousAvg)
                .divide(previousAvg, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
