package com.jiseong.homesense.complex.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jiseong.homesense.region.entity.LegalDistrictCode;

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
    private Integer buildingCount;

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
    private int elevatorPassengerCount;

    @Column(name = "elevator_cargo_count", nullable = false)
    private int elevatorCargoCount;

    @Column(name = "elevator_combined_count", nullable = false)
    private int elevatorCombinedCount;

    @Column(name = "total_parking_count")
    private Integer totalParkingCount;

    @Column(name = "ground_parking_count")
    private Integer groundParkingCount;

    @Column(name = "underground_parking_count")
    private Integer undergroundParkingCount;

    @Column(name = "cctv_count")
    private Integer cctvCount;

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
    private Integer highestFloor;

    @Column(name = "highest_floor_registered")
    private Integer highestFloorRegistered;

    @Column(name = "basement_floor_count")
    private Integer basementFloorCount;

    @Column(name = "ev_charger_ground_yn")
    private Boolean evChargerGroundYn;

    @Column(name = "ev_charger_underground_yn")
    private Boolean evChargerUndergroundYn;

    @Column(name = "ev_parking_ground_count")
    private Integer evParkingGroundCount;

    @Column(name = "ev_parking_underground_count")
    private Integer evParkingUndergroundCount;

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
}
