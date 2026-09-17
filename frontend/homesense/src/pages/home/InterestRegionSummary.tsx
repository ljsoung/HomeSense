import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { EmptyState } from '../../components/ui/EmptyState';
import { HomeIcon } from '../../components/icons/HomeIcon';
import { InfoCircleIcon } from '../../components/icons/InfoCircleIcon';
import { PlusIcon } from '../../components/icons/PlusIcon';
import { Spinner } from '../../components/ui/Spinner';
import { TrendingDownIcon } from '../../components/icons/TrendingDownIcon';
import { TrendingUpIcon } from '../../components/icons/TrendingUpIcon';
import { getInterestSummary, splitRegionPath } from '../../features/region/api';
import type { InterestRegionSummaryResponse } from '../../features/region/types';
import { formatChangeRate, formatKoreanPrice } from '../../lib/format';
import { useAuth } from '../../features/auth/useAuth';

/**
 * HOME-01 구성요소 4 앞부분 — 완료 조건: 비로그인은 interest-summary를 호출하지 않고(불필요한
 * 401 회피) 가입 유도 카드를 대신 보여준다.
 *
 * 거래건수(tradeCount, 2026-09-17 CPX-RCV-RGN 카드 표시 필드 보강으로 추가됨) — Figma 원본 카피는
 * "이번 달 N건"이지만 실제 백엔드 집계 기간은 달력월이 아니라 "최근 1개월" 롤링 윈도우다
 * (RegionStatsCalculator.calculate()가 `LocalDate.now(KST).minusMonths(1)`로 계산 — 코드 확인
 * 완료). "이번 달"을 그대로 쓰면 매달 1~2일경 실제로는 지난 30여 일 치 거래를 세고 있으면서도
 * "이번 달"이라 자칭해 이용자가 잘못된 기간으로 오인할 수 있어, 실제 계산 기준과 일치하는 "최근
 * 1개월"로 문구를 확정했다("최근 30일"도 검토했으나 minusMonths(1)은 날짜 수 고정이 아니라 달력상
 * 1개월 앞이라 30일보다 부정확할 이유가 없고, 오히려 실제 구현 표현과 한 글자도 다르지 않게
 * 맞출 수 있어 이 쪽을 택했다).
 */
export function InterestRegionSummary() {
  const { isAuthenticated } = useAuth();
  const [regions, setRegions] = useState<InterestRegionSummaryResponse[] | null>(null);
  const [loading, setLoading] = useState(isAuthenticated);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        const result = await getInterestSummary();
        if (!cancelled) {
          setRegions(result);
        }
      } catch {
        if (!cancelled) {
          setRegions([]);
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
      {isAuthenticated && (
        <div className="flex items-center justify-between pb-4">
          <p className="text-[15px] font-bold text-[#101828]">관심 지역 요약</p>
          <button type="button" disabled className="flex items-center gap-1 text-[12px] font-semibold text-brand disabled:opacity-60">
            <PlusIcon className="size-3.5" />
            지역 추가
          </button>
        </div>
      )}

      {!isAuthenticated ? (
        <SignupInducementCard />
      ) : loading ? (
        <div className="flex flex-1 items-center justify-center">
          <Spinner />
        </div>
      ) : regions && regions.length > 0 ? (
        <>
          <div className="flex flex-1 flex-col gap-3">
            {regions.map((region) => (
              <RegionCard key={region.favoriteRegionId} region={region} />
            ))}
          </div>
          <div className="flex items-center gap-1 pt-3 text-[11px] text-[#99a1af]">
            <InfoCircleIcon className="size-3" />
            전월 동기간 대비 변동률 · 매일 오전 8시 갱신
          </div>
        </>
      ) : (
        <EmptyState
          icon={<PlusIcon className="size-5" />}
          description={
            <>
              아직 등록한 관심 지역이 없어요.
              <br />
              지역을 추가하고 시세 변동을 확인해보세요.
            </>
          }
        />
      )}
    </div>
  );
}

function RegionCard({ region }: { region: InterestRegionSummaryResponse }) {
  const { sigungu, eupmyeondong } = splitRegionPath(region.fullPath);
  const hasStats = region.avgPrice !== null && region.changeRate !== null;

  return (
    <div className="flex flex-1 flex-col justify-center rounded-[14px] border border-[#f3f4f6] bg-[#fafafa] p-4">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-[11px] text-[#99a1af]">{sigungu}</p>
          <p className="text-[14px] font-bold text-[#101828]">{eupmyeondong}</p>
        </div>
        {hasStats && region.changeRate !== null && (
          <span
            className={`flex items-center gap-0.5 rounded-full px-2 py-0.5 text-[11px] font-bold ${
              region.changeRate >= 0 ? 'bg-[#fef2f2] text-[#e7000b]' : 'bg-[#eff6ff] text-[#155dfc]'
            }`}
          >
            {region.changeRate >= 0 ? <TrendingUpIcon className="size-3" /> : <TrendingDownIcon className="size-3" />}
            {formatChangeRate(region.changeRate)}
          </span>
        )}
      </div>
      <p className="pt-2 text-[20px] font-extrabold tracking-[-0.3px] text-[#1c1c1e]">
        {hasStats && region.avgPrice !== null ? formatKoreanPrice(region.avgPrice) : '데이터 없음'}
      </p>
      <p className="pt-1.5 text-[11px] text-[#99a1af]">평균 거래가 · 최근 1개월 {region.tradeCount}건</p>
    </div>
  );
}

function SignupInducementCard() {
  return (
    <EmptyState
      variant="inducement"
      icon={<HomeIcon className="size-7 [&_path]:stroke-brand" />}
      title="관심 지역을 등록하고"
      description={
        <>
          가격 변동 알림을 받아보세요.
          <br />
          지역별 실거래가 동향을 한눈에 파악하세요.
        </>
      }
      actions={
        <>
          <Link to="/signup" className="flex items-center justify-center rounded-[14px] bg-brand px-6 py-2.5 text-[13.5px] font-semibold text-white">
            무료 회원가입
          </Link>
          <Link
            to="/login"
            className="flex items-center justify-center rounded-[14px] border border-[#e5e7eb] bg-white px-5 py-2.5 text-[13.5px] font-semibold text-[#4a5565]"
          >
            로그인
          </Link>
        </>
      }
    />
  );
}
