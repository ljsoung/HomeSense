import { AlertCircleIcon } from '../icons/AlertCircleIcon';
import { CheckIcon } from '../icons/CheckIcon';

interface FieldHintProps {
  status: 'success' | 'error';
  message: string;
  id?: string;
}

/** 입력 필드 바로 아래에 붙는 성공/실패 안내 — AUTH-02 전 필드(이메일/비밀번호확인/닉네임)와 폼 전체 배너가 공유한다. */
export function FieldHint({ status, message, id }: FieldHintProps) {
  const isError = status === 'error';
  return (
    <div
      id={id}
      role={isError ? 'alert' : 'status'}
      className={`mt-1 flex items-center gap-1.5 text-[12px] ${isError ? 'text-[#fb2c36]' : 'text-[#00a63e]'}`}
    >
      {isError ? (
        <AlertCircleIcon className="size-3.5 shrink-0" />
      ) : (
        <CheckIcon strokeWidth={1.16667} className="size-3.5 shrink-0" />
      )}
      <span>{message}</span>
    </div>
  );
}
