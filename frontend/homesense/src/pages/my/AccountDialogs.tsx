import { useId, useRef, useState, type FormEvent } from 'react';
import { Checkbox } from '../../components/ui/Checkbox';
import { FieldHint } from '../../components/ui/FieldHint';
import {
  CONFIRM_SECONDARY_BUTTON_CLASS,
  MODAL_DANGER_BUTTON_CLASS,
  MODAL_FOOTER_ROW_CLASS,
  MODAL_SECONDARY_BUTTON_CLASS,
  Modal,
} from '../../components/ui/Modal';
import { TextField } from '../../components/ui/TextField';
import { useToast } from '../../components/ui/useToast';
import { useAuth } from '../../features/auth/useAuth';
import { useLogoutAction } from '../../features/auth/useLogoutAction';
import { withdraw } from '../../features/user/api';
import { WITHDRAWAL_GRACE_DAYS } from '../../features/user/withdrawalPolicy';
import { getErrorMessage } from '../../lib/apiError';

export type AccountDialog = 'choice' | 'withdraw' | null;

// 선택 다이얼로그는 확인형(제목·짧은 본문·버튼) — 버튼 높이 경계를 확인형과 같이 1280px로 둔다(Modal CONFIRM_*).
const PRIMARY_BUTTON_CLASS =
  'flex h-[43px] w-full items-center justify-center rounded-[14px] bg-brand text-[14px] font-semibold text-white hover:bg-[#0c4a44] disabled:cursor-not-allowed disabled:opacity-50 xl:h-[47px]';
const DANGER_OUTLINE_BUTTON_CLASS =
  'flex h-[43px] w-full items-center justify-center rounded-[14px] border border-[#fecaca] bg-white text-[14px] font-semibold text-[#c10007] hover:bg-[#fef2f2] disabled:cursor-not-allowed disabled:opacity-50 xl:h-[47px]';

interface AccountDialogsProps {
  dialog: AccountDialog;
  onChange: (dialog: AccountDialog) => void;
}

/**
 * MY-01 "로그아웃·회원탈퇴" 타일이 여는 다이얼로그 두 개.
 * - 선택 다이얼로그: 로그아웃 / 회원탈퇴 / 취소. 로그아웃은 확인 없이 바로 실행하고 헤더 계정 메뉴와 같은 경로
 *   (useLogoutAction)를 쓴다. 화면 이동은 보호 라우트 가드가 한다 — 사용자가 직접 로그아웃했으니 HOME-01로
 *   replace(`signedOutByUser`, RequireAuth).
 * - 탈퇴 확인 다이얼로그(alertdialog): 처리 안내 → "안내 사항을 확인했습니다" 체크 → 비밀번호 재확인 → 탈퇴하기.
 *
 * 두 다이얼로그는 따로 마운트된다. 선택 다이얼로그가 닫히며 포커스를 타일로 돌려준 직후 탈퇴 다이얼로그가 열려
 * 그 타일을 트리거로 기억하므로, 탈퇴를 취소해도 포커스는 타일로 돌아간다(useDialogBehavior).
 */
export function AccountDialogs({ dialog, onChange }: AccountDialogsProps) {
  return (
    <>
      <ChoiceDialog open={dialog === 'choice'} onClose={() => onChange(null)} onWithdraw={() => onChange('withdraw')} />
      {/* 열 때마다 새로 마운트해 체크·비밀번호·오류가 이전 시도에서 남지 않게 한다. */}
      {dialog === 'withdraw' && <WithdrawDialog onClose={() => onChange(null)} />}
    </>
  );
}

function ChoiceDialog({ open, onClose, onWithdraw }: { open: boolean; onClose: () => void; onWithdraw: () => void }) {
  const { logoutWithNotice, loggingOut } = useLogoutAction();
  const descriptionId = useId();

  return (
    <Modal
      open={open}
      onClose={loggingOut ? () => {} : onClose}
      title="로그아웃·회원탈퇴"
      describedBy={descriptionId}
      variant="confirm"
      footer={
        <div className="flex flex-col gap-2">
          <button type="button" onClick={() => void logoutWithNotice()} disabled={loggingOut} className={PRIMARY_BUTTON_CLASS}>
            {loggingOut ? '로그아웃 중…' : '로그아웃'}
          </button>
          <button type="button" onClick={onWithdraw} disabled={loggingOut} className={DANGER_OUTLINE_BUTTON_CLASS}>
            회원탈퇴
          </button>
          <button type="button" onClick={onClose} disabled={loggingOut} className={CONFIRM_SECONDARY_BUTTON_CLASS}>
            취소
          </button>
        </div>
      }
    >
      <p id={descriptionId} className="text-[14px] leading-[22px] text-[#4a5565]">
        원하시는 작업을 선택해 주세요.
      </p>
    </Modal>
  );
}

