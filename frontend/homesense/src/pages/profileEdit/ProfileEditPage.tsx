import axios from 'axios';
import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode, type Ref } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircleIcon } from '../../components/icons/AlertCircleIcon';
import { CheckIcon } from '../../components/icons/CheckIcon';
import { ChevronDownIcon } from '../../components/icons/ChevronDownIcon';
import { ChevronLeftIcon } from '../../components/icons/ChevronLeftIcon';
import { EyeIcon } from '../../components/icons/EyeIcon';
import { EyeOffIcon } from '../../components/icons/EyeOffIcon';
import { MainLayout } from '../../components/layout/MainLayout';
import { MobileAppBar } from '../../components/layout/MobileAppBar';
import { useGoBack } from '../../components/layout/useGoBack';
import { FieldHint } from '../../components/ui/FieldHint';
import { CONFIRM_DANGER_BUTTON_CLASS, CONFIRM_FOOTER_ROW_CLASS, CONFIRM_SECONDARY_BUTTON_CLASS, Modal } from '../../components/ui/Modal';
import { PasswordChecklist } from '../../components/ui/PasswordChecklist';
import { useToast } from '../../components/ui/useToast';
import { useAuth } from '../../features/auth/useAuth';
import { getMe, updateMe } from '../../features/user/api';
import type { UserResponse } from '../../features/user/types';
import { getErrorMessage } from '../../lib/apiError';
import { MEDIA_XL_UP, useMediaQuery } from '../../lib/useMediaQuery';
import { MY_ROUTES } from '../../routes/paths';
import type { ApiErrorResponse } from '../../types/api';
import { useLoadable } from '../my/useLoadable';
import {
  buildUpdateBody,
  EMPTY_PASSWORD_FIELDS,
  evaluateSave,
  initialForm,
  resolveSaveError,
  type ErrorTarget,
  type ProfileForm,
} from './profileEditModel';

const TITLE = '회원정보 수정';
/** D1 — Figma "이메일 변경 시 재인증이 필요할 수 있습니다" 대신. 이메일 변경(소유 확인 플로우)은 범위 밖이다. */
const EMAIL_NOTICE = '이메일(로그인 ID)은 현재 변경할 수 없습니다';
const PASSWORD_CHANGED_TOAST = '비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.';
const SAVED_TOAST = '회원정보가 저장되었습니다';

const CARD_CLASS =
  'rounded-[16px] border border-[#f3f4f6] bg-white shadow-[0_1px_4px_rgba(0,0,0,0.04),0_4px_24px_rgba(0,0,0,0.08)]';
const SECTION_TITLE_CLASS = 'text-[13px] leading-[19.5px] font-bold text-[#99a1af]';

type FieldKey = Exclude<ErrorTarget, 'banner'>;

/**
 * SCR-MY-05 회원정보 수정(UI정의서 5.5절). 닉네임·비밀번호 변경, 이메일은 읽기 전용(D1). 트리는 한 벌이다 — 머리(1280px 이상
 * 브레드크럼·H1, 그 아래 공용 앱 바)만 화면 크기로 고르고 필드 배치(2열/1열)·ActionBar는 CSS로 나눈다(MY-03과 같은 경계).
 */
export function ProfileEditPage() {
  const profile = useLoadable(getMe);

  useEffect(() => {
    const previous = document.title;
    document.title = `${TITLE} | HomeSense`;
    return () => {
      document.title = previous;
    };
  }, []);

  return <ProfileEditFrame>{profile.state.status === 'success' ? <ProfileEditForm baseline={profile.state.data} /> : <LoadingOrError profile={profile} />}</ProfileEditFrame>;
}

function LoadingOrError({ profile }: { profile: ReturnType<typeof useLoadable<UserResponse>> }) {
  if (profile.state.status === 'error') {
    return (
      <>
        <FormHeader onLeave={(run) => run()} />
        <div className="p-4 xl:p-0 xl:pt-6">
          <ErrorBanner message={profile.state.message} onRetry={profile.retry} />
        </div>
      </>
    );
  }
  return <FormSkeleton />;
}

