import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { GithubCallbackComponent } from './github-callback.component';
import { AuthService } from '../../../core/services/auth.service';

describe('GithubCallbackComponent', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['setToken']);

    await TestBed.configureTestingModule({
      imports: [GithubCallbackComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        provideRouter([{ path: '', component: GithubCallbackComponent }])
      ]
    }).compileComponents();
  });

  afterEach(() => {
    window.location.hash = '';
  });

  function createComponent() {
    const fixture = TestBed.createComponent(GithubCallbackComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should store the token and navigate to returnUrl from the fragment', () => {
    window.location.hash = '#token=jwt-fake&expiresAt=2026-01-01T00%3A00%3A00&returnUrl=%2Fboards%2F1';

    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigateByUrl');

    const fixture = createComponent();

    expect(authServiceSpy.setToken).toHaveBeenCalledWith('jwt-fake');
    expect(navigateSpy).toHaveBeenCalledWith('/boards/1');
    expect(fixture.componentInstance.errorMessage).toBeNull();
  });

  it('should default to /projects when the fragment has no returnUrl', () => {
    window.location.hash = '#token=jwt-fake&expiresAt=2026-01-01T00%3A00%3A00';

    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigateByUrl');

    createComponent();

    expect(navigateSpy).toHaveBeenCalledWith('/projects');
  });

  it('should show an error and skip navigation when the fragment has no token', () => {
    window.location.hash = '';

    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigateByUrl');

    const fixture = createComponent();

    expect(authServiceSpy.setToken).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(fixture.componentInstance.errorMessage).toContain('GitHub');
  });
});
