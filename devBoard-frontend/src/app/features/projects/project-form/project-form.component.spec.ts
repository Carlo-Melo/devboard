import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ProjectFormComponent } from './project-form.component';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectResponse } from '../../../core/models/project.models';
import { ApiError } from '../../../core/models/api-error.model';

describe('ProjectFormComponent', () => {
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
      boards: [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      ...overrides
    };
  }

  function configure(paramId: string | null) {
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['create', 'update', 'getById']);

    return TestBed.configureTestingModule({
      imports: [ProjectFormComponent],
      providers: [
        { provide: ProjectService, useValue: projectServiceSpy },
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => paramId } } }
        }
      ]
    }).compileComponents();
  }

  function createComponent() {
    const fixture = TestBed.createComponent(ProjectFormComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should not call projectService when form is invalid (create mode)', async () => {
    await configure(null);
    const fixture = createComponent();

    fixture.componentInstance.onSubmit();

    expect(projectServiceSpy.create).not.toHaveBeenCalled();
  });

  it('should create a project and navigate to its detail page', async () => {
    await configure(null);
    projectServiceSpy.create.and.returnValue(of(projectResponse()));

    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');

    const fixture = createComponent();
    fixture.componentInstance.form.setValue({ name: 'Projeto X', description: '' });
    fixture.componentInstance.onSubmit();

    expect(projectServiceSpy.create).toHaveBeenCalledWith({ name: 'Projeto X', description: '' });
    expect(navigateSpy).toHaveBeenCalledWith(['/projects', 1]);
  });

  it('should surface backend errors on failure', async () => {
    await configure(null);
    const apiError: ApiError = { status: 400, message: 'Nome inválido' };
    projectServiceSpy.create.and.returnValue(throwError(() => apiError));

    const fixture = createComponent();
    fixture.componentInstance.form.setValue({ name: 'Projeto X', description: '' });
    fixture.componentInstance.onSubmit();

    expect(fixture.componentInstance.errorMessage).toBe('Nome inválido');
  });

  it('should load the existing project and switch to edit mode', async () => {
    await configure('1');
    projectServiceSpy.getById.and.returnValue(of(projectResponse({ name: 'Projeto Existente' })));

    const fixture = createComponent();

    expect(fixture.componentInstance.isEditMode).toBeTrue();
    expect(fixture.componentInstance.form.value.name).toBe('Projeto Existente');
  });

  it('should call update instead of create in edit mode', async () => {
    await configure('1');
    projectServiceSpy.getById.and.returnValue(of(projectResponse()));
    projectServiceSpy.update.and.returnValue(of(projectResponse({ name: 'Projeto Editado' })));

    const fixture = createComponent();
    fixture.componentInstance.form.setValue({ name: 'Projeto Editado', description: '' });
    fixture.componentInstance.onSubmit();

    expect(projectServiceSpy.update).toHaveBeenCalledWith(1, { name: 'Projeto Editado', description: '' });
    expect(projectServiceSpy.create).not.toHaveBeenCalled();
  });
});
