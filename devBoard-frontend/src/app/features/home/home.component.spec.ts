import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HomeComponent } from './home.component';
import { AuthService } from '../../core/services/auth.service';
import { ProjectService } from '../../core/services/project.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { GithubService } from '../../core/services/github.service';
import { ProjectSummaryResponse } from '../../core/models/project.models';

describe('HomeComponent', () => {
  let projects: jasmine.SpyObj<ProjectService>;
  let dashboard: jasmine.SpyObj<DashboardService>;
  let github: jasmine.SpyObj<GithubService>;
  const owner = { id: 1, username: 'ana', email: 'ana@example.com', authProvider: 'TRADITIONAL' as const, githubConnected: true };
  const allProjects: ProjectSummaryResponse[] = Array.from({ length: 8 }, (_, i) => ({ id: i + 1, name: `Projeto ${i + 1}`, owner, memberCount: 1, boardCount: 2, githubBoardCount: 0, updatedAt: '2026-10-01T10:00:00' }));
  const emptyPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true };

  beforeEach(async () => {
    projects = jasmine.createSpyObj('ProjectService', ['list']);
    projects.list.and.callFake((_archived, _page, size) => of({ ...emptyPage, content: allProjects.slice(0, size), totalElements: 8 }));
    dashboard = jasmine.createSpyObj('DashboardService', ['summary', 'tasks', 'activities']);
    dashboard.summary.and.returnValue(of({ activeProjects: 8, pendingTasks: 0, overdueTasks: 0, dueSoonTasks: 0, tasksByStage: {}, pendingByPriority: {} }));
    dashboard.tasks.and.returnValue(of(emptyPage));
    dashboard.activities.and.returnValue(of(emptyPage));
    github = jasmine.createSpyObj('GithubService', ['availableRepositories', 'destinations']);
    github.availableRepositories.and.returnValue(of(emptyPage));
    await TestBed.configureTestingModule({ imports: [HomeComponent], providers: [provideRouter([]),
      { provide: AuthService, useValue: { getMe: () => of(owner) } },
      { provide: ProjectService, useValue: projects }, { provide: DashboardService, useValue: dashboard },
      { provide: GithubService, useValue: github }
    ] }).compileComponents();
  });

  it('keeps the dashboard and a four-project preview with an independent filter', () => {
    const fixture = TestBed.createComponent(HomeComponent); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-project-card').length).toBe(4);
    expect(fixture.nativeElement.querySelector('.projects-section .pagination')).toBeNull();
    expect(fixture.nativeElement.querySelector('.projects-section .view-all').getAttribute('href')).toBe('/projects/list');
    expect(fixture.nativeElement.querySelectorAll('select option').length).toBe(9);
    expect(fixture.nativeElement.querySelector('.metrics-grid')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.repositories-panel')).toBeTruthy();
    expect(projects.list).toHaveBeenCalledWith(false, 0, 4);
    expect(projects.list).toHaveBeenCalledWith(false, 0, 100);
    expect(github.availableRepositories).toHaveBeenCalledOnceWith('', 0, 4);
  });

  it('refreshes the preview after linking without resetting dashboard filters or pagination', () => {
    const fixture = TestBed.createComponent(HomeComponent); fixture.detectChanges();
    const home = fixture.componentInstance;
    home.onProjectChange('7'); home.taskPage = 2; home.activityPage = 3;
    projects.list.calls.reset(); dashboard.summary.calls.reset();
    home.repositoryLinked(42);
    expect(projects.list).toHaveBeenCalledWith(false, 0, 4);
    expect(projects.list).toHaveBeenCalledWith(false, 0, 100);
    expect(home.selectedProject).toBe(7); expect(home.taskPage).toBe(2); expect(home.activityPage).toBe(3);
    expect(dashboard.summary).not.toHaveBeenCalled();
  });

  it('keeps the dashboard available when GitHub fails and allows retry', () => {
    github.availableRepositories.and.returnValue(throwError(() => ({ status: 503 })));
    const fixture = TestBed.createComponent(HomeComponent); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.metrics-grid')).toBeTruthy();
    expect(fixture.componentInstance.repositoriesError).toBeTrue();
    github.availableRepositories.and.returnValue(of(emptyPage));
    fixture.componentInstance.loadRepositories();
    expect(fixture.componentInstance.repositoriesError).toBeFalse();
  });
});
