import { CheckIcon } from '../icons/CheckIcon';
import { evaluatePassword } from '../../features/auth/validation';

const RULES = [
  { key: 'minLength', label: '8자 이상' },
  { key: 'hasLetter', label: '영문 포함' },
  { key: 'hasDigit', label: '숫자 포함' },
  { key: 'hasSpecial', label: '특수문자 포함' },
] as const;

/** 비밀번호 정책 2x2 체크리스트 — 키 입력마다 4개 항목을 개별 평가해 실시간으로 반영한다. */
export function PasswordChecklist({ password }: { password: string }) {
  const policy = evaluatePassword(password);

  return (
    <div className="mt-2 grid w-full grid-cols-2 gap-x-4 gap-y-1">
      {RULES.map((rule) => {
        const met = policy[rule.key];
        return (
          <div key={rule.key} className="flex items-center gap-1.5">
            {met ? (
              <CheckIcon strokeWidth={1} className="size-3 shrink-0 text-[#00c950]" />
            ) : (
              <span className="size-3 shrink-0 rounded-full border border-[#d1d5dc]" />
            )}
            <span className={`text-[11.5px] ${met ? 'text-[#00a63e]' : 'text-[#99a1af]'}`}>{rule.label}</span>
          </div>
        );
      })}
    </div>
  );
}
