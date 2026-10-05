import { TestBed } from '@angular/core/testing';
import { BoardService } from '../../../core/services/board.service';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ProjectDetailComponent } from './project-detail.component';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectResponse } from '../../../core/models/project.models';
import { ApiError } from '../../../core/models/api-error.model';

describe('ProjectDetailComponent', () => {
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let boardServiceSpy: jasmine.SpyObj<BoardService>;

  const owner = { id: 1, username: 'joao_dev', email: 'joao@example.com', authProvider: 'TRADITIONAL' as const, githubConnected: false };

  function projectResponse(overrides: Partial<ProjectResponse> = {}): ProjectResponse {
    return {
      id: 1,
      name: 'Projeto X',
      owner,
      currentUserRole: 'ADMIN',
      currentUserOwner: true,
      archived: false,
      members: [],
      boards: [{ id: 1, name: 'Main Board', defaultBoard: true, githubLinked: false }],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      ...overrides
    };
  }

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['getById', 'archive']);
    boardServiceSpy = jasmine.createSpyObj('BoardService', ['create', 'archive', 'restore', 'listArchived']);

    await TestBed.configureTestingModule({
      imports: [ProjectDetailComponent],
      providers: [
        { provide: BoardService, useValue: boardServiceSpy },
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

  it('should render the member management action with the secondary button style', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse()));

    const fixture = createComponent();
    const manageLink = fixture.nativeElement.querySelector('a[href="/projects/1/members"]');

    expect(manageLink).not.toBeNull();
    expect(manageLink.classList).toContain('btn-secondary');
    expect(manageLink.textContent).toContain('Gerenciar membros');
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
    expect(navigateSpy).toHaveBeenCalledWith('/projects/list');
  });

  it('should not archive when the confirmation is dismissed', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse()));
    spyOn(window, 'confirm').and.returnValue(false);

    const fixture = createComponent();
    fixture.componentInstance.archive();

    expect(projectServiceSpy.archive).not.toHaveBeenCalled();
  });

  const backend = { id: 2, name: 'Backend', defaultBoard: false, githubLinked: true };
  const archived = { ...backend, projectId: 1, archived: true, githubRepoId: 10, githubReauthRequired: false, watchedBranches: [], defaultBaseBranch: 'main', columns: [], createdAt: '', updatedAt: '' };
  it('protects Main Board and asks before archiving Backend', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse({ boards: [projectResponse().boards[0], backend] })));
    boardServiceSpy.archive.and.returnValue(of(undefined));
    const confirmation = spyOn(window, 'confirm').and.returnValue(false);
    const fixture = createComponent();
    expect(fixture.nativeElement.querySelectorAll('.board-archive').length).toBe(1);
    fixture.componentInstance.archiveBoard(backend); expect(boardServiceSpy.archive).not.toHaveBeenCalled();
    confirmation.and.returnValue(true); fixture.componentInstance.archiveBoard(backend); fixture.detectChanges();
    expect(boardServiceSpy.archive).toHaveBeenCalledOnceWith(2);
    expect(fixture.componentInstance.project?.boards.map(board => board.name)).toEqual(['Main Board']);
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('preservadas');
    expect(projectServiceSpy.archive).not.toHaveBeenCalled();
  });

  it('hides board lifecycle controls from developer and viewer', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse({ currentUserRole: 'DEVELOPER', currentUserOwner: false, boards: [backend] })));
    const fixture = createComponent();
    expect(fixture.nativeElement.querySelector('.board-archive')).toBeNull();
    expect(fixture.nativeElement.querySelector('.archived-toggle')).toBeNull();
    fixture.componentInstance.archiveBoard(backend); fixture.componentInstance.toggleArchivedBoards();
    expect(boardServiceSpy.archive).not.toHaveBeenCalled(); expect(boardServiceSpy.listArchived).not.toHaveBeenCalled();
  });

  it('loads archived boards and restores them without navigating to tasks', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse()));
    boardServiceSpy.listArchived.and.returnValues(of({ content: [archived], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true }), of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true }));
    boardServiceSpy.restore.and.returnValue(of({ ...archived, archived: false }));
    const fixture = createComponent(); fixture.componentInstance.toggleArchivedBoards(); fixture.detectChanges();
    expect(boardServiceSpy.listArchived).toHaveBeenCalledWith(1, 0);
    expect(fixture.nativeElement.querySelector('#archived-boards a')).toBeNull();
    fixture.nativeElement.querySelector('.board-restore').click(); fixture.detectChanges();
    expect(boardServiceSpy.restore).toHaveBeenCalledOnceWith(2);
    expect(fixture.componentInstance.project?.boards.map(board => board.id)).toContain(2);
    expect(fixture.componentInstance.archivedBoards).toEqual([]);
  });

  it('keeps the archived board visible and explains a repository conflict', () => {
    projectServiceSpy.getById.and.returnValue(of(projectResponse()));
    boardServiceSpy.restore.and.returnValue(throwError(() => ({ status: 409, message: 'Repositório já vinculado a outro board ativo.' })));
    const fixture = createComponent(); fixture.componentInstance.archivedBoards = [archived];
    fixture.componentInstance.restoreBoard(archived); fixture.detectChanges();
    expect(fixture.componentInstance.archivedBoards).toEqual([archived]);
    expect(fixture.componentInstance.boardBusyId).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('outro board ativo');
  });
});
