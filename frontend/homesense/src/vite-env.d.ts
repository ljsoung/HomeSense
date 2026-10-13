/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  /** 카카오맵 JavaScript 키(REST 키 아님). frontend/homesense/.env.local(git 제외)과 Vercel 환경변수에 같은 이름으로 둔다. */
  readonly VITE_KAKAO_JS_KEY?: string;
}
