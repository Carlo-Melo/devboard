import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AppShellComponent } from './app-shell.component';
import { AuthService } from '../../../core/services/auth.service';

describe('AppShellComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  beforeEach(async () => {
    auth = jasmine.createSpyObj('AuthService', ['getMe', 'logout', 'removeToken']);
    auth.getMe.and.returnValue(of({ id: 1, username: 'ana', email: 'ana@example.com', authProvider: 'TRADITIONAL', githubConnected: false }));
    await TestBed.configureTestingModule({ imports: [AppShellComponent], providers: [provideRouter([]), { provide: AuthService, useValue: auth }] }).compileComponents();
  });

  it('opens the account menu and closes it with Escape', () => {
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.account-trigger').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.account-popover').textContent).toContain('ana@example.com');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.account-popover')).toBeNull();
  });

  it('clears the local session even if the logout request fails', () => {
    auth.logout.and.returnValue(throwError(() => ({ status: 503 })));
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl');
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.componentInstance.logout();
    expect(auth.removeToken).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
