import type { FavoritePropertySummaryResponse, FavoriteRegionSummaryResponse } from '../../features/favorite/types';
import type { NotificationSettingItem, NotificationSettingResponse } from '../../features/notification/types';
import { describeNotificationBadge, sortFavorites } from '../favorites/favoritesModel';

/**
 * SCR-MY-03 알림 설정의 상태 계산 — 화면과 떼어 단위 테스트한다(결정 D3·D5·D7·D10, CLAUDE.md "SCR-MY-03" 절).
 * 대상은 관심 매물·관심 지역이고, 키는 `property:{id}` / `region:{id}`다(두 id 공간이 겹쳐도 구분되게).
 */

export type TargetKind = 'property' | 'region';

export interface SettingTarget {
  key: string;
  kind: TargetKind;
  id: number;
  name: string;
}

/** 폼 값 — 임계치(%)·신규거래 알림·이메일 수신. 저장 요청의 세 값과 같다. */
export interface FormValues {
  threshold: number;
  newTrade: boolean;
  email: boolean;
}

export type FormField = keyof FormValues;
export const FORM_FIELDS: readonly FormField[] = ['threshold', 'newTrade', 'email'];

/** 설정이 없는 대상의 기본값(D4) — 임계치 5%는 명세 값, 이 화면에서 저장한다는 것은 알림을 받겠다는 뜻이라 나머지는 켬. */
export const DEFAULT_VALUES: FormValues = { threshold: 5, newTrade: true, email: true };

export const THRESHOLD_MIN = 0;
export const THRESHOLD_MAX = 20;

export const propertyTargetKey = (id: number) => `property:${id}`;
export const regionTargetKey = (id: number) => `region:${id}`;

/** 지역 표시명 "시군구 읍면동"(예: "안성시 금도읍"). 시군구가 없는 곳(세종)은 시도로 대신한다. */
export function regionDisplayName(region: Pick<FavoriteRegionSummaryResponse, 'sidoName' | 'sigunguName' | 'eupmyeondongName'>): string {
  return [region.sigunguName ?? region.sidoName, region.eupmyeondongName].filter(Boolean).join(' ');
}

/** 대상 목록 — 관심 매물 다음 관심 지역, 각각 MY-02의 등록순(최근 등록 먼저). */
export function buildTargets(
  properties: readonly FavoritePropertySummaryResponse[],
  regions: readonly FavoriteRegionSummaryResponse[],
): SettingTarget[] {
  const sortedProperties = sortFavorites(properties, 'registered', (p) => ({
    id: p.favoritePropertyId,
    registeredAt: p.registeredAt,
    changeRate: null,
  }));
  const sortedRegions = sortFavorites(regions, 'registered', (r) => ({
    id: r.favoriteRegionId,
    registeredAt: r.registeredAt,
    changeRate: null,
  }));
  return [
    ...sortedProperties.map((p) => ({
      key: propertyTargetKey(p.favoritePropertyId),
      kind: 'property' as const,
      id: p.favoritePropertyId,
      name: p.complexName,
    })),
    ...sortedRegions.map((r) => ({
      key: regionTargetKey(r.favoriteRegionId),
      kind: 'region' as const,
      id: r.favoriteRegionId,
      name: regionDisplayName(r),
    })),
  ];
}

/** 저장된 설정을 대상 키로 묶는다. */
export function indexSettings(settings: readonly NotificationSettingResponse[]): Map<string, NotificationSettingResponse> {
  const map = new Map<string, NotificationSettingResponse>();
  for (const setting of settings) {
    if (setting.favoritePropertyId != null) map.set(propertyTargetKey(setting.favoritePropertyId), setting);
    else if (setting.favoriteRegionId != null) map.set(regionTargetKey(setting.favoriteRegionId), setting);
  }
  return map;
}

/** 대상의 저장된 값. 설정이 없으면 기본값(D4). */
export function savedValuesOf(setting: NotificationSettingResponse | undefined): FormValues {
  if (!setting) return DEFAULT_VALUES;
  return { threshold: setting.priceChangeThresholdPct, newTrade: setting.newTradeAlertYn, email: setting.emailAlertYn };
}

/** 대상 행의 보조 텍스트(D6) — MY-02 알림조건 배지와 같은 변환 함수를 쓰고, 설정이 없으면 "미설정". */
export function describeTargetSetting(setting: NotificationSettingResponse | undefined): string {
  return setting ? describeNotificationBadge(setting).label : '미설정';
}

export interface DerivedForm {
  values: FormValues;
  /** 사용자가 바꾸지 않았고, 선택한 대상들의 저장된 값이 서로 달라 기본값을 보이는 필드 — "대상마다 다름" 표시. */
  mixed: Record<FormField, boolean>;
  /** 선택한 대상들의 저장된 값이 한 필드라도 서로 다른가 — "선택한 대상 n곳에 같은 설정이 적용됩니다" 안내. */
  hadDifferences: boolean;
}

/**
 * 폼 값 계산(D3). 필드마다 사용자가 바꾼 값(overrides)이 있으면 그 값을 쓴다 — 선택을 바꿔도 입력한 값이 유지된다. 바꾸지 않은
 * 필드는 선택한 대상들의 저장된 값이 모두 같으면 그 값, 다르면 기본값 + "대상마다 다름". 선택이 없으면 기본값.
 */
