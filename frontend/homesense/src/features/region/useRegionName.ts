import axios from 'axios';
import { useEffect, useState } from 'react';
import { lookupRegion } from './api';

/**
 * 조회 결과 — 이름(fullPath) 또는 null(404: 없거나 폐지된 코드). 탭 안에서만 들고 있다. 일시 실패(네트워크·5xx)는 담지 않아
 * 다음 진입에 다시 묻는다. 서버도 같은 결과를 24시간 캐시한다(regionLookup).
 */
const resolved = new Map<string, string | null>();

/** 테스트 전용 — 모듈 캐시를 비운다. */
export function clearRegionNameCache() {
  resolved.clear();
}

/**
 * SRCH-01 — URL에 regionCode만 있고 regionLabel이 없을 때 코드로 지역 이름을 조회한다. 이름을 이미 알면(knownLabel) 부르지
 * 않는다. 없는 코드(404)·조회 실패면 null — 호출자는 이름 없이 검색만 진행한다. 코드가 바뀌면 진행 중 요청을 끊고, 이전 코드의
 * 결과는 돌려주지 않는다.
 */
export function useRegionName(regionCode: string | undefined, knownLabel: string | undefined): string | null {
  const needsLookup = regionCode !== undefined && knownLabel === undefined;
  const [fetched, setFetched] = useState<{ code: string; label: string | null } | null>(null);

  useEffect(() => {
    if (!needsLookup || resolved.has(regionCode)) return;
    const controller = new AbortController();
    lookupRegion(regionCode, controller.signal)
      .then((region) => {
        resolved.set(regionCode, region.fullPath);
        setFetched({ code: regionCode, label: region.fullPath });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (axios.isAxiosError(error) && error.response?.status === 404) resolved.set(regionCode, null);
        setFetched({ code: regionCode, label: null });
      });
    return () => controller.abort();
  }, [needsLookup, regionCode]);

  if (!needsLookup) return null;
  if (resolved.has(regionCode)) return resolved.get(regionCode) ?? null;
  return fetched?.code === regionCode ? fetched.label : null;
}
