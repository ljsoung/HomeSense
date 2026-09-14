import axios from 'axios';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useToast } from '../../components/ui/useToast';
import { addFavoriteProperty, getFavoriteProperties, removeFavoriteProperty } from '../../features/favorite/api';
import { useAuth } from '../../features/auth/useAuth';
import type { ApiErrorResponse } from '../../types/api';

const PENDING_FAVORITE_KEY = 'homesense.pendingFavoriteComplexId';
const GENERIC_ERROR_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';

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
  const { isAuthenticated } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  // complexId -> favoritePropertyId. DELETE가 favoritePropertyId를 요구해 Set<complexId>만으론 부족하다.
  const [favorites, setFavorites] = useState<Map<number, number>>(new Map());
  const replayedRef = useRef(false);

  // 로그인 상태에서 마운트되거나(또는 로그인 직후 isAuthenticated가 true로 바뀌면) 실제 등록된
  // 관심 매물 목록으로 하트 상태를 하이드레이트한다 — 이게 없으면 이미 등록된 단지도 새로고침 후
  // 빈 하트로 보이고 클릭 시 add만 시도해 중복 등록 에러를 받는다.
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      if (!isAuthenticated) {
        setFavorites(new Map());
        return;
      }
      try {
        const result = await getFavoriteProperties();
        if (!cancelled) {
          setFavorites(new Map(result.map((item) => [item.complexId, item.favoritePropertyId])));
        }
      } catch {
        // 하이드레이션 실패는 "아직 아무것도 안 찜한 것"과 구분 없이 조용히 빈 상태로 둔다 —
        // 이후 클릭이 add를 시도하고, 이미 등록된 상태였다면 서버가 409로 알려준다(fail-safe).
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  const addFavorite = useCallback(
    async (complexId: number) => {
      try {
        const result = await addFavoriteProperty(complexId);
        setFavorites((prev) => new Map(prev).set(complexId, result.favoritePropertyId));
        showToast('관심 매물로 등록되었습니다.', 'success');
      } catch (error) {
        if (axios.isAxiosError<ApiErrorResponse>(error) && error.response?.data?.error?.message) {
          // 409(DuplicateFavoriteException) 등 서버 메시지를 그대로 노출한다(AUTH-01 확립 관례).
          showToast(error.response.data.error.message, 'error');
        } else {
          showToast(GENERIC_ERROR_MESSAGE, 'error');
        }
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
        if (axios.isAxiosError<ApiErrorResponse>(error) && error.response?.data?.error?.message) {
          showToast(error.response.data.error.message, 'error');
        } else {
          showToast(GENERIC_ERROR_MESSAGE, 'error');
        }
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
    if (!isAuthenticated || replayedRef.current) {
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
  }, [isAuthenticated, addFavorite]);

  const toggleFavorite = useCallback(
    (complexId: number) => {
      if (!isAuthenticated) {
        sessionStorage.setItem(PENDING_FAVORITE_KEY, String(complexId));
        navigate('/login', { state: { from: location } });
        return;
      }
      const favoritePropertyId = favorites.get(complexId);
      if (favoritePropertyId !== undefined) {
        void removeFavorite(complexId, favoritePropertyId);
      } else {
        void addFavorite(complexId);
      }
    },
    [isAuthenticated, navigate, location, favorites, addFavorite, removeFavorite],
  );

  const favoritedIds = useMemo(() => new Set(favorites.keys()), [favorites]);

  return { favoritedIds, toggleFavorite };
}