/** 머리·본문 틀. 불러오는 동안(스켈레톤)에도 같은 틀을 쓴다. 이탈 확인이 필요한 뒤로가기는 폼이 따로 그린다(아래 FormHeader). */
function ProfileEditFrame({ children }: { children: ReactNode }) {
  return (
    <MainLayout>
      <div className="mx-auto flex w-full flex-col xl:max-w-[660px] xl:px-6 xl:py-8">{children}</div>
    </MainLayout>
  );
}

function FormHeader({ onLeave }: { onLeave: (action: () => void) => void }) {
  const isDesktop = useMediaQuery(MEDIA_XL_UP);
  const navigate = useNavigate();
  if (!isDesktop) return <MobileAppBar title={TITLE} onBack={onLeave} />;
  return (
    <>
      <nav aria-label="위치 경로" className="text-[13px] leading-[19.5px]">
        <ol className="flex items-center gap-1.5">
          <li>
            <button
              type="button"
              onClick={() => onLeave(() => navigate(MY_ROUTES.home))}
              className="flex items-center gap-1 font-semibold text-[#6a7282] hover:underline focus-visible:outline-2 focus-visible:outline-brand"
            >
              <ChevronLeftIcon aria-hidden="true" className="size-4" />
              마이페이지
            </button>
          </li>
          <li aria-hidden="true" className="text-[16px] leading-none text-[#d1d5dc]">
            /
          </li>
          <li aria-current="page" className="font-semibold text-[#101828]">
            {TITLE}
          </li>
        </ol>
      </nav>
      <h1 className="mt-5 text-[22px] leading-[33px] font-extrabold text-[#101828]">{TITLE}</h1>
    </>
  );
}

