import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/services/auth.service';
import { ApiError } from '../../../core/models/api-error.model';
import { AuthResponse } from '../../../core/models/auth.models';

describe('LoginComponent', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        provideRouter([{ path: '', component: LoginComponent }])
      ]
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should create', () => {
    const fixture = createComponent();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should not call authService when form is invalid', () => {
    const fixture = createComponent();
    fixture.componentInstance.onSubmit();
    expect(authServiceSpy.login).not.toHaveBeenCalled();
  });

  it('should call authService.login and redirect on success', () => {
    const response: AuthResponse = {
      token: 'token-fake',
      expiresAt: new Date().toISOString(),
      user: { id: 1, username: 'joao_dev', email: 'joao@example.com', authProvider: 'TRADITIONAL', githubConnected: false }
    };
    authServiceSpy.login.and.returnValue(of(response));

    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigateByUrl');

    const fixture = createComponent();
    fixture.componentInstance.loginForm.setValue({ emailOrUsername: 'joao_dev', password: 'SecurePass123' });
    fixture.componentInstance.onSubmit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({ emailOrUsername: 'joao_dev', password: 'SecurePass123' });
    expect(navigateSpy).toHaveBeenCalledWith('/projects');
  });

  it('should show the backend error message on failure', () => {
    const apiError: ApiError = { status: 401, message: 'Credenciais inválidas' };
    authServiceSpy.login.and.returnValue(throwError(() => apiError));

    const fixture = createComponent();
    fixture.componentInstance.loginForm.setValue({ emailOrUsername: 'joao_dev', password: 'errada' });
    fixture.componentInstance.onSubmit();

    expect(fixture.componentInstance.errorMessage).toBe('Credenciais inválidas');
  });
});
