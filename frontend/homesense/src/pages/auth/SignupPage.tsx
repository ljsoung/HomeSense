import axios from 'axios';
import { useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { EyeIcon } from '../../components/icons/EyeIcon';
import { EyeOffIcon } from '../../components/icons/EyeOffIcon';
import { HomeIcon } from '../../components/icons/HomeIcon';
import { AuthLayout } from '../../components/layout/AuthLayout';
import { Button } from '../../components/ui/Button';
import { Checkbox } from '../../components/ui/Checkbox';
import { FieldHint } from '../../components/ui/FieldHint';
import { Input, type FieldStatus } from '../../components/ui/Input';
import { PasswordChecklist } from '../../components/ui/PasswordChecklist';
import { TextField } from '../../components/ui/TextField';
import { checkEmail } from '../../features/auth/api';
import { useAuth } from '../../features/auth/useAuth';
import { evaluatePassword, isValidEmailFormat, isValidNickname } from '../../features/auth/validation';
import type { ApiErrorResponse } from '../../types/api';

const GENERIC_ERROR_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
const INVALID_EMAIL_MESSAGE = '올바른 이메일 형식이 아닙니다';
const EMAIL_CHECK_FAILED_MESSAGE = '이메일 확인 중 오류가 발생했습니다. 다시 시도해주세요.';
// ValidNickname.message()와 동일한 문구 — 서버가 방어적으로 같은 오류를 돌려줄 때도 문구가 어긋나지 않도록.
const NICKNAME_INVALID_MESSAGE = '닉네임은 2자 이상 12자 이하여야 합니다';

interface SignupFormValues {
  email: string;
  password: string;
  passwordConfirm: string;
  nickname: string;
}

type EmailCheckState =
  | { status: 'idle' }
  | { status: 'invalid' }
  | { status: 'checking' }
  | { status: 'available'; checkedEmail: string }
  | { status: 'duplicate'; checkedEmail: string; message: string }
  | { status: 'error'; message: string };

export function SignupPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [agreeToTerms, setAgreeToTerms] = useState(false);
  const [emailCheck, setEmailCheck] = useState<EmailCheckState>({ status: 'idle' });
  const [serverFieldErrors, setServerFieldErrors] = useState<Partial<Record<'email' | 'password' | 'nickname', string>>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setFocus,
    formState: { isSubmitting },
  } = useForm<SignupFormValues>({
    defaultValues: { email: '', password: '', passwordConfirm: '', nickname: '' },
  });

  const email = watch('email');
  const password = watch('password');
  const passwordConfirm = watch('passwordConfirm');
  const nickname = watch('nickname');

  const passwordPolicy = useMemo(() => evaluatePassword(password), [password]);
  const passwordConfirmTouched = passwordConfirm.length > 0;
  const passwordConfirmValid = passwordConfirmTouched && passwordConfirm === password;
  const nicknameTouched = nickname.length > 0;
  // 실제로 서버에 보내는 값(onSubmit의 values.nickname.trim())과 동일한 값을 검증해야 한다 —
  // 원본 문자열로만 검사하면 " a"(2자, 통과) 같은 값이 trim 후 "a"(1자)가 돼 서버에서 거부되거나,
  // 반대로 끝에 공백이 붙은 유효한 12자 닉네임이 raw 길이(13자) 때문에 부당하게 막힐 수 있다.
  const nicknameValid = isValidNickname(nickname.trim());

  // 이메일 중복확인은 비동기라 응답 순서가 요청 순서와 다르게 도착할 수 있다 — 이 카운터를
  // "가장 최근 요청/편집"의 식별자로 써서, 늦게 도착한 응답이 그 사이 편집된 최신 상태를
  // 덮어쓰지 않게 막는다(예: A 조회 중 B로 수정 후 B가 먼저 끝나도, 나중에 도착한 A의 결과가
  // B의 상태를 덮어쓰면 안 됨).
  const emailCheckRequestId = useRef(0);

  const resetEmailCheck = () => {
    emailCheckRequestId.current += 1;
    setEmailCheck({ status: 'idle' });
  };

  async function handleCheckEmail() {
    const value = email.trim();
    if (emailCheck.status === 'checking' || value.length === 0) {
      return;
    }
    if (!isValidEmailFormat(value)) {
      setEmailCheck({ status: 'invalid' });
      return;
    }
    const requestId = ++emailCheckRequestId.current;
    setEmailCheck({ status: 'checking' });
    try {
      const result = await checkEmail(value);
      if (requestId !== emailCheckRequestId.current) {
        return; // 그 사이 값이 바뀌었거나 새 요청이 시작됨 — 이 응답은 폐기
      }
      setEmailCheck(
        result.duplicate
          ? { status: 'duplicate', checkedEmail: value, message: '이미 사용 중인 이메일입니다' }
          : { status: 'available', checkedEmail: value },
      );
    } catch {
      if (requestId !== emailCheckRequestId.current) {
        return;
      }
      setEmailCheck({ status: 'error', message: EMAIL_CHECK_FAILED_MESSAGE });
    }
  }

  const emailHint = serverFieldErrors.email
    ? { status: 'error' as const, message: serverFieldErrors.email }
    : emailCheck.status === 'available'
      ? { status: 'success' as const, message: '사용 가능한 이메일입니다.' }
      : emailCheck.status === 'duplicate'
        ? { status: 'error' as const, message: emailCheck.message }
        : emailCheck.status === 'invalid'
          ? { status: 'error' as const, message: INVALID_EMAIL_MESSAGE }
          : emailCheck.status === 'error'
            ? { status: 'error' as const, message: emailCheck.message }
            : null;

  const emailFieldStatus: FieldStatus = emailHint ? emailHint.status : 'default';
  const passwordFieldStatus: FieldStatus = password.length === 0 ? 'default' : passwordPolicy.isValid ? 'success' : 'error';
  const passwordConfirmHint = serverFieldErrors.password
    ? { status: 'error' as const, message: serverFieldErrors.password }
    : !passwordConfirmTouched
      ? null
      : passwordConfirmValid
        ? { status: 'success' as const, message: '비밀번호가 일치합니다.' }
        : { status: 'error' as const, message: '비밀번호가 일치하지 않습니다' };
  const passwordConfirmFieldStatus: FieldStatus = !passwordConfirmTouched ? 'default' : passwordConfirmValid ? 'success' : 'error';
  const nicknameHint = serverFieldErrors.nickname
    ? { status: 'error' as const, message: serverFieldErrors.nickname }
    : !nicknameTouched
      ? null
      : nicknameValid
        ? null
        : { status: 'error' as const, message: NICKNAME_INVALID_MESSAGE };
  const nicknameFieldStatus: FieldStatus = !nicknameTouched ? 'default' : nicknameValid ? 'success' : 'error';

  const canSubmit =
    emailCheck.status === 'available' &&
    emailCheck.checkedEmail === email.trim() &&
    passwordPolicy.isValid &&
    passwordConfirmValid &&
    nicknameValid &&
    agreeToTerms &&
    !isSubmitting;

  const onSubmit = handleSubmit(async (values) => {
    if (!canSubmit) {
      return;
    }
    setFormError(null);
    setServerFieldErrors({});
    try {
      await auth.signup({
        email: values.email.trim(),
        password: values.password,
        nickname: values.nickname.trim(),
      });
      navigate('/', { replace: true });
    } catch (error) {
      if (axios.isAxiosError<ApiErrorResponse>(error) && error.response) {
        const status = error.response.status;
        const body = error.response.data;
        if (status === 409) {
          setServerFieldErrors({ email: body?.error?.message ?? '이미 사용 중인 이메일입니다' });
          resetEmailCheck();
          setFocus('email');
          return;
        }
        if (status === 400 && body?.error?.fieldErrors?.length) {
          const mapped: Partial<Record<'email' | 'password' | 'nickname', string>> = {};
          for (const fieldError of body.error.fieldErrors) {
            if (fieldError.field === 'email' || fieldError.field === 'password' || fieldError.field === 'nickname') {
              mapped[fieldError.field] = fieldError.message;
            }
          }
          setServerFieldErrors(mapped);
          return;
        }
      }
      setFormError(GENERIC_ERROR_MESSAGE);
    }
  });

  return (
    <AuthLayout>
      <div className="flex w-full flex-col items-center pb-7">
        <div className="flex h-[52px] items-center gap-2 pb-4">
          <div className="flex size-9 shrink-0 items-center justify-center rounded-2xl bg-brand">
            <HomeIcon className="size-[18px]" />
          </div>
          <p className="text-lg font-extrabold tracking-[-0.4px] text-brand">HomeSense</p>
        </div>
        <p className="text-2xl font-extrabold tracking-[-0.5px] text-[#101828]">회원가입</p>
      </div>

      <form noValidate onSubmit={onSubmit} className="flex w-full flex-col items-start gap-3.5 md:gap-4">
        <div className="flex w-full flex-col">
          <label htmlFor="email" className="text-[13px] font-semibold text-[#364153]">
            이메일 <span className="text-[11px] font-normal text-[#99a1af]">포커스 아웃 시 중복 확인</span>
          </label>
          <div className="flex items-start gap-2 pt-1.5">
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              status={emailFieldStatus}
              className="flex-1"
              aria-invalid={emailFieldStatus === 'error'}
              aria-describedby={emailHint ? 'email-hint' : undefined}
              {...register('email', { onChange: resetEmailCheck, onBlur: handleCheckEmail })}
            />
            <button
              type="button"
              onClick={handleCheckEmail}
              disabled={email.trim().length === 0 || emailCheck.status === 'checking'}
              className="h-11 shrink-0 rounded-[14px] border border-[#e5e7eb] px-4 text-[13px] font-semibold text-[#364153] transition-colors hover:bg-[#f7f8fa] disabled:cursor-not-allowed disabled:opacity-50"
            >
              중복확인
            </button>
          </div>
          {emailHint && <FieldHint id="email-hint" status={emailHint.status} message={emailHint.message} />}
        </div>

        <div className="flex w-full flex-col">
          <label htmlFor="password" className="text-[13px] font-semibold text-[#364153]">
            비밀번호
          </label>
          <div className="pt-1.5">
            <Input
              id="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="비밀번호 입력"
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
              {...register('password')}
            />
          </div>
          <PasswordChecklist password={password} />
          {serverFieldErrors.password && <FieldHint status="error" message={serverFieldErrors.password} />}
        </div>

        <div className="flex w-full flex-col">
          <label htmlFor="passwordConfirm" className="text-[13px] font-semibold text-[#364153]">
            비밀번호 확인
          </label>
          <div className="pt-1.5">
            <Input
              id="passwordConfirm"
              type={showPasswordConfirm ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="비밀번호 재입력"
              status={passwordConfirmFieldStatus}
              aria-invalid={passwordConfirmFieldStatus === 'error'}
              aria-describedby={passwordConfirmHint ? 'password-confirm-hint' : undefined}
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
              {...register('passwordConfirm')}
            />
          </div>
          {passwordConfirmHint && (
            <FieldHint id="password-confirm-hint" status={passwordConfirmHint.status} message={passwordConfirmHint.message} />
          )}
        </div>

        <TextField
          id="nickname"
          label="닉네임"
          autoComplete="nickname"
          placeholder="화면에 표시될 이름"
          status={nicknameFieldStatus}
          aria-invalid={nicknameFieldStatus === 'error'}
          aria-describedby={nicknameHint ? 'nickname-hint' : undefined}
          {...register('nickname')}
        />
        {nicknameHint && <FieldHint id="nickname-hint" status={nicknameHint.status} message={nicknameHint.message} />}

        <div className="flex w-full flex-col gap-1 pt-1">
          <div className="h-px w-full bg-[#f3f4f6]" />
          <div className="pt-1">
            <Checkbox
              id="agreeToTerms"
              checked={agreeToTerms}
              onChange={setAgreeToTerms}
              label={
                <>
                  <span className="font-semibold text-brand underline lg:no-underline">이용약관</span>
                  <span> 및 </span>
                  <span className="font-semibold text-brand underline lg:no-underline">개인정보처리방침</span>
                  <span>에 동의합니다 </span>
                  <span className="text-[#99a1af]">(필수)</span>
                </>
              }
            />
          </div>
        </div>

        {formError && <FieldHint status="error" message={formError} />}

        <Button type="submit" disabled={!canSubmit}>
          가입하기
        </Button>
      </form>

      <div className="flex w-full flex-col items-center pt-5">
        <p className="text-[13px] text-[#6a7282]">
          이미 계정이 있으신가요?{' '}
          <Link to="/login" className="font-bold text-brand">
            로그인
          </Link>
        </p>
      </div>
    </AuthLayout>
  );
}
