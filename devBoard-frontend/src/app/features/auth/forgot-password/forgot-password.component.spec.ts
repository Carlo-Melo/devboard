import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ForgotPasswordComponent } from './forgot-password.component';
import { AuthService } from '../../../core/services/auth.service';
import { ApiError } from '../../../core/models/api-error.model';

describe('ForgotPasswordComponent', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['forgotPassword']);

    await TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent],
      providers: [{ provide: AuthService, useValue: authServiceSpy }, provideRouter([])]
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should create', () => {
    const fixture = createComponent();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should not call authService when email is invalid', () => {
    const fixture = createComponent();
    fixture.componentInstance.onSubmit();
    expect(authServiceSpy.forgotPassword).not.toHaveBeenCalled();
  });

  it('should show the generic confirmation after submitting a valid email', () => {
    authServiceSpy.forgotPassword.and.returnValue(of({ success: true, message: 'ok' }));

    const fixture = createComponent();
    fixture.componentInstance.form.setValue({ email: 'joao@example.com' });
    fixture.componentInstance.onSubmit();

    expect(authServiceSpy.forgotPassword).toHaveBeenCalledWith({ email: 'joao@example.com' });
    expect(fixture.componentInstance.submitted).toBeTrue();
  });

  it('should show the backend error message on failure (e.g. rate limit)', () => {
    const apiError: ApiError = { status: 429, message: 'Limite de requisições excedido.' };
    authServiceSpy.forgotPassword.and.returnValue(throwError(() => apiError));

    const fixture = createComponent();
    fixture.componentInstance.form.setValue({ email: 'joao@example.com' });
    fixture.componentInstance.onSubmit();

    expect(fixture.componentInstance.errorMessage).toBe('Limite de requisições excedido.');
    expect(fixture.componentInstance.submitted).toBeFalse();
  });
});
