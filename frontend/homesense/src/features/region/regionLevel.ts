/**
 * 법정동코드 10자리(시도 2 · 시군구 3 · 읍면동 3 · 리 2) 중 읍·면·동 단위인지 — 읍면동 자리가 000이 아니고 리 자리가
 * 00이다. 관심 지역은 읍면동 단위만 등록한다(FR-5.2). 서버도 같은 규칙으로 거부한다
 * (FavoriteService.isEupmyeondongLevel, 400 INVALID_REGION_LEVEL) — 이 함수는 자동완성 후보를 거르는 용도다.
 */
export function isEupmyeondongCode(legalDongCd: string): boolean {
  return /^\d{10}$/.test(legalDongCd) && legalDongCd.slice(5, 8) !== '000' && legalDongCd.slice(8) === '00';
}
