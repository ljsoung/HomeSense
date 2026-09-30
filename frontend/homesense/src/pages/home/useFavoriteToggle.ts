import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useToast } from '../../components/ui/useToast';
import { addFavoriteProperty, getFavoriteProperties, removeFavoriteProperty } from '../../features/favorite/api';
import type { AuthStatus } from '../../features/auth/authContext';
import { useAuth } from '../../features/auth/useAuth';
import { assertNever } from '../../lib/assertNever';
import { getErrorMessage } from '../../lib/apiError';

const PENDING_FAVORITE_KEY = 'homesense.pendingFavoriteComplexId';

/** 확인 중에 미룬 하트 클릭 — 클릭 순간 보이던 하트 상태로 정한 의도(빈 하트 → add, 채워진 하트 → remove). */
interface DeferredClick {
  complexId: number;
  intent: 'add' | 'remove';
}

/**
 * 하트 클릭 공용 로직 — 완료 조건: 로그인 시 POST/DELETE /api/favorites/properties로 토글 +
 * 성공 토스트, 비로그인 시 AUTH-01로 리다이렉트 후 로그인 성공 시 원래 하트 클릭을 자동 재생.
 *
 * 판단 기록(P2 코드리뷰 대응) — 처음엔 favoritedIds를 항상 빈 Set에서 시작해 매 클릭을 POST로만
 * 처리했다: 이미 관심 매물로 등록된 단지도 새로고침 후엔 하트가 빈 채로 보이고, 클릭하면
 * DuplicateFavoriteException(409)만 받을 뿐 실제로 해제(DELETE)할 방법이 없었다 — UI는 토글처럼
 * 보이는데 백엔드 GET/DELETE 엔드포인트를 전혀 쓰지 않는 반쪽짜리 구현이었다. 로그인 시 마운트
 * 시점에 GET /api/favorites/properties로 실제 등록 목록을 하이드레이트하고, DELETE
 * /api/favorites/properties/{id}의 {id}가 complexId가 아니라 favoritePropertyId라(FavoriteController
 * 확인) complexId→favoritePropertyId 매핑까지 함께 들고 있어야 해제가 가능해 Map으로 상태를 바꿨다.
 *
 * AUTH-01의 location.state.from 패턴(getRedirectPath)은 "돌아갈 경로"만 기억하므로, "클릭했던
 * complexId"는 별도로 sessionStorage에 남겨 로그인 후 이 훅이 다시 마운트됐을 때 자동 재생한다 —
 * redirect.ts 자체를 이 기능 전용 필드로 확장하지 않았다(다른 보호된 라우트도 같은 유틸을 공유하는
 * 범용 계약이라, 즐겨찾기 전용 필드를 얹으면 그 계약이 HomeSense의 한 기능에 결합된다).
 */
