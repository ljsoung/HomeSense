import type { KakaoMapsNamespace } from './kakaoTypes';

/** 카카오맵 JS SDK 주소. e2e는 이 주소를 가로채 가짜 SDK를 넣거나 막는다. */
export const KAKAO_SDK_URL = 'https://dapi.kakao.com/v2/maps/sdk.js';

let loading: Promise<KakaoMapsNamespace> | null = null;

export class KakaoSdkError extends Error {}

/**
 * 카카오맵 JS SDK를 한 번만 불러온다(Promise 싱글턴). 스크립트는 `autoload=false`로 넣고 `kakao.maps.load()` 콜백에서
 * 끝난 것으로 본다 — autoload를 켜면 SDK가 document.write로 하위 스크립트를 넣어 비동기 로드에서 동작하지 않는다.
 *
 * 실패하면(키 없음·네트워크 차단·도메인 미등록으로 load 콜백이 오지 않음) 싱글턴을 비워 두어, 화면의 "다시 시도"가
 * 이 함수를 다시 불렀을 때 스크립트를 새로 넣는다. 키는 VITE_KAKAO_JS_KEY(JavaScript 키 — REST 키가 아니다)다.
 */
export function loadKakaoMaps(timeoutMs = 10_000): Promise<KakaoMapsNamespace> {
  if (loading) return loading;

  const attempt = new Promise<KakaoMapsNamespace>((resolve, reject) => {
    const existing = window.kakao?.maps;
    if (existing && typeof existing.LatLng === 'function') {
      resolve(existing);
      return;
    }
    const appKey = import.meta.env.VITE_KAKAO_JS_KEY;
    if (!appKey) {
      reject(new KakaoSdkError('카카오맵 키(VITE_KAKAO_JS_KEY)가 설정되지 않았습니다.'));
      return;
    }

    const script = document.createElement('script');
    script.src = `${KAKAO_SDK_URL}?appkey=${encodeURIComponent(appKey)}&autoload=false&libraries=clusterer,services`;
    script.async = true;
    script.dataset.kakaoSdk = 'true';

    // 도메인이 콘솔에 등록되지 않으면 스크립트는 받아지지만 load 콜백이 영영 오지 않을 수 있어 시간 상한을 둔다.
    const timer = window.setTimeout(() => fail(new KakaoSdkError('지도를 불러오지 못했습니다.')), timeoutMs);
    const fail = (error: Error) => {
      window.clearTimeout(timer);
      script.remove();
      reject(error);
    };

    script.onload = () => {
      const maps = window.kakao?.maps;
      if (!maps) {
        fail(new KakaoSdkError('지도를 불러오지 못했습니다.'));
        return;
      }
      maps.load(() => {
        window.clearTimeout(timer);
        resolve(maps);
      });
    };
    script.onerror = () => fail(new KakaoSdkError('지도를 불러오지 못했습니다.'));
    document.head.appendChild(script);
  });

  loading = attempt;
  attempt.catch(() => {
    // 실패한 Promise를 계속 들고 있으면 재시도해도 같은 실패만 돌려준다.
    if (loading === attempt) loading = null;
  });
  return attempt;
}

/** 테스트용 — 싱글턴을 비운다. */
export function resetKakaoLoaderForTest(): void {
  loading = null;
}
