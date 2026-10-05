export interface FieldErrorItem {
  field: string;
  message: string;
}

export interface ApiError {
  status: number;
  message: string;
  fieldErrors?: FieldErrorItem[];
  retryAfterSeconds?: number;
}

export interface MessageResponse {
  success: boolean;
  message: string;
}
