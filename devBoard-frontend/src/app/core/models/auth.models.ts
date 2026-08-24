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
