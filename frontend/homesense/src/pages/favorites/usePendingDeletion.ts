import axios from 'axios';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '../../components/ui/useToast';
import { getErrorMessage } from '../../lib/apiError';

/** 실행취소를 받을 수 있는 시간. 토스트에 마우스를 올리거나 키보드 포커스가 있으면 멈춘다. */
export const UNDO_WINDOW_MS = 5000;

export interface PendingDeletion {
  /** 목록에서 숨길 항목의 키(예: "property:12", "region:3"). */
  key: string;
  /** 서버에 삭제를 보낸다. */
  commit: () => Promise<void>;
  /** 서버 삭제가 끝났을 때(이미 지워진 404 포함) — 데이터 목록에서 항목을 뺀다. */
  onCommitted: () => void;
  /** 실행취소로 되살아났을 때(포커스 이동 등). */
  onRestored?: () => void;
}

interface ActiveEntry extends PendingDeletion {
  toastId: number;
}

function isAlreadyDeleted(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.data?.error?.code === 'FAVORITE_NOT_FOUND';
}

/**
 * MY-02 지연 삭제(결정 D6). 프로그램설계서 3.7절: 백엔드는 즉시 삭제 API 하나만 주고, 실행취소는 프론트가 DELETE를
 * 5초 늦춰 구현한다.
 * 1. `schedule` → 항목을 곧바로 숨기고(`hiddenKeys`) "삭제했어요 · 실행취소" 토스트를 5초 띄운다.
 * 2. 실행취소 → 숨김만 푼다. 서버에는 아무것도 보내지 않는다. 항목은 현재 정렬대로 원래 자리에 돌아온다.
 * 3. 토스트 시간이 다 지나면 DELETE. 이미 지워진 경우(FAVORITE_NOT_FOUND)는 성공으로 본다. 그 밖의 실패는 항목을
 *    되살리고 서버 문구로 오류 토스트.
 * 4. 대기 중에 다른 항목을 지우면 앞 건은 그 자리에서 DELETE로 확정하고 새 건의 5초를 시작한다(토스트는 늘 마지막 하나).
 * 5. 화면을 떠나면(언마운트) 대기 건을 즉시 DELETE한다. 결과는 기다리지 않고 실패는 콘솔에만 남긴다.
 * 6. 탭을 닫거나 새로고침하면 대기 건은 삭제되지 않는다 — 인증 헤더가 필요한 DELETE를 pagehide에서 확실히 보낼 방법이
 *    없고(keepalive fetch는 axios 인터셉터·재발급을 거치지 않는다), 남는 쪽이 데이터를 잃는 쪽보다 안전하다.
 *
 * 다른 탭의 계정 변경으로 화면이 다시 확인 중이 되면 이 화면이 언마운트되며 5번을 거치는데, 그때는 탭이 계정을
 * 확정하지 않은 상태라 요청 인터셉터가 DELETE를 보내지 않고 거절한다(CLAUDE.md "탭 계정 동기화") — 다른 계정의
 * 항목을 지우지 않는다.
 */
export function usePendingDeletion() {
  const { showToast, dismissToast } = useToast();
  const [hiddenKeys, setHiddenKeys] = useState<ReadonlySet<string>>(new Set());
  const activeRef = useRef<ActiveEntry | null>(null);
  const mountedRef = useRef(true);

  const unhide = useCallback((key: string) => {
    setHiddenKeys((prev) => {
      if (!prev.has(key)) return prev;
      const next = new Set(prev);
      next.delete(key);
      return next;
    });
  }, []);

  const commit = useCallback(
    async (entry: PendingDeletion) => {
      try {
        await entry.commit();
      } catch (error) {
        if (!isAlreadyDeleted(error)) {
          if (!mountedRef.current) {
            console.warn('관심 항목 삭제를 확정하지 못했습니다', error);
            return;
          }
          unhide(entry.key);
          showToast(getErrorMessage(error), 'error');
          return;
        }
      }
      if (!mountedRef.current) return;
      entry.onCommitted();
      unhide(entry.key);
    },
    [showToast, unhide],
  );

  /** 대기 중인 건을 지금 확정한다(토스트는 닫는다). 서버 응답까지 기다리려면 반환된 Promise를 기다린다. */
  const flush = useCallback((): Promise<void> => {
    const active = activeRef.current;
    if (!active) return Promise.resolve();
    activeRef.current = null;
    dismissToast(active.toastId);
    return commit(active);
  }, [commit, dismissToast]);

  const schedule = useCallback(
    (entry: PendingDeletion) => {
      void flush();
      setHiddenKeys((prev) => new Set(prev).add(entry.key));
      const toastId = showToast('삭제했어요', 'success', {
        durationMs: UNDO_WINDOW_MS,
        action: {
          label: '실행취소',
          onClick: () => {
            if (activeRef.current?.key !== entry.key) return;
            activeRef.current = null;
            unhide(entry.key);
            entry.onRestored?.();
          },
        },
        onExpire: () => {
          if (activeRef.current?.key !== entry.key) return;
          activeRef.current = null;
          void commit(entry);
        },
      });
      activeRef.current = { ...entry, toastId };
    },
    [commit, flush, showToast, unhide],
  );

  // 언마운트 시 대기 건을 확정한다. 의존성 없이 최신 flush를 ref로 불러, 콜백이 바뀌어도 화면에 있는 동안에는
  // 확정되지 않게 한다. mountedRef를 마운트 때 다시 켜는 것은 개발 모드 StrictMode의 가짜 언마운트 뒤에도 상태
  // 갱신이 막히지 않게 하려는 것이다(그 시점엔 대기 건이 없다).
  const flushRef = useRef(flush);
  useEffect(() => {
    flushRef.current = flush;
  });
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      void flushRef.current();
    };
  }, []);

  return { hiddenKeys, schedule, flush };
}
