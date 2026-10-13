import { getComplexDetail } from '../complex/api';
import type { ComplexDetailResponse, ComplexMapPointResponse } from '../complex/types';
import type { SearchFilters } from '../search/searchParams';
import { searchComplexesInBounds } from './api';
import { buildMapApiQuery, type MapBounds } from './mapParams';

type RepresentativeKey =
  | 'representativeHousingType'
  | 'representativeDealCategory'
  | 'representativeDealDate'
  | 'representativeAmount'
  | 'representativeArea';

/**
 * 지도에 그리는 단지 하나. 범위 조회 응답 항목(ComplexMapPointResponse)이거나, 선택한 단지가 응답에 없어 상세 API로 채운
 * 항목이다. 후자는 지금 필터에 맞는 대표 거래가 없을 수 있어 대표 거래 필드가 빠질 수 있다 — 그때 핀은 단지명을, 팝업은
 * "거래 정보 없음"을 보인다.
 */
export type MapPinItem = Omit<ComplexMapPointResponse, RepresentativeKey> &
  Partial<Pick<ComplexMapPointResponse, RepresentativeKey>>;

export type MapPinTrade = MapPinItem & Pick<ComplexMapPointResponse, RepresentativeKey>;

export function hasRepresentativeTrade(item: MapPinItem): item is MapPinTrade {
  return (
    item.representativeDealCategory != null &&
    item.representativeAmount != null &&
    item.representativeArea != null &&
    item.representativeDealDate != null
  );
}

/** 상세 응답으로 핀 항목을 만든다. 좌표가 없으면 지도에 둘 수 없어 null. */
export function pointFromDetail(detail: ComplexDetailResponse): MapPinItem | null {
  if (detail.latitude == null || detail.longitude == null) return null;
  return {
    complexId: detail.complexId,
    complexName: detail.complexName,
    sido: detail.sido,
    sigungu: detail.sigungu,
    dongRi: detail.dongRi,
    householdCount: detail.basicInfo?.householdCount,
    buildingCount: detail.basicInfo?.buildingCount,
    approvalDate: detail.basicInfo?.approvalDate,
    representativeHousingType: detail.housingType ?? undefined,
    matchMethod: detail.matchMethod,
    precisionBadge: detail.precisionBadge,
    latitude: detail.latitude,
    longitude: detail.longitude,
    locationPrecision: detail.locationPrecision,
  };
}

/** 단지 좌표 주변의 아주 좁은 범위(약 ±50m) — 그 단지 하나만 범위 조회로 다시 받으려고 쓴다. */
export function boundsAround(lat: number, lng: number, delta = 0.0005): MapBounds {
  return { swLat: lat - delta, swLng: lng - delta, neLat: lat + delta, neLng: lng + delta };
}

/**
 * 선택한 단지가 지도 범위 조회 응답에 없을 때(상한을 넘어 잘림, DTL-01 딥링크로 넓은 레벨 진입) 그 단지만 따로 받는다.
 * 1) 상세 API(GET /api/complexes/{id})로 좌표·이름·배지를 받고, 2) 그 좌표 주변의 좁은 범위로 같은 필터의 지도 API를 한 번
 * 더 불러 팝업에 쓸 대표 거래(필터 기준, SRCH-01 카드와 같은 규칙)를 받는다. 2)에서 못 찾으면(필터에 맞는 거래 없음)
 * 상세만으로 만든 항목을 쓴다. 좌표가 없거나 상세를 못 받으면 null.
 */
export async function fetchSelectedComplex(complexId: number, filters: SearchFilters, signal?: AbortSignal): Promise<MapPinItem | null> {
  const detail = await getComplexDetail(complexId, signal);
  const fromDetail = pointFromDetail(detail);
  if (!fromDetail) return null;
  try {
    const nearby = await searchComplexesInBounds(
      buildMapApiQuery(filters, boundsAround(fromDetail.latitude, fromDetail.longitude)),
      signal,
    );
    return nearby.items.find((item) => item.complexId === complexId) ?? fromDetail;
  } catch (error) {
    if (signal?.aborted) throw error;
    return fromDetail;
  }
}