function ProfileEditForm({ baseline: initialBaseline }: { baseline: UserResponse }) {
  const { applyUserUpdate, endSession } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const goBack = useGoBack(MY_ROUTES.home);
  const isDesktop = useMediaQuery(MEDIA_XL_UP);

  const [baseline, setBaseline] = useState(initialBaseline);
  const [form, setForm] = useState<ProfileForm>(() => initialForm(initialBaseline.nickname));
  const [saving, setSaving] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<FieldKey, string>>>({});
  const [bannerError, setBannerError] = useState<string | null>(null);
  const [visible, setVisible] = useState({ currentPassword: false, newPassword: false, confirmPassword: false });
  const [pendingLeave, setPendingLeave] = useState<{ run: () => void } | null>(null);

  const nicknameRef = useRef<HTMLInputElement>(null);
  const currentPasswordRef = useRef<HTMLInputElement>(null);
  const newPasswordRef = useRef<HTMLInputElement>(null);
  const focusCurrentOnOpen = useRef(false);
  // GNB·하단 탭·브라우저 뒤로가기로는 저장 중에도 화면을 떠날 수 있다(D7 한계). 떠난 뒤 도착한 응답이 화면을 옮기면 안 된다.
  // 언마운트만 보면 부족하다 — 라우터 이동은 전환(transition)이라 주소가 바뀐 뒤 새 화면이 커밋되기 전까지 이 폼이 남아 있다.
  // 그래서 지금 주소가 아직 이 화면인지도 함께 본다.
  const mountedRef = useRef(false);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);
  const isStillHere = () => mountedRef.current && window.location.pathname === MY_ROUTES.profileEdit;

  const save = evaluateSave(baseline.nickname, form, saving);
  const ids = { nickname: useId(), email: useId(), current: useId(), next: useId(), confirm: useId(), body: useId(), notice: useId() };

  // D7 — 저장하지 않은 변경이 있으면 새로고침·탭 닫기를 확인한다. GNB·하단 탭 이동은 막지 못한다(<BrowserRouter>, CLAUDE.md 한계).
  useEffect(() => {
    if (!save.dirty) return;
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [save.dirty]);

  // 섹션을 펼치면 "현재 비밀번호"로 포커스.
  useEffect(() => {
    if (form.passwordOpen && focusCurrentOnOpen.current) {
      focusCurrentOnOpen.current = false;
      currentPasswordRef.current?.focus();
    }
  }, [form.passwordOpen]);

  const requestLeave = (run: () => void) => {
    if (save.dirty) setPendingLeave({ run });
    else run();
  };

  const update = (patch: Partial<ProfileForm>, clears: FieldKey[]) => {
    setForm((previous) => ({ ...previous, ...patch }));
    if (clears.some((key) => fieldErrors[key] !== undefined)) {
      setFieldErrors((previous) => {
        const next = { ...previous };
        for (const key of clears) delete next[key];
        return next;
      });
    }
  };

  const togglePasswordSection = () => {
    if (form.passwordOpen) {
      // D6 — 접으면 세 필드와 관련 오류를 모두 비운다. 접힌 상태에서는 비밀번호 변경을 요청하지 않는다.
      setForm((previous) => ({ ...previous, passwordOpen: false, ...EMPTY_PASSWORD_FIELDS }));
      setFieldErrors((previous) => ({ nickname: previous.nickname }));
      setVisible({ currentPassword: false, newPassword: false, confirmPassword: false });
    } else {
      focusCurrentOnOpen.current = true;
      setForm((previous) => ({ ...previous, passwordOpen: true }));
    }
  };

  const submit = async (event?: FormEvent) => {
    event?.preventDefault();
    if (!save.canSave) return;
    const body = buildUpdateBody(baseline.nickname, form);
    const changingPassword = body.newPassword !== undefined;
    setSaving(true);
    setBannerError(null);
    try {
      const updated = await updateMe(body);
      if (changingPassword) {
        // D4 — 서버가 이 회원의 모든 세션을 끝냈다. 로그아웃 API를 부르지 않고 로컬 세션만 정리하고, 이동은 보호 라우트
        // 가드가 한다(로그인 화면, 복귀 목적지 없음).
        showToast(PASSWORD_CHANGED_TOAST, 'success');
        endSession({ redirectTo: '/login' });
        return;
      }
      // D5 — 헤더의 사용자 정보를 바로 바꾸고, 변경 없음 상태로 되돌린 뒤 MY-01로. 응답 전에 사용자가 다른 화면으로 갔으면
      // 저장 결과(헤더 닉네임·토스트)만 반영하고 이동하지 않는다 — 사용자가 고른 화면을 늦은 응답이 덮지 않게.
      applyUserUpdate(updated);
      showToast(SAVED_TOAST, 'success');
      if (!isStillHere()) return;
      setBaseline(updated);
      setForm(initialForm(updated.nickname));
      navigate(MY_ROUTES.home);
    } catch (error) {
      const apiError = axios.isAxiosError<ApiErrorResponse>(error) ? error.response?.data?.error : undefined;
      const { target, message } = resolveSaveError(apiError ?? undefined);
      if (target === 'banner') {
        setBannerError(message ?? getErrorMessage(error));
      } else {
        setFieldErrors((previous) => ({ ...previous, [target]: message }));
        const ref = target === 'nickname' ? nicknameRef : target === 'currentPassword' ? currentPasswordRef : newPasswordRef;
        ref.current?.focus();
      }
    } finally {
      setSaving(false);
    }
  };

  const eyeToggle = (key: keyof typeof visible) => (
    <button
      type="button"
      aria-label={visible[key] ? '비밀번호 숨기기' : '비밀번호 표시'}
      aria-pressed={visible[key]}
      onClick={() => setVisible((previous) => ({ ...previous, [key]: !previous[key] }))}
      className="flex size-6 items-center justify-center rounded text-[#99a1af] hover:text-[#4a5565] focus-visible:outline-2 focus-visible:outline-brand"
    >
      {visible[key] ? <EyeOffIcon aria-hidden="true" className="size-4" /> : <EyeIcon aria-hidden="true" className="size-4" />}
    </button>
  );

  const nicknameError = fieldErrors.nickname ?? (save.nicknameInvalid ? '닉네임은 2자 이상 12자 이하여야 합니다' : undefined);
  const notice = save.dirty ? '저장하지 않은 변경 사항이 있습니다.' : '변경 사항이 없으면 저장이 비활성화됩니다.';
  const saveBlocked = !save.canSave;

  return (
    <>
      <FormHeader onLeave={requestLeave} />
      <div className="flex flex-col p-4 xl:p-0">
        {bannerError && (
          <div className="mb-4 xl:mt-6 xl:mb-0">
            <ErrorBanner message={bannerError} onRetry={() => void submit()} retryDisabled={saveBlocked} />
          </div>
        )}
        <form noValidate onSubmit={(event) => void submit(event)} aria-label={TITLE}>
          <div className={`xl:mt-6 ${CARD_CLASS}`}>
            <section aria-labelledby={`${ids.nickname}-section`} className="px-8 pt-7 pb-6">
              <h2 id={`${ids.nickname}-section`} className={SECTION_TITLE_CLASS}>
                기본 정보
              </h2>
              <div className="mt-5 grid grid-cols-1 gap-5 xl:grid-cols-2">
                <Field
                  id={ids.nickname}
                  label="닉네임"
                  value={form.nickname}
                  onChange={(value) => update({ nickname: value }, ['nickname'])}
                  error={nicknameError}
                  inputRef={nicknameRef}
                  autoComplete="nickname"
                />
                <Field id={ids.email} label="이메일" value={baseline.email} readOnly help={EMAIL_NOTICE} type="email" autoComplete="email" />
              </div>
            </section>

            <div aria-hidden="true" className="mx-8 h-px bg-[#f3f4f6]" />

            <section className="px-8 pt-5 pb-7">
              <h2 className="m-0">
                <button
                  type="button"
                  onClick={togglePasswordSection}
                  aria-expanded={form.passwordOpen}
                  aria-controls={form.passwordOpen ? ids.body : undefined}
                  className="flex w-full items-center justify-between rounded-[8px] text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
                >
                  <span className={SECTION_TITLE_CLASS}>비밀번호 변경</span>
                  <span
                    className={`flex items-center gap-1.5 text-[12px] leading-[18px] font-semibold ${form.passwordOpen ? 'text-brand' : 'text-[#99a1af]'}`}
                  >
                    {form.passwordOpen ? '접기' : '펼치기'}
                    <ChevronDownIcon aria-hidden="true" className={`size-3.5 ${form.passwordOpen ? 'rotate-180' : ''}`} />
                  </span>
                </button>
              </h2>
              {form.passwordOpen && (
                <div id={ids.body} className="mt-5 flex flex-col gap-4">
                  <Field
                    id={ids.current}
                    label="현재 비밀번호"
                    type={visible.currentPassword ? 'text' : 'password'}
                    value={form.currentPassword}
                    onChange={(value) => update({ currentPassword: value }, ['currentPassword'])}
                    error={fieldErrors.currentPassword}
                    inputRef={currentPasswordRef}
                    endAdornment={eyeToggle('currentPassword')}
                    autoComplete="current-password"
                  />
                  <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
                    <div>
                      <Field
                        id={ids.next}
                        label="새 비밀번호"
                        type={visible.newPassword ? 'text' : 'password'}
                        value={form.newPassword}
                        onChange={(value) => update({ newPassword: value }, ['newPassword'])}
                        error={fieldErrors.newPassword}
                        inputRef={newPasswordRef}
                        endAdornment={eyeToggle('newPassword')}
                        autoComplete="new-password"
                        describedBy={form.newPassword !== '' ? `${ids.next}-policy` : undefined}
                      />
                      {form.newPassword !== '' && (
                        <div id={`${ids.next}-policy`} className="mt-1.5" aria-label="새 비밀번호 조건">
                          <PasswordChecklist password={form.newPassword} />
                        </div>
                      )}
                    </div>
                    <div>
                      <Field
                        id={ids.confirm}
                        label="새 비밀번호 확인"
                        type={visible.confirmPassword ? 'text' : 'password'}
                        value={form.confirmPassword}
                        onChange={(value) => update({ confirmPassword: value }, [])}
                        invalid={save.confirmMatches === false}
                        endAdornment={eyeToggle('confirmPassword')}
                        autoComplete="new-password"
                        describedBy={save.confirmMatches !== null ? `${ids.confirm}-match` : undefined}
                      />
                      {save.confirmMatches !== null && (
                        <FieldHint
                          id={`${ids.confirm}-match`}
                          status={save.confirmMatches ? 'success' : 'error'}
                          message={save.confirmMatches ? '비밀번호가 일치합니다' : '비밀번호가 일치하지 않습니다'}
                        />
                      )}
                    </div>
                  </div>
                  {form.newPassword !== '' && save.policy.isValid && (
                    <p
                      role="status"
                      className="flex items-center gap-2 rounded-[14px] border border-[#dcfce7] bg-[#f0fdf4] px-3.5 py-2.5 text-[13px] leading-[19.5px] font-semibold text-[#008236]"
                    >
                      <CheckIcon aria-hidden="true" strokeWidth={1.33} className="size-4 shrink-0 text-[#00c950]" />
                      새 비밀번호 조건을 모두 충족합니다.
                    </p>
                  )}
                </div>
              )}
            </section>
          </div>

          <div className="mt-6 flex items-center gap-2.5 xl:justify-between">
            <p id={ids.notice} className="hidden text-[12.5px] leading-[18.75px] text-[#99a1af] xl:block">
              {notice}
            </p>
            <div className="flex flex-1 gap-2.5 xl:flex-none">
              <button
                type="button"
                onClick={() => requestLeave(goBack)}
                className="min-w-0 flex-1 rounded-[14px] border border-[#e5e7eb] bg-white px-0 py-2.5 text-[14px] leading-[21px] font-bold text-[#364153] hover:bg-[#f7f8fa] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand xl:flex-none xl:px-6"
              >
                취소
              </button>
              <button
                type="submit"
                // 저장 중에는 disabled 대신 aria-disabled — 포커스된 버튼이 disabled가 되면 포커스가 body로 빠진다(MY-03 D11).
                disabled={saveBlocked && !saving}
                aria-disabled={saving || undefined}
                aria-busy={saving || undefined}
                aria-describedby={isDesktop ? ids.notice : undefined}
                className={`flex min-w-0 flex-1 items-center justify-center gap-2 rounded-[14px] border border-transparent px-0 py-2.5 text-[14px] leading-[21px] font-bold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand xl:flex-none xl:px-8 ${
                  saveBlocked && !saving
                    ? 'cursor-not-allowed bg-[#e5e7eb] text-[#9ca3af]'
                    : 'bg-brand text-white shadow-[0_4px_14px_rgba(15,92,84,0.30)] hover:bg-[#0b4a43]'
                }`}
              >
                {saving && <span aria-hidden="true" className="size-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
                {saving ? '저장 중' : '저장'}
              </button>
            </div>
          </div>
        </form>
      </div>

      <Modal
        open={pendingLeave !== null}
        onClose={() => setPendingLeave(null)}
        role="alertdialog"
        variant="confirm"
        describedBy="profile-leave-description"
        title="변경 사항을 저장하지 않고 나갈까요?"
        footer={
          <div className={CONFIRM_FOOTER_ROW_CLASS}>
            <button
              type="button"
              onClick={() => {
                const leave = pendingLeave;
                setPendingLeave(null);
                leave?.run();
              }}
              className={CONFIRM_DANGER_BUTTON_CLASS}
            >
              나가기
            </button>
            <button type="button" onClick={() => setPendingLeave(null)} className={CONFIRM_SECONDARY_BUTTON_CLASS}>
              계속 수정
            </button>
          </div>
        }
      >
        <p id="profile-leave-description" className="text-[13px] leading-[21.125px] text-[#4a5565] xl:text-[14px] xl:leading-[22.75px]">
          저장하지 않은 변경사항이 있습니다.
        </p>
      </Modal>
    </>
  );
}

/**
 * 입력 한 칸(Figma 6.1: 높이 47, #f9fafb, 테두리 #e5e7eb, radius 14). 오류면 테두리 #ef4444 + 3px 링, 아래에 role="alert"
 * 문구를 붙이고 aria-invalid·aria-describedby로 잇는다(AUTH-01 패턴). 사용자가 고치면 호출자가 오류를 지운다.
 */
function Field({
  id,
  label,
  value,
  onChange,
  error,
  invalid,
  help,
  readOnly,
  type = 'text',
  inputRef,
  endAdornment,
  autoComplete,
  describedBy,
}: {
  id: string;
  label: string;
  value: string;
  onChange?: (value: string) => void;
  error?: string;
  /** 문구 없이 테두리만 오류로(확인값 불일치 — 문구는 아래 FieldHint). */
  invalid?: boolean;
  help?: string;
  readOnly?: boolean;
  type?: string;
  inputRef?: Ref<HTMLInputElement>;
  endAdornment?: ReactNode;
  autoComplete?: string;
  describedBy?: string;
}) {
  const showError = error !== undefined || invalid === true;
  const described = [error !== undefined ? `${id}-error` : null, help ? `${id}-help` : null, describedBy ?? null].filter(Boolean).join(' ');
  return (
    <div className="flex flex-col">
      <label htmlFor={id} className="mb-1.5 text-[13px] leading-[19.5px] font-bold text-[#364153]">
        {label}
      </label>
      <div className="relative">
        <input
          ref={inputRef}
          id={id}
          type={type}
          value={value}
          readOnly={readOnly}
          aria-readonly={readOnly || undefined}
          aria-invalid={showError || undefined}
          aria-describedby={described || undefined}
          autoComplete={autoComplete}
          onChange={(event) => onChange?.(event.target.value)}
          className={`h-[47px] w-full rounded-[14px] border bg-[#f9fafb] px-4 text-[14px] text-[#1c1c1e] outline-none transition-colors placeholder:text-[#1c1c1e]/50 ${
            endAdornment ? 'pr-11' : ''
          } ${
            showError
              ? 'border-[#ef4444] shadow-[0_0_0_3px_rgba(239,68,68,0.10)]'
              : readOnly
                ? 'border-[#e5e7eb]'
                : 'border-[#e5e7eb] focus:border-brand'
          }`}
        />
        {endAdornment && <div className="absolute inset-y-0 right-3.5 flex items-center">{endAdornment}</div>}
      </div>
      {error !== undefined && (
        <p id={`${id}-error`} role="alert" className="mt-1.5 flex items-center gap-1 text-[12px] leading-[18px] font-medium text-[#fb2c36]">
          <AlertCircleIcon aria-hidden="true" className="size-3.5 shrink-0" />
          <span>{error}</span>
        </p>
      )}
      {help && (
        <p id={`${id}-help`} className="mt-1.5 text-[12px] leading-[18px] font-medium text-[#99a1af]">
          {help}
        </p>
      )}
    </div>
  );
}

function ErrorBanner({ message, onRetry, retryDisabled }: { message: string; onRetry: () => void; retryDisabled?: boolean }) {
  return (
    <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-[12px] border border-[#fee2e2] bg-[#fef2f2] px-4 py-3">
      <p className="flex items-center gap-2 text-[13px] text-[#7f1d1d]">
        <AlertCircleIcon aria-hidden="true" className="size-4 shrink-0 text-[#e7000b]" />
        <span>{message}</span>
      </p>
      <button
        type="button"
        onClick={onRetry}
        disabled={retryDisabled}
        className="rounded-[10px] bg-white px-3.5 py-1.5 text-[13px] font-semibold text-[#e7000b] shadow-[0_1px_2px_rgba(0,0,0,0.1)] disabled:cursor-not-allowed disabled:opacity-50"
      >
        다시 시도
      </button>
    </div>
  );
}

/** 불러오는 동안의 카드 자리(로딩). 머리는 뒤로가기만 있으면 되므로 확인 없이 바로 나가는 앱 바/제목을 쓴다. */
function FormSkeleton() {
  return (
    <>
      <FormHeader onLeave={(run) => run()} />
      <div className="p-4 xl:p-0">
        <div aria-busy="true" className={`xl:mt-6 ${CARD_CLASS} px-8 pt-7 pb-7`}>
          <span className="sr-only">회원정보를 불러오는 중</span>
          <div className="h-4 w-16 animate-pulse rounded bg-[#f3f4f6]" />
          <div className="mt-5 grid grid-cols-1 gap-5 xl:grid-cols-2">
            {[0, 1].map((index) => (
              <div key={index} className="flex flex-col gap-2">
                <div className="h-4 w-12 animate-pulse rounded bg-[#f3f4f6]" />
                <div className="h-[47px] animate-pulse rounded-[14px] bg-[#f3f4f6]" />
              </div>
            ))}
          </div>
          <div className="mt-8 h-4 w-24 animate-pulse rounded bg-[#f3f4f6]" />
        </div>
      </div>
    </>
  );
}
