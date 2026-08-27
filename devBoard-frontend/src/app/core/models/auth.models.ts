export type AuthProvider = 'TRADITIONAL' | 'GITHUB';

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
  fullName?: string;
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  fullName?: string;
  avatarUrl?: string;
  authProvider: AuthProvider;
  githubConnected: boolean;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: UserResponse;
}

export interface LoginRequest {
  emailOrUsername: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
  confirmPassword: string;
}

export interface UpdateProfileRequest {
  fullName?: string;
  avatarUrl?: string;
}

export interface TokenValidationResponse {
  valid: boolean;
  expiresAt?: string;
}
