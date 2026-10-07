import { describe, expect, it } from 'vitest';
import type { NotificationSettingResponse } from '../../features/notification/types';
import {
  DEFAULT_VALUES,
  buildSaveItems,
  canSave,
  changedTargets,
  deriveForm,
  describeTargetSetting,
  indexSettings,
  initialSelection,
  normalizeThresholdInput,
  parseThresholdDraft,
  regionDisplayName,
  saveHint,
  thresholdSummary,
  type SettingTarget,
} from './notificationSettingsModel';

const setting = (
  id: number,
  target: { favoritePropertyId?: number; favoriteRegionId?: number },
  threshold: number,
  newTrade: boolean,
  email: boolean,
): NotificationSettingResponse => ({
  notificationSettingId: id,
  ...target,
  priceChangeThresholdPct: threshold,
  newTradeAlertYn: newTrade,
  emailAlertYn: email,
});

// 매물 1·2는 설정 있음(값이 서로 다름), 매물 3은 설정 없음, 지역 9는 매물 1과 같은 값.
const SETTINGS = indexSettings([
  setting(1, { favoritePropertyId: 1 }, 5, true, true),
  setting(2, { favoritePropertyId: 2 }, 10, true, false),
  setting(3, { favoriteRegionId: 9 }, 5, true, true),
]);

describe('deriveForm(D3) — 선택한 대상들의 저장된 값에서 폼 값을 만든다', () => {
  it('선택이 없으면 기본값, 대상마다 다름 없음', () => {
    expect(deriveForm([], SETTINGS, {})).toEqual({
      values: DEFAULT_VALUES,
      mixed: { threshold: false, newTrade: false, email: false },
      hadDifferences: false,
    });
  });

  it('하나를 고르면 그 대상의 저장된 값', () => {
    const { values, mixed } = deriveForm(['property:2'], SETTINGS, {});
    expect(values).toEqual({ threshold: 10, newTrade: true, email: false });
    expect(mixed).toEqual({ threshold: false, newTrade: false, email: false });
  });

  it('값이 같은 대상 여럿이면 그 값, 대상마다 다름 없음', () => {
    const { values, mixed, hadDifferences } = deriveForm(['property:1', 'region:9'], SETTINGS, {});
    expect(values).toEqual({ threshold: 5, newTrade: true, email: true });
    expect(mixed).toEqual({ threshold: false, newTrade: false, email: false });
    expect(hadDifferences).toBe(false);
  });

  it('필드별로 다르면 그 필드만 기본값 + 대상마다 다름', () => {
    const { values, mixed, hadDifferences } = deriveForm(['property:1', 'property:2'], SETTINGS, {});
    expect(values).toEqual({ threshold: 5, newTrade: true, email: true });
    expect(mixed).toEqual({ threshold: true, newTrade: false, email: true });
    expect(hadDifferences).toBe(true);
  });

  it('미설정 대상은 기본값으로 본다', () => {
    expect(deriveForm(['property:3'], SETTINGS, {}).values).toEqual(DEFAULT_VALUES);
    // 매물 2(10%·이메일 끔)와 섞이면 두 필드가 다르다.
    expect(deriveForm(['property:2', 'property:3'], SETTINGS, {}).mixed).toEqual({ threshold: true, newTrade: false, email: true });
  });

  it('사용자가 바꾼 필드는 선택을 바꿔도 입력한 값을 유지하고 대상마다 다름을 지운다', () => {
    const overrides = { threshold: 12 };
    expect(deriveForm(['property:1'], SETTINGS, overrides).values.threshold).toBe(12);
    const after = deriveForm(['property:1', 'property:2'], SETTINGS, overrides);
    expect(after.values.threshold).toBe(12);
    expect(after.mixed.threshold).toBe(false);
    // 바꾸지 않은 필드는 새 선택에 맞춰 다시 계산된다.
    expect(after.mixed.email).toBe(true);
    expect(after.hadDifferences).toBe(true);
  });
});

describe('changedTargets·canSave·saveHint(D5)', () => {
  it('미선택이면 저장 불가, 안내는 대상 먼저 선택', () => {
    expect(changedTargets([], SETTINGS, DEFAULT_VALUES)).toEqual([]);
    expect(canSave(0, 0, true, false)).toBe(false);
    expect(saveHint(0, 0)).toBe('noSelection');
  });

  it('저장된 값 그대로면 변경 없음', () => {
    const { values } = deriveForm(['property:1'], SETTINGS, {});
    const changed = changedTargets(['property:1'], SETTINGS, values);
    expect(changed).toEqual([]);
    expect(canSave(1, changed.length, true, false)).toBe(false);
    expect(saveHint(1, changed.length)).toBe('noChange');
  });

  it('미설정 대상만 골라도 저장하면 행이 생기므로 변경 있음', () => {
    const { values } = deriveForm(['property:3'], SETTINGS, {});
    expect(changedTargets(['property:3'], SETTINGS, values)).toEqual(['property:3']);
    expect(saveHint(1, 1)).toBe('changed');
  });

  it('값을 바꿨다가 원래 값으로 되돌리면 변경 없음', () => {
    const changedOnce = deriveForm(['property:1'], SETTINGS, { threshold: 7 }).values;
    expect(changedTargets(['property:1'], SETTINGS, changedOnce)).toEqual(['property:1']);
    const reverted = deriveForm(['property:1'], SETTINGS, { threshold: 5 }).values;
    expect(changedTargets(['property:1'], SETTINGS, reverted)).toEqual([]);
  });

  it('여러 대상 중 바뀌는 대상만 센다', () => {
    const { values } = deriveForm(['property:1', 'property:2'], SETTINGS, {});
    // 기본값(5·켬·켬)이 매물 1과 같고 매물 2와 다르다.
    expect(changedTargets(['property:1', 'property:2'], SETTINGS, values)).toEqual(['property:2']);
  });

  it('입력이 유효하지 않거나 저장 중이면 저장 불가', () => {
    expect(canSave(1, 1, false, false)).toBe(false);
    expect(canSave(1, 1, true, true)).toBe(false);
    expect(canSave(1, 1, true, false)).toBe(true);
  });
});