export function useFavoriteToggle() {
  const { status } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  // complexId -> favoritePropertyId. DELETE가 favoritePropertyId를 요구해 Set<complexId>만으론 부족하다.
  const [favorites, setFavorites] = useState<Map<number, number>>(new Map());
  const replayedRef = useRef(false);
  // 하트 상태(favorites)가 어떤 인증 상태 기준으로 채워졌는지 — 로그인 판정 직후 아직 목록을 불러오기
  // 전에 클릭을 처리하면, 이미 찜한 단지도 해제가 아니라 등록으로 시도해 409를 받는다.
  const [hydratedFor, setHydratedFor] = useState<Exclude<AuthStatus, 'checking'> | null>(null);
  // 세션 확인(checking) 중에 누른 하트. 이때 곧바로 /login으로 보내면 로그인된 사용자가 로그인 화면에
  // 남는다(Codex P2). 클릭을 미뤘다가 판정과
  // 하트 상태 채우기가 끝나면 처리한다. 전역으로 마지막 클릭 하나만 기억한다 — 다른 카드를 누르면 앞 클릭은
  // 버려지고 그 하트의 대기 표시도 풀린다(판단 근거: CLAUDE.md 세션 절 "확인 중 하트 클릭" 행).
  // 클릭은 "토글"이 아니라 클릭 순간 화면에 보이던 하트 기준의 의도로 기억한다 — 빈 하트면 등록, 채워진
  // 하트면 해제. 목록이 도착한 뒤 이미 그 상태면 요청을 보내지 않는다(빈 하트를 눌렀는데 이미 등록된 단지를
  // 해제하지 않는다).
  const deferredClickRef = useRef<DeferredClick | null>(null);
  // 대기 중인 하트를 화면에 알리기 위한 상태(카드가 aria-busy와 시각적 표시를 단다).
  const [pendingFavoriteId, setPendingFavoriteId] = useState<number | null>(null);

  // 로그인 상태에서 마운트되거나(또는 로그인 직후 status가 authenticated로 바뀌면) 실제 등록된
  // 관심 매물 목록으로 하트 상태를 하이드레이트한다 — 이게 없으면 이미 등록된 단지도 새로고침 후
  // 빈 하트로 보이고 클릭 시 add만 시도해 중복 등록 에러를 받는다.
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      switch (status) {
        case 'checking':
          // 판정이 나기 전에는 채우지 않고, 이전 판정 기준의 하트 상태도 버린다. 다른 탭의 계정 변경으로
          // authenticated(A) → checking → authenticated(B)가 되면 hydratedFor가 'authenticated' 그대로라,
          // 확인 중에 미룬 클릭이 B의 목록을 받기 전에 A의 하트 상태로 등록/해제를 B 토큰으로 보냈다.
          setFavorites(new Map());
          setHydratedFor(null);
          return;
        case 'anonymous':
          setFavorites(new Map());
          setHydratedFor('anonymous');
          return;
        case 'authenticated':
          break;
        default:
          assertNever(status);
      }
      try {
        const result = await getFavoriteProperties();
        if (!cancelled) {
          setFavorites(new Map(result.map((item) => [item.complexId, item.favoritePropertyId])));
        }
      } catch {
        // 하이드레이션 실패는 "아직 아무것도 안 찜한 것"과 구분 없이 조용히 빈 상태로 둔다 —
        // 이후 클릭이 add를 시도하고, 이미 등록된 상태였다면 서버가 409로 알려준다(fail-safe).
      } finally {
        if (!cancelled) setHydratedFor('authenticated');
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [status]);

  const addFavorite = useCallback(
    async (complexId: number) => {
      try {
        const result = await addFavoriteProperty(complexId);
        setFavorites((prev) => new Map(prev).set(complexId, result.favoritePropertyId));
        showToast('관심 매물로 등록되었습니다.', 'success');
      } catch (error) {
        // 409(DuplicateFavoriteException) 등 서버 메시지를 그대로 노출한다(AUTH-01 확립 관례). 다른 탭의 계정
        // 변경으로 요청을 보내지 않은 경우도 그 사실을 알린다(getErrorMessage).
        showToast(getErrorMessage(error), 'error');
      }
    },
    [showToast],
  );

  const removeFavorite = useCallback(
    async (complexId: number, favoritePropertyId: number) => {
      try {
        await removeFavoriteProperty(favoritePropertyId);
        setFavorites((prev) => {
          const next = new Map(prev);
          next.delete(complexId);
          return next;
        });
        showToast('관심 매물에서 해제되었습니다.', 'success');
      } catch (error) {
        showToast(getErrorMessage(error), 'error');
      }
    },
    [showToast],
  );

  // 로그인 성공 후 이 페이지로 돌아왔을 때 대기 중인 하트 클릭을 정확히 한 번만 재생한다.
  // 재생 시점엔 항상 add다 — 비로그인 상태에서 클릭할 수 있었던 하트는 애초에 등록 전(빈 하트)
  // 상태였을 때만 sessionStorage에 pending으로 남으므로(toggleFavorite가 비로그인 분기에서
  // favorites.has() 여부와 무관하게 항상 이 경로를 타지만, 비로그인 사용자는애초에 favorites
  // 맵을 가질 수 없어 이 경로에 들어오는 complexId는 항상 "등록 시도"다).
  useEffect(() => {
    if (status !== 'authenticated' || replayedRef.current) {
      return;
    }
    const pending = sessionStorage.getItem(PENDING_FAVORITE_KEY);
    if (!pending) {
      return;
    }
    replayedRef.current = true;
    sessionStorage.removeItem(PENDING_FAVORITE_KEY);
    const pendingComplexId = Number(pending);
    const replay = async () => {
      await addFavorite(pendingComplexId);
    };
    void replay();
  }, [status, addFavorite]);

  const toggleFavorite = useCallback(
    (complexId: number) => {
      switch (status) {
        case 'checking':
          deferredClickRef.current = { complexId, intent: favorites.has(complexId) ? 'remove' : 'add' };
          setPendingFavoriteId(complexId);
          return;
        case 'anonymous':
          sessionStorage.setItem(PENDING_FAVORITE_KEY, String(complexId));
          navigate('/login', { state: { from: location } });
          return;
        case 'authenticated': {
          const favoritePropertyId = favorites.get(complexId);
          if (favoritePropertyId !== undefined) {
            void removeFavorite(complexId, favoritePropertyId);
          } else {
            void addFavorite(complexId);
          }
          return;
        }
        default:
          assertNever(status);
      }
    },
    [status, navigate, location, favorites, addFavorite, removeFavorite],
  );

  // 미룬 클릭의 의도를 판정 결과에 맞춰 처리한다. 로그인이면 채워진 하트 상태와 의도가 다를 때만 등록/해제
  // 요청을 보낸다. 비로그인이면 등록 의도만 로그인 화면으로 넘겨 로그인 후 재생한다(비로그인에게 해제할 관심
  // 매물은 없다).
  const resolveDeferredClick = useCallback(
    ({ complexId, intent }: DeferredClick) => {
      switch (status) {
        case 'checking':
          return; // 호출부가 판정 뒤에만 부른다.
        case 'anonymous':
          if (intent === 'add') {
            sessionStorage.setItem(PENDING_FAVORITE_KEY, String(complexId));
            navigate('/login', { state: { from: location } });
          }
          return;
        case 'authenticated': {
          const favoritePropertyId = favorites.get(complexId);
          if (intent === 'add' && favoritePropertyId === undefined) {
            void addFavorite(complexId);
          } else if (intent === 'remove' && favoritePropertyId !== undefined) {
            void removeFavorite(complexId, favoritePropertyId);
          }
          return; // 이미 의도한 상태면 요청하지 않는다.
        }
        default:
          assertNever(status);
      }
    },
    [status, navigate, location, favorites, addFavorite, removeFavorite],
  );

  // 미룬 클릭은 판정이 끝나고, 그 판정 기준으로 하트 상태까지 채워진 뒤에 처리한다.
  useEffect(() => {
    const deferred = deferredClickRef.current;
    if (deferred === null || status === 'checking' || hydratedFor !== status) return;
    deferredClickRef.current = null;
    // 대기 표시를 풀고 처리한다(등록/해제 요청을 보내거나 로그인 화면으로 이동). effect 본문에서 동기로
    // setState하지 않도록 마이크로태스크로 넘긴다(react-hooks/set-state-in-effect).
    queueMicrotask(() => {
      setPendingFavoriteId(null);
      resolveDeferredClick(deferred);
    });
  }, [status, hydratedFor, resolveDeferredClick]);

  const favoritedIds = useMemo(() => new Set(favorites.keys()), [favorites]);

  return { favoritedIds, toggleFavorite, pendingFavoriteId };
}
