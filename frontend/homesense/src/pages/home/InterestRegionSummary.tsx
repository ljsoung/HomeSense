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
import type { AuthStatus } from '../../features/auth/authContext';
import { useAuth } from '../../features/auth/useAuth';
import { assertNever } from '../../lib/assertNever';

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
  const { status } = useAuth();
  const [regions, setRegions] = useState<InterestRegionSummaryResponse[] | null>(null);
  const [loading, setLoading] = useState(true);

  // 개인화 API는 로그인이 확인된 뒤에만 부른다 — 확인 중(checking)에 부르면 옛 토큰으로 401을 받고, 비로그인에
  // 부르는 것은 불필요한 401이다.
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      switch (status) {
        case 'checking':
        case 'anonymous':
          // 이전 계정의 요약을 버린다. 다른 탭의 계정 변경으로 authenticated(A) → checking →
          // authenticated(B)가 되면, B로 확정된 첫 렌더에 A의 관심 지역이 잠깐 보였다(loading이 false로 남아 있었다).
          setRegions(null);
          setLoading(true);
          return;
        case 'authenticated':
          break;
        default:
          assertNever(status);
      }
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
  }, [status]);

  return (
    <div className="flex h-full flex-col rounded-[16px] border border-[#f3f4f6] bg-white p-5 shadow-[0_1px_1.5px_rgba(0,0,0,0.1),0_1px_1px_rgba(0,0,0,0.1)]">
      {status === 'authenticated' && (
        <div className="flex items-center justify-between pb-4">
          <p className="text-[15px] font-bold text-[#101828]">관심 지역 요약</p>
          <button type="button" disabled className="flex items-center gap-1 text-[12px] font-semibold text-brand disabled:opacity-60">
            <PlusIcon className="size-3.5" />
            지역 추가
          </button>
        </div>
      )}

      {status !== 'authenticated' ? (
        <SummaryPlaceholder status={status} />
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

/**
 * 로그인이 확인되지 않은 두 상태의 본문. 확인 중에는 가입 유도 카드를 먼저 보였다가 뒤집지 않도록 로딩으로
 * 둔다(비로그인 CTA를 띄우지 않는다).
 */
function SummaryPlaceholder({ status }: { status: Exclude<AuthStatus, 'authenticated'> }) {
  switch (status) {
    case 'checking':
      return (
        <div className="flex flex-1 items-center justify-center">
          <Spinner />
        </div>
      );
    case 'anonymous':
      return <SignupInducementCard />;
    default:
      return assertNever(status);
  }
}

/**
 * 관심 지역 카드 한 장. 평균가·변동률은 거래가 없으면 서버가 키째 빼므로(`non_null`) `!= null`로 본다 — 예전에는
 * `!== null`이라 빠진 키가 통과해 "−NaN% / NaN만원"이 그려졌다. 빈 값 표시는 MY-02 지역 카드와 같다: 평균가가
 * 없으면 "거래 없음", 변동률이 없으면 "—". 두 값은 따로 판단한다 — 이번 달 거래는 있고 직전 1개월 거래가 없으면
 * 평균가는 보이고 변동률만 "—"다(예전엔 둘 중 하나만 없어도 둘 다 숨겼다).
 */
export function RegionCard({ region }: { region: InterestRegionSummaryResponse }) {
  const { sigungu, eupmyeondong } = splitRegionPath(region.fullPath);
  const { avgPrice, changeRate } = region;

  return (
    <div className="flex flex-1 flex-col justify-center rounded-[14px] border border-[#f3f4f6] bg-[#fafafa] p-4">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-[11px] text-[#99a1af]">{sigungu}</p>
          <p className="text-[14px] font-bold text-[#101828]">{eupmyeondong}</p>
        </div>
        {changeRate != null ? (
          <span
            className={`flex items-center gap-0.5 rounded-full px-2 py-0.5 text-[11px] font-bold ${
              changeRate >= 0 ? 'bg-[#fef2f2] text-[#e7000b]' : 'bg-[#eff6ff] text-[#155dfc]'
            }`}
          >
            {changeRate >= 0 ? <TrendingUpIcon className="size-3" /> : <TrendingDownIcon className="size-3" />}
            {formatChangeRate(changeRate)}
          </span>
        ) : (
          <span className="px-2 py-0.5 text-[11px] font-bold text-[#99a1af]">
            <span aria-hidden="true">—</span>
            <span className="sr-only">변동 정보 없음</span>
          </span>
        )}
      </div>
      <p className="pt-2 text-[20px] font-extrabold tracking-[-0.3px] text-[#1c1c1e]">
        {avgPrice != null ? formatKoreanPrice(avgPrice) : '거래 없음'}
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
