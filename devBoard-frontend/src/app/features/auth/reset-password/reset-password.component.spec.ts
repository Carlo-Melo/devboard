import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ResetPasswordComponent } from './reset-password.component';
import { AuthService } from '../../../core/services/auth.service';
import { ApiError } from '../../../core/models/api-error.model';

describe('ResetPasswordComponent', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  function configure(token: string | null) {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['validateResetToken', 'resetPassword']);

    return TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } }
        }
      ]
    }).compileComponents();
  }

  it('should show invalid state when there is no token in the query string', async () => {
    await configure(null);
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.state).toBe('invalid');
    expect(authServiceSpy.validateResetToken).not.toHaveBeenCalled();
  });

  it('should show the form when the token is valid', async () => {
    await configure('valid-token');
    authServiceSpy.validateResetToken.and.returnValue(of({ valid: true }));

    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.detectChanges();

    expect(authServiceSpy.validateResetToken).toHaveBeenCalledWith('valid-token');
    expect(fixture.componentInstance.state).toBe('form');
  });

  it('should show invalid state when the token validation fails', async () => {
    await configure('expired-token');
    const apiError: ApiError = { status: 400, message: 'Token inválido ou expirado' };
    authServiceSpy.validateResetToken.and.returnValue(throwError(() => apiError));

    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.state).toBe('invalid');
  });

  it('should reset the password and move to done state on success', async () => {
    await configure('valid-token');
    authServiceSpy.validateResetToken.and.returnValue(of({ valid: true }));
    authServiceSpy.resetPassword.and.returnValue(of({ success: true, message: 'ok' }));

    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ newPassword: 'NovaSenha123', confirmPassword: 'NovaSenha123' });
    fixture.componentInstance.onSubmit();

    expect(authServiceSpy.resetPassword).toHaveBeenCalledWith({
      token: 'valid-token',
      newPassword: 'NovaSenha123',
      confirmPassword: 'NovaSenha123'
    });
    expect(fixture.componentInstance.state).toBe('done');
  });
});
