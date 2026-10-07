import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AlertTriangleIcon } from '../../components/icons/AlertTriangleIcon';
import { BellIcon } from '../../components/icons/BellIcon';
import { CheckIcon } from '../../components/icons/CheckIcon';
import { MainLayout } from '../../components/layout/MainLayout';
import { MobileAppBar } from '../../components/layout/MobileAppBar';
import { EmptyState } from '../../components/ui/EmptyState';
import { useToast } from '../../components/ui/useToast';
import { getFavoriteProperties, getFavoriteRegions } from '../../features/favorite/api';
import type { FavoritePropertySummaryResponse, FavoriteRegionSummaryResponse } from '../../features/favorite/types';
import { getNotificationSettings, updateNotificationSettings } from '../../features/notification/api';
import type { NotificationSettingResponse } from '../../features/notification/types';
import { getErrorMessage } from '../../lib/apiError';
import { MEDIA_MD_DOWN, MEDIA_XL_UP, useMediaQuery } from '../../lib/useMediaQuery';
import { MY_ROUTES } from '../../routes/paths';
import { loadProfile } from '../my/myPageData';
import { RowSkeleton } from '../my/SectionCard';
import { useLoadable } from '../my/useLoadable';
import {
  SAVE_HINT_TEXT,
  THRESHOLD_MAX,
  THRESHOLD_MIN,
  buildSaveItems,
  buildTargets,
  canSave,
  changedTargets,
  deriveForm,
  describeTargetSetting,
  formatThreshold,
  indexSettings,
  initialSelection,
  normalizeThresholdInput,
  parseThresholdDraft,
  saveHint,
  thresholdSummary,
  type FormValues,
  type SettingTarget,
} from './notificationSettingsModel';

// useLoadable은 렌더마다 바뀌지 않는 fetcher를 받는다 — 모듈 수준 함수로 둔다.
const loadProperties = (): Promise<FavoritePropertySummaryResponse[]> => getFavoriteProperties();
const loadRegions = (): Promise<FavoriteRegionSummaryResponse[]> => getFavoriteRegions();
const loadSettings = (): Promise<NotificationSettingResponse[]> => getNotificationSettings();

const TITLE = '알림 설정';
const SUBTITLE = '관심 매물·지역 단위로 가격 변동 임계치와 신규 거래 알림 여부를 설정합니다.';
/** BAT-NTF-01 NEW_TRADE는 매매·전월세 모두, 해제 거래 제외(WatchConditionQuery.findNewTrades) — D2. */
const NEW_TRADE_HINT = '매매·전세·월세 거래가 새로 등록되면 알림';
const CARD_CLASS = 'rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_1px_4px_rgba(0,0,0,0.05)]';
const SECTION_TITLE_CLASS = 'text-[15px] leading-[22.5px] font-bold text-[#101828]';
const MIXED_TEXT = '대상마다 다름';

type Layout = 'mobile' | 'tablet' | 'desktop';

/**
 * SCR-MY-03 알림 설정(UI정의서 v2.1 5.5절, FR-6.1·6.2). 보호 라우트(RequireAuth) 안에서만 그려진다.
 * 관심 매물·관심 지역·알림 설정·내 정보를 병렬로 불러온다. 앞의 셋 중 하나라도 실패하면 페이지 단위 오류, 내 정보가 실패하면
 * 이메일 주소만 숨긴다. 대상 목록은 관심 매물·지역 목록이 기준이고 설정은 id로 붙인다(설정 응답에는 대상 이름이 없다).
 * 화면 크기별로 트리를 두 벌 렌더하지 않는다 — 머리(브레드크럼/앱 바)와 임계치 값 표시(텍스트/숫자 입력)만 useMediaQuery로 고른다.
 * 결정 D1~D11은 CLAUDE.md "SCR-MY-03" 절.
 */
