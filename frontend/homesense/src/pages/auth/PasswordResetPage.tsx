import axios from 'axios';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertTriangleIcon } from '../../components/icons/AlertTriangleIcon';
import { ArrowLeftIcon } from '../../components/icons/ArrowLeftIcon';
import { CheckIcon } from '../../components/icons/CheckIcon';
import { CircleCheckIcon } from '../../components/icons/CircleCheckIcon';
import { ClockIcon } from '../../components/icons/ClockIcon';
import { EyeIcon } from '../../components/icons/EyeIcon';
import { EyeOffIcon } from '../../components/icons/EyeOffIcon';
import { HomeIcon } from '../../components/icons/HomeIcon';
import { LockIcon } from '../../components/icons/LockIcon';
import { MailIcon } from '../../components/icons/MailIcon';
import { AuthLayout } from '../../components/layout/AuthLayout';
import { Button } from '../../components/ui/Button';
import { FieldHint } from '../../components/ui/FieldHint';
import { Input, type FieldStatus } from '../../components/ui/Input';
import { PasswordChecklist } from '../../components/ui/PasswordChecklist';
import { Spinner } from '../../components/ui/Spinner';
import { TextField } from '../../components/ui/TextField';
import { requestPasswordReset, resetPassword, validatePasswordResetToken } from '../../features/auth/api';
import { evaluatePassword, isValidEmailFormat } from '../../features/auth/validation';
import type { ApiErrorResponse } from '../../types/api';

const GENERIC_ERROR_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
// InvalidResetTokenException의 기본 메시지와 동일한 문구 — 서버가 이 문구를 그대로 내려주므로
// 평소엔 노출될 일이 없지만, 응답 본문을 못 읽는 방어적인 경우(네트워크 파싱 실패 등)의 폴백이다.
const DEFAULT_INVALID_TOKEN_MESSAGE = '유효하지 않거나 만료된 재설정 링크입니다. 다시 요청해주세요';
// PasswordResetTokenService의 쿨다운 TTL(60초)과 동일 — 서버가 최종 권위이며 이 값은 UI 카운트다운
// 근사치일 뿐이다(정확한 잔여 시간은 서버만 안다).
const RESEND_COOLDOWN_SECONDS = 60;

/** 모든 화면 상단에 고정 배치되는 HomeSense 로고 — Figma는 카드 밖 좌상단에 두지만(LogoMark), 이
 * 프로젝트의 AuthLayout은 로고를 카드 안에 넣는 AUTH-01/02 관례를 이미 확립해 두었다(재사용). 화면
 * 하나만을 위해 두 화면에서 이미 검증된 공유 레이아웃을 바꾸는 대신, 카드 안에 두는 기존 관례를
 * 그대로 따르는 의도적 차이다(CLAUDE.md SCR-AUTH-03 절 참고). */
function LogoLockup() {
  return (
    <div className="flex w-full flex-col items-center pb-6">
      <div className="flex h-[52px] items-center gap-2">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-2xl bg-brand">
          <HomeIcon className="size-[18px]" />
        </div>
        <p className="text-lg font-extrabold tracking-[-0.4px] text-brand">HomeSense</p>
      </div>
    </div>
  );
}

/** Figma StepProgress(31:18187/31:18364) — 1단계(이메일 입력)/2단계(비밀번호 설정) 진행 표시. */
function StepIndicator({ step }: { step: 1 | 2 }) {
  const step1Done = step === 2;
  return (
    <div className="flex w-full items-center justify-center gap-2 pb-7">
      <div
        className={`flex size-6 shrink-0 items-center justify-center rounded-full text-[11px] font-bold ${
          step1Done ? 'bg-[#00c950] text-white' : 'bg-brand text-white'
        }`}
      >
        {step1Done ? <CheckIcon strokeWidth={1.16667} className="size-3.5" /> : '1'}
      </div>
      <span className={`text-[12px] font-semibold ${step1Done ? 'text-[#00a63e]' : 'text-brand'}`}>이메일 입력</span>
      <div className={`h-px w-8 shrink-0 ${step1Done ? 'bg-[#22c55e]' : 'bg-[#e5e7eb]'}`} />
      <div
        className={`flex size-6 shrink-0 items-center justify-center rounded-full text-[11px] font-bold ${
          step === 2 ? 'bg-brand text-white' : 'bg-[#e5e7eb] text-[#99a1af]'
        }`}
      >
        2
      </div>
      <span className={`text-[12px] font-semibold ${step === 2 ? 'text-brand' : 'text-[#99a1af]'}`}>비밀번호 설정</span>
    </div>
  );
}