export function deriveForm(
  selectedKeys: readonly string[],
  settingsByKey: ReadonlyMap<string, NotificationSettingResponse>,
  overrides: Partial<FormValues>,
): DerivedForm {
  const saved = selectedKeys.map((key) => savedValuesOf(settingsByKey.get(key)));
  const mixed: Record<FormField, boolean> = { threshold: false, newTrade: false, email: false };
  let hadDifferences = false;
  const pick = <F extends FormField>(field: F): FormValues[F] => {
    const differs = new Set(saved.map((v) => v[field])).size > 1;
    if (differs) hadDifferences = true;
    const override = overrides[field];
    if (override !== undefined) return override as FormValues[F];
    if (differs) {
      mixed[field] = true;
      return DEFAULT_VALUES[field];
    }
    return saved.length > 0 ? saved[0][field] : DEFAULT_VALUES[field];
  };
  const values: FormValues = { threshold: pick('threshold'), newTrade: pick('newTrade'), email: pick('email') };
  return { values, mixed, hadDifferences };
}

/** 저장하면 실제로 값이 바뀌는 대상(D5) — 설정이 없는 대상은 저장하면 행이 생기므로 항상 포함한다. */
export function changedTargets(
  selectedKeys: readonly string[],
  settingsByKey: ReadonlyMap<string, NotificationSettingResponse>,
  values: FormValues,
): string[] {
  return selectedKeys.filter((key) => {
    const setting = settingsByKey.get(key);
    if (!setting) return true;
    const saved = savedValuesOf(setting);
    return FORM_FIELDS.some((field) => saved[field] !== values[field]);
  });
}

export type SaveHint = 'noSelection' | 'noChange' | 'changed';

export const SAVE_HINT_TEXT: Record<SaveHint, string> = {
  noSelection: '알림을 받을 대상을 먼저 선택하세요',
  noChange: '변경 사항이 없으면 저장이 비활성화됩니다.',
  changed: '저장하지 않은 변경 사항이 있습니다.',
};

export function saveHint(selectedCount: number, changedCount: number): SaveHint {
  if (selectedCount === 0) return 'noSelection';
  return changedCount === 0 ? 'noChange' : 'changed';
}

/** 저장 버튼 활성 조건(D5) — 대상 1개 이상, 바뀌는 대상 1개 이상, 숫자 입력 유효, 저장 중 아님. */
export function canSave(selectedCount: number, changedCount: number, inputValid: boolean, saving: boolean): boolean {
  return selectedCount > 0 && changedCount > 0 && inputValid && !saving;
}

/** 표시용 임계치 — 저장된 값이 정수가 아니면(직접 API 호출로만 가능) 반올림하지 않고 그대로 보인다(D10). */
export function formatThreshold(value: number): string {
  return String(value);
}

/** 요약 문구(D7). */
export function thresholdSummary(threshold: number): string {
  if (threshold === 0) return '가격이 조금이라도 오르거나 내리면 알림';
  const n = formatThreshold(threshold);
  return `상승 ${n}% 이상 또는 하락 ${n}% 이상 시 알림`;
}

/** 입력 중 값이 그대로 확정 가능한가 — 0~20 정수 문자열. 이때만 슬라이더에 바로 반영한다. */
export function parseThresholdDraft(raw: string): number | null {
  const trimmed = raw.trim();
  if (!/^\d{1,2}$/.test(trimmed)) return null;
  const value = Number(trimmed);
  return value >= THRESHOLD_MIN && value <= THRESHOLD_MAX ? value : null;
}

/**
 * blur·Enter 때 숫자 입력 보정(D10) — 0~20 정수로 맞춘다. 비었거나 숫자가 아니면 직전 유효값으로 되돌린다.
 * 소수는 가장 가까운 정수로(입력 중 오타 보정이지 저장된 값의 반올림이 아니다).
 */
export function normalizeThresholdInput(raw: string, previous: number): number {
  const trimmed = raw.trim();
  if (trimmed === '') return previous;
  const value = Number(trimmed);
  if (!Number.isFinite(value)) return previous;
  return Math.min(THRESHOLD_MAX, Math.max(THRESHOLD_MIN, Math.round(value)));
}

/** 저장 요청 본문 — 선택한 대상 전부에 화면의 값(D3). 세 값은 항상 명시한다(D4). */
export function buildSaveItems(selected: readonly SettingTarget[], values: FormValues): NotificationSettingItem[] {
  return selected.map((target) => ({
    favoritePropertyId: target.kind === 'property' ? target.id : null,
    favoriteRegionId: target.kind === 'region' ? target.id : null,
    priceChangeThresholdPct: values.threshold,
    newTradeAlertYn: values.newTrade,
    emailAlertYn: values.email,
  }));
}

/**
 * 진입 쿼리로 미리 선택할 대상 키(D9) — `?favoritePropertyId=`(MY-02 배지 링크 계약)와 `?favoriteRegionId=`. 목록에 없는
 * id·숫자가 아닌 값은 조용히 무시한다.
 */
export function initialSelection(params: URLSearchParams, targets: readonly SettingTarget[]): string[] {
  const keys = new Set(targets.map((t) => t.key));
  const picked: string[] = [];
  const propertyId = params.get('favoritePropertyId');
  const regionId = params.get('favoriteRegionId');
  if (propertyId && /^\d+$/.test(propertyId) && keys.has(propertyTargetKey(Number(propertyId)))) {
    picked.push(propertyTargetKey(Number(propertyId)));
  }
  if (regionId && /^\d+$/.test(regionId) && keys.has(regionTargetKey(Number(regionId)))) {
    picked.push(regionTargetKey(Number(regionId)));
  }
  return picked;
}
