// e2e 목업 응답 본문을 실제 서버와 같은 모양으로 만든다. 서버는 spring.jackson.default-property-inclusion=non_null이라
// null인 필드를 JSON에서 아예 뺀다 — 목업이 null을 그대로 보내면 프론트의 `!== null` 같은 잘못된 검사가 통과해
// 실제 화면에서만 "NaN만원"·"undefined층"이 나오는 회귀를 잡지 못한다(2026-10-05, MY-02에서 처음 발견).
// 그래서 목업은 이 헬퍼로만 본문을 만들고, "값 없음"을 표현할 때도 null을 넣으면 된다(키째 빠진다).

/** JSON.stringify replacer — 깊이와 상관없이 값이 null인 키를 뺀다. 배열 안의 null 원소는 서버도 그대로 두므로 남긴다. */
export const dropNulls = function (key, value) {
  if (value === null && !Array.isArray(this)) return undefined;
  return value;
};

/** 성공 응답(ApiResponse). 목록이면 pageMeta를 함께 싣는다. */
export const okBody = (data, pageMeta) =>
  JSON.stringify({ success: true, data, error: null, ...(pageMeta ? { pageMeta } : {}), timestamp: '' }, dropNulls);

/** 실패 응답(ApiResponse). data는 null이라 키째 빠진다(서버와 같다). */
export const errBody = (code, message) =>
  JSON.stringify({ success: false, data: null, error: { code, message }, timestamp: '' }, dropNulls);
