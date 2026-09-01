package com.jiseong.homesense.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.region.entity.LegalDistrictCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trade")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trade_id")
    private Long tradeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_type", nullable = false, length = 10)
    private HousingType housingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "deal_category", nullable = false, length = 10)
    private DealCategory dealCategory;

    /**
     * RENT 행에서만 값을 가진다(SALE 행은 NULL).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rent_type", length = 10)
    private RentType rentType;

    @Column(name = "dataset_id", nullable = false, length = 20)
    private String datasetId;

    @Column(name = "sgg_cd", nullable = false, length = 5)
    private String sggCd;

    /**
     * sgg_cd 기반 매칭 결과. 매칭 실패 시 NULL이어도 실거래 자체는 정상 적재·노출된다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_dong_cd", nullable = true)
    private LegalDistrictCode legalDistrictCode;

    @Column(name = "umd_nm", length = 60)
    private String umdNm;

    /**
     * 단지 마스터 매칭 성공 시에만 채워진다(FR-2.5).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complex_id", nullable = true)
    private Complex complex;

    @Column(name = "building_name", length = 100)
    private String buildingName;

    @Column(name = "jibun", length = 20)
    private String jibun;

    @Column(name = "road_address", length = 200)
    private String roadAddress;

    @Column(name = "exclu_use_area", nullable = false, precision = 6, scale = 2)
    private BigDecimal excluUseArea;

    @Column(name = "floor")
    private Short floor;

    @Column(name = "build_year")
    private Short buildYear;

    @Column(name = "deal_date", nullable = false)
    private LocalDate dealDate;

    @Column(name = "deal_amount")
    private Long dealAmount;

    @Column(name = "deposit_amount")
    private Long depositAmount;

    @Column(name = "monthly_rent_amount")
    private Long monthlyRentAmount;

    @Column(name = "apt_dong", length = 20)
    private String aptDong;

    @Column(name = "dealing_type", length = 10)
    private String dealingType;

    @Column(name = "agent_sgg_nm", length = 30)
    private String agentSggNm;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "seller_type", length = 10)
    private String sellerType;

    @Column(name = "buyer_type", length = 10)
    private String buyerType;

    @Column(name = "land_lease_yn")
    private Boolean landLeaseYn;

    @Column(name = "cancel_yn", nullable = false)
    private boolean cancelYn;

    @Column(name = "cancel_date")
    private LocalDate cancelDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method", nullable = true, length = 12)
    private MatchMethod matchMethod;

    @Column(name = "match_confidence", precision = 4, scale = 3)
    private BigDecimal matchConfidence;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "location_precision", length = 10)
    private String locationPrecision;

    @Column(name = "dedup_hash", nullable = false, unique = true, length = 64)
    private String dedupHash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void cancel(LocalDate cancelDate) {
        this.cancelYn = true;
        this.cancelDate = cancelDate;
    }

    /**
     * BAT-LOD-01이 dedup_hash로 기존 행을 찾았을 때(upsert의 UPDATE 경로) 갱신하는 필드만 모은다.
     * 등기 완료 후 동정보가 추가 공개되거나(apt_dong), 거래가 사후 신고 취소되는 등(cancel_yn/cancel_date)
     * 최초 적재 이후에도 값이 바뀔 수 있는 필드만 대상이며, complex/legalDistrictCode/matchMethod
     * 같은 매칭 결과나 dedup_hash 자체는 최초 적재 시점 값을 그대로 유지한다(재매칭은 이 메서드의 책임이 아니다).
     */
    public void applyLateUpdate(boolean cancelYn, LocalDate cancelDate, LocalDate registrationDate, String aptDong) {
        this.cancelYn = cancelYn;
        this.cancelDate = cancelDate;
        this.registrationDate = registrationDate;
        this.aptDong = aptDong;
    }
}