export function NotificationSettingsPage() {
  const isDesktop = useMediaQuery(MEDIA_XL_UP);
  const isMobile = useMediaQuery(MEDIA_MD_DOWN);
  const layout: Layout = isDesktop ? 'desktop' : isMobile ? 'mobile' : 'tablet';

  const properties = useLoadable(loadProperties);
  const regions = useLoadable(loadRegions);
  const settings = useLoadable(loadSettings);
  const profile = useLoadable(loadProfile);

  const failed = [properties, regions, settings].filter((item) => item.state.status === 'error');
  const loading = [properties, regions, settings].some((item) => item.state.status === 'loading');

  let body: ReactNode;
  if (failed.length > 0) {
    const first = failed[0].state;
    body = (
      <div role="alert" className={`${CARD_CLASS} flex flex-col items-center gap-3 px-6 py-12 text-center`}>
        <p className="text-[14px] text-[#7f1d1d]">{first.status === 'error' ? first.message : ''}</p>
        <button
          type="button"
          onClick={() => failed.forEach((item) => item.retry())}
          className="rounded-[10px] border border-[#e5e7eb] bg-white px-4 py-2 text-[13px] font-semibold text-[#364153] hover:bg-[#f7f8fa]"
        >
          다시 시도
        </button>
      </div>
    );
  } else if (loading || properties.state.status !== 'success' || regions.state.status !== 'success' || settings.state.status !== 'success') {
    body = (
      <div aria-busy="true" className={`${CARD_CLASS} divide-y divide-[#f3f4f6]`}>
        <span className="sr-only">알림 설정을 불러오는 중</span>
        {Array.from({ length: 4 }, (_, index) => (
          <RowSkeleton key={index} />
        ))}
      </div>
    );
  } else if (properties.state.data.length === 0 && regions.state.data.length === 0) {
    body = (
      <div className={`${CARD_CLASS} px-6 py-10`}>
        <EmptyState
          icon={<BellIcon className="size-5" />}
          title="알림을 받을 관심 매물·지역이 없어요"
          description="관심 목록에 매물이나 지역을 추가하면 알림을 설정할 수 있어요"
          actions={
            <Link
              to={MY_ROUTES.favorites}
              className="rounded-[12px] bg-brand px-4 py-2.5 text-[13px] font-bold text-white hover:bg-brand/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
            >
              관심 목록으로 가기
            </Link>
          }
        />
      </div>
    );
  } else {
    body = (
      <SettingsForm
        layout={layout}
        targets={buildTargets(properties.state.data, regions.state.data)}
        settings={settings.state.data}
        onSettingsChange={settings.setData}
        email={profile.state.status === 'success' ? profile.state.data.email : null}
      />
    );
  }

  return (
    <MainLayout>
      {layout === 'desktop' ? (
        <div className="mx-auto flex max-w-[1000px] flex-col px-8 py-12">
          <nav aria-label="위치 경로" className="mb-3 text-[12.5px] leading-[19px] text-[#99a1af]">
            <ol className="flex items-center gap-1.5">
              <li>
                <Link to={MY_ROUTES.home} className="hover:text-[#4a5565] hover:underline">
                  마이페이지
                </Link>
              </li>
              <li aria-hidden="true" className="text-[#d1d5dc]">
                /
              </li>
              <li aria-current="page" className="font-semibold text-[#4a5565]">
                {TITLE}
              </li>
            </ol>
          </nav>
          <h1 className="text-[24px] leading-[36px] font-extrabold text-[#101828]">{TITLE}</h1>
          <p className="mt-1 text-[13px] leading-[19.5px] text-[#99a1af]">{SUBTITLE}</p>
          <div className="mt-7">{body}</div>
        </div>
      ) : (
        <div className="flex flex-col">
          <MobileAppBar title={TITLE} />
          <div className={`mx-auto flex w-full max-w-[1000px] flex-col ${layout === 'tablet' ? 'px-6 py-6' : 'px-4 py-5'}`}>
            <p className="text-[13px] leading-[19.5px] text-[#99a1af]">{SUBTITLE}</p>
            <div className="mt-5">{body}</div>
          </div>
        </div>
      )}
    </MainLayout>
  );
}

