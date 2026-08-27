import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import {
  AuthResponse,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
  TokenValidationResponse,
  UpdateProfileRequest,
  UserResponse
} from '../models/auth.models';
import { MessageResponse } from '../models/api-error.model';
import { environment } from '../../../environments/environment';

const TOKEN_KEY = 'devboardToken';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly apiUrl = `${environment.apiUrl}/auth`;

  constructor(private http: HttpClient) {}

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, request)
      .pipe(tap(response => this.setToken(response.token)));
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, request)
      .pipe(tap(response => this.setToken(response.token)));
  }

  logout(): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/logout`, {})
      .pipe(tap(() => this.removeToken()));
  }

  getMe(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.apiUrl}/me`);
  }

  updateProfile(request: UpdateProfileRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${this.apiUrl}/me`, request);
  }

  changePassword(request: ChangePasswordRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/change-password`, request);
  }

  forgotPassword(request: ForgotPasswordRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/forgot-password`, request);
  }

  validateResetToken(token: string): Observable<TokenValidationResponse> {
    return this.http.get<TokenValidationResponse>(`${this.apiUrl}/reset-password/validate-token`, {
      params: { token }
    });
  }

  resetPassword(request: ResetPasswordRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/reset-password`, request);
  }

  setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  removeToken(): void {
    localStorage.removeItem(TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }
}
