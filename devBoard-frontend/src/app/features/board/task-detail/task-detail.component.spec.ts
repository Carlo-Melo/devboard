import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TaskDetailComponent } from './task-detail.component';
import { TaskService } from '../../../core/services/task.service';
import { ProjectService } from '../../../core/services/project.service';
import { AuthService } from '../../../core/services/auth.service';
import { TaskResponse } from '../../../core/models/task.models';
import { ApiError } from '../../../core/models/api-error.model';

describe('TaskDetailComponent', () => {
  let taskServiceSpy: jasmine.SpyObj<TaskService>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const author = { id: 9, username: 'maria_dev', email: 'maria@example.com', authProvider: 'TRADITIONAL' as const, githubConnected: false };
  const currentUser = { id: 1, username: 'joao_dev', email: 'joao@example.com', authProvider: 'TRADITIONAL' as const, githubConnected: false };

  function taskResponse(overrides: Partial<TaskResponse> = {}): TaskResponse {
    return {
      id: 42,
      projectId: 7,
      boardId: 3,
      columnId: 1,
      columnName: 'Backlog',
      title: 'Implementar login',
      position: 0,
      type: 'DEV',
      priority: 'MEDIUM',
      collaborators: [],
      creator: author,
      archived: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      comments: [],
      activities: [],
      ...overrides
    };
  }

  beforeEach(async () => {
    taskServiceSpy = jasmine.createSpyObj('TaskService', [
      'getById', 'update', 'archive', 'createComment', 'updateComment', 'deleteComment'
    ]);
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['getById']);
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getMe']);
    authServiceSpy.getMe.and.returnValue(of(currentUser));
    projectServiceSpy.getById.and.returnValue(of({ members: [] } as any));

    await TestBed.configureTestingModule({
      imports: [TaskDetailComponent],
      providers: [
        { provide: TaskService, useValue: taskServiceSpy },
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '42' } } } },
        provideRouter([])
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
  });

  function createComponent() {
    const fixture = TestBed.createComponent(TaskDetailComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should load the task on init', () => {
    taskServiceSpy.getById.and.returnValue(of(taskResponse()));

    const fixture = createComponent();

    expect(fixture.componentInstance.task?.title).toBe('Implementar login');
    expect(fixture.componentInstance.isLoading).toBeFalse();
    expect(fixture.componentInstance.currentUserId).toBe(1);
  });

  it('should show a not-found message on 404', () => {
    const apiError: ApiError = { status: 404, message: 'Tarefa não encontrada' };
    taskServiceSpy.getById.and.returnValue(throwError(() => apiError));

    const fixture = createComponent();

    expect(fixture.componentInstance.errorMessage).toBe('Tarefa não encontrada.');
  });

  it('should identify comments authored by the current user', () => {
    taskServiceSpy.getById.and.returnValue(of(taskResponse()));
    const fixture = createComponent();

    expect(fixture.componentInstance.isMine({ author: currentUser } as any)).toBeTrue();
    expect(fixture.componentInstance.isMine({ author } as any)).toBeFalse();
  });

  it('should append a new comment and clear the input', () => {
    taskServiceSpy.getById.and.returnValue(of(taskResponse()));
    const fixture = createComponent();
    const newComment = { id: 5, taskId: 42, author: currentUser, content: 'Feito', edited: false, createdAt: '', updatedAt: '' };
    taskServiceSpy.createComment.and.returnValue(of(newComment));

    fixture.componentInstance.newCommentContent = 'Feito';
    fixture.componentInstance.addComment();

    expect(fixture.componentInstance.task?.comments).toContain(newComment);
    expect(fixture.componentInstance.newCommentContent).toBe('');
  });

  it('should save edits and exit edit mode', () => {
    taskServiceSpy.getById.and.returnValue(of(taskResponse()));
    const fixture = createComponent();
    fixture.componentInstance.startEdit();
    fixture.componentInstance.editForm.title = 'Novo título';
    const updated = taskResponse({ title: 'Novo título' });
    taskServiceSpy.update.and.returnValue(of(updated));

    fixture.componentInstance.saveEdit();

    expect(fixture.componentInstance.task?.title).toBe('Novo título');
    expect(fixture.componentInstance.isEditing).toBeFalse();
  });

  it('should navigate to the board after archiving', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    spyOn(router, 'navigate');
    taskServiceSpy.getById.and.returnValue(of(taskResponse()));
    const fixture = createComponent();
    taskServiceSpy.archive.and.returnValue(of(undefined));

    fixture.componentInstance.archive();

    expect(router.navigate).toHaveBeenCalledWith(['/boards', 3]);
  });
});
