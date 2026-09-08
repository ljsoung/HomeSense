package com.jiseong.homesense.complex.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.trade.entity.HousingType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "complex")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Complex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "complex_id")
    private Long complexId;

    @Column(name = "source_complex_cd", nullable = false, unique = true, length = 20)
    private String sourceComplexCd;

    /**
     * 법정동 매칭 대기 단지는 NULL일 수 있다 — 그래도 단지 자체는 정상 노출되어야 하므로 선택 FK다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_dong_cd", nullable = true)
    private LegalDistrictCode legalDistrictCode;

    @Column(name = "complex_name", nullable = false, length = 100)
    private String complexName;

    @Column(name = "complex_type", nullable = false, length = 20)
    private String complexType;

    @Column(name = "sido", length = 20)
    private String sido;

    @Column(name = "sigungu", length = 20)
    private String sigungu;

    @Column(name = "dong_ri", length = 20)
    private String dongRi;

    @Column(name = "legal_dong_address", length = 200)
    private String legalDongAddress;

    @Column(name = "supply_type", length = 10)
    private String supplyType;

    @Column(name = "approval_date")
    private LocalDate approvalDate;

    @Column(name = "building_count")
    private Short buildingCount;

    @Column(name = "household_count")
    private Integer householdCount;

    @Column(name = "sale_household_count")
    private Integer saleHouseholdCount;

    @Column(name = "rental_household_count")
    private Integer rentalHouseholdCount;

    @Column(name = "public_rental_count")
    private Integer publicRentalCount;

    @Column(name = "private_rental_count")
    private Integer privateRentalCount;

    @Column(name = "management_type", length = 20)
    private String managementType;

    @Column(name = "heating_type", length = 20)
    private String heatingType;

    @Column(name = "corridor_type", length = 20)
    private String corridorType;

    @Column(name = "building_structure", length = 30)
    private String buildingStructure;

    @Column(name = "constructor", length = 100)
    private String constructor;

    @Column(name = "developer", length = 100)
    private String developer;

    @Column(name = "management_company", length = 100)
    private String managementCompany;

    @Column(name = "elevator_passenger_count", nullable = false)
    private short elevatorPassengerCount;

    @Column(name = "elevator_cargo_count", nullable = false)
    private short elevatorCargoCount;

    @Column(name = "elevator_combined_count", nullable = false)
    private short elevatorCombinedCount;

    @Column(name = "total_parking_count")
    private Integer totalParkingCount;

    @Column(name = "ground_parking_count")
    private Integer groundParkingCount;

    @Column(name = "underground_parking_count")
    private Integer undergroundParkingCount;

    @Column(name = "cctv_count")
    private Short cctvCount;

    @Column(name = "home_network_yn")
    private Boolean homeNetworkYn;

    @Column(name = "office_address", length = 200)
    private String officeAddress;

    @Column(name = "office_phone", length = 20)
    private String officePhone;

    @Column(name = "community_facilities", length = 500)
    private String communityFacilities;

    @Column(name = "resident_amenities", length = 500)
    private String residentAmenities;

    @Column(name = "highest_floor")
    private Short highestFloor;

    @Column(name = "highest_floor_registered")
    private Short highestFloorRegistered;

    @Column(name = "basement_floor_count")
    private Short basementFloorCount;

    @Column(name = "ev_charger_ground_yn")
    private Boolean evChargerGroundYn;

    @Column(name = "ev_charger_underground_yn")
    private Boolean evChargerUndergroundYn;

    @Column(name = "ev_parking_ground_count")
    private Short evParkingGroundCount;

    @Column(name = "ev_parking_underground_count")
    private Short evParkingUndergroundCount;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "location_precision", length = 10)
    private String locationPrecision;

    @Column(name = "data_updated_at", nullable = false)
    private LocalDate dataUpdatedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * complex_type은 원본 xlsx의 자유 텍스트("아파트" 등)라 trade.housing_type과는 별도 컬럼이고,
     * 엔티티정의서 4.2절 실사용 감사에 따르면 이 값이 원본 미기재(NULL)인 행이 약 0.48%(105건)
     * 존재한다. SVC-RCV-01.record()가 recent_view.housing_type(NOT NULL)을 채우려면 이 값을
     * enum으로 환산해야 하는데, "아파트" 외의 정확한 원본 표기(연립다세대 쪽 문구)를 실제 xlsx로
     * 아직 확인하지 못했다 — MVP가 APT/VILLA 2종뿐이라는 CHECK 제약을 이용해 "아파트"가 아니면
     * 전부 VILLA로 취급한다(지성 확인 필요). 원본이 NULL인 105건은 VILLA로 임의 추정하지 않고
     * null을 그대로 반환한다 — 호출부(RecentViewService.record())가 null이면 조용히 기록을
     * 스킵해, 잘못된 housing_type을 확정적으로 저장하는 것보다 "이 105건은 기록하지 않는다"를
     * 택했다. 원본 표기가 여러 형태로 나뉘어 있다고 밝혀지면 이 메서드만 수정하면 된다.
     */
    public HousingType inferHousingType() {
        if (complexType == null) {
            return null;
        }
        return "아파트".equals(complexType) ? HousingType.APT : HousingType.VILLA;
    }
}