interface SettingsFormProps {
  layout: Layout;
  targets: SettingTarget[];
  settings: NotificationSettingResponse[];
  onSettingsChange: (next: NotificationSettingResponse[]) => void;
  email: string | null;
}

/**
 * 대상 선택 → 임계치 → 신규거래 → 수신 방법 → 저장. 데이터가 다 온 뒤에만 마운트되므로 진입 쿼리로 고른 대상을 첫 상태로 쓴다(D9).
 * 폼 값은 선택한 대상들의 저장된 값에서 만들고, 사용자가 바꾼 필드만 `overrides`로 들고 있다 — 대상을 더 골라도 입력이 남는다(D3).
 */
function SettingsForm({ layout, targets, settings, onSettingsChange, email }: SettingsFormProps) {
  const [searchParams] = useSearchParams();
  const { showToast } = useToast();
  const [selected, setSelected] = useState<ReadonlySet<string>>(() => new Set(initialSelection(searchParams, targets)));
  const [overrides, setOverrides] = useState<Partial<FormValues>>({});
  // 숫자 입력 중인 문자열(태블릿·모바일). null이면 확정된 값을 그대로 보인다.
  const [thresholdDraft, setThresholdDraft] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const rowRefs = useRef(new Map<string, HTMLLabelElement>());
  const ids = {
    hint: useId(),
    applyNote: useId(),
    thresholdMixed: useId(),
    thresholdHelp: useId(),
    newTradeHelp: useId(),
    newTradeMixed: useId(),
    emailHelp: useId(),
    emailMixed: useId(),
    pushBadge: useId(),
  };

  const settingsByKey = useMemo(() => indexSettings(settings), [settings]);
  const selectedTargets = targets.filter((target) => selected.has(target.key));
  const selectedKeys = selectedTargets.map((target) => target.key);
  const { values, mixed, hadDifferences } = deriveForm(selectedKeys, settingsByKey, overrides);
  const changed = changedTargets(selectedKeys, settingsByKey, values);
  const draftValid = thresholdDraft === null || parseThresholdDraft(thresholdDraft) !== null;
  const saveEnabled = canSave(selectedKeys.length, changed.length, draftValid, saving);
  const hint = saveHint(selectedKeys.length, changed.length);
  const noSelection = selectedKeys.length === 0;
  const zero = values.threshold === 0;

  // 진입 쿼리로 고른 대상이 보이도록 한 번 스크롤한다(D9).
  useEffect(() => {
    const first = targets.find((target) => selected.has(target.key));
    if (first) rowRefs.current.get(first.key)?.scrollIntoView?.({ block: 'center' });
    // 처음 마운트할 때만 — 이후 선택 변경으로 스크롤하지 않는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toggleTarget = (key: string, checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) next.add(key);
      else next.delete(key);
      return next;
    });
  };

  const setField = <F extends keyof FormValues>(field: F, value: FormValues[F]) => {
    setOverrides((prev) => ({ ...prev, [field]: value }));
  };

  const commitDraft = () => {
    if (thresholdDraft === null) return;
    setField('threshold', normalizeThresholdInput(thresholdDraft, values.threshold));
    setThresholdDraft(null);
  };

  const save = async () => {
    // 저장 버튼은 saveEnabled일 때만 눌리고, 오류 배너의 "다시 시도"는 같은 값을 다시 보낸다.
    if (saving || selectedKeys.length === 0) return;
    setSaving(true);
    setSaveError(null);
    const items = buildSaveItems(selectedTargets, values);
    try {
      await updateNotificationSettings(items);
    } catch (error) {
      setSaveError(getErrorMessage(error));
      setSaving(false);
      return;
    }
    let fresh: NotificationSettingResponse[];
    try {
      fresh = await getNotificationSettings();
    } catch {
      // 다시 조회하지 못하면 저장한 값을 화면 데이터에 직접 반영한다 — 저장 자체는 성공했다.
      fresh = mergeSaved(settings, items);
    }
    onSettingsChange(fresh);
    setOverrides({});
    setThresholdDraft(null);
    setSaving(false);
    showToast('알림 설정을 저장했어요', 'success');
  };

  const thresholdInput =
    layout === 'desktop' ? (
      <span className={`text-[20px] leading-[30px] font-extrabold ${zero ? 'text-[#bb4d00]' : 'text-brand'}`}>
        {formatThreshold(values.threshold)}%
      </span>
    ) : (
      <span className="flex items-center gap-1.5">
        <input
          type="text"
          inputMode="numeric"
          aria-label="변동 임계치(%)"
          aria-describedby={mixed.threshold ? ids.thresholdMixed : undefined}
          aria-invalid={!draftValid || undefined}
          value={thresholdDraft ?? formatThreshold(values.threshold)}
          onChange={(event) => {
            const raw = event.target.value;
            setThresholdDraft(raw);
            const parsed = parseThresholdDraft(raw);
            if (parsed !== null) setField('threshold', parsed);
          }}
          onBlur={commitDraft}
          onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              commitDraft();
            }
          }}
          className={`h-[35px] w-[52px] rounded-[10px] border bg-white text-center text-[15px] font-bold outline-none focus:border-brand ${
            !draftValid ? 'border-[#fb2c36]' : zero ? 'border-[#fee685] text-[#bb4d00]' : 'border-[#e5e7eb] text-[#101828]'
          }`}
        />
        <span aria-hidden="true" className="text-[14px] font-semibold text-[#4a5565]">
          %
        </span>
      </span>
    );

  return (
    <div className="flex flex-col gap-4">
      {zero && !noSelection && (
        <div role="status" className="flex gap-3 rounded-[14px] border border-[#fee685] bg-[#fffbeb] px-4 py-3.5">
          <AlertTriangleIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-[#e17100]" />
          <div>
            <p className="text-[13px] leading-5 font-bold text-[#973c00]">임계치 0% 설정 확인</p>
            <p className="mt-0.5 text-[12.5px] leading-[19px] text-[#973c00]">
              변동이 있을 때마다 알림이 발송될 수 있습니다. 알림 빈도가 매우 높아질 수 있으니 확인 후 저장해주세요.
            </p>
          </div>
        </div>
      )}

      <section className={`${CARD_CLASS} p-5 md:p-6`}>
        <fieldset>
          <legend className="mb-1">
            <h2 className={SECTION_TITLE_CLASS}>대상 선택</h2>
          </legend>
          <p className="text-[12.5px] leading-[19px] text-[#99a1af]">
            알림을 설정할 관심 매물·지역을 고르세요. 여러 곳을 고르면 같은 설정이 함께 저장됩니다.
          </p>
          <div className="mt-4 flex flex-col gap-2">
            {targets.map((target) => (
              <TargetRow
                key={target.key}
                target={target}
                summary={describeTargetSetting(settingsByKey.get(target.key))}
                checked={selected.has(target.key)}
                onChange={(checked) => toggleTarget(target.key, checked)}
                rowRef={(element) => {
                  if (element) rowRefs.current.set(target.key, element);
                  else rowRefs.current.delete(target.key);
                }}
              />
            ))}
          </div>
        </fieldset>
      </section>

      <SettingsSection title="가격 변동 임계치" disabled={noSelection}>
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-[14px] leading-[21px] font-semibold text-[#101828]">
              변동 임계치
              {mixed.threshold && <MixedTag id={ids.thresholdMixed} />}
            </p>
            <p id={ids.thresholdHelp} className="mt-0.5 text-[12px] leading-[18px] text-[#99a1af]">
              상승·하락에 같은 기준이 적용됩니다
            </p>
          </div>
          {thresholdInput}
        </div>
        <input
          type="range"
          min={THRESHOLD_MIN}
          max={THRESHOLD_MAX}
          step={1}
          value={values.threshold}
          onChange={(event) => {
            setField('threshold', Number(event.target.value));
            setThresholdDraft(null);
          }}
          aria-label="변동 임계치"
          aria-valuetext={`${formatThreshold(values.threshold)}%`}
          aria-describedby={[ids.thresholdHelp, mixed.threshold ? ids.thresholdMixed : null].filter(Boolean).join(' ')}
          className={`mt-4 h-2 w-full cursor-pointer disabled:cursor-not-allowed ${zero ? 'accent-[#e17100]' : 'accent-brand'}`}
        />
        <div aria-hidden="true" className="mt-1 flex justify-between text-[11px] leading-4 text-[#99a1af]">
          <span>{THRESHOLD_MIN}%</span>
          <span>{THRESHOLD_MAX}%</span>
        </div>
        <ThresholdSummary threshold={values.threshold} warning={zero} />
      </SettingsSection>

      <SettingsSection title="신규거래 알림" disabled={noSelection}>
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-[14px] leading-[21px] font-semibold text-[#101828]">
              신규거래 알림 수신
              {mixed.newTrade && <MixedTag id={ids.newTradeMixed} />}
            </p>
            <p id={ids.newTradeHelp} className="mt-0.5 text-[12px] leading-[18px] text-[#99a1af]">
              {NEW_TRADE_HINT}
            </p>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={values.newTrade}
            aria-label="신규거래 알림 수신"
            aria-describedby={[ids.newTradeHelp, mixed.newTrade ? ids.newTradeMixed : null].filter(Boolean).join(' ')}
            onClick={() => setField('newTrade', !values.newTrade)}
            className={`relative h-[26px] w-12 shrink-0 rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:cursor-not-allowed ${
              values.newTrade ? 'bg-brand' : 'bg-[#d1d5dc]'
            }`}
          >
            <span
              aria-hidden="true"
              className={`absolute top-[3px] size-5 rounded-full bg-white shadow-[0_1px_3px_rgba(0,0,0,0.2)] transition-[left] ${
                values.newTrade ? 'left-[25px]' : 'left-[3px]'
              }`}
            />
          </button>
        </div>
      </SettingsSection>

      <SettingsSection title="수신 방법" disabled={noSelection}>
        <div className="flex flex-col gap-2">
          <label className="flex cursor-pointer items-center gap-3 rounded-[12px] border border-[#f3f4f6] px-4 py-3">
            <input
              type="checkbox"
              checked={values.email}
              onChange={(event) => setField('email', event.target.checked)}
              aria-describedby={[values.email ? null : ids.emailHelp, mixed.email ? ids.emailMixed : null].filter(Boolean).join(' ') || undefined}
              className="peer sr-only"
            />
            <CheckVisual checked={values.email} />
            <span className="flex min-w-0 flex-1 flex-wrap items-center justify-between gap-x-3 gap-y-0.5">
              <span className="text-[14px] leading-[21px] font-semibold text-[#101828]">
                이메일 수신
                {mixed.email && <MixedTag id={ids.emailMixed} />}
              </span>
              {email && <span className="truncate text-[12.5px] leading-[19px] text-[#99a1af]">{email}</span>}
            </span>
          </label>
          {!values.email && (
            <p id={ids.emailHelp} className="px-1 text-[12px] leading-[18px] text-[#4a5565]">
              이메일은 보내지 않고 알림 이력에만 기록해요
            </p>
          )}
          <label className="flex cursor-not-allowed items-center gap-3 rounded-[12px] border border-[#f3f4f6] bg-[#f9fafb] px-4 py-3">
            <input type="checkbox" checked={false} disabled aria-describedby={ids.pushBadge} className="peer sr-only" readOnly />
            <CheckVisual checked={false} disabled />
            <span className="flex flex-1 items-center justify-between gap-3">
              <span className="text-[14px] leading-[21px] font-semibold text-[#99a1af]">웹 푸시</span>
              <span id={ids.pushBadge} className="rounded-full bg-[#f3f4f6] px-2 py-0.5 text-[11px] leading-4 font-semibold text-[#6a7282]">
                2차 확장 예정
              </span>
            </span>
          </label>
        </div>
      </SettingsSection>

      <div className="flex flex-col gap-3 pt-1">
        {saveError && (
          <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-[14px] border border-[#ffc9c9] bg-[#fef2f2] px-4 py-3">
            <p className="text-[13px] leading-5 text-[#9f0712]">{saveError}</p>
            <button
              type="button"
              onClick={() => void save()}
              disabled={saving}
              className="rounded-[10px] border border-[#ffc9c9] bg-white px-3.5 py-1.5 text-[13px] font-semibold text-[#9f0712] hover:bg-[#fff5f5] disabled:opacity-60"
            >
              다시 시도
            </button>
          </div>
        )}
        <div className={`flex gap-3 ${layout === 'mobile' ? 'flex-col' : 'items-center justify-between'}`}>
          <div className="flex flex-col gap-0.5">
            <p id={ids.hint} className="text-[12.5px] leading-[19px] text-[#99a1af]">
              {SAVE_HINT_TEXT[hint]}
            </p>
            {hadDifferences && selectedKeys.length > 1 && (
              <p id={ids.applyNote} className="text-[12.5px] leading-[19px] font-semibold text-[#4a5565]">
                선택한 대상 {selectedKeys.length}곳에 같은 설정이 적용됩니다
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={() => void save()}
            disabled={!saveEnabled}
            aria-busy={saving || undefined}
            aria-describedby={[ids.hint, hadDifferences && selectedKeys.length > 1 ? ids.applyNote : null].filter(Boolean).join(' ')}
            className={`h-12 rounded-[14px] bg-brand px-8 text-[15px] font-bold text-white transition-colors hover:bg-brand/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:cursor-not-allowed disabled:bg-[#d1d5dc] ${
              layout === 'mobile' ? 'w-full' : 'shrink-0'
            }`}
          >
            {saving ? '저장 중…' : '저장'}
          </button>
        </div>
      </div>
    </div>
  );
}

/** 저장 뒤 다시 조회하지 못했을 때 화면 데이터에 저장한 값을 반영한다. 새로 생긴 설정의 id는 알 수 없어 음수로 둔다. */
function mergeSaved(
  current: readonly NotificationSettingResponse[],
  items: ReturnType<typeof buildSaveItems>,
): NotificationSettingResponse[] {
  const next = current.map((setting) => ({ ...setting }));
  items.forEach((item, index) => {
    const existing = next.find((setting) =>
      item.favoritePropertyId != null
        ? setting.favoritePropertyId === item.favoritePropertyId
        : setting.favoriteRegionId === item.favoriteRegionId,
    );
    const values = {
      priceChangeThresholdPct: item.priceChangeThresholdPct,
      newTradeAlertYn: item.newTradeAlertYn,
      emailAlertYn: item.emailAlertYn,
    };
    if (existing) Object.assign(existing, values);
    else next.push({ notificationSettingId: -(index + 1), favoritePropertyId: item.favoritePropertyId, favoriteRegionId: item.favoriteRegionId, ...values });
  });
  return next;
}

function SettingsSection({ title, disabled, children }: { title: string; disabled: boolean; children: ReactNode }) {
  return (
    <section className={`${CARD_CLASS} p-5 md:p-6`}>
      <h2 className={SECTION_TITLE_CLASS}>{title}</h2>
      {/* 대상을 고르지 않으면 입력을 막는다 — 무엇을 설정하는 화면인지는 보이게 섹션은 그대로 둔다(D5). */}
      <fieldset disabled={disabled} className={`mt-3 min-w-0 ${disabled ? 'opacity-50' : ''}`}>
        {children}
      </fieldset>
    </section>
  );
}

function MixedTag({ id }: { id: string }) {
  return (
    <span id={id} className="ml-2 rounded-full bg-[#f3f4f6] px-2 py-0.5 align-middle text-[11px] leading-4 font-semibold text-[#4a5565]">
      {MIXED_TEXT}
    </span>
  );
}

function CheckVisual({ checked, disabled = false }: { checked: boolean; disabled?: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={`flex size-5 shrink-0 items-center justify-center rounded-[6px] border-2 transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-brand peer-focus-visible:ring-offset-2 ${
        disabled ? 'border-[#e5e7eb] bg-[#f3f4f6]' : checked ? 'border-brand bg-brand' : 'border-[#d1d5db] bg-white'
      }`}
    >
      {checked && <CheckIcon strokeWidth={1.5} className="size-3 text-white" />}
    </span>
  );
}

const KIND_BADGE: Record<SettingTarget['kind'], { label: string; className: string }> = {
  property: { label: '관심 매물', className: 'bg-[#e8f2f0] text-brand' },
  region: { label: '관심 지역', className: 'bg-[#fef3c6] text-[#973c00]' },
};

/** 대상 행 — 행 전체가 체크박스의 라벨이다. 유형 배지와 현재 설정 요약(D6)은 체크박스 설명으로 잇는다. */
function TargetRow({
  target,
  summary,
  checked,
  onChange,
  rowRef,
}: {
  target: SettingTarget;
  summary: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  rowRef: (element: HTMLLabelElement | null) => void;
}) {
  const badgeId = useId();
  const summaryId = useId();
  const badge = KIND_BADGE[target.kind];
  return (
    <label
      ref={rowRef}
      data-target-key={target.key}
      className={`flex cursor-pointer items-center gap-3 rounded-[12px] border px-4 py-3 transition-colors ${
        checked ? 'border-brand bg-[#f0f9f7]' : 'border-[#f3f4f6] bg-white hover:bg-[#f7f8fa]'
      }`}
    >
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        aria-describedby={`${badgeId} ${summaryId}`}
        className="peer sr-only"
      />
      <CheckVisual checked={checked} />
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-[14px] leading-[21px] font-semibold text-[#101828]">{target.name}</span>
        <span id={summaryId} className="text-[12px] leading-[18px] text-[#99a1af]">
          {summary}
        </span>
      </span>
      <span id={badgeId} className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] leading-4 font-semibold ${badge.className}`}>
        {badge.label}
      </span>
    </label>
  );
}

/**
 * 요약 박스(D7). 보이는 문구는 즉시 바뀌고, 스크린리더용 알림은 값이 300ms 멈춘 뒤에만 갱신한다 — 슬라이더를 끄는 동안 매 단계
 * 읽히지 않게. 배너가 화면 밖에 있을 수 있어 0%일 때 이 박스도 경고 문구를 담는다.
 */
function ThresholdSummary({ threshold, warning }: { threshold: number; warning: boolean }) {
  const text = thresholdSummary(threshold);
  const [announced, setAnnounced] = useState(text);
  useEffect(() => {
    const timer = window.setTimeout(() => setAnnounced(text), 300);
    return () => window.clearTimeout(timer);
  }, [text]);
  return (
    <div
      data-testid="threshold-summary"
      className={`mt-4 rounded-[12px] border px-4 py-3 text-[13px] leading-5 ${
        warning ? 'border-[#fee685] bg-[#fffbeb] text-[#973c00]' : 'border-[#d1eae6] bg-[#f0f9f7] text-brand'
      }`}
    >
      <p aria-hidden="true" className="font-semibold">
        {text}
      </p>
      {warning && (
        <p aria-hidden="true" className="mt-0.5 text-[12px] leading-[18px]">
          변동이 있을 때마다 알림이 발송될 수 있습니다
        </p>
      )}
      <p aria-live="polite" className="sr-only">
        {announced}
        {warning && announced === text ? '. 변동이 있을 때마다 알림이 발송될 수 있습니다' : ''}
      </p>
    </div>
  );
}
