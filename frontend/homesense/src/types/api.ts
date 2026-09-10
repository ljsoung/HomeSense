export interface ApiError {
  code: string;
  message: string;
}

export interface ApiSuccessResponse<T> {
  success: true;
  data: T;
  error: null;
  timestamp: string;
}

export interface ApiErrorResponse {
  success: false;
  data: null;
  error: ApiError;
  timestamp: string;
}

export type ApiResponse<T> = ApiSuccessResponse<T> | ApiErrorResponse;
