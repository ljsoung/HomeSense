package com.jiseong.homesense.complex.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.trade.entity.HousingType;

/**
 * DTL-01 단지 상세 응답. basicInfo(기본정보 요약)/extendedInfo(확장 상세정보, "상세정보 보기" 토글용)
 * 두 섹션으로 나눠 한 응답 안에 함께 담는다 — 프론트가 토글을 펼칠 때 별도 API를 다시 부르지 않는다.
 *
 * <p>matchPending은 legal_dong_cd가 NULL(법정동 매칭 대기)인 단지에서 true다 — DTL-01은 이 경우
 * 기본정보 카드 대신 안내 문구로 대체한다(UI정의서 7.3절).
 *
 * <p>housingType은 {@link Complex#inferHousingType()}로 environment complex_type 텍스트에서 환산한
 * 값이다 — SVC-CPX-01 캐시(complexDetailV2::{complexId})가 감싸는 이 응답 안에서만 값을 얻을 수 있어,
 * ComplexController가 SVC-RCV-01.record()를 호출할 때 별도 조회 없이 이 필드를 그대로 쓴다
 * (getDetail()이 @Cacheable이라 서비스 내부에서 record()를 부르면 캐시 히트 시 기록이 스킵되므로
 * Controller에서 두 서비스를 나란히 호출한다, CLAUDE.md SVC-RCV-01 절 참고).
 *
 * <p>Redis 캐시(complexDetailV2::{complexId})에 직렬화되므로 record라도 {@link Serializable}을
 * 구현할 필요는 없다 — CacheConfig가 GenericJacksonJsonRedisSerializer(JSON)를 쓰지 Java 직렬화를
 * 쓰지 않는다. 캐시 이름이 {@code complexDetail}이 아니라 {@code complexDetailV2}인 이유는
 * {@link com.jiseong.homesense.complex.service.ComplexDetailCache}의 클래스 주석 참고 — 이 레코드에
 * housingType 필드를 추가하며 캐시 이름을 버전업했다(Codex 코드리뷰 지적).
 */
public record ComplexDetailResponse(
        Long complexId,
        String complexName,
        String complexType,
        HousingType housingType,
        String sido,
        String sigungu,
        String dongRi,
        String legalDongAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        String locationPrecision,
        boolean matchPending,
        BasicInfo basicInfo,
        ExtendedInfo extendedInfo) {

    /** DTL-01 구성요소 3번 — 세대수·동수·사용승인일·시공사·총주차대수·최고층수 등. */
    public record BasicInfo(
            Integer householdCount,
            Short buildingCount,
            LocalDate approvalDate,
            String constructor,
            Integer totalParkingCount,
            Short highestFloor) {
    }

    /** DTL-01 구성요소 4번 — 관리방식·승강기·주차/전기차·보안/편의시설 등 "상세정보 보기" 토글 대상. */
    public record ExtendedInfo(
            String supplyType,
            Integer saleHouseholdCount,
            Integer rentalHouseholdCount,
            Integer publicRentalCount,
            Integer privateRentalCount,
            String managementType,
            String heatingType,
            String corridorType,
            String buildingStructure,
            String developer,
            String managementCompany,
            short elevatorPassengerCount,
            short elevatorCargoCount,
            short elevatorCombinedCount,
            Integer groundParkingCount,
            Integer undergroundParkingCount,
            Boolean evChargerGroundYn,
            Boolean evChargerUndergroundYn,
            Short evParkingGroundCount,
            Short evParkingUndergroundCount,
            Short cctvCount,
            Boolean homeNetworkYn,
            String communityFacilities,
            String residentAmenities,
            Short highestFloorRegistered,
            Short basementFloorCount,
            String officeAddress,
            String officePhone) {
    }

    public static ComplexDetailResponse from(Complex c) {
        boolean matchPending = c.getLegalDistrictCode() == null;

        BasicInfo basicInfo = new BasicInfo(
                c.getHouseholdCount(), c.getBuildingCount(), c.getApprovalDate(),
                c.getConstructor(), c.getTotalParkingCount(), c.getHighestFloor());

        ExtendedInfo extendedInfo = new ExtendedInfo(
                c.getSupplyType(), c.getSaleHouseholdCount(), c.getRentalHouseholdCount(),
                c.getPublicRentalCount(), c.getPrivateRentalCount(), c.getManagementType(),
                c.getHeatingType(), c.getCorridorType(), c.getBuildingStructure(), c.getDeveloper(),
                c.getManagementCompany(), c.getElevatorPassengerCount(), c.getElevatorCargoCount(),
                c.getElevatorCombinedCount(), c.getGroundParkingCount(), c.getUndergroundParkingCount(),
                c.getEvChargerGroundYn(), c.getEvChargerUndergroundYn(), c.getEvParkingGroundCount(),
                c.getEvParkingUndergroundCount(), c.getCctvCount(), c.getHomeNetworkYn(),
                c.getCommunityFacilities(), c.getResidentAmenities(), c.getHighestFloorRegistered(),
                c.getBasementFloorCount(), c.getOfficeAddress(), c.getOfficePhone());

        return new ComplexDetailResponse(
                c.getComplexId(), c.getComplexName(), c.getComplexType(), c.inferHousingType(), c.getSido(),
                c.getSigungu(), c.getDongRi(), c.getLegalDongAddress(), c.getLatitude(), c.getLongitude(),
                c.getLocationPrecision(), matchPending, basicInfo, extendedInfo);
    }
}