/** Figma StepProgress(31:18508, 링크 만료 화면) — 스테퍼 대신 노출되는 상태 배지. */
function ExpiredBadge() {
  return (
    <div className="flex w-full justify-center pb-7">
      <div className="flex items-center gap-1.5 rounded-full border border-[#ffe2e2] bg-[#fef2f2] px-3.5 py-1.5">
        <ClockIcon className="size-3.5 text-[#e7000b]" />
        <span className="text-[12px] font-bold text-[#e7000b]">링크 만료</span>
      </div>
    </div>
  );
}

/** Figma의 Container(56x56, rounded-2xl) 아이콘 배지 — 완전한 원(rounded-full)이 아니라 둥근 사각형이다. */
function IconBadge({ tone, children }: { tone: 'neutral' | 'success' | 'error'; children: ReactNode }) {
  const bg = tone === 'neutral' ? 'bg-[#e8f2f0]' : tone === 'success' ? 'bg-[#dcfce7]' : 'bg-[#fef2f2]';
  return <div className={`flex size-14 shrink-0 items-center justify-center rounded-2xl ${bg}`}>{children}</div>;
}

function CardIntro({ icon, tone, title, description }: { icon: ReactNode; tone: 'neutral' | 'success' | 'error'; title: string; description: ReactNode }) {
  return (
    <div className="flex w-full flex-col items-center gap-2 pb-7 text-center">
      <IconBadge tone={tone}>{icon}</IconBadge>
      <p className="pt-2 text-2xl font-extrabold tracking-[-0.4px] text-[#101828]">{title}</p>
      <p className="text-[14px] leading-[1.6] text-[#6a7282]">{description}</p>
    </div>
  );
}

function BackToLogin() {
  return (
    <div className="flex w-full flex-col items-center pt-5">
      <Link
        to="/login"
        className="flex items-center gap-1.5 text-[13px] font-semibold text-[#6a7282] hover:text-[#364153]"
      >
        <ArrowLeftIcon className="size-3.5" />
        로그인으로 돌아가기
      </Link>
    </div>
  );
}

interface RequestFormValues {
  email: string;
}

