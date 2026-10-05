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

  it('keeps the create-project action in the desktop navigation', () => {
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    const createProjectLink = fixture.nativeElement.querySelector('nav a[aria-label="Criar projeto"]') as HTMLAnchorElement | null;

    expect(createProjectLink).toBeTruthy();
    expect(createProjectLink?.getAttribute('href')).toContain('/projects/new');
  });

  it('shows the sidebar destinations in the requested order', () => {
    const fixture = TestBed.createComponent(AppShellComponent); fixture.detectChanges();
    const links = Array.from(fixture.nativeElement.querySelectorAll('nav a')) as HTMLAnchorElement[];
    expect(links.map(link => link.getAttribute('href'))).toEqual(['/projects', '/projects/list', '/repositories', '/projects/new']);
  });

  for (const [url, destination, section] of [
    ['/projects?project=7#summary', '/projects', 'Home'],
    ['/projects/list?page=2#items', '/projects/list', 'Meus projetos'],
    ['/projects/7', '/projects/list', 'Detalhe do projeto'],
    ['/projects/7/edit', '/projects/list', 'Editar projeto'],
    ['/projects/7/members', '/projects/list', 'Membros do projeto'],
    ['/repositories?search=dev', '/repositories', 'Meus repositórios'],
    ['/projects/new', '/projects/new', 'Novo projeto']
  ]) {
    it(`activates only the correct sidebar destination for ${url}`, () => {
      spyOnProperty(TestBed.inject(Router), 'url', 'get').and.returnValue(url);
      const fixture = TestBed.createComponent(AppShellComponent); fixture.detectChanges();
      const active = fixture.nativeElement.querySelectorAll('nav a.active');
      expect(active.length).toBe(1);
      expect(active[0].getAttribute('href')).toBe(destination);
      expect(active[0].getAttribute('aria-current')).toBe('page');
      expect(fixture.componentInstance.section).toBe(section);
      fixture.componentInstance.menuOpen = true; fixture.componentInstance.closeMenus();
      expect(fixture.componentInstance.menuOpen).toBeFalse();
    });
  }
});
