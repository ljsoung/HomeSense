import { useEffect, useState } from 'react';
import { ClockIcon } from '../../components/icons/ClockIcon';
import { HeartIcon } from '../../components/icons/HeartIcon';
import { HomeIcon } from '../../components/icons/HomeIcon';
import { EmptyState } from '../../components/ui/EmptyState';
import { Spinner } from '../../components/ui/Spinner';
import { getRecentViews } from '../../features/recentview/api';
import type { RecentViewResponse } from '../../features/recentview/types';
import { useAuth } from '../../features/auth/useAuth';

const HOUSING_TYPE_LABEL: Record<string, string> = { APT: '아파트', VILLA: '연립다세대' };

interface RecentViewsProps {
  favoritedIds: Set<number>;
  onToggleFavorite: (complexId: number) => void;
}

/**
 * HOME-01 구성요소 4 뒷부분 — 판단 기록: 비로그인 사용자를 위해 항상 로그인 유도 문구만 보여줄지
 * (Figma 정적 목업 그대로), 세션 기반 실제 조회 이력을 보여줄지(SVC-RCV-01은 X-Session-Id로
 * 비로그인 조회도 이미 지원) 사이에서 후자를 택했다 — RCV 도메인이 애초에 이 용도로 설계됐고,
 * 세션에 기록이 전혀 없는 신규 방문자는 결과적으로 Figma 목업과 동일한 화면(빈 상태 → 유도
 * 문구)을 보게 되어 두 선택지가 사실상 상위/하위 호환 관계다(CLAUDE.md SCR-HOME-01 절 참고).
 * 로그인 상태에서 조회 이력이 없을 때는 다른 문구("아직 조회한 단지가 없어요")를 쓴다 — 이미
 * 로그인한 사용자에게 "로그인하면..."을 보여주는 건 명백히 틀린 문구이기 때문이다.
 *
 * 주소/가격/면적·층수는 RecentViewResponse에 없는 필드라(complexId/complexName/housingType/
 * viewedAt 4개뿐) 생략한다 — ComplexSummaryResponse의 matchMethod/floor 생략과 같은 종류의
 * 데이터 갭.
 */
export function RecentViews({ favoritedIds, onToggleFavorite }: RecentViewsProps) {
  const { isAuthenticated } = useAuth();
  const [views, setViews] = useState<RecentViewResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        const result = await getRecentViews(3);
        if (!cancelled) {
          setViews(result);
        }
      } catch {
        if (!cancelled) {
          setViews([]);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  return (
    <div className="flex h-full flex-col rounded-[16px] border border-[#f3f4f6] bg-white p-5 shadow-[0_1px_1.5px_rgba(0,0,0,0.1),0_1px_1px_rgba(0,0,0,0.1)]">
      <div className="flex items-center gap-1.5 pb-3">
        <ClockIcon className="size-4 text-[#101828]" />
        <p className="text-[15px] font-bold text-[#101828]">최근 조회한 단지</p>
      </div>

      {loading ? (
        <div className="flex flex-1 items-center justify-center">
          <Spinner />
        </div>
      ) : views.length === 0 ? (
        <div className="flex flex-1 items-center justify-center">
          <EmptyState
            icon={<ClockIcon className="size-6" />}
            description={
              isAuthenticated ? (
                <>
                  아직 조회한 단지가 없어요.
                  <br />
                  관심있는 단지를 둘러보세요.
                </>
              ) : (
                <>
                  로그인하면 최근 조회한
                  <br />
                  단지가 자동으로 저장돼요
                </>
              )
            }
          />
        </div>
      ) : (
        <div className="flex flex-1 flex-col">
          {views.map((view) => (
            <div key={view.complexId} className="flex items-start gap-3 border-b border-[#f9fafb] py-3 last:border-b-0">
              <div className="flex size-[60px] shrink-0 items-center justify-center rounded-[10px] bg-[#f3f4f6]">
                <HomeIcon className="size-6 opacity-40 [&_path]:stroke-[#6a7282]" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <p className="truncate text-[13px] font-semibold text-[#101828]">{view.complexName}</p>
                  <button
                    type="button"
                    onClick={() => onToggleFavorite(view.complexId)}
                    aria-label={favoritedIds.has(view.complexId) ? '관심 매물 해제' : '관심 매물 등록'}
                    aria-pressed={favoritedIds.has(view.complexId)}
                    className="shrink-0 text-[#99a1af] hover:text-[#e7000b]"
                  >
                    <HeartIcon
                      filled={favoritedIds.has(view.complexId)}
                      className={`size-3.5 ${favoritedIds.has(view.complexId) ? 'text-[#ff2056]' : ''}`}
                    />
                  </button>
                </div>
                <p className="mt-0.5 text-[11px] text-[#99a1af]">
                  {view.housingType ? HOUSING_TYPE_LABEL[view.housingType] : ''}
                </p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
