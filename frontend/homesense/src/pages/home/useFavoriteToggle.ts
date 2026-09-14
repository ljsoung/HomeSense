import axios from 'axios';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useToast } from '../../components/ui/useToast';
import { addFavoriteProperty } from '../../features/favorite/api';
import { useAuth } from '../../features/auth/useAuth';
import type { ApiErrorResponse } from '../../types/api';

const PENDING_FAVORITE_KEY = 'homesense.pendingFavoriteComplexId';
const GENERIC_ERROR_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';

/**
 * 하트 클릭 공용 로직 — 완료 조건: 로그인 시 POST /api/favorites/properties + 성공 토스트,
 * 비로그인 시 AUTH-01로 리다이렉트 후 로그인 성공 시 원래 하트 클릭을 자동 재생.
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
  const [favoritedIds, setFavoritedIds] = useState<Set<number>>(new Set());
  const replayedRef = useRef(false);

  const favoriteComplex = useCallback(
    async (complexId: number) => {
      try {
        await addFavoriteProperty(complexId);
        setFavoritedIds((prev) => new Set(prev).add(complexId));
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

  // 로그인 성공 후 이 페이지로 돌아왔을 때 대기 중인 하트 클릭을 정확히 한 번만 재생한다.
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
      await favoriteComplex(pendingComplexId);
    };
    void replay();
  }, [isAuthenticated, favoriteComplex]);

  const toggleFavorite = useCallback(
    (complexId: number) => {
      if (!isAuthenticated) {
        sessionStorage.setItem(PENDING_FAVORITE_KEY, String(complexId));
        navigate('/login', { state: { from: location } });
        return;
      }
      void favoriteComplex(complexId);
    },
    [isAuthenticated, navigate, location, favoriteComplex],
  );

  return { favoritedIds, toggleFavorite };
}
