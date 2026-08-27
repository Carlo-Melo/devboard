import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RegisterComponent } from './register.component';
import { AuthService } from '../../../core/services/auth.service';
import { ApiError } from '../../../core/models/api-error.model';
import { AuthResponse } from '../../../core/models/auth.models';

describe('RegisterComponent', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['register']);

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [{ provide: AuthService, useValue: authServiceSpy }, provideRouter([])]
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(RegisterComponent);
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
    expect(authServiceSpy.register).not.toHaveBeenCalled();
  });

  it('should flag passwordMismatch when confirmPassword differs from password', () => {
    const fixture = createComponent();
    const form = fixture.componentInstance.registerForm;

    form.patchValue({
      username: 'joao_dev',
      email: 'joao@example.com',
      password: 'SecurePass123',
      confirmPassword: 'Different123'
    });

    expect(form.get('confirmPassword')?.errors?.['passwordMismatch']).toBeTrue();
  });

  it('should call authService.register and expose the created user on success', () => {
    const response: AuthResponse = {
      token: 'token-fake',
      expiresAt: new Date().toISOString(),
      user: {
        id: 1,
        username: 'joao_dev',
        email: 'joao@example.com',
        authProvider: 'TRADITIONAL',
        githubConnected: false
      }
    };
    authServiceSpy.register.and.returnValue(of(response));

    const fixture = createComponent();
    fixture.componentInstance.registerForm.setValue({
      username: 'joao_dev',
      email: 'joao@example.com',
      password: 'SecurePass123',
      confirmPassword: 'SecurePass123',
      fullName: ''
    });

    fixture.componentInstance.onSubmit();

    expect(authServiceSpy.register).toHaveBeenCalled();
    expect(fixture.componentInstance.registeredUser?.username).toBe('joao_dev');
  });

  it('should surface backend field errors on the matching control', () => {
    const apiError: ApiError = {
      status: 409,
      message: 'Username já está em uso',
      fieldErrors: [{ field: 'username', message: 'Username já está em uso' }]
    };
    authServiceSpy.register.and.returnValue(throwError(() => apiError));

    const fixture = createComponent();
    fixture.componentInstance.registerForm.setValue({
      username: 'joao_dev',
      email: 'joao@example.com',
      password: 'SecurePass123',
      confirmPassword: 'SecurePass123',
      fullName: ''
    });

    fixture.componentInstance.onSubmit();

    expect(fixture.componentInstance.errorMessage).toBe('Username já está em uso');
    expect(fixture.componentInstance.registerForm.get('username')?.errors?.['server']).toBe('Username já está em uso');
  });
});