describe('thresholdSummary(D7)', () => {
  it.each([
    [0, '가격이 조금이라도 오르거나 내리면 알림'],
    [1, '상승 1% 이상 또는 하락 1% 이상 시 알림'],
    [5, '상승 5% 이상 또는 하락 5% 이상 시 알림'],
    [20, '상승 20% 이상 또는 하락 20% 이상 시 알림'],
  ])('%s%% → %s', (value, text) => {
    expect(thresholdSummary(value)).toBe(text);
  });

  it('정수가 아닌 저장값은 반올림하지 않는다(D10)', () => {
    expect(thresholdSummary(2.5)).toBe('상승 2.5% 이상 또는 하락 2.5% 이상 시 알림');
  });
});

describe('숫자 입력 보정(D10)', () => {
  it.each([
    ['', 6, 6],
    ['25', 6, 20],
    ['-3', 6, 0],
    ['abc', 6, 6],
    ['7', 6, 7],
    [' 12 ', 6, 12],
    ['7.6', 6, 8],
  ])('"%s"(직전 %s) → %s', (raw, previous, expected) => {
    expect(normalizeThresholdInput(raw, previous)).toBe(expected);
  });

  it('입력 중에는 0~20 정수 문자열만 바로 반영한다', () => {
    expect(parseThresholdDraft('0')).toBe(0);
    expect(parseThresholdDraft('20')).toBe(20);
    expect(parseThresholdDraft('21')).toBeNull();
    expect(parseThresholdDraft('')).toBeNull();
    expect(parseThresholdDraft('-1')).toBeNull();
    expect(parseThresholdDraft('5.5')).toBeNull();
  });
});

describe('대상 표시·저장 요청·진입 선택', () => {
  const targets: SettingTarget[] = [
    { key: 'property:1', kind: 'property', id: 1, name: '래미안' },
    { key: 'region:9', kind: 'region', id: 9, name: '안성시 금도읍' },
  ];

  it('지역 이름은 시군구 + 읍면동, 시군구가 없으면 시도', () => {
    expect(regionDisplayName({ sidoName: '경기도', sigunguName: '안성시', eupmyeondongName: '금도읍' })).toBe('안성시 금도읍');
    expect(regionDisplayName({ sidoName: '세종특별자치시', eupmyeondongName: '어진동' })).toBe('세종특별자치시 어진동');
  });

  it('행 보조 텍스트는 MY-02 배지 문구, 설정이 없으면 미설정(D6·D8)', () => {
    expect(describeTargetSetting(undefined)).toBe('미설정');
    expect(describeTargetSetting(setting(1, { favoritePropertyId: 1 }, 5, true, true))).toBe('±5% · 신규거래');
    expect(describeTargetSetting(setting(1, { favoritePropertyId: 1 }, 5, true, false))).toBe('이메일 꺼짐');
  });

  it('저장 요청은 선택한 대상 전부에 같은 값, 세 값은 항상 명시', () => {
    expect(buildSaveItems(targets, { threshold: 0, newTrade: false, email: true })).toEqual([
      { favoritePropertyId: 1, favoriteRegionId: null, priceChangeThresholdPct: 0, newTradeAlertYn: false, emailAlertYn: true },
      { favoritePropertyId: null, favoriteRegionId: 9, priceChangeThresholdPct: 0, newTradeAlertYn: false, emailAlertYn: true },
    ]);
  });

  it('진입 쿼리의 대상을 미리 고르고, 목록에 없거나 잘못된 id는 무시한다(D9)', () => {
    expect(initialSelection(new URLSearchParams('favoritePropertyId=1'), targets)).toEqual(['property:1']);
    expect(initialSelection(new URLSearchParams('favoriteRegionId=9&favoritePropertyId=1'), targets)).toEqual(['property:1', 'region:9']);
    expect(initialSelection(new URLSearchParams('favoritePropertyId=404'), targets)).toEqual([]);
    expect(initialSelection(new URLSearchParams('favoritePropertyId=abc'), targets)).toEqual([]);
    expect(initialSelection(new URLSearchParams(''), targets)).toEqual([]);
  });
});
