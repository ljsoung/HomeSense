export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: ApiFieldError[];
}

/**
 * COM-RES-01 — 목록 조회 응답에만 `data`와 함께 실리는 형제 필드(0-base page). CLAUDE.md "응답
 * 포맷" 절의 `PageMeta.from(Page<?>)`를 그대로 미러링한다 — 목록 조회가 아닌 응답에서는 항상
 * 없고(`spring.jackson.default-property-inclusion=non_null`이라 키 자체가 안 나온다), 그래서
 * `ApiSuccessResponse<T>`에서도 선택 필드다.
 */
export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiSuccessResponse<T> {
  success: true;
  data: T;
  error: null;
  timestamp: string;
  pageMeta?: PageMeta;
}

export interface ApiErrorResponse {
  success: false;
  data: null;
  error: ApiError;
  timestamp: string;
}

export type ApiResponse<T> = ApiSuccessResponse<T> | ApiErrorResponse;
