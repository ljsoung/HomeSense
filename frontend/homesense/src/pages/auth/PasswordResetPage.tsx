import axios from 'axios';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircleIcon } from '../../components/icons/AlertCircleIcon';
import { CheckIcon } from '../../components/icons/CheckIcon';
import { EyeIcon } from '../../components/icons/EyeIcon';
import { EyeOffIcon } from '../../components/icons/EyeOffIcon';
import { HomeIcon } from '../../components/icons/HomeIcon';
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

function Header({ title }: { title: string }) {
  return (
    <div className="flex w-full flex-col items-center pb-7">
      <div className="flex h-[52px] items-center gap-2 pb-4">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-2xl bg-brand">
          <HomeIcon className="size-[18px]" />
        </div>
        <p className="text-lg font-extrabold tracking-[-0.4px] text-brand">HomeSense</p>
      </div>
      <p className="text-2xl font-extrabold tracking-[-0.5px] text-[#101828]">{title}</p>
    </div>
  );
}

function BackToLogin() {
  return (
    <div className="flex w-full flex-col items-center pt-5">
      <Link to="/login" className="text-[13px] font-bold text-brand">
        로그인으로 돌아가기
      </Link>
    </div>
  );
}

function StatusBadge({ tone, children }: { tone: 'success' | 'error'; children: ReactNode }) {
  return (
    <div
      className={`flex size-12 items-center justify-center rounded-full ${
        tone === 'success' ? 'bg-[#dcfce7]' : 'bg-[#ffe2e2]'
      }`}
    >
      {children}
    </div>
  );
}

interface RequestFormValues {
  email: string;
}

/** AUTH-03 1단계 — 이메일 입력 후 재설정 링크 발송을 요청한다. */
function RequestStep() {
  const [sent, setSent] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    formState: { isSubmitting },
  } = useForm<RequestFormValues>({ defaultValues: { email: '' } });

  const email = watch('email');
  const emailValid = isValidEmailFormat(email);

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      await requestPasswordReset({ email: values.email.trim() });
      setSent(true);
    } catch (error) {
      // 429(PASSWORD_RESET_COOLDOWN)만 서버 문구를 그대로 보여준다 — 그 외에는 계정 존재 여부와
      // 무관하게 항상 같은 성공 응답만 오도록 서버가 설계돼 있어(AuthController.java 참고) 이 외의
      // 분기는 원래 존재하지 않는다.
      if (axios.isAxiosError<ApiErrorResponse>(error) && error.response?.status === 429) {
        setServerError(error.response.data?.error?.message ?? GENERIC_ERROR_MESSAGE);
        return;
      }
      setServerError(GENERIC_ERROR_MESSAGE);
    }
  });

  if (sent) {
    return (
      <>
        <Header title="이메일을 확인해주세요" />
        <div className="flex w-full flex-col items-center gap-2 text-center">
          <StatusBadge tone="success">
            <CheckIcon strokeWidth={1.5} className="size-6 text-[#00a63e]" />
          </StatusBadge>
          <p className="pt-2 text-[14px] text-[#364153]">
            입력하신 이메일로 재설정 링크를 발송했습니다.
            <br />
            메일이 보이지 않으면 스팸함도 확인해주세요.
          </p>
        </div>
        <div className="w-full pt-6">
          <Button type="button" onClick={() => setSent(false)}>
            다른 이메일로 다시 시도
          </Button>
        </div>
        <BackToLogin />
      </>
    );
  }

  return (
    <>
      <Header title="비밀번호 찾기" />
      <p className="w-full pb-6 text-center text-[14px] text-[#6a7282]">
        가입 시 등록한 이메일을 입력해주세요.
        <br />
        비밀번호를 재설정할 수 있는 링크를 보내드립니다.
      </p>
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
          <Button type="submit" disabled={!emailValid || isSubmitting}>
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

type TokenStatus = 'validating' | 'valid' | 'invalid';

/** AUTH-03 2단계 — 토큰을 먼저 소비 없이 검증(peek)한 뒤에만 새 비밀번호 폼을 보여준다. */
function ConfirmStep({ token }: { token: string }) {
  const navigate = useNavigate();
  const [tokenStatus, setTokenStatus] = useState<TokenStatus>('validating');
  const [invalidMessage, setInvalidMessage] = useState(DEFAULT_INVALID_TOKEN_MESSAGE);
  const [formError, setFormError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    let cancelled = false;
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
        if (axios.isAxiosError<ApiErrorResponse>(error) && error.response?.status === 400) {
          setInvalidMessage(error.response.data?.error?.message ?? DEFAULT_INVALID_TOKEN_MESSAGE);
        }
        setTokenStatus('invalid');
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

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
        <Header title="확인 중" />
        <div className="flex w-full items-center justify-center py-10">
          <Spinner />
        </div>
      </>
    );
  }

  if (tokenStatus === 'invalid') {
    return (
      <>
        <Header title="링크가 만료되었습니다" />
        <div className="flex w-full flex-col items-center gap-2 text-center">
          <StatusBadge tone="error">
            <AlertCircleIcon className="size-6 text-[#fb2c36]" />
          </StatusBadge>
          <p className="pt-2 text-[14px] text-[#364153]">{invalidMessage}</p>
        </div>
        <div className="w-full pt-6">
          <Button type="button" onClick={() => navigate('/password-reset', { replace: true })}>
            재설정 다시 요청
          </Button>
        </div>
        <BackToLogin />
      </>
    );
  }

  if (success) {
    return (
      <>
        <Header title="비밀번호가 변경되었습니다" />
        <div className="flex w-full flex-col items-center gap-2 text-center">
          <StatusBadge tone="success">
            <CheckIcon strokeWidth={1.5} className="size-6 text-[#00a63e]" />
          </StatusBadge>
          <p className="pt-2 text-[14px] text-[#364153]">
            새 비밀번호로 로그인해주세요.
            <br />
            다른 기기에 로그인돼 있었다면 그 세션도 함께 종료됩니다.
          </p>
        </div>
        <div className="w-full pt-6">
          <Button type="button" onClick={() => navigate('/login', { replace: true })}>
            로그인하러 가기
          </Button>
        </div>
      </>
    );
  }

  return (
    <>
      <Header title="새 비밀번호 설정" />
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
              placeholder="새 비밀번호 재입력"
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