/** AUTH-03 1단계 — 이메일 입력 후 재설정 링크 발송을 요청한다. */
function RequestStep() {
  const [phase, setPhase] = useState<'form' | 'sent'>('form');
  const [sentEmail, setSentEmail] = useState('');
  const [serverError, setServerError] = useState<string | null>(null);
  const [isRequesting, setIsRequesting] = useState(false);
  // 서버 쿨다운(60초)의 클라이언트 근사치 — 정확한 잔여 시간은 서버만 안다. 0이 되면 재발송 버튼이
  // 활성화되지만, 실제로는 여전히 서버가 429로 거부할 수 있다(그 경우 아래 catch가 다시 쿨다운을 건다).
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) {
      return;
    }
    const timer = setTimeout(() => setCooldown((prev) => prev - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  const {
    register,
    handleSubmit,
    watch,
  } = useForm<RequestFormValues>({ defaultValues: { email: '' } });

  const email = watch('email');
  const emailValid = isValidEmailFormat(email);

  async function submitRequest(targetEmail: string) {
    setIsRequesting(true);
    setServerError(null);
    try {
      await requestPasswordReset({ email: targetEmail });
      setSentEmail(targetEmail);
      setPhase('sent');
      setCooldown(RESEND_COOLDOWN_SECONDS);
    } catch (error) {
      // 429(PASSWORD_RESET_COOLDOWN)만 서버 문구를 그대로 보여준다 — 그 외에는 계정 존재 여부와
      // 무관하게 항상 같은 성공 응답만 오도록 서버가 설계돼 있어(AuthController.java 참고) 이 외의
      // 분기는 원래 존재하지 않는다.
      if (axios.isAxiosError<ApiErrorResponse>(error) && error.response?.status === 429) {
        setServerError(error.response.data?.error?.message ?? GENERIC_ERROR_MESSAGE);
        setCooldown((prev) => Math.max(prev, RESEND_COOLDOWN_SECONDS));
        return;
      }
      setServerError(GENERIC_ERROR_MESSAGE);
    } finally {
      setIsRequesting(false);
    }
  }

  const onSubmit = handleSubmit((values) => submitRequest(values.email.trim()));
  const handleResend = () => {
    if (cooldown === 0 && !isRequesting) {
      void submitRequest(sentEmail);
    }
  };

  if (phase === 'sent') {
    return (
      <>
        <LogoLockup />
        <StepIndicator step={1} />
        <CardIntro
          tone="success"
          icon={<CircleCheckIcon className="size-7 text-[#00a63e]" />}
          title="이메일을 발송했습니다"
          description="입력하신 이메일로 재설정 링크를 발송했습니다."
        />
        <div className="flex w-full items-center gap-3 rounded-[14px] border border-[#e5e7eb] bg-[#f9fafb] px-4 py-3">
          <MailIcon className="size-4 shrink-0 text-[#99a1af]" />
          <p className="truncate text-[14px] font-medium text-[#364153]">{sentEmail}</p>
        </div>
        <p className="w-full pt-2 text-center text-[11.5px] text-[#99a1af]">
          보안상 이메일 존재 여부와 무관하게 동일한 안내 문구를 표시합니다.
        </p>
        <div className="w-full pt-6">
          {cooldown > 0 ? (
            <div className="flex h-[54.5px] w-full items-center justify-center gap-2 rounded-[14px] border-2 border-[#e5e7eb] bg-white text-[15px] font-bold text-[#9ca3af]">
              <ClockIcon className="size-4" />
              {cooldown}초 후 재발송 가능
            </div>
          ) : (
            <Button type="button" onClick={handleResend} disabled={isRequesting}>
              재설정 링크 발송
            </Button>
          )}
          {serverError && <FieldHint status="error" message={serverError} />}
        </div>
        <BackToLogin />
      </>
    );
  }

  return (
    <>
      <LogoLockup />
      <StepIndicator step={1} />
      <CardIntro
        tone="neutral"
        icon={<LockIcon className="size-7 text-brand" />}
        title="비밀번호 찾기"
        description={
          <>
            가입 시 등록한 이메일을 입력해주세요.
            <br />
            재설정 링크를 이메일로 보내드립니다.
          </>
        }
      />
      <form noValidate onSubmit={onSubmit} className="flex w-full flex-col gap-4">
        <TextField
          id="email"
          label="이메일"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          status={serverError ? 'error' : 'default'}
          aria-invalid={serverError !== null}
          aria-describedby={serverError ? 'request-error' : undefined}
          {...register('email', { onChange: () => setServerError(null) })}
        />
        {serverError && <FieldHint id="request-error" status="error" message={serverError} />}
        <div className="w-full pt-1">
          <Button type="submit" disabled={!emailValid || isRequesting}>
            재설정 링크 발송
          </Button>
        </div>
      </form>
      <BackToLogin />
    </>
  );
}

interface ConfirmFormValues {
  newPassword: string;
  newPasswordConfirm: string;
}

type TokenStatus = 'validating' | 'valid' | 'invalid' | 'error';

/** AUTH-03 2단계 — 토큰을 먼저 소비 없이 검증(peek)한 뒤에만 새 비밀번호 폼을 보여준다. */
function ConfirmStep({ token }: { token: string }) {
  const navigate = useNavigate();
  const [tokenStatus, setTokenStatus] = useState<TokenStatus>('validating');
  const [invalidMessage, setInvalidMessage] = useState(DEFAULT_INVALID_TOKEN_MESSAGE);
  const [formError, setFormError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [success, setSuccess] = useState(false);
  // 재시도 트리거 — 값이 바뀔 때마다 아래 useEffect가 토큰 검증을 다시 시도한다("다시 시도" 버튼용).
  const [validationAttempt, setValidationAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setTokenStatus('validating');
    validatePasswordResetToken(token)
      .then(() => {
        if (!cancelled) {
          setTokenStatus('valid');
        }
      })
      .catch((error) => {
        if (cancelled) {
          return;
        }
        // 문서화된 무효 토큰 응답(400 INVALID_RESET_TOKEN)만 "링크 만료"로 취급한다 — 오프라인이거나
        // 서버가 5xx를 반환한 경우(네트워크/서버 일시 장애)까지 여기서 함께 "만료"로 단정하면, 실제로는
        // 유효한 링크를 들고 있는 사용자에게 "다시 요청하라"고 잘못 안내하게 된다. 그런 전송 계층
        // 실패는 별도의 재시도 가능한 상태로 분리한다.
        if (axios.isAxiosError<ApiErrorResponse>(error) && error.response?.status === 400) {
          setInvalidMessage(error.response.data?.error?.message ?? DEFAULT_INVALID_TOKEN_MESSAGE);
          setTokenStatus('invalid');
          return;
        }
        setTokenStatus('error');
      });
    return () => {
      cancelled = true;
    };
  }, [token, validationAttempt]);

  const {
    register,
    handleSubmit,
    watch,
    formState: { isSubmitting },
  } = useForm<ConfirmFormValues>({ defaultValues: { newPassword: '', newPasswordConfirm: '' } });

  const newPassword = watch('newPassword');
  const newPasswordConfirm = watch('newPasswordConfirm');
  const passwordPolicy = useMemo(() => evaluatePassword(newPassword), [newPassword]);
  const confirmTouched = newPasswordConfirm.length > 0;
  const confirmValid = confirmTouched && newPasswordConfirm === newPassword;
  const passwordFieldStatus: FieldStatus = newPassword.length === 0 ? 'default' : passwordPolicy.isValid ? 'success' : 'error';
  const confirmFieldStatus: FieldStatus = !confirmTouched ? 'default' : confirmValid ? 'success' : 'error';
  const canSubmit = passwordPolicy.isValid && confirmValid && !isSubmitting;

  const onSubmit = handleSubmit(async (values) => {
    if (!canSubmit) {
      return;
    }
    setFormError(null);
    try {
      await resetPassword({ token, newPassword: values.newPassword });
      setSuccess(true);
    } catch (error) {
      if (axios.isAxiosError<ApiErrorResponse>(error) && error.response?.status === 400) {
        // 사전 검증(validate-token) 이후 시간이 지났거나 다른 탭/기기에서 먼저 소비됐을 수 있다 —
        // 같은 INVALID_RESET_TOKEN이므로 아래의 "링크 만료" 화면으로 그대로 전환한다.
        setInvalidMessage(error.response.data?.error?.message ?? DEFAULT_INVALID_TOKEN_MESSAGE);
        setTokenStatus('invalid');
        return;
      }
      setFormError(GENERIC_ERROR_MESSAGE);
    }
  });

  if (tokenStatus === 'validating') {
    return (
      <>
        <LogoLockup />
        <div className="flex w-full items-center justify-center py-16">
          <Spinner />
        </div>
      </>
    );
  }

  if (tokenStatus === 'error') {
    return (
      <>
        <LogoLockup />
        <CardIntro
          tone="error"
          icon={<AlertTriangleIcon className="size-7 text-[#fb2c36]" />}
          title="링크를 확인할 수 없습니다"
          description={GENERIC_ERROR_MESSAGE}
        />
        <div className="w-full pt-6">
          <Button type="button" onClick={() => setValidationAttempt((prev) => prev + 1)}>
            다시 시도
          </Button>
        </div>
        <BackToLogin />
      </>
    );
  }

  if (tokenStatus === 'invalid') {
    return (
      <>
        <LogoLockup />
        <ExpiredBadge />
        <CardIntro
          tone="error"
          icon={<ClockIcon className="size-7 text-[#fb2c36]" />}
          title="링크가 만료되었습니다"
          description={invalidMessage}
        />
        <div className="flex w-full items-start gap-3 rounded-2xl border border-[#fecaca] bg-[#fef2f2] px-4 py-3.5">
          <AlertTriangleIcon className="mt-0.5 size-4 shrink-0 text-[#fb2c36]" />
          <p className="text-[13px] leading-[1.6] text-[#e7000b]">
            재설정 링크는 발송 후 <span className="font-bold">30분간</span> 유효합니다.
            <br />
            스팸함도 확인해보세요.
          </p>
        </div>
        <div className="w-full pt-6">
          <Button type="button" onClick={() => navigate('/password-reset', { replace: true })}>
            재설정 링크 다시 요청
          </Button>
        </div>
        <BackToLogin />
      </>
    );
  }

  if (success) {
    return (
      <>
        <LogoLockup />
        <CardIntro
          tone="success"
          icon={<CircleCheckIcon className="size-7 text-[#00a63e]" />}
          title="비밀번호가 변경되었습니다"
          description={
            <>
              새 비밀번호로 로그인해주세요.
              <br />
              다른 기기에 로그인돼 있었다면 그 세션도 함께 종료됩니다.
            </>
          }
        />
        <div className="w-full">
          <Button type="button" onClick={() => navigate('/login', { replace: true })}>
            로그인하러 가기
          </Button>
        </div>
      </>
    );
  }

  return (
    <>
      <LogoLockup />
      <StepIndicator step={2} />
      <CardIntro
        tone="neutral"
        icon={<LockIcon className="size-7 text-brand" />}
        title="새 비밀번호 설정"
        description="안전한 새 비밀번호를 설정해주세요."
      />
      <form noValidate onSubmit={onSubmit} className="flex w-full flex-col gap-4">
        <div className="flex w-full flex-col">
          <label htmlFor="newPassword" className="text-[13px] font-semibold text-[#364153]">
            새 비밀번호
          </label>
          <div className="pt-1.5">
            <Input
              id="newPassword"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="새 비밀번호 입력"
              status={passwordFieldStatus}
              aria-invalid={passwordFieldStatus === 'error'}
              endAdornment={
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'}
                  className="text-[#99a1af] hover:text-[#6a7282]"
                >
                  {showPassword ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
                </button>
              }
              {...register('newPassword')}
            />
          </div>
          <PasswordChecklist password={newPassword} />
        </div>

        <div className="flex w-full flex-col">
          <label htmlFor="newPasswordConfirm" className="text-[13px] font-semibold text-[#364153]">
            새 비밀번호 확인
          </label>
          <div className="pt-1.5">
            <Input
              id="newPasswordConfirm"
              type={showPasswordConfirm ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="비밀번호 재입력"
              status={confirmFieldStatus}
              aria-invalid={confirmFieldStatus === 'error'}
              aria-describedby={confirmTouched ? 'password-confirm-hint' : undefined}
              endAdornment={
                <button
                  type="button"
                  onClick={() => setShowPasswordConfirm((prev) => !prev)}
                  aria-label={showPasswordConfirm ? '비밀번호 숨기기' : '비밀번호 표시'}
                  className="text-[#99a1af] hover:text-[#6a7282]"
                >
                  {showPasswordConfirm ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
                </button>
              }
              {...register('newPasswordConfirm')}
            />
          </div>
          {confirmTouched && (
            <FieldHint
              id="password-confirm-hint"
              status={confirmValid ? 'success' : 'error'}
              message={confirmValid ? '비밀번호가 일치합니다.' : '비밀번호가 일치하지 않습니다'}
            />
          )}
        </div>

        {formError && <FieldHint status="error" message={formError} />}

        <Button type="submit" disabled={!canSubmit}>
          비밀번호 변경 완료
        </Button>
      </form>
      <BackToLogin />
    </>
  );
}

/**
 * AUTH-03(신규 제안, 프로그램목록서 미반영) — `?token=` 유무로 1단계(이메일 입력)/2단계(새 비밀번호
 * 설정)를 같은 화면 ID 안에서 순차 전환한다(UI정의서 5.1절). 백엔드가 발송하는 재설정 링크가
 * `{baseUrl}/password-reset?token=...` 형식으로 고정돼 있어 이 라우트 경로 자체를 바꾸면 이미 발송된
 * 메일의 링크가 깨진다 — AppRouter.tsx의 경로와 반드시 함께 맞춰야 한다.
 */
export function PasswordResetPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  return <AuthLayout>{token ? <ConfirmStep key={token} token={token} /> : <RequestStep />}</AuthLayout>;
}
