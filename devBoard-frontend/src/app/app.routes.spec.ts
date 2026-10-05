import { TestBed } from '@angular/core/testing';
import { NgZone } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { routes } from './app.routes';
import { AuthService } from './core/services/auth.service';
import { ProjectService } from './core/services/project.service';
import { BoardService } from './core/services/board.service';
import { DashboardService } from './core/services/dashboard.service';
import { GithubService } from './core/services/github.service';
import { HomeComponent } from './features/home/home.component';
import { ProjectListComponent } from './features/projects/project-list/project-list.component';
import { ProjectDetailComponent } from './features/projects/project-detail/project-detail.component';
import { LoginComponent } from './features/auth/login/login.component';

describe('Workspace routes', () => {
  let authenticated: boolean;
  let projects: jasmine.SpyObj<ProjectService>;
  let dashboard: jasmine.SpyObj<DashboardService>;
  let github: jasmine.SpyObj<GithubService>;
  const user = { id: 1, username: 'ana', email: 'ana@example.com', authProvider: 'TRADITIONAL' as const, githubConnected: false };
  const page = { content: [], page: 0, size: 12, totalElements: 0, totalPages: 0, first: true, last: true };
  beforeEach(() => {
    authenticated = true;
    projects = jasmine.createSpyObj('ProjectService', ['list', 'getById']); projects.list.and.returnValue(of(page));
    projects.getById.and.returnValue(of({ id: 7, name: 'TCC', owner: user, archived: false, currentUserRole: 'ADMIN', currentUserOwner: true, boards: [], members: [], createdAt: '', updatedAt: '' }));
    dashboard = jasmine.createSpyObj('DashboardService', ['summary', 'tasks', 'activities']);
    dashboard.summary.and.returnValue(of({ activeProjects: 0, pendingTasks: 0, overdueTasks: 0, dueSoonTasks: 0, tasksByStage: {}, pendingByPriority: {} }));
    dashboard.tasks.and.returnValue(of(page)); dashboard.activities.and.returnValue(of(page));
    github = jasmine.createSpyObj('GithubService', ['availableRepositories']);
    TestBed.configureTestingModule({ providers: [provideRouter(routes),
      { provide: AuthService, useValue: { isAuthenticated: () => authenticated, getMe: () => of(user), login: () => { authenticated = true; return of({ token: 'test', user, expiresAt: '' }); } } },
      { provide: ProjectService, useValue: projects }, { provide: DashboardService, useValue: dashboard },
      { provide: GithubService, useValue: github }, { provide: BoardService, useValue: {} }
    ] });
  });
  it('keeps root and default login destination at Home', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', HomeComponent);
    expect(TestBed.inject(Router).url).toBe('/projects');
    const login = await harness.navigateByUrl('/login', LoginComponent);
    login.loginForm.setValue({ emailOrUsername: 'ana', password: 'Password123' }); TestBed.inject(NgZone).run(() => login.onSubmit());
    await harness.fixture.whenStable(); expect(TestBed.inject(Router).url).toBe('/projects');
  });
  it('resolves list before the project ID route and only loads projects', async () => {
    const harness = await RouterTestingHarness.create(); await harness.navigateByUrl('/projects/list', ProjectListComponent);
    expect(projects.getById).not.toHaveBeenCalled(); expect(dashboard.summary).not.toHaveBeenCalled(); expect(github.availableRepositories).not.toHaveBeenCalled();
  });
  it('navigates Home → list → detail → back to list', async () => {
    const harness = await RouterTestingHarness.create(); await harness.navigateByUrl('/projects', HomeComponent);
    await harness.navigateByUrl('/projects/list', ProjectListComponent); await harness.navigateByUrl('/projects/7', ProjectDetailComponent);
    expect(harness.routeNativeElement?.querySelector('.back-link')?.getAttribute('href')).toBe('/projects/list');
    await harness.navigateByUrl('/projects/list', ProjectListComponent); expect(TestBed.inject(Router).url).toBe('/projects/list');
  });
  for (const destination of ['/projects', '/projects/list']) {
    it(`protects ${destination} and preserves returnUrl through login`, async () => {
      authenticated = false;
      const harness = await RouterTestingHarness.create(); const login = await harness.navigateByUrl(destination, LoginComponent);
      expect(TestBed.inject(Router).url).toBe('/login?returnUrl=' + encodeURIComponent(destination));
      login.loginForm.setValue({ emailOrUsername: 'ana', password: 'Password123' }); TestBed.inject(NgZone).run(() => login.onSubmit());
      await harness.fixture.whenStable(); expect(TestBed.inject(Router).url).toBe(destination);
    });
  }
});
