export type HousingType = 'APT' | 'VILLA';
export type DealCategory = 'SALE' | 'RENT';
export type RentType = 'JEONSE' | 'WOLSE';
export type MatchMethod = 'EXACT' | 'SIMILAR';

/**
 * ComplexSummaryResponse.java 실제 필드 그대로. 대표 거래 금액은 dealCategory가 SALE이면
 * 매매금액, RENT면 보증금이다(representativeAmount 자체가 이미 그 의미로 내려온다). 이미지 URL
 * 필드는 백엔드에 아예 없다(Complex 엔티티 확인 완료, ComplexCard의 자리표시 썸네일 참고).
 *
 * matchMethod/floor는 대표 거래(Trade) 엔티티에서 그대로 옮겨오는 nullable 필드다(2026-09-17,
 * CPX-RCV-RGN 카드 표시 필드 보강 작업으로 백엔드에 추가됨 — 이전엔 이 두 필드 자체가 DTO에
 * 없어 CLAUDE.md SCR-HOME-01 절이 "프론트에서 생략"으로 확정했던 갭이다). match_method는 매칭
 * 실패 시, floor는 원본 xlsx 미기재 시 각각 NULL로 내려온다 — 두 경우 모두 UI는 해당 배지/세그먼트를
 * 렌더링하지 않는다(DataTrustBadge/ComplexCard 참고).
 *
 * rentType/monthlyRentAmount는 SRCH-01 백엔드 선행작업(2026-09-23, "단지 검색 지역코드·키워드·
 * 거래유형")으로 추가됐다 — dealCategory=SALE이면 `non_null` 직렬화 설정 때문에 이 두 키 자체가
 * 응답 JSON에 없다(라이브 curl로 확인). 그래서 optional(`?`)이 아니라 `| undefined`가 아닌
 * `?:`(선택 프로퍼티)로 선언해 "키 부재"와 "null"을 굳이 구분하지 않고 둘 다 falsy로 취급한다.
 */
export interface ComplexSummaryResponse {
  complexId: number;
  complexName: string;
  sido: string;
  sigungu: string;
  dongRi: string;
  householdCount: number;
  buildingCount: number;
  approvalDate: string;
  representativeHousingType: HousingType;
  representativeDealCategory: DealCategory;
  representativeDealDate: string;
  representativeAmount: number;
  representativeArea: number;
  matchMethod: MatchMethod | null;
  floor: number | null;
  rentType?: RentType;
  monthlyRentAmount?: number;
}

/**
 * GET /api/complexes/{id} — ComplexDetailResponse.java 실제 필드 그대로(2026-09-30 DTL-01 백엔드 보강 반영).
 * `non_null` 직렬화라 null 필드는 키 자체가 빠질 수 있어 nullable 필드는 모두 선택 프로퍼티로 둔다.
 * matchMethod는 대표 거래(취소되지 않은 가장 최근 거래) 기준이고 거래가 없으면 없다. legalDongCd는 매칭 대기
 * 단지(matchPending)면 없다.
 */
export interface ComplexDetailResponse {
  complexId: number;
  complexName: string;
  complexType?: string | null;
  housingType?: HousingType | null;
  sido?: string | null;
  sigungu?: string | null;
  dongRi?: string | null;
  legalDongCd?: string | null;
  legalDongAddress?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  locationPrecision?: string | null;
  matchPending: boolean;
  matchMethod?: MatchMethod | null;
  basicInfo: ComplexBasicInfo;
  extendedInfo: ComplexExtendedInfo;
}

/** DTL-01 구성요소 3 — 기본정보 요약. */
export interface ComplexBasicInfo {
  householdCount?: number | null;
  buildingCount?: number | null;
  approvalDate?: string | null;
  constructor?: string | null;
  totalParkingCount?: number | null;
  highestFloor?: number | null;
}

/** DTL-01 구성요소 4 — 상세정보 토글(28개 필드, 6그룹은 pages/complex/detailGroups.ts). */
export interface ComplexExtendedInfo {
  supplyType?: string | null;
  saleHouseholdCount?: number | null;
  rentalHouseholdCount?: number | null;
  publicRentalCount?: number | null;
  privateRentalCount?: number | null;
  managementType?: string | null;
  heatingType?: string | null;
  corridorType?: string | null;
  buildingStructure?: string | null;
  developer?: string | null;
  managementCompany?: string | null;
  elevatorPassengerCount?: number | null;
  elevatorCargoCount?: number | null;
  elevatorCombinedCount?: number | null;
  groundParkingCount?: number | null;
  undergroundParkingCount?: number | null;
  evChargerGroundYn?: boolean | null;
  evChargerUndergroundYn?: boolean | null;
  evParkingGroundCount?: number | null;
  evParkingUndergroundCount?: number | null;
  cctvCount?: number | null;
  homeNetworkYn?: boolean | null;
  communityFacilities?: string | null;
  residentAmenities?: string | null;
  highestFloorRegistered?: number | null;
  basementFloorCount?: number | null;
  officeAddress?: string | null;
  officePhone?: string | null;
}
