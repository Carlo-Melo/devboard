import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ProjectListComponent } from './project-list.component';
import { ProjectService } from '../../../core/services/project.service';
import { AuthService } from '../../../core/services/auth.service';
import { PageResponse } from '../../../core/models/page-response.model';
import { ProjectSummaryResponse } from '../../../core/models/project.models';

describe('ProjectListComponent', () => {
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const owner = { id: 1, username: 'joao_dev', email: 'joao@example.com', authProvider: 'TRADITIONAL' as const, githubConnected: false };

  function pageOf(projects: ProjectSummaryResponse[]): PageResponse<ProjectSummaryResponse> {
    return { content: projects, page: 0, size: 12, totalElements: projects.length, totalPages: 1, first: true, last: true };
  }

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['list']);
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getMe', 'logout']);
    authServiceSpy.getMe.and.returnValue(of(owner));

    await TestBed.configureTestingModule({
      imports: [ProjectListComponent],
      providers: [
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
        provideRouter([])
      ]
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(ProjectListComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should load projects on init', () => {
    const projects: ProjectSummaryResponse[] = [{
      id: 1, name: 'Projeto X', owner, memberCount: 0, githubLinked: false, updatedAt: new Date().toISOString()
    }];
    projectServiceSpy.list.and.returnValue(of(pageOf(projects)));

    const fixture = createComponent();

    expect(fixture.componentInstance.projects.length).toBe(1);
    expect(fixture.componentInstance.isLoading).toBeFalse();
  });

  it('should show an error state when loading fails', () => {
    projectServiceSpy.list.and.returnValue(throwError(() => ({ status: 500, message: 'erro' })));

    const fixture = createComponent();

    expect(fixture.componentInstance.errorMessage).toBeTruthy();
    expect(fixture.componentInstance.isLoading).toBeFalse();
  });

  it('should not advance page when already on the last page', () => {
    projectServiceSpy.list.and.returnValue(of(pageOf([])));
    const fixture = createComponent();

    fixture.componentInstance.nextPage();

    expect(projectServiceSpy.list).toHaveBeenCalledTimes(1);
  });
});
