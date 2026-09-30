import type { ComplexBasicInfo, ComplexExtendedInfo } from '../../features/complex/types';

export const NO_INFO = '정보 없음';

type FieldKind = { type: 'count'; unit: string } | { type: 'text' } | { type: 'boolean' };

interface FieldDef {
  key: keyof ComplexExtendedInfo;
  label: string;
  kind: FieldKind;
}

interface GroupDef {
  title: string;
  fields: FieldDef[];
}

const count = (unit: string): FieldKind => ({ type: 'count', unit });
const text: FieldKind = { type: 'text' };
const yesNo: FieldKind = { type: 'boolean' };

/**
 * 상세정보 토글(DTL-01 구성요소 4)의 6그룹 — 28개 필드(지성 확정, 2026-09-30, CLAUDE.md "DTL-01 백엔드 보강" 절).
 * Figma는 5그룹·샘플 라벨이라 이 구성과 다르다(디자인 이탈). 라벨은 테이블정의서 컬럼 의미를 따른다.
 */
export const DETAIL_GROUPS: GroupDef[] = [
  {
    title: '분양/세대구성',
    fields: [
      { key: 'supplyType', label: '분양형태', kind: text },
      { key: 'saleHouseholdCount', label: '분양 세대수', kind: count('세대') },
      { key: 'rentalHouseholdCount', label: '임대 세대수', kind: count('세대') },
      { key: 'publicRentalCount', label: '공공임대 세대수', kind: count('세대') },
      { key: 'privateRentalCount', label: '민간임대 세대수', kind: count('세대') },
      { key: 'developer', label: '시행사', kind: text },
    ],
  },
  {
    title: '관리방식',
    fields: [
      { key: 'managementType', label: '관리방식', kind: text },
      { key: 'managementCompany', label: '관리업체', kind: text },
    ],
  },
  {
    title: '승강기',
    fields: [
      { key: 'elevatorPassengerCount', label: '승객용', kind: count('대') },
      { key: 'elevatorCargoCount', label: '화물용', kind: count('대') },
      { key: 'elevatorCombinedCount', label: '승객·화물 겸용', kind: count('대') },
    ],
  },
  {
    title: '주차/전기차',
    fields: [
      { key: 'groundParkingCount', label: '지상 주차', kind: count('대') },
      { key: 'undergroundParkingCount', label: '지하 주차', kind: count('대') },
      { key: 'evChargerGroundYn', label: '지상 전기차 충전기', kind: yesNo },
      { key: 'evChargerUndergroundYn', label: '지하 전기차 충전기', kind: yesNo },
      { key: 'evParkingGroundCount', label: '지상 전기차 주차', kind: count('대') },
      { key: 'evParkingUndergroundCount', label: '지하 전기차 주차', kind: count('대') },
    ],
  },
  {
    title: '보안/편의시설',
    fields: [
      { key: 'cctvCount', label: 'CCTV', kind: count('대') },
      { key: 'homeNetworkYn', label: '홈네트워크', kind: yesNo },
      { key: 'communityFacilities', label: '부대복리시설', kind: text },
      { key: 'residentAmenities', label: '편의시설', kind: text },
    ],
  },
  {
    title: '관리사무소 및 건물구조',
    fields: [
      { key: 'officeAddress', label: '관리사무소 주소', kind: text },
      { key: 'officePhone', label: '관리사무소 연락처', kind: text },
      { key: 'corridorType', label: '복도유형', kind: text },
      { key: 'buildingStructure', label: '건물구조', kind: text },
      { key: 'heatingType', label: '난방방식', kind: text },
      { key: 'highestFloorRegistered', label: '최고층(건축물대장)', kind: count('층') },
      { key: 'basementFloorCount', label: '지하층수', kind: count('층') },
    ],
  },
];

export interface DetailRow {
  label: string;
  /** 표시값. 값이 없으면 null(화면은 "정보 없음"을 보조 텍스트 색으로 그린다). */
  value: string | null;
}

export interface DetailGroupView {
  title: string;
  rows: DetailRow[];
}

function formatField(kind: FieldKind, raw: unknown): string | null {
  if (raw === null || raw === undefined || raw === '') return null;
  switch (kind.type) {
    case 'count':
      return typeof raw === 'number' ? `${raw.toLocaleString('ko-KR')}${kind.unit}` : null;
    case 'boolean':
      return raw ? '있음' : '없음';
    case 'text':
      return String(raw);
  }
}

/** 세대당 주차대수 = 총주차대수 ÷ 세대수(소수 둘째 자리). 두 값이 모두 있고 세대수가 0보다 클 때만. */
export function parkingPerHousehold(basic: ComplexBasicInfo): string | null {
  const { totalParkingCount, householdCount } = basic;
  if (totalParkingCount == null || householdCount == null || householdCount <= 0) return null;
  return `${(totalParkingCount / householdCount).toFixed(2)}대`;
}

/**
 * 화면에 그릴 그룹 목록. 모든 값이 없는 그룹은 뺀다. 주차/전기차 그룹 끝에는 파생값(세대당 주차대수)을 둔다 —
 * 입력이 모두 있을 때만 값이 있다.
 */
export function buildDetailGroups(extended: ComplexExtendedInfo, basic: ComplexBasicInfo): DetailGroupView[] {
  const groups: DetailGroupView[] = DETAIL_GROUPS.map((group) => ({
    title: group.title,
    rows: group.fields.map((field) => ({ label: field.label, value: formatField(field.kind, extended[field.key]) })),
  }));
  const parking = groups.find((g) => g.title === '주차/전기차');
  parking?.rows.push({ label: '세대당 주차대수', value: parkingPerHousehold(basic) });
  return groups.filter((g) => g.rows.some((row) => row.value !== null));
}