/**
 * 안내 문구는 개인정보처리방침 2항(privacyPolicySections.tsx)과 코드(UserService.withdraw, BAT-USR-01)에 있는 사실만
 * 쓴다. 탈퇴 철회는 안내하지 않는다 — 철회 API는 있지만 호출하는 화면이 없어 방침도 철회를 안내하지 않는다.
 * 탈퇴 사유는 받지 않는다 — 서버가 받아도 저장하지 않는다(WithdrawRequest.reason, user 테이블에 컬럼 없음).
 */
function WithdrawDialog({ onClose }: { onClose: () => void }) {
  const { endSession } = useAuth();
  const { showToast } = useToast();
  const [confirmed, setConfirmed] = useState(false);
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const noticeId = useId();
  const checkboxId = useId();
  const passwordId = useId();
  const errorId = useId();
  const formId = useId();
  const passwordRef = useRef<HTMLInputElement>(null);

  const canSubmit = confirmed && password.length > 0 && !submitting;

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await withdraw(password);
    } catch (caught) {
      // 서버 error.message 그대로(비밀번호 불일치 401 INVALID_CREDENTIALS 포함 — 비즈니스 401이라 재발급하지 않는다).
      setError(getErrorMessage(caught));
      setSubmitting(false);
      // 누른 버튼이 처리 중 비활성화되며 포커스를 잃으므로, 고쳐 입력할 비밀번호 칸으로 돌려준다.
      passwordRef.current?.focus();
      return;
    }
    // 서버가 이미 Refresh Token을 모두 폐기했으므로 /logout은 부르지 않고 이 브라우저의 세션만 정리한다.
    // 화면 이동은 보호 라우트 가드가 HOME-01로 replace한다(signedOutByUser).
    showToast('회원 탈퇴가 완료되었습니다');
    endSession();
  };

  return (
    // 내용형(기본) — 안내 목록·체크박스·비밀번호 입력을 담아 768px 이상에서 폭 400을 쓴다.
    <Modal
      open
      role="alertdialog"
      onClose={submitting ? () => {} : onClose}
      title="회원탈퇴"
      describedBy={noticeId}
      footer={
        <div className={MODAL_FOOTER_ROW_CLASS}>
          <button type="submit" form={formId} disabled={!canSubmit} className={MODAL_DANGER_BUTTON_CLASS}>
            {submitting ? '탈퇴 처리 중…' : '탈퇴하기'}
          </button>
          <button type="button" onClick={onClose} disabled={submitting} className={MODAL_SECONDARY_BUTTON_CLASS}>
            취소
          </button>
        </div>
      }
    >
      <form id={formId} onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        <div id={noticeId} className="rounded-[12px] bg-[#f7f8fa] px-4 py-3 text-[13px] leading-[20px] text-[#364153]">
          <p className="mb-1.5 font-semibold text-[#101828]">탈퇴하면 다음과 같이 처리됩니다.</p>
          <ul className="list-disc space-y-1 pl-4">
            <li>계정이 즉시 탈퇴 상태로 바뀌어 더 이상 로그인할 수 없고, 로그인에 쓰이던 인증 토큰은 즉시 폐기됩니다.</li>
            <li>
              탈퇴 후 {WITHDRAWAL_GRACE_DAYS}일 동안은 로그인할 수 없는 상태로 보관되고, 그 뒤 매일 새벽 정기 처리에서
              자동으로 파기됩니다.
            </li>
            <li>파기 대상: 계정정보, 관심 매물·관심 지역, 알림 설정·이력, 최근 조회 이력, 인증 토큰</li>
            <li>재로그인으로 복구되지 않으며, 같은 이메일로 다시 가입하려면 계정이 파기된 뒤에 가능합니다.</li>
          </ul>
        </div>
        <Checkbox id={checkboxId} checked={confirmed} onChange={setConfirmed} label="안내 사항을 확인했습니다" />
        <div>
          <TextField
            ref={passwordRef}
            id={passwordId}
            label="비밀번호 확인"
            type="password"
            autoComplete="current-password"
            placeholder="현재 비밀번호를 입력하세요"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            status={error ? 'error' : 'default'}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            readOnly={submitting}
          />
          {error && <FieldHint id={errorId} status="error" message={error} />}
        </div>
      </form>
    </Modal>
  );
}
