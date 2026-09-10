import { zodResolver } from '@hookform/resolvers/zod';
import axios from 'axios';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import loginBackground from '../../assets/auth/login-background.jpg';
import { AlertCircleIcon } from '../../components/icons/AlertCircleIcon';
import { EyeIcon } from '../../components/icons/EyeIcon';
import { EyeOffIcon } from '../../components/icons/EyeOffIcon';
import { GoogleIcon } from '../../components/icons/GoogleIcon';
import { HomeIcon } from '../../components/icons/HomeIcon';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { getRedirectPath } from '../../features/auth/redirect';
import { useAuth } from '../../features/auth/useAuth';
import { loginSchema, type LoginFormValues } from '../../features/auth/loginSchema';
import type { ApiErrorResponse } from '../../types/api';

const GENERIC_ERROR_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
const INLINE_ERROR_STATUS_CODES = new Set([401, 403, 429]);

export function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { isValid, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    mode: 'onChange',
    defaultValues: { email: '', password: '' },
  });

  const clearServerError = () => setServerError(null);

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      await auth.login(values);
      navigate(getRedirectPath(location), { replace: true });
    } catch (error) {
      if (axios.isAxiosError<ApiErrorResponse>(error) && error.response && INLINE_ERROR_STATUS_CODES.has(error.response.status)) {
        setServerError(error.response.data?.error?.message ?? GENERIC_ERROR_MESSAGE);
      } else {
        setServerError(GENERIC_ERROR_MESSAGE);
      }
    }
  });

  const hasServerError = serverError !== null;

  return (
    <div className="relative flex min-h-screen w-full items-center justify-center overflow-hidden px-4 py-10">
      <img
        src={loginBackground}
        alt=""
        className="animate-aerial-pan absolute inset-0 h-full w-full object-cover will-change-transform"
      />
      <div className="absolute inset-0 bg-gradient-to-b from-black/55 via-black/38 to-black/60" />

      <div className="relative z-10 flex w-full max-w-[440px] flex-col items-start rounded-[24px] bg-white p-6 shadow-[0_24px_64px_rgba(0,0,0,0.22)] sm:p-10">
        <div className="flex w-full flex-col items-center">
          <div className="flex h-[60px] items-center gap-2 pb-5">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-[16px] bg-brand">
              <HomeIcon className="size-5" />
            </div>
            <p className="text-[20px] font-extrabold tracking-[-0.5px] text-brand">HomeSense</p>
          </div>
          <p className="text-[26px] font-extrabold tracking-[-0.5px] text-[#101828]">로그인</p>
        </div>

        <form noValidate onSubmit={onSubmit} className="flex w-full flex-col gap-4 pt-8">
          <TextField
            id="email"
            label="이메일"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            error={hasServerError}
            {...register('email', { onChange: clearServerError })}
          />

          <div className="flex w-full flex-col gap-1.5">
            <TextField
              id="password"
              label="비밀번호"
              labelAction={
                <Link to="/forgot-password" className="text-[12px] text-[#99a1af] hover:text-[#6a7282]">
                  비밀번호 찾기
                </Link>
              }
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="비밀번호 입력"
              error={hasServerError}
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
              {...register('password', { onChange: clearServerError })}
            />
            {hasServerError && (
              <div className="flex items-center gap-1.5 text-[12px] text-[#fb2c36]">
                <AlertCircleIcon className="size-3.5 shrink-0" />
                <span>{serverError}</span>
              </div>
            )}
          </div>

          <div className="w-full pt-1">
            <Button type="submit" disabled={!isValid || isSubmitting}>
              로그인
            </Button>
          </div>
        </form>

        <div className="flex h-10 w-full flex-col items-center pt-5">
          <p className="text-[13px] text-[#6a7282]">
            계정이 없으신가요?{' '}
            <Link to="/signup" className="font-bold text-brand">
              회원가입
            </Link>
          </p>
        </div>

        <div className="flex w-full items-center gap-3 pt-6">
          <div className="h-px flex-1 bg-[#e5e7eb]" />
          <p className="text-[12px] text-[#99a1af]">또는</p>
          <div className="h-px flex-1 bg-[#e5e7eb]" />
        </div>

        <div className="flex w-full flex-col pt-6">
          <div className="flex items-center justify-center gap-2">
            <p className="text-[11.5px] text-[#99a1af]">소셜 로그인</p>
            <span className="rounded-full border border-[#fee685] bg-[#fffbeb] px-2 py-0.5 text-[10px] font-semibold text-[#e17100]">
              2차 확장 예정
            </span>
          </div>
          <div className="flex gap-3 pt-3">
            <button
              type="button"
              disabled
              title="2차 확장 예정"
              aria-disabled="true"
              className="flex h-11 flex-1 cursor-not-allowed items-center justify-center gap-2 rounded-[14px] bg-[#fee500] opacity-45"
            >
              <span className="text-[18px] font-black text-[#3a1d1d]">K</span>
              <span className="text-[13px] font-bold text-[#3a1d1d]">카카오</span>
            </button>
            <button
              type="button"
              disabled
              title="2차 확장 예정"
              aria-disabled="true"
              className="flex h-11 flex-1 cursor-not-allowed items-center justify-center gap-2 rounded-[14px] border border-[#e5e7eb] bg-white opacity-45"
            >
              <GoogleIcon className="size-4" />
              <span className="text-[13px] font-bold text-[#364153]">Google</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
