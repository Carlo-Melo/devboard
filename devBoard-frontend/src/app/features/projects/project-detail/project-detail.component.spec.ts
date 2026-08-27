import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ProjectDetailComponent } from './project-detail.component';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectResponse } from '../../../core/models/project.models';
import { ApiError } from '../../../core/models/api-error.model';

describe('ProjectDetailComponent', () => {
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;

  const owner = { id: 1, username: 'joao_dev', email: 'joao@example.com', authProvider: 'TRADITIONAL' as const, githubConnected: false };

  function projectResponse(overrides: Partial<ProjectResponse> = {}): ProjectResponse {
    return {
      id: 1,
      name: 'Projeto X',
      owner,
      currentUserRole: 'ADMIN',
      currentUserOwner: true,
      watchedBranches: [],
      defaultBaseBranch: 'main',
      archived: false,
      members: [],
      boards: [{ id: 1, name: 'Main Board', defaultBoard: true }],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      ...overrides
    };
  }

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['getById', 'archive']);

    await TestBed.configureTestingModule({
      imports: [ProjectDetailComponent],
      providers: [
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
        provideRouter([])
      ]
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(ProjectDetailComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should load the project on init', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse()));

    const fixture = createComponent();

    expect(fixture.componentInstance.project?.name).toBe('Projeto X');
    expect(fixture.componentInstance.isLoading).toBeFalse();
  });

  it('should show a not-found message on 404', () => {
    const apiError: ApiError = { status: 404, message: 'Projeto não encontrado' };
    projectServiceSpy.getById.and.returnValue(throwError(() => apiError));

    const fixture = createComponent();

    expect(fixture.componentInstance.errorMessage).toBe('Projeto não encontrado.');
  });

  it('should expose canEdit only for ADMIN role', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse({ currentUserRole: 'VIEWER', currentUserOwner: false })));

    const fixture = createComponent();

    expect(fixture.componentInstance.canEdit).toBeFalse();
  });

  it('should expose isOwner from currentUserOwner', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse({ currentUserRole: 'ADMIN', currentUserOwner: false })));

    const fixture = createComponent();

    expect(fixture.componentInstance.isOwner).toBeFalse();
  });

  it('should archive and navigate to the project list when confirmed', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse()));
    projectServiceSpy.archive.and.returnValue(of(void 0));
    spyOn(window, 'confirm').and.returnValue(true);

    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigateByUrl');

    const fixture = createComponent();
    fixture.componentInstance.archive();

    expect(projectServiceSpy.archive).toHaveBeenCalledWith(1);
    expect(navigateSpy).toHaveBeenCalledWith('/projects');
  });

  it('should not archive when the confirmation is dismissed', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse()));
    spyOn(window, 'confirm').and.returnValue(false);

    const fixture = createComponent();
    fixture.componentInstance.archive();

    expect(projectServiceSpy.archive).not.toHaveBeenCalled();
  });
});
